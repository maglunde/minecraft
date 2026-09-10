package no.minecraft.world;

import no.minecraft.render.TextureAtlas;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
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
            blocks[getIndex(x, y, z)] = type.getId();
            setDirty(true);
            needsSave = true;
            // Mark neighboring chunks dirty if on border
            if (x == 0) world.markChunkDirty(chunkX - 1, chunkZ);
            if (x == SIZE_X - 1) world.markChunkDirty(chunkX + 1, chunkZ);
            if (z == 0) world.markChunkDirty(chunkX, chunkZ - 1);
            if (z == SIZE_Z - 1) world.markChunkDirty(chunkX, chunkZ + 1);
        }
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

    public void rebuildMesh() {
        List<Float> vertices = new ArrayList<>();

        List<int[]> torches = new ArrayList<>();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    if (getBlock(x, y, z) == BlockType.TORCH) {
                        torches.add(new int[]{x, y, z});
                    }
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
                    if (type == BlockType.CACTUS) {
                        float boost = getTorchLightBoost(torches, x, y, z);
                        addCactus(vertices, wx, wy, wz, x, y, z, boost);
                        continue;
                    }
                    if (type == BlockType.CHEST) {
                        float boost = getTorchLightBoost(torches, x, y, z);
                        addChest(vertices, wx, wy, wz, x, y, z, boost);
                        continue;
                    }

                    float boost = getTorchLightBoost(torches, x, y, z);

                    // Top (+Y)
                    if (shouldRenderFace(x, y + 1, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.TOP, type, Math.min(1.0f, 1.0f + boost));
                    }
                    // Bottom (-Y)
                    if (shouldRenderFace(x, y - 1, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.BOTTOM, type, Math.min(1.0f, 0.5f + boost));
                    }
                    // North (-Z)
                    if (shouldRenderFace(x, y, z - 1, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.NORTH, type, Math.min(1.0f, 0.7f + boost));
                    }
                    // South (+Z)
                    if (shouldRenderFace(x, y, z + 1, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.SOUTH, type, Math.min(1.0f, 0.7f + boost));
                    }
                    // West (-X)
                    if (shouldRenderFace(x - 1, y, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.WEST, type, Math.min(1.0f, 0.8f + boost));
                    }
                    // East (+X)
                    if (shouldRenderFace(x + 1, y, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.EAST, type, Math.min(1.0f, 0.8f + boost));
                    }
                }
            }
        }

        vertexCount = vertices.size() / 6; // 6 floats per vertex: (x, y, z, u, v, light)

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
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // UV: 2 floats
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Light: 1 float
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 6 * Float.BYTES, 5 * Float.BYTES);
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

    private void addFace(List<Float> v, float x, float y, float z, BlockType.Face face, BlockType block, float light) {
        int textureId = block.getTexture(face);
        float[] uv = TextureAtlas.getUVs(textureId);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        switch (face) {
            case TOP -> {
                // (x, y+1, z) to (x+1, y+1, z+1)
                addVertex(v, x, y + 1, z, u0, v0, light);
                addVertex(v, x, y + 1, z + 1, u0, v1, light);
                addVertex(v, x + 1, y + 1, z + 1, u1, v1, light);

                addVertex(v, x, y + 1, z, u0, v0, light);
                addVertex(v, x + 1, y + 1, z + 1, u1, v1, light);
                addVertex(v, x + 1, y + 1, z, u1, v0, light);
            }
            case BOTTOM -> {
                addVertex(v, x, y, z, u0, v0, light);
                addVertex(v, x + 1, y, z, u1, v0, light);
                addVertex(v, x + 1, y, z + 1, u1, v1, light);

                addVertex(v, x, y, z, u0, v0, light);
                addVertex(v, x + 1, y, z + 1, u1, v1, light);
                addVertex(v, x, y, z + 1, u0, v1, light);
            }
            case NORTH -> { // -Z face
                addVertex(v, x, y, z, u1, v1, light);
                addVertex(v, x, y + 1, z, u1, v0, light);
                addVertex(v, x + 1, y + 1, z, u0, v0, light);

                addVertex(v, x, y, z, u1, v1, light);
                addVertex(v, x + 1, y + 1, z, u0, v0, light);
                addVertex(v, x + 1, y, z, u0, v1, light);
            }
            case SOUTH -> { // +Z face
                addVertex(v, x, y, z + 1, u0, v1, light);
                addVertex(v, x + 1, y, z + 1, u1, v1, light);
                addVertex(v, x + 1, y + 1, z + 1, u1, v0, light);

                addVertex(v, x, y, z + 1, u0, v1, light);
                addVertex(v, x + 1, y + 1, z + 1, u1, v0, light);
                addVertex(v, x, y + 1, z + 1, u0, v0, light);
            }
            case WEST -> { // -X face
                addVertex(v, x, y, z + 1, u1, v1, light);
                addVertex(v, x, y + 1, z + 1, u1, v0, light);
                addVertex(v, x, y + 1, z, u0, v0, light);

                addVertex(v, x, y, z + 1, u1, v1, light);
                addVertex(v, x, y + 1, z, u0, v0, light);
                addVertex(v, x, y, z, u0, v1, light);
            }
            case EAST -> { // +X face
                addVertex(v, x + 1, y, z, u1, v1, light);
                addVertex(v, x + 1, y + 1, z, u1, v0, light);
                addVertex(v, x + 1, y + 1, z + 1, u0, v0, light);

                addVertex(v, x + 1, y, z, u1, v1, light);
                addVertex(v, x + 1, y + 1, z + 1, u0, v0, light);
                addVertex(v, x + 1, y, z + 1, u0, v1, light);
            }
        }
    }

    private float getTorchLightBoost(List<int[]> torches, int x, int y, int z) {
        float maxBoost = 0.0f;
        for (int[] t : torches) {
            int dx = x - t[0];
            int dy = y - t[1];
            int dz = z - t[2];
            int distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= 49) {
                float dist = (float) Math.sqrt(distSq);
                float boost = (1.0f - dist / 7.0f) * 0.6f;
                if (boost > maxBoost) {
                    maxBoost = boost;
                }
            }
        }
        return maxBoost;
    }

    private boolean isSolidBlock(int x, int y, int z) {
        if (y < 0) return true;
        if (y >= SIZE_Y) return false;
        BlockType b = getBlock(x, y, z);
        return b != null && b.isSolid();
    }

    private void addCactus(List<Float> v, float wx, float wy, float wz, int x, int y, int z, float boost) {
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
            float light = Math.min(1.0f, 1.0f + boost);
            addVertex(v, x0, y1, z0, uTop0, vTop0, light);
            addVertex(v, x0, y1, z1, uTop0, vTop1, light);
            addVertex(v, x1, y1, z1, uTop1, vTop1, light);

            addVertex(v, x0, y1, z0, uTop0, vTop0, light);
            addVertex(v, x1, y1, z1, uTop1, vTop1, light);
            addVertex(v, x1, y1, z0, uTop1, vTop0, light);
        }

        // Bottom face (-Y)
        if (getBlock(x, y - 1, z) != BlockType.CACTUS) {
            float light = Math.min(1.0f, 0.5f + boost);
            addVertex(v, x0, y0, z0, uTop0, vTop0, light);
            addVertex(v, x1, y0, z0, uTop1, vTop0, light);
            addVertex(v, x1, y0, z1, uTop1, vTop1, light);

            addVertex(v, x0, y0, z0, uTop0, vTop0, light);
            addVertex(v, x1, y0, z1, uTop1, vTop1, light);
            addVertex(v, x0, y0, z1, uTop0, vTop1, light);
        }

        // North face (-Z, plane at z = z0)
        float lightN = Math.min(1.0f, 0.7f + boost);
        addVertex(v, x0, y0, z0, uSide1, vSide1, lightN);
        addVertex(v, x0, y1, z0, uSide1, vSide0, lightN);
        addVertex(v, x1, y1, z0, uSide0, vSide0, lightN);

        addVertex(v, x0, y0, z0, uSide1, vSide1, lightN);
        addVertex(v, x1, y1, z0, uSide0, vSide0, lightN);
        addVertex(v, x1, y0, z0, uSide0, vSide1, lightN);

        // South face (+Z, plane at z = z1)
        float lightS = Math.min(1.0f, 0.7f + boost);
        addVertex(v, x0, y0, z1, uSide0, vSide1, lightS);
        addVertex(v, x1, y0, z1, uSide1, vSide1, lightS);
        addVertex(v, x1, y1, z1, uSide1, vSide0, lightS);

        addVertex(v, x0, y0, z1, uSide0, vSide1, lightS);
        addVertex(v, x1, y1, z1, uSide1, vSide0, lightS);
        addVertex(v, x0, y1, z1, uSide0, vSide0, lightS);

        // West face (-X, plane at x = x0)
        float lightW = Math.min(1.0f, 0.8f + boost);
        addVertex(v, x0, y0, z1, uSide1, vSide1, lightW);
        addVertex(v, x0, y1, z1, uSide1, vSide0, lightW);
        addVertex(v, x0, y1, z0, uSide0, vSide0, lightW);

        addVertex(v, x0, y0, z1, uSide1, vSide1, lightW);
        addVertex(v, x0, y1, z0, uSide0, vSide0, lightW);
        addVertex(v, x0, y0, z0, uSide0, vSide1, lightW);

        // East face (+X, plane at x = x1)
        float lightE = Math.min(1.0f, 0.8f + boost);
        addVertex(v, x1, y0, z0, uSide1, vSide1, lightE);
        addVertex(v, x1, y1, z0, uSide1, vSide0, lightE);
        addVertex(v, x1, y1, z1, uSide0, vSide0, lightE);

        addVertex(v, x1, y0, z0, uSide1, vSide1, lightE);
        addVertex(v, x1, y1, z1, uSide0, vSide0, lightE);
        addVertex(v, x1, y0, z1, uSide0, vSide1, lightE);
    }

    private void addChest(List<Float> v, float wx, float wy, float wz, int x, int y, int z, float boost) {
        float x0 = wx + 0.0625f; // 1/16
        float x1 = wx + 0.9375f; // 15/16
        float y0 = wy;
        float y1 = wy + 0.875f;  // 14/16
        float z0 = wz + 0.0625f; // 1/16
        float z1 = wz + 0.9375f; // 15/16

        float[] uvTop = TextureAtlas.getUVs(BlockType.CHEST.getTexture(BlockType.Face.TOP));
        float[] uvBot = TextureAtlas.getUVs(BlockType.CHEST.getTexture(BlockType.Face.BOTTOM));
        float[] uvFront = TextureAtlas.getUVs(BlockType.CHEST.getTexture(BlockType.Face.NORTH));
        float[] uvSide = TextureAtlas.getUVs(BlockType.CHEST.getTexture(BlockType.Face.SOUTH));

        // Top face (+Y, plane at y = y1)
        float lightTop = Math.min(1.0f, 1.0f + boost);
        addVertex(v, x0, y1, z0, uvTop[0], uvTop[1], lightTop);
        addVertex(v, x0, y1, z1, uvTop[0], uvTop[3], lightTop);
        addVertex(v, x1, y1, z1, uvTop[2], uvTop[3], lightTop);

        addVertex(v, x0, y1, z0, uvTop[0], uvTop[1], lightTop);
        addVertex(v, x1, y1, z1, uvTop[2], uvTop[3], lightTop);
        addVertex(v, x1, y1, z0, uvTop[2], uvTop[1], lightTop);

        // Bottom face (-Y, plane at y = y0)
        float lightBot = Math.min(1.0f, 0.5f + boost);
        addVertex(v, x0, y0, z0, uvBot[0], uvBot[1], lightBot);
        addVertex(v, x1, y0, z0, uvBot[2], uvBot[1], lightBot);
        addVertex(v, x1, y0, z1, uvBot[2], uvBot[3], lightBot);

        addVertex(v, x0, y0, z0, uvBot[0], uvBot[1], lightBot);
        addVertex(v, x1, y0, z1, uvBot[2], uvBot[3], lightBot);
        addVertex(v, x0, y0, z1, uvBot[0], uvBot[3], lightBot);

        // North face (-Z, plane at z = z0, FRONT)
        float lightN = Math.min(1.0f, 0.7f + boost);
        addVertex(v, x0, y0, z0, uvFront[2], uvFront[3], lightN);
        addVertex(v, x0, y1, z0, uvFront[2], uvFront[1], lightN);
        addVertex(v, x1, y1, z0, uvFront[0], uvFront[1], lightN);

        addVertex(v, x0, y0, z0, uvFront[2], uvFront[3], lightN);
        addVertex(v, x1, y1, z0, uvFront[0], uvFront[1], lightN);
        addVertex(v, x1, y0, z0, uvFront[0], uvFront[3], lightN);

        // South face (+Z, plane at z = z1, BACK)
        float lightS = Math.min(1.0f, 0.7f + boost);
        addVertex(v, x0, y0, z1, uvSide[0], uvSide[3], lightS);
        addVertex(v, x1, y0, z1, uvSide[2], uvSide[3], lightS);
        addVertex(v, x1, y1, z1, uvSide[2], uvSide[1], lightS);

        addVertex(v, x0, y0, z1, uvSide[0], uvSide[3], lightS);
        addVertex(v, x1, y1, z1, uvSide[2], uvSide[1], lightS);
        addVertex(v, x0, y1, z1, uvSide[0], uvSide[1], lightS);

        // West face (-X, plane at x = x0, LEFT)
        float lightW = Math.min(1.0f, 0.8f + boost);
        addVertex(v, x0, y0, z1, uvSide[2], uvSide[3], lightW);
        addVertex(v, x0, y1, z1, uvSide[2], uvSide[1], lightW);
        addVertex(v, x0, y1, z0, uvSide[0], uvSide[1], lightW);

        addVertex(v, x0, y0, z1, uvSide[2], uvSide[3], lightW);
        addVertex(v, x0, y1, z0, uvSide[0], uvSide[1], lightW);
        addVertex(v, x0, y0, z0, uvSide[0], uvSide[3], lightW);

        // East face (+X, plane at x = x1, RIGHT)
        float lightE = Math.min(1.0f, 0.8f + boost);
        addVertex(v, x1, y0, z0, uvSide[2], uvSide[3], lightE);
        addVertex(v, x1, y1, z0, uvSide[2], uvSide[1], lightE);
        addVertex(v, x1, y1, z1, uvSide[0], uvSide[1], lightE);

        addVertex(v, x1, y0, z0, uvSide[2], uvSide[3], lightE);
        addVertex(v, x1, y1, z1, uvSide[0], uvSide[1], lightE);
        addVertex(v, x1, y0, z1, uvSide[0], uvSide[3], lightE);
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

        float light = 1.0f;

        // Top face (+Y)
        addVertex(v, t00x, t00y, t00z, uMin, vTopMin, light);
        addVertex(v, t01x, t01y, t01z, uMin, vTopMax, light);
        addVertex(v, t11x, t11y, t11z, uMax, vTopMax, light);
        addVertex(v, t00x, t00y, t00z, uMin, vTopMin, light);
        addVertex(v, t11x, t11y, t11z, uMax, vTopMax, light);
        addVertex(v, t10x, t10y, t10z, uMax, vTopMin, light);

        // Bottom face (-Y)
        addVertex(v, b00x, b00y, b00z, uMin, vBotMin, light);
        addVertex(v, b10x, b10y, b10z, uMax, vBotMin, light);
        addVertex(v, b11x, b11y, b11z, uMax, vBotMax, light);
        addVertex(v, b00x, b00y, b00z, uMin, vBotMin, light);
        addVertex(v, b11x, b11y, b11z, uMax, vBotMax, light);
        addVertex(v, b01x, b01y, b01z, uMin, vBotMax, light);

        // North face (-Z)
        addVertex(v, b00x, b00y, b00z, uMax, vMax, light);
        addVertex(v, t00x, t00y, t00z, uMax, vMin, light);
        addVertex(v, t10x, t10y, t10z, uMin, vMin, light);
        addVertex(v, b00x, b00y, b00z, uMax, vMax, light);
        addVertex(v, t10x, t10y, t10z, uMin, vMin, light);
        addVertex(v, b10x, b10y, b10z, uMin, vMax, light);

        // South face (+Z)
        addVertex(v, b01x, b01y, b01z, uMin, vMax, light);
        addVertex(v, b11x, b11y, b11z, uMax, vMax, light);
        addVertex(v, t11x, t11y, t11z, uMax, vMin, light);
        addVertex(v, b01x, b01y, b01z, uMin, vMax, light);
        addVertex(v, t11x, t11y, t11z, uMax, vMin, light);
        addVertex(v, t01x, t01y, t01z, uMin, vMin, light);

        // West face (-X)
        addVertex(v, b01x, b01y, b01z, uMax, vMax, light);
        addVertex(v, t01x, t01y, t01z, uMax, vMin, light);
        addVertex(v, t00x, t00y, t00z, uMin, vMin, light);
        addVertex(v, b01x, b01y, b01z, uMax, vMax, light);
        addVertex(v, t00x, t00y, t00z, uMin, vMin, light);
        addVertex(v, b00x, b00y, b00z, uMin, vMax, light);

        // East face (+X)
        addVertex(v, b10x, b10y, b10z, uMax, vMax, light);
        addVertex(v, t10x, t10y, t10z, uMax, vMin, light);
        addVertex(v, t11x, t11y, t11z, uMin, vMin, light);
        addVertex(v, b10x, b10y, b10z, uMax, vMax, light);
        addVertex(v, t11x, t11y, t11z, uMin, vMin, light);
        addVertex(v, b11x, b11y, b11z, uMin, vMax, light);
    }

    private void addVertex(List<Float> v, float x, float y, float z, float u, float valV, float light) {
        v.add(x);
        v.add(y);
        v.add(z);
        v.add(u);
        v.add(valV);
        v.add(light);
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
