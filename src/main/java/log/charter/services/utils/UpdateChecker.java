package log.charter.services.utils;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

import log.charter.CharterMain;
import log.charter.data.config.Config;
import log.charter.data.config.Localization.Label;
import log.charter.data.config.SystemType;
import log.charter.gui.CharterFrame;
import log.charter.gui.components.utils.ComponentUtils;
import log.charter.gui.components.utils.ComponentUtils.ConfirmAnswer;
import log.charter.io.Logger;
import log.charter.services.CharterContext;
import log.charter.util.RW;
import log.charter.util.Utils;

public class UpdateChecker {
	private static final URI latestVersionLink = URI.create("https://github.com/Lordszynencja/Charter/releases/latest");

	private static HttpResponse<String> getLatestVersionRedirect() {
		try {
			final HttpClient client = HttpClient.newHttpClient();
			final HttpRequest request = HttpRequest.newBuilder(latestVersionLink).build();
			final HttpResponse<String> response = client.sendAsync(request, BodyHandlers.ofString())//
					.join();
			if (response.statusCode() != 302) {
				Logger.error("Couldn't check latest version, GitHub response: " + response.statusCode());
				return null;
			}

			return response;
		} catch (final Throwable e) {
			Logger.error("Couldn't check for updates", e);
			return null;
		}
	}

	private static String getVersion(final HttpResponse<String> response) {
		final Optional<String> locationHeader = response.headers()//
				.firstValue("location");
		if (locationHeader.isEmpty()) {
			Logger.error("Location header was not present");
			return null;
		}

		final String location = locationHeader.get();
		final int slashPosition = location.lastIndexOf('/');
		if (slashPosition == -1) {
			Logger.error("There was no slash in redirect location: " + location);
			return null;
		}
		if (slashPosition == location.length() - 1) {
			Logger.error("There was no version in redirect location: " + location);
			return null;
		}

		return location.substring(slashPosition + 1);
	}

	private static boolean isNewerVersion(final String currentVersion, final String newVersion) {
		try {
			final String[] currentParts = currentVersion.split("\\.");
			final String[] newParts = newVersion.split("\\.");
			
			final int length = Math.max(currentParts.length, newParts.length);
			for (int i = 0; i < length; i++) {
				final int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
				final int newer = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
				
				if (newer > current) {
					return true;
				}
				if (newer < current) {
					return false;
				}
			}
			return false; // versions are equal
		} catch (final NumberFormatException e) {
			Logger.error("Error comparing versions: " + currentVersion + " vs " + newVersion, e);
			return false;
		}
	}

	private static boolean isExeAvailable() {
		try {
			return RW.getJarDirectory().getParentFile().listFiles((dir, name) -> name.endsWith(".exe")).length > 0;
		} catch (final Exception e) {

			return false;
		}
	}

	private CharterContext charterContext;
	private CharterFrame charterFrame;

	private boolean runUpdate(final String oldVersion, final String newVersion) throws IOException {
		new ProcessBuilder()//
				.command("cmd.exe", "/c", "START \"\" start_update.bat " + newVersion + " " + oldVersion)//
				.directory(RW.getJarDirectory()).start();
		return true;
	}

	private void skipVersion(final String version) {
		Config.skippedUpdateVersion = version;
		Config.markChanged();
		Config.save();
	}

	private void informUserAboutNewVersion(final String newVersion) {
		final ConfirmAnswer answer = ComponentUtils.askYesNo(charterFrame, Label.NEW_VERSION,
				Label.NEW_VERSION_AVAILABLE_DOWNLOAD, newVersion, CharterMain.VERSION);

		if (answer != ConfirmAnswer.YES) {
			skipVersion(newVersion);
			return;
		}

		try {
			Desktop.getDesktop().browse(latestVersionLink);
		} catch (final Exception e) {
			Logger.error("Couldn't open browser", e);
		}
	}

	private void informUserAboutNewVersionWithUpdate(final String newVersion) {
		final ConfirmAnswer answer = ComponentUtils.askYesNo(charterFrame, Label.NEW_VERSION,
				Label.NEW_VERSION_AVAILABLE_UPDATE, newVersion, CharterMain.VERSION);

		if (answer != ConfirmAnswer.YES) {
			skipVersion(newVersion);
			return;
		}

		try {
			if (runUpdate(CharterMain.VERSION, newVersion)) {
				charterContext.forceExit();
			}
		} catch (final IOException e) {
			Logger.error("Couldn't autoupdate", e);
		}
	}

	public void checkForUpdates() {
		if (Utils.isDevEnv) {
			return;
		}

		final HttpResponse<String> response = getLatestVersionRedirect();
		if (response == null) {
			return;
		}

		final String version = getVersion(response);
		if (version == null || !isNewerVersion(CharterMain.VERSION, version)) {
			return;
		}

		if (version.equals(Config.skippedUpdateVersion)) {
			return;
		}

		if (SystemType.is(SystemType.WINDOWS) && isExeAvailable()) {
			informUserAboutNewVersionWithUpdate(version);
		} else {
			informUserAboutNewVersion(version);
		}
	}
}
