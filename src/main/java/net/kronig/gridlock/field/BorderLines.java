package net.kronig.gridlock.field;

import net.kronig.gridlock.config.BorderColor;
import net.kronig.gridlock.config.BorderStyle;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Draws the border as a solid glowing line lying on the ground: one thin block display per edge
 * between an unlocked and a locked column. Only edges near players exist as entities.
 */
public final class BorderLines {

    /** Edge between column (x,z) inside the field and its neighbour in direction {@code dir}. */
    private record Edge(UUID world, int x, int z, int dir) {
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final float WIDTH = 0.14f;
    private static final float THICKNESS = 0.03f;
    private static final Material[] PUSH_STAGES = {
            Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE
    };

    private final Settings settings;
    private final FieldManager fields;
    private final ExpansionManager expansion;
    private final Supplier<GameData> data;
    private final Map<Edge, BlockDisplay> lines = new HashMap<>();

    public BorderLines(Settings settings, FieldManager fields, ExpansionManager expansion, Supplier<GameData> data) {
        this.settings = settings;
        this.fields = fields;
        this.expansion = expansion;
        this.data = data;
    }

    /** Creates, moves and removes line segments around all players. */
    public void update() {
        if (!data.get().state.isIngame() || !settings.choice(Settings.BORDER_STYLE, BorderStyle.class).line()) {
            clear();
            return;
        }
        Material base = settings.choice(Settings.BORDER_COLOR, BorderColor.class).block();
        int view = Math.max(settings.integer(Settings.BORDER_VIEW), 12);
        Set<Edge> wanted = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (player.getGameMode() == GameMode.SPECTATOR || !fields.isChallengeWorld(world)) {
                continue;
            }
            int bx = player.getLocation().getBlockX();
            int bz = player.getLocation().getBlockZ();
            for (int x = bx - view; x <= bx + view; x++) {
                for (int z = bz - view; z <= bz + view; z++) {
                    if (!fields.isAllowed(world, x, z)) {
                        continue;
                    }
                    for (int dir = 0; dir < DIRECTIONS.length; dir++) {
                        if (!fields.isAllowed(world, x + DIRECTIONS[dir][0], z + DIRECTIONS[dir][1])) {
                            Edge edge = new Edge(world.getUID(), x, z, dir);
                            if (wanted.add(edge)) {
                                place(world, edge, material(world, edge, base));
                            }
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<Edge, BlockDisplay>> iterator = lines.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Edge, BlockDisplay> entry = iterator.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
    }

    /** Recolours edges players are currently pushing against (runs often, only touches pushed edges). */
    public void updatePushes() {
        if (lines.isEmpty()) {
            return;
        }
        Material base = settings.choice(Settings.BORDER_COLOR, BorderColor.class).block();
        for (Map.Entry<Edge, BlockDisplay> entry : lines.entrySet()) {
            BlockDisplay display = entry.getValue();
            if (!display.isValid()) {
                continue;
            }
            Material material = material(display.getWorld(), entry.getKey(), base);
            if (display.getBlock().getMaterial() != material) {
                display.setBlock(material.createBlockData());
            }
        }
    }

    private Material material(World world, Edge edge, Material base) {
        int nx = edge.x() + DIRECTIONS[edge.dir()][0];
        int nz = edge.z() + DIRECTIONS[edge.dir()][1];
        for (Player player : world.getPlayers()) {
            ExpansionManager.Push push = expansion.activePush(player);
            if (push != null && push.targets(world, nx, nz)) {
                double progress = expansion.progress(push);
                int stage = Math.min(PUSH_STAGES.length - 1, (int) (progress * PUSH_STAGES.length));
                return PUSH_STAGES[stage];
            }
        }
        return base;
    }

    private void place(World world, Edge edge, Material material) {
        int top = world.getHighestBlockYAt(edge.x(), edge.z(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (top <= world.getMinHeight()) {
            // Void column (e.g. End island edge) – nothing to lie on.
            return;
        }
        Location location = new Location(world, edge.x(), top + 1.002, edge.z());
        BlockDisplay existing = lines.get(edge);
        if (existing != null && existing.isValid()) {
            if (Math.abs(existing.getLocation().getY() - location.getY()) > 0.001) {
                existing.teleport(location);
            }
            if (existing.getBlock().getMaterial() != material) {
                existing.setBlock(material.createBlockData());
            }
            return;
        }
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBlock(material.createBlockData());
            entity.setTransformation(transformation(edge.dir()));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setShadowRadius(0f);
            entity.setShadowStrength(0f);
            entity.setViewRange(0.6f);
        });
        lines.put(edge, display);
    }

    /** Thin slab lying across the boundary, slightly longer than one block so corners close up. */
    private static Transformation transformation(int dir) {
        float half = WIDTH / 2f;
        Vector3f translation;
        Vector3f scale;
        switch (dir) {
            case 0 -> { // +X
                translation = new Vector3f(1f - half, 0f, -half);
                scale = new Vector3f(WIDTH, THICKNESS, 1f + WIDTH);
            }
            case 1 -> { // -X
                translation = new Vector3f(-half, 0f, -half);
                scale = new Vector3f(WIDTH, THICKNESS, 1f + WIDTH);
            }
            case 2 -> { // +Z
                translation = new Vector3f(-half, 0f, 1f - half);
                scale = new Vector3f(1f + WIDTH, THICKNESS, WIDTH);
            }
            default -> { // -Z
                translation = new Vector3f(-half, 0f, -half);
                scale = new Vector3f(1f + WIDTH, THICKNESS, WIDTH);
            }
        }
        return new Transformation(translation, new AxisAngle4f(), scale, new AxisAngle4f());
    }

    public void clear() {
        for (BlockDisplay display : lines.values()) {
            display.remove();
        }
        lines.clear();
    }
}
