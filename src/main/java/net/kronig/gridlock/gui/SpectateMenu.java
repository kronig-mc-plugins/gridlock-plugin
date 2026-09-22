package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** For spectators: jump to a player who is still playing. */
public final class SpectateMenu extends Gui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public SpectateMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, "<dark_gray>» <aqua><bold>Zuschauen");
    }

    @Override
    protected void render() {
        frame(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE);
        set(4, ItemBuilder.of(Material.ENDER_EYE).glow(true)
                .name("<aqua><bold>Wem willst du zusehen?")
                .description("Klick auf einen Spieler, um zu ihm zu springen. Nochmal klicken heftet die Kamera an ihn.")
                .build());
        List<Player> targets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(viewer) && player.getGameMode() != GameMode.SPECTATOR
                    && plugin.fields().isChallengeWorld(player.getWorld())) {
                targets.add(player);
            }
        }
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        if (targets.isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE).name("<gray>Gerade spielt niemand.").build());
        }
        for (int i = 0; i < targets.size() && i < SLOTS.length; i++) {
            Player target = targets.get(i);
            boolean attached = target.equals(viewer.getSpectatorTarget());
            set(SLOTS[i], ItemBuilder.of(Material.PLAYER_HEAD).skull(target).glow(attached)
                    .name("<white><bold>" + Text.escape(target.getName()))
                    .lore("<gray>" + ExpansionManager.dimensionName(target.getWorld()) + " <dark_gray>·</dark_gray> <gray>"
                            + target.getLocation().getBlockX() + ", " + target.getLocation().getBlockY() + ", "
                            + target.getLocation().getBlockZ())
                    .lore("<gray>Level: <green>" + target.getLevel() + "</green>  Herzen: <red>"
                            + Math.round(target.getHealth() / 2.0))
                    .lore("")
                    .lore(attached ? "<green>✔ Kamera angeheftet" : "<yellow>▶ Klick zum Hinspringen")
                    .build(), type -> {
                if (!target.isOnline() || viewer.getGameMode() != GameMode.SPECTATOR) {
                    refresh();
                    return;
                }
                click();
                if (target.equals(viewer.getSpectatorTarget())) {
                    viewer.setSpectatorTarget(null);
                    viewer.sendMessage(Text.prefixed("<gray>Kamera gelöst."));
                } else {
                    viewer.teleport(target.getLocation());
                    viewer.setSpectatorTarget(target);
                    viewer.sendMessage(Text.prefixed("<gray>Du siehst jetzt <white>" + Text.escape(target.getName())
                            + "</white> zu. Shift löst die Kamera."));
                }
                viewer.closeInventory();
            });
        }
        set(45, ItemBuilder.of(Material.ARROW).name("<yellow>« Menü").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
        set(49, ItemBuilder.of(Material.BARRIER).name("<red>Schließen").build(), type -> viewer.closeInventory());
    }
}
