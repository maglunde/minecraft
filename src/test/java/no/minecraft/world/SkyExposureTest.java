package no.minecraft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SkyExposureTest {

    /** Clear the column (x,z) from yMin to the top of the world. */
    private void clearColumn(World world, int x, int z, int yMin) {
        for (int y = yMin; y < Chunk.SIZE_Y; y++) {
            world.setBlock(x, y, z, BlockType.AIR);
        }
    }

    @Test
    public void surfaceColumnIsExposed() {
        World world = new World(12345L);
        clearColumn(world, 0, 0, 19);
        world.setBlock(0, 20, 0, BlockType.STONE);

        assertTrue(world.isSkyExposed(0, 20, 0), "Overflateblokken ser himmelen");
        assertFalse(world.isSkyExposed(0, 19, 0), "Cellen under overflaten er blokkert");
        assertTrue(world.isSkyExposed(0, 21, 0), "Cellen over overflaten ser himmelen");
    }

    @Test
    public void caveCeilingBlocksSky() {
        World world = new World(12345L);
        clearColumn(world, 0, 0, 19);
        world.setBlock(0, 20, 0, BlockType.STONE);
        world.setBlock(0, 22, 0, BlockType.STONE);

        assertFalse(world.isSkyExposed(0, 21, 0), "Hulecelle mellom to steinlag er mørk");
    }

    @Test
    public void pillarSideIsLit() {
        World world = new World(12345L);
        clearColumn(world, 0, 0, 19);
        clearColumn(world, -1, 0, 19);
        clearColumn(world, 1, 0, 19);
        world.setBlock(0, 19, 0, BlockType.STONE);
        world.setBlock(0, 20, 0, BlockType.STONE);

        // The pillar's own column is blocked...
        assertFalse(world.isSkyExposed(0, 19, 0), "Pillarblokkens egen kolonne er blokkert");
        // ...but the air cell beside the pillar sees the sky, so the pillar's side stays lit
        assertTrue(world.isSkyExposed(-1, 19, 0), "Luftcellen ved siden av pillar ser himmelen");
    }

    @Test
    public void agreesWithIsOpenToSky() {
        World world = new World(12345L);
        for (int x = -32; x <= 32; x += 7) {
            for (int z = -32; z <= 32; z += 7) {
                for (int y = 5; y <= 40; y += 3) {
                    assertEquals(world.isOpenToSky(x, y, z), world.isSkyExposed(x, y, z),
                            "Uenighet ved (" + x + "," + y + "," + z + ")");
                }
            }
        }
    }

    @Test
    public void glassRoofDoesNotBlockSky() {
        World world = new World(12345L);
        clearColumn(world, 0, 0, 19);
        world.setBlock(0, 20, 0, BlockType.STONE);
        world.setBlock(0, 25, 0, BlockType.GLASS);

        assertTrue(world.isSkyExposed(0, 21, 0), "Glasstak blokkerer ikke himmelen");
    }

    @Test
    public void nonOverworldIsExposed() {
        World world = new World(12345L);
        world.setCurrentDimension(Dimension.NETHER);
        assertTrue(world.isSkyExposed(0, 10, 0), "Nether har konstant lys, alltid 'eksponert'");
    }

    @Test
    public void heightmapInvalidatesOnSetBlock() {
        World world = new World(12345L);
        clearColumn(world, 5, 5, 19);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        world.setBlock(5, 30, 5, BlockType.STONE);

        assertEquals(30, chunk.getColumnHeight(5, 5), "Heightmap skal være gyldig rett etter setBlock");

        world.setBlock(5, 40, 5, BlockType.STONE);
        assertEquals(40, chunk.getColumnHeight(5, 5), "Heightmap skal invalidates av setBlock");
    }

    @Test
    public void skyLightUnderLeavesIsSoftShade() {
        World world = new World(12345L);
        clearColumn(world, 5, 5, 19);
        world.setBlock(5, 20, 5, BlockType.GRASS);
        world.setBlock(5, 25, 5, BlockType.LEAVES);

        float lightUnderLeaves = world.getSkyLight(5, 21, 5);
        assertTrue(lightUnderLeaves >= 0.80f, "Lys under løvverk skal være myk skygge (>= 0.80), fikk: " + lightUnderLeaves);
    }

    @Test
    public void skyLightUnderOverhangPropagates() {
        World world = new World(12345L);
        clearColumn(world, 5, 5, 19);
        clearColumn(world, 6, 5, 19);
        world.setBlock(5, 20, 5, BlockType.STONE);
        world.setBlock(6, 20, 5, BlockType.STONE);
        world.setBlock(5, 22, 5, BlockType.STONE); // Overheng over (5, 21, 5)

        float lightUnderOverhang = world.getSkyLight(5, 21, 5);
        assertTrue(lightUnderOverhang >= 0.85f, "1 blokk under overheng skal motta dagslys fra nabocellen (>= 0.85), fikk: " + lightUnderOverhang);
    }

    @Test
    public void caveSkyLightFadesToZero() {
        World world = new World(12345L);
        // x=0 er åpen mot himmelen foran inngangen
        clearColumn(world, 0, 5, 9);
        world.setBlock(0, 9, 5, BlockType.STONE);
        world.setBlock(0, 10, 5, BlockType.AIR);

        // Tunnel fra x=1 til x=16
        for (int x = 1; x <= 16; x++) {
            clearColumn(world, x, 5, 9);
            world.setBlock(x, 9, 5, BlockType.STONE); // gulv
            world.setBlock(x, 11, 5, BlockType.STONE); // tak
            world.setBlock(x, 10, 4, BlockType.STONE); // nordvegg
            world.setBlock(x, 10, 6, BlockType.STONE); // sørvegg
            world.setBlock(x, 10, 5, BlockType.AIR);   // tunnelgang
        }
        world.setBlock(16, 10, 5, BlockType.STONE); // lukk enden

        float entranceLight = world.getSkyLight(1, 10, 5); // 1 blokk inn i tunnelen
        float deepCaveLight = world.getSkyLight(15, 10, 5); // 15 blokker inn i fjellet

        assertTrue(entranceLight >= 0.85f, "Åpning skal ha høyt dagslys (>= 0.85), fikk: " + entranceLight);
        assertEquals(0.0f, deepCaveLight, 0.05f, "Dyp hule skal falle ned mot 0.0 dagslys, fikk: " + deepCaveLight);
    }
}
