package no.minecraft.i18n;

import no.minecraft.advancement.AdvancementManager;
import no.minecraft.player.CraftingRecipe;
import no.minecraft.player.GameMode;
import no.minecraft.player.Perspective;
import no.minecraft.render.MainMenu;
import no.minecraft.settings.GameSettings;
import no.minecraft.world.BlockType;
import no.minecraft.world.Dimension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class EnumKeyCoverageTest {

    @BeforeEach
    void cleanBundles() { I18n.resetBundles(); }

    @AfterEach
    void restoreBundles() { I18n.resetBundles(); }

    private static void assertResolves(GameSettings.Language language, String key) {
        assertNotNull(I18n.rawFor(language, key), () -> language + " missing raw value for " + key);
        assertNotEquals(key, I18n.getFor(language, key), () -> language + " does not resolve " + key);
    }

    @Test
    void blockKeysResolveInBothLanguages() {
        for (BlockType block : BlockType.values()) {
            for (GameSettings.Language language : GameSettings.Language.values()) {
                assertResolves(language, block.getTranslationKey());
            }
        }
    }

    @Test
    void recipeKeysResolveInBothLanguages() {
        for (CraftingRecipe recipe : CraftingRecipe.getDefaultRecipes()) {
            for (GameSettings.Language language : GameSettings.Language.values()) {
                assertResolves(language, recipe.getNameKey());
            }
        }
    }

    @Test
    void difficultyKeysResolveInBothLanguages() {
        for (MainMenu.Difficulty difficulty : MainMenu.Difficulty.values()) {
            String key = "difficulty." + difficulty.name().toLowerCase(Locale.ROOT);
            for (GameSettings.Language language : GameSettings.Language.values()) {
                assertResolves(language, key);
            }
        }
    }

    @Test
    void gameModeDimensionPerspectiveKeysResolveInBothLanguages() {
        for (GameMode mode : GameMode.values()) assertResolvesKey("game_mode." + mode.name().toLowerCase(Locale.ROOT));
        for (Dimension dimension : Dimension.values()) assertResolvesKey("dimension." + dimension.name().toLowerCase(Locale.ROOT));
        for (Perspective perspective : Perspective.values()) assertResolvesKey("perspective." + perspective.name().toLowerCase(Locale.ROOT));
        for (AdvancementManager.Advancement advancement : AdvancementManager.Advancement.values()) {
            assertResolvesKey(advancement.key() + ".title");
            assertResolvesKey(advancement.key() + ".description");
        }
        for (GameSettings.Language language : GameSettings.Language.values()) {
            assertResolvesKey("language." + language.getCode());
        }
    }

    private static void assertResolvesKey(String key) {
        for (GameSettings.Language language : GameSettings.Language.values()) {
            assertResolves(language, key);
        }
    }
}
