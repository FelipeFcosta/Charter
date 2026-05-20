package log.charter.gui.lookAndFeel;

import java.awt.Color;
import java.awt.Window;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.SystemType;
import log.charter.io.Logger;
import log.charter.io.rs.xml.song.ArrangementType;
import log.charter.services.editModes.EditMode;

public class WindowsTitleBarUtil {
	private interface Dwmapi extends StdCallLibrary {
		int DwmSetWindowAttribute(Pointer hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
	}

	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
	private static final int DWMWA_CAPTION_COLOR = 35;

	private static Dwmapi dwmapi = null;
	private static Pointer hwnd = null;

	private static Color captionColor(final EditMode mode, final ArrangementType type) {
		final Color bg = ColorLabel.BASE_BG_2.color();

		if (mode == EditMode.VOCALS) {
			return bg;
		}

		if (mode != EditMode.GUITAR || type == null) {
			return bg;
		}

		final Color accent;
		final float blend;
		switch (type) {
			case Lead, Combo -> { accent = new Color(230, 140, 30); blend = 0.6f; }
			case Rhythm -> { accent = new Color(60, 190, 60);        blend = 0.35f; }
			case Bass -> { accent = new Color(40, 130, 230);         blend = 0.35f; }
			default -> { return bg; }
		}

		final int r = Math.round(accent.getRed() * blend + bg.getRed() * (1 - blend));
		final int g = Math.round(accent.getGreen() * blend + bg.getGreen() * (1 - blend));
		final int b = Math.round(accent.getBlue() * blend + bg.getBlue() * (1 - blend));
		return new Color(r, g, b);
	}

	private static void setDarkMode(final int enabled) {
		try (Memory value = new Memory(4)) {
			value.setInt(0, enabled);
			dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4);
		}
	}

	private static void setCaptionColor(final Color color) {
		try (Memory value = new Memory(4)) {
			final int colorRef = (color.getBlue() << 16) | (color.getGreen() << 8) | color.getRed();
			value.setInt(0, colorRef);
			dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, value, 4);
		}
	}

	public static void init(final Window window) {
		if (!SystemType.is(SystemType.WINDOWS)) {
			return;
		}

		try {
			dwmapi = Native.load("dwmapi", Dwmapi.class);
			hwnd = Pointer.createConstant(Native.getWindowID(window));
			setDarkMode(1);
		} catch (final Exception e) {
			Logger.error("Failed to init title bar", e);
		}
	}

	public static void updateColor(final EditMode mode, final ArrangementType type) {
		if (dwmapi == null || hwnd == null) {
			return;
		}

		try {
			final Color color = captionColor(mode, type);
			setDarkMode(1);
			setCaptionColor(color);
		} catch (final Exception e) {
			Logger.error("Failed to update title bar color", e);
		}
	}
}
