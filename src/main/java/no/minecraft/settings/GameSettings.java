package no.minecraft.settings;

import static org.lwjgl.glfw.GLFW.*;

public class GameSettings {
    private static final GameSettings INSTANCE = new GameSettings();

    public static GameSettings getInstance() {
        return INSTANCE;
    }

    public enum Language {
        ENGLISH("English (US)"),
        NORWEGIAN("Norsk (Bokmal)");

        private final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
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

    private GameSettings() {}

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public void toggleLanguage() {
        language = (language == Language.NORWEGIAN) ? Language.ENGLISH : Language.NORWEGIAN;
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
