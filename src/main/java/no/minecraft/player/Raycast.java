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
        float step = 0.05f;
        Vector3f currentPos = new Vector3f(origin);
        Vector3f rayStep = new Vector3f(direction).mul(step);

        int prevBlockX = (int) Math.floor(currentPos.x);
        int prevBlockY = (int) Math.floor(currentPos.y);
        int prevBlockZ = (int) Math.floor(currentPos.z);

        float distanceTraveled = 0.0f;

        while (distanceTraveled <= maxDistance) {
            currentPos.add(rayStep);
            distanceTraveled += step;

            int blockX = (int) Math.floor(currentPos.x);
            int blockY = (int) Math.floor(currentPos.y);
            int blockZ = (int) Math.floor(currentPos.z);

            if (blockX != prevBlockX || blockY != prevBlockY || blockZ != prevBlockZ) {
                BlockType type = world.getBlock(blockX, blockY, blockZ);
                if (type != BlockType.AIR && type != BlockType.BEDROCK) {
                    return new HitResult(blockX, blockY, blockZ, prevBlockX, prevBlockY, prevBlockZ, type);
                }
                prevBlockX = blockX;
                prevBlockY = blockY;
                prevBlockZ = blockZ;
            }
        }

        return null;
    }
}
