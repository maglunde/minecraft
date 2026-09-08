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

        // Position (3 floats), UV (2 floats), Light (1 float) = 6 floats
        int stride = 6 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
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

            // Transform and add all 6 faces of the cube with rotation around (cx, cy, cz)
            addRotatedCube(vertices, cx, cy, cz, half, rotY, type);
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
        glDrawArrays(GL_TRIANGLES, 0, vertices.size() / 6);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void addRotatedCube(List<Float> v, float cx, float cy, float cz, float h, float rotY, BlockType block) {
        float cos = (float) Math.cos(rotY);
        float sin = (float) Math.sin(rotY);

        // 8 local corners
        float[][] local = {
                {-h, -h, -h}, { h, -h, -h}, { h,  h, -h}, {-h,  h, -h},
                {-h, -h,  h}, { h, -h,  h}, { h,  h,  h}, {-h,  h,  h}
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

        // Top Face
        addFace(v, p[3], p[2], p[6], p[7], block.getTexture(BlockType.Face.TOP), 1.0f);
        // Bottom Face
        addFace(v, p[4], p[5], p[1], p[0], block.getTexture(BlockType.Face.BOTTOM), 0.5f);
        // North Face
        addFace(v, p[0], p[1], p[2], p[3], block.getTexture(BlockType.Face.NORTH), 0.75f);
        // South Face
        addFace(v, p[5], p[4], p[7], p[6], block.getTexture(BlockType.Face.SOUTH), 0.75f);
        // West Face
        addFace(v, p[4], p[0], p[3], p[7], block.getTexture(BlockType.Face.WEST), 0.85f);
        // East Face
        addFace(v, p[1], p[5], p[6], p[2], block.getTexture(BlockType.Face.EAST), 0.85f);
    }

    private void addFace(List<Float> v, float[] p0, float[] p1, float[] p2, float[] p3, int tileId, float light) {
        float[] uv = TextureAtlas.getUVs(tileId);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        // Triangle 1: p0, p1, p2
        addVertex(v, p0[0], p0[1], p0[2], u0, v1, light);
        addVertex(v, p1[0], p1[1], p1[2], u1, v1, light);
        addVertex(v, p2[0], p2[1], p2[2], u1, v0, light);

        // Triangle 2: p0, p2, p3
        addVertex(v, p0[0], p0[1], p0[2], u0, v1, light);
        addVertex(v, p2[0], p2[1], p2[2], u1, v0, light);
        addVertex(v, p3[0], p3[1], p3[2], u0, v0, light);
    }

    private void addVertex(List<Float> v, float x, float y, float z, float u, float valV, float light) {
        v.add(x);
        v.add(y);
        v.add(z);
        v.add(u);
        v.add(valV);
        v.add(light);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
