package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnderPearlTest {

    @Test
    public void pearlTeleportsPlayerOnImpact() {
        World world = new World(12345L);
        Player player = new Player(world, 0.5f, 16.9f, 0.5f);
        player.setHealth(20);
        // Clear the test area and build a floor at y=15 with a thin wall at x=3, y=17
        for (int x = 0; x <= 4; x++) {
            for (int y = 14; y <= 22; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
            world.setBlock(x, 15, 0, BlockType.STONE);
        }
        world.setBlock(3, 17, 0, BlockType.STONE);

        // Pearl flies at y=17.5 and hits the thin wall at x=3
        EnderPearl pearl = new EnderPearl(0.5f, 17.5f, 0.5f, 20.0f, 0.0f, 0.0f, player);
        for (int i = 0; i < 20 && !pearl.isDead(); i++) {
            pearl.update(1.0f / 60.0f, world, player);
        }

        assertTrue(pearl.isDead(), "Perlen skal treffe veggen");
        // Lands on the floor in front of the wall (x=2 column), never inside the wall
        assertEquals(2.5f, player.getPosition().x, 0.1f, "Player skal lande på gulvet foran veggen, ikke inne i den");
        assertTrue(player.getPosition().y >= 16.0f && player.getPosition().y <= 16.2f,
                "Player skal stå på gulvet (y=" + player.getPosition().y + ")");
        assertEquals(15, player.getHealth(), "Player skal ta 2.5 hjerter (5) skade");
    }

    @Test
    public void pearlDoesNotDamagePlayerBeforeImpact() {
        World world = new World(12345L);
        Player player = new Player(world, 0.5f, 20.9f, 0.5f);
        player.setHealth(20);
        for (int x = 0; x <= 6; x++) {
            for (int y = 19; y <= 24; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
        }

        EnderPearl pearl = new EnderPearl(0.5f, 20.5f, 0.5f, 10.0f, 0.0f, 0.0f, player);
        pearl.update(1.0f / 60.0f, world, player);

        assertEquals(20, player.getHealth(), "Perlen skal ikke skade før treff");
        assertFalse(pearl.isDead());
    }

    @Test
    public void pearlFallsAndLandsOnGround() {
        World world = new World(12345L);
        Player player = new Player(world, 0.5f, 20.9f, 0.5f);
        player.setHealth(20);
        for (int x = 0; x <= 2; x++) {
            for (int y = 15; y <= 22; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
        }
        // Floor at y=15
        for (int x = 0; x <= 2; x++) {
            world.setBlock(x, 15, 0, BlockType.STONE);
        }

        // Pearl thrown straight up, falls back down onto the floor
        EnderPearl pearl = new EnderPearl(0.5f, 18.5f, 0.5f, 0.0f, 8.0f, 0.0f, player);
        for (int i = 0; i < 300 && !pearl.isDead(); i++) {
            pearl.update(1.0f / 60.0f, world, player);
        }

        assertTrue(pearl.isDead(), "Perlen skal treffe gulvet");
        assertTrue(player.getPosition().y >= 15.9f && player.getPosition().y <= 16.2f,
                "Player skal lande oppå gulvet (y=" + player.getPosition().y + ")");
        assertEquals(15, player.getHealth());
    }

    @Test
    public void pearlNeverFallsIntoVoid() {
        World world = new World(12345L);
        Player player = new Player(world, 0.5f, 20.9f, 0.5f);
        player.setHealth(20);
        // Clear the whole column down to bedrock (y=0), leaving nothing but the void
        for (int x = 0; x <= 2; x++) {
            for (int y = 1; y <= 24; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
        }

        EnderPearl pearl = new EnderPearl(0.5f, 20.5f, 0.5f, 0.0f, -30.0f, 0.0f, player);
        for (int i = 0; i < 300 && !pearl.isDead(); i++) {
            pearl.update(1.0f / 60.0f, world, player);
        }

        assertTrue(pearl.isDead(), "Perlen skal treffe bedrocks");
        assertTrue(player.getPosition().y >= 1.0f,
                "Player skal lande på bedrock, aldri falle i void (y=" + player.getPosition().y + ")");
        assertEquals(15, player.getHealth(), "Player skal ta 2.5 hjerter skade for landingen");
    }
}
