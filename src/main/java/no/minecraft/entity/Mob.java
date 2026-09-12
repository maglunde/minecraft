package no.minecraft.entity;

import no.minecraft.physics.AABB;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

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

    // Per-tick movement decided by the active brain
    private float moveX = 0.0f;
    private float moveZ = 0.0f;

    private final Random random = new Random();

    @FunctionalInterface
    private interface MobBrain {
        /** Decides movement for this tick; returns false to abort the rest of the update (e.g. after exploding). */
        boolean think(Mob self, float dt, World world, Player player, float distToPlayer, float dx, float dz);
    }

    private static final java.util.Map<MobType, MobBrain> BRAINS = java.util.Map.of(
            MobType.CREEPER, Mob::thinkCreeper,
            MobType.SKELETON, Mob::thinkSkeleton,
            MobType.SPIDER, Mob::thinkSpider,
            MobType.BLAZE, Mob::thinkBlaze,
            MobType.ENDERMAN, Mob::thinkEnderman,
            MobType.ZOMBIE, Mob::thinkMelee
    );

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

        // AI behaviors
        boolean canTargetPlayer = player.getGameMode() != no.minecraft.player.GameMode.CREATIVE
                && player.getHealth() > 0
                && distToPlayer < 32.0f;

        if (type == MobType.ENDER_DRAGON) {
            updateDragonAI(dt, world, player);
            return;
        } else if (type == MobType.END_CRYSTAL) {
            return;
        }

        moveX = 0.0f;
        moveZ = 0.0f;

        MobBrain brain = BRAINS.get(type);
        if (brain != null && canTargetPlayer && !type.isPassive()) {
            float dx = player.getPosition().x - position.x;
            float dz = player.getPosition().z - position.z;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001f) {
                dx /= len;
                dz /= len;
            }
            if (!brain.think(this, dt, world, player, distToPlayer, dx, dz)) {
                return;
            }
        } else {
            updateIdle(dt, world, player);
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

    private static boolean thinkCreeper(Mob m, float dt, World world, Player player, float dist, float dx, float dz) {
        m.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        if (dist < 3.2f) {
            if (!m.ignited) {
                no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.0f);
            }
            m.ignited = true;
            m.fuseTime += dt;
            if (m.fuseTime >= FUSE_MAX) {
                m.explode(world, player);
                return false;
            }
        } else {
            m.ignited = false;
            m.fuseTime = Math.max(0.0f, m.fuseTime - dt * 0.5f);
            m.moveX = dx * m.type.getMoveSpeed();
            m.moveZ = dz * m.type.getMoveSpeed();
        }
        return true;
    }

    private static boolean thinkSkeleton(Mob m, float dt, World world, Player player, float dist, float dx, float dz) {
        m.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        // Skeleton keeps distance (around 8-12 blocks) and shoots
        if (dist > 10.0f) {
            m.moveX = dx * m.type.getMoveSpeed();
            m.moveZ = dz * m.type.getMoveSpeed();
        } else if (dist < 6.0f) {
            m.moveX = -dx * m.type.getMoveSpeed();
            m.moveZ = -dz * m.type.getMoveSpeed();
        }

        m.shootCooldown -= dt;
        if (m.shootCooldown <= 0 && dist < 18.0f) {
            m.shootCooldown = 2.0f + m.random.nextFloat() * 0.5f;
            no.minecraft.sound.SoundManager.getInstance().play("bow_shoot", 0.9f);
            float arrowVx = dx * 14.0f;
            float arrowVy = (player.getPosition().y - m.position.y) * 2.0f + 2.5f;
            float arrowVz = dz * 14.0f;
            world.spawnArrow(m.position.x, m.position.y + m.type.getHeight() * 0.7f, m.position.z, arrowVx, arrowVy, arrowVz, true);
        }
        return true;
    }

    private static boolean thinkSpider(Mob m, float dt, World world, Player player, float dist, float dx, float dz) {
        m.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        m.moveX = dx * m.type.getMoveSpeed();
        m.moveZ = dz * m.type.getMoveSpeed();
        if (m.position.distance(player.getPosition()) < 1.6f && m.attackCooldown <= 0) {
            player.damage(m.type.getAttackDamage());
            no.minecraft.sound.SoundManager.getInstance().play("spider_say", 0.85f);
            m.attackCooldown = 1.0f;
        }
        return true;
    }

    private static boolean thinkBlaze(Mob m, float dt, World world, Player player, float dist, float dx, float dz) {
        m.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        if (dist > 8.0f) {
            m.moveX = dx * m.type.getMoveSpeed();
            m.moveZ = dz * m.type.getMoveSpeed();
        } else if (dist < 4.0f) {
            m.moveX = -dx * m.type.getMoveSpeed();
            m.moveZ = -dz * m.type.getMoveSpeed();
        }
        float targetY = player.getPosition().y + 1.5f;
        m.velocity.y += (targetY - m.position.y) * 2.0f * dt;
        m.velocity.y *= 0.85f;

        m.shootCooldown -= dt;
        if (m.shootCooldown <= 0 && dist < 20.0f) {
            m.shootCooldown = 2.5f + m.random.nextFloat();
            no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.2f);
            float vx = dx * 16.0f;
            float vy = (player.getPosition().y - m.position.y) * 2.0f + 1.5f;
            float vz = dz * 16.0f;
            world.spawnArrow(m.position.x, m.position.y + 0.8f, m.position.z, vx, vy, vz, true);
        }
        return true;
    }

    private static boolean thinkEnderman(Mob m, float dt, World world, Player player, float dist, float dx, float dz) {
        if (!m.aggressive && dist < 40.0f) {
            org.joml.Vector3f pEye = player.getEyePosition();
            org.joml.Vector3f toHead = new org.joml.Vector3f(
                    m.position.x - pEye.x,
                    (m.position.y + m.type.getHeight() * 0.85f) - pEye.y,
                    m.position.z - pEye.z
            );
            float dLen = toHead.length();
            if (dLen > 0.1f) {
                toHead.normalize();
                org.joml.Vector3f lookDir = player.getCamera().getForward();
                float dot = lookDir.dot(toHead);
                if (dot > 0.978f) {
                    no.minecraft.player.Raycast.HitResult los = no.minecraft.player.Raycast.raycast(world, pEye, toHead, dLen);
                    if (los == null) {
                        m.aggressive = true;
                        no.minecraft.sound.SoundManager.getInstance().play("fuse", 1.6f);
                    }
                }
            }
        }

        if (m.aggressive) {
            m.moveX = dx * (m.type.getMoveSpeed() * 1.35f);
            m.moveZ = dz * (m.type.getMoveSpeed() * 1.35f);
            m.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
            if (dist < 1.6f && m.attackCooldown <= 0) {
                player.damage(m.type.getAttackDamage());
                m.attackCooldown = 0.8f;
            }
            if (m.random.nextFloat() < 0.015f) {
                m.teleportRandom(world);
            }
        } else {
            m.updateWander(dt, 0.25f);
            if (m.random.nextFloat() < 0.001f) {
                m.teleportRandom(world);
            }
        }
        return true;
    }

    private static boolean thinkMelee(Mob m, float dt, World world, Player player, float dist, float dx, float dz) {
        // Zombie-style: chase and attack on contact
        m.yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        m.moveX = dx * m.type.getMoveSpeed();
        m.moveZ = dz * m.type.getMoveSpeed();
        if (dist < 1.4f && m.attackCooldown <= 0) {
            player.damage(m.type.getAttackDamage());
            no.minecraft.sound.SoundManager.getInstance().play("zombie_say", 0.85f);
            m.attackCooldown = 1.0f;
        }
        return true;
    }

    /** Idle wandering or panic flee when no player can be targeted. */
    private void updateIdle(float dt, World world, Player player) {
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
            updateWander(dt, 0.35f);
            if (type == MobType.ENDERMAN && random.nextFloat() < 0.001f) {
                teleportRandom(world);
            }
        }
    }

    /** Random idle wandering shared by all non-targeting mobs. */
    private void updateWander(float dt, float speedMultiplier) {
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
            moveX = (float) Math.cos(rad) * (type.getMoveSpeed() * speedMultiplier);
            moveZ = (float) Math.sin(rad) * (type.getMoveSpeed() * speedMultiplier);
            yaw = wanderYaw;
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

    public enum DragonPhase {
        CIRCLING,
        LANDING,
        PERCHED,
        TAKEOFF,
        SWOOPING
    }

    private DragonPhase dragonPhase = DragonPhase.CIRCLING;
    private float phaseTimer = 0.0f;
    private float dragonAngle = 0.0f;
    private float perchedTimer = 0.0f;
    private float perchedDamageTaken = 0.0f;
    private boolean lastPerchedWasLanding = false;

    public DragonPhase getDragonPhase() {
        return dragonPhase;
    }

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

        Vector3f ppos = player.getPosition();

        switch (dragonPhase) {
            case CIRCLING -> {
                phaseTimer += dt;
                dragonAngle += dt * 0.40f;
                float circleRadius = 26.0f;
                float targetX = (float) Math.cos(dragonAngle) * circleRadius;
                float targetZ = (float) Math.sin(dragonAngle) * circleRadius;
                float targetY = 36.0f + (float) Math.sin(dragonAngle * 2.0f) * 3.5f;

                float dx = targetX - position.x;
                float dy = targetY - position.y;
                float dz = targetZ - position.z;

                yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(dragonAngle), Math.sin(dragonAngle)));
                position.x += dx * 1.5f * dt;
                position.y += dy * 1.5f * dt;
                position.z += dz * 1.5f * dt;

                // After 12-16 seconds, choose next action (Landing on platform or Swooping)
                if (phaseTimer > 14.0f) {
                    phaseTimer = 0.0f;
                    if (!lastPerchedWasLanding || random.nextFloat() < 0.65f) {
                        dragonPhase = DragonPhase.LANDING;
                    } else {
                        dragonPhase = DragonPhase.SWOOPING;
                    }
                }
            }

            case LANDING -> {
                phaseTimer += dt;
                // Fly towards central bedrock platform pillar at (0, 33.5, 0)
                float targetX = 0.0f;
                float targetY = 33.5f;
                float targetZ = 0.0f;

                float dx = targetX - position.x;
                float dy = targetY - position.y;
                float dz = targetZ - position.z;
                float distXZ = (float) Math.sqrt(dx * dx + dz * dz);

                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));

                float speed = 9.0f;
                position.x += (dx / Math.max(1.0f, distXZ)) * speed * dt;
                position.y += Math.signum(dy) * 4.0f * dt;
                position.z += (dz / Math.max(1.0f, distXZ)) * speed * dt;

                // Landed on platform!
                if (distXZ < 2.0f && Math.abs(dy) < 1.5f || phaseTimer > 10.0f) {
                    dragonPhase = DragonPhase.PERCHED;
                    position.set(0.0f, 33.5f, 0.0f);
                    velocity.set(0, 0, 0);
                    perchedTimer = 0.0f;
                    perchedDamageTaken = 0.0f;
                    lastPerchedWasLanding = true;
                    no.minecraft.sound.SoundManager.getInstance().play("growl", 1.0f);
                }
            }

            case PERCHED -> {
                perchedTimer += dt;
                position.set(0.0f, 33.5f, 0.0f);
                velocity.set(0, 0, 0);

                // Face the player while perched
                float dx = ppos.x - position.x;
                float dz = ppos.z - position.z;
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx));

                // Melee contact damage if player touches the dragon directly
                if (position.distance(ppos) < 3.0f) {
                    player.damage(2);
                    player.getVelocity().add(dx * 1.5f, 4.0f, dz * 1.5f);
                }

                // Dragon stays perched for 14 seconds or until taking 40+ damage
                if (perchedTimer > 14.0f || perchedDamageTaken >= 40.0f) {
                    dragonPhase = DragonPhase.TAKEOFF;
                    phaseTimer = 0.0f;
                    no.minecraft.sound.SoundManager.getInstance().play("growl", 1.0f);
                }
            }

            case TAKEOFF -> {
                position.y += 6.0f * dt;
                if (position.y >= 38.0f) {
                    dragonPhase = DragonPhase.CIRCLING;
                    phaseTimer = 0.0f;
                    dragonAngle = (float) Math.atan2(position.z, position.x);
                }
            }

            case SWOOPING -> {
                phaseTimer += dt;
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
                    player.getVelocity().add(dx * 2.0f, 10.0f, dz * 2.0f);
                    dragonPhase = DragonPhase.TAKEOFF;
                    phaseTimer = 0.0f;
                    lastPerchedWasLanding = false;
                }

                if (dist < 2.0f || phaseTimer > 8.0f) {
                    dragonPhase = DragonPhase.TAKEOFF;
                    phaseTimer = 0.0f;
                    lastPerchedWasLanding = false;
                }
            }
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
        if (type == MobType.ENDER_DRAGON && dragonPhase == DragonPhase.PERCHED) {
            perchedDamageTaken += amount;
        }
        if (type != MobType.ENDER_DRAGON && type != MobType.END_CRYSTAL) {
            velocity.x += knockbackX * 6.0f;
            velocity.y += 4.5f;
            velocity.z += knockbackZ * 6.0f;
            onGround = false;
        }

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
            for (MobType.MobDrop drop : type.getDrops()) {
                int count = drop.min() + random.nextInt(drop.max() - drop.min() + 1);
                world.spawnItemDrop(position.x, position.y + 0.5f, position.z, drop.type(), count);
            }
        }
    }

    public AABB getBoundingBox() {
        float halfW = type.getWidth() / 2.0f;
        return new AABB(
                position.x - halfW, position.y, position.z - halfW,
                position.x + halfW, position.y + type.getHeight(), position.z + halfW
        );
    }

    private final no.minecraft.physics.EntityCollider collider = new no.minecraft.physics.EntityCollider();
    private final no.minecraft.physics.EntityCollider.MoveResult moveResult = new no.minecraft.physics.EntityCollider.MoveResult();

    private void moveWithCollision(World world, float dx, float dy, float dz) {
        float origDx = dx;
        float origDz = dz;

        collider.resolveMove(world, position, velocity, type.getWidth() / 2.0f, type.getHeight(),
                dx, dy, dz, false, moveResult);
        onGround = moveResult.onGround;
        dx = moveResult.dx;
        dz = moveResult.dz;

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
    public void setIgnited(boolean ignited) { this.ignited = ignited; }
    public void setFuseTime(float fuseTime) { this.fuseTime = fuseTime; }
    public float getWalkTime() { return walkTime; }
    public void setWalkTime(float walkTime) { this.walkTime = walkTime; }
}
