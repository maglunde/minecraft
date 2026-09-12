package no.minecraft.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class Shader {
    private final int programId;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public Shader(String vertexCode, String fragmentCode) {
        int vertexId = compileShader(vertexCode, GL_VERTEX_SHADER);
        int fragmentId = compileShader(fragmentCode, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexId);
        glAttachShader(programId, fragmentId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String info = glGetProgramInfoLog(programId);
            throw new RuntimeException("Shader linking failed: " + info);
        }

        glDeleteShader(vertexId);
        glDeleteShader(fragmentId);
    }

    private int compileShader(String source, int type) {
        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String info = glGetShaderInfoLog(shaderId);
            throw new RuntimeException("Shader compilation failed: " + info);
        }
        return shaderId;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public int getUniformLocation(String name) {
        if (uniforms.containsKey(name)) {
            return uniforms.get(name);
        }
        int location = glGetUniformLocation(programId, name);
        uniforms.put(name, location);
        return location;
    }

    public void setUniform(String name, Matrix4f matrix) {
        int location = getUniformLocation(name);
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(location, false, matrixBuffer);
    }

    public void setUniform(String name, Vector3f vector) {
        int location = getUniformLocation(name);
        glUniform3f(location, vector.x, vector.y, vector.z);
    }

    public void setUniform(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        glUniform4f(location, x, y, z, w);
    }

    public void setUniform(String name, float value) {
        int location = getUniformLocation(name);
        glUniform1f(location, value);
    }

    public void setUniform(String name, int value) {
        int location = getUniformLocation(name);
        glUniform1i(location, value);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }
}
