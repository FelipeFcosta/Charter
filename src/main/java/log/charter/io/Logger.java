package log.charter.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import log.charter.data.config.values.DebugConfig;
import log.charter.util.Utils;

public class Logger {
	private static final SimpleDateFormat timeFormat = new SimpleDateFormat("<yyyy-MM-dd HH:mm:ss>");

	private static PrintStream out = System.err;

	static {
		try {
			final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			final String name = "log-" + dateFormat.format(new Date()) + ".txt";
			final File dir = new File(Utils.defaultConfigDir, "logs");
			if (!dir.exists()) {
				dir.mkdirs();
				dir.mkdir();
			}

			out = new PrintStream(new FileOutputStream(new File(dir, name), true), true);
			System.setErr(out);
			System.setOut(out);
		} catch (final Throwable e) {
			System.err.println("[LOGGER INIT FAILED] " + e);
			e.printStackTrace(System.err);
		}
	}

	private static String getLine(final String type, final String msg) {
		return "[" + type + "]" + timeFormat.format(new Date()) + " " + msg;
	}

	private static void write(final String msg) {
		out.println(msg);
	}

	private static void write(final String msg, final Throwable t) {
		out.println(msg);
		t.printStackTrace(out);
	}

	public static void debug(String msg) {
		if (!DebugConfig.logging) {
			return;
		}

		write(getLine("DEBUG", msg));
	}

	public static void debug(String msg, final Throwable t) {
		write(getLine("DEBUG", msg), t);
	}

	public static void info(String msg) {
		write(getLine("INFO", msg));
	}

	public static void info(String msg, final Exception e) {
		write(getLine("INFO", msg), e);
	}

	public static void warning(String msg) {
		write(getLine("WARNING", msg));
	}

	public static void warning(String msg, final Throwable e) {
		write(getLine("WARNING", msg), e);
	}

	public static void error(String msg) {
		write(getLine("ERROR", msg));
	}

	public static void error(String msg, final Throwable e) {
		write(getLine("ERROR", msg), e);
	}
}
