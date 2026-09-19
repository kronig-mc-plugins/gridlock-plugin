package net.kronig.gridlock.field;

import io.papermc.paper.math.Position;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes the border physically solid on the client: invisible barrier blocks are sent (to that player only)
 * into the free spaces of the locked columns right next to the field. The client collides with them itself,
 * so players stop at the border like at a wall instead of being teleported back by the server.
 */
public final class SolidBorder implements Listener {

    private static final int RADIUS = 3;
    private static final int BELOW = 1;
    private static final int ABOVE = 3;
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final BlockData BARRIER = Material.BARRIER.createBlockData();

    private final GridLockPlugin plugin;
    private final FieldManager fields;
    /** Player -> fake barrier positions currently shown to them, plus the world they are in. */
    private final Map<UUID, Set<Long>> shown = new HashMap<>();
    private final Map<UUID, UUID> shownWorld = new HashMap<>();

    public SolidBorder(GridLockPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
    }

    private boolean active(Player player) {
        return plugin.settings().bool(Settings.BORDER_SOLID) && fields.isRestricted(player);
    }

    /** Recomputes the barriers around a player; {@code resend} repeats already shown ones (they may have been overwritten). */
    public void refresh(Player player, boolean resend) {
        UUID id = player.getUniqueId();
        Set<Long> previous = shown.getOrDefault(id, Set.of());
        World world = player.getWorld();
        if (!world.getUID().equals(shownWorld.get(id))) {
            previous = Set.of(); // different world: old fakes are gone with the old chunks
        }

        Set<Long> wanted = new HashSet<>();
        Map<Position, BlockData> changes = new HashMap<>();
        if (active(player)) {
            Location location = player.getLocation();
            int px = location.getBlockX();
            int py = location.getBlockY();
            int pz = location.getBlockZ();
            for (int x = px - RADIUS; x <= px + RADIUS; x++) {
                for (int z = pz - RADIUS; z <= pz + RADIUS; z++) {
                    if (fields.isAllowed(world, x, z) || !touchesField(world, x, z)) {
                        continue;
                    }
                    for (int y = py - BELOW; y <= py + ABOVE; y++) {
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                            continue;
                        }
                        Block block = world.getBlockAt(x, y, z);
                        if (!block.isPassable() && !block.isLiquid()) {
                            continue; // already solid for real
                        }
                        long key = pack(x, y, z);
                        wanted.add(key);
                        if (resend || !previous.contains(key)) {
                            changes.put(Position.block(x, y, z), BARRIER);
                        }
                    }
                }
            }
        }
        for (long key : previous) {
            if (!wanted.contains(key)) {
                Block block = world.getBlockAt(unpackX(key), unpackY(key), unpackZ(key));
                changes.put(Position.block(block.getX(), block.getY(), block.getZ()), block.getBlockData());
            }
        }
        if (!changes.isEmpty()) {
            player.sendMultiBlockChange(changes);
        }
        if (wanted.isEmpty()) {
            shown.remove(id);
            shownWorld.remove(id);
        } else {
            shown.put(id, wanted);
            shownWorld.put(id, world.getUID());
        }
    }

    private boolean touchesField(World world, int x, int z) {
        for (int[] offset : NEIGHBOURS) {
            if (fields.isAllowed(world, x + offset[0], z + offset[1])) {
                return true;
            }
        }
        return false;
    }

    /** Periodic resend: block updates from the server (water flow, grass growth...) overwrite fakes. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player, true);
        }
    }

    /** After the field changed in a world: the new column must not stay blocked. */
    public void refreshWorld(World world) {
        for (Player player : world.getPlayers()) {
            refresh(player, false);
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<Long> previous = shown.remove(player.getUniqueId());
            shownWorld.remove(player.getUniqueId());
            if (previous == null || previous.isEmpty()) {
                continue;
            }
            Map<Position, BlockData> changes = new HashMap<>();
            for (long key : previous) {
                Block block = player.getWorld().getBlockAt(unpackX(key), unpackY(key), unpackZ(key));
                changes.put(Position.block(block.getX(), block.getY(), block.getZ()), block.getBlockData());
            }
            player.sendMultiBlockChange(changes);
        }
    }

    public void forget(Player player) {
        shown.remove(player.getUniqueId());
        shownWorld.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ triggers

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> refresh(player, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        refreshNear(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        refreshNear(event.getBlock().getLocation());
    }

    private void later(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(player, true);
            }
        }, 2L);
    }

    private void refreshNear(Location location) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) < 64) {
                    refresh(player, true);
                }
            }
        });
    }

    // ------------------------------------------------------------------ packing (like vanilla BlockPos)

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int unpackX(long key) {
        return (int) (key >> 38);
    }

    private static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    private static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
