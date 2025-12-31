package log.charter.gui.components.tabs.selectionEditor.chords;

import static log.charter.data.config.ChartPanelColors.getStringBasedColor;
import static log.charter.util.Utils.getStringPosition;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.ChartPanelColors.StringColorLabelType;
import log.charter.data.config.values.InstrumentConfig;
import log.charter.data.song.ChordTemplate;

/**
 * A compact visual representation of a chord template for use in the chord
 * library sidebar. Displays chord shape, name, frets, and fingers in a
 * condensed format.
 */
public class ChordLibraryItem extends JComponent {
	private static final long serialVersionUID = 1L;

	private static final int SHAPE_NOTE_SIZE = 5;
	private static final int ITEM_HEIGHT = 48;
	private static final int PADDING = 4;
	private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
	private static final Font DETAIL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 10);

	private final ChordTemplate template;
	private final int strings;
	private final BufferedImage chordShape;

	private boolean hovered = false;
	private boolean removeHovered = false;

	public ChordLibraryItem(final ChordTemplate template, final int strings,
			final Consumer<ChordTemplate> onSelect, final Runnable onRemove) {
		this.template = template;
		this.strings = strings;
		this.chordShape = generateChordShapeImage(template, strings);

		setPreferredSize(new Dimension(180, ITEM_HEIGHT));
		setMinimumSize(new Dimension(150, ITEM_HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_HEIGHT));
		setBorder(new EmptyBorder(PADDING, PADDING, PADDING, PADDING));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(final MouseEvent e) {
				hovered = false;
				removeHovered = false;
				repaint();
			}

			@Override
			public void mouseClicked(final MouseEvent e) {
				if (isInRemoveZone(e.getX(), e.getY())) {
					if (onRemove != null) {
						onRemove.run();
					}
				} else {
					if (onSelect != null) {
						onSelect.accept(template);
					}
				}
			}

			@Override
			public void mouseMoved(final MouseEvent e) {
				final boolean wasRemoveHovered = removeHovered;
				removeHovered = isInRemoveZone(e.getX(), e.getY());
				if (wasRemoveHovered != removeHovered) {
					repaint();
				}
			}
		});

		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(final MouseEvent e) {
				final boolean wasRemoveHovered = removeHovered;
				removeHovered = isInRemoveZone(e.getX(), e.getY());
				if (wasRemoveHovered != removeHovered) {
					repaint();
				}
			}
		});
	}

	private boolean isInRemoveZone(final int x, final int y) {
		final int removeX = getWidth() - 18;
		final int removeY = 4;
		return x >= removeX && x <= removeX + 14 && y >= removeY && y <= removeY + 14;
	}

	private static BufferedImage generateChordShapeImage(final ChordTemplate template, final int strings) {
		final int[] frets = new int[strings];
		for (int i = 0; i < strings; i++) {
			frets[i] = -1;
		}

		int minNonzeroFret = InstrumentConfig.frets;
		int maxNonzeroFret = 0;

		for (final Entry<Integer, Integer> entry : template.frets.entrySet()) {
			final int string = entry.getKey();
			final int fret = entry.getValue();

			if (string < strings) {
				frets[string] = fret;
			}

			if (fret > 0) {
				minNonzeroFret = Math.min(minNonzeroFret, fret);
				maxNonzeroFret = Math.max(maxNonzeroFret, fret);
			}
		}

		if (maxNonzeroFret < minNonzeroFret) {
			minNonzeroFret = maxNonzeroFret = 0;
		}

		final int fretRange = Math.max(4, maxNonzeroFret - minNonzeroFret + 1);
		final int width = SHAPE_NOTE_SIZE * fretRange + 4;
		final int height = strings * SHAPE_NOTE_SIZE + 2;

		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		for (int string = 0; string < strings; string++) {
			final int fret = frets[string];
			if (fret == -1) {
				continue;
			}

			g.setColor(getStringBasedColor(StringColorLabelType.NOTE, string, strings));

			final int y = getStringPosition(string, strings) * SHAPE_NOTE_SIZE + 1;
			if (fret == 0) {
				// Open string - draw horizontal line
				g.fillRect(0, y + SHAPE_NOTE_SIZE / 2 - 1, width, 2);
			} else {
				// Fretted note - draw circle
				final int x = (fret - minNonzeroFret) * SHAPE_NOTE_SIZE + 2;
				g.fillOval(x, y, SHAPE_NOTE_SIZE - 1, SHAPE_NOTE_SIZE - 1);
			}
		}

		g.dispose();
		return image;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// Background
		if (hovered) {
			g2.setColor(ColorLabel.BASE_BG_4.color());
		} else {
			g2.setColor(ColorLabel.BASE_BG_2.color());
		}
		g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

		// Border
		g2.setColor(ColorLabel.BASE_BG_4.color());
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

		// Chord shape
		final int shapeY = (getHeight() - chordShape.getHeight()) / 2;
		g2.drawImage(chordShape, PADDING, shapeY, null);

		// Text content
		final int textX = PADDING + chordShape.getWidth() + 6;
		g2.setColor(ColorLabel.BASE_TEXT.color());

		// Chord name
		g2.setFont(NAME_FONT);
		String name = template.chordName;
		if (name == null || name.isEmpty()) {
			name = "(unnamed)";
		}
		if (template.arpeggio) {
			name += " (arp)";
		}
		// Truncate if too long
		if (name.length() > 15) {
			name = name.substring(0, 14) + "…";
		}
		g2.drawString(name, textX, 14);

		// Frets
		g2.setFont(DETAIL_FONT);
		g2.setColor(ColorLabel.BASE_TEXT.color().darker());
		final String frets = "F: " + template.getTemplateFrets(strings);
		g2.drawString(frets, textX, 26);

		// Fingers
		final String fingers = "H: " + template.getTemplateFingers(strings);
		g2.drawString(fingers, textX, 38);

		// Remove button (X)
		if (hovered) {
			final int removeX = getWidth() - 18;
			final int removeY = 4;

			if (removeHovered) {
				g2.setColor(new Color(200, 60, 60));
			} else {
				g2.setColor(ColorLabel.BASE_TEXT.color().darker());
			}
			g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
			g2.drawString("×", removeX + 2, removeY + 12);
		}

		g2.dispose();
	}

	public ChordTemplate getTemplate() {
		return template;
	}
}
