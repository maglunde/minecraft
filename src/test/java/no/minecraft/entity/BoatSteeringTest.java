package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BoatSteeringTest {

    @Test
    public void testBoatSteeringWithADKeys() {
        World world = new World(12345L);
        Player player = new Player(world, 0, 10, 0);
        Boat boat = new Boat(0, 10, 0, -90.0f);
        boat.setDriver(player);
        player.setRidingBoat(boat);

        float initialYaw = boat.getYaw();

        // 1. Holding A (turn left): yaw should decrease
        player.update(0.1f, false, false, true, false, false, false, false);
        assertTrue(player.isMovingLeft());
        boat.update(0.1f, world, player);

        assertTrue(boat.getYaw() < initialYaw, "Boat should turn left (yaw decreases) when holding A");
        assertEquals(player.getCamera().getYaw(), boat.getYaw(), 0.01f, "Camera should rotate with boat");

        // 2. Holding D (turn right): yaw should increase
        float currentYaw = boat.getYaw();
        player.update(0.1f, false, false, false, true, false, false, false);
        assertTrue(player.isMovingRight());
        boat.update(0.1f, world, player);

        assertTrue(boat.getYaw() > currentYaw, "Boat should turn right (yaw increases) when holding D");
    }

    @Test
    public void testBoatMomentumRedirectsWhenTurning() {
        World world = new World(12345L);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                world.setBlock(x, 99, z, BlockType.WATER);
                world.setBlock(x, 100, z, BlockType.AIR);
                world.setBlock(x, 101, z, BlockType.AIR);
            }
        }
        Player player = new Player(world, 0, 100, 0);
        Boat boat = new Boat(0, 100, 0, -90.0f); // facing -Z
        boat.setDriver(player);
        player.setRidingBoat(boat);

        // Row forward along -Z
        for (int i = 0; i < 10; i++) {
            player.update(0.05f, true, false, false, false, false, false, false);
            boat.update(0.05f, world, player);
        }

        assertTrue(boat.getVelocity().z < -1.0f, "Should have forward velocity along -Z");

        // Turn 90 degrees right to face +X (yaw = 0)
        boat.setYaw(0.0f);
        player.getCamera().rotate(90.0f, 0.0f);

        // Continue rowing forward on new heading
        for (int i = 0; i < 10; i++) {
            player.update(0.05f, true, false, false, false, false, false, false);
            boat.update(0.05f, world, player);
        }

        // Velocity should now have redirected toward +X (forward)
        assertTrue(boat.getVelocity().x > 1.0f, "Velocity should redirect towards new heading +X");
    }
}
