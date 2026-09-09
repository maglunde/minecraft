package no.minecraft.entity;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MobAITest {

    @Test
    public void testCreeperTargetsPlayerAndRotates() {
        World world = new World(12345L);
        for (int x = -10; x <= 15; x++) {
            for (int z = -10; z <= 10; z++) {
                world.setBlock(x, 9, z, BlockType.STONE);
                for (int y = 10; y <= 16; y++) {
                    world.setBlock(x, y, z, BlockType.AIR);
                }
            }
        }

        Player player = new Player(world, 10.0f, 10.0f, 0.0f);
        player.setGameMode(GameMode.SURVIVAL);

        Mob creeper = new Mob(MobType.CREEPER, 0.0f, 10.0f, 0.0f);

        creeper.update(0.1f, world, player);

        assertEquals(0.0f, creeper.getYaw(), 1.0f, "Creeper should turn to face player at 0 degrees");
        assertTrue(creeper.getVelocity().x > 0.5f, "Creeper should move along +X towards player");
        assertEquals(0.0f, creeper.getVelocity().z, 0.01f, "Creeper should not drift on Z");
    }

    @Test
    public void testCreeperIgnitesNearPlayer() {
        World world = new World(12345L);
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.setBlock(x, 9, z, BlockType.STONE);
                for (int y = 10; y <= 16; y++) {
                    world.setBlock(x, y, z, BlockType.AIR);
                }
            }
        }

        Player player = new Player(world, 2.0f, 10.0f, 0.0f);
        player.setGameMode(GameMode.SURVIVAL);

        Mob creeper = new Mob(MobType.CREEPER, 0.0f, 10.0f, 0.0f);

        creeper.update(0.1f, world, player);
        assertTrue(creeper.isIgnited(), "Creeper should ignite when player is within 3.2 blocks");
        assertTrue(creeper.getFuseRatio() > 0.0f, "Fuse ratio should increase");
    }

    @Test
    public void testCreeperJumpsOverOneBlockObstacle() {
        World world = new World(12345L);
        for (int x = -5; x <= 15; x++) {
            for (int z = -5; z <= 5; z++) {
                world.setBlock(x, 9, z, BlockType.STONE);
                for (int y = 10; y <= 16; y++) {
                    world.setBlock(x, y, z, BlockType.AIR);
                }
            }
        }
        // 1-block obstacle step at x = 1
        world.setBlock(1, 10, 0, BlockType.DIRT);

        Player player = new Player(world, 10.0f, 10.0f, 0.0f);
        player.setGameMode(GameMode.SURVIVAL);

        Mob creeper = new Mob(MobType.CREEPER, 0.0f, 10.0f, 0.0f);

        boolean jumped = false;
        for (int step = 0; step < 40; step++) {
            creeper.update(0.05f, world, player);
            if (creeper.getPosition().y > 10.15f) {
                jumped = true;
                break;
            }
        }

        assertTrue(jumped, "Creeper should jump over 1-block obstacle");
    }

    @Test
    public void testCreativeModePreventsHostileTargeting() {
        World world = new World(12345L);
        Player player = new Player(world, 2.0f, 10.0f, 0.0f);
        player.setGameMode(GameMode.CREATIVE);

        Mob creeper = new Mob(MobType.CREEPER, 0.0f, 10.0f, 0.0f);
        creeper.update(0.1f, world, player);

        assertFalse(creeper.isIgnited(), "Creeper should not ignite player in Creative mode");
    }
}
