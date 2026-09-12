package no.minecraft.entity;

import no.minecraft.physics.AABB;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class Arrow {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final boolean hostileShooter;
    private final Player shooter;
    private final int damage;
    private final boolean isCrit;
    private boolean dead = false;
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
        this.shooter = shooter;
        this.hostileShooter = hostileShooter;
        this.damage = damage;
        this.isCrit = isCrit;
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        lifetime += dt;
        if (lifetime > 8.0f) {
            dead = true;
            return;
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

            int bx = (int) Math.floor(sx);
            int by = (int) Math.floor(sy);
            int bz = (int) Math.floor(sz);
            BlockType b = world.getBlock(bx, by, bz);
            if (b != BlockType.AIR && b.isSolid()) {
                dead = true;
                return;
            }

            if (hitsEntity(world, player, sx, sy, sz)) {
                dead = true;
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
}
