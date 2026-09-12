package no.minecraft.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GameSettingsTest {

    @Test
    public void testRenderDistanceRange() {
        GameSettings settings = GameSettings.getInstance();
        int original = settings.getRenderDistance();
        try {
            settings.setRenderDistance(20);
            assertEquals(20, settings.getRenderDistance());

            settings.setRenderDistance(25);
            assertEquals(20, settings.getRenderDistance()); // Clamped to 20

            settings.setRenderDistance(2);
            assertEquals(3, settings.getRenderDistance()); // Clamped to 3

            settings.setRenderDistance(12);
            assertEquals(12, settings.getRenderDistance());
        } finally {
            settings.setRenderDistance(original);
        }
    }

    @Test
    public void testCalculateGuiScale() {
        GameSettings settings = GameSettings.getInstance();
        int originalGuiScale = settings.getGuiScale();
        try {
            settings.setGuiScale(0); // Auto

            // Small window (854x480) -> scale 2
            assertEquals(2.0f, settings.calculateGuiScale(854, 480));

            // 720p window (1280x720) -> scale 3
            assertEquals(3.0f, settings.calculateGuiScale(1280, 720));

            // 1080p window (1920x1080) -> scale 4
            assertEquals(4.0f, settings.calculateGuiScale(1920, 1080));

            // 1440p / Retina window (2560x1440) -> scale 6
            assertEquals(6.0f, settings.calculateGuiScale(2560, 1440));

            // Ultra tiny window (300x200) -> min scale 1
            assertEquals(1.0f, settings.calculateGuiScale(300, 200));

            // Manual GUI Scale override
            settings.setGuiScale(2);
            assertEquals(2.0f, settings.calculateGuiScale(1920, 1080));
            assertEquals(2.0f, settings.calculateGuiScale(2560, 1440));

            // Manual override cannot exceed window capability
            settings.setGuiScale(4);
            assertEquals(2.0f, settings.calculateGuiScale(854, 480));
        } finally {
            settings.setGuiScale(originalGuiScale);
        }
    }
}
