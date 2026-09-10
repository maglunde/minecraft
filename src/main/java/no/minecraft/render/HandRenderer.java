package no.minecraft.render;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class HandRenderer {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(2048);

    private float swingProgress = 0.0f;
    private float equipProgress = 1.0f;
    private float useTimer = 0.0f;
    private float walkBobTimer = 0.0f;

    private int previousSlot = -1;
    private BlockType previousBlock = null;

    private static final String VERT_SRC = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aColor;

            uniform mat4 uProjection;
            uniform mat4 uModelView;

            out vec2 vTexCoord;
            out vec4 vColor;

            void main() {
                vTexCoord = aTexCoord;
                vColor = aColor;
                gl_Position = uProjection * uModelView * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core
            in vec2 vTexCoord;
            in vec4 vColor;

            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform float uSunLight;

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

                float ambient = 0.35;
                float light = ambient + (1.0 - ambient) * uSunLight;
                FragColor = vec4(baseColor.rgb * light, baseColor.a);
            }
            """;

    public HandRenderer() {
        this.shader = new Shader(VERT_SRC, FRAG_SRC);
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Pos: 3, UV: 2, Color: 4 -> Stride: 9 floats (36 bytes)
        int stride = 9 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void swing() {
        if (swingProgress <= 0.0f) {
            swingProgress = 0.001f;
        }
    }

    public void use() {
        useTimer = 0.15f;
    }

    public void render(Player player, float sunLight, float dt, boolean isMining, int screenWidth, int screenHeight) {
        // 1. Update slot / item change detection for equip animation
        int currentSlot = player.getSelectedSlot();
        BlockType currentBlock = player.getSelectedBlock();
        if (currentSlot != previousSlot || currentBlock != previousBlock) {
            previousSlot = currentSlot;
            previousBlock = currentBlock;
            equipProgress = 0.0f;
        }

        if (equipProgress < 1.0f) {
            equipProgress = Math.min(1.0f, equipProgress + dt * 6.0f);
        }

        // 2. Update swing animation
        if (isMining && swingProgress <= 0.0f) {
            swingProgress = 0.001f;
        }

        if (swingProgress > 0.0f) {
            swingProgress += dt / 0.25f; // ~0.25s per swing
            if (swingProgress >= 1.0f) {
                if (isMining) {
                    swingProgress = 0.001f; // Repeat swing while mining
                } else {
                    swingProgress = 0.0f;
                }
            }
        }

        // 3. Update right-click use timer
        if (useTimer > 0.0f) {
            useTimer = Math.max(0.0f, useTimer - dt);
        }

        // 4. Update walk bobbing
        Vector3f vel = player.getVelocity();
        boolean onGround = player.isOnGround();
        float horizSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (onGround && horizSpeed > 0.1f) {
            walkBobTimer += dt * 10.0f;
        } else {
            walkBobTimer += dt * 2.0f;
        }

        float bobIntensity = (onGround && horizSpeed > 0.1f) ? Math.min(1.0f, horizSpeed / 4.0f) : 0.0f;
        float bobX = (float) Math.sin(walkBobTimer * 0.5f) * 0.012f * bobIntensity;
        float bobY = (float) Math.abs(Math.cos(walkBobTimer)) * 0.012f * bobIntensity;

        // 5. Determine held item
        BlockType held = player.getSelectedBlock();
        boolean hasItem = held != null && held != BlockType.AIR;
        if (player.getGameMode() != GameMode.CREATIVE && player.getSelectedBlockCount() <= 0) {
            hasItem = false;
        }

        // 6. Setup OpenGL state for first-person rendering
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniform("uTexture", 0);
        shader.setUniform("uSunLight", sunLight);

        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(70.0f),
                (float) screenWidth / (float) Math.max(1, screenHeight),
                0.05f,
                20.0f
        );
        shader.setUniform("uProjection", proj);

        // 7. Calculate Arm ModelView Matrix
        float armX = 0.35f + bobX;
        float armY = -0.32f + bobY;
        float armZ = -0.52f;

        if (hasItem) {
            if (held.isSolid()) {
                armX += 0.05f;
                armY -= 0.04f;
                armZ += 0.04f;
            } else {
                armX += 0.02f;
                armY -= 0.02f;
                armZ += 0.02f;
            }
        } else {
            armX -= 0.02f;
            armY += 0.02f;
        }

        float equipOffset = (1.0f - (float) Math.sin(equipProgress * Math.PI * 0.5f)) * -0.25f;

        Matrix4f armMat = new Matrix4f().identity();
        if (swingProgress > 0.0f) {
            float swing = (float) Math.sin(swingProgress * Math.PI);
            armX -= swing * 0.08f;
            armY -= swing * 0.06f;
            armZ -= swing * 0.10f;
            armMat.translate(armX, armY + equipOffset, armZ);
            armMat.rotateX((float) Math.toRadians(-swing * 42.0f));
            armMat.rotateY((float) Math.toRadians(-swing * 28.0f));
            armMat.rotateZ((float) Math.toRadians(swing * 20.0f));
        } else {
            armMat.translate(armX, armY + equipOffset, armZ);
        }

        if (useTimer > 0.0f) {
            float use = (float) Math.sin((1.0f - useTimer / 0.15f) * Math.PI);
            armMat.translate(0.0f, -use * 0.02f, use * 0.04f);
        }

        if (hasItem && held.isSolid()) {
            armMat.rotateY((float) Math.toRadians(-22.0f));
            armMat.rotateX((float) Math.toRadians(18.0f));
            armMat.rotateZ((float) Math.toRadians(-8.0f));
        } else if (hasItem) {
            armMat.rotateY((float) Math.toRadians(-20.0f));
            armMat.rotateX((float) Math.toRadians(15.0f));
            armMat.rotateZ((float) Math.toRadians(-6.0f));
        } else {
            armMat.rotateY((float) Math.toRadians(-18.0f));
            armMat.rotateX((float) Math.toRadians(12.0f));
            armMat.rotateZ((float) Math.toRadians(-4.0f));
        }

        // 8. Render Arm (Cyan sleeve + Steve skin forearm & fist)
        buffer.clear();
        buildArmGeometry();
        buffer.flip();

        int armVertCount = buffer.limit() / 9;
        shader.setUniform("uModelView", armMat);
        shader.setUniform("uUseTexture", 0);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, armVertCount);

        // 9. Render Held Item / Block if available
        if (hasItem) {
            Matrix4f itemMat = new Matrix4f(armMat);
            buffer.clear();

            if (held.isSolid()) {
                // 3D Block
                itemMat.translate(-0.06f, 0.07f, -0.15f);
                itemMat.rotateY((float) Math.toRadians(-35.0f));
                itemMat.rotateX((float) Math.toRadians(25.0f));
                itemMat.rotateZ((float) Math.toRadians(-10.0f));
                buildBlockGeometry(held, 0.22f);
            } else {
                // Flat 2D Tool / Item
                itemMat.translate(0.0f, 0.01f, 0.02f);
                itemMat.rotateY((float) Math.toRadians(45.0f));
                itemMat.rotateX((float) Math.toRadians(-55.0f));
                itemMat.rotateZ((float) Math.toRadians(40.0f));
                buildItemGeometry(held, 0.36f);
            }

            buffer.flip();
            int itemVertCount = buffer.limit() / 9;
            shader.setUniform("uModelView", itemMat);
            shader.setUniform("uUseTexture", 1);

            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            glDrawArrays(GL_TRIANGLES, 0, itemVertCount);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();

        glEnable(GL_CULL_FACE);
    }

    private void buildArmGeometry() {
        // Forearm & Fist (Skin tone)
        // Steve skin: rgb(199, 148, 120) -> (0.78f, 0.58f, 0.47f)
        float sr = 0.78f, sg = 0.58f, sb = 0.47f;
        addBoxColored(-0.05f, -0.05f, 0.0f, 0.05f, 0.05f, 0.26f, sr, sg, sb, 1.0f);

        // Sleeve (Cyan shirt)
        // Steve shirt: rgb(0, 148, 160) -> (0.0f, 0.58f, 0.63f)
        float cr = 0.0f, cg = 0.58f, cb = 0.63f;
        addBoxColored(-0.053f, -0.053f, 0.26f, 0.053f, 0.053f, 0.44f, cr, cg, cb, 1.0f);
    }

    private void buildBlockGeometry(BlockType block, float size) {
        float hx = (block == BlockType.CACTUS) ? (size * 0.5f * (14.0f / 16.0f)) : (size * 0.5f);
        float hy = size * 0.5f;
        float hz = (block == BlockType.CACTUS) ? (size * 0.5f * (14.0f / 16.0f)) : (size * 0.5f);

        float uTop0, vTop0, uTop1, vTop1;
        float uBot0, vBot0, uBot1, vBot1;
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
            float uSide0 = uvS[0] + 1.0f * sx;
            float uSide1 = uvS[0] + 15.0f * sx;
            float vSide0 = uvS[1];
            float vSide1 = uvS[3];
            uvSide = new float[]{uSide0, vSide0, uSide1, vSide1};
        } else {
            float[] uvT = TextureAtlas.getUVs(block.getTexture(BlockType.Face.TOP));
            uTop0 = uvT[0]; vTop0 = uvT[1]; uTop1 = uvT[2]; vTop1 = uvT[3];
            float[] uvB = TextureAtlas.getUVs(block.getTexture(BlockType.Face.BOTTOM));
            uBot0 = uvB[0]; vBot0 = uvB[1]; uBot1 = uvB[2]; vBot1 = uvB[3];
            uvSide = null;
        }

        // Top Face (+Y)
        addQuad(-hx, hy, -hz, uTop0, vTop0,
                 hx, hy, -hz, uTop1, vTop0,
                 hx, hy,  hz, uTop1, vTop1,
                -hx, hy,  hz, uTop0, vTop1,
                1.0f, 1.0f, 1.0f, 1.0f);

        // Bottom Face (-Y)
        addQuad(-hx, -hy,  hz, uBot0, vBot1,
                 hx, -hy,  hz, uBot1, vBot1,
                 hx, -hy, -hz, uBot1, vBot0,
                -hx, -hy, -hz, uBot0, vBot0,
                0.55f, 0.55f, 0.55f, 1.0f);

        // North Face (-Z)
        float[] uvN = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.NORTH));
        addQuad( hx, -hy, -hz, uvN[2], uvN[3],
                -hx, -hy, -hz, uvN[0], uvN[3],
                -hx,  hy, -hz, uvN[0], uvN[1],
                 hx,  hy, -hz, uvN[2], uvN[1],
                0.75f, 0.75f, 0.75f, 1.0f);

        // South Face (+Z)
        float[] uvS = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.SOUTH));
        addQuad(-hx, -hy, hz, uvS[0], uvS[3],
                 hx, -hy, hz, uvS[2], uvS[3],
                 hx,  hy, hz, uvS[2], uvS[1],
                -hx,  hy, hz, uvS[0], uvS[1],
                0.75f, 0.75f, 0.75f, 1.0f);

        // West Face (-X)
        float[] uvW = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.WEST));
        addQuad(-hx, -hy, -hz, uvW[0], uvW[3],
                -hx, -hy,  hz, uvW[2], uvW[3],
                -hx,  hy,  hz, uvW[2], uvW[1],
                -hx,  hy, -hz, uvW[0], uvW[1],
                0.85f, 0.85f, 0.85f, 1.0f);

        // East Face (+X)
        float[] uvE = (uvSide != null) ? uvSide : TextureAtlas.getUVs(block.getTexture(BlockType.Face.EAST));
        addQuad(hx, -hy,  hz, uvE[2], uvE[3],
                hx, -hy, -hz, uvE[0], uvE[3],
                hx,  hy, -hz, uvE[0], uvE[1],
                hx,  hy,  hz, uvE[2], uvE[1],
                0.85f, 0.85f, 0.85f, 1.0f);
    }

    private void buildItemGeometry(BlockType item, float size) {
        float[] uv = TextureAtlas.getUVs(item.getTexture(BlockType.Face.NORTH));
        float grip = 0.08f;
        float halfThickness = 0.002f;

        // Front Face (Facing +Z)
        addQuad(-grip, -grip, halfThickness, uv[0], uv[3],
                size - grip, -grip, halfThickness, uv[2], uv[3],
                size - grip, size - grip, halfThickness, uv[2], uv[1],
                -grip, size - grip, halfThickness, uv[0], uv[1],
                0.95f, 0.95f, 0.95f, 1.0f);

        // Back Face (Facing -Z)
        addQuad(size - grip, -grip, -halfThickness, uv[2], uv[3],
                -grip, -grip, -halfThickness, uv[0], uv[3],
                -grip, size - grip, -halfThickness, uv[0], uv[1],
                size - grip, size - grip, -halfThickness, uv[2], uv[1],
                0.80f, 0.80f, 0.80f, 1.0f);
    }

    private void addBoxColored(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                               float r, float g, float b, float a) {
        // Top face (+Y)
        float tL = 1.0f;
        addQuad(minX, maxY, minZ, 0, 0,
                maxX, maxY, minZ, 0, 0,
                maxX, maxY, maxZ, 0, 0,
                minX, maxY, maxZ, 0, 0,
                r * tL, g * tL, b * tL, a);

        // Bottom face (-Y)
        float bL = 0.55f;
        addQuad(minX, minY, maxZ, 0, 0,
                maxX, minY, maxZ, 0, 0,
                maxX, minY, minZ, 0, 0,
                minX, minY, minZ, 0, 0,
                r * bL, g * bL, b * bL, a);

        // Front face (-Z, knuckles/fist)
        float fL = 0.80f;
        addQuad(maxX, minY, minZ, 0, 0,
                minX, minY, minZ, 0, 0,
                minX, maxY, minZ, 0, 0,
                maxX, maxY, minZ, 0, 0,
                r * fL, g * fL, b * fL, a);

        // Back face (+Z, towards shoulder)
        float kL = 0.60f;
        addQuad(minX, minY, maxZ, 0, 0,
                maxX, minY, maxZ, 0, 0,
                maxX, maxY, maxZ, 0, 0,
                minX, maxY, maxZ, 0, 0,
                r * kL, g * kL, b * kL, a);

        // Left face (-X)
        float lL = 0.70f;
        addQuad(minX, minY, minZ, 0, 0,
                minX, minY, maxZ, 0, 0,
                minX, maxY, maxZ, 0, 0,
                minX, maxY, minZ, 0, 0,
                r * lL, g * lL, b * lL, a);

        // Right face (+X)
        float rL = 0.85f;
        addQuad(maxX, minY, maxZ, 0, 0,
                maxX, minY, minZ, 0, 0,
                maxX, maxY, minZ, 0, 0,
                maxX, maxY, maxZ, 0, 0,
                r * rL, g * rL, b * rL, a);
    }

    private void addQuad(float x0, float y0, float z0, float u0, float v0,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float r, float g, float b, float a) {
        // Triangle 1: 0 -> 1 -> 2
        addVertex(x0, y0, z0, u0, v0, r, g, b, a);
        addVertex(x1, y1, z1, u1, v1, r, g, b, a);
        addVertex(x2, y2, z2, u2, v2, r, g, b, a);

        // Triangle 2: 0 -> 2 -> 3
        addVertex(x0, y0, z0, u0, v0, r, g, b, a);
        addVertex(x2, y2, z2, u2, v2, r, g, b, a);
        addVertex(x3, y3, z3, u3, v3, r, g, b, a);
    }

    private void addVertex(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        buffer.put(x).put(y).put(z).put(u).put(v).put(r).put(g).put(b).put(a);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        shader.cleanup();
    }
}
