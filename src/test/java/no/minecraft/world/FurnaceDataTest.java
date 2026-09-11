package no.minecraft.world;

import no.minecraft.player.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FurnaceDataTest {

    @Test
    public void testFurnaceSmeltingMeat() {
        FurnaceData furnace = new FurnaceData(0, 0, 0);
        furnace.getInput().setType(BlockType.BEEF);
        furnace.getInput().setCount(2);

        furnace.getFuel().setType(BlockType.COAL);
        furnace.getFuel().setCount(1);

        // Update 1 tick to initiate burning
        furnace.update(0.1f);
        assertTrue(furnace.isBurning());
        assertEquals(BlockType.BEEF, furnace.getInput().getType());
        assertEquals(2, furnace.getInput().getCount());
        assertEquals(0, furnace.getFuel().getCount()); // Coal consumed

        // Fast forward cooking time (10 seconds total)
        furnace.update(10.0f);
        assertEquals(BlockType.COOKED_BEEF, furnace.getOutput().getType());
        assertEquals(1, furnace.getOutput().getCount());
        assertEquals(1, furnace.getInput().getCount());
    }

    @Test
    public void testFurnaceSmeltingIronOre() {
        FurnaceData furnace = new FurnaceData(0, 0, 0);
        furnace.getInput().setType(BlockType.IRON_ORE);
        furnace.getInput().setCount(1);
        furnace.getFuel().setType(BlockType.PLANKS);
        furnace.getFuel().setCount(1);

        furnace.update(10.1f);
        assertEquals(BlockType.IRON_INGOT, furnace.getOutput().getType());
        assertEquals(1, furnace.getOutput().getCount());
        assertTrue(furnace.getInput().isEmpty());
    }

    @Test
    public void testFurnaceTextureIsFrontFacing() {
        assertEquals(33, BlockType.FURNACE.getItemTexture());
        assertNotEquals(BlockType.COBBLESTONE.getItemTexture(), BlockType.FURNACE.getItemTexture());
        assertEquals(114, BlockType.FURNACE.getTexture(BlockType.Face.TOP));
        assertEquals(114, BlockType.FURNACE.getTexture(BlockType.Face.BOTTOM));
        assertEquals(33, BlockType.FURNACE.getTexture(BlockType.Face.NORTH));
        assertEquals(115, BlockType.FURNACE.getTexture(BlockType.Face.SOUTH));
        assertEquals(115, BlockType.FURNACE.getTexture(BlockType.Face.EAST));
        assertEquals(115, BlockType.FURNACE.getTexture(BlockType.Face.WEST));
    }

    @Test
    public void testNonPlaceableItems() {
        assertFalse(BlockType.BEEF.isPlaceable());
        assertFalse(BlockType.COOKED_BEEF.isPlaceable());
        assertFalse(BlockType.PORKCHOP.isPlaceable());
        assertFalse(BlockType.COOKED_PORKCHOP.isPlaceable());
        assertFalse(BlockType.CHICKEN_MEAT.isPlaceable());
        assertFalse(BlockType.COOKED_CHICKEN.isPlaceable());
        assertFalse(BlockType.BOAT.isPlaceable());
        assertFalse(BlockType.DIAMOND_SWORD.isPlaceable());
        assertFalse(BlockType.WATER.isPlaceable());
        assertFalse(BlockType.LAVA.isPlaceable());

        assertTrue(BlockType.DIRT.isPlaceable());
        assertTrue(BlockType.STONE.isPlaceable());
        assertTrue(BlockType.FURNACE.isPlaceable());
        assertTrue(BlockType.CRAFTING_TABLE.isPlaceable());
    }

    @Test
    public void testLavaHardnessUnbreakable() {
        assertTrue(BlockType.LAVA.getHardness() < 0.0f);
        assertTrue(BlockType.WATER.getHardness() < 0.0f);
    }

    @Test
    public void testFurnaceFuelTypes() {
        FurnaceData furnace = new FurnaceData(0, 0, 0);
        furnace.getInput().setType(BlockType.COBBLESTONE);
        furnace.getInput().setCount(5);

        // Test coal
        furnace.getFuel().setType(BlockType.COAL);
        furnace.getFuel().setCount(1);
        furnace.update(0.1f);
        assertTrue(furnace.isBurning());
        assertTrue(furnace.getMaxBurnTime() >= 48.0f);

        // Fast forward until smelted to stone
        furnace.update(10.0f);
        assertEquals(BlockType.STONE, furnace.getOutput().getType());
        assertEquals(1, furnace.getOutput().getCount());
    }

    @Test
    public void testFurnacePorkchopAndChicken() {
        FurnaceData f1 = new FurnaceData(0, 0, 0);
        f1.getInput().setType(BlockType.PORKCHOP);
        f1.getInput().setCount(1);
        f1.getFuel().setType(BlockType.COAL);
        f1.getFuel().setCount(1);
        f1.update(10.1f);
        assertEquals(BlockType.COOKED_PORKCHOP, f1.getOutput().getType());

        FurnaceData f2 = new FurnaceData(0, 0, 0);
        f2.getInput().setType(BlockType.CHICKEN_MEAT);
        f2.getInput().setCount(1);
        f2.getFuel().setType(BlockType.COAL);
        f2.getFuel().setCount(1);
        f2.update(10.1f);
        assertEquals(BlockType.COOKED_CHICKEN, f2.getOutput().getType());
    }
}
