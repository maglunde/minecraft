package no.minecraft.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MobUVMappingTest {

    @Test
    public void testAddTexturedBoxVertexCountAndStride() {
        List<Float> verts = new ArrayList<>();
        Matrix4f mat = new Matrix4f();

        // Add standard 8x8x8 head box on 64x64 texture
        MobRenderer.addTexturedBox(verts, mat,
                -0.24f, 0, -0.24f,
                0.48f, 0.48f, 0.48f,
                0, 0, 64, 64,
                8, 8, 8,
                1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

        // 6 faces * 2 triangles * 3 vertices = 36 vertices
        // Stride is 10 floats per vertex (pos: 3, uv: 2, color: 4, light: 1)
        assertEquals(36 * 10, verts.size(), "Boksen skal ha nøyaktig 36 vertekser med 10 floats hver (360 floats)");
    }

    @Test
    public void testUVCoordinatesBounds() {
        List<Float> verts = new ArrayList<>();
        Matrix4f mat = new Matrix4f();

        int u = 16, v = 16, tw = 64, th = 64;
        int pw = 8, ph = 12, pd = 4;

        MobRenderer.addTexturedBox(verts, mat,
                -0.24f, 0, -0.12f,
                0.48f, 0.72f, 0.24f,
                u, v, tw, th,
                pw, ph, pd,
                1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

        // Verify all UV coords are in range [0..1]
        for (int i = 0; i < verts.size(); i += 10) {
            float uvU = verts.get(i + 3);
            float uvV = verts.get(i + 4);
            assertTrue(uvU >= 0.0f && uvU <= 1.0f, "U-koordinat må være innenfor [0, 1]: " + uvU);
            assertTrue(uvV >= 0.0f && uvV <= 1.0f, "V-koordinat må være innenfor [0, 1]: " + uvV);
        }
    }

    @Test
    public void testTopFaceUVRange() {
        List<Float> verts = new ArrayList<>();
        Matrix4f mat = new Matrix4f();

        int u = 0, v = 0, tw = 64, th = 64;
        int pw = 8, ph = 8, pd = 8;

        MobRenderer.addTexturedBox(verts, mat,
                -0.24f, 0, -0.24f,
                0.48f, 0.48f, 0.48f,
                u, v, tw, th,
                pw, ph, pd,
                1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

        // Top face is the first 6 vertices (indices 0..59)
        // Expected Top face U range: (u + pd)/tw = 8/64 = 0.125 to (u + pd + pw)/tw = 16/64 = 0.250
        // Expected Top face V range: v/th = 0.0 to (v + pd)/th = 8/64 = 0.125
        for (int vertex = 0; vertex < 6; vertex++) {
            int offset = vertex * 10;
            float uvU = verts.get(offset + 3);
            float uvV = verts.get(offset + 4);

            assertTrue(uvU >= 0.124f && uvU <= 0.251f, "Toppflate U må ligge i [0.125, 0.25]: " + uvU);
            assertTrue(uvV >= 0.0f && uvV <= 0.126f, "Toppflate V må ligge i [0.0, 0.125]: " + uvV);
        }
    }
}
