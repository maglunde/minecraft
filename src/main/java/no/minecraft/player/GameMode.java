package no.minecraft.player;

import no.minecraft.settings.GameSettings;

public enum GameMode {
    SURVIVAL("Survival", "Overlevelse"),
    CREATIVE("Creative", "Kreativ"),
    HARDCORE("Hardcore", "Hardcore");

    private final String englishName;
    private final String norwegianName;

    GameMode(String englishName, String norwegianName) {
        this.englishName = englishName;
        this.norwegianName = norwegianName;
    }

    public String getDisplayName() {
        if (GameSettings.getInstance().getLanguage() == GameSettings.Language.NORWEGIAN) {
            return norwegianName;
        }
        return englishName;
    }
}
