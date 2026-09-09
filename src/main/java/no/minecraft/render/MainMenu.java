package no.minecraft.render;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import no.minecraft.world.save.WorldInfo;
import no.minecraft.world.save.WorldSaveManager;
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

    public enum Screen {
        TITLE,
        WORLD_SELECT,
        CREATE_WORLD,
        CONFIRM_DELETE
    }

    private Screen currentScreen = Screen.TITLE;
    private boolean inMenu = true;
    private boolean gameStarted = false;
    private boolean quitRequested = false;
    private boolean openOptionsRequested = false;

    // World Save & Selection state
    private List<WorldInfo> worlds = new ArrayList<>();
    private int selectedWorldIndex = -1;
    private int scrollOffset = 0;
    private long lastWorldClickTime = 0;
    private int lastClickedWorldIndex = -1;

    private WorldInfo activeWorldInfo = null;
    private boolean worldStartRequested = false;
    private WorldInfo pendingDeleteWorld = null;

    // Create World inputs
    private String createWorldName = "Ny verden";
    private String createSeedInput = "";
    private GameMode createGameMode = GameMode.SURVIVAL;
    private int focusedField = 1; // 1: worldName, 2: seed
    private float cursorBlinkTimer = 0.0f;

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

        refreshWorlds();
    }

    public void refreshWorlds() {
        this.worlds = WorldSaveManager.listWorlds();
        if (selectedWorldIndex >= worlds.size()) {
            selectedWorldIndex = worlds.isEmpty() ? -1 : 0;
        }
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
        if (inMenu) {
            this.currentScreen = Screen.TITLE;
            refreshWorlds();
        }
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }

    public void setCurrentScreen(Screen currentScreen) {
        this.currentScreen = currentScreen;
        if (currentScreen == Screen.WORLD_SELECT) {
            refreshWorlds();
            if (!worlds.isEmpty() && selectedWorldIndex < 0) {
                selectedWorldIndex = 0;
            }
        }
    }

    public boolean isQuitRequested() {
        return quitRequested;
    }

    public void clearQuitRequested() {
        quitRequested = false;
    }

    public boolean isOpenOptionsRequested() {
        return openOptionsRequested;
    }

    public void clearOpenOptionsRequested() {
        openOptionsRequested = false;
    }

    public boolean isWorldStartRequested() {
        return worldStartRequested;
    }

    public void clearWorldStartRequested() {
        worldStartRequested = false;
    }

    public WorldInfo getActiveWorldInfo() {
        return activeWorldInfo;
    }

    public void setActiveWorldInfo(WorldInfo info) {
        this.activeWorldInfo = info;
    }

    public WorldInfo consumeActiveWorldInfo() {
        WorldInfo info = activeWorldInfo;
        worldStartRequested = false;
        return info;
    }

    public GameMode getSelectedMode() {
        if (activeWorldInfo != null) {
            return activeWorldInfo.getGameMode();
        }
        return createGameMode;
    }

    public void handleScroll(double yoffset) {
        if (!inMenu || currentScreen != Screen.WORLD_SELECT) return;
        if (yoffset > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (yoffset < 0) {
            int maxOffset = Math.max(0, worlds.size() - 4);
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        }
    }

    public void handleChar(char c) {
        if (!inMenu || currentScreen != Screen.CREATE_WORLD) return;
        if (c < 32 || c > 126 && c != 'Æ' && c != 'Ø' && c != 'Å' && c != 'æ' && c != 'ø' && c != 'å') return;

        if (focusedField == 1) {
            if (createWorldName.length() < 28) {
                createWorldName += c;
            }
        } else if (focusedField == 2) {
            if (createSeedInput.length() < 30) {
                createSeedInput += c;
            }
        }
    }

    public boolean handleKey(int key, int action) {
        if (!inMenu || (action != GLFW_PRESS && action != GLFW_REPEAT)) return false;

        if (currentScreen == Screen.CREATE_WORLD) {
            if (key == GLFW_KEY_BACKSPACE) {
                if (focusedField == 1 && !createWorldName.isEmpty()) {
                    createWorldName = createWorldName.substring(0, createWorldName.length() - 1);
                    return true;
                } else if (focusedField == 2 && !createSeedInput.isEmpty()) {
                    createSeedInput = createSeedInput.substring(0, createSeedInput.length() - 1);
                    return true;
                }
            }
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_TAB) {
                    focusedField = (focusedField == 1) ? 2 : 1;
                    return true;
                } else if (key == GLFW_KEY_ESCAPE) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    currentScreen = Screen.WORLD_SELECT;
                    return true;
                }
            }
        } else if (currentScreen == Screen.WORLD_SELECT) {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    currentScreen = Screen.TITLE;
                    return true;
                } else if (key == GLFW_KEY_UP) {
                    if (selectedWorldIndex > 0) {
                        selectedWorldIndex--;
                        if (selectedWorldIndex < scrollOffset) {
                            scrollOffset = selectedWorldIndex;
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                    return true;
                } else if (key == GLFW_KEY_DOWN) {
                    if (selectedWorldIndex < worlds.size() - 1) {
                        selectedWorldIndex++;
                        if (selectedWorldIndex >= scrollOffset + 4) {
                            scrollOffset = selectedWorldIndex - 3;
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                    return true;
                }
            }
        } else if (currentScreen == Screen.CONFIRM_DELETE) {
            if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                pendingDeleteWorld = null;
                currentScreen = Screen.WORLD_SELECT;
                return true;
            }
        }

        return false;
    }

    public boolean handleClick(double mx, double my, int button, int width, int height, World world, Player player) {
        if (!inMenu || button != GLFW_MOUSE_BUTTON_LEFT) return false;

        float p = no.minecraft.settings.GameSettings.getInstance().calculateGuiScale(width, height);
        boolean norwegian = no.minecraft.settings.GameSettings.getInstance().getLanguage() == no.minecraft.settings.GameSettings.Language.NORWEGIAN;

        if (currentScreen == Screen.TITLE) {
            float btnW = 180.0f * p;
            float btnH = 24.0f * p;
            float startX = (width - btnW) / 2.0f;
            float startY = height * 0.38f;
            float gap = 34.0f * p;

            // Button 0: Enkeltspiller (Singleplayer)
            float y0 = startY;
            if (mx >= startX && mx <= startX + btnW && my >= y0 && my <= y0 + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                currentScreen = Screen.WORLD_SELECT;
                refreshWorlds();
                if (!worlds.isEmpty() && selectedWorldIndex < 0) {
                    selectedWorldIndex = 0;
                }
                return false;
            }

            // Button 1: Innstillinger... (Options...)
            float y1 = startY + gap;
            if (mx >= startX && mx <= startX + btnW && my >= y1 && my <= y1 + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                openOptionsRequested = true;
                return false;
            }

            // Button 2: Avslutt (Quit Game)
            float y2 = startY + gap * 2.0f;
            if (mx >= startX && mx <= startX + btnW && my >= y2 && my <= y2 + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                quitRequested = true;
                return false;
            }

            return false;

        } else if (currentScreen == Screen.WORLD_SELECT) {
            float listW = 280.0f * p;
            float listH = 34.0f * p;
            float listX = (width - listW) / 2.0f;
            float listStartY = 45.0f * p;
            float listGap = 38.0f * p;

            int maxVisible = 4;
            int visibleCount = Math.min(maxVisible, Math.max(0, worlds.size() - scrollOffset));

            // Check click on world entries
            for (int i = 0; i < visibleCount; i++) {
                int worldIdx = scrollOffset + i;
                float wy = listStartY + i * listGap;
                if (mx >= listX && mx <= listX + listW && my >= wy && my <= wy + listH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    long now = System.currentTimeMillis();
                    if (selectedWorldIndex == worldIdx && (now - lastWorldClickTime) < 400) {
                        // Double click: Launch world!
                        WorldInfo info = worlds.get(selectedWorldIndex);
                        if (WorldSaveManager.loadWorld(world, player, info)) {
                            this.activeWorldInfo = info;
                            this.worldStartRequested = true;
                            this.inMenu = false;
                            this.gameStarted = true;
                            return true;
                        }
                    }
                    selectedWorldIndex = worldIdx;
                    lastWorldClickTime = now;
                    return false;
                }
            }

            // Bottom Buttons (2 rows of 2 buttons, like Minecraft 1.16.1)
            float btnW = 135.0f * p;
            float btnH = 22.0f * p;
            float spacing = 10.0f * p;
            float row1Y = height - 58.0f * p;
            float row2Y = height - 32.0f * p;
            float col1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float col2X = (width / 2.0f) + (spacing / 2.0f);

            // Row 1, Col 1: Spill valgt verden (Play Selected World)
            if (mx >= col1X && mx <= col1X + btnW && my >= row1Y && my <= row1Y + btnH) {
                if (selectedWorldIndex >= 0 && selectedWorldIndex < worlds.size()) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    WorldInfo info = worlds.get(selectedWorldIndex);
                    if (WorldSaveManager.loadWorld(world, player, info)) {
                        this.activeWorldInfo = info;
                        this.worldStartRequested = true;
                        this.inMenu = false;
                        this.gameStarted = true;
                        return true;
                    }
                }
                return false;
            }

            // Row 1, Col 2: Lag ny verden (Create New World)
            if (mx >= col2X && mx <= col2X + btnW && my >= row1Y && my <= row1Y + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                createWorldName = norwegian ? "Ny verden" : "New World";
                createSeedInput = "";
                createGameMode = GameMode.SURVIVAL;
                focusedField = 1;
                currentScreen = Screen.CREATE_WORLD;
                return false;
            }

            // Row 2, Col 1: Slett (Delete)
            if (mx >= col1X && mx <= col1X + btnW && my >= row2Y && my <= row2Y + btnH) {
                if (selectedWorldIndex >= 0 && selectedWorldIndex < worlds.size()) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    pendingDeleteWorld = worlds.get(selectedWorldIndex);
                    currentScreen = Screen.CONFIRM_DELETE;
                }
                return false;
            }

            // Row 2, Col 2: Avbryt (Cancel)
            if (mx >= col2X && mx <= col2X + btnW && my >= row2Y && my <= row2Y + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                currentScreen = Screen.TITLE;
                return false;
            }

            return false;

        } else if (currentScreen == Screen.CREATE_WORLD) {
            float boxW = 200.0f * p;
            float boxH = 22.0f * p;
            float boxX = (width - boxW) / 2.0f;

            float nameY = 58.0f * p;
            float modeY = 98.0f * p;
            float seedY = 158.0f * p;

            // Click name text box
            if (mx >= boxX && mx <= boxX + boxW && my >= nameY && my <= nameY + boxH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                focusedField = 1;
                return false;
            }

            // Click game mode toggle button
            if (mx >= boxX && mx <= boxX + boxW && my >= modeY && my <= modeY + boxH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                if (createGameMode == GameMode.SURVIVAL) {
                    createGameMode = GameMode.CREATIVE;
                } else if (createGameMode == GameMode.CREATIVE) {
                    createGameMode = GameMode.HARDCORE;
                } else {
                    createGameMode = GameMode.SURVIVAL;
                }
                return false;
            }

            // Click seed text box
            if (mx >= boxX && mx <= boxX + boxW && my >= seedY && my <= seedY + boxH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                focusedField = 2;
                return false;
            }

            // Bottom Buttons: Create New World & Cancel
            float btnW = 140.0f * p;
            float btnH = 22.0f * p;
            float spacing = 10.0f * p;
            float btnY = height - 42.0f * p;
            float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float b2X = (width / 2.0f) + (spacing / 2.0f);

            // Create New World button
            if (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                WorldInfo info = WorldSaveManager.createNewWorld(createWorldName, createSeedInput, createGameMode, world, player);
                this.activeWorldInfo = info;
                this.worldStartRequested = true;
                this.inMenu = false;
                this.gameStarted = true;
                return true;
            }

            // Cancel button
            if (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                currentScreen = Screen.WORLD_SELECT;
                return false;
            }

            return false;

        } else if (currentScreen == Screen.CONFIRM_DELETE) {
            float btnW = 130.0f * p;
            float btnH = 22.0f * p;
            float spacing = 14.0f * p;
            float btnY = height * 0.58f;
            float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float b2X = (width / 2.0f) + (spacing / 2.0f);

            // Confirm Delete button
            if (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                if (pendingDeleteWorld != null) {
                    WorldSaveManager.deleteWorld(pendingDeleteWorld);
                    pendingDeleteWorld = null;
                }
                refreshWorlds();
                if (selectedWorldIndex >= worlds.size()) {
                    selectedWorldIndex = worlds.isEmpty() ? -1 : 0;
                }
                currentScreen = Screen.WORLD_SELECT;
                return false;
            }

            // Cancel button
            if (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                pendingDeleteWorld = null;
                currentScreen = Screen.WORLD_SELECT;
                return false;
            }

            return false;
        }

        return false;
    }

    public void render(int width, int height, float mx, float my, TextureAtlas atlas) {
        if (!inMenu) return;

        cursorBlinkTimer = (cursorBlinkTimer + 0.035f) % 1.0f;
        boolean blink = cursorBlinkTimer < 0.5f;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -1, 1);
        shader.bind();
        shader.setUniform("uOrtho", ortho);

        List<Float> geom = new ArrayList<>();
        List<Float> tex = new ArrayList<>();
        List<Float> overlayGeom = new ArrayList<>();

        float p = no.minecraft.settings.GameSettings.getInstance().calculateGuiScale(width, height);
        boolean norwegian = no.minecraft.settings.GameSettings.getInstance().getLanguage() == no.minecraft.settings.GameSettings.Language.NORWEGIAN;

        // 1. Panoramic dirt tile background
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

        if (currentScreen == Screen.TITLE) {
            drawMinecraftTitle(overlayGeom, "MINECRAFT", width / 2.0f, height * 0.16f, p * 1.6f);
            drawModeSelectSubtitle(overlayGeom, "JAVA 1.16.1 CLONE", width / 2.0f, height * 0.28f, p * 0.85f);

            float btnW = 180.0f * p;
            float btnH = 24.0f * p;
            float startX = (width - btnW) / 2.0f;
            float startY = height * 0.38f;
            float gap = 34.0f * p;

            String[] titles = {
                    norwegian ? "ENKELTSPILLER" : "SINGLEPLAYER",
                    norwegian ? "INNSTILLINGER..." : "OPTIONS...",
                    norwegian ? "AVSLUTT" : "QUIT GAME"
            };
            String[] descs = {
                    norwegian ? "Velg verden eller lag ny fra seed" : "Choose a world or create new from seed",
                    norwegian ? "Grafikk, kontroller, lyd og sprak" : "Video, controls, sound and language",
                    norwegian ? "Avslutt spillet og lukk vinduet" : "Quit the game and close window"
            };
            int[] iconTiles = {
                    BlockType.GRASS.getTexture(BlockType.Face.TOP),
                    BlockType.CRAFTING_TABLE.getTexture(BlockType.Face.TOP),
                    BlockType.STONE_SWORD.getTexture(BlockType.Face.TOP)
            };

            for (int i = 0; i < 3; i++) {
                float by = startY + i * gap;
                boolean hovered = (mx >= startX && mx <= startX + btnW && my >= by && my <= by + btnH);

                drawMinecraftMenuButton(geom, startX, by, btnW, btnH, hovered, p);

                float[] uv = TextureAtlas.getUVs(iconTiles[i]);
                addRect(tex, startX + 6.0f * p, by + 4.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);

                drawButtonLabel(overlayGeom, titles[i], startX + 28.0f * p, by + 6.0f * p, p * 0.9f, 1.0f, 1.0f, 1.0f);
                drawSmallDescription(overlayGeom, descs[i], startX + 28.0f * p, by + 14.5f * p, p * 0.55f);
            }

            drawSmallDescription(overlayGeom, norwegian ? "Minecraft Java Edition 1.16.1 Clone" : "Minecraft Java Edition 1.16.1 Clone", width / 2.0f - 90.0f * p, height - 16.0f * p, p * 0.65f);

        } else if (currentScreen == Screen.WORLD_SELECT) {
            // Header
            drawCenteredButtonLabel(overlayGeom, norwegian ? "VELG VERDEN" : "SELECT WORLD", width / 2.0f, 18.0f * p, p * 1.0f, 1.0f, 1.0f, 1.0f);

            float listW = 280.0f * p;
            float listH = 34.0f * p;
            float listX = (width - listW) / 2.0f;
            float listStartY = 45.0f * p;
            float listGap = 38.0f * p;

            if (worlds.isEmpty()) {
                drawCenteredButtonLabel(overlayGeom, norwegian ? "INGEN VERDENER FUNNET" : "NO WORLDS FOUND", width / 2.0f, height * 0.35f, p * 0.85f, 0.7f, 0.7f, 0.7f);
                drawCenteredButtonLabel(overlayGeom, norwegian ? "TRYKK 'LAG NY VERDEN' FOR A STARTE" : "CLICK 'CREATE NEW WORLD' TO START", width / 2.0f, height * 0.42f, p * 0.65f, 0.6f, 0.6f, 0.6f);
            } else {
                int maxVisible = 4;
                int visibleCount = Math.min(maxVisible, Math.max(0, worlds.size() - scrollOffset));

                for (int i = 0; i < visibleCount; i++) {
                    int worldIdx = scrollOffset + i;
                    WorldInfo wi = worlds.get(worldIdx);
                    float wy = listStartY + i * listGap;
                    boolean isSelected = (worldIdx == selectedWorldIndex);
                    boolean isHovered = (mx >= listX && mx <= listX + listW && my >= wy && my <= wy + listH);

                    // World Slot Card Background
                    float fillR = isSelected ? 0.15f : (isHovered ? 0.10f : 0.05f);
                    float fillG = isSelected ? 0.15f : (isHovered ? 0.10f : 0.05f);
                    float fillB = isSelected ? 0.15f : (isHovered ? 0.10f : 0.05f);

                    // Border
                    if (isSelected) {
                        addRect(geom, listX, wy, listW, listH, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f); // White outline
                        addRect(geom, listX + p, wy + p, listW - 2 * p, listH - 2 * p, 0, 0, 0, 0, fillR, fillG, fillB, 0.95f);
                    } else if (isHovered) {
                        addRect(geom, listX, wy, listW, listH, 0, 0, 0, 0, 0.5f, 0.5f, 0.5f, 1.0f); // Gray outline
                        addRect(geom, listX + p, wy + p, listW - 2 * p, listH - 2 * p, 0, 0, 0, 0, fillR, fillG, fillB, 0.9f);
                    } else {
                        addRect(geom, listX, wy, listW, listH, 0, 0, 0, 0, 0.2f, 0.2f, 0.2f, 1.0f);
                        addRect(geom, listX + p, wy + p, listW - 2 * p, listH - 2 * p, 0, 0, 0, 0, fillR, fillG, fillB, 0.85f);
                    }

                    // World Icon: Grass block or Bedrock for Hardcore
                    int iconTile = (wi.getGameMode() == GameMode.HARDCORE)
                            ? BlockType.STONE_SWORD.getTexture(BlockType.Face.TOP)
                            : BlockType.GRASS.getTexture(BlockType.Face.TOP);
                    float[] uvs = TextureAtlas.getUVs(iconTile);
                    addRect(tex, listX + 6.0f * p, wy + 5.0f * p, 24.0f * p, 24.0f * p, uvs[0], uvs[1], uvs[2], uvs[3], 1, 1, 1, 1);

                    // Line 1: World Name (bold white)
                    drawButtonLabel(overlayGeom, wi.getName(), listX + 36.0f * p, wy + 5.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                    // Line 2: Folder name & Last Played (gray)
                    String line2 = wi.getFolderName() + " (" + wi.getFormattedDate() + ")";
                    drawSmallDescription(overlayGeom, line2, listX + 36.0f * p, wy + 14.5f * p, p * 0.52f);

                    // Line 3: Mode and Version (gray/yellow)
                    String line3 = wi.getModeDisplayName(norwegian) + ", 1.16.1";
                    drawSmallDescription(overlayGeom, line3, listX + 36.0f * p, wy + 23.0f * p, p * 0.52f);
                }
            }

            // Bottom Buttons
            float btnW = 135.0f * p;
            float btnH = 22.0f * p;
            float spacing = 10.0f * p;
            float row1Y = height - 58.0f * p;
            float row2Y = height - 32.0f * p;
            float col1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float col2X = (width / 2.0f) + (spacing / 2.0f);

            boolean hasSelection = (selectedWorldIndex >= 0 && selectedWorldIndex < worlds.size());

            // 1. Play Selected World
            boolean h1 = hasSelection && (mx >= col1X && mx <= col1X + btnW && my >= row1Y && my <= row1Y + btnH);
            drawMenuButtonOrDisabled(geom, col1X, row1Y, btnW, btnH, h1, hasSelection, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "SPILL VALGT VERDEN" : "PLAY SELECTED WORLD",
                    col1X + btnW / 2.0f, row1Y + 7.0f * p, p * 0.75f,
                    hasSelection ? 1.0f : 0.45f, hasSelection ? 1.0f : 0.45f, hasSelection ? 1.0f : 0.45f);

            // 2. Create New World
            boolean h2 = (mx >= col2X && mx <= col2X + btnW && my >= row1Y && my <= row1Y + btnH);
            drawMinecraftMenuButton(geom, col2X, row1Y, btnW, btnH, h2, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "LAG NY VERDEN" : "CREATE NEW WORLD",
                    col2X + btnW / 2.0f, row1Y + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

            // 3. Delete
            boolean h3 = hasSelection && (mx >= col1X && mx <= col1X + btnW && my >= row2Y && my <= row2Y + btnH);
            drawMenuButtonOrDisabled(geom, col1X, row2Y, btnW, btnH, h3, hasSelection, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "SLETT" : "DELETE",
                    col1X + btnW / 2.0f, row2Y + 7.0f * p, p * 0.75f,
                    hasSelection ? 1.0f : 0.45f, hasSelection ? 0.35f : 0.45f, hasSelection ? 0.35f : 0.45f);

            // 4. Cancel
            boolean h4 = (mx >= col2X && mx <= col2X + btnW && my >= row2Y && my <= row2Y + btnH);
            drawMinecraftMenuButton(geom, col2X, row2Y, btnW, btnH, h4, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "AVBRYT" : "CANCEL",
                    col2X + btnW / 2.0f, row2Y + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

        } else if (currentScreen == Screen.CREATE_WORLD) {
            // Header
            drawCenteredButtonLabel(overlayGeom, norwegian ? "LAG NY VERDEN" : "CREATE NEW WORLD", width / 2.0f, 20.0f * p, p * 1.0f, 1.0f, 1.0f, 1.0f);

            float boxW = 200.0f * p;
            float boxH = 22.0f * p;
            float boxX = (width - boxW) / 2.0f;

            // Subtitle 1: Verdensnavn
            drawButtonLabel(overlayGeom, norwegian ? "VERDENSNAVN" : "WORLD NAME", boxX, 48.0f * p, p * 0.70f, 0.8f, 0.8f, 0.8f);

            // Input box 1: World Name
            float nameY = 58.0f * p;
            drawTextBox(geom, overlayGeom, boxX, nameY, boxW, boxH, createWorldName, "", focusedField == 1, blink, p);

            // Button: Game Mode
            float modeY = 98.0f * p;
            boolean modeHovered = (mx >= boxX && mx <= boxX + boxW && my >= modeY && my <= modeY + boxH);
            drawMinecraftMenuButton(geom, boxX, modeY, boxW, boxH, modeHovered, p);

            String modeLabel = norwegian
                    ? "SPILLMODUS: " + createGameMode.name()
                    : "GAME MODE: " + createGameMode.name();
            if (norwegian) {
                modeLabel = switch (createGameMode) {
                    case CREATIVE -> "SPILLMODUS: KREATIV";
                    case HARDCORE -> "SPILLMODUS: HARDCORE";
                    default -> "SPILLMODUS: OVERLEVELSE";
                };
            }
            drawCenteredButtonLabel(overlayGeom, modeLabel, boxX + boxW / 2.0f, modeY + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

            // Mode Description underneath
            String modeDesc = switch (createGameMode) {
                case CREATIVE -> norwegian ? "Uendelige ressurser, fri flyving og odelogg blokker" : "Infinite resources, flight and destroy blocks instantly";
                case HARDCORE -> norwegian ? "Ett liv, vanskeligste grad og ingen respawn" : "One life, locked hard difficulty and no respawn";
                default -> norwegian ? "Sok etter ressurser, lag verktoy, overlev natten" : "Gather resources, craft tools, survive the night";
            };
            drawCenteredButtonLabel(overlayGeom, modeDesc, width / 2.0f, modeY + 28.0f * p, p * 0.55f, 0.75f, 0.75f, 0.75f);

            // Subtitle 3: Seed for generator
            float seedLabelY = 146.0f * p;
            drawButtonLabel(overlayGeom, norwegian ? "FRO TIL VERDENSGENERERING" : "SEED FOR WORLD GENERATOR", boxX, seedLabelY, p * 0.70f, 0.8f, 0.8f, 0.8f);

            // Input box 2: Seed
            float seedY = 158.0f * p;
            drawTextBox(geom, overlayGeom, boxX, seedY, boxW, boxH, createSeedInput,
                    norwegian ? "La sta tomt for tilfeldig fro" : "Leave blank for a random seed",
                    focusedField == 2, blink, p);

            // Bottom Buttons
            float btnW = 140.0f * p;
            float btnH = 22.0f * p;
            float spacing = 10.0f * p;
            float btnY = height - 42.0f * p;
            float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float b2X = (width / 2.0f) + (spacing / 2.0f);

            boolean hCreate = (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH);
            drawMinecraftMenuButton(geom, b1X, btnY, btnW, btnH, hCreate, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "LAG NY VERDEN" : "CREATE NEW WORLD",
                    b1X + btnW / 2.0f, btnY + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

            boolean hCancel = (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH);
            drawMinecraftMenuButton(geom, b2X, btnY, btnW, btnH, hCancel, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "AVBRYT" : "CANCEL",
                    b2X + btnW / 2.0f, btnY + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

        } else if (currentScreen == Screen.CONFIRM_DELETE) {
            drawCenteredButtonLabel(overlayGeom, norwegian ? "ER DU SIKKER PA AT DU VIL SLETTE?" : "ARE YOU SURE YOU WANT TO DELETE?",
                    width / 2.0f, height * 0.32f, p * 0.95f, 1.0f, 1.0f, 1.0f);

            String wName = (pendingDeleteWorld != null) ? ("'" + pendingDeleteWorld.getName() + "'") : "denne verdenen";
            drawCenteredButtonLabel(overlayGeom, wName, width / 2.0f, height * 0.40f, p * 0.90f, 1.0f, 0.85f, 0.2f);

            drawCenteredButtonLabel(overlayGeom,
                    norwegian ? "Denne verdenen vil bli slettet for alltid! (Kan ikke angres)" : "This world will be lost forever! (A long time!)",
                    width / 2.0f, height * 0.48f, p * 0.65f, 0.9f, 0.35f, 0.35f);

            float btnW = 130.0f * p;
            float btnH = 22.0f * p;
            float spacing = 14.0f * p;
            float btnY = height * 0.58f;
            float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float b2X = (width / 2.0f) + (spacing / 2.0f);

            boolean hDel = (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH);
            drawMinecraftMenuButton(geom, b1X, btnY, btnW, btnH, hDel, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "SLETT" : "DELETE",
                    b1X + btnW / 2.0f, btnY + 7.0f * p, p * 0.75f, 1.0f, 0.3f, 0.3f);

            boolean hCan = (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH);
            drawMinecraftMenuButton(geom, b2X, btnY, btnW, btnH, hCan, p);
            drawCenteredButtonLabel(overlayGeom, norwegian ? "AVBRYT" : "CANCEL",
                    b2X + btnW / 2.0f, btnY + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);
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

    private void drawTextBox(List<Float> g, List<Float> og, float x, float y, float w, float h,
                             String text, String placeholder, boolean focused, boolean blink, float p) {
        // Border: white if focused, gray if not
        float bCol = focused ? 1.0f : 0.4f;
        addRect(g, x, y, w, h, 0, 0, 0, 0, bCol, bCol, bCol, 1.0f);
        // Fill: black
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, 0.04f, 0.04f, 0.04f, 1.0f);

        float textX = x + 6.0f * p;
        float textY = y + 7.0f * p;

        if (text.isEmpty() && !focused && !placeholder.isEmpty()) {
            drawButtonLabel(og, placeholder, textX, textY, p * 0.65f, 0.45f, 0.45f, 0.45f);
        } else {
            String renderStr = text + (focused && blink ? "_" : "");
            drawButtonLabel(og, renderStr, textX, textY, p * 0.75f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void drawMenuButtonOrDisabled(List<Float> g, float x, float y, float w, float h,
                                         boolean hovered, boolean enabled, float p) {
        if (!enabled) {
            // Disabled button: dark gray, no hover effect
            addRect(g, x, y, w, h, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f);
            addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, 0.18f, 0.18f, 0.18f, 1.0f);
            return;
        }
        drawMinecraftMenuButton(g, x, y, w, h, hovered, p);
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

    private void drawMinecraftTitle(List<Float> g, String text, float centerX, float centerY, float s) {
        float totalWidth = text.length() * (6 * s);
        float startX = centerX - totalWidth / 2.0f;

        for (int i = 0; i < text.length(); i++) {
            float px = startX + i * (6 * s);
            drawLetterBlock(g, text.charAt(i), px + s, centerY + s, s, 0.15f, 0.15f, 0.15f);
            drawLetterBlock(g, text.charAt(i), px, centerY, s, 0.82f, 0.82f, 0.82f);
        }
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

    private void drawCenteredButtonLabel(List<Float> g, String label, float cx, float startY, float s, float red, float green, float blue) {
        float totalWidth = label.length() * (6 * s);
        float startX = cx - totalWidth / 2.0f;
        drawButtonLabel(g, label, startX, startY, s, red, green, blue);
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
            case 'Q' -> new int[][]{{0,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,1,0},{0,1,0,1}};
            case 'R' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,1,0},{1,0,0,1}};
            case 'S' -> new int[][]{{0,1,1,1},{1,0,0,0},{0,1,1,0},{0,0,0,1},{1,1,1,0}};
            case 'T' -> new int[][]{{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0}};
            case 'U' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'V' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,0,0}};
            case 'W' -> new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,1,0,1},{1,1,0,1,1},{1,0,0,0,1}};
            case 'X' -> new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{1,0,0,1},{1,0,0,1}};
            case 'Y' -> new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,1,0},{0,0,1,0}};
            case 'Z' -> new int[][]{{1,1,1,1},{0,0,0,1},{0,1,1,0},{1,0,0,0},{1,1,1,1}};
            case '0' -> new int[][]{{1,1,1},{1,0,1},{1,0,1},{1,0,1},{1,1,1}};
            case '1' -> new int[][]{{0,1,0},{1,1,0},{0,1,0},{0,1,0},{1,1,1}};
            case '2' -> new int[][]{{1,1,1},{0,0,1},{1,1,1},{1,0,0},{1,1,1}};
            case '3' -> new int[][]{{1,1,1},{0,0,1},{1,1,1},{0,0,1},{1,1,1}};
            case '4' -> new int[][]{{1,0,1},{1,0,1},{1,1,1},{0,0,1},{0,0,1}};
            case '5' -> new int[][]{{1,1,1},{1,0,0},{1,1,1},{0,0,1},{1,1,1}};
            case '6' -> new int[][]{{1,1,1},{1,0,0},{1,1,1},{1,0,1},{1,1,1}};
            case '7' -> new int[][]{{1,1,1},{0,0,1},{0,0,1},{0,0,1},{0,0,1}};
            case '8' -> new int[][]{{1,1,1},{1,0,1},{1,1,1},{1,0,1},{1,1,1}};
            case '9' -> new int[][]{{1,1,1},{1,0,1},{1,1,1},{0,0,1},{1,1,1}};
            case '%' -> new int[][]{{1,0,1},{0,0,1},{0,1,0},{1,0,0},{1,0,1}};
            case ':' -> new int[][]{{0},{1},{0},{1},{0}};
            case '-' -> new int[][]{{0,0,0,0},{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}};
            case '_' -> new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{1,1,1,1}};
            case '!' -> new int[][]{{1},{1},{1},{0},{1}};
            case '?' -> new int[][]{{1,1,1},{0,0,1},{0,1,1},{0,0,0},{0,1,0}};
            case '(' -> new int[][]{{0,1},{1,0},{1,0},{1,0},{0,1}};
            case ')' -> new int[][]{{1,0},{0,1},{0,1},{0,1},{1,0}};
            case '/' -> new int[][]{{0,0,1},{0,0,1},{0,1,0},{1,0,0},{1,0,0}};
            case '+' -> new int[][]{{0,0,0},{0,1,0},{1,1,1},{0,1,0},{0,0,0}};
            case '\'' -> new int[][]{{1},{1},{0},{0},{0}};
            case '"' -> new int[][]{{1,0,1},{1,0,1},{0,0,0},{0,0,0},{0,0,0}};
            case ',' -> new int[][]{{0},{0},{0},{1},{1}};
            case '.' -> new int[][]{{0},{0},{0},{0},{1}};
            case '|' -> new int[][]{{1},{1},{1},{1},{1}};
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
