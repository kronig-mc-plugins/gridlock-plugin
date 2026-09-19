package net.kronig.gridlock.config;

import org.bukkit.Color;
import org.bukkit.Material;

public enum BorderColor implements Setting.Choice {
    RED("Rot", Color.fromRGB(255, 40, 40), Material.RED_CONCRETE, Material.RED_STAINED_GLASS),
    ORANGE("Orange", Color.fromRGB(255, 140, 0), Material.ORANGE_CONCRETE, Material.ORANGE_STAINED_GLASS),
    YELLOW("Gelb", Color.fromRGB(255, 230, 40), Material.YELLOW_CONCRETE, Material.YELLOW_STAINED_GLASS),
    GREEN("Grün", Color.fromRGB(60, 230, 60), Material.LIME_CONCRETE, Material.LIME_STAINED_GLASS),
    AQUA("Türkis", Color.fromRGB(40, 220, 230), Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS),
    BLUE("Blau", Color.fromRGB(50, 90, 255), Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS),
    PURPLE("Lila", Color.fromRGB(180, 60, 255), Material.PURPLE_CONCRETE, Material.PURPLE_STAINED_GLASS),
    WHITE("Weiß", Color.fromRGB(240, 240, 240), Material.WHITE_CONCRETE, Material.WHITE_STAINED_GLASS);

    private final String displayName;
    private final Color color;
    private final Material block;
    private final Material glass;

    BorderColor(String displayName, Color color, Material block, Material glass) {
        this.displayName = displayName;
        this.color = color;
        this.block = block;
        this.glass = glass;
    }

    /** Translucent block for the soft glow next to the line. */
    public Material glass() {
        return glass;
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
