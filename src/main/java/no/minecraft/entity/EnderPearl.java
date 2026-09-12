package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.Chunk;
import no.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Thrown ender pearl: follows a gravity arc and teleports its owner to the
 * impact point with 2.5 hearts of fall damage, like in vanilla. Passes
 * through entities and never damages them.
 */
public class EnderPearl {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Player owner;
    private boolean dead = false;
    private float lifetime = 0.0f;

    public EnderPearl(float x, float y, float z, float vx, float vy, float vz, Player owner) {
        this.position.set(x, y, z);
        this.velocity.set(vx, vy, vz);
        this.owner = owner;
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        lifetime += dt;
        if (lifetime > 20.0f) {
            dead = true;
            return;
        }

        velocity.y -= 14.0f * dt; // Gravity
        float prevX = position.x, prevY = position.y, prevZ = position.z;
        position.add(velocity.x * dt, velocity.y * dt, velocity.z * dt);

        // Sweep the moved segment so fast pearls cannot tunnel through walls
        float dist = position.distance(prevX, prevY, prevZ);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.25f));
        float lastFreeX = prevX, lastFreeY = prevY, lastFreeZ = prevZ;
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
                onImpact(world, lastFreeX, lastFreeY, lastFreeZ);
                dead = true;
                return;
            }
            lastFreeX = sx;
            lastFreeY = sy;
            lastFreeZ = sz;
        }
    }

    private void onImpact(World world, float x, float y, float z) {
        if (owner == null || owner.getHealth() <= 0) return;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int feetY = (int) Math.floor(y);

        // Fall through open air to the first solid ground below the impact.
        // Never scans through solid rock, so the player can neither end up
        // inside a mountain nor be teleported through it; if the impact cell
        // itself is inside a wall (point-blank throw), cancel instead.
        for (int cy = Math.min(feetY, Chunk.SIZE_Y - 3); cy >= 0; cy--) {
            BlockType ground = world.getBlock(bx, cy, bz);
            if (ground == BlockType.AIR || !ground.isSolid()) continue;
            if (!isLandingSpot(world, bx, cy, bz)) return; // Solid but blocked: inside a wall
            owner.teleportTo(bx + 0.5f, cy + 1.05f, bz + 0.5f);
            owner.damage(5); // 2.5 hearts, like vanilla fall damage
            return;
        }
        // Only air all the way down: no safe landing, cancel the teleport
    }

    /** True when a player can stand here: solid ground with free feet and head cells. */
    private static boolean isLandingSpot(World world, int x, int y, int z) {
        BlockType ground = world.getBlock(x, y, z);
        if (!ground.isSolid() || ground == BlockType.CACTUS || ground == BlockType.LAVA || ground == BlockType.WATER) {
            return false;
        }
        BlockType feet = world.getBlock(x, y + 1, z);
        if (feet.isSolid() || feet == BlockType.CACTUS || feet == BlockType.LAVA) return false;
        BlockType head = world.getBlock(x, y + 2, z);
        return !(head.isSolid() || head == BlockType.CACTUS || head == BlockType.LAVA);
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
