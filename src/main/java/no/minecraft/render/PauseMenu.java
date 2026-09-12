package no.minecraft.render;

import no.minecraft.advancement.AdvancementManager;
import no.minecraft.advancement.AdvancementManager.Advancement;
import no.minecraft.i18n.I18n;
import no.minecraft.player.GameMode;
import no.minecraft.settings.GameSettings;
import no.minecraft.sound.SoundManager;
import no.minecraft.world.BlockType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static no.minecraft.render.UiBatch.addRect;
import static no.minecraft.render.UiBatch.addVertex;

public class PauseMenu {
    public enum Screen {
        NONE,
        MAIN,
        OPTIONS,
        CONTROLS,
        ADVANCEMENTS
    }

    private Screen currentScreen = Screen.NONE;
    private final Shader shader;
    private final int vaoId;
    private final int vboId;

    // Controls rebinding state
    private int rebindingActionIndex = -1; // 0: Fwd, 1: Bwd, 2: Left, 3: Right, 4: Jump, 5: Sneak, 6: Inv

    // Callback flag when player chooses "Save and Quit to Title"
    private boolean quitToTitleRequested = false;
    private boolean fromTitle = false;

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

    public PauseMenu() {
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

    public boolean isOpen() {
        return currentScreen != Screen.NONE;
    }

    public void open() {
        currentScreen = Screen.MAIN;
        fromTitle = false;
        rebindingActionIndex = -1;
    }

    public void openOptionsFromTitle() {
        currentScreen = Screen.OPTIONS;
        fromTitle = true;
        rebindingActionIndex = -1;
    }

    public void close() {
        currentScreen = Screen.NONE;
        fromTitle = false;
        rebindingActionIndex = -1;
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }

    public boolean isQuitToTitleRequested() {
        return quitToTitleRequested;
    }

    public void clearQuitToTitleRequested() {
        quitToTitleRequested = false;
    }

    public boolean handleKey(int key, int action) {
        if (!isOpen() || action != GLFW_PRESS) return false;

        // If rebinding a key
        if (currentScreen == Screen.CONTROLS && rebindingActionIndex >= 0) {
            GameSettings gs = GameSettings.getInstance();
            if (key != GLFW_KEY_ESCAPE) {
                switch (rebindingActionIndex) {
                    case 0 -> gs.keyForward = key;
                    case 1 -> gs.keyBackward = key;
                    case 2 -> gs.keyLeft = key;
                    case 3 -> gs.keyRight = key;
                    case 4 -> gs.keyJump = key;
                    case 5 -> gs.keySneak = key;
                    case 6 -> gs.keyInventory = key;
                }
            }
            SoundManager.getInstance().play("click");
            rebindingActionIndex = -1;
            return true;
        }

        // ESC navigates back or resumes
        if (key == GLFW_KEY_ESCAPE) {
            SoundManager.getInstance().play("click");
            if (currentScreen == Screen.MAIN) {
                close();
            } else if (currentScreen == Screen.CONTROLS) {
                currentScreen = Screen.OPTIONS;
            } else if (currentScreen == Screen.OPTIONS && fromTitle) {
                close();
            } else {
                currentScreen = Screen.MAIN;
            }
            return true;
        }

        return false;
    }

    public boolean handleClick(double mx, double my, int button, int width, int height) {
        if (!isOpen()) return false;
        if (button != GLFW_MOUSE_BUTTON_LEFT && button != GLFW_MOUSE_BUTTON_RIGHT) return false;
        if (button == GLFW_MOUSE_BUTTON_RIGHT && currentScreen != Screen.OPTIONS) return false;

        float p = GameSettings.getInstance().calculateGuiScale(width, height);

        if (currentScreen == Screen.MAIN) {
            return handleMainClick(mx, my, width, height, p);
        } else if (currentScreen == Screen.OPTIONS) {
            return handleOptionsClick(mx, my, width, height, p, button);
        } else if (currentScreen == Screen.CONTROLS) {
            return handleControlsClick(mx, my, width, height, p);
        } else if (currentScreen == Screen.ADVANCEMENTS) {
            return handleAdvancementsClick(mx, my, width, height, p);
        }

        return false;
    }

    private boolean handleMainClick(double mx, double my, int width, int height, float p) {
        float btnW = 200.0f * p;
        float halfW = 98.0f * p;
        float btnH = 20.0f * p;
        float cx = width / 2.0f;
        float startX = cx - btnW / 2.0f;
        float startY = height * 0.28f;
        float gap = 24.0f * p;

        // 1. Back to Game
        float y1 = startY;
        if (mx >= startX && mx <= startX + btnW && my >= y1 && my <= y1 + btnH) {
            SoundManager.getInstance().play("click");
            close();
            return true;
        }

        // 2. Advancements (left) & Statistics (right)
        float y2 = startY + gap;
        float leftX = startX;
        float rightX = startX + halfW + 4.0f * p;

        // Advancements button
        if (mx >= leftX && mx <= leftX + halfW && my >= y2 && my <= y2 + btnH) {
            SoundManager.getInstance().play("click");
            currentScreen = Screen.ADVANCEMENTS;
            return true;
        }

        // 3. Options... (left)
        float y3 = startY + gap * 2.0f;
        if (mx >= leftX && mx <= leftX + halfW && my >= y3 && my <= y3 + btnH) {
            SoundManager.getInstance().play("click");
            currentScreen = Screen.OPTIONS;
            return true;
        }

        // 4. Save and Quit to Title
        float y4 = startY + gap * 3.0f;
        if (mx >= startX && mx <= startX + btnW && my >= y4 && my <= y4 + btnH) {
            SoundManager.getInstance().play("click");
            quitToTitleRequested = true;
            close();
            return true;
        }

        return false;
    }

    private boolean handleOptionsClick(double mx, double my, int width, int height, float p, int button) {
        GameSettings s = GameSettings.getInstance();
        float btnW = 150.0f * p;
        float btnH = 20.0f * p;
        float cx = width / 2.0f;
        float leftX = cx - btnW - 4.0f * p;
        float rightX = cx + 4.0f * p;
        float startY = height * 0.20f;
        float gap = 25.0f * p;

        // Row 0: FOV (left) & Mouse Sensitivity (right)
        float y0 = startY;
        if (mx >= leftX && mx <= leftX + btnW && my >= y0 && my <= y0 + btnH) {
            SoundManager.getInstance().play("click");
            // Cycle FOV: 70 -> 80 -> 90 -> 100 -> 110 -> 60 -> 70
            float fov = s.getFov() + 10.0f;
            if (fov > 110.0f) fov = 60.0f;
            s.setFov(fov);
            return true;
        }
        if (mx >= rightX && mx <= rightX + btnW && my >= y0 && my <= y0 + btnH) {
            SoundManager.getInstance().play("click");
            // Cycle Sensitivity: 50% -> 75% -> 100% -> 125% -> 150% -> 200%
            float sens = s.getMouseSensitivity();
            if (sens < 0.08f) sens = 0.09f;
            else if (sens < 0.11f) sens = 0.12f;
            else if (sens < 0.14f) sens = 0.15f;
            else if (sens < 0.17f) sens = 0.18f;
            else if (sens < 0.22f) sens = 0.24f;
            else sens = 0.06f;
            s.setMouseSensitivity(sens);
            return true;
        }

        // Row 1: Render Distance (left) & Brightness (right)
        float y1 = startY + gap;
        if (mx >= leftX && mx <= leftX + btnW && my >= y1 && my <= y1 + btnH) {
            SoundManager.getInstance().play("click");
            int rd = (button == GLFW_MOUSE_BUTTON_RIGHT) ? s.getRenderDistance() - 1 : s.getRenderDistance() + 1;
            if (rd > 20) rd = 3;
            if (rd < 3) rd = 20;
            s.setRenderDistance(rd);
            return true;
        }
        if (mx >= rightX && mx <= rightX + btnW && my >= y1 && my <= y1 + btnH) {
            SoundManager.getInstance().play("click");
            s.setBrightness(s.getBrightness() > 0.5f ? 0.0f : 1.0f);
            return true;
        }

        // Row 2: Master Volume (left) & Controls... (right)
        float y2 = startY + gap * 2.0f;
        if (mx >= leftX && mx <= leftX + btnW && my >= y2 && my <= y2 + btnH) {
            SoundManager.getInstance().play("click");
            float vol = s.getSoundVolume() + 0.25f;
            if (vol > 1.01f) vol = 0.0f;
            s.setSoundVolume(vol);
            return true;
        }
        if (mx >= rightX && mx <= rightX + btnW && my >= y2 && my <= y2 + btnH) {
            SoundManager.getInstance().play("click");
            currentScreen = Screen.CONTROLS;
            return true;
        }

        // Row 3: Language (left) & GUI Scale (right)
        float y3 = startY + gap * 3.0f;
        if (mx >= leftX && mx <= leftX + btnW && my >= y3 && my <= y3 + btnH) {
            SoundManager.getInstance().play("click");
            s.nextLanguage();
            return true;
        }
        if (mx >= rightX && mx <= rightX + btnW && my >= y3 && my <= y3 + btnH) {
            SoundManager.getInstance().play("click");
            int next = (button == GLFW_MOUSE_BUTTON_RIGHT) ? s.getGuiScale() - 1 : s.getGuiScale() + 1;
            if (next > 4) next = 0;
            if (next < 0) next = 4;
            s.setGuiScale(next);
            return true;
        }

        // Bottom: Done (center)
        float doneW = 200.0f * p;
        float doneX = cx - doneW / 2.0f;
        float doneY = height * 0.85f;
        if (mx >= doneX && mx <= doneX + doneW && my >= doneY && my <= doneY + btnH) {
            SoundManager.getInstance().play("click");
            if (fromTitle) {
                close();
            } else {
                currentScreen = Screen.MAIN;
            }
            return true;
        }

        return false;
    }

    private boolean handleControlsClick(double mx, double my, int width, int height, float p) {
        float btnW = 100.0f * p;
        float btnH = 20.0f * p;
        float cx = width / 2.0f;
        float rightX = cx + 20.0f * p;
        float startY = height * 0.18f;
        float gap = 24.0f * p;

        for (int i = 0; i < 7; i++) {
            float by = startY + i * gap;
            if (mx >= rightX && mx <= rightX + btnW && my >= by && my <= by + btnH) {
                SoundManager.getInstance().play("click");
                rebindingActionIndex = i;
                return true;
            }
        }

        // Bottom: Done
        float doneW = 200.0f * p;
        float doneX = cx - doneW / 2.0f;
        float doneY = height * 0.86f;
        if (mx >= doneX && mx <= doneX + doneW && my >= doneY && my <= doneY + btnH) {
            SoundManager.getInstance().play("click");
            rebindingActionIndex = -1;
            currentScreen = Screen.OPTIONS;
            return true;
        }

        return false;
    }

    private boolean handleAdvancementsClick(double mx, double my, int width, int height, float p) {
        float btnW = 200.0f * p;
        float btnH = 20.0f * p;
        float cx = width / 2.0f;
        float doneX = cx - btnW / 2.0f;
        float doneY = height * 0.86f;

        if (mx >= doneX && mx <= doneX + btnW && my >= doneY && my <= doneY + btnH) {
            SoundManager.getInstance().play("click");
            currentScreen = Screen.MAIN;
            return true;
        }
        return false;
    }

    public void render(int width, int height, float mx, float my, TextureAtlas atlas) {
        if (!isOpen()) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -1, 1);
        shader.bind();
        shader.setUniform("uOrtho", ortho);

        List<Float> geom = new ArrayList<>();
        List<Float> tex = new ArrayList<>();
        List<Float> overlayGeom = new ArrayList<>();

        float p = GameSettings.getInstance().calculateGuiScale(width, height);

        // Background: Tinted translucent dark layer for pause menu (in-game world dimly visible underneath)
        if (currentScreen == Screen.MAIN) {
            addRect(geom, 0, 0, width, height, 0, 0, 0, 0, 0, 0, 0, 0.60f);
            renderMainScreen(geom, tex, overlayGeom, width, height, mx, my, p, atlas);
        } else {
            // Options / Controls / Advancements: Dirt background pattern as in Minecraft options screen
            int dirtTile = BlockType.DIRT.getTexture(BlockType.Face.TOP);
            float[] dirtUV = TextureAtlas.getUVs(dirtTile);
            float bgTileSize = 32.0f * p;
            for (float x = 0; x < width; x += bgTileSize) {
                for (float y = 0; y < height; y += bgTileSize) {
                    addRect(tex, x, y, bgTileSize, bgTileSize, dirtUV[0], dirtUV[1], dirtUV[2], dirtUV[3], 0.22f, 0.22f, 0.22f, 1.0f);
                }
            }
            addRect(geom, 0, 0, width, height, 0, 0, 0, 0, 0, 0, 0, 0.35f);

            if (currentScreen == Screen.OPTIONS) {
                renderOptionsScreen(geom, tex, overlayGeom, width, height, mx, my, p);
            } else if (currentScreen == Screen.CONTROLS) {
                renderControlsScreen(geom, tex, overlayGeom, width, height, mx, my, p);
            } else if (currentScreen == Screen.ADVANCEMENTS) {
                renderAdvancementsScreen(geom, tex, overlayGeom, width, height, mx, my, p, atlas);
            }
        }

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

    private void renderMainScreen(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                  int width, int height, float mx, float my, float p, TextureAtlas atlas) {
        float cx = width / 2.0f;

        // Title text: "Game Menu" / "Spillmeny"
        String title = I18n.get("pause.title");
        drawCenteredText(overlayGeom, title, cx, height * 0.16f, p * 1.3f, 1.0f, 1.0f, 1.0f);

        float btnW = 200.0f * p;
        float halfW = 98.0f * p;
        float btnH = 20.0f * p;
        float startX = cx - btnW / 2.0f;
        float startY = height * 0.28f;
        float gap = 24.0f * p;

        // Button 1: Back to Game
        float y1 = startY;
        boolean h1 = (mx >= startX && mx <= startX + btnW && my >= y1 && my <= y1 + btnH);
        drawMenuButton(geom, startX, y1, btnW, btnH, h1, p);
        String t1 = I18n.get("pause.back_to_game");
        drawCenteredButtonText(overlayGeom, t1, startX + btnW / 2.0f, y1 + 5.5f * p, p * 0.85f, h1);

        // Button 2: Advancements (left) & Statistics placeholder (right)
        float y2 = startY + gap;
        float leftX = startX;
        float rightX = startX + halfW + 4.0f * p;

        boolean hAdv = (mx >= leftX && mx <= leftX + halfW && my >= y2 && my <= y2 + btnH);
        drawMenuButton(geom, leftX, y2, halfW, btnH, hAdv, p);
        String tAdv = I18n.get("pause.advancements");
        drawCenteredButtonText(overlayGeom, tAdv, leftX + halfW / 2.0f, y2 + 5.5f * p, p * 0.75f, hAdv);

        // Right slot: Statistics (disabled aesthetic)
        drawMenuButton(geom, rightX, y2, halfW, btnH, false, p);
        String tStat = I18n.get("pause.statistics");
        drawCenteredButtonText(overlayGeom, tStat, rightX + halfW / 2.0f, y2 + 5.5f * p, p * 0.75f, false);

        // Button 3: Options... (left) & Empty / None on right (No Open to LAN)
        float y3 = startY + gap * 2.0f;
        boolean hOpt = (mx >= leftX && mx <= leftX + halfW && my >= y3 && my <= y3 + btnH);
        drawMenuButton(geom, leftX, y3, halfW, btnH, hOpt, p);
        String tOpt = I18n.get("pause.options");
        drawCenteredButtonText(overlayGeom, tOpt, leftX + halfW / 2.0f, y3 + 5.5f * p, p * 0.70f, hOpt);

        // Button 4: Save and Quit to Title
        float y4 = startY + gap * 3.0f;
        boolean hQuit = (mx >= startX && mx <= startX + btnW && my >= y4 && my <= y4 + btnH);
        drawMenuButton(geom, startX, y4, btnW, btnH, hQuit, p);
        String tQuit = I18n.get("pause.save_quit");
        drawCenteredButtonText(overlayGeom, tQuit, startX + btnW / 2.0f, y4 + 5.5f * p, p * 0.75f, hQuit);
    }

    private void renderOptionsScreen(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                     int width, int height, float mx, float my, float p) {
        GameSettings s = GameSettings.getInstance();
        float cx = width / 2.0f;

        drawCenteredText(overlayGeom, I18n.get("options.title"), cx, height * 0.08f, p * 1.3f, 1.0f, 1.0f, 1.0f);

        float btnW = 150.0f * p;
        float btnH = 20.0f * p;
        float leftX = cx - btnW - 4.0f * p;
        float rightX = cx + 4.0f * p;
        float startY = height * 0.20f;
        float gap = 25.0f * p;

        // Row 0: FOV & Sensitivity
        float y0 = startY;
        boolean h0L = (mx >= leftX && mx <= leftX + btnW && my >= y0 && my <= y0 + btnH);
        drawMenuButton(geom, leftX, y0, btnW, btnH, h0L, p);
        String fovStr = s.getFov() == 70.0f ? I18n.get("options.fov_normal") : (int) s.getFov() + "";
        drawCenteredButtonText(overlayGeom, I18n.format("options.fov", fovStr), leftX + btnW / 2.0f, y0 + 5.5f * p, p * 0.8f, h0L);

        boolean h0R = (mx >= rightX && mx <= rightX + btnW && my >= y0 && my <= y0 + btnH);
        drawMenuButton(geom, rightX, y0, btnW, btnH, h0R, p);
        int sensPercent = (int) (s.getMouseSensitivity() / 0.12f * 100.0f);
        String sensLabel = I18n.format("options.sensitivity", sensPercent);
        drawCenteredButtonText(overlayGeom, sensLabel, rightX + btnW / 2.0f, y0 + 5.5f * p, p * 0.75f, h0R);

        // Row 1: Render Distance & Brightness
        float y1 = startY + gap;
        boolean h1L = (mx >= leftX && mx <= leftX + btnW && my >= y1 && my <= y1 + btnH);
        drawMenuButton(geom, leftX, y1, btnW, btnH, h1L, p);
        String rdLabel = I18n.format("options.render_distance", s.getRenderDistance());
        drawCenteredButtonText(overlayGeom, rdLabel, leftX + btnW / 2.0f, y1 + 5.5f * p, p * 0.70f, h1L);

        boolean h1R = (mx >= rightX && mx <= rightX + btnW && my >= y1 && my <= y1 + btnH);
        drawMenuButton(geom, rightX, y1, btnW, btnH, h1R, p);
        String brightStr = s.getBrightness() > 0.5f ? I18n.get("options.bright") : I18n.get("options.moody");
        drawCenteredButtonText(overlayGeom, I18n.format("options.brightness", brightStr), rightX + btnW / 2.0f, y1 + 5.5f * p, p * 0.70f, h1R);

        // Row 2: Volume & Controls
        float y2 = startY + gap * 2.0f;
        boolean h2L = (mx >= leftX && mx <= leftX + btnW && my >= y2 && my <= y2 + btnH);
        drawMenuButton(geom, leftX, y2, btnW, btnH, h2L, p);
        int volPercent = (int) Math.round(s.getSoundVolume() * 100);
        String volStr = volPercent == 0 ? I18n.get("options.off") : volPercent + "%";
        drawCenteredButtonText(overlayGeom, I18n.format("options.volume", volStr), leftX + btnW / 2.0f, y2 + 5.5f * p, p * 0.75f, h2L);

        boolean h2R = (mx >= rightX && mx <= rightX + btnW && my >= y2 && my <= y2 + btnH);
        drawMenuButton(geom, rightX, y2, btnW, btnH, h2R, p);
        drawCenteredButtonText(overlayGeom, I18n.get("options.controls"), rightX + btnW / 2.0f, y2 + 5.5f * p, p * 0.75f, h2R);

        // Row 3: Language (left) & GUI Scale (right)
        float y3 = startY + gap * 3.0f;
        boolean h3L = (mx >= leftX && mx <= leftX + btnW && my >= y3 && my <= y3 + btnH);
        drawMenuButton(geom, leftX, y3, btnW, btnH, h3L, p);
        String langStr = I18n.format("options.language", s.getLanguage().getDisplayName());
        drawCenteredButtonText(overlayGeom, langStr, leftX + btnW / 2.0f, y3 + 5.5f * p, p * 0.65f, h3L);

        boolean h3R = (mx >= rightX && mx <= rightX + btnW && my >= y3 && my <= y3 + btnH);
        drawMenuButton(geom, rightX, y3, btnW, btnH, h3R, p);
        String guiScaleStr = s.getGuiScale() == 0 ? I18n.get("options.auto") : String.valueOf(s.getGuiScale());
        drawCenteredButtonText(overlayGeom, I18n.format("options.gui_scale", guiScaleStr), rightX + btnW / 2.0f, y3 + 5.5f * p, p * 0.75f, h3R);

        // Done button
        float doneW = 200.0f * p;
        float doneX = cx - doneW / 2.0f;
        float doneY = height * 0.85f;
        boolean hDone = (mx >= doneX && mx <= doneX + doneW && my >= doneY && my <= doneY + btnH);
        drawMenuButton(geom, doneX, doneY, doneW, btnH, hDone, p);
        drawCenteredButtonText(overlayGeom, I18n.get("pause.done"), doneX + doneW / 2.0f, doneY + 5.5f * p, p * 0.85f, hDone);
    }

    private void renderControlsScreen(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                      int width, int height, float mx, float my, float p) {
        GameSettings s = GameSettings.getInstance();
        float cx = width / 2.0f;

        drawCenteredText(overlayGeom, I18n.get("controls.title"), cx, height * 0.08f, p * 1.2f, 1.0f, 1.0f, 1.0f);

        String[] actions = {
                I18n.get("controls.forward"),
                I18n.get("controls.backward"),
                I18n.get("controls.left"),
                I18n.get("controls.right"),
                I18n.get("controls.jump"),
                I18n.get("controls.sneak"),
                I18n.get("controls.inventory")
        };
        int[] keys = {
                s.keyForward, s.keyBackward, s.keyLeft, s.keyRight, s.keyJump, s.keySneak, s.keyInventory
        };

        float labelX = cx - 140.0f * p;
        float rightX = cx + 20.0f * p;
        float btnW = 100.0f * p;
        float btnH = 20.0f * p;
        float startY = height * 0.18f;
        float gap = 24.0f * p;

        for (int i = 0; i < 7; i++) {
            float by = startY + i * gap;
            drawText(overlayGeom, actions[i], labelX, by + 6.0f * p, p * 0.75f, 0.85f, 0.85f, 0.85f);

            boolean hovered = (mx >= rightX && mx <= rightX + btnW && my >= by && my <= by + btnH);
            boolean isRebinding = (rebindingActionIndex == i);

            drawMenuButton(geom, rightX, by, btnW, btnH, hovered || isRebinding, p);

            String keyText = isRebinding ? "> ??? <" : GameSettings.getKeyName(keys[i]);
            float tr = isRebinding ? 1.0f : (hovered ? 1.0f : 0.9f);
            float tg = isRebinding ? 0.9f : (hovered ? 1.0f : 0.9f);
            float tb = isRebinding ? 0.3f : (hovered ? 0.6f : 0.9f);

            drawCenteredButtonText(overlayGeom, keyText, rightX + btnW / 2.0f, by + 5.5f * p, p * 0.75f, hovered);
        }

        // Done button
        float doneW = 200.0f * p;
        float doneX = cx - doneW / 2.0f;
        float doneY = height * 0.86f;
        boolean hDone = (mx >= doneX && mx <= doneX + doneW && my >= doneY && my <= doneY + btnH);
        drawMenuButton(geom, doneX, doneY, doneW, btnH, hDone, p);
        drawCenteredButtonText(overlayGeom, I18n.get("pause.done"), doneX + doneW / 2.0f, doneY + 5.5f * p, p * 0.85f, hDone);
    }

    private void renderAdvancementsScreen(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                          int width, int height, float mx, float my, float p, TextureAtlas atlas) {
        float cx = width / 2.0f;

        drawCenteredText(overlayGeom, I18n.get("pause.advancements_title"), cx, height * 0.08f, p * 1.2f, 1.0f, 0.95f, 0.3f);

        AdvancementManager am = AdvancementManager.getInstance();
        Advancement[] allAdv = Advancement.values();

        float cardW = 260.0f * p;
        float cardH = 28.0f * p;
        float cardX = cx - cardW / 2.0f;
        float startY = height * 0.18f;
        float gap = 34.0f * p;

        for (int i = 0; i < allAdv.length; i++) {
            Advancement adv = allAdv[i];
            boolean isUnlocked = am.isUnlocked(adv);
            float y = startY + i * gap;

            // Card background frame
            float bgAlpha = isUnlocked ? 0.75f : 0.40f;
            float br = isUnlocked ? 0.22f : 0.12f;
            float bg = isUnlocked ? 0.25f : 0.12f;
            float bb = isUnlocked ? 0.22f : 0.12f;

            // Bevel box
            drawMinecraftMenuButton(geom, cardX, y, cardW, cardH, isUnlocked, p);

            // Icon on left
            float[] uv = TextureAtlas.getUVs(adv.getIcon().getItemTexture());
            addRect(tex, cardX + 6.0f * p, y + 6.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3],
                    isUnlocked ? 1.0f : 0.45f, isUnlocked ? 1.0f : 0.45f, isUnlocked ? 1.0f : 0.45f, 1.0f);

            // Title
            String title = adv.getTitle() + " "
                    + (isUnlocked ? I18n.get("advancement.status.unlocked") : I18n.get("advancement.status.locked"));
            float tr = isUnlocked ? 1.0f : 0.55f;
            float tg = isUnlocked ? 0.85f : 0.55f;
            float tb = isUnlocked ? 0.2f : 0.55f;
            drawText(overlayGeom, title, cardX + 28.0f * p, y + 6.5f * p, p * 0.8f, tr, tg, tb);

            // Description
            drawText(overlayGeom, adv.getDescription(), cardX + 28.0f * p, y + 16.0f * p, p * 0.55f, 0.75f, 0.75f, 0.75f);
        }

        // Done button
        float doneW = 200.0f * p;
        float doneH = 20.0f * p;
        float doneX = cx - doneW / 2.0f;
        float doneY = height * 0.86f;
        boolean hDone = (mx >= doneX && mx <= doneX + doneW && my >= doneY && my <= doneY + doneH);
        drawMenuButton(geom, doneX, doneY, doneW, doneH, hDone, p);
        drawCenteredButtonText(overlayGeom, I18n.get("pause.back"), doneX + doneW / 2.0f, doneY + 5.5f * p, p * 0.85f, hDone);
    }

    private void drawMenuButton(List<Float> g, float x, float y, float w, float h, boolean hovered, float p) {
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

    private void drawMinecraftMenuButton(List<Float> g, float x, float y, float w, float h, boolean unlocked, float p) {
        float r = unlocked ? 0.25f : 0.18f;
        float gr = unlocked ? 0.28f : 0.18f;
        float b = unlocked ? 0.25f : 0.18f;

        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f);
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, r, gr, b, 0.95f);
        addRect(g, x + p, y + p, w - 2 * p, p, 0, 0, 0, 0, r + 0.15f, gr + 0.15f, b + 0.15f, 1.0f);
        addRect(g, x + p, y + p, p, h - 2 * p, 0, 0, 0, 0, r + 0.15f, gr + 0.15f, b + 0.15f, 1.0f);
        addRect(g, x + p, y + h - 2 * p, w - 2 * p, p, 0, 0, 0, 0, r - 0.12f, gr - 0.12f, b - 0.12f, 1.0f);
        addRect(g, x + w - 2 * p, y + p, p, h - 2 * p, 0, 0, 0, 0, r - 0.12f, gr - 0.12f, b - 0.12f, 1.0f);
    }

    private void drawCenteredButtonText(List<Float> g, String text, float centerX, float y, float s, boolean hovered) {
        float totalWidth = text.length() * (6.0f * s);
        float startX = centerX - totalWidth / 2.0f;
        float tr = hovered ? 1.0f : 0.9f;
        float tg = hovered ? 1.0f : 0.9f;
        float tb = hovered ? 0.6f : 0.9f;
        drawText(g, text, startX, y, s, tr, tg, tb);
    }

    private void drawCenteredText(List<Float> g, String text, float centerX, float y, float s, float r, float gr, float b) {
        float totalWidth = text.length() * (6.0f * s);
        float startX = centerX - totalWidth / 2.0f;
        drawText(g, text, startX, y, s, r, gr, b);
    }

    private void drawText(List<Float> g, String text, float startX, float startY, float s, float r, float gr, float b) {
        for (int i = 0; i < text.length(); i++) {
            float px = startX + i * (6.0f * s);
            // Drop shadow
            drawLetterBlock(g, text.charAt(i), px + s * 0.6f, startY + s * 0.6f, s, 0.12f, 0.12f, 0.12f);
            drawLetterBlock(g, text.charAt(i), px, startY, s, r, gr, b);
        }
    }

    private void drawLetterBlock(List<Float> g, char ch, float x, float y, float s, float r, float gr, float b) {
        UiBatch.drawLetterBlock(g, ch, x, y, s, r, gr, b, 1.0f);
    }

    // addRect/addVertex and the pixel font are shared via static imports from UiBatch

    private void drawVertices(List<Float> vertices) {
        UiBatch.drawVertices(vaoId, vboId, vertices);
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
