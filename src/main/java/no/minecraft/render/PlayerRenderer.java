package no.minecraft.render;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class PlayerRenderer {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;

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

    public PlayerRenderer() {
        this.shader = new Shader(VERT_SRC, FRAG_SRC);
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

    public void render(World world, Player player, Matrix4f projection, Matrix4f view, float sunLight, TextureAtlas atlas) {
        Vector3f eye = player.getEyePosition();
        float sky = world.getSkyLight((int) Math.floor(eye.x), (int) Math.floor(eye.y), (int) Math.floor(eye.z));
        playerLight = 0.15f + (0.20f + 0.65f * sunLight) * sky;
        Vector3f pos = player.getPosition();
        float yaw = player.getCamera().getYaw();
        float pitch = player.getCamera().getPitch();
        boolean hurt = player.getDeathFlashTimer() > 0.0f;
        boolean sneaking = player.isSneaking();

        // 1. Calculate walk and swing animation angles
        float walkTime = player.getWalkAnimTime();
        Vector3f vel = player.getVelocity();
        float horizSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float walkIntensity = (player.isOnGround() && horizSpeed > 0.1f)
                ? Math.min(1.0f, horizSpeed / 3.8f)
                : 0.0f;

        float legAngle = (float) Math.sin(walkTime) * 0.65f * walkIntensity;
        float armAngle = (float) Math.sin(walkTime) * 0.65f * walkIntensity;

        float swingProgress = player.getSwingProgress();
        float swingAngle = 0.0f;
        if (swingProgress > 0.0f) {
            swingAngle = (float) Math.sin(swingProgress * Math.PI) * 1.3f;
        }

        // Steve Colors
        float skinR = hurt ? 1.0f : 0.78f;
        float skinG = hurt ? 0.3f : 0.58f;
        float skinB = hurt ? 0.3f : 0.47f;

        float shirtR = hurt ? 1.0f : 0.00f;
        float shirtG = hurt ? 0.3f : 0.58f;
        float shirtB = hurt ? 0.3f : 0.65f;

        float jeansR = hurt ? 1.0f : 0.15f;
        float jeansG = hurt ? 0.3f : 0.18f;
        float jeansB = hurt ? 0.3f : 0.45f;

        float shoeR = hurt ? 1.0f : 0.22f;
        float shoeG = hurt ? 0.3f : 0.22f;
        float shoeB = hurt ? 0.3f : 0.24f;

        float hairR = hurt ? 1.0f : 0.28f;
        float hairG = hurt ? 0.2f : 0.18f;
        float hairB = hurt ? 0.2f : 0.10f;

        // Base world translation for player
        float baseY = pos.y + (sneaking ? -0.12f : 0.0f);

        // Body yaw: in OpenGL, yaw=-90 is along -Z.
        // Convert to standard rotation: angle around Y is -(yaw + 90)
        float bodyAngleRad = (float) Math.toRadians(-yaw - 90.0f);

        // Geometry buffers for colored body parts and textured held item
        List<Float> bodyVerts = new ArrayList<>();
        List<Float> itemVerts = new ArrayList<>();

        // Helper matrix for limb positioning
        Matrix4f bodyTransform = new Matrix4f()
                .translate(pos.x, baseY, pos.z)
                .rotateY(bodyAngleRad);

        // --- TORSO ---
        Matrix4f torsoMat = new Matrix4f(bodyTransform).translate(0.0f, 0.75f, 0.0f);
        if (sneaking) {
            torsoMat.rotateX((float) Math.toRadians(15.0f));
        }
        // Torso: width 0.44m, height 0.65m, depth 0.24m
        addOrientedBox(bodyVerts, torsoMat, -0.22f, 0.0f, -0.12f, 0.44f, 0.65f, 0.24f, shirtR, shirtG, shirtB);

        // --- HEAD ---
        // Pivot at top of torso: y = 0.65f on torso
        Matrix4f headMat = new Matrix4f(torsoMat).translate(0.0f, 0.65f, 0.0f);
        headMat.rotateX((float) Math.toRadians(pitch));
        // Head cube: 0.44m x 0.44m x 0.44m
        addOrientedBox(bodyVerts, headMat, -0.22f, 0.0f, -0.22f, 0.44f, 0.44f, 0.44f, skinR, skinG, skinB);
        // Steve Hair on Top & Back
        addOrientedBox(bodyVerts, headMat, -0.23f, 0.22f, -0.23f, 0.46f, 0.23f, 0.46f, hairR, hairG, hairB);
        addOrientedBox(bodyVerts, headMat, -0.23f, 0.0f, 0.10f, 0.46f, 0.44f, 0.13f, hairR, hairG, hairB);
        // Steve Eyes (Front face: -Z side)
        float eyeY = 0.18f, eyeH = 0.07f;
        // Left Eye (White + Blue)
        addOrientedBox(bodyVerts, headMat, -0.16f, eyeY, -0.23f, 0.05f, eyeH, 0.02f, 0.95f, 0.95f, 0.95f);
        addOrientedBox(bodyVerts, headMat, -0.11f, eyeY, -0.23f, 0.05f, eyeH, 0.02f, 0.18f, 0.35f, 0.75f);
        // Right Eye (Blue + White)
        addOrientedBox(bodyVerts, headMat, 0.06f, eyeY, -0.23f, 0.05f, eyeH, 0.02f, 0.18f, 0.35f, 0.75f);
        addOrientedBox(bodyVerts, headMat, 0.11f, eyeY, -0.23f, 0.05f, eyeH, 0.02f, 0.95f, 0.95f, 0.95f);
        // Beard / Mouth
        addOrientedBox(bodyVerts, headMat, -0.07f, 0.06f, -0.23f, 0.14f, 0.06f, 0.02f, 0.38f, 0.22f, 0.15f);

        // --- LEGS ---
        // Left Leg
        Matrix4f leftLegMat = new Matrix4f(bodyTransform).translate(-0.11f, 0.75f, 0.0f);
        leftLegMat.rotateX(legAngle);
        // Upper pants: height 0.60m
        addOrientedBox(bodyVerts, leftLegMat, -0.10f, -0.60f, -0.11f, 0.20f, 0.60f, 0.22f, jeansR, jeansG, jeansB);
        // Shoe: bottom 0.15m
        addOrientedBox(bodyVerts, leftLegMat, -0.10f, -0.75f, -0.11f, 0.20f, 0.15f, 0.22f, shoeR, shoeG, shoeB);

        // Right Leg
        Matrix4f rightLegMat = new Matrix4f(bodyTransform).translate(0.11f, 0.75f, 0.0f);
        rightLegMat.rotateX(-legAngle);
        // Upper pants
        addOrientedBox(bodyVerts, rightLegMat, -0.10f, -0.60f, -0.11f, 0.20f, 0.60f, 0.22f, jeansR, jeansG, jeansB);
        // Shoe
        addOrientedBox(bodyVerts, rightLegMat, -0.10f, -0.75f, -0.11f, 0.20f, 0.15f, 0.22f, shoeR, shoeG, shoeB);

        // --- ARMS ---
        // Left Arm (Swings opposite to left leg, aims bow when drawing)
        Matrix4f leftArmMat = new Matrix4f(torsoMat).translate(-0.31f, 0.60f, 0.0f);
        if (player.isDrawingBow()) {
            leftArmMat.rotateX((float) Math.toRadians(-85.0f));
            leftArmMat.rotateY((float) Math.toRadians(25.0f));
        } else {
            leftArmMat.rotateX(-armAngle);
        }
        // Sleeve (top 0.18m)
        addOrientedBox(bodyVerts, leftArmMat, -0.09f, -0.18f, -0.09f, 0.18f, 0.18f, 0.18f, shirtR, shirtG, shirtB);
        // Forearm & Hand (skin tone: 0.47m)
        addOrientedBox(bodyVerts, leftArmMat, -0.08f, -0.65f, -0.08f, 0.16f, 0.47f, 0.16f, skinR, skinG, skinB);

        // Right Arm (Carries tool / item, swings when mining/attacking, raises when eating, pulls string when drawing bow)
        Matrix4f rightArmMat = new Matrix4f(torsoMat).translate(0.31f, 0.60f, 0.0f);
        if (player.isEating()) {
            float eatProg = player.getEatProgress();
            float eatWobble = (float) Math.sin(eatProg * 32.0f * Math.PI) * 0.05f;
            rightArmMat.rotateX((float) Math.toRadians(-80.0f + eatWobble * 5.0f));
            rightArmMat.rotateY((float) Math.toRadians(-35.0f));
            rightArmMat.rotateZ((float) Math.toRadians(15.0f));
        } else if (player.isDrawingBow()) {
            rightArmMat.rotateX((float) Math.toRadians(-85.0f));
            rightArmMat.rotateY((float) Math.toRadians(-30.0f));
        } else {
            rightArmMat.rotateX(armAngle - swingAngle);
        }
        // Sleeve (top 0.18m)
        addOrientedBox(bodyVerts, rightArmMat, -0.09f, -0.18f, -0.09f, 0.18f, 0.18f, 0.18f, shirtR, shirtG, shirtB);
        // Forearm & Hand
        addOrientedBox(bodyVerts, rightArmMat, -0.08f, -0.65f, -0.08f, 0.16f, 0.47f, 0.16f, skinR, skinG, skinB);

        // --- HELD ITEM IN RIGHT HAND ---
        BlockType held = player.getSelectedBlock();
        boolean hasItem = held != null && held != BlockType.AIR;
        if (player.getGameMode() != GameMode.CREATIVE && player.getSelectedBlockCount() <= 0) {
            hasItem = false;
        }

        if (hasItem) {
            Matrix4f itemMat = new Matrix4f(rightArmMat).translate(0.0f, -0.58f, -0.10f);
            if (held.isSolid()) {
                // Mini 3D Block in hand
                itemMat.rotateY((float) Math.toRadians(45.0f));
                itemMat.rotateX((float) Math.toRadians(-20.0f));
                addBlockGeometry(itemVerts, itemMat, held, 0.24f);
            } else {
                // 2D Tool or Item in hand
                itemMat.translate(0.0f, 0.05f, 0.05f);
                itemMat.rotateY((float) Math.toRadians(90.0f));
                itemMat.rotateZ((float) Math.toRadians(-40.0f));
                addItemGeometry(itemVerts, itemMat, held, 0.45f);
            }
        }

        // --- HELD ITEM IN LEFT HAND (OFFHAND) ---
        no.minecraft.player.ItemStack offhand = player.getOffhandItem();
        boolean hasOffhand = offhand != null && !offhand.isEmpty() && offhand.getType() != BlockType.AIR;
        if (hasOffhand) {
            BlockType offBlock = offhand.getType();
            Matrix4f offMat = new Matrix4f(leftArmMat).translate(0.0f, -0.58f, -0.10f);
            if (offBlock.isSolid()) {
                // Mini 3D Block in left hand
                offMat.rotateY((float) Math.toRadians(-45.0f));
                offMat.rotateX((float) Math.toRadians(-20.0f));
                addBlockGeometry(itemVerts, offMat, offBlock, 0.24f);
            } else {
                // 2D Tool or Item in left hand
                offMat.translate(0.0f, 0.05f, 0.05f);
                offMat.rotateY((float) Math.toRadians(-90.0f));
                offMat.rotateZ((float) Math.toRadians(40.0f));
                addItemGeometry(itemVerts, offMat, offBlock, 0.45f);
            }
        }

        // --- OPENGL DRAW CALLS ---
        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uView", view);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // 1. Draw Body Parts (colored, no texture)
        if (!bodyVerts.isEmpty()) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(bodyVerts.size());
            for (float f : bodyVerts) buffer.put(f);
            buffer.flip();

            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            shader.setUniform("uUseTexture", 0);
            glDrawArrays(GL_TRIANGLES, 0, bodyVerts.size() / 10);
        }

        // 2. Draw Held Item (textured)
        if (!itemVerts.isEmpty() && atlas != null) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(itemVerts.size());
            for (float f : itemVerts) buffer.put(f);
            buffer.flip();

            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            atlas.bind();
            shader.setUniform("uUseTexture", 1);
            glDrawArrays(GL_TRIANGLES, 0, itemVerts.size() / 10);
            atlas.unbind();
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();
    }

    private void addOrientedBox(List<Float> v, Matrix4f mat, float minX, float minY, float minZ,
                                float w, float h, float d, float r, float g, float b) {
        float maxX = minX + w;
        float maxY = minY + h;
        float maxZ = minZ + d;

        // Top face (+Y)
        float tL = 1.0f;
        addQuad(v, mat, minX, maxY, minZ, 0, 0,
                        maxX, maxY, minZ, 0, 0,
                        maxX, maxY, maxZ, 0, 0,
                        minX, maxY, maxZ, 0, 0,
                        r * tL, g * tL, b * tL, 1.0f);

        // Bottom face (-Y)
        float bL = 0.55f;
        addQuad(v, mat, minX, minY, maxZ, 0, 0,
                        maxX, minY, maxZ, 0, 0,
                        maxX, minY, minZ, 0, 0,
                        minX, minY, minZ, 0, 0,
                        r * bL, g * bL, b * bL, 1.0f);

        // Front face (-Z, facing forward)
        float fL = 0.80f;
        addQuad(v, mat, maxX, minY, minZ, 0, 0,
                        minX, minY, minZ, 0, 0,
                        minX, maxY, minZ, 0, 0,
                        maxX, maxY, minZ, 0, 0,
                        r * fL, g * fL, b * fL, 1.0f);

        // Back face (+Z, towards back)
        float kL = 0.65f;
        addQuad(v, mat, minX, minY, maxZ, 0, 0,
                        maxX, minY, maxZ, 0, 0,
                        maxX, maxY, maxZ, 0, 0,
                        minX, maxY, maxZ, 0, 0,
                        r * kL, g * kL, b * kL, 1.0f);

        // Left face (-X)
        float lL = 0.70f;
        addQuad(v, mat, minX, minY, minZ, 0, 0,
                        minX, minY, maxZ, 0, 0,
                        minX, maxY, maxZ, 0, 0,
                        minX, maxY, minZ, 0, 0,
                        r * lL, g * lL, b * lL, 1.0f);

        // Right face (+X)
        float rL = 0.85f;
        addQuad(v, mat, maxX, minY, maxZ, 0, 0,
                        maxX, minY, minZ, 0, 0,
                        maxX, maxY, minZ, 0, 0,
                        maxX, maxY, maxZ, 0, 0,
                        r * rL, g * rL, b * rL, 1.0f);
    }

    private void addBlockGeometry(List<Float> v, Matrix4f mat, BlockType block, float size) {
        float hx = (block == BlockType.CACTUS || block == BlockType.CHEST) ? (size * 0.5f * (14.0f / 16.0f)) : (size * 0.5f);
        float hy = (block == BlockType.CHEST) ? (size * 0.5f * (14.0f / 16.0f)) : (size * 0.5f);
        float hz = (block == BlockType.CACTUS || block == BlockType.CHEST) ? (size * 0.5f * (14.0f / 16.0f)) : (size * 0.5f);

        float uTop0, vTop0, uTop1, vTop1;
        float uBot0, vBot0, uBot1, vBot1;
        float uSide0, vSide0, uSide1, vSide1;
        float[] uvSide;

        if (block == BlockType.CACTUS) {
            int topTexId = block.getTexture(BlockType.Face.TOP);
            float[] uvT = TextureAtlas.getUVs(topTexId);
            float px = (uvT[2] - uvT[0]) / 16.0f;
            float py = (uvT[3] - uvT[1]) / 16.0f;
            uTop0 = uvT[0] + 1.0f * px;
            uTop1 = uvT[0] + 15.0f * px;
            vTop0 = uvT[1] + 1.0f * py;
            vTop1 = uvT[1] + 15.0f * py;

            int botTexId = block.getTexture(BlockType.Face.BOTTOM);
            float[] uvB = TextureAtlas.getUVs(botTexId);
            float bx = (uvB[2] - uvB[0]) / 16.0f;
            float by = (uvB[3] - uvB[1]) / 16.0f;
            uBot0 = uvB[0] + 1.0f * bx;
            uBot1 = uvB[0] + 15.0f * bx;
            vBot0 = uvB[1] + 1.0f * by;
            vBot1 = uvB[1] + 15.0f * by;

            int sideTexId = block.getTexture(BlockType.Face.NORTH);
            float[] uvS = TextureAtlas.getUVs(sideTexId);
            float sx = (uvS[2] - uvS[0]) / 16.0f;
            uSide0 = uvS[0] + 1.0f * sx;
            uSide1 = uvS[0] + 15.0f * sx;
            vSide0 = uvS[1];
            vSide1 = uvS[3];
            uvSide = new float[]{uSide0, vSide0, uSide1, vSide1};
        } else {
            float[] uvT = TextureAtlas.getUVs(block.getTexture(BlockType.Face.TOP));
            uTop0 = uvT[0]; vTop0 = uvT[1]; uTop1 = uvT[2]; vTop1 = uvT[3];
            float[] uvB = TextureAtlas.getUVs(block.getTexture(BlockType.Face.BOTTOM));
            uBot0 = uvB[0]; vBot0 = uvB[1]; uBot1 = uvB[2]; vBot1 = uvB[3];
            uvSide = null;
        }

        // Top Face (+Y)
        addQuad(v, mat, -hx, hy, -hz, uTop0, vTop0,
                         hx, hy, -hz, uTop1, vTop0,
                         hx, hy,  hz, uTop1, vTop1,
                        -hx, hy,  hz, uTop0, vTop1,
                        1.0f, 1.0f, 1.0f, 1.0f);

        // Bottom Face (-Y)
        addQuad(v, mat, -hx, -hy,  hz, uBot0, vBot1,
                         hx, -hy,  hz, uBot1, vBot1,
                         hx, -hy, -hz, uBot1, vBot0,
                        -hx, -hy, -hz, uBot0, vBot0,
                        0.55f, 0.55f, 0.55f, 1.0f);

        // North Face (-Z)
        float[] uvN = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.NORTH));
        addQuad(v, mat,  hx, -hy, -hz, uvN[2], uvN[3],
                        -hx, -hy, -hz, uvN[0], uvN[3],
                        -hx,  hy, -hz, uvN[0], uvN[1],
                         hx,  hy, -hz, uvN[2], uvN[1],
                        0.75f, 0.75f, 0.75f, 1.0f);

        // South Face (+Z)
        float[] uvS = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.SOUTH));
        addQuad(v, mat, -hx, -hy, hz, uvS[0], uvS[3],
                         hx, -hy, hz, uvS[2], uvS[3],
                         hx,  hy, hz, uvS[2], uvS[1],
                        -hx,  hy, hz, uvS[0], uvS[1],
                        0.75f, 0.75f, 0.75f, 1.0f);

        // West Face (-X)
        float[] uvW = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.WEST));
        addQuad(v, mat, -hx, -hy, -hz, uvW[0], uvW[3],
                        -hx, -hy,  hz, uvW[2], uvW[3],
                        -hx,  hy,  hz, uvW[2], uvW[1],
                        -hx,  hy, -hz, uvW[0], uvW[1],
                        0.85f, 0.85f, 0.85f, 1.0f);

        // East Face (+X)
        float[] uvE = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.EAST));
        addQuad(v, mat, hx, -hy,  hz, uvE[2], uvE[3],
                        hx, -hy, -hz, uvE[0], uvE[3],
                        hx,  hy, -hz, uvE[0], uvE[1],
                        hx,  hy,  hz, uvE[2], uvE[1],
                        0.85f, 0.85f, 0.85f, 1.0f);
    }

    private void addItemGeometry(List<Float> v, Matrix4f mat, BlockType item, float size) {
        float[] uv = TextureAtlas.getUVs(item.getTexture(BlockType.Face.NORTH));
        float grip = 0.08f;
        float halfThickness = 0.003f;

        // Front Face
        addQuad(v, mat, -grip, -grip, halfThickness, uv[0], uv[3],
                        size - grip, -grip, halfThickness, uv[2], uv[3],
                        size - grip, size - grip, halfThickness, uv[2], uv[1],
                        -grip, size - grip, halfThickness, uv[0], uv[1],
                        0.95f, 0.95f, 0.95f, 1.0f);

        // Back Face
        addQuad(v, mat, size - grip, -grip, -halfThickness, uv[2], uv[3],
                        -grip, -grip, -halfThickness, uv[0], uv[3],
                        -grip, size - grip, -halfThickness, uv[0], uv[1],
                        size - grip, size - grip, -halfThickness, uv[2], uv[1],
                        0.80f, 0.80f, 0.80f, 1.0f);
    }

    private void addQuad(List<Float> v, Matrix4f mat,
                         float x0, float y0, float z0, float u0, float v0,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float r, float g, float b, float a) {
        Vector4f p0 = transform(mat, x0, y0, z0);
        Vector4f p1 = transform(mat, x1, y1, z1);
        Vector4f p2 = transform(mat, x2, y2, z2);
        Vector4f p3 = transform(mat, x3, y3, z3);

        // Triangle 1: 0 -> 1 -> 2
        addVertex(v, p0.x, p0.y, p0.z, u0, v0, r, g, b, a);
        addVertex(v, p1.x, p1.y, p1.z, u1, v1, r, g, b, a);
        addVertex(v, p2.x, p2.y, p2.z, u2, v2, r, g, b, a);

        // Triangle 2: 0 -> 2 -> 3
        addVertex(v, p0.x, p0.y, p0.z, u0, v0, r, g, b, a);
        addVertex(v, p2.x, p2.y, p2.z, u2, v2, r, g, b, a);
        addVertex(v, p3.x, p3.y, p3.z, u3, v3, r, g, b, a);
    }

    private Vector4f transform(Matrix4f mat, float x, float y, float z) {
        Vector4f res = new Vector4f(x, y, z, 1.0f);
        mat.transform(res);
        return res;
    }

    private void addVertex(List<Float> v, float x, float y, float z, float u, float vCoord, float r, float g, float b, float a) {
        v.add(x); v.add(y); v.add(z);
        v.add(u); v.add(vCoord);
        v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(playerLight);
    }

    private float playerLight = 1.0f;

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        shader.cleanup();
    }
}
