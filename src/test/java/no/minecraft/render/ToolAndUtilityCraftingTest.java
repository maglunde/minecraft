package no.minecraft.render;

import no.minecraft.player.CraftingRecipe;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ToolAndUtilityCraftingTest {

    private HUD hud;
    private Player player;
    private List<CraftingRecipe> recipes;

    @BeforeEach
    public void setUp() {
        hud = new HUD(false);
        player = new Player(new World(), 0, 10, 0);
        recipes = CraftingRecipe.getDefaultRecipes();
    }

    private CraftingRecipe findRecipe(BlockType outputType) {
        return recipes.stream()
                .filter(r -> r.getOutput().getType() == outputType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Recipe not found for " + outputType));
    }

    @Test
    public void testIronPickaxeCraftingManualGrid() {
        ItemStack[] bench = hud.getBenchSlots();
        for (ItemStack s : bench) s.clear();

        // Row 0: 3 Iron Ingots
        bench[0].setType(BlockType.IRON_INGOT); bench[0].setCount(1);
        bench[1].setType(BlockType.IRON_INGOT); bench[1].setCount(1);
        bench[2].setType(BlockType.IRON_INGOT); bench[2].setCount(1);
        // Column 1, Rows 1 and 2: Sticks
        bench[4].setType(BlockType.STICK); bench[4].setCount(1);
        bench[7].setType(BlockType.STICK); bench[7].setCount(1);

        ItemStack res = hud.get3x3CraftingResult();
        assertNotNull(res);
        assertEquals(BlockType.IRON_PICKAXE, res.getType());
        assertEquals(1, res.getCount());
    }

    @Test
    public void testIronPickaxePopulateRecipe() {
        CraftingRecipe pickRecipe = findRecipe(BlockType.IRON_PICKAXE);
        player.getInventory().addItem(BlockType.IRON_INGOT, 3);
        player.getInventory().addItem(BlockType.STICK, 2);

        hud.populate3x3Recipe(pickRecipe, player);

        ItemStack res = hud.get3x3CraftingResult();
        assertNotNull(res);
        assertEquals(BlockType.IRON_PICKAXE, res.getType());
        assertEquals(1, res.getCount());
    }

    @Test
    public void testDiamondPickaxeCraftingAndPopulate() {
        CraftingRecipe pickRecipe = findRecipe(BlockType.DIAMOND_PICKAXE);
        player.getInventory().addItem(BlockType.DIAMOND, 3);
        player.getInventory().addItem(BlockType.STICK, 2);

        hud.populate3x3Recipe(pickRecipe, player);

        ItemStack res = hud.get3x3CraftingResult();
        assertNotNull(res);
        assertEquals(BlockType.DIAMOND_PICKAXE, res.getType());
        assertEquals(1, res.getCount());
    }

    @Test
    public void testIronAndDiamondSwords() {
        CraftingRecipe ironSword = findRecipe(BlockType.IRON_SWORD);
        player.getInventory().addItem(BlockType.IRON_INGOT, 2);
        player.getInventory().addItem(BlockType.STICK, 1);

        hud.populate3x3Recipe(ironSword, player);
        ItemStack res1 = hud.get3x3CraftingResult();
        assertNotNull(res1);
        assertEquals(BlockType.IRON_SWORD, res1.getType());

        CraftingRecipe diaSword = findRecipe(BlockType.DIAMOND_SWORD);
        player.getInventory().addItem(BlockType.DIAMOND, 2);
        player.getInventory().addItem(BlockType.STICK, 1);

        hud.populate3x3Recipe(diaSword, player);
        ItemStack res2 = hud.get3x3CraftingResult();
        assertNotNull(res2);
        assertEquals(BlockType.DIAMOND_SWORD, res2.getType());
    }

    @Test
    public void testIronAndDiamondAxes() {
        CraftingRecipe ironAxe = findRecipe(BlockType.IRON_AXE);
        player.getInventory().addItem(BlockType.IRON_INGOT, 3);
        player.getInventory().addItem(BlockType.STICK, 2);

        hud.populate3x3Recipe(ironAxe, player);
        ItemStack res1 = hud.get3x3CraftingResult();
        assertNotNull(res1);
        assertEquals(BlockType.IRON_AXE, res1.getType());

        CraftingRecipe diaAxe = findRecipe(BlockType.DIAMOND_AXE);
        player.getInventory().addItem(BlockType.DIAMOND, 3);
        player.getInventory().addItem(BlockType.STICK, 2);

        hud.populate3x3Recipe(diaAxe, player);
        ItemStack res2 = hud.get3x3CraftingResult();
        assertNotNull(res2);
        assertEquals(BlockType.DIAMOND_AXE, res2.getType());
    }

    @Test
    public void testIronAndDiamondShovels() {
        CraftingRecipe ironShovel = findRecipe(BlockType.IRON_SHOVEL);
        player.getInventory().addItem(BlockType.IRON_INGOT, 1);
        player.getInventory().addItem(BlockType.STICK, 2);

        hud.populate3x3Recipe(ironShovel, player);
        ItemStack res1 = hud.get3x3CraftingResult();
        assertNotNull(res1);
        assertEquals(BlockType.IRON_SHOVEL, res1.getType());

        CraftingRecipe diaShovel = findRecipe(BlockType.DIAMOND_SHOVEL);
        player.getInventory().addItem(BlockType.DIAMOND, 1);
        player.getInventory().addItem(BlockType.STICK, 2);

        hud.populate3x3Recipe(diaShovel, player);
        ItemStack res2 = hud.get3x3CraftingResult();
        assertNotNull(res2);
        assertEquals(BlockType.DIAMOND_SHOVEL, res2.getType());
    }

    @Test
    public void testBowAndArrowCrafting() {
        CraftingRecipe bowRecipe = findRecipe(BlockType.BOW);
        player.getInventory().addItem(BlockType.STICK, 3);
        player.getInventory().addItem(BlockType.STRING, 3);

        hud.populate3x3Recipe(bowRecipe, player);
        ItemStack resBow = hud.get3x3CraftingResult();
        assertNotNull(resBow);
        assertEquals(BlockType.BOW, resBow.getType());

        CraftingRecipe arrowRecipe = findRecipe(BlockType.ARROW);
        player.getInventory().addItem(BlockType.COBBLESTONE, 1);
        player.getInventory().addItem(BlockType.STICK, 1);
        player.getInventory().addItem(BlockType.STRING, 1);

        hud.populate3x3Recipe(arrowRecipe, player);
        ItemStack resArrow = hud.get3x3CraftingResult();
        assertNotNull(resArrow);
        assertEquals(BlockType.ARROW, resArrow.getType());
        assertEquals(4, resArrow.getCount());
    }

    @Test
    public void testFlintAndSteelEyeOfEnderBlazePowderObsidian() {
        // Flint and Steel
        player.getInventory().addItem(BlockType.IRON_INGOT, 1);
        player.getInventory().addItem(BlockType.GUNPOWDER, 1);
        hud.populate3x3Recipe(findRecipe(BlockType.FLINT_AND_STEEL), player);
        assertEquals(BlockType.FLINT_AND_STEEL, hud.get3x3CraftingResult().getType());

        // Eye of Ender
        player.getInventory().addItem(BlockType.ENDER_PEARL, 1);
        player.getInventory().addItem(BlockType.BLAZE_POWDER, 1);
        hud.populate3x3Recipe(findRecipe(BlockType.EYE_OF_ENDER), player);
        assertEquals(BlockType.EYE_OF_ENDER, hud.get3x3CraftingResult().getType());

        // Blaze Powder
        player.getInventory().addItem(BlockType.BLAZE_ROD, 1);
        hud.populate3x3Recipe(findRecipe(BlockType.BLAZE_POWDER), player);
        ItemStack resBlaze = hud.get3x3CraftingResult();
        assertEquals(BlockType.BLAZE_POWDER, resBlaze.getType());
        assertEquals(2, resBlaze.getCount());

        // Obsidian
        player.getInventory().addItem(BlockType.STONE, 4);
        player.getInventory().addItem(BlockType.COBBLESTONE, 4);
        hud.populate3x3Recipe(findRecipe(BlockType.OBSIDIAN), player);
        ItemStack resObs = hud.get3x3CraftingResult();
        assertEquals(BlockType.OBSIDIAN, resObs.getType());
        assertEquals(2, resObs.getCount());
    }

    @Test
    public void test2x2UtilityCrafting() {
        // Blaze Powder in 2x2
        ItemStack[] craftSlots = hud.getCraftSlots();
        for (ItemStack s : craftSlots) s.clear();
        craftSlots[0].setType(BlockType.BLAZE_ROD);
        craftSlots[0].setCount(1);
        ItemStack res1 = hud.getCraftingResult();
        assertNotNull(res1);
        assertEquals(BlockType.BLAZE_POWDER, res1.getType());
        assertEquals(2, res1.getCount());

        // Eye of Ender in 2x2
        for (ItemStack s : craftSlots) s.clear();
        craftSlots[0].setType(BlockType.ENDER_PEARL);
        craftSlots[0].setCount(1);
        craftSlots[1].setType(BlockType.BLAZE_POWDER);
        craftSlots[1].setCount(1);
        ItemStack res2 = hud.getCraftingResult();
        assertNotNull(res2);
        assertEquals(BlockType.EYE_OF_ENDER, res2.getType());

        // Flint and Steel in 2x2
        for (ItemStack s : craftSlots) s.clear();
        craftSlots[0].setType(BlockType.IRON_INGOT);
        craftSlots[0].setCount(1);
        craftSlots[1].setType(BlockType.GUNPOWDER);
        craftSlots[1].setCount(1);
        ItemStack res3 = hud.getCraftingResult();
        assertNotNull(res3);
        assertEquals(BlockType.FLINT_AND_STEEL, res3.getType());
    }
}
