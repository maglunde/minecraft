package no.minecraft.world;

import no.minecraft.player.Player;
import org.joml.Vector3f;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class World {
    public static final int RENDER_DISTANCE = 5;
    public static final int UNLOAD_DISTANCE = RENDER_DISTANCE + 2;
    public static final int SEA_LEVEL = 18;

    // Dimension management
    private Dimension currentDimension = Dimension.OVERWORLD;
    private final Map<Dimension, Map<Long, Chunk>> dimensionChunks = new EnumMap<>(Dimension.class);
    private final Map<Dimension, Set<Long>> dimensionGenerated = new EnumMap<>(Dimension.class);
    private final Map<Dimension, Set<Long>> savedChunkKeys = new EnumMap<>(Dimension.class);

    // Save directory this world is persisted to (null for unsaved worlds)
    private Path saveDir = null;

    private long seed;
    private double offsetX;
    private double offsetZ;

    // Stronghold coordinates in Overworld
    public static final int STRONGHOLD_X = 54;
    public static final int STRONGHOLD_Y = 12;
    public static final int STRONGHOLD_Z = 54;

    private final List<DroppedItem> droppedItems = new ArrayList<>();
    private final List<no.minecraft.entity.Mob> mobs = new ArrayList<>();
    private final List<no.minecraft.entity.Arrow> arrows = new ArrayList<>();
    private final List<no.minecraft.entity.EnderPearl> enderPearls = new ArrayList<>();
    private final List<no.minecraft.entity.EyeOfEnder> eyeOfEnders = new ArrayList<>();
    private float mobSpawnTimer = 0.0f;
    private final Random rand = new Random();
    private boolean shouldClearHostileMobs = false;

    public static final float DAY_LENGTH_SECONDS = 600.0f;
    private float worldTime = 20.0f;

    // Victory state when Dragon is slain
    private boolean gameWon = false;
    private Vector3f spawnPoint = null;

    private int lastCenterCx = 0;
    private int lastCenterCz = 0;
    private int lastUpdateCx = Integer.MIN_VALUE;
    private int lastUpdateCz = Integer.MIN_VALUE;
    private int lastUpdateRd = -1;
    private int renderedChunkCount = 0;

    public int getRenderedChunkCount() {
        return renderedChunkCount;
    }

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
        savedChunkKeys.clear();
        for (Dimension dim : Dimension.values()) {
            dimensionChunks.put(dim, new ConcurrentHashMap<>());
            dimensionGenerated.put(dim, ConcurrentHashMap.newKeySet());
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
        return dimensionChunks.computeIfAbsent(currentDimension, k -> new ConcurrentHashMap<>());
    }

    private Set<Long> getActiveGenerated() {
        return dimensionGenerated.computeIfAbsent(currentDimension, k -> ConcurrentHashMap.newKeySet());
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
            chunk.invalidateLight();
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
        if (old == BlockType.CHEST && type != BlockType.CHEST) {
            removeChest(x, y, z);
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

    // Decoration-only block writes: never create phantom chunks in ungenerated neighbors.
    // Dropped border writes are repaired when the neighbor chunk is generated later.
    private void setDecorationBlock(int x, int y, int z, BlockType type) {
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        if (!getActiveGenerated().contains(chunkKey(cx, cz))) return;
        setBlockInternal(x, y, z, type);
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

        // 2. Search outwards in an expanding box pattern for bare ground (within immediate 2-chunk radius)
        int maxRadius = 32;
        for (int r = 2; r <= maxRadius; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
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
            for (int dz = -r + 4; dz <= r - 4; dz += 4) {
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
        for (int r = 0; r <= maxRadius; r += 8) {
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
        enderPearls.clear();
        eyeOfEnders.clear();
        droppedItems.clear();
        this.lastUpdateCx = Integer.MIN_VALUE;
        this.lastUpdateCz = Integer.MIN_VALUE;
        this.lastUpdateRd = -1;

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
        spawnArrow(x, y, z, vx, vy, vz, false);
    }

    public void spawnArrow(float x, float y, float z, float vx, float vy, float vz, boolean hostileShooter) {
        arrows.add(new no.minecraft.entity.Arrow(x, y, z, vx, vy, vz, hostileShooter));
    }

    public void spawnArrow(float x, float y, float z, float vx, float vy, float vz, Player shooter, boolean hostileShooter) {
        arrows.add(new no.minecraft.entity.Arrow(x, y, z, vx, vy, vz, shooter, hostileShooter));
    }

    public void spawnEnderPearl(float x, float y, float z, float vx, float vy, float vz, Player owner) {
        enderPearls.add(new no.minecraft.entity.EnderPearl(x, y, z, vx, vy, vz, owner));
    }

    public void spawnEyeOfEnder(float x, float y, float z, float vx, float vy, float vz) {
        eyeOfEnders.add(new no.minecraft.entity.EyeOfEnder(x, y, z, vx, vy, vz));
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
    public List<no.minecraft.entity.EnderPearl> getEnderPearls() { return enderPearls; }
    public List<no.minecraft.entity.EyeOfEnder> getEyeOfEnders() { return eyeOfEnders; }
    private final List<no.minecraft.entity.Boat> boats = new ArrayList<>();
    public List<no.minecraft.entity.Boat> getBoats() { return boats; }

    public no.minecraft.entity.Boat spawnBoat(float x, float y, float z, float yaw) {
        no.minecraft.entity.Boat boat = new no.minecraft.entity.Boat(x, y, z, yaw);
        boats.add(boat);
        return boat;
    }

    private final Map<Long, FurnaceData> furnaces = new HashMap<>();
    private final Map<Long, ChestData> chests = new HashMap<>();

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

    public ChestData getOrCreateChest(int x, int y, int z) {
        return chests.computeIfAbsent(blockPosKey(x, y, z), k -> new ChestData(x, y, z));
    }

    public ChestData getChest(int x, int y, int z) {
        return chests.get(blockPosKey(x, y, z));
    }

    public void removeChest(int x, int y, int z) {
        ChestData cd = chests.remove(blockPosKey(x, y, z));
        if (cd != null) {
            for (no.minecraft.player.ItemStack is : cd.getItems()) {
                if (!is.isEmpty()) {
                    spawnItemDrop(x + 0.5f, y + 0.5f, z + 0.5f, is.getType(), is.getCount());
                    is.clear();
                }
            }
        }
    }

    public Map<Long, ChestData> getChests() {
        return Collections.unmodifiableMap(chests);
    }

    public void setChests(List<ChestData> list) {
        chests.clear();
        if (list != null) {
            for (ChestData cd : list) {
                chests.put(blockPosKey(cd.getX(), cd.getY(), cd.getZ()), cd);
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
        if (currentDimension == Dimension.THE_END) return 0.70f;
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

    /**
     * True when the cell at (x, y, z) has no opaque block above it in its column.
     * O(1) via per-chunk column heightmaps; unlike isOpenToSky this never triggers
     * chunk generation, so it is safe to call from mesh builds. Non-overworld
     * dimensions are always "exposed" since their sun level is constant.
     */
    public boolean isSkyExposed(int x, int y, int z) {
        if (currentDimension != Dimension.OVERWORLD) return true;
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        Chunk c = getChunk(cx, cz);
        if (c == null) return true; // Unloaded neighbour: assume open (degrades to old behaviour)
        return c.getColumnHeight(x - c.getWorldStartX(), z - c.getWorldStartZ()) <= y;
    }

    /**
     * Sky light factor (0.0 to 1.0) with Java 1.16.1 horizontal and vertical propagation.
     * Fully illuminated under open sky (1.0), softly shaded under trees and overhangs (~0.8-0.95),
     * gradually falls off into cave entrances down to 0.0 in deep unlit caverns.
     */
    public float getSkyLight(int x, int y, int z) {
        if (currentDimension != Dimension.OVERWORLD) return 1.0f;
        if (y >= Chunk.SIZE_Y) return 1.0f;
        if (y < 0) return 0.0f;
        int cx = Math.floorDiv(x, Chunk.SIZE_X);
        int cz = Math.floorDiv(z, Chunk.SIZE_Z);
        Chunk c = getChunk(cx, cz);
        if (c == null) return isSkyExposed(x, y, z) ? 1.0f : 0.0f;
        int lx = (x % Chunk.SIZE_X + Chunk.SIZE_X) % Chunk.SIZE_X;
        int lz = (z % Chunk.SIZE_Z + Chunk.SIZE_Z) % Chunk.SIZE_Z;
        return c.getSkyLight(lx, y, lz) / 15.0f;
    }

    public boolean isDarkAt(int x, int y, int z) {
        if (currentDimension != Dimension.OVERWORLD) return true;
        // Torches prevent darkness in an area of radius 6
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= 25) {
                        BlockType b = getBlock(x + dx, y + dy, z + dz);
                        if (b == BlockType.TORCH || b == BlockType.LAVA) {
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

        // Update Ender Pearls
        for (int i = enderPearls.size() - 1; i >= 0; i--) {
            if (i >= enderPearls.size()) continue;
            no.minecraft.entity.EnderPearl pearl = enderPearls.get(i);
            pearl.update(dt, this, player);
            if (pearl.isDead() && i < enderPearls.size()) enderPearls.remove(i);
        }

        // Update Eyes of Ender
        for (int i = eyeOfEnders.size() - 1; i >= 0; i--) {
            if (i >= eyeOfEnders.size()) continue;
            no.minecraft.entity.EyeOfEnder eye = eyeOfEnders.get(i);
            eye.update(dt, this, player);
            if (eye.isDead() && i < eyeOfEnders.size()) eyeOfEnders.remove(i);
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
            // Mark generated before decorating so border decoration writes into this chunk are kept
            activeGenerated.add(key);
            if (!isChunkSaved(cx, cz) || !loadChunkFromSave(chunk, cx, cz)) {
                generateChunkTerrain(chunk);
                decorateChunk(cx, cz);
                repairNeighborDecorations(cx, cz);
            }
            // Generated/loaded terrain is deterministic or persisted; only player edits need saving
            chunk.clearNeedsSave();
            chunk.setDirty(true);
            markChunkDirty(cx - 1, cz);
            markChunkDirty(cx + 1, cz);
            markChunkDirty(cx, cz - 1);
            markChunkDirty(cx, cz + 1);
        }
        return chunk;
    }

    // Decorations near a newly generated chunk may have dropped border writes into it;
    // re-run neighbor decorations (deterministic, idempotent) to fill those in.
    private void repairNeighborDecorations(int cx, int cz) {
        if (currentDimension != Dimension.OVERWORLD) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (getActiveGenerated().contains(chunkKey(cx + dx, cz + dz))) {
                    decorateChunk(cx + dx, cz + dz);
                }
            }
        }
    }

    public void updateLoadedChunks(int centerCx, int centerCz) {
        this.lastCenterCx = centerCx;
        this.lastCenterCz = centerCz;
        int rd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        if (centerCx != lastUpdateCx || centerCz != lastUpdateCz || rd != lastUpdateRd) {
            lastUpdateCx = centerCx;
            lastUpdateCz = centerCz;
            lastUpdateRd = rd;

            for (int dx = -rd; dx <= rd; dx++) {
                for (int dz = -rd; dz <= rd; dz++) {
                    int cx = centerCx + dx;
                    int cz = centerCz + dz;
                    ensureChunkGenerated(cx, cz);
                }
            }
        }
        evictFarChunks(centerCx, centerCz, rd + 2);
    }

    private void evictFarChunks(int centerCx, int centerCz, int unloadDist) {
        // Active dimension: evict chunks past unload distance once their data is persisted
        Map<Long, Chunk> activeChunks = getActiveChunks();
        Set<Long> activeGenerated = getActiveGenerated();
        Iterator<Map.Entry<Long, Chunk>> iterator = activeChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            Chunk chunk = entry.getValue();
            int dist = Math.max(Math.abs(chunk.getChunkX() - centerCx), Math.abs(chunk.getChunkZ() - centerCz));
            if (dist > unloadDist && !chunk.needsSave()) {
                chunk.unloadMesh();
                iterator.remove();
                activeGenerated.remove(entry.getKey());
            }
        }

        // Inactive dimensions: keep only dirty chunks; the rest is reloaded from save on return
        for (Map.Entry<Dimension, Map<Long, Chunk>> dimEntry : dimensionChunks.entrySet()) {
            if (dimEntry.getKey() == currentDimension) continue;
            Set<Long> genSet = dimensionGenerated.get(dimEntry.getKey());
            Iterator<Map.Entry<Long, Chunk>> it = dimEntry.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Chunk> entry = it.next();
                Chunk chunk = entry.getValue();
                if (!chunk.needsSave()) {
                    chunk.unloadMesh();
                    it.remove();
                    genSet.remove(entry.getKey());
                }
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
                } else if (biome.equals("minecraft:badlands")) {
                    // Striated colored terracotta strata capped with red sand
                    for (int y = Math.max(1, height - 8); y < height; y++) {
                        chunk.setBlock(lx, y, lz, getMesaBandBlock(wx, y, wz));
                    }
                    boolean topSand = ((wx * 7 + wz * 13) % 4 != 0);
                    chunk.setBlock(lx, height, lz, topSand ? BlockType.RED_SAND : getMesaBandBlock(wx, height, wz));
                } else if (biome.equals("minecraft:snowy_plains") || biome.equals("minecraft:snowy_tundra") || biome.equals("minecraft:snowy_mountains")) {
                    for (int y = Math.max(1, height - 3); y < height; y++) {
                        chunk.setBlock(lx, y, lz, BlockType.DIRT);
                    }
                    if (height > 0 && height < Chunk.SIZE_Y) {
                        chunk.setBlock(lx, height, lz, BlockType.SNOW_BLOCK);
                    }
                } else if (biome.equals("minecraft:mountains")) {
                    if (height >= 30) {
                        // Rocky cliffs and high peaks: Stone, with naturally occurring ores and gravel pockets
                        for (int y = Math.max(1, height - 3); y <= height; y++) {
                            chunk.setBlock(lx, y, lz, getUndergroundBlock(wx, y, wz));
                        }
                    } else {
                        // Lower mountain base: Dirt and grass
                        for (int y = Math.max(1, height - 3); y < height; y++) {
                            chunk.setBlock(lx, y, lz, BlockType.DIRT);
                        }
                        chunk.setBlock(lx, height, lz, BlockType.GRASS);
                    }
                } else {
                    // Plains, Forest, Savanna, Tall Birch Forest, Dark Forest
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

    boolean isOverworldCave(int wx, int y, int wz, int height) {
        if (y <= 1 || y >= height - 3) return false;
        if (height <= SEA_LEVEL && y >= height - 6) return false;

        // Cave density varies by region: some areas are riddled with caves, others barely any
        double density = Noise.fbm2D(seed ^ 0x43415644L, wx * 0.0070, wz * 0.0070, 2, 0.5) * 0.5 + 0.5;
        double threshold = 0.045 + 0.030 * density;

        // Two continuous worm tunnels winding through 3D
        double n1 = Noise.value2D(seed ^ 0x43415645L, wx * 0.07 + y * 0.035, wz * 0.07 - y * 0.035);
        double n2 = Noise.value2D(seed ^ 0x43415646L, wx * 0.07 - y * 0.030, wz * 0.07 + y * 0.030);
        if (n1 * n1 + n2 * n2 < threshold) return true;

        // Large open caverns at mid-depth in some regions
        double cav = Noise.fbm2D(seed ^ 0x43415647L, wx * 0.020, wz * 0.020 + y * 0.02, 2, 0.5);
        return y > 6 && y < 34 && cav > 0.62;
    }

    private BlockType getUndergroundBlock(int wx, int y, int wz) {
        int cx2 = Math.floorDiv(wx, 2);
        int cy2 = Math.floorDiv(y, 2);
        int cz2 = Math.floorDiv(wz, 2);

        long clusterHash = ((long) cx2 * 3129871L) ^ ((long) cz2 * 116129781L) ^ ((long) cy2 * 8429183L) ^ seed;
        clusterHash = (clusterHash ^ (clusterHash >> 16)) * 0x45d9f3bL;
        clusterHash = clusterHash ^ (clusterHash >> 16);
        int clusterType = (int) Math.abs(clusterHash % 10000);

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

        // Iron Ore: Underground and throughout mountains, frequent
        if (clusterType >= 40 && clusterType <= 85 && blockVar < 8) {
            return BlockType.IRON_ORE;
        }

        // Coal Ore: Abundant underground and throughout mountains
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

        if (biome.equals("minecraft:desert") || biome.equals("minecraft:badlands")) {
            // Cacti: 1-3 per chunk on sand/red sand, clumped by density noise
            int numCacti = 1 + decRand.nextInt(3);
            int placed = 0;
            for (int attempt = 0; attempt < numCacti * 4 && placed < numCacti; attempt++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                if (!treeDensityAt(wx, wz)) continue;
                int groundY = getTerrainHeight(wx, wz);
                BlockType b = getBlock(wx, groundY, wz);
                if (groundY > SEA_LEVEL && (b == BlockType.SAND || b == BlockType.RED_SAND || b == BlockType.TERRACOTTA)) {
                    spawnCactus(wx, groundY + 1, wz, decRand);
                    placed++;
                }
            }
            return;
        }

        if (biome.equals("minecraft:snowy_plains") || biome.equals("minecraft:snowy_tundra")) {
            // Pine/Spruce trees: 1-3 per chunk on snow, clumped by density noise
            int numTrees = 1 + decRand.nextInt(3);
            int placed = 0;
            for (int attempt = 0; attempt < numTrees * 4 && placed < numTrees; attempt++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                if (!treeDensityAt(wx, wz)) continue;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.SNOW_BLOCK) {
                    spawnPineTree(wx, groundY + 1, wz, decRand);
                    placed++;
                }
            }
            return;
        }

        if (biome.equals("minecraft:tall_birch_forest") || biome.equals("minecraft:old_growth_birch_forest")) {
            // Dense tall birch trees: 4-7 per chunk (8-14 blocks high)
            int numTrees = 4 + decRand.nextInt(4);
            int placed = 0;
            for (int attempt = 0; attempt < numTrees * 4 && placed < numTrees; attempt++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                if (!treeDensityAt(wx, wz)) continue;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                    spawnTallBirchTree(wx, groundY + 1, wz, decRand);
                    placed++;
                }
            }
            return;
        }

        if (biome.equals("minecraft:savanna")) {
            // Savanna acacia trees: 1-3 per chunk with angled branching
            int numTrees = 1 + decRand.nextInt(3);
            int placed = 0;
            for (int attempt = 0; attempt < numTrees * 4 && placed < numTrees; attempt++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                if (!treeDensityAt(wx, wz)) continue;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                    spawnAcaciaTree(wx, groundY + 1, wz, decRand);
                    placed++;
                }
            }
            return;
        }

        if (biome.equals("minecraft:dark_forest")) {
            // Dense dark oak forest: 5-8 thick dark oak trees
            int numTrees = 5 + decRand.nextInt(4);
            int placed = 0;
            for (int attempt = 0; attempt < numTrees * 4 && placed < numTrees; attempt++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                if (!treeDensityAt(wx, wz)) continue;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                    spawnDarkOakTree(wx, groundY + 1, wz, decRand);
                    placed++;
                }
            }
            return;
        }

        if (biome.equals("minecraft:forest")) {
            // Dense forest: 4-7 oak trees per chunk, growing in groves with clearings
            int numTrees = 4 + decRand.nextInt(4);
            int placed = 0;
            for (int attempt = 0; attempt < numTrees * 4 && placed < numTrees; attempt++) {
                int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
                int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
                int wx = startX + lx;
                int wz = startZ + lz;
                if (!treeDensityAt(wx, wz)) continue;
                int groundY = getTerrainHeight(wx, wz);
                if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                    spawnTree(wx, groundY + 1, wz, decRand);
                    placed++;
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

        // Plains: 0-2 trees per chunk, clumped by density noise
        int numTrees = decRand.nextInt(3);
        int placed = 0;
        for (int attempt = 0; attempt < numTrees * 4 && placed < numTrees; attempt++) {
            int lx = 2 + decRand.nextInt(Chunk.SIZE_X - 4);
            int lz = 2 + decRand.nextInt(Chunk.SIZE_Z - 4);
            int wx = startX + lx;
            int wz = startZ + lz;
            if (!treeDensityAt(wx, wz)) continue;

            int groundY = getTerrainHeight(wx, wz);
            if (groundY > SEA_LEVEL && getBlock(wx, groundY, wz) == BlockType.GRASS) {
                spawnTree(wx, groundY + 1, wz, decRand);
                placed++;
            }
        }
    }

    /** Noise-driven tree density: forests clump into groves instead of uniform scatter. */
    private boolean treeDensityAt(int wx, int wz) {
        return Noise.fbm2D(seed ^ 0x4445434FL, wx * 0.040, wz * 0.040, 2, 0.5) > -0.15;
    }

    public BlockType getMesaBandBlock(int x, int y, int z) {
        int pattern = (y + (int)(Noise.fbm2D(seed ^ 0x42414E44L, x * 0.03, z * 0.03, 2, 0.5) * 3)) % 14;
        if (pattern < 0) pattern += 14;
        return switch (pattern) {
            case 0, 1 -> BlockType.TERRACOTTA;
            case 2, 3 -> BlockType.ORANGE_TERRACOTTA;
            case 4 -> BlockType.YELLOW_TERRACOTTA;
            case 5, 6 -> BlockType.BROWN_TERRACOTTA;
            case 7 -> BlockType.WHITE_TERRACOTTA;
            case 8, 9 -> BlockType.RED_TERRACOTTA;
            case 10 -> BlockType.ORANGE_TERRACOTTA;
            case 11 -> BlockType.WHITE_TERRACOTTA;
            default -> BlockType.TERRACOTTA;
        };
    }

    private void spawnCactus(int rootX, int rootY, int rootZ, Random rand) {
        int height = 1 + rand.nextInt(3);
        for (int dy = 0; dy < height; dy++) {
            if (getBlock(rootX, rootY + dy, rootZ) == BlockType.AIR) {
                setDecorationBlock(rootX, rootY + dy, rootZ, BlockType.CACTUS);
            }
        }
    }

    private void spawnTallBirchTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 8 + rand.nextInt(7); // 8 to 14 blocks high
        for (int dy = 0; dy < trunkHeight; dy++) {
            setDecorationBlock(rootX, rootY + dy, rootZ, BlockType.BIRCH_LOG);
        }
        int topY = rootY + trunkHeight;
        for (int dy = -3; dy <= 1; dy++) {
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
                        setDecorationBlock(tx, ty, tz, BlockType.BIRCH_LEAVES);
                    }
                }
            }
        }
    }

    private void spawnAcaciaTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 4 + rand.nextInt(3);
        for (int dy = 0; dy < trunkHeight; dy++) {
            setDecorationBlock(rootX, rootY + dy, rootZ, BlockType.ACACIA_LOG);
        }
        int branchY = rootY + trunkHeight;
        int dirX = rand.nextBoolean() ? 1 : -1;
        int dirZ = rand.nextBoolean() ? 1 : -1;

        // Diagonal branch 1
        setDecorationBlock(rootX + dirX, branchY, rootZ + dirZ, BlockType.ACACIA_LOG);
        setDecorationBlock(rootX + dirX * 2, branchY + 1, rootZ + dirZ * 2, BlockType.ACACIA_LOG);

        // Flat umbrella canopy 1
        int tipX = rootX + dirX * 2;
        int tipY = branchY + 1;
        int tipZ = rootZ + dirZ * 2;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rand.nextBoolean()) continue;
                if (getBlock(tipX + dx, tipY, tipZ + dz) == BlockType.AIR) {
                    setDecorationBlock(tipX + dx, tipY, tipZ + dz, BlockType.ACACIA_LEAVES);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (getBlock(tipX + dx, tipY + 1, tipZ + dz) == BlockType.AIR) {
                    setDecorationBlock(tipX + dx, tipY + 1, tipZ + dz, BlockType.ACACIA_LEAVES);
                }
            }
        }
    }

    private void spawnDarkOakTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 5 + rand.nextInt(3);
        for (int dy = 0; dy < trunkHeight; dy++) {
            setDecorationBlock(rootX, rootY + dy, rootZ, BlockType.DARK_OAK_LOG);
        }
        int topY = rootY + trunkHeight;
        // Wide flat canopy
        for (int dy = -2; dy <= 1; dy++) {
            int radius = (dy == 1) ? 2 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && rand.nextBoolean()) continue;
                    int tx = rootX + dx;
                    int ty = topY + dy;
                    int tz = rootZ + dz;
                    if (getBlock(tx, ty, tz) == BlockType.AIR) {
                        setDecorationBlock(tx, ty, tz, BlockType.DARK_OAK_LEAVES);
                    }
                }
            }
        }
    }

    private void spawnPineTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 6 + rand.nextInt(3);
        for (int dy = 0; dy < trunkHeight; dy++) {
            setDecorationBlock(rootX, rootY + dy, rootZ, BlockType.WOOD);
        }
        int topY = rootY + trunkHeight;
        // Tip
        setDecorationBlock(rootX, topY, rootZ, BlockType.LEAVES);

        // Layer 1 (radius 1 cross)
        setDecorationBlock(rootX + 1, topY - 1, rootZ, BlockType.LEAVES);
        setDecorationBlock(rootX - 1, topY - 1, rootZ, BlockType.LEAVES);
        setDecorationBlock(rootX, topY - 1, rootZ + 1, BlockType.LEAVES);
        setDecorationBlock(rootX, topY - 1, rootZ - 1, BlockType.LEAVES);

        // Layer 2 (3x3 square)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (getBlock(rootX + dx, topY - 2, rootZ + dz) == BlockType.AIR) {
                    setDecorationBlock(rootX + dx, topY - 2, rootZ + dz, BlockType.LEAVES);
                }
            }
        }

        // Layer 3 (radius 2 cross)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (getBlock(rootX + dx, topY - 3, rootZ + dz) == BlockType.AIR) {
                    setDecorationBlock(rootX + dx, topY - 3, rootZ + dz, BlockType.LEAVES);
                }
            }
        }

        // Layer 4 (5x5 square with cut corners)
        if (trunkHeight >= 7) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rand.nextBoolean()) continue;
                    if (getBlock(rootX + dx, topY - 4, rootZ + dz) == BlockType.AIR) {
                        setDecorationBlock(rootX + dx, topY - 4, rootZ + dz, BlockType.LEAVES);
                    }
                }
            }
        }
    }

    private void spawnTree(int rootX, int rootY, int rootZ, Random rand) {
        int trunkHeight = 4 + rand.nextInt(2);
        for (int dy = 0; dy < trunkHeight; dy++) {
            setDecorationBlock(rootX, rootY + dy, rootZ, BlockType.WOOD);
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
                        setDecorationBlock(tx, ty, tz, BlockType.LEAVES);
                    }
                }
            }
        }
    }

    int getTerrainHeight(int x, int z) {
        // Seeded value-noise fields: each seed produces a completely different landscape
        double cont = Noise.fbm2D(seed ^ 0x434F4E54L, x * 0.0020, z * 0.0020, 4, 0.5);
        double ero  = Noise.fbm2D(seed ^ 0x45524F53L, x * 0.0040, z * 0.0040, 3, 0.5);
        double rid  = Noise.ridged2D(seed ^ 0x52494447L, x * 0.0060, z * 0.0060, 4);
        double det  = Noise.fbm2D(seed ^ 0x44455441L, x * 0.040, z * 0.040, 3, 0.5);
        double temp = temperatureAt(x, z);

        // Continentalness -> smooth blend from ocean floor to inland plateau
        double land = Noise.smoothstep(-0.25, 0.10, cont);
        double oceanFloor = 6.0 + 4.0 * (cont + 1.0);
        double inland = 21.0 + 6.0 * cont;
        double base = oceanFloor + (inland - oceanFloor) * land;

        // Ridged mountain ranges fading into hills (smooth mask instead of a hard cutoff)
        double mask = Noise.smoothstep(0.30, 0.70, rid) * (1.0 - 0.35 * ero);
        double peak = 24.0 + 30.0 * Noise.smoothstep(0.30, 0.90, rid) - 6.0 * ero;
        base += (Math.max(base, peak) - base) * mask;

        // Rivers and lakes carved below sea level (the ocean branch fills them with water)
        double river = Math.abs(Noise.fbm2D(seed ^ 0x52495652L, x * 0.0020, z * 0.0020, 2, 0.5));
        double riverF = 1.0 - Noise.smoothstep(0.0, 0.055, river);
        if (riverF > 0.0 && base > SEA_LEVEL - 2.0) {
            base += ((SEA_LEVEL - 2.5) - base) * riverF * 0.9;
        }

        // Fine detail: deserts flatten out, forests and plains keep rolling hills
        double detailAmp = (temp > 0.30) ? 0.45 : 1.0;
        base += (det * 3.0 + Noise.fbm2D(seed ^ 0x44455442L, x * 0.12, z * 0.12, 2, 0.5) * 1.2) * detailAmp;

        int height = (int) Math.round(base);
        return (int) Math.clamp(height, 5, Chunk.SIZE_Y - 7);
    }

    private double temperatureAt(int x, int z) {
        return Noise.fbm2D(seed ^ 0x54454D50L, x * 0.0018, z * 0.0018, 3, 0.5);
    }

    private double humidityAt(int x, int z) {
        return Noise.fbm2D(seed ^ 0x48554D49L, x * 0.0020, z * 0.0020, 3, 0.5);
    }

    private double mesaNoiseAt(int x, int z) {
        return Noise.fbm2D(seed ^ 0x4D455341L, x * 0.0025, z * 0.0025, 2, 0.5);
    }

    public void updateAndRender(int centerCx, int centerCz, int renderDistance) {
        updateAndRender(centerCx, centerCz, renderDistance, null);
    }

    public void updateAndRender(int centerCx, int centerCz, int renderDistance, no.minecraft.math.Frustum frustum) {
        this.lastCenterCx = centerCx;
        this.lastCenterCz = centerCz;
        Map<Long, Chunk> activeChunks = getActiveChunks();
        renderedChunkCount = 0;

        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                Chunk chunk = activeChunks.get(chunkKey(cx, cz));
                if (chunk != null) {
                    chunk.updateMeshIfNeeded();
                    if (frustum == null || frustum.intersectsAabb(
                            chunk.getWorldStartX(), 0, chunk.getWorldStartZ(),
                            chunk.getWorldStartX() + Chunk.SIZE_X, Chunk.SIZE_Y, chunk.getWorldStartZ() + Chunk.SIZE_Z)) {
                        chunk.render();
                        renderedChunkCount++;
                    }
                }
            }
        }
    }

    public void updateAndRender() {
        int rd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        updateAndRender(lastCenterCx, lastCenterCz, rd);
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
            if (h >= 44) {
                return "minecraft:mountains";
            }
            double temp = temperatureAt(x, z);
            double hum  = humidityAt(x, z);

            // Rare polar snowy zone (< 5% frequency)
            if (temp < -0.70) {
                return (h >= 34) ? "minecraft:snowy_mountains" : "minecraft:snowy_tundra";
            }

            // Hot / Arid zones
            if (temp > 0.35) {
                if (hum < -0.10) {
                    double mesa = mesaNoiseAt(x, z);
                    return (mesa > 0.0) ? "minecraft:badlands" : "minecraft:desert";
                }
                return "minecraft:savanna";
            }

            // Warm Savanna zone
            if (temp > 0.15 && hum < 0.10) {
                return "minecraft:savanna";
            }

            // Lush / Wet temperate zones
            if (hum > 0.25) {
                if (hum > 0.45 && temp < 0.15) {
                    return "minecraft:dark_forest";
                }
                return "minecraft:tall_birch_forest";
            }

            // Normal temperate zones
            if (hum > -0.05) {
                return "minecraft:forest";
            } else {
                return "minecraft:plains";
            }
        }
    }

    public void cleanup() {
        mobs.clear();
        droppedItems.clear();
        arrows.clear();
        enderPearls.clear();
        eyeOfEnders.clear();
        fallingBlocks.clear();
        pendingFallingBlocks.clear();
        furnaces.clear();
        chests.clear();
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
        savedChunkKeys.clear();
        this.lastUpdateCx = Integer.MIN_VALUE;
        this.lastUpdateCz = Integer.MIN_VALUE;
        this.lastUpdateRd = -1;
    }

    public void setSaveDirectory(Path saveDir) {
        this.saveDir = saveDir;
    }

    public void markChunkSaved(Dimension dim, int cx, int cz) {
        savedChunkKeys.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet()).add(chunkKey(cx, cz));
    }

    public void markAllChunksSaved() {
        for (Map.Entry<Dimension, Map<Long, Chunk>> dimEntry : dimensionChunks.entrySet()) {
            Set<Long> genSet = dimensionGenerated.get(dimEntry.getKey());
            Set<Long> keys = savedChunkKeys.computeIfAbsent(dimEntry.getKey(), k -> ConcurrentHashMap.newKeySet());
            for (Map.Entry<Long, Chunk> entry : dimEntry.getValue().entrySet()) {
                entry.getValue().clearNeedsSave();
                // Only fully generated chunks count as persisted; phantom chunks must be regenerated
                if (genSet != null && genSet.contains(entry.getKey())) {
                    keys.add(entry.getKey());
                }
            }
        }
    }

    private boolean isChunkSaved(int cx, int cz) {
        Set<Long> keys = savedChunkKeys.get(currentDimension);
        return keys != null && keys.contains(chunkKey(cx, cz));
    }

    private boolean loadChunkFromSave(Chunk chunk, int cx, int cz) {
        if (saveDir == null) return false;
        return no.minecraft.world.save.WorldSaveManager.loadChunkInto(chunk, saveDir, currentDimension);
    }
}
