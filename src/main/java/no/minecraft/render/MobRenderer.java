package no.minecraft.render;

import no.minecraft.entity.Arrow;
import no.minecraft.entity.Boat;
import no.minecraft.entity.EnderPearl;
import no.minecraft.entity.EyeOfEnder;
import no.minecraft.entity.Mob;
import no.minecraft.entity.MobType;
import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class MobRenderer {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;
    private final MobTextureManager textureManager;
    private final Matrix4f arrowMat = new Matrix4f();

    private static final String VERT_SRC = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aColor;
            layout (location = 3) in float aLight;

            uniform mat4 uProjection;
            uniform mat4 uView;

            out vec2 vTexCoord;
            out vec4 vColor;
            out float vLight;

            void main() {
                vTexCoord = aTexCoord;
                vColor = aColor;
                vLight = aLight;
                gl_Position = uProjection * uView * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core
            in vec2 vTexCoord;
            in vec4 vColor;
            in float vLight;

            uniform sampler2D uTexture;
            uniform int uUseTexture;

            out vec4 FragColor;

            void main() {
                vec4 baseColor;
                if (uUseTexture == 1) {
                    vec4 tex = texture(uTexture, vTexCoord);
                    if (tex.a < 0.1) discard;
                    baseColor = tex * vColor;
                } else {
                    baseColor = vColor;
                }

                FragColor = vec4(baseColor.rgb * vLight, baseColor.a);
            }
            """;

    public MobRenderer() {
        this.shader = new Shader(VERT_SRC, FRAG_SRC);
        this.textureManager = MobTextureManager.getInstance();
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Pos: 3, UV: 2, Color: 4, Light: 1 -> Stride: 10 floats (40 bytes)
        int stride = 10 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 9 * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(World world, List<Mob> mobs, List<Arrow> arrows, Matrix4f projection, Matrix4f view, float sunLight) {
        render(world, mobs, arrows, java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptyList(), projection, view, sunLight);
    }

    public void render(World world, List<Mob> mobs, List<Arrow> arrows, List<Boat> boats, Matrix4f projection, Matrix4f view, float sunLight) {
        render(world, mobs, arrows, boats, java.util.Collections.emptyList(), java.util.Collections.emptyList(), projection, view, sunLight);
    }

    public void render(World world, List<Mob> mobs, List<Arrow> arrows, List<Boat> boats,
                       List<EnderPearl> pearls, List<EyeOfEnder> eyes,
                       Matrix4f projection, Matrix4f view, float sunLight) {
        List<ParticleManager.Particle> particles = ParticleManager.getInstance().getParticles();
        if (mobs.isEmpty() && arrows.isEmpty() && (boats == null || boats.isEmpty())
                && (pearls == null || pearls.isEmpty()) && (eyes == null || eyes.isEmpty()) && particles.isEmpty()) return;

        Map<MobType, List<Float>> mobBatches = new EnumMap<>(MobType.class);
        for (MobType type : MobType.values()) {
            mobBatches.put(type, new ArrayList<>());
        }
        List<Float> untexturedVerts = new ArrayList<>();

        // 1. Build Mob Geometry per type
        for (Mob mob : mobs) {
            if (mob.isDead()) continue;

            float x = mob.getPosition().x;
            float y = mob.getPosition().y;
            float z = mob.getPosition().z;
            float light = entityLight(world, x, y + 1.0f, z, sunLight);

            // Hurt flash (red tint) or Creeper flash (white flashing)
            boolean hurt = mob.getHurtTimer() > 0;
            boolean creeperFlash = mob.getType() == MobType.CREEPER && mob.isIgnited() && ((int) (mob.getFuseRatio() * 12) % 2 == 1);

            float r = 1.0f, g = 1.0f, b = 1.0f;
            if (creeperFlash) {
                r = 1.8f; g = 1.8f; b = 1.8f;
            } else if (hurt) {
                r = 1.0f; g = 0.35f; b = 0.35f;
            }

            List<Float> verts = mobBatches.get(mob.getType());
            buildMobModel(verts, mob, x, y, z, r, g, b, light);

            // Burning fire overlay
            if (mob.isOnFire()) {
                float flameAnim = (float) Math.sin(System.currentTimeMillis() * 0.02) * 0.06f;
                float fH = mob.getType().getHeight();
                float fW = mob.getType().getWidth() + 0.16f;
                Matrix4f fireMat = new Matrix4f().translate(x, y, z);
                addUntexturedBox(untexturedVerts, fireMat, -fW * 0.5f, 0, -fW * 0.5f, fW, fH * 0.85f + flameAnim, fW, 1.0f, 0.45f, 0.05f, 0.6f, 1.0f);
                addUntexturedBox(untexturedVerts, fireMat, -fW * 0.35f, 0.1f, -fW * 0.35f, fW * 0.7f, fH * 0.65f - flameAnim, fW * 0.7f, 1.0f, 0.85f, 0.1f, 0.7f, 1.0f);
            }
        }

        // 2. Build Arrows (3D-modell med treskaft, flintspiss og fjær, rotert i flyretning)
        for (Arrow a : arrows) {
            if (a.isDead()) continue;
            float ax = a.getPosition().x;
            float ay = a.getPosition().y;
            float az = a.getPosition().z;
            float aLight = entityLight(world, ax, ay, az, sunLight);

            Vector3f dir = a.getDirection();
            float dx = dir.x;
            float dy = dir.y;
            float dz = dir.z;
            float h = (float) Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.atan2(dx, dz);
            float pitch = (float) Math.atan2(dy, h);

            arrowMat.identity()
                    .translate(ax, ay, az)
                    .rotateY(yaw)
                    .rotateX(-pitch);

            // 1. Shaft: Slankt treskaft (Oak wood: 0.55, 0.38, 0.22)
            addUntexturedBox(untexturedVerts, arrowMat, -0.015f, -0.015f, -0.20f, 0.03f, 0.03f, 0.45f, 0.55f, 0.38f, 0.22f, 1.0f, aLight);

            // 2. Arrowhead: Mørk flintspiss foran (+Z)
            addUntexturedBox(untexturedVerts, arrowMat, -0.035f, -0.035f, 0.25f, 0.07f, 0.07f, 0.06f, 0.28f, 0.28f, 0.30f, 1.0f, aLight);
            addUntexturedBox(untexturedVerts, arrowMat, -0.018f, -0.018f, 0.31f, 0.036f, 0.036f, 0.04f, 0.20f, 0.20f, 0.22f, 1.0f, aLight);

            // 3. Fletchings: Hvite fjær i kryss bak på nocken (-Z)
            addUntexturedBox(untexturedVerts, arrowMat, -0.06f, -0.008f, -0.24f, 0.12f, 0.016f, 0.10f, 0.94f, 0.94f, 0.90f, 1.0f, aLight);
            addUntexturedBox(untexturedVerts, arrowMat, -0.008f, -0.06f, -0.24f, 0.016f, 0.12f, 0.10f, 0.90f, 0.90f, 0.86f, 1.0f, aLight);

            // 4. Liten nock-ende bak fjærene
            addUntexturedBox(untexturedVerts, arrowMat, -0.016f, -0.016f, -0.26f, 0.032f, 0.032f, 0.02f, 0.42f, 0.28f, 0.16f, 1.0f, aLight);
        }

        // 3. Build Ender Pearls
        if (pearls != null) {
            for (EnderPearl p : pearls) {
                if (p.isDead()) continue;
                float px = p.getPosition().x;
                float py = p.getPosition().y;
                float pz = p.getPosition().z;
                float pLight = entityLight(world, px, py, pz, sunLight);
                Matrix4f mat = new Matrix4f().translate(px, py, pz);
                addUntexturedBox(untexturedVerts, mat, -0.11f, -0.11f, -0.11f, 0.22f, 0.22f, 0.22f, 0.10f, 0.45f, 0.42f, 1.0f, pLight);
            }
        }

        // 4. Build Eyes of Ender
        if (eyes != null) {
            for (EyeOfEnder e : eyes) {
                if (e.isDead()) continue;
                float ex = e.getPosition().x;
                float ey = e.getPosition().y;
                float ez = e.getPosition().z;
                float eLight = entityLight(world, ex, ey, ez, sunLight);
                Matrix4f mat = new Matrix4f().translate(ex, ey, ez);
                addUntexturedBox(untexturedVerts, mat, -0.11f, -0.11f, -0.11f, 0.22f, 0.22f, 0.22f, 0.55f, 0.85f, 0.65f, 1.0f, eLight);
            }
        }

        // 5. Build Healing Beams from End Crystals to Ender Dragon
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
                        int segments = (int) (dist * 1.5f);
                        for (int s = 0; s < segments; s++) {
                            float t = (float) s / segments;
                            float bx = m.getPosition().x + (dragon.getPosition().x - m.getPosition().x) * t;
                            float by = (m.getPosition().y + 0.6f) + (dragon.getPosition().y + 0.8f - (m.getPosition().y + 0.6f)) * t;
                            float bz = m.getPosition().z + (dragon.getPosition().z - m.getPosition().z) * t;
                            Matrix4f mat = new Matrix4f().translate(bx, by, bz);
                            addUntexturedBox(untexturedVerts, mat, -0.06f, -0.06f, -0.06f, 0.12f, 0.12f, 0.12f, 0.9f, 0.2f, 0.95f, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }

        // 6. Build Boats
        if (boats != null) {
            for (Boat boat : boats) {
                if (boat.isDead()) continue;
                float bx = boat.getPosition().x;
                float by = boat.getPosition().y;
                float bz = boat.getPosition().z;
                float yaw = boat.getYaw();
                float bLight = entityLight(world, bx, by, bz, sunLight);

                Matrix4f boatMat = new Matrix4f().translate(bx, by, bz).rotateY((float) Math.toRadians(-yaw - 90.0f));

                float wr = 0.58f, wg = 0.38f, wb = 0.22f; // Oak wood plank color
                float rr = 0.50f, rg = 0.32f, rb = 0.18f; // Oak wood rim color
                float sr = 0.45f, sg = 0.28f, sb = 0.15f; // Seat bench color

                // Bottom floor
                addUntexturedBox(untexturedVerts, boatMat, -0.55f, 0, -0.75f, 1.1f, 0.08f, 1.5f, wr, wg, wb, 1.0f, bLight);
                // Left & right rim
                addUntexturedBox(untexturedVerts, boatMat, -0.57f, 0.08f, -0.75f, 0.10f, 0.38f, 1.5f, rr, rg, rb, 1.0f, bLight);
                addUntexturedBox(untexturedVerts, boatMat, 0.47f, 0.08f, -0.75f, 0.10f, 0.38f, 1.5f, rr, rg, rb, 1.0f, bLight);
                // Back & front rim
                addUntexturedBox(untexturedVerts, boatMat, -0.57f, 0.08f, -0.77f, 1.14f, 0.38f, 0.10f, rr, rg, rb, 1.0f, bLight);
                addUntexturedBox(untexturedVerts, boatMat, -0.57f, 0.08f, 0.67f, 1.14f, 0.38f, 0.10f, rr, rg, rb, 1.0f, bLight);
                // Center seat bench
                addUntexturedBox(untexturedVerts, boatMat, -0.47f, 0.15f, -0.12f, 0.94f, 0.08f, 0.24f, sr, sg, sb, 1.0f, bLight);

                // Oars
                float or = 0.65f, og = 0.48f, ob = 0.28f;
                Matrix4f leftOar = new Matrix4f(boatMat).translate(-0.62f, 0.24f, 0.1f).rotateY((float) Math.toRadians(25.0f));
                Matrix4f rightOar = new Matrix4f(boatMat).translate(0.62f, 0.24f, 0.1f).rotateY((float) Math.toRadians(-25.0f));
                addUntexturedBox(untexturedVerts, leftOar, -0.03f, -0.03f, -0.35f, 0.06f, 0.06f, 0.7f, or, og, ob, 1.0f, bLight);
                addUntexturedBox(untexturedVerts, rightOar, -0.03f, -0.03f, -0.35f, 0.06f, 0.06f, 0.7f, or, og, ob, 1.0f, bLight);
            }
        }

        // 7. Particles
        for (ParticleManager.Particle p : particles) {
            float alpha = Math.max(0.0f, 1.0f - (p.age / p.maxLifetime));
            float s = p.size * (0.6f + alpha * 0.4f);
            Matrix4f mat = new Matrix4f().translate(p.pos.x, p.pos.y, p.pos.z);
            addUntexturedBox(untexturedVerts, mat, -s * 0.5f, -s * 0.5f, -s * 0.5f, s, s, s, p.r, p.g, p.b, alpha, 1.0f);
        }

        // --- RENDER PASSES ---
        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uView", view);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Draw Textured Mobs
        shader.setUniform("uUseTexture", 1);
        for (MobType type : MobType.values()) {
            List<Float> verts = mobBatches.get(type);
            if (!verts.isEmpty()) {
                textureManager.bindTexture(type);
                uploadAndDraw(verts);
            }
        }
        textureManager.unbind();

        // Draw Untextured Objects
        if (!untexturedVerts.isEmpty()) {
            shader.setUniform("uUseTexture", 0);
            uploadAndDraw(untexturedVerts);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();
    }

    private void uploadAndDraw(List<Float> verts) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(verts.size());
        for (float f : verts) buffer.put(f);
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, verts.size() / 10);
    }

    // --- MOB MODELS WITH JAVA EDITION UV MAPPING & ANIMATIONS ---

    private void buildMobModel(List<Float> v, Mob mob, float x, float y, float z, float r, float g, float b, float light) {
        MobType mt = mob.getType();
        float yaw = mob.getYaw();
        float bodyAngleRad = (float) Math.toRadians(-yaw - 90.0f);
        float walkTime = mob.getWalkTime();

        Vector3f vel = mob.getVelocity();
        float horizSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float walkIntensity = (horizSpeed > 0.02f) ? Math.min(1.0f, horizSpeed / 1.5f) : 0.0f;

        Matrix4f rootMat = new Matrix4f().translate(x, y, z).rotateY(bodyAngleRad);

        switch (mt) {
            case ZOMBIE -> {
                int tw = 64, th = 64;
                float legAngle = (float) Math.sin(walkTime * 4.5f) * 0.65f * walkIntensity;

                // 1. Torso
                Matrix4f torsoMat = new Matrix4f(rootMat).translate(0, 0.72f, 0);
                addTexturedBox(v, torsoMat, -0.24f, 0, -0.12f, 0.48f, 0.72f, 0.24f, 16, 16, tw, th, 8, 12, 4, r, g, b, 1.0f, light);

                // 2. Head (8x8x8 px)
                Matrix4f headMat = new Matrix4f(torsoMat).translate(0, 0.72f, 0);
                addTexturedBox(v, headMat, -0.24f, 0, -0.24f, 0.48f, 0.48f, 0.48f, 0, 0, tw, th, 8, 8, 8, r, g, b, 1.0f, light);

                // 3. Left & Right Arm (Outstretched forward at -90 deg X)
                float armSwing = (float) Math.sin(walkTime * 4.5f) * 0.15f * walkIntensity;
                Matrix4f leftArmMat = new Matrix4f(torsoMat).translate(-0.36f, 0.60f, 0).rotateX((float) Math.toRadians(-90.0f) + armSwing);
                addTexturedBox(v, leftArmMat, -0.12f, -0.72f, -0.12f, 0.24f, 0.72f, 0.24f, 40, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);

                Matrix4f rightArmMat = new Matrix4f(torsoMat).translate(0.36f, 0.60f, 0).rotateX((float) Math.toRadians(-90.0f) - armSwing);
                addTexturedBox(v, rightArmMat, -0.12f, -0.72f, -0.12f, 0.24f, 0.72f, 0.24f, 40, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);

                // 4. Left & Right Leg (4x12x4 px)
                Matrix4f leftLegMat = new Matrix4f(rootMat).translate(-0.12f, 0.72f, 0).rotateX(legAngle);
                addTexturedBox(v, leftLegMat, -0.12f, -0.72f, -0.12f, 0.24f, 0.72f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);

                Matrix4f rightLegMat = new Matrix4f(rootMat).translate(0.12f, 0.72f, 0).rotateX(-legAngle);
                addTexturedBox(v, rightLegMat, -0.12f, -0.72f, -0.12f, 0.24f, 0.72f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
            }
            case SKELETON -> {
                int tw = 64, th = 32;
                float legAngle = (float) Math.sin(walkTime * 4.5f) * 0.65f * walkIntensity;

                // 1. Torso (8x12x4 px)
                Matrix4f torsoMat = new Matrix4f(rootMat).translate(0, 0.72f, 0);
                addTexturedBox(v, torsoMat, -0.24f, 0, -0.12f, 0.48f, 0.72f, 0.24f, 16, 16, tw, th, 8, 12, 4, r, g, b, 1.0f, light);

                // 2. Skull (8x8x8 px)
                Matrix4f headMat = new Matrix4f(torsoMat).translate(0, 0.72f, 0);
                addTexturedBox(v, headMat, -0.24f, 0, -0.24f, 0.48f, 0.48f, 0.48f, 0, 0, tw, th, 8, 8, 8, r, g, b, 1.0f, light);

                // 3. Left Arm (Swings) & Right Arm (Aiming Bow)
                Matrix4f leftArmMat = new Matrix4f(torsoMat).translate(-0.30f, 0.66f, 0).rotateX(-legAngle);
                addTexturedBox(v, leftArmMat, -0.06f, -0.72f, -0.06f, 0.12f, 0.72f, 0.12f, 40, 16, tw, th, 2, 12, 2, r, g, b, 1.0f, light);

                Matrix4f rightArmMat = new Matrix4f(torsoMat).translate(0.30f, 0.66f, 0)
                        .rotateX((float) Math.toRadians(-80.0f))
                        .rotateY((float) Math.toRadians(-20.0f));
                addTexturedBox(v, rightArmMat, -0.06f, -0.72f, -0.06f, 0.12f, 0.72f, 0.12f, 40, 16, tw, th, 2, 12, 2, r, g, b, 1.0f, light);

                // 4. Left & Right Leg (2x12x2 px)
                Matrix4f leftLegMat = new Matrix4f(rootMat).translate(-0.12f, 0.72f, 0).rotateX(legAngle);
                addTexturedBox(v, leftLegMat, -0.06f, -0.72f, -0.06f, 0.12f, 0.72f, 0.12f, 0, 16, tw, th, 2, 12, 2, r, g, b, 1.0f, light);

                Matrix4f rightLegMat = new Matrix4f(rootMat).translate(0.12f, 0.72f, 0).rotateX(-legAngle);
                addTexturedBox(v, rightLegMat, -0.06f, -0.72f, -0.06f, 0.12f, 0.72f, 0.12f, 0, 16, tw, th, 2, 12, 2, r, g, b, 1.0f, light);
            }
            case CREEPER -> {
                int tw = 64, th = 32;
                float scale = 1.0f + (mob.isIgnited() ? mob.getFuseRatio() * 0.22f : 0.0f);
                float legAngle = (float) Math.sin(walkTime * 6.0f) * 0.5f * walkIntensity;

                Matrix4f creeperMat = new Matrix4f(rootMat).scale(scale);

                // 1. Torso (8x12x4 px)
                Matrix4f torsoMat = new Matrix4f(creeperMat).translate(0, 0.36f, 0);
                addTexturedBox(v, torsoMat, -0.24f, 0, -0.12f, 0.48f, 0.72f, 0.24f, 16, 16, tw, th, 8, 12, 4, r, g, b, 1.0f, light);

                // 2. Head (8x8x8 px)
                Matrix4f headMat = new Matrix4f(torsoMat).translate(0, 0.72f, 0);
                addTexturedBox(v, headMat, -0.24f, 0, -0.24f, 0.48f, 0.48f, 0.48f, 0, 0, tw, th, 8, 8, 8, r, g, b, 1.0f, light);

                // 3. 4 Legs (4x6x4 px each)
                // Front-Left & Back-Right
                Matrix4f flLeg = new Matrix4f(creeperMat).translate(-0.12f, 0.36f, -0.18f).rotateX(legAngle);
                addTexturedBox(v, flLeg, -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);

                Matrix4f brLeg = new Matrix4f(creeperMat).translate(0.12f, 0.36f, 0.18f).rotateX(legAngle);
                addTexturedBox(v, brLeg, -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);

                // Front-Right & Back-Left
                Matrix4f frLeg = new Matrix4f(creeperMat).translate(0.12f, 0.36f, -0.18f).rotateX(-legAngle);
                addTexturedBox(v, frLeg, -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);

                Matrix4f blLeg = new Matrix4f(creeperMat).translate(-0.12f, 0.36f, 0.18f).rotateX(-legAngle);
                addTexturedBox(v, blLeg, -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);
            }
            case SPIDER -> {
                int tw = 64, th = 32;

                // 1. Head (8x8x8 px)
                Matrix4f headMat = new Matrix4f(rootMat).translate(0, 0.24f, -0.30f);
                addTexturedBox(v, headMat, -0.24f, -0.24f, -0.24f, 0.48f, 0.48f, 0.48f, 32, 4, tw, th, 8, 8, 8, r, g, b, 1.0f, light);

                // 2. Abdomen (12x8x12 px)
                Matrix4f bodyMat = new Matrix4f(rootMat).translate(0, 0.30f, 0.25f);
                addTexturedBox(v, bodyMat, -0.36f, -0.24f, -0.36f, 0.72f, 0.48f, 0.72f, 0, 12, tw, th, 12, 8, 12, r, g, b, 1.0f, light);

                // 3. 8 Legs (12x2x2 px)
                for (int i = 0; i < 4; i++) {
                    float lz = -0.15f + i * 0.18f;
                    float swing = (float) Math.sin(walkTime * 7.0f + i) * 0.25f * walkIntensity;

                    // Left legs
                    Matrix4f lLeg = new Matrix4f(rootMat).translate(-0.20f, 0.24f, lz)
                            .rotateY((float) Math.toRadians(45.0f - i * 25.0f))
                            .rotateZ((float) Math.toRadians(-25.0f) + swing);
                    addTexturedBox(v, lLeg, -0.72f, -0.06f, -0.06f, 0.72f, 0.12f, 0.12f, 18, 0, tw, th, 12, 2, 2, r, g, b, 1.0f, light);

                    // Right legs
                    Matrix4f rLeg = new Matrix4f(rootMat).translate(0.20f, 0.24f, lz)
                            .rotateY((float) Math.toRadians(-45.0f + i * 25.0f))
                            .rotateZ((float) Math.toRadians(25.0f) - swing);
                    addTexturedBox(v, rLeg, 0, -0.06f, -0.06f, 0.72f, 0.12f, 0.12f, 18, 0, tw, th, 12, 2, 2, r, g, b, 1.0f, light);
                }
            }
            case PIG -> {
                int tw = 64, th = 32;
                float legAngle = (float) Math.sin(walkTime * 5.0f) * 0.5f * walkIntensity;

                // 1. Horizontal Body (10x16x8 px)
                Matrix4f bodyMat = new Matrix4f(rootMat).translate(0, 0.36f, 0);
                addTexturedBox(v, bodyMat, -0.30f, 0, -0.48f, 0.60f, 0.48f, 0.96f, 28, 8, tw, th, 10, 16, 8, r, g, b, 1.0f, light);

                // 2. Head (8x8x8 px) + Snout (4x3x1 px)
                Matrix4f headMat = new Matrix4f(bodyMat).translate(0, 0.18f, -0.48f);
                addTexturedBox(v, headMat, -0.24f, 0, -0.48f, 0.48f, 0.48f, 0.48f, 0, 0, tw, th, 8, 8, 8, r, g, b, 1.0f, light);
                addTexturedBox(v, headMat, -0.12f, 0.06f, -0.54f, 0.24f, 0.18f, 0.06f, 16, 16, tw, th, 4, 3, 1, r, g, b, 1.0f, light);

                // 3. 4 Legs (4x6x4 px)
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.18f, 0.36f, -0.30f).rotateX(legAngle),
                        -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.18f, 0.36f, -0.30f).rotateX(-legAngle),
                        -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.18f, 0.36f, 0.30f).rotateX(-legAngle),
                        -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.18f, 0.36f, 0.30f).rotateX(legAngle),
                        -0.12f, -0.36f, -0.12f, 0.24f, 0.36f, 0.24f, 0, 16, tw, th, 4, 6, 4, r, g, b, 1.0f, light);
            }
            case COW -> {
                int tw = 64, th = 32;
                float legAngle = (float) Math.sin(walkTime * 5.0f) * 0.5f * walkIntensity;

                // 1. Body (12x18x10 px)
                Matrix4f bodyMat = new Matrix4f(rootMat).translate(0, 0.60f, 0);
                addTexturedBox(v, bodyMat, -0.36f, 0, -0.54f, 0.72f, 0.60f, 1.08f, 18, 4, tw, th, 12, 18, 10, r, g, b, 1.0f, light);

                // 2. Head (8x8x6 px) + Horns (1x3x1 px)
                Matrix4f headMat = new Matrix4f(bodyMat).translate(0, 0.24f, -0.54f);
                addTexturedBox(v, headMat, -0.24f, 0, -0.36f, 0.48f, 0.48f, 0.36f, 0, 0, tw, th, 8, 8, 6, r, g, b, 1.0f, light);
                addTexturedBox(v, headMat, -0.30f, 0.30f, -0.24f, 0.06f, 0.18f, 0.06f, 22, 0, tw, th, 1, 3, 1, r, g, b, 1.0f, light);
                addTexturedBox(v, headMat, 0.24f, 0.30f, -0.24f, 0.06f, 0.18f, 0.06f, 22, 0, tw, th, 1, 3, 1, r, g, b, 1.0f, light);

                // 3. 4 Legs (4x12x4 px)
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.24f, 0.60f, -0.36f).rotateX(legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.24f, 0.60f, -0.36f).rotateX(-legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.24f, 0.60f, 0.36f).rotateX(-legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.24f, 0.60f, 0.36f).rotateX(legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
            }
            case SHEEP -> {
                int tw = 64, th = 32;
                float legAngle = (float) Math.sin(walkTime * 5.0f) * 0.5f * walkIntensity;

                // 1. Fleece Body (12x16x10 px)
                Matrix4f bodyMat = new Matrix4f(rootMat).translate(0, 0.60f, 0);
                addTexturedBox(v, bodyMat, -0.36f, 0, -0.48f, 0.72f, 0.60f, 0.96f, 28, 8, tw, th, 12, 16, 10, r, g, b, 1.0f, light);

                // 2. Head (6x6x8 px)
                Matrix4f headMat = new Matrix4f(bodyMat).translate(0, 0.18f, -0.48f);
                addTexturedBox(v, headMat, -0.18f, 0, -0.48f, 0.36f, 0.36f, 0.48f, 0, 0, tw, th, 6, 6, 8, r, g, b, 1.0f, light);

                // 3. 4 Legs (4x12x4 px)
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.20f, 0.60f, -0.30f).rotateX(legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.20f, 0.60f, -0.30f).rotateX(-legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.20f, 0.60f, 0.30f).rotateX(-legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.20f, 0.60f, 0.30f).rotateX(legAngle),
                        -0.12f, -0.60f, -0.12f, 0.24f, 0.60f, 0.24f, 0, 16, tw, th, 4, 12, 4, r, g, b, 1.0f, light);
            }
            case CHICKEN -> {
                int tw = 64, th = 32;
                float legAngle = (float) Math.sin(walkTime * 6.0f) * 0.6f * walkIntensity;

                // 1. Body (6x8x6 px)
                Matrix4f bodyMat = new Matrix4f(rootMat).translate(0, 0.25f, 0);
                addTexturedBox(v, bodyMat, -0.18f, 0, -0.24f, 0.36f, 0.36f, 0.48f, 0, 9, tw, th, 6, 8, 6, r, g, b, 1.0f, light);

                // 2. Head (4x6x3 px) + Beak (4x2x2 px) + Wattle (2x2x2 px)
                Matrix4f headMat = new Matrix4f(bodyMat).translate(0, 0.20f, -0.24f);
                addTexturedBox(v, headMat, -0.12f, 0, -0.18f, 0.24f, 0.36f, 0.18f, 0, 0, tw, th, 4, 6, 3, r, g, b, 1.0f, light);
                addTexturedBox(v, headMat, -0.12f, 0.12f, -0.30f, 0.24f, 0.12f, 0.12f, 14, 0, tw, th, 4, 2, 2, r, g, b, 1.0f, light);
                addTexturedBox(v, headMat, -0.06f, 0.04f, -0.24f, 0.12f, 0.12f, 0.12f, 14, 4, tw, th, 2, 2, 2, r, g, b, 1.0f, light);

                // 3. Wings (1x4x6 px)
                addTexturedBox(v, new Matrix4f(bodyMat).translate(-0.20f, 0.08f, 0),
                        -0.03f, 0, -0.18f, 0.06f, 0.24f, 0.36f, 24, 13, tw, th, 1, 4, 6, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(bodyMat).translate(0.20f, 0.08f, 0),
                        -0.03f, 0, -0.18f, 0.06f, 0.24f, 0.36f, 24, 13, tw, th, 1, 4, 6, r, g, b, 1.0f, light);

                // 4. 2 Legs (3x5x3 px)
                addTexturedBox(v, new Matrix4f(rootMat).translate(-0.08f, 0.25f, 0).rotateX(legAngle),
                        -0.09f, -0.25f, -0.09f, 0.18f, 0.25f, 0.18f, 26, 0, tw, th, 3, 5, 3, r, g, b, 1.0f, light);
                addTexturedBox(v, new Matrix4f(rootMat).translate(0.08f, 0.25f, 0).rotateX(-legAngle),
                        -0.09f, -0.25f, -0.09f, 0.18f, 0.25f, 0.18f, 26, 0, tw, th, 3, 5, 3, r, g, b, 1.0f, light);
            }
            case ENDERMAN -> {
                int tw = 64, th = 32;
                float legAngle = (float) Math.sin(walkTime * 4.5f) * 0.5f * walkIntensity;
                boolean aggro = mob.isAggressive();
                float shake = aggro ? (float) Math.sin(System.currentTimeMillis() * 0.05) * 0.02f : 0.0f;

                // 1. Torso (8x12x4 px)
                Matrix4f torsoMat = new Matrix4f(rootMat).translate(0, 1.50f, 0);
                addTexturedBox(v, torsoMat, -0.24f, 0, -0.12f, 0.48f, 0.72f, 0.24f, 32, 16, tw, th, 8, 12, 4, r, g, b, 1.0f, light);

                // 2. Head (8x8x8 px)
                Matrix4f headMat = new Matrix4f(torsoMat).translate(shake, 0.72f, 0);
                addTexturedBox(v, headMat, -0.24f, 0, -0.24f, 0.48f, 0.48f, 0.48f, 0, 0, tw, th, 8, 8, 8, r, g, b, 1.0f, light);
                if (aggro) {
                    // Open Jaw (8x4x8 px)
                    Matrix4f jawMat = new Matrix4f(torsoMat).translate(shake, 0.60f, 0);
                    addTexturedBox(v, jawMat, -0.24f, 0, -0.24f, 0.48f, 0.24f, 0.48f, 0, 16, tw, th, 8, 4, 8, r, g, b, 1.0f, light);
                }

                // 3. Slender Arms (2x30x2 px)
                Matrix4f leftArmMat = new Matrix4f(torsoMat).translate(-0.30f, 0.66f, 0).rotateX(-legAngle);
                addTexturedBox(v, leftArmMat, -0.06f, -1.80f, -0.06f, 0.12f, 1.80f, 0.12f, 56, 0, tw, th, 2, 30, 2, r, g, b, 1.0f, light);

                Matrix4f rightArmMat = new Matrix4f(torsoMat).translate(0.30f, 0.66f, 0).rotateX(legAngle);
                addTexturedBox(v, rightArmMat, -0.06f, -1.80f, -0.06f, 0.12f, 1.80f, 0.12f, 56, 0, tw, th, 2, 30, 2, r, g, b, 1.0f, light);

                // 4. Slender Legs (2x30x2 px)
                Matrix4f leftLegMat = new Matrix4f(rootMat).translate(-0.12f, 1.50f, 0).rotateX(legAngle);
                addTexturedBox(v, leftLegMat, -0.06f, -1.50f, -0.06f, 0.12f, 1.50f, 0.12f, 56, 0, tw, th, 2, 30, 2, r, g, b, 1.0f, light);

                Matrix4f rightLegMat = new Matrix4f(rootMat).translate(0.12f, 1.50f, 0).rotateX(-legAngle);
                addTexturedBox(v, rightLegMat, -0.06f, -1.50f, -0.06f, 0.12f, 1.50f, 0.12f, 56, 0, tw, th, 2, 30, 2, r, g, b, 1.0f, light);
            }
            case BLAZE -> {
                int tw = 64, th = 32;

                // 1. Head (8x8x8 px)
                Matrix4f headMat = new Matrix4f(rootMat).translate(0, 1.25f, 0);
                addTexturedBox(v, headMat, -0.24f, -0.24f, -0.24f, 0.48f, 0.48f, 0.48f, 0, 0, tw, th, 8, 8, 8, r, g, b, 1.0f, light);

                // 2. Orbiting Blaze Rods (12 rods: 3 layers of 4)
                float rodW = 0.12f, rodH = 0.48f;
                // Layer 1
                for (int i = 0; i < 4; i++) {
                    double angle = (System.currentTimeMillis() * 0.003) + (i * Math.PI / 2.0);
                    float rx = (float) Math.cos(angle) * 0.38f;
                    float rz = (float) Math.sin(angle) * 0.38f;
                    Matrix4f rodMat = new Matrix4f(rootMat).translate(rx, 0.85f, rz);
                    addTexturedBox(v, rodMat, -rodW / 2, 0, -rodW / 2, rodW, rodH, rodW, 0, 16, tw, th, 2, 8, 2, r, g, b, 1.0f, 1.0f);
                }
                // Layer 2
                for (int i = 0; i < 4; i++) {
                    double angle = -(System.currentTimeMillis() * 0.003) + (i * Math.PI / 2.0) + (Math.PI / 4.0);
                    float rx = (float) Math.cos(angle) * 0.46f;
                    float rz = (float) Math.sin(angle) * 0.46f;
                    Matrix4f rodMat = new Matrix4f(rootMat).translate(rx, 0.45f, rz);
                    addTexturedBox(v, rodMat, -rodW / 2, 0, -rodW / 2, rodW, rodH, rodW, 0, 16, tw, th, 2, 8, 2, r, g, b, 1.0f, 1.0f);
                }
                // Layer 3
                for (int i = 0; i < 4; i++) {
                    double angle = (System.currentTimeMillis() * 0.0035) + (i * Math.PI / 2.0);
                    float rx = (float) Math.cos(angle) * 0.30f;
                    float rz = (float) Math.sin(angle) * 0.30f;
                    Matrix4f rodMat = new Matrix4f(rootMat).translate(rx, 0.08f, rz);
                    addTexturedBox(v, rodMat, -rodW / 2, 0, -rodW / 2, rodW, rodH, rodW, 0, 16, tw, th, 2, 8, 2, r, g, b, 1.0f, 1.0f);
                }
            }
            case ENDER_DRAGON -> {
                int tw = 256, th = 256;
                float wingFlap = (float) Math.sin(System.currentTimeMillis() * 0.008f) * 0.45f;

                // 1. Dragon Body (16x16x24 px)
                Matrix4f bodyMat = new Matrix4f(rootMat).translate(0, 0.8f, 0);
                addTexturedBox(v, bodyMat, -0.65f, -0.45f, -1.2f, 1.3f, 0.9f, 2.4f, 0, 0, tw, th, 16, 16, 24, r, g, b, 1.0f, light);

                // 2. Neck & Head
                Matrix4f neckMat = new Matrix4f(bodyMat).translate(0, 0.2f, -1.6f);
                addTexturedBox(v, neckMat, -0.35f, -0.35f, -0.45f, 0.7f, 0.7f, 0.9f, 0, 40, tw, th, 10, 10, 10, r, g, b, 1.0f, light);

                Matrix4f headMat = new Matrix4f(neckMat).translate(0, 0.2f, -0.7f);
                addTexturedBox(v, headMat, -0.30f, -0.28f, -0.35f, 0.6f, 0.55f, 0.7f, 176, 44, tw, th, 12, 10, 16, r, g, b, 1.0f, light);

                // 3. Tail Segments
                for (int i = 0; i < 3; i++) {
                    float tz = 1.6f + i * 0.8f;
                    float segScale = 0.5f - i * 0.1f;
                    Matrix4f tailMat = new Matrix4f(bodyMat).translate(0, 0, tz);
                    addTexturedBox(v, tailMat, -segScale / 2, -segScale / 2, -0.45f, segScale, segScale, 0.9f, 192, 104, tw, th, 8, 8, 12, r, g, b, 1.0f, light);
                }

                // 4. Flapping Wings (Left & Right)
                Matrix4f leftWing = new Matrix4f(bodyMat).translate(-0.65f, 0.3f, -0.2f).rotateZ(-wingFlap);
                addTexturedBox(v, leftWing, -2.2f, -0.04f, -0.9f, 2.2f, 0.08f, 1.8f, 112, 88, tw, th, 28, 4, 20, r, g, b, 1.0f, light);

                Matrix4f rightWing = new Matrix4f(bodyMat).translate(0.65f, 0.3f, -0.2f).rotateZ(wingFlap);
                addTexturedBox(v, rightWing, 0, -0.04f, -0.9f, 2.2f, 0.08f, 1.8f, 112, 88, tw, th, 28, 4, 20, r, g, b, 1.0f, light);
            }
            case END_CRYSTAL -> {
                int tw = 64, th = 32;
                float time = (System.currentTimeMillis() % 100000) * 0.001f;

                // 1. Obsidian base
                Matrix4f baseMat = new Matrix4f(rootMat);
                addTexturedBox(v, baseMat, -0.45f, 0, -0.45f, 0.9f, 0.2f, 0.9f, 0, 16, tw, th, 14, 4, 14, r, g, b, 1.0f, light);

                // 2. Outer rotating glass cube
                Matrix4f outerCube = new Matrix4f(rootMat).translate(0, 0.6f, 0).rotateY(time * 2.0f).rotateX((float) Math.sin(time * 3.0f) * 0.3f);
                addTexturedBox(v, outerCube, -0.35f, -0.35f, -0.35f, 0.7f, 0.7f, 0.7f, 0, 0, tw, th, 8, 8, 8, r, g, b, 0.8f, 1.0f);

                // 3. Inner counter-rotating core
                Matrix4f innerCube = new Matrix4f(rootMat).translate(0, 0.6f, 0).rotateY(-time * 3.0f).rotateZ((float) Math.cos(time * 3.0f) * 0.3f);
                addTexturedBox(v, innerCube, -0.20f, -0.20f, -0.20f, 0.4f, 0.4f, 0.4f, 32, 0, tw, th, 6, 6, 6, r, g, b, 1.0f, 1.0f);
            }
        }
    }

    // --- STANDARD MINECRAFT 1.16.1 BOX UV MAPPING UTILITY ---

    public static void addTexturedBox(
            List<Float> v, Matrix4f mat,
            float minX, float minY, float minZ,
            float sizeX, float sizeY, float sizeZ,
            int u, int vPixel, int texW, int texH,
            int pw, int ph, int pd,
            float r, float g, float b, float a, float light
    ) {
        float maxX = minX + sizeX;
        float maxY = minY + sizeY;
        float maxZ = minZ + sizeZ;

        // UV coordinate bounds (Normalized [0..1])
        float uTop0 = (float) (u + pd) / texW;
        float vTop0 = (float) (vPixel) / texH;
        float uTop1 = (float) (u + pd + pw) / texW;
        float vTop1 = (float) (vPixel + pd) / texH;

        float uBot0 = (float) (u + pd + pw) / texW;
        float vBot0 = (float) (vPixel) / texH;
        float uBot1 = (float) (u + pd + 2 * pw) / texW;
        float vBot1 = (float) (vPixel + pd) / texH;

        float uRight0 = (float) (u) / texW;
        float vRight0 = (float) (vPixel + pd) / texH;
        float uRight1 = (float) (u + pd) / texW;
        float vRight1 = (float) (vPixel + pd + ph) / texH;

        float uFront0 = (float) (u + pd) / texW;
        float vFront0 = (float) (vPixel + pd) / texH;
        float uFront1 = (float) (u + pd + pw) / texW;
        float vFront1 = (float) (vPixel + pd + ph) / texH;

        float uLeft0 = (float) (u + pd + pw) / texW;
        float vLeft0 = (float) (vPixel + pd) / texH;
        float uLeft1 = (float) (u + 2 * pd + pw) / texW;
        float vLeft1 = (float) (vPixel + pd + ph) / texH;

        float uBack0 = (float) (u + 2 * pd + pw) / texW;
        float vBack0 = (float) (vPixel + pd) / texH;
        float uBack1 = (float) (u + 2 * pd + 2 * pw) / texW;
        float vBack1 = (float) (vPixel + pd + ph) / texH;

        // Top face (+Y)
        float tL = 1.0f;
        addQuad(v, mat, minX, maxY, minZ, uTop0, vTop0,
                maxX, maxY, minZ, uTop1, vTop0,
                maxX, maxY, maxZ, uTop1, vTop1,
                minX, maxY, maxZ, uTop0, vTop1,
                r * tL, g * tL, b * tL, a, light);

        // Bottom face (-Y)
        float bL = 0.55f;
        addQuad(v, mat, minX, minY, maxZ, uBot0, vBot1,
                maxX, minY, maxZ, uBot1, vBot1,
                maxX, minY, minZ, uBot1, vBot0,
                minX, minY, minZ, uBot0, vBot0,
                r * bL, g * bL, b * bL, a, light);

        // Front face (-Z, facing forward)
        float fL = 0.80f;
        addQuad(v, mat, maxX, minY, minZ, uFront1, vFront1,
                minX, minY, minZ, uFront0, vFront1,
                minX, maxY, minZ, uFront0, vFront0,
                maxX, maxY, minZ, uFront1, vFront0,
                r * fL, g * fL, b * fL, a, light);

        // Back face (+Z, towards back)
        float kL = 0.65f;
        addQuad(v, mat, minX, minY, maxZ, uBack1, vBack1,
                maxX, minY, maxZ, uBack0, vBack1,
                maxX, maxY, maxZ, uBack0, vBack0,
                minX, maxY, maxZ, uBack1, vBack0,
                r * kL, g * kL, b * kL, a, light);

        // Left face (-X)
        float lL = 0.70f;
        addQuad(v, mat, minX, minY, minZ, uLeft0, vLeft1,
                minX, minY, maxZ, uLeft1, vLeft1,
                minX, maxY, maxZ, uLeft1, vLeft0,
                minX, maxY, minZ, uLeft0, vLeft0,
                r * lL, g * lL, b * lL, a, light);

        // Right face (+X)
        float rL = 0.85f;
        addQuad(v, mat, maxX, minY, maxZ, uRight0, vRight1,
                maxX, minY, minZ, uRight1, vRight1,
                maxX, maxY, minZ, uRight1, vRight0,
                maxX, maxY, maxZ, uRight0, vRight0,
                r * rL, g * rL, b * rL, a, light);
    }

    private static void addUntexturedBox(List<Float> v, Matrix4f mat, float minX, float minY, float minZ,
                                         float sizeX, float sizeY, float sizeZ,
                                         float r, float g, float b, float a, float light) {
        float maxX = minX + sizeX;
        float maxY = minY + sizeY;
        float maxZ = minZ + sizeZ;

        // Top face
        addQuad(v, mat, minX, maxY, minZ, 0, 0, maxX, maxY, minZ, 0, 0, maxX, maxY, maxZ, 0, 0, minX, maxY, maxZ, 0, 0, r, g, b, a, light);
        // Bottom face
        addQuad(v, mat, minX, minY, maxZ, 0, 0, maxX, minY, maxZ, 0, 0, maxX, minY, minZ, 0, 0, minX, minY, minZ, 0, 0, r * 0.55f, g * 0.55f, b * 0.55f, a, light);
        // North face (-Z)
        addQuad(v, mat, maxX, minY, minZ, 0, 0, minX, minY, minZ, 0, 0, minX, maxY, minZ, 0, 0, maxX, maxY, minZ, 0, 0, r * 0.80f, g * 0.80f, b * 0.80f, a, light);
        // South face (+Z)
        addQuad(v, mat, minX, minY, maxZ, 0, 0, maxX, minY, maxZ, 0, 0, maxX, maxY, maxZ, 0, 0, minX, maxY, maxZ, 0, 0, r * 0.65f, g * 0.65f, b * 0.65f, a, light);
        // West face (-X)
        addQuad(v, mat, minX, minY, minZ, 0, 0, minX, minY, maxZ, 0, 0, minX, maxY, maxZ, 0, 0, minX, maxY, minZ, 0, 0, r * 0.70f, g * 0.70f, b * 0.70f, a, light);
        // East face (+X)
        addQuad(v, mat, maxX, minY, maxZ, 0, 0, maxX, minY, minZ, 0, 0, maxX, maxY, minZ, 0, 0, maxX, maxY, maxZ, 0, 0, r * 0.85f, g * 0.85f, b * 0.85f, a, light);
    }

    private static void addQuad(List<Float> v, Matrix4f mat,
                                float x0, float y0, float z0, float u0, float v0,
                                float x1, float y1, float z1, float u1, float v1,
                                float x2, float y2, float z2, float u2, float v2,
                                float x3, float y3, float z3, float u3, float v3,
                                float r, float g, float b, float a, float light) {
        Vector4f p0 = transform(mat, x0, y0, z0);
        Vector4f p1 = transform(mat, x1, y1, z1);
        Vector4f p2 = transform(mat, x2, y2, z2);
        Vector4f p3 = transform(mat, x3, y3, z3);

        // Triangle 1: 0 -> 1 -> 2
        addVertex(v, p0.x, p0.y, p0.z, u0, v0, r, g, b, a, light);
        addVertex(v, p1.x, p1.y, p1.z, u1, v1, r, g, b, a, light);
        addVertex(v, p2.x, p2.y, p2.z, u2, v2, r, g, b, a, light);

        // Triangle 2: 0 -> 2 -> 3
        addVertex(v, p0.x, p0.y, p0.z, u0, v0, r, g, b, a, light);
        addVertex(v, p2.x, p2.y, p2.z, u2, v2, r, g, b, a, light);
        addVertex(v, p3.x, p3.y, p3.z, u3, v3, r, g, b, a, light);
    }

    private static Vector4f transform(Matrix4f mat, float x, float y, float z) {
        Vector4f res = new Vector4f(x, y, z, 1.0f);
        mat.transform(res);
        return res;
    }

    private static void addVertex(List<Float> v, float x, float y, float z, float u, float vCoord,
                                  float r, float g, float b, float a, float light) {
        v.add(x); v.add(y); v.add(z);
        v.add(u); v.add(vCoord);
        v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(light);
    }

    /** Light factor for an entity: sky-lit when exposed to the sky, dim constant inside caves. */
    private static float entityLight(World world, float x, float y, float z, float sunLight) {
        float sky = world.getSkyLight((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        float ambient = 0.15f;
        return ambient + (0.10f + 0.75f * sunLight) * sky;
    }

    public void cleanup() {
        if (textureManager != null) {
            textureManager.cleanup();
        }
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
