package no.minecraft.entity;

import no.minecraft.player.AABB;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Mob {
    private final MobType type;
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private int health;
    private boolean onGround = false;
    private boolean dead = false;

    private float hurtTimer = 0.0f; // Red flash when damaged
    private float attackCooldown = 0.0f;
    private float shootCooldown = 1.5f;

    // Creeper fuse state
    private float fuseTime = 0.0f;
    private static final float FUSE_MAX = 1.5f;
    private boolean ignited = false;

    // Fire / burning state
    private float fireTimer = 0.0f;
    private float fireDamageTimer = 0.0f;

    // Enderman aggro state
    private boolean aggressive = false;

    // Rotation
    private float yaw = 0.0f;

    // Wandering AI state
    private float wanderTimer = 0.0f;
    private float wanderYaw = 0.0f;
    private boolean isWanderingMoving = false;

    // Animation state
    private float walkTime = 0.0f;

    private final Random random = new Random();

    private Boat ridingBoat = null;

    public Mob(MobType type, float x, float y, float z) {
        this.type = type;
        this.position.set(x, y, z);
        this.health = type.getMaxHealth();
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;

        if (ridingBoat != null) {
            if (ridingBoat.isDead()) {
                ridingBoat = null;
            } else {
                return; // Trapped in boat! Position is managed by boat
            }
        }

        if (hurtTimer > 0) hurtTimer -= dt;
        if (attackCooldown > 0) attackCooldown -= dt;

        // Fire & Daylight burning processing
        if (fireTimer > 0) {
            fireTimer -= dt;
            fireDamageTimer += dt;
            if (fireDamageTimer >= 1.0f) {
                fireDamageTimer = 0.0f;
                takeDamage(1, 0, 0, world);
            }
        }

        // Extinguish in water, or ignite in sunlight for Zombie & Skeleton
        int mbx = (int) Math.floor(position.x);
        int mby = (int) Math.floor(position.y);
        int mbz = (int) Math.floor(position.z);
        if (world.getBlock(mbx, mby, mbz) == BlockType.WATER || world.getBlock(mbx, mby + 1, mbz) == BlockType.WATER) {
            fireTimer = 0.0f;
        } else if (world.getCurrentDimension() == no.minecraft.world.Dimension.OVERWORLD && !world.isNight()) {
            if ((type == MobType.ZOMBIE || type == MobType.SKELETON) && world.isOpenToSky(mbx, mby, mbz)) {
                fireTimer = Math.max(fireTimer, 4.0f);
            }
        }

        float distToPlayer = position.distance(player.getPosition());

        // AI Behaviors
        float moveX = 0;
        float moveZ = 0;

        boolean canTargetPlayer = player.getGameMode() != no.minecraft.player.GameMode.CREATIVE
                && player.getHealth() > 0
                && distToPlayer < 32.0f;

        if (type == MobType.ENDER_DRAGON) {
            updateDragonAI(dt, world, player);
            return;
        } else if (type == MobType.END_CRYSTAL) {
            return;
        }

        if (canTargetPlayer && !type.isPassive()) {
            float dx = player.getPosition().x - position.x;
            float dz = player.getPosition().z - position.z;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001f) {
                dx /= len;
                dz /= len;
            }

            if (type == MobType.CREEPER) {
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                if (distToPlayer < 3.2f) {
                    if (!ignited) {
                        no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.0f);
                    }
                    ignited = true;
                    fuseTime += dt;
                    if (fuseTime >= FUSE_MAX) {
                        explode(world, player);
                        return;
                    }
                } else {
                    ignited = false;
                    fuseTime = Math.max(0.0f, fuseTime - dt * 0.5f);
                    moveX = dx * type.getMoveSpeed();
                    moveZ = dz * type.getMoveSpeed();
                }
            } else if (type == MobType.SKELETON) {
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                // Skeleton keeps distance (around 8-12 blocks) and shoots
                if (distToPlayer > 10.0f) {
                    moveX = dx * type.getMoveSpeed();
                    moveZ = dz * type.getMoveSpeed();
                } else if (distToPlayer < 6.0f) {
                    moveX = -dx * type.getMoveSpeed();
                    moveZ = -dz * type.getMoveSpeed();
                }

                shootCooldown -= dt;
                if (shootCooldown <= 0 && distToPlayer < 18.0f) {
                    shootCooldown = 2.0f + random.nextFloat() * 0.5f;
                    no.minecraft.sound.SoundManager.getInstance().play("bow_shoot", 0.9f);
                    float arrowVx = dx * 14.0f;
                    float arrowVy = (player.getPosition().y - position.y) * 2.0f + 2.5f;
                    float arrowVz = dz * 14.0f;
                    world.spawnArrow(position.x, position.y + type.getHeight() * 0.7f, position.z, arrowVx, arrowVy, arrowVz, true);
                }
            } else if (type == MobType.SPIDER) {
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                moveX = dx * type.getMoveSpeed();
                moveZ = dz * type.getMoveSpeed();
                if (position.distance(player.getPosition()) < 1.6f && attackCooldown <= 0) {
                    player.damage(type.getAttackDamage());
                    no.minecraft.sound.SoundManager.getInstance().play("spider_say", 0.85f);
                    attackCooldown = 1.0f;
                }
            } else if (type == MobType.BLAZE) {
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                if (distToPlayer > 8.0f) {
                    moveX = dx * type.getMoveSpeed();
                    moveZ = dz * type.getMoveSpeed();
                } else if (distToPlayer < 4.0f) {
                    moveX = -dx * type.getMoveSpeed();
                    moveZ = -dz * type.getMoveSpeed();
                }
                float targetY = player.getPosition().y + 1.5f;
                velocity.y += (targetY - position.y) * 2.0f * dt;
                velocity.y *= 0.85f;

                shootCooldown -= dt;
                if (shootCooldown <= 0 && distToPlayer < 20.0f) {
                    shootCooldown = 2.5f + random.nextFloat();
                    no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.2f);
                    float vx = dx * 16.0f;
                    float vy = (player.getPosition().y - position.y) * 2.0f + 1.5f;
                    float vz = dz * 16.0f;
                    world.spawnArrow(position.x, position.y + 0.8f, position.z, vx, vy, vz, true);
                }
            } else if (type == MobType.ENDERMAN) {
                if (!aggressive && distToPlayer < 40.0f) {
                    org.joml.Vector3f pEye = player.getEyePosition();
                    org.joml.Vector3f toHead = new org.joml.Vector3f(
                            position.x - pEye.x,
                            (position.y + type.getHeight() * 0.85f) - pEye.y,
                            position.z - pEye.z
                    );
                    float dLen = toHead.length();
                    if (dLen > 0.1f) {
                        toHead.normalize();
                        org.joml.Vector3f lookDir = player.getCamera().getForward();
                        float dot = lookDir.dot(toHead);
                        if (dot > 0.978f) {
                            no.minecraft.player.Raycast.HitResult los = no.minecraft.player.Raycast.raycast(world, pEye, toHead, dLen);
                            if (los == null) {
                                aggressive = true;
                                no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.6f);
                            }
                        }
                    }
                }

                if (aggressive) {
                    moveX = dx * (type.getMoveSpeed() * 1.35f);
                    moveZ = dz * (type.getMoveSpeed() * 1.35f);
                    yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                    if (distToPlayer < 1.6f && attackCooldown <= 0) {
                        player.damage(type.getAttackDamage());
                        attackCooldown = 0.8f;
                    }
                    if (random.nextFloat() < 0.015f) {
                        teleportRandom(world);
                    }
                } else {
                    wanderTimer -= dt;
                    if (wanderTimer <= 0) {
                        wanderTimer = 2.0f + random.nextFloat() * 4.0f;
                        isWanderingMoving = random.nextFloat() < 0.6f;
                        if (isWanderingMoving) {
                            wanderYaw = random.nextFloat() * 360.0f;
                        }
                    }
                    if (isWanderingMoving) {
                        float rad = (float) Math.toRadians(wanderYaw);
                        moveX = (float) Math.cos(rad) * (type.getMoveSpeed() * 0.25f);
                        moveZ = (float) Math.sin(rad) * (type.getMoveSpeed() * 0.25f);
                        yaw = wanderYaw;
                    }
                    if (random.nextFloat() < 0.001f) {
                        teleportRandom(world);
                    }
                }
            } else { // ZOMBIE
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                moveX = dx * type.getMoveSpeed();
                moveZ = dz * type.getMoveSpeed();
                if (distToPlayer < 1.4f && attackCooldown <= 0) {
                    player.damage(type.getAttackDamage());
                    no.minecraft.sound.SoundManager.getInstance().play("zombie_say", 0.85f);
                    attackCooldown = 1.0f;
                }
            }
        } else {
            // Idle wandering or panic flee
            if (type == MobType.CREEPER) {
                ignited = false;
                fuseTime = Math.max(0.0f, fuseTime - dt * 0.5f);
            }

            if (type.isPassive() && hurtTimer > 0) {
                // Panic and sprint away from player when attacked!
                float dx = player.getPosition().x - position.x;
                float dz = player.getPosition().z - position.z;
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                if (len > 0.001f) {
                    dx /= len;
                    dz /= len;
                }
                moveX = -dx * type.getMoveSpeed() * 1.8f;
                moveZ = -dz * type.getMoveSpeed() * 1.8f;
                yaw = (float) Math.toDegrees(Math.atan2(-dz, -dx));
            } else {
                wanderTimer -= dt;
                if (wanderTimer <= 0) {
                    wanderTimer = 2.0f + random.nextFloat() * 4.0f;
                    isWanderingMoving = random.nextFloat() < 0.6f;
                    if (isWanderingMoving) {
                        wanderYaw = random.nextFloat() * 360.0f;
                    }
                }
                if (isWanderingMoving) {
                    float rad = (float) Math.toRadians(wanderYaw);
                    moveX = (float) Math.cos(rad) * (type.getMoveSpeed() * 0.35f);
                    moveZ = (float) Math.sin(rad) * (type.getMoveSpeed() * 0.35f);
                    yaw = wanderYaw;
                }
                if (type == MobType.ENDERMAN && random.nextFloat() < 0.001f) {
                    teleportRandom(world);
                }
            }
        }

        // Apply movement and gravity
        if (type != MobType.BLAZE && type != MobType.ENDER_DRAGON && type != MobType.END_CRYSTAL) {
            if (!onGround) {
                velocity.y -= 26.0f * dt;
            }
        }

        velocity.x = moveX;
        velocity.z = moveZ;

        if (Math.abs(moveX) > 0.01f || Math.abs(moveZ) > 0.01f) {
            walkTime += dt;
        }

        // Auto-jump over 1-block obstacles or spider climbing
        if (onGround && (moveX != 0 || moveZ != 0)) {
            boolean obstacleAhead = false;
            float checkDist = type.getWidth() * 0.5f + 0.35f;

            // Check X obstacle
            if (Math.abs(moveX) > 0.01f) {
                float targetX = position.x + Math.signum(moveX) * checkDist;
                int bx = (int) Math.floor(targetX);
                int by = (int) Math.floor(position.y + 0.5f);
                int bz = (int) Math.floor(position.z);
                BlockType lower = world.getBlock(bx, by, bz);
                BlockType upper = world.getBlock(bx, by + 1, bz);
                if (lower != BlockType.AIR && lower.isSolid() && (upper == BlockType.AIR || !upper.isSolid())) {
                    obstacleAhead = true;
                }
            }

            // Check Z obstacle
            if (!obstacleAhead && Math.abs(moveZ) > 0.01f) {
                float targetZ = position.z + Math.signum(moveZ) * checkDist;
                int bx = (int) Math.floor(position.x);
                int by = (int) Math.floor(position.y + 0.5f);
                int bz = (int) Math.floor(targetZ);
                BlockType lower = world.getBlock(bx, by, bz);
                BlockType upper = world.getBlock(bx, by + 1, bz);
                if (lower != BlockType.AIR && lower.isSolid() && (upper == BlockType.AIR || !upper.isSolid())) {
                    obstacleAhead = true;
                }
            }

            // Check diagonal obstacle
            if (!obstacleAhead && Math.abs(moveX) > 0.01f && Math.abs(moveZ) > 0.01f) {
                float targetX = position.x + Math.signum(moveX) * checkDist;
                float targetZ = position.z + Math.signum(moveZ) * checkDist;
                int bx = (int) Math.floor(targetX);
                int by = (int) Math.floor(position.y + 0.5f);
                int bz = (int) Math.floor(targetZ);
                BlockType lower = world.getBlock(bx, by, bz);
                BlockType upper = world.getBlock(bx, by + 1, bz);
                if (lower != BlockType.AIR && lower.isSolid() && (upper == BlockType.AIR || !upper.isSolid())) {
                    obstacleAhead = true;
                }
            }

            if (obstacleAhead) {
                velocity.y = (type == MobType.SPIDER) ? 9.5f : 8.0f; // Jump
                onGround = false;
            }
        }

        moveWithCollision(world, velocity.x * dt, velocity.y * dt, velocity.z * dt);

        if (position.y < -10.0f) {
            dead = true;
        }
    }

    private void teleportRandom(World world) {
        float tx = position.x + (random.nextFloat() - 0.5f) * 16.0f;
        float tz = position.z + (random.nextFloat() - 0.5f) * 16.0f;
        int bx = (int) Math.floor(tx);
        int bz = (int) Math.floor(tz);
        int by = world.getSpawnHeight(bx, bz);
        if (by > 0 && by < 60) {
            position.set(tx, by, tz);
            no.minecraft.sound.SoundManager.getInstance().play("pop", 0.7f);
        }
    }

    private float dragonAngle = 0.0f;
    private boolean dragonSwooping = false;
    private float swoopTimer = 0.0f;

    private void updateDragonAI(float dt, World world, Player player) {
        // Find nearest active End Crystal to heal from
        Mob nearestCrystal = null;
        float minCDist = 40.0f;
        for (Mob m : world.getMobs()) {
            if (m.getType() == MobType.END_CRYSTAL && !m.isDead()) {
                float d = m.getPosition().distance(position);
                if (d < minCDist) {
                    minCDist = d;
                    nearestCrystal = m;
                }
            }
        }

        if (nearestCrystal != null && health < type.getMaxHealth()) {
            health = Math.min(type.getMaxHealth(), health + (int)(6 * dt) + 1);
        }

        // Dragon flight: circles island or swoops down at player
        swoopTimer += dt;
        if (swoopTimer > 15.0f && !dragonSwooping) {
            dragonSwooping = true;
            swoopTimer = 0.0f;
        }

        if (dragonSwooping) {
            // Swoop towards player
            Vector3f ppos = player.getPosition();
            float dx = ppos.x - position.x;
            float dy = (ppos.y + 1.0f) - position.y;
            float dz = ppos.z - position.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            yaw = (float) Math.toDegrees(Math.atan2(dz, dx));

            float speed = 12.0f;
            position.x += (dx / Math.max(1.0f, dist)) * speed * dt;
            position.y += Math.signum(dy) * 4.0f * dt;
            position.z += (dz / Math.max(1.0f, dist)) * speed * dt;

            // Attack player on contact
            if (position.distance(ppos) < 3.8f) {
                player.damage(type.getAttackDamage());
                // Launch player up & back
                player.getVelocity().add(dx * 2.0f, 10.0f, dz * 2.0f);
                dragonSwooping = false;
                swoopTimer = 0.0f;
            }

            if (dist < 2.0f || swoopTimer > 8.0f) {
                dragonSwooping = false;
                swoopTimer = 0.0f;
            }
        } else {
            // Circle center of End island (0, 35, 0)
            dragonAngle += dt * 0.45f;
            float circleRadius = 26.0f;
            float targetX = (float) Math.cos(dragonAngle) * circleRadius;
            float targetZ = (float) Math.sin(dragonAngle) * circleRadius;
            float targetY = 36.0f + (float) Math.sin(dragonAngle * 2.0f) * 4.0f;

            float dx = targetX - position.x;
            float dy = targetY - position.y;
            float dz = targetZ - position.z;

            yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(dragonAngle), Math.sin(dragonAngle)));
            position.x += dx * 1.5f * dt;
            position.y += dy * 1.5f * dt;
            position.z += dz * 1.5f * dt;
        }
    }

    private void explode(World world, Player player) {
        dead = true;
        no.minecraft.sound.SoundManager.getInstance().play("explode", 1.0f);
        int radius = 3;
        int cx = (int) Math.floor(position.x);
        int cy = (int) Math.floor(position.y);
        int cz = (int) Math.floor(position.z);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        int tx = cx + x;
                        int ty = cy + y;
                        int tz = cz + z;
                        BlockType b = world.getBlock(tx, ty, tz);
                        if (b != BlockType.AIR && b != BlockType.BEDROCK) {
                            world.setBlock(tx, ty, tz, BlockType.AIR);
                            if (random.nextFloat() < 0.35f) {
                                world.spawnItemDrop(tx + 0.5f, ty + 0.5f, tz + 0.5f, b.getDrop(), 1);
                            }
                        }
                    }
                }
            }
        }

        // Damage player based on distance
        float pDist = position.distance(player.getPosition());
        if (pDist < 6.0f) {
            int dmg = (int) ((6.0f - pDist) * 3.5f);
            player.damage(Math.max(2, dmg));
            // Explosive knockback
            Vector3f push = new Vector3f(player.getPosition()).sub(position).normalize().mul(12.0f);
            player.getVelocity().add(push.x, 8.0f, push.z);
        }
    }

    public void takeDamage(int amount, float knockbackX, float knockbackZ, World world) {
        if (dead) return;
        health -= amount;
        hurtTimer = 0.4f;
        if (type == MobType.ENDERMAN) {
            aggressive = true;
        }
        velocity.x += knockbackX * 6.0f;
        velocity.y += 4.5f;
        velocity.z += knockbackZ * 6.0f;
        onGround = false;

        if (health <= 0) {
            dead = true;
            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.MONSTER_HUNTER);
            if (type == MobType.BLAZE) {
                no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.INTO_FIRE);
            } else if (type == MobType.ENDER_DRAGON) {
                no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.FREE_THE_END);
                no.minecraft.sound.SoundManager.getInstance().play("explode", 1.0f);
            } else if (type == MobType.END_CRYSTAL) {
                no.minecraft.sound.SoundManager.getInstance().play("explode", 0.9f);
                // Explode End Crystal on hit
                world.spawnItemDrop(position.x, position.y + 0.5f, position.z, BlockType.AIR, 0);
                return;
            }

            // Drop mob loot
            int count = (type == MobType.ENDER_DRAGON) ? 1 : (1 + random.nextInt(2));
            world.spawnItemDrop(position.x, position.y + 0.5f, position.z, type.getDropItem(), count);
        }
    }

    public AABB getBoundingBox() {
        float halfW = type.getWidth() / 2.0f;
        return new AABB(
                position.x - halfW, position.y, position.z - halfW,
                position.x + halfW, position.y + type.getHeight(), position.z + halfW
        );
    }

    private void moveWithCollision(World world, float dx, float dy, float dz) {
        float origDx = dx;
        float origDz = dz;
        AABB box = getBoundingBox();

        // Y Collision
        List<AABB> blocks = getSurroundingBoxes(world, box.offset(0, dy, 0));
        for (AABB b : blocks) {
            if (dy > 0 && box.offset(0, dy, 0).intersects(b)) {
                dy = b.minY - box.maxY - 0.001f;
                velocity.y = 0;
            } else if (dy < 0 && box.offset(0, dy, 0).intersects(b)) {
                dy = b.maxY - box.minY + 0.001f;
                velocity.y = 0;
                onGround = true;
            }
        }
        position.y += dy;
        box = getBoundingBox();

        if (dy <= 0.0001f && dy >= -0.0001f && velocity.y <= 0) {
            List<AABB> ground = getSurroundingBoxes(world, box.offset(0, -0.05f, 0));
            onGround = !ground.isEmpty();
        } else if (dy > 0) {
            onGround = false;
        }

        // X Collision
        blocks = getSurroundingBoxes(world, box.offset(dx, 0, 0));
        for (AABB b : blocks) {
            if (dx > 0 && box.offset(dx, 0, 0).intersects(b)) {
                dx = b.minX - box.maxX - 0.001f;
            } else if (dx < 0 && box.offset(dx, 0, 0).intersects(b)) {
                dx = b.maxX - box.minX + 0.001f;
            }
        }
        position.x += dx;
        box = getBoundingBox();

        // Z Collision
        blocks = getSurroundingBoxes(world, box.offset(0, 0, dz));
        for (AABB b : blocks) {
            if (dz > 0 && box.offset(0, 0, dz).intersects(b)) {
                dz = b.minZ - box.maxZ - 0.001f;
            } else if (dz < 0 && box.offset(0, 0, dz).intersects(b)) {
                dz = b.maxZ - box.minZ + 0.001f;
            }
        }
        position.z += dz;

        // If mob was on ground and horizontally blocked by an obstacle, jump if headroom exists
        if (onGround && ((Math.abs(origDx) > 0.001f && Math.abs(dx) < Math.abs(origDx) * 0.5f) ||
                         (Math.abs(origDz) > 0.001f && Math.abs(dz) < Math.abs(origDz) * 0.5f))) {
            int mx = (int) Math.floor(position.x);
            int my = (int) Math.floor(position.y + type.getHeight() + 0.5f);
            int mz = (int) Math.floor(position.z);
            BlockType headBlock = world.getBlock(mx, my, mz);
            if (headBlock == BlockType.AIR || !headBlock.isSolid()) {
                velocity.y = (type == MobType.SPIDER) ? 9.5f : 8.0f;
                onGround = false;
            }
            if (isWanderingMoving) {
                wanderTimer = 0.0f;
            }
        }
    }

    private List<AABB> getSurroundingBoxes(World world, AABB q) {
        List<AABB> list = new ArrayList<>();
        int minX = (int) Math.floor(q.minX);
        int maxX = (int) Math.floor(q.maxX);
        int minY = (int) Math.floor(q.minY);
        int maxY = (int) Math.floor(q.maxY);
        int minZ = (int) Math.floor(q.minZ);
        int maxZ = (int) Math.floor(q.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType bt = world.getBlock(x, y, z);
                    if (bt != BlockType.AIR && bt.isSolid()) {
                        list.add(new AABB(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f));
                    }
                }
            }
        }
        return list;
    }

    public MobType getType() { return type; }
    public Vector3f getPosition() { return position; }
    public Vector3f getVelocity() { return velocity; }
    public float getYaw() { return yaw; }
    public boolean isDead() { return dead; }
    public float getHurtTimer() { return hurtTimer; }
    public boolean isIgnited() { return ignited; }
    public float getFuseRatio() { return Math.min(1.0f, fuseTime / FUSE_MAX); }
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    public boolean isOnFire() { return fireTimer > 0; }
    public boolean isAggressive() { return aggressive; }
    public Boat getRidingBoat() { return ridingBoat; }
    public void setRidingBoat(Boat boat) { this.ridingBoat = boat; }
    public float getWalkTime() { return walkTime; }
}
