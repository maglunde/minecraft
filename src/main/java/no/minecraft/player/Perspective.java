package no.minecraft.player;

public enum Perspective {
    FIRST_PERSON("Førsteperson", "First Person"),
    THIRD_PERSON_BACK("Tredjeperson (Bak)", "Third Person (Back)"),
    THIRD_PERSON_FRONT("Tredjeperson (Foran)", "Third Person (Front)");

    private final String norwegianName;
    private final String englishName;

    Perspective(String norwegianName, String englishName) {
        this.norwegianName = norwegianName;
        this.englishName = englishName;
    }

    public String getDisplayName(boolean norwegian) {
        return norwegian ? norwegianName : englishName;
    }

    public Perspective next() {
        Perspective[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
