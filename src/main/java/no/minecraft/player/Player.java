package no.minecraft.player;

import no.minecraft.physics.AABB;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class Player {
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float SNEAK_EYE_HEIGHT = 1.42f;

    public static final float GRAVITY = -26.0f;
    public static final float JUMP_SPEED = 8.5f;
    public static final float WALK_SPEED = 4.8f;
    public static final float SPRINT_SPEED = 7.5f;
    public static final float SNEAK_SPEED = 1.6f;
    public static final float FLY_SPEED = 12.0f;

    public static final int MAX_HEALTH = 20;

    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Camera camera;
    private final World world;

    private boolean onGround = false;
    private boolean flying = false;

    private GameMode gameMode = GameMode.SURVIVAL;
    private final Inventory inventory = new Inventory();
    private final ItemStack offhandItem = new ItemStack(BlockType.AIR, 0);
    private int health = MAX_HEALTH;
    private float lastAirVerticalSpeed = 0.0f;

    // Hotbar blocks for Creative mode
    public static final BlockType[] CREATIVE_HOTBAR_BLOCKS = {
            BlockType.GRASS,
            BlockType.DIRT,
            BlockType.STONE,
            BlockType.COBBLESTONE,
            BlockType.WOOD,
            BlockType.LEAVES,
            BlockType.PLANKS,
            BlockType.BRICKS,
            BlockType.GLASS
    };
    private int selectedSlot = 0;

    private final Vector3f spawnPosition = new Vector3f();
    private float deathFlashTimer = 0.0f;

    private float jumpBufferTimer = 0.0f;
    private float coyoteTimer = 0.0f;
    private boolean isSprinting = false;
    private boolean isSneaking = false;
    private float walkAnimTime = 0.0f;
    private float swingProgress = 0.0f;
    private float lavaBurnTimer = 0.0f;

    // Hunger System
    private int hunger = 20;
    private float saturation = 5.0f;
    private float exhaustion = 0.0f;
    private float regenTimer = 0.0f;
    private float fastRegenTimer = 0.0f;
    private float starveTimer = 0.0f;

    // Eating System (32 ticks = 1.61s in Minecraft)
    public static final float EAT_DURATION = 1.61f;
    private boolean isEating = false;
    private float eatTimer = 0.0f;
    private float eatSoundTimer = 0.0f;

    private boolean movingForward = false;
    private boolean movingBackward = false;
    private boolean movingLeft = false;
    private boolean movingRight = false;
    private boolean inWater = false;
    private boolean onLadder = false;
    private no.minecraft.entity.Boat ridingBoat = null;

    public Player(World world, float startX, float startY, float startZ) {
        this.world = world;
        this.spawnPosition.set(startX, startY, startZ);
        this.position.set(startX, startY, startZ);
        this.camera = new Camera(startX, startY + EYE_HEIGHT, startZ);
        ensureGroundedOnSolidBlock();
    }

    public void update(float dt, boolean forward, boolean backward, boolean left, boolean right,
                       boolean jump, boolean sneak, boolean sprint) {
        this.movingForward = forward;
        this.movingBackward = backward;
        this.movingLeft = left;
        this.movingRight = right;
        this.isSneaking = sneak && !flying;
        this.isSprinting = sprint && forward && !isSneaking && (gameMode == GameMode.CREATIVE || hunger > 6);

        if (ridingBoat != null) {
            if (ridingBoat.isDead()) {
                ridingBoat = null;
            } else {
                camera.getPosition().set(position.x, position.y + EYE_HEIGHT, position.z);
                return;
            }
        }

        float baseSpeed = isSneaking ? SNEAK_SPEED : (isSprinting ? SPRINT_SPEED : WALK_SPEED);
        if (isEating) {
            baseSpeed *= 0.30f;
        }
        // Soul Sand speed reduction
        int currX = (int) Math.floor(position.x);
        int currY = (int) Math.floor(position.y);
        int currZ = (int) Math.floor(position.z);
        BlockType bFeet = world.getBlock(currX, currY, currZ);
        BlockType bHead = world.getBlock(currX, (int) Math.floor(position.y + 0.9f), currZ);
        this.inWater = (bFeet == BlockType.WATER || bHead == BlockType.WATER);
        this.onLadder = (bFeet == BlockType.LADDER || bHead == BlockType.LADDER);

        if (world.getBlock(currX, (int) Math.floor(position.y - 0.2f), currZ) == BlockType.SOUL_SAND) {
            baseSpeed *= 0.45f;
        }
        if (inWater) {
            baseSpeed *= 0.70f;
        }
        // Sprint-jump momentum boost in air
        if (!onGround && isSprinting && !inWater) {
            baseSpeed *= 1.12f;
        }
        float speed = (flying && gameMode == GameMode.CREATIVE) ? FLY_SPEED : baseSpeed;

        // Calculate input movement direction relative to camera yaw
        float moveX = 0;
        float moveZ = 0;

        float radYaw = (float) Math.toRadians(camera.getYaw());
        float cos = (float) Math.cos(radYaw);
        float sin = (float) Math.sin(radYaw);

        if (forward) {
            moveX += cos;
            moveZ += sin;
        }
        if (backward) {
            moveX -= cos;
            moveZ -= sin;
        }
        if (left) {
            moveX += sin;
            moveZ -= cos;
        }
        if (right) {
            moveX -= sin;
            moveZ += cos;
        }

        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 0.001f) {
            moveX = (moveX / len) * speed;
            moveZ = (moveZ / len) * speed;
        } else {
            moveX = 0;
            moveZ = 0;
        }

        velocity.x = moveX;
        velocity.z = moveZ;

        // Jump buffer and coyote time timers
        if (jump) {
            jumpBufferTimer = 0.18f;
        } else if (jumpBufferTimer > 0) {
            jumpBufferTimer -= dt;
        }

        if (onGround) {
            coyoteTimer = 0.15f;
        } else if (coyoteTimer > 0) {
            coyoteTimer -= dt;
        }

        if (flying && gameMode == GameMode.CREATIVE) {
            velocity.y = 0;
            if (jump) velocity.y += FLY_SPEED;
            if (sneak) velocity.y -= FLY_SPEED;
            moveWithCollision(velocity.x * dt, velocity.y * dt, velocity.z * dt);
        } else {
            if (inWater) {
                if (jump) {
                    velocity.y = 3.6f; // Swim upward
                } else if (sneak) {
                    velocity.y = -3.6f; // Dive downward
                } else {
                    velocity.y = Math.max(-2.5f, velocity.y + (GRAVITY * 0.22f) * dt); // Buoyant sink
                }
                lastAirVerticalSpeed = 0.0f;
            } else {
                if (!onGround) {
                    lastAirVerticalSpeed = velocity.y;
                }

                // Apply gravity
                velocity.y += GRAVITY * dt;

                // Jump trigger (support sprint-jumping and jump buffer)
                if (jumpBufferTimer > 0 && coyoteTimer > 0) {
                    velocity.y = JUMP_SPEED;
                    onGround = false;
                    coyoteTimer = 0.0f;
                    jumpBufferTimer = 0.0f;
                    if (gameMode == GameMode.SURVIVAL) {
                        exhaustion += isSprinting ? 0.2f : 0.05f;
                    }
                }
            }

            boolean wasInAir = !onGround;

            // Move with AABB collision resolution
            moveWithCollision(velocity.x * dt, velocity.y * dt, velocity.z * dt);

            // Fall damage calculation on hard landing in Survival
            if (wasInAir && onGround && gameMode != GameMode.CREATIVE && !inWater) {
                if (lastAirVerticalSpeed < -16.0f) {
                    int damage = (int) ((-lastAirVerticalSpeed - 16.0f) * 1.5f);
                    no.minecraft.sound.SoundManager.getInstance().play("fall_small", 0.9f);
                    damage(Math.max(1, damage));
                }
                lastAirVerticalSpeed = 0.0f;
            }
        }

        // Environmental hazard checks (Lava burn, Cactus touch, Void check)
        int px = (int) Math.floor(position.x);
        int pyFeet = (int) Math.floor(position.y);
        int pz = (int) Math.floor(position.z);
        BlockType bBelow = world.getBlock(px, pyFeet - 1, pz);

        if (bFeet == BlockType.CACTUS || bHead == BlockType.CACTUS) {
            damage(1); // Half-heart damage from touching cactus
        }

        if (bFeet == BlockType.LAVA || bHead == BlockType.LAVA || bBelow == BlockType.LAVA) {
            lavaBurnTimer += dt;
            if (lavaBurnTimer >= 0.5f) {
                lavaBurnTimer = 0.0f;
                damage(2); // Lava burn damage (1 full heart per 0.5s)
            }
        } else {
            lavaBurnTimer = 0.0f;
        }

        if (position.y < -30.0f) {
            damage(MAX_HEALTH); // Void kill
        }

        // Damage red flash timer
        if (deathFlashTimer > 0) {
            deathFlashTimer = Math.max(0.0f, deathFlashTimer - dt);
        }

        // Walk animation timer
        float horizSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (onGround && horizSpeed > 0.1f) {
            walkAnimTime += dt * (isSprinting ? 12.0f : 7.0f);
        }

        // Swing animation timer
        if (swingProgress > 0.0f) {
            swingProgress += dt / 0.25f;
            if (swingProgress >= 1.0f) {
                swingProgress = 0.0f;
            }
        }

        // Hunger & Exhaustion processing
        if (isSprinting) {
            exhaustion += 0.10f * dt;
        }

        if (gameMode == GameMode.SURVIVAL) {
            if (health < MAX_HEALTH) {
                if (hunger == 20 && saturation > 0.0f) {
                    // Fast Saturation Regen: 1 HP every 0.5s, costs 6.0 exhaustion
                    fastRegenTimer += dt;
                    if (fastRegenTimer >= 0.5f) {
                        fastRegenTimer = 0.0f;
                        health = Math.min(MAX_HEALTH, health + 1);
                        exhaustion += 6.0f;
                    }
                    regenTimer = 0.0f;
                } else if (hunger >= 18) {
                    // Normal Hunger Regen: 1 HP every 4.0s, costs 6.0 exhaustion
                    fastRegenTimer = 0.0f;
                    regenTimer += dt;
                    if (regenTimer >= 4.0f) {
                        regenTimer = 0.0f;
                        health = Math.min(MAX_HEALTH, health + 1);
                        exhaustion += 6.0f;
                    }
                } else {
                    fastRegenTimer = 0.0f;
                    regenTimer = 0.0f;
                }
            } else {
                fastRegenTimer = 0.0f;
                regenTimer = 0.0f;
            }

            // Starvation damage when hunger is 0
            if (hunger <= 0) {
                starveTimer += dt;
                if (starveTimer >= 4.0f) {
                    starveTimer = 0.0f;
                    damage(1);
                }
            } else {
                starveTimer = 0.0f;
            }
        }

        while (exhaustion >= 4.0f) {
            exhaustion -= 4.0f;
            if (saturation > 0.0f) {
                saturation = Math.max(0.0f, saturation - 1.0f);
            } else if (gameMode == GameMode.SURVIVAL) {
                hunger = Math.max(0, hunger - 1);
            }
        }

        // Update camera position
        camera.updatePosition(world, position.x, position.y + getEyeHeight(), position.z);
    }

    public void damage(int amount) {
        if (gameMode == GameMode.CREATIVE) return;
        health = Math.max(0, health - amount);
        deathFlashTimer = 0.6f;
        exhaustion += 0.1f;
        no.minecraft.sound.SoundManager.getInstance().play("hurt", 1.0f);
        if (health <= 0) {
            die();
        }
    }

    public void die() {
        if (gameMode == GameMode.HARDCORE) {
            // Hardcore death: Drop everything and game over
            inventory.clear();
            setGameMode(GameMode.CREATIVE);
            setFlying(true);
            deathFlashTimer = 3.0f;
            world.clearHostileMobs();
            return;
        }

        world.clearHostileMobs();
        int groundY = world.getSpawnHeight((int) Math.floor(spawnPosition.x), (int) Math.floor(spawnPosition.z));
        if (groundY <= 0) {
            Vector3f safe = world.findSafeSpawnPosition((int) Math.floor(spawnPosition.x), (int) Math.floor(spawnPosition.z));
            spawnPosition.set(safe);
            groundY = (int) Math.floor(safe.y);
        }
        position.set(spawnPosition.x, groundY + 0.05f, spawnPosition.z);
        velocity.set(0, 0, 0);
        health = MAX_HEALTH;
        deathFlashTimer = 2.0f;
        if (gameMode == GameMode.SURVIVAL) {
            flying = false;
        }
        ensureGroundedOnSolidBlock();
    }

    public Vector3f getSpawnPosition() {
        return spawnPosition;
    }

    public Vector3f getVelocity() {
        return velocity;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public float getDeathFlashTimer() {
        return deathFlashTimer;
    }

    private final no.minecraft.physics.EntityCollider collider = new no.minecraft.physics.EntityCollider();
    private final no.minecraft.physics.EntityCollider.MoveResult moveResult = new no.minecraft.physics.EntityCollider.MoveResult();

    private void moveWithCollision(float dx, float dy, float dz) {
        collider.resolveMove(world, position, velocity, WIDTH / 2.0f, HEIGHT, dx, dy, dz, true, moveResult);
        onGround = moveResult.onGround;
    }

    public AABB getBoundingBox() {
        float halfW = WIDTH / 2.0f;
        return new AABB(
                position.x - halfW, position.y, position.z - halfW,
                position.x + halfW, position.y + HEIGHT, position.z + halfW
        );
    }

    public void toggleFlying() {
        if (gameMode == GameMode.CREATIVE) {
            this.flying = !this.flying;
            this.velocity.y = 0;
        }
    }

    public void setFlying(boolean flying) {
        if (gameMode == GameMode.CREATIVE) {
            this.flying = flying;
            this.velocity.y = 0;
        } else {
            this.flying = false;
        }
    }

    public boolean isFlying() {
        return flying && gameMode == GameMode.CREATIVE;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        if (mode == GameMode.SURVIVAL) {
            this.flying = false;
        }
    }

    public void toggleGameMode() {
        setGameMode(gameMode == GameMode.SURVIVAL ? GameMode.CREATIVE : GameMode.SURVIVAL);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = Math.clamp(health, 0, MAX_HEALTH);
    }

    public BlockType getSelectedBlock() {
        if (gameMode == GameMode.CREATIVE) {
            return CREATIVE_HOTBAR_BLOCKS[selectedSlot];
        } else {
            return inventory.getSlot(selectedSlot).getType();
        }
    }

    public int getSelectedBlockCount() {
        if (gameMode == GameMode.CREATIVE) {
            return -1; // Infinite
        } else {
            return inventory.getSlot(selectedSlot).getCount();
        }
    }

    public boolean canPlaceSelectedBlock() {
        BlockType type = getSelectedBlock();
        if (type == null || !type.isPlaceable()) {
            return false;
        }
        if (gameMode == GameMode.CREATIVE) {
            return true;
        } else {
            ItemStack stack = inventory.getSlot(selectedSlot);
            return !stack.isEmpty();
        }
    }

    public void useSelectedBlock() {
        if (gameMode != GameMode.CREATIVE) {
            ItemStack stack = inventory.getSlot(selectedSlot);
            if (!stack.isEmpty()) {
                stack.add(-1);
            }
        }
    }

    public void collectBlock(BlockType type) {
        if (type == BlockType.AIR || type == BlockType.BEDROCK) return;
        if (gameMode != GameMode.CREATIVE) {
            inventory.addItem(type, 1);
            no.minecraft.sound.SoundManager.getInstance().play("pop", 0.7f);
        }
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < Inventory.HOTBAR_SIZE) {
            this.selectedSlot = slot;
        }
    }

    public ItemStack getOffhandItem() {
        return offhandItem;
    }

    public void swapHands() {
        if (gameMode == GameMode.CREATIVE) {
            BlockType creativeSelected = CREATIVE_HOTBAR_BLOCKS[selectedSlot];
            if (offhandItem.isEmpty()) {
                if (creativeSelected != BlockType.AIR) {
                    offhandItem.setType(creativeSelected);
                    offhandItem.setCount(1);
                }
            } else {
                BlockType tempType = offhandItem.getType();
                int tempCount = offhandItem.getCount();
                offhandItem.setType(creativeSelected);
                offhandItem.setCount(1);
                CREATIVE_HOTBAR_BLOCKS[selectedSlot] = tempType;
            }
        } else {
            ItemStack mainHand = inventory.getSlot(selectedSlot);
            BlockType tempType = mainHand.getType();
            int tempCount = mainHand.getCount();
            int tempDamage = mainHand.getDamage();

            mainHand.setType(offhandItem.getType());
            mainHand.setCount(offhandItem.getCount());
            mainHand.setDamage(offhandItem.getDamage());

            offhandItem.setType(tempType);
            offhandItem.setCount(tempCount);
            offhandItem.setDamage(tempDamage);
        }
        no.minecraft.sound.SoundManager.getInstance().play("click", 0.8f);
    }

    public void scrollSlot(int direction) {
        selectedSlot = (selectedSlot + direction + Inventory.HOTBAR_SIZE) % Inventory.HOTBAR_SIZE;
    }

    public Camera getCamera() {
        return camera;
    }

    public Vector3f getPosition() {
        return position;
    }

    public void resetToSpawn(float x, float y, float z) {
        this.spawnPosition.set(x, y, z);
        this.position.set(x, y, z);
        this.velocity.set(0, 0, 0);
        this.health = MAX_HEALTH;
        this.camera.updatePosition(world, x, y + getEyeHeight(), z);
        this.camera.updateVectors();
        this.inventory.clear();
        this.selectedSlot = 0;
        ensureGroundedOnSolidBlock();
    }

    public void ensureGroundedOnSolidBlock() {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        int checkY = (position.y - by < 0.2f) ? by - 1 : by;
        BlockType ground = world.getBlock(bx, checkY, bz);
        if (!ground.isSolid() || ground == BlockType.WATER || ground == BlockType.LAVA || ground == BlockType.CACTUS) {
            Vector3f safe = world.findSafeSpawnPosition(bx, bz);
            this.position.set(safe);
            this.spawnPosition.set(safe);
            this.velocity.set(0, 0, 0);
            this.camera.updatePosition(world, safe.x, safe.y + getEyeHeight(), safe.z);
            this.camera.updateVectors();
        }
    }

    public void teleportTo(float x, float y, float z) {
        this.position.set(x, y, z);
        this.velocity.set(0, 0, 0);
        this.camera.updatePosition(world, x, y + getEyeHeight(), z);
        this.camera.updateVectors();
    }

    public float getEyeHeight() {
        return isSneaking ? SNEAK_EYE_HEIGHT : EYE_HEIGHT;
    }

    public Vector3f getEyePosition() {
        return camera.getEyePosition();
    }

    public boolean isSneaking() {
        return isSneaking;
    }

    public void swing() {
        if (swingProgress <= 0.0f) {
            swingProgress = 0.001f;
        }
    }

    public float getSwingProgress() {
        return swingProgress;
    }

    public float getWalkAnimTime() {
        return walkAnimTime;
    }

    public int getHunger() {
        return hunger;
    }

    public void setHunger(int h) {
        this.hunger = Math.clamp(h, 0, 20);
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float s) {
        this.saturation = Math.clamp(s, 0.0f, 20.0f);
    }

    public float getExhaustion() {
        return exhaustion;
    }

    public void setExhaustion(float e) {
        this.exhaustion = Math.max(0.0f, e);
    }

    public boolean isEating() { return isEating; }
    public float getEatProgress() { return Math.min(1.0f, eatTimer / EAT_DURATION); }

    public void startEating() {
        if (!isEating) {
            isEating = true;
            eatTimer = 0.0f;
            eatSoundTimer = 0.0f;
        }
    }

    public void stopEating() {
        isEating = false;
        eatTimer = 0.0f;
        eatSoundTimer = 0.0f;
    }

    public boolean canEat(BlockType food) {
        if (food == null || !food.isFood()) return false;
        return gameMode == GameMode.CREATIVE || hunger < 20;
    }

    public boolean updateEating(float dt, BlockType food) {
        if (!canEat(food)) {
            stopEating();
            return false;
        }

        isEating = true;
        eatTimer += dt;
        eatSoundTimer += dt;

        if (eatSoundTimer >= 0.2f) {
            eatSoundTimer = 0.0f;
            no.minecraft.sound.SoundManager.getInstance().play("eat", 0.9f);
            Vector3f eye = getEyePosition();
            Vector3f fwd = camera.getForward();
            no.minecraft.render.ParticleManager.getInstance().spawnEatingParticles(
                    eye.x + fwd.x * 0.4f,
                    eye.y + fwd.y * 0.4f - 0.15f,
                    eye.z + fwd.z * 0.4f,
                    food, 4);
        }

        if (eatTimer >= EAT_DURATION) {
            eatFood(food);
            stopEating();
            return true;
        }

        return false;
    }

    public boolean eatFood(BlockType food) {
        if (food == null || !food.isFood()) return false;
        if (gameMode == GameMode.SURVIVAL && hunger >= 20) return false;
        int fv = food.getFoodValue();
        float sv = food.getSaturationValue();
        hunger = Math.min(20, hunger + fv);
        // In Minecraft 1.16, saturation cannot exceed current hunger level
        saturation = Math.min((float) hunger, saturation + sv);
        no.minecraft.sound.SoundManager.getInstance().play("burp", 1.0f);
        Vector3f eye = getEyePosition();
        Vector3f fwd = camera.getForward();
        no.minecraft.render.ParticleManager.getInstance().spawnEatingParticles(
                eye.x + fwd.x * 0.4f,
                eye.y + fwd.y * 0.4f - 0.15f,
                eye.z + fwd.z * 0.4f,
                food, 16);

        if (food == BlockType.ROTTEN_FLESH && Math.random() < 0.80) {
            exhaustion += 12.0f;
        } else if (food == BlockType.CHICKEN_MEAT && Math.random() < 0.30) {
            exhaustion += 8.0f;
        }
        return true;
    }

    public void addExhaustion(float e) {
        if (gameMode == GameMode.SURVIVAL) {
            this.exhaustion += e;
        }
    }

    public World getWorld() {
        return world;
    }

    public boolean isMovingForward() { return movingForward; }
    public boolean isMovingBackward() { return movingBackward; }
    public boolean isMovingLeft() { return movingLeft; }
    public boolean isMovingRight() { return movingRight; }
    public boolean isSprinting() { return isSprinting; }
    public no.minecraft.entity.Boat getRidingBoat() { return ridingBoat; }
    public void setRidingBoat(no.minecraft.entity.Boat boat) { this.ridingBoat = boat; }
    public boolean isInWater() { return inWater; }
    public boolean isOnLadder() { return onLadder; }

    /**
     * In Minecraft, a critical hit occurs when the player is descending in mid-air
     * (!onGround && velocity.y < 0) and not sprinting, in water, on a ladder, flying, or riding.
     */
    public boolean canPerformCriticalHit() {
        return !onGround && velocity.y < 0.0f && !inWater && !onLadder && !flying && ridingBoat == null && !isSprinting;
    }

    /** Minecraft critical hit damage: 1.5x base (rounded up), at least +1. */
    public static int calculateAttackDamage(int baseDamage, boolean critical) {
        return critical ? Math.max(baseDamage + 1, (int) Math.ceil(baseDamage * 1.5f)) : baseDamage;
    }
}
