package no.minecraft.render;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

public class TextureAtlas {
    public static final int ATLAS_TILES_PER_ROW = 16;
    public static final int TILE_SIZE = 16;
    public static final int ATLAS_SIZE = ATLAS_TILES_PER_ROW * TILE_SIZE; // 256x256 pixels

    private final int textureId;

    public TextureAtlas() {
        this.textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        ByteBuffer buffer = generateAtlasImage();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }

    public static float[] getUVs(int tileIndex) {
        int tileX = tileIndex % ATLAS_TILES_PER_ROW;
        int tileY = tileIndex / ATLAS_TILES_PER_ROW;

        float u0 = (float) tileX / ATLAS_TILES_PER_ROW;
        float v0 = (float) tileY / ATLAS_TILES_PER_ROW;
        float u1 = u0 + (1.0f / ATLAS_TILES_PER_ROW);
        float v1 = v0 + (1.0f / ATLAS_TILES_PER_ROW);

        // [u0, v0, u1, v1]
        return new float[]{u0, v0, u1, v1};
    }

    private ByteBuffer generateAtlasImage() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
        byte[][] pixelData = new byte[ATLAS_SIZE][ATLAS_SIZE * 4];

        // Fill all with transparent
        for (int y = 0; y < ATLAS_SIZE; y++) {
            for (int x = 0; x < ATLAS_SIZE * 4; x += 4) {
                pixelData[y][x] = 0;
                pixelData[y][x + 1] = 0;
                pixelData[y][x + 2] = 0;
                pixelData[y][x + 3] = 0;
            }
        }

        // Draw / Load each tile
        // 0: Grass Top
        loadOrPaint(pixelData, 0, "grass_top.png", (x, y, rand) -> {
            int g = 140 + rand.nextInt(40);
            int r = (int) (g * 0.45f) + rand.nextInt(15);
            int b = (int) (g * 0.25f);
            return rgba(r, g, b, 255);
        });

        // 1: Grass Side
        loadOrPaint(pixelData, 1, "grass_side.png", (x, y, rand) -> {
            int grassDepth = 3 + ((x % 3 == 0) ? 2 : (x % 2 == 0 ? 1 : 0));
            if (y < grassDepth) {
                int g = 135 + rand.nextInt(35);
                int r = (int) (g * 0.45f) + rand.nextInt(10);
                int b = (int) (g * 0.25f);
                return rgba(r, g, b, 255);
            } else {
                int base = 90 + rand.nextInt(25);
                return rgba((int) (base * 1.3f), (int) (base * 0.9f), (int) (base * 0.6f), 255);
            }
        });

        // 2: Dirt
        loadOrPaint(pixelData, 2, "dirt.png", (x, y, rand) -> {
            int base = 90 + rand.nextInt(30);
            return rgba((int) (base * 1.3f), (int) (base * 0.9f), (int) (base * 0.6f), 255);
        });

        // 3: Stone
        loadOrPaint(pixelData, 3, "stone.png", (x, y, rand) -> {
            int v = 110 + rand.nextInt(35);
            return rgba(v, v, v, 255);
        });

        // 4: Cobblestone
        loadOrPaint(pixelData, 4, "cobblestone.png", (x, y, rand) -> {
            boolean isBorder = (x % 5 == 0) || (y % 4 == 0) || ((x + y) % 7 == 0);
            int v = isBorder ? (70 + rand.nextInt(20)) : (120 + rand.nextInt(35));
            return rgba(v, v, v, 255);
        });

        // 5: Wood Side (Bark)
        loadOrPaint(pixelData, 5, "wood_side.png", (x, y, rand) -> {
            int stripe = (x % 4 == 0 || (x + 2) % 6 == 0) ? 60 : 100;
            int v = stripe + rand.nextInt(20);
            return rgba((int) (v * 1.1f), (int) (v * 0.75f), (int) (v * 0.45f), 255);
        });

        // 6: Wood Top (Rings)
        loadOrPaint(pixelData, 6, "wood_top.png", (x, y, rand) -> {
            float dx = x - 7.5f;
            float dy = y - 7.5f;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            int v = (dist > 6.5f) ? (65 + rand.nextInt(15)) : (((int) dist % 2 == 0) ? 140 : 160) + rand.nextInt(15);
            return rgba((int) (v * 1.1f), (int) (v * 0.85f), (int) (v * 0.55f), 255);
        });

        // 7: Leaves
        loadOrPaint(pixelData, 7, "leaves.png", (x, y, rand) -> {
            if (rand.nextInt(5) == 0) return rgba(0, 0, 0, 0);
            int g = 110 + rand.nextInt(60);
            return rgba((int) (g * 0.35f), g, (int) (g * 0.2f), 240);
        });

        // 8: Planks
        loadOrPaint(pixelData, 8, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
        });

        // 9: Bricks
        loadOrPaint(pixelData, 9, "bricks.png", (x, y, rand) -> {
            int row = y / 4;
            int shift = (row % 2 == 0) ? 0 : 4;
            boolean mortar = (y % 4 == 0) || ((x + shift) % 8 == 0);
            if (mortar) return rgba(190, 190, 190, 255);
            int r = 160 + rand.nextInt(30);
            return rgba(r, (int) (r * 0.45f), (int) (r * 0.35f), 255);
        });

        // 10: Sand
        loadOrPaint(pixelData, 10, "sand.png", (x, y, rand) -> {
            int base = 195 + rand.nextInt(25);
            return rgba(base, (int) (base * 0.92f), (int) (base * 0.65f), 255);
        });

        // 11: Glass
        loadOrPaint(pixelData, 11, "glass.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            return border ? rgba(220, 240, 255, 200) : rgba(200, 230, 255, 40);
        });

        // 12: Bedrock
        loadOrPaint(pixelData, 12, "bedrock.png", (x, y, rand) -> {
            int v = rand.nextInt(3) == 0 ? (20 + rand.nextInt(20)) : (60 + rand.nextInt(40));
            return rgba(v, v, v, 255);
        });

        // 13: Crafting Table Top
        loadOrPaint(pixelData, 13, "crafting_table_top.png", (x, y, rand) -> {
            int v = 160 + rand.nextInt(20);
            return rgba((int)(v * 1.1f), (int)(v * 0.85f), (int)(v * 0.55f), 255);
        });

        // 14: Crafting Table Side
        loadOrPaint(pixelData, 14, "crafting_table_side.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 100 : (145 + rand.nextInt(20));
            return rgba((int)(base * 1.15f), (int)(base * 0.82f), (int)(base * 0.52f), 255);
        });

        // 15: Stick
        loadOrPaint(pixelData, 15, "stick.png", (x, y, rand) -> {
            int diff = (15 - x) - y;
            if (diff >= -1 && diff <= 1 && x >= 2 && x <= 13) {
                int base = (diff == -1) ? 120 : (diff == 0 ? 155 : 95);
                return rgba((int)(base * 1.15f), (int)(base * 0.82f), (int)(base * 0.52f), 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 16: Wooden Pickaxe
        loadOrPaint(pixelData, 16, "wooden_pickaxe.png", (x, y, rand) -> rgba(0,0,0,0));

        // 17: Wooden Axe
        loadOrPaint(pixelData, 17, "wooden_axe.png", (x, y, rand) -> rgba(0,0,0,0));

        // 18: Wooden Shovel
        loadOrPaint(pixelData, 18, "wooden_shovel.png", (x, y, rand) -> rgba(0,0,0,0));

        // 19: Wooden Sword
        loadOrPaint(pixelData, 19, "wooden_sword.png", (x, y, rand) -> rgba(0,0,0,0));

        // 20: Wooden Hoe
        loadOrPaint(pixelData, 20, "wooden_hoe.png", (x, y, rand) -> rgba(0,0,0,0));

        // 21: Boat
        loadOrPaint(pixelData, 21, "boat.png", (x, y, rand) -> rgba(0,0,0,0));

        // 22: Chest (Procedural fallback or icon)
        drawTile(pixelData, 22, (x, y, rand) -> {
            if (x < 1 || x > 14 || y < 1 || y > 14) return rgba(0, 0, 0, 0);
            if (x == 1 || x == 14 || y == 1 || y == 14) return rgba(60, 40, 20, 255);
            if (x >= 7 && x <= 8 && y >= 6 && y <= 8) return rgba(220, 220, 220, 255); // Latch
            int base = 140 + rand.nextInt(20);
            return rgba((int)(base * 1.15f), (int)(base * 0.75f), (int)(base * 0.35f), 255);
        });

        // 23: Wooden Door
        loadOrPaint(pixelData, 23, "wooden_door.png", (x, y, rand) -> rgba(0,0,0,0));

        // 24: Trapdoor
        loadOrPaint(pixelData, 24, "trapdoor.png", (x, y, rand) -> rgba(0,0,0,0));

        // 25: Ladder
        loadOrPaint(pixelData, 25, "ladder.png", (x, y, rand) -> rgba(0,0,0,0));

        // 26: Fence
        drawTile(pixelData, 26, (x, y, rand) -> {
            if (x >= 3 && x <= 6) return rgba(140, 95, 50, 255);
            if (x >= 10 && x <= 13) return rgba(140, 95, 50, 255);
            if (y >= 4 && y <= 6 || y >= 9 && y <= 11) return rgba(160, 110, 60, 255);
            return rgba(0, 0, 0, 0);
        });

        // 27: Fence Gate
        drawTile(pixelData, 27, (x, y, rand) -> {
            if (x == 1 || x == 2 || x == 13 || x == 14) return rgba(140, 95, 50, 255);
            if (y >= 5 && y <= 11 && x >= 4 && x <= 11) {
                return (x == 7 || x == 8) ? rgba(120, 80, 40, 255) : rgba(165, 115, 65, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 28: Wooden Slab
        drawTile(pixelData, 28, (x, y, rand) -> {
            if (y < 8) return rgba(0, 0, 0, 0);
            boolean line = (y == 8 || y == 12);
            int base = line ? 110 : (160 + rand.nextInt(20));
            return rgba((int)(base * 1.15f), (int)(base * 0.85f), (int)(base * 0.55f), 255);
        });

        // 29: Wooden Stairs
        drawTile(pixelData, 29, (x, y, rand) -> {
            if (x < 8 && y < 8) return rgba(0, 0, 0, 0);
            int base = 150 + rand.nextInt(20);
            return rgba((int)(base * 1.15f), (int)(base * 0.85f), (int)(base * 0.55f), 255);
        });

        // 30: Pressure Plate
        drawTile(pixelData, 30, (x, y, rand) -> {
            if (y >= 13 && x >= 2 && x <= 13) return rgba(160, 115, 65, 255);
            return rgba(0, 0, 0, 0);
        });

        // 31: Button
        drawTile(pixelData, 31, (x, y, rand) -> {
            if (x >= 5 && x <= 10 && y >= 6 && y <= 9) return rgba(165, 120, 70, 255);
            return rgba(0, 0, 0, 0);
        });

        // 32: Bowl
        loadOrPaint(pixelData, 32, "bowl.png", (x, y, rand) -> rgba(0,0,0,0));

        // 33: Furnace Front
        loadOrPaint(pixelData, 33, "furnace_front.png", (x, y, rand) -> rgba(0,0,0,0));

        // 34: Stone Pickaxe
        loadOrPaint(pixelData, 34, "stone_pickaxe.png", (x, y, rand) -> rgba(0,0,0,0));

        // 35: Stone Axe
        loadOrPaint(pixelData, 35, "stone_axe.png", (x, y, rand) -> rgba(0,0,0,0));

        // 36: Stone Shovel
        loadOrPaint(pixelData, 36, "stone_shovel.png", (x, y, rand) -> rgba(0,0,0,0));

        // 37: Stone Sword
        loadOrPaint(pixelData, 37, "stone_sword.png", (x, y, rand) -> rgba(0,0,0,0));

        // 38: Rotten Flesh
        loadOrPaint(pixelData, 38, "rotten_flesh.png", (x, y, rand) -> rgba(0,0,0,0));

        // 39: Gunpowder
        loadOrPaint(pixelData, 39, "gunpowder.png", (x, y, rand) -> rgba(0,0,0,0));

        // 40: String
        loadOrPaint(pixelData, 40, "string.png", (x, y, rand) -> rgba(0,0,0,0));

        // 41: Bone
        loadOrPaint(pixelData, 41, "bone.png", (x, y, rand) -> rgba(0,0,0,0));

        // 42-51: Mining crack animation stages (0 to 9)
        for (int stage = 0; stage < 10; stage++) {
            final int s = stage;
            loadOrPaint(pixelData, 42 + s, "destroy_stage_" + s + ".png", (x, y, rand) -> {
                int density = (s + 1) * 3;
                boolean crack = ((x * 7 + y * 13 + (x ^ y)) % 37) < density;
                return crack ? rgba(0, 0, 0, 180 + s * 7) : rgba(0, 0, 0, 0);
            });
        }

        for (int y = 0; y < ATLAS_SIZE; y++) {
            buffer.put(pixelData[y]);
        }
        buffer.flip();
        return buffer;
    }

    private void loadOrPaint(byte[][] data, int tileIndex, String filename, TilePainter fallback) {
        int tileX = (tileIndex % ATLAS_TILES_PER_ROW) * TILE_SIZE;
        int tileY = (tileIndex / ATLAS_TILES_PER_ROW) * TILE_SIZE;

        try {
            InputStream is = getClass().getResourceAsStream("/assets/textures/" + filename);
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    for (int py = 0; py < TILE_SIZE; py++) {
                        for (int px = 0; px < TILE_SIZE; px++) {
                            int ix = (px * img.getWidth()) / TILE_SIZE;
                            int iy = (py * img.getHeight()) / TILE_SIZE;
                            int argb = img.getRGB(ix, iy);

                            int a = (argb >> 24) & 0xFF;
                            int r = (argb >> 16) & 0xFF;
                            int g = (argb >> 8) & 0xFF;
                            int b = argb & 0xFF;

                            int targetY = tileY + py;
                            int targetX = (tileX + px) * 4;

                            data[targetY][targetX] = (byte) r;
                            data[targetY][targetX + 1] = (byte) g;
                            data[targetY][targetX + 2] = (byte) b;
                            data[targetY][targetX + 3] = (byte) a;
                        }
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback procedural painting if resource not found
        drawTile(data, tileIndex, fallback);
    }

    private interface TilePainter {
        int[] paint(int x, int y, Random rand);
    }

    private void drawTile(byte[][] data, int tileIndex, TilePainter painter) {
        int tileX = (tileIndex % ATLAS_TILES_PER_ROW) * TILE_SIZE;
        int tileY = (tileIndex / ATLAS_TILES_PER_ROW) * TILE_SIZE;
        Random rand = new Random(tileIndex * 1337L);

        for (int py = 0; py < TILE_SIZE; py++) {
            for (int px = 0; px < TILE_SIZE; px++) {
                int[] c = painter.paint(px, py, rand);
                int targetY = tileY + py;
                int targetX = (tileX + px) * 4;

                data[targetY][targetX] = (byte) c[0];
                data[targetY][targetX + 1] = (byte) c[1];
                data[targetY][targetX + 2] = (byte) c[2];
                data[targetY][targetX + 3] = (byte) c[3];
            }
        }
    }

    private static int[] rgba(int r, int g, int b, int a) {
        return new int[]{
                Math.clamp(r, 0, 255),
                Math.clamp(g, 0, 255),
                Math.clamp(b, 0, 255),
                Math.clamp(a, 0, 255)
        };
    }
}
