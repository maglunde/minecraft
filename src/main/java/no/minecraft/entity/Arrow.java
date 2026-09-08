package no.minecraft.entity;

import no.minecraft.player.AABB;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class Arrow {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private boolean dead = false;
    private float lifetime = 0.0f;

    public Arrow(float x, float y, float z, float vx, float vy, float vz) {
        this.position.set(x, y, z);
        this.velocity.set(vx, vy, vz);
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        lifetime += dt;
        if (lifetime > 8.0f) {
            dead = true;
            return;
        }

        velocity.y -= 14.0f * dt; // Gravity
        position.add(velocity.x * dt, velocity.y * dt, velocity.z * dt);

        // Check block collision
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        BlockType b = world.getBlock(bx, by, bz);
        if (b != BlockType.AIR && b.isSolid()) {
            dead = true;
            return;
        }

        // Check collision with player
        AABB pBox = player.getBoundingBox();
        if (position.x >= pBox.minX && position.x <= pBox.maxX &&
            position.y >= pBox.minY && position.y <= pBox.maxY &&
            position.z >= pBox.minZ && position.z <= pBox.maxZ) {
            player.damage(3);
            // Knockback
            player.getVelocity().add(velocity.x * 0.25f, 3.0f, velocity.z * 0.25f);
            dead = true;
        }
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
}
