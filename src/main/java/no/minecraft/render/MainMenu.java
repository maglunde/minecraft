package no.minecraft.render;

import no.minecraft.i18n.I18n;
import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import no.minecraft.world.save.WorldInfo;
import no.minecraft.world.save.WorldSaveManager;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static no.minecraft.render.UiBatch.addRect;
import static no.minecraft.render.UiBatch.addVertex;

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

    public enum Tab {
        GAME,
        WORLD,
        MORE
    }

    public enum Difficulty {
        PEACEFUL,
        EASY,
        NORMAL,
        HARD;

        public Difficulty next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String getDisplayName() {
            return I18n.get("difficulty." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum FocusedElement {
        NONE,
        // Game tab
        GAME_NAME,
        GAME_MODE,
        GAME_DIFFICULTY,
        GAME_CHEATS,
        GAME_RULES,
        // More tab
        MORE_SEED,
        MORE_STRUCTURES,
        MORE_WORLDTYPE,
        MORE_BONUSCHEST
    }

    private Screen currentScreen = Screen.TITLE;
    private Tab activeTab = Tab.GAME;
    private FocusedElement focusedElement = FocusedElement.GAME_NAME;

    private boolean inMenu = true;
    private boolean gameStarted = false;
    private boolean quitRequested = false;
    private boolean openOptionsRequested = false;

    // World Save & Selection state
    private List<WorldInfo> worlds = new ArrayList<>();
    private int selectedWorldIndex = -1;
    private int scrollOffset = 0;
    private long lastWorldClickTime = 0;

    private WorldInfo activeWorldInfo = null;
    private boolean worldStartRequested = false;
    private WorldInfo pendingDeleteWorld = null;

    // Create World inputs & options
    private String createWorldName = I18n.get("menu.default_world_name");
    private String createSeedInput = "";
    private GameMode createGameMode = GameMode.SURVIVAL;
    private Difficulty createDifficulty = Difficulty.NORMAL;
    private boolean allowCheats = false;
    private boolean generateStructures = true;
    private boolean bonusChest = false;

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
        if (worlds.isEmpty()) {
            selectedWorldIndex = -1;
        } else if (selectedWorldIndex < 0 || selectedWorldIndex >= worlds.size()) {
            selectedWorldIndex = 0;
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
            this.gameStarted = false;
            this.activeWorldInfo = null;
            refreshWorlds();
        }
    }

    public Screen getCurrentScreen() {
        return currentScreen;
    }

    public void setCurrentScreen(Screen currentScreen) {
        this.currentScreen = currentScreen;
        if (currentScreen == Screen.WORLD_SELECT) {
            this.activeTab = Tab.WORLD;
            refreshWorlds();
            if (!worlds.isEmpty() && selectedWorldIndex < 0) {
                selectedWorldIndex = 0;
            }
        } else if (currentScreen == Screen.CREATE_WORLD) {
            this.activeTab = Tab.GAME;
            this.focusedElement = FocusedElement.GAME_NAME;
            this.focusedField = 1;
        }
    }

    public Tab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
        if (tab == Tab.WORLD) {
            this.currentScreen = Screen.WORLD_SELECT;
            refreshWorlds();
            if (!worlds.isEmpty() && selectedWorldIndex < 0) {
                selectedWorldIndex = 0;
            }
        } else {
            this.currentScreen = Screen.CREATE_WORLD;
            if (tab == Tab.GAME) {
                this.focusedElement = FocusedElement.GAME_NAME;
                this.focusedField = 1;
            } else if (tab == Tab.MORE) {
                this.focusedElement = FocusedElement.MORE_SEED;
                this.focusedField = 2;
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
        if (!inMenu || (currentScreen != Screen.WORLD_SELECT && activeTab != Tab.WORLD)) return;
        if (yoffset > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (yoffset < 0) {
            int maxOffset = Math.max(0, worlds.size() - 4);
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        }
    }

    public void handleChar(char c) {
        if (!inMenu || (currentScreen != Screen.CREATE_WORLD && currentScreen != Screen.WORLD_SELECT)) return;
        if (activeTab == Tab.WORLD) return;
        if (c < 32 || (c > 126 && c != 'Æ' && c != 'Ø' && c != 'Å' && c != 'æ' && c != 'ø' && c != 'å')) return;

        if (activeTab == Tab.GAME && focusedElement == FocusedElement.GAME_NAME) {
            if (createWorldName.length() < 28) {
                createWorldName += c;
            }
        } else if (activeTab == Tab.MORE && focusedElement == FocusedElement.MORE_SEED) {
            if (createSeedInput.length() < 30) {
                createSeedInput += c;
            }
        }
    }

    public boolean handleKey(int key, int action) {
        if (!inMenu || (action != GLFW_PRESS && action != GLFW_REPEAT)) return false;

        boolean isWorldTab = (currentScreen == Screen.WORLD_SELECT || activeTab == Tab.WORLD);

        if (currentScreen == Screen.CONFIRM_DELETE) {
            if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                pendingDeleteWorld = null;
                currentScreen = Screen.WORLD_SELECT;
                activeTab = Tab.WORLD;
                return true;
            }
            return false;
        }

        if (currentScreen == Screen.TITLE) {
            return false;
        }

        // Tabbed Screen key handling (Game / World / More)
        if (key == GLFW_KEY_BACKSPACE) {
            if (activeTab == Tab.GAME && focusedElement == FocusedElement.GAME_NAME && !createWorldName.isEmpty()) {
                createWorldName = createWorldName.substring(0, createWorldName.length() - 1);
                return true;
            } else if (activeTab == Tab.MORE && focusedElement == FocusedElement.MORE_SEED && !createSeedInput.isEmpty()) {
                createSeedInput = createSeedInput.substring(0, createSeedInput.length() - 1);
                return true;
            }
        }

        if (action == GLFW_PRESS) {
            if (key == GLFW_KEY_ESCAPE) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                currentScreen = Screen.TITLE;
                return true;
            }

            if (key == GLFW_KEY_TAB) {
                cycleFocus();
                return true;
            }

            if (key == GLFW_KEY_UP) {
                if (isWorldTab) {
                    if (selectedWorldIndex > 0) {
                        selectedWorldIndex--;
                        if (selectedWorldIndex < scrollOffset) {
                            scrollOffset = selectedWorldIndex;
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                    return true;
                } else {
                    cycleFocusPrevious();
                    return true;
                }
            } else if (key == GLFW_KEY_DOWN) {
                if (isWorldTab) {
                    if (selectedWorldIndex < worlds.size() - 1) {
                        selectedWorldIndex++;
                        if (selectedWorldIndex >= scrollOffset + 4) {
                            scrollOffset = selectedWorldIndex - 3;
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                    return true;
                } else {
                    cycleFocus();
                    return true;
                }
            }
        }

        return false;
    }

    private void cycleFocus() {
        if (activeTab == Tab.GAME) {
            focusedElement = switch (focusedElement) {
                case GAME_NAME -> FocusedElement.GAME_MODE;
                case GAME_MODE -> FocusedElement.GAME_DIFFICULTY;
                case GAME_DIFFICULTY -> FocusedElement.GAME_CHEATS;
                case GAME_CHEATS -> FocusedElement.GAME_RULES;
                default -> FocusedElement.GAME_NAME;
            };
            focusedField = (focusedElement == FocusedElement.GAME_NAME) ? 1 : 0;
            no.minecraft.sound.SoundManager.getInstance().play("click");
        } else if (activeTab == Tab.MORE) {
            focusedElement = switch (focusedElement) {
                case MORE_SEED -> FocusedElement.MORE_STRUCTURES;
                case MORE_STRUCTURES -> FocusedElement.MORE_WORLDTYPE;
                case MORE_WORLDTYPE -> FocusedElement.MORE_BONUSCHEST;
                default -> FocusedElement.MORE_SEED;
            };
            focusedField = (focusedElement == FocusedElement.MORE_SEED) ? 2 : 0;
            no.minecraft.sound.SoundManager.getInstance().play("click");
        }
    }

    private void cycleFocusPrevious() {
        if (activeTab == Tab.GAME) {
            focusedElement = switch (focusedElement) {
                case GAME_RULES -> FocusedElement.GAME_CHEATS;
                case GAME_CHEATS -> FocusedElement.GAME_DIFFICULTY;
                case GAME_DIFFICULTY -> FocusedElement.GAME_MODE;
                case GAME_MODE -> FocusedElement.GAME_NAME;
                default -> FocusedElement.GAME_RULES;
            };
            focusedField = (focusedElement == FocusedElement.GAME_NAME) ? 1 : 0;
            no.minecraft.sound.SoundManager.getInstance().play("click");
        } else if (activeTab == Tab.MORE) {
            focusedElement = switch (focusedElement) {
                case MORE_BONUSCHEST -> FocusedElement.MORE_WORLDTYPE;
                case MORE_WORLDTYPE -> FocusedElement.MORE_STRUCTURES;
                case MORE_STRUCTURES -> FocusedElement.MORE_SEED;
                default -> FocusedElement.MORE_BONUSCHEST;
            };
            focusedField = (focusedElement == FocusedElement.MORE_SEED) ? 2 : 0;
            no.minecraft.sound.SoundManager.getInstance().play("click");
        }
    }

    public boolean handleClick(double mx, double my, int button, int width, int height, World world, Player player) {
        if (!inMenu || button != GLFW_MOUSE_BUTTON_LEFT) return false;

        float p = no.minecraft.settings.GameSettings.getInstance().calculateGuiScale(width, height);

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
                refreshWorlds();
                if (worlds.isEmpty()) {
                    setActiveTab(Tab.GAME);
                } else {
                    setActiveTab(Tab.WORLD);
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
                setActiveTab(Tab.WORLD);
                return false;
            }

            // Cancel button
            if (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                pendingDeleteWorld = null;
                setActiveTab(Tab.WORLD);
                return false;
            }

            return false;

        } else {
            // UNIFIED TABBED SCREEN (Game / World / More)
            // 1. Check Top Header Tabs
            float tabW = 68.0f * p;
            float tabH = 20.0f * p;
            float tabGap = 2.0f * p;
            float totalTabsW = 3 * tabW + 2 * tabGap;
            float startTabX = (width - totalTabsW) / 2.0f;
            float tabY = 5.0f * p;

            // Tab 0: Game
            if (mx >= startTabX && mx <= startTabX + tabW && my >= tabY && my <= tabY + tabH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                setActiveTab(Tab.GAME);
                return false;
            }

            // Tab 1: World
            float tab1X = startTabX + tabW + tabGap;
            if (mx >= tab1X && mx <= tab1X + tabW && my >= tabY && my <= tabY + tabH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                setActiveTab(Tab.WORLD);
                return false;
            }

            // Tab 2: More
            float tab2X = startTabX + 2 * (tabW + tabGap);
            if (mx >= tab2X && mx <= tab2X + tabW && my >= tabY && my <= tabY + tabH) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                setActiveTab(Tab.MORE);
                return false;
            }

            // 2. Tab-Specific Content Clicks
            if (activeTab == Tab.GAME) {
                float boxW = 210.0f * p;
                float boxH = 20.0f * p;
                float boxX = (width - boxW) / 2.0f;

                float nameY = 48.0f * p;
                float modeY = 74.0f * p;
                float diffY = 100.0f * p;
                float cheatsY = 126.0f * p;
                float rulesY = 152.0f * p;

                // Click World Name text box
                if (mx >= boxX && mx <= boxX + boxW && my >= nameY && my <= nameY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.GAME_NAME;
                    focusedField = 1;
                    return false;
                }

                // Click Game Mode button
                if (mx >= boxX && mx <= boxX + boxW && my >= modeY && my <= modeY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.GAME_MODE;
                    focusedField = 0;
                    if (createGameMode == GameMode.SURVIVAL) {
                        createGameMode = GameMode.CREATIVE;
                        allowCheats = true;
                    } else if (createGameMode == GameMode.CREATIVE) {
                        createGameMode = GameMode.HARDCORE;
                        createDifficulty = Difficulty.HARD;
                        allowCheats = false;
                    } else {
                        createGameMode = GameMode.SURVIVAL;
                    }
                    return false;
                }

                // Click Difficulty button
                if (mx >= boxX && mx <= boxX + boxW && my >= diffY && my <= diffY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.GAME_DIFFICULTY;
                    focusedField = 0;
                    if (createGameMode != GameMode.HARDCORE) {
                        createDifficulty = createDifficulty.next();
                    }
                    return false;
                }

                // Click Allow Cheats button
                if (mx >= boxX && mx <= boxX + boxW && my >= cheatsY && my <= cheatsY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.GAME_CHEATS;
                    focusedField = 0;
                    if (createGameMode != GameMode.HARDCORE) {
                        allowCheats = !allowCheats;
                    }
                    return false;
                }

                // Click Game Rules button (navigates to More tab)
                if (mx >= boxX && mx <= boxX + boxW && my >= rulesY && my <= rulesY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.GAME_RULES;
                    focusedField = 0;
                    setActiveTab(Tab.MORE);
                    return false;
                }

                // Bottom Buttons: Create New World & Cancel
                float btnW = 145.0f * p;
                float btnH = 20.0f * p;
                float spacing = 10.0f * p;
                float btnY = height - 29.0f * p;
                float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
                float b2X = (width / 2.0f) + (spacing / 2.0f);

                // Create New World button
                if (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    WorldInfo info = WorldSaveManager.createNewWorld(createWorldName, createSeedInput, createGameMode, world, player, bonusChest);
                    this.activeWorldInfo = info;
                    this.worldStartRequested = true;
                    this.inMenu = false;
                    this.gameStarted = true;
                    return true;
                }

                // Cancel button
                if (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    currentScreen = Screen.TITLE;
                    return false;
                }

            } else if (activeTab == Tab.WORLD) {
                float listW = 280.0f * p;
                float listH = 34.0f * p;
                float listX = (width - listW) / 2.0f;
                float listStartY = 36.0f * p;
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

                // Bottom Buttons on World Tab
                float btnW = 95.0f * p;
                float btnH = 20.0f * p;
                float spacing = 8.0f * p;
                float totalW = 3 * btnW + 2 * spacing;
                float startBtnX = (width - totalW) / 2.0f;
                float btnY = height - 29.0f * p;

                float b1X = startBtnX;
                float b2X = startBtnX + btnW + spacing;
                float b3X = startBtnX + 2 * (btnW + spacing);

                boolean hasSelection = (selectedWorldIndex >= 0 && selectedWorldIndex < worlds.size());

                // 1. Play Selected World
                if (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH) {
                    if (hasSelection) {
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

                // 2. Delete
                if (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH) {
                    if (hasSelection) {
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        pendingDeleteWorld = worlds.get(selectedWorldIndex);
                        currentScreen = Screen.CONFIRM_DELETE;
                    }
                    return false;
                }

                // 3. Cancel
                if (mx >= b3X && mx <= b3X + btnW && my >= btnY && my <= btnY + btnH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    currentScreen = Screen.TITLE;
                    return false;
                }

            } else if (activeTab == Tab.MORE) {
                float boxW = 210.0f * p;
                float boxH = 20.0f * p;
                float boxX = (width - boxW) / 2.0f;

                float seedY = 54.0f * p;
                float structY = 86.0f * p;
                float typeY = 112.0f * p;
                float chestY = 138.0f * p;

                // Click Seed text box
                if (mx >= boxX && mx <= boxX + boxW && my >= seedY && my <= seedY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.MORE_SEED;
                    focusedField = 2;
                    return false;
                }

                // Click Generate Structures button
                if (mx >= boxX && mx <= boxX + boxW && my >= structY && my <= structY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.MORE_STRUCTURES;
                    focusedField = 0;
                    generateStructures = !generateStructures;
                    return false;
                }

                // Click World Type button
                if (mx >= boxX && mx <= boxX + boxW && my >= typeY && my <= typeY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.MORE_WORLDTYPE;
                    focusedField = 0;
                    return false;
                }

                // Click Bonus Chest button
                if (mx >= boxX && mx <= boxX + boxW && my >= chestY && my <= chestY + boxH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    focusedElement = FocusedElement.MORE_BONUSCHEST;
                    focusedField = 0;
                    bonusChest = !bonusChest;
                    return false;
                }

                // Bottom Buttons: Create New World & Cancel
                float btnW = 145.0f * p;
                float btnH = 20.0f * p;
                float spacing = 10.0f * p;
                float btnY = height - 29.0f * p;
                float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
                float b2X = (width / 2.0f) + (spacing / 2.0f);

                // Create New World button
                if (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    WorldInfo info = WorldSaveManager.createNewWorld(createWorldName, createSeedInput, createGameMode, world, player, bonusChest);
                    this.activeWorldInfo = info;
                    this.worldStartRequested = true;
                    this.inMenu = false;
                    this.gameStarted = true;
                    return true;
                }

                // Cancel button
                if (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    currentScreen = Screen.TITLE;
                    return false;
                }
            }

            return false;
        }
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
            drawMinecraftTitle(overlayGeom, I18n.get("menu.title"), width / 2.0f, height * 0.16f, p * 1.6f);
            drawModeSelectSubtitle(overlayGeom, I18n.get("menu.subtitle"), width / 2.0f, height * 0.28f, p * 0.85f);

            float btnW = 180.0f * p;
            float btnH = 24.0f * p;
            float startX = (width - btnW) / 2.0f;
            float startY = height * 0.38f;
            float gap = 34.0f * p;

            String[] titles = {
                    I18n.get("menu.singleplayer"),
                    I18n.get("menu.options"),
                    I18n.get("menu.quit")
            };
            String[] descs = {
                    I18n.get("menu.singleplayer.description"),
                    I18n.get("menu.options.description"),
                    I18n.get("menu.quit.description")
            };
            int[] iconTiles = {
                    BlockType.GRASS.getTexture(BlockType.Face.TOP),
                    BlockType.CRAFTING_TABLE.getTexture(BlockType.Face.TOP),
                    BlockType.STONE_SWORD.getTexture(BlockType.Face.TOP)
            };

            for (int i = 0; i < 3; i++) {
                float by = startY + i * gap;
                boolean hovered = (mx >= startX && mx <= startX + btnW && my >= by && my <= by + btnH);

                drawMinecraftMenuButton(geom, startX, by, btnW, btnH, hovered, false, p);

                float[] uv = TextureAtlas.getUVs(iconTiles[i]);
                addRect(tex, startX + 6.0f * p, by + 4.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);

                drawButtonLabel(overlayGeom, titles[i], startX + 28.0f * p, by + 6.0f * p, p * 0.9f, 1.0f, 1.0f, 1.0f);
                drawSmallDescription(overlayGeom, descs[i], startX + 28.0f * p, by + 14.5f * p, p * 0.55f);
            }

            drawSmallDescription(overlayGeom, I18n.get("menu.version_footer"), width / 2.0f - 90.0f * p, height - 16.0f * p, p * 0.65f);

        } else if (currentScreen == Screen.CONFIRM_DELETE) {
            String wName = (pendingDeleteWorld != null) ? ("'" + pendingDeleteWorld.getName() + "'") : I18n.get("menu.delete.this_world");
            drawCenteredButtonLabel(overlayGeom, I18n.format("menu.delete.title", wName),
                    width / 2.0f, height * 0.32f, p * 0.95f, 1.0f, 1.0f, 1.0f);

            drawCenteredButtonLabel(overlayGeom, I18n.get("menu.delete.warning"),
                    width / 2.0f, height * 0.48f, p * 0.65f, 0.9f, 0.35f, 0.35f);

            float btnW = 130.0f * p;
            float btnH = 22.0f * p;
            float spacing = 14.0f * p;
            float btnY = height * 0.58f;
            float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
            float b2X = (width / 2.0f) + (spacing / 2.0f);

            boolean hDel = (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH);
            drawMinecraftMenuButton(geom, b1X, btnY, btnW, btnH, hDel, false, p);
            drawCenteredButtonLabel(overlayGeom, I18n.get("menu.delete"),
                    b1X + btnW / 2.0f, btnY + 7.0f * p, p * 0.75f, 1.0f, 0.3f, 0.3f);

            boolean hCan = (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH);
            drawMinecraftMenuButton(geom, b2X, btnY, btnW, btnH, hCan, false, p);
            drawCenteredButtonLabel(overlayGeom, I18n.get("menu.cancel"),
                    b2X + btnW / 2.0f, btnY + 7.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

        } else {
            // UNIFIED TABBED SCREEN (Game / World / More)
            // 1. Header & Footer Background Panels
            float headerH = 28.0f * p;
            float footerH = 34.0f * p;
            addRect(geom, 0, 0, width, headerH, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.65f);
            addRect(geom, 0, headerH - p, width, p, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);

            addRect(geom, 0, height - footerH, width, footerH, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.65f);
            addRect(geom, 0, height - footerH, width, p, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);

            // 2. Render Top Tabs
            float tabW = 68.0f * p;
            float tabH = 20.0f * p;
            float tabGap = 2.0f * p;
            float totalTabsW = 3 * tabW + 2 * tabGap;
            float startTabX = (width - totalTabsW) / 2.0f;
            float tabY = 5.0f * p;

            String[] tabLabels = {
                    I18n.get("menu.tab.game"),
                    I18n.get("menu.tab.world"),
                    I18n.get("menu.tab.more")
            };
            Tab[] tabs = {Tab.GAME, Tab.WORLD, Tab.MORE};

            for (int i = 0; i < 3; i++) {
                float tx = startTabX + i * (tabW + tabGap);
                boolean isTabActive = (activeTab == tabs[i]);
                boolean isTabHovered = (mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + tabH);

                // Tab frame
                if (isTabActive) {
                    addRect(geom, tx, tabY, tabW, tabH, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 0.95f);
                    // Active underline
                    float lineW = tabLabels[i].length() * 6.0f * (p * 0.75f);
                    float lineX = tx + (tabW - lineW) / 2.0f;
                    addRect(overlayGeom, lineX, tabY + tabH - 3.0f * p, lineW, 1.5f * p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
                } else if (isTabHovered) {
                    addRect(geom, tx, tabY, tabW, tabH, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 0.85f);
                } else {
                    addRect(geom, tx, tabY, tabW, tabH, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 0.65f);
                }

                // Tab border
                drawOutline(geom, tx, tabY, tabW, tabH, p, 0.28f, 0.28f, 0.28f, 1.0f);

                float tabTextR = isTabActive ? 1.0f : (isTabHovered ? 0.95f : 0.70f);
                float tabTextG = isTabActive ? 1.0f : (isTabHovered ? 0.95f : 0.70f);
                float tabTextB = isTabActive ? 1.0f : (isTabHovered ? 0.95f : 0.70f);
                drawCenteredButtonLabel(overlayGeom, tabLabels[i], tx + tabW / 2.0f, tabY + 6.0f * p, p * 0.75f, tabTextR, tabTextG, tabTextB);
            }

            // 3. Tab Content
            if (activeTab == Tab.GAME) {
                float boxW = 210.0f * p;
                float boxH = 20.0f * p;
                float boxX = (width - boxW) / 2.0f;

                // Label: World Name
                float labelY = 38.0f * p;
                drawButtonLabel(overlayGeom, I18n.get("menu.world_name"), boxX, labelY, p * 0.65f, 0.85f, 0.85f, 0.85f);

                // Text Box: World Name
                float nameY = 48.0f * p;
                boolean nameFocused = (focusedElement == FocusedElement.GAME_NAME);
                drawTextBox(geom, overlayGeom, boxX, nameY, boxW, boxH, createWorldName, "", nameFocused, blink, p);

                // Button 1: Game Mode
                float modeY = 74.0f * p;
                boolean hMode = (mx >= boxX && mx <= boxX + boxW && my >= modeY && my <= modeY + boxH);
                boolean hlMode = (focusedElement == FocusedElement.GAME_MODE);
                drawMinecraftMenuButton(geom, boxX, modeY, boxW, boxH, hMode, hlMode, p);

                String modeLabel = I18n.format("menu.game_mode", createGameMode.getDisplayName());
                drawCenteredButtonLabel(overlayGeom, modeLabel, boxX + boxW / 2.0f, modeY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Button 2: Difficulty
                float diffY = 100.0f * p;
                boolean hDiff = (mx >= boxX && mx <= boxX + boxW && my >= diffY && my <= diffY + boxH);
                boolean hlDiff = (focusedElement == FocusedElement.GAME_DIFFICULTY);
                drawMinecraftMenuButton(geom, boxX, diffY, boxW, boxH, hDiff, hlDiff, p);

                String diffLabel = I18n.format("menu.difficulty", createDifficulty.getDisplayName());
                drawCenteredButtonLabel(overlayGeom, diffLabel, boxX + boxW / 2.0f, diffY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Button 3: Allow Cheats
                float cheatsY = 126.0f * p;
                boolean hCheats = (mx >= boxX && mx <= boxX + boxW && my >= cheatsY && my <= cheatsY + boxH);
                boolean hlCheats = (focusedElement == FocusedElement.GAME_CHEATS);
                drawMinecraftMenuButton(geom, boxX, cheatsY, boxW, boxH, hCheats, hlCheats, p);

                String cheatsLabel = I18n.format("menu.allow_cheats", I18n.get(allowCheats ? "options.on" : "options.off"));
                drawCenteredButtonLabel(overlayGeom, cheatsLabel, boxX + boxW / 2.0f, cheatsY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Button 4: Game Rules
                float rulesY = 152.0f * p;
                boolean hRules = (mx >= boxX && mx <= boxX + boxW && my >= rulesY && my <= rulesY + boxH);
                boolean hlRules = (focusedElement == FocusedElement.GAME_RULES);
                drawMinecraftMenuButton(geom, boxX, rulesY, boxW, boxH, hRules, hlRules, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.game_rules"), boxX + boxW / 2.0f, rulesY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Bottom Buttons
                float btnW = 145.0f * p;
                float btnH = 20.0f * p;
                float spacing = 10.0f * p;
                float btnY = height - 29.0f * p;
                float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
                float b2X = (width / 2.0f) + (spacing / 2.0f);

                boolean hCreate = (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH);
                drawMinecraftMenuButton(geom, b1X, btnY, btnW, btnH, hCreate, false, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.create_world"),
                        b1X + btnW / 2.0f, btnY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                boolean hCancel = (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH);
                drawMinecraftMenuButton(geom, b2X, btnY, btnW, btnH, hCancel, false, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.cancel"),
                        b2X + btnW / 2.0f, btnY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

            } else if (activeTab == Tab.WORLD) {
                float listW = 280.0f * p;
                float listH = 34.0f * p;
                float listX = (width - listW) / 2.0f;
                float listStartY = 36.0f * p;
                float listGap = 38.0f * p;

                if (worlds.isEmpty()) {
                    drawCenteredButtonLabel(overlayGeom, I18n.get("menu.worlds_empty"), width / 2.0f, height * 0.35f, p * 0.85f, 0.7f, 0.7f, 0.7f);
                    drawCenteredButtonLabel(overlayGeom, I18n.get("menu.worlds_empty_hint"), width / 2.0f, height * 0.43f, p * 0.65f, 0.6f, 0.6f, 0.6f);
                } else {
                    int maxVisible = 4;
                    int visibleCount = Math.min(maxVisible, Math.max(0, worlds.size() - scrollOffset));

                    for (int i = 0; i < visibleCount; i++) {
                        int worldIdx = scrollOffset + i;
                        WorldInfo wi = worlds.get(worldIdx);
                        float wy = listStartY + i * listGap;
                        boolean isSelected = (worldIdx == selectedWorldIndex);
                        boolean isHovered = (mx >= listX && mx <= listX + listW && my >= wy && my <= wy + listH);

                        // Card background & outline
                        if (isSelected) {
                            addRect(geom, listX, wy, listW, listH, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.95f);
                            drawOutline(overlayGeom, listX, wy, listW, listH, Math.max(1.5f, p), 1.0f, 1.0f, 1.0f, 1.0f);
                            drawOutline(overlayGeom, listX + 4.0f * p, wy + 3.0f * p, 26.0f * p, 26.0f * p, p, 1.0f, 1.0f, 1.0f, 0.9f);
                        } else if (isHovered) {
                            addRect(geom, listX, wy, listW, listH, 0, 0, 0, 0, 0.06f, 0.06f, 0.06f, 0.85f);
                            drawOutline(overlayGeom, listX, wy, listW, listH, p, 0.55f, 0.55f, 0.55f, 1.0f);
                        } else {
                            addRect(geom, listX, wy, listW, listH, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.50f);
                            drawOutline(geom, listX, wy, listW, listH, p, 0.22f, 0.22f, 0.22f, 0.7f);
                        }

                        // World Icon: Grass block or Bedrock for Hardcore
                        int iconTile = (wi.getGameMode() == GameMode.HARDCORE)
                                ? BlockType.STONE_SWORD.getTexture(BlockType.Face.TOP)
                                : BlockType.GRASS.getTexture(BlockType.Face.TOP);
                        float[] uvs = TextureAtlas.getUVs(iconTile);
                        addRect(tex, listX + 6.0f * p, wy + 5.0f * p, 24.0f * p, 24.0f * p, uvs[0], uvs[1], uvs[2], uvs[3], 1, 1, 1, 1);

                        // Line 1: World Name (bold white/cyan or gold when selected)
                        float nameR = isSelected ? 1.0f : (isHovered ? 1.0f : 0.85f);
                        float nameG = isSelected ? 1.0f : (isHovered ? 1.0f : 0.85f);
                        float nameB = isSelected ? 0.25f : (isHovered ? 1.0f : 0.85f);
                        String nameStr = (isSelected ? "> " : "") + wi.getName();
                        drawButtonLabel(overlayGeom, nameStr, listX + 36.0f * p, wy + 5.0f * p, p * 0.75f, nameR, nameG, nameB);

                        // Line 2: Folder name & Last Played (gray)
                        String line2 = wi.getFolderName() + " (" + wi.getFormattedDate() + ")";
                        drawSmallDescription(overlayGeom, line2, listX + 36.0f * p, wy + 14.5f * p, p * 0.52f);

                        // Line 3: Mode and Version (gray/yellow)
                        String line3 = wi.getModeDisplayName() + ", 1.16.1";
                        drawSmallDescription(overlayGeom, line3, listX + 36.0f * p, wy + 23.0f * p, p * 0.52f);
                    }
                }

                // Bottom Buttons on World Tab
                float btnW = 95.0f * p;
                float btnH = 20.0f * p;
                float spacing = 8.0f * p;
                float totalW = 3 * btnW + 2 * spacing;
                float startBtnX = (width - totalW) / 2.0f;
                float btnY = height - 29.0f * p;

                float b1X = startBtnX;
                float b2X = startBtnX + btnW + spacing;
                float b3X = startBtnX + 2 * (btnW + spacing);

                boolean hasSelection = (selectedWorldIndex >= 0 && selectedWorldIndex < worlds.size());

                // 1. Play Selected World
                boolean h1 = hasSelection && (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH);
                drawMenuButtonOrDisabled(geom, b1X, btnY, btnW, btnH, h1, hasSelection, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.play_world"),
                        b1X + btnW / 2.0f, btnY + 6.0f * p, p * 0.70f,
                        hasSelection ? 1.0f : 0.45f, hasSelection ? 1.0f : 0.45f, hasSelection ? 1.0f : 0.45f);

                // 2. Delete
                boolean h2 = hasSelection && (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH);
                drawMenuButtonOrDisabled(geom, b2X, btnY, btnW, btnH, h2, hasSelection, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.delete"),
                        b2X + btnW / 2.0f, btnY + 6.0f * p, p * 0.70f,
                        hasSelection ? 1.0f : 0.45f, hasSelection ? 0.35f : 0.45f, hasSelection ? 0.35f : 0.45f);

                // 3. Cancel
                boolean h3 = (mx >= b3X && mx <= b3X + btnW && my >= btnY && my <= btnY + btnH);
                drawMinecraftMenuButton(geom, b3X, btnY, btnW, btnH, h3, false, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.cancel"),
                        b3X + btnW / 2.0f, btnY + 6.0f * p, p * 0.70f, 1.0f, 1.0f, 1.0f);

            } else if (activeTab == Tab.MORE) {
                float boxW = 210.0f * p;
                float boxH = 20.0f * p;
                float boxX = (width - boxW) / 2.0f;

                // Subtitle: Seed for generator
                float seedLabelY = 42.0f * p;
                drawButtonLabel(overlayGeom, I18n.get("menu.seed_label"), boxX, seedLabelY, p * 0.65f, 0.85f, 0.85f, 0.85f);

                // Input box: Seed
                float seedY = 54.0f * p;
                boolean seedFocused = (focusedElement == FocusedElement.MORE_SEED);
                drawTextBox(geom, overlayGeom, boxX, seedY, boxW, boxH, createSeedInput,
                        I18n.get("menu.seed_hint"),
                        seedFocused, blink, p);

                // Button 1: Generate Structures
                float structY = 86.0f * p;
                boolean hStruct = (mx >= boxX && mx <= boxX + boxW && my >= structY && my <= structY + boxH);
                boolean hlStruct = (focusedElement == FocusedElement.MORE_STRUCTURES);
                drawMinecraftMenuButton(geom, boxX, structY, boxW, boxH, hStruct, hlStruct, p);
                String structLabel = I18n.format("menu.generate_structures", I18n.get(generateStructures ? "options.on" : "options.off"));
                drawCenteredButtonLabel(overlayGeom, structLabel, boxX + boxW / 2.0f, structY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Button 2: World Type
                float typeY = 112.0f * p;
                boolean hType = (mx >= boxX && mx <= boxX + boxW && my >= typeY && my <= typeY + boxH);
                boolean hlType = (focusedElement == FocusedElement.MORE_WORLDTYPE);
                drawMinecraftMenuButton(geom, boxX, typeY, boxW, boxH, hType, hlType, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.world_type"), boxX + boxW / 2.0f, typeY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Button 3: Bonus Chest
                float chestY = 138.0f * p;
                boolean hChest = (mx >= boxX && mx <= boxX + boxW && my >= chestY && my <= chestY + boxH);
                boolean hlChest = (focusedElement == FocusedElement.MORE_BONUSCHEST);
                drawMinecraftMenuButton(geom, boxX, chestY, boxW, boxH, hChest, hlChest, p);
                String chestLabel = I18n.format("menu.bonus_chest", I18n.get(bonusChest ? "options.on" : "options.off"));
                drawCenteredButtonLabel(overlayGeom, chestLabel, boxX + boxW / 2.0f, chestY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                // Bottom Buttons
                float btnW = 145.0f * p;
                float btnH = 20.0f * p;
                float spacing = 10.0f * p;
                float btnY = height - 29.0f * p;
                float b1X = (width / 2.0f) - btnW - (spacing / 2.0f);
                float b2X = (width / 2.0f) + (spacing / 2.0f);

                boolean hCreate = (mx >= b1X && mx <= b1X + btnW && my >= btnY && my <= btnY + btnH);
                drawMinecraftMenuButton(geom, b1X, btnY, btnW, btnH, hCreate, false, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.create_world"),
                        b1X + btnW / 2.0f, btnY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);

                boolean hCancel = (mx >= b2X && mx <= b2X + btnW && my >= btnY && my <= btnY + btnH);
                drawMinecraftMenuButton(geom, b2X, btnY, btnW, btnH, hCancel, false, p);
                drawCenteredButtonLabel(overlayGeom, I18n.get("menu.cancel"),
                        b2X + btnW / 2.0f, btnY + 6.0f * p, p * 0.75f, 1.0f, 1.0f, 1.0f);
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

    private void drawTextBox(List<Float> g, List<Float> og, float x, float y, float w, float h,
                             String text, String placeholder, boolean focused, boolean blink, float p) {
        // Border: white outline if focused, dark gray if not
        if (focused) {
            drawOutline(g, x, y, w, h, p, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            drawOutline(g, x, y, w, h, p, 0.4f, 0.4f, 0.4f, 1.0f);
        }
        // Fill: pitch black
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, 0.02f, 0.02f, 0.02f, 1.0f);

        float textX = x + 6.0f * p;
        float textY = y + 6.0f * p;

        if (text.isEmpty() && !focused && !placeholder.isEmpty()) {
            drawButtonLabel(og, placeholder, textX, textY, p * 0.65f, 0.45f, 0.45f, 0.45f);
        } else {
            String renderStr = text + (focused && blink ? "_" : "");
            // Cyan / aqua font color matching the reference image (e.g. HOW TO CREATE WORLDS)
            drawButtonLabel(og, renderStr, textX, textY, p * 0.75f, 0.33f, 1.0f, 1.0f);
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
        drawMinecraftMenuButton(g, x, y, w, h, hovered, false, p);
    }

    private void drawMinecraftMenuButton(List<Float> g, float x, float y, float w, float h,
                                         boolean hovered, boolean highlighted, float p) {
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

        // Crisp white outline if highlighted / focused (as seen around 'Difficulty: Hard' in reference image)
        if (highlighted) {
            drawOutline(g, x, y, w, h, p, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void drawOutline(List<Float> g, float x, float y, float w, float h, float p, float r, float gr, float b, float a) {
        addRect(g, x, y, w, p, 0, 0, 0, 0, r, gr, b, a); // Top
        addRect(g, x, y + h - p, w, p, 0, 0, 0, 0, r, gr, b, a); // Bottom
        addRect(g, x, y + p, p, h - 2 * p, 0, 0, 0, 0, r, gr, b, a); // Left
        addRect(g, x + w - p, y + p, p, h - 2 * p, 0, 0, 0, 0, r, gr, b, a); // Right
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
        UiBatch.drawLetterBlock(g, ch, x, y, s, r, gr, b, 1.0f);
    }

    private void drawVertices(List<Float> vertices) {
        UiBatch.drawVertices(vaoId, vboId, vertices);
    }

    public boolean isBonusChest() {
        return bonusChest;
    }

    public void setBonusChest(boolean bonusChest) {
        this.bonusChest = bonusChest;
    }

    public void cleanup() {
        shader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
