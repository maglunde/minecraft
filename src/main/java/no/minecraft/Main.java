package no.minecraft;

import no.minecraft.i18n.I18n;
import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.player.Raycast;
import no.minecraft.render.*;
import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {
    private long window;
    private int width = 1280;
    private int height = 720;
    private int windowWidth = 1280;
    private int windowHeight = 720;

    private World world;
    private Player player;
    private TextureAtlas atlas;
    private Shader worldShader;
    private BlockOutline blockOutline;
    private ItemRenderer itemRenderer;
    private HandRenderer handRenderer;
    private PlayerRenderer playerRenderer;
    private MobRenderer mobRenderer;
    private MiningOverlay miningOverlay;
    private SkyRenderer skyRenderer;
    private HUD hud;
    private MainMenu mainMenu;
    private no.minecraft.render.PauseMenu pauseMenu;
    private no.minecraft.chat.ChatManager chatManager = new no.minecraft.chat.ChatManager();

    // Screens: the main menu and pause menu live on the stack as GuiScreen adapters
    private final ScreenStack screenStack = new ScreenStack();
    private InventoryScreen inventoryScreen;
    private CraftingTableScreen craftingTableScreen;
    private FurnaceScreen furnaceScreen;
    private ChestScreen chestScreen;
    private MainMenuScreen mainMenuScreen;
    private PauseMenuScreen pauseMenuScreen;

    private boolean cursorLocked = false;
    private double lastMouseX, lastMouseY;
    private double lastScreenX, lastScreenY;
    private boolean firstMouse = true;

    // Mining progress state
    private int miningBlockX = Integer.MIN_VALUE;
    private int miningBlockY = Integer.MIN_VALUE;
    private int miningBlockZ = Integer.MIN_VALUE;
    private float miningDamage = 0.0f;
    private float miningSoundTimer = 0.0f;
    private boolean isLeftMouseDown = false;
    private boolean isRightMouseDown = false;
    private float rightClickTimer = 0.0f;
    private boolean ignoreNextChar = false;
    private float dimensionPortalCooldown = 0.0f;
    private float autoSaveTimer = 0.0f;
    private boolean sprintActive = false;
    private Raycast.HitResult targetedHit = null;
    private static final float FIXED_TICK = 1.0f / 60.0f; // 60 ticks/s fixed simulation step (smooth without interpolation on 60 Hz displays)
    private final no.minecraft.math.Frustum frustum = new no.minecraft.math.Frustum();

    private static final String WORLD_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec2 aLight;

            uniform mat4 uProjection;
            uniform mat4 uView;

            out vec2 vTexCoord;
            out vec2 vLight;
            out float vDist;

            void main() {
                vec4 viewPos = uView * vec4(aPos, 1.0);
                gl_Position = uProjection * viewPos;
                vTexCoord = aTexCoord;
                vLight = aLight;
                vDist = length(viewPos.xyz);
            }
            """;

    private static final String WORLD_FRAG = """
            #version 330 core
            in vec2 vTexCoord;
            in vec2 vLight;
            in float vDist;

            uniform sampler2D uTexture;
            uniform vec3 uSkyColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uSunLight;

            out vec4 FragColor;

            void main() {
                vec4 texColor = texture(uTexture, vTexCoord);
                if (texColor.a < 0.1) {
                    discard;
                }

                // Modulate sky light with dynamic sun light (0.12 ambient) and directional factor.
                // vLight.x already includes sky exposure, so cave faces get no sun light.
                float ambient = 0.12;
                float skyLight = vLight.x * (ambient + (1.0 - ambient) * uSunLight);

                // Torch light provides bright illumination independent of time of day
                float faceFactor = 0.8 + 0.2 * vLight.x;
                float torchLight = vLight.y * faceFactor;

                // Unlit caves get a faint constant gray light so they ignore the day/night cycle
                float totalLight = clamp(max(skyLight, torchLight), 0.12, 1.0);
                vec3 shadedColor = texColor.rgb * totalLight;
                float fogFactor = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                vec3 finalColor = mix(shadedColor, uSkyColor, fogFactor);

                FragColor = vec4(finalColor, texColor.a);
            }
            """;

    public static void main(String[] args) {
        // MobTextureManager uses java.awt (BufferedImage/Graphics2D/ImageIO).
        // On macOS, a non-headless AWT starts its own AppKit event loop on the
        // main thread, which traps the GLFW game loop forever (frozen window).
        System.setProperty("java.awt.headless", "true");
        new Main().run();
    }

    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private GLFWErrorCallback errorCallback;

    private void init() {
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        no.minecraft.settings.GameSettings.load();

        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(width, height, I18n.get("window.title"), NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.IntBuffer fbW = stack.mallocInt(1);
            java.nio.IntBuffer fbH = stack.mallocInt(1);
            java.nio.IntBuffer winW = stack.mallocInt(1);
            java.nio.IntBuffer winH = stack.mallocInt(1);
            glfwGetFramebufferSize(window, fbW, fbH);
            glfwGetWindowSize(window, winW, winH);
            this.width = fbW.get(0);
            this.height = fbH.get(0);
            this.windowWidth = winW.get(0);
            this.windowHeight = winH.get(0);
        }

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
        });

        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            this.windowWidth = w;
            this.windowHeight = h;
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // Enable VSync
        glfwShowWindow(window);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Initialize game components
        atlas = new TextureAtlas();
        worldShader = new Shader(WORLD_VERT, WORLD_FRAG);
        blockOutline = new BlockOutline();
        itemRenderer = new ItemRenderer();
        handRenderer = new HandRenderer();
        playerRenderer = new PlayerRenderer();
        mobRenderer = new MobRenderer();
        miningOverlay = new MiningOverlay();
        skyRenderer = new SkyRenderer();
        hud = new HUD();
        mainMenu = new MainMenu();
        pauseMenu = new PauseMenu();

        world = new World();
        Vector3f spawn = world.getSpawnPoint();
        player = new Player(world, spawn.x, spawn.y, spawn.z);

        // Adapter screens wrapping the legacy menus (constructed after the game state they forward)
        mainMenuScreen = new MainMenuScreen(mainMenu, () -> width, () -> height, () -> world, () -> player);
        pauseMenuScreen = new PauseMenuScreen(pauseMenu, () -> width, () -> height);
        inventoryScreen = new InventoryScreen(hud, () -> player, () -> width, () -> height);
        craftingTableScreen = new CraftingTableScreen(hud, () -> player, () -> width, () -> height);
        furnaceScreen = new FurnaceScreen(hud, () -> player, () -> width, () -> height);
        chestScreen = new ChestScreen(hud, () -> player, () -> width, () -> height);

        // Wire input callbacks only after all game state exists (callbacks dereference it immediately)
        setupInput();

        // Start on the title screen
        screenStack.push(mainMenuScreen);

        // Show cursor in menu
        setCursorLocked(false);
    }

    private final boolean[] keyPressed = new boolean[GLFW_KEY_LAST + 1];
    private double lastWPressTime = 0.0;
    private double lastSpacePressTime = 0.0;
    private boolean doubleTapSprint = false;

    private boolean isKeyDown(int key) {
        if (key >= 0 && key <= GLFW_KEY_LAST && keyPressed[key]) {
            return true;
        }
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    // --- Stack-driven UI state helpers (replace the legacy menu flag checks) ---

    /** Whether the title/main menu is open (its adapter screen is on the stack). */
    private boolean inMenu() {
        return mainMenuScreen.isOpen();
    }

    /** Whether the game loop is paused by an open screen (main menu or pause menu). */
    private boolean isPaused() {
        return screenStack.anyPausesGame();
    }

    /** Whether any UI blocks game input: menus, pause, inventory containers or chat. */
    private boolean inGui() {
        return isPaused() || hud.isInventoryOpen() || chatManager.isOpen();
    }

    /**
     * Keeps the stack in sync with the legacy menus after a screen consumed an
     * input event. The legacy menus close themselves internally (ESC, Back to
     * Game, world start, Save & Quit to Title) while their adapter screens stay
     * pushed, so pop them here and mirror the legacy post-menu side effects:
     * world saving on quit-to-title, re-opening the title screen, cursor locking
     * and the window close / open-options requests.
     */
    private void reconcileScreens() {
        boolean pauseWasOpen = pauseMenuScreen.isOpen();

        // The pause menu closed itself (ESC, Back to Game, Done, Save & Quit): pop its screen.
        if (pauseMenuScreen.isOpen() && !pauseMenu.isOpen()) {
            screenStack.pop();
        }
        // The main menu closed itself (a world was started): pop its screen and lock the cursor.
        if (mainMenuScreen.isOpen() && !mainMenu.isInMenu()) {
            screenStack.pop();
            setCursorLocked(true);
        }

        if (pauseMenu.isQuitToTitleRequested()) {
            pauseMenu.clearQuitToTitleRequested();
            // Legacy "Save and Quit to Title": save the world, show the title screen, unlock the cursor.
            if (mainMenu.getActiveWorldInfo() != null) {
                no.minecraft.world.save.WorldSaveManager.saveWorld(world, player, mainMenu.getActiveWorldInfo());
            }
            mainMenu.setGameStarted(false);
            mainMenu.setActiveWorldInfo(null);
            if (!mainMenuScreen.isOpen()) {
                screenStack.push(mainMenuScreen); // onOpen() -> setInMenu(true) (title screen + world refresh)
            }
            setCursorLocked(false);
        } else if (mainMenu.isQuitRequested()) {
            // "Quit" on the title screen: close the window.
            mainMenu.clearQuitRequested();
            glfwSetWindowShouldClose(window, true);
        } else if (mainMenu.isOpenOptionsRequested()) {
            // "Options" on the title screen: open the pause menu's options screen on top of it.
            mainMenu.clearOpenOptionsRequested();
            screenStack.push(pauseMenuScreen); // onOpen() -> pauseMenu.open()
            pauseMenu.openOptionsFromTitle();
        }

        // Legacy cursor rule: leaving the pause menu with the game still running relocks the cursor.
        if (pauseWasOpen && !pauseMenuScreen.isOpen() && !mainMenuScreen.isOpen()) {
            setCursorLocked(true);
        }
    }

    /**
     * Keeps the stack in sync with the HUD's container state, which remains
     * the single source of truth for what is open (E key, ESC and world
     * interaction all route through the legacy HUD state methods). Container
     * screens only exist to carry the container rendering and clicks; they
     * are pushed and popped here whenever that state changes.
     */
    private void syncContainerScreens() {
        GuiScreen desired;
        if (hud.isCraftingTableOpen()) {
            desired = craftingTableScreen;
        } else if (hud.isFurnaceOpen()) {
            desired = furnaceScreen;
        } else if (hud.isChestOpen()) {
            desired = chestScreen;
        } else if (hud.isInventoryOpen()) {
            desired = inventoryScreen;
        } else {
            desired = null;
        }
        GuiScreen top = screenStack.getTop();
        if (top == desired) {
            return;
        }
        if (top == inventoryScreen || top == craftingTableScreen || top == furnaceScreen || top == chestScreen) {
            screenStack.pop();
        }
        if (desired != null) {
            screenStack.push(desired);
        }
    }

    /**
     * Draws the open container screen (inventory / crafting table / furnace / chest)
     * above the game HUD. The screens only emit vertex lists; the GL passes
     * run through the HUD shader so the container backdrop covers the hotbar
     * etc., exactly like the legacy single-pass HUD rendering.
     */
    private void renderContainerScreens(int windowWidth, int windowHeight) {
        List<Float> geom = new ArrayList<>();
        List<Float> tex = new ArrayList<>();
        List<Float> overlayGeom = new ArrayList<>();
        if (hud.isCraftingTableOpen()) {
            craftingTableScreen.renderGui(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);
        } else if (hud.isFurnaceOpen() && hud.getActiveFurnace() != null) {
            furnaceScreen.renderGui(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);
        } else if (hud.isChestOpen() && hud.getActiveChest() != null) {
            chestScreen.renderGui(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);
        } else if (hud.isInventoryOpen()) {
            inventoryScreen.renderGui(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);
        } else {
            return;
        }
        hud.drawScreenGeometry(windowWidth, windowHeight, atlas, geom, tex, overlayGeom);
    }

    private void setupInput() {
        // Cursor movement
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            double scaleX = (windowWidth > 0) ? ((double) this.width / windowWidth) : 1.0;
            double scaleY = (windowHeight > 0) ? ((double) this.height / windowHeight) : 1.0;
            double fbMouseX = xpos * scaleX;
            double fbMouseY = ypos * scaleY;

            if (hud.isInventoryOpen() || !screenStack.isEmpty() || chatManager.isOpen() || !cursorLocked) {
                lastMouseX = fbMouseX;
                lastMouseY = fbMouseY;
                lastScreenX = xpos;
                lastScreenY = ypos;
                firstMouse = true;
                if (hud.isInventoryOpen()) {
                    hud.handleMouseMove(fbMouseX, fbMouseY, player, width, height);
                }
                return;
            }

            if (firstMouse) {
                lastMouseX = fbMouseX;
                lastMouseY = fbMouseY;
                lastScreenX = xpos;
                lastScreenY = ypos;
                firstMouse = false;
                return;
            }

            float dx = (float) (xpos - lastScreenX);
            float dy = (float) (lastScreenY - ypos); // Inverted Y for OpenGL
            lastMouseX = fbMouseX;
            lastMouseY = fbMouseY;
            lastScreenX = xpos;
            lastScreenY = ypos;

            float sens = no.minecraft.settings.GameSettings.getInstance().getMouseSensitivity();
            player.getCamera().rotate(dx * sens, dy * sens);
        });

        // Mouse clicks for mining, combat, placing and crafting
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                boolean isShiftDown = (mods & GLFW_MOD_SHIFT) != 0 || keyPressed[GLFW_KEY_LEFT_SHIFT] || keyPressed[GLFW_KEY_RIGHT_SHIFT];
                // Menus (and other screens) receive clicks through the stack first;
                // only clicks no open screen consumed reach the game world below.
                if (screenStack.handleMouseClick(lastMouseX, lastMouseY, button, isShiftDown)) {
                    reconcileScreens();
                    return;
                }
                if (hud.isInventoryOpen()) {
                    hud.handleMouseClick(lastMouseX, lastMouseY, button, isShiftDown, player, width, height);
                    syncContainerScreens();
                    return;
                }
                if (chatManager.isOpen()) {
                    return;
                }
                if (!cursorLocked) {
                    setCursorLocked(true);
                    return;
                }
            } else if (action == GLFW_RELEASE) {
                if (hud.isInventoryOpen()) {
                    hud.handleMouseRelease(lastMouseX, lastMouseY, button, player, width, height);
                    return;
                }
                if (chatManager.isOpen()) {
                    return;
                }
                if (!cursorLocked) {
                    return;
                }
            }

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    isLeftMouseDown = true;
                    player.swing();
                    handRenderer.swing();

                    // 1. Check if attacking a mob with sword / tool / fist
                    Vector3f eye = player.getEyePosition();
                    Vector3f fwd = player.getCamera().getForward();
                    no.minecraft.entity.Mob hitMob = null;
                    float minMobDist = 4.0f;

                    for (no.minecraft.entity.Mob mob : world.getMobs()) {
                        if (mob.isDead()) continue;
                        Vector3f toMob = new Vector3f(mob.getPosition()).add(0, mob.getType().getHeight() * 0.5f, 0).sub(eye);
                        float dist = toMob.length();
                        if (dist < minMobDist) {
                            toMob.normalize();
                            if (toMob.dot(fwd) > 0.85f) { // Facing mob
                                minMobDist = dist;
                                hitMob = mob;
                            }
                        }
                    }

                    if (hitMob != null) {
                        BlockType tool = player.getSelectedBlock();
                        int baseDmg = tool != null ? tool.getAttackDamage() : 1;
                        boolean isCrit = player.canPerformCriticalHit();
                        int dmg = Player.calculateAttackDamage(baseDmg, isCrit);

                        hitMob.takeDamage(dmg, fwd.x, fwd.z, world);
                        player.addExhaustion(0.1f);
                        if (isCrit) {
                            no.minecraft.sound.SoundManager.getInstance().play("crit", 1.0f);
                        } else {
                            no.minecraft.sound.SoundManager.getInstance().play("hurt", 0.9f);
                        }

                        float mobH = hitMob.getType().getHeight();
                        float targetY = hitMob.getPosition().y + mobH * 0.65f;

                        if (isCrit) {
                            no.minecraft.render.ParticleManager.getInstance().spawnCritParticles(
                                    hitMob.getPosition().x,
                                    targetY,
                                    hitMob.getPosition().z,
                                    16
                            );
                        }

                        CombatTextManager.getInstance().add(
                                hitMob.getPosition().x,
                                hitMob.getPosition().y + mobH * 0.75f,
                                hitMob.getPosition().z,
                                dmg / 2.0f,
                                isCrit
                        );
                        // Damage tool in survival
                        if (player.getGameMode() == GameMode.SURVIVAL) {
                            player.getInventory().getSlot(player.getSelectedSlot()).damageTool(1);
                        }
                        miningDamage = 0.0f; // Reset mining
                        return;
                    }

                    // 1.5 Check if hitting a boat
                    no.minecraft.entity.Boat hitBoat = null;
                    float minBoatDist = 4.0f;
                    for (no.minecraft.entity.Boat boat : world.getBoats()) {
                        if (boat.isDead()) continue;
                        Vector3f toBoat = new Vector3f(boat.getPosition()).add(0, 0.25f, 0).sub(eye);
                        float dist = toBoat.length();
                        if (dist < minBoatDist) {
                            toBoat.normalize();
                            if (toBoat.dot(fwd) > 0.80f) {
                                minBoatDist = dist;
                                hitBoat = boat;
                            }
                        }
                    }
                    if (hitBoat != null) {
                        hitBoat.breakBoat(world);
                        miningDamage = 0.0f;
                        return;
                    }
                } else if (action == GLFW_RELEASE) {
                    isLeftMouseDown = false;
                    miningDamage = 0.0f;
                    miningBlockX = Integer.MIN_VALUE;
                }
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == GLFW_PRESS) {
                    isRightMouseDown = true;
                    player.swing();
                    handRenderer.use();
                    if (handleRightClickAction()) {
                        rightClickTimer = 0.22f;
                    } else {
                        rightClickTimer = 0.0f;
                    }
                } else if (action == GLFW_RELEASE) {
                    isRightMouseDown = false;
                    rightClickTimer = 0.0f;
                }
            }
        });

        // Scroll for hotbar / GUI
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            // The main menu screen scrolls its world-select list; while the pause
            // menu covers it (options from title) nothing scrolls, as in legacy.
            if (mainMenuScreen.isOpen()) {
                screenStack.handleScroll(xoffset, yoffset);
                return;
            }
            if (hud.isInventoryOpen()) {
                hud.handleScroll(xoffset, yoffset);
                return;
            }
            // Legacy: with no menu or inventory open the scroll wheel changes the
            // hotbar selection (this also applied while the pause menu was open).
            if (yoffset > 0) {
                player.scrollSlot(-1);
            } else if (yoffset < 0) {
                player.scrollSlot(1);
            }
        });

        // Key callbacks (with complete key state tracking and double-tap W sprint)
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key >= 0 && key <= GLFW_KEY_LAST) {
                if (action == GLFW_PRESS) {
                    keyPressed[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keyPressed[key] = false;
                }
            }

            no.minecraft.settings.GameSettings gs = no.minecraft.settings.GameSettings.getInstance();

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                // Screens receive key events through the stack first (top screen only)
                if (screenStack.handleKey(key, action)) {
                    reconcileScreens();
                    return;
                }
                // Unconsumed keys while a screen is open keep the legacy swallow rules:
                // the pause menu swallowed everything, the main menu everything but
                // the title-screen ESC that resumes a running game (its handleKey
                // already ran through the stack without consuming that key).
                if (pauseMenuScreen.isOpen()) {
                    return;
                }
                if (mainMenuScreen.isOpen()) {
                    return;
                }
                if (hud.isRecipeSearchFocused()) {
                    if (key == GLFW_KEY_BACKSPACE) {
                        hud.recipeSearchBackspace();
                        return;
                    }
                }
                if ((key == GLFW_KEY_Q || key == gs.keyDrop) && !isPaused() && !chatManager.isOpen()) {
                    boolean isCtrl = (mods & (GLFW_MOD_CONTROL | GLFW_MOD_SUPER)) != 0
                            || keyPressed[GLFW_KEY_LEFT_CONTROL] || keyPressed[GLFW_KEY_RIGHT_CONTROL];
                    if (hud.isInventoryOpen()) {
                        if (hud.handleDropKeyPress(lastMouseX, lastMouseY, isCtrl, player, width, height)) {
                            return;
                        }
                    } else {
                        no.minecraft.player.ItemStack sel = player.getInventory().getSlot(player.getSelectedSlot());
                        if (sel != null && !sel.isEmpty()) {
                            int count = isCtrl ? sel.getCount() : 1;
                            BlockType type = sel.getType();
                            sel.add(-count);
                            Vector3f eye = player.getEyePosition();
                            Vector3f fwd = player.getCamera().getForward();
                            world.spawnItemDrop(eye.x, eye.y - 0.2f, eye.z, fwd.x * 4.5f, fwd.y * 4.5f + 1.5f, fwd.z * 4.5f, type, count);
                            no.minecraft.sound.SoundManager.getInstance().play("pop", 0.8f);
                            return;
                        }
                    }
                }
            }

            if (action == GLFW_PRESS) {
                if (hud.isRecipeSearchFocused()) {
                    if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_ENTER) {
                        hud.setRecipeSearchFocused(false);
                        return;
                    }
                    return;
                }

                if (chatManager.isOpen()) {
                    if (key == GLFW_KEY_ESCAPE) {
                        chatManager.closeChat();
                        setCursorLocked(true);
                    } else if (key == GLFW_KEY_ENTER) {
                        chatManager.submitMessage(world, player);
                        setCursorLocked(true);
                    } else if (key == GLFW_KEY_BACKSPACE) {
                        chatManager.backspace();
                    } else if (key == GLFW_KEY_UP) {
                        chatManager.navigateHistory(-1); // Previous command
                    } else if (key == GLFW_KEY_DOWN) {
                        chatManager.navigateHistory(1);  // Next command
                    } else if (key == GLFW_KEY_TAB) {
                        chatManager.handleTabCompletion(); // Autocomplete
                    }
                    return;
                }

                if (key == GLFW_KEY_T && !hud.isInventoryOpen() && !pauseMenuScreen.isOpen()) {
                    // Open Chat empty (prevent 't' from char callback)
                    ignoreNextChar = true;
                    chatManager.openChat("");
                    setCursorLocked(false);
                    return;
                } else if (key == GLFW_KEY_SLASH && !hud.isInventoryOpen() && !pauseMenuScreen.isOpen()) {
                    // Open Chat with prefilled "/" for quick commands (prevent redundant '/' from char callback)
                    ignoreNextChar = true;
                    chatManager.openChat("/");
                    setCursorLocked(false);
                    return;
                }

                if (hud.isInventoryOpen()) {
                    if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                        if (hud.handleInventoryKeyPress(key, lastMouseX, lastMouseY, player, width, height)) {
                            return;
                        }
                    } else if (key == GLFW_KEY_F) {
                        if (hud.handleSwapKeyPress(lastMouseX, lastMouseY, player, width, height)) {
                            return;
                        }
                    }
                }

                if (key == GLFW_KEY_ESCAPE) {
                    if (hud.isInventoryOpen()) {
                        hud.closeInventory(player);
                        setCursorLocked(true);
                        syncContainerScreens();
                    } else {
                        // Open dedicated Pause Menu
                        screenStack.push(pauseMenuScreen); // onOpen() -> pauseMenu.open()
                        setCursorLocked(false);
                    }
                } else if (key == GLFW_KEY_M && !hud.isInventoryOpen() && !pauseMenuScreen.isOpen()) {
                    screenStack.push(mainMenuScreen); // onOpen() -> setInMenu(true) (title screen)
                    setCursorLocked(false);
                } else if (!isPaused() && key == gs.keyInventory) {
                    // Toggle Inventory / Crafting GUI
                    hud.toggleInventory(player);
                    setCursorLocked(!hud.isInventoryOpen());
                    syncContainerScreens();
                } else if (!isPaused() && (key == GLFW_KEY_F3 || key == gs.keyToggleDebug)) {
                    // Toggle F3 Debug Screen
                    hud.toggleDebugInfo();
                } else if (!isPaused() && (key == GLFW_KEY_F5 || key == gs.keyTogglePerspective)) {
                    // Toggle F5 Camera Perspective (First Person -> Third Person Back -> Third Person Front)
                    player.getCamera().cyclePerspective();
                    no.minecraft.sound.SoundManager.getInstance().play("click", 0.8f);
                } else if (!isPaused() && key == GLFW_KEY_G) {
                    // Toggle GameMode (Survival / Creative)
                    player.toggleGameMode();
                } else if (!isPaused() && key == GLFW_KEY_F) {
                    player.swapHands();
                } else if (key == GLFW_KEY_P) {
                    // Reset position to ground at spawn
                    int gy = world.getSpawnHeight((int) Math.floor(player.getSpawnPosition().x), (int) Math.floor(player.getSpawnPosition().z));
                    if (gy <= 0) {
                        Vector3f safe = world.findSafeSpawnPosition((int) Math.floor(player.getSpawnPosition().x), (int) Math.floor(player.getSpawnPosition().z));
                        player.getSpawnPosition().set(safe);
                        gy = (int) Math.floor(safe.y);
                    }
                    player.getPosition().set(player.getSpawnPosition().x, gy + 0.05f, player.getSpawnPosition().z);
                    player.getVelocity().set(0, 0, 0);
                    player.ensureGroundedOnSolidBlock();
                } else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                    player.setSelectedSlot(key - GLFW_KEY_1);
                } else if (key == gs.keyJump) {
                    // Double-tap Space detection for flying in Creative mode (0 gravity)
                    if (!hud.isInventoryOpen() && !pauseMenuScreen.isOpen()) {
                        double now = glfwGetTime();
                        if (now - lastSpacePressTime < 0.35) {
                            if (player.getGameMode() == GameMode.CREATIVE) {
                                player.toggleFlying();
                            }
                            lastSpacePressTime = 0.0;
                        } else {
                            lastSpacePressTime = now;
                        }
                    }
                } else if (key == gs.keyForward) {
                    // Double-tap forward detection for Minecraft-style sprinting
                    double now = glfwGetTime();
                    if (now - lastWPressTime < 0.30) {
                        doubleTapSprint = true;
                    }
                    lastWPressTime = now;
                }
            } else if (action == GLFW_RELEASE) {
                if (key == no.minecraft.settings.GameSettings.getInstance().keyForward) {
                    doubleTapSprint = false;
                }
            }
        });

        // Text character typing callback for Chat and Recipe Search
        glfwSetCharCallback(window, (win, codepoint) -> {
            if (ignoreNextChar) {
                ignoreNextChar = false;
                return;
            }
            // The top screen receives typed characters first (the main menu's
            // world-name field, later container-screen search fields, ...)
            screenStack.handleChar((char) codepoint);
            if (!screenStack.isEmpty()) {
                return; // legacy: characters never reached chat/GUI code while a menu was open
            }
            if (chatManager.isOpen()) {
                chatManager.addChar((char) codepoint);
            } else if (hud.isRecipeSearchFocused()) {
                hud.addRecipeSearchChar((char) codepoint);
            }
        });
    }

    private boolean handleRightClickAction() {
        BlockType held = player.getSelectedBlock();

        // 0. Eating food
        if (held != null && held.isFood()) {
            Raycast.HitResult hit = Raycast.raycast(world, player.getEyePosition(), player.getCamera().getForward(), 5.5f);
            boolean isSneaking = player.isSneaking() || keyPressed[GLFW_KEY_LEFT_SHIFT] || keyPressed[GLFW_KEY_RIGHT_SHIFT];
            if (hit != null && !isSneaking) {
                BlockType clickedBlock = world.getBlock(hit.hitX, hit.hitY, hit.hitZ);
                if (clickedBlock == BlockType.CRAFTING_TABLE || clickedBlock == BlockType.FURNACE || clickedBlock == BlockType.CHEST) {
                    return tryPlaceBlock();
                }
            }

            if (player.canEat(held)) {
                player.startEating();
                return true;
            }
        }

        // 0.5 Equip armor on right click
        if (held != null && held.isArmor()) {
            Raycast.HitResult hit = Raycast.raycast(world, player.getEyePosition(), player.getCamera().getForward(), 5.5f);
            boolean isSneaking = player.isSneaking() || keyPressed[GLFW_KEY_LEFT_SHIFT] || keyPressed[GLFW_KEY_RIGHT_SHIFT];
            if (hit != null && !isSneaking) {
                BlockType clickedBlock = world.getBlock(hit.hitX, hit.hitY, hit.hitZ);
                if (clickedBlock == BlockType.CRAFTING_TABLE || clickedBlock == BlockType.FURNACE || clickedBlock == BlockType.CHEST) {
                    return tryPlaceBlock();
                }
            }

            if (player.equipArmorFromInventory(player.getSelectedSlot())) {
                return true;
            }
        }

        // 1. Bow shooting (fires Arrow entity if player has arrows or is in Creative)
        if (held == BlockType.BOW) {
            boolean hasArrow = player.getGameMode() == GameMode.CREATIVE || player.getInventory().getItemCount(BlockType.ARROW) > 0;
            if (hasArrow) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.getInventory().removeItem(BlockType.ARROW, 1);
                    player.getInventory().getSlot(player.getSelectedSlot()).damageTool(1);
                }
                Vector3f eye = player.getEyePosition();
                Vector3f fwd = player.getCamera().getForward();
                world.spawnArrow(eye.x + fwd.x * 0.3f, eye.y + fwd.y * 0.3f, eye.z + fwd.z * 0.3f, fwd.x * 24.0f, fwd.y * 24.0f, fwd.z * 24.0f, player, false);
                no.minecraft.sound.SoundManager.getInstance().play("bow_shoot", 1.0f);
                return true;
            }
        }

        // 2. Eye of Ender throwing towards Stronghold (48, 14, 48)
        if (held == BlockType.EYE_OF_ENDER) {
            Raycast.HitResult target = Raycast.raycast(world, player.getEyePosition(), player.getCamera().getForward(), 5.0f);
            boolean aimingAtFrame = target != null && world.getBlock(target.hitX, target.hitY, target.hitZ) == BlockType.END_PORTAL_FRAME;
            if (!aimingAtFrame) {
                // Throw towards Stronghold
                float dx = World.STRONGHOLD_X - player.getPosition().x;
                float dz = World.STRONGHOLD_Z - player.getPosition().z;
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                if (len > 0.001f) {
                    dx /= len;
                    dz /= len;
                }
                Vector3f eye = player.getEyePosition();
                world.spawnEyeOfEnder(eye.x, eye.y, eye.z, dx * 16.0f, 6.0f, dz * 16.0f);
                no.minecraft.sound.SoundManager.getInstance().play("pop", 1.0f);
                no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.EYE_SPY);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.useSelectedBlock();
                }
                return true;
            }
        }

        // 2.5 Ender pearl throw -> teleport to impact point
        if (held == BlockType.ENDER_PEARL) {
            Vector3f eye = player.getEyePosition();
            Vector3f fwd = player.getCamera().getForward();
            world.spawnEnderPearl(eye.x + fwd.x * 0.3f, eye.y + fwd.y * 0.3f, eye.z + fwd.z * 0.3f,
                    fwd.x * 30.0f, fwd.y * 30.0f + 2.0f, fwd.z * 30.0f, player);
            no.minecraft.sound.SoundManager.getInstance().play("pop", 1.0f);
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.useSelectedBlock();
            }
            return true;
        }

        // Mount boat if looking at a boat
        for (no.minecraft.entity.Boat b : world.getBoats()) {
            if (b.isDead()) continue;
            float dx = b.getPosition().x - player.getPosition().x;
            float dy = b.getPosition().y - player.getPosition().y;
            float dz = b.getPosition().z - player.getPosition().z;
            if (dx * dx + dy * dy + dz * dz < 16.0f) {
                Vector3f eye = player.getEyePosition();
                Vector3f fwd = player.getCamera().getForward();
                float bx = b.getPosition().x - eye.x;
                float by = (b.getPosition().y + 0.3f) - eye.y;
                float bz = b.getPosition().z - eye.z;
                float dot = bx * fwd.x + by * fwd.y + bz * fwd.z;
                if (dot > 0) {
                    float distSq = bx * bx + by * by + bz * bz;
                    float perpSq = distSq - dot * dot;
                    if (perpSq < 1.2f) {
                        if (player.getRidingBoat() == b) {
                            b.dismountDriver();
                        } else {
                            b.mountDriver(player);
                        }
                        return true;
                    }
                }
            }
        }

        // Placing Boat item on ground or water
        if (held == BlockType.BOAT) {
            Raycast.HitResult hit = Raycast.raycast(world, player.getEyePosition(), player.getCamera().getForward(), 5.0f, true);
            if (hit != null) {
                float sx = hit.hitX + 0.5f;
                float sy = (hit.blockType == BlockType.WATER) ? (hit.hitY + 0.85f) : (hit.hitY + 1.05f);
                float sz = hit.hitZ + 0.5f;
                world.spawnBoat(sx, sy, sz, player.getCamera().getYaw());
                no.minecraft.sound.SoundManager.getInstance().play("wood_dig", 1.0f);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.useSelectedBlock();
                }
                return true;
            }
        }

        return tryPlaceBlock();
    }

    private boolean tryPlaceBlock() {
        Raycast.HitResult hit = Raycast.raycast(
                world,
                player.getEyePosition(),
                player.getCamera().getForward(),
                5.5f
        );

        if (hit != null) {
            BlockType clickedBlock = world.getBlock(hit.hitX, hit.hitY, hit.hitZ);
            boolean isSneaking = player.isSneaking() || keyPressed[GLFW_KEY_LEFT_SHIFT] || keyPressed[GLFW_KEY_RIGHT_SHIFT];
            boolean canPlace = player.canPlaceSelectedBlock();

            // When holding shift (sneaking) and holding a placeable block, bypass GUI interaction to place block instead!
            if (!isSneaking || !canPlace) {
                if (clickedBlock == BlockType.CRAFTING_TABLE) {
                    hud.openCraftingTable();
                    setCursorLocked(false);
                    isLeftMouseDown = false;
                    isRightMouseDown = false;
                    syncContainerScreens();
                    return true;
                }
                if (clickedBlock == BlockType.FURNACE) {
                    hud.openFurnace(world.getOrCreateFurnace(hit.hitX, hit.hitY, hit.hitZ));
                    setCursorLocked(false);
                    isLeftMouseDown = false;
                    isRightMouseDown = false;
                    syncContainerScreens();
                    return true;
                }
                if (clickedBlock == BlockType.CHEST) {
                    hud.openChest(world.getOrCreateChest(hit.hitX, hit.hitY, hit.hitZ));
                    setCursorLocked(false);
                    isLeftMouseDown = false;
                    isRightMouseDown = false;
                    syncContainerScreens();
                    return true;
                }
            }

            // Inserting Eye of Ender into End Portal Frame
            BlockType held = player.getSelectedBlock();
            if (clickedBlock == BlockType.END_PORTAL_FRAME && held == BlockType.EYE_OF_ENDER) {
                world.setBlock(hit.hitX, hit.hitY, hit.hitZ, BlockType.END_PORTAL_FRAME_FILLED);
                no.minecraft.sound.SoundManager.getInstance().play("stone_dig", 1.0f);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.useSelectedBlock();
                }
                PortalController.checkAndActivateEndPortal(world);
                return true;
            }

            // Flint and Steel igniting Nether Portal
            if (held == BlockType.FLINT_AND_STEEL) {
                if (clickedBlock == BlockType.OBSIDIAN) {
                    PortalController.igniteNetherPortal(world, hit.hitX, hit.hitY, hit.hitZ);
                    no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.0f);
                    return true;
                }
            }

            // Place block only if player has it in inventory
            if (canPlace) {
                BlockType toPlace = player.getSelectedBlock();
                if (toPlace != null && toPlace != BlockType.AIR) {
                    boolean roomAvailable = !toPlace.isSolid() || player.isFlying() || !player.getBoundingBox().intersects(
                            new no.minecraft.physics.AABB(hit.placeX, hit.placeY, hit.placeZ,
                                    hit.placeX + 1, hit.placeY + 1, hit.placeZ + 1));
                    if (toPlace == BlockType.TORCH && hit.placeY < hit.hitY) {
                        // Torch cannot be placed on ceiling
                        return false;
                    }
                    if (roomAvailable) {
                        world.setBlock(hit.placeX, hit.placeY, hit.placeZ, toPlace);
                        no.minecraft.sound.SoundManager.getInstance().play(toPlace.getDigSound(), 0.8f);
                        player.useSelectedBlock();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void setCursorLocked(boolean locked) {
        this.cursorLocked = locked;
        glfwSetInputMode(window, GLFW_CURSOR, locked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, locked ? GLFW_TRUE : GLFW_FALSE);
        }
        if (locked) {
            firstMouse = true;
        }
    }

    private void loop() {
        double lastTime = glfwGetTime();
        double fpsTimer = lastTime;
        int frameCount = 0;
        int currentFps = 60;
        float accumulator = 0.0f;

        Vector3f skyColor = new Vector3f(0.53f, 0.81f, 0.98f); // Minecraft sky blue

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // Fixed-step simulation at 20 ticks/s; frame dt is capped so hitches cannot spiral
            accumulator += Math.min(dt, 0.25f);
            while (accumulator >= FIXED_TICK) {
                tick(FIXED_TICK);
                accumulator -= FIXED_TICK;
            }

            no.minecraft.settings.GameSettings gs = no.minecraft.settings.GameSettings.getInstance();

            // Per-frame raycast for the block outline; the simulation tick uses the latest result
            if (inGui()) {
                targetedHit = null;
            } else {
                targetedHit = Raycast.raycast(
                        world,
                        player.getEyePosition(),
                        player.getCamera().getForward(),
                        5.5f
                );
            }

            // FPS Counter & Info
            frameCount++;
            if (currentTime - fpsTimer >= 1.0) {
                currentFps = frameCount;
                String title = TitleBuilder.buildTitle(world, player, frameCount, sprintActive);
                glfwSetWindowTitle(window, title);
                frameCount = 0;
                fpsTimer += 1.0;
            }

            // Calculate dynamic day/night sky color & lighting
            float sunLight = world.getSunLightLevel();
            SkyColorCalculator.calculateSkyColor(world, player, skyColor);

            // Clear buffers
            glClearColor(skyColor.x, skyColor.y, skyColor.z, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Setup 3D matrices
            Matrix4f projection = new Matrix4f().perspective(
                    (float) Math.toRadians(gs.getFov()),
                    (float) width / (float) height,
                    0.05f,
                    300.0f
            );
            Matrix4f view = player.getCamera().getViewMatrix();
            frustum.update(projection, view);

            // 1. Render Moving Celestial Bodies (Sun & Moon in Sky) in Overworld
            if (world.getCurrentDimension() == no.minecraft.world.Dimension.OVERWORLD) {
                skyRenderer.render(projection, view, player.getPosition(), world.getDayFraction());
            }

            // 2. Render World Chunks
            float fogEnd = gs.getRenderDistance() * 16.0f;
            float fogStart = fogEnd * 0.65f;
            if (world.getCurrentDimension() != no.minecraft.world.Dimension.OVERWORLD) {
                fogStart = world.getCurrentDimension().getFogStart();
                fogEnd = world.getCurrentDimension().getFogEnd();
            } else if (world.getBlock((int) Math.floor(player.getPosition().x),
                                      (int) Math.floor(player.getPosition().y + Player.EYE_HEIGHT),
                                      (int) Math.floor(player.getPosition().z)) == BlockType.WATER) {
                fogStart = 1.0f;
                fogEnd = 20.0f;
            }
            float brightness = gs.getBrightness();
            float dynamicSunLight = Math.min(1.0f, sunLight * (1.0f + brightness * 0.4f) + brightness * 0.15f);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            worldShader.bind();
            worldShader.setUniform("uProjection", projection);
            worldShader.setUniform("uView", view);
            worldShader.setUniform("uSkyColor", skyColor);
            worldShader.setUniform("uFogStart", fogStart);
            worldShader.setUniform("uFogEnd", fogEnd);
            worldShader.setUniform("uSunLight", dynamicSunLight);

            atlas.bind();
            int playerCx = Math.floorDiv((int) Math.floor(player.getPosition().x), Chunk.SIZE_X);
            int playerCz = Math.floorDiv((int) Math.floor(player.getPosition().z), Chunk.SIZE_Z);
            world.updateAndRender(playerCx, playerCz, gs.getRenderDistance(), frustum);

            // 3. Render 3D Dropped Items on ground (spinning & bobbing)
            itemRenderer.render(world.getDroppedItems(), worldShader, view, projection, atlas);

            atlas.unbind();
            worldShader.unbind();
            glDisable(GL_BLEND);

            // 4. Render 3D Mobs (Zombie, Creeper, Spider, Skeleton, Blaze, Enderman, Ender Dragon, End Crystal), Arrows & Boats
            mobRenderer.render(world, world.getMobs(), world.getArrows(), world.getBoats(), world.getEnderPearls(), world.getEyeOfEnders(), projection, view, sunLight);

            // 4.5 Render 3D Player character model (if in 3rd person mode)
            if (!inMenu() && player.getCamera().getPerspective() != no.minecraft.player.Perspective.FIRST_PERSON) {
                playerRenderer.render(world, player, projection, view, dynamicSunLight, atlas);
            }

            // 5. Render Mining crack animation if actively mining
            if (!hud.isInventoryOpen() && isLeftMouseDown && miningDamage > 0.0f && miningBlockX != Integer.MIN_VALUE) {
                int stage = Math.clamp((int) (miningDamage * 10), 0, 9);
                miningOverlay.render(projection, view, miningBlockX, miningBlockY, miningBlockZ, stage, atlas);
            }

            // 6. Render Selection Box around targeted block (if inventory is not open)
            if (!hud.isInventoryOpen() && targetedHit != null) {
                BlockType targetType = world.getBlock(targetedHit.hitX, targetedHit.hitY, targetedHit.hitZ);
                blockOutline.render(projection, view, targetedHit.hitX, targetedHit.hitY, targetedHit.hitZ, targetType);
            }

            // 6.5 Render First-Person Hand & Held Item (only in first-person mode)
            if (!inMenu() && player.getCamera().getPerspective() == no.minecraft.player.Perspective.FIRST_PERSON) {
                atlas.bind();
                handRenderer.render(player, dynamicSunLight, dt, isLeftMouseDown && !hud.isInventoryOpen(), width, height);
                atlas.unbind();
            }

            // 7. Render 2D HUD or Main Menu or Pause Menu
            if (inMenu()) {
                mainMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
                if (pauseMenuScreen.isOpen()) {
                    pauseMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
                }
            } else {
                hud.render(width, height, (float) lastMouseX, (float) lastMouseY, player, atlas, world, chatManager, currentFps, targetedHit);
                if (!pauseMenuScreen.isOpen()) {
                    // Container screens draw on top of the HUD, after its three passes
                    renderContainerScreens(width, height);
                }
                if (pauseMenuScreen.isOpen()) {
                    pauseMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
                }
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void tick(float dt) {
        if (!inMenu() && mainMenu.getActiveWorldInfo() != null) {
            autoSaveTimer += dt;
            if (autoSaveTimer >= 60.0f) {
                autoSaveTimer = 0.0f;
                no.minecraft.world.save.WorldSaveManager.saveWorld(world, player, mainMenu.getActiveWorldInfo());
            }
        }

        no.minecraft.settings.GameSettings gs = no.minecraft.settings.GameSettings.getInstance();
        boolean paused = isPaused();
        if (inGui()) {
            isLeftMouseDown = false;
            isRightMouseDown = false;
        }

        chatManager.update(dt);

        // Input handling (multi-key simultaneous support)
        boolean fwd = !inGui() && isKeyDown(gs.keyForward);
        boolean bwd = !inGui() && isKeyDown(gs.keyBackward);
        boolean left = !inGui() && isKeyDown(gs.keyLeft);
        boolean right = !inGui() && isKeyDown(gs.keyRight);
        boolean jump = !inGui() && isKeyDown(gs.keyJump);

        // Sprinting via double-tap W, Left Shift, Tab, R, or Left/Right Control
        boolean sprintKey = isKeyDown(GLFW_KEY_LEFT_SHIFT) ||
                            isKeyDown(GLFW_KEY_TAB) ||
                            isKeyDown(GLFW_KEY_R) ||
                            isKeyDown(GLFW_KEY_LEFT_CONTROL) ||
                            isKeyDown(GLFW_KEY_RIGHT_CONTROL);

        boolean sprint = (doubleTapSprint || sprintKey) && fwd;
        sprintActive = sprint;

        boolean sneak = !inGui() && (isKeyDown(gs.keySneak) ||
                        isKeyDown(GLFW_KEY_RIGHT_SHIFT) ||
                        isKeyDown(GLFW_KEY_C) ||
                        isKeyDown(GLFW_KEY_LEFT_ALT));

        if (!paused) {
            player.update(dt, fwd, bwd, left, right, jump, sneak, sprint);
            world.update(dt, player);
            CombatTextManager.getInstance().update(dt);
            no.minecraft.render.ParticleManager.getInstance().update(dt);

            // Dimension Portal stepping check with cooldown
            if (dimensionPortalCooldown > 0) {
                dimensionPortalCooldown -= dt;
            } else {
                no.minecraft.physics.AABB playerAABB = player.getBoundingBox();
                int minX = (int) Math.floor(playerAABB.minX);
                int maxX = (int) Math.floor(playerAABB.maxX);
                int minY = (int) Math.floor(playerAABB.minY);
                int maxY = (int) Math.floor(playerAABB.maxY);
                int minZ = (int) Math.floor(playerAABB.minZ);
                int maxZ = (int) Math.floor(playerAABB.maxZ);

                boolean inNetherPortal = false;
                boolean inEndPortal = false;

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockType bt = world.getBlock(x, y, z);
                            if (bt == BlockType.NETHER_PORTAL) {
                                inNetherPortal = true;
                                break;
                            } else if (bt == BlockType.END_PORTAL) {
                                inEndPortal = true;
                                break;
                            }
                        }
                        if (inNetherPortal || inEndPortal) break;
                    }
                    if (inNetherPortal || inEndPortal) break;
                }

                if (inNetherPortal) {
                    dimensionPortalCooldown = 2.5f; // Wait 2.5s before next portal transition
                    if (world.getCurrentDimension() == no.minecraft.world.Dimension.OVERWORLD) {
                        world.teleportToDimension(no.minecraft.world.Dimension.NETHER, player);
                    } else if (world.getCurrentDimension() == no.minecraft.world.Dimension.NETHER) {
                        world.teleportToDimension(no.minecraft.world.Dimension.OVERWORLD, player);
                    }
                } else if (inEndPortal) {
                    dimensionPortalCooldown = 2.5f;
                    if (world.getCurrentDimension() == no.minecraft.world.Dimension.OVERWORLD) {
                        world.teleportToDimension(no.minecraft.world.Dimension.THE_END, player);
                    } else if (world.getCurrentDimension() == no.minecraft.world.Dimension.THE_END) {
                        world.teleportToDimension(no.minecraft.world.Dimension.OVERWORLD, player);
                    }
                }
            }
        }

        // Continuous Mining Logic (Left Click hold down)
        if (isLeftMouseDown && targetedHit != null && !hud.isInventoryOpen()) {
            int hx = targetedHit.hitX;
            int hy = targetedHit.hitY;
            int hz = targetedHit.hitZ;
            BlockType targetBlock = world.getBlock(hx, hy, hz);

            if (targetBlock != BlockType.AIR && targetBlock != BlockType.BEDROCK && targetBlock != BlockType.WATER && targetBlock != BlockType.LAVA && targetBlock.getHardness() >= 0.0f) {
                if (hx != miningBlockX || hy != miningBlockY || hz != miningBlockZ) {
                    miningBlockX = hx;
                    miningBlockY = hy;
                    miningBlockZ = hz;
                    miningDamage = 0.0f;
                }

                if (player.getGameMode() == GameMode.CREATIVE) {
                    // Creative: Instant break
                    world.setBlock(hx, hy, hz, BlockType.AIR);
                    no.minecraft.sound.SoundManager.getInstance().play(targetBlock.getBreakSound(), 1.0f);
                    miningDamage = 0.0f;
                    miningBlockX = Integer.MIN_VALUE;
                } else {
                    // Survival: Accumulate mining progress based on hardness & tool speed
                    float hardness = targetBlock.getHardness();
                    BlockType tool = player.getSelectedBlock();
                    float multiplier = tool != null ? tool.getMiningSpeedMultiplier(targetBlock) : 1.0f;

                    // Survival base mining rate (seconds to break = hardness / multiplier)
                    float speed = (multiplier / Math.max(0.05f, hardness));
                    miningDamage += speed * dt;

                    // Periodic dig sound while mining
                    miningSoundTimer += dt;
                    if (miningSoundTimer >= 0.28f) {
                        miningSoundTimer = 0.0f;
                        no.minecraft.sound.SoundManager.getInstance().play(targetBlock.getDigSound(), 0.6f);
                    }

                    if (miningDamage >= 1.0f) {
                        // Block broken!
                        world.setBlock(hx, hy, hz, BlockType.AIR);
                        player.addExhaustion(0.005f);
                        no.minecraft.sound.SoundManager.getInstance().play(targetBlock.getBreakSound(), 1.0f);
                        // Drop item if harvested correctly
                        if (targetBlock.canHarvest(tool)) {
                            world.spawnItemDrop(hx + 0.5f, hy + 0.5f, hz + 0.5f, targetBlock.getDrop(), 1);
                        }

                        // Damage tool
                        if (tool != null && tool.isDamageable()) {
                            player.getInventory().getSlot(player.getSelectedSlot()).damageTool(1);
                        }

                        miningDamage = 0.0f;
                        miningSoundTimer = 0.0f;
                        miningBlockX = Integer.MIN_VALUE;
                    }
                }
            } else {
                miningDamage = 0.0f;
                miningSoundTimer = 0.0f;
                miningBlockX = Integer.MIN_VALUE;
            }
        } else if (!isLeftMouseDown) {
            miningDamage = 0.0f;
            miningSoundTimer = 0.0f;
            miningBlockX = Integer.MIN_VALUE;
        }

        // Continuous Eating / Block Placement Logic (Right Click hold down)
        if (isRightMouseDown && !inGui()) {
            BlockType held = player.getSelectedBlock();
            if (held != null && held.isFood() && player.canEat(held)) {
                boolean finished = player.updateEating(dt, held);
                if (finished) {
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        player.useSelectedBlock();
                    }
                }
            } else {
                if (player.isEating()) {
                    player.stopEating();
                }
                rightClickTimer -= dt;
                if (rightClickTimer <= 0.0f) {
                    if (tryPlaceBlock()) {
                        rightClickTimer = 0.22f; // Minecraft default block placement cooldown (~4 ticks)
                    } else {
                        // Rapid polling so jumping upwards places block at the exact moment room clears
                        rightClickTimer = 0.02f;
                    }
                }
            }
        } else if (!isRightMouseDown) {
            if (player.isEating()) {
                player.stopEating();
            }
            rightClickTimer = 0.0f;
        }
    }

    private void cleanup() {
        // Emergency save: runs on normal exit AND on exceptions, so pending world state is not lost
        if (mainMenuScreen != null && !inMenu() && mainMenu.getActiveWorldInfo() != null) {
            try {
                no.minecraft.world.save.WorldSaveManager.saveWorld(world, player, mainMenu.getActiveWorldInfo());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (pauseMenu != null) pauseMenu.cleanup();
        if (mainMenu != null) mainMenu.cleanup();
        if (hud != null) hud.cleanup();
        if (skyRenderer != null) skyRenderer.cleanup();
        if (miningOverlay != null) miningOverlay.cleanup();
        if (mobRenderer != null) mobRenderer.cleanup();
        if (itemRenderer != null) itemRenderer.cleanup();
        if (handRenderer != null) handRenderer.cleanup();
        if (playerRenderer != null) playerRenderer.cleanup();
        if (blockOutline != null) blockOutline.cleanup();
        if (worldShader != null) worldShader.cleanup();
        if (atlas != null) atlas.cleanup();
        if (world != null) world.cleanup();
        no.minecraft.sound.SoundManager.getInstance().cleanup();

        if (window != NULL) {
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
        if (errorCallback != null) {
            errorCallback.free();
            errorCallback = null;
        }
        no.minecraft.settings.GameSettings.save();
    }
}
