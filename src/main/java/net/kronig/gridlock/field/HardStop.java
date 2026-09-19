package net.kronig.gridlock.field;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hard stop at the border, done by the client itself.
 *
 * <p>A vanilla client only collides with real blocks and with the world border. So every player gets a personal
 * (virtual) world border that is huge, but positioned so that one of its sides lies exactly on the field edge the
 * player is heading for – per axis, so a corner gives two walls. The rest of the square is thousands of blocks
 * away, which keeps the field free-form.
 *
 * <p>The client refuses to break or place blocks beyond its world border, so the wall only exists while the player
 * actually moves towards an edge. Standing still or walking away removes it and everything outside is clickable.
 */
public final class HardStop implements Listener {

    /** Side length of the personal border; one side is the wall, the others are out of reach. */
    private static final double SIZE = 8192;
    /** How many blocks ahead an edge is turned into a wall (gives the packet time to arrive). */
    private static final int LOOKAHEAD = 3;
    /** A remembered movement delta counts for this many ticks (momentum without key input: jumps, ice, knockback). */
    private static final int DELTA_TICKS = 4;
    /** Shrinking borders are drawn red by the client; 1 block per ~115 days is invisible drift. */
    private static final long SHRINK_TICKS = 200_000_000L;

    private record Wall(boolean x, boolean z, double wallX, double wallZ, int signX, int signZ) {
        static final Wall NONE = new Wall(false, false, 0, 0, 0, 0);
    }

    private record Delta(double x, double z, long tick) {
    }

    private final GridLockPlugin plugin;
    private final FieldManager fields;
    private final Map<UUID, WorldBorder> borders = new HashMap<>();
    private final Map<UUID, Wall> current = new HashMap<>();
    private final Map<UUID, Delta> deltas = new HashMap<>();
    private long tick;

    public HardStop(GridLockPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        if (dx * dx + dz * dz > 0.0004) {
            deltas.put(event.getPlayer().getUniqueId(), new Delta(dx, dz, tick));
        }
    }

    /** Every tick. */
    public void tick() {
        tick++;
        boolean enabled = plugin.settings().bool(Settings.BORDER_HARD_STOP);
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Mod players collide with the field on their own client and need no vanilla border.
            Wall wall = enabled && fields.isRestricted(player) && !player.isInsideVehicle() && !plugin.modLink().hasMod(player)
                    ? compute(player) : Wall.NONE;
            apply(player, wall);
        }
    }

    private Wall compute(Player player) {
        Location location = player.getLocation();
        if (!fields.isAllowed(location)) {
            return Wall.NONE; // outside (rescue pending): never lock anyone out of the field
        }
        double moveX = 0;
        double moveZ = 0;
        Input input = player.getCurrentInput();
        double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        double strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
        if (forward != 0 || strafe != 0) {
            double yaw = Math.toRadians(location.getYaw());
            moveX = forward * -Math.sin(yaw) + strafe * Math.cos(yaw);
            moveZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        } else {
            Delta delta = deltas.get(player.getUniqueId());
            if (delta != null && tick - delta.tick() <= DELTA_TICKS) {
                moveX = delta.x();
                moveZ = delta.z();
            }
        }
        double length = Math.hypot(moveX, moveZ);
        if (length < 1.0E-4) {
            return Wall.NONE;
        }
        moveX /= length;
        moveZ /= length;

        World world = player.getWorld();
        int bx = location.getBlockX();
        int bz = location.getBlockZ();
        int signX = Math.abs(moveX) > 0.2 ? (int) Math.signum(moveX) : 0;
        int signZ = Math.abs(moveZ) > 0.2 ? (int) Math.signum(moveZ) : 0;
        Double wallX = signX == 0 ? null : edgeAhead(world, bx, bz, signX, true);
        Double wallZ = signZ == 0 ? null : edgeAhead(world, bx, bz, signZ, false);
        if (wallX == null && wallZ == null) {
            return Wall.NONE;
        }
        return new Wall(wallX != null, wallZ != null, wallX != null ? wallX : 0, wallZ != null ? wallZ : 0, signX, signZ);
    }

    /** Coordinate of the first field edge ahead in the player's row/column, or null within the lookahead. */
    private Double edgeAhead(World world, int bx, int bz, int sign, boolean alongX) {
        for (int step = 1; step <= LOOKAHEAD; step++) {
            int x = alongX ? bx + sign * step : bx;
            int z = alongX ? bz : bz + sign * step;
            if (!fields.isAllowed(world, x, z)) {
                int column = alongX ? x : z;
                return (double) (sign > 0 ? column : column + 1);
            }
        }
        return null;
    }

    private void apply(Player player, Wall wall) {
        UUID id = player.getUniqueId();
        Wall previous = current.getOrDefault(id, Wall.NONE);
        if (wall.equals(previous)) {
            return;
        }
        current.put(id, wall);
        if (!wall.x() && !wall.z()) {
            player.setWorldBorder(null); // back to the world's own border
            return;
        }
        WorldBorder border = borders.computeIfAbsent(id, key -> {
            WorldBorder created = Bukkit.createWorldBorder();
            created.setWarningDistance(0);
            created.setWarningTimeTicks(0);
            created.setDamageAmount(0);
            return created;
        });
        double half = SIZE / 2;
        Location location = player.getLocation();
        // Wall on the side we move towards: east/south wall -> centre lies half a size before it, and vice versa.
        double centerX = wall.x() ? wall.wallX() - wall.signX() * half : location.getX();
        double centerZ = wall.z() ? wall.wallZ() - wall.signZ() * half : location.getZ();
        border.setCenter(centerX, centerZ);
        border.setSize(SIZE);
        border.changeSize(SIZE - 1, SHRINK_TICKS);
        player.setWorldBorder(border);
    }

    public void forget(Player player) {
        borders.remove(player.getUniqueId());
        current.remove(player.getUniqueId());
        deltas.remove(player.getUniqueId());
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Wall.NONE.equals(current.getOrDefault(player.getUniqueId(), Wall.NONE))) {
                player.setWorldBorder(null);
            }
        }
        borders.clear();
        current.clear();
        deltas.clear();
    }
}
