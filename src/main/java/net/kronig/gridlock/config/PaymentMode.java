package net.kronig.gridlock.config;

public enum PaymentMode implements Setting.Choice {
    PLAYER("Spieler zahlt"),
    POOL("Team-Pool");

    private final String displayName;

    PaymentMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
