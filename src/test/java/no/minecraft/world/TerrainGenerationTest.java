package no.minecraft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TerrainGenerationTest {

    @Test
    public void sameSeedSameTerrain() {
        World w1 = new World(12345L);
        World w2 = new World(12345L);
        for (int x = -96; x <= 96; x += 3) {
            for (int z = -96; z <= 96; z += 3) {
                assertEquals(w1.getTerrainHeight(x, z), w2.getTerrainHeight(x, z),
                        "Samme seed skal gi identisk terreng ved (" + x + "," + z + ")");
            }
        }
    }

    @Test
    public void differentSeedsDiffer() {
        World w1 = new World(12345L);
        World w2 = new World(999L);
        int diff = 0;
        int total = 0;
        for (int x = -96; x <= 96; x += 3) {
            for (int z = -96; z <= 96; z += 3) {
                total++;
                if (w1.getTerrainHeight(x, z) != w2.getTerrainHeight(x, z)) diff++;
            }
        }
        assertTrue(diff > total * 0.6,
                "Ulike seeds skal gi vesentlig forskjellig terreng (" + diff + "/" + total + " ulike)");
    }

    @Test
    public void heightStaysInBounds() {
        World world = new World(12345L);
        for (int x = -128; x <= 128; x += 1) {
            for (int z = -128; z <= 128; z += 1) {
                int h = world.getTerrainHeight(x, z);
                assertTrue(h >= 5 && h <= Chunk.SIZE_Y - 7,
                        "Høyde utenfor bounds ved (" + x + "," + z + "): " + h);
            }
        }
    }

    @Test
    public void chunkBorderIsContinuous() {
        World world = new World(12345L);
        world.updateLoadedChunks(0, 0);

        // The generated surface at the border columns of chunk (0,0) and (1,0) must match
        // the terrain function exactly — terrain is a pure function of world coords, so a
        // seam at a chunk border would mean one of the chunks generated wrong
        for (int z = 0; z < 16; z++) {
            int h15 = world.getTerrainHeight(15, z);
            int h16 = world.getTerrainHeight(16, z);
            if (h15 > World.SEA_LEVEL) {
                assertEquals(h15, topSolid(world, 15, z), "Kolonne x=15 avviker fra terrengfunksjonen");
            }
            if (h16 > World.SEA_LEVEL) {
                assertEquals(h16, topSolid(world, 16, z), "Kolonne x=16 avviker fra terrengfunksjonen");
            }
        }
    }

    private int topSolid(World world, int x, int z) {
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            if (world.getBlock(x, y, z) != BlockType.AIR) return y;
        }
        return -1;
    }

    @Test
    public void riversAndOceansExist() {
        World world = new World(12345L);
        boolean foundWater = false;
        boolean foundLand = false;
        for (int x = -256; x <= 256; x += 2) {
            for (int z = -256; z <= 256; z += 2) {
                int h = world.getTerrainHeight(x, z);
                if (h <= World.SEA_LEVEL) foundWater = true;
                if (h > World.SEA_LEVEL + 5) foundLand = true;
            }
        }
        assertTrue(foundWater, "Det skal finnes hav og elver under hav-nivå");
        assertTrue(foundLand, "Det skal finnes land over hav-nivå");

        // Biome variety
        java.util.Set<String> biomes = new java.util.HashSet<>();
        for (int x = -256; x <= 256; x += 4) {
            for (int z = -256; z <= 256; z += 4) {
                int h = world.getTerrainHeight(x, z);
                biomes.add(world.getBiomeName(x, h, z));
            }
        }
        assertTrue(biomes.size() >= 4,
                "Det skal finnes minst 4 ulike biomer, fant: " + biomes);
    }

    @Test
    public void cavesVaryBySeed() {
        World w1 = new World(12345L);
        World w2 = new World(999L);
        int diff = 0;
        int total = 0;
        for (int x = -64; x <= 64; x += 2) {
            for (int z = -64; z <= 64; z += 2) {
                for (int y = 10; y <= 30; y += 2) {
                    total++;
                    if (w1.isOverworldCave(x, y, z, 40) != w2.isOverworldCave(x, y, z, 40)) diff++;
                }
            }
        }
        assertTrue(diff > total * 0.05,
                "Huler skal variere med seed (" + diff + "/" + total + " ulike)");
    }

    @Test
    public void spawnRemainsValid() {
        World world = new World(12345L);
        org.joml.Vector3f spawn = world.getSpawnPoint();
        int sx = (int) Math.floor(spawn.x);
        int sy = (int) Math.floor(spawn.y);
        int sz = (int) Math.floor(spawn.z);
        BlockType ground = world.getBlock(sx, sy - 1, sz);
        assertTrue(ground.isSolid(), "Blokken under spawn skal være solid, var: " + ground);
    }
}
