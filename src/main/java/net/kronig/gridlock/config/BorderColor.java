package net.kronig.gridlock.config;

import org.bukkit.Color;
import org.bukkit.Material;

public enum BorderColor implements Setting.Choice {
    RED("Rot", Color.fromRGB(255, 40, 40), Material.RED_CONCRETE),
    ORANGE("Orange", Color.fromRGB(255, 140, 0), Material.ORANGE_CONCRETE),
    YELLOW("Gelb", Color.fromRGB(255, 230, 40), Material.YELLOW_CONCRETE),
    GREEN("Grün", Color.fromRGB(60, 230, 60), Material.LIME_CONCRETE),
    AQUA("Türkis", Color.fromRGB(40, 220, 230), Material.LIGHT_BLUE_CONCRETE),
    BLUE("Blau", Color.fromRGB(50, 90, 255), Material.BLUE_CONCRETE),
    PURPLE("Lila", Color.fromRGB(180, 60, 255), Material.PURPLE_CONCRETE),
    WHITE("Weiß", Color.fromRGB(240, 240, 240), Material.WHITE_CONCRETE);

    private final String displayName;
    private final Color color;
    private final Material block;

    BorderColor(String displayName, Color color, Material block) {
        this.displayName = displayName;
        this.color = color;
        this.block = block;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    public Color color() {
        return color;
    }

    /** Block used for the ground line. */
    public Material block() {
        return block;
    }
}
