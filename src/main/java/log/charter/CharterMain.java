package log.charter;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import log.charter.data.config.Config;
import log.charter.data.config.GraphicalConfig;
import log.charter.data.config.values.PathsConfig;
import log.charter.io.Logger;
import log.charter.services.CharterContext;
import log.charter.services.mouseAndKeyboard.ShortcutConfig;
import log.charter.util.RW;

public class CharterMain {
	public static final String VERSION = "0.23.4";
	public static final String VERSION_DATE = "2026.02.17 15:00";
	public static final String TITLE = "Charter " + VERSION;

	private static void deleteTempUpdateFile() {
		try {
			final File tempUpdateFile = new File(RW.getJarDirectory(), "tmp_update.bat");
			if (tempUpdateFile.exists()) {
				tempUpdateFile.delete();
			}
		} catch (final SecurityException e) {
			Logger.debug("Couldn't delete tmp_update.bat", e);
		}
	}

	private static void initConfigs() {
		Config.init();
		GraphicalConfig.init();
		ShortcutConfig.init();
	}

	private static String getPathToOpen(final String[] args) {
		if (args.length > 0) {
			return args[0];
		}

		return PathsConfig.lastPath;
	}

	private static void startContext(final String[] args) {
		final String pathToOpen = getPathToOpen(args);

		final CharterContext context = new CharterContext();
		context.init();

		if (pathToOpen != null && !pathToOpen.isBlank()) {
			context.openProject(pathToOpen);
		}

		context.checkForUpdates();
	}

	private static void startEdtWatchdog() {
		final AtomicLong lastEdtPing = new AtomicLong(System.currentTimeMillis());
		final long freezeThresholdMs = 5000;

		final Thread pinger = new Thread(() -> {
			while (true) {
				SwingUtilities.invokeLater(() -> lastEdtPing.set(System.currentTimeMillis()));
				try {
					Thread.sleep(500);
				} catch (final InterruptedException e) {
					return;
				}
			}
		}, "EDT-watchdog-pinger");
		pinger.setDaemon(true);
		pinger.start();

		final Thread checker = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(freezeThresholdMs);
				} catch (final InterruptedException e) {
					return;
				}
				final long elapsed = System.currentTimeMillis() - lastEdtPing.get();
				if (elapsed >= freezeThresholdMs) {
					Logger.error("EDT frozen for " + elapsed + "ms — thread dump:");
					final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
					for (final ThreadInfo info : bean.dumpAllThreads(true, true)) {
						Logger.error(info.toString());
					}
				}
			}
		}, "EDT-watchdog-checker");
		checker.setDaemon(true);
		checker.start();
	}

	public static void main(final String[] args) throws InterruptedException, IOException {
		Thread.setDefaultUncaughtExceptionHandler(
				(thread, t) -> Logger.error("Uncaught exception on thread [" + thread.getName() + "]", t));

		startEdtWatchdog();

		try {
			deleteTempUpdateFile();
			initConfigs();
			startContext(args);
			Logger.info("Charter started");
		} catch (final Throwable t) {
			Logger.error("Couldn't start Charter", t);
		}
	}
}
