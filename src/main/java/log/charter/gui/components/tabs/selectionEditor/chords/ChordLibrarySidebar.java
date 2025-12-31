package log.charter.gui.components.tabs.selectionEditor.chords;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import log.charter.data.ChordLibrary;
import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.Localization.Label;
import log.charter.data.song.ChordTemplate;
import log.charter.util.collections.HashMap2;

/**
 * A scrollable sidebar panel that displays chord library items. Updates
 * dynamically as the user sketches a chord pattern, filtering to show matching
 * chords.
 */
public class ChordLibrarySidebar extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final int SIDEBAR_WIDTH = 190;

	private final ChordLibrary library;
	private final Consumer<ChordTemplate> onChordSelected;
	private int strings = 6;

	private final JPanel contentPanel;
	private final JScrollPane scrollPane;
	private final JLabel headerLabel;
	private final JLabel statusLabel;

	public ChordLibrarySidebar(final Consumer<ChordTemplate> onChordSelected) {
		this.library = ChordLibrary.getInstance();
		this.onChordSelected = onChordSelected;

		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(SIDEBAR_WIDTH, 300));
		setMinimumSize(new Dimension(SIDEBAR_WIDTH, 100));
		setBackground(ColorLabel.BASE_BG_1.color());
		setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ColorLabel.BASE_BG_4.color()));

		// Header
		headerLabel = new JLabel(Label.CHORD_LIBRARY.label());
		headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		headerLabel.setForeground(ColorLabel.BASE_TEXT.color());
		headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
		headerLabel.setBorder(new EmptyBorder(8, 4, 8, 4));
		headerLabel.setOpaque(true);
		headerLabel.setBackground(ColorLabel.BASE_BG_2.color());
		add(headerLabel, BorderLayout.NORTH);

		// Content panel (scrollable)
		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorLabel.BASE_BG_1.color());
		contentPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

		scrollPane = new JScrollPane(contentPanel);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBackground(ColorLabel.BASE_BG_1.color());
		scrollPane.getViewport().setBackground(ColorLabel.BASE_BG_1.color());
		add(scrollPane, BorderLayout.CENTER);

		// Status label (shown when empty or no matches)
		statusLabel = new JLabel();
		statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
		statusLabel.setForeground(ColorLabel.BASE_TEXT.color().darker());
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setBorder(new EmptyBorder(20, 4, 20, 4));
		statusLabel.setAlignmentX(CENTER_ALIGNMENT);

		// Initial population
		updateChordList(null);
	}

	public void setStrings(final int strings) {
		this.strings = strings;
	}

	/**
	 * Updates the displayed chord list, filtering by the given partial fret
	 * pattern.
	 * 
	 * @param partialFrets the current fret pattern to filter by, or null to show
	 *                     all
	 */
	public void updateChordList(final HashMap2<Integer, Integer> partialFrets) {
		contentPanel.removeAll();

		final List<ChordTemplate> matchingChords = library.findMatchingChords(partialFrets);

		if (library.size() == 0) {
			statusLabel.setText(Label.CHORD_LIBRARY_EMPTY.label());
			contentPanel.add(statusLabel);
		} else if (matchingChords.isEmpty()) {
			statusLabel.setText(Label.CHORD_LIBRARY_NO_MATCHES.label());
			contentPanel.add(statusLabel);
		} else {
			for (final ChordTemplate chord : matchingChords) {
				final ChordLibraryItem item = new ChordLibraryItem(chord, strings, 
						onChordSelected, 
						() -> removeChord(chord, partialFrets));
				item.setAlignmentX(LEFT_ALIGNMENT);
				contentPanel.add(item);
				contentPanel.add(Box.createRigidArea(new Dimension(0, 4)));
			}
		}

		contentPanel.revalidate();
		contentPanel.repaint();
		scrollPane.revalidate();
	}

	private void removeChord(final ChordTemplate chord, final HashMap2<Integer, Integer> currentFilter) {
		library.removeChord(chord);
		updateChordList(currentFilter);
	}

	/**
	 * Refreshes the sidebar display.
	 */
	public void refresh() {
		updateChordList(null);
	}

	/**
	 * Gets the chord library instance.
	 */
	public ChordLibrary getLibrary() {
		return library;
	}
}
