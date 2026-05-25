package log.charter.services.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import log.charter.data.ChartData;
import log.charter.io.Logger;
import log.charter.data.song.position.FractionalPosition;
import log.charter.data.song.position.fractional.IConstantFractionalPosition;
import log.charter.data.song.vocals.Vocal;
import log.charter.data.song.vocals.Vocal.VocalFlag;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.components.liveLyrics.LiveLyricsPanel;
import log.charter.gui.components.tabs.TextTab;
import log.charter.services.CharterContext.Initiable;
import log.charter.services.data.fixers.ArrangementFixer;
import log.charter.services.editModes.EditMode;
import log.charter.services.editModes.ModeManager;

public class LiveLyricsHandler implements Initiable {

	public enum SyllableState { CURRENT, SAME_LINE, NEXT_LINE_PREVIEW }

	public static final class DisplaySyllable {
		public final String text;
		public final SyllableState state;

		public DisplaySyllable(final String text, final SyllableState state) {
			this.text = text;
			this.state = state;
		}
	}

	private static final class LyricToken {
		private final String text;
		private VocalFlag flag;
		private boolean lineBreakAfter;

		private LyricToken(final String text, final VocalFlag flag) {
			this.text = text;
			this.flag = flag;
		}
	}

	private static final class ActiveDraft {
		private final Vocal vocal;
		private final FractionalPosition minimumEnd;

		private ActiveDraft(final Vocal vocal, final FractionalPosition minimumEnd) {
			this.vocal = vocal;
			this.minimumEnd = minimumEnd;
		}
	}

	private ArrangementFixer arrangementFixer;
	private ChartData chartData;
	private ChartTimeHandler chartTimeHandler;
	private LiveLyricsPanel liveLyricsPanel;
	private ModeManager modeManager;
	private TextTab textTab;
	private UndoSystem undoSystem;

	private final List<LyricToken> tokens = new ArrayList<>();
	private final Map<Integer, Vocal> tokenIndexToVocal = new HashMap<>();
	private String sourceText = "";
	private String pendingText = null; // non-null when user has unsaved edits in the text tab
	private int currentTokenIndex = 0;
	private boolean enabled = false;
	private volatile ActiveDraft activeDraft = null; // volatile: read by frame thread, written by EDT
	private volatile boolean syncScheduled = false;  // volatile: written by frame thread, cleared by EDT
	private boolean updatingTextFromVocals = false;

	@Override
	public void init() {
		if (textTab != null) {
			textTab.addTextChangeListener(this::onTextTabChanged);
			textTab.addApplyListener(this::applyPendingText);
			textTab.addRevertListener(this::revertPendingText);
			updateTextTabStatus();
		}
	}

	private void onTextTabChanged() {
		if (updatingTextFromVocals) {
			return;
		}
		final String newText = textTab.getText();
		pendingText = newText.equals(sourceText) ? null : newText;
		// Defer UI updates until after the document operation fully completes.
		// During a select-all + paste, Swing fires removeUpdate then insertUpdate
		// synchronously; modifying Swing components (highlights, buttons) from
		// within those callbacks corrupts the layout cycle.
		SwingUtilities.invokeLater(this::updateTextTabStatus);
	}

	private void updateTextTabStatus() {
		if (textTab == null) {
			return;
		}
		textTab.clearHighlights();
		if (pendingText == null) {
			textTab.setStatus("Applied", true, false);
			return;
		}
		final List<int[]> errors = validateLyricsText(pendingText);
		for (final int[] range : errors) {
			textTab.addErrorHighlight(range[0], range[1]);
		}
		if (errors.isEmpty()) {
			textTab.setStatus("Valid — click Apply to use", true, true);
		} else {
			textTab.setStatus(errors.size() + " error(s) — fix before applying", false, true);
		}
	}

	private static List<int[]> validateLyricsText(final String text) {
		final List<int[]> errors = new ArrayList<>();
		boolean atWordStart = true;
		int i = 0;
		while (i < text.length()) {
			final char c = text.charAt(i);
			if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
				atWordStart = true;
				i++;
				continue;
			}
			if (c == '-' || c == '+') {
				if (atWordStart) {
					errors.add(new int[]{ i, i + 1 });
					i++;
					continue;
				}
				if (i + 1 < text.length()) {
					final char next = text.charAt(i + 1);
					if (next == '-' || next == '+') {
						final int start = i;
						while (i < text.length() && (text.charAt(i) == '-' || text.charAt(i) == '+')) {
							i++;
						}
						errors.add(new int[]{ start, i });
						continue;
					}
				}
			}
			atWordStart = false;
			i++;
		}
		return errors;
	}

	private void applyPendingText() {
		if (pendingText == null) {
			return;
		}
		sourceText = pendingText;
		pendingText = null;
		parseLyrics();
		rebuildTokenIndexToVocal();
		updateTextTabStatus();
		refreshPanel();
	}

	private void revertPendingText() {
		if (pendingText == null) {
			return;
		}
		pendingText = null;
		if (textTab != null) {
			updatingTextFromVocals = true;
			try {
				textTab.setText(sourceText);
			} finally {
				updatingTextFromVocals = false;
			}
		}
		updateTextTabStatus();
	}

	private void rebuildTokenIndexToVocal() {
		tokenIndexToVocal.clear();
		if (!isVocalsMode()) {
			return;
		}
		final var currentVocals = chartData.currentVocals();
		if (currentVocals == null) {
			return;
		}
		for (int i = 0; i < Math.min(currentVocals.vocals.size(), tokens.size()); i++) {
			tokenIndexToVocal.put(i, currentVocals.vocals.get(i));
		}
	}

	private void refreshPanel() {
		if (liveLyricsPanel != null) {
			liveLyricsPanel.refreshView();
		}
	}

	private static VocalFlag flagForSeparator(final char separator) {
		return separator == '+' ? VocalFlag.PHRASE_END : VocalFlag.WORD_PART;
	}

	private void addToken(final String tokenText, final char separator) {
		if (tokenText == null || tokenText.isBlank()) {
			return;
		}

		tokens.add(new LyricToken(tokenText, separator == 0 ? VocalFlag.NONE : flagForSeparator(separator)));
	}

	private void finalizeLine(final int lineStartIndex) {
		if (tokens.size() <= lineStartIndex) {
			return;
		}

		final LyricToken lastToken = tokens.get(tokens.size() - 1);
		lastToken.lineBreakAfter = true;
		if (lastToken.flag == VocalFlag.NONE) {
			lastToken.flag = VocalFlag.PHRASE_END;
		}
	}

	private void parseLyrics() {
		tokens.clear();

		final String[] lines = sourceText.split("\\R", -1);
		for (final String line : lines) {
			final int lineStartIndex = tokens.size();
			if (line.isBlank()) {
				if (!tokens.isEmpty()) {
					tokens.get(tokens.size() - 1).lineBreakAfter = true;
				}
				continue;
			}

			for (final String rawWord : line.trim().split(" +")) {
				if (rawWord.isBlank()) {
					continue;
				}

				int start = 0;
				for (int i = 0; i <= rawWord.length(); i++) {
					if (i < rawWord.length() && rawWord.charAt(i) != '-' && rawWord.charAt(i) != '+') {
						continue;
					}

					final String part = rawWord.substring(start, i);
					final char separator = i < rawWord.length() ? rawWord.charAt(i) : 0;
					addToken(part, separator);
					start = i + 1;
				}
			}

			finalizeLine(lineStartIndex);
		}

		if (currentTokenIndex > tokens.size()) {
			currentTokenIndex = tokens.size();
		}
	}

	private LyricToken currentToken() {
		if (currentTokenIndex < 0 || currentTokenIndex >= tokens.size()) {
			return null;
		}

		return tokens.get(currentTokenIndex);
	}

	private String formatToken(final LyricToken token) {
		return token.text + switch (token.flag) {
			case PHRASE_END -> "+";
			case WORD_PART -> "-";
			default -> "";
		};
	}

	private static final int TAP_OFFSET_MS = 0;

	private FractionalPosition currentTapPosition() {
		return FractionalPosition.fromTime(chartData.beats(), chartTimeHandler.displayTime() + TAP_OFFSET_MS);
	}

	private boolean hasCurrentPlacement() {
		return activeDraft != null;
	}

	private boolean hasLyricsReady() {
		return !tokens.isEmpty() && currentTokenIndex < tokens.size();
	}

	private boolean isVocalsMode() {
		return modeManager.getMode() == EditMode.VOCALS && chartData.currentVocals() != null;
	}

	private FractionalPosition moveToNextAvailablePosition(final FractionalPosition position) {
		FractionalPosition nextPosition = position;
		while (true) {
			boolean occupied = false;
			for (final Vocal vocal : chartData.currentVocals().vocals) {
				if (vocal.position().equals(nextPosition)) {
					occupied = true;
					break;
				}
			}
			if (!occupied) {
				return nextPosition;
			}

			nextPosition = chartData.beats().addGrid(nextPosition, 1).toFraction(chartData.beats()).position();
		}
	}

	private void removeOverlappingVocal(final FractionalPosition cursor) {
		chartData.currentVocals().vocals.removeIf(vocal -> {
			if (vocal.position().compareTo(cursor) <= 0 && vocal.endPosition().compareTo(cursor) > 0) {
				tokenIndexToVocal.entrySet().removeIf(e -> e.getValue() == vocal);
				return true;
			}
			return false;
		});
	}

	private void startPlacement() {
		final LyricToken token = currentToken();
		if (token == null) {
			return;
		}

		final FractionalPosition cursor = currentTapPosition();
		undoSystem.addUndo();
		removeOverlappingVocal(cursor);

		// If this token is already placed, remove its existing vocal so tapping replaces rather than duplicates it
		final Vocal existingVocal = tokenIndexToVocal.get(currentTokenIndex);
		if (existingVocal != null) {
			chartData.currentVocals().vocals.remove(existingVocal);
			tokenIndexToVocal.remove(currentTokenIndex);
		}

		final FractionalPosition start = moveToNextAvailablePosition(cursor);
		final FractionalPosition minimumEnd = chartData.beats().getMinEndPositionAfter(start).toFraction(chartData.beats())
				.position();
		final Vocal vocal = new Vocal(start, minimumEnd, token.text, token.flag);

		chartData.currentVocals().vocals.add(vocal);
		chartData.currentVocals().vocals.sort(IConstantFractionalPosition::compareTo);
		tokenIndexToVocal.put(currentTokenIndex, vocal);

		activeDraft = new ActiveDraft(vocal, minimumEnd);
	}

	private void updateCurrentIndexFromCursor() {
		if (!isVocalsMode()) {
			return;
		}

		final FractionalPosition cursor = currentTapPosition();
		int count = 0;
		for (final Vocal vocal : chartData.currentVocals().vocals) {
			if (vocal.endPosition().compareTo(cursor) <= 0) {
				count++;
			} else {
				break;
			}
		}
		final int newIndex = Math.min(count, tokens.size());

		if (newIndex != currentTokenIndex) {
			currentTokenIndex = newIndex;
			refreshPanel();
		}
	}

	private void finishPlacement() {
		if (activeDraft == null) {
			return;
		}

		final FractionalPosition releasePosition = currentTapPosition();
		final FractionalPosition endPosition = releasePosition.compareTo(activeDraft.minimumEnd) < 0
				? activeDraft.minimumEnd
				: releasePosition;
		activeDraft.vocal.endPosition(endPosition);
		arrangementFixer.fixLengths(chartData.currentVocals().vocals);
		activeDraft = null;
		currentTokenIndex++;
		refreshPanel();
	}

	public boolean shouldCaptureSpace() {
		return enabled && isVocalsMode() && (hasLyricsReady() || hasCurrentPlacement());
	}

	public boolean hasCurrentPlacementActive() {
		return hasCurrentPlacement();
	}

	public void handleSpacePressed() {
		try {
			if (!shouldCaptureSpace() || hasCurrentPlacement()) {
				return;
			}

			startPlacement();
			refreshPanel();
		} catch (final Exception e) {
			Logger.error("Exception in LiveLyricsHandler.handleSpacePressed()", e);
		}
	}

	public void handleSpaceReleased() {
		try {
			finishPlacement();
		} catch (final Exception e) {
			Logger.error("Exception in LiveLyricsHandler.handleSpaceReleased()", e);
		}
	}

	private String buildSyncedText() {
		if (!isVocalsMode()) {
			return sourceText;
		}
		final var currentVocals = chartData.currentVocals();
		if (currentVocals == null || currentVocals.vocals.isEmpty()) {
			return sourceText;
		}

		final StringBuilder sb = new StringBuilder();
		boolean prevWasWordPart = false;

		for (int i = 0; i < tokens.size(); i++) {
			final Vocal vocal = tokenIndexToVocal.get(i);
			final boolean placed = vocal != null && currentVocals.vocals.contains(vocal);
			if (vocal != null && !placed) {
				// Vocal deleted from timeline — unplace the token but keep it in the output
				tokenIndexToVocal.remove(i);
			}

			if (placed) {
				final Vocal.VocalFlag flag = vocal.flag();
				if (!prevWasWordPart && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
					sb.append(' ');
				}
				sb.append(vocal.lyrics());
				if (flag == Vocal.VocalFlag.PHRASE_END) {
					sb.append('\n');
				}
				prevWasWordPart = flag == Vocal.VocalFlag.WORD_PART;
			} else {
				final LyricToken token = tokens.get(i);
				if (!prevWasWordPart && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
					sb.append(' ');
				}
				sb.append(formatToken(token));
				if (token.flag == VocalFlag.PHRASE_END) {
					sb.append('\n');
				}
				prevWasWordPart = token.flag == VocalFlag.WORD_PART;
			}
		}

		String result = sb.toString();
		while (result.endsWith("\n")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private void syncVocalsToText() {
		if (!isVocalsMode()) {
			return;
		}
		if (pendingText != null) {
			return;
		}
		// Clean up stale token→vocal mappings (e.g. after timeline deletions).
		// Never touch sourceText or the text tab — those are only changed by Apply.
		buildSyncedText();
		refreshPanel();
	}

	private void removeVocalsInGrowingRange(final ActiveDraft draft, final FractionalPosition end) {
		if (chartData.currentVocals() == null) {
			return;
		}
		chartData.currentVocals().vocals.removeIf(vocal -> {
			if (vocal != draft.vocal
					&& vocal.position().compareTo(draft.vocal.position()) > 0
					&& vocal.position().compareTo(end) <= 0) {
				// Remove from tokenIndexToVocal so the token appears as "unplaced"
				// (still visible in text/chips) rather than fully deleted.
				tokenIndexToVocal.entrySet().removeIf(e -> e.getValue() == vocal);
				return true;
			}
			return false;
		});
	}

	public void frame() {
		try {
			final ActiveDraft draft = activeDraft; // single volatile read — stable reference for this frame
			if (draft != null) {
				final FractionalPosition current = currentTapPosition();
				final FractionalPosition end = current.compareTo(draft.minimumEnd) < 0 ? draft.minimumEnd : current;
				draft.vocal.endPosition(end);
				if (!syncScheduled) {
					syncScheduled = true;
					SwingUtilities.invokeLater(() -> {
						try {
							syncScheduled = false;
							if (activeDraft == draft) {
								removeVocalsInGrowingRange(draft, end);
							}
						} catch (final Exception e) {
							Logger.error("Exception in LiveLyricsHandler growing-range sync", e);
						}
					});
				}
			} else if (!syncScheduled) {
				syncScheduled = true;
				SwingUtilities.invokeLater(() -> {
					try {
						syncScheduled = false;
						syncVocalsToText();
						if (enabled) {
							updateCurrentIndexFromCursor();
						}
					} catch (final Exception e) {
						Logger.error("Exception in LiveLyricsHandler vocals sync", e);
					}
				});
			}
		} catch (final Exception e) {
			Logger.error("Exception in LiveLyricsHandler.frame()", e);
		}
	}

	private void reloadFromTextTab() {
		sourceText = textTab != null ? textTab.getText() : "";
		parseLyrics();
	}

	public void resetQueue() {
		pendingText = null;
		reloadFromTextTab(); // adopt whatever is currently in the text tab as the source
		currentTokenIndex = 0;
		activeDraft = null;
		rebuildTokenIndexToVocal();
		updateTextTabStatus();
		refreshPanel();
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
		refreshPanel();
	}

	public void goBack() {
		if (activeDraft != null || currentTokenIndex <= 0) {
			return;
		}
		currentTokenIndex--;
		undoSystem.undo();
		refreshPanel();
	}

	public void toggleEnabled() {
		setEnabled(!enabled);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<DisplaySyllable> getDisplaySyllables(final int maxCount) {
		final List<DisplaySyllable> result = new ArrayList<>();

		if (tokens.isEmpty() || currentTokenIndex >= tokens.size()) {
			return result;
		}

		final int limit = Math.min(tokens.size(), currentTokenIndex + maxCount);
		for (int i = currentTokenIndex; i < limit; i++) {
			final SyllableState state = i == currentTokenIndex ? SyllableState.CURRENT : SyllableState.SAME_LINE;
			result.add(new DisplaySyllable(formatToken(tokens.get(i)), state));
		}

		return result;
	}

	public String statusText() {
		if (!enabled) {
			return "Live tapping is off";
		}
		if (!isVocalsMode()) {
			return "Switch to vocals mode to tap lyrics";
		}
		if (tokens.isEmpty()) {
			return "Paste syllabified lyrics in the Text tab";
		}
		if (currentTokenIndex >= tokens.size()) {
			return "Lyrics finished";
		}
		return "Tap the syllables while they are sung";
	}

}
