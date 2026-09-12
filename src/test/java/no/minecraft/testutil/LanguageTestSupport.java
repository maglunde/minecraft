package no.minecraft.testutil;

import no.minecraft.settings.GameSettings;

/**
 * Pins the shared GameSettings language for a test. All tests share one JVM, so any test
 * that depends on translated text must pin and restore; a leaked language setting makes
 * other test classes flaky.
 */
public final class LanguageTestSupport {
    private static final ThreadLocal<GameSettings.Language> PREVIOUS = new ThreadLocal<>();

    private LanguageTestSupport() {}

    /** Pins the language; call {@link #restore()} afterwards (ideally in a finally/@AfterEach). */
    public static void pin(GameSettings.Language language) {
        PREVIOUS.set(GameSettings.getInstance().getLanguage());
        GameSettings.getInstance().setLanguage(language);
    }

    public static void restore() {
        GameSettings.Language previous = PREVIOUS.get();
        if (previous != null) {
            GameSettings.getInstance().setLanguage(previous);
            PREVIOUS.remove();
        }
    }

    /** Runs runnable with the given language pinned, then restores the previous one. */
    public static void runWith(GameSettings.Language language, Runnable runnable) {
        pin(language);
        try {
            runnable.run();
        } finally {
            restore();
        }
    }
}
