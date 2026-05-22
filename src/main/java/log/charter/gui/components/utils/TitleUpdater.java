package log.charter.gui.components.utils;

import java.awt.Color;

import javax.swing.SwingUtilities;

import log.charter.CharterMain;
import log.charter.data.ChartData;
import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.Localization.Label;
import log.charter.data.song.Arrangement;
import log.charter.data.song.vocals.VocalPath;
import log.charter.data.undoSystem.UndoSystem;
import log.charter.gui.CharterFrame;
import log.charter.gui.lookAndFeel.CharterCheckBox;
import log.charter.gui.lookAndFeel.CharterRadioButton;
import log.charter.services.editModes.EditMode;
import log.charter.services.editModes.ModeManager;

public class TitleUpdater {
	private static final Color DEFAULT_ACCENT = new Color(53, 116, 240);

	private ChartData chartData;
	private CharterFrame charterFrame;
	private ModeManager modeManager;
	private UndoSystem undoSystem;

	private Color accentForMode(final EditMode mode) {
		return switch (mode) {
			case GUITAR -> switch (chartData.currentArrangement().arrangementType) {
				case Lead -> new Color(230, 140, 30);
				case Combo -> new Color(200, 170, 20);
				case Rhythm -> new Color(60, 190, 60);
				case Bass -> new Color(40, 130, 230);
			};
			case VOCALS -> {
				final Color c = chartData.currentVocals().color;
				yield new Color((int) (c.getRed() * 0.9), (int) (c.getGreen() * 0.9), (int) (c.getBlue() * 0.9));
			}
			default -> DEFAULT_ACCENT;
		};
	}

	private static Color contrastColor(final Color accent) {
		final double lum = (0.299 * accent.getRed() + 0.587 * accent.getGreen() + 0.114 * accent.getBlue()) / 255;
		return lum > 0.5 ? new Color(20, 20, 20) : Color.WHITE;
	}

	private void updateAccentColor() {
		final Color accent = accentForMode(modeManager.getMode());
		final Color icon = contrastColor(accent);
		ColorLabel.BASE_HIGHLIGHT.setColor(accent);
		CharterRadioButton.selectColor = accent;
		CharterRadioButton.iconColor = icon;
		CharterRadioButton.install();
		CharterCheckBox.iconColor = icon;
		CharterCheckBox.install();
		SwingUtilities.invokeLater(charterFrame::repaint);
	}

	private String getSongData() {
		return chartData.songChart.artistName() + " - " + chartData.songChart.title();
	}

	private String getVocalPathTitlePart() {
		final VocalPath vocalPath = chartData.currentVocals();
		return vocalPath.getName(chartData.currentVocals);
	}

	private String getArrangementTitlePart() {
		final Arrangement arrangement = chartData.currentArrangement();
		final String arrangementName = arrangement.getTypeNameLabel(chartData.currentArrangement);
		final String tuning = arrangement.getTuningName("%s - %s");

		return "%s (%s)".formatted(arrangementName, tuning);
	}

	private String modeInfo() {
		return switch (modeManager.getMode()) {
			case TEMPO_MAP -> getSongData() + " : Tempo map";
			case VOCALS -> getSongData() + " : " + getVocalPathTitlePart();
			case GUITAR -> getSongData() + " : " + getArrangementTitlePart();
			case EMPTY -> Label.NO_PROJECT.label();
			default -> "Surprise mode! (contact dev for fix)";
		};
	}

	private String addUnsavedStatus(final String title) {
		if (undoSystem.isSaved()) {
			return title;
		}

		return title + "*";
	}

	public void updateTitle() {
		updateAccentColor();

		String title = "%s : %s".formatted(CharterMain.TITLE, modeInfo());
		title = addUnsavedStatus(title);

		if (title.equals(charterFrame.getTitle())) {
			return;
		}

		charterFrame.setTitle(title);
		charterFrame.validate();
	}

}
