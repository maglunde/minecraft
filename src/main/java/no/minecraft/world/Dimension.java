package no.minecraft.world;

public enum Dimension {
    OVERWORLD("Overworld", "Oververden", 0.70f, 0.85f, 1.0f, 60.0f, 100.0f),
    NETHER("Nether", "Nether", 0.35f, 0.05f, 0.05f, 30.0f, 70.0f),
    THE_END("The End", "Enden", 0.08f, 0.05f, 0.12f, 40.0f, 90.0f);

    private final String nameEn;
    private final String nameNo;
    private final float skyR, skyG, skyB;
    private final float fogStart, fogEnd;

    Dimension(String nameEn, String nameNo, float skyR, float skyG, float skyB, float fogStart, float fogEnd) {
        this.nameEn = nameEn;
        this.nameNo = nameNo;
        this.skyR = skyR;
        this.skyG = skyG;
        this.skyB = skyB;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
    }

    public String getName(boolean norwegian) {
        return norwegian ? nameNo : nameEn;
    }

    public float getSkyR() { return skyR; }
    public float getSkyG() { return skyG; }
    public float getSkyB() { return skyB; }
    public float getFogStart() { return fogStart; }
    public float getFogEnd() { return fogEnd; }
}
