package no.minecraft.math;

import no.minecraft.player.Camera;
import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FrustumTest {

    @Test
    public void testChunkInFrontVisibleAndBehindCulled() {
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0f), 1280.0f / 720.0f, 0.05f, 300.0f);
        Matrix4f view = new Matrix4f().lookAt(
                new Vector3f(8.0f, 20.0f, 8.0f),
                new Vector3f(8.0f, 20.0f, -8.0f),
                new Vector3f(0, 1, 0));

        Frustum frustum = new Frustum();
        frustum.update(proj, view);

        // Chunk directly ahead of the camera must be visible
        assertTrue(frustum.intersectsAabb(-8, 0, -32, 8, 64, -16),
                "Chunk foran kameraet må ikke cull-av");

        // Chunk directly behind must be culled
        assertFalse(frustum.intersectsAabb(-8, 0, 16, 8, 64, 32),
                "Chunk bak kameraet skal cull-av");
    }

    @Test
    public void testChunkContainingPlayerVisible() {
        // Exact in-game setup: player at (60, 30, 60) inside chunk (3, 3), yaw -90 (facing -Z)
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0f), 1280.0f / 720.0f, 0.05f, 300.0f);
        Matrix4f view = new Matrix4f().lookAt(
                new Vector3f(60.5f, 31.6f, 60.5f),
                new Vector3f(60.5f, 31.6f, 50.5f),
                new Vector3f(0, 1, 0));

        Frustum frustum = new Frustum();
        frustum.update(proj, view);

        // Chunk (3, 3) spans x 48..63, z 48..63 — the player is inside it
        assertTrue(frustum.intersectsAabb(48, 0, 48, 64, 64, 64),
                "Chunken spilleren står i må være synlig");

        // Chunks ahead (chunk z 2) and to the sides must also be visible
        assertTrue(frustum.intersectsAabb(48, 0, 32, 64, 64, 48), "Chunk foran skal være synlig");
        assertTrue(frustum.intersectsAabb(64, 0, 48, 80, 64, 64), "Chunk til høyre skal være synlig");
        assertTrue(frustum.intersectsAabb(32, 0, 48, 48, 64, 64), "Chunk til venstre skal være synlig");
    }

    @Test
    public void testFrustumWithCameraClassViewMatrix() {
        // The exact in-game path: Camera.getViewMatrix() + Frustum used by World.updateAndRender
        World world = new World(12345L);
        Camera camera = new Camera(60.5f, 30.0f, 60.5f);
        camera.setRotation(-90.0f, 0.0f); // Minecraft default yaw, facing -Z
        camera.updatePosition(world, 60.5f, 31.6f, 60.5f);

        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0f), 1280.0f / 720.0f, 0.05f, 300.0f);
        Matrix4f view = camera.getViewMatrix();

        Frustum frustum = new Frustum();
        frustum.update(proj, view);

        for (int cx = 0; cx < 6; cx++) {
            for (int cz = 0; cz < 6; cz++) {
                boolean visible = frustum.intersectsAabb(cx * 16, 0, cz * 16, cx * 16 + 16, 64, cz * 16 + 16);
                if (cx == 3 && cz == 3) {
                    assertTrue(visible, "Chunk (3,3) med spilleren i må være synlig");
                }
                // At least one chunk around the player must be visible
                if (Math.abs(cx - 3) <= 1 && Math.abs(cz - 3) <= 1 && visible) {
                    return; // found a visible nearby chunk — culling works
                }
            }
        }
        fail("Ingen chunks i nærheten av spilleren ble vurdert synlige");
    }
}
