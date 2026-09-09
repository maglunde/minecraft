package no.minecraft.render;

import no.minecraft.player.ItemStack;
import no.minecraft.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DragDistributionTest {

    @Test
    public void testRightDragSinglePlacement() {
        ItemStack carried = new ItemStack(BlockType.DIRT, 10);
        ItemStack slot = new ItemStack(BlockType.AIR, 0);

        boolean placed = HUD.distributeRightDragSlot(carried, slot);
        assertTrue(placed);
        assertEquals(BlockType.DIRT, slot.getType());
        assertEquals(1, slot.getCount());
        assertEquals(9, carried.getCount());
    }

    @Test
    public void testRightDragAddToExistingMatchingSlot() {
        ItemStack carried = new ItemStack(BlockType.COBBLESTONE, 5);
        ItemStack slot = new ItemStack(BlockType.COBBLESTONE, 10);

        boolean placed = HUD.distributeRightDragSlot(carried, slot);
        assertTrue(placed);
        assertEquals(BlockType.COBBLESTONE, slot.getType());
        assertEquals(11, slot.getCount());
        assertEquals(4, carried.getCount());
    }

    @Test
    public void testRightDragRejectsFullSlotOrDifferentType() {
        ItemStack carried = new ItemStack(BlockType.DIRT, 5);

        // Full slot
        ItemStack fullSlot = new ItemStack(BlockType.DIRT, 64);
        assertFalse(HUD.distributeRightDragSlot(carried, fullSlot));
        assertEquals(64, fullSlot.getCount());
        assertEquals(5, carried.getCount());

        // Different type
        ItemStack woodSlot = new ItemStack(BlockType.WOOD, 2);
        assertFalse(HUD.distributeRightDragSlot(carried, woodSlot));
        assertEquals(BlockType.WOOD, woodSlot.getType());
        assertEquals(2, woodSlot.getCount());
        assertEquals(5, carried.getCount());
    }

    @Test
    public void testLeftDragEvenSplitNoRemainder() {
        ItemStack carried = new ItemStack(BlockType.PLANKS, 60);
        ItemStack s1 = new ItemStack(BlockType.AIR, 0);
        ItemStack s2 = new ItemStack(BlockType.AIR, 0);
        ItemStack s3 = new ItemStack(BlockType.AIR, 0);

        List<ItemStack> slots = Arrays.asList(s1, s2, s3);
        HUD.distributeLeftDrag(carried, slots);

        assertEquals(20, s1.getCount());
        assertEquals(BlockType.PLANKS, s1.getType());
        assertEquals(20, s2.getCount());
        assertEquals(BlockType.PLANKS, s2.getType());
        assertEquals(20, s3.getCount());
        assertEquals(BlockType.PLANKS, s3.getType());
        assertTrue(carried.isEmpty());
        assertEquals(0, carried.getCount());
    }

    @Test
    public void testLeftDragEvenSplitWithRemainder() {
        ItemStack carried = new ItemStack(BlockType.STONE, 64);
        ItemStack s1 = new ItemStack(BlockType.AIR, 0);
        ItemStack s2 = new ItemStack(BlockType.AIR, 0);
        ItemStack s3 = new ItemStack(BlockType.AIR, 0);

        List<ItemStack> slots = Arrays.asList(s1, s2, s3);
        HUD.distributeLeftDrag(carried, slots);

        // 64 / 3 = 21 per slot, 1 remainder in carried
        assertEquals(21, s1.getCount());
        assertEquals(21, s2.getCount());
        assertEquals(21, s3.getCount());
        assertEquals(1, carried.getCount());
        assertEquals(BlockType.STONE, carried.getType());
    }

    @Test
    public void testLeftDragWithPrePopulatedSlotsAndStackCapping() {
        ItemStack carried = new ItemStack(BlockType.SAND, 30);
        ItemStack s1 = new ItemStack(BlockType.SAND, 5);
        ItemStack s2 = new ItemStack(BlockType.SAND, 60); // only space for 4 more
        ItemStack s3 = new ItemStack(BlockType.AIR, 0);

        List<ItemStack> slots = Arrays.asList(s1, s2, s3);
        // perSlot = 30 / 3 = 10
        HUD.distributeLeftDrag(carried, slots);

        // s1: 5 + 10 = 15
        assertEquals(15, s1.getCount());
        // s2: capped at 64 (added 4 of 10)
        assertEquals(64, s2.getCount());
        // s3: 0 + 10 = 10
        assertEquals(10, s3.getCount());

        // Total distributed: 10 + 4 + 10 = 24. Carried should have 30 - 24 = 6 remaining.
        assertEquals(6, carried.getCount());
        assertEquals(BlockType.SAND, carried.getType());
    }

    @Test
    public void testLeftDragNotEnoughItemsForSlots() {
        ItemStack carried = new ItemStack(BlockType.IRON_INGOT, 2);
        ItemStack s1 = new ItemStack(BlockType.AIR, 0);
        ItemStack s2 = new ItemStack(BlockType.AIR, 0);
        ItemStack s3 = new ItemStack(BlockType.AIR, 0);

        List<ItemStack> slots = Arrays.asList(s1, s2, s3);
        HUD.distributeLeftDrag(carried, slots);

        // 2 / 3 = 0, nothing distributed
        assertTrue(s1.isEmpty());
        assertTrue(s2.isEmpty());
        assertTrue(s3.isEmpty());
        assertEquals(2, carried.getCount());
    }
}
