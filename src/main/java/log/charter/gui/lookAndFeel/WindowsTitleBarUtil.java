package log.charter.gui.lookAndFeel;

import java.awt.Window;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import log.charter.data.config.ChartPanelColors.ColorLabel;
import log.charter.data.config.SystemType;
import log.charter.io.Logger;

public class WindowsTitleBarUtil {
	private interface Dwmapi extends StdCallLibrary {
		int DwmSetWindowAttribute(Pointer hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
	}

	private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
	private static final int DWMWA_CAPTION_COLOR = 35;

	private static Dwmapi dwmapi = null;
	private static Pointer hwnd = null;

	public static void init(final Window window) {
		if (!SystemType.is(SystemType.WINDOWS)) {
			return;
		}

		try {
			dwmapi = Native.load("dwmapi", Dwmapi.class);
			hwnd = Pointer.createConstant(Native.getWindowID(window));

			try (Memory value = new Memory(4)) {
				value.setInt(0, 1);
				dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4);
			}

			try (Memory color = new Memory(4)) {
				final var bg = ColorLabel.BASE_BG_2.color();
				color.setInt(0, (bg.getBlue() << 16) | (bg.getGreen() << 8) | bg.getRed());
				dwmapi.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, color, 4);
			}
		} catch (final Exception e) {
			Logger.error("Failed to init title bar", e);
		}
	}
}
