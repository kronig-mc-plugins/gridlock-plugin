package net.kronig.gridlock.field;

import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BorderListener implements Listener {

    /** How long (ms) a portal arrival may take before the safety net is allowed to step in again. */
    private static final long ARRIVAL_GRACE_MS = 10_000;

    private final GridLockPlugin plugin;
    private final FieldManager fields;
    private final ExpansionManager expansion;
    private final Map<UUID, TeleportCause> pendingCause = new HashMap<>();
    private final Map<UUID, Long> pendingSince = new HashMap<>();

    public BorderListener(GridLockPlugin plugin, FieldManager fields, ExpansionManager expansion) {
        this.plugin = plugin;
        this.fields = fields;
        this.expansion = expansion;
    }

    // ------------------------------------------------------------------ walking

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        int fx = from.getBlockX();
        int fz = from.getBlockZ();
        int tx = to.getBlockX();
        int tz = to.getBlockZ();
        if (fx == tx && fz == tz) {
            return;
        }
        Player player = event.getPlayer();
        if (!fields.isRestricted(player) || isArriving(player) || !fields.isAllowed(from) || fields.isAllowed(to)) {
            return;
        }
        World world = to.getWorld();

        // Which locked column is the player pushing into? Cardinal neighbours win over the diagonal.
        boolean xBlocked = tx != fx && !fields.isAllowed(world, tx, fz);
        boolean zBlocked = tz != fz && !fields.isAllowed(world, fx, tz);
        double dx = Math.abs(to.getX() - from.getX());
        double dz = Math.abs(to.getZ() - from.getZ());
        int targetX;
        int targetZ;
        if (xBlocked && (!zBlocked || dx >= dz)) {
            targetX = tx;
            targetZ = fz;
        } else if (zBlocked) {
            targetX = fx;
            targetZ = tz;
        } else {
            targetX = tx;
            targetZ = tz;
        }

        // Slide along the border instead of stopping dead.
        double nx = to.getX();
        double nz = to.getZ();
        if (!fields.isAllowed(world, tx, fz)) {
            nx = clampToColumn(nx, fx);
        }
        if (!fields.isAllowed(world, (int) Math.floor(nx), tz)) {
            nz = clampToColumn(nz, fz);
        }
        Location clamped = to.clone();
        clamped.setX(nx);
        clamped.setZ(nz);
        if (!fields.isAllowed(clamped)) {
            clamped.setX(from.getX());
            clamped.setZ(from.getZ());
        }
        event.setTo(clamped);
        expansion.onBlocked(player, targetX, targetZ);
    }

    private static double clampToColumn(double value, int column) {
        return Math.max(column + 0.01, Math.min(column + 0.99, value));
    }

    // ------------------------------------------------------------------ teleports & portals

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        TeleportCause cause = event.getCause();
        if (cause == TeleportCause.END_GATEWAY) {
            markArrival(player, cause);
            Bukkit.getScheduler().runTaskLater(plugin, () -> handleArrival(player), 3L);
            return;
        }
        if (isItemTeleport(cause) && fields.isRestricted(player) && !fields.isAllowed(event.getTo())) {
            event.setCancelled(true);
            player.sendMessage(Text.prefixed("<red>Du kannst dich nicht aus dem Feld teleportieren."));
        }
    }

    private static boolean isItemTeleport(TeleportCause cause) {
        return cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CONSUMABLE_EFFECT;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        markArrival(player, event.getCause());
        if (event.getCause() == TeleportCause.END_GATEWAY) {
            // Same world, so no PlayerChangedWorldEvent will follow.
            Bukkit.getScheduler().runTaskLater(plugin, () -> handleArrival(player), 3L);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (pendingCause.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> handleArrival(player), 2L);
        }
    }

    private void markArrival(Player player, TeleportCause cause) {
        pendingCause.put(player.getUniqueId(), cause);
        pendingSince.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isArriving(Player player) {
        Long since = pendingSince.get(player.getUniqueId());
        if (since == null) {
            return false;
        }
        if (System.currentTimeMillis() - since > ARRIVAL_GRACE_MS) {
            pendingSince.remove(player.getUniqueId());
            pendingCause.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** A portal always opens a new 1x1 field where it drops the player (plus the portal itself). */
    private void handleArrival(Player player) {
        TeleportCause cause = pendingCause.remove(player.getUniqueId());
        pendingSince.remove(player.getUniqueId());
        if (cause == null || !player.isOnline() || !fields.isRestricted(player)) {
            return;
        }
        Location location = player.getLocation();
        World world = location.getWorld();
        if (fields.isAllowed(location)) {
            return;
        }
        if (cause == TeleportCause.END_PORTAL && world.getEnvironment() == World.Environment.NORMAL) {
            player.teleport(plugin.game().spawnLocation());
            return;
        }
        Set<Long> columns = new LinkedHashSet<>();
        columns.add(FieldManager.pack(location.getBlockX(), location.getBlockZ()));
        if (cause == TeleportCause.NETHER_PORTAL) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        if (location.clone().add(x, y, z).getBlock().getType() == Material.NETHER_PORTAL) {
                            columns.add(FieldManager.pack(location.getBlockX() + x, location.getBlockZ() + z));
                        }
                    }
                }
            }
        }
        boolean firstInWorld = fields.size(world) == 0;
        for (long column : columns) {
            fields.unlock(world, FieldManager.unpackX(column), FieldManager.unpackZ(column));
        }
        String dimension = ExpansionManager.dimensionName(world);
        Bukkit.broadcast(Text.prefixed((firstInWorld ? "<light_purple>Neue Dimension!</light_purple> " : "")
                + "<gray>Portal-Feld im <white>" + dimension + "</white> bei <white>" + location.getBlockX() + ", "
                + location.getBlockZ() + "</white> freigeschaltet <dark_gray>(" + columns.size() + " Blöcke)"));
    }

    // ------------------------------------------------------------------ vehicles & mobs

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Vehicle vehicle = event.getVehicle();
        if (!carriesRestrictedPlayer(vehicle) || !fields.isAllowed(from) || fields.isAllowed(to)) {
            return;
        }
        vehicle.setVelocity(new Vector());
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        vehicle.teleport(back);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Entity entity = event.getEntity();
        if (carriesRestrictedPlayer(entity)) {
            if (fields.isAllowed(from) && !fields.isAllowed(to)) {
                event.setCancelled(true);
            }
            return;
        }
        if (entity instanceof Enemy
                && !plugin.settings().bool(Settings.MOBS_CAN_ENTER)
                && plugin.data().state.isIngame()
                && fields.isChallengeWorld(to.getWorld())
                && !fields.isEndFreeZone(to.getWorld(), to.getBlockX(), to.getBlockZ())
                && !fields.isAllowed(from) && fields.isAllowed(to)) {
            event.setCancelled(true);
        }
    }

    private boolean carriesRestrictedPlayer(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player && fields.isRestricted(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ respawn & safety net

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.data().state.isIngame()) {
            return;
        }
        Location location = event.getRespawnLocation();
        if (fields.isChallengeWorld(location.getWorld()) && !fields.isAllowed(location)) {
            event.setRespawnLocation(plugin.game().spawnLocation());
        }
    }

    /** Runs periodically: anyone who ended up outside (pistons, glitches, horses...) is put back. */
    public void safetyNet() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!fields.isRestricted(player) || isArriving(player) || fields.isAllowed(player.getLocation())) {
                continue;
            }
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            rescue(player);
        }
    }

    public void rescue(Player player) {
        World world = player.getWorld();
        Location location = player.getLocation();
        long[] target = fields.nearestAllowed(world, location.getBlockX(), location.getBlockZ(), 8);
        if (target == null) {
            target = fields.origin(world);
        }
        Location destination = target == null
                ? plugin.game().spawnLocation()
                : FieldManager.safeSpot(world, (int) target[0], (int) target[1], location.getY());
        destination.setYaw(location.getYaw());
        destination.setPitch(location.getPitch());
        player.teleport(destination);
        player.sendMessage(Text.prefixed("<gray>Du warst außerhalb des Feldes und wurdest zurückgesetzt."));
    }

    public void forget(Player player) {
        pendingCause.remove(player.getUniqueId());
        pendingSince.remove(player.getUniqueId());
    }
}
