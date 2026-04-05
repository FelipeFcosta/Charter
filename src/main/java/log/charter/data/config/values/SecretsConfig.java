package log.charter.data.config.values;

import static log.charter.data.config.values.accessors.BooleanValueAccessor.forBoolean;

import java.util.Map;

import log.charter.data.config.values.accessors.ValueAccessor;

public class SecretsConfig {
	public static boolean optionsUnlocked = false;

	public static boolean explosions = true;
	public static boolean explosionsShakyCam = false;

	public static void init(final Map<String, ValueAccessor> valueAccessors, final String name) {
		valueAccessors.put(name + ".explosions", forBoolean(v -> explosions = v, () -> explosions, explosions));
		valueAccessors.put(name + ".explosionsShakyCam",
				forBoolean(v -> explosionsShakyCam = v, () -> explosionsShakyCam, explosionsShakyCam));
	}

	public static boolean explosionsEnabled() {
		return explosions;
	}

	public static boolean explosionsShakyCamEnabled() {
		return explosionsShakyCam;
	}
}
