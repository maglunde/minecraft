package no.minecraft.world;

import no.minecraft.player.Player;
import org.joml.Vector3f;

import java.util.Random;

public class DroppedItem {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final BlockType type;
    private final int count;

    private float age = 0.0f;
    private float pickupDelay = 0.4f;
    private boolean dead = false;
    private boolean onGround = false;

    private static final Random rand = new Random();

    public DroppedItem(float x, float y, float z, BlockType type, int count) {
        this.position.set(x, y, z);
        this.type = type;
        this.count = count;

        // Small initial pop velocity
        this.velocity.x = (rand.nextFloat() - 0.5f) * 1.6f;
        this.velocity.y = 2.2f + rand.nextFloat() * 1.2f;
        this.velocity.z = (rand.nextFloat() - 0.5f) * 1.6f;
        this.age = rand.nextFloat() * 10.0f;
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        age += dt;
        if (pickupDelay > 0) {
            pickupDelay -= dt;
        }

        // Apply physics
        if (!onGround) {
            velocity.y -= 16.0f * dt;
        }

        // Move horizontally with friction
        position.x += velocity.x * dt;
        position.z += velocity.z * dt;
        if (onGround) {
            velocity.x *= Math.max(0, 1.0f - 8.0f * dt);
            velocity.z *= Math.max(0, 1.0f - 8.0f * dt);
        }

        // Move vertically with collision against blocks below
        float newY = position.y + velocity.y * dt;
        int blockX = (int) Math.floor(position.x);
        int blockY = (int) Math.floor(newY);
        int blockZ = (int) Math.floor(position.z);

        BlockType blockBelow = world.getBlock(blockX, blockY, blockZ);
        if (blockBelow != BlockType.AIR && blockBelow.isSolid()) {
            position.y = (float) Math.floor(newY) + 1.02f;
            velocity.y = 0;
            onGround = true;
        } else {
            position.y = newY;
            onGround = false;
        }

        // Pickup check by player
        if (pickupDelay <= 0) {
            float playerCenterX = player.getPosition().x;
            float playerCenterY = player.getPosition().y + Player.HEIGHT * 0.5f;
            float playerCenterZ = player.getPosition().z;

            float dx = playerCenterX - position.x;
            float dy = playerCenterY - position.y;
            float dz = playerCenterZ - position.z;
            float distSq = dx * dx + dy * dy + dz * dz;

            // Magnet pull towards player
            if (distSq < 4.0f) { // distance < 2.0 blocks
                float dist = (float) Math.sqrt(distSq);
                if (dist > 0.01f) {
                    float pullSpeed = 5.0f * dt;
                    position.x += (dx / dist) * pullSpeed;
                    position.y += (dy / dist) * pullSpeed;
                    position.z += (dz / dist) * pullSpeed;
                }
            }

            // Collect into inventory
            if (distSq < 1.44f) { // distance < 1.2 blocks
                boolean added = player.getInventory().addItem(type, count);
                if (added) {
                    dead = true;
                }
            }
        }
    }

    public Vector3f getPosition() {
        return position;
    }

    public BlockType getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public float getAge() {
        return age;
    }

    public boolean isDead() {
        return dead;
    }
}
