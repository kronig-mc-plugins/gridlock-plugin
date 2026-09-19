package net.kronig.gridlock.spawn;

import net.kronig.gridlock.field.FieldManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BiomeSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Finds a safe 1x1 start column matching a spawn preset. */
public final class SpawnFinder {

    private static final int MAX_ATTEMPTS = 12;
    private static final int BIOME_SEARCH_RADIUS = 2500;
    private static final int MIN_TRUNK_HEIGHT = 4;
    private static final int[][] STAND_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    private final Plugin plugin;

    public SpawnFinder(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Location> find(World world, SpawnPreset preset, int radius) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        attempt(world, preset, radius, 0, result);
        return result;
    }

    private void attempt(World world, SpawnPreset preset, int radius, int attempt, CompletableFuture<Location> result) {
        if (attempt >= MAX_ATTEMPTS) {
            // Give up on the preset's wish and take any safe land spot near the world spawn.
            Location spawn = world.getSpawnLocation();
            loadAround(world, spawn).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                Location spot = scanOpen(world, spawn.getBlockX(), spawn.getBlockZ());
                result.complete(spot != null ? spot : FieldManager.safeSpot(world, spawn.getBlockX(), spawn.getBlockZ(),
                        world.getHighestBlockYAt(spawn) + 1));
            }));
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location target = new Location(world, random.nextInt(-radius, radius + 1), 64, random.nextInt(-radius, radius + 1));
        if (preset == SpawnPreset.WORLDSPAWN) {
            // The world spawn itself; if it is not safe, the fallback below finds land right next to it.
            attempt(world, preset, radius, MAX_ATTEMPTS, result);
            return;
        }
        if (!preset.biomes().isEmpty()) {
            BiomeSearchResult found = world.locateNearestBiome(target, BIOME_SEARCH_RADIUS,
                    preset.biomes().toArray(new org.bukkit.block.Biome[0]));
            if (found == null) {
                attempt(world, preset, radius, attempt + 1, result);
                return;
            }
            target = found.getLocation();
        }
        Location center = target;
        loadAround(world, center).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            Location spot = preset.nextToTree()
                    ? scanTree(world, center.getBlockX(), center.getBlockZ())
                    : scanOpen(world, center.getBlockX(), center.getBlockZ());
            if (spot != null) {
                result.complete(spot);
            } else {
                attempt(world, preset, radius, attempt + 1, result);
            }
        }));
    }

    private static CompletableFuture<Void> loadAround(World world, Location center) {
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.add(world.getChunkAtAsync(cx + dx, cz + dz));
            }
        }
        return CompletableFuture.allOf(chunks.toArray(new CompletableFuture[0]));
    }

    private static Block surface(World world, int x, int z) {
        return world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    /** Closest safe land column to (x,z) within the loaded 3x3 chunk area. */
    private static Location scanOpen(World world, int x, int z) {
        for (int r = 0; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    Location spot = standOn(surface(world, x + dx, z + dz));
                    if (spot != null) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    /** A safe column right next to a tree trunk, closest to (x,z). */
    private static Location scanTree(World world, int x, int z) {
        List<Block> trunks = new ArrayList<>();
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                Block top = surface(world, x + dx, z + dz);
                if (Tag.LOGS.isTagged(top.getType())) {
                    trunks.add(top);
                }
            }
        }
        trunks.sort(Comparator.comparingDouble(b -> Math.hypot(b.getX() - x, b.getZ() - z)));
        for (Block trunkTop : trunks) {
            Block base = trunkTop;
            while (Tag.LOGS.isTagged(base.getRelative(0, -1, 0).getType())) {
                base = base.getRelative(0, -1, 0);
            }
            // Skip stumps and fallen logs – we want a real, standing tree.
            if (trunkTop.getY() - base.getY() + 1 < MIN_TRUNK_HEIGHT) {
                continue;
            }
            for (int[] offset : STAND_OFFSETS) {
                Block ground = surface(world, base.getX() + offset[0], base.getZ() + offset[1]);
                if (Math.abs(ground.getY() - (base.getY() - 1)) > 1) {
                    continue;
                }
                Location spot = standOn(ground);
                if (spot != null) {
                    return spot;
                }
            }
        }
        return null;
    }

    private static Location standOn(Block ground) {
        if (Tag.LOGS.isTagged(ground.getType()) || Tag.LEAVES.isTagged(ground.getType())) {
            return null;
        }
        Block feet = ground.getRelative(0, 1, 0);
        if (!FieldManager.isSafe(feet)) {
            return null;
        }
        return new Location(ground.getWorld(), ground.getX() + 0.5, feet.getY(), ground.getZ() + 0.5);
    }
}
