package no.minecraft.render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class MiningOverlay {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;

            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform mat4 uModel;

            out vec2 vTexCoord;

            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            out vec4 FragColor;

            void main() {
                vec4 tex = texture(uTexture, vTexCoord);
                if (tex.a < 0.1) discard;
                FragColor = tex;
            }
            """;

    public MiningOverlay() {
        this.shader = new Shader(VERTEX_SHADER, FRAGMENT_SHADER);

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // 6 faces * 2 triangles * 3 vertices = 36 vertices (Pos: 3, UV: 2)
        int stride = 5 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(Matrix4f projection, Matrix4f view, int bx, int by, int bz, int crackStage, TextureAtlas atlas) {
        if (crackStage < 0 || crackStage > 9) return;

        int tileIndex = 42 + crackStage;
        float[] uv = TextureAtlas.getUVs(tileIndex);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        float d0 = -0.003f;
        float d1 = 1.003f;

        float[] verts = {
                // Top
                d0, d1, d1, u0, v1,   d1, d1, d1, u1, v1,   d1, d1, d0, u1, v0,
                d0, d1, d1, u0, v1,   d1, d1, d0, u1, v0,   d0, d1, d0, u0, v0,
                // Bottom
                d0, d0, d0, u0, v1,   d1, d0, d0, u1, v1,   d1, d0, d1, u1, v0,
                d0, d0, d0, u0, v1,   d1, d0, d1, u1, v0,   d0, d0, d1, u0, v0,
                // North (-Z)
                d1, d0, d0, u0, v1,   d0, d0, d0, u1, v1,   d0, d1, d0, u1, v0,
                d1, d0, d0, u0, v1,   d0, d1, d0, u1, v0,   d1, d1, d0, u0, v0,
                // South (+Z)
                d0, d0, d1, u0, v1,   d1, d0, d1, u1, v1,   d1, d1, d1, u1, v0,
                d0, d0, d1, u0, v1,   d1, d1, d1, u1, v0,   d0, d1, d1, u0, v0,
                // West (-X)
                d0, d0, d0, u0, v1,   d0, d0, d1, u1, v1,   d0, d1, d1, u1, v0,
                d0, d0, d0, u0, v1,   d0, d1, d1, u1, v0,   d0, d1, d0, u0, v0,
                // East (+X)
                d1, d0, d1, u0, v1,   d1, d0, d0, u1, v1,   d1, d1, d0, u1, v0,
                d1, d0, d1, u0, v1,   d1, d1, d0, u1, v0,   d1, d1, d1, u0, v0
        };

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glPolygonOffset(-1.0f, -1.0f);
        glEnable(GL_POLYGON_OFFSET_FILL);

        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uView", view);

        Matrix4f model = new Matrix4f().translate(bx, by, bz);
        shader.setUniform("uModel", model);

        atlas.bind();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
        buf.put(verts).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);

        glDrawArrays(GL_TRIANGLES, 0, 36);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        atlas.unbind();
        shader.unbind();

        glDisable(GL_POLYGON_OFFSET_FILL);
        glDisable(GL_BLEND);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
