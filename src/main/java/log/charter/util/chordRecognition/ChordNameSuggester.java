package log.charter.util.chordRecognition;

import static java.util.Arrays.asList;
import static log.charter.util.SoundUtils.soundToSimpleName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import log.charter.data.song.configs.Tuning;
import log.charter.data.song.configs.Tuning.TuningType;
import log.charter.util.SoundUtils;
import log.charter.util.collections.ArrayList2;

public class ChordNameSuggester {
	private static List<Integer> soundsToNotes(final int[] sounds) {
		final List<Integer> notes = new ArrayList<>(sounds.length);
		for (int i = 0; i < sounds.length; i++) {
			int note = sounds[i];
			while (note < 0) {
				note += 12;
			}
			note = note % 12;

			if (!notes.contains(note)) {
				notes.add(note);
			}
		}

		return notes;
	}

	private static List<String> recognizeChord(final int[] sounds) {
		final List<Integer> notes = soundsToNotes(sounds);
		if (notes.size() == 1) {
			return asList(soundToSimpleName(notes.get(0), true));
		}

		final List<String> foundNames = new ArrayList2<>();
		for (int i = 0; i < notes.size(); i++) {
			final int root = notes.get(i);
			final List<String> foundNamesForRoot = ChordNameAdder.getSuggestedChordNames(root, notes);
			foundNames.addAll(foundNamesForRoot);

			if (root != sounds[0] % 12) {
				for (final String name : foundNamesForRoot) {
					foundNames.add(name + "/" + soundToSimpleName(root, true));
				}
			}
		}

		return foundNames;
	}

	private static boolean negativeExists(final int[] sounds) {
		for (final int sound : sounds) {
			if (sound < 0) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns an E-tuning-equivalent Tuning for chord naming purposes, if the
	 * actual tuning is a standard (all-uniform) or drop-2 tuning. For standard
	 * tunings this returns E standard; for drop-2 tunings this returns E drop D.
	 * For anything else (open, DADGAD, E drop C, custom, etc.) the actual tuning
	 * is returned unchanged.
	 */
	private static Tuning computeETuningEquivalent(final Tuning tuning) {
		final int strings = tuning.strings();
		if (strings <= 1) {
			return tuning;
		}

		final int[] tuningValues = tuning.getTuning();

		// The drop string index mirrors the logic used in TuningType.fromStandardOrDropTuning
		final int dropStringIdx = strings - Math.min(6, strings);

		// Determine the common value of all non-drop strings
		int baseValue = Integer.MIN_VALUE;
		boolean isNormalizable = true;
		for (int i = 0; i < strings; i++) {
			if (i == dropStringIdx) {
				continue;
			}
			if (baseValue == Integer.MIN_VALUE) {
				baseValue = tuningValues[i];
			} else if (tuningValues[i] != baseValue) {
				isNormalizable = false;
				break;
			}
		}

		if (!isNormalizable || baseValue == Integer.MIN_VALUE) {
			return tuning;
		}

		final int dropDiff = tuningValues[dropStringIdx] - baseValue;
		if (dropDiff == 0) {
			// Standard tuning (all strings same) → name as E standard
			return new Tuning(TuningType.E_STANDARD, strings);
		} else if (dropDiff == -2) {
			// Drop-2 tuning → name as E drop D
			return new Tuning(TuningType.E_DROP_D, strings);
		}

		// Drop-4 (E drop C) or any other interval structure → no normalization
		return tuning;
	}

	public static List<String> suggestChordNames(final Tuning tuning, final Map<Integer, Integer> templateFrets) {
		return suggestChordNames(tuning, templateFrets, 0, false);
	}

	public static List<String> suggestChordNames(final Tuning tuning, final Map<Integer, Integer> templateFrets, final int capo) {
		return suggestChordNames(tuning, templateFrets, capo, false);
	}

	public static List<String> suggestChordNames(final Tuning tuning, final Map<Integer, Integer> templateFrets,
			final int capo, final boolean useETuningNaming) {
		final Map<Integer, Integer> adjustedFrets;
		if (capo > 0) {
			adjustedFrets = new java.util.HashMap<>();
			for (final Map.Entry<Integer, Integer> entry : templateFrets.entrySet()) {
				final int fret = entry.getValue();
				adjustedFrets.put(entry.getKey(), Math.max(0, fret - capo));
			}
		} else {
			adjustedFrets = templateFrets;
		}

		final Tuning effectiveTuning = useETuningNaming ? computeETuningEquivalent(tuning) : tuning;
		final int[] sounds = SoundUtils.getSounds(effectiveTuning, false, adjustedFrets);

		while (negativeExists(sounds)) {
			for (int i = 0; i < sounds.length; i++) {
				sounds[i] += 12;
			}
		}

		Arrays.sort(sounds);

		if (sounds.length == 1) {
			return asList(soundToSimpleName(sounds[0], true));
		}

		return recognizeChord(sounds);
	}
}
