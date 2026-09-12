package no.minecraft;

import no.minecraft.world.BlockType;
import no.minecraft.world.World;

public class PortalController {

    public static void igniteNetherPortal(World world, int x, int y, int z) {
        // Find which plane (XY or ZY) forms a valid Minecraft Nether Portal frame:
        // A standard portal frame is 4 wide x 5 tall (interior 2x3 air blocks),
        // or up to 23x23. We support standard 4x5 vertical frames along X or Z axis.
        if (tryIgnitePortalAxis(world, x, y, z, true)) return;
        tryIgnitePortalAxis(world, x, y, z, false);
    }

    private static boolean tryIgnitePortalAxis(World world, int startX, int startY, int startZ, boolean alongX) {
        // Search in a local neighborhood around the clicked obsidian block for candidate portal interior base
        for (int offset = -3; offset <= 1; offset++) {
            for (int dy = -1; dy <= 1; dy++) {
                int baseX = alongX ? startX + offset : startX;
                int baseZ = alongX ? startZ : startZ + offset;
                int baseY = startY + dy;

                // Check if this (baseX, baseY, baseZ) is the bottom-left interior corner of a 2x3 portal
                // Interior is: (i=0..1, j=0..2)
                // Bottom frame: (baseX + (alongX ? i : 0), baseY - 1, baseZ + (alongX ? 0 : i)) == OBSIDIAN
                // Top frame: (baseX + (alongX ? i : 0), baseY + 3, baseZ + (alongX ? 0 : i)) == OBSIDIAN
                // Left frame: (baseX - (alongX ? 1 : 0), baseY + j, baseZ - (alongX ? 0 : 1)) == OBSIDIAN
                // Right frame: (baseX + (alongX ? 2 : 0), baseY + j, baseZ + (alongX ? 0 : 2)) == OBSIDIAN
                boolean validFrame = true;

                // Bottom and Top frames
                for (int i = 0; i < 2; i++) {
                    int bx = alongX ? baseX + i : baseX;
                    int bz = alongX ? baseZ : baseZ + i;
                    if (world.getBlock(bx, baseY - 1, bz) != BlockType.OBSIDIAN ||
                        world.getBlock(bx, baseY + 3, bz) != BlockType.OBSIDIAN) {
                        validFrame = false;
                        break;
                    }
                }
                if (!validFrame) continue;

                // Left and Right sides
                for (int j = 0; j < 3; j++) {
                    int lx = alongX ? baseX - 1 : baseX;
                    int lz = alongX ? baseZ : baseZ - 1;
                    int rx = alongX ? baseX + 2 : baseX;
                    int rz = alongX ? baseZ : baseZ + 2;
                    if (world.getBlock(lx, baseY + j, lz) != BlockType.OBSIDIAN ||
                        world.getBlock(rx, baseY + j, rz) != BlockType.OBSIDIAN) {
                        validFrame = false;
                        break;
                    }
                }
                if (!validFrame) continue;

                // Check that interior is air (or already portal)
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 3; j++) {
                        int ix = alongX ? baseX + i : baseX;
                        int iz = alongX ? baseZ : baseZ + i;
                        BlockType cur = world.getBlock(ix, baseY + j, iz);
                        if (cur != BlockType.AIR && cur != BlockType.NETHER_PORTAL) {
                            validFrame = false;
                            break;
                        }
                    }
                    if (!validFrame) break;
                }
                if (!validFrame) continue;

                // Valid frame! Fill interior with NETHER_PORTAL
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 3; j++) {
                        int ix = alongX ? baseX + i : baseX;
                        int iz = alongX ? baseZ : baseZ + i;
                        world.setBlock(ix, baseY + j, iz, BlockType.NETHER_PORTAL);
                    }
                }
                return true;
            }
        }
        return false;
    }

    public static void checkAndActivateEndPortal(World world) {
        // Stronghold center portal space is at local x: 5..7, z: 5..7 in chunk (3, 3)
        // World coordinates: cx*16 + 5 = 48 + 5 = 53, py = 12
        int py = 12;
        boolean allFilled = true;
        // Check frames around (53..55, 53..55)
        for (int x = 53; x <= 55; x++) {
            if (world.getBlock(x, py, 52) != BlockType.END_PORTAL_FRAME_FILLED ||
                world.getBlock(x, py, 56) != BlockType.END_PORTAL_FRAME_FILLED) {
                allFilled = false;
                break;
            }
        }
        for (int z = 53; z <= 55; z++) {
            if (world.getBlock(52, py, z) != BlockType.END_PORTAL_FRAME_FILLED ||
                world.getBlock(56, py, z) != BlockType.END_PORTAL_FRAME_FILLED) {
                allFilled = false;
                break;
            }
        }

        if (allFilled) {
            // Fill 3x3 horizontal portal
            for (int x = 53; x <= 55; x++) {
                for (int z = 53; z <= 55; z++) {
                    world.setBlock(x, py, z, BlockType.END_PORTAL);
                }
            }
            no.minecraft.sound.SoundManager.getInstance().play("explode", 0.8f);
        }
    }
}
