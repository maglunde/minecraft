package no.minecraft.world.save;

import no.minecraft.player.GameMode;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.Dimension;
import no.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

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
        player.getInventory().getSlot(0).setDamage(55); // Damaged tool!
        player.getInventory().getSlot(1).setType(BlockType.IRON_INGOT);
        player.getInventory().getSlot(1).setCount(42);

        // Armor with damage
        player.getArmorSlot(0).setType(BlockType.DIAMOND_HELMET);
        player.getArmorSlot(0).setCount(1);
        player.getArmorSlot(0).setDamage(120);

        // Offhand item with damage
        player.getOffhandItem().setType(BlockType.BOW);
        player.getOffhandItem().setCount(1);
        player.getOffhandItem().setDamage(40);

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

        // Assert inventory and durability
        ItemStack slot0 = newPlayer.getInventory().getSlot(0);
        assertEquals(BlockType.IRON_PICKAXE, slot0.getType());
        assertEquals(1, slot0.getCount());
        assertEquals(55, slot0.getDamage());

        ItemStack slot1 = newPlayer.getInventory().getSlot(1);
        assertEquals(BlockType.IRON_INGOT, slot1.getType());
        assertEquals(42, slot1.getCount());

        // Assert armor durability
        assertEquals(BlockType.DIAMOND_HELMET, newPlayer.getArmorSlot(0).getType());
        assertEquals(120, newPlayer.getArmorSlot(0).getDamage());

        // Assert offhand durability
        assertEquals(BlockType.BOW, newPlayer.getOffhandItem().getType());
        assertEquals(40, newPlayer.getOffhandItem().getDamage());

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

    @Test
    public void testSecondSavePreservesEvictedChunkData() {
        int originalRd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        try {
            no.minecraft.settings.GameSettings.getInstance().setRenderDistance(3);

            final int chunkX = 0;
            final int chunkZ = 0;
            final int blockX = 2;
            final int blockY = 20;
            final int blockZ = 0;
            final BlockType marker = BlockType.OBSIDIAN;

            World world = new World();
            Player player = new Player(world, 0, 10, 0);
            createdWorldInfo = WorldSaveManager.createNewWorld(
                    "EvictedChunkSecondSave_" + System.currentTimeMillis(),
                    "8675309", GameMode.SURVIVAL, world, player);

            world.updateLoadedChunks(chunkX, chunkZ);
            assertNotNull(world.getChunk(chunkX, chunkZ));
            world.setBlock(blockX, blockY, blockZ, marker);
            assertEquals(marker, world.getBlock(blockX, blockY, blockZ));

            assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

            world.updateLoadedChunks(6, 0);
            assertNull(world.getChunk(chunkX, chunkZ), "Opprinnelig chunk skal være eviktert fra minnet");

            assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

            World loadedWorld = new World();
            Player loadedPlayer = new Player(loadedWorld, 0, 0, 0);
            assertTrue(WorldSaveManager.loadWorld(loadedWorld, loadedPlayer, createdWorldInfo));

            loadedWorld.updateLoadedChunks(chunkX, chunkZ);
            assertNotNull(loadedWorld.getChunk(chunkX, chunkZ));
            assertEquals(marker, loadedWorld.getBlock(blockX, blockY, blockZ),
                    "Blokkendringen skal overleve en ny save etter at chunken ble eviktert");
        } finally {
            no.minecraft.settings.GameSettings.getInstance().setRenderDistance(originalRd);
        }
    }

    @Test
    public void testNegativeChunkCoordinatesSaveAndLoad() {
        final int chunkX = -2;
        final int chunkZ = -3;
        final int blockX = chunkX * Chunk.SIZE_X + 3;
        final int blockZ = chunkZ * Chunk.SIZE_Z + 5;

        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        createdWorldInfo = WorldSaveManager.createNewWorld(
                "NegativeChunks_" + System.currentTimeMillis(), "1200", GameMode.SURVIVAL, world, player);

        world.ensureChunkGenerated(chunkX, chunkZ);
        world.setBlock(blockX, 20, blockZ, BlockType.OBSIDIAN);
        assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

        Path chunkFile = tempDir.resolve(createdWorldInfo.getFolderName())
                .resolve("dimensions/overworld/c.-2.-3.dat");
        assertTrue(Files.isRegularFile(chunkFile));

        World loadedWorld = new World();
        Player loadedPlayer = new Player(loadedWorld, 0, 0, 0);
        assertTrue(WorldSaveManager.loadWorld(loadedWorld, loadedPlayer, createdWorldInfo));
        loadedWorld.ensureChunkGenerated(chunkX, chunkZ);
        assertEquals(BlockType.OBSIDIAN, loadedWorld.getBlock(blockX, 20, blockZ));
    }

    @Test
    public void testSameChunkCoordinatesAreSeparatedByDimension() {
        final int chunkX = 12;
        final int chunkZ = -11;
        final int blockX = chunkX * Chunk.SIZE_X + 4;
        final int blockZ = chunkZ * Chunk.SIZE_Z + 6;

        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        createdWorldInfo = WorldSaveManager.createNewWorld(
                "DimensionChunks_" + System.currentTimeMillis(), "2200", GameMode.SURVIVAL, world, player);

        world.setCurrentDimension(Dimension.OVERWORLD);
        world.ensureChunkGenerated(chunkX, chunkZ);
        world.setBlock(blockX, 20, blockZ, BlockType.GOLD_ORE);

        world.setCurrentDimension(Dimension.NETHER);
        world.ensureChunkGenerated(chunkX, chunkZ);
        world.setBlock(blockX, 20, blockZ, BlockType.OBSIDIAN);
        assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

        Path worldDir = tempDir.resolve(createdWorldInfo.getFolderName());
        assertTrue(Files.isRegularFile(worldDir.resolve("dimensions/overworld/c.12.-11.dat")));
        assertTrue(Files.isRegularFile(worldDir.resolve("dimensions/nether/c.12.-11.dat")));

        World loadedWorld = new World();
        Player loadedPlayer = new Player(loadedWorld, 0, 0, 0);
        assertTrue(WorldSaveManager.loadWorld(loadedWorld, loadedPlayer, createdWorldInfo));

        loadedWorld.setCurrentDimension(Dimension.OVERWORLD);
        loadedWorld.ensureChunkGenerated(chunkX, chunkZ);
        assertEquals(BlockType.GOLD_ORE, loadedWorld.getBlock(blockX, 20, blockZ));

        loadedWorld.setCurrentDimension(Dimension.NETHER);
        loadedWorld.ensureChunkGenerated(chunkX, chunkZ);
        assertEquals(BlockType.OBSIDIAN, loadedWorld.getBlock(blockX, 20, blockZ));
    }

    @Test
    public void testLaterSavePreservesOtherUnloadedChunkFiles() {
        final int firstChunkX = 20;
        final int firstChunkZ = 20;
        final int secondChunkX = -20;
        final int secondChunkZ = 18;
        final int firstBlockX = firstChunkX * Chunk.SIZE_X + 2;
        final int firstBlockZ = firstChunkZ * Chunk.SIZE_Z + 2;
        final int secondBlockX = secondChunkX * Chunk.SIZE_X + 3;
        final int secondBlockZ = secondChunkZ * Chunk.SIZE_Z + 3;

        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        createdWorldInfo = WorldSaveManager.createNewWorld(
                "UnloadedChunkFiles_" + System.currentTimeMillis(), "3200", GameMode.SURVIVAL, world, player);
        world.ensureChunkGenerated(firstChunkX, firstChunkZ);
        world.setBlock(firstBlockX, 20, firstBlockZ, BlockType.GOLD_ORE);
        world.ensureChunkGenerated(secondChunkX, secondChunkZ);
        world.setBlock(secondBlockX, 20, secondBlockZ, BlockType.DIAMOND_ORE);
        assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

        World laterWorld = new World();
        Player laterPlayer = new Player(laterWorld, 0, 0, 0);
        assertTrue(WorldSaveManager.loadWorld(laterWorld, laterPlayer, createdWorldInfo));
        assertNull(laterWorld.getChunk(secondChunkX, secondChunkZ));
        laterWorld.ensureChunkGenerated(firstChunkX, firstChunkZ);
        laterWorld.setBlock(firstBlockX, 20, firstBlockZ, BlockType.IRON_ORE);
        assertTrue(WorldSaveManager.saveWorld(laterWorld, laterPlayer, createdWorldInfo));

        World reloadedWorld = new World();
        Player reloadedPlayer = new Player(reloadedWorld, 0, 0, 0);
        assertTrue(WorldSaveManager.loadWorld(reloadedWorld, reloadedPlayer, createdWorldInfo));
        reloadedWorld.ensureChunkGenerated(firstChunkX, firstChunkZ);
        assertEquals(BlockType.IRON_ORE, reloadedWorld.getBlock(firstBlockX, 20, firstBlockZ));
        reloadedWorld.ensureChunkGenerated(secondChunkX, secondChunkZ);
        assertEquals(BlockType.DIAMOND_ORE, reloadedWorld.getBlock(secondBlockX, 20, secondBlockZ));
    }

    @Test
    public void testFailedChunkWriteKeepsChunkDirty() throws Exception {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        createdWorldInfo = WorldSaveManager.createNewWorld(
                "FailedChunkWrite_" + System.currentTimeMillis(), "4200", GameMode.SURVIVAL, world, player);

        Chunk chunk = world.ensureChunkGenerated(0, 0);
        world.setBlock(2, 20, 2, BlockType.OBSIDIAN);
        assertTrue(chunk.needsSave());

        Path chunkFile = tempDir.resolve(createdWorldInfo.getFolderName())
                .resolve("dimensions/overworld/c.0.0.dat");
        Files.delete(chunkFile);
        Files.createDirectory(chunkFile);

        assertFalse(WorldSaveManager.saveWorld(world, player, createdWorldInfo));
        assertTrue(chunk.needsSave(), "Dirty-flagget må beholdes når chunkfilen ikke kan erstattes");
        assertFalse(world.isChunkSaved(Dimension.OVERWORLD, 0, 0));
    }

    @Test
    public void testLegacyChunksDatIsImported() throws Exception {
        final int chunkX = 24;
        final int chunkZ = -24;
        final int localX = 2;
        final int localY = 20;
        final int localZ = 3;

        World world = new World();
        Player player = new Player(world, 0, 10, 0);
        createdWorldInfo = WorldSaveManager.createNewWorld(
                "LegacyChunks_" + System.currentTimeMillis(), "5200", GameMode.SURVIVAL, world, player);
        Path worldDir = tempDir.resolve(createdWorldInfo.getFolderName());

        byte[] blocks = new byte[Chunk.SIZE_X * Chunk.SIZE_Y * Chunk.SIZE_Z];
        int blockIndex = (localY * Chunk.SIZE_Z + localZ) * Chunk.SIZE_X + localX;
        blocks[blockIndex] = BlockType.OBSIDIAN.getId();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(worldDir.resolve("chunks.dat")))))) {
            out.writeInt(0x4D43434B);
            out.writeInt(3);
            out.writeInt(1);
            out.writeByte(Dimension.OVERWORLD.ordinal());
            out.writeInt(1);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            out.write(blocks);
            out.writeInt(1);
            out.writeLong(World.chunkKey(chunkX, chunkZ));
        }

        World loadedWorld = new World();
        Player loadedPlayer = new Player(loadedWorld, 0, 0, 0);
        assertTrue(WorldSaveManager.loadWorld(loadedWorld, loadedPlayer, createdWorldInfo));
        loadedWorld.ensureChunkGenerated(chunkX, chunkZ);
        assertEquals(BlockType.OBSIDIAN, loadedWorld.getBlock(
                chunkX * Chunk.SIZE_X + localX, localY, chunkZ * Chunk.SIZE_Z + localZ));
        assertTrue(Files.isRegularFile(worldDir.resolve("dimensions/overworld/c.24.-24.dat")));
        assertTrue(Files.isRegularFile(worldDir.resolve("chunks.dat.legacy")));
        assertFalse(Files.exists(worldDir.resolve("chunks.dat")));
    }

    @Test
    public void testBonusChestCreationAndContents() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);

        String testWorldName = "BonusChestWorld_" + System.currentTimeMillis();
        createdWorldInfo = WorldSaveManager.createNewWorld(testWorldName, "4242", GameMode.SURVIVAL, world, player, true);

        // Find the generated chest in the world
        var chests = world.getChests();
        assertFalse(chests.isEmpty(), "Bonus chest should have been generated");

        no.minecraft.world.ChestData bonusChest = chests.values().iterator().next();
        assertNotNull(bonusChest);
        assertFalse(bonusChest.isEmpty(), "Bonus chest should not be empty");

        // Verify starter items exist (e.g. axe, pickaxe, food, wood)
        boolean hasTool = false;
        boolean hasFood = false;
        boolean hasWood = false;

        for (int i = 0; i < bonusChest.getSize(); i++) {
            ItemStack slot = bonusChest.getSlot(i);
            if (!slot.isEmpty()) {
                BlockType type = slot.getType();
                if (type == BlockType.WOODEN_AXE || type == BlockType.STONE_AXE ||
                    type == BlockType.WOODEN_PICKAXE || type == BlockType.STONE_PICKAXE ||
                    type == BlockType.WOODEN_SHOVEL) {
                    hasTool = true;
                }
                if (type == BlockType.APPLE || type == BlockType.BREAD) {
                    hasFood = true;
                }
                if (type == BlockType.WOOD || type == BlockType.PLANKS || type == BlockType.STICK) {
                    hasWood = true;
                }
            }
        }

        assertTrue(hasTool, "Bonus chest should contain starter tools");
        assertTrue(hasFood, "Bonus chest should contain food");
        assertTrue(hasWood, "Bonus chest should contain wood/planks");

        // Verify block in world is CHEST
        assertEquals(BlockType.CHEST, world.getBlock(bonusChest.getX(), bonusChest.getY(), bonusChest.getZ()));

        // Verify save and load preserves bonus chest items
        assertTrue(WorldSaveManager.saveWorld(world, player, createdWorldInfo));

        World loadedWorld = new World();
        Player loadedPlayer = new Player(loadedWorld, 0, 0, 0);
        assertTrue(WorldSaveManager.loadWorld(loadedWorld, loadedPlayer, createdWorldInfo));

        var loadedChests = loadedWorld.getChests();
        assertEquals(1, loadedChests.size());
        no.minecraft.world.ChestData loadedChest = loadedWorld.getChest(bonusChest.getX(), bonusChest.getY(), bonusChest.getZ());
        assertNotNull(loadedChest);
        assertFalse(loadedChest.isEmpty());

        for (int i = 0; i < bonusChest.getSize(); i++) {
            ItemStack orig = bonusChest.getSlot(i);
            ItemStack loaded = loadedChest.getSlot(i);
            assertEquals(orig.getType(), loaded.getType());
            assertEquals(orig.getCount(), loaded.getCount());
        }
    }

    @Test
    public void testChestBlockBreakDropsItems() {
        World world = new World();
        world.setBlock(5, 10, 5, BlockType.CHEST);
        no.minecraft.world.ChestData chest = world.getOrCreateChest(5, 10, 5);
        chest.addItem(BlockType.DIAMOND, 3);
        chest.addItem(BlockType.BREAD, 5);

        assertEquals(0, world.getDroppedItems().size());

        // Break chest block
        world.setBlock(5, 10, 5, BlockType.AIR);

        assertNull(world.getChest(5, 10, 5));
        assertTrue(world.getChests().isEmpty());
        assertEquals(2, world.getDroppedItems().size(), "Breaking chest should drop all contained items");
    }
}
