package net.kronig.gridlock.config;

public enum PaymentMode implements Setting.Choice {
    /** Everyone pays with their own levels. */
    PLAYER("Jeder für sich"),
    /** One shared level pool, mirrored into every player's XP bar. */
    POOL("Team-Pool (geteilte XP-Leiste)"),
    /** Own levels like PLAYER, but levels can be sent to other players. */
    TRANSFER("Jeder für sich + Überweisen");

    private final String displayName;

    PaymentMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
