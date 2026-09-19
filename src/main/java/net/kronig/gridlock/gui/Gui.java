package net.kronig.gridlock.gui;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.util.ItemBuilder;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Base class for all chest menus. Every slot can carry its own click handler. */
public abstract class Gui implements InventoryHolder {

    @FunctionalInterface
    public interface ClickHandler {
        void click(ClickType type);
    }

    protected final GridLockPlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();

    protected Gui(GridLockPlugin plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.mm(title));
    }

    protected abstract void render();

    public void open() {
        refresh();
        viewer.openInventory(inventory);
    }

    public void refresh() {
        handlers.clear();
        inventory.clear();
        render();
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item, ClickHandler handler) {
        inventory.setItem(slot, item);
        handlers.put(slot, handler);
    }

    protected void frame(Material edge, Material accent) {
        int size = inventory.getSize();
        ItemStack edgeItem = ItemBuilder.of(edge).name(" ").build();
        ItemStack accentItem = ItemBuilder.of(accent).name(" ").build();
        for (int slot = 0; slot < size; slot++) {
            boolean border = slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
            if (border) {
                boolean corner = slot == 0 || slot == 8 || slot == size - 9 || slot == size - 1;
                inventory.setItem(slot, corner ? accentItem : edgeItem);
            }
        }
    }

    protected boolean requireAdmin() {
        if (viewer.hasPermission(GridLockPlugin.ADMIN_PERMISSION)) {
            return true;
        }
        viewer.sendMessage(Text.prefixed("<red>Nur Admins können das ändern."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        return false;
    }

    protected void click() {
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    void handle(int slot, ClickType type) {
        ClickHandler handler = handlers.get(slot);
        if (handler != null) {
            handler.click(type);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
