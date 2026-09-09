package no.minecraft.render;

import no.minecraft.player.CraftingRecipe;
import no.minecraft.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeFilterTest {

    private List<CraftingRecipe> filter(List<CraftingRecipe> recipes, String query) {
        if (query == null || query.trim().isEmpty()) {
            return recipes;
        }
        String q = query.trim().toLowerCase();
        List<CraftingRecipe> list = new ArrayList<>();
        for (CraftingRecipe r : recipes) {
            String name = r.getName().toLowerCase();
            String disp = r.getOutput().getType().getName().toLowerCase();
            String enumName = r.getOutput().getType().name().toLowerCase();
            if (name.contains(q) || disp.contains(q) || enumName.contains(q)) {
                list.add(r);
            }
        }
        return list;
    }

    @Test
    public void testRecipeFilterDefault() {
        List<CraftingRecipe> all = CraftingRecipe.getDefaultRecipes();
        assertFalse(all.isEmpty());

        // Empty filter returns all recipes
        assertEquals(all.size(), filter(all, "").size());
        assertEquals(all.size(), filter(all, "   ").size());
    }

    @Test
    public void testRecipeFilterBoat() {
        List<CraftingRecipe> all = CraftingRecipe.getDefaultRecipes();

        // Norwegian "Båt"
        List<CraftingRecipe> norwegian = filter(all, "båt");
        assertFalse(norwegian.isEmpty());
        assertTrue(norwegian.stream().anyMatch(r -> r.getOutput().getType() == BlockType.BOAT));

        // English "Boat"
        List<CraftingRecipe> english = filter(all, "boat");
        assertFalse(english.isEmpty());
        assertTrue(english.stream().anyMatch(r -> r.getOutput().getType() == BlockType.BOAT));
    }

    @Test
    public void testRecipeFilterSwordsAndOvens() {
        List<CraftingRecipe> all = CraftingRecipe.getDefaultRecipes();

        List<CraftingRecipe> swords = filter(all, "sverd");
        assertTrue(swords.size() >= 3);

        List<CraftingRecipe> ovens = filter(all, "ovn");
        assertFalse(ovens.isEmpty());
        assertTrue(ovens.stream().anyMatch(r -> r.getOutput().getType() == BlockType.FURNACE));
    }

    @Test
    public void testRecipeFilterNoMatch() {
        List<CraftingRecipe> all = CraftingRecipe.getDefaultRecipes();
        List<CraftingRecipe> result = filter(all, "xyznonexistentitem123");
        assertTrue(result.isEmpty());
    }
}
