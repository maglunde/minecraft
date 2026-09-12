package no.minecraft.player;

import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private final Vector3f position = new Vector3f();
    private final Vector3f eyePosition = new Vector3f();
    private float yaw = -90.0f;  // Facing negative Z by default
    private float pitch = 0.0f;

    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f(0, 1, 0);

    private Perspective perspective = Perspective.FIRST_PERSON;
    public static final float MAX_THIRD_PERSON_DISTANCE = 4.0f;

    public Camera(float x, float y, float z) {
        eyePosition.set(x, y, z);
        position.set(x, y, z);
        updateVectors();
    }

    public void cyclePerspective() {
        this.perspective = this.perspective.next();
    }

    public Perspective getPerspective() {
        return perspective;
    }

    public void setPerspective(Perspective perspective) {
        this.perspective = perspective;
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

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Math.clamp(pitch, -89.0f, 89.0f);
        updateVectors();
    }

    public void updatePosition(World world, float eyeX, float eyeY, float eyeZ) {
        eyePosition.set(eyeX, eyeY, eyeZ);

        switch (perspective) {
            case FIRST_PERSON -> position.set(eyePosition);
            case THIRD_PERSON_BACK -> {
                // Vector going backward from eyes: -forward
                Vector3f backDir = new Vector3f(forward).negate();
                float dist = (world != null)
                        ? Raycast.getCameraDistance(world, eyePosition, backDir, MAX_THIRD_PERSON_DISTANCE)
                        : MAX_THIRD_PERSON_DISTANCE;
                position.set(eyePosition).add(new Vector3f(backDir).mul(dist));
            }
            case THIRD_PERSON_FRONT -> {
                // Vector going forward from eyes: +forward
                float dist = (world != null)
                        ? Raycast.getCameraDistance(world, eyePosition, forward, MAX_THIRD_PERSON_DISTANCE)
                        : MAX_THIRD_PERSON_DISTANCE;
                position.set(eyePosition).add(new Vector3f(forward).mul(dist));
            }
        }
    }

    public Matrix4f getViewMatrix() {
        if (perspective == Perspective.THIRD_PERSON_FRONT) {
            return new Matrix4f().lookAt(
                    position,
                    eyePosition,
                    new Vector3f(0, 1, 0)
            );
        } else {
            return new Matrix4f().lookAt(
                    position,
                    new Vector3f(position).add(forward),
                    up
            );
        }
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getEyePosition() {
        return eyePosition;
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
