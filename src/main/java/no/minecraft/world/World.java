package no.minecraft.world;

import no.minecraft.player.Player;
import org.joml.Vector3f;
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
    private Vector3f spawnPoint = null;

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

    public Vector3f getSpawnPoint() {
        if (spawnPoint == null) {
            spawnPoint = findSafeSpawnPosition(0, 0);
        }
        return spawnPoint;
    }

    public Dimension getCurrentDimension() {
        return currentDimension;
    }

    public void setCurrentDimension(Dimension currentDimension) {
        this.currentDimension = currentDimension;
    }

    public Map<Dimension, Map<Long, Chunk>> getDimensionChunks() {
        return dimensionChunks;
    }

    public Map<Dimension, Set<Long>> getDimensionGenerated() {
        return dimensionGenerated;
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
        this.spawnPoint = findSafeSpawnPosition(0, 0);
        int scx = Math.floorDiv((int) Math.floor(spawnPoint.x), Chunk.SIZE_X);
        int scz = Math.floorDiv((int) Math.floor(spawnPoint.z), Chunk.SIZE_Z);
        updateLoadedChunks(scx, scz);
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

    public record BlockPos(int x, int y, int z, boolean inWater) {}

    public static class FallingBlock {
        private final int x;
        private int y;
        private final int z;
        private final BlockType type;
        private boolean inWater;
        private float delayTimer;
        private float velocity;
        private float fallProgress;
        private boolean finished = false;

        public FallingBlock(int x, int y, int z, BlockType type, boolean inWater, float delayTimer) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.inWater = inWater;
            this.delayTimer = delayTimer;
            this.velocity = 1.8f;
            this.fallProgress = 0.0f;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public BlockType getType() { return type; }
        public float getDelayTimer() { return delayTimer; }
        public float getVelocity() { return velocity; }
        public boolean isFinished() { return finished; }
    }

    private final List<FallingBlock> fallingBlocks = new ArrayList<>();
    private final List<FallingBlock> pendingFallingBlocks = new ArrayList<>();

    public List<FallingBlock> getFallingBlocks() {
        return fallingBlocks;
    }

    public static boolean isGravityBlock(BlockType type) {
        return type == BlockType.SAND || type == BlockType.GRAVEL;
    }

    public static boolean canFallThrough(BlockType type) {
        if (type == null || type == BlockType.AIR || type == BlockType.WATER || type == BlockType.LAVA) {
            return true;
        }
        return !type.isSolid();
    }

    public void setBlock(int x, int y, int z, BlockType type) {
        BlockType old = getBlock(x, y, z);
        if (old == BlockType.FURNACE && type != BlockType.FURNACE) {
            removeFurnace(x, y, z);
        }
        setBlockInternal(x, y, z, type);
        triggerGravityUpdate(x, y, z);
    }

    public void setBlockInternal(int x, int y, int z, BlockType type) {
        if (y < 0 || y >= Chunk.SIZE_Y) return;
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        Chunk chunk = getOrCreateChunk(cx, cz);

        int localX = (x % Chunk.SIZE_X + Chunk.SIZE_X) % Chunk.SIZE_X;
        int localZ = (z % Chunk.SIZE_Z + Chunk.SIZE_Z) % Chunk.SIZE_Z;
        chunk.setBlock(localX, y, localZ, type);
    }

    public void triggerGravityUpdate(int x, int y, int z) {
        checkAndAddFalling(x, y, z);
        checkAndAddFalling(x, y + 1, z);
        checkAndAddFalling(x + 1, y, z);
        checkAndAddFalling(x - 1, y, z);
        checkAndAddFalling(x, y, z + 1);
        checkAndAddFalling(x, y, z - 1);
    }

    private boolean isAlreadyFalling(int x, int y, int z) {
        for (FallingBlock fb : fallingBlocks) {
            if (!fb.finished && fb.x == x && fb.y == y && fb.z == z) {
                return true;
            }
        }
        for (FallingBlock fb : pendingFallingBlocks) {
            if (!fb.finished && fb.x == x && fb.y == y && fb.z == z) {
                return true;
            }
        }
        return false;
    }

    public void checkAndAddFalling(int x, int y, int z) {
        if (y <= 1 || y >= Chunk.SIZE_Y) return;
        BlockType type = getBlock(x, y, z);
        if (!isGravityBlock(type)) return;
        if (!canFallThrough(getBlock(x, y - 1, z))) return;

        int cy = y;
        float delay = 0.22f; // Initial hesitation delay before gravity takes hold
        while (cy < Chunk.SIZE_Y && isGravityBlock(getBlock(x, cy, z))) {
            if (!isAlreadyFalling(x, cy, z)) {
                boolean inWater = (getBlock(x, cy + 1, z) == BlockType.WATER);
                pendingFallingBlocks.add(new FallingBlock(x, cy, z, getBlock(x, cy, z), inWater, delay));
            }
            cy++;
        }
    }

    private void updateFallingBlocks(float dt) {
        if (!pendingFallingBlocks.isEmpty()) {
            fallingBlocks.addAll(pendingFallingBlocks);
            pendingFallingBlocks.clear();
        }
        if (fallingBlocks.isEmpty()) return;

        // 1. Advance delay and velocity/progress for active blocks
        for (FallingBlock fb : fallingBlocks) {
            if (fb.finished) continue;

            if (getBlock(fb.x, fb.y, fb.z) != fb.type) {
                fb.finished = true;
                continue;
            }

            if (fb.delayTimer > 0.0f) {
                fb.delayTimer -= dt;
                continue;
            }

            boolean submerged = (fb.inWater || getBlock(fb.x, fb.y, fb.z) == BlockType.WATER || getBlock(fb.x, fb.y - 1, fb.z) == BlockType.WATER);
            float gravity = submerged ? 10.0f : 22.0f;
            float maxSpeed = submerged ? 7.0f : 16.0f;

            fb.velocity = Math.min(maxSpeed, fb.velocity + gravity * dt);
            fb.fallProgress += fb.velocity * dt;
        }

        // 2. Process downward movement sorted from lowest Y to highest Y
        fallingBlocks.sort(Comparator.comparingInt(fb -> fb.y));

        boolean playedLandSound = false;

        for (FallingBlock fb : fallingBlocks) {
            if (fb.finished || fb.delayTimer > 0.0f) continue;

            while (fb.fallProgress >= 1.0f && !fb.finished) {
                fb.fallProgress -= 1.0f;

                int x = fb.x;
                int y = fb.y;
                int z = fb.z;

                if (y <= 1) {
                    fb.finished = true;
                    if (!playedLandSound) {
                        no.minecraft.sound.SoundManager.getInstance().play(fb.type.getDigSound(), 0.6f);
                        playedLandSound = true;
                    }
                    break;
                }

                BlockType below = getBlock(x, y - 1, z);
                if (canFallThrough(below)) {
                    BlockType replacement = fb.inWater ? BlockType.WATER : BlockType.AIR;

                    setBlockInternal(x, y, z, replacement);
                    setBlockInternal(x, y - 1, z, fb.type);

                    fb.y = y - 1;
                    fb.inWater = (below == BlockType.WATER);

                    // Check above and horizontal neighbors in case they were supported by this block
                    checkAndAddFalling(x, y + 1, z);
                    checkAndAddFalling(x + 1, y, z);
                    checkAndAddFalling(x - 1, y, z);
                    checkAndAddFalling(x, y, z + 1);
                    checkAndAddFalling(x, y, z - 1);
                } else {
                    fb.finished = true;
                    if (!playedLandSound) {
                        no.minecraft.sound.SoundManager.getInstance().play(fb.type.getDigSound(), 0.6f);
                        playedLandSound = true;
                    }
                    break;
                }
            }
        }

        fallingBlocks.removeIf(fb -> fb.finished);
    }

    public boolean isSafeSolidSpawn(int x, int y, int z) {
        if (y < 1 || y >= Chunk.SIZE_Y - 2) return false;

        BlockType ground = getBlock(x, y, z);
        if (!ground.isSolid() || ground == BlockType.CACTUS || ground == BlockType.LAVA || ground == BlockType.WATER) {
            return false;
        }

        // If ground is sand or gravel, make sure it has solid support below
        if (isGravityBlock(ground) && canFallThrough(getBlock(x, y - 1, z))) {
            return false;
        }

        BlockType feet = getBlock(x, y + 1, z);
        if (feet.isSolid() || feet == BlockType.WATER || feet == BlockType.LAVA || feet == BlockType.CACTUS) {
            return false;
        }

        BlockType head = getBlock(x, y + 2, z);
        if (head.isSolid() || head == BlockType.WATER || head == BlockType.LAVA || head == BlockType.CACTUS) {
            return false;
        }

        // In Overworld, cannot be submerged or underneath water
        if (currentDimension == Dimension.OVERWORLD && y < SEA_LEVEL) {
            for (int cy = y + 1; cy <= SEA_LEVEL; cy++) {
                if (getBlock(x, cy, z) == BlockType.WATER) {
                    return false;
                }
            }
        }

        // Avoid spawning directly touching cactus
        if (getBlock(x + 1, y + 1, z) == BlockType.CACTUS ||
            getBlock(x - 1, y + 1, z) == BlockType.CACTUS ||
            getBlock(x, y + 1, z + 1) == BlockType.CACTUS ||
            getBlock(x, y + 1, z - 1) == BlockType.CACTUS) {
            return false;
        }

        return true;
    }

    public int getSpawnHeight(int x, int z) {
        for (int y = Chunk.SIZE_Y - 3; y >= 1; y--) {
            if (isSafeSolidSpawn(x, y, z)) {
                return y + 1;
            }
        }
        return -1;
    }

    public Vector3f findSafeSpawnPosition(int originX, int originZ) {
        int originCx = Math.floorDiv(originX, Chunk.SIZE_X);
        int originCz = Math.floorDiv(originZ, Chunk.SIZE_Z);
        ensureChunkGenerated(originCx, originCz);

        // 1. First check origin itself (prefer non-leaves ground)
        int y = getSpawnHeight(originX, originZ);
        if (y > 0 && getBlock(originX, y - 1, originZ) != BlockType.LEAVES) {
            return new Vector3f(originX + 0.5f, y + 0.05f, originZ + 0.5f);
        }

        // 2. Search outwards in an expanding box pattern for bare ground
        int maxRadius = 160;
        for (int r = 1; r <= maxRadius; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz : new int[]{-r, r}) {
                    int tx = originX + dx;
                    int tz = originZ + dz;
                    int cx = Math.floorDiv(tx, Chunk.SIZE_X);
                    int cz = Math.floorDiv(tz, Chunk.SIZE_Z);
                    ensureChunkGenerated(cx, cz);
                    int sy = getSpawnHeight(tx, tz);
                    if (sy > 0 && getBlock(tx, sy - 1, tz) != BlockType.LEAVES) {
                        return new Vector3f(tx + 0.5f, sy + 0.05f, tz + 0.5f);
                    }
                }
            }
            for (int dz = -r + 2; dz <= r - 2; dz += 2) {
                for (int dx : new int[]{-r, r}) {
                    int tx = originX + dx;
                    int tz = originZ + dz;
                    int cx = Math.floorDiv(tx, Chunk.SIZE_X);
                    int cz = Math.floorDiv(tz, Chunk.SIZE_Z);
                    ensureChunkGenerated(cx, cz);
                    int sy = getSpawnHeight(tx, tz);
                    if (sy > 0 && getBlock(tx, sy - 1, tz) != BlockType.LEAVES) {
                        return new Vector3f(tx + 0.5f, sy + 0.05f, tz + 0.5f);
                    }
                }
            }
        }

        // 3. Fallback: accept leaves if no bare ground was found
        for (int r = 0; r <= maxRadius; r += 4) {
            int tx = originX + r;
            int tz = originZ;
            int cx = Math.floorDiv(tx, Chunk.SIZE_X);
            int cz = Math.floorDiv(tz, Chunk.SIZE_Z);
            ensureChunkGenerated(cx, cz);
            int sy = getSpawnHeight(tx, tz);
            if (sy > 0) {
                return new Vector3f(tx + 0.5f, sy + 0.05f, tz + 0.5f);
            }
        }

        // 4. Guaranteed absolute fallback: build a safe 3x3 solid platform at SEA_LEVEL
        int spawnY = (currentDimension == Dimension.OVERWORLD) ? SEA_LEVEL : 30;
        for (int px = -1; px <= 1; px++) {
            for (int pz = -1; pz <= 1; pz++) {
                setBlock(originX + px, spawnY, originZ + pz, BlockType.GRASS);
                setBlock(originX + px, spawnY + 1, originZ + pz, BlockType.AIR);
                setBlock(originX + px, spawnY + 2, originZ + pz, BlockType.AIR);
            }
        }
        return new Vector3f(originX + 0.5f, spawnY + 1.05f, originZ + 0.5f);
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
                Vector3f safe = getSpawnPoint();
                int cx = Math.floorDiv((int) Math.floor(safe.x), Chunk.SIZE_X);
                int cz = Math.floorDiv((int) Math.floor(safe.z), Chunk.SIZE_Z);
                updateLoadedChunks(cx, cz);
                player.teleportTo(safe.x, safe.y, safe.z);
            }
        }
    }

    public void spawnItemDrop(float x, float y, float z, BlockType type, int count) {
        if (type == BlockType.AIR || type == BlockType.BEDROCK || count <= 0) return;
        droppedItems.add(new DroppedItem(x, y, z, type, count));
    }

    public void spawnItemDrop(float x, float y, float z, float vx, float vy, float vz, BlockType type, int count) {
        if (type == BlockType.AIR || type == BlockType.BEDROCK || count <= 0) return;
        DroppedItem item = new DroppedItem(x, y, z, type, count);
        item.getVelocity().set(vx, vy, vz);
        item.setPickupDelay(1.2f);
        droppedItems.add(item);
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
    private final List<no.minecraft.entity.Boat> boats = new ArrayList<>();
    public List<no.minecraft.entity.Boat> getBoats() { return boats; }

    public no.minecraft.entity.Boat spawnBoat(float x, float y, float z, float yaw) {
        no.minecraft.entity.Boat boat = new no.minecraft.entity.Boat(x, y, z, yaw);
        boats.add(boat);
        return boat;
    }

    private final Map<Long, FurnaceData> furnaces = new HashMap<>();

    public static long blockPosKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) (y & 0xFFF)) << 26) | ((long) z & 0x3FFFFFFL);
    }

    public FurnaceData getOrCreateFurnace(int x, int y, int z) {
        return furnaces.computeIfAbsent(blockPosKey(x, y, z), k -> new FurnaceData(x, y, z));
    }

    public FurnaceData getFurnace(int x, int y, int z) {
        return furnaces.get(blockPosKey(x, y, z));
    }

    public void removeFurnace(int x, int y, int z) {
        FurnaceData fd = furnaces.remove(blockPosKey(x, y, z));
        if (fd != null) {
            if (!fd.getInput().isEmpty()) {
                spawnItemDrop(x + 0.5f, y + 0.5f, z + 0.5f, fd.getInput().getType(), fd.getInput().getCount());
                fd.getInput().clear();
            }
            if (!fd.getFuel().isEmpty()) {
                spawnItemDrop(x + 0.5f, y + 0.5f, z + 0.5f, fd.getFuel().getType(), fd.getFuel().getCount());
                fd.getFuel().clear();
            }
            if (!fd.getOutput().isEmpty()) {
                spawnItemDrop(x + 0.5f, y + 0.5f, z + 0.5f, fd.getOutput().getType(), fd.getOutput().getCount());
                fd.getOutput().clear();
            }
        }
    }

    public Map<Long, FurnaceData> getFurnaces() {
        return Collections.unmodifiableMap(furnaces);
    }

    public void setFurnaces(List<FurnaceData> list) {
        furnaces.clear();
        if (list != null) {
            for (FurnaceData fd : list) {
                furnaces.put(blockPosKey(fd.getX(), fd.getY(), fd.getZ()), fd);
            }
        }
    }

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

    public boolean isOpenToSky(int x, int y, int z) {
        if (currentDimension != Dimension.OVERWORLD) return false;
        for (int checkY = y + 1; checkY < Chunk.SIZE_Y; checkY++) {
            BlockType b = getBlock(x, checkY, z);
            if (b != BlockType.AIR && !b.isTransparent()) {
                return false;
            }
        }
        return true;
    }

    public boolean isDarkAt(int x, int y, int z) {
        if (currentDimension != Dimension.OVERWORLD) return true;
        // Torches prevent darkness in an area of radius 6
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= 25) {
                        if (getBlock(x + dx, y + dy, z + dz) == BlockType.TORCH) {
                            return false;
                        }
                    }
                }
            }
        }
        return isOpenToSky(x, y, z) ? isNight() : true;
    }

    public void update(float dt, Player player) {
        worldTime += dt;
        int centerCx = Math.floorDiv((int) Math.floor(player.getPosition().x), Chunk.SIZE_X);
        int centerCz = Math.floorDiv((int) Math.floor(player.getPosition().z), Chunk.SIZE_Z);
        updateLoadedChunks(centerCx, centerCz);

        // Falling blocks (sand & gravel gravity with delay and acceleration)
        if (!fallingBlocks.isEmpty() || !pendingFallingBlocks.isEmpty()) {
            updateFallingBlocks(dt);
        }

        // Active furnaces (smelting / cooking)
        if (!furnaces.isEmpty()) {
            for (FurnaceData fd : furnaces.values()) {
                fd.update(dt);
            }
        }

        // Boats
        if (!boats.isEmpty()) {
            for (no.minecraft.entity.Boat b : boats) {
                b.update(dt, this, player);
            }
            boats.removeIf(no.minecraft.entity.Boat::isDead);
        }

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
                } else if (mob.getType() != no.minecraft.entity.MobType.ENDER_DRAGON && mob.getType() != no.minecraft.entity.MobType.END_CRYSTAL) {
                    float dist = mob.getPosition().distance(player.getPosition());
                    if (mob.getType().isHostile() && dist > 75.0f) {
                        mobs.remove(i);
                    } else if (mob.getType().isPassive() && dist > 120.0f) {
                        mobs.remove(i);
                    }
                }
            }
        }

        if (shouldClearHostileMobs) {
            shouldClearHostileMobs = false;
            mobs.removeIf(mob -> mob.getType().isHostile() &&
                                 mob.getType() != no.minecraft.entity.MobType.ENDER_DRAGON &&
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
            if (groundY > 1 && groundY < 55) {
                if (isDarkAt(bx, groundY, bz)) {
                    no.minecraft.entity.MobType[] types = {
                            no.minecraft.entity.MobType.ZOMBIE,
                            no.minecraft.entity.MobType.CREEPER,
                            no.minecraft.entity.MobType.SPIDER,
                            no.minecraft.entity.MobType.SKELETON,
                            no.minecraft.entity.MobType.ENDERMAN
                    };
                    spawnMob(types[rand.nextInt(types.length)], mx, groundY + 0.05f, mz);
                } else {
                    // Daytime / lit surface: spawn passive farm animals on grass
                    BlockType surfaceBlock = getBlock(bx, groundY - 1, bz);
                    if (surfaceBlock == BlockType.GRASS) {
                        long passiveCount = mobs.stream().filter(m -> m.getType().isPassive()).count();
                        if (passiveCount < 10) {
                            no.minecraft.entity.MobType[] passiveTypes = {
                                    no.minecraft.entity.MobType.PIG,
                                    no.minecraft.entity.MobType.COW,
                                    no.minecraft.entity.MobType.SHEEP,
                                    no.minecraft.entity.MobType.CHICKEN
                            };
                            spawnMob(passiveTypes[rand.nextInt(passiveTypes.length)], mx, groundY + 0.05f, mz);
                        }
                    }
                }
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

    public Chunk ensureChunkGenerated(int cx, int cz) {
        long key = chunkKey(cx, cz);
        Set<Long> activeGenerated = getActiveGenerated();
        Chunk chunk = getOrCreateChunk(cx, cz);
        if (!activeGenerated.contains(key)) {
            generateChunkTerrain(chunk);
            decorateChunk(cx, cz);
            activeGenerated.add(key);
            chunk.setDirty(true);
            markChunkDirty(cx - 1, cz);
            markChunkDirty(cx + 1, cz);
            markChunkDirty(cx, cz - 1);
            markChunkDirty(cx, cz + 1);
        }
        return chunk;
    }

    public void updateLoadedChunks(int centerCx, int centerCz) {
        int rd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        int unloadDist = rd + 2;

        Map<Long, Chunk> activeChunks = getActiveChunks();
        Set<Long> activeGenerated = getActiveGenerated();

        for (int dx = -rd; dx <= rd; dx++) {
            for (int dz = -rd; dz <= rd; dz++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                ensureChunkGenerated(cx, cz);
            }
        }

        Iterator<Map.Entry<Long, Chunk>> iterator = activeChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            Chunk chunk = entry.getValue();
            int dist = Math.max(Math.abs(chunk.getChunkX() - centerCx), Math.abs(chunk.getChunkZ() - centerCz));
            if (dist > unloadDist) {
                chunk.unloadMesh();
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
                String biome = getBiomeName(wx, height, wz);

                // Bedrock at y = 0
                chunk.setBlock(lx, 0, lz, BlockType.BEDROCK);

                // Underground stone layers, caves and ores
                for (int y = 1; y < height - 3; y++) {
                    if (isOverworldCave(wx, y, wz, height)) {
                        chunk.setBlock(lx, y, lz, (y <= 3) ? BlockType.LAVA : BlockType.AIR);
                    } else {
                        chunk.setBlock(lx, y, lz, getUndergroundBlock(wx, y, wz));
                    }
                }

                // Biome-specific surface layers
                if (biome.equals("minecraft:ocean")) {
                    // Ocean floor: sand and gravel
                    boolean isGravel = (Math.abs(wx * 7 + wz * 13) % 4 == 0);
                    for (int y = Math.max(1, height - 3); y <= height; y++) {
                        chunk.setBlock(lx, y, lz, isGravel ? BlockType.GRAVEL : BlockType.SAND);
                    }
                    // Fill water from ocean floor up to SEA_LEVEL
                    for (int y = height + 1; y <= SEA_LEVEL; y++) {
                        chunk.setBlock(lx, y, lz, BlockType.WATER);
                    }
                } else if (biome.equals("minecraft:desert")) {
                    // Sandstone stratum under sand
                    for (int y = Math.max(1, height - 6); y < height - 2; y++) {
                        chunk.setBlock(lx, y, lz, BlockType.SANDSTONE);
                    }
                    // Sand layers on top
                    for (int y = Math.max(1, height - 2); y <= height; y++) {
                        chunk.setBlock(lx, y, lz, BlockType.SAND);
                    }
                } else if (biome.equals("minecraft:snowy_plains")) {
                    for (int y = Math.max(1, height - 3); y < height; y++) {
                        chunk.setBlock(lx, y, lz, BlockType.DIRT);
                    }
                    if (height > 0 && height < Chunk.SIZE_Y) {
                        chunk.setBlock(lx, height, lz, BlockType.SNOW_BLOCK);
                    }
                } else if (biome.equals("minecraft:mountains")) {
                    if (height >= 38) {
                        // High peaks: Snow caps
                        for (int y = Math.max(1, height - 2); y <= height; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.SNOW_BLOCK);
                        }
                    } else if (height >= 29) {
                        // Rocky cliffs: Stone and gravel scree
                        boolean isGravel = (Math.abs(wx * 11 + wz * 17) % 5 == 0);
                        for (int y = Math.max(1, height - 3); y <= height; y++) {
                            chunk.setBlock(lx, y, lz, isGravel ? BlockType.GRAVEL : BlockType.STONE);
                        }
                    } else {
                        // Lower mountain base: Dirt and grass
                        for (int y = Math.max(1, height - 3); y < height; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.DIRT);
                        }
                        chunk.setBlock(lx, height, lz, BlockType.GRASS);
                    }
                } else {
                    // Plains or Forest
                    boolean isBeach = (height <= SEA_LEVEL + 1);
                    for (int y = Math.max(1, height - 3); y < height; y++) {
                        chunk.setBlock(lx, y, lz, isBeach ? BlockType.SAND : BlockType.DIRT);
                    }
                    if (height > 0 && height < Chunk.SIZE_Y) {
                        chunk.setBlock(lx, height, lz, isBeach ? BlockType.SAND : BlockType.GRASS);
                    }
                }
            }
        }

        // Generate underground Stronghold & End Portal room around (48, 14, 48)
        // Chunk (3, 3) covers X [48..63] and Z [48..63]
        if (chunk.getChunkX() == 3 && chunk.getChunkZ() == 3) {
            buildStrongholdPortalRoom(chunk);
        }
    }

    private boolean isOverworldCave(int wx, int y, int wz, int height) {
        if (y <= 1 || y >= height - 3) return false;
        if (height <= SEA_LEVEL && y >= height - 6) return false;

        double sx = wx + offsetX;
        double sz = wz + offsetZ;

        // Two continuous 3D worm noise tunnels
        double n1 = Math.sin(sx * 0.08 + y * 0.12) * Math.cos(sz * 0.08) + Math.sin(y * 0.07) * 0.4;
        double n2 = Math.cos(sx * 0.08) * Math.sin(sz * 0.08 + y * 0.12) + Math.cos((sx + sz) * 0.05) * 0.4;

        return (n1 * n1 + n2 * n2) < 0.06;
    }

    private BlockType getUndergroundBlock(int wx, int y, int wz) {
        int cx2 = Math.floorDiv(wx, 2);
        int cy2 = Math.floorDiv(y, 2);
        int cz2 = Math.floorDiv(wz, 2);

        long clusterHash = ((long) cx2 * 3129871L) ^ ((long) cz2 * 116129781L) ^ ((long) cy2 * 8429183L) ^ seed;
        clusterHash = (clusterHash ^ (clusterHash >> 16)) * 0x45d9f3bL;
        clusterHash = clusterHash ^ (clusterHash >> 16);
        int clusterType = (int) Math.abs(clusterHash % 1000);

        long blockHash = ((long) wx * 918273L) ^ ((long) wz * 482917L) ^ ((long) y * 182739L);
        int blockVar = (int) Math.abs(blockHash % 10);

        // Diamond Ore: Deep underground (y <= 14), rare
        if (y <= 14 && clusterType >= 10 && clusterType <= 13 && blockVar < 7) {
            return BlockType.DIAMOND_ORE;
        }

        // Gold Ore: Deep underground (y <= 24), uncommon
        if (y <= 24 && clusterType >= 20 && clusterType <= 28 && blockVar < 7) {
            return BlockType.GOLD_ORE;
        }

        // Iron Ore: From y = 2 to y = 42, frequent
        if (y <= 42 && clusterType >= 40 && clusterType <= 85 && blockVar < 8) {
            return BlockType.IRON_ORE;
        }

        // Coal Ore: Abundant anywhere underground
        if (clusterType >= 100 && clusterType <= 170 && blockVar < 8) {
            return BlockType.COAL_ORE;
        }

        // Gravel pockets: underground gravel veins
        if (clusterType >= 180 && clusterType <= 195) {
            return BlockType.GRAVEL;
        }

        return BlockType.STONE;
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
        Random decRand = new Random(chunkSeed);

        int startX = cx * Chunk.SIZE_X;
        int startZ = cz * Chunk.SIZE_Z;

        // Sample biome in chunk center
        int midX = startX + Chunk.SIZE_X / 2;
        int midZ = startZ + Chunk.SIZE_Z / 2;
        int midH = getTerrainHeight(midX, midZ);
        String biome = getBiomeName(midX, midH, midZ);

        if (biome.equals("minecraft:ocean")) {
            return; // No vegetation in open ocean
        }

        if (biome.equals("minecraft:desert")) {
            // Cacti: 1-3 per chunk on sand
            int numCacti = 1 + decRand.nextInt(3);
            for (int i = 0; i < numCacti; i++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.SAND) {
                    spawnCactus(wx, groundY + 1, wz, decRand);
                }
            }
            return;
        }

        if (biome.equals("minecraft:snowy_plains")) {
            // Pine/Spruce trees: 1-3 per chunk on snow
            int numTrees = 1 + decRand.nextInt(3);
            for (int t = 0; t < numTrees; t++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.SNOW_BLOCK) {
                    spawnPineTree(wx, groundY + 1, wz, decRand);
                }
            }
            return;
        }

        if (biome.equals("minecraft:forest")) {
            // Dense forest: 4-7 oak trees per chunk
            int numTrees = 4 + decRand.nextInt(4);
            for (int t = 0; t < numTrees; t++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                    spawnTree(wx, groundY + 1, wz, decRand);
                }
            }
            return;
        }

        if (biome.equals("minecraft:mountains")) {
            // Rare mountain tree on grassy terraces
            if (decRand.nextInt(3) == 0) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && groundY < 35 && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                    spawnTree(wx, groundY + 1, wz, decRand);
                }
            }
            return;
        }

        // Plains: 0-2 trees per chunk
        int numTrees = decRand.nextInt(3);
        for (int t = 0; t < numTrees; t++) {
            int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
            int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
            int wx = startX + lx;
            int wz = startZ + lz;

            int groundY = getTerrainHeight(wx, wz);
            if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                spawnTree(wx, groundY + 1, wz, decRand);
            }
        }
    }

    private void spawnCactus(int rootX, int rootY, int rootZ, Random rand) {
        int height = 1 + rand.nextInt(3);
        for (int dy = 0; dy < height; dy++) {
            if (getBlock(rootX, rootY + dy, rootZ) == BlockType.AIR) {
                setBlock(rootX, rootY + dy, rootZ, BlockType.CACTUS);
            }
        }
    }

    private void spawnPineTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 6 + rand.nextInt(3);
        for (int dy = 0; dy < trunkHeight; dy++) {
            setBlock(rootX, rootY + dy, rootZ, BlockType.WOOD);
        }
        int topY = rootY + trunkHeight;
        // Tip
        setBlock(rootX, topY, rootZ, BlockType.LEAVES);

        // Layer 1 (radius 1 cross)
        setBlock(rootX + 1, topY - 1, rootZ, BlockType.LEAVES);
        setBlock(rootX - 1, topY - 1, rootZ, BlockType.LEAVES);
        setBlock(rootX, topY - 1, rootZ + 1, BlockType.LEAVES);
        setBlock(rootX, topY - 1, rootZ - 1, BlockType.LEAVES);

        // Layer 2 (3x3 square)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (getBlock(rootX + dx, topY - 2, rootZ + dz) == BlockType.AIR) {
                    setBlock(rootX + dx, topY - 2, rootZ + dz, BlockType.LEAVES);
                }
            }
        }

        // Layer 3 (radius 2 cross)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (getBlock(rootX + dx, topY - 3, rootZ + dz) == BlockType.AIR) {
                    setBlock(rootX + dx, topY - 3, rootZ + dz, BlockType.LEAVES);
                }
            }
        }

        // Layer 4 (5x5 square with cut corners)
        if (trunkHeight >= 7) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rand.nextBoolean()) continue;
                    if (getBlock(rootX + dx, topY - 4, rootZ + dz) == BlockType.AIR) {
                        setBlock(rootX + dx, topY - 4, rootZ + dz, BlockType.LEAVES);
                    }
                }
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

        // 1. Continentalness noise (oceans vs coastal lowlands vs inland continents)
        double cont = Math.sin(sx * 0.005) * Math.cos(sz * 0.005)
                    + 0.5 * Math.sin((sx + 150.0) * 0.010) * Math.cos((sz + 80.0) * 0.010);

        // 2. Mountain ridge noise (ridged multifractal for sharp, continuous peaks & crests)
        double r1 = Math.sin((sx + 350.0) * 0.008) * Math.cos((sz - 250.0) * 0.008);
        double r2 = Math.sin((sx - 180.0) * 0.016) * Math.cos((sz + 220.0) * 0.016);
        double ridge = 1.0 - Math.abs(r1 + 0.5 * r2); // 0.0 (valleys) to 1.5 (sharp ridges)

        // 3. Valley & river canyon carving noise
        double vNoise = Math.sin((sx * 0.5 + sz * 0.5) * 0.011) * Math.cos((sx * 0.5 - sz * 0.5) * 0.011);
        double valley = Math.abs(vNoise);

        // 4. Rolling hills & terrain undulations
        double hills = Math.sin((sx + 80.0) * 0.022) * Math.cos((sz - 60.0) * 0.022) * 3.5
                     + Math.sin((sx - 200.0) * 0.014) * Math.cos((sz + 150.0) * 0.014) * 5.0;

        // 5. Fine surface roughness
        double detail = Math.sin(sx * 0.045) * Math.cos(sz * 0.045) * 2.5
                      + Math.sin((sx + 40.0) * 0.09) * Math.cos((sz + 70.0) * 0.09) * 1.2;

        double base;
        if (cont < -0.32) {
            // Deep ocean floor
            base = 9.0 + (cont + 0.32) * 8.0 + detail * 0.6;
        } else if (cont < -0.15) {
            // Coastal shelf & beaches
            base = 13.5 + (cont + 0.15) * 13.0 + detail * 0.8;
        } else {
            // Inland terrain: rolling plains & plateaus
            base = 20.5 + (cont + 0.15) * 7.5;

            // Dramatic mountain spines and alpine peaks
            if (ridge > 0.82) {
                double mFactor = Math.pow((ridge - 0.82) / 0.68, 1.35);
                double mountainHeight = 28.0 + mFactor * 26.0; // Up to height 54
                base = Math.max(base, mountainHeight);
            }

            // Valley & gorge erosion between high grounds
            if (valley < 0.12 && base > SEA_LEVEL + 3.0) {
                double carve = (1.0 - valley / 0.12) * 8.0;
                base = Math.max(SEA_LEVEL + 1.0, base - carve);
            }

            base += hills + detail;
        }

        int height = (int) Math.round(base);
        return (int) Math.clamp(height, 5, Chunk.SIZE_Y - 7);
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
            if (h <= SEA_LEVEL) {
                return "minecraft:ocean";
            }
            if (h >= 32) {
                return "minecraft:mountains";
            }
            double sx = x + offsetX;
            double sz = z + offsetZ;
            double temp = Math.sin((sx + 800.0) * 0.010) * Math.cos((sz + 600.0) * 0.010)
                        + 0.5 * Math.sin((sx + 180.0) * 0.022) * Math.cos((sz + 40.0) * 0.022);
            double hum  = Math.sin((sx - 500.0) * 0.010) * Math.cos((sz - 300.0) * 0.010)
                        + 0.5 * Math.sin((sx + 120.0) * 0.021) * Math.cos((sz - 160.0) * 0.021);

            if (temp > 0.30) {
                return "minecraft:desert";
            } else if (temp < -0.30) {
                return "minecraft:snowy_plains";
            } else if (hum > 0.05) {
                return "minecraft:forest";
            } else {
                return "minecraft:plains";
            }
        }
    }

    public void cleanup() {
        fallingBlocks.clear();
        pendingFallingBlocks.clear();
        furnaces.clear();
        boats.clear();
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
