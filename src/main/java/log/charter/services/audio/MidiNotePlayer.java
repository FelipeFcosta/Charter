package log.charter.services.audio;

import static log.charter.data.song.configs.Tuning.getStringDistanceFromC0;
import static log.charter.util.CollectionUtils.lastBeforeEqual;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;

import log.charter.data.ChartData;
import log.charter.data.config.values.AudioConfig;
import log.charter.data.song.BendValue;
import log.charter.data.song.ChordTemplate;
import log.charter.data.song.ToneChange;
import log.charter.data.song.enums.Harmonic;
import log.charter.data.song.enums.Mute;
import log.charter.data.song.enums.Harmonic;
import log.charter.data.song.notes.Chord;
import log.charter.data.song.notes.ChordNote;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.notes.Note;
import log.charter.data.song.position.FractionalPosition;
import log.charter.data.song.position.time.Position;
import log.charter.io.Logger;

public class MidiNotePlayer {
	private enum GuitarSoundType {
		CLEAN(27),        // Electric Guitar (clean) - GM program 28
		OVERDRIVE(29),    // Overdriven Guitar - GM program 30
		DISTORTION(30),   // Distortion Guitar - GM program 31
		MUTE(28),         // Electric Guitar (muted) - GM program 29
		HARMONIC(31);     // Guitar Harmonics - GM program 32 (sample-based; in the default Java softsynth it transposes the played note up by one octave, compensated for at noteOn time — see HARMONIC_PATCH_OCTAVE_OFFSET)

		public final int midiProgram;

		private GuitarSoundType(final int midiProgram) {
			this.midiProgram = midiProgram;
		}
	}

	/**
	 * Pitch bend sensitivity sent via RPN (Data Entry MSB = whole semitones up/down). Must be large enough for
	 * single-string slides (up to {@link log.charter.data.config.values.InstrumentConfig#frets}) plus typical
	 * bends, vibrato, and cent offset. Was ±12 which capped slides (e.g. 3→24 = 21 semitones) far below the chart.
	 */
	private static final int PITCH_BEND_SEMITONE_RANGE = 48;

	/**
	 * The GM "Guitar Harmonics" patch in the default Java softsynth plays one octave higher than the
	 * requested MIDI note. We subtract this at noteOn time so the audible pitch matches what the rest
	 * of the code computes (and what real natural harmonics produce on the guitar).
	 */
	private static final int HARMONIC_PATCH_OCTAVE_OFFSET = -12;

	private static final int midiZeroDistanceFromC0 = -12;
	private static final int pitchBendBaseValue = 8192;
	private static final int pitchBendRange = 8191;

	private boolean available = true;
	private ChartData chartData;

	private Synthesizer synthesizer;
	private MidiChannel[] channels;
	private int[] lastNotes;
	private int[] lastActualNotes;
	private boolean[] lastHarmonic;
	private boolean[] ringingHarmonics;
	private double appliedCentOffset = Double.NaN;

	public void init(final ChartData chartData) {
		this.chartData = chartData;
		initializeSynthesizer();
	}

	private void initializeSynthesizer() {
		try {
			if (synthesizer != null && synthesizer.isOpen()) {
				return;
			}

		synthesizer = MidiSystem.getSynthesizer();
		synthesizer.open();

		channels = synthesizer.getChannels();
		lastNotes = new int[channels.length];
		lastActualNotes = new int[channels.length];
		lastHarmonic = new boolean[channels.length];
		ringingHarmonics = new boolean[channels.length];
			for (int i = 0; i < channels.length; i++) {
				// Set volume to maximum
				channels[i].controlChange(7, 127);
				
				// Pitch bend range: enough for max fret span on one string + bends (see PITCH_BEND_SEMITONE_RANGE)
				channels[i].controlChange(101, 0);  // RPN MSB
				channels[i].controlChange(100, 0);  // RPN LSB
				channels[i].controlChange(6, PITCH_BEND_SEMITONE_RANGE);
				channels[i].controlChange(38, 0);   // Data Entry LSB - cents
				// Reset RPN to null to avoid accidentally changing settings
				channels[i].controlChange(101, 127);
				channels[i].controlChange(100, 127);
				
				lastNotes[i] = -1;
				lastActualNotes[i] = -1;
				lastHarmonic[i] = false;
				ringingHarmonics[i] = false;
			}

			appliedCentOffset = Double.NaN;
			available = true;
		} catch (final MidiUnavailableException e) {
			available = false;
			Logger.error("Midi unavailable", e);
		}
	}

	private int getMidiNote(final int string, final int fret, final int strings) {
		final boolean bass = chartData.currentArrangement().isBass();
		return getStringDistanceFromC0(string, strings, bass) + fret - midiZeroDistanceFromC0;
	}

	private int getPitchBend(double bendStep) {
		if (bendStep < -PITCH_BEND_SEMITONE_RANGE) {
			bendStep = -PITCH_BEND_SEMITONE_RANGE;
		}
		if (bendStep > PITCH_BEND_SEMITONE_RANGE) {
			bendStep = PITCH_BEND_SEMITONE_RANGE;
		}

		return pitchBendBaseValue + (int) (bendStep * pitchBendRange / PITCH_BEND_SEMITONE_RANGE);
	}

	/**
	 * Applies the arrangement's cent offset as a persistent channel detune via RPN 1 (Channel Fine Tuning)
	 * instead of riding pitch bend. Pitch bend on the Guitar Harmonics patch is unreliable in many synths;
	 * channel fine tuning is honored consistently across patches. Range is ±100 cents (14-bit resolution).
	 */
	private void applyChannelFineTuning(final MidiChannel channel, final double cents) {
		double clamped = cents;
		if (clamped > 100.0) {
			clamped = 100.0;
		}
		if (clamped < -100.0) {
			clamped = -100.0;
		}

		int value = 8192 + (int) Math.round(clamped / 100.0 * 8192.0);
		if (value < 0) {
			value = 0;
		}
		if (value > 16383) {
			value = 16383;
		}

		final int msb = (value >> 7) & 0x7F;
		final int lsb = value & 0x7F;

		channel.controlChange(101, 0);   // RPN MSB
		channel.controlChange(100, 1);   // RPN LSB = Channel Fine Tuning
		channel.controlChange(6, msb);   // Data Entry MSB
		channel.controlChange(38, lsb);  // Data Entry LSB
		channel.controlChange(101, 127); // null RPN
		channel.controlChange(100, 127);
	}

	private void updateFineTuning() {
		if (channels == null || chartData == null || chartData.currentArrangement() == null) {
			return;
		}

		final double currentOffset = chartData.currentArrangement().centOffset.doubleValue();
		if (currentOffset == appliedCentOffset) {
			return;
		}

		for (final MidiChannel channel : channels) {
			applyChannelFineTuning(channel, currentOffset);
		}
		appliedCentOffset = currentOffset;
	}

	private int getHarmonicShift(final int fret, final Harmonic harmonicValue) {
		// The mathematical physical pitch difference for harmonics.
		switch (fret) {
			case 3:
				return 28;
			case 4:
				return 24;
			case 5:
			case 9:
				return 19;
			case 7:
			case 16:
				return 12;
			case 12:
			case 19:
			case 24:
			default:
				return 0;
		}
	}

	private void playMidiNotes(final GuitarSoundType soundType, final int string, final int[] notes, final int[] velocities, double bendValue, final boolean harmonic) {
		if (lastNotes[string] != -1) {
			return;
		}

		ensureSynthesizerAvailable();
		if (!available || channels == null || string >= channels.length) {
			return;
		}

		final MidiChannel channel = channels[string];
		channel.allNotesOff();
		ringingHarmonics[string] = false;
		channel.programChange(soundType.midiProgram);

		// Play the note at the initial bend position
		channel.setPitchBend(getPitchBend(bendValue));
		final int patchOffset = (soundType == GuitarSoundType.HARMONIC) ? HARMONIC_PATCH_OCTAVE_OFFSET : 0;
		for (int i = 0; i < notes.length; i++) {
			channel.noteOn(notes[i] + patchOffset, velocities[i]);
		}
		lastNotes[string] = notes[0];
		lastActualNotes[string] = notes[0];
		lastHarmonic[string] = harmonic;
	}

	private void ensureSynthesizerAvailable() {
		if (!available) {
			return;
		}

		if (synthesizer == null || !synthesizer.isOpen()) {
			initializeSynthesizer();
		}
	}

	public void updateBend(final int string, final int fret, double bendValue) {
		if (!available || channels == null || string >= channels.length || lastNotes[string] == -1) {
			return;
		}

		ensureSynthesizerAvailable();
		if (!available || channels == null || string >= channels.length) {
			return;
		}

		final MidiChannel channel = channels[string];

		int actualNote = lastNotes[string];
		int baseNote = getMidiNote(string, fret, chartData.currentStrings())
				+ chartData.currentArrangement().tuning.getTuning()[string];
		if (lastHarmonic[string]) {
			baseNote += getHarmonicShift(fret, log.charter.data.song.enums.Harmonic.NORMAL);
		}
		
		bendValue += baseNote - actualNote;

		// Clamp to configured pitch bend range (see PITCH_BEND_SEMITONE_RANGE)
		if (bendValue > PITCH_BEND_SEMITONE_RANGE) {
			bendValue = PITCH_BEND_SEMITONE_RANGE;
		}
		if (bendValue < -PITCH_BEND_SEMITONE_RANGE) {
			bendValue = -PITCH_BEND_SEMITONE_RANGE;
		}

		final int pitchBend = getPitchBend(bendValue);
		channel.setPitchBend(pitchBend);
		
		// Compensate for volume loss during pitch bends
		// Many synthesizers reduce volume when pitch is bent
		// Apply a slight volume boost proportional to bend amount
		final double bendAmount = Math.abs(bendValue);
		if (bendAmount > 0.1) {
			// Boost expression (CC11) slightly during bends (5-15% boost depending on bend amount)
			final int expressionBoost = (int) (127 + Math.min(bendAmount * 1.5, 15));
			channel.controlChange(11, Math.min(expressionBoost, 127));
		} else {
			// Reset to normal expression when not bending
			channel.controlChange(11, 127);
		}
	}

	public void updateVolume() {
		if (!available || channels == null) {
			return;
		}

		ensureSynthesizerAvailable();
		if (!available || channels == null) {
			return;
		}

		for (final MidiChannel channel : channels) {
			channel.controlChange(7, (int) (AudioConfig.sfxVolume * 127.0));
		}
	}

	private void playSimpleNote(final FractionalPosition position, final int string, final int fret, final boolean mute,
			final Harmonic harmonicValue, final List<BendValue> bendValues, final String toneName) {
		GuitarSoundType soundType;
		if (mute) {
			soundType = GuitarSoundType.MUTE;
		} else if (harmonicValue == Harmonic.NORMAL || harmonicValue == Harmonic.PINCH) {
			soundType = GuitarSoundType.HARMONIC;
		} else if (toneName.contains("distortion") || toneName.contains("lead")) {
			soundType = GuitarSoundType.DISTORTION;
		} else if (toneName.contains("overdrive")) {
			soundType = GuitarSoundType.OVERDRIVE;
		} else {
			soundType = GuitarSoundType.CLEAN;
		}

		final int strings = chartData.currentStrings();
		final int baseMidiNote = getMidiNote(string, fret, strings)
				+ chartData.currentArrangement().tuning.getTuning()[string];

		int[] notesToPlay;
		int[] velocities;

		if (harmonicValue == Harmonic.PINCH) {
			notesToPlay = new int[] { baseMidiNote, baseMidiNote + getHarmonicShift(fret, harmonicValue) };
			velocities = new int[] { (int) (127 * 0.5), (int) (127 * 1.0) };
		} else if (harmonicValue == Harmonic.NORMAL) {
			notesToPlay = new int[] { baseMidiNote + getHarmonicShift(fret, harmonicValue) };
			velocities = new int[] { 127 };
		} else {
			notesToPlay = new int[] { baseMidiNote };
			velocities = new int[] { 127 };
		}

		double bendValue = 0;
		if (!bendValues.isEmpty()) {
			final BendValue noteBendValue = bendValues.get(0);
			if (noteBendValue.position().compareTo(position) == 0) {
				bendValue = noteBendValue.bendValue.doubleValue();
			}
		}

		playMidiNotes(soundType, string, notesToPlay, velocities, bendValue, harmonicValue == Harmonic.NORMAL);
	}

	private String getToneName(final double position) {
		final ToneChange lastToneChange = lastBeforeEqual(chartData.currentToneChanges(),
				new Position(position).toFraction(chartData.beats())).find();
		if (lastToneChange != null) {
			return lastToneChange.toneName;
		}

		return chartData.currentArrangement().startingTone;
	}

	private void playNote(final Note note) {
		final int string = note.string;
		final int fret = note.fret;
		final boolean mute = note.mute != Mute.NONE;
		final Harmonic harmonicValue = note.harmonic;
		final List<BendValue> bendValues = note.bendValues;
		final String toneName = getToneName(note.position(chartData.beats()));

		playSimpleNote(note.position(), string, fret, mute, harmonicValue, bendValues, toneName);
	}

	private void playChord(final Chord chord) {
		final String toneName = getToneName(chord.position(chartData.beats()));
		final ChordTemplate template = chartData.currentArrangement().chordTemplates.get(chord.templateId());

		for (final Entry<Integer, ChordNote> chordNoteData : chord.chordNotes.entrySet()) {
			final int string = chordNoteData.getKey();
			final int fret = template.frets.get(string);
			final boolean mute = chordNoteData.getValue().mute != Mute.NONE;
			final Harmonic harmonicValue = chordNoteData.getValue().harmonic;
			final List<BendValue> bendValues = chordNoteData.getValue().bendValues;

			playSimpleNote(chord.position(), string, fret, mute, harmonicValue, bendValues, toneName);
		}
	}

	public void playSound(final ChordOrNote sound) {
		if (!available) {
			return;
		}

		ensureSynthesizerAvailable();
		if (!available) {
			return;
		}

		updateFineTuning();

		// Cut off any harmonics that were left ringing from a previous note/preview
		for (int i = 0; i < channels.length; i++) {
			if (ringingHarmonics[i]) {
				channels[i].allNotesOff();
				ringingHarmonics[i] = false;
			}
		}

		if (sound.isNote()) {
			playNote(sound.note());
		} else {
			playChord(sound.chord());
		}
	}

	public void stopSound(final int string) {
		if (!available || channels == null || string >= channels.length) {
			return;
		}

		if (!lastHarmonic[string]) {
			channels[string].allNotesOff();
		} else {
			ringingHarmonics[string] = true;
		}
		lastNotes[string] = -1;
		lastActualNotes[string] = -1;
		lastHarmonic[string] = false;
	}

	public void stopSound() {
		if (!available || channels == null) {
			return;
		}

		for (int string = 0; string < channels.length; string++) {
			stopSound(string);
		}
	}
}
