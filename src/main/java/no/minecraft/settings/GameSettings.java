package no.minecraft.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.lwjgl.glfw.GLFW.*;

public class GameSettings {
    public static final Path SETTINGS_FILE = Paths.get("settings.properties");
    private static final GameSettings INSTANCE = new GameSettings();

    public static GameSettings getInstance() {
        return INSTANCE;
    }

    public enum Language {
        ENGLISH("en_us"),
        NORWEGIAN("nb_no");

        private final String code;

        Language(String code) {
            this.code = code;
        }

        /** Bundle file name fragment, e.g. "en_us". Persisted value stays name(). */
        public String getCode() {
            return code;
        }

        /** Native name of the language itself, e.g. "Norsk (Bokmål)" in every bundle. */
        public String getDisplayName() {
            return no.minecraft.i18n.I18n.get("language." + code);
        }

        public Language next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Language language = Language.NORWEGIAN;

    // Controls / Keybindings
    public int keyForward = GLFW_KEY_W;
    public int keyBackward = GLFW_KEY_S;
    public int keyLeft = GLFW_KEY_A;
    public int keyRight = GLFW_KEY_D;
    public int keyJump = GLFW_KEY_SPACE;
    public int keySneak = GLFW_KEY_LEFT_SHIFT;
    public int keyInventory = GLFW_KEY_E;
    public int keyDrop = GLFW_KEY_Q;
    public int keyToggleDebug = GLFW_KEY_F3;
    public int keyTogglePerspective = GLFW_KEY_F5;

    // Mouse Sensitivity (default 0.12f, displayed as 100%)
    private float mouseSensitivity = 0.12f;

    // FOV in degrees (default 70, Normal)
    private float fov = 70.0f;

    // Video Settings
    private int renderDistance = 5; // 3 to 20
    private float brightness = 1.0f; // 0.0f (Moody) to 1.0f (Bright)

    // Master Sound Volume (0.0f to 1.0f)
    private float soundVolume = 1.0f;

    // GUI Scale: 0 = Auto, 1, 2, 3, 4
    private int guiScale = 0;

    private GameSettings() {}

    public static void load() {
        getInstance().loadFrom(SETTINGS_FILE);
    }

    public static void save() {
        getInstance().saveTo(SETTINGS_FILE);
    }

    public void loadFrom(Path path) {
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        String lang = p.getProperty("language");
        if (lang != null) {
            try {
                language = Language.valueOf(lang);
            } catch (IllegalArgumentException ignored) {}
        }
        setMouseSensitivity(parseFloat(p, "mouseSensitivity", mouseSensitivity));
        setFov(parseFloat(p, "fov", fov));
        setRenderDistance(parseInt(p, "renderDistance", renderDistance));
        setBrightness(parseFloat(p, "brightness", brightness));
        setSoundVolume(parseFloat(p, "soundVolume", soundVolume));
        setGuiScale(parseInt(p, "guiScale", guiScale));
        keyForward = parseInt(p, "keyForward", keyForward);
        keyBackward = parseInt(p, "keyBackward", keyBackward);
        keyLeft = parseInt(p, "keyLeft", keyLeft);
        keyRight = parseInt(p, "keyRight", keyRight);
        keyJump = parseInt(p, "keyJump", keyJump);
        keySneak = parseInt(p, "keySneak", keySneak);
        keyInventory = parseInt(p, "keyInventory", keyInventory);
        keyDrop = parseInt(p, "keyDrop", keyDrop);
        keyToggleDebug = parseInt(p, "keyToggleDebug", keyToggleDebug);
        keyTogglePerspective = parseInt(p, "keyTogglePerspective", keyTogglePerspective);
    }

    public void saveTo(Path path) {
        Properties p = new Properties();
        p.setProperty("language", language.name());
        p.setProperty("mouseSensitivity", Float.toString(mouseSensitivity));
        p.setProperty("fov", Float.toString(fov));
        p.setProperty("renderDistance", Integer.toString(renderDistance));
        p.setProperty("brightness", Float.toString(brightness));
        p.setProperty("soundVolume", Float.toString(soundVolume));
        p.setProperty("guiScale", Integer.toString(guiScale));
        p.setProperty("keyForward", Integer.toString(keyForward));
        p.setProperty("keyBackward", Integer.toString(keyBackward));
        p.setProperty("keyLeft", Integer.toString(keyLeft));
        p.setProperty("keyRight", Integer.toString(keyRight));
        p.setProperty("keyJump", Integer.toString(keyJump));
        p.setProperty("keySneak", Integer.toString(keySneak));
        p.setProperty("keyInventory", Integer.toString(keyInventory));
        p.setProperty("keyDrop", Integer.toString(keyDrop));
        p.setProperty("keyToggleDebug", Integer.toString(keyToggleDebug));
        p.setProperty("keyTogglePerspective", Integer.toString(keyTogglePerspective));
        try (OutputStream out = Files.newOutputStream(path)) {
            p.store(out, "Minecraft clone settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static float parseFloat(Properties p, String key, float fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int getGuiScale() {
        return guiScale;
    }

    public void setGuiScale(int guiScale) {
        this.guiScale = Math.clamp(guiScale, 0, 4);
    }

    public float calculateGuiScale(int width, int height) {
        int maxScale = 1;
        while (width / (maxScale + 1) >= 320 && height / (maxScale + 1) >= 240) {
            maxScale++;
        }
        if (guiScale > 0 && guiScale <= maxScale) {
            return (float) guiScale;
        }
        return (float) Math.max(1, maxScale);
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public void nextLanguage() {
        language = language.next();
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    public void setMouseSensitivity(float s) {
        this.mouseSensitivity = Math.clamp(s, 0.04f, 0.30f);
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = Math.clamp(fov, 60.0f, 110.0f);
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = Math.clamp(renderDistance, 3, 20);
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = Math.clamp(brightness, 0.0f, 1.0f);
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(float soundVolume) {
        this.soundVolume = Math.clamp(soundVolume, 0.0f, 1.0f);
    }

    public static String getKeyName(int key) {
        return switch (key) {
            case GLFW_KEY_SPACE -> "SPACE";
            case GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW_KEY_TAB -> "TAB";
            case GLFW_KEY_ENTER -> "ENTER";
            case GLFW_KEY_BACKSPACE -> "BACKSPACE";
            default -> {
                String name = glfwGetKeyName(key, 0);
                yield (name != null && !name.isEmpty()) ? name.toUpperCase() : "KEY_" + key;
            }
        };
    }
}
