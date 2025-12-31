package log.charter.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import log.charter.data.song.ChordTemplate;
import log.charter.io.Logger;
import log.charter.io.rsc.xml.ChordLibraryXStreamHandler;
import log.charter.util.Utils;
import log.charter.util.collections.HashMap2;

/**
 * A persistent library of chord templates that users can save and reuse across
 * projects. Chords are stored in a user config file and can be searched by
 * partial fret patterns.
 */
public class ChordLibrary {
	private static final String libraryPath = Utils.defaultConfigDir + "chordLibrary.xml";
	private static ChordLibrary instance;

	private List<ChordTemplate> chords = new ArrayList<>();

	private ChordLibrary() {
	}

	public static ChordLibrary getInstance() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static ChordLibrary load() {
		final File file = new File(libraryPath);
		if (!file.exists()) {
			return new ChordLibrary();
		}

		try {
			final ChordLibrary loaded = ChordLibraryXStreamHandler.readChordLibrary(file);
			return loaded != null ? loaded : new ChordLibrary();
		} catch (final Exception e) {
			Logger.error("Failed to load chord library", e);
			return new ChordLibrary();
		}
	}

	public void save() {
		try {
			ChordLibraryXStreamHandler.writeChordLibrary(this, new File(libraryPath));
		} catch (final Exception e) {
			Logger.error("Failed to save chord library", e);
		}
	}

	public List<ChordTemplate> getChords() {
		return new ArrayList<>(chords);
	}

	public void setChords(final List<ChordTemplate> chords) {
		this.chords = new ArrayList<>(chords);
	}

	/**
	 * Adds a chord to the library if it doesn't already exist. A chord is
	 * considered duplicate if it has the same name, frets, and fingers.
	 * 
	 * @param template the chord template to add
	 * @return true if the chord was added, false if it already exists
	 */
	public boolean addChord(final ChordTemplate template) {
		if (containsChord(template)) {
			return false;
		}

		chords.add(new ChordTemplate(template));
		save();
		return true;
	}

	/**
	 * Removes a chord from the library.
	 * 
	 * @param template the chord template to remove
	 * @return true if the chord was removed
	 */
	public boolean removeChord(final ChordTemplate template) {
		final boolean removed = chords.removeIf(c -> chordsEqual(c, template));
		if (removed) {
			save();
		}
		return removed;
	}

	/**
	 * Checks if the library contains a chord with matching name, frets, and
	 * fingers.
	 */
	public boolean containsChord(final ChordTemplate template) {
		return chords.stream().anyMatch(c -> chordsEqual(c, template));
	}

	/**
	 * Two chords are considered equal if they have the same chord name, frets, and
	 * fingers.
	 */
	private boolean chordsEqual(final ChordTemplate a, final ChordTemplate b) {
		return Objects.equals(a.chordName, b.chordName) //
				&& Objects.equals(a.frets, b.frets) //
				&& Objects.equals(a.fingers, b.fingers);
	}

	/**
	 * Finds chords that match the given partial fret pattern. A chord matches if
	 * for every string that has a fret in the pattern, the chord has the same fret
	 * on that string.
	 * 
	 * @param partialFrets a map of string -> fret for the partial pattern
	 * @return list of matching chords, sorted by relevance (more matching strings
	 *         first)
	 */
	public List<ChordTemplate> findMatchingChords(final HashMap2<Integer, Integer> partialFrets) {
		if (partialFrets == null || partialFrets.isEmpty()) {
			return new ArrayList<>(chords);
		}

		return chords.stream()//
				.filter(chord -> matchesPattern(chord, partialFrets))//
				.sorted((a, b) -> {
					// Sort by number of matching strings (more = better match)
					final int aMatches = countMatches(a, partialFrets);
					final int bMatches = countMatches(b, partialFrets);
					if (aMatches != bMatches) {
						return Integer.compare(bMatches, aMatches);
					}
					// Then by total strings in chord (fewer extra = tighter match)
					return Integer.compare(a.frets.size(), b.frets.size());
				})//
				.collect(Collectors.toList());
	}

	private boolean matchesPattern(final ChordTemplate chord, final HashMap2<Integer, Integer> partialFrets) {
		for (final var entry : partialFrets.entrySet()) {
			final int string = entry.getKey();
			final int fret = entry.getValue();
			final Integer chordFret = chord.frets.get(string);
			if (chordFret == null || !chordFret.equals(fret)) {
				return false;
			}
		}
		return true;
	}

	private int countMatches(final ChordTemplate chord, final HashMap2<Integer, Integer> partialFrets) {
		int count = 0;
		for (final var entry : partialFrets.entrySet()) {
			final Integer chordFret = chord.frets.get(entry.getKey());
			if (chordFret != null && chordFret.equals(entry.getValue())) {
				count++;
			}
		}
		return count;
	}

	public int size() {
		return chords.size();
	}

	public void clear() {
		chords.clear();
		save();
	}
}
