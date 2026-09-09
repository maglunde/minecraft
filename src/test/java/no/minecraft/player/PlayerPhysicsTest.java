package no.minecraft.player;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerPhysicsTest {

    @Test
    public void testPlayerStandsOnGroundAtSpawn() {
        World world = new World(12345L);
        Vector3f spawn = world.getSpawnPoint();
        Player player = new Player(world, spawn.x, spawn.y, spawn.z);

        float startY = player.getPosition().y;

        for (int i = 0; i < 120; i++) {
            player.update(1.0f / 60.0f, false, false, false, false, false, false, false);
        }

        assertTrue(player.isOnGround(), "Player skal stå på bakken etter 2 sekunder");
        assertTrue(Math.abs(player.getPosition().y - startY) < 0.5f,
                "Player skal ikke synke gjennom bakken (startY=" + startY + ", y=" + player.getPosition().y + ")");
    }

    @Test
    public void testSpawnAreaHasSolidGround() {
        World world = new World(12345L);
        Vector3f spawn = world.getSpawnPoint();
        int sx = (int) Math.floor(spawn.x);
        int sz = (int) Math.floor(spawn.z);

        BlockType ground = world.getBlock(sx, (int) Math.floor(spawn.y) - 1, sz);
        assertTrue(ground.isSolid(), "Blokken under spawn skal være solid, var: " + ground);
    }
}
