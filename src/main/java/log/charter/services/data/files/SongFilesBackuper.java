package log.charter.services.data.files;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import log.charter.data.ChartData;
import log.charter.data.config.Config;
import log.charter.io.Logger;
import log.charter.services.CharterContext.Initiable;
import log.charter.util.RW;

public class SongFilesBackuper implements Initiable {
	private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
	private static final int MAX_BACKUP_FOLDERS = 50;

	private static String getCurrentTimeString() {
		return timeFormat.format(LocalDateTime.now());
	}

	private static File getBackupDirsFile(final String dir) {
		return new File(new File(dir), "backups");
	}

	private static File makeSureBackupFolderExists(final String dir) {
		final File backupFolder = new File(getBackupDirsFile(dir), getCurrentTimeString());
		backupFolder.mkdirs();

		return backupFolder;
	}

	public static void makeBackups(final String dir, final List<String> fileNames) {
		final File backupFolder = makeSureBackupFolderExists(dir);

		for (final String fileName : fileNames) {
			final File f = new File(dir, fileName);
			if (f.exists()) {
				RW.writeB(new File(backupFolder, fileName), RW.readB(f));
			}
		}

		cleanupOldBackups(dir);
	}

	private static void cleanupOldBackups(final String dir) {
		final File backupsDir = getBackupDirsFile(dir);
		final File[] backupFolders = backupsDir.listFiles(File::isDirectory);
		if (backupFolders == null || backupFolders.length <= MAX_BACKUP_FOLDERS) {
			return;
		}

		// Sort by name (which is timestamp-based, so oldest first)
		Arrays.sort(backupFolders, Comparator.comparing(File::getName));

		// Delete oldest folders until we're at the limit
		final int foldersToDelete = backupFolders.length - MAX_BACKUP_FOLDERS;
		for (int i = 0; i < foldersToDelete; i++) {
			deleteFolder(backupFolders[i]);
		}
	}

	private static void deleteFolder(final File folder) {
		final File[] files = folder.listFiles();
		if (files != null) {
			for (final File file : files) {
				if (file.isDirectory()) {
					deleteFolder(file);
				} else {
					file.delete();
				}
			}
		}
		folder.delete();
	}

	public static void makeAudioBackup(final File file) {
		final File backupFolder = makeSureBackupFolderExists(file.getParent());
		final String backupfileName = getCurrentTimeString() + " " + file.getName();
		RW.writeB(new File(backupFolder, backupfileName), RW.readB(file));
	}

	private ChartData chartData;

	@Override
	public void init() {
		final Thread t = new Thread(() -> {
			while (true) {
				try {
					if (Config.backupDelay > 0) {
						Thread.sleep(Config.backupDelay * 1000);
					}
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}

				makeDefaultBackups();
			}
		});

		t.setName("Song files backupper");

		t.start();
	}

	private void makeDefaultBackups() {
		if (chartData.isEmpty) {
			return;
		}

		final List<String> filesToBackup = new ArrayList<>();
		filesToBackup.add(chartData.projectFileName);

		// Also backup RS XML arrangement files
		final File projectDir = new File(chartData.path);
		final File[] xmlFiles = projectDir.listFiles((dir, name) -> name.endsWith("_RS2.xml"));
		if (xmlFiles != null) {
			for (final File xmlFile : xmlFiles) {
				filesToBackup.add(xmlFile.getName());
			}
		}

		Logger.debug("Doing backup of " + chartData.path + ", files: " + filesToBackup);

		makeBackups(chartData.path, filesToBackup);
	}
}
