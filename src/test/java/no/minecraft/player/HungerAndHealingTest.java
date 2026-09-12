package no.minecraft.player;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HungerAndHealingTest {

    @Test
    public void testFoodValuesAndSaturation() {
        assertEquals(8, BlockType.COOKED_BEEF.getFoodValue());
        assertEquals(12.8f, BlockType.COOKED_BEEF.getSaturationValue(), 0.01f);

        assertEquals(8, BlockType.COOKED_PORKCHOP.getFoodValue());
        assertEquals(12.8f, BlockType.COOKED_PORKCHOP.getSaturationValue(), 0.01f);

        assertEquals(6, BlockType.COOKED_CHICKEN.getFoodValue());
        assertEquals(7.2f, BlockType.COOKED_CHICKEN.getSaturationValue(), 0.01f);

        assertEquals(5, BlockType.BREAD.getFoodValue());
        assertEquals(6.0f, BlockType.BREAD.getSaturationValue(), 0.01f);

        assertEquals(4, BlockType.APPLE.getFoodValue());
        assertEquals(2.4f, BlockType.APPLE.getSaturationValue(), 0.01f);

        assertEquals(3, BlockType.PORKCHOP.getFoodValue());
        assertEquals(1.8f, BlockType.PORKCHOP.getSaturationValue(), 0.01f);

        assertEquals(2, BlockType.CHICKEN_MEAT.getFoodValue());
        assertEquals(1.2f, BlockType.CHICKEN_MEAT.getSaturationValue(), 0.01f);
    }

    @Test
    public void testEatFoodRestoresHungerAndSaturationCapped() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);

        player.setHunger(10);
        player.setSaturation(0.0f);

        assertTrue(player.eatFood(BlockType.BREAD));
        assertEquals(15, player.getHunger()); // 10 + 5
        assertEquals(6.0f, player.getSaturation(), 0.01f);

        // Saturation cannot exceed hunger level
        player.setHunger(1);
        player.setSaturation(0.0f);
        assertTrue(player.eatFood(BlockType.COOKED_BEEF));
        // hunger = 1 + 8 = 9. saturation = min(9, 0 + 12.8) = 9.0
        assertEquals(9, player.getHunger());
        assertEquals(9.0f, player.getSaturation(), 0.01f);
    }

    @Test
    public void testCannotEatAtFullHungerInSurvival() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        player.setHunger(20);

        assertFalse(player.canEat(BlockType.APPLE));
        assertFalse(player.eatFood(BlockType.APPLE));
    }

    private void advanceTime(Player player, float seconds) {
        float step = 0.05f;
        int steps = Math.round(seconds / step);
        for (int i = 0; i < steps; i++) {
            player.update(step, false, false, false, false, false, false, false);
        }
    }

    private Player createGroundedPlayer(World world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlock(x, 9, z, BlockType.STONE);
            }
        }
        return new Player(world, 0.5f, 10.0f, 0.5f);
    }

    @Test
    public void testSprintingRequiresHungerAbove6() {
        World world = new World();
        Player player = createGroundedPlayer(world);

        // Hunger 7 -> can sprint
        player.setHunger(7);
        player.update(0.05f, true, false, false, false, false, false, true);
        assertTrue(player.isSprinting());

        // Hunger 6 -> cannot sprint
        player.setHunger(6);
        player.update(0.05f, true, false, false, false, false, false, true);
        assertFalse(player.isSprinting());
    }

    @Test
    public void testFastSaturationRegeneration() {
        World world = new World();
        Player player = createGroundedPlayer(world);

        player.setHealth(10);
        player.setHunger(20);
        player.setSaturation(10.0f);
        player.setExhaustion(0.0f);

        // Fast regen heals 1 HP every 0.5s when hunger==20 and saturation > 0
        advanceTime(player, 0.55f);
        assertEquals(11, player.getHealth());
        // Exhaustion was added 6.0f -> 4.0 consumed, 2.0 remaining, saturation decreased by 1
        assertEquals(9.0f, player.getSaturation(), 0.01f);
        assertEquals(2.0f, player.getExhaustion(), 0.01f);
    }

    @Test
    public void testNormalHungerRegeneration() {
        World world = new World();
        Player player = createGroundedPlayer(world);

        player.setHealth(10);
        player.setHunger(19);
        player.setSaturation(0.0f);
        player.setExhaustion(0.0f);

        // Normal regen heals 1 HP every 4.0s when hunger >= 18
        advanceTime(player, 2.0f);
        assertEquals(10, player.getHealth(), "Should not heal before 4.0s");

        advanceTime(player, 2.1f);
        assertEquals(11, player.getHealth(), "Should heal after 4.0s");
        assertEquals(18, player.getHunger(), "Hunger decreases as exhaustion reaches 6 (consumed 4 -> hunger-1, 2 rem)");
    }

    @Test
    public void testStarvationDamage() {
        World world = new World();
        Player player = createGroundedPlayer(world);

        player.setHealth(20);
        player.setHunger(0);
        player.setSaturation(0.0f);

        // Starvation deals 1 damage every 4.0s
        advanceTime(player, 2.0f);
        assertEquals(20, player.getHealth());

        advanceTime(player, 2.1f);
        assertEquals(19, player.getHealth());
    }

    @Test
    public void testEatingDurationAndCompletion() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        player.setHunger(10);

        player.startEating();
        assertTrue(player.isEating());

        // Update partially (1.0s < 1.61s)
        boolean finished = player.updateEating(1.0f, BlockType.APPLE);
        assertFalse(finished);
        assertTrue(player.isEating());
        assertEquals(10, player.getHunger());

        // Update remainder past 1.61s
        finished = player.updateEating(0.7f, BlockType.APPLE);
        assertTrue(finished);
        assertFalse(player.isEating());
        assertEquals(14, player.getHunger()); // 10 + 4
    }

    @Test
    public void testHeartBarEmptiesFromRight() {
        // Full health (20 HP) -> all 10 hearts are full (state 2)
        for (int i = 0; i < 10; i++) {
            assertEquals(2, no.minecraft.render.HUD.getHeartState(20, i));
        }

        // 19 HP -> rightmost heart (index 9) is half (state 1), other 9 are full (state 2)
        assertEquals(1, no.minecraft.render.HUD.getHeartState(19, 9));
        for (int i = 0; i < 9; i++) {
            assertEquals(2, no.minecraft.render.HUD.getHeartState(19, i));
        }

        // 18 HP -> rightmost heart (index 9) is empty (state 0), other 9 are full (state 2)
        assertEquals(0, no.minecraft.render.HUD.getHeartState(18, 9));
        for (int i = 0; i < 9; i++) {
            assertEquals(2, no.minecraft.render.HUD.getHeartState(18, i));
        }

        // 1 HP -> leftmost heart (index 0) is half (state 1), other 9 are empty (state 0)
        assertEquals(1, no.minecraft.render.HUD.getHeartState(1, 0));
        for (int i = 1; i < 10; i++) {
            assertEquals(0, no.minecraft.render.HUD.getHeartState(1, i));
        }

        // 0 HP -> all 10 hearts are empty (state 0)
        for (int i = 0; i < 10; i++) {
            assertEquals(0, no.minecraft.render.HUD.getHeartState(0, i));
        }
    }

    @Test
    public void testHungerBarEmptiesFromLeft() {
        // Full hunger (20) -> all 10 drumsticks are full (state 2)
        for (int i = 0; i < 10; i++) {
            assertEquals(2, no.minecraft.render.HUD.getDrumstickState(20, i));
        }

        // 19 hunger -> leftmost drumstick (index 0) is half (state 1), other 9 are full (state 2)
        assertEquals(1, no.minecraft.render.HUD.getDrumstickState(19, 0));
        for (int i = 1; i < 10; i++) {
            assertEquals(2, no.minecraft.render.HUD.getDrumstickState(19, i));
        }

        // 18 hunger -> leftmost drumstick (index 0) is empty (state 0), other 9 are full (state 2)
        assertEquals(0, no.minecraft.render.HUD.getDrumstickState(18, 0));
        for (int i = 1; i < 10; i++) {
            assertEquals(2, no.minecraft.render.HUD.getDrumstickState(18, i));
        }

        // 1 hunger -> rightmost drumstick (index 9) is half (state 1), other 9 on the left are empty (state 0)
        assertEquals(1, no.minecraft.render.HUD.getDrumstickState(1, 9));
        for (int i = 0; i < 9; i++) {
            assertEquals(0, no.minecraft.render.HUD.getDrumstickState(1, i));
        }

        // 0 hunger -> all 10 drumsticks are empty (state 0)
        for (int i = 0; i < 10; i++) {
            assertEquals(0, no.minecraft.render.HUD.getDrumstickState(0, i));
        }
    }
}
