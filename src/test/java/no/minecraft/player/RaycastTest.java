package no.minecraft.player;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RaycastTest {

    private World worldWithClearPath() {
        World world = new World(12345L);
        for (int x = 0; x <= 5; x++) {
            for (int y = 19; y <= 22; y++) {
                world.setBlock(x, y, 0, BlockType.AIR);
            }
        }
        return world;
    }

    @Test
    public void testRaycastHitsBlock() {
        World world = worldWithClearPath();
        world.setBlock(3, 20, 0, BlockType.STONE);

        Raycast.HitResult hit = Raycast.raycast(world,
                new Vector3f(0.5f, 20.5f, 0.5f),
                new Vector3f(1, 0, 0),
                5.0f);

        assertNotNull(hit);
        assertEquals(3, hit.hitX);
        assertEquals(20, hit.hitY);
        assertEquals(0, hit.hitZ);
        assertEquals(2, hit.placeX, "Place-posisjon er blokken før treffet");
        assertEquals(BlockType.STONE, hit.blockType);
    }

    @Test
    public void testRaycastMissesPastMaxDistance() {
        World world = worldWithClearPath();
        world.setBlock(5, 20, 0, BlockType.STONE);

        Raycast.HitResult hit = Raycast.raycast(world,
                new Vector3f(0.5f, 20.5f, 0.5f),
                new Vector3f(1, 0, 0),
                2.0f);

        assertNull(hit, "Blokk utenfor maks-avstand gir ingen treff");
    }

    @Test
    public void testRaycastWaterExclusion() {
        World world = worldWithClearPath();
        world.setBlock(3, 20, 0, BlockType.WATER);

        Vector3f origin = new Vector3f(0.5f, 20.5f, 0.5f);
        Vector3f dir = new Vector3f(1, 0, 0);

        assertNull(Raycast.raycast(world, origin, dir, 5.0f), "Vann ekskluderes som standard");
        Raycast.HitResult hit = Raycast.raycast(world, origin, dir, 5.0f, true);
        assertNotNull(hit, "includeWater=true gir treff i vann");
        assertEquals(BlockType.WATER, hit.blockType);
    }

    @Test
    public void testHasLineOfSightClear() {
        World world = worldWithClearPath();
        Vector3f from = new Vector3f(0.5f, 20.5f, 0.5f);
        Vector3f to = new Vector3f(4.5f, 20.5f, 0.5f);

        assertTrue(Raycast.hasLineOfSight(world, from, to), "Klar bane gjennom luft skal returnere true");
    }

    @Test
    public void testHasLineOfSightBlockedByStone() {
        World world = worldWithClearPath();
        world.setBlock(2, 20, 0, BlockType.STONE);

        Vector3f from = new Vector3f(0.5f, 20.5f, 0.5f);
        Vector3f to = new Vector3f(4.5f, 20.5f, 0.5f);

        assertFalse(Raycast.hasLineOfSight(world, from, to), "Steinblokk i veien skal blokkere sikt");
    }

    @Test
    public void testHasLineOfSightBlockedByBedrock() {
        World world = worldWithClearPath();
        world.setBlock(2, 20, 0, BlockType.BEDROCK);

        Vector3f from = new Vector3f(0.5f, 20.5f, 0.5f);
        Vector3f to = new Vector3f(4.5f, 20.5f, 0.5f);

        assertFalse(Raycast.hasLineOfSight(world, from, to), "Bedrock skal blokkere sikt");
    }

    @Test
    public void testHasLineOfSightThroughWater() {
        World world = worldWithClearPath();
        world.setBlock(2, 20, 0, BlockType.WATER);

        Vector3f from = new Vector3f(0.5f, 20.5f, 0.5f);
        Vector3f to = new Vector3f(4.5f, 20.5f, 0.5f);

        assertTrue(Raycast.hasLineOfSight(world, from, to), "Vann skal ikke blokkere sikt");
    }

    @Test
    public void testHasLineOfSightSameBlock() {
        World world = worldWithClearPath();
        Vector3f from = new Vector3f(0.2f, 20.5f, 0.5f);
        Vector3f to = new Vector3f(0.8f, 20.5f, 0.5f);

        assertTrue(Raycast.hasLineOfSight(world, from, to), "Samme luftblokk skal gi fri sikt");
    }

    @Test
    public void testHasLineOfSightObstaclePastTarget() {
        World world = worldWithClearPath();
        world.setBlock(5, 20, 0, BlockType.STONE);

        Vector3f from = new Vector3f(0.5f, 20.5f, 0.5f);
        Vector3f to = new Vector3f(3.5f, 20.5f, 0.5f);

        assertTrue(Raycast.hasLineOfSight(world, from, to), "Blokk bak målet skal ikke blokkere sikt mot målet");
    }
}
