package net.kronig.gridlock.config;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** A single configurable value, shared by config.yml, the settings GUI and /gl config. */
public final class Setting {

    public enum Type { BOOL, INT, CHOICE }

    private final String key;
    private final Category category;
    private final Type type;
    private final Material icon;
    private final String name;
    private final String description;
    private final Object defaultValue;
    private final int min;
    private final int max;
    private final String unit;
    private final List<String> choices;
    private final List<String> choiceNames;

    private Setting(String key, Category category, Type type, Material icon, String name, String description,
                    Object defaultValue, int min, int max, String unit, List<String> choices, List<String> choiceNames) {
        this.key = key;
        this.category = category;
        this.type = type;
        this.icon = icon;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.choices = choices;
        this.choiceNames = choiceNames;
    }

    static Setting bool(String key, Category category, Material icon, String name, String description, boolean def) {
        return new Setting(key, category, Type.BOOL, icon, name, description, def, 0, 1, "", List.of(), List.of());
    }

    static Setting integer(String key, Category category, Material icon, String name, String description,
                           int def, int min, int max, String unit) {
        return new Setting(key, category, Type.INT, icon, name, description, def, min, max, unit, List.of(), List.of());
    }

    static <E extends Enum<E> & Choice> Setting choice(String key, Category category, Material icon, String name,
                                                        String description, E def) {
        E[] values = def.getDeclaringClass().getEnumConstants();
        return new Setting(key, category, Type.CHOICE, icon, name, description, def.name(), 0, 0, "",
                Arrays.stream(values).map(Enum::name).toList(),
                Arrays.stream(values).map(Choice::displayName).toList());
    }

    /** Enum values usable as a CHOICE setting. */
    public interface Choice {
        String displayName();
    }

    /**
     * Parses user input for this setting.
     *
     * @return the parsed value, or {@code null} if the input is invalid
     */
    public Object parse(String input) {
        String in = input.trim();
        return switch (type) {
            case BOOL -> switch (in.toLowerCase(Locale.ROOT)) {
                case "true", "an", "on", "ja", "yes", "1" -> Boolean.TRUE;
                case "false", "aus", "off", "nein", "no", "0" -> Boolean.FALSE;
                default -> null;
            };
            case INT -> {
                try {
                    int value = Integer.parseInt(in);
                    yield value < min || value > max ? null : value;
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case CHOICE -> {
                String upper = in.toUpperCase(Locale.ROOT);
                if (choices.contains(upper)) {
                    yield upper;
                }
                for (int i = 0; i < choiceNames.size(); i++) {
                    if (choiceNames.get(i).equalsIgnoreCase(in)) {
                        yield choices.get(i);
                    }
                }
                yield null;
            }
        };
    }

    public String formatValue(Object value) {
        return switch (type) {
            case BOOL -> (Boolean) value ? "An" : "Aus";
            case INT -> value + (unit.isEmpty() ? "" : " " + unit);
            case CHOICE -> {
                int index = choices.indexOf(String.valueOf(value));
                yield index >= 0 ? choiceNames.get(index) : String.valueOf(value);
            }
        };
    }

    public String inputHint() {
        return switch (type) {
            case BOOL -> "an|aus";
            case INT -> min + "-" + max;
            case CHOICE -> String.join("|", choices).toLowerCase(Locale.ROOT);
        };
    }

    public String key() {
        return key;
    }

    public Category category() {
        return category;
    }

    public Type type() {
        return type;
    }

    public Material icon() {
        return icon;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public List<String> choices() {
        return choices;
    }
}
