package no.minecraft.render;

import no.minecraft.math.Frustum;
import no.minecraft.world.Chunk;
import no.minecraft.world.World;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Owns chunk GPU lifetimes. Rendering and cleanup require an active GL context. */
public final class ChunkRenderer {
    private final Map<Chunk, ChunkMesh> meshes = new IdentityHashMap<>();
    private int renderedChunkCount;

    public int getRenderedChunkCount() {
        return renderedChunkCount;
    }

    public void render(World world, int centerCx, int centerCz, int renderDistance, Frustum frustum) {
        // Identity matters: a reset or dimension change can replace a chunk at the same coordinates.
        Iterator<Map.Entry<Chunk, ChunkMesh>> iterator = meshes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Chunk, ChunkMesh> entry = iterator.next();
            Chunk chunk = entry.getKey();
            if (world.getChunk(chunk.getChunkX(), chunk.getChunkZ()) != chunk) {
                entry.getValue().cleanup();
                iterator.remove();
            }
        }
        renderedChunkCount = 0;
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                Chunk chunk = world.getChunk(centerCx + dx, centerCz + dz);
                if (chunk == null) continue;
                ChunkMesh mesh = meshes.get(chunk);
                if (mesh == null) {
                    mesh = new ChunkMesh(world, chunk);
                    meshes.put(chunk, mesh);
                    mesh.rebuildMesh();
                    chunk.setDirty(false);
                } else if (chunk.isDirty()) {
                    mesh.rebuildMesh();
                    chunk.setDirty(false);
                }
                if (frustum == null || frustum.intersectsAabb(
                        chunk.getWorldStartX(), 0, chunk.getWorldStartZ(),
                        chunk.getWorldStartX() + Chunk.SIZE_X, Chunk.SIZE_Y,
                        chunk.getWorldStartZ() + Chunk.SIZE_Z)) {
                    mesh.render();
                    renderedChunkCount++;
                }
            }
        }
    }

    public void cleanup() {
        for (ChunkMesh mesh : meshes.values()) {
            mesh.cleanup();
        }
        meshes.clear();
        renderedChunkCount = 0;
    }
}
