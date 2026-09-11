package no.minecraft.render;

import no.minecraft.world.BlockType;
import no.minecraft.world.DroppedItem;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class ItemRenderer {
    private final int vaoId;
    private final int vboId;

    public ItemRenderer() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Position (3 floats), UV (2 floats), Light (2 floats) = 7 floats
        int stride = 7 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(List<DroppedItem> items, Shader shader, Matrix4f view, Matrix4f projection, TextureAtlas atlas) {
        if (items.isEmpty()) return;

        List<Float> vertices = new ArrayList<>();

        for (DroppedItem item : items) {
            if (item.isDead()) continue;

            float age = item.getAge();
            float rotY = age * 2.0f;
            float bobY = (float) Math.sin(age * 3.5f) * 0.08f;

            float cx = item.getPosition().x;
            float cy = item.getPosition().y + bobY + 0.15f;
            float cz = item.getPosition().z;

            float size = 0.26f; // Mini cube size
            float half = size * 0.5f;

            BlockType type = item.getType();

            if (type.isSolid()) {
                // Transform and add all 6 faces of the cube with rotation around (cx, cy, cz)
                addRotatedCube(vertices, cx, cy, cz, half, rotY, type);
            } else {
                // 2D flat item rotating around (cx, cy, cz)
                addRotatedItem(vertices, cx, cy, cz, 0.32f, rotY, type);
            }
        }

        if (vertices.isEmpty()) return;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float f : vertices) {
            buffer.put(f);
        }
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, vertices.size() / 7);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void addRotatedItem(List<Float> v, float cx, float cy, float cz, float size, float rotY, BlockType item) {
        float cos = (float) Math.cos(rotY);
        float sin = (float) Math.sin(rotY);
        float h = size * 0.5f;

        // 4 local corners (centered flat quad on XY plane)
        float[][] local = {
                {-h, -h, 0}, { h, -h, 0}, { h,  h, 0}, {-h,  h, 0}
        };

        // Rotated world corners around (cx, cy, cz)
        float[][] p = new float[4][3];
        for (int i = 0; i < 4; i++) {
            float lx = local[i][0];
            float ly = local[i][1];
            p[i][0] = cx + (lx * cos);
            p[i][1] = cy + ly;
            p[i][2] = cz + (lx * sin);
        }

        float[] uv = TextureAtlas.getUVs(item.getItemTexture());
        float torchLight = (item == BlockType.TORCH) ? 1.0f : 0.0f;

        // Front Face
        addFaceWithUV(v, p[0], p[1], p[2], p[3], uv[0], uv[1], uv[2], uv[3], 0.95f, torchLight);
        // Back Face (reverse winding order so visible from behind)
        addFaceWithUV(v, p[1], p[0], p[3], p[2], uv[0], uv[1], uv[2], uv[3], 0.85f, torchLight);
    }

    private void addRotatedCube(List<Float> v, float cx, float cy, float cz, float h, float rotY, BlockType block) {
        float cos = (float) Math.cos(rotY);
        float sin = (float) Math.sin(rotY);

        float hx = (block == BlockType.CACTUS || block == BlockType.CHEST) ? (h * (14.0f / 16.0f)) : h;
        float hy = (block == BlockType.CHEST) ? (h * (14.0f / 16.0f)) : h;
        float hz = (block == BlockType.CACTUS || block == BlockType.CHEST) ? (h * (14.0f / 16.0f)) : h;

        // 8 local corners
        float[][] local = {
                {-hx, -hy, -hz}, { hx, -hy, -hz}, { hx,  hy, -hz}, {-hx,  hy, -hz},
                {-hx, -hy,  hz}, { hx, -hy,  hz}, { hx,  hy,  hz}, {-hx,  hy,  hz}
        };

        // Rotated world corners
        float[][] p = new float[8][3];
        for (int i = 0; i < 8; i++) {
            float lx = local[i][0];
            float ly = local[i][1];
            float lz = local[i][2];

            p[i][0] = cx + (lx * cos - lz * sin);
            p[i][1] = cy + ly;
            p[i][2] = cz + (lx * sin + lz * cos);
        }

        float torchLight = (block == BlockType.TORCH) ? 1.0f : 0.0f;

        if (block == BlockType.CACTUS) {
            int topTexId = block.getTexture(BlockType.Face.TOP);
            float[] uvTop = TextureAtlas.getUVs(topTexId);
            float px = (uvTop[2] - uvTop[0]) / 16.0f;
            float py = (uvTop[3] - uvTop[1]) / 16.0f;
            float uTop0 = uvTop[0] + 1.0f * px;
            float uTop1 = uvTop[0] + 15.0f * px;
            float vTop0 = uvTop[1] + 1.0f * py;
            float vTop1 = uvTop[1] + 15.0f * py;

            int sideTexId = block.getTexture(BlockType.Face.NORTH);
            float[] uvSide = TextureAtlas.getUVs(sideTexId);
            float sx = (uvSide[2] - uvSide[0]) / 16.0f;
            float uSide0 = uvSide[0] + 1.0f * sx;
            float uSide1 = uvSide[0] + 15.0f * sx;
            float vSide0 = uvSide[1];
            float vSide1 = uvSide[3];

            // Top Face
            addFaceWithUV(v, p[3], p[2], p[6], p[7], uTop0, vTop0, uTop1, vTop1, 1.0f, torchLight);
            // Bottom Face
            addFaceWithUV(v, p[4], p[5], p[1], p[0], uTop0, vTop0, uTop1, vTop1, 0.5f, torchLight);
            // North Face
            addFaceWithUV(v, p[0], p[1], p[2], p[3], uSide0, vSide0, uSide1, vSide1, 0.75f, torchLight);
            // South Face
            addFaceWithUV(v, p[5], p[4], p[7], p[6], uSide0, vSide0, uSide1, vSide1, 0.75f, torchLight);
            // West Face
            addFaceWithUV(v, p[4], p[0], p[3], p[7], uSide0, vSide0, uSide1, vSide1, 0.85f, torchLight);
            // East Face
            addFaceWithUV(v, p[1], p[5], p[6], p[2], uSide0, vSide0, uSide1, vSide1, 0.85f, torchLight);
        } else {
            // Top Face
            addFace(v, p[3], p[2], p[6], p[7], block.getTexture(BlockType.Face.TOP), 1.0f, torchLight);
            // Bottom Face
            addFace(v, p[4], p[5], p[1], p[0], block.getTexture(BlockType.Face.BOTTOM), 0.5f, torchLight);
            // North Face
            addFace(v, p[0], p[1], p[2], p[3], block.getTexture(BlockType.Face.NORTH), 0.75f, torchLight);
            // South Face
            addFace(v, p[5], p[4], p[7], p[6], block.getTexture(BlockType.Face.SOUTH), 0.75f, torchLight);
            // West Face
            addFace(v, p[4], p[0], p[3], p[7], block.getTexture(BlockType.Face.WEST), 0.85f, torchLight);
            // East Face
            addFace(v, p[1], p[5], p[6], p[2], block.getTexture(BlockType.Face.EAST), 0.85f, torchLight);
        }
    }

    private void addFace(List<Float> v, float[] p0, float[] p1, float[] p2, float[] p3, int tileId, float light, float torchLight) {
        float[] uv = TextureAtlas.getUVs(tileId);
        addFaceWithUV(v, p0, p1, p2, p3, uv[0], uv[1], uv[2], uv[3], light, torchLight);
    }

    private void addFaceWithUV(List<Float> v, float[] p0, float[] p1, float[] p2, float[] p3,
                               float u0, float v0, float u1, float v1, float light, float torchLight) {
        // Triangle 1: p0, p1, p2
        addVertex(v, p0[0], p0[1], p0[2], u0, v1, light, torchLight);
        addVertex(v, p1[0], p1[1], p1[2], u1, v1, light, torchLight);
        addVertex(v, p2[0], p2[1], p2[2], u1, v0, light, torchLight);

        // Triangle 2: p0, p2, p3
        addVertex(v, p0[0], p0[1], p0[2], u0, v1, light, torchLight);
        addVertex(v, p2[0], p2[1], p2[2], u1, v0, light, torchLight);
        addVertex(v, p3[0], p3[1], p3[2], u0, v0, light, torchLight);
    }

    private void addVertex(List<Float> v, float x, float y, float z, float u, float valV, float light, float torchLight) {
        v.add(x);
        v.add(y);
        v.add(z);
        v.add(u);
        v.add(valV);
        v.add(light);
        v.add(torchLight);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
