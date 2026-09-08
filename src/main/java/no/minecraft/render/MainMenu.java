package no.minecraft.render;

import no.minecraft.player.GameMode;
import no.minecraft.world.BlockType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class MainMenu {
    private final Shader shader;
    private final int vaoId;
    private final int vboId;

    private boolean inMenu = true;
    private boolean gameStarted = false;
    private GameMode selectedMode = GameMode.SURVIVAL;

    private static final String VERT_SRC = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aColor;

            uniform mat4 uOrtho;

            out vec2 vTexCoord;
            out vec4 vColor;

            void main() {
                gl_Position = uOrtho * vec4(aPos, 0.0, 1.0);
                vTexCoord = aTexCoord;
                vColor = aColor;
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core
            in vec2 vTexCoord;
            in vec4 vColor;

            uniform sampler2D uTexture;
            uniform int uUseTexture;

            out vec4 FragColor;

            void main() {
                if (uUseTexture == 1) {
                    vec4 tex = texture(uTexture, vTexCoord);
                    FragColor = tex * vColor;
                } else {
                    FragColor = vColor;
                }
            }
            """;

    public MainMenu() {
        this.shader = new Shader(VERT_SRC, FRAG_SRC);
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        int stride = (2 + 2 + 4) * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public boolean isInMenu() {
        return inMenu;
    }

    public void setInMenu(boolean inMenu) {
        this.inMenu = inMenu;
    }

    public GameMode getSelectedMode() {
        return selectedMode;
    }

    public boolean handleClick(double mx, double my, int button, int width, int height) {
        if (!inMenu || button != GLFW_MOUSE_BUTTON_LEFT) return false;

        float p = 2.4f;
        float btnW = 160.0f * p;
        float btnH = 24.0f * p;
        float startX = (width - btnW) / 2.0f;
        float startY = height * 0.38f;
        float gap = 34.0f * p;

        // Start Menu: 3 Game mode buttons (Creative, Survival, Hardcore)
        GameMode[] modes = {GameMode.CREATIVE, GameMode.SURVIVAL, GameMode.HARDCORE};

        for (int i = 0; i < 3; i++) {
            float by = startY + i * gap;
            if (mx >= startX && mx <= startX + btnW && my >= by && my <= by + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                this.selectedMode = modes[i];
                this.inMenu = false; // Start game in selected mode
                return true;
            }
        }

        return false;
    }

    public void render(int width, int height, float mx, float my, TextureAtlas atlas) {
        if (!inMenu) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -1, 1);
        shader.bind();
        shader.setUniform("uOrtho", ortho);

        List<Float> geom = new ArrayList<>();
        List<Float> tex = new ArrayList<>();
        List<Float> overlayGeom = new ArrayList<>();

        float p = 2.4f;

        // 1. Dark dirt-tiled panoramic background
        int dirtTile = BlockType.DIRT.getTexture(BlockType.Face.TOP);
        float[] dirtUV = TextureAtlas.getUVs(dirtTile);
        float bgTileSize = 32.0f * p;

        for (float x = 0; x < width; x += bgTileSize) {
            for (float y = 0; y < height; y += bgTileSize) {
                addRect(tex, x, y, bgTileSize, bgTileSize, dirtUV[0], dirtUV[1], dirtUV[2], dirtUV[3], 0.25f, 0.25f, 0.25f, 1.0f);
            }
        }

        // Vignette dark gradient overlay
        addRect(geom, 0, 0, width, height, 0, 0, 0, 0, 0, 0, 0, 0.45f);

        // 2. Title Text ("MINECRAFT")
        drawMinecraftTitle(overlayGeom, "MINECRAFT", width / 2.0f, height * 0.16f, p * 1.6f);
        drawModeSelectSubtitle(overlayGeom, "VELG MODUS FOR A STARTE", width / 2.0f, height * 0.28f, p * 0.9f);

        // 3. Menu Buttons
        float btnW = 160.0f * p;
        float btnH = 24.0f * p;
        float startX = (width - btnW) / 2.0f;
        float startY = height * 0.38f;
        float gap = 34.0f * p;

        String[] titles = {"CREATIVE", "SURVIVAL", "HARDCORE"};
        String[] descs = {
                "Uendelige ressurser, flyving & udodelighet",
                "Samle ressurser, lag verktoy, overlev natten",
                "Ett liv! Mobs er farlige, ingen respawn"
        };
        int[] iconTiles = {
                BlockType.GRASS.getTexture(BlockType.Face.TOP),
                BlockType.WOODEN_PICKAXE.getTexture(BlockType.Face.TOP),
                BlockType.STONE_SWORD.getTexture(BlockType.Face.TOP)
        };

        for (int i = 0; i < 3; i++) {
            float by = startY + i * gap;
            boolean hovered = (mx >= startX && mx <= startX + btnW && my >= by && my <= by + btnH);

            // Button frame with 3D bevel
            drawMinecraftMenuButton(geom, startX, by, btnW, btnH, hovered, p);

            // Icon on button left
            float[] uv = TextureAtlas.getUVs(iconTiles[i]);
            addRect(tex, startX + 6.0f * p, by + 4.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);

            // Button Label in pixel letters
            drawButtonLabel(overlayGeom, titles[i], startX + 28.0f * p, by + 6.0f * p, p * 0.9f, i == 2 ? 0.95f : 1.0f, i == 2 ? 0.3f : 1.0f, i == 2 ? 0.3f : 1.0f);

            // Description below label
            drawSmallDescription(overlayGeom, descs[i], startX + 28.0f * p, by + 14.5f * p, p * 0.55f);
        }

        // Footer version info
        drawSmallDescription(overlayGeom, "Minecraft Java Clone - Velg et alternativ med musen", width / 2.0f - 110.0f * p, height - 16.0f * p, p * 0.65f);

        // Draw calls
        shader.setUniform("uUseTexture", 0);
        drawVertices(geom);

        shader.setUniform("uUseTexture", 1);
        atlas.bind();
        drawVertices(tex);
        atlas.unbind();

        shader.setUniform("uUseTexture", 0);
        drawVertices(overlayGeom);

        shader.unbind();
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    private void drawMinecraftMenuButton(List<Float> g, float x, float y, float w, float h, boolean hovered, float p) {
        float r = hovered ? 0.42f : 0.32f;
        float gr = hovered ? 0.45f : 0.32f;
        float b = hovered ? 0.62f : 0.32f;

        // Outer border (Black)
        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f);
        // Base fill
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, r, gr, b, 1.0f);
        // Highlight top & left
        addRect(g, x + p, y + p, w - 2 * p, p, 0, 0, 0, 0, r + 0.25f, gr + 0.25f, b + 0.25f, 1.0f);
        addRect(g, x + p, y + p, p, h - 2 * p, 0, 0, 0, 0, r + 0.25f, gr + 0.25f, b + 0.25f, 1.0f);
        // Shadow bottom & right
        addRect(g, x + p, y + h - 2 * p, w - 2 * p, p, 0, 0, 0, 0, r - 0.2f, gr - 0.2f, b - 0.2f, 1.0f);
        addRect(g, x + w - 2 * p, y + p, p, h - 2 * p, 0, 0, 0, 0, r - 0.2f, gr - 0.2f, b - 0.2f, 1.0f);
    }

    private void drawMinecraftTitle(List<Float> g, float centerX, float centerY, float s) {
        drawMinecraftTitle(g, "MINECRAFT", centerX, centerY, s);
    }

    private void drawMinecraftTitle(List<Float> g, String text, float centerX, float centerY, float s) {
        float totalWidth = text.length() * (6 * s);
        float startX = centerX - totalWidth / 2.0f;

        for (int i = 0; i < text.length(); i++) {
            float px = startX + i * (6 * s);
            // Gray blocky font with drop shadow
            drawLetterBlock(g, text.charAt(i), px + s, centerY + s, s, 0.15f, 0.15f, 0.15f);
            drawLetterBlock(g, text.charAt(i), px, centerY, s, 0.82f, 0.82f, 0.82f);
        }
    }

    private void drawModeSelectSubtitle(List<Float> g, float centerX, float centerY, float s) {
        drawModeSelectSubtitle(g, "VELG MODUS FOR A STARTE", centerX, centerY, s);
    }

    private void drawModeSelectSubtitle(List<Float> g, String text, float centerX, float centerY, float s) {
        float totalWidth = text.length() * (6 * s);
        float startX = centerX - totalWidth / 2.0f;

        for (int i = 0; i < text.length(); i++) {
            float px = startX + i * (6 * s);
            drawLetterBlock(g, text.charAt(i), px + s * 0.5f, centerY + s * 0.5f, s, 0.18f, 0.18f, 0.05f);
            drawLetterBlock(g, text.charAt(i), px, centerY, s, 1.0f, 0.95f, 0.25f);
        }
    }

    private void drawButtonLabel(List<Float> g, String label, float startX, float startY, float s, float red, float green, float blue) {
        for (int i = 0; i < label.length(); i++) {
            float px = startX + i * (6 * s);
            drawLetterBlock(g, label.charAt(i), px + s * 0.6f, startY + s * 0.6f, s, 0.12f, 0.12f, 0.12f);
            drawLetterBlock(g, label.charAt(i), px, startY, s, red, green, blue);
        }
    }

    private void drawSmallDescription(List<Float> g, String desc, float startX, float startY, float s) {
        for (int i = 0; i < desc.length(); i++) {
            float px = startX + i * (5.5f * s);
            drawLetterBlock(g, desc.charAt(i), px + s * 0.5f, startY + s * 0.5f, s, 0.12f, 0.12f, 0.12f);
            drawLetterBlock(g, desc.charAt(i), px, startY, s, 0.82f, 0.82f, 0.75f);
        }
    }

    private void drawLetterBlock(List<Float> g, char ch, float x, float y, float s, float r, float gr, float b) {
        ch = Character.toUpperCase(ch);
        int[][] glyph = getGlyph(ch);
        if (glyph == null) return;

        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length; col++) {
                if (glyph[row][col] == 1) {
                    addRect(g, x + col * s, y + row * s, s, s, 0, 0, 0, 0, r, gr, b, 1.0f);
                }
            }
        }
    }

    private int[][] getGlyph(char c) {
        return switch (c) {
            case 'A' -> new int[][]{{0,1,1,0},{1,0,0,1},{1,1,1,1},{1,0,0,1},{1,0,0,1}};
            case 'B' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,0,1},{1,1,1,0}};
            case 'C' -> new int[][]{{0,1,1,1},{1,0,0,0},{1,0,0,0},{1,0,0,0},{0,1,1,1}};
            case 'D' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,1,1,0}};
            case 'E' -> new int[][]{{1,1,1,1},{1,0,0,0},{1,1,1,0},{1,0,0,0},{1,1,1,1}};
            case 'F' -> new int[][]{{1,1,1,1},{1,0,0,0},{1,1,1,0},{1,0,0,0},{1,0,0,0}};
            case 'G' -> new int[][]{{0,1,1,1},{1,0,0,0},{1,0,1,1},{1,0,0,1},{0,1,1,1}};
            case 'H' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,1,1,1},{1,0,0,1},{1,0,0,1}};
            case 'I' -> new int[][]{{1,1,1},{0,1,0},{0,1,0},{0,1,0},{1,1,1}};
            case 'J' -> new int[][]{{0,0,1,1},{0,0,0,1},{0,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'K' -> new int[][]{{1,0,0,1},{1,0,1,0},{1,1,0,0},{1,0,1,0},{1,0,0,1}};
            case 'L' -> new int[][]{{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,1,1,1}};
            case 'M' -> new int[][]{{1,0,0,0,1},{1,1,0,1,1},{1,0,1,0,1},{1,0,0,0,1},{1,0,0,0,1}};
            case 'N' -> new int[][]{{1,0,0,1},{1,1,0,1},{1,0,1,1},{1,0,0,1},{1,0,0,1}};
            case 'O' -> new int[][]{{0,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'P' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,0,0},{1,0,0,0}};
            case 'R' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,1,0},{1,0,0,1}};
            case 'S' -> new int[][]{{0,1,1,1},{1,0,0,0},{0,1,1,0},{0,0,0,1},{1,1,1,0}};
            case 'T' -> new int[][]{{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0}};
            case 'U' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'V' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,0,0}};
            case 'W' -> new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,1,0,1},{1,1,0,1,1},{1,0,0,0,1}};
            case 'X' -> new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{1,0,0,1},{1,0,0,1}};
            case 'Y' -> new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,1,0},{0,0,1,0}};
            case 'Z' -> new int[][]{{1,1,1,1},{0,0,0,1},{0,1,1,0},{1,0,0,0},{1,1,1,1}};
            case '-' -> new int[][]{{0,0,0,0},{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}};
            case '!' -> new int[][]{{1},{1},{1},{0},{1}};
            case ',' -> new int[][]{{0},{0},{0},{1},{1}};
            case '.' -> new int[][]{{0},{0},{0},{0},{1}};
            default -> null;
        };
    }

    private void addRect(List<Float> v, float x, float y, float w, float h,
                          float u0, float v0, float u1, float v1,
                          float r, float g, float b, float a) {
        addVertex(v, x, y, u0, v0, r, g, b, a);
        addVertex(v, x, y + h, u0, v1, r, g, b, a);
        addVertex(v, x + w, y + h, u1, v1, r, g, b, a);

        addVertex(v, x, y, u0, v0, r, g, b, a);
        addVertex(v, x + w, y + h, u1, v1, r, g, b, a);
        addVertex(v, x + w, y, u1, v0, r, g, b, a);
    }

    private void addVertex(List<Float> v, float x, float y, float u, float valV, float r, float g, float b, float a) {
        v.add(x); v.add(y); v.add(u); v.add(valV);
        v.add(r); v.add(g); v.add(b); v.add(a);
    }

    private void drawVertices(List<Float> vertices) {
        if (vertices.isEmpty()) return;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float f : vertices) buffer.put(f);
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, vertices.size() / 8);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
