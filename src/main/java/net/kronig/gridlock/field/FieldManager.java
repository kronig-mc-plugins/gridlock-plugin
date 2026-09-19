package net.kronig.gridlock.field;

import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Holds the unlocked columns of every world and answers "may a player stand here?". */
public final class FieldManager {

    private final Settings settings;
    private final Supplier<GameData> data;
    private final Supplier<World> lobby;
    private final Map<String, Set<Long>> fields = new HashMap<>();
    private Consumer<World> unlockListener = world -> {
    };
    private ColumnListener columnListener = (world, x, z, unlocked) -> {
    };

    /** Told about every single column that is unlocked or locked again. */
    @FunctionalInterface
    public interface ColumnListener {
        void changed(World world, int x, int z, boolean unlocked);
    }

    public void onColumnChange(ColumnListener listener) {
        this.columnListener = listener;
    }

    /** All unlocked columns of a world, packed (see {@link #pack}). */
    public long[] columns(World world) {
        Set<Long> columns = fields.get(world.getName());
        if (columns == null) {
            return new long[0];
        }
        return columns.stream().mapToLong(Long::longValue).toArray();
    }

    public FieldManager(Settings settings, Supplier<GameData> data, Supplier<World> lobby) {
        this.settings = settings;
        this.data = data;
        this.lobby = lobby;
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    public void loadFrom(GameData gameData) {
        fields.clear();
        gameData.fields.forEach((world, columns) -> fields.put(world, new HashSet<>(columns)));
    }

    public void saveTo(GameData gameData) {
        gameData.fields.clear();
        fields.forEach((world, columns) -> gameData.fields.put(world, new ArrayList<>(columns)));
    }

    /** Called whenever a column is unlocked or locked again. */
    public void onUnlock(Consumer<World> listener) {
        this.unlockListener = listener;
    }

    public void clear() {
        fields.clear();
    }

    public boolean isChallengeWorld(World world) {
        World lobbyWorld = lobby.get();
        return lobbyWorld == null || !lobbyWorld.equals(world);
    }

    /** True if this world's border is enforced for the player right now (everyone except spectators). */
    public boolean isRestricted(Player player) {
        return data.get().state.isIngame()
                && player.getGameMode() != GameMode.SPECTATOR
                && isChallengeWorld(player.getWorld());
    }

    public boolean isAllowed(World world, int x, int z) {
        if (isEndFreeZone(world, x, z)) {
            return true;
        }
        Set<Long> columns = fields.get(world.getName());
        return columns != null && columns.contains(pack(x, z));
    }

    public boolean isAllowed(Location location) {
        return isAllowed(location.getWorld(), location.getBlockX(), location.getBlockZ());
    }

    public boolean isEndFreeZone(World world, int x, int z) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }
        long radius = settings.integer(Settings.END_FREE_RADIUS);
        double cx = x + 0.5;
        double cz = z + 0.5;
        return cx * cx + cz * cz <= radius * radius;
    }

    /** @return true if the column was newly unlocked */
    public boolean unlock(World world, int x, int z) {
        Set<Long> columns = fields.computeIfAbsent(world.getName(), k -> new HashSet<>());
        boolean added = columns.add(pack(x, z));
        if (added && columns.size() == 1) {
            data.get().fieldOrigins.put(world.getName(), pack(x, z));
        }
        if (added) {
            unlockListener.accept(world);
            columnListener.changed(world, x, z, true);
        }
        return added;
    }

    /** @return true if the column was unlocked before */
    public boolean lock(World world, int x, int z) {
        Set<Long> columns = fields.get(world.getName());
        boolean removed = columns != null && columns.remove(pack(x, z));
        if (removed) {
            unlockListener.accept(world);
            columnListener.changed(world, x, z, false);
        }
        return removed;
    }

    public int size(World world) {
        Set<Long> columns = fields.get(world.getName());
        return columns == null ? 0 : columns.size();
    }

    public int totalSize() {
        int total = 0;
        for (Set<Long> columns : fields.values()) {
            total += columns.size();
        }
        return total;
    }

    /** Level cost of the next block in this world. */
    public int nextCost(World world) {
        int base = settings.integer(Settings.COST_BASE);
        int increase = settings.integer(Settings.COST_INCREASE);
        int every = settings.integer(Settings.COST_INCREASE_EVERY);
        int unlocked = Math.max(0, size(world) - 1);
        int cost = base + (unlocked / every) * increase;
        int multiplier = switch (world.getEnvironment()) {
            case NETHER -> settings.integer(Settings.NETHER_MULTIPLIER);
            case THE_END -> settings.integer(Settings.END_MULTIPLIER);
            default -> 1;
        };
        return cost * multiplier;
    }

    /** Finds the closest allowed column (in blocks, Chebyshev rings) or null. */
    public long[] nearestAllowed(World world, int x, int z, int maxRadius) {
        for (int r = 0; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (isAllowed(world, x + dx, z + dz)) {
                        return new long[]{x + dx, z + dz};
                    }
                }
            }
        }
        return null;
    }

    /** A column of this world's field to fall back on, or null if the world has none. */
    public long[] origin(World world) {
        Long origin = data.get().fieldOrigins.get(world.getName());
        if (origin != null && isAllowed(world, unpackX(origin), unpackZ(origin))) {
            return new long[]{unpackX(origin), unpackZ(origin)};
        }
        Set<Long> columns = fields.get(world.getName());
        if (columns != null && !columns.isEmpty()) {
            long any = columns.iterator().next();
            return new long[]{unpackX(any), unpackZ(any)};
        }
        if (world.getEnvironment() == World.Environment.THE_END && settings.integer(Settings.END_FREE_RADIUS) > 0) {
            return new long[]{0, 0};
        }
        return null;
    }

    /**
     * Finds a safe standing spot in a column, preferring heights close to {@code preferredY}.
     */
    public static Location safeSpot(World world, int x, int z, double preferredY) {
        int start = (int) Math.floor(preferredY);
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight() - 2;
        for (int offset = 0; offset < world.getMaxHeight() - world.getMinHeight(); offset++) {
            for (int y : new int[]{start - offset, start + offset}) {
                if (y < min || y > max) {
                    continue;
                }
                if (isSafe(world.getBlockAt(x, y, z))) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        int top = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, top + 1, z + 0.5);
    }

    /** Feet block: passable and not dangerous, head passable, ground solid. */
    public static boolean isSafe(Block feet) {
        Block ground = feet.getRelative(0, -1, 0);
        Block head = feet.getRelative(0, 1, 0);
        return ground.getType().isSolid()
                && !isDangerous(ground)
                && feet.isPassable() && !feet.isLiquid() && !isDangerous(feet)
                && head.isPassable() && !head.isLiquid();
    }

    private static boolean isDangerous(Block block) {
        return switch (block.getType()) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH,
                 POWDER_SNOW, WITHER_ROSE, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }
}
