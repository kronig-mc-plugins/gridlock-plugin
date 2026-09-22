package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ConfirmSellMenu extends Gui {

    public ConfirmSellMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 3, "<dark_gray>» <gold><bold>Block verkaufen?");
    }

    @Override
    protected void render() {
        frame(Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        String blocker = plugin.buyback().blocker(viewer);
        int hundredths = plugin.buyback().refundHundredths(viewer.getWorld());
        int credit = plugin.buyback().credit(viewer);
        if (blocker != null) {
            set(13, ItemBuilder.of(Material.BARRIER).name("<red><bold>Geht gerade nicht").description(blocker).build());
        } else {
            set(11, ItemBuilder.of(Material.LIME_CONCRETE)
                    .name("<green><bold>Ja, verkaufen")
                    .description("Der Block, auf dem du stehst, wird wieder gesperrt. Du wirst auf den Nachbarblock gestellt.")
                    .lore("")
                    .lore("<gray>Wert: <green>" + net.kronig.gridlock.field.BuybackManager.formatLevels(hundredths) + " Level</green> <dark_gray>("
                            + plugin.settings().integer(Settings.BUYBACK_PERCENT) + " %)")
                    .lore("<gray>Guthaben danach: <white>" + net.kronig.gridlock.field.BuybackManager.formatLevels((credit + hundredths) % 100)
                            + "</white> <dark_gray>· ausgezahlt: <green>+" + (credit + hundredths) / 100 + " Level")
                    .lore("<dark_gray>Bruchteile werden gesammelt, bei 1,00 gibt es ein Level.")
                    .build(), type -> {
                viewer.closeInventory();
                plugin.buyback().sell(viewer);
            });
            set(13, ItemBuilder.of(Material.GRASS_BLOCK)
                    .name("<white><bold>Dein Block")
                    .lore("<gray>" + viewer.getLocation().getBlockX() + ", " + viewer.getLocation().getBlockZ())
                    .build());
        }
        set(15, ItemBuilder.of(Material.RED_CONCRETE).name("<red><bold>Abbrechen").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
    }
}
