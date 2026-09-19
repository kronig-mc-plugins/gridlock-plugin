package net.kronig.gridlock.field;

import net.kronig.gridlock.GridLockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makes the border physically solid without changing any blocks.
 *
 * <p>Shulkers are the only mobs a vanilla client collides with like a block, so a grid of tiny invisible
 * shulkers (1/16 of a block) is placed on the boundary planes around each player. The client stops itself,
 * hard and without the server having to pull the player back. The posts are far too small to get in the way
 * of clicking, so breaking, placing and picking up outside the field still works.
 */
public final class ShulkerWall implements Listener {

    /** Size of a post; the vanilla minimum for the scale attribute. */
    private static final double POST_SCALE = 0.0625;
    /** Post centres along an edge (player width is 0.6, so nobody fits through). */
    private static final double[] ALONG = {0.25, 0.75};
    /** Post heights above the player's feet: standing, stepping up and jumping are all covered. */
    private static final double[] HEIGHTS = {0.3, 1.1, 1.9, 2.7};
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final GridLockPlugin plugin;
    private final FieldManager fields;
    private final NamespacedKey tag;
    private final Map<UUID, List<Shulker>> posts = new HashMap<>();
    private final Map<UUID, String> lastLayout = new HashMap<>();

    public ShulkerWall(GridLockPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
        this.tag = new NamespacedKey(plugin, "border_post");
    }

    /** Removes posts left behind by a crash. */
    public void cleanupLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getType() == EntityType.SHULKER && entity.getPersistentDataContainer().has(tag)) {
                    entity.remove();
                }
            }
        }
    }

    /** Rebuilds the grid around one player (cheap no-op while they stay in the same block). */
    public void update(Player player) {
        if (!player.isOnline() || !fields.isRestricted(player)) {
            clear(player);
            return;
        }
        World world = player.getWorld();
        Location location = player.getLocation();
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();

        List<Location> targets = new ArrayList<>();
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                if (!fields.isAllowed(world, x, z)) {
                    continue;
                }
                for (int[] dir : DIRECTIONS) {
                    if (fields.isAllowed(world, x + dir[0], z + dir[1])) {
                        continue;
                    }
                    boolean alongZ = dir[0] != 0;
                    double plane = alongZ ? (dir[0] > 0 ? x + 1 : x) : (dir[1] > 0 ? z + 1 : z);
                    for (double along : ALONG) {
                        for (double height : HEIGHTS) {
                            targets.add(alongZ
                                    ? new Location(world, plane, by + height, z + along)
                                    : new Location(world, x + along, by + height, plane));
                        }
                    }
                }
            }
        }

        String layout = layoutKey(world, targets);
        if (layout.equals(lastLayout.get(player.getUniqueId()))) {
            return;
        }
        lastLayout.put(player.getUniqueId(), layout);
        apply(player, world, targets);
    }

    private static String layoutKey(World world, List<Location> targets) {
        StringBuilder key = new StringBuilder(world.getUID().toString());
        for (Location location : targets) {
            key.append(';').append(location.getX()).append(',').append(location.getY()).append(',').append(location.getZ());
        }
        return key.toString();
    }

    private void apply(Player player, World world, List<Location> targets) {
        List<Shulker> pool = posts.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        pool.removeIf(shulker -> !shulker.isValid());
        for (int i = 0; i < targets.size(); i++) {
            Location target = targets.get(i);
            if (i < pool.size()) {
                Shulker shulker = pool.get(i);
                if (!shulker.getWorld().equals(world)) {
                    shulker.remove();
                    pool.set(i, spawnPost(target));
                } else {
                    shulker.teleport(target);
                }
            } else {
                pool.add(spawnPost(target));
            }
        }
        while (pool.size() > targets.size()) {
            pool.remove(pool.size() - 1).remove();
        }
    }

    private Shulker spawnPost(Location location) {
        return location.getWorld().spawn(location, Shulker.class, shulker -> {
            shulker.setAI(false);
            shulker.setAware(false);
            shulker.setSilent(true);
            shulker.setInvisible(true);
            shulker.setInvulnerable(true);
            shulker.setGravity(false);
            shulker.setPersistent(false);
            shulker.setRemoveWhenFarAway(false);
            shulker.setPeek(0f);
            shulker.setAttachedFace(BlockFace.DOWN);
            AttributeInstance scale = shulker.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(POST_SCALE);
            }
            shulker.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
    }

    public void clear(Player player) {
        List<Shulker> pool = posts.remove(player.getUniqueId());
        lastLayout.remove(player.getUniqueId());
        if (pool != null) {
            pool.forEach(Entity::remove);
        }
    }

    public void clearAll() {
        posts.values().forEach(pool -> pool.forEach(Entity::remove));
        posts.clear();
        lastLayout.clear();
    }

    /** After the field changed: the new block must not stay walled off. */
    public void refreshWorld(World world) {
        for (Player player : world.getPlayers()) {
            lastLayout.remove(player.getUniqueId());
            update(player);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            update(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> update(event.getPlayer()));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        clear(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> update(event.getPlayer()));
    }

    /** Posts are scenery: they take no damage and never target anyone. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(tag)) {
            event.setCancelled(true);
        }
    }
}
