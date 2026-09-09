package no.minecraft.settings;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsPersistenceTest {

    @Test
    public void testSettingsSaveLoadRoundtrip() throws Exception {
        Path tmp = Files.createTempFile("settings-test", ".properties");
        try {
            GameSettings gs = GameSettings.getInstance();
            float originalFov = gs.getFov();
            int originalRd = gs.getRenderDistance();
            try {
                gs.setFov(85.0f);
                gs.setRenderDistance(7);
                gs.saveTo(tmp);

                gs.setFov(60.0f);
                gs.setRenderDistance(3);

                gs.loadFrom(tmp);
                assertEquals(85.0f, gs.getFov(), 0.001f, "FOV skal gjenopprettes fra fil");
                assertEquals(7, gs.getRenderDistance(), "Render distance skal gjenopprettes fra fil");
            } finally {
                gs.setFov(originalFov);
                gs.setRenderDistance(originalRd);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
