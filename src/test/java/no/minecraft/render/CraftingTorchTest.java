package no.minecraft.render;

import no.minecraft.player.CraftingRecipe;
import no.minecraft.player.Inventory;
import no.minecraft.player.ItemStack;
import no.minecraft.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CraftingTorchTest {

    private HUD hud;

    @BeforeEach
    public void setUp() {
        hud = new HUD(false);
    }

    @Test
    public void test2x2TorchCraftingLeftColumn() {
        ItemStack[] slots = hud.getCraftSlots();
        slots[0].setType(BlockType.COAL);
        slots[0].setCount(1);
        slots[2].setType(BlockType.STICK);
        slots[2].setCount(1);

        ItemStack result = hud.getCraftingResult();
        assertNotNull(result);
        assertEquals(BlockType.TORCH, result.getType());
        assertEquals(4, result.getCount());
    }

    @Test
    public void test2x2TorchCraftingRightColumn() {
        ItemStack[] slots = hud.getCraftSlots();
        slots[1].setType(BlockType.COAL);
        slots[1].setCount(1);
        slots[3].setType(BlockType.STICK);
        slots[3].setCount(1);

        ItemStack result = hud.getCraftingResult();
        assertNotNull(result);
        assertEquals(BlockType.TORCH, result.getType());
        assertEquals(4, result.getCount());
    }

    @Test
    public void test2x2TorchCraftingReversedFails() {
        // Stick on top of coal is invalid in Minecraft
        ItemStack[] slots = hud.getCraftSlots();
        slots[0].setType(BlockType.STICK);
        slots[0].setCount(1);
        slots[2].setType(BlockType.COAL);
        slots[2].setCount(1);

        ItemStack result = hud.getCraftingResult();
        assertNull(result);
    }

    @Test
    public void test3x3TorchCraftingPositions() {
        int[][] validPairs = {
                {0, 3}, // col 0, row 0 & 1
                {3, 6}, // col 0, row 1 & 2
                {1, 4}, // col 1, row 0 & 1 (center column top)
                {4, 7}, // col 1, row 1 & 2 (center column bottom)
                {2, 5}, // col 2, row 0 & 1
                {5, 8}  // col 2, row 1 & 2
        };

        for (int[] pair : validPairs) {
            ItemStack[] bench = hud.getBenchSlots();
            for (ItemStack s : bench) s.clear();

            bench[pair[0]].setType(BlockType.COAL);
            bench[pair[0]].setCount(1);
            bench[pair[1]].setType(BlockType.STICK);
            bench[pair[1]].setCount(1);

            ItemStack res = hud.get3x3CraftingResult();
            assertNotNull(res, "Should craft torches at " + pair[0] + " and " + pair[1]);
            assertEquals(BlockType.TORCH, res.getType());
            assertEquals(4, res.getCount());
        }
    }

    @Test
    public void test3x3TorchCraftingReversedFails() {
        ItemStack[] bench = hud.getBenchSlots();
        bench[1].setType(BlockType.STICK);
        bench[1].setCount(1);
        bench[4].setType(BlockType.COAL);
        bench[4].setCount(1);

        ItemStack res = hud.get3x3CraftingResult();
        assertNull(res);
    }

    @Test
    public void testTorchRecipeDefinition() {
        List<CraftingRecipe> recipes = CraftingRecipe.getDefaultRecipes();
        CraftingRecipe torchRecipe = recipes.stream()
                .filter(r -> r.getOutput().getType() == BlockType.TORCH)
                .findFirst()
                .orElse(null);

        assertNotNull(torchRecipe);
        assertEquals("Fakkel", torchRecipe.getName());
        assertEquals(4, torchRecipe.getOutput().getCount());

        Inventory inv = new Inventory();
        assertFalse(torchRecipe.canCraft(inv));

        inv.addItem(BlockType.COAL, 1);
        assertFalse(torchRecipe.canCraft(inv));

        inv.addItem(BlockType.STICK, 1);
        assertTrue(torchRecipe.canCraft(inv));
    }
}
