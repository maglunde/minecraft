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
    private MobRenderer mobRenderer;
    private MiningOverlay miningOverlay;
    private SkyRenderer skyRenderer;
    private HUD hud;
    private MainMenu mainMenu;

    private boolean cursorLocked = false;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private float mouseSensitivity = 0.12f;

    // Mining progress state
    private int miningBlockX = Integer.MIN_VALUE;
    private int miningBlockY = Integer.MIN_VALUE;
    private int miningBlockZ = Integer.MIN_VALUE;
    private float miningDamage = 0.0f;
    private float miningSoundTimer = 0.0f;
    private boolean isLeftMouseDown = false;

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
        mobRenderer = new MobRenderer();
        miningOverlay = new MiningOverlay();
        skyRenderer = new SkyRenderer();
        hud = new HUD();
        mainMenu = new MainMenu();

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
            if (hud.isInventoryOpen() || !cursorLocked) {
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

            player.getCamera().rotate(dx * mouseSensitivity, dy * mouseSensitivity);
        });

        // Mouse clicks for mining, combat, placing and crafting
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (mainMenu.isInMenu()) {
                if (action == GLFW_PRESS) {
                    if (mainMenu.handleClick(lastMouseX, lastMouseY, button, width, height)) {
                        // Started game from menu!
                        player.setGameMode(mainMenu.getSelectedMode());
                        setCursorLocked(true);
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

            if (!cursorLocked) {
                if (action == GLFW_PRESS) {
                    setCursorLocked(true);
                }
                return;
            }

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    isLeftMouseDown = true;

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
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
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
                        return;
                    }

                    // Place block only if player has it in inventory
                    if (player.canPlaceSelectedBlock()) {
                        BlockType toPlace = player.getSelectedBlock();
                        if (toPlace.isSolid() && (player.isFlying() || !player.getBoundingBox().intersects(
                                new no.minecraft.player.AABB(hit.placeX, hit.placeY, hit.placeZ,
                                        hit.placeX + 1, hit.placeY + 1, hit.placeZ + 1)))) {
                            world.setBlock(hit.placeX, hit.placeY, hit.placeZ, toPlace);
                            no.minecraft.sound.SoundManager.getInstance().play(toPlace.getDigSound(), 0.8f);
                            player.useSelectedBlock();
                        }
                    }
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
                if (key == GLFW_KEY_ESCAPE) {
                    if (mainMenu.isInMenu()) {
                        if (mainMenu.isGameStarted()) {
                            // Resume game from pause
                            no.minecraft.sound.SoundManager.getInstance().play("click");
                            mainMenu.setInMenu(false);
                            player.setGameMode(mainMenu.getSelectedMode());
                            setCursorLocked(true);
                        }
                        return;
                    }
                    if (hud.isInventoryOpen()) {
                        hud.closeInventory(player);
                        setCursorLocked(true);
                    } else {
                        // Pause game
                        mainMenu.setInMenu(true);
                        setCursorLocked(false);
                    }
                } else if (key == GLFW_KEY_M && !hud.isInventoryOpen()) {
                    mainMenu.setInMenu(true);
                    setCursorLocked(false);
                } else if (!mainMenu.isInMenu() && key == GLFW_KEY_E) {
                    // Toggle Inventory / Crafting GUI
                    hud.toggleInventory(player);
                    setCursorLocked(!hud.isInventoryOpen());
                } else if (!mainMenu.isInMenu() && key == GLFW_KEY_G) {
                    // Toggle GameMode (Survival / Creative)
                    player.toggleGameMode();
                } else if (!mainMenu.isInMenu() && key == GLFW_KEY_F) {
                    player.toggleFlying();
                } else if (key == GLFW_KEY_P) {
                    // Reset position to ground at spawn
                    int gy = world.getSpawnHeight((int) Math.floor(player.getSpawnPosition().x), (int) Math.floor(player.getSpawnPosition().z));
                    player.getPosition().set(player.getSpawnPosition().x, gy + 0.05f, player.getSpawnPosition().z);
                    player.getVelocity().set(0, 0, 0);
                } else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                    player.setSelectedSlot(key - GLFW_KEY_1);
                } else if (key == GLFW_KEY_SPACE) {
                    // Double-tap Space detection for flying in Creative mode (0 gravity)
                    if (!hud.isInventoryOpen()) {
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
                } else if (key == GLFW_KEY_W) {
                    // Double-tap W detection for Minecraft-style sprinting
                    double now = glfwGetTime();
                    if (now - lastWPressTime < 0.30) {
                        doubleTapSprint = true;
                    }
                    lastWPressTime = now;
                }
            } else if (action == GLFW_RELEASE) {
                if (key == GLFW_KEY_W) {
                    doubleTapSprint = false;
                }
            }
        });
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

        Vector3f skyColor = new Vector3f(0.53f, 0.81f, 0.98f); // Minecraft sky blue

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            float dt = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // Cap dt to prevent physics tunneling during lags
            dt = Math.min(dt, 0.05f);

            // Input handling (multi-key simultaneous support)
            boolean fwd = !mainMenu.isInMenu() && !hud.isInventoryOpen() && isKeyDown(GLFW_KEY_W);
            boolean bwd = !mainMenu.isInMenu() && !hud.isInventoryOpen() && isKeyDown(GLFW_KEY_S);
            boolean left = !mainMenu.isInMenu() && !hud.isInventoryOpen() && isKeyDown(GLFW_KEY_A);
            boolean right = !mainMenu.isInMenu() && !hud.isInventoryOpen() && isKeyDown(GLFW_KEY_D);
            boolean jump = !mainMenu.isInMenu() && !hud.isInventoryOpen() && isKeyDown(GLFW_KEY_SPACE);

            // Sprinting via double-tap W, Left Shift, Tab, R, or Left/Right Control
            boolean sprintKey = isKeyDown(GLFW_KEY_LEFT_SHIFT) ||
                                isKeyDown(GLFW_KEY_TAB) ||
                                isKeyDown(GLFW_KEY_R) ||
                                isKeyDown(GLFW_KEY_LEFT_CONTROL) ||
                                isKeyDown(GLFW_KEY_RIGHT_CONTROL);

            boolean sprint = (doubleTapSprint || sprintKey) && fwd;

            boolean sneak = !mainMenu.isInMenu() && !hud.isInventoryOpen() && (isKeyDown(GLFW_KEY_LEFT_SHIFT) ||
                            isKeyDown(GLFW_KEY_RIGHT_SHIFT) ||
                            isKeyDown(GLFW_KEY_C) ||
                            isKeyDown(GLFW_KEY_LEFT_ALT));

            if (!mainMenu.isInMenu()) {
                player.update(dt, fwd, bwd, left, right, jump, sneak, sprint);
                world.update(dt, player);
            }

            // Continuous Mining Logic (Left Click hold down)
            Raycast.HitResult targetedHit = null;
            if (!mainMenu.isInMenu() && !hud.isInventoryOpen()) {
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

            // FPS Counter & Info
            frameCount++;
            if (currentTime - fpsTimer >= 1.0) {
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
                String title = String.format("Minecraft Java Clone | FPS: %d | Tid: %s | Mobs: %d | Modus: %s%s | Valgt: %s (x%s) | Drops: %d | E: Crafting%s",
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

            // Clear buffers
            glClearColor(skyColor.x, skyColor.y, skyColor.z, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Setup 3D matrices
            Matrix4f projection = new Matrix4f().perspective(
                    (float) Math.toRadians(75.0),
                    (float) width / (float) height,
                    0.05f,
                    300.0f
            );
            Matrix4f view = player.getCamera().getViewMatrix();

            // 1. Render Moving Celestial Bodies (Sun & Moon in Sky)
            skyRenderer.render(projection, view, player.getPosition(), world.getDayFraction());

            // 2. Render World Chunks
            float fogEnd = World.RENDER_DISTANCE * 16.0f;
            float fogStart = fogEnd * 0.65f;

            worldShader.bind();
            worldShader.setUniform("uProjection", projection);
            worldShader.setUniform("uView", view);
            worldShader.setUniform("uSkyColor", skyColor);
            worldShader.setUniform("uFogStart", fogStart);
            worldShader.setUniform("uFogEnd", fogEnd);
            worldShader.setUniform("uSunLight", sunLight);

            atlas.bind();
            world.updateAndRender();

            // 3. Render 3D Dropped Items on ground (spinning & bobbing)
            itemRenderer.render(world.getDroppedItems(), worldShader, view, projection, atlas);

            atlas.unbind();
            worldShader.unbind();

            // 4. Render 3D Mobs (Zombie, Creeper, Spider, Skeleton) & Arrows
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

            // 7. Render 2D HUD or Main Menu
            if (mainMenu.isInMenu()) {
                mainMenu.render(width, height, (float) lastMouseX, (float) lastMouseY, atlas);
            } else {
                hud.render(width, height, (float) lastMouseX, (float) lastMouseY, player, atlas);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        mainMenu.cleanup();
        hud.cleanup();
        skyRenderer.cleanup();
        miningOverlay.cleanup();
        mobRenderer.cleanup();
        itemRenderer.cleanup();
        blockOutline.cleanup();
        worldShader.cleanup();
        atlas.cleanup();
        world.cleanup();
        no.minecraft.sound.SoundManager.getInstance().cleanup();

        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
