package no.minecraft.render;

import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TorchLightingTest {

    @Test
    public void testTorchCollection() {
        World world = new World(12345L);
        Chunk chunk = world.getOrCreateChunk(0, 0);

        chunk.setBlock(5, 20, 5, BlockType.TORCH);
        chunk.setBlock(10, 20, 10, BlockType.TORCH);

        List<int[]> torches = new ArrayList<>();
        chunk.collectTorches(torches);

        assertEquals(2, torches.size());
        assertArrayEquals(new int[]{5, 20, 5}, torches.get(0));
        assertArrayEquals(new int[]{10, 20, 10}, torches.get(1));
    }

    @Test
    public void testTorchLightReachAndFalloff() {
        World world = new World(12345L);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        chunk.setBlock(0, 10, 0, BlockType.TORCH);

        List<int[]> torches = new ArrayList<>();
        chunk.collectTorches(torches);

        // Distance 0: Torch center has full illumination
        float lightAtSource = getTorchLight(torches, 0, 10, 0);
        assertEquals(1.0f, lightAtSource, 0.001f);

        // Distance 7 blocks: approx 50% brightness
        float lightAt7 = getTorchLight(torches, 7, 10, 0);
        assertEquals(0.5f, lightAt7, 0.01f);

        // Distance 14 blocks or more: no torch light
        float lightAt14 = getTorchLight(torches, 14, 10, 0);
        assertEquals(0.0f, lightAt14, 0.001f);

        float lightAt15 = getTorchLight(torches, 15, 10, 0);
        assertEquals(0.0f, lightAt15, 0.001f);
    }

    @Test
    public void testLavaCollectionAndReach() {
        World world = new World(12345L);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        chunk.setBlock(5, 20, 5, BlockType.LAVA);

        List<int[]> sources = new ArrayList<>();
        chunk.collectLava(sources);

        int[] placedLava = sources.stream().filter(s -> s[1] == 20).findFirst().orElse(null);
        assertNotNull(placedLava);
        assertEquals(5, placedLava[0]);
        assertEquals(20, placedLava[1]);
        assertEquals(5, placedLava[2]);
        assertEquals(15, placedLava[3]);

        List<int[]> testSource = List.of(placedLava);
        // Lava has radius 15, so distance 0 is 1.0
        assertEquals(1.0f, getTorchLight(testSource, 5, 20, 5), 0.001f);
        // Distance 14: torch is 0.0, but lava still gives light (1 - 14/15 = 0.067)
        float lightAt14 = getTorchLight(testSource, 5 + 14, 20, 5);
        assertTrue(lightAt14 > 0.05f, "Lava skal lyse opp til radius 15, fikk: " + lightAt14);
        // Distance 15 or more: 0.0
        assertEquals(0.0f, getTorchLight(testSource, 5 + 15, 20, 5), 0.001f);
    }

    @Test
    public void testLavaPreventsDarkness() {
        World world = new World(12345L);
        world.setBlock(10, 20, 10, BlockType.LAVA);
        // Lava forhindrer mørke (og dermed fiendtlige mobs) i en radius på 5 blokker
        assertFalse(world.isDarkAt(10, 20, 10), "Lava-blokken er opplyst");
        assertFalse(world.isDarkAt(12, 20, 10), "2 blokker unna lava skal ikke være mørkt");
    }

    private float getTorchLight(List<int[]> torches, int wx, int wy, int wz) {
        float maxLight = 0.0f;
        for (int[] t : torches) {
            int dx = wx - t[0];
            int dy = wy - t[1];
            int dz = wz - t[2];
            int distSq = dx * dx + dy * dy + dz * dz;
            float radius = (t.length >= 4) ? t[3] : 14.0f;
            float radiusSq = radius * radius;
            if (distSq <= radiusSq) {
                float dist = (float) Math.sqrt(distSq);
                float light = 1.0f - (dist / radius);
                if (light > maxLight) {
                    maxLight = light;
                }
            }
        }
        return maxLight;
    }
}
