package net.kronig.gridlock.game;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Wall;

import java.util.Random;

/**
 * Builds the lobby: a floating island whose surface is a glowing red grid – an unlocked grass field in the
 * middle, dark locked tiles around it – plus a few themed mini islands in the distance. Deterministic (fixed
 * seed), so every rebuild looks the same.
 */
final class LobbyBuilder {

    static final int TOP = 100;
    static final int RADIUS = 16;
    /** Distance between the red grid lines. */
    private static final int GRID = 4;

    private final World world;
    private final Random random = new Random(1337);

    LobbyBuilder(World world) {
        this.world = world;
    }

    void build() {
        clear(-20, 20, TOP - 2, TOP + 4);
        mainIsland();
        connectWalls();
        centerPedestal();
        miniIsland(-27, TOP - 4, -8, 4, Island.FOREST);
        miniIsland(25, TOP + 3, -12, 4, Island.DESERT);
        miniIsland(22, TOP - 6, 22, 4, Island.NETHER);
        miniIsland(-20, TOP + 5, 24, 4, Island.END);
    }

    private void clear(int min, int max, int minY, int maxY) {
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                for (int y = minY; y <= maxY; y++) {
                    set(x, y, z, Material.AIR);
                }
            }
        }
    }

    // ------------------------------------------------------------------ main island

    private void mainIsland() {
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > RADIUS + 0.3) {
                    continue;
                }
                surface(x, z, distance);
                underside(x, z, distance, RADIUS, 16);
            }
        }
    }

    /**
     * Blocks are placed without physics (fast, no falling sand on the mini islands), so walls do not know their
     * neighbours yet. Compute the connections of the rim wall by hand.
     */
    private void connectWalls() {
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                Block block = world.getBlockAt(x, TOP + 1, z);
                if (!(block.getBlockData() instanceof Wall wall)) {
                    continue;
                }
                boolean straightX = true;
                boolean straightZ = true;
                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
                    boolean connected = block.getRelative(face).getBlockData() instanceof Wall;
                    wall.setHeight(face, connected ? Wall.Height.LOW : Wall.Height.NONE);
                    if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                        straightX &= !connected;
                        straightZ &= connected;
                    } else {
                        straightX &= connected;
                        straightZ &= !connected;
                    }
                }
                // The centre post disappears only on a straight run, like vanilla does it.
                wall.setUp(!(straightX || straightZ));
                block.setBlockData(wall, false);
            }
        }
    }

    private void surface(int x, int z, double distance) {
        if (distance > RADIUS - 0.7) {
            // Rim with a low wall so nobody walks off by accident.
            set(x, TOP, z, Material.POLISHED_BLACKSTONE_BRICKS);
            set(x, TOP + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            set(x, TOP - 1, z, Material.STONE);
            return;
        }
        boolean line = Math.floorMod(x, GRID) == 0 || Math.floorMod(z, GRID) == 0;
        if (line) {
            set(x, TOP, z, Material.RED_STAINED_GLASS);
            set(x, TOP - 1, z, Material.SHROOMLIGHT);
            return;
        }
        int cellX = Math.floorDiv(x, GRID);
        int cellZ = Math.floorDiv(z, GRID);
        boolean unlocked = cellX >= -1 && cellX <= 0 && cellZ >= -1 && cellZ <= 0;
        Material top;
        if (unlocked) {
            top = random.nextInt(6) == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
        } else {
            top = (cellX + cellZ) % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            if (random.nextInt(14) == 0) {
                top = Material.CRACKED_DEEPSLATE_TILES;
            }
        }
        set(x, TOP, z, top);
        set(x, TOP - 1, z, unlocked ? Material.DIRT : Material.STONE);
    }

    /** Inverted, slightly noisy cone with ores and dripstone underneath an island. */
    private void underside(int x, int z, double distance, int radius, int depth) {
        double edgeFactor = distance / (radius + 0.3);
        int columnDepth = (int) Math.round(depth * Math.pow(1 - edgeFactor, 0.7)) + random.nextInt(2);
        for (int d = 2; d <= columnDepth; d++) {
            int y = TOP - d;
            Material material;
            if (d <= 3) {
                material = Material.DIRT;
            } else if (d < depth * 0.55) {
                material = ore(Material.STONE, false);
            } else {
                material = ore(Material.DEEPSLATE, true);
            }
            set(x, y, z, material);
        }
        if (columnDepth > 3 && random.nextInt(9) == 0) {
            Block tip = world.getBlockAt(x, TOP - columnDepth - 1, z);
            tip.setType(Material.POINTED_DRIPSTONE, false);
        }
    }

    private Material ore(Material base, boolean deep) {
        int roll = random.nextInt(100);
        if (roll < 4) {
            return deep ? Material.DEEPSLATE_COAL_ORE : Material.COAL_ORE;
        }
        if (roll < 7) {
            return deep ? Material.DEEPSLATE_IRON_ORE : Material.IRON_ORE;
        }
        if (roll < 9) {
            return deep ? Material.DEEPSLATE_COPPER_ORE : Material.COPPER_ORE;
        }
        if (roll < 10) {
            return deep ? Material.DEEPSLATE_DIAMOND_ORE : Material.GOLD_ORE;
        }
        if (roll < 13) {
            return Material.TUFF;
        }
        return base;
    }

    // ------------------------------------------------------------------ center

    /** The "1x1 field": a single grass block framed by glowing red glass, the logo floats above it. */
    private void centerPedestal() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                if (ring == 2) {
                    set(x, TOP + 1, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
                } else if (ring == 1) {
                    set(x, TOP + 1, z, Material.RED_STAINED_GLASS);
                    set(x, TOP, z, Material.SHROOMLIGHT);
                } else {
                    set(x, TOP + 1, z, Material.GRASS_BLOCK);
                    set(x, TOP, z, Material.DIRT);
                }
            }
        }
    }

    // ------------------------------------------------------------------ mini islands

    private enum Island { FOREST, DESERT, NETHER, END }

    private void miniIsland(int cx, int top, int cz, int radius, Island type) {
        Material surface = switch (type) {
            case FOREST -> Material.GRASS_BLOCK;
            case DESERT -> Material.SAND;
            case NETHER -> Material.NETHERRACK;
            case END -> Material.END_STONE;
        };
        Material filler = switch (type) {
            case FOREST -> Material.DIRT;
            case DESERT -> Material.SANDSTONE;
            case NETHER -> Material.NETHERRACK;
            case END -> Material.END_STONE;
        };
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > radius + 0.3) {
                    continue;
                }
                set(cx + x, top, cz + z, surface);
                int depth = (int) Math.round((radius + 2) * (1 - distance / (radius + 0.5))) + 1;
                for (int d = 1; d <= depth; d++) {
                    set(cx + x, top - d, cz + z, d > 2 && type != Island.END ? Material.STONE : filler);
                }
            }
        }
        switch (type) {
            case FOREST -> world.generateTree(new Location(world, cx, top + 1, cz), TreeType.TREE);
            case DESERT -> {
                for (int y = 1; y <= 3; y++) {
                    set(cx, top + y, cz, Material.CACTUS);
                }
                set(cx + 2, top + 1, cz - 1, Material.DEAD_BUSH);
            }
            case NETHER -> netherPortal(cx - 1, top + 1, cz);
            case END -> {
                set(cx, top + 1, cz, Material.BEDROCK);
                set(cx, top + 2, cz, Material.DRAGON_EGG);
                set(cx + 2, top + 1, cz + 1, Material.END_ROD);
            }
        }
    }

    /** A small lit 2x3 nether portal (frame along X). */
    private void netherPortal(int x, int y, int z) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                boolean frame = dx == -1 || dx == 2 || dy == -1 || dy == 3;
                if (!frame) {
                    continue;
                }
                set(x + dx, y + dy, z, Material.OBSIDIAN);
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                block.setType(Material.NETHER_PORTAL, false);
                if (block.getBlockData() instanceof Orientable orientable) {
                    orientable.setAxis(Axis.X);
                    block.setBlockData(orientable, false);
                }
            }
        }
    }

    private void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
