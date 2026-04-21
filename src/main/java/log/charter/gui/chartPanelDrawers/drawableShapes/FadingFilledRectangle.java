package log.charter.gui.chartPanelDrawers.drawableShapes;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;

public class FadingFilledRectangle implements DrawableShape {
	private final ShapePositionWithSize position;
	private final Color color;
	private final float startAlpha;
	private final float endAlpha;

	public FadingFilledRectangle(final ShapePositionWithSize position, final Color color, final float startAlpha,
			final float endAlpha) {
		this.position = position;
		this.color = color;
		this.startAlpha = startAlpha;
		this.endAlpha = endAlpha;
	}

	private static Color withAlpha(final Color c, final float alpha) {
		final int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	@Override
	public void draw(final Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		final Paint previousPaint = g.getPaint();
		final GradientPaint gradient = new GradientPaint(//
				position.x, position.y, withAlpha(color, startAlpha), //
				position.x + position.width, position.y, withAlpha(color, endAlpha));
		g.setPaint(gradient);
		g.fillRect(position.x, position.y, position.width, position.height);
		g.setPaint(previousPaint);
	}
}
