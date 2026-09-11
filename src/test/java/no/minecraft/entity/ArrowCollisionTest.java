package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ArrowCollisionTest {

    @Test
    public void testArrowDoesNotTunnelThroughWall() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 22, 0);
        clearFlightPath(world, 0, 1);
        // 1-thick wall across the arrow path
        world.setBlock(2, 20, 0, BlockType.STONE);
        world.setBlock(2, 21, 0, BlockType.STONE);

        // Arrow at 100 m/s covers 5 m in one 0.05 s tick; the wall sits mid-segment
        Arrow arrow = new Arrow(0.5f, 20.5f, 0.5f, 100.0f, 0.0f, 0.0f);
        arrow.update(0.05f, world, player);

        assertTrue(arrow.isDead(), "Pilen må treffe veggen midt i segmentet, ikke tunneler gjennom");
    }

    @Test
    public void testPlayerArrowHitsMob() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 22, 0);
        clearFlightPath(world, 0, 4);
        world.spawnMob(MobType.ZOMBIE, 3.0f, 20.0f, 0.5f);
        Mob zombie = world.getMobs().get(0);
        int healthBefore = zombie.getHealth();

        // Arrow at 60 m/s covers 3 m in one tick; zombie stands mid-segment
        Arrow arrow = new Arrow(0.5f, 20.5f, 0.5f, 60.0f, 0.0f, 0.0f);
        arrow.update(0.05f, world, player);

        assertTrue(zombie.getHealth() < healthBefore, "Player-skutt pil skal skade mobs");
        assertTrue(arrow.isDead());
    }

    @Test
    public void testHostileArrowDoesNotHitMob() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 22, 0);
        clearFlightPath(world, 0, 4);
        world.spawnMob(MobType.ZOMBIE, 3.0f, 20.0f, 0.5f);
        Mob zombie = world.getMobs().get(0);
        int healthBefore = zombie.getHealth();

        Arrow arrow = new Arrow(0.5f, 20.5f, 0.5f, 60.0f, 0.0f, 0.0f, true);
        arrow.update(0.05f, world, player);

        assertEquals(healthBefore, zombie.getHealth(), "Fiendtlig pil skal ikke skade andre mobs");
        assertFalse(arrow.isDead(), "Pilen skal fortsette forbi mobben");
    }

    @Test
    public void testPlayerArrowDoesNotDamageShooterOnSpawn() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 20, 0);
        clearFlightPath(world, 0, 10);
        player.setHealth(20);

        // Arrow spawned at player's eye position and shot forward
        float eyeY = player.getEyePosition().y;
        Arrow arrow = new Arrow(0.0f, eyeY, 0.0f, 24.0f, 0.0f, 0.0f, false);
        arrow.update(0.05f, world, player);

        assertEquals(20, player.getHealth(), "Spiller skal ikke ta skade av egen pil ved avfyring");
        assertFalse(arrow.isDead(), "Pilen skal fortsette å fly fremover");
        assertTrue(arrow.getPosition().x > 0.5f, "Pilen skal ha beveget seg fremover");
    }

    @Test
    public void testShooterArrowNeverHitsPlayer() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 20, 0);
        clearFlightPath(world, 0, 2);
        player.setHealth(20);

        // Arrow with a shooter reference that stays inside the player box past the grace period
        Arrow arrow = new Arrow(player.getPosition().x, player.getEyePosition().y, player.getPosition().z,
                0.1f, 0.0f, 0.0f, player, false);
        for (int i = 0; i < 40; i++) {
            arrow.update(0.05f, world, player); // 2 seconds
        }

        assertEquals(20, player.getHealth(), "Egen pil skal aldri skade spilleren");
        assertTrue(arrow.isDead(), "Pilen skal ha dødd mot bakken/vegg, ikke spilleren");
    }

    @Test
    public void testHostileArrowStillHitsPlayer() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 20, 0);
        clearFlightPath(world, 0, 2);
        player.setHealth(20);

        // Hostile arrow with a shooter reference (mob shot) inside the player box
        Arrow arrow = new Arrow(player.getPosition().x, player.getEyePosition().y, player.getPosition().z,
                0.0f, 0.0f, 0.0f, null, true);
        arrow.update(0.05f, world, player);

        assertTrue(player.getHealth() < 20, "Fiendtlig pil skal skade spilleren");
    }

    private void clearFlightPath(World world, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            for (int y = 19; y <= 22; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
        }
    }
}
