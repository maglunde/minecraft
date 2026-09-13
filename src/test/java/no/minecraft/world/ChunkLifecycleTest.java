package no.minecraft.world;

import no.minecraft.settings.GameSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChunkLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void queuesNearestNegativeChunkFirstWithoutDuplicates() {
        int originalDistance = GameSettings.getInstance().getRenderDistance();
        try {
            GameSettings.getInstance().setRenderDistance(2);
            World world = new World(41L);
            world.cleanup();

            world.updateLoadedChunks(-3, -4);

            assertNotNull(world.getChunk(-3, -4), "Sentrumschunken skal ha høyest prioritet");
            int desiredChunkCount = (GameSettings.getInstance().getRenderDistance() * 2 + 1);
            desiredChunkCount *= desiredChunkCount;
            assertEquals(desiredChunkCount - World.CHUNK_LOAD_BUDGET, world.getPendingChunkLoadCount(),
                    "Hver ønsket chunk skal ligge én gang i køen");

            world.updateLoadedChunks(-3, -4);
            assertEquals(desiredChunkCount - World.CHUNK_LOAD_BUDGET * 2, world.getPendingChunkLoadCount(),
                    "En ny oppdatering skal bare behandle budsjettet, ikke legge til duplikater");
        } finally {
            GameSettings.getInstance().setRenderDistance(originalDistance);
        }
    }

    @Test
    void dirtyChunkIsSavedBeforeItIsUnloaded() throws Exception {
        int originalDistance = GameSettings.getInstance().getRenderDistance();
        try {
            GameSettings.getInstance().setRenderDistance(2);
            World world = new World(91L);
            world.setSaveDirectory(tempDir);
            world.ensureChunkGenerated(0, 0);
            world.setBlock(1, 20, 1, BlockType.DIAMOND_ORE);

            world.updateLoadedChunks(10, 0);

            assertNull(world.getChunk(0, 0));
            assertTrue(Files.isRegularFile(tempDir.resolve("dimensions/overworld/c.0.0.dat")));

            World reloaded = new World(91L);
            reloaded.setSaveDirectory(tempDir);
            reloaded.cleanup();
            reloaded.setSeed(91L);
            assertEquals(BlockType.DIAMOND_ORE, reloaded.ensureChunkGenerated(0, 0).getBlock(1, 20, 1));
        } finally {
            GameSettings.getInstance().setRenderDistance(originalDistance);
        }
    }
}
