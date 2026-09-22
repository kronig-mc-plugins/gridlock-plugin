package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.stats.StatsManager;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Current round per player, plus the all-time best rounds. */
public final class StatsMenu extends Gui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public StatsMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, "<dark_gray>» <gold><bold>Statistiken");
    }

    @Override
    protected void render() {
        frame(Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        GameData data = plugin.data();
        ItemBuilder round = ItemBuilder.of(Material.CLOCK).glow(true).name("<gold><bold>Diese Runde");
        for (String line : plugin.stats().summaryLines()) {
            round.lore(line);
        }
        set(4, round.build());

        List<StatsManager.PlayerRecord> players = plugin.stats().currentPlayers();
        for (int i = 0; i < players.size() && i < SLOTS.length; i++) {
            StatsManager.PlayerRecord record = players.get(i);
            ItemBuilder item = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name((i == 0 && record.blocks > 0 ? "<gold>★ " : "<white>") + "<bold>" + Text.escape(record.name))
                    .lore("<gray>Blöcke gekauft: <green>" + record.blocks)
                    .lore("<gray>Level ausgegeben: <aqua>" + record.levelsSpent)
                    .lore("<gray>Tode: <red>" + record.deaths)
                    .lore("<gray>Spielzeit: <yellow>" + Text.time(record.playtime));
            Player online = Bukkit.getPlayerExact(record.name);
            if (online != null) {
                item.skull(online);
            }
            set(SLOTS[i], item.build());
        }

        ItemBuilder best = ItemBuilder.of(Material.GOLDEN_APPLE).name("<gold><bold>Bestenliste")
                .lore("<gray>Schnellste gewonnene Runden");
        List<StatsManager.RoundRecord> wins = plugin.stats().bestRounds(5);
        if (wins.isEmpty()) {
            best.lore("<dark_gray>Noch keine gewonnene Runde.");
        }
        int place = 1;
        for (StatsManager.RoundRecord win : wins) {
            best.lore("<yellow>" + place++ + ". <white>" + Text.time(win.seconds) + " <gray>" + Text.escape(win.preset)
                    + " <dark_gray>(" + win.totalBlocks + " Blöcke, " + Text.escape(win.date) + ")");
        }
        best.lore("").lore("<dark_gray>Runden gesamt: " + plugin.stats().roundsPlayed());
        set(53, best.build());

        set(45, ItemBuilder.of(Material.ARROW).name("<yellow>« Menü").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
        set(49, ItemBuilder.of(Material.BARRIER).name("<red>Schließen").build(), type -> viewer.closeInventory());
    }
}
