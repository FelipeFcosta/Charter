package log.charter.gui.components.simple;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JLabel;
import javax.swing.JPanel;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.Localization.Label;
import log.charter.gui.components.utils.ComponentUtils;
import log.charter.gui.components.utils.ComponentUtils.ConfirmAnswer;
import log.charter.services.data.ChartingTimerHandler;

/**
 * Time as plain text; transport controls draw vector glyphs. Auto uses the same paint logic as delay "ON"
 * ({@link log.charter.gui.lookAndFeel.CharterToggleButtonUI}), not {@link javax.swing.JToggleButton} (avoids PLAF
 * ellipsis/clipping on the tab strip).
 */
public class ChartingTimerPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final int ROW_H = 20;
	private static final int BTN_W = 30;
	private static final int GAP_TIME_TO_BTNS = 6;
	private static final int GAP_BETWEEN_BUTTONS = 4;
	private static final int GAP_BEFORE_AUTO = 8;
	private static final int TIME_PAD_X = 4;
	private static final int AUTO_TOGGLE_W = 40;

	private final ChartingTimerHandler handler;
	private final JLabel timeLabel;
	private final TransportGlyphButton playPauseButton;
	private final TransportGlyphButton stopButton;
	private final AutoTextToggle autoToggle;

	public ChartingTimerPanel(final ChartingTimerHandler handler) {
		super(null);
		this.handler = handler;

		setOpaque(false);
		setFocusable(false);

		timeLabel = new JLabel(formatMs(0));
		timeLabel.setForeground(ColorLabel.BASE_TEXT.color());
		timeLabel.setFocusable(false);
		timeLabel.setRequestFocusEnabled(false);

		playPauseButton = new TransportGlyphButton(TransportGlyphButton.Mode.PLAY_PAUSE);
		playPauseButton.setOnClick(() -> {
			if (handler.isRunning()) {
				handler.pause();
			} else {
				handler.play();
			}
			refresh();
		});

		stopButton = new TransportGlyphButton(TransportGlyphButton.Mode.STOP);
		stopButton.setOnClick(this::onStopClicked);

		autoToggle = new AutoTextToggle(Label.CHARTING_TIMER_AUTO.label(), () -> {
			handler.setSyncWithAudio(!handler.isSyncWithAudio());
			refresh();
		});
		ComponentUtils.setComponentSize(autoToggle, AUTO_TOGGLE_W, ROW_H);

		add(timeLabel);
		add(playPauseButton);
		add(stopButton);
		add(autoToggle);

		refresh();
	}

	private void onStopClicked() {
		if (!handler.isEnabled()) {
			return;
		}
		if (handler.currentTotalMs() == 0 && !handler.isRunning()) {
			return;
		}
		final ConfirmAnswer answer = ComponentUtils.askYesNo(this, Label.CHARTING_TIMER_RESET_POPUP_TITLE,
				Label.CHARTING_TIMER_RESET_POPUP_MSG);
		if (answer != ConfirmAnswer.YES) {
			return;
		}
		handler.stop();
		refresh();
	}

	private int timeColumnWidth() {
		final FontMetrics fm = timeLabel.getFontMetrics(timeLabel.getFont());
		return fm.stringWidth(timeLabel.getText()) + TIME_PAD_X * 2;
	}

	private int rowContentWidth() {
		return timeColumnWidth() + GAP_TIME_TO_BTNS + BTN_W + GAP_BETWEEN_BUTTONS + BTN_W + GAP_BEFORE_AUTO
				+ AUTO_TOGGLE_W;
	}

	@Override
	public void doLayout() {
		final int contentW = rowContentWidth();
		int x = Math.max(0, (getWidth() - contentW) / 2);
		final int y = Math.max(0, (getHeight() - ROW_H) / 2);

		final int tw = timeColumnWidth();
		timeLabel.setBounds(x, y, tw, ROW_H);
		x += tw + GAP_TIME_TO_BTNS;

		playPauseButton.setBounds(x, y, BTN_W, ROW_H);
		x += BTN_W + GAP_BETWEEN_BUTTONS;

		stopButton.setBounds(x, y, BTN_W, ROW_H);
		x += BTN_W + GAP_BEFORE_AUTO;

		autoToggle.setBounds(x, y, AUTO_TOGGLE_W, ROW_H);
	}

	public void refresh() {
		final boolean enabled = handler.isEnabled();
		timeLabel.setText(formatMs(handler.currentTotalMs()));

		autoToggle.setSelected(handler.isSyncWithAudio());
		autoToggle.setEnabled(enabled);

		final boolean running = handler.isRunning();
		playPauseButton.setPausedVisual(running);
		playPauseButton.setEnabled(enabled && !handler.isSyncWithAudio());

		stopButton.setEnabled(enabled);

		doLayout();
	}

	private static String formatMs(final long ms) {
		final long totalSec = ms / 1000;
		final int h = (int) (totalSec / 3600);
		final int m = (int) ((totalSec % 3600) / 60);
		final int s = (int) (totalSec % 60);
		return "%d:%02d:%02d".formatted(h, m, s);
	}

	@Override
	public boolean contains(final int x, final int y) {
		for (final Component c : getComponents()) {
			if (!c.isVisible()) {
				continue;
			}
			final Rectangle b = c.getBounds();
			if (b.contains(x, y)) {
				final Point childPt = new Point(x - b.x, y - b.y);
				return c.contains(childPt);
			}
		}
		return false;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(rowContentWidth() + 16, Math.max(28, ROW_H + 8));
	}

	@Override
	public Dimension getMinimumSize() {
		return getPreferredSize();
	}

	/**
	 * Same visuals as {@link log.charter.gui.lookAndFeel.CharterToggleButtonUI}: grey off, blue (highlight) on.
	 */
	private static final class AutoTextToggle extends JPanel {
		private static final long serialVersionUID = 1L;

		private final String text;
		private final Runnable onUserClick;
		private boolean selected;
		private boolean pressed;

		AutoTextToggle(final String text, final Runnable onUserClick) {
			super(null);
			this.text = text;
			this.onUserClick = onUserClick;
			setOpaque(false);
			setFocusable(false);
			setRequestFocusEnabled(false);

			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(final MouseEvent e) {
					if (isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
						pressed = true;
						repaint();
					}
				}

				@Override
				public void mouseReleased(final MouseEvent e) {
					if (e.getButton() == MouseEvent.BUTTON1 && pressed) {
						pressed = false;
						repaint();
						if (isEnabled() && contains(e.getPoint())) {
							onUserClick.run();
						}
					}
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					if (pressed) {
						pressed = false;
						repaint();
					}
				}
			});
		}

		void setSelected(final boolean selected) {
			this.selected = selected;
			repaint();
		}

		@Override
		public void setEnabled(final boolean enabled) {
			super.setEnabled(enabled);
			setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
					: Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			repaint();
		}

		@Override
		protected void paintComponent(final Graphics g) {
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final Font plainFont = g2.getFont().deriveFont(Font.PLAIN);
			g2.setFont(plainFont);

			final int w = getWidth();
			final int h = getHeight();
			final RoundRectangle2D.Double rounded = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 5, 5);

			final Color fill;
			if (!isEnabled()) {
				fill = ColorLabel.BASE_BG_3.color();
			} else if (pressed || selected) {
				fill = ColorLabel.BASE_HIGHLIGHT.color();
			} else {
				fill = ColorLabel.BASE_BUTTON.color();
			}
			g2.setColor(fill);
			g2.fill(rounded);

			final Color textColor;
			if (!isEnabled()) {
				textColor = ColorLabel.BASE_DARK_TEXT.color();
			} else if (pressed || selected) {
				final Color accent = ColorLabel.BASE_HIGHLIGHT.color();
				final double lum = (0.299 * accent.getRed() + 0.587 * accent.getGreen() + 0.114 * accent.getBlue()) / 255;
				textColor = lum > 0.75 ? new Color(20, 20, 20) : Color.WHITE;
			} else {
				textColor = ColorLabel.BASE_TEXT.color();
			}
			g2.setColor(textColor);
			final FontMetrics fm = g2.getFontMetrics();
			final int tx = (int) ((w - fm.getStringBounds(text, g2).getWidth()) / 2);
			final int ty = (int) ((h - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
			g2.drawString(text, tx, ty);

			g2.dispose();
		}
	}

	private static final class TransportGlyphButton extends JPanel {
		private static final long serialVersionUID = 1L;

		enum Mode {
			PLAY_PAUSE, STOP
		}

		private final Mode mode;
		private boolean pausedVisual;
		private Runnable onClick;
		private boolean pressed;

		TransportGlyphButton(final Mode mode) {
			super(null);
			this.mode = mode;
			setOpaque(false);
			setFocusable(false);
			setRequestFocusEnabled(false);
			ComponentUtils.setComponentSize(this, BTN_W, ROW_H);

			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(final MouseEvent e) {
					if (isEnabled() && e.getButton() == MouseEvent.BUTTON1) {
						pressed = true;
						repaint();
					}
				}

				@Override
				public void mouseReleased(final MouseEvent e) {
					if (e.getButton() == MouseEvent.BUTTON1 && pressed) {
						pressed = false;
						repaint();
						if (isEnabled() && contains(e.getPoint()) && onClick != null) {
							onClick.run();
						}
					}
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					if (pressed) {
						pressed = false;
						repaint();
					}
				}
			});
		}

		void setOnClick(final Runnable onClick) {
			this.onClick = onClick;
		}

		void setPausedVisual(final boolean paused) {
			this.pausedVisual = paused;
			repaint();
		}

		@Override
		public void setEnabled(final boolean enabled) {
			super.setEnabled(enabled);
			setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
					: Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			repaint();
		}

		@Override
		protected void paintComponent(final Graphics g) {
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			final int w = getWidth();
			final int h = getHeight();

			final Color fill;
			if (!isEnabled()) {
				fill = ColorLabel.BASE_BG_2.color();
			} else if (pressed) {
				fill = ColorLabel.BASE_HIGHLIGHT.color();
			} else {
				fill = ColorLabel.BASE_BUTTON.color();
			}
			g2.setColor(fill);
			g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 5, 5));

			final Color glyphColor = isEnabled() ? ColorLabel.BASE_TEXT.color() : ColorLabel.BASE_DARK_TEXT.color();
			g2.setColor(glyphColor);

			if (mode == Mode.STOP) {
				paintStop(g2, w, h);
			} else if (pausedVisual) {
				paintPause(g2, w, h);
			} else {
				paintPlay(g2, w, h);
			}

			g2.dispose();
		}

		private static void paintPlay(final Graphics2D g, final int w, final int h) {
			final int m = 6;
			final int s = Math.min(w, h) - m;
			final int x0 = (w - s) / 2;
			final int y0 = (h - s) / 2;
			final Polygon p = new Polygon();
			p.addPoint(x0, y0);
			p.addPoint(x0 + s, y0 + s / 2);
			p.addPoint(x0, y0 + s);
			g.fill(p);
		}

		private static void paintPause(final Graphics2D g, final int w, final int h) {
			final int barW = 3;
			final int gap = 3;
			final int total = barW + gap + barW;
			final int x0 = (w - total) / 2;
			final int y0 = (h - 10) / 2;
			final int bh = 10;
			g.fillRect(x0, y0, barW, bh);
			g.fillRect(x0 + barW + gap, y0, barW, bh);
		}

		private static void paintStop(final Graphics2D g, final int w, final int h) {
			final int s = 8;
			final int x0 = (w - s) / 2;
			final int y0 = (h - s) / 2;
			g.fillRect(x0, y0, s, s);
		}
	}
}
