package no.minecraft.math;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Six-plane view frustum for AABB culling. */
public class Frustum {
    private final Matrix4f clip = new Matrix4f();
    private final Vector4f[] planes = new Vector4f[]{
            new Vector4f(), new Vector4f(), new Vector4f(),
            new Vector4f(), new Vector4f(), new Vector4f()
    };

    public void update(Matrix4f projection, Matrix4f view) {
        projection.mul(view, clip);
        for (int i = 0; i < 6; i++) {
            clip.frustumPlane(i, planes[i]);
        }
    }

    /** Conservative AABB test: false means fully outside. */
    public boolean intersectsAabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (Vector4f p : planes) {
            float px = p.x > 0 ? maxX : minX;
            float py = p.y > 0 ? maxY : minY;
            float pz = p.z > 0 ? maxZ : minZ;
            if (p.x * px + p.y * py + p.z * pz + p.w < 0) {
                return false;
            }
        }
        return true;
    }
}
