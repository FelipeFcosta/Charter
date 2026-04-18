package log.charter.util;

import static log.charter.gui.components.utils.ComponentUtils.showPopup;

import java.awt.Component;
import java.awt.Container;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.metal.MetalComboBoxButton;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.Localization.Label;
import log.charter.data.config.SystemType;
import log.charter.sound.SoundFileType;

public class FileChooseUtils {
	private static String extension(final String fileName) {
		final int dotIndex = fileName.lastIndexOf('.');
		return fileName.substring(dotIndex + 1).toLowerCase();
	}

	/**
	 * Runs a PowerShell command and returns the first non-empty line of output.
	 */
	private static String runPowerShell(final String command) {
		try {
			final ProcessBuilder pb = new ProcessBuilder("powershell", "-noprofile", "-command", command);
			pb.redirectErrorStream(true);
			final Process process = pb.start();
			final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			String result = null;
			while ((line = reader.readLine()) != null) {
				if (!line.isEmpty()) {
					result = line;
				}
			}
			process.waitFor();
			return result != null ? result.trim() : null;
		} catch (final Exception e) {
			return null;
		}
	}

	/**
	 * Shows the modern Windows Explorer file picker (IFileOpenDialog via
	 * System.Windows.Forms.OpenFileDialog).
	 *
	 * @param startingDir   initial folder shown in the dialog
	 * @param windowsFilter Windows Forms filter string, e.g.
	 *                      "Audio Files (*.wav;*.mp3)|*.wav;*.mp3"
	 */
	private static File showWindowsFileDialog(final String startingDir, final String windowsFilter) {
		final String escapedDir = startingDir.replace("'", "''");
		final String escapedFilter = windowsFilter.replace("'", "''");

		final String command = "Add-Type -AssemblyName System.Windows.Forms; " +
				"[System.Windows.Forms.Application]::EnableVisualStyles(); " +
				"$dlg = New-Object System.Windows.Forms.OpenFileDialog; " +
				"$dlg.InitialDirectory = '" + escapedDir + "'; " +
				"$dlg.Filter = '" + escapedFilter + "'; " +
				"$dlg.AutoUpgradeEnabled = $true; " +
				"if ($dlg.ShowDialog() -eq 'OK') { Write-Output $dlg.FileName }";

		final String result = runPowerShell(command);
		return result != null ? new File(result) : null;
	}

	/**
	 * Shows the modern Windows Explorer folder picker. Uses OpenFileDialog in
	 * folder-selection mode so that the modern IFileOpenDialog COM interface is
	 * used instead of the old FolderBrowserDialog tree view.
	 */
	private static File showWindowsFolderDialog(final String startingPath) {
		final String escapedPath = startingPath.replace("'", "''");

		final String command = "Add-Type -AssemblyName System.Windows.Forms; " +
				"[System.Windows.Forms.Application]::EnableVisualStyles(); " +
				"$dlg = New-Object System.Windows.Forms.OpenFileDialog; " +
				"$dlg.InitialDirectory = '" + escapedPath + "'; " +
				"$dlg.ValidateNames = $false; " +
				"$dlg.CheckFileExists = $false; " +
				"$dlg.CheckPathExists = $true; " +
				"$dlg.FileName = 'Folder Selection.'; " +
				"$dlg.Filter = 'Folders|.'; " +
				"$dlg.AutoUpgradeEnabled = $true; " +
				"if ($dlg.ShowDialog() -eq 'OK') { " +
				"    Write-Output ([System.IO.Path]::GetDirectoryName($dlg.FileName)) " +
				"}";

		final String result = runPowerShell(command);
		return result != null ? new File(result) : null;
	}

	private static File showDialog(final Component parent, final JFileChooser chooser) {
		final int chosenOption = chooser.showOpenDialog(parent);
		if (chosenOption != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		return chooser.getSelectedFile();
	}

	public static File chooseMusicFile(final Component parent, final String startingDir) {
		if (SystemType.is(SystemType.WINDOWS)) {
			final StringBuilder exts = new StringBuilder();
			for (final SoundFileType type : SoundFileType.values()) {
				if (exts.length() > 0) {
					exts.append(';');
				}
				exts.append("*.").append(type.extension);
			}
			final String filter = Label.SUPPORTED_MUSIC_FILE.label() + " (" + exts + ")|" + exts;

			final File file = showWindowsFileDialog(startingDir, filter);
			if (file == null) {
				return null;
			}
			if (SoundFileType.fromExtension(extension(file.getName())) == null) {
				showPopup(parent, Label.UNSUPPORTED_MUSIC_FORMAT);
				return null;
			}
			return file;
		}

		final JFileChooser chooser = new JFileChooser(new File(startingDir));
		chooser.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(final File f) {
				if (SoundFileType.fromExtension(extension(f.getName())) != null) {
					return true;
				}

				return f.isDirectory();
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
		if (SystemType.is(SystemType.WINDOWS)) {
			final StringBuilder exts = new StringBuilder();
			for (final String ext : extensions) {
				if (exts.length() > 0) {
					exts.append(';');
				}
				exts.append("*").append(ext);
			}
			final String filter = description + " (" + exts + ")|" + exts;
			return showWindowsFileDialog(startingDir, filter);
		}

		final JFileChooser chooser = new JFileChooser(new File(startingDir));
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.addChoosableFileFilter(new FileFilter() {
			@Override
			public boolean accept(final File f) {
				if (f.isDirectory()) {
					return true;
				}

				for (final String extension : extensions) {
					if (f.getName().toLowerCase().endsWith(extension)) {
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
		if (SystemType.is(SystemType.WINDOWS)) {
			final StringBuilder filter = new StringBuilder();
			for (int i = 0; i < extensions.length; i++) {
				if (filter.length() > 0) {
					filter.append('|');
				}
				final String pattern = "*" + extensions[i];
				filter.append(descriptions[i]).append(" (").append(pattern).append(")|").append(pattern);
			}
			return showWindowsFileDialog(startingDir, filter.toString());
		}

		final JFileChooser chooser = new JFileChooser(new File(startingDir));
		chooser.setAcceptAllFileFilterUsed(false);

		for (int i = 0; i < extensions.length; i++) {
			final String extension = extensions[i];
			final String description = descriptions[i];
			chooser.addChoosableFileFilter(new FileFilter() {
				@Override
				public boolean accept(final File f) {
					if (f.isDirectory()) {
						return true;
					}

					return f.getName().toLowerCase().endsWith(extension);
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
		for (final Component component : container.getComponents()) {
			if (MetalComboBoxButton.class.isAssignableFrom(component.getClass())) {
				final MetalComboBoxButton button = (MetalComboBoxButton) component;
				button.setBackground(ColorLabel.BASE_BG_3.color());
			} else if (Container.class.isAssignableFrom(component.getClass())) {
				setComboBoxesBackgrounds((Container) component);
			}
		}
	}

	public static File chooseDirectory(final Component parent, final String startingPath) {
		if (SystemType.is(SystemType.WINDOWS)) {
			return showWindowsFolderDialog(startingPath);
		}

		final JFileChooser chooser = new JFileChooser(new File(startingPath));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setApproveButtonText(Label.SAVE_AS.label());

		setComboBoxesBackgrounds(chooser);

		final int chosenOption = chooser.showOpenDialog(parent);
		if (chosenOption != JFileChooser.APPROVE_OPTION) {
			return null;
		}

		return chooser.getSelectedFile();
	}
}
