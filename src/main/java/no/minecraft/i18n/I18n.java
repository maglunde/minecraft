package no.minecraft.i18n;

import no.minecraft.settings.GameSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Key-based translations. One .properties file per language under /assets/lang/.
 *
 * IMPORTANT: the bundle map is loaded lazily inside {@link #bundle(GameSettings.Language)} —
 * never in a static initializer. {@link GameSettings.Language#getDisplayName()} calls back
 * into this class, and touching GameSettings while I18n itself is being initialized would
 * deadlock on class init.
 */
public final class I18n {
    private static final String BUNDLE_PATH = "/assets/lang/%s.properties";

    /** Lazily loaded bundles; null until first lookup. The test seam may replace entries. */
    private static EnumMap<GameSettings.Language, Map<String, String>> bundles;
    private static final Set<String> WARNED = new HashSet<>();

    private I18n() {}

    /** Value for the key in the selected language, falling back to English, then the key itself. */
    public static String get(String key) {
        return getFor(GameSettings.getInstance().getLanguage(), key);
    }

    /** get(key) run through String.format(Locale.ROOT). A malformed format returns the raw value. */
    public static String format(String key, Object... args) {
        return formatFor(GameSettings.getInstance().getLanguage(), key, args);
    }

    /** Pinned-language lookup with the same fallback chain. For tests and cross-language matching. */
    public static String getFor(GameSettings.Language language, String key) {
        String value = bundle(language).get(key);
        if (value == null) {
            value = bundle(GameSettings.Language.ENGLISH).get(key);
        }
        if (value == null) {
            if (WARNED.add(key)) {
                System.err.println("[I18n] Missing translation key: " + key);
            }
            return key;
        }
        return value;
    }

    /** Pinned-language format. */
    public static String formatFor(GameSettings.Language language, String key, Object... args) {
        String value = getFor(language, key);
        try {
            return String.format(Locale.ROOT, value, args);
        } catch (IllegalFormatException e) {
            // A bad translation must never crash the render loop.
            return value;
        }
    }

    /** Raw bundle value, or null if absent. No fallback. */
    public static String rawFor(GameSettings.Language language, String key) {
        return bundle(language).get(key);
    }

    /** Immutable key set of a language's file. */
    public static Set<String> keysFor(GameSettings.Language language) {
        return bundle(language).keySet();
    }

    /** True if any loaded language's value for key equals candidate (case-insensitive). */
    public static boolean anyLanguageMatches(String key, String candidate) {
        for (GameSettings.Language language : GameSettings.Language.values()) {
            String value = bundle(language).get(key);
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> bundle(GameSettings.Language language) {
        if (bundles == null) {
            bundles = new EnumMap<>(GameSettings.Language.class);
        }
        Map<String, String> bundle = bundles.get(language);
        if (bundle == null) {
            bundle = load(language);
            bundles.put(language, bundle);
        }
        return bundle;
    }

    private static Map<String, String> load(GameSettings.Language language) {
        String path = String.format(BUNDLE_PATH, language.getCode());
        Properties p = new Properties();
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[I18n] Missing bundle file: " + path);
                return Map.of();
            }
            // Reader-based load: Properties.load(InputStream) assumes ISO-8859-1.
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (String key : p.stringPropertyNames()) {
            map.put(key, p.getProperty(key));
        }
        return Collections.unmodifiableMap(map);
    }

    // --- Test seam: install stub bundles and reset to classpath-loaded state ---

    public static void overrideBundle(GameSettings.Language language, Map<String, String> entries) {
        if (bundles == null) {
            bundles = new EnumMap<>(GameSettings.Language.class);
        }
        bundles.put(language, Collections.unmodifiableMap(new HashMap<>(entries)));
    }

    public static void resetBundles() {
        bundles = null;
        WARNED.clear();
    }
}
