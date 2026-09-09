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

    private void clearFlightPath(World world, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            for (int y = 19; y <= 22; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
        }
    }
}
