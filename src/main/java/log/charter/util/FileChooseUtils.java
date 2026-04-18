package log.charter.util;

import static log.charter.gui.components.utils.ComponentUtils.showPopup;

import java.awt.Component;
import java.awt.Container;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.metal.MetalComboBoxButton;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.Localization.Label;
import log.charter.sound.SoundFileType;

public class FileChooseUtils {
	private static String extension(final String fileName) {
		final int dotIndex = fileName.lastIndexOf('.');
		return fileName.substring(dotIndex + 1).toLowerCase();
	}

	/** Strips the leading dot that the codebase uses, e.g. ".xml" → "xml". */
	private static String nfdExt(final String extension) {
		return extension.startsWith(".") ? extension.substring(1) : extension;
	}

	private static volatile boolean nfdReady = false;

	private static synchronized boolean initNFD() {
		if (nfdReady) {
			return true;
		}
		if (NativeFileDialog.NFD_Init() == NativeFileDialog.NFD_OKAY) {
			Runtime.getRuntime().addShutdownHook(new Thread(NativeFileDialog::NFD_Quit));
			nfdReady = true;
		}
		return nfdReady;
	}

	/**
	 * Opens the native file dialog with a single filter group (all extensions
	 * lumped together under one description).
	 */
	private static File nfdOpenFile(final String startingDir, final String filterName, final String filterSpec) {
		try (final MemoryStack stack = MemoryStack.stackPush()) {
			final PointerBuffer outPath = stack.mallocPointer(1);
			final NFDFilterItem.Buffer filters = NFDFilterItem.malloc(1, stack);
			filters.get(0).name(stack.UTF8(filterName)).spec(stack.UTF8(filterSpec));

			final int result = NativeFileDialog.NFD_OpenDialog(outPath, filters, startingDir);
			if (result == NativeFileDialog.NFD_OKAY) {
				final String path = outPath.getStringUTF8(0);
				NativeFileDialog.NFD_FreePath(outPath.get(0));
				return new File(path);
			}
		}
		return null;
	}

	/**
	 * Opens the native file dialog with one filter entry per name/spec pair.
	 */
	private static File nfdOpenFile(final String startingDir, final String[] filterNames,
			final String[] filterSpecs) {
		try (final MemoryStack stack = MemoryStack.stackPush()) {
			final PointerBuffer outPath = stack.mallocPointer(1);
			final NFDFilterItem.Buffer filters = NFDFilterItem.malloc(filterNames.length, stack);
			for (int i = 0; i < filterNames.length; i++) {
				filters.get(i).name(stack.UTF8(filterNames[i])).spec(stack.UTF8(filterSpecs[i]));
			}

			final int result = NativeFileDialog.NFD_OpenDialog(outPath, filters, startingDir);
			if (result == NativeFileDialog.NFD_OKAY) {
				final String path = outPath.getStringUTF8(0);
				NativeFileDialog.NFD_FreePath(outPath.get(0));
				return new File(path);
			}
		}
		return null;
	}

	private static File nfdPickFolder(final String startingDir) {
		try (final MemoryStack stack = MemoryStack.stackPush()) {
			final PointerBuffer outPath = stack.mallocPointer(1);
			final int result = NativeFileDialog.NFD_PickFolder(outPath, startingDir);
			if (result == NativeFileDialog.NFD_OKAY) {
				final String path = outPath.getStringUTF8(0);
				NativeFileDialog.NFD_FreePath(outPath.get(0));
				return new File(path);
			}
		}
		return null;
	}

	private static File showDialog(final Component parent, final JFileChooser chooser) {
		final int chosenOption = chooser.showOpenDialog(parent);
		if (chosenOption != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		return chooser.getSelectedFile();
	}

	public static File chooseMusicFile(final Component parent, final String startingDir) {
		if (initNFD()) {
			final SoundFileType[] types = SoundFileType.values();
			final StringBuilder spec = new StringBuilder();
			for (final SoundFileType type : types) {
				if (spec.length() > 0) {
					spec.append(',');
				}
				spec.append(type.extension);
			}

			final File file = nfdOpenFile(startingDir, Label.SUPPORTED_MUSIC_FILE.label(), spec.toString());
			if (file == null) {
				return null;
			}
			if (SoundFileType.fromExtension(extension(file.getName())) == null) {
				showPopup(parent, Label.UNSUPPORTED_MUSIC_FORMAT);
				return null;
			}
			return file;
		}

		// JFileChooser fallback (non-Windows or NFD unavailable)
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
		if (initNFD()) {
			final StringBuilder spec = new StringBuilder();
			for (final String ext : extensions) {
				if (spec.length() > 0) {
					spec.append(',');
				}
				spec.append(nfdExt(ext));
			}
			return nfdOpenFile(startingDir, description, spec.toString());
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
		if (initNFD()) {
			final String[] specs = new String[extensions.length];
			for (int i = 0; i < extensions.length; i++) {
				specs[i] = nfdExt(extensions[i]);
			}
			return nfdOpenFile(startingDir, descriptions, specs);
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
		if (initNFD()) {
			return nfdPickFolder(startingPath);
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
