package no.minecraft.world;

import no.minecraft.player.Player;
import java.util.*;

public class World {
    public static final int RENDER_DISTANCE = 5;
    public static final int UNLOAD_DISTANCE = RENDER_DISTANCE + 2;
    public static final int SEA_LEVEL = 18;

    // Dimension management
    private Dimension currentDimension = Dimension.OVERWORLD;
    private final Map<Dimension, Map<Long, Chunk>> dimensionChunks = new EnumMap<>(Dimension.class);
    private final Map<Dimension, Set<Long>> dimensionGenerated = new EnumMap<>(Dimension.class);

    private long seed;
    private double offsetX;
    private double offsetZ;

    // Stronghold coordinates in Overworld
    public static final int STRONGHOLD_X = 48;
    public static final int STRONGHOLD_Y = 14;
    public static final int STRONGHOLD_Z = 48;

    private final List<DroppedItem> droppedItems = new ArrayList<>();
    private final List<no.minecraft.entity.Mob> mobs = new ArrayList<>();
    private final List<no.minecraft.entity.Arrow> arrows = new ArrayList<>();
    private float mobSpawnTimer = 0.0f;
    private final Random rand = new Random();
    private boolean shouldClearHostileMobs = false;

    public static final float DAY_LENGTH_SECONDS = 240.0f;
    private float worldTime = 20.0f;

    // Victory state when Dragon is slain
    private boolean gameWon = false;

    public World() {
        this(new Random().nextLong());
    }

    public World(long seed) {
        for (Dimension dim : Dimension.values()) {
            dimensionChunks.put(dim, new HashMap<>());
            dimensionGenerated.put(dim, new HashSet<>());
        }
        setSeed(seed);
    }

    public Dimension getCurrentDimension() {
        return currentDimension;
    }

    public boolean isGameWon() {
        return gameWon;
    }

    public void setGameWon(boolean won) {
        this.gameWon = won;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
        Random r = new Random(seed);
        this.offsetX = (r.nextDouble() - 0.5) * 200000.0;
        this.offsetZ = (r.nextDouble() - 0.5) * 200000.0;
        this.gameWon = false;
        cleanup();
        for (Dimension dim : Dimension.values()) {
            dimensionChunks.put(dim, new HashMap<>());
            dimensionGenerated.put(dim, new HashSet<>());
        }
        updateLoadedChunks(0, 0);
    }

    public static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private Map<Long, Chunk> getActiveChunks() {
        return dimensionChunks.computeIfAbsent(currentDimension, k -> new HashMap<>());
    }

    private Set<Long> getActiveGenerated() {
        return dimensionGenerated.computeIfAbsent(currentDimension, k -> new HashSet<>());
    }

    public Chunk getChunk(int cx, int cz) {
        return getActiveChunks().get(chunkKey(cx, cz));
    }

    public Chunk getOrCreateChunk(int cx, int cz) {
        return getActiveChunks().computeIfAbsent(chunkKey(cx, cz), k -> new Chunk(this, cx, cz));
    }

    public void markChunkDirty(int cx, int cz) {
        Chunk chunk = getChunk(cx, cz);
        if (chunk != null) {
            chunk.setDirty(true);
        }
    }

    public BlockType getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return BlockType.AIR;
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        Chunk chunk = getChunk(cx, cz);
        if (chunk == null) return BlockType.AIR;

        int localX = (x % Chunk.SIZE_X + Chunk.SIZE_X) % Chunk.SIZE_X;
        int localZ = (z % Chunk.SIZE_Z + Chunk.SIZE_Z) % Chunk.SIZE_Z;
        return chunk.getBlock(localX, y, localZ);
    }

    public void setBlock(int x, int y, int z, BlockType type) {
        if (y < 0 || y >= Chunk.SIZE_Y) return;
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        Chunk chunk = getOrCreateChunk(cx, cz);

        int localX = (x % Chunk.SIZE_X + Chunk.SIZE_X) % Chunk.SIZE_X;
        int localZ = (z % Chunk.SIZE_Z + Chunk.SIZE_Z) % Chunk.SIZE_Z;
        chunk.setBlock(localX, y, localZ, type);
    }

    public int getSpawnHeight(int x, int z) {
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            BlockType type = getBlock(x, y, z);
            if (type != BlockType.AIR && type.isSolid()) {
                return y + 1;
            }
        }
        return 25;
    }

    private org.joml.Vector3f lastOverworldPortal = null;

    public void teleportToDimension(Dimension newDim, Player player) {
        if (this.currentDimension == newDim) return;
        mobs.clear();
        arrows.clear();
        droppedItems.clear();

        if (newDim == Dimension.NETHER) {
            // Save location where player entered from Overworld
            if (this.currentDimension == Dimension.OVERWORLD) {
                lastOverworldPortal = new org.joml.Vector3f(player.getPosition());
            }
            this.currentDimension = Dimension.NETHER;
            no.minecraft.advancement.AdvancementManager.getInstance().unlock(
                    no.minecraft.advancement.AdvancementManager.Advancement.WE_NEED_TO_GO_DEEPER
            );
            // Spawn on Nether portal platform at (8.5, 25.05, 9.5) directly facing the portal frame
            updateLoadedChunks(0, 0);
            player.teleportTo(8.5f, 25.05f, 9.5f);
        } else if (newDim == Dimension.THE_END) {
            if (this.currentDimension == Dimension.OVERWORLD) {
                lastOverworldPortal = new org.joml.Vector3f(player.getPosition());
            }
            this.currentDimension = Dimension.THE_END;
            no.minecraft.advancement.AdvancementManager.getInstance().unlock(
                    no.minecraft.advancement.AdvancementManager.Advancement.THE_END
            );
            // Spawn on End island at (0, 32, 28) facing central portal
            updateLoadedChunks(0, 0);
            player.teleportTo(0.5f, 32.05f, 28.5f);

            // Spawn Ender Dragon boss at (0, 40, 0)
            spawnMob(no.minecraft.entity.MobType.ENDER_DRAGON, 0.0f, 40.0f, 0.0f);
        } else {
            // Overworld return
            this.currentDimension = Dimension.OVERWORLD;
            if (lastOverworldPortal != null) {
                int cx = Math.floorDiv((int) Math.floor(lastOverworldPortal.x), Chunk.SIZE_X);
                int cz = Math.floorDiv((int) Math.floor(lastOverworldPortal.z), Chunk.SIZE_Z);
                updateLoadedChunks(cx, cz);
                player.teleportTo(lastOverworldPortal.x, lastOverworldPortal.y, lastOverworldPortal.z);
            } else {
                updateLoadedChunks(0, 0);
                int sy = getSpawnHeight(0, 0);
                player.teleportTo(0.5f, sy + 0.05f, 0.5f);
            }
        }
    }

    public void spawnItemDrop(float x, float y, float z, BlockType type, int count) {
        if (type == BlockType.AIR || type == BlockType.BEDROCK || count <= 0) return;
        droppedItems.add(new DroppedItem(x, y, z, type, count));
    }

    public void spawnArrow(float x, float y, float z, float vx, float vy, float vz) {
        arrows.add(new no.minecraft.entity.Arrow(x, y, z, vx, vy, vz));
    }

    public void spawnMob(no.minecraft.entity.MobType type, float x, float y, float z) {
        mobs.add(new no.minecraft.entity.Mob(type, x, y, z));
    }

    public void clearHostileMobs() {
        shouldClearHostileMobs = true;
    }

    public List<DroppedItem> getDroppedItems() { return droppedItems; }
    public List<no.minecraft.entity.Mob> getMobs() { return mobs; }
    public List<no.minecraft.entity.Arrow> getArrows() { return arrows; }

    public float getWorldTime() { return worldTime; }
    public void setWorldTime(float time) { this.worldTime = Math.max(0.0f, time); }
    public void setTimeOfDay(float dayFraction) {
        float norm = ((dayFraction % 1.0f) + 1.0f) % 1.0f;
        long dayCount = (long) (this.worldTime / DAY_LENGTH_SECONDS);
        this.worldTime = dayCount * DAY_LENGTH_SECONDS + norm * DAY_LENGTH_SECONDS;
    }
    public float getDayFraction() { return (worldTime % DAY_LENGTH_SECONDS) / DAY_LENGTH_SECONDS; }

    public boolean isNight() {
        if (currentDimension != Dimension.OVERWORLD) return false;
        float f = getDayFraction();
        return f >= 0.50f && f <= 0.92f;
    }

    public float getSunLightLevel() {
        if (currentDimension == Dimension.NETHER) return 0.65f;
        if (currentDimension == Dimension.THE_END) return 0.40f;
        float f = getDayFraction();
        double angle = f * 2.0 * Math.PI;
        double sunHeight = Math.sin(angle);
        if (sunHeight > 0.15) {
            return 1.0f;
        } else if (sunHeight < -0.15) {
            return 0.18f;
        } else {
            return (float) (0.18 + (sunHeight + 0.15) / 0.30 * 0.82);
        }
    }

    public boolean isDarkAt(int x, int y, int z) {
        if (currentDimension != Dimension.OVERWORLD) return true;
        boolean openToSky = true;
        for (int checkY = y + 1; checkY < Chunk.SIZE_Y; checkY++) {
            BlockType b = getBlock(x, checkY, z);
            if (b != BlockType.AIR && !b.isTransparent()) {
                openToSky = false;
                break;
            }
        }
        return openToSky ? isNight() : true;
    }

    public void update(float dt, Player player) {
        worldTime += dt;
        int centerCx = Math.floorDiv((int) Math.floor(player.getPosition().x), Chunk.SIZE_X);
        int centerCz = Math.floorDiv((int) Math.floor(player.getPosition().z), Chunk.SIZE_Z);
        updateLoadedChunks(centerCx, centerCz);

        // Check if Ender Dragon died in The End -> win game
        if (currentDimension == Dimension.THE_END && !gameWon) {
            boolean dragonAlive = false;
            for (no.minecraft.entity.Mob m : mobs) {
                if (m.getType() == no.minecraft.entity.MobType.ENDER_DRAGON && !m.isDead()) {
                    dragonAlive = true;
                    break;
                }
            }
            if (!dragonAlive) {
                // If dragon is defeated
                for (no.minecraft.entity.Mob m : mobs) {
                    if (m.getType() == no.minecraft.entity.MobType.ENDER_DRAGON && m.isDead()) {
                        gameWon = true;
                        break;
                    }
                }
            }
        }

        if (shouldClearHostileMobs) {
            shouldClearHostileMobs = false;
            mobs.removeIf(mob -> mob.getType() != no.minecraft.entity.MobType.ENDER_DRAGON &&
                                 mob.getType() != no.minecraft.entity.MobType.END_CRYSTAL);
        }

        // Dropped items
        for (int i = droppedItems.size() - 1; i >= 0; i--) {
            if (i >= droppedItems.size()) continue;
            DroppedItem item = droppedItems.get(i);
            item.update(dt, this, player);
            if (item.isDead() && i < droppedItems.size()) droppedItems.remove(i);
        }

        // Mob spawning
        mobSpawnTimer += dt;
        if (mobSpawnTimer > 3.0f) {
            mobSpawnTimer = 0.0f;
            spawnDimensionMobs(player);
        }

        // Update Mobs
        for (int i = mobs.size() - 1; i >= 0; i--) {
            if (i >= mobs.size()) continue;
            no.minecraft.entity.Mob mob = mobs.get(i);
            mob.update(dt, this, player);

            if (shouldClearHostileMobs) {
                break;
            }

            if (i < mobs.size()) {
                if (mob.isDead() && mob.getType() != no.minecraft.entity.MobType.ENDER_DRAGON) {
                    mobs.remove(i);
                } else if (mob.getPosition().distance(player.getPosition()) > 75.0f && mob.getType() != no.minecraft.entity.MobType.ENDER_DRAGON) {
                    mobs.remove(i);
                }
            }
        }

        if (shouldClearHostileMobs) {
            shouldClearHostileMobs = false;
            mobs.removeIf(mob -> mob.getType() != no.minecraft.entity.MobType.ENDER_DRAGON &&
                                 mob.getType() != no.minecraft.entity.MobType.END_CRYSTAL);
        }

        // Update Arrows
        for (int i = arrows.size() - 1; i >= 0; i--) {
            if (i >= arrows.size()) continue;
            no.minecraft.entity.Arrow arrow = arrows.get(i);
            arrow.update(dt, this, player);
            if (arrow.isDead() && i < arrows.size()) arrows.remove(i);
        }
    }

    private void spawnDimensionMobs(Player player) {
        if (mobs.size() >= 20) return;

        float angle = rand.nextFloat() * (float) (2 * Math.PI);
        float dist = 32.0f + rand.nextFloat() * 22.0f;
        float mx = player.getPosition().x + (float) Math.cos(angle) * dist;
        float mz = player.getPosition().z + (float) Math.sin(angle) * dist;
        int bx = (int) Math.floor(mx);
        int bz = (int) Math.floor(mz);
        int groundY = getSpawnHeight(bx, bz);

        int playerCx = Math.floorDiv((int) Math.floor(player.getPosition().x), Chunk.SIZE_X);
        int playerCz = Math.floorDiv((int) Math.floor(player.getPosition().z), Chunk.SIZE_Z);
        int spawnCx = Math.floorDiv(bx, Chunk.SIZE_X);
        int spawnCz = Math.floorDiv(bz, Chunk.SIZE_Z);

        // Do not spawn mobs in player's chunk or directly neighboring chunks (minimum 2 chunks away)
        if (Math.abs(spawnCx - playerCx) <= 1 && Math.abs(spawnCz - playerCz) <= 1) {
            return;
        }

        if (currentDimension == Dimension.OVERWORLD) {
            if (groundY > 1 && groundY < 50 && isDarkAt(bx, groundY, bz)) {
                no.minecraft.entity.MobType[] types = {
                        no.minecraft.entity.MobType.ZOMBIE,
                        no.minecraft.entity.MobType.CREEPER,
                        no.minecraft.entity.MobType.SPIDER,
                        no.minecraft.entity.MobType.SKELETON,
                        no.minecraft.entity.MobType.ENDERMAN
                };
                spawnMob(types[rand.nextInt(types.length)], mx, groundY + 0.05f, mz);
            }
        } else if (currentDimension == Dimension.NETHER) {
            // Check for nearby active Blaze spawners (within 16 blocks)
            List<int[]> nearSpawners = NetherFortressGenerator.getSpawnersNear(
                    player.getPosition().x, player.getPosition().z, 16.0f, seed);
            for (int[] s : nearSpawners) {
                // Check if spawner block still exists
                if (getBlock(s[0], s[1], s[2]) == BlockType.SPAWNER) {
                    // Count blazes near this spawner
                    int blazeCount = 0;
                    for (no.minecraft.entity.Mob m : mobs) {
                        if (m.getType() == no.minecraft.entity.MobType.BLAZE &&
                            m.getPosition().distance(new org.joml.Vector3f(s[0], s[1], s[2])) < 10.0f) {
                            blazeCount++;
                        }
                    }
                    if (blazeCount < 3) {
                        float spawnX = s[0] + (rand.nextFloat() - 0.5f) * 4.0f;
                        float spawnZ = s[2] + (rand.nextFloat() - 0.5f) * 4.0f;
                        float spawnY = s[1] + 0.5f;
                        spawnMob(no.minecraft.entity.MobType.BLAZE, spawnX, spawnY, spawnZ);
                        no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.4f);
                    }
                }
            }

            // Also regular ambient Nether mob spawning
            if (groundY > 5 && groundY < 45 && getBlock(bx, groundY - 1, bz) != BlockType.LAVA) {
                spawnMob(no.minecraft.entity.MobType.BLAZE, mx, groundY + 0.5f, mz);
            }
        } else if (currentDimension == Dimension.THE_END) {
            if (groundY > 15 && groundY < 45) {
                spawnMob(no.minecraft.entity.MobType.ENDERMAN, mx, groundY + 0.05f, mz);
            }
        }
    }

    public void updateLoadedChunks(int centerCx, int centerCz) {
        int rd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        int unloadDist = rd + 2;

        Map<Long, Chunk> activeChunks = getActiveChunks();
        Set<Long> activeGenerated = getActiveGenerated();

        List<Chunk> newlyGenerated = new ArrayList<>();

        for (int dx = -rd; dx <= rd; dx++) {
            for (int dz = -rd; dz <= rd; dz++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                long key = chunkKey(cx, cz);

                if (!activeGenerated.contains(key)) {
                    Chunk chunk = getOrCreateChunk(cx, cz);
                    generateChunkTerrain(chunk);
                    decorateChunk(cx, cz);
                    activeGenerated.add(key);
                    newlyGenerated.add(chunk);

                    markChunkDirty(cx - 1, cz);
                    markChunkDirty(cx + 1, cz);
                    markChunkDirty(cx, cz - 1);
                    markChunkDirty(cx, cz + 1);
                }
            }
        }

        for (Chunk chunk : newlyGenerated) {
            chunk.setDirty(true);
        }

        Iterator<Map.Entry<Long, Chunk>> iterator = activeChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            Chunk chunk = entry.getValue();
            int dist = Math.max(Math.abs(chunk.getChunkX() - centerCx), Math.abs(chunk.getChunkZ() - centerCz));
            if (dist > unloadDist) {
                chunk.cleanup();
                activeGenerated.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void generateChunkTerrain(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        int startX = cx * Chunk.SIZE_X;
        int startZ = cz * Chunk.SIZE_Z;

        if (currentDimension == Dimension.NETHER) {
            generateNetherChunk(chunk, startX, startZ);
        } else if (currentDimension == Dimension.THE_END) {
            generateEndChunk(chunk, startX, startZ);
        } else {
            generateOverworldChunk(chunk, startX, startZ);
        }
    }

    private void generateOverworldChunk(Chunk chunk, int startX, int startZ) {
        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int wx = startX + lx;
                int wz = startZ + lz;
                int height = getTerrainHeight(wx, wz);

                chunk.setBlock(lx, 0, lz, BlockType.BEDROCK);
                for (int y = 1; y < height - 3; y++) {
                    chunk.setBlock(lx, y, lz, BlockType.STONE);
                }
                boolean isSand = (height <= SEA_LEVEL + 1);
                for (int y = Math.max(1, height - 3); y < height; y++) {
                    chunk.setBlock(lx, y, lz, isSand ? BlockType.SAND : BlockType.DIRT);
                }
                if (height > 0 && height < Chunk.SIZE_Y) {
                    chunk.setBlock(lx, height, lz, isSand ? BlockType.SAND : BlockType.GRASS);
                }
            }
        }

        // Generate underground Stronghold & End Portal room around (48, 14, 48)
        // Chunk (3, 3) covers X [48..63] and Z [48..63]
        if (chunk.getChunkX() == 3 && chunk.getChunkZ() == 3) {
            buildStrongholdPortalRoom(chunk);
        }
    }

    private void buildStrongholdPortalRoom(Chunk chunk) {
        // Build stone brick room inside Chunk 3, 3 at local coordinates lx: 2..10, lz: 2..10, y: 10..16
        for (int lx = 1; lx <= 11; lx++) {
            for (int lz = 1; lz <= 11; lz++) {
                for (int y = 10; y <= 17; y++) {
                    boolean wall = (lx == 1 || lx == 11 || lz == 1 || lz == 11 || y == 10 || y == 17);
                    chunk.setBlock(lx, y, lz, wall ? BlockType.STONE : BlockType.AIR);
                }
            }
        }

        // Horizontal 3x3 portal space in center: local lx: 5..7, lz: 5..7, y: 12
        // Frame surrounding it (12 frames):
        // North side (lz = 4, lx = 5..7)
        // South side (lz = 8, lx = 5..7)
        // West side  (lx = 4, lz = 5..7)
        // East side  (lx = 8, lz = 5..7)
        int py = 12;
        // Pre-fill some frames (e.g., 2 frames already filled like vanilla Minecraft)
        Random r = new Random(seed ^ 42);

        for (int lx = 5; lx <= 7; lx++) {
            chunk.setBlock(lx, py, 4, (r.nextInt(8) == 0) ? BlockType.END_PORTAL_FRAME_FILLED : BlockType.END_PORTAL_FRAME);
            chunk.setBlock(lx, py, 8, (r.nextInt(8) == 0) ? BlockType.END_PORTAL_FRAME_FILLED : BlockType.END_PORTAL_FRAME);
        }
        for (int lz = 5; lz <= 7; lz++) {
            chunk.setBlock(4, py, lz, (r.nextInt(8) == 0) ? BlockType.END_PORTAL_FRAME_FILLED : BlockType.END_PORTAL_FRAME);
            chunk.setBlock(8, py, lz, (r.nextInt(8) == 0) ? BlockType.END_PORTAL_FRAME_FILLED : BlockType.END_PORTAL_FRAME);
        }
    }

    private void generateNetherChunk(Chunk chunk, int startX, int startZ) {
        long chunkSeed = ((long) chunk.getChunkX() * 341873128712L) ^ ((long) chunk.getChunkZ() * 132897987543L) ^ seed;
        Random cRand = new Random(chunkSeed);

        // 1. Bedrock floor & ceiling + Cavern terrain & Biomes
        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int wx = startX + lx;
                int wz = startZ + lz;

                // Bedrock boundaries
                chunk.setBlock(lx, 0, lz, BlockType.BEDROCK);
                chunk.setBlock(lx, 63, lz, BlockType.BEDROCK);

                // Biome calculation based on 2D noise:
                // 0: Nether Wastes (classic Netherrack + Quartz)
                // 1: Soul Sand Valley (Soul sand floor, eerie caverns)
                // 2: Basalt Deltas (Basalt pillars & floors)
                double bVal = Math.sin((wx + offsetX * 0.5) * 0.02) + Math.cos((wz + offsetZ * 0.5) * 0.02);
                int biome = (bVal > 0.6) ? 1 : (bVal < -0.6 ? 2 : 0);

                // Base floor and ceiling terrain thickness
                double fNoise = Math.sin((wx + offsetX) * 0.04) * Math.cos((wz + offsetZ) * 0.04) * 6.0;
                double cNoise = Math.cos((wx - offsetX) * 0.04) * Math.sin((wz - offsetZ) * 0.04) * 6.0;
                int floorThickness = (int) Math.clamp(10 + fNoise, 3, 20);
                int ceilingThickness = (int) Math.clamp(10 + cNoise, 3, 20);

                for (int y = 1; y < Chunk.SIZE_Y - 1; y++) {
                    boolean isSolid = false;
                    BlockType block = BlockType.NETHERRACK;

                    if (y <= floorThickness || y >= Chunk.SIZE_Y - 1 - ceilingThickness) {
                        isSolid = true;
                    } else {
                        // 3D Cavern noise for floating ledges and caves
                        double n3d = Math.sin((wx + offsetX) * 0.06) * Math.cos(y * 0.12) * Math.sin((wz + offsetZ) * 0.06);
                        if (n3d > 0.35) {
                            isSolid = true;
                        }
                    }

                    // Basalt columns in Basalt Deltas biome
                    if (biome == 2 && !isSolid && (lx % 5 == 0 && lz % 5 == 0) && y >= 14 && y <= 35) {
                        isSolid = true;
                        block = BlockType.BASALT;
                    }

                    if (isSolid) {
                        // Apply biome specific surface block
                        if (biome == 1 && y <= floorThickness && y >= floorThickness - 2) {
                            block = BlockType.SOUL_SAND;
                        } else if (biome == 2) {
                            block = BlockType.BASALT;
                        } else {
                            // Nether Wastes: Quartz ore veins (5% chance in Netherrack)
                            if (cRand.nextInt(20) == 0 && y > 5 && y < 58) {
                                block = BlockType.NETHER_QUARTZ_ORE;
                            }
                        }
                        chunk.setBlock(lx, y, lz, block);
                    } else {
                        // Lava Ocean level at Y <= 16
                        if (y <= 16) {
                            chunk.setBlock(lx, y, lz, BlockType.LAVA);
                        } else {
                            chunk.setBlock(lx, y, lz, BlockType.AIR);
                        }
                    }
                }

                // Hanging Glowstone clusters from the ceiling (y: 48..56)
                if (cRand.nextInt(35) == 0) {
                    int glowY = Chunk.SIZE_Y - 1 - ceilingThickness;
                    if (glowY >= 45 && glowY <= 58) {
                        int len = 1 + cRand.nextInt(3);
                        for (int gy = 0; gy < len; gy++) {
                            if (glowY - gy > 20) {
                                chunk.setBlock(lx, glowY - gy, lz, BlockType.GLOWSTONE);
                            }
                        }
                    }
                }
            }
        }

        // 2. Procedural Nether Fortress: continuous connected structures & spawners
        List<NetherFortressGenerator.FortressPiece> pieces =
                NetherFortressGenerator.getPiecesIntersectingChunk(chunk.getChunkX(), chunk.getChunkZ(), seed);
        for (NetherFortressGenerator.FortressPiece piece : pieces) {
            NetherFortressGenerator.carveAndBuildFortressPiece(chunk, piece);
        }

        // 3. Nether Portal frame back to Overworld:
        // Positioned safely at origin chunk (0, 0)
        if (chunk.getChunkX() == 0 && chunk.getChunkZ() == 0) {
            // Find a solid base or create platform at (8, 24, 8)
            for (int px = 6; px <= 11; px++) {
                for (int pz = 6; pz <= 10; pz++) {
                    chunk.setBlock(px, 24, pz, BlockType.NETHER_BRICKS);
                    for (int py = 25; py <= 29; py++) {
                        chunk.setBlock(px, py, pz, BlockType.AIR);
                    }
                }
            }
            buildNetherPortalFrame(chunk, 7, 25, 8);
        }
    }

    private void buildNetherPortalFrame(Chunk chunk, int baseX, int baseY, int baseZ) {
        // 4x5 vertical obsidian frame along X axis
        for (int dx = 0; dx < 4; dx++) {
            chunk.setBlock(baseX + dx, baseY, baseZ, BlockType.OBSIDIAN);
            chunk.setBlock(baseX + dx, baseY + 4, baseZ, BlockType.OBSIDIAN);
        }
        for (int dy = 1; dy <= 3; dy++) {
            chunk.setBlock(baseX, baseY + dy, baseZ, BlockType.OBSIDIAN);
            chunk.setBlock(baseX + 3, baseY + dy, baseZ, BlockType.OBSIDIAN);
            // Portal blocks inside
            chunk.setBlock(baseX + 1, baseY + dy, baseZ, BlockType.NETHER_PORTAL);
            chunk.setBlock(baseX + 2, baseY + dy, baseZ, BlockType.NETHER_PORTAL);
        }
    }

    private void generateEndChunk(Chunk chunk, int startX, int startZ) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();

        // Main End Stone island in chunks (-2..2, -2..2)
        float maxDist = 42.0f;

        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int wx = startX + lx;
                int wz = startZ + lz;
                float d = (float) Math.sqrt(wx * wx + wz * wz);

                if (d < maxDist) {
                    float islandThickness = (1.0f - (d / maxDist)) * 14.0f;
                    int minY = (int) (30 - islandThickness);
                    int maxY = (int) (30 + islandThickness * 0.35f);

                    for (int y = minY; y <= maxY; y++) {
                        chunk.setBlock(lx, y, lz, BlockType.END_STONE);
                    }
                }
            }
        }

        // Obsidian Pillars with End Crystals around the central fountain (r = 24)
        // 4 pillars at: (18, 0), (-18, 0), (0, 18), (0, -18)
        checkAndBuildObsidianPillar(chunk, 18, 0);
        checkAndBuildObsidianPillar(chunk, -18, 0);
        checkAndBuildObsidianPillar(chunk, 0, 18);
        checkAndBuildObsidianPillar(chunk, 0, -18);

        // Center Exit Bedrock Portal & Dragon Egg pedestal at (0, 31, 0) in Chunk (0, 0)
        if (cx == 0 && cz == 0) {
            buildEndExitPortal(chunk);
        }
    }

    private void checkAndBuildObsidianPillar(Chunk chunk, int targetX, int targetZ) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        int startX = cx * Chunk.SIZE_X;
        int startZ = cz * Chunk.SIZE_Z;

        if (targetX >= startX && targetX < startX + Chunk.SIZE_X &&
            targetZ >= startZ && targetZ < startZ + Chunk.SIZE_Z) {
            int lx = targetX - startX;
            int lz = targetZ - startZ;

            // Pillar of Obsidian from y: 30 to 48 (radius 1: 3x3)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int px = lx + dx;
                    int pz = lz + dz;
                    if (px >= 0 && px < Chunk.SIZE_X && pz >= 0 && pz < Chunk.SIZE_Z) {
                        for (int y = 30; y <= 48; y++) {
                            chunk.setBlock(px, y, pz, BlockType.OBSIDIAN);
                        }
                    }
                }
            }

            // Spawn End Crystal entity on top of pillar
            spawnMob(no.minecraft.entity.MobType.END_CRYSTAL, targetX + 0.5f, 49.0f, targetZ + 0.5f);
        }
    }

    private void buildEndExitPortal(Chunk chunk) {
        // Bedrock basin at (0, 31, 0)
        // Local lx: 8, lz: 8 corresponds to world coords (8, 8) in chunk 0,0.
        // World origin (0, 0) is lx: 0, lz: 0 in chunk (0, 0)
        int px = 0;
        int pz = 0;
        int py = 31;

        // Bedrock pillar in center with Dragon Egg on top
        chunk.setBlock(px, py, pz, BlockType.BEDROCK);
        chunk.setBlock(px, py + 1, pz, BlockType.BEDROCK);
        chunk.setBlock(px, py + 2, pz, BlockType.BEDROCK);
        chunk.setBlock(px, py + 3, pz, BlockType.DRAGON_EGG);
    }

    private void decorateChunk(int cx, int cz) {
        if (currentDimension != Dimension.OVERWORLD) return;

        long chunkSeed = ((long) cx * 341873128711L) ^ ((long) cz * 132897987541L) ^ seed;
        Random treeRand = new Random(chunkSeed);

        int startX = cx * Chunk.SIZE_X;
        int startZ = cz * Chunk.SIZE_Z;

        int numTrees = 1 + treeRand.nextInt(3);
        for (int t = 0; t < numTrees; t++) {
            int lx = 2 + treeRand.nextInt(Chunk.SIZE_X - 4);
            int lz = 2 + treeRand.nextInt(Chunk.SIZE_Z - 4);
            int wx = startX + lx;
            int wz = startZ + lz;

            int groundY = getTerrainHeight(wx, wz);
            if (groundY > SEA_LEVEL + 1 && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                spawnTree(wx, groundY + 1, wz, treeRand);
            }
        }
    }

    private void spawnTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 4 + rand.nextInt(2);
        for (int dy = 0; dy < trunkHeight; dy++) {
            setBlock(rootX, rootY + dy, rootZ, BlockType.WOOD);
        }
        int topY = rootY + trunkHeight;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = (dy == 1) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && (dy == 1 || rand.nextBoolean())) {
                        continue;
                    }
                    int tx = rootX + dx;
                    int ty = topY + dy;
                    int tz = rootZ + dz;
                    if (getBlock(tx, ty, tz) == BlockType.AIR) {
                        setBlock(tx, ty, tz, BlockType.LEAVES);
                    }
                }
            }
        }
    }

    private int getTerrainHeight(int x, int z) {
        double sx = x + offsetX;
        double sz = z + offsetZ;
        double n1 = Math.sin(sx * 0.035) * Math.cos(sz * 0.035) * 8.0;
        double n2 = Math.sin((sx + 100.0) * 0.07) * Math.cos((sz + 50.0) * 0.07) * 4.0;
        double n3 = Math.sin(sx * 0.015 + sz * 0.015) * 6.0;

        int baseHeight = 24;
        int height = (int) Math.round(baseHeight + n1 + n2 + n3);
        return Math.clamp(height, 5, Chunk.SIZE_Y - 10);
    }

    public void updateAndRender() {
        for (Chunk chunk : getActiveChunks().values()) {
            chunk.updateMeshIfNeeded();
            chunk.render();
        }
    }

    public int getLoadedChunkCount() {
        return getActiveChunks().size();
    }

    public String getBiomeName(int x, int y, int z) {
        if (currentDimension == Dimension.NETHER) {
            double bVal = Math.sin((x + offsetX * 0.5) * 0.02) + Math.cos((z + offsetZ * 0.5) * 0.02);
            if (bVal > 0.6) return "minecraft:soul_sand_valley";
            if (bVal < -0.6) return "minecraft:basalt_deltas";
            return "minecraft:nether_wastes";
        } else if (currentDimension == Dimension.THE_END) {
            return "minecraft:the_end";
        } else {
            int h = getTerrainHeight(x, z);
            if (h <= SEA_LEVEL + 1) {
                return "minecraft:ocean";
            } else if (h >= 30) {
                return "minecraft:mountains";
            } else {
                return "minecraft:plains";
            }
        }
    }

    public void cleanup() {
        for (Map<Long, Chunk> map : dimensionChunks.values()) {
            for (Chunk chunk : map.values()) {
                chunk.cleanup();
            }
            map.clear();
        }
        for (Set<Long> set : dimensionGenerated.values()) {
            set.clear();
        }
    }
}
