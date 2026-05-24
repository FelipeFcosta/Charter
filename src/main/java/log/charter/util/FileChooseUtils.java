package log.charter.util;

import static log.charter.gui.components.utils.ComponentUtils.showPopup;

import java.awt.Component;
import java.awt.Container;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.metal.MetalComboBoxButton;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.Localization.Label;
import log.charter.data.config.values.PathsConfig;
import log.charter.sound.SoundFileType;

public class FileChooseUtils {

	private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

	private static String extension(final String fileName) {
		final int dotIndex = fileName.lastIndexOf('.');
		return fileName.substring(dotIndex + 1).toLowerCase();
	}

	/**
	 * Strips trailing separators, validates the directory exists, and falls back to
	 * the configured songs path when the requested directory is unusable.
	 */
	private static File resolveStartingDir(final String startingDir) {
		if (startingDir != null && !startingDir.isEmpty()) {
			String dir = startingDir;
			while (dir.endsWith("/") || dir.endsWith("\\")) {
				dir = dir.substring(0, dir.length() - 1);
			}
			if (!dir.isEmpty()) {
				final File f = new File(dir);
				if (f.isDirectory()) {
					return f;
				}
			}
		}
		final String songsDir = PathsConfig.songsPath;
		if (songsDir != null && !songsDir.isEmpty()) {
			final File f = new File(songsDir);
			if (f.isDirectory()) {
				return f;
			}
		}
		return null;
	}

	// -----------------------------------------------------------------------
	// Windows native picker (IFileOpenDialog via JNA)
	// Falls back to JFileChooser if COM fails for any reason.
	// -----------------------------------------------------------------------

	private static File winOpen(final String startingDir, final String[][] filters) {
		final File dir = resolveStartingDir(startingDir);
		final File result = WinFileDialog.open(dir, filters, false);
		return result;
	}

	private static File winOpenFolder(final String startingDir) {
		final File dir = resolveStartingDir(startingDir);
		return WinFileDialog.open(dir, null, true);
	}

	// -----------------------------------------------------------------------
	// JFileChooser fallback (non-Windows or COM failure)
	// -----------------------------------------------------------------------

	private static JFileChooser newChooser(final String startingDir) {
		final File dir = resolveStartingDir(startingDir);
		final JFileChooser chooser = new JFileChooser(dir);
		if (dir != null) {
			chooser.setCurrentDirectory(dir);
		}
		return chooser;
	}

	private static File showDialog(final Component parent, final JFileChooser chooser) {
		return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	public static File chooseMusicFile(final Component parent, final String startingDir) {
		if (IS_WINDOWS) {
			final SoundFileType[] types = SoundFileType.values();
			final StringBuilder spec = new StringBuilder();
			for (final SoundFileType type : types) {
				if (spec.length() > 0) {
					spec.append(';');
				}
				spec.append("*.").append(type.extension);
			}
			final File file = winOpen(startingDir,
					new String[][] { { Label.SUPPORTED_MUSIC_FILE.label(), spec.toString() } });
			if (file == null) {
				return null;
			}
			if (SoundFileType.fromExtension(extension(file.getName())) == null) {
				showPopup(parent, Label.UNSUPPORTED_MUSIC_FORMAT);
				return null;
			}
			return file;
		}

		final JFileChooser chooser = newChooser(startingDir);
		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(final File f) {
				return f.isDirectory() || SoundFileType.fromExtension(extension(f.getName())) != null;
			}

			@Override
			public String getDescription() {
				return Label.SUPPORTED_MUSIC_FILE.label();
			}
		});
		final File file = showDialog(parent, chooser);
		if (file == null) {
			return null;
		}
		if (SoundFileType.fromExtension(extension(file.getName())) == null) {
			showPopup(parent, Label.UNSUPPORTED_MUSIC_FORMAT);
			return null;
		}
		return file;
	}

	public static File chooseFile(final Component parent, final String startingDir, final String[] extensions,
			final String description) {
		if (IS_WINDOWS) {
			final StringBuilder spec = new StringBuilder();
			for (final String ext : extensions) {
				if (spec.length() > 0) {
					spec.append(';');
				}
				final String e = ext.startsWith(".") ? ext.substring(1) : ext;
				spec.append("*.").append(e);
			}
			return winOpen(startingDir, new String[][] { { description, spec.toString() } });
		}

		final JFileChooser chooser = newChooser(startingDir);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.addChoosableFileFilter(new FileFilter() {
			@Override
			public boolean accept(final File f) {
				if (f.isDirectory()) {
					return true;
				}
				for (final String ext : extensions) {
					if (f.getName().toLowerCase().endsWith(ext)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public String getDescription() {
				return description;
			}
		});
		return showDialog(parent, chooser);
	}

	public static File chooseFile(final Component parent, final String startingDir, final String[] extensions,
			final String[] descriptions) {
		if (IS_WINDOWS) {
			final String[][] filters = new String[extensions.length][2];
			for (int i = 0; i < extensions.length; i++) {
				final String e = extensions[i].startsWith(".") ? extensions[i].substring(1) : extensions[i];
				filters[i][0] = descriptions[i];
				filters[i][1] = "*." + e;
			}
			return winOpen(startingDir, filters);
		}

		final JFileChooser chooser = newChooser(startingDir);
		chooser.setAcceptAllFileFilterUsed(false);
		for (int i = 0; i < extensions.length; i++) {
			final String extension = extensions[i];
			final String description = descriptions[i];
			chooser.addChoosableFileFilter(new FileFilter() {
				@Override
				public boolean accept(final File f) {
					return f.isDirectory() || f.getName().toLowerCase().endsWith(extension);
				}

				@Override
				public String getDescription() {
					return description;
				}
			});
		}
		return showDialog(parent, chooser);
	}

	private static void setComboBoxesBackgrounds(final Container container) {
		for (final java.awt.Component component : container.getComponents()) {
			if (MetalComboBoxButton.class.isAssignableFrom(component.getClass())) {
				((MetalComboBoxButton) component).setBackground(ColorLabel.BASE_BG_3.color());
			} else if (Container.class.isAssignableFrom(component.getClass())) {
				setComboBoxesBackgrounds((Container) component);
			}
		}
	}

	public static File chooseDirectory(final Component parent, final String startingPath) {
		if (IS_WINDOWS) {
			return winOpenFolder(startingPath);
		}

		final JFileChooser chooser = newChooser(startingPath);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setApproveButtonText(Label.SAVE_AS.label());
		setComboBoxesBackgrounds(chooser);

		return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}
}
