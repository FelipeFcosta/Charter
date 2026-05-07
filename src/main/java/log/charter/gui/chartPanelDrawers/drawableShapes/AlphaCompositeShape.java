package log.charter.gui.chartPanelDrawers.drawableShapes;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;

public final class AlphaCompositeShape implements DrawableShape {
	private final DrawableShape shape;
	private final float alpha;

	public AlphaCompositeShape(final DrawableShape shape, final float alpha) {
		this.shape = shape;
		this.alpha = alpha;
	}

	@Override
	public void draw(final Graphics2D g) {
		final Composite saved = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		shape.draw(g);
		g.setComposite(saved);
	}
}
