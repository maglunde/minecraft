package no.minecraft.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class BiomeGenerationTest {

    @Test
    public void testSnowBiomesAreRare() {
        World world = new World(12345L);
        int totalSamples = 10000;
        int snowSamples = 0;
        Random rand = new Random(42);

        for (int i = 0; i < totalSamples; i++) {
            int x = rand.nextInt(40000) - 20000;
            int z = rand.nextInt(40000) - 20000;
            String biome = world.getBiomeName(x, 20, z);
            if (biome.equals("minecraft:snowy_tundra") || biome.equals("minecraft:snowy_plains") || biome.equals("minecraft:snowy_mountains")) {
                snowSamples++;
            }
        }

        double snowPercentage = (double) snowSamples / totalSamples * 100.0;
        assertTrue(snowPercentage < 5.0, "Snow biomes should be rare (< 5%), but was " + snowPercentage + "%");
        assertTrue(snowPercentage > 0.1, "Snow biomes should still generate in rare instances (> 0.1%), but was " + snowPercentage + "%");
    }

    @Test
    public void testNewBiomesGenerate() {
        World world = new World(123456789L);
        Map<String, Integer> biomeCounts = new HashMap<>();

        for (int x = -10000; x <= 10000; x += 100) {
            for (int z = -10000; z <= 10000; z += 100) {
                String biome = world.getBiomeName(x, 20, z);
                biomeCounts.put(biome, biomeCounts.getOrDefault(biome, 0) + 1);
            }
        }

        assertTrue(biomeCounts.getOrDefault("minecraft:tall_birch_forest", 0) > 0, "Tall birch forest should generate");
        assertTrue(biomeCounts.getOrDefault("minecraft:savanna", 0) > 0, "Savanna should generate");
        assertTrue(biomeCounts.getOrDefault("minecraft:badlands", 0) > 0, "Badlands/Mesa should generate");
        assertTrue(biomeCounts.getOrDefault("minecraft:dark_forest", 0) > 0, "Dark forest should generate");
    }

    @Test
    public void testBiomesAreExpansive() {
        World world = new World(54321L);
        // Step in small 5-block increments across 300 blocks
        // In an expansive biome system, we should have long contiguous runs of the same biome
        int maxRun = 0;
        int currentRun = 0;
        String prevBiome = null;

        for (int step = 0; step < 200; step++) {
            int x = step * 10;
            int z = 0;
            String biome = world.getBiomeName(x, 20, z);
            if (biome.equals(prevBiome)) {
                currentRun++;
                if (currentRun > maxRun) {
                    maxRun = currentRun;
                }
            } else {
                prevBiome = biome;
                currentRun = 1;
            }
        }

        // At 10m per step, a run of 20+ samples = 200+ contiguous blocks in a single biome
        assertTrue(maxRun >= 20, "Biomes should be expansive with contiguous stretches of 200+ blocks, max run was " + (maxRun * 10) + " blocks");
    }

    @Test
    public void testNewBlocksProperties() {
        assertEquals(BlockType.ToolType.AXE, BlockType.BIRCH_LOG.getEffectiveTool());
        assertEquals(BlockType.ToolType.AXE, BlockType.ACACIA_LOG.getEffectiveTool());
        assertEquals(BlockType.ToolType.AXE, BlockType.DARK_OAK_LOG.getEffectiveTool());
        assertEquals(BlockType.ToolType.SWORD, BlockType.BIRCH_LEAVES.getEffectiveTool());
        assertEquals(BlockType.ToolType.SHOVEL, BlockType.RED_SAND.getEffectiveTool());
        assertEquals(BlockType.ToolType.PICKAXE, BlockType.TERRACOTTA.getEffectiveTool());
        assertTrue(BlockType.TERRACOTTA.requiresToolForDrop());
        assertTrue(BlockType.RED_TERRACOTTA.requiresToolForDrop());
        assertFalse(BlockType.BIRCH_LOG.requiresToolForDrop());
        assertFalse(BlockType.RED_SAND.requiresToolForDrop());
    }

    @Test
    public void testMesaColorBands() {
        World world = new World(999L);
        BlockType b1 = world.getMesaBandBlock(0, 20, 0);
        BlockType b2 = world.getMesaBandBlock(0, 23, 0);
        assertNotNull(b1);
        assertNotNull(b2);
        assertTrue(b1.isSolid());
        assertTrue(b2.isSolid());
    }
}
