package net.kronig.gridlock.config;

import org.bukkit.Color;

public enum BorderColor implements Setting.Choice {
    RED("Rot", Color.fromRGB(255, 40, 40)),
    ORANGE("Orange", Color.fromRGB(255, 140, 0)),
    YELLOW("Gelb", Color.fromRGB(255, 230, 40)),
    GREEN("Grün", Color.fromRGB(60, 230, 60)),
    AQUA("Türkis", Color.fromRGB(40, 220, 230)),
    BLUE("Blau", Color.fromRGB(50, 90, 255)),
    PURPLE("Lila", Color.fromRGB(180, 60, 255)),
    WHITE("Weiß", Color.fromRGB(240, 240, 240));

    private final String displayName;
    private final Color color;

    BorderColor(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    public Color color() {
        return color;
    }
}
