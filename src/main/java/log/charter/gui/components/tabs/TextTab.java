package log.charter.gui.components.tabs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.gui.components.containers.CharterScrollPane;

public class TextTab extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final Color COLOR_STATUS_IDLE    = new Color(120, 120, 120);
	private static final Color COLOR_STATUS_VALID   = new Color(60, 180, 60);
	private static final Color COLOR_STATUS_INVALID = new Color(210, 60, 60);
	private static final Highlighter.HighlightPainter ERROR_PAINTER =
			new DefaultHighlighter.DefaultHighlightPainter(new Color(220, 60, 60, 80));

	private final JTextArea textArea;
	private final JPanel    footer;
	private final JLabel    statusLabel;
	private final JButton   revertButton;
	private final JButton   applyButton;

	private Runnable applyListener;
	private Runnable revertListener;

	public TextTab() {
		this(new JTextArea(1000, 1000));
	}

	private TextTab(final JTextArea textArea) {
		super(new BorderLayout());
		this.textArea = textArea;

		final CharterScrollPane scrollPane = new CharterScrollPane(textArea);
		add(scrollPane, BorderLayout.CENTER);

		statusLabel = new JLabel("Applied");
		statusLabel.setForeground(COLOR_STATUS_IDLE);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

		revertButton = new JButton("Cancel");
		revertButton.setEnabled(false);
		revertButton.setFocusable(false);
		revertButton.addActionListener(e -> { if (revertListener != null) revertListener.run(); });

		applyButton = new JButton("Apply");
		applyButton.setEnabled(false);
		applyButton.setFocusable(false);
		applyButton.addActionListener(e -> { if (applyListener != null) applyListener.run(); });

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
		buttonPanel.setOpaque(false);
		buttonPanel.add(revertButton);
		buttonPanel.add(applyButton);

		footer = new JPanel(new BorderLayout());
		footer.add(statusLabel, BorderLayout.CENTER);
		footer.add(buttonPanel, BorderLayout.EAST);

		add(footer, BorderLayout.SOUTH);

		applyColors();
	}

	private void applyColors() {
		final Color bg = ColorLabel.BASE_BG_2.color();
		textArea.setBackground(bg);
		textArea.setForeground(ColorLabel.BASE_TEXT.color());
		textArea.setCaretColor(ColorLabel.BASE_TEXT.color());
		footer.setBackground(bg);
		footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, bg.darker()));
	}

	@Override
	public void paint(final Graphics g) {
		// Only update text area colors on paint (theme may change at runtime).
		// Never touch footer here: setBorder() calls revalidate() which is unsafe mid-paint.
		final Color bg = ColorLabel.BASE_BG_2.color();
		textArea.setBackground(bg);
		textArea.setForeground(ColorLabel.BASE_TEXT.color());
		textArea.setCaretColor(ColorLabel.BASE_TEXT.color());
		super.paint(g);
	}

	public void addTextChangeListener(final Runnable listener) {
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent e) { listener.run(); }

			@Override
			public void removeUpdate(final DocumentEvent e) { listener.run(); }

			@Override
			public void changedUpdate(final DocumentEvent e) { listener.run(); }
		});
	}

	public void addApplyListener(final Runnable listener) {
		this.applyListener = listener;
	}

	public void addRevertListener(final Runnable listener) {
		this.revertListener = listener;
	}

	public String getText() {
		return textArea.getText();
	}

	public void setText(final String text) {
		textArea.setText(text);
	}

	public void setStatus(final String message, final boolean valid, final boolean hasPending) {
		statusLabel.setText(message);
		if (!hasPending) {
			statusLabel.setForeground(COLOR_STATUS_IDLE);
		} else if (valid) {
			statusLabel.setForeground(COLOR_STATUS_VALID);
		} else {
			statusLabel.setForeground(COLOR_STATUS_INVALID);
		}
		applyButton.setEnabled(hasPending && valid);
		revertButton.setEnabled(hasPending);
	}

	public void clearHighlights() {
		textArea.getHighlighter().removeAllHighlights();
	}

	public void addErrorHighlight(final int start, final int end) {
		try {
			textArea.getHighlighter().addHighlight(start, end, ERROR_PAINTER);
		} catch (final BadLocationException e) {
			// offset out of range — ignore
		}
	}
}
