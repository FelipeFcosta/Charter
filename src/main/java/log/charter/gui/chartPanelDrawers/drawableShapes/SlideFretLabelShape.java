package log.charter.gui.chartPanelDrawers.drawableShapes;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.services.data.selection.SlideFretLabelHandler;
import log.charter.util.data.Position2D;

public class SlideFretLabelShape implements DrawableShape {
	private final int noteId;
	private final int string;
	private final Position2D position;
	private final Font font;
	private final String text;
	private final Color textColor;
	private final Color backgroundColor;
	private final Color borderColor;

	public SlideFretLabelShape(final int noteId, final int string, final Position2D position, final Font font,
			final String text, final Color textColor, final Color backgroundColor, final Color borderColor) {
		this.noteId = noteId;
		this.string = string;
		this.position = position;
		this.font = font;
		this.text = text;
		this.textColor = textColor;
		this.backgroundColor = backgroundColor;
		this.borderColor = borderColor;
	}

	@Override
	public void draw(final Graphics2D g) {
		final boolean selected = SlideFretLabelHandler.instanceIsSelected(noteId, string);
		final Color effectiveBorder = selected ? ColorLabel.SELECT.color() : borderColor;

		final CenteredTextWithBackgroundAndBorder shape = new CenteredTextWithBackgroundAndBorder(position, font, text,
				textColor, backgroundColor, effectiveBorder);
		final ShapePositionWithSize bounds = shape.getPositionAndSize(g);

		if (noteId >= 0) {
			SlideFretLabelHandler.instanceRegisterHitbox(noteId, string, bounds);
		}

		shape.draw(g, bounds);
	}
}
