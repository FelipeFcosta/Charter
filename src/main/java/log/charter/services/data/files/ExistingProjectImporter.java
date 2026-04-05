package log.charter.services.data.files;

import static log.charter.gui.components.simple.LoadingDialog.doWithLoadingDialog;
import static log.charter.gui.components.utils.ComponentUtils.showPopup;
import static log.charter.io.rsc.xml.ChartProjectXStreamHandler.readChartProject;
import static log.charter.services.data.files.SongFilesBackuper.makeBackups;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import log.charter.data.ChartData;
import log.charter.data.config.Localization.Label;
import log.charter.data.song.Arrangement;
import log.charter.data.song.SongChart;
import log.charter.data.song.vocals.VocalPath;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.simple.ChartingTimerPanel;
import log.charter.gui.components.simple.LoadingDialog;
import log.charter.gui.components.tabs.TextTab;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.io.Logger;
import log.charter.io.rs.xml.RSXMLToArrangement;
import log.charter.io.rs.xml.song.SongArrangement;
import log.charter.io.rs.xml.song.SongArrangementXStreamHandler;
import log.charter.io.rs.xml.vocals.ArrangementVocals;
import log.charter.io.rs.xml.vocals.VocalsXStreamHandler;
import log.charter.io.rsc.xml.ChartProject;
import log.charter.util.RW;
import log.charter.services.audio.AudioHandler;
import log.charter.services.data.ChartingTimerHandler;
import log.charter.services.data.ChartTimeHandler;
import log.charter.services.data.ProjectAudioHandler;
import log.charter.sound.data.AudioData;
import log.charter.sound.utils.AudioGenerator;

public class ExistingProjectImporter {
	private AudioHandler audioHandler;
	private ChartData chartData;
	private ChartingTimerHandler chartingTimerHandler;
	private CharterFrame charterFrame;
	private ChartTimeHandler chartTimeHandler;
	private ChordTemplatesEditorTab chordTemplatesEditorTab;
	private ProjectAudioHandler projectAudioHandler;
	private TextTab textTab;
	private ChartingTimerPanel chartingTimerPanel;

	private ChartProject loadProjectFile(final File projectFileChosen) {
		final String name = projectFileChosen.getName().toLowerCase();
		if (!name.endsWith(".rscp")) {
			Logger.error("unsupported file: " + projectFileChosen.getName());
			showPopup(charterFrame, Label.UNSUPPORTED_FILE_TYPE);
			return null;
		}

		final ChartProject project;
		try {
			project = readChartProject(projectFileChosen);
			if (project.chartFormatVersion > 3) {
				Logger.error("project has wrong version " + project.chartFormatVersion);
				showPopup(charterFrame, Label.PROJECT_IS_NEWER_VERSION);
				return null;
			}
		} catch (final Exception e) {
			Logger.error("Error when reading project", e);
			showPopup(charterFrame, Label.MISSING_ARRANGEMENT_FILE, projectFileChosen.getAbsolutePath());
			return null;
		}

		return project;
	}

	private AudioData loadMusicData(final ChartProject project, final String dir) {
		final AudioData musicData = AudioData.readFile(new File(dir, project.musicFileName));
		if (musicData == null) {
			showPopup(charterFrame, Label.WRONG_MUSIC_FILE, project.musicFileName);
			return AudioGenerator.generateEmpty(0);
		}

		return musicData;
	}

	private static int rsXmlFileId(final File f) {
		try {
			return Integer.parseInt(f.getName().split("_")[0]);
		} catch (final NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	private File[] rsXmlFiles(final String dir) {
		final File rsXmlDir = new File(dir, "RS XML");
		if (!rsXmlDir.exists() || !rsXmlDir.isDirectory()) {
			return null;
		}

		final File[] xmlFiles = rsXmlDir.listFiles((d, name) -> name.endsWith("_RS2.xml"));
		if (xmlFiles == null || xmlFiles.length == 0) {
			return null;
		}

		Arrays.sort(xmlFiles, Comparator.comparingInt(ExistingProjectImporter::rsXmlFileId));
		return xmlFiles;
	}

	private void reimportArrangementsFromRSXML(final SongChart songChart, final String dir) {
		final File[] xmlFiles = rsXmlFiles(dir);
		if (xmlFiles == null) {
			return;
		}

		final List<Arrangement> arrangements = new ArrayList<>();

		for (final File xmlFile : xmlFiles) {
			if (xmlFile.getName().contains("_Vocals_")) {
				continue;
			}

			try {
				final SongArrangement songArrangement = SongArrangementXStreamHandler.readSong(xmlFile);
				arrangements.add(RSXMLToArrangement.toArrangement(songArrangement, songChart.beatsMap.immutable));
			} catch (final Exception e) {
				Logger.error("Couldn't reimport RS XML file: " + xmlFile.getName(), e);
			}
		}

		songChart.arrangements = arrangements;
	}

	private void reimportVocalsFromRSXML(final SongChart songChart, final String dir) {
		final File[] xmlFiles = rsXmlFiles(dir);
		if (xmlFiles == null) {
			return;
		}

		final List<VocalPath> vocalPaths = new ArrayList<>();

		for (final File xmlFile : xmlFiles) {
			if (!xmlFile.getName().contains("_Vocals_")) {
				continue;
			}

			try {
				final ArrangementVocals arrangementVocals = VocalsXStreamHandler
						.readVocals(RW.read(xmlFile, "UTF-8"));
				vocalPaths.add(new VocalPath(songChart.beatsMap.immutable, arrangementVocals));
			} catch (final Exception e) {
				Logger.error("Couldn't reimport vocals RS XML file: " + xmlFile.getName(), e);
			}
		}

		if (!vocalPaths.isEmpty()) {
			songChart.vocalPaths = vocalPaths;
		}
	}

	private void openInternal(final LoadingDialog loadingDialog, final String path) {
		loadingDialog.setProgress(0, Label.LOADING_PROJECT_FILE.label());

		final List<String> filesToBackup = new ArrayList<>();
		final File projectFileChosen = new File(path);
		final ChartProject project = loadProjectFile(projectFileChosen);
		if (project == null) {
			return;
		}
		loadingDialog.addProgress(Label.LOADING_MUSIC_FILE);

		filesToBackup.add(projectFileChosen.getName());
		filesToBackup.addAll(project.arrangementFiles);
		filesToBackup.add(SongFileHandler.vocalsFileName);

		final String dir = projectFileChosen.getParent() + File.separator;
		final AudioData musicData = loadMusicData(project, dir);
		if (musicData == null) {
			return;
		}
		loadingDialog.addProgress(Label.LOADING_ARRANGEMENTS);

		final SongChart songChart;
		try {
			songChart = new SongChart(project, dir);
		} catch (final Exception e) {
			showPopup(charterFrame, Label.COULDNT_LOAD_PROJECT, e.getMessage());
			return;
		}

		if (songChart.arrangements.isEmpty()) {
			reimportArrangementsFromRSXML(songChart, dir);
		}
		reimportVocalsFromRSXML(songChart, dir);

		final List<String> rsXmlFilesToBackup = new ArrayList<>();
		final File rsXmlDir = new File(dir, "RS XML");
		final File[] xmlFiles = rsXmlDir.listFiles((d, name) -> name.endsWith("_RS2.xml"));
		if (xmlFiles != null) {
			for (final File xmlFile : xmlFiles) {
				rsXmlFilesToBackup.add(xmlFile.getName());
			}
		}

		makeBackups(dir, filesToBackup, rsXmlFilesToBackup);

		chartData.setSong(dir, songChart, projectFileChosen.getName(), project.editMode, project.arrangement,
				project.level);
		chartTimeHandler.nextTime(project.time);
		projectAudioHandler.setAudio(musicData);
		projectAudioHandler.readStems();
		projectAudioHandler.selectStem(project.selectedStem);
		textTab.setText(project.text);

		chartingTimerHandler.loadFromProject(project.chartingTimeMs, project.chartingTimerSyncWithAudio);
		chartingTimerPanel.refresh();

		audioHandler.clear();
		chordTemplatesEditorTab.refreshTemplates();

		loadingDialog.addProgress(Label.LOADING_DONE);
	}

	public void open(final String path) {
		doWithLoadingDialog(charterFrame, 3, dialog -> openInternal(dialog, path), "open " + path);
	}
}
