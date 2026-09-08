package no.minecraft.render;

import no.minecraft.entity.Arrow;
import no.minecraft.entity.Mob;
import no.minecraft.entity.MobType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class MobRenderer {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;

    private static final String VERT_SRC = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec4 aColor;

            uniform mat4 uProjection;
            uniform mat4 uView;

            out vec4 vColor;

            void main() {
                vColor = aColor;
                gl_Position = uProjection * uView * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core
            in vec4 vColor;
            uniform float uSunLight;
            out vec4 FragColor;

            void main() {
                float ambient = 0.25;
                float light = ambient + (1.0 - ambient) * uSunLight;
                FragColor = vec4(vColor.rgb * light, vColor.a);
            }
            """;

    public MobRenderer() {
        this.shader = new Shader(VERT_SRC, FRAG_SRC);
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        int stride = (3 + 4) * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(List<Mob> mobs, List<Arrow> arrows, Matrix4f projection, Matrix4f view, float sunLight) {
        if (mobs.isEmpty() && arrows.isEmpty()) return;

        List<Float> verts = new ArrayList<>();

        for (Mob mob : mobs) {
            if (mob.isDead()) continue;

            float x = mob.getPosition().x;
            float y = mob.getPosition().y;
            float z = mob.getPosition().z;

            // Hurt flash (red tint) or Creeper flash (white flashing)
            float r = 1, g = 1, b = 1;
            boolean hurt = mob.getHurtTimer() > 0;
            boolean creeperFlash = mob.getType() == MobType.CREEPER && mob.isIgnited() && ((int)(mob.getFuseRatio() * 12) % 2 == 1);

            MobType mt = mob.getType();

            if (mt == MobType.ZOMBIE) {
                // Head (Green skin)
                float hr = hurt ? 1.0f : 0.28f;
                float hg = hurt ? 0.2f : 0.55f;
                float hb = hurt ? 0.2f : 0.28f;
                addBox(verts, x - 0.22f, y + 1.4f, z - 0.22f, 0.44f, 0.45f, 0.44f, hr, hg, hb);

                // Body (Blue/Cyan shirt)
                float br = hurt ? 1.0f : 0.05f;
                float bg = hurt ? 0.2f : 0.58f;
                float bb = hurt ? 0.2f : 0.65f;
                addBox(verts, x - 0.24f, y + 0.7f, z - 0.15f, 0.48f, 0.7f, 0.3f, br, bg, bb);

                // Legs (Dark blue)
                float lr = hurt ? 1.0f : 0.15f;
                float lg = hurt ? 0.2f : 0.15f;
                float lb = hurt ? 0.2f : 0.50f;
                addBox(verts, x - 0.22f, y, z - 0.14f, 0.2f, 0.7f, 0.28f, lr, lg, lb);
                addBox(verts, x + 0.02f, y, z - 0.14f, 0.2f, 0.7f, 0.28f, lr, lg, lb);

                // Arms outstretched forward (Zombie classic pose)
                addBox(verts, x - 0.38f, y + 0.95f, z - 0.45f, 0.14f, 0.14f, 0.55f, hr, hg, hb);
                addBox(verts, x + 0.24f, y + 0.95f, z - 0.45f, 0.14f, 0.14f, 0.55f, hr, hg, hb);

            } else if (mt == MobType.CREEPER) {
                float cr = creeperFlash ? 1.0f : (hurt ? 1.0f : 0.18f);
                float cg = creeperFlash ? 1.0f : (hurt ? 0.2f : 0.72f);
                float cb = creeperFlash ? 1.0f : (hurt ? 0.2f : 0.18f);

                // Head
                addBox(verts, x - 0.24f, y + 1.2f, z - 0.24f, 0.48f, 0.48f, 0.48f, cr, cg, cb);
                // Body
                addBox(verts, x - 0.22f, y + 0.45f, z - 0.14f, 0.44f, 0.75f, 0.28f, cr * 0.9f, cg * 0.9f, cb * 0.9f);
                // 4 Feet
                addBox(verts, x - 0.24f, y, z - 0.24f, 0.2f, 0.45f, 0.2f, cr * 0.8f, cg * 0.8f, cb * 0.8f);
                addBox(verts, x + 0.04f, y, z - 0.24f, 0.2f, 0.45f, 0.2f, cr * 0.8f, cg * 0.8f, cb * 0.8f);
                addBox(verts, x - 0.24f, y, z + 0.04f, 0.2f, 0.45f, 0.2f, cr * 0.8f, cg * 0.8f, cb * 0.8f);
                addBox(verts, x + 0.04f, y, z + 0.04f, 0.2f, 0.45f, 0.2f, cr * 0.8f, cg * 0.8f, cb * 0.8f);

            } else if (mt == MobType.SKELETON) {
                float sr = hurt ? 1.0f : 0.82f;
                float sg = hurt ? 0.2f : 0.82f;
                float sb = hurt ? 0.2f : 0.82f;

                // Skull
                addBox(verts, x - 0.22f, y + 1.4f, z - 0.22f, 0.44f, 0.45f, 0.44f, sr, sg, sb);
                // Ribcage
                addBox(verts, x - 0.20f, y + 0.7f, z - 0.12f, 0.40f, 0.7f, 0.24f, sr * 0.9f, sg * 0.9f, sb * 0.9f);
                // Legs
                addBox(verts, x - 0.16f, y, z - 0.08f, 0.12f, 0.7f, 0.16f, sr * 0.85f, sg * 0.85f, sb * 0.85f);
                addBox(verts, x + 0.04f, y, z - 0.08f, 0.12f, 0.7f, 0.16f, sr * 0.85f, sg * 0.85f, sb * 0.85f);
                // Bow in hand
                addBox(verts, x + 0.22f, y + 0.8f, z - 0.3f, 0.08f, 0.5f, 0.08f, 0.45f, 0.3f, 0.15f);

            } else if (mt == MobType.SPIDER) {
                float spr = hurt ? 1.0f : 0.18f;
                float spg = hurt ? 0.2f : 0.14f;
                float spb = hurt ? 0.2f : 0.14f;

                // Head
                addBox(verts, x - 0.22f, y + 0.15f, z - 0.48f, 0.44f, 0.35f, 0.4f, spr * 1.1f, spg * 1.1f, spb * 1.1f);
                // Glowing Red eyes
                addBox(verts, x - 0.16f, y + 0.26f, z - 0.50f, 0.08f, 0.08f, 0.05f, 0.9f, 0.05f, 0.05f);
                addBox(verts, x + 0.08f, y + 0.26f, z - 0.50f, 0.08f, 0.08f, 0.05f, 0.9f, 0.05f, 0.05f);

                // Body (Abdomen)
                addBox(verts, x - 0.35f, y + 0.2f, z - 0.1f, 0.7f, 0.5f, 0.8f, spr, spg, spb);

                // 8 Spider Legs sprawled out wide
                for (int l = 0; l < 4; l++) {
                    float lz = z - 0.3f + l * 0.22f;
                    // Left leg
                    addBox(verts, x - 0.75f, y + 0.15f, lz, 0.45f, 0.08f, 0.08f, 0.12f, 0.10f, 0.10f);
                    // Right leg
                    addBox(verts, x + 0.30f, y + 0.15f, lz, 0.45f, 0.08f, 0.08f, 0.12f, 0.10f, 0.10f);
                }
            }
        }

        // Render Arrows
        for (Arrow a : arrows) {
            if (a.isDead()) continue;
            float ax = a.getPosition().x;
            float ay = a.getPosition().y;
            float az = a.getPosition().z;
            addBox(verts, ax - 0.03f, ay - 0.03f, az - 0.25f, 0.06f, 0.06f, 0.5f, 0.9f, 0.85f, 0.75f);
        }

        if (verts.isEmpty()) return;

        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uView", view);
        shader.setUniform("uSunLight", sunLight);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(verts.size());
        for (float f : verts) buffer.put(f);
        buffer.flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);

        glDrawArrays(GL_TRIANGLES, 0, verts.size() / 7);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();
    }

    private void addBox(List<Float> v, float x, float y, float z, float w, float h, float d, float r, float g, float b) {
        float x1 = x + w;
        float y1 = y + h;
        float z1 = z + d;

        // Top face
        float topL = 1.0f;
        addQuad(v, x, y1, z1, x1, y1, z1, x1, y1, z, x, y1, z, r * topL, g * topL, b * topL);
        // Bottom face
        float botL = 0.55f;
        addQuad(v, x, y, z, x1, y, z, x1, y, z1, x, y, z1, r * botL, g * botL, b * botL);
        // North face (-Z)
        float nL = 0.75f;
        addQuad(v, x1, y, z, x, y, z, x, y1, z, x1, y1, z, r * nL, g * nL, b * nL);
        // South face (+Z)
        float sL = 0.75f;
        addQuad(v, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1, r * sL, g * sL, b * sL);
        // West face (-X)
        float wL = 0.65f;
        addQuad(v, x, y, z, x, y, z1, x, y1, z1, x, y1, z, r * wL, g * wL, b * wL);
        // East face (+X)
        float eL = 0.65f;
        addQuad(v, x1, y, z1, x1, y, z, x1, y1, z, x1, y1, z1, r * eL, g * eL, b * eL);
    }

    private void addQuad(List<Float> v, float x1, float y1, float z1, float x2, float y2, float z2,
                         float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b) {
        addVertex(v, x1, y1, z1, r, g, b, 1.0f);
        addVertex(v, x2, y2, z2, r, g, b, 1.0f);
        addVertex(v, x3, y3, z3, r, g, b, 1.0f);

        addVertex(v, x1, y1, z1, r, g, b, 1.0f);
        addVertex(v, x3, y3, z3, r, g, b, 1.0f);
        addVertex(v, x4, y4, z4, r, g, b, 1.0f);
    }

    private void addVertex(List<Float> v, float x, float y, float z, float r, float g, float b, float a) {
        v.add(x); v.add(y); v.add(z);
        v.add(r); v.add(g); v.add(b); v.add(a);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
