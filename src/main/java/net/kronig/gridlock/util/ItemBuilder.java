package net.kronig.gridlock.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack stack;
    private final List<Component> lore = new ArrayList<>();
    private Component name;
    private boolean glow;
    private NamespacedKey tagKey;
    private String tagValue;

    private ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder name(String miniMessage) {
        this.name = Text.item(miniMessage);
        return this;
    }

    public ItemBuilder lore(String miniMessage) {
        this.lore.add(Text.item(miniMessage));
        return this;
    }

    public ItemBuilder lore(List<String> miniMessages) {
        miniMessages.forEach(this::lore);
        return this;
    }

    /** Adds a gray, word-wrapped description. */
    public ItemBuilder description(String plain) {
        for (String line : Text.wrap(plain, 34)) {
            lore("<gray>" + Text.escape(line));
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, String value) {
        this.tagKey = key;
        this.tagValue = value;
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = stack.getItemMeta();
        if (name != null) {
            meta.itemName(name);
            meta.customName(name);
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.values());
        if (tagKey != null) {
            meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tagValue);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
