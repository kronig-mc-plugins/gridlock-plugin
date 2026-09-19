package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Send levels to other players (payment mode TRANSFER). Click amounts: left 1, right 5, shift 10. */
public final class TransferMenu extends Gui {

    private static final int[] PLAYER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34
    };

    public TransferMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, "<dark_gray>» <green><bold>Level überweisen");
    }

    @Override
    protected void render() {
        frame(Material.GREEN_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE);
        set(4, ItemBuilder.of(Material.EXPERIENCE_BOTTLE).glow(true)
                .name("<green><bold>Deine Level: <white>" + viewer.getLevel())
                .description("Klick auf einen Spieler, um ihm Level von deiner XP-Leiste zu schicken.")
                .lore("")
                .lore("<yellow>Links</yellow> <gray>1 Level   <yellow>Rechts</yellow> <gray>5 Level")
                .lore("<yellow>Shift</yellow> <gray>+ Klick: 10 Level")
                .build());

        if (plugin.levels().mode() != PaymentMode.TRANSFER) {
            set(22, ItemBuilder.of(Material.BARRIER)
                    .name("<red>Überweisen ist gerade aus")
                    .description("Nur im Bezahlmodus \"" + PaymentMode.TRANSFER.displayName() + "\" möglich.")
                    .build());
        } else {
            List<Player> others = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(viewer) && player.getGameMode() != GameMode.SPECTATOR
                        && plugin.fields().isChallengeWorld(player.getWorld())) {
                    others.add(player);
                }
            }
            others.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            if (others.isEmpty()) {
                set(22, ItemBuilder.of(Material.GRAY_DYE).name("<gray>Niemand da, dem du Level schicken kannst.").build());
            }
            for (int i = 0; i < others.size() && i < PLAYER_SLOTS.length; i++) {
                Player target = others.get(i);
                set(PLAYER_SLOTS[i], ItemBuilder.of(Material.PLAYER_HEAD).skull(target)
                        .name("<white><bold>" + Text.escape(target.getName()))
                        .lore("<gray>Hat: <green>" + target.getLevel() + " Level")
                        .lore("")
                        .lore("<yellow>Links</yellow> <gray>+1   <yellow>Rechts</yellow> <gray>+5   <yellow>Shift</yellow> <gray>+10")
                        .build(), type -> {
                    int amount = type.isShiftClick() ? 10 : type.isRightClick() ? 5 : 1;
                    if (!target.isOnline()) {
                        refresh();
                        return;
                    }
                    if (!plugin.levels().transfer(viewer, target, amount)) {
                        viewer.sendMessage(Text.prefixed("<red>Du hast nur " + viewer.getLevel() + " Level."));
                        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                    }
                    refresh();
                });
            }
        }

        set(45, ItemBuilder.of(Material.ARROW).name("<yellow>« Menü").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
        set(49, ItemBuilder.of(Material.BARRIER).name("<red>Schließen").build(), type -> viewer.closeInventory());
    }
}
