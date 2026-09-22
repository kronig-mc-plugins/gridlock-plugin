package net.kronig.gridlock.spawn;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.List;

public enum SpawnPreset {
    RANDOM("Zufall", Material.ENDER_EYE, Difficulty.UNKNOWN,
            "Einer der anderen Spawns wird zufällig ausgelost – vom Baum bis zur Pilzinsel. Lass dich überraschen.",
            List.of(), false, Kind.RANDOM),
    WORLDSPAWN("Normaler Worldspawn", Material.GRASS_BLOCK, Difficulty.MEDIUM,
            "Genau da, wo Minecraft selbst den Spawn der Welt gesetzt hat. Wie in einer normalen neuen Welt.",
            List.of(), false, Kind.WORLDSPAWN),
    TREE("Neben einem Baum", Material.OAK_SAPLING, Difficulty.EASY,
            "Du startest direkt neben einem Baum. Holz ist sofort da, der perfekte Einstieg.",
            List.of(Biome.FOREST, Biome.BIRCH_FOREST, Biome.FLOWER_FOREST, Biome.PLAINS), true, Kind.BIOME),
    VILLAGE("Dorf", Material.BELL, Difficulty.EASY,
            "Am Rand eines Dorfes. Betten, Kisten und Händler in Reichweite – wenn ihr euch dahin kauft.",
            List.of(), false, Kind.VILLAGE),
    JUNGLE("Dschungel", Material.JUNGLE_SAPLING, Difficulty.EASY,
            "Riesige Bäume, Melonen und Kakao. Viel Holz, aber unübersichtlich.",
            List.of(Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE), true, Kind.BIOME),
    CHERRY("Kirschblütenhain", Material.CHERRY_SAPLING, Difficulty.EASY,
            "Hübsch, ruhig, mit Bäumen. Liegt oft in den Bergen.",
            List.of(Biome.CHERRY_GROVE), true, Kind.BIOME),
    SAVANNA("Savanne", Material.ACACIA_SAPLING, Difficulty.MEDIUM,
            "Akazien, Gras und viel freie Sicht. Holz ja, Schatten nein.",
            List.of(Biome.SAVANNA, Biome.SAVANNA_PLATEAU), true, Kind.BIOME),
    DARK_FOREST("Dunkler Wald", Material.DARK_OAK_SAPLING, Difficulty.MEDIUM,
            "Dichte Kronen, Riesenpilze und Monster auch am Tag. Holz ohne Ende.",
            List.of(Biome.DARK_FOREST), true, Kind.BIOME),
    TAIGA("Taiga", Material.SPRUCE_SAPLING, Difficulty.MEDIUM,
            "Fichtenwald mit Beeren und Wölfen. Kalt, aber machbar.",
            List.of(Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.OLD_GROWTH_PINE_TAIGA), true, Kind.BIOME),
    PLAINS("Ebene", Material.SHORT_GRASS, Difficulty.MEDIUM,
            "Offenes Grasland. Bäume sind selten – der erste Stamm kann teuer werden.",
            List.of(Biome.PLAINS, Biome.SUNFLOWER_PLAINS), false, Kind.BIOME),
    BEACH("Strand", Material.SAND, Difficulty.MEDIUM,
            "Sand, Wasser, Schildkröten. Holz gibt es erst hinter der Küste.",
            List.of(Biome.BEACH), false, Kind.BIOME),
    SWAMP("Sumpf", Material.LILY_PAD, Difficulty.MEDIUM,
            "Wasser, Schleim und Hexen. Nicht jeder Block ist trocken.",
            List.of(Biome.SWAMP, Biome.MANGROVE_SWAMP), false, Kind.BIOME),
    DESERT("Wüste", Material.CACTUS, Difficulty.HARD,
            "Sand, Kakteen, tote Büsche. Kein Holz weit und breit.",
            List.of(Biome.DESERT), false, Kind.BIOME),
    SNOW("Schneeebene", Material.SNOW_BLOCK, Difficulty.HARD,
            "Eisig und leer. Nahrung und Holz sind Mangelware.",
            List.of(Biome.SNOWY_PLAINS), false, Kind.BIOME),
    BADLANDS("Tafelberge", Material.RED_SAND, Difficulty.HARD,
            "Terrakotta und Gold, aber kaum Leben.",
            List.of(Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS), false, Kind.BIOME),
    CAVE("Höhle", Material.DEEPSLATE, Difficulty.HARD,
            "Tief unter der Erde in einer Höhle. Erze überall, aber kein Tageslicht und kein Holz.",
            List.of(), false, Kind.CAVE),
    ICE_SPIKES("Eiszapfen", Material.PACKED_ICE, Difficulty.EXTREME,
            "Zwischen riesigen Eissäulen. Selten, schön, gnadenlos.",
            List.of(Biome.ICE_SPIKES), false, Kind.BIOME),
    PEAKS("Berggipfel", Material.STONE, Difficulty.EXTREME,
            "Ganz oben auf einem Berg. Jeder Schritt kann der letzte sein.",
            List.of(Biome.STONY_PEAKS, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS), false, Kind.BIOME),
    MUSHROOM("Pilzinsel", Material.RED_MUSHROOM_BLOCK, Difficulty.EXTREME,
            "Keine Monster, aber mitten im Ozean. Viel Glück mit Holz und Erzen.",
            List.of(Biome.MUSHROOM_FIELDS), false, Kind.BIOME);

    /** How the spawn finder looks for this preset. */
    public enum Kind { RANDOM, WORLDSPAWN, BIOME, VILLAGE, CAVE }

    public enum Difficulty {
        EASY("Leicht", "#55ff55", 1),
        MEDIUM("Mittel", "#ffff55", 2),
        HARD("Schwer", "#ffaa00", 3),
        EXTREME("Extrem", "#ff3b3b", 4),
        UNKNOWN("Überraschung", "#ff55ff", 0);

        private final String displayName;
        private final String color;
        private final int stars;

        Difficulty(String displayName, String color, int stars) {
            this.displayName = displayName;
            this.color = color;
            this.stars = stars;
        }

        public String format() {
            String starText = stars == 0 ? "? ? ? ?" : "★".repeat(stars) + "☆".repeat(4 - stars);
            return "<" + color + ">" + starText + " " + displayName + "</" + color + ">";
        }
    }

    private final String displayName;
    private final Material icon;
    private final Difficulty difficulty;
    private final String description;
    private final List<Biome> biomes;
    private final boolean nextToTree;
    private final Kind kind;

    SpawnPreset(String displayName, Material icon, Difficulty difficulty, String description, List<Biome> biomes,
                boolean nextToTree, Kind kind) {
        this.displayName = displayName;
        this.icon = icon;
        this.difficulty = difficulty;
        this.description = description;
        this.biomes = biomes;
        this.nextToTree = nextToTree;
        this.kind = kind;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public String description() {
        return description;
    }

    public List<Biome> biomes() {
        return biomes;
    }

    public boolean nextToTree() {
        return nextToTree;
    }

    public Kind kind() {
        return kind;
    }

    /** RANDOM resolves to one of the concrete presets; everything else stays as it is. */
    public SpawnPreset resolve(java.util.Random random) {
        if (this != RANDOM) {
            return this;
        }
        SpawnPreset[] values = values();
        return values[1 + random.nextInt(values.length - 1)];
    }

    public static SpawnPreset parse(String name) {
        if (name == null) {
            return null;
        }
        for (SpawnPreset preset : values()) {
            if (preset.name().equalsIgnoreCase(name) || preset.displayName.equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }
}
