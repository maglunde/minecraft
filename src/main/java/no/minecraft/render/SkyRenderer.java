package no.minecraft.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class SkyRenderer {
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
            out vec4 FragColor;

            void main() {
                FragColor = vColor;
            }
            """;

    public SkyRenderer() {
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

    public void render(Matrix4f projection, Matrix4f view, Vector3f playerPos, float dayFraction) {
        // Celestial sphere radius around player
        float dist = 140.0f;
        // Sun and Moon angle across sky:
        // dayFraction = 0.0 -> Sunrise in East (+X)
        // dayFraction = 0.25 -> Noon at zenith (+Y)
        // dayFraction = 0.50 -> Sunset in West (-X)
        // dayFraction = 0.75 -> Midnight, moon at zenith (+Y)
        double angle = dayFraction * 2.0 * Math.PI;

        float sunX = playerPos.x + (float) Math.cos(angle) * dist;
        float sunY = playerPos.y + (float) Math.sin(angle) * dist;
        float sunZ = playerPos.z;

        // Moon is opposite to the sun (180 degrees away)
        float moonX = playerPos.x - (float) Math.cos(angle) * dist;
        float moonY = playerPos.y - (float) Math.sin(angle) * dist;
        float moonZ = playerPos.z;

        float sunSize = 14.0f;
        float moonSize = 12.0f;

        float[] verts = new float[12 * 7]; // 2 quads * 6 vertices * 7 floats
        int idx = 0;

        // --- 1. Sun Quad (Square Minecraft Sun: Bright Yellow-White) ---
        // Facing player (along X-Y plane rotated)
        float sHalf = sunSize * 0.5f;
        // Normal perpendicular to radial direction in X-Y plane
        float sinA = (float) Math.sin(angle);
        float cosA = (float) Math.cos(angle);
        float perpX = -sinA * sHalf;
        float perpY = cosA * sHalf;
        float perpZ = sHalf;

        // Sun 4 corners
        float sx1 = sunX - perpX, sy1 = sunY - perpY, sz1 = sunZ - perpZ;
        float sx2 = sunX - perpX, sy2 = sunY - perpY, sz2 = sunZ + perpZ;
        float sx3 = sunX + perpX, sy3 = sunY + perpY, sz3 = sunZ + perpZ;
        float sx4 = sunX + perpX, sy4 = sunY + perpY, sz4 = sunZ - perpZ;

        float sr = 1.0f, sg = 0.98f, sb = 0.75f, sa = 1.0f;

        // Triangle 1
        idx = putVertex(verts, idx, sx1, sy1, sz1, sr, sg, sb, sa);
        idx = putVertex(verts, idx, sx2, sy2, sz2, sr, sg, sb, sa);
        idx = putVertex(verts, idx, sx3, sy3, sz3, sr, sg, sb, sa);
        // Triangle 2
        idx = putVertex(verts, idx, sx1, sy1, sz1, sr, sg, sb, sa);
        idx = putVertex(verts, idx, sx3, sy3, sz3, sr, sg, sb, sa);
        idx = putVertex(verts, idx, sx4, sy4, sz4, sr, sg, sb, sa);

        // --- 2. Moon Quad (Square Minecraft Moon: Soft Pale White/Blue) ---
        float mHalf = moonSize * 0.5f;
        float mPerpX = sinA * mHalf;
        float mPerpY = -cosA * mHalf;
        float mPerpZ = mHalf;

        float mx1 = moonX - mPerpX, my1 = moonY - mPerpY, mz1 = moonZ - mPerpZ;
        float mx2 = moonX - mPerpX, my2 = moonY - mPerpY, mz2 = moonZ + mPerpZ;
        float mx3 = moonX + mPerpX, my3 = moonY + mPerpY, mz3 = moonZ + mPerpZ;
        float mx4 = moonX + mPerpX, my4 = moonY + mPerpY, mz4 = moonZ - mPerpZ;

        float mr = 0.92f, mg = 0.95f, mb = 1.0f, ma = 1.0f;

        // Triangle 1
        idx = putVertex(verts, idx, mx1, my1, mz1, mr, mg, mb, ma);
        idx = putVertex(verts, idx, mx2, my2, mz2, mr, mg, mb, ma);
        idx = putVertex(verts, idx, mx3, my3, mz3, mr, mg, mb, ma);
        // Triangle 2
        idx = putVertex(verts, idx, mx1, my1, mz1, mr, mg, mb, ma);
        idx = putVertex(verts, idx, mx3, my3, mz3, mr, mg, mb, ma);
        idx = putVertex(verts, idx, mx4, my4, mz4, mr, mg, mb, ma);

        glDisable(GL_CULL_FACE);
        glDepthMask(false); // Don't write to depth buffer so terrain renders over celestial bodies

        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uView", view);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
        buf.put(verts).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);

        glDrawArrays(GL_TRIANGLES, 0, 12);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.unbind();

        glDepthMask(true);
        glEnable(GL_CULL_FACE);
    }

    private int putVertex(float[] arr, int idx, float x, float y, float z, float r, float g, float b, float a) {
        arr[idx++] = x;
        arr[idx++] = y;
        arr[idx++] = z;
        arr[idx++] = r;
        arr[idx++] = g;
        arr[idx++] = b;
        arr[idx++] = a;
        return idx;
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
