package no.minecraft.physics;

public class AABB {
    public float minX, minY, minZ;
    public float maxX, maxY, maxZ;

    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public boolean intersects(AABB other) {
        return this.maxX > other.minX && this.minX < other.maxX &&
               this.maxY > other.minY && this.minY < other.maxY &&
               this.maxZ > other.minZ && this.minZ < other.maxZ;
    }

    public AABB offset(float dx, float dy, float dz) {
        return new AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }
}
