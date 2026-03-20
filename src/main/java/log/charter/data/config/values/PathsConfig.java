package log.charter.data.config.values;

import static log.charter.data.config.values.accessors.StringValueAccessor.forString;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import log.charter.data.config.values.accessors.ValueAccessor;

public class PathsConfig {
	public static final int MAX_RECENT_PATHS = 10;
	private static final String RECENT_PATHS_SEPARATOR = "|";

	public static String lastDir = "";
	public static String lastPath = "";
	public static String musicPath = System.getProperty("user.home") + File.separator + "Music";
	public static String songsPath = System.getProperty("user.home") + File.separator + "Documents";
	public static String gpFilesPath = songsPath;
	public static List<String> recentPaths = new ArrayList<>();

	public static void addRecentPath(final String path) {
		if (path == null || path.isEmpty()) {
			return;
		}

		recentPaths.remove(path);
		recentPaths.add(0, path);

		if (recentPaths.size() > MAX_RECENT_PATHS) {
			recentPaths = recentPaths.subList(0, MAX_RECENT_PATHS);
		}
	}

	private static String serializeRecentPaths() {
		return recentPaths.stream().collect(Collectors.joining(RECENT_PATHS_SEPARATOR));
	}

	private static void deserializeRecentPaths(final String value) {
		recentPaths = new ArrayList<>();
		if (value == null || value.isEmpty()) {
			return;
		}
		Arrays.stream(value.split("\\|")).filter(p -> !p.isEmpty()).forEach(recentPaths::add);
	}

	public static void init(final Map<String, ValueAccessor> valueAccessors, final String name) {
		valueAccessors.put(name + ".lastDir", forString(v -> lastDir = v, () -> lastDir, lastDir));
		valueAccessors.put(name + ".lastPath", forString(v -> lastPath = v, () -> lastPath, lastPath));
		valueAccessors.put(name + ".musicPath", forString(v -> musicPath = v, () -> musicPath, musicPath));
		valueAccessors.put(name + ".songsPath", forString(v -> songsPath = v, () -> songsPath, songsPath));
		valueAccessors.put(name + ".gpFilesPath", forString(v -> gpFilesPath = v, () -> gpFilesPath, gpFilesPath));
		valueAccessors.put(name + ".recentPaths",
				forString(PathsConfig::deserializeRecentPaths, PathsConfig::serializeRecentPaths, ""));
	}
}
