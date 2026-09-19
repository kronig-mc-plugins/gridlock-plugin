package net.kronig.gridlock.config;

public enum DeathMode implements Setting.Choice {
    NORMAL("Normal respawnen"),
    HARDCORE("Hardcore (Runde vorbei)");

    private final String displayName;

    DeathMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
