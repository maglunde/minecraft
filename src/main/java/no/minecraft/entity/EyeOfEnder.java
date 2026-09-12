package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Thrown eye of ender: flies toward the stronghold like in vanilla, passes
 * through blocks, and drops as an item when it arrives. Never damages anyone.
 */
public class EyeOfEnder {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private boolean dead = false;
    private float lifetime = 0.0f;

    public EyeOfEnder(float x, float y, float z, float vx, float vy, float vz) {
        this.position.set(x, y, z);
        this.velocity.set(vx, vy, vz);
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        lifetime += dt;
        if (lifetime > 20.0f) {
            dead = true;
            return;
        }

        // Home in on the stronghold portal room, slowing down as it approaches
        float tx = World.STRONGHOLD_X + 0.5f;
        float ty = World.STRONGHOLD_Y + 1.5f;
        float tz = World.STRONGHOLD_Z + 0.5f;
        float dx = tx - position.x;
        float dy = ty - position.y;
        float dz = tz - position.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001f) {
            arrive(world);
            return;
        }
        float speed = Math.max(8.0f, velocity.length() - 8.0f * dt);
        velocity.set(dx / dist * speed, dy / dist * speed, dz / dist * speed);
        position.add(velocity.x * dt, velocity.y * dt, velocity.z * dt);

        if (position.distance(tx, ty, tz) < 0.75f) {
            arrive(world);
        }
    }

    private void arrive(World world) {
        world.spawnItemDrop(position.x, position.y + 0.5f, position.z, BlockType.EYE_OF_ENDER, 1);
        dead = true;
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
