package no.minecraft.player;

public enum GameMode {
    SURVIVAL("Survival"),
    CREATIVE("Creative"),
    HARDCORE("Hardcore");

    private final String displayName;

    GameMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
