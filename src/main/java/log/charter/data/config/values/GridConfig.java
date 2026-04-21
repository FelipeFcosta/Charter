package log.charter.data.config.values;

import static log.charter.data.config.values.accessors.BooleanValueAccessor.forBoolean;
import static log.charter.data.config.values.accessors.EnumValueAccessor.forEnum;
import static log.charter.data.config.values.accessors.IntValueAccessor.forInteger;

import java.util.Map;

import log.charter.data.GridType;
import log.charter.data.config.values.accessors.ValueAccessor;
import log.charter.data.song.BeatsMap.ImmutableBeatsMap;

public class GridConfig {
	public static boolean showGrid = true;
	public static GridType gridType = GridType.BEAT;
	public static int gridSize = 4;

	/**
	 * Grid lines must stay at least this far apart (in milliseconds). Positions in
	 * the chart are stored in ms, so this is also the lower bound for
	 * {@code beatLength / gridSize}.
	 */
	public static final double minGridLineDistanceMs = 2.0;

	/**
	 * Absolute cap on {@code gridSize}, independent of the beats map. Prevents the
	 * text input / shortcut / toolbar button from going past an unreasonable
	 * value even when the minimum beat length would allow it.
	 */
	public static final int hardMaxGridSize = 1024;

	public static void init(final Map<String, ValueAccessor> valueAccessors, final String name) {
		valueAccessors.put(name + ".showGrid", forBoolean(v -> showGrid = v, () -> showGrid, showGrid));
		valueAccessors.put(name + ".gridType", forEnum(GridType.class, v -> gridType = v, () -> gridType, gridType));
		valueAccessors.put(name + ".gridSize", forInteger(v -> gridSize = v, () -> gridSize, gridSize));
	}

	/**
	 * Returns the largest {@code gridSize} that keeps any two adjacent grid lines
	 * at least {@link #minGridLineDistanceMs} apart, given the current beats. The
	 * grid divides every beat into {@code gridSize} parts, so the strictest bound
	 * is {@code minBeatLength / minGridLineDistanceMs}.
	 */
	public static int maxGridSize(final ImmutableBeatsMap beats) {
		if (beats == null || beats.size() < 2) {
			return Integer.MAX_VALUE;
		}

		double minBeatLength = Double.POSITIVE_INFINITY;
		for (int i = 1; i < beats.size(); i++) {
			final double length = beats.get(i).position() - beats.get(i - 1).position();
			if (length > 0 && length < minBeatLength) {
				minBeatLength = length;
			}
		}

		if (!Double.isFinite(minBeatLength)) {
			return Integer.MAX_VALUE;
		}

		final int max = (int) Math.floor(minBeatLength / minGridLineDistanceMs);
		return Math.max(1, max);
	}
}
