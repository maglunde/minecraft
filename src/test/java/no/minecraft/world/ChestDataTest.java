package no.minecraft.world;

import no.minecraft.player.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChestDataTest {

    @Test
    public void testChestInitialization() {
        ChestData chest = new ChestData(10, 64, -20);
        assertEquals(10, chest.getX());
        assertEquals(64, chest.getY());
        assertEquals(-20, chest.getZ());
        assertEquals(27, chest.getSize());
        assertTrue(chest.isEmpty());

        for (int i = 0; i < 27; i++) {
            ItemStack stack = chest.getSlot(i);
            assertNotNull(stack);
            assertTrue(stack.isEmpty());
        }
    }

    @Test
    public void testSetAndGetSlot() {
        ChestData chest = new ChestData(0, 0, 0);
        chest.setSlot(0, new ItemStack(BlockType.IRON_INGOT, 10));
        assertFalse(chest.isEmpty());
        assertEquals(BlockType.IRON_INGOT, chest.getSlot(0).getType());
        assertEquals(10, chest.getSlot(0).getCount());

        chest.setSlot(0, null);
        assertTrue(chest.getSlot(0).isEmpty());
        assertTrue(chest.isEmpty());
    }

    @Test
    public void testAddItemMergingAndPlacing() {
        ChestData chest = new ChestData(0, 0, 0);
        int remaining = chest.addItem(BlockType.APPLE, 5);
        assertEquals(0, remaining);
        assertFalse(chest.isEmpty());
        assertEquals(BlockType.APPLE, chest.getSlot(0).getType());
        assertEquals(5, chest.getSlot(0).getCount());

        // Merge into existing stack
        remaining = chest.addItem(BlockType.APPLE, 10);
        assertEquals(0, remaining);
        assertEquals(15, chest.getSlot(0).getCount());

        // Add different item into next slot
        remaining = chest.addItem(BlockType.WOODEN_AXE, 1);
        assertEquals(0, remaining);
        assertEquals(BlockType.WOODEN_AXE, chest.getSlot(1).getType());
        assertEquals(1, chest.getSlot(1).getCount());
    }

    @Test
    public void testClear() {
        ChestData chest = new ChestData(0, 0, 0);
        chest.addItem(BlockType.BREAD, 3);
        assertFalse(chest.isEmpty());
        chest.clear();
        assertTrue(chest.isEmpty());
    }
}
