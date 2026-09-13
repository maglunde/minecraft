package no.minecraft.world.gen;

import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.World;
import no.minecraft.world.gen.noise.OctaveNoiseSampler;
import no.minecraft.world.gen.noise.PerlinNoiseSampler;

/**
 * Autentisk, optimalisert 3D-terrenggenerator for Overworld i Minecraft Java Edition-klonen.
 * Bruker 3D-tetthet, trilinær interpolasjon i 4x8x4 celler, 3D-huleutskjæring,
 * biome-basert lagdeling (Surface Builder) og realistisk malmfordeling.
 */
public class OverworldGenerator {

    public static final int CELL_SIZE_X = 4;
    public static final int CELL_SIZE_Y = 8;
    public static final int CELL_SIZE_Z = 4;

    public static final int CELLS_X = Chunk.SIZE_X / CELL_SIZE_X; // 4
    public static final int CELLS_Y = Chunk.SIZE_Y / CELL_SIZE_Y; // 8
    public static final int CELLS_Z = Chunk.SIZE_Z / CELL_SIZE_Z; // 4

    private final long seed;
    private final World world;

    // 3D Støygeneratorer for huler, terreng og malmer
    private final OctaveNoiseSampler terrain3DNoise;
    private final OctaveNoiseSampler caveNoise1;
    private final OctaveNoiseSampler caveNoise2;
    private final OctaveNoiseSampler caveDensityNoise;
    private final OctaveNoiseSampler oreNoise;
    private final PerlinNoiseSampler surfaceNoise;

    public OverworldGenerator(long seed, World world) {
        this.seed = seed;
        this.world = world;

        this.terrain3DNoise = new OctaveNoiseSampler(seed ^ 0x54455252L, 4, 0.5, 2.0);
        this.caveNoise1 = new OctaveNoiseSampler(seed ^ 0x43415645L, 3, 0.5, 2.0);
        this.caveNoise2 = new OctaveNoiseSampler(seed ^ 0x43415646L, 3, 0.5, 2.0);
        this.caveDensityNoise = new OctaveNoiseSampler(seed ^ 0x43415647L, 2, 0.5, 2.0);
        this.oreNoise = new OctaveNoiseSampler(seed ^ 0x4F524553L, 3, 0.5, 2.0);
        this.surfaceNoise = new PerlinNoiseSampler(seed ^ 0x53555246L);
    }

    public long getSeed() {
        return seed;
    }

    /**
     * 3D Tetthetsfunksjon for terrenget: density(x, y, z) = noise3D - bias(y).
     * Positiv tetthet betyr fast grunn, negativ betyr luft eller vann.
     */
    public double sampleDensity(int x, int y, int z) {
        int height = world.getTerrainHeight(x, z);
        double bias = (double) (y - height) / 8.0;
        double n3D = terrain3DNoise.sample3D(x * 0.03, y * 0.04, z * 0.03);
        double density = (n3D * 0.6) - bias;

        if (y < height - 3 && y > 1) {
            if (isOverworldCave(x, y, z, height)) {
                density -= 2.0;
            }
        }
        return density;
    }

    /**
     * Genererer en komplett Overworld chunk med trilinær interpolasjon og Surface Builder.
     */
    public void generateChunk(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        int startX = cx * Chunk.SIZE_X;
        int startZ = cz * Chunk.SIZE_Z;

        // 1. Trilinær interpolasjon for 3D hule- og tetthetsfelt
        // 5 x 9 x 5 = 225 målinger per chunk for 4x8x4 celler
        double[][][] caveGrid = new double[CELLS_X + 1][CELLS_Y + 1][CELLS_Z + 1];
        for (int cellX = 0; cellX <= CELLS_X; cellX++) {
            int wx = startX + cellX * CELL_SIZE_X;
            for (int cellZ = 0; cellZ <= CELLS_Z; cellZ++) {
                int wz = startZ + cellZ * CELL_SIZE_Z;
                for (int cellY = 0; cellY <= CELLS_Y; cellY++) {
                    int y = cellY * CELL_SIZE_Y;
                    caveGrid[cellX][cellY][cellZ] = sampleCaveDensity(wx, y, wz);
                }
            }
        }

        // 2. Kolonner og overflate
        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            int wx = startX + lx;
            int cellX = lx / CELL_SIZE_X;
            double xAlpha = (double) (lx % CELL_SIZE_X) / CELL_SIZE_X;

            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int wz = startZ + lz;
                int cellZ = lz / CELL_SIZE_Z;
                double zAlpha = (double) (lz % CELL_SIZE_Z) / CELL_SIZE_Z;

                int height = world.getTerrainHeight(wx, wz);
                String biome = world.getBiomeName(wx, height, wz);

                // Bunnlag: Bedrock på Y=0
                chunk.setBlock(lx, 0, lz, BlockType.BEDROCK);

                // Underjordisk kjerne med 3D-huler og malmer
                for (int y = 1; y < height - 3; y++) {
                    int cellY = y / CELL_SIZE_Y;
                    double yAlpha = (double) (y % CELL_SIZE_Y) / CELL_SIZE_Y;

                    // Les de 8 hjørnene i gjeldende 4x8x4 celle for lynrask trilinær lerp
                    double c000 = caveGrid[cellX][cellY][cellZ];
                    double c100 = caveGrid[cellX + 1][cellY][cellZ];
                    double c010 = caveGrid[cellX][cellY + 1][cellZ];
                    double c110 = caveGrid[cellX + 1][cellY + 1][cellZ];
                    double c001 = caveGrid[cellX][cellY][cellZ + 1];
                    double c101 = caveGrid[cellX + 1][cellY][cellZ + 1];
                    double c011 = caveGrid[cellX][cellY + 1][cellZ + 1];
                    double c111 = caveGrid[cellX + 1][cellY + 1][cellZ + 1];

                    double d00 = lerp(yAlpha, c000, c010);
                    double d01 = lerp(yAlpha, c001, c011);
                    double d10 = lerp(yAlpha, c100, c110);
                    double d11 = lerp(yAlpha, c101, c111);

                    double d0 = lerp(zAlpha, d00, d01);
                    double d1 = lerp(zAlpha, d10, d11);
                    double caveSample = lerp(xAlpha, d0, d1);

                    // Sjekk om blokken skjæres ut som hule
                    boolean isCave = isOverworldCave(wx, y, wz, height) || (caveSample < -0.45);

                    if (isCave) {
                        chunk.setBlock(lx, y, lz, (y <= 3) ? BlockType.LAVA : BlockType.AIR);
                    } else {
                        chunk.setBlock(lx, y, lz, getUndergroundBlock(wx, y, wz, biome));
                    }
                }

                // 3. Biome Surface Builder
                applySurface(chunk, lx, lz, wx, wz, height, biome);
            }
        }
    }

    private double sampleCaveDensity(int wx, int y, int wz) {
        if (y <= 1 || y >= Chunk.SIZE_Y - 5) return 1.0;
        double n1 = caveNoise1.sample3D(wx * 0.035, y * 0.045, wz * 0.035);
        double n2 = caveNoise2.sample3D(wx * 0.035 + 100.0, y * 0.045 + 100.0, wz * 0.035 + 100.0);
        return (n1 * n1 + n2 * n2) - 0.5;
    }

    /**
     * Autentisk 3D hulealgoritme (Cheese & Spaghetti caves).
     */
    public boolean isOverworldCave(int wx, int y, int wz, int height) {
        if (y <= 1 || y >= height - 3) return false;
        if (height <= World.SEA_LEVEL && y >= height - 6) return false;

        // Regional huletetthet
        double density = caveDensityNoise.sample2D(wx * 0.0070, wz * 0.0070) * 0.5 + 0.5;
        double threshold = 0.045 + 0.030 * density;

        // To kontinuerlige 3D-orm-tunneler
        double n1 = caveNoise1.sample3D(wx * 0.07 + y * 0.035, y * 0.05, wz * 0.07 - y * 0.035);
        double n2 = caveNoise2.sample3D(wx * 0.07 - y * 0.030, y * 0.05, wz * 0.07 + y * 0.030);
        if (n1 * n1 + n2 * n2 < threshold) return true;

        // Store åpne kaverner i midtre dyp
        double cav = caveDensityNoise.sample3D(wx * 0.020, y * 0.025, wz * 0.020 + y * 0.02);
        return y > 6 && y < 34 && cav > 0.62;
    }

    /**
     * Malmfordeling (Ores) og bergarter under jorden.
     */
    public BlockType getUndergroundBlock(int wx, int y, int wz, String biome) {
        int cx2 = Math.floorDiv(wx, 2);
        int cy2 = Math.floorDiv(y, 2);
        int cz2 = Math.floorDiv(wz, 2);

        long clusterHash = ((long) cx2 * 3129871L) ^ ((long) cz2 * 116129781L) ^ ((long) cy2 * 8429183L) ^ seed;
        clusterHash = (clusterHash ^ (clusterHash >>> 16)) * 0x45d9f3bL;
        clusterHash = clusterHash ^ (clusterHash >>> 16);
        int clusterType = (int) Math.abs(clusterHash % 10000);

        long blockHash = ((long) wx * 918273L) ^ ((long) wz * 482917L) ^ ((long) y * 182739L);
        int blockVar = (int) Math.abs(blockHash % 10);

        // Diamantmalm: Dypest (y <= 14), sjelden
        if (y <= 14 && clusterType >= 10 && clusterType <= 13 && blockVar < 7) {
            return BlockType.DIAMOND_ORE;
        }

        // Gullmalm: Dype lag (y <= 24), samt ekstra hyppig i Badlands (opp til y <= 60)
        boolean isBadlands = biome != null && biome.equals("minecraft:badlands");
        if ((y <= 24 && clusterType >= 20 && clusterType <= 28 && blockVar < 7) ||
            (isBadlands && y <= 60 && clusterType >= 20 && clusterType <= 38 && blockVar < 8)) {
            return BlockType.GOLD_ORE;
        }

        // Jernmalm: Både under jorden og i fjelltopper
        if (clusterType >= 40 && clusterType <= 85 && blockVar < 8) {
            return BlockType.IRON_ORE;
        }

        // Kullmalm: Svært utbredt under jorden og i fjellet
        if (clusterType >= 100 && clusterType <= 170 && blockVar < 8) {
            return BlockType.COAL_ORE;
        }

        // Gruslommer (Gravel veins)
        if (clusterType >= 180 && clusterType <= 195) {
            return BlockType.GRAVEL;
        }

        return BlockType.STONE;
    }

    /**
     * Surface Builder for lagdeling tilpasset gjeldende biome.
     */
    private void applySurface(Chunk chunk, int lx, int lz, int wx, int wz, int height, String biome) {
        if (biome.equals("minecraft:ocean")) {
            // Sjøbunn: Sand og grus
            boolean isGravel = (Math.abs(wx * 7 + wz * 13) % 4 == 0);
            for (int y = Math.max(1, height - 3); y <= height; y++) {
                chunk.setBlock(lx, y, lz, isGravel ? BlockType.GRAVEL : BlockType.SAND);
            }
            // Fyll vann opp til SEA_LEVEL
            for (int y = height + 1; y <= World.SEA_LEVEL; y++) {
                chunk.setBlock(lx, y, lz, BlockType.WATER);
            }
        } else if (biome.equals("minecraft:desert")) {
            // Sandstein under sand
            for (int y = Math.max(1, height - 6); y < height - 2; y++) {
                chunk.setBlock(lx, y, lz, BlockType.SANDSTONE);
            }
            // Sandlag på toppen
            for (int y = Math.max(1, height - 2); y <= height; y++) {
                chunk.setBlock(lx, y, lz, BlockType.SAND);
            }
        } else if (biome.equals("minecraft:badlands")) {
            // Striert terrakotta med rød sand på toppen
            for (int y = Math.max(1, height - 8); y < height; y++) {
                chunk.setBlock(lx, y, lz, world.getMesaBandBlock(wx, y, wz));
            }
            boolean topSand = ((wx * 7 + wz * 13) % 4 != 0);
            chunk.setBlock(lx, height, lz, topSand ? BlockType.RED_SAND : world.getMesaBandBlock(wx, height, wz));
        } else if (biome.equals("minecraft:snowy_plains") || biome.equals("minecraft:snowy_tundra") || biome.equals("minecraft:snowy_mountains")) {
            for (int y = Math.max(1, height - 3); y < height; y++) {
                chunk.setBlock(lx, y, lz, BlockType.DIRT);
            }
            if (height > 0 && height < Chunk.SIZE_Y) {
                chunk.setBlock(lx, height, lz, BlockType.SNOW_BLOCK);
            }
        } else if (biome.equals("minecraft:mountains")) {
            if (height >= 30) {
                // Stein og malm på høye fjelltopper
                for (int y = Math.max(1, height - 3); y <= height; y++) {
                    chunk.setBlock(lx, y, lz, getUndergroundBlock(wx, y, wz, biome));
                }
            } else {
                // Lavere fjellskråninger: Jord og gress
                for (int y = Math.max(1, height - 3); y < height; y++) {
                    chunk.setBlock(lx, y, lz, BlockType.DIRT);
                }
                chunk.setBlock(lx, height, lz, BlockType.GRASS);
            }
        } else {
            // Sletter, skog, savanne, bjørkeskog
            boolean isBeach = (height <= World.SEA_LEVEL + 1);
            for (int y = Math.max(1, height - 3); y < height; y++) {
                chunk.setBlock(lx, y, lz, isBeach ? BlockType.SAND : BlockType.DIRT);
            }
            if (height > 0 && height < Chunk.SIZE_Y) {
                chunk.setBlock(lx, height, lz, isBeach ? BlockType.SAND : BlockType.GRASS);
            }
        }
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
