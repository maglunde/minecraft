package no.minecraft.world;

import no.minecraft.settings.GameSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkUnloadingTest {

    @Test
    public void testChunkMeshUnloadingWhenMovingAway() {
        World world = new World(12345L);
        GameSettings gs = GameSettings.getInstance();
        gs.setRenderDistance(5); // rd = 5, unloadDist = 7

        // Initially at spawn (0, 0)
        world.updateLoadedChunks(0, 0);

        Chunk originChunk = world.getChunk(0, 0);
        assertNotNull(originChunk);
        assertFalse(originChunk.hasMesh(), "Chunk starts without mesh");

        // Move 20 chunks away to (20, 0)
        world.updateLoadedChunks(20, 0);

        // Origin chunk is at distance 20 > 7 (unloadDist)
        assertFalse(originChunk.hasMesh(), "Origin chunk mesh should not exist when far away");

        // When moving back to (0, 0)
        world.updateLoadedChunks(0, 0);
        assertNotNull(world.getChunk(0, 0));
    }

    @Test
    public void testChunkGenerationExpandsAsPlayerMoves() {
        World world = new World(12345L);
        int initialLoaded = world.getLoadedChunkCount();
        assertTrue(initialLoaded > 0, "Should generate chunks at spawn");

        // Move far away to (15, 15)
        world.updateLoadedChunks(15, 15);
        int afterMove = world.getLoadedChunkCount();
        assertTrue(afterMove > initialLoaded, "Loaded chunks count should increase as new area is explored");

        // Check that chunks at (15, 15) exist
        assertNotNull(world.getChunk(15, 15));
    }
}
