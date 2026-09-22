package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Category;
import net.kronig.gridlock.config.Setting;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * One page per category. Every setting is shown as its icon plus a colored status pane below it;
 * clicking either changes the value.
 */
public final class CategoryMenu extends Gui {

    private static final int[] SETTING_SLOTS = {10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34};

    private final Category category;

    public CategoryMenu(GridLockPlugin plugin, Player viewer, Category category) {
        super(plugin, viewer, 6, "<dark_gray>» <" + category.color() + "><bold>" + category.displayName()
                + "</bold></" + category.color() + "> <dark_gray>Einstellungen");
        this.category = category;
    }

    @Override
    protected void render() {
        frame(Material.GRAY_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE);
        set(4, ItemBuilder.of(category.icon()).glow(true)
                .name("<" + category.color() + "><bold>" + category.displayName())
                .description(category.description())
                .build());

        List<Setting> settings = Settings.byCategory(category);
        for (int i = 0; i < settings.size() && i < SETTING_SLOTS.length; i++) {
            Setting setting = settings.get(i);
            Gui.ClickHandler handler = type -> change(setting, type);
            set(SETTING_SLOTS[i], settingItem(setting), handler);
            set(SETTING_SLOTS[i] + 9, statusPane(setting), handler);
        }

        set(45, ItemBuilder.of(Material.ARROW).name("<yellow>« Zurück").build(), type -> {
            click();
            new MainMenu(plugin, viewer).open();
        });
        set(49, ItemBuilder.of(Material.BARRIER).name("<red>Schließen").build(), type -> viewer.closeInventory());
        set(53, ItemBuilder.of(Material.WATER_BUCKET)
                .name("<aqua>Kategorie zurücksetzen")
                .description("Setzt alle Einstellungen dieser Seite auf den Standard zurück.")
                .lore("")
                .lore("<yellow>▶ Shift + Klick")
                .build(), type -> {
            if (!type.isShiftClick() || !requireAdmin()) {
                return;
            }
            for (Setting setting : settings) {
                plugin.settings().set(setting, setting.defaultValue());
            }
            plugin.onSettingsChanged();
            click();
            viewer.sendMessage(Text.prefixed("<gray>" + category.displayName() + " wurde auf Standard zurückgesetzt."));
            refresh();
        });
    }

    private ItemBuilder base(Setting setting) {
        Object value = plugin.settings().get(setting);
        boolean on = setting.type() == Setting.Type.BOOL && (Boolean) value;
        return ItemBuilder.of(setting.icon()).glow(on)
                .name("<white><bold>" + setting.name())
                .description(setting.description())
                .lore("")
                .lore("<gray>Aktuell: " + valueColor(setting, value) + "<bold>" + Text.escape(setting.formatValue(value)))
                .lore("<dark_gray>Standard: " + Text.escape(setting.formatValue(setting.defaultValue())));
    }

    private org.bukkit.inventory.ItemStack settingItem(Setting setting) {
        ItemBuilder builder = base(setting).lore("");
        Object value = plugin.settings().get(setting);
        switch (setting.type()) {
            case BOOL -> builder.lore("<yellow>Klick</yellow> <gray>zum Umschalten");
            case INT -> builder
                    .lore("<yellow>Links</yellow> <gray>+1   <yellow>Rechts</yellow> <gray>-1")
                    .lore("<yellow>Shift</yellow> <gray>+ Klick: ±10")
                    .lore("<dark_gray>Bereich: " + setting.min() + " – " + setting.max());
            case CHOICE -> {
                for (String choice : setting.choices()) {
                    boolean selected = choice.equals(String.valueOf(value));
                    builder.lore((selected ? "<green>▶ " : "<dark_gray>  ") + Text.escape(setting.formatValue(choice)));
                }
                builder.lore("<yellow>Links</yellow> <gray>weiter   <yellow>Rechts</yellow> <gray>zurück");
            }
        }
        if (lockedNow(setting)) {
            builder.lore("<red>✖ Nur in der Lobby änderbar");
        }
        return builder.lore("<dark_gray>/gl config " + setting.key()).build();
    }

    private org.bukkit.inventory.ItemStack statusPane(Setting setting) {
        Object value = plugin.settings().get(setting);
        Material pane = switch (setting.type()) {
            case BOOL -> (Boolean) value ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            case INT -> Material.YELLOW_STAINED_GLASS_PANE;
            case CHOICE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        };
        String label = setting.type() == Setting.Type.BOOL
                ? ((Boolean) value ? "<green><bold>● AN" : "<red><bold>● AUS")
                : valueColor(setting, value) + "<bold>" + Text.escape(setting.formatValue(value));
        ItemBuilder builder = ItemBuilder.of(pane).name(label).lore("<gray>" + Text.escape(setting.name()));
        if (setting.type() == Setting.Type.INT) {
            builder.amount((Integer) value);
        }
        return builder.build();
    }

    private static String valueColor(Setting setting, Object value) {
        return switch (setting.type()) {
            case BOOL -> (Boolean) value ? "<green>" : "<red>";
            case INT -> "<yellow>";
            case CHOICE -> "<aqua>";
        };
    }

    private boolean lockedNow(Setting setting) {
        return Settings.LOBBY_ONLY.contains(setting) && plugin.data().state != net.kronig.gridlock.game.GameState.LOBBY;
    }

    private void change(Setting setting, ClickType type) {
        if (!requireAdmin()) {
            return;
        }
        if (lockedNow(setting)) {
            viewer.sendMessage(Text.prefixed("<red>" + setting.name() + " lässt sich nur in der Lobby ändern – die Runde läuft schon."));
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        int delta = switch (type) {
            case RIGHT -> -1;
            case SHIFT_LEFT -> 10;
            case SHIFT_RIGHT -> -10;
            default -> 1;
        };
        Object next = plugin.settings().step(setting, delta);
        plugin.onSettingsChanged();
        click();
        viewer.sendActionBar(Text.mm("<gray>" + Text.escape(setting.name()) + " → " + valueColor(setting, next)
                + "<bold>" + Text.escape(setting.formatValue(next))));
        refresh();
    }
}
