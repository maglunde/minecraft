package no.minecraft.world;

import no.minecraft.i18n.I18n;

import java.util.Locale;

public enum Dimension {
    OVERWORLD(0.70f, 0.85f, 1.0f, 60.0f, 100.0f),
    NETHER(0.35f, 0.05f, 0.05f, 30.0f, 70.0f),
    THE_END(0.14f, 0.09f, 0.20f, 50.0f, 120.0f);

    private final float skyR, skyG, skyB;
    private final float fogStart, fogEnd;

    Dimension(float skyR, float skyG, float skyB, float fogStart, float fogEnd) {
        this.skyR = skyR;
        this.skyG = skyG;
        this.skyB = skyB;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
    }

    public String getName() {
        return I18n.get("dimension." + name().toLowerCase(Locale.ROOT));
    }

    public float getSkyR() { return skyR; }
    public float getSkyG() { return skyG; }
    public float getSkyB() { return skyB; }
    public float getFogStart() { return fogStart; }
    public float getFogEnd() { return fogEnd; }
}
