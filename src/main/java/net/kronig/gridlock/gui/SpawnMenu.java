package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpawnMenu extends Gui {

    private static final int[] PRESET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34
    };

    public SpawnMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, "<dark_gray>» <aqua><bold>Spawn wählen</bold></aqua>");
    }

    @Override
    protected void render() {
        frame(Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        GameData data = plugin.data();
        boolean voting = data.state == GameState.LOBBY;
        String myVote = data.votes.get(viewer.getUniqueId().toString());
        SpawnPreset leader = plugin.game().winningPreset();

        set(4, ItemBuilder.of(Material.FILLED_MAP)
                .name("<aqua><bold>Wo startet ihr?")
                .description(voting
                        ? "Jeder hat eine Stimme. Die meisten Stimmen gewinnen, bei Gleichstand entscheidet der Zufall. Ohne Stimmen: Zufall."
                        : "Die Runde läuft bereits.")
                .lore("")
                .lore("<gray>Aktuell vorne: <white>" + leader.displayName() + " " + leader.difficulty().format())
                .build());

        Map<SpawnPreset, List<String>> voters = votersByPreset(data);
        SpawnPreset chosen = SpawnPreset.parse(data.preset);
        SpawnPreset[] presets = SpawnPreset.values();
        for (int i = 0; i < presets.length && i < PRESET_SLOTS.length; i++) {
            SpawnPreset preset = presets[i];
            List<String> names = voters.getOrDefault(preset, List.of());
            boolean mine = preset.name().equals(myVote);
            boolean active = !voting && preset == chosen;
            ItemBuilder item = ItemBuilder.of(preset.icon()).glow(mine || active)
                    .amount(Math.max(1, names.size()))
                    .name("<white><bold>" + preset.displayName())
                    .lore(preset.difficulty().format())
                    .lore("")
                    .description(preset.description())
                    .lore("");
            if (voting) {
                item.lore("<gray>Stimmen: <white><bold>" + names.size());
                for (String name : names) {
                    item.lore("<dark_gray> • <gray>" + Text.escape(name));
                }
                item.lore("");
                item.lore(mine ? "<green>✔ Deine Stimme" : "<yellow>▶ Klick zum Abstimmen");
            } else if (active) {
                item.lore("<green>✔ Diese Runde");
            }
            set(PRESET_SLOTS[i], item.build(), type -> {
                if (plugin.data().state != GameState.LOBBY) {
                    return;
                }
                click();
                plugin.game().vote(viewer, preset);
                refresh();
            });
        }

        set(45, ItemBuilder.of(Material.ARROW).name("<yellow>« Menü").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
        if (voting && myVote != null) {
            set(47, ItemBuilder.of(Material.MILK_BUCKET).name("<gray>Stimme zurückziehen").build(), type -> {
                click();
                plugin.game().vote(viewer, null);
                refresh();
            });
        }
        set(49, ItemBuilder.of(Material.BARRIER).name("<red>Schließen").build(), type -> viewer.closeInventory());
        if (voting) {
            boolean ready = plugin.game().isReady(viewer);
            set(53, ItemBuilder.of(ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE).glow(ready)
                    .name(ready ? "<green><bold>Bereit ✔" : "<yellow><bold>Bereit machen")
                    .lore("<gray>Bereit: <white>" + plugin.game().readyCount() + "<gray>/<white>" + Bukkit.getOnlinePlayers().size())
                    .lore("<gray>Spawn-Favorit: <white>" + leader.displayName())
                    .lore("")
                    .lore("<yellow>▶ Klick zum Umschalten")
                    .build(), type -> {
                click();
                plugin.game().toggleReady(viewer);
                if (plugin.data().state == GameState.LOBBY) {
                    refresh();
                } else {
                    viewer.closeInventory();
                }
            });
        }
    }

    private static Map<SpawnPreset, List<String>> votersByPreset(GameData data) {
        Map<SpawnPreset, List<String>> result = new EnumMap<>(SpawnPreset.class);
        data.votes.forEach((uuid, presetName) -> {
            SpawnPreset preset = SpawnPreset.parse(presetName);
            if (preset == null) {
                return;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            String name = player.getName() != null ? player.getName() : "?";
            result.computeIfAbsent(preset, k -> new ArrayList<>()).add(name);
        });
        return result;
    }
}
