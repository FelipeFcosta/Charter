package log.charter.services.data.selection;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import log.charter.data.ChartData;
import log.charter.data.config.values.InstrumentConfig;
import log.charter.data.song.notes.Chord;
import log.charter.data.song.notes.ChordNote;
import log.charter.data.song.notes.ChordOrNote;
import log.charter.data.song.notes.Note;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.chartPanelDrawers.drawableShapes.ShapePositionWithSize;
import log.charter.services.data.GuitarSoundsStatusesHandler;

public class SlideFretLabelHandler {
	private static SlideFretLabelHandler instance = null;

	public static SlideFretLabelHandler instance() {
		return instance;
	}

	public static boolean instanceIsSelected(final int noteId, final int string) {
		return instance != null && instance.isSelected(noteId, string);
	}

	public static void instanceRegisterHitbox(final int noteId, final int string,
			final ShapePositionWithSize bounds) {
		if (instance != null) {
			instance.registerHitbox(noteId, string, bounds);
		}
	}

	public SlideFretLabelHandler() {
		instance = this;
	}

	public static final class LabelKey {
		public final int noteId;
		public final int string;

		public LabelKey(final int noteId, final int string) {
			this.noteId = noteId;
			this.string = string;
		}

		@Override
		public int hashCode() {
			return Objects.hash(noteId, string);
		}

		@Override
		public boolean equals(final Object other) {
			if (!(other instanceof LabelKey)) {
				return false;
			}
			final LabelKey o = (LabelKey) other;
			return o.noteId == noteId && o.string == string;
		}
	}

	private ChartData chartData;
	private GuitarSoundsStatusesHandler guitarSoundsStatusesHandler;
	private UndoSystem undoSystem;

	private final Map<LabelKey, ShapePositionWithSize> hitboxes = new HashMap<>();
	private LabelKey selected = null;

	private int pendingFret = 0;
	private long pendingFretTimeoutMs = 0;
	private static final long pendingFretWindowMs = 2000;

	public void clearHitboxes() {
		hitboxes.clear();
	}

	public void registerHitbox(final int noteId, final int string, final ShapePositionWithSize bounds) {
		hitboxes.put(new LabelKey(noteId, string), bounds);
	}

	public boolean isSelected(final int noteId, final int string) {
		return selected != null && selected.noteId == noteId && selected.string == string;
	}

	public boolean hasSelection() {
		return selected != null;
	}

	public void clearSelection() {
		selected = null;
		pendingFret = 0;
		pendingFretTimeoutMs = 0;
	}

	public boolean handleClick(final int x, final int y) {
		for (final Map.Entry<LabelKey, ShapePositionWithSize> entry : hitboxes.entrySet()) {
			final ShapePositionWithSize b = entry.getValue();
			if (x >= b.x && x < b.x + b.width && y >= b.y && y < b.y + b.height) {
				final LabelKey key = entry.getKey();
				if (selected != null && selected.equals(key)) {
					clearSelection();
				} else {
					selected = key;
					pendingFret = 0;
					pendingFretTimeoutMs = 0;
				}
				return true;
			}
		}
		return false;
	}

	public boolean handleNumber(final int digit) {
		if (selected == null) {
			return false;
		}

		final long now = System.currentTimeMillis();
		final int candidate = now <= pendingFretTimeoutMs ? pendingFret * 10 + digit : digit;
		pendingFret = candidate > InstrumentConfig.frets ? digit : candidate;
		pendingFretTimeoutMs = now + pendingFretWindowMs;

		applySlideFret(pendingFret);
		return true;
	}

	private void applySlideFret(final int fret) {
		final int clamped = max(1, min(InstrumentConfig.frets, fret));
		final List<ChordOrNote> sounds = chartData.currentSounds();
		if (selected.noteId < 0 || selected.noteId >= sounds.size()) {
			clearSelection();
			return;
		}

		final ChordOrNote sound = sounds.get(selected.noteId);
		undoSystem.addUndo();

		if (sound.isNote()) {
			final Note note = sound.note();
			if (note.string != selected.string) {
				clearSelection();
				return;
			}
			note.slideTo = clamped;
		} else {
			final Chord chord = sound.chord();
			final ChordNote chordNote = chord.chordNotes.get(selected.string);
			if (chordNote == null) {
				clearSelection();
				return;
			}
			chordNote.slideTo = clamped;
		}

		guitarSoundsStatusesHandler.updateLinkedNote(selected.noteId);
	}
}
