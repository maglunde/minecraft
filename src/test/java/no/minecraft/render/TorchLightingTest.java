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

    private float getTorchLight(List<int[]> torches, int wx, int wy, int wz) {
        float maxLight = 0.0f;
        for (int[] t : torches) {
            int dx = wx - t[0];
            int dy = wy - t[1];
            int dz = wz - t[2];
            int distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= 196) {
                float dist = (float) Math.sqrt(distSq);
                float light = 1.0f - (dist / 14.0f);
                if (light > maxLight) {
                    maxLight = light;
                }
            }
        }
        return maxLight;
    }
}
