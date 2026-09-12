package no.minecraft.player;

import no.minecraft.i18n.I18n;

import java.util.Locale;

public enum GameMode {
    SURVIVAL,
    CREATIVE,
    HARDCORE;

    public String getDisplayName() {
        return I18n.get("game_mode." + name().toLowerCase(Locale.ROOT));
    }
}
