package no.minecraft.render;

import no.minecraft.math.Frustum;
import no.minecraft.world.Chunk;
import no.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkRendererFrustumTest {
    @Test
    void onlyVisibleChunksAreEligibleForMeshing() {
        World world = new World(12345L);
        Chunk aheadCamera = world.ensureChunkGenerated(0, -1);
        Chunk behindCamera = world.ensureChunkGenerated(0, 1);
        behindCamera.setDirty(true);

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16.0f / 9.0f, 0.05f, 300.0f);
        Matrix4f view = new Matrix4f().lookAt(
                new Vector3f(8, 20, 8), new Vector3f(8, 20, -8), new Vector3f(0, 1, 0));
        Frustum frustum = new Frustum();
        frustum.update(projection, view);

        assertTrue(ChunkRenderer.isChunkVisible(aheadCamera, frustum));
        assertFalse(ChunkRenderer.isChunkVisible(behindCamera, frustum));
        assertTrue(behindCamera.isDirty(), "Culling må ikke rydde dirty-flagget før chunk blir synlig");
    }
}
