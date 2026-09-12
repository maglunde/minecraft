package no.minecraft.player;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class Raycast {
    public static class HitResult {
        public final int hitX, hitY, hitZ;
        public final int placeX, placeY, placeZ;
        public final BlockType blockType;

        public HitResult(int hitX, int hitY, int hitZ, int placeX, int placeY, int placeZ, BlockType blockType) {
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.placeX = placeX;
            this.placeY = placeY;
            this.placeZ = placeZ;
            this.blockType = blockType;
        }
    }

    public static HitResult raycast(World world, Vector3f origin, Vector3f direction, float maxDistance) {
        return raycast(world, origin, direction, maxDistance, false);
    }

    public static HitResult raycast(World world, Vector3f origin, Vector3f direction, float maxDistance, boolean includeWater) {
        // DDA voxel traversal: step through grid cells exactly, no brute-force sampling
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = direction.x > 0 ? 1 : -1;
        int stepY = direction.y > 0 ? 1 : -1;
        int stepZ = direction.z > 0 ? 1 : -1;

        double tMaxX = (direction.x == 0) ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? x + 1 : x) - origin.x) / direction.x;
        double tMaxY = (direction.y == 0) ? Double.POSITIVE_INFINITY
                : ((stepY > 0 ? y + 1 : y) - origin.y) / direction.y;
        double tMaxZ = (direction.z == 0) ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? z + 1 : z) - origin.z) / direction.z;

        double tDeltaX = (direction.x == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction.x);
        double tDeltaY = (direction.y == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction.y);
        double tDeltaZ = (direction.z == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction.z);

        double t = 0.0;
        int prevX = x, prevY = y, prevZ = z;

        while (t <= maxDistance) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                prevX = x; prevY = y; prevZ = z;
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                prevX = x; prevY = y; prevZ = z;
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                prevX = x; prevY = y; prevZ = z;
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
            if (t > maxDistance) break;

            BlockType type = world.getBlock(x, y, z);
            boolean isHit = (type != BlockType.AIR && type != BlockType.LAVA);
            if (!includeWater) {
                isHit = isHit && (type != BlockType.WATER);
            }
            if (isHit) {
                return new HitResult(x, y, z, prevX, prevY, prevZ, type);
            }
        }

        return null;
    }

    /**
     * Sjekker om det er fri sikt (line of sight) mellom to punkter uten hindringer av faste blokker.
     * Allokerer ingen objekter på heapen (GC-fritt).
     */
    public static boolean hasLineOfSight(World world, Vector3f from, Vector3f to) {
        return hasLineOfSight(world, from.x, from.y, from.z, to.x, to.y, to.z);
    }

    /**
     * Sjekker om det er fri sikt (line of sight) mellom to punkter uten hindringer av faste blokker.
     * Bruker 3D DDA traversal og allokerer null objekter på heapen.
     */
    public static boolean hasLineOfSight(World world, float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        float distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 0.0001f) {
            int bx = (int) Math.floor(fromX);
            int by = (int) Math.floor(fromY);
            int bz = (int) Math.floor(fromZ);
            BlockType b = world.getBlock(bx, by, bz);
            return b == null || !b.isSolid();
        }

        int x = (int) Math.floor(fromX);
        int y = (int) Math.floor(fromY);
        int z = (int) Math.floor(fromZ);

        BlockType startBlock = world.getBlock(x, y, z);
        if (startBlock != null && startBlock.isSolid()) {
            return false;
        }

        int targetBlockX = (int) Math.floor(toX);
        int targetBlockY = (int) Math.floor(toY);
        int targetBlockZ = (int) Math.floor(toZ);

        if (x == targetBlockX && y == targetBlockY && z == targetBlockZ) {
            return true;
        }

        float dist = (float) Math.sqrt(distSq);
        float dirX = dx / dist;
        float dirY = dy / dist;
        float dirZ = dz / dist;

        int stepX = dirX > 0 ? 1 : (dirX < 0 ? -1 : 0);
        int stepY = dirY > 0 ? 1 : (dirY < 0 ? -1 : 0);
        int stepZ = dirZ > 0 ? 1 : (dirZ < 0 ? -1 : 0);

        double tMaxX = (dirX == 0) ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? x + 1 : x) - fromX) / dirX;
        double tMaxY = (dirY == 0) ? Double.POSITIVE_INFINITY
                : ((stepY > 0 ? y + 1 : y) - fromY) / dirY;
        double tMaxZ = (dirZ == 0) ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? z + 1 : z) - fromZ) / dirZ;

        double tDeltaX = (dirX == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dirX);
        double tDeltaY = (dirY == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dirY);
        double tDeltaZ = (dirZ == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dirZ);

        double t = 0.0;

        while (t <= dist) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }

            if (t > dist) {
                break;
            }

            BlockType type = world.getBlock(x, y, z);
            if (type != null && type.isSolid()) {
                return false;
            }

            if (x == targetBlockX && y == targetBlockY && z == targetBlockZ) {
                break;
            }
        }

        return true;
    }

    public static float getCameraDistance(World world, Vector3f origin, Vector3f direction, float maxDistance) {
        float step = 0.08f;
        Vector3f currentPos = new Vector3f(origin);
        Vector3f rayStep = new Vector3f(direction).mul(step);
        float distanceTraveled = 0.0f;

        while (distanceTraveled + step <= maxDistance) {
            currentPos.add(rayStep);
            distanceTraveled += step;

            int bx = (int) Math.floor(currentPos.x);
            int by = (int) Math.floor(currentPos.y);
            int bz = (int) Math.floor(currentPos.z);

            BlockType type = world.getBlock(bx, by, bz);
            if (type != null && type.isSolid()) {
                return Math.max(0.3f, distanceTraveled - 0.2f);
            }
        }
        return maxDistance;
    }
}
