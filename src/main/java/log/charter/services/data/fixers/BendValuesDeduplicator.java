package log.charter.services.data.fixers;

import java.util.ArrayList;
import java.util.List;

import log.charter.data.song.BendValue;
import log.charter.data.song.position.fractional.IConstantFractionalPosition;

/**
 * Ensures at most one bend keyframe exists per fractional position on a note.
 */
public final class BendValuesDeduplicator {
	private BendValuesDeduplicator() {
	}

	/**
	 * Sorts by position and collapses entries that share the same position; when several
	 * share a position, the last one in sort order (original tie-breaker) is kept.
	 */
	public static void deduplicateInPlace(final List<BendValue> bendValues) {
		if (bendValues == null || bendValues.size() <= 1) {
			return;
		}

		bendValues.sort(IConstantFractionalPosition::compareTo);

		final List<BendValue> out = new ArrayList<>(bendValues.size());
		for (final BendValue bv : bendValues) {
			if (out.isEmpty()) {
				out.add(bv);
				continue;
			}
			final BendValue last = out.get(out.size() - 1);
			if (last.position().equals(bv.position())) {
				out.set(out.size() - 1, bv);
			} else {
				out.add(bv);
			}
		}

		bendValues.clear();
		bendValues.addAll(out);
	}
}
