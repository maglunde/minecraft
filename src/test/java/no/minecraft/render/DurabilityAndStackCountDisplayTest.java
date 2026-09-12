package no.minecraft.render;

import no.minecraft.player.ItemStack;
import no.minecraft.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DurabilityAndStackCountDisplayTest {

    @Test
    public void testDurabilityRatioCalculation() {
        ItemStack pickaxe = new ItemStack(BlockType.DIAMOND_PICKAXE, 1);
        assertTrue(pickaxe.getType().isDamageable());
        assertEquals(1561, pickaxe.getType().getMaxDurability());

        // Full durability (0 damage)
        assertEquals(1.0f, pickaxe.getDurabilityRatio(), 0.0001f);

        // Damaged
        pickaxe.setDamage(780);
        float expectedRatio = (1561 - 780) / 1561.0f;
        assertEquals(expectedRatio, pickaxe.getDurabilityRatio(), 0.001f);
        assertTrue(pickaxe.getDurabilityRatio() < 1.0f);
        assertTrue(pickaxe.getDurabilityRatio() > 0.0f);

        // Near broken
        pickaxe.setDamage(1560);
        assertEquals(1.0f / 1561.0f, pickaxe.getDurabilityRatio(), 0.001f);
    }

    @Test
    public void testStackCountDisplayRules() {
        ItemStack singleBlock = new ItemStack(BlockType.DIRT, 1);
        assertEquals(1, singleBlock.getCount());
        assertFalse(singleBlock.getType().isDamageable());

        ItemStack stack = new ItemStack(BlockType.DIRT, 64);
        assertEquals(64, stack.getCount());

        ItemStack tool = new ItemStack(BlockType.IRON_SWORD, 1);
        assertEquals(1, tool.getCount());
        assertTrue(tool.getType().isDamageable());
    }
}
