package log.charter.services.audio;

import static log.charter.data.song.configs.Tuning.getStringDistanceFromC0;
import static log.charter.util.CollectionUtils.lastBeforeEqual;

import java.math.BigDecimal;
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
		HARMONIC(31);     // Guitar Harmonics - GM program 32

		public final int midiProgram;

		private GuitarSoundType(final int midiProgram) {
			this.midiProgram = midiProgram;
		}
	}

	private static final int midiZeroDistanceFromC0 = -12;
	private static final int pitchBendBaseValue = 8192;
	private static final int pitchBendRange = 8191;

	private boolean available = true;
	private ChartData chartData;

	private Synthesizer synthesizer;
	private MidiChannel[] channels;
	private int[] lastNotes;
	private int[] lastActualNotes;

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
			for (int i = 0; i < channels.length; i++) {
				// Set volume to maximum
				channels[i].controlChange(7, 127);
				
				// Configure pitch bend range to ±12 semitones (1 octave) using RPN
				// RPN MSB (101) = 0, RPN LSB (100) = 0 selects pitch bend sensitivity
				channels[i].controlChange(101, 0);  // RPN MSB
				channels[i].controlChange(100, 0);  // RPN LSB
				channels[i].controlChange(6, 12);   // Data Entry MSB - semitones (12 = 1 octave)
				channels[i].controlChange(38, 0);   // Data Entry LSB - cents
				// Reset RPN to null to avoid accidentally changing settings
				channels[i].controlChange(101, 127);
				channels[i].controlChange(100, 127);
				
				lastNotes[i] = -1;
				lastActualNotes[i] = -1;
			}

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
		// Clamp to ±12 semitones (1 octave) which we configured via RPN
		if (bendStep < -12) {
			bendStep = -12;
		}
		if (bendStep > 12) {
			bendStep = 12;
		}

		return pitchBendBaseValue + (int) (bendStep * pitchBendRange / 12);
	}

	private void playMidiNote(final GuitarSoundType soundType, final int string, final int note, double bendValue) {
		if (lastNotes[string] != -1) {
			return;
		}

		ensureSynthesizerAvailable();
		if (!available || channels == null || string >= channels.length) {
			return;
		}

		final MidiChannel channel = channels[string];
		channel.allNotesOff();
		channel.programChange(soundType.midiProgram);

		// Play the note at the initial bend position
		channel.setPitchBend(getPitchBend(bendValue));
		channel.noteOn(note, 127);
		lastNotes[string] = note;
		lastActualNotes[string] = note;
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
		bendValue += getMidiNote(string, fret, chartData.currentStrings())
				+ chartData.currentArrangement().tuning.getTuning()[string] - actualNote;
		bendValue += chartData.currentArrangement().centOffset.multiply(new BigDecimal("0.01")).doubleValue();

		// Simply apply pitch bend - no note switching to avoid clicks
		// Clamp to configured pitch bend range (±12 semitones / 1 octave)
		if (bendValue > 12.0) {
			bendValue = 12.0;
		}
		if (bendValue < -12.0) {
			bendValue = -12.0;
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
			final boolean harmonic, final List<BendValue> bendValues, final String toneName) {
		GuitarSoundType soundType;
		if (mute) {
			soundType = GuitarSoundType.MUTE;
		} else if (harmonic) {
			soundType = GuitarSoundType.HARMONIC;
		} else if (toneName.contains("distortion") || toneName.contains("lead")) {
			soundType = GuitarSoundType.DISTORTION;
		} else if (toneName.contains("overdrive")) {
			soundType = GuitarSoundType.OVERDRIVE;
		} else {
			soundType = GuitarSoundType.CLEAN;
		}

		final int strings = chartData.currentStrings();
		final int midiNote = getMidiNote(string, fret, strings)
				+ chartData.currentArrangement().tuning.getTuning()[string];

		double bendValue = 0;
		if (!bendValues.isEmpty()) {
			final BendValue noteBendValue = bendValues.get(0);
			if (noteBendValue.position().compareTo(position) == 0) {
				bendValue = noteBendValue.bendValue.doubleValue();
			}
		}
		bendValue += chartData.currentArrangement().centOffset.multiply(new BigDecimal("0.01")).doubleValue();

		playMidiNote(soundType, string, midiNote, bendValue);
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
		final boolean harmonic = note.harmonic != Harmonic.NONE;
		final List<BendValue> bendValues = note.bendValues;
		final String toneName = getToneName(note.position(chartData.beats()));

		playSimpleNote(note.position(), string, fret, mute, harmonic, bendValues, toneName);
	}

	private void playChord(final Chord chord) {
		final String toneName = getToneName(chord.position(chartData.beats()));
		final ChordTemplate template = chartData.currentArrangement().chordTemplates.get(chord.templateId());

		for (final Entry<Integer, ChordNote> chordNoteData : chord.chordNotes.entrySet()) {
			final int string = chordNoteData.getKey();
			final int fret = template.frets.get(string);
			final boolean mute = chordNoteData.getValue().mute != Mute.NONE;
			final boolean harmonic = chordNoteData.getValue().harmonic != Harmonic.NONE;
			final List<BendValue> bendValues = chordNoteData.getValue().bendValues;

			playSimpleNote(chord.position(), string, fret, mute, harmonic, bendValues, toneName);
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

		channels[string].allNotesOff();
		lastNotes[string] = -1;
		lastActualNotes[string] = -1;
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
