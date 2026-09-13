package no.minecraft.world;

import java.util.Arrays;
import java.util.List;

public class Chunk {
    public static final int SIZE_X = 16;
    public static final int SIZE_Z = 16;
    public static final int SIZE_Y = 64;

    private final int chunkX;
    private final int chunkZ;
    private final World world;
    private final byte[] blocks = new byte[SIZE_X * SIZE_Y * SIZE_Z];

    /** 3D Sky light array (0..15) per block. Lazily rebuilt after block changes. */
    private final byte[] skyLight = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    private boolean lightValid = false;
    private final int[] lightQueue = new int[32768];

    private boolean isDirty = true;
    private boolean needsSave = false;

    public Chunk(World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getWorldStartX() {
        return chunkX * SIZE_X;
    }

    public int getWorldStartZ() {
        return chunkZ * SIZE_Z;
    }

    private int getIndex(int x, int y, int z) {
        return (y * SIZE_Z + z) * SIZE_X + x;
    }

    public BlockType getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) {
            return world.getBlock(getWorldStartX() + x, y, getWorldStartZ() + z);
        }
        return BlockType.getById(blocks[getIndex(x, y, z)]);
    }

    public void setBlock(int x, int y, int z, BlockType type) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            BlockType oldType = getBlock(x, y, z);
            blocks[getIndex(x, y, z)] = type.getId();
            setDirty(true);
            needsSave = true;
            heightsValid = false;
            lightValid = false;
            // When placing or breaking a light source (torch or lava), dirty neighbor chunks so lighting updates across borders
            if (type == BlockType.TORCH || oldType == BlockType.TORCH ||
                type == BlockType.LAVA || oldType == BlockType.LAVA) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            world.markChunkDirty(chunkX + dx, chunkZ + dz);
                        }
                    }
                }
            } else {
                // Mark neighboring chunks dirty if on border
                if (x == 0) world.markChunkDirty(chunkX - 1, chunkZ);
                if (x == SIZE_X - 1) world.markChunkDirty(chunkX + 1, chunkZ);
                if (z == 0) world.markChunkDirty(chunkX, chunkZ - 1);
                if (z == SIZE_Z - 1) world.markChunkDirty(chunkX, chunkZ + 1);
            }
        }
    }

    public void invalidateLight() {
        this.lightValid = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    public boolean needsSave() {
        return needsSave;
    }

    public void clearNeedsSave() {
        needsSave = false;
    }

    public void collectTorches(List<int[]> out) {
        int startX = getWorldStartX();
        int startZ = getWorldStartZ();
        byte torchId = BlockType.TORCH.getId();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    if (blocks[getIndex(x, y, z)] == torchId) {
                        out.add(new int[]{startX + x, y, startZ + z});
                    }
                }
            }
        }
    }

    public void collectLava(List<int[]> out) {
        int startX = getWorldStartX();
        int startZ = getWorldStartZ();
        byte lavaId = BlockType.LAVA.getId();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    if (blocks[getIndex(x, y, z)] == lavaId) {
                        out.add(new int[]{startX + x, y, startZ + z, 15});
                    }
                }
            }
        }
    }

    public void collectLightSources(List<int[]> out) {
        collectTorches(out);
        collectLava(out);
    }

    /** Highest opaque block y per column, or -1 for all-air columns. Lazily rebuilt after setBlock. */
    private final byte[] columnHeight = new byte[SIZE_X * SIZE_Z];
    private boolean heightsValid = false;

    public int getColumnHeight(int x, int z) {
        if (!heightsValid) rebuildColumnHeights();
        return columnHeight[z * SIZE_X + x];
    }

    private void rebuildColumnHeights() {
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                int h = -1;
                for (int y = SIZE_Y - 1; y >= 0; y--) {
                    BlockType b = getBlock(x, y, z);
                    if (b != BlockType.AIR && !b.isTransparent()) {
                        h = y;
                        break;
                    }
                }
                columnHeight[z * SIZE_X + x] = (byte) h;
            }
        }
        heightsValid = true;
    }

    private static final int[][] LIGHT_DIRS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    public int getSkyLight(int x, int y, int z) {
        if (y >= SIZE_Y) return 15;
        if (y < 0) return 0;
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z) {
            int wx = getWorldStartX() + x;
            int wz = getWorldStartZ() + z;
            int cx = Math.floorDiv(wx, SIZE_X);
            int cz = Math.floorDiv(wz, SIZE_Z);
            Chunk c = world.getChunk(cx, cz);
            if (c == null) {
                return world.isSkyExposed(wx, y, wz) ? 15 : 0;
            }
            int lx = (wx % SIZE_X + SIZE_X) % SIZE_X;
            int lz = (wz % SIZE_Z + SIZE_Z) % SIZE_Z;
            return c.getSkyLight(lx, y, lz);
        }
        if (!lightValid) {
            rebuildSkyLight();
        }
        return skyLight[getIndex(x, y, z)] & 0xFF;
    }

    private void rebuildSkyLight() {
        lightValid = true;
        if (world.getCurrentDimension() != Dimension.OVERWORLD) {
            Arrays.fill(skyLight, (byte) 15);
            return;
        }

        Arrays.fill(skyLight, (byte) 0);
        int head = 0;
        int tail = 0;

        // 1. Direct vertical sunlight downwards through air and transparent blocks
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                int light = 15;
                for (int y = SIZE_Y - 1; y >= 0; y--) {
                    BlockType b = getBlock(x, y, z);
                    if (b != null && b != BlockType.AIR && !b.isTransparent()) {
                        // Opaque block stops vertical sunlight
                        break;
                    }
                    if (b == BlockType.LEAVES || b == BlockType.BIRCH_LEAVES ||
                        b == BlockType.ACACIA_LEAVES || b == BlockType.DARK_OAK_LEAVES) {
                        light = Math.max(0, light - 1);
                    }
                    int idx = getIndex(x, y, z);
                    skyLight[idx] = (byte) light;
                    if (light > 1) {
                        lightQueue[tail] = (y << 8) | (z << 4) | x;
                        tail = (tail + 1) & 0x7FFF;
                    }
                    if (light == 0) break;
                }
            }
        }

        // 2. Daylight injection from neighboring chunks across borders
        int startX = getWorldStartX();
        int startZ = getWorldStartZ();
        for (int i = 0; i < 16; i++) {
            tail = injectBorderLight(0, i, startX - 1, startZ + i, tail);
            tail = injectBorderLight(15, i, startX + 16, startZ + i, tail);
            tail = injectBorderLight(i, 0, startX + i, startZ - 1, tail);
            tail = injectBorderLight(i, 15, startX + i, startZ + 16, tail);
        }

        // 3. 3D BFS propagation of sky light (Minecraft Java 1.16.1 style flood-fill)
        while (head != tail) {
            int packed = lightQueue[head];
            head = (head + 1) & 0x7FFF;
            int x = packed & 0xF;
            int z = (packed >> 4) & 0xF;
            int y = (packed >> 8);
            int curLight = skyLight[getIndex(x, y, z)] & 0xFF;
            if (curLight <= 1) continue;

            for (int[] dir : LIGHT_DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];
                if (nx < 0 || nx >= SIZE_X || ny < 0 || ny >= SIZE_Y || nz < 0 || nz >= SIZE_Z) {
                    continue;
                }
                BlockType b = getBlock(nx, ny, nz);
                if (b != null && b != BlockType.AIR && !b.isTransparent()) {
                    continue; // Solid opaque block blocks light
                }
                int drop = (b == BlockType.LEAVES || b == BlockType.BIRCH_LEAVES ||
                            b == BlockType.ACACIA_LEAVES || b == BlockType.DARK_OAK_LEAVES) ? 2 : 1;
                int nextLight = Math.max(0, curLight - drop);
                int nIdx = getIndex(nx, ny, nz);
                if (nextLight > (skyLight[nIdx] & 0xFF)) {
                    skyLight[nIdx] = (byte) nextLight;
                    lightQueue[tail] = (ny << 8) | (nz << 4) | nx;
                    tail = (tail + 1) & 0x7FFF;
                }
            }
        }
    }

    private int injectBorderLight(int lx, int lz, int nwx, int nwz, int tail) {
        int cx = Math.floorDiv(nwx, SIZE_X);
        int cz = Math.floorDiv(nwz, SIZE_Z);
        Chunk neighbor = world.getChunk(cx, cz);
        if (neighbor == null) return tail;
        int nlx = (nwx % SIZE_X + SIZE_X) % SIZE_X;
        int nlz = (nwz % SIZE_Z + SIZE_Z) % SIZE_Z;
        if (neighbor.lightValid) {
            for (int y = 0; y < SIZE_Y; y++) {
                BlockType b = getBlock(lx, y, lz);
                if (b == null || b == BlockType.AIR || b.isTransparent()) {
                    int nLight = neighbor.getSkyLight(nlx, y, nlz);
                    int drop = (b == BlockType.LEAVES || b == BlockType.BIRCH_LEAVES ||
                                b == BlockType.ACACIA_LEAVES || b == BlockType.DARK_OAK_LEAVES) ? 2 : 1;
                    int injected = nLight - drop;
                    if (injected > 0) {
                        int idx = getIndex(lx, y, lz);
                        int cur = skyLight[idx] & 0xFF;
                        if (injected > cur) {
                            skyLight[idx] = (byte) injected;
                            lightQueue[tail] = (y << 8) | (lz << 4) | lx;
                            tail = (tail + 1) & 0x7FFF;
                        }
                    }
                }
            }
        } else {
            int nh = neighbor.getColumnHeight(nlx, nlz);
            for (int y = Math.max(0, nh + 1); y < SIZE_Y; y++) {
                BlockType b = getBlock(lx, y, lz);
                if (b == null || b == BlockType.AIR || b.isTransparent()) {
                    int idx = getIndex(lx, y, lz);
                    int cur = skyLight[idx] & 0xFF;
                    if (cur < 14) {
                        skyLight[idx] = (byte) 14;
                        lightQueue[tail] = (y << 8) | (lz << 4) | lx;
                        tail = (tail + 1) & 0x7FFF;
                    }
                }
            }
        }
        return tail;
    }

    public byte[] getBlocks() {
        return blocks;
    }

    public void setBlocks(byte[] src) {
        if (src != null && src.length == blocks.length) {
            System.arraycopy(src, 0, blocks, 0, blocks.length);
            isDirty = true;
        }
    }
}
