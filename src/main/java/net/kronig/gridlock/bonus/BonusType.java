package net.kronig.gridlock.bonus;

import org.bukkit.Material;

public enum BonusType {
    HALF_PRICE("Halber Preis", "Jeder neue Block kostet nur die Hälfte (mindestens 1 Level).", Material.GOLD_INGOT, "#ffd700"),
    FREE_BLOCK("Gratis-Block", "Der nächste Block ist für jeden Spieler kostenlos – einer pro Kopf.", Material.EMERALD, "#55ff55"),
    DOUBLE_TIME("Doppelte Zeit-Level", "Zeit-Level zählen doppelt.", Material.CLOCK, "#ffaa00"),
    XP_RUSH("XP-Rausch", "Alle XP zählen doppelt: Erze, Mobs, Öfen.", Material.EXPERIENCE_BOTTLE, "#7fff00");

    private final String displayName;
    private final String description;
    private final Material icon;
    private final String color;

    BonusType(String displayName, String description, Material icon, String color) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Material icon() {
        return icon;
    }

    public String color() {
        return color;
    }

    public static BonusType parse(String name) {
        if (name == null) {
            return null;
        }
        for (BonusType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
