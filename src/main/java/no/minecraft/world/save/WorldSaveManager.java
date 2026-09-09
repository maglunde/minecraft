package no.minecraft.world.save;

import no.minecraft.advancement.AdvancementManager;
import no.minecraft.advancement.AdvancementManager.Advancement;
import no.minecraft.player.GameMode;
import no.minecraft.player.Inventory;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.Dimension;
import no.minecraft.world.FurnaceData;
import no.minecraft.world.World;
import org.joml.Vector3f;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class WorldSaveManager {
    public static final Path SAVES_DIR = Paths.get("saves");

    private static final int WORLD_MAGIC = 0x4D435744; // "MCWD"
    private static final int CHUNKS_MAGIC = 0x4D43434B; // "MCCK"
    private static final int VERSION = 1;

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
        String safeName = (name == null || name.trim().isEmpty()) ? "Ny verden" : name.trim();
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

        // Initial save
        saveWorld(world, player, info);
        return info;
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

                    out.writeByte(fd.getFuel().getType().getId());
                    out.writeInt(fd.getFuel().getCount());

                    out.writeByte(fd.getOutput().getType().getId());
                    out.writeInt(fd.getOutput().getCount());

                    out.writeFloat(fd.getCookTime());
                    out.writeFloat(fd.getBurnTime());
                    out.writeFloat(fd.getMaxBurnTime());
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
            ItemStack[] loadedSlots = new ItemStack[Inventory.TOTAL_SLOTS];

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
                    if (i < Inventory.TOTAL_SLOTS) {
                        loadedSlots[i] = new ItemStack(BlockType.getById(typeId), count);
                    }
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
                    fd.getInput().setType(BlockType.getById(inId));
                    fd.getInput().setCount(inCount);

                    byte fuelId = in.readByte();
                    int fuelCount = in.readInt();
                    fd.getFuel().setType(BlockType.getById(fuelId));
                    fd.getFuel().setCount(fuelCount);

                    byte outId = in.readByte();
                    int outCount = in.readInt();
                    fd.getOutput().setType(BlockType.getById(outId));
                    fd.getOutput().setCount(outCount);

                    fd.setCookTime(in.readFloat());
                    fd.setBurnTime(in.readFloat());
                    fd.setMaxBurnTime(in.readFloat());
                    furnaceList.add(fd);
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
                            Map<Long, Chunk> targetChunks = world.getDimensionChunks().computeIfAbsent(chunkDim, k -> new HashMap<>());
                            Set<Long> targetGen = world.getDimensionGenerated().computeIfAbsent(chunkDim, k -> new HashSet<>());

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
                }
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
