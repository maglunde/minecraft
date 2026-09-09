package no.minecraft;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.player.Raycast;
import no.minecraft.render.*;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {
    private long window;
    private int width = 1280;
    private int height = 720;

    private World world;
    private Player player;
    private TextureAtlas atlas;
    private Shader worldShader;
    private BlockOutline blockOutline;
    private ItemRenderer itemRenderer;
    private HandRenderer handRenderer;
    private MobRenderer mobRenderer;
    private MiningOverlay miningOverlay;
    private SkyRenderer skyRenderer;
    private HUD hud;
    private MainMenu mainMenu;
    private no.minecraft.render.PauseMenu pauseMenu;
    private no.minecraft.chat.ChatManager chatManager = new no.minecraft.chat.ChatManager();

    private boolean cursorLocked = false;
    private double lastMouseX, lastMouseY;
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

    private static final String WORLD_VERT = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in float aLight;

            uniform mat4 uProjection;
            uniform mat4 uView;

            out vec2 vTexCoord;
            out float vLight;
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
            in float vLight;
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

                // Modulate block light with dynamic sun light (with 0.15 minimum ambient light)
                float ambient = 0.15;
                float dynamicLight = vLight * (ambient + (1.0 - ambient) * uSunLight);
                vec3 shadedColor = texColor.rgb * dynamicLight;
                float fogFactor = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                vec3 finalColor = mix(shadedColor, uSkyColor, fogFactor);

                FragColor = vec4(finalColor, texColor.a);
            }
            """;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

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

        window = glfwCreateWindow(width, height, "Minecraft Java Clone", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
        });

        // Setup mouse and keyboard input
        setupInput();

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
        mobRenderer = new MobRenderer();
        miningOverlay = new MiningOverlay();
        skyRenderer = new SkyRenderer();
        hud = new HUD();
        mainMenu = new MainMenu();
        pauseMenu = new PauseMenu();

        world = new World();
        int spawnY = world.getSpawnHeight(0, 0);
        player = new Player(world, 0.5f, spawnY + 0.05f, 0.5f);

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

    private void setupInput() {
        // Cursor movement
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (hud.isInventoryOpen() || mainMenu.isInMenu() || pauseMenu.isOpen() || chatManager.isOpen() || !cursorLocked) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = true;
                return;
            }

            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
                return;
            }

            float dx = (float) (xpos - lastMouseX);
            float dy = (float) (lastMouseY - ypos); // Inverted Y for OpenGL
            lastMouseX = xpos;
            lastMouseY = ypos;

            float sens = no.minecraft.settings.GameSettings.getInstance().getMouseSensitivity();
            player.getCamera().rotate(dx * sens, dy * sens);
        });

        // Mouse clicks for mining, combat, placing and crafting
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (pauseMenu.isOpen()) {
                if (action == GLFW_PRESS) {
                    pauseMenu.handleClick(lastMouseX, lastMouseY, button, width, height);
                    if (pauseMenu.isQuitToTitleRequested()) {
                        pauseMenu.clearQuitToTitleRequested();
                        pauseMenu.close();
                        mainMenu.setInMenu(true);
                        setCursorLocked(false);
                    } else if (!pauseMenu.isOpen()) {
                        if (!mainMenu.isInMenu()) {
                            setCursorLocked(true);
                        }
                    }
                }
                return;
            }

            if (mainMenu.isInMenu()) {
                if (action == GLFW_PRESS) {
                    boolean wasGameStarted = mainMenu.isGameStarted();
                    if (mainMenu.handleClick(lastMouseX, lastMouseY, button, width, height)) {
                        player.setGameMode(mainMenu.getSelectedMode());
                        // If freshly started from title menu, generate a brand new random seed terrain!
                        if (!wasGameStarted) {
                            long newSeed = new java.util.Random().nextLong();
                            world.setSeed(newSeed);
                            int sy = world.getSpawnHeight(0, 0);
                            player.resetToSpawn(0.5f, sy + 0.05f, 0.5f);
                            mainMenu.setGameStarted(true);
                        }
                        setCursorLocked(true);
                    } else if (mainMenu.isOpenOptionsRequested()) {
                        mainMenu.clearOpenOptionsRequested();
                        pauseMenu.openOptionsFromTitle();
                    } else if (mainMenu.isQuitRequested()) {
                        glfwSetWindowShouldClose(window, true);
                    }
                }
                return;
            }

            if (hud.isInventoryOpen()) {
                if (action == GLFW_PRESS) {
                    hud.handleMouseClick(lastMouseX, lastMouseY, button, player, width, height);
                }
                return;
            }

            if (chatManager.isOpen()) {
                return;
            }

            if (!cursorLocked) {
                if (action == GLFW_PRESS) {
                    setCursorLocked(true);
                }
                return;
            }

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    isLeftMouseDown = true;
                    handRenderer.swing();

                    // 1. Check if attacking a mob with sword / tool / fist
                    Vector3f eye = player.getCamera().getPosition();
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
                        int dmg = tool != null ? tool.getAttackDamage() : 1;
                        hitMob.takeDamage(dmg, fwd.x, fwd.z, world);
                        float mobH = hitMob.getType().getHeight();
                        CombatTextManager.getInstance().add(
                                hitMob.getPosition().x,
                                hitMob.getPosition().y + mobH * 0.75f,
                                hitMob.getPosition().z,
                                dmg / 2.0f
                        );
                        // Damage tool in survival
                        if (player.getGameMode() == GameMode.SURVIVAL) {
                            player.getInventory().getSlot(player.getSelectedSlot()).damageTool(1);
                        }
                        miningDamage = 0.0f; // Reset mining
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

        // Scroll for hotbar
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
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

            if (action == GLFW_PRESS) {
                if (pauseMenu.isOpen()) {
                    pauseMenu.handleKey(key, action);
                    if (!pauseMenu.isOpen()) {
                        if (!mainMenu.isInMenu()) {
                            setCursorLocked(true);
                        }
                    }
                    return;
                }

                if (mainMenu.isInMenu()) {
                    if (mainMenu.handleKey(key, action)) {
                        return;
                    }
                    if (key == GLFW_KEY_ESCAPE && mainMenu.isGameStarted()) {
                        // Resume game if already in progress
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        mainMenu.setInMenu(false);
                        setCursorLocked(true);
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

                no.minecraft.settings.GameSettings gs = no.minecraft.settings.GameSettings.getInstance();

                if (key == GLFW_KEY_T && !hud.isInventoryOpen() && !pauseMenu.isOpen()) {
                    // Open Chat empty (prevent 't' from char callback)
                    ignoreNextChar = true;
                    chatManager.openChat("");
                    setCursorLocked(false);
                    return;
                } else if (key == GLFW_KEY_SLASH && !hud.isInventoryOpen() && !pauseMenu.isOpen()) {
                    // Open Chat with prefilled "/" for quick commands (prevent redundant '/' from char callback)
                    ignoreNextChar = true;
                    chatManager.openChat("/");
                    setCursorLocked(false);
                    return;
                }

                if (key == GLFW_KEY_ESCAPE) {
                    if (hud.isInventoryOpen()) {
                        hud.closeInventory(player);
                        setCursorLocked(true);
                    } else {
                        // Open dedicated Pause Menu
                        pauseMenu.open();
                        setCursorLocked(false);
                    }
                } else if (key == GLFW_KEY_M && !hud.isInventoryOpen() && !pauseMenu.isOpen()) {
                    mainMenu.setInMenu(true);
                    setCursorLocked(false);
                } else if (!mainMenu.isInMenu() && !pauseMenu.isOpen() && key == gs.keyInventory) {
                    // Toggle Inventory / Crafting GUI
                    hud.toggleInventory(player);
                    setCursorLocked(!hud.isInventoryOpen());
                } else if (!mainMenu.isInMenu() && !pauseMenu.isOpen() && (key == GLFW_KEY_F3 || key == gs.keyToggleDebug)) {
                    // Toggle F3 Debug Screen
                    hud.toggleDebugInfo();
                } else if (!mainMenu.isInMenu() && !pauseMenu.isOpen() && key == GLFW_KEY_G) {
                    // Toggle GameMode (Survival / Creative)
                    player.toggleGameMode();
                } else if (!mainMenu.isInMenu() && !pauseMenu.isOpen() && key == GLFW_KEY_F) {
                    player.toggleFlying();
                } else if (key == GLFW_KEY_P) {
                    // Reset position to ground at spawn
                    int gy = world.getSpawnHeight((int) Math.floor(player.getSpawnPosition().x), (int) Math.floor(player.getSpawnPosition().z));
                    player.getPosition().set(player.getSpawnPosition().x, gy + 0.05f, player.getSpawnPosition().z);
                    player.getVelocity().set(0, 0, 0);
                } else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                    player.setSelectedSlot(key - GLFW_KEY_1);
                } else if (key == gs.keyJump) {
                    // Double-tap Space detection for flying in Creative mode (0 gravity)
                    if (!hud.isInventoryOpen() && !pauseMenu.isOpen()) {
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

        // Text character typing callback for Chat
        glfwSetCharCallback(window, (win, codepoint) -> {
            if (ignoreNextChar) {
                ignoreNextChar = false;
                return;
            }
            if (chatManager.isOpen()) {
                chatManager.addChar((char) codepoint);
            }
        });
    }

    private boolean handleRightClickAction() {
        // 1. Bow shooting (fires Arrow entity if player has arrows or is in Creative)
        BlockType held = player.getSelectedBlock();
        if (held == BlockType.BOW) {
            boolean hasArrow = player.getGameMode() == GameMode.CREATIVE || player.getInventory().getItemCount(BlockType.ARROW) > 0;
            if (hasArrow) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.getInventory().removeItem(BlockType.ARROW, 1);
                    player.getInventory().getSlot(player.getSelectedSlot()).damageTool(1);
                }
                Vector3f eye = player.getCamera().getPosition();
                Vector3f fwd = player.getCamera().getForward();
                world.spawnArrow(eye.x, eye.y, eye.z, fwd.x * 24.0f, fwd.y * 24.0f, fwd.z * 24.0f);
                no.minecraft.sound.SoundManager.getInstance().play("bow_shoot", 1.0f);
                return true;
            }
        }

        // 2. Eye of Ender throwing towards Stronghold (48, 14, 48)
        if (held == BlockType.EYE_OF_ENDER) {
            Raycast.HitResult target = Raycast.raycast(world, player.getCamera().getPosition(), player.getCamera().getForward(), 5.0f);
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
                Vector3f eye = player.getCamera().getPosition();
                world.spawnArrow(eye.x, eye.y, eye.z, dx * 16.0f, 6.0f, dz * 16.0f);
                no.minecraft.sound.SoundManager.getInstance().play("pop", 1.0f);
                no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.EYE_SPY);
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
                player.getCamera().getPosition(),
                player.getCamera().getForward(),
                5.5f
        );

        if (hit != null) {
            BlockType clickedBlock = world.getBlock(hit.hitX, hit.hitY, hit.hitZ);
            if (clickedBlock == BlockType.CRAFTING_TABLE) {
                hud.openCraftingTable();
                setCursorLocked(false);
                isLeftMouseDown = false;
                isRightMouseDown = false;
                return true;
            }

            // Inserting Eye of Ender into End Portal Frame
            BlockType held = player.getSelectedBlock();
            if (clickedBlock == BlockType.END_PORTAL_FRAME && held == BlockType.EYE_OF_ENDER) {
                world.setBlock(hit.hitX, hit.hitY, hit.hitZ, BlockType.END_PORTAL_FRAME_FILLED);
                no.minecraft.sound.SoundManager.getInstance().play("stone_dig", 1.0f);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.useSelectedBlock();
                }
                checkAndActivateEndPortal();
                return true;
            }

            // Flint and Steel igniting Nether Portal
            if (held == BlockType.FLINT_AND_STEEL) {
                if (clickedBlock == BlockType.OBSIDIAN) {
                    igniteNetherPortal(hit.hitX, hit.hitY, hit.hitZ);
                    no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.0f);
                    return true;
                }
            }

            // Place block only if player has it in inventory
            if (player.canPlaceSelectedBlock()) {
                BlockType toPlace = player.getSelectedBlock();
                if (toPlace != null && toPlace.isSolid()) {
                    boolean roomAvailable = player.isFlying() || !player.getBoundingBox().intersects(
                            new no.minecraft.player.AABB(hit.placeX, hit.placeY, hit.placeZ,
                                    hit.placeX + 1, hit.placeY + 1, hit.placeZ + 1));
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

    private void igniteNetherPortal(int x, int y, int z) {
        // Find which plane (XY or ZY) forms a valid Minecraft Nether Portal frame:
        // A standard portal frame is 4 wide x 5 tall (interior 2x3 air blocks),
        // or up to 23x23. We support standard 4x5 vertical frames along X or Z axis.
        if (tryIgnitePortalAxis(x, y, z, true)) return;
        tryIgnitePortalAxis(x, y, z, false);
    }

    private boolean tryIgnitePortalAxis(int startX, int startY, int startZ, boolean alongX) {
        // Search in a local neighborhood around the clicked obsidian block for candidate portal interior base
        for (int offset = -3; offset <= 1; offset++) {
            for (int dy = -1; dy <= 1; dy++) {
                int baseX = alongX ? startX + offset : startX;
                int baseZ = alongX ? startZ : startZ + offset;
                int baseY = startY + dy;

                // Check if this (baseX, baseY, baseZ) is the bottom-left interior corner of a 2x3 portal
                // Interior is: (i=0..1, j=0..2)
                // Bottom frame: (baseX + (alongX ? i : 0), baseY - 1, baseZ + (alongX ? 0 : i)) == OBSIDIAN
                // Top frame: (baseX + (alongX ? i : 0), baseY + 3, baseZ + (alongX ? 0 : i)) == OBSIDIAN
                // Left frame: (baseX - (alongX ? 1 : 0), baseY + j, baseZ - (alongX ? 0 : 1)) == OBSIDIAN
                // Right frame: (baseX + (alongX ? 2 : 0), baseY + j, baseZ + (alongX ? 0 : 2)) == OBSIDIAN
                boolean validFrame = true;

                // Bottom and Top frames
                for (int i = 0; i < 2; i++) {
                    int bx = alongX ? baseX + i : baseX;
                    int bz = alongX ? baseZ : baseZ + i;
                    if (world.getBlock(bx, baseY - 1, bz) != BlockType.OBSIDIAN ||
                        world.getBlock(bx, baseY + 3, bz) != BlockType.OBSIDIAN) {
                        validFrame = false;
                        break;
                    }
                }
                if (!validFrame) continue;

                // Left and Right sides
                for (int j = 0; j < 3; j++) {
                    int lx = alongX ? baseX - 1 : baseX;
                    int lz = alongX ? baseZ : baseZ - 1;
                    int rx = alongX ? baseX + 2 : baseX;
                    int rz = alongX ? baseZ : baseZ + 2;
                    if (world.getBlock(lx, baseY + j, lz) != BlockType.OBSIDIAN ||
                        world.getBlock(rx, baseY + j, rz) != BlockType.OBSIDIAN) {
                        validFrame = false;
                        break;
                    }
                }
                if (!validFrame) continue;

                // Check that interior is air (or already portal)
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 3; j++) {
                        int ix = alongX ? baseX + i : baseX;
                        int iz = alongX ? baseZ : baseZ + i;
                        BlockType cur = world.getBlock(ix, baseY + j, iz);
                        if (cur != BlockType.AIR && cur != BlockType.NETHER_PORTAL) {
                            validFrame = false;
                            break;
                        }
                    }
                    if (!validFrame) break;
                }
                if (!validFrame) continue;

                // Valid frame! Fill interior with NETHER_PORTAL
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 3; j++) {
                        int ix = alongX ? baseX + i : baseX;
                        int iz = alongX ? baseZ : baseZ + i;
                        world.setBlock(ix, baseY + j, iz, BlockType.NETHER_PORTAL);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void checkAndActivateEndPortal() {
        // Stronghold center portal space is at local x: 5..7, z: 5..7 in chunk (3, 3)
        // World coordinates: cx*16 + 5 = 48 + 5 = 53, py = 12
        int py = 12;
        boolean allFilled = true;
        // Check frames around (53..55, 53..55)
        for (int x = 53; x <= 55; x++) {
            if (world.getBlock(x, py, 52) != BlockType.END_PORTAL_FRAME_FILLED ||
                world.getBlock(x, py, 56) != BlockType.END_PORTAL_FRAME_FILLED) {
                allFilled = false;
                break;
            }
        }
        for (int z = 53; z <= 55; z++) {
            if (world.getBlock(52, py, z) != BlockType.END_PORTAL_FRAME_FILLED ||
                world.getBlock(56, py, z) != BlockType.END_PORTAL_FRAME_FILLED) {
                allFilled = false;
                break;
            }
        }

        if (allFilled) {
            // Fill 3x3 horizontal portal
            for (int x = 53; x <= 55; x++) {
                for (int z = 53; z <= 55; z++) {
                    world.setBlock(x, py, z, BlockType.END_PORTAL);
                }
            }
            no.minecraft.sound.SoundManager.getInstance().play("explode", 0.8f);
        }
    }

    private void setCursorLocked(boolean locked) {
        this.cursorLocked = locked;
        glfwSetInputMode(window, GLFW_CURSOR, locked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        if (locked) {
            firstMouse = true;
        }
    }

    private void loop() {
        double lastTime = glfwGetTime();
        double fpsTimer = lastTime;
        int frameCount = 0;
        int currentFps = 60;

        Vector3f skyColor = new Vector3f(0.53f, 0.81f, 0.98f); // Minecraft sky blue

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // Cap dt to prevent physics tunneling during lags
            dt = Math.min(dt, 0.05f);

            no.minecraft.settings.GameSettings gs = no.minecraft.settings.GameSettings.getInstance();
            boolean isPaused = mainMenu.isInMenu() || pauseMenu.isOpen();
            boolean inGui = isPaused || hud.isInventoryOpen() || chatManager.isOpen();
            if (inGui) {
                isLeftMouseDown = false;
                isRightMouseDown = false;
            }

            chatManager.update(dt);

            // Input handling (multi-key simultaneous support)
            boolean fwd = !inGui && isKeyDown(gs.keyForward);
            boolean bwd = !inGui && isKeyDown(gs.keyBackward);
            boolean left = !inGui && isKeyDown(gs.keyLeft);
            boolean right = !inGui && isKeyDown(gs.keyRight);
            boolean jump = !inGui && isKeyDown(gs.keyJump);

            // Sprinting via double-tap W, Left Shift, Tab, R, or Left/Right Control
            boolean sprintKey = isKeyDown(GLFW_KEY_LEFT_SHIFT) ||
                                isKeyDown(GLFW_KEY_TAB) ||
                                isKeyDown(GLFW_KEY_R) ||
                                isKeyDown(GLFW_KEY_LEFT_CONTROL) ||
                                isKeyDown(GLFW_KEY_RIGHT_CONTROL);

            boolean sprint = (doubleTapSprint || sprintKey) && fwd;

            boolean sneak = !inGui && (isKeyDown(gs.keySneak) ||
                            isKeyDown(GLFW_KEY_RIGHT_SHIFT) ||
                            isKeyDown(GLFW_KEY_C) ||
                            isKeyDown(GLFW_KEY_LEFT_ALT));

            if (!isPaused) {
                player.update(dt, fwd, bwd, left, right, jump, sneak, sprint);
                world.update(dt, player);
                CombatTextManager.getInstance().update(dt);

                // Dimension Portal stepping check with cooldown
                if (dimensionPortalCooldown > 0) {
                    dimensionPortalCooldown -= dt;
                } else {
                    no.minecraft.player.AABB playerAABB = player.getBoundingBox();
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
            Raycast.HitResult targetedHit = null;
            if (!inGui) {
                targetedHit = Raycast.raycast(
                        world,
                        player.getCamera().getPosition(),
                        player.getCamera().getForward(),
                        5.5f
                );
            }

            if (isLeftMouseDown && targetedHit != null && !hud.isInventoryOpen()) {
                int hx = targetedHit.hitX;
                int hy = targetedHit.hitY;
                int hz = targetedHit.hitZ;
                BlockType targetBlock = world.getBlock(hx, hy, hz);

                if (targetBlock != BlockType.AIR && targetBlock != BlockType.BEDROCK) {
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
                            no.minecraft.sound.SoundManager.getInstance().play(targetBlock.getBreakSound(), 1.0f);
                            // Drop item if harvested correctly
                            boolean toolRequired = targetBlock.requiresToolForDrop();
                            boolean hasCorrectTool = tool != null && tool.getItemToolType() == targetBlock.getEffectiveTool();

                            if (!toolRequired || hasCorrectTool) {
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

            // Continuous Block Placement Logic (Right Click hold down)
            if (isRightMouseDown && !inGui) {
                rightClickTimer -= dt;
                if (rightClickTimer <= 0.0f) {
                    if (tryPlaceBlock()) {
                        rightClickTimer = 0.22f; // Minecraft default block placement cooldown (~4 ticks)
                    } else {
                        // Rapid polling so jumping upwards places block at the exact moment room clears
                        rightClickTimer = 0.02f;
                    }
                }
            } else if (!isRightMouseDown) {
                rightClickTimer = 0.0f;
            }

            // FPS Counter & Info
            frameCount++;
            if (currentTime - fpsTimer >= 1.0) {
                currentFps = frameCount;
                String status = player.getDeathFlashTimer() > 0 ? " [💀 DU DØDE - Falt ut av verden!]" : "";
                String sprintIndicator = sprint ? " [⚡ SPRINT]" : "";
                String countStr = player.getSelectedBlockCount() == -1 ? "∞" : String.valueOf(player.getSelectedBlockCount());
                String modeStr = player.getGameMode().getDisplayName();
                if (player.getGameMode() == GameMode.SURVIVAL) {
                    modeStr += String.format(" (HP: %d/20)", player.getHealth());
                } else if (player.isFlying()) {
                    modeStr += " [Flyvende]";
                }

                String timeStr = world.isNight() ? "🌙 Natt" : "☀️ Dag";
                String title = String.format("Minecraft Java Clone | Seed: %d | FPS: %d | Tid: %s | Mobs: %d | Modus: %s%s | Valgt: %s (x%s) | Drops: %d | E: Crafting%s",
                        world.getSeed(),
                        frameCount,
                        timeStr,
                        world.getMobs().size(),
                        modeStr,
                        sprintIndicator,
                        player.getSelectedBlock() != null ? player.getSelectedBlock().getName() : "Ingen",
                        countStr,
                        world.getDroppedItems().size(),
                        status);
                glfwSetWindowTitle(window, title);
                frameCount = 0;
                fpsTimer += 1.0;
            }

            // Calculate dynamic day/night sky color & lighting
            float sunLight = world.getSunLightLevel();
            Vector3f daySky = new Vector3f(0.53f, 0.81f, 0.98f);    // Minecraft azure sky
            Vector3f nightSky = new Vector3f(0.04f, 0.05f, 0.10f);  // Deep starry night sky
            Vector3f sunsetColor = new Vector3f(0.85f, 0.42f, 0.22f); // Golden sunset orange

            if (world.getCurrentDimension() == no.minecraft.world.Dimension.NETHER) {
                skyColor.set(world.getCurrentDimension().getSkyR(), world.getCurrentDimension().getSkyG(), world.getCurrentDimension().getSkyB());
            } else if (world.getCurrentDimension() == no.minecraft.world.Dimension.THE_END) {
                skyColor.set(world.getCurrentDimension().getSkyR(), world.getCurrentDimension().getSkyG(), world.getCurrentDimension().getSkyB());
            } else {
                // Blend day and night sky
                skyColor.set(
                        nightSky.x + (daySky.x - nightSky.x) * sunLight,
                        nightSky.y + (daySky.y - nightSky.y) * sunLight,
                        nightSky.z + (daySky.z - nightSky.z) * sunLight
                );

                // Add warm sunset / sunrise tint when sun is on the horizon
                float sunsetFactor = 1.0f - Math.abs(sunLight - 0.5f) * 2.0f;
                if (sunsetFactor > 0.0f) {
                    skyColor.lerp(sunsetColor, sunsetFactor * 0.45f);
                }

                // Underwater atmosphere if submerged
                int hx = (int) Math.floor(player.getPosition().x);
                int hy = (int) Math.floor(player.getPosition().y + Player.EYE_HEIGHT);
                int hz = (int) Math.floor(player.getPosition().z);
                if (world.getBlock(hx, hy, hz) == BlockType.WATER) {
                    skyColor.set(0.06f, 0.22f, 0.55f);
                }
            }

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
            world.updateAndRender();

            // 3. Render 3D Dropped Items on ground (spinning & bobbing)
            itemRenderer.render(world.getDroppedItems(), worldShader, view, projection, atlas);

            atlas.unbind();
            worldShader.unbind();
            glDisable(GL_BLEND);

            // 4. Render 3D Mobs (Zombie, Creeper, Spider, Skeleton, Blaze, Enderman, Ender Dragon, End Crystal) & Arrows
            mobRenderer.render(world.getMobs(), world.getArrows(), projection, view, sunLight);

            // 5. Render Mining crack animation if actively mining
            if (!hud.isInventoryOpen() && isLeftMouseDown && miningDamage > 0.0f && miningBlockX != Integer.MIN_VALUE) {
                int stage = Math.clamp((int) (miningDamage * 10), 0, 9);
                miningOverlay.render(projection, view, miningBlockX, miningBlockY, miningBlockZ, stage, atlas);
            }

            // 6. Render Selection Box around targeted block (if inventory is not open)
            if (!hud.isInventoryOpen() && targetedHit != null) {
                blockOutline.render(projection, view, targetedHit.hitX, targetedHit.hitY, targetedHit.hitZ);
            }

            // 6.5 Render First-Person Hand & Held Item (if in game)
            if (!mainMenu.isInMenu()) {
                atlas.bind();
                handRenderer.render(player, dynamicSunLight, dt, isLeftMouseDown && !hud.isInventoryOpen(), width, height);
                atlas.unbind();
            }

            // 7. Render 2D HUD or Main Menu or Pause Menu
            if (mainMenu.isInMenu()) {
                mainMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
                if (pauseMenu.isOpen()) {
                    pauseMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
                }
            } else {
                hud.render(width, height, (float) lastMouseX, (float) lastMouseY, player, atlas, world, chatManager, currentFps, targetedHit);
                if (pauseMenu.isOpen()) {
                    pauseMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
                }
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        if (pauseMenu != null) {
            pauseMenu.cleanup();
        }
        mainMenu.cleanup();
        hud.cleanup();
        skyRenderer.cleanup();
        miningOverlay.cleanup();
        mobRenderer.cleanup();
        itemRenderer.cleanup();
        if (handRenderer != null) {
            handRenderer.cleanup();
        }
        blockOutline.cleanup();
        worldShader.cleanup();
        atlas.cleanup();
        world.cleanup();
        no.minecraft.sound.SoundManager.getInstance().cleanup();

        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
