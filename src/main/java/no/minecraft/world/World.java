package no.minecraft.world;

import no.minecraft.player.Player;
import java.util.*;

public class World {
    public static final int RENDER_DISTANCE = 5; // Radius of chunks around player (11x11 = 121 chunks)
    public static final int UNLOAD_DISTANCE = RENDER_DISTANCE + 2;
    public static final int SEA_LEVEL = 18;

    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final Set<Long> generatedChunks = new HashSet<>();
    private long seed;
    private double offsetX;
    private double offsetZ;

    public World() {
        this(new Random().nextLong());
    }

    public World(long seed) {
        setSeed(seed);
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
        Random r = new Random(seed);
        // Distribute terrain coordinate offsets randomly within [-100000, 100000]
        this.offsetX = (r.nextDouble() - 0.5) * 200000.0;
        this.offsetZ = (r.nextDouble() - 0.5) * 200000.0;
        cleanup();
        updateLoadedChunks(0, 0);
    }

    public static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(chunkKey(cx, cz));
    }

    public Chunk getOrCreateChunk(int cx, int cz) {
        return chunks.computeIfAbsent(chunkKey(cx, cz), k -> new Chunk(this, cx, cz));
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

    private final List<DroppedItem> droppedItems = new ArrayList<>();
    private final List<no.minecraft.entity.Mob> mobs = new ArrayList<>();
    private final List<no.minecraft.entity.Arrow> arrows = new ArrayList<>();
    private float mobSpawnTimer = 0.0f;
    private final Random rand = new Random();

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

    public List<DroppedItem> getDroppedItems() {
        return droppedItems;
    }

    public List<no.minecraft.entity.Mob> getMobs() {
        return mobs;
    }

    public List<no.minecraft.entity.Arrow> getArrows() {
        return arrows;
    }

    public static final float DAY_LENGTH_SECONDS = 240.0f;
    private float worldTime = 20.0f; // Start in daytime

    public float getWorldTime() {
        return worldTime;
    }

    public float getDayFraction() {
        return (worldTime % DAY_LENGTH_SECONDS) / DAY_LENGTH_SECONDS;
    }

    public boolean isNight() {
        float f = getDayFraction();
        return f >= 0.50f && f <= 0.92f;
    }

    public float getSunLightLevel() {
        float f = getDayFraction();
        // Sine curve for smooth daylight transition
        double angle = f * 2.0 * Math.PI;
        double sunHeight = Math.sin(angle);
        if (sunHeight > 0.15) {
            return 1.0f;
        } else if (sunHeight < -0.15) {
            return 0.18f; // Deep night brightness
        } else {
            return (float) (0.18 + (sunHeight + 0.15) / 0.30 * 0.82);
        }
    }

    public boolean isDarkAt(int x, int y, int z) {
        boolean openToSky = true;
        for (int checkY = y + 1; checkY < Chunk.SIZE_Y; checkY++) {
            BlockType b = getBlock(x, checkY, z);
            if (b != BlockType.AIR && !b.isTransparent()) {
                openToSky = false;
                break;
            }
        }

        if (openToSky) {
            return isNight();
        } else {
            return true;
        }
    }

    public void update(float dt, Player player) {
        worldTime += dt;
        int centerCx = Math.floorDiv((int) Math.floor(player.getPosition().x), Chunk.SIZE_X);
        int centerCz = Math.floorDiv((int) Math.floor(player.getPosition().z), Chunk.SIZE_Z);
        updateLoadedChunks(centerCx, centerCz);

        // Update all dropped items physics and player pickup
        for (int i = droppedItems.size() - 1; i >= 0; i--) {
            DroppedItem item = droppedItems.get(i);
            item.update(dt, this, player);
            if (item.isDead()) {
                droppedItems.remove(i);
            }
        }

        // Periodic Mob Spawning around player (mobs spawn only at night or in dark areas)
        mobSpawnTimer += dt;
        if (mobSpawnTimer > 3.0f) {
        mobSpawnTimer = 0.0f;
        if (mobs.size() < 18) {
            float angle = rand.nextFloat() * (float) (2 * Math.PI);
            float dist = 14.0f + rand.nextFloat() * 18.0f;
            float mx = player.getPosition().x + (float) Math.cos(angle) * dist;
            float mz = player.getPosition().z + (float) Math.sin(angle) * dist;
            int bx = (int) Math.floor(mx);
            int bz = (int) Math.floor(mz);
            int groundY = getSpawnHeight(bx, bz);

            if (groundY > 1 && groundY < 50) {
                // Only spawn if it's dark at the spawn location (either night or dark/covered/cave)
                if (isDarkAt(bx, groundY, bz)) {
                    no.minecraft.entity.MobType[] types = no.minecraft.entity.MobType.values();
                    no.minecraft.entity.MobType chosen = types[rand.nextInt(types.length)];
                    spawnMob(chosen, mx, groundY + 0.05f, mz);
                }
            }
        }
    }

        // Update Mobs
        for (int i = mobs.size() - 1; i >= 0; i--) {
            no.minecraft.entity.Mob mob = mobs.get(i);
            mob.update(dt, this, player);
            if (mob.isDead() || mob.getPosition().distance(player.getPosition()) > 64.0f) {
                mobs.remove(i);
            }
        }

        // Update Arrows
        for (int i = arrows.size() - 1; i >= 0; i--) {
            no.minecraft.entity.Arrow arrow = arrows.get(i);
            arrow.update(dt, this, player);
            if (arrow.isDead()) {
                arrows.remove(i);
            }
        }
    }

    private void updateLoadedChunks(int centerCx, int centerCz) {
        int rd = no.minecraft.settings.GameSettings.getInstance().getRenderDistance();
        int unloadDist = rd + 2;

        // 1. Generate / load chunks within render distance
        List<Chunk> newlyGenerated = new ArrayList<>();

        for (int dx = -rd; dx <= rd; dx++) {
            for (int dz = -rd; dz <= rd; dz++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                long key = chunkKey(cx, cz);

                if (!generatedChunks.contains(key)) {
                    Chunk chunk = getOrCreateChunk(cx, cz);
                    generateChunkTerrain(chunk);
                    decorateChunk(cx, cz);
                    generatedChunks.add(key);
                    newlyGenerated.add(chunk);

                    // Notify adjacent existing chunks that a neighbor appeared
                    markChunkDirty(cx - 1, cz);
                    markChunkDirty(cx + 1, cz);
                    markChunkDirty(cx, cz - 1);
                    markChunkDirty(cx, cz + 1);
                }
            }
        }

        // Rebuild mesh for newly generated chunks
        for (Chunk chunk : newlyGenerated) {
            chunk.setDirty(true);
        }

        // 2. Unload chunks that are too far away
        Iterator<Map.Entry<Long, Chunk>> iterator = chunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            Chunk chunk = entry.getValue();
            int dist = Math.max(Math.abs(chunk.getChunkX() - centerCx), Math.abs(chunk.getChunkZ() - centerCz));
            if (dist > unloadDist) {
                chunk.cleanup();
                generatedChunks.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void generateChunkTerrain(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        int startX = cx * Chunk.SIZE_X;
        int startZ = cz * Chunk.SIZE_Z;

        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int wx = startX + lx;
                int wz = startZ + lz;

                int height = getTerrainHeight(wx, wz);

                // Bedrock at y=0
                chunk.setBlock(lx, 0, lz, BlockType.BEDROCK);

                // Stone layer
                for (int y = 1; y < height - 3; y++) {
                    chunk.setBlock(lx, y, lz, BlockType.STONE);
                }

                // Dirt / Sand layer
                boolean isSand = (height <= SEA_LEVEL + 1);
                for (int y = Math.max(1, height - 3); y < height; y++) {
                    chunk.setBlock(lx, y, lz, isSand ? BlockType.SAND : BlockType.DIRT);
                }

                // Surface block
                if (height > 0 && height < Chunk.SIZE_Y) {
                    if (isSand) {
                        chunk.setBlock(lx, height, lz, BlockType.SAND);
                    } else {
                        chunk.setBlock(lx, height, lz, BlockType.GRASS);
                    }
                }
            }
        }
    }

    private void decorateChunk(int cx, int cz) {
        // Deterministic PRNG seed for this chunk using world seed
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

        // Trunk
        for (int dy = 0; dy < trunkHeight; dy++) {
            setBlock(rootX, rootY + dy, rootZ, BlockType.WOOD);
        }

        // Leaves canopy
        int topY = rootY + trunkHeight;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = (dy == 1) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && (dy == 1 || rand.nextBoolean())) {
                        continue; // Round edges
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
        // Multi-frequency noise shifted by seed-based world coordinate offsets
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
        for (Chunk chunk : chunks.values()) {
            chunk.updateMeshIfNeeded();
            chunk.render();
        }
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    public void cleanup() {
        for (Chunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        generatedChunks.clear();
    }
}
