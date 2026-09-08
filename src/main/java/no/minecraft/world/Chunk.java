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
            // Mark neighboring chunks dirty if on border
            if (x == 0) world.markChunkDirty(chunkX - 1, chunkZ);
            if (x == SIZE_X - 1) world.markChunkDirty(chunkX + 1, chunkZ);
            if (z == 0) world.markChunkDirty(chunkX, chunkZ - 1);
            if (z == SIZE_Z - 1) world.markChunkDirty(chunkX, chunkZ + 1);
        }
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    public void updateMeshIfNeeded() {
        if (isDirty) {
            rebuildMesh();
            isDirty = false;
        }
    }

    public void rebuildMesh() {
        List<Float> vertices = new ArrayList<>();

        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int x = 0; x < SIZE_X; x++) {
                    BlockType type = getBlock(x, y, z);
                    if (type == BlockType.AIR) continue;

                    float wx = getWorldStartX() + x;
                    float wy = y;
                    float wz = getWorldStartZ() + z;

                    // Top (+Y)
                    if (shouldRenderFace(x, y + 1, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.TOP, type, 1.0f);
                    }
                    // Bottom (-Y)
                    if (shouldRenderFace(x, y - 1, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.BOTTOM, type, 0.5f);
                    }
                    // North (-Z)
                    if (shouldRenderFace(x, y, z - 1, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.NORTH, type, 0.7f);
                    }
                    // South (+Z)
                    if (shouldRenderFace(x, y, z + 1, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.SOUTH, type, 0.7f);
                    }
                    // West (-X)
                    if (shouldRenderFace(x - 1, y, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.WEST, type, 0.8f);
                    }
                    // East (+X)
                    if (shouldRenderFace(x + 1, y, z, type)) {
                        addFace(vertices, wx, wy, wz, BlockType.Face.EAST, type, 0.8f);
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

    private void addVertex(List<Float> v, float x, float y, float z, float u, float valV, float light) {
        v.add(x);
        v.add(y);
        v.add(z);
        v.add(u);
        v.add(valV);
        v.add(light);
    }

    public void render() {
        if (vertexCount == 0) return;
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        if (vboId != 0) glDeleteBuffers(vboId);
        if (vaoId != 0) glDeleteVertexArrays(vaoId);
    }
}
