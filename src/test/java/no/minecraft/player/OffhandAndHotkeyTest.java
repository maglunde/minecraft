package no.minecraft.player;

import no.minecraft.render.HUD;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OffhandAndHotkeyTest {

    @Test
    public void testPlayerSwapHands() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        player.setSelectedSlot(0);
        player.getInventory().getSlot(0).setType(BlockType.DIAMOND_SWORD);
        player.getInventory().getSlot(0).setCount(1);
        player.getInventory().getSlot(0).setDamage(15);

        player.getOffhandItem().setType(BlockType.IRON_PICKAXE);
        player.getOffhandItem().setCount(1);
        player.getOffhandItem().setDamage(5);

        player.swapHands();

        assertEquals(BlockType.IRON_PICKAXE, player.getInventory().getSlot(0).getType());
        assertEquals(1, player.getInventory().getSlot(0).getCount());
        assertEquals(5, player.getInventory().getSlot(0).getDamage());

        assertEquals(BlockType.DIAMOND_SWORD, player.getOffhandItem().getType());
        assertEquals(1, player.getOffhandItem().getCount());
        assertEquals(15, player.getOffhandItem().getDamage());

        // Swap back
        player.swapHands();
        assertEquals(BlockType.DIAMOND_SWORD, player.getInventory().getSlot(0).getType());
        assertEquals(BlockType.IRON_PICKAXE, player.getOffhandItem().getType());
    }

    @Test
    public void testPlayerSwapHandsWithEmpty() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        player.setSelectedSlot(2);
        player.getInventory().getSlot(2).setType(BlockType.TORCH);
        player.getInventory().getSlot(2).setCount(16);

        assertTrue(player.getOffhandItem().isEmpty());

        player.swapHands();

        assertTrue(player.getInventory().getSlot(2).isEmpty());
        assertEquals(BlockType.TORCH, player.getOffhandItem().getType());
        assertEquals(16, player.getOffhandItem().getCount());

        player.swapHands();

        assertEquals(BlockType.TORCH, player.getInventory().getSlot(2).getType());
        assertEquals(16, player.getInventory().getSlot(2).getCount());
        assertTrue(player.getOffhandItem().isEmpty());
    }

    @Test
    public void testInventoryHotkeySwap() {
        HUD hud = new HUD(false);
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        hud.toggleInventory(player);

        // Put cobblestone in hotbar slot 0 (key 1)
        player.getInventory().getSlot(0).setType(BlockType.COBBLESTONE);
        player.getInventory().getSlot(0).setCount(32);

        // Put iron ingot in main inventory slot 9
        player.getInventory().getSlot(9).setType(BlockType.IRON_INGOT);
        player.getInventory().getSlot(9).setCount(10);

        int winW = 800;
        int winH = 600;
        float scale = hud.getGuiScale(winW, winH);
        float ix = (winW - 176.0f * scale) / 2.0f;
        float iy = (winH - 166.0f * scale) / 2.0f;

        // Coordinates of inventory slot 9 (row 0, col 0 in 3x9 grid)
        double mx = ix + 8.0f * scale + 4.0f;
        double my = iy + 84.0f * scale + 4.0f;

        // Press key '1' (GLFW_KEY_1 = 49)
        boolean handled = hud.handleInventoryKeyPress(49, mx, my, player, winW, winH);
        assertTrue(handled);

        // Slot 9 should now be Cobblestone, and hotbar slot 0 should be Iron Ingot
        assertEquals(BlockType.COBBLESTONE, player.getInventory().getSlot(9).getType());
        assertEquals(32, player.getInventory().getSlot(9).getCount());
        assertEquals(BlockType.IRON_INGOT, player.getInventory().getSlot(0).getType());
        assertEquals(10, player.getInventory().getSlot(0).getCount());
    }

    @Test
    public void testInventorySwapKeyFOnSlot() {
        HUD hud = new HUD(false);
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        hud.toggleInventory(player);

        // Offhand has torches
        player.getOffhandItem().setType(BlockType.TORCH);
        player.getOffhandItem().setCount(64);

        // Slot 10 has bread
        player.getInventory().getSlot(10).setType(BlockType.BREAD);
        player.getInventory().getSlot(10).setCount(5);

        int winW = 800;
        int winH = 600;
        float scale = hud.getGuiScale(winW, winH);
        float ix = (winW - 176.0f * scale) / 2.0f;
        float iy = (winH - 166.0f * scale) / 2.0f;

        // Coordinates of inventory slot 10 (row 0, col 1 in 3x9 grid)
        double mx = ix + 8.0f * scale + 18.0f * scale + 4.0f;
        double my = iy + 84.0f * scale + 4.0f;

        boolean handled = hud.handleSwapKeyPress(mx, my, player, winW, winH);
        assertTrue(handled);

        // Slot 10 should now have Torches, offhand should have Bread
        assertEquals(BlockType.TORCH, player.getInventory().getSlot(10).getType());
        assertEquals(64, player.getInventory().getSlot(10).getCount());
        assertEquals(BlockType.BREAD, player.getOffhandItem().getType());
        assertEquals(5, player.getOffhandItem().getCount());
    }

    @Test
    public void testDoubleClickCollectsMatchingItemsUpTo64() {
        HUD hud = new HUD(false);
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        hud.toggleInventory(player);

        // Populate multiple slots with dirt
        player.getInventory().getSlot(0).setType(BlockType.DIRT);
        player.getInventory().getSlot(0).setCount(20);

        player.getInventory().getSlot(1).setType(BlockType.DIRT);
        player.getInventory().getSlot(1).setCount(30);

        player.getInventory().getSlot(2).setType(BlockType.DIRT);
        player.getInventory().getSlot(2).setCount(40);

        int winW = 800;
        int winH = 600;
        float scale = hud.getGuiScale(winW, winH);
        float ix = (winW - 176.0f * scale) / 2.0f;
        float iy = (winH - 166.0f * scale) / 2.0f;

        // Slot 0 (hotbar col 0)
        double mx = ix + 8.0f * scale + 4.0f;
        double my = iy + 142.0f * scale + 4.0f;

        // 1st Click on slot 0 picks up 20 dirt
        hud.handleMouseClick(mx, my, 0, false, player, winW, winH);
        assertEquals(BlockType.DIRT, hud.getCarriedItem().getType());
        assertEquals(20, hud.getCarriedItem().getCount());
        assertTrue(player.getInventory().getSlot(0).isEmpty());

        // 2nd Click (double click) gathers matching items up to 64
        hud.handleMouseClick(mx, my, 0, false, player, winW, winH);

        // Carried should now be full at 64
        assertEquals(64, hud.getCarriedItem().getCount());

        // Total dirt was 20 + 30 + 40 = 90. Carried took 64, remainder in inventory should be 26.
        int totalRemaining = 0;
        for (int i = 0; i < Inventory.TOTAL_SLOTS; i++) {
            if (player.getInventory().getSlot(i).getType() == BlockType.DIRT) {
                totalRemaining += player.getInventory().getSlot(i).getCount();
            }
        }
        assertEquals(26, totalRemaining);
    }
}
