package no.minecraft.player;

import no.minecraft.render.HUD;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmorTest {

    private World world;
    private Player player;

    @BeforeEach
    void setUp() {
        world = new World();
        player = new Player(world, 0, 10, 0);
    }

    @Test
    void testArmorProperties() {
        assertTrue(BlockType.LEATHER_HELMET.isArmor());
        assertTrue(BlockType.IRON_CHESTPLATE.isArmor());
        assertTrue(BlockType.DIAMOND_LEGGINGS.isArmor());
        assertTrue(BlockType.DIAMOND_BOOTS.isArmor());
        assertFalse(BlockType.STONE.isArmor());
        assertFalse(BlockType.DIAMOND_SWORD.isArmor());

        assertEquals(BlockType.ArmorSlot.HELMET, BlockType.LEATHER_HELMET.getArmorSlot());
        assertEquals(BlockType.ArmorSlot.CHESTPLATE, BlockType.IRON_CHESTPLATE.getArmorSlot());
        assertEquals(BlockType.ArmorSlot.LEGGINGS, BlockType.DIAMOND_LEGGINGS.getArmorSlot());
        assertEquals(BlockType.ArmorSlot.BOOTS, BlockType.DIAMOND_BOOTS.getArmorSlot());

        // Defense points
        assertEquals(1, BlockType.LEATHER_HELMET.getArmorDefense());
        assertEquals(3, BlockType.LEATHER_CHESTPLATE.getArmorDefense());
        assertEquals(2, BlockType.LEATHER_LEGGINGS.getArmorDefense());
        assertEquals(1, BlockType.LEATHER_BOOTS.getArmorDefense());

        assertEquals(2, BlockType.IRON_HELMET.getArmorDefense());
        assertEquals(6, BlockType.IRON_CHESTPLATE.getArmorDefense());
        assertEquals(5, BlockType.IRON_LEGGINGS.getArmorDefense());
        assertEquals(2, BlockType.IRON_BOOTS.getArmorDefense());

        assertEquals(3, BlockType.DIAMOND_HELMET.getArmorDefense());
        assertEquals(8, BlockType.DIAMOND_CHESTPLATE.getArmorDefense());
        assertEquals(6, BlockType.DIAMOND_LEGGINGS.getArmorDefense());
        assertEquals(3, BlockType.DIAMOND_BOOTS.getArmorDefense());

        // Durabilities
        assertEquals(55, BlockType.LEATHER_HELMET.getMaxDurability());
        assertEquals(80, BlockType.LEATHER_CHESTPLATE.getMaxDurability());
        assertEquals(75, BlockType.LEATHER_LEGGINGS.getMaxDurability());
        assertEquals(65, BlockType.LEATHER_BOOTS.getMaxDurability());

        assertEquals(165, BlockType.IRON_HELMET.getMaxDurability());
        assertEquals(240, BlockType.IRON_CHESTPLATE.getMaxDurability());
        assertEquals(225, BlockType.IRON_LEGGINGS.getMaxDurability());
        assertEquals(195, BlockType.IRON_BOOTS.getMaxDurability());

        assertEquals(363, BlockType.DIAMOND_HELMET.getMaxDurability());
        assertEquals(528, BlockType.DIAMOND_CHESTPLATE.getMaxDurability());
        assertEquals(495, BlockType.DIAMOND_LEGGINGS.getMaxDurability());
        assertEquals(429, BlockType.DIAMOND_BOOTS.getMaxDurability());

        // Not placeable
        assertFalse(BlockType.LEATHER_HELMET.isPlaceable());
        assertFalse(BlockType.IRON_CHESTPLATE.isPlaceable());
        assertFalse(BlockType.DIAMOND_BOOTS.isPlaceable());
    }

    @Test
    void testTotalArmorAndDamageReduction() {
        assertEquals(0, player.getTotalArmor());

        // Equip Iron Set (15 armor points -> 60% damage reduction)
        player.getArmorSlot(0).setType(BlockType.IRON_HELMET);
        player.getArmorSlot(0).setCount(1);
        player.getArmorSlot(1).setType(BlockType.IRON_CHESTPLATE);
        player.getArmorSlot(1).setCount(1);
        player.getArmorSlot(2).setType(BlockType.IRON_LEGGINGS);
        player.getArmorSlot(2).setCount(1);
        player.getArmorSlot(3).setType(BlockType.IRON_BOOTS);
        player.getArmorSlot(3).setCount(1);

        assertEquals(15, player.getTotalArmor());

        // Player takes 10 damage -> 10 * (1 - 0.60) = 4 damage
        player.setHealth(20);
        player.damage(10);
        assertEquals(16, player.getHealth()); // 20 - 4 = 16

        // Check durability loss
        assertEquals(1, player.getArmorSlot(0).getDamage());
        assertEquals(1, player.getArmorSlot(1).getDamage());
        assertEquals(1, player.getArmorSlot(2).getDamage());
        assertEquals(1, player.getArmorSlot(3).getDamage());
    }

    @Test
    void testDiamondSetMaxReduction() {
        // Equip Diamond Set (20 armor points -> 80% damage reduction)
        player.getArmorSlot(0).setType(BlockType.DIAMOND_HELMET);
        player.getArmorSlot(0).setCount(1);
        player.getArmorSlot(1).setType(BlockType.DIAMOND_CHESTPLATE);
        player.getArmorSlot(1).setCount(1);
        player.getArmorSlot(2).setType(BlockType.DIAMOND_LEGGINGS);
        player.getArmorSlot(2).setCount(1);
        player.getArmorSlot(3).setType(BlockType.DIAMOND_BOOTS);
        player.getArmorSlot(3).setCount(1);

        assertEquals(20, player.getTotalArmor());

        player.setHealth(20);
        player.damage(10);
        // 10 * (1 - 0.80) = 2 damage
        assertEquals(18, player.getHealth());
    }

    @Test
    void testEquipArmorFromInventory() {
        player.getInventory().getSlot(0).setType(BlockType.DIAMOND_CHESTPLATE);
        player.getInventory().getSlot(0).setCount(1);

        assertTrue(player.getArmorSlot(1).isEmpty());

        boolean equipped = player.equipArmorFromInventory(0);
        assertTrue(equipped);
        assertEquals(BlockType.DIAMOND_CHESTPLATE, player.getArmorSlot(1).getType());
        assertTrue(player.getInventory().getSlot(0).isEmpty());

        // Now put Iron Chestplate in slot 0 and swap
        player.getInventory().getSlot(0).setType(BlockType.IRON_CHESTPLATE);
        player.getInventory().getSlot(0).setCount(1);

        boolean swapped = player.equipArmorFromInventory(0);
        assertTrue(swapped);
        assertEquals(BlockType.IRON_CHESTPLATE, player.getArmorSlot(1).getType());
        assertEquals(BlockType.DIAMOND_CHESTPLATE, player.getInventory().getSlot(0).getType());
    }

    @Test
    void testHudArmorState() {
        // 7 armor points (e.g. Full Leather Set)
        // Icon 0: 2 threshold <= 7 -> 2 (Full)
        // Icon 1: 4 threshold <= 7 -> 2 (Full)
        // Icon 2: 6 threshold <= 7 -> 2 (Full)
        // Icon 3: 8 threshold -> 7 == 8 - 1 -> 1 (Half)
        // Icon 4: 10 threshold -> 0 (Empty)
        assertEquals(2, HUD.getArmorState(7, 0));
        assertEquals(2, HUD.getArmorState(7, 1));
        assertEquals(2, HUD.getArmorState(7, 2));
        assertEquals(1, HUD.getArmorState(7, 3));
        assertEquals(0, HUD.getArmorState(7, 4));
        assertEquals(0, HUD.getArmorState(7, 9));

        // 20 armor points -> all 10 icons full (state 2)
        for (int i = 0; i < 10; i++) {
            assertEquals(2, HUD.getArmorState(20, i));
        }
    }

    @Test
    void testArmorCraftingRecipes() {
        // Test Diamond Chestplate recipe (8 diamonds in 3x3)
        ItemStack[] bench = new ItemStack[9];
        for (int i = 0; i < 9; i++) bench[i] = new ItemStack(BlockType.AIR, 0);
        // Chestplate pattern: D D / DDD / DDD
        bench[0].setType(BlockType.DIAMOND); bench[0].setCount(1);
        bench[2].setType(BlockType.DIAMOND); bench[2].setCount(1);
        bench[3].setType(BlockType.DIAMOND); bench[3].setCount(1);
        bench[4].setType(BlockType.DIAMOND); bench[4].setCount(1);
        bench[5].setType(BlockType.DIAMOND); bench[5].setCount(1);
        bench[6].setType(BlockType.DIAMOND); bench[6].setCount(1);
        bench[7].setType(BlockType.DIAMOND); bench[7].setCount(1);
        bench[8].setType(BlockType.DIAMOND); bench[8].setCount(1);

        ItemStack result = CraftingRecipe.matchGrid(bench, 3, 3);
        assertNotNull(result);
        assertEquals(BlockType.DIAMOND_CHESTPLATE, result.getType());
        assertEquals(1, result.getCount());

        // Test Iron Helmet recipe (5 iron ingots: III / I I)
        for (int i = 0; i < 9; i++) bench[i].clear();
        bench[0].setType(BlockType.IRON_INGOT); bench[0].setCount(1);
        bench[1].setType(BlockType.IRON_INGOT); bench[1].setCount(1);
        bench[2].setType(BlockType.IRON_INGOT); bench[2].setCount(1);
        bench[3].setType(BlockType.IRON_INGOT); bench[3].setCount(1);
        bench[5].setType(BlockType.IRON_INGOT); bench[5].setCount(1);

        result = CraftingRecipe.matchGrid(bench, 3, 3);
        assertNotNull(result);
        assertEquals(BlockType.IRON_HELMET, result.getType());

        // Test Leather Boots recipe (4 leather: L L / L L)
        for (int i = 0; i < 9; i++) bench[i].clear();
        bench[3].setType(BlockType.LEATHER); bench[3].setCount(1);
        bench[5].setType(BlockType.LEATHER); bench[5].setCount(1);
        bench[6].setType(BlockType.LEATHER); bench[6].setCount(1);
        bench[8].setType(BlockType.LEATHER); bench[8].setCount(1);

        result = CraftingRecipe.matchGrid(bench, 3, 3);
        assertNotNull(result);
        assertEquals(BlockType.LEATHER_BOOTS, result.getType());
    }
}
