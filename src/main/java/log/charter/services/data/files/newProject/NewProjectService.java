package log.charter.services.data.files.newProject;

import static log.charter.gui.components.utils.ComponentUtils.showPopup;
import static log.charter.util.FileUtils.cleanFileName;

import java.io.File;

import log.charter.data.ChartData;
import log.charter.data.config.Localization.Label;
import log.charter.data.config.values.PathsConfig;
import log.charter.data.song.SongChart;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.containers.SongFolderSelectPane;
import log.charter.gui.components.simple.ChartingTimerPanel;
import log.charter.gui.components.tabs.TextTab;
import log.charter.gui.components.tabs.chordEditor.ChordTemplatesEditorTab;
import log.charter.services.audio.AudioHandler;
import log.charter.services.data.ChartTimeHandler;
import log.charter.services.data.ChartingTimerHandler;
import log.charter.services.data.ProjectAudioHandler;
import log.charter.services.data.files.SongFileHandler;
import log.charter.sound.audioFormats.AudioFileMetadata;
import log.charter.sound.data.AudioData;

public class NewProjectService {
	private AudioHandler audioHandler;
	private ChartData chartData;
	private ChartTimeHandler chartTimeHandler;
	private ChartingTimerHandler chartingTimerHandler;
	private CharterFrame charterFrame;
	private ChordTemplatesEditorTab chordTemplatesEditorTab;
	private ProjectAudioHandler projectAudioHandler;
	private SongFileHandler songFileHandler;
	private TextTab textTab;
	private ChartingTimerPanel chartingTimerPanel;

	public String generateFolderName(final File songFile, final AudioFileMetadata metadata) {
		String defaultFolderName;
		if (metadata.artist.isBlank() && metadata.title.isBlank()) {
			final String songFileName = songFile.getName();
			defaultFolderName = songFileName.substring(0, songFileName.lastIndexOf('.'));
		} else {
			defaultFolderName = "%s - %s".formatted(metadata.artist.isBlank() ? "unknown artist" : metadata.artist, //
					metadata.title.isBlank() ? "unknown title" : metadata.title);
		}
		defaultFolderName = cleanFileName(defaultFolderName);

		return defaultFolderName;
	}

	public File chooseSongFolder(final String audioFileDirectory, final String defaultFolderName) {
		File songFolder = null;

		while (songFolder == null) {
			final SongFolderSelectPane songFolderSelectPane = new SongFolderSelectPane(charterFrame,
					PathsConfig.songsPath, audioFileDirectory, defaultFolderName);

			if (songFolderSelectPane.isAudioFolderChosen()) {
				return new File(audioFileDirectory);
			}

			String folderName = songFolderSelectPane.getFolderName();
			if (folderName == null || folderName.isBlank()) {
				return null;
			}
			folderName = cleanFileName(folderName);

			songFolder = new File(PathsConfig.songsPath, folderName);

			if (songFolder.exists()) {
				songFolder = null;
				showPopup(charterFrame, Label.FOLDER_EXISTS_CHOOSE_DIFFERENT);
				continue;
			}
			if (!songFolder.mkdir()) {
				songFolder = null;
				showPopup(charterFrame, Label.COULDNT_CREATE_FOLDER_CHOOSE_DIFFERENT);
				continue;
			}
		}

		return songFolder;
	}

	public void fillMetadata(final SongChart songChart, final File songFile, final AudioFileMetadata metadata) {
		songChart.musicFileName = songFile.getName();
		songChart.artistName(metadata.artist);
		songChart.title(metadata.title);
		songChart.albumName(metadata.album);
		if (metadata.year != null) {
			songChart.albumYear = metadata.year;
		}
	}

	public void setDataForNewProject(final File projectFolder, final SongChart songChart, final AudioData musicData) {
		// Belt-and-suspenders: redirect chartData's write target to the new folder up-front.
		// ChartData.setSong also does this as its first step, but assigning here too ensures
		// that even if setNewSong isn't reached (e.g. a later helper throws first), any
		// subsequent audio/project write still lands in the new folder rather than in the
		// previously-opened project's folder. This was the root cause of new-project
		// creation silently corrupting the previous project.
		chartData.path = projectFolder.getAbsolutePath();
		chartData.projectFileName = "project.rscp";

		// Clear state carried over from the previously opened project so the new project
		// starts from a clean slate (playback position, selected stem, charting timer, etc.).
		chartTimeHandler.reset();
		projectAudioHandler.selectStem(-1);
		chartingTimerHandler.reset();
		chartingTimerPanel.refresh();

		chartData.setNewSong(projectFolder, songChart, "project.rscp");
		textTab.setText("");

		projectAudioHandler.changeAudio(musicData);
		projectAudioHandler.readStems();
		audioHandler.clear();

		chordTemplatesEditorTab.refreshTemplates();

		songFileHandler.save();
	}
}
