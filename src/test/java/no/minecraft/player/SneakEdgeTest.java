package no.minecraft.player;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SneakEdgeTest {

    /** Flat stone platform at y=30, x in [-4, platformMaxX], z in [-4, 4]. Everything else cleared. */
    private World flatWorld(int platformMaxX) {
        World w = new World(12345L);
        for (int x = -4; x <= 34; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 26; y <= 45; y++) {
                    w.setBlock(x, y, z, BlockType.AIR);
                }
            }
        }
        for (int x = -4; x <= platformMaxX; x++) {
            for (int z = -4; z <= 4; z++) {
                w.setBlock(x, 30, z, BlockType.STONE);
            }
        }
        return w;
    }

    private Player settledPlayer(World world, float x, float z) {
        Player player = new Player(world, x, 30.9f, z);
        player.getCamera().setRotation(0f, 0f); // yaw 0 = forward is +X
        for (int i = 0; i < 30; i++) {
            player.update(1.0f / 60.0f, false, false, false, false, false, false, false);
        }
        assertTrue(player.isOnGround(), "Player skal stå på platformen");
        return player;
    }

    private void sneakForward(Player player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.update(1.0f / 60.0f, true, false, false, false, false, true, false);
        }
    }

    private void walkForward(Player player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.update(1.0f / 60.0f, true, false, false, false, false, false, false);
        }
    }

    @Test
    public void sneakStopsAtLedge() {
        World world = flatWorld(2); // platform ends at x=2 (block [2,3)), edge plane at x=3
        Player player = settledPlayer(world, 0.5f, 0.5f);

        sneakForward(player, 120);

        assertTrue(player.getPosition().x > 0.9f, "Player skal ha beveget seg fremover");
        assertTrue(player.getPosition().x + Player.WIDTH / 2.0f <= 3.02f,
                "Snikende player skal stoppe på kanten (x + halfWidth="
                        + (player.getPosition().x + Player.WIDTH / 2.0f) + ", edge=3.0)");
        assertTrue(player.isOnGround(), "Player skal fortsatt stå på blokken");
    }

    @Test
    public void walkingOffLedgeWithoutSneakFalls() {
        World world = flatWorld(2);
        Player player = settledPlayer(world, 0.5f, 0.5f);

        walkForward(player, 60);

        assertTrue(player.getPosition().x > 3.0f, "Uten sneak skal player gå av kanten");
        assertTrue(player.getPosition().y < 30.9f, "Player skal ha falt ned");
    }

    @Test
    public void sneakStillWalksOnFlatGround() {
        World world = flatWorld(30);
        Player player = settledPlayer(world, 0.5f, 0.5f);

        sneakForward(player, 60);

        assertTrue(player.getPosition().x > 1.2f, "Sneak skal fungere normalt på flat bakke");
        assertTrue(player.isOnGround());
    }

    @Test
    public void sneakDoesNotBlockJumpOffLedge() {
        World world = flatWorld(2);
        Player player = settledPlayer(world, 0.5f, 0.5f);
        float startX = player.getPosition().x;

        player.update(1.0f / 60.0f, true, false, false, false, true, true, false);

        assertFalse(player.isOnGround(), "Jump skal løfte player fra bakken");
        assertTrue(player.getPosition().x > startX, "Jump med sneak skal ikke blokkere horisontal bevegelse");
    }
}
