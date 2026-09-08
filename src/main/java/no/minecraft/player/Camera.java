package no.minecraft.player;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private final Vector3f position = new Vector3f();
    private float yaw = -90.0f;  // Facing negative Z by default
    private float pitch = 0.0f;

    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f(0, 1, 0);

    public Camera(float x, float y, float z) {
        position.set(x, y, z);
        updateVectors();
    }

    public void updateVectors() {
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);

        forward.x = (float) (Math.cos(radPitch) * Math.cos(radYaw));
        forward.y = (float) Math.sin(radPitch);
        forward.z = (float) (Math.cos(radPitch) * Math.sin(radYaw));
        forward.normalize();

        forward.cross(0.0f, 1.0f, 0.0f, right);
        right.normalize();

        right.cross(forward, up);
        up.normalize();
    }

    public void rotate(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch += dPitch;

        // Constrain pitch to avoid flipping
        pitch = Math.clamp(pitch, -89.0f, 89.0f);
        updateVectors();
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(
                position,
                new Vector3f(position).add(forward),
                up
        );
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getForward() {
        return forward;
    }

    public Vector3f getRight() {
        return right;
    }

    public Vector3f getUp() {
        return up;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
