package no.minecraft.i18n;

import no.minecraft.settings.GameSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {

    @BeforeEach
    void cleanBundles() {
        I18n.resetBundles();
    }

    @AfterEach
    void restoreBundles() {
        I18n.resetBundles();
    }

    @Test
    void knownKeyResolvesInBothLanguages() {
        assertEquals("Fredelig", I18n.getFor(GameSettings.Language.NORWEGIAN, "difficulty.peaceful"));
        assertEquals("Peaceful", I18n.getFor(GameSettings.Language.ENGLISH, "difficulty.peaceful"));
    }

    @Test
    void fallbackSelectedThenEnglishThenKey() {
        I18n.overrideBundle(GameSettings.Language.NORWEGIAN, Map.of());
        I18n.overrideBundle(GameSettings.Language.ENGLISH, Map.of("k", "English"));
        assertEquals("English", I18n.getFor(GameSettings.Language.NORWEGIAN, "k"));

        I18n.overrideBundle(GameSettings.Language.ENGLISH, Map.of());
        assertEquals("k", I18n.getFor(GameSettings.Language.NORWEGIAN, "k"));
    }

    @Test
    void formatUsesRootLocale() {
        I18n.overrideBundle(GameSettings.Language.ENGLISH, Map.of("k", "%s / %.1f"));
        assertEquals("1000 / 41.7", I18n.formatFor(GameSettings.Language.ENGLISH, "k", 1000, 41.666));
    }

    @Test
    void malformedFormatReturnsRawValueInsteadOfThrowing() {
        I18n.overrideBundle(GameSettings.Language.ENGLISH, Map.of("k", "100% %s"));
        assertEquals("100% %s", I18n.formatFor(GameSettings.Language.ENGLISH, "k", "x"));
    }

    @Test
    void unknownKeyReturnsKey() {
        assertEquals("no.such.key", I18n.getFor(GameSettings.Language.ENGLISH, "no.such.key"));
    }

    @Test
    void bundleKeySetsMatch() {
        assertEquals(I18n.keysFor(GameSettings.Language.ENGLISH),
                I18n.keysFor(GameSettings.Language.NORWEGIAN));
    }

    @Test
    void everyLanguageHasNonEmptyBundle() {
        for (GameSettings.Language language : GameSettings.Language.values()) {
            assertFalse(I18n.keysFor(language).isEmpty(), language + " has no bundle");
        }
    }

    @Test
    void norwegianCharactersSurviveUtf8Loading() {
        // Guard against Properties.load(InputStream) ISO-8859-1 mojibake.
        assertEquals("Norsk (Bokmål)", I18n.getFor(GameSettings.Language.NORWEGIAN, "language.nb_no"));
    }

    @Test
    void anyLanguageMatchesIsCaseInsensitive() {
        assertTrue(I18n.anyLanguageMatches("difficulty.peaceful", "fredelig"));
        assertTrue(I18n.anyLanguageMatches("difficulty.peaceful", "PEACEFUL"));
        assertFalse(I18n.anyLanguageMatches("difficulty.peaceful", "vanskelig"));
    }
}
