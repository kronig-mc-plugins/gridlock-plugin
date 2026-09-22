package net.kronig.gridlock.field;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Selling the column a player stands on back for a share of its price. */
public final class BuybackManager {

    private final GridLockPlugin plugin;
    private final FieldManager fields;

    public BuybackManager(GridLockPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
    }

    public boolean enabled() {
        return plugin.settings().bool(Settings.BUYBACK_ENABLED);
    }

    /** Refund for the column the player stands on, in hundredths of a level (e.g. 25 = a quarter level). */
    public int refundHundredths(World world) {
        int size = fields.size(world);
        if (size <= 1) {
            return 0;
        }
        return fields.costAt(world, size - 1) * plugin.settings().integer(Settings.BUYBACK_PERCENT);
    }

    /** Whole levels of a refund, for display. */
    public int refund(World world) {
        return refundHundredths(world) / 100;
    }

    public static String formatLevels(int hundredths) {
        return String.format(java.util.Locale.GERMANY, "%.2f", hundredths / 100.0);
    }



    /** Why the player cannot sell the column they stand on, or null if they can. */
    public String blocker(Player player) {
        if (!enabled()) {
            return "Der Rückkauf ist ausgeschaltet.";
        }
        if (!fields.isRestricted(player)) {
            return "Das geht nur im Feld während einer Runde.";
        }
        World world = player.getWorld();
        Location at = player.getLocation();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        if (!fields.isUnlocked(world, x, z)) {
            return "Du stehst auf keinem gekauften Block.";
        }
        if (fields.isOrigin(world, x, z)) {
            return "Der Startblock kann nicht verkauft werden.";
        }
        if (!fields.isEdge(world, x, z)) {
            return "Nur Blöcke am Rand des Feldes können verkauft werden.";
        }
        if (!fields.staysConnected(world, x, z)) {
            return "Ohne diesen Block würde das Feld auseinanderbrechen.";
        }
        for (Player other : world.getPlayers()) {
            if (!other.equals(player) && other.getLocation().getBlockX() == x && other.getLocation().getBlockZ() == z) {
                return Text.escape(other.getName()) + " steht auf diesem Block.";
            }
        }
        return null;
    }

    public boolean sell(Player player) {
        String blocker = blocker(player);
        if (blocker != null) {
            player.sendMessage(Text.prefixed("<red>" + blocker));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        World world = player.getWorld();
        Location at = player.getLocation();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int hundredths = refundHundredths(world);
        // Step off first, so nobody stands outside for even a tick.
        // The sold column itself must not count, so lock it first and undo if there is nowhere to go.
        fields.lock(world, x, z);
        Location destination = fields.safeNearby(world, at, 4, 6);
        if (destination == null) {
            long[] target = fields.nearestNeighbour(world, x, z);
            if (target != null) {
                destination = FieldManager.safeSpot(world, (int) target[0], (int) target[1], at.getY());
            }
        }
        if (destination == null) {
            fields.unlock(world, x, z);
            player.sendMessage(Text.prefixed("<red>Kein sicherer Nachbarblock zum Ausweichen gefunden."));
            return false;
        }
        destination.setYaw(at.getYaw());
        destination.setPitch(at.getPitch());
        player.teleport(destination);
        // Fractions of a level go straight into the XP bar as progress towards the next level.
        plugin.levels().refundHundredths(player, hundredths);
        plugin.data().blocksSold++;

        world.spawnParticle(Particle.SMOKE, new Location(world, x + 0.5, at.getY() + 0.5, z + 0.5), 20, 0.3, 0.5, 0.3, 0.01);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.9f);
        player.sendMessage(Text.prefixed("<gray>Block verkauft: <green>+" + formatLevels(hundredths) + " Level</green> <dark_gray>("
                + plugin.settings().integer(Settings.BUYBACK_PERCENT) + " %"
                + (plugin.levels().mode() == net.kronig.gridlock.config.PaymentMode.POOL ? ", Team-Pool" : "")
                + ")</dark_gray><gray>. Feld: <white>" + fields.size(world) + " Blöcke"));
        for (Player other : world.getPlayers()) {
            if (!other.equals(player)) {
                other.sendMessage(Text.prefixed("<gray>" + Text.escape(player.getName()) + " hat einen Block verkauft <dark_gray>("
                        + fields.size(world) + ")"));
            }
        }
        return true;
    }
}
