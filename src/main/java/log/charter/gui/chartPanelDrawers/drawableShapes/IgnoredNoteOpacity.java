package log.charter.gui.chartPanelDrawers.drawableShapes;

/**
 * Opacity applied to ignored notes’ heads and tails. When combined with unpitched-slide fading, uses
 * additive transparencies: {@code combinedOpacity = ignoreOpacity + fadeAlpha - 1} (clamped), i.e. the
 * invisible portions stack.
 */
public final class IgnoredNoteOpacity {
	public static final float VALUE = 0.8f;

	private IgnoredNoteOpacity() {
	}

	public static float combineWithFading(final float ignoredOpacity, final float fadeAlpha) {
		return Math.max(0f, Math.min(1f, ignoredOpacity + fadeAlpha - 1f));
	}
}
