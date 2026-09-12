package no.minecraft.player;

import no.minecraft.i18n.I18n;

import java.util.Locale;

public enum Perspective {
    FIRST_PERSON,
    THIRD_PERSON_BACK,
    THIRD_PERSON_FRONT;

    public String getDisplayName() {
        return I18n.get("perspective." + name().toLowerCase(Locale.ROOT));
    }

    public Perspective next() {
        Perspective[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
