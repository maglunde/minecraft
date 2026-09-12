package no.minecraft.physics;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared axis-swept collision against solid world blocks (Y first, then X and Z).
 * Scratch state is reused across calls to avoid per-frame allocations in hot paths.
 */
public class EntityCollider {
    public static final float EPSILON = 0.001f;

    private final List<AABB> scratchBlocks = new ArrayList<>();
    private final AABB scratchBox = new AABB(0, 0, 0, 0, 0, 0);

    public static class MoveResult {
        public float dx, dy, dz;
        public boolean onGround;
    }

    /**
     * Resolves a movement through solid blocks, mutating {@code position} and zeroing
     * {@code velocity.y} on vertical hits. Grounded state is recomputed from the blocks below.
     *
     * @param finalGroundCheck extra ground verification after all axes (player uses it, mobs do not)
     */
    public MoveResult resolveMove(World world, Vector3f position, Vector3f velocity,
                                  float halfWidth, float height,
                                  float dx, float dy, float dz,
                                  boolean finalGroundCheck, MoveResult out) {
        if (out == null) out = new MoveResult();
        out.onGround = false;

        // Y axis
        fillBlockBoxes(world, offsetBox(position, halfWidth, height, 0, dy, 0));
        for (AABB b : scratchBlocks) {
            if (dy > 0 && offsetBox(position, halfWidth, height, 0, dy, 0).intersects(b)) {
                dy = b.minY - (position.y + height) - EPSILON;
                velocity.y = 0;
            } else if (dy < 0 && offsetBox(position, halfWidth, height, 0, dy, 0).intersects(b)) {
                dy = b.maxY - position.y + EPSILON;
                velocity.y = 0;
                out.onGround = true;
            }
        }
        position.y += dy;

        if (dy <= 0.0001f && dy >= -0.0001f && velocity.y <= 0) {
            fillBlockBoxes(world, offsetBox(position, halfWidth, height, 0, -0.05f, 0));
            out.onGround = !scratchBlocks.isEmpty();
        } else if (dy > 0) {
            out.onGround = false;
        }

        // X axis
        fillBlockBoxes(world, offsetBox(position, halfWidth, height, dx, 0, 0));
        for (AABB b : scratchBlocks) {
            if (dx > 0 && offsetBox(position, halfWidth, height, dx, 0, 0).intersects(b)) {
                dx = b.minX - (position.x + halfWidth) - EPSILON;
            } else if (dx < 0 && offsetBox(position, halfWidth, height, dx, 0, 0).intersects(b)) {
                dx = b.maxX - (position.x - halfWidth) + EPSILON;
            }
        }
        position.x += dx;

        // Z axis
        fillBlockBoxes(world, offsetBox(position, halfWidth, height, 0, 0, dz));
        for (AABB b : scratchBlocks) {
            if (dz > 0 && offsetBox(position, halfWidth, height, 0, 0, dz).intersects(b)) {
                dz = b.minZ - (position.z + halfWidth) - EPSILON;
            } else if (dz < 0 && offsetBox(position, halfWidth, height, 0, 0, dz).intersects(b)) {
                dz = b.maxZ - (position.z - halfWidth) + EPSILON;
            }
        }
        position.z += dz;

        if (finalGroundCheck) {
            fillBlockBoxes(world, offsetBox(position, halfWidth, height, 0, -0.08f, 0));
            out.onGround = !scratchBlocks.isEmpty() && velocity.y <= 0.1f;
        }

        out.dx = dx;
        out.dy = dy;
        out.dz = dz;
        return out;
    }

    private void fillBlockBoxes(World world, AABB query) {
        scratchBlocks.clear();
        int minX = (int) Math.floor(query.minX);
        int maxX = (int) Math.floor(query.maxX);
        int minY = (int) Math.floor(query.minY);
        int maxY = (int) Math.floor(query.maxY);
        int minZ = (int) Math.floor(query.minZ);
        int maxZ = (int) Math.floor(query.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType type = world.getBlock(x, y, z);
                    if (type != BlockType.AIR && type.isSolid()) {
                        scratchBlocks.add(new AABB(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f));
                    }
                }
            }
        }
    }

    private AABB offsetBox(Vector3f position, float halfWidth, float height, float ox, float oy, float oz) {
        scratchBox.minX = position.x - halfWidth + ox;
        scratchBox.minY = position.y + oy;
        scratchBox.minZ = position.z - halfWidth + oz;
        scratchBox.maxX = position.x + halfWidth + ox;
        scratchBox.maxY = position.y + height + oy;
        scratchBox.maxZ = position.z + halfWidth + oz;
        return scratchBox;
    }
}
