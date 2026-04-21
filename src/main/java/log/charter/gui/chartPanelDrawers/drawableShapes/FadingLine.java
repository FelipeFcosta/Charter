package log.charter.gui.chartPanelDrawers.drawableShapes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Stroke;

import log.charter.util.data.Position2D;

public class FadingLine implements DrawableShape {
	private final Position2D from;
	private final Position2D to;
	private final Color color;
	private final int thickness;
	private final float startAlpha;
	private final float endAlpha;

	public FadingLine(final Position2D from, final Position2D to, final Color color, final int thickness,
			final float startAlpha, final float endAlpha) {
		this.from = from;
		this.to = to;
		this.color = color;
		this.thickness = thickness;
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
		final Stroke previousStroke = g.getStroke();

		final GradientPaint gradient = new GradientPaint(//
				from.x, from.y, withAlpha(color, startAlpha), //
				to.x, to.y, withAlpha(color, endAlpha));
		g.setPaint(gradient);
		g.setStroke(new BasicStroke(thickness));
		g.drawLine(from.x, from.y, to.x, to.y);

		g.setPaint(previousPaint);
		g.setStroke(previousStroke);
	}
}
