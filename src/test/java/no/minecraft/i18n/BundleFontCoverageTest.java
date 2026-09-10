package no.minecraft.i18n;

import no.minecraft.render.UiBatch;
import no.minecraft.settings.GameSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every character of every bundle value must be renderable by the pixel font.
 * Without this, a translator can write "é" or an en-dash and the text silently
 * disappears (or now, shows ?) on screen.
 */
class BundleFontCoverageTest {

    @BeforeEach
    void cleanBundles() {
        I18n.resetBundles();
    }

    @AfterEach
    void restoreBundles() {
        I18n.resetBundles();
    }

    @Test
    void everyBundleCharacterIsRenderable() {
        assertNotNull(UiBatch.getGlyph('Æ'));
        assertNotNull(UiBatch.getGlyph('Ø'));
        assertNotNull(UiBatch.getGlyph('Å'));
        assertNotNull(UiBatch.getGlyph('?'));

        for (GameSettings.Language language : GameSettings.Language.values()) {
            for (String key : I18n.keysFor(language)) {
                String value = I18n.rawFor(language, key);
                for (char c : value.toCharArray()) {
                    if (Character.isWhitespace(c)) continue;
                    assertNotNull(UiBatch.getGlyph(Character.toUpperCase(c)),
                            () -> language + "/" + key + " contains unrenderable char '" + c + "'");
                }
            }
        }
    }
}
