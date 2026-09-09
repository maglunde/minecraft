package no.minecraft.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GameSettingsTest {

    @Test
    public void testRenderDistanceRange() {
        GameSettings settings = GameSettings.getInstance();

        settings.setRenderDistance(20);
        assertEquals(20, settings.getRenderDistance());

        settings.setRenderDistance(25);
        assertEquals(20, settings.getRenderDistance()); // Clamped to 20

        settings.setRenderDistance(2);
        assertEquals(3, settings.getRenderDistance()); // Clamped to 3

        settings.setRenderDistance(12);
        assertEquals(12, settings.getRenderDistance());

        // Restore default
        settings.setRenderDistance(5);
    }
}
