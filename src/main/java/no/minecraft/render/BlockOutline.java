package no.minecraft.render;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class BlockOutline {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform mat4 uModel;

            void main() {
                gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            out vec4 FragColor;

            void main() {
                FragColor = vec4(0.0, 0.0, 0.0, 0.6); // Semi-transparent black outline
            }
            """;

    public BlockOutline() {
        this.shader = new Shader(VERTEX_SHADER, FRAGMENT_SHADER);

        // Unit cube edges (12 edges = 24 vertices) with a tiny expansion to avoid z-fighting
        float d0 = -0.002f;
        float d1 = 1.002f;

        float[] lines = {
                // Bottom
                d0, d0, d0,  d1, d0, d0,
                d1, d0, d0,  d1, d0, d1,
                d1, d0, d1,  d0, d0, d1,
                d0, d0, d1,  d0, d0, d0,
                // Top
                d0, d1, d0,  d1, d1, d0,
                d1, d1, d0,  d1, d1, d1,
                d1, d1, d1,  d0, d1, d1,
                d0, d1, d1,  d0, d1, d0,
                // Vertical pillars
                d0, d0, d0,  d0, d1, d0,
                d1, d0, d0,  d1, d1, d0,
                d1, d0, d1,  d1, d1, d1,
                d0, d0, d1,  d0, d1, d1
        };

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(lines.length);
        buffer.put(lines).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(Matrix4f projection, Matrix4f view, int bx, int by, int bz) {
        render(projection, view, bx, by, bz, null);
    }

    public void render(Matrix4f projection, Matrix4f view, int bx, int by, int bz, no.minecraft.world.BlockType type) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(2.5f);

        shader.bind();
        shader.setUniform("uProjection", projection);
        shader.setUniform("uView", view);

        Matrix4f model = new Matrix4f().translate(bx, by, bz);
        if (type == no.minecraft.world.BlockType.CACTUS) {
            model.translate(0.0625f, 0.0f, 0.0625f).scale(0.875f, 1.0f, 0.875f);
        } else if (type == no.minecraft.world.BlockType.CHEST) {
            model.translate(0.0625f, 0.0f, 0.0625f).scale(0.875f, 0.875f, 0.875f);
        }
        shader.setUniform("uModel", model);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);

        shader.unbind();
        glDisable(GL_BLEND);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
