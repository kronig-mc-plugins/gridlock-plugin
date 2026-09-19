package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Category;
import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class MainMenu extends Gui {

    private static final int[] CATEGORY_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};

    public MainMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, "<dark_gray>» <gradient:#ff3b3b:#ff9e3b><bold>GridLock</bold></gradient> <dark_gray>Menü");
    }

    @Override
    protected void render() {
        frame(Material.BLACK_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE);
        GameData data = plugin.data();
        boolean admin = viewer.hasPermission(GridLockPlugin.ADMIN_PERMISSION);

        ItemBuilder info = ItemBuilder.of(Material.NETHER_STAR).glow(true)
                .name("<gradient:#ff3b3b:#ff9e3b><bold>GridLock</bold></gradient> <gray>1x1-Challenge")
                .lore("<dark_gray>────────────────")
                .lore("<gray>Status: " + stateLabel(data.state))
                .lore("<gray>Timer: <gold>" + Text.time(data.timerSeconds) + (data.timerPaused ? " <red>(pausiert)" : ""))
                .lore("<gray>Level-Modus: <white>" + plugin.levels().mode().displayName());
        for (World world : Bukkit.getWorlds()) {
            if (plugin.fields().isChallengeWorld(world) && plugin.fields().size(world) > 0) {
                info.lore("<gray>Feld " + dimension(world) + ": <white>" + plugin.fields().size(world) + " Blöcke");
            }
        }
        SpawnPreset preset = SpawnPreset.parse(data.preset);
        if (preset != null) {
            info.lore("<gray>Spawn: <white>" + preset.displayName() + " " + preset.difficulty().format());
        }
        set(4, info.build());

        for (int i = 0; i < Category.values().length && i < CATEGORY_SLOTS.length; i++) {
            Category category = Category.values()[i];
            set(CATEGORY_SLOTS[i], ItemBuilder.of(category.icon())
                    .name("<" + category.color() + "><bold>" + category.displayName())
                    .description(category.description())
                    .lore("")
                    .lore("<yellow>▶ Klick zum Öffnen")
                    .build(), type -> {
                click();
                new CategoryMenu(plugin, viewer, category).open();
            });
        }

        set(45, ItemBuilder.of(Material.COMPASS)
                .name("<aqua><bold>Spawn-Auswahl")
                .description(data.state == GameState.LOBBY
                        ? "Stimme ab, wo ihr startet – mit Schwierigkeitsgrad."
                        : "Zeigt, mit welchem Spawn diese Runde gestartet ist.")
                .lore("")
                .lore("<yellow>▶ Klick zum Öffnen")
                .build(), type -> {
            click();
            new SpawnMenu(plugin, viewer).open();
        });

        set(49, ItemBuilder.of(Material.BARRIER).name("<red>Schließen").build(), type -> viewer.closeInventory());

        boolean sidebarHidden = data.hiddenSidebar.contains(viewer.getUniqueId().toString());
        set(46, ItemBuilder.of(sidebarHidden ? Material.GRAY_DYE : Material.LIME_DYE)
                .name("<white><bold>Dein Scoreboard: " + (sidebarHidden ? "<red>AUS" : "<green>AN"))
                .description("Blendet das Fenster rechts nur für dich ein oder aus.")
                .lore("")
                .lore("<yellow>▶ Klick zum Umschalten")
                .build(), type -> {
            click();
            viewer.performCommand("gl scoreboard");
            refresh();
        });

        if (plugin.levels().mode() == PaymentMode.TRANSFER && data.state.isIngame()) {
            set(52, ItemBuilder.of(Material.EXPERIENCE_BOTTLE).glow(true)
                    .name("<green><bold>Level überweisen")
                    .description("Schick anderen Spielern Level von deiner XP-Leiste.")
                    .lore("")
                    .lore("<yellow>▶ Klick zum Öffnen")
                    .build(), type -> {
                click();
                new TransferMenu(plugin, viewer).open();
            });
        }

        set(53, ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name("<white><bold>Befehle")
                .lore("<gray>/gl <dark_gray>– dieses Menü")
                .lore("<gray>/gl config [key] [wert] <dark_gray>– Einstellung")
                .lore("<gray>/gl spawn <dark_gray>– Spawn-Auswahl")
                .lore("<gray>/gl info <dark_gray>– Feld-Infos")
                .lore("<gray>/gl pay [spieler] [level] <dark_gray>– überweisen")
                .lore("<gray>/gl scoreboard <dark_gray>– Scoreboard an/aus")
                .lore("<gray>/gl help <dark_gray>– alle Befehle (inkl. Admin)")
                .build());

        if (!admin) {
            return;
        }
        switch (data.state) {
            case LOBBY -> set(51, ItemBuilder.of(Material.LIME_CONCRETE).glow(true)
                    .name("<green><bold>Challenge starten")
                    .description("Sucht den Spawn mit den meisten Stimmen und startet den Countdown für alle.")
                    .lore("")
                    .lore("<yellow>▶ Klick zum Starten")
                    .build(), type -> {
                viewer.closeInventory();
                plugin.game().start(viewer);
            });
            case STARTING -> set(51, ItemBuilder.of(Material.YELLOW_CONCRETE)
                    .name("<yellow><bold>Startet gerade…").build());
            default -> {
                set(47, ItemBuilder.of(data.timerPaused ? Material.LIME_DYE : Material.ORANGE_DYE)
                        .name(data.timerPaused ? "<green><bold>Timer fortsetzen" : "<gold><bold>Timer pausieren")
                        .lore("<gray>Aktuell: <gold>" + Text.time(data.timerSeconds))
                        .lore("")
                        .lore("<yellow>▶ Klick")
                        .build(), type -> {
                    click();
                    plugin.timer().setPaused(!data.timerPaused, viewer);
                    refresh();
                });
                set(51, ItemBuilder.of(Material.TNT)
                        .name("<red><bold>Neue Runde")
                        .description("Löscht Oberwelt, Nether und End und startet den Server neu. Danach geht es wieder in die Lobby.")
                        .lore("")
                        .lore("<yellow>▶ Klick (mit Bestätigung)")
                        .build(), type -> {
                    click();
                    new ConfirmResetMenu(plugin, viewer).open();
                });
            }
        }
    }

    static String stateLabel(GameState state) {
        return switch (state) {
            case LOBBY -> "<aqua>Lobby";
            case STARTING -> "<yellow>Startet…";
            case RUNNING -> "<green>Läuft";
            case WON -> "<gold>Gewonnen!";
            case LOST -> "<red>Verloren";
        };
    }

    private static String dimension(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "<red>Nether</red>";
            case THE_END -> "<light_purple>End</light_purple>";
            default -> "<green>Oberwelt</green>";
        };
    }
}
