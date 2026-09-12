package no.minecraft.entity;

import no.minecraft.physics.AABB;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class Arrow {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f direction = new Vector3f(0, 0, 1);
    private final boolean hostileShooter;
    private final Player shooter;
    private final int damage;
    private final boolean isCrit;
    private boolean dead = false;
    private boolean inGround = false;
    private float inGroundTimer = 0.0f;
    private float lifetime = 0.0f;

    public Arrow(float x, float y, float z, float vx, float vy, float vz) {
        this(x, y, z, vx, vy, vz, null, false, 3, false);
    }

    public Arrow(float x, float y, float z, float vx, float vy, float vz, boolean hostileShooter) {
        this(x, y, z, vx, vy, vz, null, hostileShooter, 3, false);
    }

    public Arrow(float x, float y, float z, float vx, float vy, float vz, Player shooter, boolean hostileShooter) {
        this(x, y, z, vx, vy, vz, shooter, hostileShooter, 3, false);
    }

    public Arrow(float x, float y, float z, float vx, float vy, float vz, Player shooter, boolean hostileShooter, int damage, boolean isCrit) {
        this.position.set(x, y, z);
        this.velocity.set(vx, vy, vz);
        if (vx * vx + vy * vy + vz * vz > 0.0001f) {
            this.direction.set(vx, vy, vz).normalize();
        }
        this.shooter = shooter;
        this.hostileShooter = hostileShooter;
        this.damage = damage;
        this.isCrit = isCrit;
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;

        if (inGround) {
            inGroundTimer += dt;
            if (inGroundTimer > 60.0f) {
                dead = true;
                return;
            }

            // Check if supporting block was broken
            int bx = (int) Math.floor(position.x);
            int by = (int) Math.floor(position.y);
            int bz = (int) Math.floor(position.z);
            if (world.getBlock(bx, by, bz) == BlockType.AIR) {
                inGround = false;
            }

            // Player pickup (only player-fired arrows can be picked up, matching Java 1.16)
            if (!hostileShooter && inGroundTimer > 0.05f && player != null) {
                float px = player.getPosition().x;
                float py = player.getPosition().y + Player.HEIGHT * 0.5f;
                float pz = player.getPosition().z;
                float dx = px - position.x;
                float dy = py - position.y;
                float dz = pz - position.z;
                if (dx * dx + dy * dy + dz * dz <= 2.25f) { // 1.5 blocks radius
                    if (player.getInventory().addItem(BlockType.ARROW, 1)) {
                        dead = true;
                        no.minecraft.sound.SoundManager.getInstance().play("pop", 1.0f);
                        return;
                    }
                }
            }
            return;
        }

        lifetime += dt;
        if (lifetime > 15.0f) {
            dead = true;
            return;
        }

        if (velocity.lengthSquared() > 0.001f) {
            direction.set(velocity).normalize();
        }

        velocity.y -= 14.0f * dt; // Gravity
        float prevX = position.x, prevY = position.y, prevZ = position.z;
        position.add(velocity.x * dt, velocity.y * dt, velocity.z * dt);

        // Sweep the moved segment so fast arrows cannot tunnel through 1-wide walls or entities
        float dist = position.distance(prevX, prevY, prevZ);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.25f));
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float sx = prevX + (position.x - prevX) * t;
            float sy = prevY + (position.y - prevY) * t;
            float sz = prevZ + (position.z - prevZ) * t;

            if (hitsEntity(world, player, sx, sy, sz)) {
                dead = true;
                return;
            }

            int bx = (int) Math.floor(sx);
            int by = (int) Math.floor(sy);
            int bz = (int) Math.floor(sz);
            BlockType b = world.getBlock(bx, by, bz);
            if (b != BlockType.AIR && b.isSolid()) {
                inGround = true;
                position.set(sx, sy, sz);
                velocity.set(0, 0, 0);
                no.minecraft.sound.SoundManager.getInstance().play("dig_wood", 1.2f);
                return;
            }
        }
    }

    private boolean hitsEntity(World world, Player player, float x, float y, float z) {
        // Player (never hit by their own arrows; only hit by hostile arrows, or self-shot arrows after grace period)
        if (shooter != player && (hostileShooter || lifetime > 0.5f)) {
            AABB pBox = player.getBoundingBox();
            if (x >= pBox.minX && x <= pBox.maxX &&
                y >= pBox.minY && y <= pBox.maxY &&
                z >= pBox.minZ && z <= pBox.maxZ) {
                player.damage(damage);
                // Knockback
                player.getVelocity().add(velocity.x * 0.25f, 3.0f, velocity.z * 0.25f);
                return true;
            }
        }

        if (hostileShooter) return false; // Mob-fired arrows only threaten the player

        // Mobs
        for (Mob m : world.getMobs()) {
            if (m.isDead()) continue;
            AABB box = m.getBoundingBox();
            if (x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ) {
                float vlen = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
                float kb = (vlen > 0.01f) ? 1.0f / vlen : 0.0f;
                m.takeDamage(damage, velocity.x * kb, velocity.z * kb, world);

                float mobH = m.getType().getHeight();
                if (isCrit) {
                    no.minecraft.sound.SoundManager.getInstance().play("crit", 1.0f);
                    no.minecraft.render.ParticleManager.getInstance().spawnCritParticles(
                            m.getPosition().x,
                            m.getPosition().y + mobH * 0.65f,
                            m.getPosition().z,
                            16
                    );
                } else {
                    no.minecraft.sound.SoundManager.getInstance().play("hurt", 0.9f);
                }

                no.minecraft.render.CombatTextManager.getInstance().add(
                        m.getPosition().x,
                        m.getPosition().y + mobH * 0.75f,
                        m.getPosition().z,
                        damage / 2.0f,
                        isCrit
                );
                return true;
            }
        }

        // Boats (approx 1.4 wide, 0.5 tall)
        for (Boat boat : world.getBoats()) {
            if (boat.isDead()) continue;
            Vector3f bp = boat.getPosition();
            if (x >= bp.x - 0.7f && x <= bp.x + 0.7f &&
                y >= bp.y && y <= bp.y + 0.5f &&
                z >= bp.z - 0.7f && z <= bp.z + 0.7f) {
                boat.hit(world);
                return true;
            }
        }
        return false;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getVelocity() {
        return velocity;
    }

    public boolean isDead() {
        return dead;
    }

    public int getDamage() {
        return damage;
    }

    public boolean isCrit() {
        return isCrit;
    }

    public boolean isInGround() {
        return inGround;
    }

    public Vector3f getDirection() {
        return direction;
    }
}
