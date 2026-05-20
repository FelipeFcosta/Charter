package log.charter.gui.components.liveLyrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.services.CharterContext.Initiable;
import log.charter.services.data.LiveLyricsHandler;
import log.charter.services.data.LiveLyricsHandler.DisplaySyllable;
import log.charter.services.data.LiveLyricsHandler.SyllableState;

public class LiveLyricsPanel extends JPanel implements Initiable {
	private static final long serialVersionUID = 1L;

	private static final Color COLOR_CURRENT_BG         = new Color(85, 85, 85);
	private static final Color COLOR_CURRENT_PRESSED_BG = new Color(50, 50, 50);
	private static final Color COLOR_CURRENT_FG         = Color.WHITE;
	private static final Color COLOR_SAME_LINE_FG       = new Color(210, 210, 210);
	private static final Color COLOR_DIVIDER            = new Color(60, 60, 60);

	private static final int CHIP_WIDTH  = 130;
	private static final int CHIP_HEIGHT = 26;

	private LiveLyricsHandler liveLyricsHandler;

	private final JLabel        instructionLabel = new JLabel("", JLabel.LEFT);
	private final JToggleButton liveToggle       = new JToggleButton("Tap");
	private final JButton       backButton       = new JButton("↩");
	private final JPanel        chipsPanel       = new JPanel();

	private boolean chipBeingClicked = false;

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(100, 48);
	}

	@Override
	public void init() {
		setLayout(new BorderLayout(0, 0));
		setBackground(ColorLabel.BASE_BG_2.color());
		setVisible(false);

		// --- instruction label (tiny, USC-style hint) ---
		instructionLabel.setFont(instructionLabel.getFont().deriveFont(Font.ITALIC, 10f));
		instructionLabel.setForeground(new Color(110, 110, 110));
		instructionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
		add(instructionLabel, BorderLayout.NORTH);

		// --- main control row ---
		final JPanel controlRow = new JPanel(new BorderLayout(0, 0));
		controlRow.setOpaque(false);
		controlRow.setBorder(BorderFactory.createEmptyBorder(0, 2, 3, 2));

		final JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		buttonsPanel.setOpaque(false);

		liveToggle.setFocusable(false);
		liveToggle.setFont(liveToggle.getFont().deriveFont(Font.PLAIN, 11f));
		buttonsPanel.add(liveToggle);

		backButton.setFocusable(false);
		backButton.setFont(backButton.getFont().deriveFont(Font.PLAIN, 16f));
		backButton.setPreferredSize(new Dimension(40, CHIP_HEIGHT));
		buttonsPanel.add(backButton);

		controlRow.add(buttonsPanel, BorderLayout.WEST);

		chipsPanel.setOpaque(false);
		controlRow.add(chipsPanel, BorderLayout.CENTER);

		add(controlRow, BorderLayout.CENTER);

		liveToggle.addActionListener(e -> {
			liveLyricsHandler.toggleEnabled();
			refreshView();
		});
		backButton.addActionListener(e -> liveLyricsHandler.goBack());

		// Persistent click-to-tap on chipsPanel — survives chip rebuilds because
		// chipsPanel itself is never removed from the hierarchy.
		chipsPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				if (isOverCurrentChip(e.getX(), e.getY())
						&& liveLyricsHandler.shouldCaptureSpace()
						&& !liveLyricsHandler.hasCurrentPlacementActive()) {
					chipBeingClicked = true;
					liveLyricsHandler.handleSpacePressed();
				}
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				if (chipBeingClicked) {
					chipBeingClicked = false;
					liveLyricsHandler.handleSpaceReleased();
				}
			}
		});

		liveToggle.setSelected(false);
		buildEmptyRow();

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(final ComponentEvent e) {
				refreshView();
			}
		});
	}

	private boolean isOverCurrentChip(final int x, final int y) {
		if (chipsPanel.getComponentCount() == 0) {
			return false;
		}
		final Rectangle bounds = chipsPanel.getComponent(0).getBounds();
		return bounds.contains(x, y);
	}

	private JLabel makeChip(final String text, final boolean isLast, final boolean isCurrent) {
		final JLabel label = new JLabel(text, JLabel.CENTER);
		label.setFont(label.getFont().deriveFont(isCurrent ? Font.BOLD : Font.PLAIN, 14f));
		label.setPreferredSize(new Dimension(CHIP_WIDTH, CHIP_HEIGHT));

		final int rightBorder = isLast ? 0 : 1;

		if (isCurrent) {
			final Color bg = chipBeingClicked ? COLOR_CURRENT_PRESSED_BG : COLOR_CURRENT_BG;
			label.setOpaque(true);
			label.setBackground(bg);
			label.setForeground(COLOR_CURRENT_FG);
			label.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(1, 1, 1, rightBorder == 0 ? 1 : 0, new Color(120, 120, 120)),
					BorderFactory.createEmptyBorder(2, 6, 2, 6)));
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		} else {
			label.setOpaque(false);
			label.setForeground(COLOR_SAME_LINE_FG);
			label.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 0, rightBorder, COLOR_DIVIDER),
					BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		}

		return label;
	}

	private void buildEmptyRow() {
		chipsPanel.removeAll();
		chipsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		final JLabel placeholder = new JLabel("Paste syllabified lyrics in the Text tab");
		placeholder.setForeground(new Color(90, 90, 90));
		placeholder.setFont(placeholder.getFont().deriveFont(Font.ITALIC, 13f));
		chipsPanel.add(placeholder);
	}

	private void buildSyllableRow(final List<DisplaySyllable> syllables) {
		chipsPanel.removeAll();
		chipsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

		for (int i = 0; i < syllables.size(); i++) {
			final DisplaySyllable ds = syllables.get(i);
			final boolean isCurrent = ds.state == SyllableState.CURRENT;
			final boolean isLast    = i == syllables.size() - 1;
			chipsPanel.add(makeChip(ds.text, isLast, isCurrent));
		}
	}

	public void refreshView() {
		liveToggle.setSelected(liveLyricsHandler.isEnabled());
		instructionLabel.setText(liveLyricsHandler.statusText());

		final int chipAreaWidth = chipsPanel.getWidth() > 0 ? chipsPanel.getWidth() : (getWidth() - 44);
		final int maxChips = Math.max(1, chipAreaWidth / CHIP_WIDTH);
		final List<DisplaySyllable> syllables = liveLyricsHandler.getDisplaySyllables(maxChips);
		if (syllables.isEmpty()) {
			buildEmptyRow();
		} else {
			buildSyllableRow(syllables);
		}

		chipsPanel.revalidate();
		chipsPanel.repaint();
	}
}
