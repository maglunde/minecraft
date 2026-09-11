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

        // 22: Chest Front (Oak wood frame, horizontal lid seam, and silver latch)
        loadOrPaint(pixelData, 22, "chest_front.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            if (border) return rgba(55, 35, 18, 255);
            boolean seam = (y == 5);
            if (seam) return rgba(45, 28, 14, 255);
            boolean latch = (x >= 7 && x <= 8 && y >= 4 && y <= 7);
            if (latch) {
                if (y == 7) return rgba(70, 70, 75, 255); // Latch shadow
                return rgba(225, 225, 230, 255); // Silver clasp
            }
            int base = 145 + rand.nextInt(18);
            return rgba((int)(base * 1.15f), (int)(base * 0.75f), (int)(base * 0.35f), 255);
        });

        // 23: Wooden Door
        loadOrPaint(pixelData, 23, "wooden_door.png", (x, y, rand) -> rgba(0,0,0,0));

        // 24: Trapdoor
        loadOrPaint(pixelData, 24, "trapdoor.png", (x, y, rand) -> rgba(0,0,0,0));

        // 25: Ladder
        loadOrPaint(pixelData, 25, "ladder.png", (x, y, rand) -> rgba(0,0,0,0));

        // 26: Fence (Solid oak wood planks)
        loadOrPaint(pixelData, 26, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
        });

        // 27: Fence Gate (Solid oak wood planks)
        loadOrPaint(pixelData, 27, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
        });

        // 28: Wooden Slab (Solid oak wood planks)
        loadOrPaint(pixelData, 28, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
        });

        // 29: Wooden Stairs (Solid oak wood planks)
        loadOrPaint(pixelData, 29, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
        });

        // 30: Pressure Plate (Solid oak wood planks)
        loadOrPaint(pixelData, 30, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
        });

        // 31: Button (Solid oak wood planks)
        loadOrPaint(pixelData, 31, "planks.png", (x, y, rand) -> {
            int base = (y % 4 == 0) ? 110 : (160 + rand.nextInt(20));
            return rgba((int) (base * 1.15f), (int) (base * 0.85f), (int) (base * 0.55f), 255);
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

        // 52: Obsidian (Dark purple/black mottled)
        loadOrPaint(pixelData, 52, "obsidian.png", (x, y, rand) -> {
            int base = 15 + rand.nextInt(25);
            int purple = (rand.nextInt(4) == 0) ? 25 : 5;
            return rgba(base + purple / 2, base, base + purple, 255);
        });

        // 53: Netherrack (Dark crimson red)
        loadOrPaint(pixelData, 53, "netherrack.png", (x, y, rand) -> {
            int r = 110 + rand.nextInt(45);
            int g = (int)(r * 0.22f) + rand.nextInt(10);
            int b = (int)(r * 0.22f);
            return rgba(r, g, b, 255);
        });

        // 54: Nether Bricks (Dark maroon bricks)
        loadOrPaint(pixelData, 54, "nether_bricks.png", (x, y, rand) -> {
            int row = y / 4;
            int shift = (row % 2 == 0) ? 0 : 4;
            boolean mortar = (y % 4 == 0) || ((x + shift) % 8 == 0);
            if (mortar) return rgba(35, 15, 18, 255);
            int r = 65 + rand.nextInt(25);
            return rgba(r, (int)(r * 0.25f), (int)(r * 0.35f), 255);
        });

        // 55: Nether Portal (Translucent purple swirl)
        loadOrPaint(pixelData, 55, "nether_portal.png", (x, y, rand) -> {
            int v = 140 + rand.nextInt(90);
            return rgba((int)(v * 0.75f), (int)(v * 0.25f), v, 200);
        });

        // 56: End Stone (Pale yellow/cream mottled stone)
        loadOrPaint(pixelData, 56, "end_stone.png", (x, y, rand) -> {
            int v = 180 + rand.nextInt(40);
            return rgba(v, (int)(v * 0.98f), (int)(v * 0.72f), 255);
        });

        // 57: End Portal Frame Top (Empty)
        loadOrPaint(pixelData, 57, "end_portal_frame_top.png", (x, y, rand) -> {
            boolean centerRecess = (x >= 4 && x <= 11 && y >= 4 && y <= 11);
            if (centerRecess) {
                int v = 30 + rand.nextInt(20);
                return rgba(v, (int)(v * 1.3f), (int)(v * 1.1f), 255);
            }
            int g = 100 + rand.nextInt(35);
            return rgba((int)(g * 0.6f), g, (int)(g * 0.65f), 255);
        });

        // 58: End Portal Frame Side
        loadOrPaint(pixelData, 58, "end_portal_frame_side.png", (x, y, rand) -> {
            if (y >= 12) {
                // Pale stone base
                int v = 175 + rand.nextInt(30);
                return rgba(v, (int)(v * 0.96f), (int)(v * 0.70f), 255);
            }
            int g = 90 + rand.nextInt(30);
            return rgba((int)(g * 0.55f), g, (int)(g * 0.60f), 255);
        });

        // 59: End Portal Frame Top (Filled with Eye)
        loadOrPaint(pixelData, 59, "end_portal_frame_eye.png", (x, y, rand) -> {
            boolean eye = (x >= 5 && x <= 10 && y >= 5 && y <= 10);
            boolean pupil = (x >= 7 && x <= 8 && y >= 6 && y <= 9);
            if (pupil) return rgba(20, 20, 20, 255);
            if (eye) {
                int r = 220 + rand.nextInt(35);
                return rgba(r, (int)(r * 0.65f), 30, 255);
            }
            int g = 100 + rand.nextInt(35);
            return rgba((int)(g * 0.6f), g, (int)(g * 0.65f), 255);
        });

        // 60: End Portal (Starry Black Void)
        loadOrPaint(pixelData, 60, "end_portal.png", (x, y, rand) -> {
            boolean star = rand.nextInt(12) == 0;
            if (star) {
                return (rand.nextBoolean()) ? rgba(180, 255, 240, 255) : rgba(220, 180, 255, 255);
            }
            return rgba(12, 12, 22, 255);
        });

        // 61: Dragon Egg (Obsidian black with purple speckles)
        loadOrPaint(pixelData, 61, "dragon_egg.png", (x, y, rand) -> {
            boolean speckle = rand.nextInt(6) == 0;
            if (speckle) return rgba(160, 40, 220, 255);
            int v = 15 + rand.nextInt(20);
            return rgba(v, v, v, 255);
        });

        // 62: Blaze Rod (Golden fiery rod)
        loadOrPaint(pixelData, 62, "blaze_rod.png", (x, y, rand) -> {
            boolean rod = (x + y >= 13 && x + y <= 17) && Math.abs(x - y) <= 2;
            if (rod) {
                int r = 240 + rand.nextInt(15);
                int g = 160 + rand.nextInt(50);
                return rgba(r, g, 20, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 63: Blaze Powder (Fiery powder)
        loadOrPaint(pixelData, 63, "blaze_powder.png", (x, y, rand) -> {
            float dx = x - 7.5f;
            float dy = y - 7.5f;
            if (dx * dx + dy * dy <= 25 && rand.nextInt(4) != 0) {
                int r = 240 + rand.nextInt(15);
                int g = 120 + rand.nextInt(60);
                return rgba(r, g, 15, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 64: Ender Pearl (Teal/cyan sphere)
        loadOrPaint(pixelData, 64, "ender_pearl.png", (x, y, rand) -> {
            float dx = x - 7.5f;
            float dy = y - 7.5f;
            float distSq = dx * dx + dy * dy;
            if (distSq <= 30) {
                int c = (distSq < 10) ? 140 : 80;
                return rgba((int)(c * 0.2f), (int)(c * 0.9f), (int)(c * 0.85f), 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 65: Eye of Ender (Green pearl with slit pupil)
        loadOrPaint(pixelData, 65, "eye_of_ender.png", (x, y, rand) -> {
            float dx = x - 7.5f;
            float dy = y - 7.5f;
            float distSq = dx * dx + dy * dy;
            if (distSq <= 30) {
                boolean pupil = (Math.abs(dx) <= 0.8f && Math.abs(dy) <= 3.0f);
                if (pupil) return rgba(10, 10, 10, 255);
                if (distSq < 12) {
                    return rgba(240, 160, 30, 255); // Orange iris
                }
                int g = 100 + rand.nextInt(40);
                return rgba((int)(g * 0.35f), g, (int)(g * 0.45f), 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 66: Bow (Curved wooden bow with string)
        loadOrPaint(pixelData, 66, "bow.png", (x, y, rand) -> {
            if (x == 3 && y >= 2 && y <= 13) return rgba(230, 230, 230, 255); // String
            float distFromArc = Math.abs((float)Math.sqrt((x - 3) * (x - 3) + (y - 7.5f) * (y - 7.5f)) - 5.5f);
            if (distFromArc < 1.0f && x >= 3) {
                return rgba(130, 85, 40, 255); // Wood arc
            }
            return rgba(0, 0, 0, 0);
        });

        // 67: Arrow (Feather fletch, stick, tip)
        loadOrPaint(pixelData, 67, "arrow.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f) {
                if (x < 4) return rgba(220, 220, 220, 255); // Fletching
                if (x > 12) return rgba(120, 120, 120, 255); // Tip
                return rgba(140, 95, 45, 255); // Shaft
            }
            return rgba(0, 0, 0, 0);
        });

        // 68: Flint and Steel
        loadOrPaint(pixelData, 68, "flint_and_steel.png", (x, y, rand) -> {
            if (x >= 7 && x <= 13 && y >= 3 && y <= 12 && (x == 7 || x == 13 || y == 3 || y == 12)) {
                return rgba(210, 210, 220, 255); // Steel arc
            }
            if (x >= 4 && x <= 7 && y >= 8 && y <= 12) {
                return rgba(35, 35, 40, 255); // Flint
            }
            return rgba(0, 0, 0, 0);
        });

        // 69: Lava (Animated red-orange-yellow molten surface)
        loadOrPaint(pixelData, 69, "lava.png", (x, y, rand) -> {
            int r = 220 + rand.nextInt(35);
            int g = 60 + rand.nextInt(120);
            int b = (g > 140) ? 20 + rand.nextInt(30) : 5;
            return rgba(r, g, b, 255);
        });

        // 70: Nether Quartz Ore (Netherrack base with white/cream quartz crystal streaks)
        loadOrPaint(pixelData, 70, "nether_quartz_ore.png", (x, y, rand) -> {
            boolean quartz = (x + y >= 9 && x + y <= 12 && Math.abs(x - y) <= 4) ||
                             (x >= 10 && x <= 13 && y >= 3 && y <= 6) ||
                             (x >= 3 && x <= 6 && y >= 11 && y <= 14);
            if (quartz) {
                int q = 220 + rand.nextInt(35);
                return rgba(q, (int)(q * 0.94f), (int)(q * 0.88f), 255);
            }
            int r = 110 + rand.nextInt(45);
            int g = (int)(r * 0.22f) + rand.nextInt(10);
            int b = (int)(r * 0.22f);
            return rgba(r, g, b, 255);
        });

        // 71: Glowstone (Warm golden glowing crystalline cluster)
        loadOrPaint(pixelData, 71, "glowstone.png", (x, y, rand) -> {
            int border = ((x % 4 == 0) || (y % 4 == 0)) ? 1 : 0;
            int r = 210 + rand.nextInt(45) - border * 30;
            int g = 160 + rand.nextInt(40) - border * 30;
            int b = 60 + rand.nextInt(30);
            return rgba(r, g, b, 255);
        });

        // 72: Soul Sand (Dark wavy brown with ghostly face accents)
        loadOrPaint(pixelData, 72, "soul_sand.png", (x, y, rand) -> {
            boolean eye = (x == 4 && y == 5) || (x == 8 && y == 5) || (x == 11 && y == 11) || (x == 13 && y == 11);
            if (eye) return rgba(35, 20, 15, 255);
            int base = 70 + rand.nextInt(25);
            return rgba((int)(base * 1.05f), (int)(base * 0.72f), (int)(base * 0.52f), 255);
        });

        // 73: Basalt Side (Dark gray vertical column streaks)
        loadOrPaint(pixelData, 73, "basalt_side.png", (x, y, rand) -> {
            int col = (x % 3 == 0) ? 45 : (58 + rand.nextInt(15));
            return rgba(col, col, (int)(col * 1.05f), 255);
        });

        // 74: Basalt Top (Dark circular basalt column top)
        loadOrPaint(pixelData, 74, "basalt_top.png", (x, y, rand) -> {
            float dx = x - 7.5f;
            float dy = y - 7.5f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            int ring = (int)(d * 1.5f) % 2;
            int c = 40 + ring * 25 + rand.nextInt(12);
            return rgba(c, c, (int)(c * 1.05f), 255);
        });

        // 75: Mob Spawner (Dark iron cage with fiery glowing orange interior)
        loadOrPaint(pixelData, 75, "spawner.png", (x, y, rand) -> {
            boolean cageBar = (x == 0 || x == 15 || y == 0 || y == 15 ||
                               x == 5 || x == 10 || y == 5 || y == 10 ||
                               ((x + y) % 5 == 0));
            if (cageBar) {
                int iron = 35 + rand.nextInt(25);
                return rgba(iron, iron, (int)(iron * 1.15f), 255);
            }
            // Inner flaming core
            float dx = x - 7.5f;
            float dy = y - 7.5f;
            if (dx * dx + dy * dy <= 16) {
                int r = 240 + rand.nextInt(15);
                int g = 120 + rand.nextInt(70);
                return rgba(r, g, 20, 255);
            }
            int bg = 15 + rand.nextInt(15);
            return rgba(bg, bg, bg, 255);
        });

        // 76: Water (Animated cyan-blue fluid with rippling wave highlights)
        loadOrPaint(pixelData, 76, "water.png", (x, y, rand) -> {
            int wave = (x * 3 + y * 7 + (x ^ y)) % 11;
            int b = 210 + rand.nextInt(40);
            int r = 35 + wave * 2 + rand.nextInt(15);
            int g = 90 + wave * 4 + rand.nextInt(25);
            return rgba(r, g, b, 215);
        });

        // 77: Sandstone Top (Smooth warm golden sandstone)
        loadOrPaint(pixelData, 77, "sandstone_top.png", (x, y, rand) -> {
            int base = 215 + rand.nextInt(20);
            return rgba(base, (int) (base * 0.94f), (int) (base * 0.72f), 255);
        });

        // 78: Sandstone Side (Horizontal layered sandstone strata)
        loadOrPaint(pixelData, 78, "sandstone_side.png", (x, y, rand) -> {
            int band = (y % 4 == 0) ? -20 : ((y % 8 == 3) ? 15 : 0);
            int base = 205 + band + rand.nextInt(18);
            return rgba(base, (int) (base * 0.93f), (int) (base * 0.70f), 255);
        });

        // 79: Sandstone Bottom (Rough textured sandstone)
        loadOrPaint(pixelData, 79, "sandstone_bottom.png", (x, y, rand) -> {
            int base = 190 + rand.nextInt(25);
            return rgba(base, (int) (base * 0.92f), (int) (base * 0.68f), 255);
        });

        // 80: Snow Block (Crisp pure white with soft pastel-blue flecks)
        loadOrPaint(pixelData, 80, "snow.png", (x, y, rand) -> {
            int v = 240 + rand.nextInt(16);
            return rgba(v - rand.nextInt(6), v - rand.nextInt(4), v, 255);
        });

        // 81: Gravel (Mottled gray, slate and beige pebble pattern)
        loadOrPaint(pixelData, 81, "gravel.png", (x, y, rand) -> {
            int v = 115 + rand.nextInt(35);
            int r = (int) (v * 1.05f) + rand.nextInt(10);
            int g = v + rand.nextInt(8);
            int b = (int) (v * 0.95f) + rand.nextInt(8);
            return rgba(r, g, b, 255);
        });

        // 82: Cactus Top / Bottom (Dark green perimeter with lighter fleshy interior)
        loadOrPaint(pixelData, 82, "cactus_top.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            if (border) return rgba(25, 75, 25, 255);
            int g = 110 + rand.nextInt(30);
            return rgba((int) (g * 0.4f), g, (int) (g * 0.25f), 255);
        });

        // 83: Cactus Side (Vertical ribbed dark green stripes and spine dots)
        loadOrPaint(pixelData, 83, "cactus_side.png", (x, y, rand) -> {
            boolean stripe = (x % 3 == 0);
            boolean spine = (x % 3 == 1 && y % 4 == 2);
            if (spine) return rgba(240, 240, 240, 255);
            int g = stripe ? (85 + rand.nextInt(20)) : (120 + rand.nextInt(30));
            return rgba((int) (g * 0.35f), g, (int) (g * 0.25f), 255);
        });

        // 84: Coal Ore
        loadOrPaint(pixelData, 84, "coal_ore.png", (x, y, rand) -> {
            boolean coal = (x >= 3 && x <= 6 && y >= 3 && y <= 6) ||
                           (x >= 9 && x <= 13 && y >= 7 && y <= 11) ||
                           (x >= 4 && x <= 8 && y >= 11 && y <= 14);
            if (coal) {
                int c = 20 + rand.nextInt(25);
                return rgba(c, c, c, 255);
            }
            int v = 110 + rand.nextInt(35);
            return rgba(v, v, v, 255);
        });

        // 85: Coal Item
        loadOrPaint(pixelData, 85, "coal.png", (x, y, rand) -> {
            float dx = x - 7.5f, dy = y - 7.5f;
            if (dx * dx + dy * dy <= 22) {
                int c = 18 + rand.nextInt(28);
                if (x + y <= 11) c += 35; // highlight
                return rgba(c, c, c, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 86: Iron Ore
        loadOrPaint(pixelData, 86, "iron_ore.png", (x, y, rand) -> {
            boolean iron = (x >= 4 && x <= 7 && y >= 3 && y <= 6) ||
                           (x >= 8 && x <= 12 && y >= 8 && y <= 12) ||
                           (x >= 3 && x <= 6 && y >= 10 && y <= 13);
            if (iron) {
                int b = 175 + rand.nextInt(30);
                return rgba(b, (int)(b * 0.88f), (int)(b * 0.76f), 255);
            }
            int v = 110 + rand.nextInt(35);
            return rgba(v, v, v, 255);
        });

        // 87: Iron Ingot
        loadOrPaint(pixelData, 87, "iron_ingot.png", (x, y, rand) -> {
            if (x >= 3 && x <= 12 && y >= 5 && y <= 10) {
                int base = (y == 5 || x == 3) ? 235 : ((y == 10 || x == 12) ? 140 : 195);
                base += rand.nextInt(15);
                return rgba(base, base, (int)(base * 1.05f), 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 88: Gold Ore
        loadOrPaint(pixelData, 88, "gold_ore.png", (x, y, rand) -> {
            boolean gold = (x >= 4 && x <= 7 && y >= 4 && y <= 7) ||
                           (x >= 9 && x <= 12 && y >= 8 && y <= 11) ||
                           (x >= 3 && x <= 6 && y >= 11 && y <= 13);
            if (gold) {
                int r = 240 + rand.nextInt(15);
                int g = 190 + rand.nextInt(35);
                return rgba(r, g, 40, 255);
            }
            int v = 110 + rand.nextInt(35);
            return rgba(v, v, v, 255);
        });

        // 89: Gold Ingot
        loadOrPaint(pixelData, 89, "gold_ingot.png", (x, y, rand) -> {
            if (x >= 3 && x <= 12 && y >= 5 && y <= 10) {
                int r = (y == 5 || x == 3) ? 255 : ((y == 10 || x == 12) ? 180 : 230);
                int g = (int)(r * 0.82f);
                return rgba(r, g, 30, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 90: Diamond Ore
        loadOrPaint(pixelData, 90, "diamond_ore.png", (x, y, rand) -> {
            boolean dia = (x >= 4 && x <= 7 && y >= 3 && y <= 6) ||
                          (x >= 9 && x <= 13 && y >= 8 && y <= 12) ||
                          (x >= 3 && x <= 6 && y >= 10 && y <= 13);
            if (dia) {
                int g = 210 + rand.nextInt(40);
                return rgba((int)(g * 0.40f), g, 255, 255);
            }
            int v = 110 + rand.nextInt(35);
            return rgba(v, v, v, 255);
        });

        // 91: Diamond Gem Item
        loadOrPaint(pixelData, 91, "diamond.png", (x, y, rand) -> {
            float dx = Math.abs(x - 7.5f), dy = Math.abs(y - 7.5f);
            if (dx + dy <= 5.5f && y >= 4 && y <= 12) {
                int g = (y < 7) ? 245 : 190 + rand.nextInt(35);
                int r = (y < 7) ? 120 : (int)(g * 0.35f);
                return rgba(r, g, 255, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 92: Torch (Stick + fire flame)
        loadOrPaint(pixelData, 92, "torch.png", (x, y, rand) -> {
            if (x >= 7 && x <= 8 && y >= 5 && y <= 14) {
                return rgba(130, 90, 50, 255); // Wood stick
            }
            if (x >= 6 && x <= 9 && y >= 1 && y <= 5) {
                if (x >= 7 && x <= 8 && y >= 2 && y <= 4) return rgba(255, 255, 200, 255); // Core flame
                return rgba(255, 140 + rand.nextInt(50), 20, 255); // Outer flame
            }
            return rgba(0, 0, 0, 0);
        });

        // 93: Iron Pickaxe
        loadOrPaint(pixelData, 93, "iron_pickaxe.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f && x >= 3 && x <= 10) return rgba(130, 90, 50, 255); // Handle
            if (x >= 9 && y >= 1 && (x + y <= 16) && (x + y >= 12)) return rgba(220, 220, 235, 255); // Iron head
            return rgba(0, 0, 0, 0);
        });

        // 94: Iron Sword
        loadOrPaint(pixelData, 94, "iron_sword.png", (x, y, rand) -> {
            if (x == y && x >= 5 && x <= 14) return rgba(225, 225, 240, 255); // Blade
            if (x == y + 1 || x == y - 1) {
                if (x >= 5 && x <= 13) return rgba(195, 195, 215, 255);
                if (x == 4) return rgba(100, 100, 110, 255); // Crossguard
            }
            if (x <= 3 && y <= 3 && Math.abs(x - y) <= 0.5f) return rgba(120, 80, 40, 255); // Hilt
            return rgba(0, 0, 0, 0);
        });

        // 95: Iron Axe
        loadOrPaint(pixelData, 95, "iron_axe.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f && x >= 2 && x <= 11) return rgba(130, 90, 50, 255);
            if (x >= 8 && x <= 13 && y >= 2 && y <= 8) return rgba(220, 220, 235, 255);
            return rgba(0, 0, 0, 0);
        });

        // 96: Iron Shovel
        loadOrPaint(pixelData, 96, "iron_shovel.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f && x >= 2 && x <= 10) return rgba(130, 90, 50, 255);
            if (x >= 10 && y >= 10 && x <= 13 && y <= 13) return rgba(225, 225, 240, 255);
            return rgba(0, 0, 0, 0);
        });

        // 97: Diamond Pickaxe
        loadOrPaint(pixelData, 97, "diamond_pickaxe.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f && x >= 3 && x <= 10) return rgba(130, 90, 50, 255);
            if (x >= 9 && y >= 1 && (x + y <= 16) && (x + y >= 12)) return rgba(75, 225, 235, 255); // Cyan head
            return rgba(0, 0, 0, 0);
        });

        // 98: Diamond Sword
        loadOrPaint(pixelData, 98, "diamond_sword.png", (x, y, rand) -> {
            if (x == y && x >= 5 && x <= 14) return rgba(95, 235, 245, 255); // Diamond blade
            if (x == y + 1 || x == y - 1) {
                if (x >= 5 && x <= 13) return rgba(50, 190, 205, 255);
                if (x == 4) return rgba(35, 140, 150, 255);
            }
            if (x <= 3 && y <= 3 && Math.abs(x - y) <= 0.5f) return rgba(120, 80, 40, 255);
            return rgba(0, 0, 0, 0);
        });

        // 99: Diamond Axe
        loadOrPaint(pixelData, 99, "diamond_axe.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f && x >= 2 && x <= 11) return rgba(130, 90, 50, 255);
            if (x >= 8 && x <= 13 && y >= 2 && y <= 8) return rgba(75, 225, 235, 255);
            return rgba(0, 0, 0, 0);
        });

        // 100: Diamond Shovel
        loadOrPaint(pixelData, 100, "diamond_shovel.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 0.5f && x >= 2 && x <= 10) return rgba(130, 90, 50, 255);
            if (x >= 10 && y >= 10 && x <= 13 && y <= 13) return rgba(75, 225, 235, 255);
            return rgba(0, 0, 0, 0);
        });

        // 101: Raw Porkchop
        loadOrPaint(pixelData, 101, "porkchop.png", (x, y, rand) -> {
            float dx = x - 7.5f, dy = y - 7.5f;
            if (dx * dx + dy * dy <= 24) {
                if (x <= 5 || y <= 4) return rgba(240, 220, 220, 255); // White fat
                return rgba(225, 120 + rand.nextInt(20), 130 + rand.nextInt(20), 255); // Pink meat
            }
            return rgba(0, 0, 0, 0);
        });

        // 102: Cooked Porkchop
        loadOrPaint(pixelData, 102, "cooked_porkchop.png", (x, y, rand) -> {
            float dx = x - 7.5f, dy = y - 7.5f;
            if (dx * dx + dy * dy <= 24) {
                int b = (x % 3 == 0) ? 90 : 140 + rand.nextInt(25);
                return rgba((int)(b * 1.25f), (int)(b * 0.75f), (int)(b * 0.40f), 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 103: Raw Beef
        loadOrPaint(pixelData, 103, "beef.png", (x, y, rand) -> {
            float dx = x - 7.5f, dy = y - 7.5f;
            if (dx * dx + dy * dy <= 24) {
                if (x >= 9 && y <= 6) return rgba(230, 230, 220, 255); // Bone
                return rgba(170 + rand.nextInt(25), 35 + rand.nextInt(15), 35 + rand.nextInt(15), 255); // Deep red
            }
            return rgba(0, 0, 0, 0);
        });

        // 104: Cooked Beef (Steak)
        loadOrPaint(pixelData, 104, "cooked_beef.png", (x, y, rand) -> {
            float dx = x - 7.5f, dy = y - 7.5f;
            if (dx * dx + dy * dy <= 24) {
                int b = 95 + rand.nextInt(25);
                return rgba((int)(b * 1.1f), (int)(b * 0.65f), (int)(b * 0.35f), 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 105: Raw Chicken
        loadOrPaint(pixelData, 105, "chicken.png", (x, y, rand) -> {
            if (x >= 4 && x <= 11 && y >= 4 && y <= 11) return rgba(235, 175, 160, 255);
            if (x >= 2 && x <= 4 && y >= 2 && y <= 4) return rgba(240, 240, 235, 255); // Bone
            return rgba(0, 0, 0, 0);
        });

        // 106: Cooked Chicken
        loadOrPaint(pixelData, 106, "cooked_chicken.png", (x, y, rand) -> {
            if (x >= 4 && x <= 11 && y >= 4 && y <= 11) return rgba(190, 110, 45, 255);
            if (x >= 2 && x <= 4 && y >= 2 && y <= 4) return rgba(240, 240, 235, 255);
            return rgba(0, 0, 0, 0);
        });

        // 107: Apple
        loadOrPaint(pixelData, 107, "apple.png", (x, y, rand) -> {
            float dx = x - 7.5f, dy = y - 8.5f;
            if (dx * dx + dy * dy <= 20) {
                if (x == 6 && y == 7) return rgba(255, 180, 180, 255); // Shine
                return rgba(215 + rand.nextInt(30), 20, 25, 255);
            }
            if (x == 7 && y >= 4 && y <= 5) return rgba(60, 130, 30, 255); // Stem
            return rgba(0, 0, 0, 0);
        });

        // 108: Bread
        loadOrPaint(pixelData, 108, "bread.png", (x, y, rand) -> {
            if (x >= 3 && x <= 12 && y >= 5 && y <= 10) {
                if (y == 5 || (x % 3 == 0 && y == 6)) return rgba(150, 85, 30, 255); // Crust
                return rgba(205, 150, 60, 255); // Bread loaf
            }
            return rgba(0, 0, 0, 0);
        });

        // 109: Leather
        loadOrPaint(pixelData, 109, "leather.png", (x, y, rand) -> {
            float dx = Math.abs(x - 7.5f), dy = Math.abs(y - 7.5f);
            if (dx <= 5 && dy <= 5 && (dx + dy <= 8)) {
                return rgba(155 + rand.nextInt(20), 85 + rand.nextInt(15), 45, 255);
            }
            return rgba(0, 0, 0, 0);
        });

        // 110: Feather
        loadOrPaint(pixelData, 110, "feather.png", (x, y, rand) -> {
            if (Math.abs(x - y) <= 1.0f && x >= 3 && x <= 12) {
                if (x == y) return rgba(180, 180, 180, 255); // Shaft
                return rgba(245, 245, 250, 255); // Feather vane
            }
            return rgba(0, 0, 0, 0);
        });

        // 111: Chest Side / Back (Oak wood panels with dark perimeter frame and lid seam)
        loadOrPaint(pixelData, 111, "chest_side.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            if (border) return rgba(55, 35, 18, 255);
            boolean seam = (y == 5);
            if (seam) return rgba(45, 28, 14, 255);
            int base = 145 + rand.nextInt(18);
            return rgba((int)(base * 1.15f), (int)(base * 0.75f), (int)(base * 0.35f), 255);
        });

        // 112: Chest Top (Oak wood lid with dark perimeter frame)
        loadOrPaint(pixelData, 112, "chest_top.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            if (border) return rgba(55, 35, 18, 255);
            int base = 145 + rand.nextInt(18);
            return rgba((int)(base * 1.15f), (int)(base * 0.75f), (int)(base * 0.35f), 255);
        });

        // 113: Chest Bottom (Oak wood bottom with dark perimeter frame)
        loadOrPaint(pixelData, 113, "chest_bottom.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            if (border) return rgba(55, 35, 18, 255);
            int base = 130 + rand.nextInt(18);
            return rgba((int)(base * 1.15f), (int)(base * 0.75f), (int)(base * 0.35f), 255);
        });

        // 114: Furnace Top / Bottom (Smooth stone with double frame bevel)
        loadOrPaint(pixelData, 114, "furnace_top.png", (x, y, rand) -> {
            boolean outer = (x == 0 || x == 15 || y == 0 || y == 15);
            if (outer) return rgba(60, 60, 60, 255);
            boolean inner = (x == 1 || x == 14 || y == 1 || y == 14);
            if (inner) return rgba(85, 85, 85, 255);
            int v = 120 + rand.nextInt(20);
            return rgba(v, v, v, 255);
        });

        // 115: Furnace Side / Back (Stone panel with dark outer frame)
        loadOrPaint(pixelData, 115, "furnace_side.png", (x, y, rand) -> {
            boolean outer = (x == 0 || x == 15 || y == 0 || y == 15);
            if (outer) return rgba(65, 65, 65, 255);
            int v = 118 + rand.nextInt(22);
            return rgba(v, v, v, 255);
        });

        // 116: Furnace Front Lit (Burning fire in opening)
        loadOrPaint(pixelData, 116, "furnace_front_on.png", (x, y, rand) -> {
            boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
            if (border) return rgba(65, 65, 65, 255);
            if (y >= 2 && y <= 4 && x >= 3 && x <= 12) {
                if (y == 3) return rgba(40, 40, 40, 255);
            }
            if (y >= 7 && y <= 13 && x >= 3 && x <= 12) {
                boolean arch = (y == 7 && (x == 3 || x == 12));
                if (!arch) {
                    if (y >= 10) {
                        return rgba(255, 130 + rand.nextInt(50), 25, 255);
                    } else {
                        return rgba(220, 70 + rand.nextInt(40), 10, 255);
                    }
                }
            }
            int v = 115 + rand.nextInt(25);
            return rgba(v, v, v, 255);
        });

        // --- 117-128: Armor Items (Leather, Iron, Diamond) ---
        int[] leatherBase = rgba(150, 90, 50, 255);
        int[] leatherDark = rgba(95, 50, 25, 255);
        int[] leatherLight = rgba(190, 120, 70, 255);

        int[] ironBase = rgba(195, 195, 200, 255);
        int[] ironDark = rgba(130, 130, 135, 255);
        int[] ironLight = rgba(240, 240, 248, 255);

        int[] diamondBase = rgba(70, 220, 210, 255);
        int[] diamondDark = rgba(35, 150, 145, 255);
        int[] diamondLight = rgba(180, 255, 248, 255);

        // 117: Leather Helmet
        loadOrPaint(pixelData, 117, "leather_helmet.png", (x, y, rand) -> paintHelmet(x, y, rand, leatherBase, leatherDark, leatherLight));
        // 118: Leather Chestplate
        loadOrPaint(pixelData, 118, "leather_chestplate.png", (x, y, rand) -> paintChestplate(x, y, rand, leatherBase, leatherDark, leatherLight));
        // 119: Leather Leggings
        loadOrPaint(pixelData, 119, "leather_leggings.png", (x, y, rand) -> paintLeggings(x, y, rand, leatherBase, leatherDark, leatherLight));
        // 120: Leather Boots
        loadOrPaint(pixelData, 120, "leather_boots.png", (x, y, rand) -> paintBoots(x, y, rand, leatherBase, leatherDark, leatherLight));

        // 121: Iron Helmet
        loadOrPaint(pixelData, 121, "iron_helmet.png", (x, y, rand) -> paintHelmet(x, y, rand, ironBase, ironDark, ironLight));
        // 122: Iron Chestplate
        loadOrPaint(pixelData, 122, "iron_chestplate.png", (x, y, rand) -> paintChestplate(x, y, rand, ironBase, ironDark, ironLight));
        // 123: Iron Leggings
        loadOrPaint(pixelData, 123, "iron_leggings.png", (x, y, rand) -> paintLeggings(x, y, rand, ironBase, ironDark, ironLight));
        // 124: Iron Boots
        loadOrPaint(pixelData, 124, "iron_boots.png", (x, y, rand) -> paintBoots(x, y, rand, ironBase, ironDark, ironLight));

        // 125: Diamond Helmet
        loadOrPaint(pixelData, 125, "diamond_helmet.png", (x, y, rand) -> paintHelmet(x, y, rand, diamondBase, diamondDark, diamondLight));
        // 126: Diamond Chestplate
        loadOrPaint(pixelData, 126, "diamond_chestplate.png", (x, y, rand) -> paintChestplate(x, y, rand, diamondBase, diamondDark, diamondLight));
        // 127: Diamond Leggings
        loadOrPaint(pixelData, 127, "diamond_leggings.png", (x, y, rand) -> paintLeggings(x, y, rand, diamondBase, diamondDark, diamondLight));
        // 128: Diamond Boots
        loadOrPaint(pixelData, 128, "diamond_boots.png", (x, y, rand) -> paintBoots(x, y, rand, diamondBase, diamondDark, diamondLight));

        for (int y = 0; y < ATLAS_SIZE; y++) {
            buffer.put(pixelData[y]);
        }
        buffer.flip();
        return buffer;
    }

    private static int[] paintHelmet(int x, int y, Random rand, int[] baseCol, int[] darkCol, int[] lightCol) {
        boolean inside = (y == 3 && x >= 4 && x <= 11) ||
                         (y >= 4 && y <= 6 && x >= 3 && x <= 12) ||
                         (y >= 7 && y <= 9 && (x >= 3 && x <= 5 || x >= 10 && x <= 12 || (y == 7 && (x == 7 || x == 8)))) ||
                         (y == 10 && (x >= 3 && x <= 4 || x >= 11 && x <= 12));
        if (!inside) return rgba(0, 0, 0, 0);
        boolean border = (y == 3) || (x == 3 || x == 12) || (y == 10) ||
                         (y == 6 && x >= 6 && x <= 9) || (y == 7 && (x == 6 || x == 9)) || (y >= 8 && (x == 5 || x == 10));
        if (border) return darkCol;
        if (x <= 5 || y <= 4) return lightCol;
        return baseCol;
    }

    private static int[] paintChestplate(int x, int y, Random rand, int[] baseCol, int[] darkCol, int[] lightCol) {
        boolean inside = (y == 3 && ((x >= 3 && x <= 5) || (x >= 10 && x <= 12))) ||
                         (y >= 4 && y <= 5 && ((x >= 2 && x <= 6) || (x >= 9 && x <= 13))) ||
                         (y >= 6 && y <= 11 && x >= 3 && x <= 12) ||
                         (y >= 12 && y <= 13 && x >= 4 && x <= 11);
        if (!inside) return rgba(0, 0, 0, 0);
        boolean border = (y == 3) || (x == 2 || x == 13) || (y == 13) ||
                         (y <= 5 && (x == 6 || x == 9)) || (y == 5 && (x == 7 || x == 8));
        if (border) return darkCol;
        if (x <= 5 || (y <= 5 && x <= 4)) return lightCol;
        return baseCol;
    }

    private static int[] paintLeggings(int x, int y, Random rand, int[] baseCol, int[] darkCol, int[] lightCol) {
        boolean inside = (y >= 3 && y <= 6 && x >= 3 && x <= 12) ||
                         (y >= 7 && y <= 13 && ((x >= 3 && x <= 6) || (x >= 9 && x <= 12)));
        if (!inside) return rgba(0, 0, 0, 0);
        boolean border = (y == 3 || y == 13 || x == 3 || x == 12 || (y >= 6 && (x == 7 || x == 8)) || (y >= 7 && (x == 6 || x == 9)));
        if (border) return darkCol;
        if (x <= 5) return lightCol;
        return baseCol;
    }

    private static int[] paintBoots(int x, int y, Random rand, int[] baseCol, int[] darkCol, int[] lightCol) {
        boolean inside = (y >= 7 && y <= 11 && ((x >= 3 && x <= 6) || (x >= 9 && x <= 12))) ||
                         (y >= 12 && y <= 13 && ((x >= 2 && x <= 6) || (x >= 9 && x <= 13)));
        if (!inside) return rgba(0, 0, 0, 0);
        boolean border = (y == 7 || y == 13 || x == 2 || x == 13 || (y <= 11 && (x == 3 || x == 12)) || x == 6 || x == 9);
        if (border) return darkCol;
        if (x <= 4 || (y == 8 && x <= 5)) return lightCol;
        return baseCol;
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

                            // Fill transparent pixels on end portal frame side with top frame color
                            if (tileIndex == 58 && a == 0) {
                                r = 90; g = 150; b = 95; a = 255;
                            }

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
