package no.minecraft.world;

import no.minecraft.settings.GameSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkUnloadingTest {

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
        try {
            GameSettings.getInstance().setRenderDistance(3);
            World world = new World(12345L);
            world.updateLoadedChunks(0, 0);
            world.setBlock(1, 20, 1, BlockType.GRASS); // player edit -> dirty

            world.updateLoadedChunks(6, 0);
            assertNotNull(world.getChunk(0, 0), "Skitten chunk må holdes i minnet til den er lagret");

            world.markAllChunksSaved(); // simulerer autosave
            world.updateLoadedChunks(6, 0);
            assertNull(world.getChunk(0, 0), "Lagret chunk skal kunne evikteres");

            // Returning: chunk is marked saved but no save file exists -> deterministic regen fallback
            world.updateLoadedChunks(0, 0);
            assertNotNull(world.getChunk(0, 0));
        } finally {
            GameSettings.getInstance().setRenderDistance(originalRd);
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
