package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ConfirmResetMenu extends Gui {

    public ConfirmResetMenu(GridLockPlugin plugin, Player viewer) {
        super(plugin, viewer, 3, "<dark_gray>» <red><bold>Wirklich neue Runde?");
    }

    @Override
    protected void render() {
        frame(Material.BLACK_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE);
        set(11, ItemBuilder.of(Material.LIME_CONCRETE)
                .name("<green><bold>Ja, zurücksetzen")
                .description("Oberwelt, Nether und End werden gelöscht, der Server fährt herunter. "
                        + "Beim nächsten Start gibt es eine neue Welt und es geht zurück in die Lobby.")
                .build(), type -> {
            if (!requireAdmin()) {
                return;
            }
            viewer.closeInventory();
            plugin.game().resetRound(viewer);
        });
        set(13, ItemBuilder.of(Material.TNT)
                .name("<red><bold>Achtung")
                .description("Das kann nicht rückgängig gemacht werden. Der Server muss danach neu gestartet werden "
                        + "(passiert automatisch, wenn ein Restart-Skript läuft).")
                .build());
        set(15, ItemBuilder.of(Material.RED_CONCRETE).name("<red><bold>Abbrechen").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
    }
}
