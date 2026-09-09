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
        render(mobs, arrows, java.util.Collections.emptyList(), projection, view, sunLight);
    }

    public void render(List<Mob> mobs, List<Arrow> arrows, List<no.minecraft.entity.Boat> boats, Matrix4f projection, Matrix4f view, float sunLight) {
        List<ParticleManager.Particle> particles = ParticleManager.getInstance().getParticles();
        if (mobs.isEmpty() && arrows.isEmpty() && (boats == null || boats.isEmpty()) && particles.isEmpty()) return;

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

                float yaw = mob.getYaw();
                float scale = 1.0f + (mob.isIgnited() ? mob.getFuseRatio() * 0.22f : 0.0f);

                // Head
                float headW = 0.48f * scale;
                float headH = 0.48f * scale;
                float headD = 0.48f * scale;
                float headY = 1.20f * scale;
                addRotatedBox(verts, x, y, z, 0, headY, 0, headW, headH, headD, yaw, cr, cg, cb);

                // Iconic Creeper Face (front face of head)
                float fr = creeperFlash ? 1.0f : 0.08f;
                float fg = creeperFlash ? 1.0f : 0.08f;
                float fb = creeperFlash ? 1.0f : 0.08f;
                float faceZ = (headD * 0.5f) + 0.005f;
                float faceD = 0.015f * scale;

                // Eyes (black rectangles)
                addRotatedBox(verts, x, y, z, -0.10f * scale, (headY + 0.26f * scale), faceZ, 0.09f * scale, 0.09f * scale, faceD, yaw, fr, fg, fb);
                addRotatedBox(verts, x, y, z, 0.10f * scale, (headY + 0.26f * scale), faceZ, 0.09f * scale, 0.09f * scale, faceD, yaw, fr, fg, fb);

                // Nose bridge
                addRotatedBox(verts, x, y, z, 0, (headY + 0.16f * scale), faceZ, 0.08f * scale, 0.12f * scale, faceD, yaw, fr, fg, fb);

                // Mouth upper horizontal bar
                addRotatedBox(verts, x, y, z, 0, (headY + 0.10f * scale), faceZ, 0.22f * scale, 0.06f * scale, faceD, yaw, fr, fg, fb);

                // Mouth outer corners (frown dropping down)
                addRotatedBox(verts, x, y, z, -0.10f * scale, (headY + 0.04f * scale), faceZ, 0.08f * scale, 0.08f * scale, faceD, yaw, fr, fg, fb);
                addRotatedBox(verts, x, y, z, 0.10f * scale, (headY + 0.04f * scale), faceZ, 0.08f * scale, 0.08f * scale, faceD, yaw, fr, fg, fb);

                // Body (Torso)
                float bodyW = 0.44f * scale;
                float bodyH = 0.75f * scale;
                float bodyD = 0.28f * scale;
                addRotatedBox(verts, x, y, z, 0, 0.45f * scale, 0, bodyW, bodyH, bodyD, yaw, cr * 0.9f, cg * 0.9f, cb * 0.9f);

                // 4 Feet with leg swing animation
                boolean isWalking = (mob.getVelocity().x * mob.getVelocity().x + mob.getVelocity().z * mob.getVelocity().z) > 0.002f;
                float legSwing = isWalking ? (float) Math.sin(mob.getWalkTime() * 8.0f) * 0.10f : 0.0f;
                float legW = 0.19f * scale;
                float legH = 0.45f * scale;
                float legD = 0.19f * scale;

                // Front-left
                addRotatedBox(verts, x, y, z, -0.12f * scale, 0, (0.13f + legSwing) * scale, legW, legH, legD, yaw, cr * 0.8f, cg * 0.8f, cb * 0.8f);
                // Front-right
                addRotatedBox(verts, x, y, z, 0.12f * scale, 0, (0.13f - legSwing) * scale, legW, legH, legD, yaw, cr * 0.8f, cg * 0.8f, cb * 0.8f);
                // Back-left
                addRotatedBox(verts, x, y, z, -0.12f * scale, 0, (-0.13f - legSwing) * scale, legW, legH, legD, yaw, cr * 0.8f, cg * 0.8f, cb * 0.8f);
                // Back-right
                addRotatedBox(verts, x, y, z, 0.12f * scale, 0, (-0.13f + legSwing) * scale, legW, legH, legD, yaw, cr * 0.8f, cg * 0.8f, cb * 0.8f);

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
            } else if (mt == MobType.BLAZE) {
                float br = hurt ? 1.0f : 1.0f;
                float bg = hurt ? 0.2f : 0.65f;
                float bb = hurt ? 0.2f : 0.08f;

                // Blaze Head
                addBox(verts, x - 0.20f, y + 1.25f, z - 0.20f, 0.40f, 0.40f, 0.40f, br, bg, bb);
                // Eyes
                addBox(verts, x - 0.15f, y + 1.42f, z - 0.22f, 0.08f, 0.06f, 0.04f, 1.0f, 1.0f, 0.9f);
                addBox(verts, x + 0.07f, y + 1.42f, z - 0.22f, 0.08f, 0.06f, 0.04f, 1.0f, 1.0f, 0.9f);

                // Orbiting Blaze Rods (upper, middle, lower layers)
                float rodW = 0.08f, rodH = 0.38f;
                // Layer 1 (4 rods)
                for (int rIdx = 0; rIdx < 4; rIdx++) {
                    double angle = (System.currentTimeMillis() * 0.003) + (rIdx * Math.PI / 2.0);
                    float rx = x + (float) Math.cos(angle) * 0.38f;
                    float rz = z + (float) Math.sin(angle) * 0.38f;
                    addBox(verts, rx - rodW / 2, y + 0.8f, rz - rodW / 2, rodW, rodH, rodW, 1.0f, 0.75f, 0.12f);
                }
                // Layer 2 (4 rods)
                for (int rIdx = 0; rIdx < 4; rIdx++) {
                    double angle = -(System.currentTimeMillis() * 0.003) + (rIdx * Math.PI / 2.0) + (Math.PI / 4.0);
                    float rx = x + (float) Math.cos(angle) * 0.46f;
                    float rz = z + (float) Math.sin(angle) * 0.46f;
                    addBox(verts, rx - rodW / 2, y + 0.4f, rz - rodW / 2, rodW, rodH, rodW, 0.95f, 0.55f, 0.05f);
                }
                // Layer 3 (4 rods)
                for (int rIdx = 0; rIdx < 4; rIdx++) {
                    double angle = (System.currentTimeMillis() * 0.0035) + (rIdx * Math.PI / 2.0);
                    float rx = x + (float) Math.cos(angle) * 0.30f;
                    float rz = z + (float) Math.sin(angle) * 0.30f;
                    addBox(verts, rx - rodW / 2, y + 0.05f, rz - rodW / 2, rodW, rodH, rodW, 1.0f, 0.45f, 0.02f);
                }

            } else if (mt == MobType.ENDERMAN) {
                float er = hurt ? 1.0f : 0.08f;
                float eg = hurt ? 0.2f : 0.08f;
                float eb = hurt ? 0.2f : 0.08f;

                boolean aggro = mob.isAggressive();
                float shake = aggro ? (float) Math.sin(System.currentTimeMillis() * 0.05) * 0.02f : 0.0f;
                float headY = aggro ? 2.30f : 2.25f;

                // Long slender legs
                addBox(verts, x - 0.12f, y, z - 0.05f, 0.08f, 1.5f, 0.08f, er, eg, eb);
                addBox(verts, x + 0.04f, y, z - 0.05f, 0.08f, 1.5f, 0.08f, er, eg, eb);
                // Torso
                addBox(verts, x - 0.18f, y + 1.5f, z - 0.08f, 0.36f, 0.75f, 0.16f, er, eg, eb);
                // Long slender arms
                addBox(verts, x - 0.28f, y + 0.6f, z - 0.05f, 0.08f, 1.65f, 0.08f, er, eg, eb);
                addBox(verts, x + 0.20f, y + 0.6f, z - 0.05f, 0.08f, 1.65f, 0.08f, er, eg, eb);
                // Head
                addBox(verts, x - 0.20f + shake, y + headY, z - 0.20f, 0.40f, 0.35f, 0.40f, er, eg, eb);
                if (aggro) {
                    // Open lower jaw
                    addBox(verts, x - 0.18f + shake, y + 2.15f, z - 0.18f, 0.36f, 0.10f, 0.36f, er, eg, eb);
                }
                // Glowing Purple eyes (brighter when angry)
                float eyeR = aggro ? 1.0f : 0.85f;
                float eyeG = aggro ? 0.05f : 0.15f;
                float eyeB = aggro ? 1.0f : 0.95f;
                addBox(verts, x - 0.15f + shake, y + headY + 0.17f, z - 0.21f, 0.09f, 0.06f, 0.03f, eyeR, eyeG, eyeB);
                addBox(verts, x + 0.06f + shake, y + headY + 0.17f, z - 0.21f, 0.09f, 0.06f, 0.03f, eyeR, eyeG, eyeB);

            } else if (mt == MobType.END_CRYSTAL) {
                // Glass outer shell
                float time = (System.currentTimeMillis() % 10000) * 0.001f;
                float pulse = 0.85f + 0.15f * (float) Math.sin(time * 5.0f);
                addBox(verts, x - 0.35f, y + 0.2f, z - 0.35f, 0.7f, 0.7f, 0.7f, 0.8f * pulse, 0.3f, 0.9f * pulse);
                // Inner core
                addBox(verts, x - 0.20f, y + 0.35f, z - 0.20f, 0.4f, 0.4f, 0.4f, 1.0f, 0.85f, 1.0f);
                // Base stand (Obsidian/Bedrock pedestal)
                addBox(verts, x - 0.45f, y, z - 0.45f, 0.9f, 0.2f, 0.9f, 0.15f, 0.15f, 0.18f);

            } else if (mt == MobType.ENDER_DRAGON) {
                float dr = hurt ? 1.0f : 0.12f;
                float dg = hurt ? 0.2f : 0.12f;
                float db = hurt ? 0.2f : 0.12f;

                // Dragon Body
                addBox(verts, x - 0.65f, y + 0.5f, z - 1.2f, 1.3f, 0.9f, 2.4f, dr, dg, db);
                // Dragon Neck & Head
                addBox(verts, x - 0.35f, y + 0.8f, z + 1.2f, 0.7f, 0.7f, 0.9f, dr * 1.2f, dg * 1.2f, db * 1.2f);
                addBox(verts, x - 0.30f, y + 1.0f, z + 2.0f, 0.6f, 0.55f, 0.7f, dr, dg, db);
                // Purple Dragon Eyes
                addBox(verts, x - 0.32f, y + 1.3f, z + 2.2f, 0.08f, 0.08f, 0.15f, 0.95f, 0.2f, 0.95f);
                addBox(verts, x + 0.24f, y + 1.3f, z + 2.2f, 0.08f, 0.08f, 0.15f, 0.95f, 0.2f, 0.95f);
                // Horns
                addBox(verts, x - 0.25f, y + 1.55f, z + 1.9f, 0.08f, 0.35f, 0.08f, 0.35f, 0.35f, 0.4f);
                addBox(verts, x + 0.17f, y + 1.55f, z + 1.9f, 0.08f, 0.35f, 0.08f, 0.35f, 0.35f, 0.4f);
                // Dragon Tail (segments)
                addBox(verts, x - 0.25f, y + 0.7f, z - 2.0f, 0.5f, 0.5f, 0.9f, dr, dg, db);
                addBox(verts, x - 0.18f, y + 0.75f, z - 2.8f, 0.36f, 0.36f, 0.9f, dr, dg, db);
                addBox(verts, x - 0.12f, y + 0.8f, z - 3.6f, 0.24f, 0.24f, 0.9f, dr, dg, db);

                // Flapping Wings
                float wingFlap = (float) Math.sin(System.currentTimeMillis() * 0.008f) * 0.4f;
                // Left Wing
                addBox(verts, x - 2.8f, y + 0.9f + wingFlap, z - 0.8f, 2.2f, 0.08f, 1.8f, 0.22f, 0.18f, 0.24f);
                // Right Wing
                addBox(verts, x + 0.65f, y + 0.9f - wingFlap, z - 0.8f, 2.2f, 0.08f, 1.8f, 0.22f, 0.18f, 0.24f);

            } else if (mt == MobType.PIG) {
                float pr = hurt ? 1.0f : 0.95f;
                float pg = hurt ? 0.3f : 0.65f;
                float pb = hurt ? 0.3f : 0.65f;

                // Body
                addBox(verts, x - 0.28f, y + 0.32f, z - 0.42f, 0.56f, 0.48f, 0.84f, pr, pg, pb);
                // Head
                addBox(verts, x - 0.22f, y + 0.40f, z + 0.36f, 0.44f, 0.44f, 0.38f, pr, pg, pb);
                // Snout
                addBox(verts, x - 0.11f, y + 0.46f, z + 0.72f, 0.22f, 0.14f, 0.08f, pr * 0.92f, pg * 0.85f, pb * 0.85f);
                // Eyes
                addBox(verts, x - 0.18f, y + 0.66f, z + 0.72f, 0.06f, 0.06f, 0.02f, 0.1f, 0.1f, 0.1f);
                addBox(verts, x + 0.12f, y + 0.66f, z + 0.72f, 0.06f, 0.06f, 0.02f, 0.1f, 0.1f, 0.1f);
                // 4 Legs
                addBox(verts, x - 0.25f, y, z - 0.36f, 0.18f, 0.32f, 0.18f, pr * 0.9f, pg * 0.9f, pb * 0.9f);
                addBox(verts, x + 0.07f, y, z - 0.36f, 0.18f, 0.32f, 0.18f, pr * 0.9f, pg * 0.9f, pb * 0.9f);
                addBox(verts, x - 0.25f, y, z + 0.18f, 0.18f, 0.32f, 0.18f, pr * 0.9f, pg * 0.9f, pb * 0.9f);
                addBox(verts, x + 0.07f, y, z + 0.18f, 0.18f, 0.32f, 0.18f, pr * 0.9f, pg * 0.9f, pb * 0.9f);

            } else if (mt == MobType.COW) {
                float cr = hurt ? 1.0f : 0.40f;
                float cg = hurt ? 0.3f : 0.26f;
                float cb = hurt ? 0.3f : 0.18f;

                // Body
                addBox(verts, x - 0.32f, y + 0.55f, z - 0.50f, 0.64f, 0.65f, 1.00f, cr, cg, cb);
                // White patch on body
                addBox(verts, x - 0.33f, y + 0.65f, z - 0.20f, 0.66f, 0.40f, 0.45f, 0.92f, 0.92f, 0.92f);
                // Head
                addBox(verts, x - 0.21f, y + 0.85f, z + 0.45f, 0.42f, 0.42f, 0.38f, cr, cg, cb);
                // Muzzle (white)
                addBox(verts, x - 0.14f, y + 0.88f, z + 0.78f, 0.28f, 0.18f, 0.08f, 0.88f, 0.88f, 0.85f);
                // Horns (grey)
                addBox(verts, x - 0.28f, y + 1.25f, z + 0.50f, 0.08f, 0.15f, 0.08f, 0.75f, 0.75f, 0.75f);
                addBox(verts, x + 0.20f, y + 1.25f, z + 0.50f, 0.08f, 0.15f, 0.08f, 0.75f, 0.75f, 0.75f);
                // 4 Legs
                addBox(verts, x - 0.28f, y, z - 0.42f, 0.18f, 0.55f, 0.18f, cr * 0.85f, cg * 0.85f, cb * 0.85f);
                addBox(verts, x + 0.10f, y, z - 0.42f, 0.18f, 0.55f, 0.18f, cr * 0.85f, cg * 0.85f, cb * 0.85f);
                addBox(verts, x - 0.28f, y, z + 0.24f, 0.18f, 0.55f, 0.18f, cr * 0.85f, cg * 0.85f, cb * 0.85f);
                addBox(verts, x + 0.10f, y, z + 0.24f, 0.18f, 0.55f, 0.18f, cr * 0.85f, cg * 0.85f, cb * 0.85f);

            } else if (mt == MobType.SHEEP) {
                float sr = hurt ? 1.0f : 0.92f;
                float sg = hurt ? 0.3f : 0.92f;
                float sb = hurt ? 0.3f : 0.92f;

                // Fleece Body
                addBox(verts, x - 0.34f, y + 0.55f, z - 0.48f, 0.68f, 0.65f, 0.96f, sr, sg, sb);
                // Head (tan skin)
                addBox(verts, x - 0.18f, y + 0.75f, z + 0.45f, 0.36f, 0.38f, 0.36f, 0.85f, 0.74f, 0.68f);
                // 4 Legs (tan skin)
                addBox(verts, x - 0.26f, y, z - 0.40f, 0.16f, 0.55f, 0.16f, 0.85f, 0.74f, 0.68f);
                addBox(verts, x + 0.10f, y, z - 0.40f, 0.16f, 0.55f, 0.16f, 0.85f, 0.74f, 0.68f);
                addBox(verts, x - 0.26f, y, z + 0.24f, 0.16f, 0.55f, 0.16f, 0.85f, 0.74f, 0.68f);
                addBox(verts, x + 0.10f, y, z + 0.24f, 0.16f, 0.55f, 0.16f, 0.85f, 0.74f, 0.68f);

            } else if (mt == MobType.CHICKEN) {
                float chr = hurt ? 1.0f : 0.95f;
                float chg = hurt ? 0.3f : 0.95f;
                float chb = hurt ? 0.3f : 0.95f;

                // Body
                addBox(verts, x - 0.18f, y + 0.22f, z - 0.22f, 0.36f, 0.28f, 0.44f, chr, chg, chb);
                // Head
                addBox(verts, x - 0.11f, y + 0.38f, z + 0.16f, 0.22f, 0.28f, 0.20f, chr, chg, chb);
                // Yellow Beak
                addBox(verts, x - 0.05f, y + 0.50f, z + 0.34f, 0.10f, 0.08f, 0.10f, 0.95f, 0.75f, 0.10f);
                // Red Wattle
                addBox(verts, x - 0.03f, y + 0.42f, z + 0.32f, 0.06f, 0.10f, 0.06f, 0.90f, 0.15f, 0.15f);
                // 2 Yellow Legs
                addBox(verts, x - 0.10f, y, z - 0.04f, 0.06f, 0.22f, 0.06f, 0.95f, 0.75f, 0.10f);
                addBox(verts, x + 0.04f, y, z - 0.04f, 0.06f, 0.22f, 0.06f, 0.95f, 0.75f, 0.10f);
            }

            if (mob.isOnFire()) {
                // Flickering fire flames around the mob
                float flameAnim = (float) Math.sin(System.currentTimeMillis() * 0.02) * 0.06f;
                float fH = mob.getType().getHeight();
                float fW = mob.getType().getWidth() + 0.16f;
                // Outer fire orange quad/box
                addBox(verts, x - fW * 0.5f, y, z - fW * 0.5f, fW, fH * 0.85f + flameAnim, fW, 1.0f, 0.45f, 0.05f);
                // Inner bright yellow flame
                addBox(verts, x - fW * 0.35f, y + 0.1f, z - fW * 0.35f, fW * 0.7f, fH * 0.65f - flameAnim, fW * 0.7f, 1.0f, 0.85f, 0.1f);
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

        // Render Healing Beams from End Crystals to Ender Dragon
        Mob dragon = null;
        for (Mob m : mobs) {
            if (m.getType() == MobType.ENDER_DRAGON && !m.isDead()) {
                dragon = m;
                break;
            }
        }
        if (dragon != null) {
            for (Mob m : mobs) {
                if (m.getType() == MobType.END_CRYSTAL && !m.isDead()) {
                    float dist = m.getPosition().distance(dragon.getPosition());
                    if (dist < 40.0f) {
                        // Render segmented beam
                        int segments = (int) (dist * 1.5f);
                        for (int s = 0; s < segments; s++) {
                            float t = (float) s / segments;
                            float bx = m.getPosition().x + (dragon.getPosition().x - m.getPosition().x) * t;
                            float by = (m.getPosition().y + 0.6f) + (dragon.getPosition().y + 0.8f - (m.getPosition().y + 0.6f)) * t;
                            float bz = m.getPosition().z + (dragon.getPosition().z - m.getPosition().z) * t;
                            addBox(verts, bx - 0.06f, by - 0.06f, bz - 0.06f, 0.12f, 0.12f, 0.12f, 0.9f, 0.2f, 0.95f);
                        }
                    }
                }
            }
        }

        if (boats != null) {
            for (no.minecraft.entity.Boat boat : boats) {
                if (boat.isDead()) continue;
                float bx = boat.getPosition().x;
                float by = boat.getPosition().y;
                float bz = boat.getPosition().z;
                float yaw = boat.getYaw();

                float wr = 0.58f, wg = 0.38f, wb = 0.22f; // Oak wood plank color
                float rr = 0.50f, rg = 0.32f, rb = 0.18f; // Oak wood rim color
                float sr = 0.45f, sg = 0.28f, sb = 0.15f; // Seat bench color

                // 1. Bottom floor
                addRotatedBox(verts, bx, by, bz, 0, 0, 0, 1.1f, 0.08f, 1.5f, yaw, wr, wg, wb);
                // 2. Left rim
                addRotatedBox(verts, bx, by, bz, -0.52f, 0.08f, 0, 0.10f, 0.38f, 1.5f, yaw, rr, rg, rb);
                // 3. Right rim
                addRotatedBox(verts, bx, by, bz, 0.52f, 0.08f, 0, 0.10f, 0.38f, 1.5f, yaw, rr, rg, rb);
                // 4. Back rim
                addRotatedBox(verts, bx, by, bz, 0, 0.08f, -0.72f, 1.14f, 0.38f, 0.10f, yaw, rr, rg, rb);
                // 5. Front bow rim
                addRotatedBox(verts, bx, by, bz, 0, 0.08f, 0.72f, 1.14f, 0.38f, 0.10f, yaw, rr, rg, rb);
                // 6. Center seat bench
                addRotatedBox(verts, bx, by, bz, 0, 0.15f, 0, 0.94f, 0.08f, 0.24f, yaw, sr, sg, sb);
                // 7. Oars
                float or = 0.65f, og = 0.48f, ob = 0.28f;
                addRotatedBox(verts, bx, by, bz, -0.62f, 0.24f, 0.1f, 0.06f, 0.06f, 0.7f, yaw + 25.0f, or, og, ob);
                addRotatedBox(verts, bx, by, bz, 0.62f, 0.24f, 0.1f, 0.06f, 0.06f, 0.7f, yaw - 25.0f, or, og, ob);
            }
        }

        // 8. 3D Particles (e.g. Critical Hit sparkles)
        for (ParticleManager.Particle p : particles) {
            float alpha = Math.max(0.0f, 1.0f - (p.age / p.maxLifetime));
            float s = p.size * (0.6f + alpha * 0.4f);
            float px = p.pos.x;
            float py = p.pos.y;
            float pz = p.pos.z;
            addBox(verts, px - s * 0.5f, py - s * 0.5f, pz - s * 0.5f, s, s, s, p.r, p.g, p.b);
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

    private void addRotatedBox(List<Float> v, float cx, float cy, float cz,
                               float lx, float ly, float lz,
                               float w, float h, float d,
                               float yaw, float r, float g, float b) {
        float rad = (float) Math.toRadians(yaw);
        float fwdX = (float) Math.cos(rad);
        float fwdZ = (float) Math.sin(rad);
        float rightX = -fwdZ;
        float rightZ = fwdX;

        float x0 = lx - w * 0.5f;
        float x1 = lx + w * 0.5f;
        float y0 = cy + ly;
        float y1 = cy + ly + h;
        float z0 = lz - d * 0.5f;
        float z1 = lz + d * 0.5f;

        float p00x = cx + x0 * rightX + z0 * fwdX; float p00z = cz + x0 * rightZ + z0 * fwdZ;
        float p10x = cx + x1 * rightX + z0 * fwdX; float p10z = cz + x1 * rightZ + z0 * fwdZ;
        float p11x = cx + x1 * rightX + z1 * fwdX; float p11z = cz + x1 * rightZ + z1 * fwdZ;
        float p01x = cx + x0 * rightX + z1 * fwdX; float p01z = cz + x0 * rightZ + z1 * fwdZ;

        // Top face
        float topL = 1.0f;
        addQuad(v, p01x, y1, p01z, p11x, y1, p11z, p10x, y1, p10z, p00x, y1, p00z, r * topL, g * topL, b * topL);
        // Bottom face
        float botL = 0.55f;
        addQuad(v, p00x, y0, p00z, p10x, y0, p10z, p11x, y0, p11z, p01x, y0, p01z, r * botL, g * botL, b * botL);
        // North face
        float nL = 0.75f;
        addQuad(v, p10x, y0, p10z, p00x, y0, p00z, p00x, y1, p00z, p10x, y1, p10z, r * nL, g * nL, b * nL);
        // South face
        float sL = 0.75f;
        addQuad(v, p01x, y0, p01z, p11x, y0, p11z, p11x, y1, p11z, p01x, y1, p01z, r * sL, g * sL, b * sL);
        // West face
        float wL = 0.65f;
        addQuad(v, p00x, y0, p00z, p01x, y0, p01z, p01x, y1, p01z, p00x, y1, p00z, r * wL, g * wL, b * wL);
        // East face
        float eL = 0.65f;
        addQuad(v, p11x, y0, p11z, p10x, y0, p10z, p10x, y1, p10z, p11x, y1, p11z, r * eL, g * eL, b * eL);
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
