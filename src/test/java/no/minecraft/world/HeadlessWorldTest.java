package no.minecraft.world;

import no.minecraft.player.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeadlessWorldTest {
    @Test
    void worldLifecycleAndChunkLightingNeedNoGraphicsContext() {
        World world = new World(12345L);
        try {
            world.cleanup();
            Chunk chunk = world.getOrCreateChunk(-1, -1);
            assertTrue(chunk.isDirty());
            chunk.setDirty(false);
            chunk.setBlock(8, 62, 8, BlockType.DIAMOND_ORE);
            assertEquals(BlockType.DIAMOND_ORE, world.getBlock(-8, 62, -8));
            assertTrue(chunk.isDirty());
            assertTrue(chunk.needsSave());
            assertEquals(62, chunk.getColumnHeight(8, 8));
            assertEquals(15, chunk.getSkyLight(8, 63, 8));
            chunk.clearNeedsSave();
            world.updateLoadedChunks(30, 30);
            assertNull(world.getChunk(-1, -1));
            assertNotNull(world.getChunk(30, 30));
            Player player = new Player(world, 488, 63, 488);
            world.update(0.01f, player);
            world.setSeed(54321L);
            assertNotNull(world.getChunk(0, 0));
            world.cleanup();
            assertEquals(0, world.getLoadedChunkCount());
            world.cleanup();
        } finally {
            world.cleanup();
        }
    }

    @Test
    void worldBytecodeHasNoDirectGraphicsDependencies() throws IOException {
        // Inspect compiled classes, including nested classes and world subpackages.
        Path root = Path.of("target/classes/no/minecraft/world");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String bytecode = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(bytecode.contains("org/lwjgl/"), file + " depends on LWJGL");
                assertFalse(bytecode.contains("no/minecraft/render/"), file + " depends on rendering");
            }
        }
    }
}
