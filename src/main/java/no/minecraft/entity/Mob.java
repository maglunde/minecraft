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

    // Rotation
    private float yaw = 0.0f;

    private final Random random = new Random();

    public Mob(MobType type, float x, float y, float z) {
        this.type = type;
        this.position.set(x, y, z);
        this.health = type.getMaxHealth();
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;

        if (hurtTimer > 0) hurtTimer -= dt;
        if (attackCooldown > 0) attackCooldown -= dt;

        float distToPlayer = position.distance(player.getPosition());

        // AI Behaviors
        float moveX = 0;
        float moveZ = 0;

        if (distToPlayer < 24.0f) {
            float dx = player.getPosition().x - position.x;
            float dz = player.getPosition().z - position.z;
            yaw = (float) Math.toDegrees(Math.atan2(dz, dx));

            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001f) {
                dx /= len;
                dz /= len;
            }

            if (type == MobType.CREEPER) {
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
                    world.spawnArrow(position.x, position.y + type.getHeight() * 0.7f, position.z, arrowVx, arrowVy, arrowVz);
                }
            } else if (type == MobType.SPIDER) {
                moveX = dx * type.getMoveSpeed();
                moveZ = dz * type.getMoveSpeed();
                // Spider climbs walls if horizontal velocity is blocked
                if (position.distance(player.getPosition()) < 1.6f && attackCooldown <= 0) {
                    player.damage(type.getAttackDamage());
                    no.minecraft.sound.SoundManager.getInstance().play("spider_say", 0.85f);
                    attackCooldown = 1.0f;
                }
            } else { // ZOMBIE
                moveX = dx * type.getMoveSpeed();
                moveZ = dz * type.getMoveSpeed();
                if (distToPlayer < 1.4f && attackCooldown <= 0) {
                    player.damage(type.getAttackDamage());
                    no.minecraft.sound.SoundManager.getInstance().play("zombie_say", 0.85f);
                    attackCooldown = 1.0f;
                }
            }
        }

        // Apply movement and gravity
        if (!onGround) {
            velocity.y -= 26.0f * dt;
        }

        velocity.x = moveX;
        velocity.z = moveZ;

        // Auto-jump over 1-block obstacles or spider climbing
        if (onGround && (moveX != 0 || moveZ != 0)) {
            float forwardCheckX = position.x + Math.signum(moveX) * (type.getWidth() * 0.6f + 0.1f);
            float forwardCheckZ = position.z + Math.signum(moveZ) * (type.getWidth() * 0.6f + 0.1f);
            BlockType frontBlock = world.getBlock((int) Math.floor(forwardCheckX), (int) Math.floor(position.y + 0.5f), (int) Math.floor(forwardCheckZ));
            if (frontBlock != BlockType.AIR && frontBlock.isSolid()) {
                velocity.y = (type == MobType.SPIDER) ? 9.5f : 8.0f; // Jump
                onGround = false;
            }
        }

        moveWithCollision(world, velocity.x * dt, velocity.y * dt, velocity.z * dt);

        if (position.y < -10.0f) {
            dead = true;
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
        velocity.x += knockbackX * 6.0f;
        velocity.y += 4.5f;
        velocity.z += knockbackZ * 6.0f;
        onGround = false;

        if (health <= 0) {
            dead = true;
            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.MONSTER_HUNTER);
            // Drop mob loot
            int count = 1 + random.nextInt(2);
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
    public float getYaw() { return yaw; }
    public boolean isDead() { return dead; }
    public float getHurtTimer() { return hurtTimer; }
    public boolean isIgnited() { return ignited; }
    public float getFuseRatio() { return Math.min(1.0f, fuseTime / FUSE_MAX); }
}
