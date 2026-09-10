package no.minecraft.render;

import no.minecraft.player.CraftingRecipe;
import no.minecraft.settings.GameSettings;
import no.minecraft.testutil.LanguageTestSupport;
import no.minecraft.world.BlockType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeFilterTest {

    @BeforeEach
    public void pinNorwegian() {
        LanguageTestSupport.pin(GameSettings.Language.NORWEGIAN);
    }

    @AfterEach
    public void restoreLanguage() {
        LanguageTestSupport.restore();
    }

    private HUD hudWithQuery(String query) {
        HUD hud = new HUD(false);
        hud.openCraftingTable();
        hud.openRecipeBook();
        hud.setRecipeSearchFocused(true);
        for (char c : query.toCharArray()) {
            hud.addRecipeSearchChar(c);
        }
        return hud;
    }

    @Test
    public void testRecipeFilterDefault() {
        HUD hud = new HUD(false);
        List<CraftingRecipe> all = CraftingRecipe.getDefaultRecipes();
        assertFalse(all.isEmpty());

        // Empty filter returns all recipes
        assertEquals(all.size(), hud.getFilteredRecipes().size());
    }

    @Test
    public void testRecipeFilterBoat() {
        // Norwegian "Båt"
        List<CraftingRecipe> norwegian = hudWithQuery("båt").getFilteredRecipes();
        assertFalse(norwegian.isEmpty());
        assertTrue(norwegian.stream().anyMatch(r -> r.getOutput().getType() == BlockType.BOAT));

        // English "Boat"
        List<CraftingRecipe> english = hudWithQuery("boat").getFilteredRecipes();
        assertFalse(english.isEmpty());
        assertTrue(english.stream().anyMatch(r -> r.getOutput().getType() == BlockType.BOAT));
    }

    @Test
    public void testRecipeFilterSwordsAndOvens() {
        List<CraftingRecipe> swords = hudWithQuery("sverd").getFilteredRecipes();
        assertTrue(swords.size() >= 3);

        List<CraftingRecipe> ovens = hudWithQuery("ovn").getFilteredRecipes();
        assertFalse(ovens.isEmpty());
        assertTrue(ovens.stream().anyMatch(r -> r.getOutput().getType() == BlockType.FURNACE));
    }

    @Test
    public void testRecipeFilterNoMatch() {
        List<CraftingRecipe> result = hudWithQuery("xyznonexistentitem123").getFilteredRecipes();
        assertTrue(result.isEmpty());
    }
}
