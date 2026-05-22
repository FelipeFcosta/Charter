package log.charter.gui.lookAndFeel;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;

import log.charter.data.config.ChartPanelColors.ColorLabel;

public class CharterToggleButtonUI extends BasicToggleButtonUI {
	private static final CharterToggleButtonUI toggleButtonUI = new CharterToggleButtonUI();

	public static ComponentUI createUI(final JComponent c) {
		return toggleButtonUI;
	}

	@Override
	public void installUI(final JComponent c) {
		super.installUI(c);
		final AbstractButton button = (AbstractButton) c;
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setBorderPainted(false);
		button.setOpaque(false);
		button.setFont(button.getFont().deriveFont(Font.PLAIN));
	}

	@Override
	public void paint(final Graphics g, final JComponent c) {
		final AbstractButton button = (AbstractButton) c;
		final Graphics2D g2d = (Graphics2D) g.create();
		setupGraphics(g2d, c);

		// button fill
		final RoundRectangle2D.Double roundedRectangle = new RoundRectangle2D.Double(0, 0, c.getWidth() - 1,
				c.getHeight() - 1, 5, 5);
		if (!button.isEnabled()) {
			g2d.setColor(ColorLabel.BASE_BG_3.color());
		} else if (button.getModel().isSelected()) {
			g2d.setColor(ColorLabel.BASE_HIGHLIGHT.color());
		} else {
			g2d.setColor(ColorLabel.BASE_BUTTON.color());
		}
		g2d.fill(roundedRectangle);

		final boolean selectedOnLightAccent;
		if (button.getModel().isSelected()) {
			final Color accent = ColorLabel.BASE_HIGHLIGHT.color();
			final double lum = (0.299 * accent.getRed() + 0.587 * accent.getGreen() + 0.114 * accent.getBlue()) / 255;
			selectedOnLightAccent = lum > 0.75;
		} else {
			selectedOnLightAccent = false;
		}

		// button icon
		if (button.getIcon() != null) {
			final Icon icon = button.getIcon();
			final int iconX = (c.getWidth() - icon.getIconWidth()) / 2;
			final int iconY = (c.getHeight() - icon.getIconHeight()) / 2;
			if (selectedOnLightAccent) {
				final BufferedImage tmp = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
						BufferedImage.TYPE_INT_ARGB);
				final Graphics2D tg = tmp.createGraphics();
				icon.paintIcon(c, tg, 0, 0);
				tg.setComposite(AlphaComposite.SrcAtop);
				tg.setColor(new Color(20, 20, 20));
				tg.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
				tg.dispose();
				g2d.drawImage(tmp, iconX, iconY, null);
			} else {
				icon.paintIcon(c, g2d, iconX, iconY);
			}
		}

		// button text
		if (button.getText() != null && !button.getText().isEmpty()) {
			if (!button.isEnabled()) {
				g2d.setColor(button.getForeground().darker());
			} else if (selectedOnLightAccent) {
				g2d.setColor(new Color(20, 20, 20));
			} else if (button.getModel().isSelected()) {
				g2d.setColor(Color.WHITE);
			} else {
				g2d.setColor(button.getForeground());
			}
			g2d.drawString(button.getText(),
					(int) (c.getWidth() - g2d.getFontMetrics().getStringBounds(button.getText(), g2d).getWidth()) / 2,
					(int) ((c.getHeight() - g2d.getFontMetrics().getAscent() - g2d.getFontMetrics().getDescent()) / 2)
							+ g2d.getFontMetrics().getAscent());
		}

		g2d.dispose();
	}

	private void setupGraphics(final Graphics2D g2d, final JComponent c) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	}

	static void install() {
		UIManager.put("ToggleButtonUI", CharterToggleButtonUI.class.getName());
	}
}
