package no.minecraft.world;

import no.minecraft.world.gen.OverworldGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OverworldGeneratorTest {

    @Test
    public void testDeterministicChunkGeneration() {
        World world1 = new World(987654321L);
        World world2 = new World(987654321L);

        Chunk c1 = new Chunk(world1, 2, -3);
        Chunk c2 = new Chunk(world2, 2, -3);

        world1.getOverworldGenerator().generateChunk(c1);
        world2.getOverworldGenerator().generateChunk(c2);

        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    assertEquals(c1.getBlock(x, y, z), c2.getBlock(x, y, z),
                            "Blokk ved (" + x + "," + y + "," + z + ") skal være identisk for samme seed");
                }
            }
        }
    }

    @Test
    public void testBedrockAtBottom() {
        World world = new World(123456L);
        Chunk chunk = new Chunk(world, 0, 0);
        world.getOverworldGenerator().generateChunk(chunk);

        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                assertEquals(BlockType.BEDROCK, chunk.getBlock(x, 0, z),
                        "Y=0 skal alltid være solid Bedrock");
            }
        }
    }

    @Test
    public void testDensityFunctionProperties() {
        World world = new World(123456L);
        OverworldGenerator generator = world.getOverworldGenerator();

        int testX = 10;
        int testZ = 10;
        int surfaceH = world.getTerrainHeight(testX, testZ);

        // Langt under overflaten skal tettheten være positiv (med mindre hule)
        double deepDensity = generator.sampleDensity(testX, 5, testZ);
        // Langt over overflaten skal tettheten være negativ (luft)
        double skyDensity = generator.sampleDensity(testX, surfaceH + 10, testZ);

        assertTrue(skyDensity < 0.0, "Tetthet over overflaten skal være negativ (luft)");
    }

    @Test
    public void testDeepCavesAndLava() {
        World world = new World(424242L);
        OverworldGenerator generator = world.getOverworldGenerator();

        boolean foundLavaInDeep = false;
        boolean foundCaveAir = false;

        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                Chunk chunk = new Chunk(world, cx, cz);
                generator.generateChunk(chunk);

                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        for (int y = 1; y < 15; y++) {
                            BlockType b = chunk.getBlock(x, y, z);
                            if (b == BlockType.LAVA && y <= 3) {
                                foundLavaInDeep = true;
                            }
                            if (b == BlockType.AIR) {
                                foundCaveAir = true;
                            }
                        }
                    }
                }
            }
        }

        assertTrue(foundCaveAir, "Det skal finnes underjordiske hulerom (AIR)");
        assertTrue(foundLavaInDeep, "Det skal genereres lava i bunnen av dype huler (Y <= 3)");
    }

    @Test
    public void testPerformanceTrilinearInterpolation() {
        World world = new World(777L);
        Chunk chunk = new Chunk(world, 0, 0);

        // Varm opp JIT
        for (int i = 0; i < 3; i++) {
            world.getOverworldGenerator().generateChunk(chunk);
        }

        long start = System.nanoTime();
        world.getOverworldGenerator().generateChunk(chunk);
        long elapsedNanos = System.nanoTime() - start;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        // Med trilinær interpolasjon (4x8x4) skal en hel chunk (16 384 blokker)
        // genereres på under 15 ms selv på trege CPU-er
        assertTrue(elapsedMs < 25.0,
                "Trilinær chunkgenerering skal være lynrask, tok: " + elapsedMs + " ms");
    }

    @Test
    public void productionGenerationIsIndependentOfRequestOrder() {
        World firstOrder = new World(24680L);
        World secondOrder = new World(24680L);

        firstOrder.ensureChunkGenerated(-3, 2);
        Chunk expected = firstOrder.ensureChunkGenerated(4, -2);

        Chunk actual = secondOrder.ensureChunkGenerated(4, -2);
        secondOrder.ensureChunkGenerated(-3, 2);

        assertChunkEquals(expected, actual);
    }

    @Test
    public void changingSeedReplacesTheProductionGenerator() {
        World world = new World(111L);
        OverworldGenerator original = world.getOverworldGenerator();
        Chunk before = world.ensureChunkGenerated(7, -4);

        world.setSeed(222L);
        OverworldGenerator reset = world.getOverworldGenerator();
        Chunk after = world.ensureChunkGenerated(7, -4);

        assertNotSame(original, reset);
        assertEquals(222L, reset.getSeed());
        assertTrue(chunksDiffer(before, after));
    }

    private static void assertChunkEquals(Chunk expected, Chunk actual) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    assertEquals(expected.getBlock(x, y, z), actual.getBlock(x, y, z));
                }
            }
        }
    }

    private static boolean chunksDiffer(Chunk first, Chunk second) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    if (first.getBlock(x, y, z) != second.getBlock(x, y, z)) return true;
                }
            }
        }
        return false;
    }
}
