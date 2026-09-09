package no.minecraft.render;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

/**
 * Shared immediate-mode UI helpers: rect/vertex emission, the batched vertex upload,
 * and the pixel font (previously copy-pasted across HUD, MainMenu and PauseMenu).
 */
public final class UiBatch {
    private UiBatch() {}

    public static void addRect(List<Float> v, float x, float y, float w, float h,
                               float u0, float v0, float u1, float v1,
                               float r, float g, float b, float a) {
        addVertex(v, x, y, u0, v0, r, g, b, a);
        addVertex(v, x, y + h, u0, v1, r, g, b, a);
        addVertex(v, x + w, y + h, u1, v1, r, g, b, a);

        addVertex(v, x, y, u0, v0, r, g, b, a);
        addVertex(v, x + w, y + h, u1, v1, r, g, b, a);
        addVertex(v, x + w, y, u1, v0, r, g, b, a);
    }

    public static void addVertex(List<Float> v, float x, float y, float u, float valV,
                                 float r, float g, float b, float a) {
        v.add(x);
        v.add(y);
        v.add(u);
        v.add(valV);
        v.add(r);
        v.add(g);
        v.add(b);
        v.add(a);
    }

    public static void drawVertices(int vaoId, int vboId, List<Float> vertices) {
        if (vertices.isEmpty()) return;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float f : vertices) {
            buffer.put(f);
        }
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, vertices.size() / 8);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    // --- Pixel font: one shared copy, built once ---

    private static final Map<Character, int[][]> GLYPHS = buildGlyphs();

    public static int[][] getGlyph(char c) {
        return GLYPHS.get(c);
    }

    public static void drawLetterBlock(List<Float> g, char ch, float x, float y, float s,
                                       float r, float gr, float b, float a) {
        ch = Character.toUpperCase(ch);
        int[][] glyph = getGlyph(ch);
        if (glyph == null) return;

        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length; col++) {
                if (glyph[row][col] == 1) {
                    addRect(g, x + col * s, y + row * s, s, s, 0, 0, 0, 0, r, gr, b, a);
                }
            }
        }
    }

    private static Map<Character, int[][]> buildGlyphs() {
        Map<Character, int[][]> m = new HashMap<>();
        m.put('A', new int[][]{{0,1,1,0},{1,0,0,1},{1,1,1,1},{1,0,0,1},{1,0,0,1}});
        m.put('B', new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,0,1},{1,1,1,0}});
        m.put('C', new int[][]{{0,1,1,1},{1,0,0,0},{1,0,0,0},{1,0,0,0},{0,1,1,1}});
        m.put('D', new int[][]{{1,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,1,1,0}});
        m.put('E', new int[][]{{1,1,1,1},{1,0,0,0},{1,1,1,0},{1,0,0,0},{1,1,1,1}});
        m.put('F', new int[][]{{1,1,1,1},{1,0,0,0},{1,1,1,0},{1,0,0,0},{1,0,0,0}});
        m.put('G', new int[][]{{0,1,1,1},{1,0,0,0},{1,0,1,1},{1,0,0,1},{0,1,1,1}});
        m.put('H', new int[][]{{1,0,0,1},{1,0,0,1},{1,1,1,1},{1,0,0,1},{1,0,0,1}});
        m.put('I', new int[][]{{1,1,1},{0,1,0},{0,1,0},{0,1,0},{1,1,1}});
        m.put('J', new int[][]{{0,0,1,1},{0,0,0,1},{0,0,0,1},{1,0,0,1},{0,1,1,0}});
        m.put('K', new int[][]{{1,0,0,1},{1,0,1,0},{1,1,0,0},{1,0,1,0},{1,0,0,1}});
        m.put('L', new int[][]{{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,1,1,1}});
        m.put('M', new int[][]{{1,0,0,0,1},{1,1,0,1,1},{1,0,1,0,1},{1,0,0,0,1},{1,0,0,0,1}});
        m.put('N', new int[][]{{1,0,0,1},{1,1,0,1},{1,0,1,1},{1,0,0,1},{1,0,0,1}});
        m.put('O', new int[][]{{0,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}});
        m.put('P', new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,0,0},{1,0,0,0}});
        m.put('Q', new int[][]{{0,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,1,0},{0,1,0,1}});
        m.put('R', new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,1,0},{1,0,0,1}});
        m.put('S', new int[][]{{0,1,1,1},{1,0,0,0},{0,1,1,0},{0,0,0,1},{1,1,1,0}});
        m.put('T', new int[][]{{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0}});
        m.put('U', new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}});
        m.put('V', new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,0,0}});
        m.put('W', new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,1,0,1},{1,1,0,1,1},{1,0,0,0,1}});
        m.put('X', new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{1,0,0,1},{1,0,0,1}});
        m.put('Y', new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,1,0},{0,0,1,0}});
        m.put('Z', new int[][]{{1,1,1,1},{0,0,0,1},{0,1,1,0},{1,0,0,0},{1,1,1,1}});
        m.put('0', new int[][]{{1,1,1},{1,0,1},{1,0,1},{1,0,1},{1,1,1}});
        m.put('1', new int[][]{{0,1,0},{1,1,0},{0,1,0},{0,1,0},{1,1,1}});
        m.put('2', new int[][]{{1,1,1},{0,0,1},{1,1,1},{1,0,0},{1,1,1}});
        m.put('3', new int[][]{{1,1,1},{0,0,1},{1,1,1},{0,0,1},{1,1,1}});
        m.put('4', new int[][]{{1,0,1},{1,0,1},{1,1,1},{0,0,1},{0,0,1}});
        m.put('5', new int[][]{{1,1,1},{1,0,0},{1,1,1},{0,0,1},{1,1,1}});
        m.put('6', new int[][]{{1,1,1},{1,0,0},{1,1,1},{1,0,1},{1,1,1}});
        m.put('7', new int[][]{{1,1,1},{0,0,1},{0,1,0},{0,1,0},{0,1,0}});
        m.put('8', new int[][]{{1,1,1},{1,0,1},{1,1,1},{1,0,1},{1,1,1}});
        m.put('9', new int[][]{{1,1,1},{1,0,1},{1,1,1},{0,0,1},{1,1,1}});
        m.put('/', new int[][]{{0,0,1},{0,0,1},{0,1,0},{1,0,0},{1,0,0}});
        m.put('-', new int[][]{{0,0,0},{0,0,0},{1,1,1},{0,0,0},{0,0,0}});
        m.put('_', new int[][]{{0,0,0},{0,0,0},{0,0,0},{0,0,0},{1,1,1}});
        m.put(':', new int[][]{{0,0},{1,0},{0,0},{1,0},{0,0}});
        m.put('.', new int[][]{{0},{0},{0},{0},{1}});
        m.put(',', new int[][]{{0},{0},{0},{1},{1}});
        m.put('<', new int[][]{{0,0,1},{0,1,0},{1,0,0},{0,1,0},{0,0,1}});
        m.put('>', new int[][]{{1,0,0},{0,1,0},{0,0,1},{0,1,0},{1,0,0}});
        m.put('[', new int[][]{{1,1},{1,0},{1,0},{1,0},{1,1}});
        m.put(']', new int[][]{{1,1},{0,1},{0,1},{0,1},{1,1}});
        m.put('@', new int[][]{{1,1,1},{1,0,1},{1,1,1},{1,0,0},{1,1,1}});
        m.put('!', new int[][]{{1},{1},{1},{0},{1}});
        m.put('?', new int[][]{{1,1,1},{0,0,1},{0,1,0},{0,0,0},{0,1,0}});
        m.put(' ', new int[][]{{0,0,0},{0,0,0},{0,0,0},{0,0,0},{0,0,0}});
        m.put('(', new int[][]{{0,1},{1,0},{1,0},{1,0},{0,1}});
        m.put(')', new int[][]{{1,0},{0,1},{0,1},{0,1},{1,0}});
        m.put('+', new int[][]{{0,0,0},{0,1,0},{1,1,1},{0,1,0},{0,0,0}});
        m.put('%', new int[][]{{1,0,1},{0,0,1},{0,1,0},{1,0,0},{1,0,1}});
        m.put('=', new int[][]{{0,0,0},{1,1,1},{0,0,0},{1,1,1},{0,0,0}});
        m.put('|', new int[][]{{1},{1},{1},{1},{1}});
        m.put('Æ', new int[][]{{0,1,1,1},{1,0,1,0},{1,1,1,0},{1,0,1,0},{1,0,1,1}});
        m.put('Ø', new int[][]{{0,1,1,1},{1,0,0,1},{1,0,1,1},{1,1,0,1},{1,1,1,0}});
        m.put('Å', new int[][]{{0,1,0},{1,0,1},{1,1,1},{1,0,1},{1,0,1}});
        m.put('\'', new int[][]{{1},{1},{0},{0},{0}});
        m.put('"', new int[][]{{1,0,1},{1,0,1},{0,0,0},{0,0,0},{0,0,0}});
        return m;
    }
}
