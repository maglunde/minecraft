package no.minecraft.world;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.settings.GameSettings;
import no.minecraft.world.save.WorldInfo;
import no.minecraft.world.save.WorldSaveManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkUnloadingTest {

    @TempDir
    Path tempDir;

    @Test
    public void testChunksEvictedFromMemoryWhenMovingAway() {
        int originalRd = GameSettings.getInstance().getRenderDistance();
        try {
            GameSettings.getInstance().setRenderDistance(3); // rd = 3, unloadDist = 5
            World world = new World(12345L);
            world.updateLoadedChunks(0, 0);
            assertNotNull(world.getChunk(0, 0));

            // Move to (6, 0): chunk (0, 0) at distance 6 > 5 must leave the chunk map entirely
            world.updateLoadedChunks(6, 0);
            assertNull(world.getChunk(0, 0), "Chunk (0,0) skal være eviktert fra minnet, ikke bare meshen");
        } finally {
            GameSettings.getInstance().setRenderDistance(originalRd);
        }
    }

    @Test
    public void testChunkRegeneratedWhenReturning() {
        int originalRd = GameSettings.getInstance().getRenderDistance();
        try {
            GameSettings.getInstance().setRenderDistance(3);
            World world = new World(12345L);
            world.updateLoadedChunks(0, 0);
            world.updateLoadedChunks(6, 0);
            assertNull(world.getChunk(0, 0));

            world.updateLoadedChunks(0, 0);
            assertNotNull(world.getChunk(0, 0), "Chunk skal re-skapes når player returnerer");
        } finally {
            GameSettings.getInstance().setRenderDistance(originalRd);
        }
    }

    @Test
    public void testDirtyChunkNotEvictedUntilSaved() {
        int originalRd = GameSettings.getInstance().getRenderDistance();
        Path originalSavesDir = WorldSaveManager.SAVES_DIR;
        try {
            WorldSaveManager.SAVES_DIR = tempDir;
            GameSettings.getInstance().setRenderDistance(3);
            World world = new World(12345L);
            Player player = new Player(world, 0, 10, 0);
            WorldInfo info = new WorldInfo("UnloadingTest", "unloading-test", 12345L,
                    GameMode.SURVIVAL, System.currentTimeMillis());
            world.updateLoadedChunks(0, 0);
            world.setBlock(1, 20, 1, BlockType.GRASS); // player edit -> dirty

            world.updateLoadedChunks(6, 0);
            assertNotNull(world.getChunk(0, 0), "Skitten chunk må holdes i minnet til den er lagret");

            assertTrue(WorldSaveManager.saveWorld(world, player, info));
            world.updateLoadedChunks(6, 0);
            assertNull(world.getChunk(0, 0), "Lagret chunk skal kunne evikteres");

            // Returning: chunk is loaded directly from its persistent file
            world.updateLoadedChunks(0, 0);
            assertNotNull(world.getChunk(0, 0));
        } finally {
            GameSettings.getInstance().setRenderDistance(originalRd);
            WorldSaveManager.SAVES_DIR = originalSavesDir;
        }
    }

    @Test
    public void testDecorationDoesNotCreatePhantomChunks() {
        int originalRd = GameSettings.getInstance().getRenderDistance();
        try {
            GameSettings.getInstance().setRenderDistance(3);
            World world = new World(12345L);
            world.updateLoadedChunks(0, 0);

            for (Map.Entry<Long, Chunk> entry : world.getDimensionChunks().get(Dimension.OVERWORLD).entrySet()) {
                assertTrue(world.getDimensionGenerated().get(Dimension.OVERWORLD).contains(entry.getKey()),
                        "Alle chunks i minnet må være fullt generert (ingen phantom-chunks fra border-dekorasjon)");
            }
        } finally {
            GameSettings.getInstance().setRenderDistance(originalRd);
        }
    }
}
