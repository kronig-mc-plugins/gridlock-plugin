package net.kronig.gridlock.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Settings {

    private static final Map<String, Setting> REGISTRY = new LinkedHashMap<>();

    // Erweitern
    public static final Setting HOLD_TICKS = register(Setting.integer("expand.hold-ticks", Category.EXPAND, Material.CLOCK,
            "Haltezeit", "Wie lange man schleichend gegen die Border drücken muss, bevor erweitert wird (20 Ticks = 1 Sekunde).",
            20, 5, 100, "Ticks"));
    public static final Setting COST_BASE = register(Setting.integer("expand.cost-base", Category.EXPAND, Material.EXPERIENCE_BOTTLE,
            "Grundkosten", "Wie viele Level ein neuer Block kostet.", 1, 0, 100, "Level"));
    public static final Setting COST_INCREASE = register(Setting.integer("expand.cost-increase", Category.EXPAND, Material.ANVIL,
            "Kostenaufschlag", "Um wie viele Level die Kosten steigen, jedes Mal wenn das Feld um 'Aufschlag alle' Blöcke gewachsen ist. 0 = immer gleich teuer.",
            0, 0, 50, "Level"));
    public static final Setting COST_INCREASE_EVERY = register(Setting.integer("expand.cost-increase-every", Category.EXPAND, Material.LADDER,
            "Aufschlag alle", "Nach wie vielen freigeschalteten Blöcken der Kostenaufschlag greift.", 25, 1, 1000, "Blöcke"));
    public static final Setting MILESTONES = register(Setting.bool("expand.milestone-announcements", Category.EXPAND, Material.GOAT_HORN,
            "Meilensteine", "Chat-Nachricht und Sound bei 10, 25, 50, 100, ... Blöcken.", true));

    // Level
    public static final Setting PAYMENT_MODE = register(Setting.choice("levels.payment-mode", Category.LEVELS, Material.GOLD_INGOT,
            "Bezahlmodus", "Jeder für sich: eigene Level. Team-Pool: alle teilen sich eine XP-Leiste. Überweisen: eigene Level, die man anderen schicken kann (/gl pay).",
            PaymentMode.PLAYER));
    public static final Setting VANILLA_XP = register(Setting.bool("levels.vanilla-xp", Category.LEVELS, Material.DIAMOND_ORE,
            "Vanilla-XP", "Normale XP aus Erzen, Mobs, Öfen usw. zählt.", true));
    public static final Setting TIME_LEVELS = register(Setting.bool("levels.time-levels", Category.LEVELS, Material.SUNFLOWER,
            "Zeit-Level", "Es gibt automatisch Level für Spielzeit.", true));
    public static final Setting TIME_INTERVAL = register(Setting.integer("levels.time-interval-minutes", Category.LEVELS, Material.CLOCK,
            "Zeit-Intervall", "Alle wie viele Minuten es Zeit-Level gibt.", 5, 1, 120, "min"));
    public static final Setting TIME_AMOUNT = register(Setting.integer("levels.time-amount", Category.LEVELS, Material.EMERALD,
            "Zeit-Level Menge", "Wie viele Level es pro Intervall gibt.", 1, 1, 20, "Level"));
    public static final Setting START_LEVELS = register(Setting.integer("levels.start-levels", Category.LEVELS, Material.CHEST,
            "Start-Level", "Mit wie vielen Leveln jeder (bzw. der Pool) startet.", 5, 0, 200, "Level"));

    // Timer
    public static final Setting TIMER_ACTIONBAR = register(Setting.bool("timer.actionbar", Category.TIMER, Material.NAME_TAG,
            "Timer in Actionbar", "Zeigt den Challenge-Timer über der Hotbar.", true));
    public static final Setting TIMER_SIDEBAR = register(Setting.bool("timer.sidebar", Category.TIMER, Material.PAINTING,
            "Scoreboard", "Zeigt das Scoreboard rechts mit Timer, Spielzeit und Feld-Infos.", true));
    public static final Setting TIMER_RUN_EMPTY = register(Setting.bool("timer.run-when-empty", Category.TIMER, Material.BARRIER,
            "Läuft ohne Spieler", "Challenge-Timer läuft weiter, auch wenn niemand online ist. Zeit-Level gibt es trotzdem nur, wenn jemand spielt.", false));

    // Dimensionen
    public static final Setting NETHER_MULTIPLIER = register(Setting.integer("dimensions.nether-cost-multiplier", Category.DIMENSIONS, Material.NETHERRACK,
            "Nether-Kosten x", "Multiplikator für die Kosten im Nether.", 1, 1, 10, "x"));
    public static final Setting END_MULTIPLIER = register(Setting.integer("dimensions.end-cost-multiplier", Category.DIMENSIONS, Material.END_STONE,
            "End-Kosten x", "Multiplikator für die Kosten im (äußeren) End.", 1, 1, 10, "x"));
    public static final Setting END_FREE_RADIUS = register(Setting.integer("dimensions.end-free-radius", Category.DIMENSIONS, Material.DRAGON_HEAD,
            "Freie Drachen-Insel", "Radius um die End-Mitte ohne Border (Hauptinsel). Dahinter, im äußeren End, gilt wieder die Border.",
            200, 0, 1000, "Blöcke"));

    // Tod
    public static final Setting DEATH_MODE = register(Setting.choice("death.mode", Category.DEATH, Material.TOTEM_OF_UNDYING,
            "Tod-Modus", "Normal: im Feld respawnen. Hardcore: stirbt einer, ist die Runde für alle vorbei.", DeathMode.NORMAL));

    // Border
    public static final Setting BORDER_STYLE = register(Setting.choice("border.style", Category.BORDER, Material.PAINTING,
            "Darstellung", "Laser-Vorhang: leuchtender Schimmer über dem Boden mit dünnen Linien am Gelände. Partikel-Wand: rote Partikel an den Kanten.",
            BorderStyle.LINE));
    public static final Setting BORDER_COLOR = register(Setting.choice("border.color", Category.BORDER, Material.RED_DYE,
            "Farbe", "Farbe der Border (Linie und Partikel).", BorderColor.RED));
    public static final Setting BORDER_HARD_STOP = register(Setting.bool("border.hard-stop", Category.BORDER, Material.IRON_BARS,
            "Harter Stopp", "Du prallst an der Border ab wie an einer Wand (persönliche Vanilla-Border genau an der Kante, auf die du zuläufst). Solange du dagegen läufst, kannst du dahinter nicht abbauen – stehen bleiben reicht. Aus: der Server setzt dich zurück.",
            true));
    public static final Setting BORDER_GLOW_HEIGHT = register(Setting.integer("border.glow-height", Category.BORDER, Material.LIGHT,
            "Schimmer-Höhe", "Wie hoch der leuchtende Vorhang über dem Boden ausblendet, in Zehntel-Blöcken (26 = 2,6 Blöcke).",
            26, 0, 60, "/10 Blöcke"));
    public static final Setting BORDER_GLOW_STRENGTH = register(Setting.integer("border.glow-strength", Category.BORDER, Material.GLOWSTONE_DUST,
            "Schimmer-Stärke", "Wie kräftig der Vorhang ist (Deckkraft in Prozent).", 28, 0, 100, "%"));
    public static final Setting BORDER_LINE_WIDTH = register(Setting.integer("border.line-width", Category.BORDER, Material.STRING,
            "Linien-Dicke", "Dicke der scharfen Linien am Gelände, in Hundertstel-Blöcken (3 = 0,03 Blöcke).",
            3, 1, 20, "/100 Blöcke"));
    public static final Setting BORDER_VIEW = register(Setting.integer("border.view-distance", Category.BORDER, Material.SPYGLASS,
            "Sichtweite", "Bis zu welcher Entfernung die Border gezeichnet wird.", 16, 4, 48, "Blöcke"));
    public static final Setting BORDER_DENSITY = register(Setting.integer("border.density", Category.BORDER, Material.REDSTONE,
            "Partikel-Dichte", "Wie dicht die Partikelwand ist (nur bei Darstellung Partikel/Beides).", 2, 1, 4, ""));
    public static final Setting MOBS_CAN_ENTER = register(Setting.bool("border.mobs-can-enter", Category.BORDER, Material.ZOMBIE_HEAD,
            "Monster dürfen rein", "Ob Monster von außen ins Feld laufen dürfen.", true));

    // Spawn
    public static final Setting SPAWN_RADIUS = register(Setting.integer("spawn.search-radius", Category.SPAWN, Material.FILLED_MAP,
            "Suchradius", "In welchem Radius um 0/0 zufällig nach dem Spawn gesucht wird.", 3000, 200, 30000, "Blöcke"));

    // MOTD
    public static final Setting MOTD_ENABLED = register(Setting.bool("motd.enabled", Category.MOTD, Material.OAK_SIGN,
            "Dynamische MOTD", "Die Server-Nachricht in der Serverliste zeigt Live-Stats und wechselnde Sprüche.", true));
    public static final Setting MOTD_STATS = register(Setting.bool("motd.show-stats", Category.MOTD, Material.WRITABLE_BOOK,
            "Stats anzeigen", "Timer, Feldgröße und Status erscheinen in der Rotation.", true));
    public static final Setting MOTD_INTERVAL = register(Setting.integer("motd.interval-seconds", Category.MOTD, Material.REPEATER,
            "Wechsel alle", "Wie oft die zweite MOTD-Zeile wechselt.", 5, 2, 60, "s"));
    public static final Setting MOTD_HOVER = register(Setting.bool("motd.hover-stats", Category.MOTD, Material.PLAYER_HEAD,
            "Hover-Infos", "Beim Drüberfahren über die Spielerzahl erscheinen Stats statt Spielernamen.", true));
    public static final Setting MOTD_ICON = register(Setting.bool("motd.server-icon", Category.MOTD, Material.ITEM_FRAME,
            "Server-Icon", "Zeigt das GridLock-Icon in der Serverliste. Eigenes Bild: plugins/GridLock/server-icon.png ersetzen und /gl reload.",
            true));

    private final JavaPlugin plugin;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static Setting register(Setting setting) {
        REGISTRY.put(setting.key(), setting);
        return setting;
    }

    public static Map<String, Setting> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public static List<Setting> byCategory(Category category) {
        List<Setting> list = new ArrayList<>();
        for (Setting setting : REGISTRY.values()) {
            if (setting.category() == category) {
                list.add(setting);
            }
        }
        return list;
    }

    /** Fills in missing keys with defaults and repairs invalid values. */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;
        for (Setting setting : REGISTRY.values()) {
            Object raw = config.get(setting.key());
            if (raw == null || setting.parse(String.valueOf(raw)) == null) {
                config.set(setting.key(), setting.defaultValue());
                changed = true;
            }
        }
        if (changed) {
            plugin.saveConfig();
        }
    }

    public Object get(Setting setting) {
        Object parsed = setting.parse(String.valueOf(plugin.getConfig().get(setting.key(), setting.defaultValue())));
        return parsed != null ? parsed : setting.defaultValue();
    }

    public boolean bool(Setting setting) {
        return (Boolean) get(setting);
    }

    public int integer(Setting setting) {
        return (Integer) get(setting);
    }

    public <E extends Enum<E>> E choice(Setting setting, Class<E> type) {
        return Enum.valueOf(type, String.valueOf(get(setting)));
    }

    public void set(Setting setting, Object value) {
        plugin.getConfig().set(setting.key(), value);
        plugin.saveConfig();
    }

    /** Steps a value like a GUI click would: toggles bools, cycles choices, adds {@code delta} to ints. */
    public Object step(Setting setting, int delta) {
        Object current = get(setting);
        Object next = switch (setting.type()) {
            case BOOL -> !(Boolean) current;
            case INT -> Math.max(setting.min(), Math.min(setting.max(), (Integer) current + delta));
            case CHOICE -> {
                List<String> choices = setting.choices();
                int index = choices.indexOf(String.valueOf(current));
                int size = choices.size();
                yield choices.get(((index + (delta >= 0 ? 1 : -1)) % size + size) % size);
            }
        };
        set(setting, next);
        return next;
    }

    public List<String> slogans() {
        return plugin.getConfig().getStringList("motd.slogans");
    }
}
