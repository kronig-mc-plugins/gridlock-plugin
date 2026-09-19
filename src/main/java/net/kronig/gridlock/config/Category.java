package net.kronig.gridlock.config;

import org.bukkit.Material;

public enum Category {
    EXPAND("Erweitern", "#55ff55", Material.GRASS_BLOCK, "Kosten und Steuerung beim Vergrößern des Feldes."),
    LEVELS("Level", "#7fff00", Material.EXPERIENCE_BOTTLE, "Woher die Level kommen und wer bezahlt."),
    TIMER("Timer", "#ffaa00", Material.CLOCK, "Challenge-Timer, Spielzeit und Anzeigen."),
    DIMENSIONS("Dimensionen", "#aa55ff", Material.ENDER_EYE, "Nether- und End-Regeln."),
    DEATH("Tod", "#ff5555", Material.SKELETON_SKULL, "Was beim Sterben passiert."),
    BORDER("Border", "#ff3b3b", Material.RED_STAINED_GLASS, "Aussehen der Border und Mob-Regeln."),
    SPAWN("Spawn", "#55ffff", Material.COMPASS, "Suche des Startpunkts."),
    MOTD("MOTD", "#ffff55", Material.OAK_SIGN, "Die Server-Nachricht in der Serverliste.");

    private final String displayName;
    private final String color;
    private final Material icon;
    private final String description;

    Category(String displayName, String color, Material icon, String description) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }
}
