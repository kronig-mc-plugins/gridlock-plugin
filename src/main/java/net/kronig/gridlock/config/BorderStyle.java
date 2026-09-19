package net.kronig.gridlock.config;

public enum BorderStyle implements Setting.Choice {
    LINE("Linie am Boden"),
    PARTICLES("Partikel-Wand"),
    BOTH("Beides");

    private final String displayName;

    BorderStyle(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    public boolean line() {
        return this != PARTICLES;
    }

    public boolean particles() {
        return this != LINE;
    }
}
