package no.minecraft.world.save;

import no.minecraft.advancement.AdvancementManager;
import no.minecraft.advancement.AdvancementManager.Advancement;
import no.minecraft.i18n.I18n;
import no.minecraft.player.GameMode;
import no.minecraft.player.Inventory;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.ChestData;
import no.minecraft.world.Chunk;
import no.minecraft.world.Dimension;
import no.minecraft.world.FurnaceData;
import no.minecraft.world.World;
import org.joml.Vector3f;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class WorldSaveManager {
    /** Save directory; mutable so tests can redirect it to a temp dir. */
    public static Path SAVES_DIR = Paths.get("saves");

    private static final int WORLD_MAGIC = 0x4D435744; // "MCWD"
    private static final int CHUNKS_MAGIC = 0x4D43434B; // "MCCK"
    private static final int VERSION = 3;

    public static long parseSeed(String seedInput) {
        if (seedInput == null || seedInput.trim().isEmpty()) {
            return new Random().nextLong();
        }
        String trimmed = seedInput.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return (long) trimmed.hashCode();
        }
    }

    public static List<WorldInfo> listWorlds() {
        List<WorldInfo> list = new ArrayList<>();
        if (!Files.exists(SAVES_DIR) || !Files.isDirectory(SAVES_DIR)) {
            return list;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SAVES_DIR)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    Path datFile = entry.resolve("world.dat");
                    if (Files.isRegularFile(datFile)) {
                        WorldInfo info = readWorldSummary(datFile, entry.getFileName().toString());
                        if (info != null) {
                            list.add(info);
                        }
                    }
                }
            }
        } catch (IOException ignored) {}

        // Sort descending by last played time (newest first)
        list.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
        return list;
    }

    private static WorldInfo readWorldSummary(Path datFile, String folderName) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(datFile))))) {
            int magic = in.readInt();
            if (magic != WORLD_MAGIC) return null;
            int version = in.readInt();
            if (version > VERSION) return null;

            String name = in.readUTF();
            String folder = in.readUTF();
            long seed = in.readLong();
            String modeStr = in.readUTF();
            long lastPlayed = in.readLong();

            GameMode mode = GameMode.SURVIVAL;
            try {
                mode = GameMode.valueOf(modeStr);
            } catch (Exception ignored) {}

            return new WorldInfo(name, folderName, seed, mode, lastPlayed);
        } catch (Exception e) {
            return null;
        }
    }

    public static String generateUniqueFolderName(String worldName) {
        String clean = worldName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.isEmpty()) clean = "World";

        Path target = SAVES_DIR.resolve(clean);
        if (!Files.exists(target)) {
            return clean;
        }

        int index = 1;
        while (Files.exists(SAVES_DIR.resolve(clean + "_" + index))) {
            index++;
        }
        return clean + "_" + index;
    }

    public static WorldInfo createNewWorld(String name, String seedInput, GameMode mode, World world, Player player) {
        return createNewWorld(name, seedInput, mode, world, player, false);
    }

    public static WorldInfo createNewWorld(String name, String seedInput, GameMode mode, World world, Player player, boolean bonusChest) {
        String safeName = (name == null || name.trim().isEmpty()) ? I18n.get("menu.default_world_name") : name.trim();
        long seed = parseSeed(seedInput);
        String folderName = generateUniqueFolderName(safeName);

        WorldInfo info = new WorldInfo(safeName, folderName, seed, mode, System.currentTimeMillis());

        // Initialize world state
        world.cleanup();
        world.setSeed(seed);
        world.setWorldTime(20.0f);
        world.setGameWon(false);
        world.setCurrentDimension(Dimension.OVERWORLD);

        Vector3f spawn = world.getSpawnPoint();
        player.resetToSpawn(spawn.x, spawn.y, spawn.z);
        player.setGameMode(mode);
        AdvancementManager.getInstance().reset();

        if (bonusChest) {
            generateBonusChest(world, spawn, seed);
        }

        // Initial save
        saveWorld(world, player, info);
        return info;
    }

    public static void generateBonusChest(World world, Vector3f spawn, long seed) {
        int sx = (int) Math.floor(spawn.x);
        int sz = (int) Math.floor(spawn.z);

        // Search for a suitable ground spot near spawn (1..3 blocks away)
        int chestX = sx + 1;
        int chestZ = sz + 1;
        int chestY = -1;

        int[][] offsets = {
            {1, 1}, {2, 1}, {1, 2}, {-1, 1}, {1, -1}, {-1, -1}, {2, 0}, {0, 2}, {-2, 0}, {0, -2}
        };

        for (int[] off : offsets) {
            int tx = sx + off[0];
            int tz = sz + off[1];
            int y = world.getSpawnHeight(tx, tz);
            if (y > 0 && y < Chunk.SIZE_Y - 2) {
                chestX = tx;
                chestZ = tz;
                chestY = y;
                break;
            }
        }

        if (chestY <= 0) {
            chestY = Math.max(1, (int) Math.floor(spawn.y));
        }

        // Place chest
        world.setBlock(chestX, chestY, chestZ, BlockType.CHEST);
        ChestData chest = world.getOrCreateChest(chestX, chestY, chestZ);
        chest.clear();

        // Bonus chest starter loot (Minecraft 1.16 Java Edition)
        Random rng = new Random(seed ^ 0x5DEECE66DL);
        List<ItemStack> loot = new ArrayList<>();
        // Tools
        loot.add(new ItemStack(rng.nextBoolean() ? BlockType.WOODEN_AXE : BlockType.STONE_AXE, 1));
        loot.add(new ItemStack(rng.nextBoolean() ? BlockType.WOODEN_PICKAXE : BlockType.STONE_PICKAXE, 1));
        loot.add(new ItemStack(BlockType.WOODEN_SHOVEL, 1));
        // Food
        loot.add(new ItemStack(BlockType.APPLE, 2 + rng.nextInt(2)));
        loot.add(new ItemStack(BlockType.BREAD, 2 + rng.nextInt(2)));
        // Wood & Building materials
        loot.add(new ItemStack(BlockType.WOOD, 2 + rng.nextInt(3)));
        loot.add(new ItemStack(BlockType.PLANKS, 4 + rng.nextInt(9)));
        loot.add(new ItemStack(BlockType.STICK, 4 + rng.nextInt(5)));

        // Distribute loot into random distinct slots of the 27-slot chest
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < ChestData.CHEST_SIZE; i++) slots.add(i);
        Collections.shuffle(slots, rng);

        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            ItemStack stack = loot.get(i);
            int slotIdx = slots.get(i);
            chest.setSlot(slotIdx, stack);
        }

        // Place 3-4 torches surrounding the chest on solid ground
        int[][] torchOffsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] toff : torchOffsets) {
            int tx = chestX + toff[0];
            int tz = chestZ + toff[1];
            int ty = world.getSpawnHeight(tx, tz);
            if (ty == chestY && world.getBlock(tx, ty, tz) == BlockType.AIR) {
                world.setBlock(tx, ty, tz, BlockType.TORCH);
            }
        }
    }

    public static boolean saveWorld(World world, Player player, WorldInfo info) {
        if (info == null) return false;
        try {
            Path worldDir = SAVES_DIR.resolve(info.getFolderName());
            Files.createDirectories(worldDir);
            world.setSaveDirectory(worldDir);
            info.setLastPlayed(System.currentTimeMillis());

            // 1. Save world.dat (write temp file, then move into place atomically)
            Path datFile = worldDir.resolve("world.dat");
            Path datTmp = worldDir.resolve("world.dat.tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(datTmp))))) {
                out.writeInt(WORLD_MAGIC);
                out.writeInt(VERSION);

                out.writeUTF(info.getName());
                out.writeUTF(info.getFolderName());
                out.writeLong(info.getSeed());
                out.writeUTF(player.getGameMode().name());
                out.writeLong(info.getLastPlayed());

                out.writeFloat(world.getWorldTime());
                out.writeBoolean(world.isGameWon());
                out.writeUTF(world.getCurrentDimension().name());

                // Player data
                Vector3f pos = player.getPosition();
                out.writeFloat(pos.x);
                out.writeFloat(pos.y);
                out.writeFloat(pos.z);
                out.writeFloat(player.getCamera().getYaw());
                out.writeFloat(player.getCamera().getPitch());

                out.writeInt(player.getHealth());
                out.writeInt(player.getHunger());
                out.writeFloat(player.getSaturation());
                out.writeFloat(player.getExhaustion());
                out.writeBoolean(player.isFlying());
                out.writeInt(player.getSelectedSlot());

                // Inventory
                Inventory inv = player.getInventory();
                out.writeInt(Inventory.TOTAL_SLOTS);
                for (int i = 0; i < Inventory.TOTAL_SLOTS; i++) {
                    ItemStack slot = inv.getSlot(i);
                    out.writeByte(slot.getType().getId());
                    out.writeInt(slot.getCount());
                    out.writeInt(slot.getDamage());
                }

                // Armor (4 slots)
                ItemStack[] armor = player.getArmorSlots();
                out.writeInt(armor.length);
                for (int i = 0; i < armor.length; i++) {
                    ItemStack slot = armor[i];
                    if (slot != null) {
                        out.writeByte(slot.getType().getId());
                        out.writeInt(slot.getCount());
                        out.writeInt(slot.getDamage());
                    } else {
                        out.writeByte(BlockType.AIR.getId());
                        out.writeInt(0);
                        out.writeInt(0);
                    }
                }

                // Offhand (1 slot)
                ItemStack offhand = player.getOffhandItem();
                if (offhand != null) {
                    out.writeByte(offhand.getType().getId());
                    out.writeInt(offhand.getCount());
                    out.writeInt(offhand.getDamage());
                } else {
                    out.writeByte(BlockType.AIR.getId());
                    out.writeInt(0);
                    out.writeInt(0);
                }

                // Advancements
                Set<Advancement> unlocked = AdvancementManager.getInstance().getUnlocked();
                out.writeInt(unlocked.size());
                for (Advancement adv : unlocked) {
                    out.writeUTF(adv.name());
                }

                // Furnaces
                Map<Long, FurnaceData> furnaces = world.getFurnaces();
                out.writeInt(furnaces.size());
                for (FurnaceData fd : furnaces.values()) {
                    out.writeInt(fd.getX());
                    out.writeInt(fd.getY());
                    out.writeInt(fd.getZ());

                    out.writeByte(fd.getInput().getType().getId());
                    out.writeInt(fd.getInput().getCount());
                    out.writeInt(fd.getInput().getDamage());

                    out.writeByte(fd.getFuel().getType().getId());
                    out.writeInt(fd.getFuel().getCount());
                    out.writeInt(fd.getFuel().getDamage());

                    out.writeByte(fd.getOutput().getType().getId());
                    out.writeInt(fd.getOutput().getCount());
                    out.writeInt(fd.getOutput().getDamage());

                    out.writeFloat(fd.getCookTime());
                    out.writeFloat(fd.getBurnTime());
                    out.writeFloat(fd.getMaxBurnTime());
                }

                // Chests
                Map<Long, ChestData> chests = world.getChests();
                out.writeInt(chests.size());
                for (ChestData cd : chests.values()) {
                    out.writeInt(cd.getX());
                    out.writeInt(cd.getY());
                    out.writeInt(cd.getZ());
                    out.writeInt(ChestData.CHEST_SIZE);
                    for (int s = 0; s < ChestData.CHEST_SIZE; s++) {
                        ItemStack slot = cd.getSlot(s);
                        out.writeByte(slot.getType().getId());
                        out.writeInt(slot.getCount());
                        out.writeInt(slot.getDamage());
                    }
                }
            }
            moveAtomically(datTmp, datFile);

            // 2. Save chunks.dat (write temp file, then move into place atomically)
            Path chunksFile = worldDir.resolve("chunks.dat");
            Path chunksTmp = worldDir.resolve("chunks.dat.tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(chunksTmp))))) {
                out.writeInt(CHUNKS_MAGIC);
                out.writeInt(VERSION);

                Dimension[] dims = Dimension.values();
                out.writeInt(dims.length);
                for (Dimension dim : dims) {
                    out.writeByte(dim.ordinal());

                    Map<Long, Chunk> dimChunks = world.getDimensionChunks().get(dim);
                    int count = (dimChunks != null) ? dimChunks.size() : 0;
                    out.writeInt(count);
                    if (dimChunks != null) {
                        for (Chunk c : dimChunks.values()) {
                            out.writeInt(c.getChunkX());
                            out.writeInt(c.getChunkZ());
                            out.write(c.getBlocks());
                        }
                    }

                    Set<Long> genSet = world.getDimensionGenerated().get(dim);
                    int genCount = (genSet != null) ? genSet.size() : 0;
                    out.writeInt(genCount);
                    if (genSet != null) {
                        for (Long key : genSet) {
                            out.writeLong(key);
                        }
                    }
                }
            }
            moveAtomically(chunksTmp, chunksFile);

            world.markAllChunksSaved();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean loadWorld(World world, Player player, WorldInfo info) {
        if (info == null) return false;
        Path worldDir = SAVES_DIR.resolve(info.getFolderName());
        Path datFile = worldDir.resolve("world.dat");
        if (!Files.exists(datFile)) return false;
        world.setSaveDirectory(worldDir);

        try {
            // 1. Read world.dat
            float posX, posY, posZ, yaw, pitch;
            int health, hunger, selectedSlot;
            float saturation, exhaustion, worldTime;
            boolean flying, gameWon;
            Dimension dim;
            GameMode mode;
            List<String> advNames = new ArrayList<>();
            List<FurnaceData> furnaceList = new ArrayList<>();
            List<ChestData> chestList = new ArrayList<>();
            ItemStack[] loadedSlots = new ItemStack[Inventory.TOTAL_SLOTS];
            ItemStack[] loadedArmor = new ItemStack[4];
            ItemStack loadedOffhand = null;

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(datFile))))) {
                int magic = in.readInt();
                if (magic != WORLD_MAGIC) return false;
                int version = in.readInt();
                if (version > VERSION) return false;

                String name = in.readUTF();
                String folder = in.readUTF();
                long seed = in.readLong();
                String modeStr = in.readUTF();
                long lastPlayed = in.readLong();

                info.setName(name);
                info.setSeed(seed);
                try {
                    mode = GameMode.valueOf(modeStr);
                } catch (Exception ignored) {
                    mode = GameMode.SURVIVAL;
                }
                info.setGameMode(mode);
                info.setLastPlayed(lastPlayed);

                worldTime = in.readFloat();
                gameWon = in.readBoolean();
                String dimStr = in.readUTF();
                try {
                    dim = Dimension.valueOf(dimStr);
                } catch (Exception ignored) {
                    dim = Dimension.OVERWORLD;
                }

                posX = in.readFloat();
                posY = in.readFloat();
                posZ = in.readFloat();
                yaw = in.readFloat();
                pitch = in.readFloat();

                health = in.readInt();
                hunger = in.readInt();
                saturation = in.readFloat();
                exhaustion = in.readFloat();
                flying = in.readBoolean();
                selectedSlot = in.readInt();

                int invSize = in.readInt();
                for (int i = 0; i < invSize; i++) {
                    byte typeId = in.readByte();
                    int count = in.readInt();
                    int damage = (version >= 3) ? in.readInt() : 0;
                    if (i < Inventory.TOTAL_SLOTS) {
                        ItemStack st = new ItemStack(BlockType.getById(typeId), count);
                        st.setDamage(damage);
                        loadedSlots[i] = st;
                    }
                }

                if (version >= 3) {
                    int armorCount = in.readInt();
                    for (int i = 0; i < armorCount; i++) {
                        byte typeId = in.readByte();
                        int count = in.readInt();
                        int damage = in.readInt();
                        if (i < 4) {
                            ItemStack st = new ItemStack(BlockType.getById(typeId), count);
                            st.setDamage(damage);
                            loadedArmor[i] = st;
                        }
                    }

                    byte offTypeId = in.readByte();
                    int offCount = in.readInt();
                    int offDamage = in.readInt();
                    loadedOffhand = new ItemStack(BlockType.getById(offTypeId), offCount);
                    loadedOffhand.setDamage(offDamage);
                }

                int advCount = in.readInt();
                for (int i = 0; i < advCount; i++) {
                    advNames.add(in.readUTF());
                }

                int furnaceCount = in.readInt();
                for (int i = 0; i < furnaceCount; i++) {
                    int fx = in.readInt();
                    int fy = in.readInt();
                    int fz = in.readInt();
                    FurnaceData fd = new FurnaceData(fx, fy, fz);

                    byte inId = in.readByte();
                    int inCount = in.readInt();
                    int inDamage = (version >= 3) ? in.readInt() : 0;
                    fd.getInput().setType(BlockType.getById(inId));
                    fd.getInput().setCount(inCount);
                    fd.getInput().setDamage(inDamage);

                    byte fuelId = in.readByte();
                    int fuelCount = in.readInt();
                    int fuelDamage = (version >= 3) ? in.readInt() : 0;
                    fd.getFuel().setType(BlockType.getById(fuelId));
                    fd.getFuel().setCount(fuelCount);
                    fd.getFuel().setDamage(fuelDamage);

                    byte outId = in.readByte();
                    int outCount = in.readInt();
                    int outDamage = (version >= 3) ? in.readInt() : 0;
                    fd.getOutput().setType(BlockType.getById(outId));
                    fd.getOutput().setCount(outCount);
                    fd.getOutput().setDamage(outDamage);

                    fd.setCookTime(in.readFloat());
                    fd.setBurnTime(in.readFloat());
                    fd.setMaxBurnTime(in.readFloat());
                    furnaceList.add(fd);
                }

                if (version >= 2) {
                    int chestCount = in.readInt();
                    for (int i = 0; i < chestCount; i++) {
                        int cx = in.readInt();
                        int cy = in.readInt();
                        int cz = in.readInt();
                        ChestData cd = new ChestData(cx, cy, cz);
                        int slotCount = in.readInt();
                        for (int s = 0; s < slotCount; s++) {
                            byte typeId = in.readByte();
                            int count = in.readInt();
                            int damage = (version >= 3) ? in.readInt() : 0;
                            if (s < ChestData.CHEST_SIZE) {
                                cd.getSlot(s).setType(BlockType.getById(typeId));
                                cd.getSlot(s).setCount(count);
                                cd.getSlot(s).setDamage(damage);
                            }
                        }
                        chestList.add(cd);
                    }
                }
            } catch (Exception e) {
                // world.dat is unreadable: quarantine it so it can be inspected/recovered manually
                quarantineCorrupt(datFile);
                e.printStackTrace();
                return false;
            }

            // Reset world
            world.cleanup();
            world.setSeed(info.getSeed());
            world.setWorldTime(worldTime);
            world.setGameWon(gameWon);
            world.setCurrentDimension(dim);
            world.setFurnaces(furnaceList);
            world.setChests(chestList);
            AdvancementManager.getInstance().setUnlocked(advNames);

            // 2. Read chunks.dat
            Path chunksFile = worldDir.resolve("chunks.dat");
            if (Files.exists(chunksFile)) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(chunksFile))))) {
                    int magic = in.readInt();
                    if (magic == CHUNKS_MAGIC) {
                        int version = in.readInt();
                        int numDims = in.readInt();
                        for (int d = 0; d < numDims; d++) {
                            byte dOrd = in.readByte();
                            Dimension chunkDim = (dOrd >= 0 && dOrd < Dimension.values().length) ? Dimension.values()[dOrd] : Dimension.OVERWORLD;
                            Map<Long, Chunk> targetChunks = world.getDimensionChunks().computeIfAbsent(chunkDim, k -> new ConcurrentHashMap<>());
                            Set<Long> targetGen = world.getDimensionGenerated().computeIfAbsent(chunkDim, k -> ConcurrentHashMap.newKeySet());

                            int chunkCount = in.readInt();
                            byte[] blockBuf = new byte[Chunk.SIZE_X * Chunk.SIZE_Y * Chunk.SIZE_Z];
                            for (int c = 0; c < chunkCount; c++) {
                                int cx = in.readInt();
                                int cz = in.readInt();
                                in.readFully(blockBuf);

                                Chunk chunk = new Chunk(world, cx, cz);
                                chunk.setBlocks(blockBuf);
                                targetChunks.put(World.chunkKey(cx, cz), chunk);
                                world.markChunkSaved(chunkDim, cx, cz);
                            }

                            int genCount = in.readInt();
                            for (int g = 0; g < genCount; g++) {
                                targetGen.add(in.readLong());
                            }
                        }
                    }
                }
            }

            // Restore player state
            player.setGameMode(info.getGameMode());
            player.teleportTo(posX, posY, posZ);
            player.getCamera().setRotation(yaw, pitch);
            player.setHealth(health);
            player.setHunger(hunger);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setFlying(flying);
            player.setSelectedSlot(selectedSlot);

            Inventory inv = player.getInventory();
            inv.clear();
            for (int i = 0; i < Inventory.TOTAL_SLOTS; i++) {
                if (loadedSlots[i] != null) {
                    inv.getSlot(i).setType(loadedSlots[i].getType());
                    inv.getSlot(i).setCount(loadedSlots[i].getCount());
                    inv.getSlot(i).setDamage(loadedSlots[i].getDamage());
                }
            }

            ItemStack[] armor = player.getArmorSlots();
            for (int i = 0; i < 4; i++) {
                if (loadedArmor[i] != null) {
                    armor[i].setType(loadedArmor[i].getType());
                    armor[i].setCount(loadedArmor[i].getCount());
                    armor[i].setDamage(loadedArmor[i].getDamage());
                } else {
                    armor[i].setType(BlockType.AIR);
                    armor[i].setCount(0);
                    armor[i].setDamage(0);
                }
            }

            if (loadedOffhand != null) {
                player.getOffhandItem().setType(loadedOffhand.getType());
                player.getOffhandItem().setCount(loadedOffhand.getCount());
                player.getOffhandItem().setDamage(loadedOffhand.getDamage());
            } else {
                player.getOffhandItem().setType(BlockType.AIR);
                player.getOffhandItem().setCount(0);
                player.getOffhandItem().setDamage(0);
            }

            // Refresh loaded chunks around player
            int pcx = Math.floorDiv((int) Math.floor(posX), Chunk.SIZE_X);
            int pcz = Math.floorDiv((int) Math.floor(posZ), Chunk.SIZE_Z);
            world.updateLoadedChunks(pcx, pcz);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean loadChunkInto(Chunk chunk, Path worldDir, Dimension dim) {
        Path chunksFile = worldDir.resolve("chunks.dat");
        if (!Files.exists(chunksFile)) return false;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(chunksFile))))) {
            int magic = in.readInt();
            if (magic != CHUNKS_MAGIC) return false;
            int version = in.readInt();
            int numDims = in.readInt();
            byte[] blockBuf = new byte[Chunk.SIZE_X * Chunk.SIZE_Y * Chunk.SIZE_Z];
            for (int d = 0; d < numDims; d++) {
                byte dOrd = in.readByte();
                int chunkCount = in.readInt();
                for (int c = 0; c < chunkCount; c++) {
                    int cx = in.readInt();
                    int cz = in.readInt();
                    in.readFully(blockBuf);
                    if (dOrd == dim.ordinal() && cx == chunk.getChunkX() && cz == chunk.getChunkZ()) {
                        chunk.setBlocks(blockBuf);
                        return true;
                    }
                }
                int genCount = in.readInt();
                for (int g = 0; g < genCount; g++) in.readLong();
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static void moveAtomically(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantineCorrupt(Path datFile) {
        try {
            if (Files.exists(datFile)) {
                Files.move(datFile, datFile.resolveSibling("world.dat.corrupt"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {}
    }

    public static boolean deleteWorld(WorldInfo info) {
        if (info == null) return false;
        try {
            Path worldDir = SAVES_DIR.resolve(info.getFolderName());
            if (Files.exists(worldDir)) {
                try (var walk = Files.walk(worldDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
