package no.minecraft.player;

import no.minecraft.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryTest {

    @Test
    public void testAddItemFillsExistingStacksFirst() {
        Inventory inv = new Inventory();
        inv.getSlot(0).setType(BlockType.STONE);
        inv.getSlot(0).setCount(60);

        inv.addItem(BlockType.STONE, 10);

        assertEquals(64, inv.getSlot(0).getCount(), "Eksisterende stack fylles først");
        assertEquals(6, inv.getSlot(1).getCount(), "Resten går i ny stack");
        assertEquals(BlockType.STONE, inv.getSlot(1).getType());
    }

    @Test
    public void testAddItemPartialWhenFull() {
        Inventory inv = new Inventory();
        // Overflowing add stores what fits and reports that something was added
        assertTrue(inv.addItem(BlockType.STONE, Inventory.TOTAL_SLOTS * Inventory.MAX_STACK_SIZE + 1));
        assertEquals(Inventory.TOTAL_SLOTS * Inventory.MAX_STACK_SIZE, inv.getItemCount(BlockType.STONE));

        // The remainder cannot be placed
        assertFalse(inv.hasSpaceFor(BlockType.STONE, 1));
    }

    @Test
    public void testRemoveItemAcrossStacks() {
        Inventory inv = new Inventory();
        inv.addItem(BlockType.STONE, 70); // 64 + 6

        assertTrue(inv.removeItem(BlockType.STONE, 30));
        assertEquals(40, inv.getItemCount(BlockType.STONE));
        assertFalse(inv.removeItem(BlockType.STONE, 41), "Kan ikke fjerne mer enn man har");
    }

    @Test
    public void testHasSpaceFor() {
        Inventory inv = new Inventory();
        assertTrue(inv.hasSpaceFor(BlockType.STONE, 0));
        assertTrue(inv.hasSpaceFor(BlockType.STONE, Inventory.TOTAL_SLOTS * Inventory.MAX_STACK_SIZE));
        assertFalse(inv.hasSpaceFor(BlockType.STONE, Inventory.TOTAL_SLOTS * Inventory.MAX_STACK_SIZE + 1));
    }

    @Test
    public void testMergeStacksRespectsMaxAndTypes() {
        ItemStack from = new ItemStack(BlockType.STONE, 10);
        ItemStack to = new ItemStack(BlockType.STONE, 60);

        assertTrue(Inventory.mergeStacks(from, to, 10));
        assertEquals(64, to.getCount());
        assertEquals(6, from.getCount());

        // Type mismatch: nothing moves
        ItemStack other = new ItemStack(BlockType.DIRT, 5);
        assertFalse(Inventory.mergeStacks(from, other, 6));
        assertEquals(6, from.getCount());
        assertEquals(5, other.getCount());

        // Empty target takes the type
        ItemStack empty = new ItemStack(BlockType.AIR, 0);
        assertTrue(Inventory.mergeStacks(from, empty, 6));
        assertEquals(BlockType.STONE, empty.getType());
        assertEquals(6, empty.getCount());
        assertTrue(from.isEmpty(), "Tom kilde nullstilles");
    }
}
