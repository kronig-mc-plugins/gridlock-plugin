package net.kronig.gridlock.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lets short-lived messages (expansion progress, hints) take priority over the timer in the action bar. */
public final class ActionBars {

    private final Map<UUID, Long> lockedUntil = new HashMap<>();

    public void show(Player player, Component message, long millis) {
        lockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
        player.sendActionBar(message);
    }

    public boolean isFree(Player player) {
        Long until = lockedUntil.get(player.getUniqueId());
        return until == null || until < System.currentTimeMillis();
    }

    public void forget(Player player) {
        lockedUntil.remove(player.getUniqueId());
    }
}
