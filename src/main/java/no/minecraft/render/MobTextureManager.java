package no.minecraft.render;

import no.minecraft.entity.MobType;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

public class MobTextureManager {
    private static MobTextureManager instance;

    private final Map<MobType, Integer> textures = new EnumMap<>(MobType.class);
    private int crystalTexture = 0;

    public MobTextureManager() {
        initTextures();
    }

    public static MobTextureManager getInstance() {
        if (instance == null) {
            instance = new MobTextureManager();
        }
        return instance;
    }

    private void initTextures() {
        for (MobType type : MobType.values()) {
            if (type == MobType.END_CRYSTAL) {
                crystalTexture = loadTexture("end_crystal.png", 64, 32, this::generateEndCrystalFallback);
                textures.put(type, crystalTexture);
            } else {
                int texId = loadMobTexture(type);
                textures.put(type, texId);
            }
        }
    }

    public void bindTexture(MobType type) {
        Integer id = textures.get(type);
        if (id != null && id != 0) {
            glBindTexture(GL_TEXTURE_2D, id);
        } else {
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private int loadMobTexture(MobType type) {
        return switch (type) {
            case ZOMBIE -> loadTexture("zombie.png", 64, 64, this::generateZombieFallback);
            case SKELETON -> loadTexture("skeleton.png", 64, 32, this::generateSkeletonFallback);
            case CREEPER -> loadTexture("creeper.png", 64, 32, this::generateCreeperFallback);
            case SPIDER -> loadTexture("spider.png", 64, 32, this::generateSpiderFallback);
            case PIG -> loadTexture("pig.png", 64, 32, this::generatePigFallback);
            case COW -> loadTexture("cow.png", 64, 32, this::generateCowFallback);
            case SHEEP -> loadTexture("sheep.png", 64, 32, this::generateSheepFallback);
            case CHICKEN -> loadTexture("chicken.png", 64, 32, this::generateChickenFallback);
            case ENDERMAN -> loadTexture("enderman.png", 64, 32, this::generateEndermanFallback);
            case BLAZE -> loadTexture("blaze.png", 64, 32, this::generateBlazeFallback);
            case ENDER_DRAGON -> loadTexture("dragon.png", 256, 256, this::generateDragonFallback);
            case END_CRYSTAL -> crystalTexture;
        };
    }

    @FunctionalInterface
    private interface TextureGenerator {
        BufferedImage generate(int width, int height);
    }

    private int loadTexture(String filename, int defaultW, int defaultH, TextureGenerator fallback) {
        BufferedImage img = null;
        try {
            InputStream is = getClass().getResourceAsStream("/assets/textures/entity/" + filename);
            if (is != null) {
                img = ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }

        if (img == null) {
            img = fallback.generate(defaultW, defaultH);
        }

        int width = img.getWidth();
        int height = img.getHeight();

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));         // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);

        return texId;
    }

    // --- PROCEDURAL TEXTURE FALLBACKS (Authentic Minecraft 1.16.1 UV patterns) ---

    private BufferedImage generateZombieFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Random r = new Random(1337);

        // Skin (Green tones)
        Color skin = new Color(55, 115, 50);
        Color skinDark = new Color(42, 90, 38);
        Color skinLight = new Color(68, 138, 62);

        // Clothes: Cyan shirt, Dark blue pants
        Color shirt = new Color(0, 145, 160);
        Color shirtDark = new Color(0, 115, 130);
        Color pants = new Color(40, 45, 115);
        Color pantsDark = new Color(30, 35, 90);

        // 1. Head (0, 0, 32, 16)
        fillNoise(img, 0, 0, 32, 16, skin, skinDark, skinLight, r);
        // Eyes (front face at u:8..16, v:8..16)
        img.setRGB(10, 12, 0xFF1B1B1B);
        img.setRGB(13, 12, 0xFF1B1B1B);
        img.setRGB(10, 13, 0xFF2A5025);
        img.setRGB(13, 13, 0xFF2A5025);
        // Nose & mouth
        img.setRGB(11, 13, 0xFF244820);
        img.setRGB(12, 13, 0xFF244820);
        img.setRGB(11, 14, 0xFF183215);
        img.setRGB(12, 14, 0xFF183215);

        // 2. Body / Torso (16, 16, 24, 16) -> Cyan shirt
        fillNoise(img, 16, 16, 24, 16, shirt, shirtDark, shirt, r);

        // 3. Right Arm (40, 16, 16, 16) & Left Arm (32, 48, 16, 16)
        fillNoise(img, 40, 16, 16, 16, skin, skinDark, skinLight, r);
        fillNoise(img, 40, 16, 16, 4, shirt, shirtDark, shirt, r); // Sleeve
        fillNoise(img, 32, 48, 16, 16, skin, skinDark, skinLight, r);
        fillNoise(img, 32, 48, 16, 4, shirt, shirtDark, shirt, r); // Sleeve

        // 4. Right Leg (0, 16, 16, 16) & Left Leg (16, 48, 16, 16)
        fillNoise(img, 0, 16, 16, 16, pants, pantsDark, pants, r);
        fillNoise(img, 16, 48, 16, 16, pants, pantsDark, pants, r);

        g.dispose();
        return img;
    }

    private BufferedImage generateSkeletonFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1338);

        Color bone = new Color(195, 195, 195);
        Color boneDark = new Color(155, 155, 155);
        Color boneLight = new Color(225, 225, 225);

        // 1. Skull (0, 0, 32, 16)
        fillNoise(img, 0, 0, 32, 16, bone, boneDark, boneLight, r);
        // Eye sockets (front face u:8..16, v:8..16)
        fillSolid(img, 9, 10, 2, 2, 0xFF222222);
        fillSolid(img, 13, 10, 2, 2, 0xFF222222);
        // Nose socket & teeth
        img.setRGB(11, 12, 0xFF333333);
        img.setRGB(12, 12, 0xFF333333);
        for (int x = 9; x <= 14; x += 2) {
            img.setRGB(x, 14, 0xFF222222);
        }

        // 2. Ribcage / Body (16, 16, 24, 16)
        fillNoise(img, 16, 16, 24, 16, bone, boneDark, boneLight, r);
        // Rib dark slots
        for (int y = 18; y <= 28; y += 2) {
            for (int x = 20; x <= 27; x++) {
                if (x == 23 || x == 24) continue;
                img.setRGB(x, y, 0xFF333333);
            }
        }

        // 3. Arms & Legs (40, 16, 16, 16) & (0, 16, 16, 16)
        fillNoise(img, 40, 16, 16, 16, bone, boneDark, boneLight, r);
        fillNoise(img, 0, 16, 16, 16, bone, boneDark, boneLight, r);

        return img;
    }

    private BufferedImage generateCreeperFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1339);

        Color cGreen = new Color(50, 180, 50);
        Color cDark = new Color(25, 110, 25);
        Color cLight = new Color(85, 215, 85);

        // Entire canvas green camo noise
        fillNoise(img, 0, 0, w, h, cGreen, cDark, cLight, r);

        // Creeper Face on Front of Head (u:8..16, v:8..16)
        int black = 0xFF0D0D0D;
        // Eyes
        fillSolid(img, 9, 10, 2, 2, black);
        fillSolid(img, 13, 10, 2, 2, black);
        // Nose
        fillSolid(img, 11, 12, 2, 2, black);
        // Mouth top
        fillSolid(img, 10, 13, 4, 1, black);
        // Mouth frown corners
        fillSolid(img, 9, 14, 2, 2, black);
        fillSolid(img, 13, 14, 2, 2, black);

        return img;
    }

    private BufferedImage generateSpiderFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1340);

        Color sDark = new Color(30, 26, 26);
        Color sBrown = new Color(48, 38, 35);
        Color sGray = new Color(60, 52, 50);

        fillNoise(img, 0, 0, w, h, sDark, sBrown, sGray, r);

        // Red glowing eyes on Head (Head front is at u:32+4..32+12 = 36..44, v:4+4..4+12 = 8..16)
        int redEye = 0xFFE01818;
        int brightEye = 0xFFFF4040;
        img.setRGB(37, 12, redEye);
        img.setRGB(38, 12, brightEye);
        img.setRGB(41, 12, brightEye);
        img.setRGB(42, 12, redEye);
        img.setRGB(39, 14, redEye);
        img.setRGB(40, 14, redEye);

        return img;
    }

    private BufferedImage generatePigFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1341);

        Color pPink = new Color(240, 160, 160);
        Color pDark = new Color(220, 135, 135);
        Color pLight = new Color(250, 185, 185);

        fillNoise(img, 0, 0, w, h, pPink, pDark, pLight, r);

        // Pig Head (front face u:8..16, v:8..16)
        // Eyes
        img.setRGB(8, 10, 0xFFFFFFFF);
        img.setRGB(9, 10, 0xFF222222);
        img.setRGB(14, 10, 0xFF222222);
        img.setRGB(15, 10, 0xFFFFFFFF);

        // Snout (u:16..22, v:16..20)
        Color snoutCol = new Color(255, 140, 140);
        fillNoise(img, 16, 16, 8, 8, snoutCol, pDark, pLight, r);
        // Nostrils
        img.setRGB(18, 18, 0xFF703030);
        img.setRGB(20, 18, 0xFF703030);

        return img;
    }

    private BufferedImage generateCowFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1342);

        Color cBrown = new Color(90, 60, 40);
        Color cDark = new Color(65, 42, 28);
        Color cWhite = new Color(230, 230, 230);

        fillNoise(img, 0, 0, w, h, cBrown, cDark, cBrown, r);

        // White patches on body & legs
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((x ^ (y * 3)) % 7 == 0) || ((x + y * 2) % 11 == 0)) {
                    img.setRGB(x, y, cWhite.getRGB());
                }
            }
        }

        // Cow Face (u:6..14, v:6..14)
        img.setRGB(7, 10, 0xFFFFFFFF);
        img.setRGB(8, 10, 0xFF222222);
        img.setRGB(12, 10, 0xFF222222);
        img.setRGB(13, 10, 0xFFFFFFFF);

        // Muzzle (pink/tan)
        Color muzzle = new Color(180, 150, 140);
        fillNoise(img, 1, 18, 8, 4, muzzle, cDark, muzzle, r);

        // Horns (grey) (22, 0, 6, 4)
        fillNoise(img, 22, 0, 6, 4, new Color(180, 180, 180), new Color(130, 130, 130), new Color(210, 210, 210), r);

        return img;
    }

    private BufferedImage generateSheepFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1343);

        Color sWhite = new Color(235, 235, 235);
        Color sGray = new Color(205, 205, 205);
        Color sSkin = new Color(210, 180, 160);

        // Wool fleece on body
        fillNoise(img, 0, 0, w, h, sWhite, sGray, sWhite, r);

        // Face & legs (tan skin)
        fillNoise(img, 0, 0, 32, 16, sSkin, new Color(185, 155, 135), sSkin, r);
        fillNoise(img, 0, 16, 16, 16, sSkin, new Color(185, 155, 135), sSkin, r);

        // Sheep eyes
        img.setRGB(8, 10, 0xFFFFFFFF);
        img.setRGB(9, 10, 0xFF352515);
        img.setRGB(12, 10, 0xFF352515);
        img.setRGB(13, 10, 0xFFFFFFFF);

        return img;
    }

    private BufferedImage generateChickenFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1344);

        Color chWhite = new Color(245, 245, 245);
        Color chShade = new Color(215, 215, 215);
        Color chYellow = new Color(235, 180, 20);
        Color chRed = new Color(220, 35, 35);

        fillNoise(img, 0, 0, w, h, chWhite, chShade, chWhite, r);

        // Beak (14, 0, 6, 4) & Legs (26, 0, 6, 8) -> Yellow
        fillNoise(img, 14, 0, 8, 4, chYellow, new Color(200, 150, 15), chYellow, r);
        fillNoise(img, 26, 0, 8, 8, chYellow, new Color(200, 150, 15), chYellow, r);

        // Wattle (14, 4, 4, 4) -> Red
        fillNoise(img, 14, 4, 4, 4, chRed, new Color(180, 25, 25), chRed, r);

        // Eyes (front of head)
        img.setRGB(3, 3, 0xFF111111);
        img.setRGB(6, 3, 0xFF111111);

        return img;
    }

    private BufferedImage generateEndermanFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1345);

        Color eBlack = new Color(20, 20, 20);
        Color eDark = new Color(10, 10, 10);
        Color eGray = new Color(30, 30, 30);

        fillNoise(img, 0, 0, w, h, eBlack, eDark, eGray, r);

        // Purple glowing eyes on Head (u:8..16, v:8..16)
        int pEye = 0xFFCC33FF;
        int pPupil = 0xFFFF88FF;
        fillSolid(img, 9, 10, 2, 1, pEye);
        fillSolid(img, 13, 10, 2, 1, pEye);
        img.setRGB(10, 10, pPupil);
        img.setRGB(13, 10, pPupil);

        return img;
    }

    private BufferedImage generateBlazeFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1346);

        Color bYellow = new Color(255, 190, 30);
        Color bOrange = new Color(230, 115, 10);
        Color bBright = new Color(255, 230, 80);

        fillNoise(img, 0, 0, w, h, bOrange, bYellow, bBright, r);

        // Blaze Eyes (Head front face)
        img.setRGB(9, 10, 0xFFFFFFFF);
        img.setRGB(10, 10, 0xFF331100);
        img.setRGB(13, 10, 0xFF331100);
        img.setRGB(14, 10, 0xFFFFFFFF);

        return img;
    }

    private BufferedImage generateDragonFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1347);

        Color dBlack = new Color(22, 22, 25);
        Color dDark = new Color(14, 14, 16);
        Color dGray = new Color(38, 38, 42);

        fillNoise(img, 0, 0, w, h, dBlack, dDark, dGray, r);

        // Glowing Magenta Eyes
        int eyeCol = 0xFFDD22DD;
        fillSolid(img, 20, 20, 6, 4, eyeCol);
        fillSolid(img, 40, 20, 6, 4, eyeCol);

        return img;
    }

    private BufferedImage generateEndCrystalFallback(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(1348);

        Color obs = new Color(28, 22, 38);
        Color glass = new Color(180, 80, 220);
        Color core = new Color(255, 220, 255);

        fillNoise(img, 0, 0, w, 16, glass, obs, core, r);
        fillNoise(img, 0, 16, w, 16, obs, new Color(18, 14, 25), obs, r);

        return img;
    }

    // --- Helpers ---

    private void fillNoise(BufferedImage img, int x0, int y0, int w, int h, Color base, Color dark, Color light, Random r) {
        int x1 = Math.min(img.getWidth(), x0 + w);
        int y1 = Math.min(img.getHeight(), y0 + h);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int n = r.nextInt(4);
                Color c = (n == 0) ? dark : ((n == 1) ? light : base);
                img.setRGB(x, y, c.getRGB());
            }
        }
    }

    private void fillSolid(BufferedImage img, int x0, int y0, int w, int h, int argb) {
        int x1 = Math.min(img.getWidth(), x0 + w);
        int y1 = Math.min(img.getHeight(), y0 + h);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                img.setRGB(x, y, argb);
            }
        }
    }

    public void cleanup() {
        for (int id : textures.values()) {
            if (id != 0) glDeleteTextures(id);
        }
        textures.clear();
        crystalTexture = 0;
    }
}
