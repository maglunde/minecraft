package no.minecraft.world.save;

import no.minecraft.player.GameMode;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WorldSaveManagerTest {

    @TempDir
    Path tempDir;

    private WorldInfo createdWorldInfo = null;
    private Path originalSavesDir;

    @BeforeEach
    public void setUp() {
        createdWorldInfo = null;
        originalSavesDir = WorldSaveManager.SAVES_DIR;
        WorldSaveManager.SAVES_DIR = tempDir;
    }

    @AfterEach
    public void tearDown() {
        if (createdWorldInfo != null) {
            WorldSaveManager.deleteWorld(createdWorldInfo);
        }
        WorldSaveManager.SAVES_DIR = originalSavesDir;
    }

    @Test
    public void testSeedParsing() {
        assertEquals(12345L, WorldSaveManager.parseSeed("12345"));
        assertEquals(-987654321L, WorldSaveManager.parseSeed("-987654321"));
        assertEquals((long) "minecraft".hashCode(), WorldSaveManager.parseSeed("minecraft"));

        long randomSeed1 = WorldSaveManager.parseSeed("");
        long randomSeed2 = WorldSaveManager.parseSeed(null);
        assertNotEquals(0, randomSeed1);
        assertNotEquals(0, randomSeed2);
    }

    @Test
    public void testCreateSaveAndLoadWorldPreservesState() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);

        String testWorldName = "TestWorld_" + System.currentTimeMillis();
        createdWorldInfo = WorldSaveManager.createNewWorld(testWorldName, "1337", GameMode.SURVIVAL, world, player);

        assertNotNull(createdWorldInfo);
        assertEquals(testWorldName, createdWorldInfo.getName());
        assertEquals(1337L, createdWorldInfo.getSeed());
        assertEquals(GameMode.SURVIVAL, createdWorldInfo.getGameMode());

        // Modify player state
        player.teleportTo(12.5f, 25.0f, -40.5f);
        player.getCamera().setRotation(45.0f, -15.0f);
        player.setHealth(14);
        player.setHunger(16);
        player.getInventory().clear();
        player.getInventory().getSlot(0).setType(BlockType.IRON_PICKAXE);
        player.getInventory().getSlot(0).setCount(1);
        player.getInventory().getSlot(1).setType(BlockType.IRON_INGOT);
        player.getInventory().getSlot(1).setCount(42);

        // Modify world blocks
        world.setBlock(10, 20, 10, BlockType.DIAMOND_ORE);
        world.setWorldTime(180.0f);

        // Save
        boolean saved = WorldSaveManager.saveWorld(world, player, createdWorldInfo);
        assertTrue(saved, "World should save successfully");

        // Verify it appears in listWorlds
        List<WorldInfo> list = WorldSaveManager.listWorlds();
        assertTrue(list.stream().anyMatch(w -> w.getFolderName().equals(createdWorldInfo.getFolderName())));

        // Load into fresh world and player
        World newWorld = new World();
        Player newPlayer = new Player(newWorld, 0, 0, 0);
        boolean loaded = WorldSaveManager.loadWorld(newWorld, newPlayer, createdWorldInfo);
        assertTrue(loaded, "World should load successfully");

        // Assert player state
        assertEquals(12.5f, newPlayer.getPosition().x, 0.01f);
        assertEquals(25.0f, newPlayer.getPosition().y, 0.01f);
        assertEquals(-40.5f, newPlayer.getPosition().z, 0.01f);
        assertEquals(45.0f, newPlayer.getCamera().getYaw(), 0.01f);
        assertEquals(-15.0f, newPlayer.getCamera().getPitch(), 0.01f);
        assertEquals(14, newPlayer.getHealth());
        assertEquals(16, newPlayer.getHunger());

        // Assert inventory
        ItemStack slot0 = newPlayer.getInventory().getSlot(0);
        assertEquals(BlockType.IRON_PICKAXE, slot0.getType());
        assertEquals(1, slot0.getCount());

        ItemStack slot1 = newPlayer.getInventory().getSlot(1);
        assertEquals(BlockType.IRON_INGOT, slot1.getType());
        assertEquals(42, slot1.getCount());

        // Assert world block and time
        assertEquals(180.0f, newWorld.getWorldTime(), 0.01f);
        assertEquals(1337L, newWorld.getSeed());
        assertEquals(BlockType.DIAMOND_ORE, newWorld.getBlock(10, 20, 10));
    }

    @Test
    public void testDeleteWorld() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);

        String testWorldName = "ToDelete_" + System.currentTimeMillis();
        WorldInfo info = WorldSaveManager.createNewWorld(testWorldName, "999", GameMode.CREATIVE, world, player);

        assertTrue(WorldSaveManager.listWorlds().stream().anyMatch(w -> w.getFolderName().equals(info.getFolderName())));

        boolean deleted = WorldSaveManager.deleteWorld(info);
        assertTrue(deleted);
        assertFalse(WorldSaveManager.listWorlds().stream().anyMatch(w -> w.getFolderName().equals(info.getFolderName())));
    }

    @Test
    public void testEvictedChunkReloadsFromSave() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        createdWorldInfo = WorldSaveManager.createNewWorld(
                "EvictTest_" + System.currentTimeMillis(), "555", GameMode.SURVIVAL, world, player);

        // Player edit, then save (autosave equivalent)
        world.setBlock(2, 20, 0, BlockType.GOLD_ORE);
        assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

        int originalRd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        try {
            no.minecraft.settings.GameSettings.getInstance().setRenderDistance(3);
            // Walk far away: spawn chunk leaves memory
            world.updateLoadedChunks(6, 0);
            assertNull(world.getChunk(0, 0), "Spawn-chunk skal være eviktert");

            // Return: chunk reloads from save, keeping the player edit
            world.updateLoadedChunks(0, 0);
            assertEquals(BlockType.GOLD_ORE, world.getBlock(2, 20, 0),
                    "Blokk i eviktert chunk må gjenopprettes fra save ved retur");
        } finally {
            no.minecraft.settings.GameSettings.getInstance().setRenderDistance(originalRd);
        }
    }
}
