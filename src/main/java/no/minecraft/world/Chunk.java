package no.minecraft.world;

import no.minecraft.render.TextureAtlas;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

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

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;
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

    public boolean hasMesh() {
        return vaoId != 0;
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

    public void updateMeshIfNeeded() {
        if (isDirty || vaoId == 0) {
            rebuildMesh();
            isDirty = false;
        }
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

    public void rebuildMesh() {
        if (!lightValid) {
            rebuildSkyLight();
        }
        List<Float> vertices = new ArrayList<>();

        List<int[]> torches = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk neighbor = (dx == 0 && dz == 0) ? this : world.getChunk(chunkX + dx, chunkZ + dz);
                if (neighbor != null) {
                    neighbor.collectLightSources(torches);
                }
            }
        }

        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    BlockType type = getBlock(x, y, z);
                    if (type == BlockType.AIR) continue;

                    float wx = getWorldStartX() + x;
                    float wy = y;
                    float wz = getWorldStartZ() + z;

                    if (type == BlockType.TORCH) {
                        addTorch(vertices, wx, wy, wz, x, y, z);
                        continue;
                    }

                    float torchLight = (type == BlockType.LAVA) ? 1.0f : getTorchLight(torches, (int) wx, (int) wy, (int) wz);

                    if (type == BlockType.CACTUS) {
                        addCactus(vertices, wx, wy, wz, x, y, z, torchLight);
                        continue;
                    }
                    if (type == BlockType.CHEST) {
                        addChest(vertices, wx, wy, wz, x, y, z, torchLight);
                        continue;
                    }
                    if (type == BlockType.FURNACE) {
                        addFurnace(vertices, wx, wy, wz, x, y, z, torchLight);
                        continue;
                    }

                    // Top (+Y)
                    if (shouldRenderFace(x, y + 1, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.TOP, type, 1.0f, torchLight);
                    }
                    // Bottom (-Y)
                    if (shouldRenderFace(x, y - 1, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.BOTTOM, type, 0.5f, torchLight);
                    }
                    // North (-Z)
                    if (shouldRenderFace(x, y, z - 1, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.NORTH, type, 0.7f, torchLight);
                    }
                    // South (+Z)
                    if (shouldRenderFace(x, y, z + 1, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.SOUTH, type, 0.7f, torchLight);
                    }
                    // West (-X)
                    if (shouldRenderFace(x - 1, y, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.WEST, type, 0.8f, torchLight);
                    }
                    // East (+X)
                    if (shouldRenderFace(x + 1, y, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.EAST, type, 0.8f, torchLight);
                    }
                }
            }
        }

        vertexCount = vertices.size() / 7; // 7 floats per vertex: (x, y, z, u, v, faceLight, torchLight)

        if (vaoId == 0) {
            vaoId = glGenVertexArrays();
            vboId = glGenBuffers();
        }

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float f : vertices) {
            buffer.put(f);
        }
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        // Position: 3 floats
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // UV: 2 floats
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 7 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Light: 2 floats (faceLight, torchLight)
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 7 * Float.BYTES, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private boolean shouldRenderFace(int x, int y, int z, BlockType self) {
        if (y < 0) return false;
        if (y >= SIZE_Y) return true;
        BlockType neighbor = getBlock(x, y, z);
        if (neighbor == BlockType.AIR) return true;
        if (neighbor.isTransparent() && neighbor != self) return true;
        return false;
    }

    /** Sky exposure factor (0.0 to 1.0) of the air cell in front of a face, using Java 1.16.1 light propagation. */
    private float skyExposure(int x, int y, int z, BlockType.Face face) {
        int nx = x, ny = y, nz = z;
        switch (face) {
            case TOP -> ny++;
            case BOTTOM -> ny--;
            case NORTH -> nz--;
            case SOUTH -> nz++;
            case WEST -> nx--;
            case EAST -> nx++;
        }
        int lx = nx - getWorldStartX();
        int lz = nz - getWorldStartZ();
        return getSkyLight(lx, ny, lz) / 15.0f;
    }

    private void addFace(List<Float> v, float x, float y, float z, BlockType.Face face, BlockType block, float faceLight, float torchLight) {
        addFaceWithTexture(v, x, y, z, face, block.getTexture(face), faceLight, torchLight);
    }

    private void addFaceWithTexture(List<Float> v, float x, float y, float z, BlockType.Face face, int textureId, float faceLight, float torchLight) {
        faceLight *= skyExposure((int) x, (int) y, (int) z, face);
        float[] uv = TextureAtlas.getUVs(textureId);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        switch (face) {
            case TOP -> {
                // (x, y+1, z) to (x+1, y+1, z+1)
                addVertex(v, x, y + 1, z, u0, v0, faceLight, torchLight);
                addVertex(v, x, y + 1, z + 1, u0, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z + 1, u1, v1, faceLight, torchLight);

                addVertex(v, x, y + 1, z, u0, v0, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z + 1, u1, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z, u1, v0, faceLight, torchLight);
            }
            case BOTTOM -> {
                addVertex(v, x, y, z, u0, v0, faceLight, torchLight);
                addVertex(v, x + 1, y, z, u1, v0, faceLight, torchLight);
                addVertex(v, x + 1, y, z + 1, u1, v1, faceLight, torchLight);

                addVertex(v, x, y, z, u0, v0, faceLight, torchLight);
                addVertex(v, x + 1, y, z + 1, u1, v1, faceLight, torchLight);
                addVertex(v, x, y, z + 1, u0, v1, faceLight, torchLight);
            }
            case NORTH -> { // -Z face
                addVertex(v, x, y, z, u1, v1, faceLight, torchLight);
                addVertex(v, x, y + 1, z, u1, v0, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z, u0, v0, faceLight, torchLight);

                addVertex(v, x, y, z, u1, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z, u0, v0, faceLight, torchLight);
                addVertex(v, x + 1, y, z, u0, v1, faceLight, torchLight);
            }
            case SOUTH -> { // +Z face
                addVertex(v, x, y, z + 1, u0, v1, faceLight, torchLight);
                addVertex(v, x + 1, y, z + 1, u1, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z + 1, u1, v0, faceLight, torchLight);

                addVertex(v, x, y, z + 1, u0, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z + 1, u1, v0, faceLight, torchLight);
                addVertex(v, x, y + 1, z + 1, u0, v0, faceLight, torchLight);
            }
            case WEST -> { // -X face
                addVertex(v, x, y, z + 1, u1, v1, faceLight, torchLight);
                addVertex(v, x, y + 1, z + 1, u1, v0, faceLight, torchLight);
                addVertex(v, x, y + 1, z, u0, v0, faceLight, torchLight);

                addVertex(v, x, y, z + 1, u1, v1, faceLight, torchLight);
                addVertex(v, x, y + 1, z, u0, v0, faceLight, torchLight);
                addVertex(v, x, y, z, u0, v1, faceLight, torchLight);
            }
            case EAST -> { // +X face
                addVertex(v, x + 1, y, z, u1, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z, u1, v0, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z + 1, u0, v0, faceLight, torchLight);

                addVertex(v, x + 1, y, z, u1, v1, faceLight, torchLight);
                addVertex(v, x + 1, y + 1, z + 1, u0, v0, faceLight, torchLight);
                addVertex(v, x + 1, y, z + 1, u0, v1, faceLight, torchLight);
            }
        }
    }

    private void addFurnace(List<Float> v, float wx, float wy, float wz, int x, int y, int z, float torchLight) {
        FurnaceData fd = world.getFurnace((int) wx, (int) wy, (int) wz);
        BlockType.Face facing = (fd != null && fd.getFacing() != null) ? fd.getFacing() : BlockType.Face.NORTH;
        int frontTex = (fd != null && fd.isBurning()) ? 116 : 33;
        int topTex = 114;
        int sideTex = 115;

        // Top (+Y)
        if (shouldRenderFace(x, y + 1, z, BlockType.FURNACE)) {
            addFaceWithTexture(v, wx, wy, wz, BlockType.Face.TOP, topTex, 1.0f, torchLight);
        }
        // Bottom (-Y)
        if (shouldRenderFace(x, y - 1, z, BlockType.FURNACE)) {
            addFaceWithTexture(v, wx, wy, wz, BlockType.Face.BOTTOM, topTex, 0.5f, torchLight);
        }
        // North (-Z)
        if (shouldRenderFace(x, y, z - 1, BlockType.FURNACE)) {
            int tex = (facing == BlockType.Face.NORTH) ? frontTex : sideTex;
            addFaceWithTexture(v, wx, wy, wz, BlockType.Face.NORTH, tex, 0.7f, torchLight);
        }
        // South (+Z)
        if (shouldRenderFace(x, y, z + 1, BlockType.FURNACE)) {
            int tex = (facing == BlockType.Face.SOUTH) ? frontTex : sideTex;
            addFaceWithTexture(v, wx, wy, wz, BlockType.Face.SOUTH, tex, 0.7f, torchLight);
        }
        // West (-X)
        if (shouldRenderFace(x - 1, y, z, BlockType.FURNACE)) {
            int tex = (facing == BlockType.Face.WEST) ? frontTex : sideTex;
            addFaceWithTexture(v, wx, wy, wz, BlockType.Face.WEST, tex, 0.8f, torchLight);
        }
        // East (+X)
        if (shouldRenderFace(x + 1, y, z, BlockType.FURNACE)) {
            int tex = (facing == BlockType.Face.EAST) ? frontTex : sideTex;
            addFaceWithTexture(v, wx, wy, wz, BlockType.Face.EAST, tex, 0.8f, torchLight);
        }
    }

    private float getTorchLight(List<int[]> torches, int wx, int wy, int wz) {
        float maxLight = 0.0f;
        for (int[] t : torches) {
            int dx = wx - t[0];
            int dy = wy - t[1];
            int dz = wz - t[2];
            int distSq = dx * dx + dy * dy + dz * dz;
            float radius = (t.length >= 4) ? t[3] : 14.0f;
            float radiusSq = radius * radius;
            if (distSq <= radiusSq) {
                float dist = (float) Math.sqrt(distSq);
                float light = 1.0f - (dist / radius);
                if (light > maxLight) {
                    maxLight = light;
                }
            }
        }
        return maxLight;
    }

    private boolean isSolidBlock(int x, int y, int z) {
        if (y < 0) return true;
        if (y >= SIZE_Y) return false;
        BlockType b = getBlock(x, y, z);
        return b != null && b.isSolid();
    }

    private void addCactus(List<Float> v, float wx, float wy, float wz, int x, int y, int z, float torchLight) {
        float x0 = wx + 0.0625f; // 1/16
        float x1 = wx + 0.9375f; // 15/16
        float y0 = wy;
        float y1 = wy + 1.0f;
        float z0 = wz + 0.0625f; // 1/16
        float z1 = wz + 0.9375f; // 15/16

        // Top and bottom textures (Tile 82)
        int topTexId = BlockType.CACTUS.getTexture(BlockType.Face.TOP);
        float[] uvTop = TextureAtlas.getUVs(topTexId);
        float px = (uvTop[2] - uvTop[0]) / 16.0f;
        float py = (uvTop[3] - uvTop[1]) / 16.0f;
        // The solid green square in cactus_top.png is at pixels 1..15
        float uTop0 = uvTop[0] + 1.0f * px;
        float uTop1 = uvTop[0] + 15.0f * px;
        float vTop0 = uvTop[1] + 1.0f * py;
        float vTop1 = uvTop[1] + 15.0f * py;

        // Side texture (Tile 83)
        int sideTexId = BlockType.CACTUS.getTexture(BlockType.Face.NORTH);
        float[] uvSide = TextureAtlas.getUVs(sideTexId);
        float sx = (uvSide[2] - uvSide[0]) / 16.0f;
        // The solid green section in cactus_side.png is columns 1..15
        float uSide0 = uvSide[0] + 1.0f * sx;
        float uSide1 = uvSide[0] + 15.0f * sx;
        float vSide0 = uvSide[1];
        float vSide1 = uvSide[3];

        // Top face (+Y)
        if (getBlock(x, y + 1, z) != BlockType.CACTUS) {
            addVertex(v, x0, y1, z0, uTop0, vTop0, 1.0f, torchLight);
            addVertex(v, x0, y1, z1, uTop0, vTop1, 1.0f, torchLight);
            addVertex(v, x1, y1, z1, uTop1, vTop1, 1.0f, torchLight);

            addVertex(v, x0, y1, z0, uTop0, vTop0, 1.0f, torchLight);
            addVertex(v, x1, y1, z1, uTop1, vTop1, 1.0f, torchLight);
            addVertex(v, x1, y1, z0, uTop1, vTop0, 1.0f, torchLight);
        }

        // Bottom face (-Y)
        if (getBlock(x, y - 1, z) != BlockType.CACTUS) {
            addVertex(v, x0, y0, z0, uTop0, vTop0, 0.5f, torchLight);
            addVertex(v, x1, y0, z0, uTop1, vTop0, 0.5f, torchLight);
            addVertex(v, x1, y0, z1, uTop1, vTop1, 0.5f, torchLight);

            addVertex(v, x0, y0, z0, uTop0, vTop0, 0.5f, torchLight);
            addVertex(v, x1, y0, z1, uTop1, vTop1, 0.5f, torchLight);
            addVertex(v, x0, y0, z1, uTop0, vTop1, 0.5f, torchLight);
        }

        // North face (-Z, plane at z = z0)
        addVertex(v, x0, y0, z0, uSide1, vSide1, 0.7f, torchLight);
        addVertex(v, x0, y1, z0, uSide1, vSide0, 0.7f, torchLight);
        addVertex(v, x1, y1, z0, uSide0, vSide0, 0.7f, torchLight);

        addVertex(v, x0, y0, z0, uSide1, vSide1, 0.7f, torchLight);
        addVertex(v, x1, y1, z0, uSide0, vSide0, 0.7f, torchLight);
        addVertex(v, x1, y0, z0, uSide0, vSide1, 0.7f, torchLight);

        // South face (+Z, plane at z = z1)
        addVertex(v, x0, y0, z1, uSide0, vSide1, 0.7f, torchLight);
        addVertex(v, x1, y0, z1, uSide1, vSide1, 0.7f, torchLight);
        addVertex(v, x1, y1, z1, uSide1, vSide0, 0.7f, torchLight);

        addVertex(v, x0, y0, z1, uSide0, vSide1, 0.7f, torchLight);
        addVertex(v, x1, y1, z1, uSide1, vSide0, 0.7f, torchLight);
        addVertex(v, x0, y1, z1, uSide0, vSide0, 0.7f, torchLight);

        // West face (-X, plane at x = x0)
        addVertex(v, x0, y0, z1, uSide1, vSide1, 0.8f, torchLight);
        addVertex(v, x0, y1, z1, uSide1, vSide0, 0.8f, torchLight);
        addVertex(v, x0, y1, z0, uSide0, vSide0, 0.8f, torchLight);

        addVertex(v, x0, y0, z1, uSide1, vSide1, 0.8f, torchLight);
        addVertex(v, x0, y1, z0, uSide0, vSide0, 0.8f, torchLight);
        addVertex(v, x0, y0, z0, uSide0, vSide1, 0.8f, torchLight);

        // East face (+X, plane at x = x1)
        addVertex(v, x1, y0, z0, uSide1, vSide1, 0.8f, torchLight);
        addVertex(v, x1, y1, z0, uSide1, vSide0, 0.8f, torchLight);
        addVertex(v, x1, y1, z1, uSide0, vSide0, 0.8f, torchLight);

        addVertex(v, x1, y0, z0, uSide1, vSide1, 0.8f, torchLight);
        addVertex(v, x1, y1, z1, uSide0, vSide0, 0.8f, torchLight);
        addVertex(v, x1, y0, z1, uSide0, vSide1, 0.8f, torchLight);
    }

    private void addChest(List<Float> v, float wx, float wy, float wz, int x, int y, int z, float torchLight) {
        float x0 = wx + 0.0625f; // 1/16
        float x1 = wx + 0.9375f; // 15/16
        float y0 = wy;
        float y1 = wy + 0.875f;  // 14/16
        float z0 = wz + 0.0625f; // 1/16
        float z1 = wz + 0.9375f; // 15/16

        ChestData cd = world.getChest((int) wx, (int) wy, (int) wz);
        BlockType.Face facing = (cd != null && cd.getFacing() != null) ? cd.getFacing() : BlockType.Face.NORTH;

        float[] uvTop = TextureAtlas.getUVs(BlockType.CHEST.getTexture(BlockType.Face.TOP));
        float[] uvBot = TextureAtlas.getUVs(BlockType.CHEST.getTexture(BlockType.Face.BOTTOM));
        float[] uvFront = TextureAtlas.getUVs(22); // Front with latch
        float[] uvSide = TextureAtlas.getUVs(111); // Side/back

        // Top face (+Y, plane at y = y1)
        addVertex(v, x0, y1, z0, uvTop[0], uvTop[1], 1.0f, torchLight);
        addVertex(v, x0, y1, z1, uvTop[0], uvTop[3], 1.0f, torchLight);
        addVertex(v, x1, y1, z1, uvTop[2], uvTop[3], 1.0f, torchLight);

        addVertex(v, x0, y1, z0, uvTop[0], uvTop[1], 1.0f, torchLight);
        addVertex(v, x1, y1, z1, uvTop[2], uvTop[3], 1.0f, torchLight);
        addVertex(v, x1, y1, z0, uvTop[2], uvTop[1], 1.0f, torchLight);

        // Bottom face (-Y, plane at y = y0)
        addVertex(v, x0, y0, z0, uvBot[0], uvBot[1], 0.5f, torchLight);
        addVertex(v, x1, y0, z0, uvBot[2], uvBot[1], 0.5f, torchLight);
        addVertex(v, x1, y0, z1, uvBot[2], uvBot[3], 0.5f, torchLight);

        addVertex(v, x0, y0, z0, uvBot[0], uvBot[1], 0.5f, torchLight);
        addVertex(v, x1, y0, z1, uvBot[2], uvBot[3], 0.5f, torchLight);
        addVertex(v, x0, y0, z1, uvBot[0], uvBot[3], 0.5f, torchLight);

        // North face (-Z, plane at z = z0)
        float[] uvN = (facing == BlockType.Face.NORTH) ? uvFront : uvSide;
        addVertex(v, x0, y0, z0, uvN[2], uvN[3], 0.7f, torchLight);
        addVertex(v, x0, y1, z0, uvN[2], uvN[1], 0.7f, torchLight);
        addVertex(v, x1, y1, z0, uvN[0], uvN[1], 0.7f, torchLight);

        addVertex(v, x0, y0, z0, uvN[2], uvN[3], 0.7f, torchLight);
        addVertex(v, x1, y1, z0, uvN[0], uvN[1], 0.7f, torchLight);
        addVertex(v, x1, y0, z0, uvN[0], uvN[3], 0.7f, torchLight);

        // South face (+Z, plane at z = z1)
        float[] uvS = (facing == BlockType.Face.SOUTH) ? uvFront : uvSide;
        addVertex(v, x0, y0, z1, uvS[0], uvS[3], 0.7f, torchLight);
        addVertex(v, x1, y0, z1, uvS[2], uvS[3], 0.7f, torchLight);
        addVertex(v, x1, y1, z1, uvS[2], uvS[1], 0.7f, torchLight);

        addVertex(v, x0, y0, z1, uvS[0], uvS[3], 0.7f, torchLight);
        addVertex(v, x1, y1, z1, uvS[2], uvS[1], 0.7f, torchLight);
        addVertex(v, x0, y1, z1, uvS[0], uvS[1], 0.7f, torchLight);

        // West face (-X, plane at x = x0)
        float[] uvW = (facing == BlockType.Face.WEST) ? uvFront : uvSide;
        addVertex(v, x0, y0, z1, uvW[2], uvW[3], 0.8f, torchLight);
        addVertex(v, x0, y1, z1, uvW[2], uvW[1], 0.8f, torchLight);
        addVertex(v, x0, y1, z0, uvW[0], uvW[1], 0.8f, torchLight);

        addVertex(v, x0, y0, z1, uvW[2], uvW[3], 0.8f, torchLight);
        addVertex(v, x0, y1, z0, uvW[0], uvW[1], 0.8f, torchLight);
        addVertex(v, x0, y0, z0, uvW[0], uvW[3], 0.8f, torchLight);

        // East face (+X, plane at x = x1)
        float[] uvE = (facing == BlockType.Face.EAST) ? uvFront : uvSide;
        addVertex(v, x1, y0, z0, uvE[2], uvE[3], 0.8f, torchLight);
        addVertex(v, x1, y1, z0, uvE[2], uvE[1], 0.8f, torchLight);
        addVertex(v, x1, y1, z1, uvE[0], uvE[1], 0.8f, torchLight);

        addVertex(v, x1, y0, z0, uvE[2], uvE[3], 0.8f, torchLight);
        addVertex(v, x1, y1, z1, uvE[0], uvE[1], 0.8f, torchLight);
        addVertex(v, x1, y0, z1, uvE[0], uvE[3], 0.8f, torchLight);
    }

    private void addTorch(List<Float> v, float wx, float wy, float wz, int x, int y, int z) {
        boolean floorSolid = isSolidBlock(x, y - 1, z);
        boolean westSolid  = isSolidBlock(x - 1, y, z);
        boolean eastSolid  = isSolidBlock(x + 1, y, z);
        boolean northSolid = isSolidBlock(x, y, z - 1);
        boolean southSolid = isSolidBlock(x, y, z + 1);

        float hw = 1.0f / 16.0f; // 0.0625f (2/16 wide cuboid)
        float bx, by, bz;
        float tx, ty, tz;

        if (!floorSolid && westSolid) {
            // Attached to West wall (-X), tilts towards +X
            bx = wx + 0.08f; by = wy + 0.2f;  bz = wz + 0.5f;
            tx = wx + 0.35f; ty = wy + 0.72f; tz = wz + 0.5f;
        } else if (!floorSolid && eastSolid) {
            // Attached to East wall (+X), tilts towards -X
            bx = wx + 1.0f - 0.08f; by = wy + 0.2f;  bz = wz + 0.5f;
            tx = wx + 1.0f - 0.35f; ty = wy + 0.72f; tz = wz + 0.5f;
        } else if (!floorSolid && northSolid) {
            // Attached to North wall (-Z), tilts towards +Z
            bx = wx + 0.5f; by = wy + 0.2f;  bz = wz + 0.08f;
            tx = wx + 0.5f; ty = wy + 0.72f; tz = wz + 0.35f;
        } else if (!floorSolid && southSolid) {
            // Attached to South wall (+Z), tilts towards -Z
            bx = wx + 0.5f; by = wy + 0.2f;  bz = wz + 1.0f - 0.08f;
            tx = wx + 0.5f; ty = wy + 0.72f; tz = wz + 1.0f - 0.35f;
        } else {
            // Standing upright on floor
            bx = wx + 0.5f; by = wy;          bz = wz + 0.5f;
            tx = wx + 0.5f; ty = wy + 0.625f; tz = wz + 0.5f;
        }

        // 8 vertices defining the 3D post
        float b00x = bx - hw, b00y = by, b00z = bz - hw;
        float b10x = bx + hw, b10y = by, b10z = bz - hw;
        float b11x = bx + hw, b11y = by, b11z = bz + hw;
        float b01x = bx - hw, b01y = by, b01z = bz + hw;

        float t00x = tx - hw, t00y = ty, t00z = tz - hw;
        float t10x = tx + hw, t10y = ty, t10z = tz - hw;
        float t11x = tx + hw, t11y = ty, t11z = tz + hw;
        float t01x = tx - hw, t01y = ty, t01z = tz + hw;

        float[] uv = TextureAtlas.getUVs(BlockType.TORCH.getTexture(BlockType.Face.NORTH));
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float px = (u1 - u0) / 16.0f;
        float py = (v1 - v0) / 16.0f;

        // Exact opaque UVs in tile 92: columns 7..8, rows 1..14
        float uMin = u0 + 7.0f * px;
        float uMax = u0 + 9.0f * px;
        float vMin = v0 + 1.0f * py;
        float vMax = v0 + 15.0f * py;

        // Top face (flame head)
        float vTopMin = v0 + 2.0f * py;
        float vTopMax = v0 + 4.0f * py;

        // Bottom face (wood base)
        float vBotMin = v0 + 13.0f * py;
        float vBotMax = v0 + 15.0f * py;

        float faceLight = 1.0f;
        float torchLight = 1.0f;

        // Top face (+Y)
        addVertex(v, t00x, t00y, t00z, uMin, vTopMin, faceLight, torchLight);
        addVertex(v, t01x, t01y, t01z, uMin, vTopMax, faceLight, torchLight);
        addVertex(v, t11x, t11y, t11z, uMax, vTopMax, faceLight, torchLight);
        addVertex(v, t00x, t00y, t00z, uMin, vTopMin, faceLight, torchLight);
        addVertex(v, t11x, t11y, t11z, uMax, vTopMax, faceLight, torchLight);
        addVertex(v, t10x, t10y, t10z, uMax, vTopMin, faceLight, torchLight);

        // Bottom face (-Y)
        addVertex(v, b00x, b00y, b00z, uMin, vBotMin, faceLight, torchLight);
        addVertex(v, b10x, b10y, b10z, uMax, vBotMin, faceLight, torchLight);
        addVertex(v, b11x, b11y, b11z, uMax, vBotMax, faceLight, torchLight);
        addVertex(v, b00x, b00y, b00z, uMin, vBotMin, faceLight, torchLight);
        addVertex(v, b11x, b11y, b11z, uMax, vBotMax, faceLight, torchLight);
        addVertex(v, b01x, b01y, b01z, uMin, vBotMax, faceLight, torchLight);

        // North face (-Z)
        addVertex(v, b00x, b00y, b00z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t00x, t00y, t00z, uMax, vMin, faceLight, torchLight);
        addVertex(v, t10x, t10y, t10z, uMin, vMin, faceLight, torchLight);
        addVertex(v, b00x, b00y, b00z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t10x, t10y, t10z, uMin, vMin, faceLight, torchLight);
        addVertex(v, b10x, b10y, b10z, uMin, vMax, faceLight, torchLight);

        // South face (+Z)
        addVertex(v, b01x, b01y, b01z, uMin, vMax, faceLight, torchLight);
        addVertex(v, b11x, b11y, b11z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t11x, t11y, t11z, uMax, vMin, faceLight, torchLight);
        addVertex(v, b01x, b01y, b01z, uMin, vMax, faceLight, torchLight);
        addVertex(v, t11x, t11y, t11z, uMax, vMin, faceLight, torchLight);
        addVertex(v, t01x, t01y, t01z, uMin, vMin, faceLight, torchLight);

        // West face (-X)
        addVertex(v, b01x, b01y, b01z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t01x, t01y, t01z, uMax, vMin, faceLight, torchLight);
        addVertex(v, t00x, t00y, t00z, uMin, vMin, faceLight, torchLight);
        addVertex(v, b01x, b01y, b01z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t00x, t00y, t00z, uMin, vMin, faceLight, torchLight);
        addVertex(v, b00x, b00y, b00z, uMin, vMax, faceLight, torchLight);

        // East face (+X)
        addVertex(v, b10x, b10y, b10z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t10x, t10y, t10z, uMax, vMin, faceLight, torchLight);
        addVertex(v, t11x, t11y, t11z, uMin, vMin, faceLight, torchLight);
        addVertex(v, b10x, b10y, b10z, uMax, vMax, faceLight, torchLight);
        addVertex(v, t11x, t11y, t11z, uMin, vMin, faceLight, torchLight);
        addVertex(v, b11x, b11y, b11z, uMin, vMax, faceLight, torchLight);
    }

    private void addVertex(List<Float> v, float x, float y, float z, float u, float valV, float faceLight, float torchLight) {
        v.add(x);
        v.add(y);
        v.add(z);
        v.add(u);
        v.add(valV);
        v.add(faceLight);
        v.add(torchLight);
    }

    public void render() {
        if (vertexCount == 0 || vaoId == 0) return;
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        vertexCount = 0;
    }

    public void unloadMesh() {
        cleanup();
        isDirty = false;
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
