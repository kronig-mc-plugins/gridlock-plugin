package net.kronig.gridlock.field;

import net.kronig.gridlock.config.BorderColor;
import net.kronig.gridlock.config.BorderStyle;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Draws the border as a soft laser curtain on the boundary plane:
 * <ul>
 *     <li>a translucent curtain that is strongest at the ground and fades out upwards (text-display backgrounds,
 *     the only displays with real transparency). Where the terrain outside is higher, its faces in the plane get
 *     tinted as well,</li>
 *     <li>thin crisp lines along the terrain profile in the plane: top edge, floor edge, steps and corners.</li>
 * </ul>
 * Collinear edges with the same profile are merged into one run. While someone pushes against the border, the
 * whole outline of that world shifts towards green, and flashes green on every new block.
 */
public final class BorderLines {

    /**
     * A straight run on plane {@code plane} (x = plane if {@code alongZ}, else z = plane), spanning
     * {@code start .. start + length} on the other axis. {@code fieldSide} is -1/+1: on which side of the plane the
     * field lies. {@code low} = floor height inside the field, {@code high} = top of the terrain profile.
     */
    private record Run(UUID world, boolean alongZ, int plane, int fieldSide, int start, int length, int low, int high) {
    }

    /** Vertical line at a vertex of the outline, from {@code low} to {@code high}. */
    private record Post(UUID world, int x, int z, int low, int high) {
    }

    /** One edge of one column: position along the plane and its profile. */
    private record Edge(boolean alongZ, int plane, int fieldSide, int along, int low, int high) {
    }

    /** A spawned display plus the alpha its colour should have (-1 = solid line). */
    private record Part(Display display, int alpha) {
    }

    private static final float LINE = 0.03f;
    /** Fading curtain bands above the terrain profile: {height, alpha}. */
    private static final int[][] FADE_BANDS = {{45, 70}, {55, 38}, {70, 14}};
    private static final int WALL_ALPHA = 70;
    private static final long FLASH_MS = 900;
    private static final Color[] PUSH_STAGES = {
            Color.fromRGB(255, 140, 0), Color.fromRGB(255, 225, 40), Color.fromRGB(70, 235, 70)
    };
    private static final Material[] PUSH_BLOCKS = {
            Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE
    };
    private static final Color SUCCESS = Color.fromRGB(70, 255, 90);

    private final Settings settings;
    private final FieldManager fields;
    private final ExpansionManager expansion;
    private final Supplier<GameData> data;
    private final Map<Object, List<Part>> pieces = new HashMap<>();
    private final Map<UUID, Color> worldColors = new HashMap<>();
    private final Map<UUID, Long> flashUntil = new HashMap<>();

    public BorderLines(Settings settings, FieldManager fields, ExpansionManager expansion, Supplier<GameData> data) {
        this.settings = settings;
        this.fields = fields;
        this.expansion = expansion;
        this.data = data;
    }

    // ------------------------------------------------------------------ geometry

    public void update() {
        if (!data.get().state.isIngame() || !settings.choice(Settings.BORDER_STYLE, BorderStyle.class).line()) {
            clear();
            return;
        }
        int view = Math.max(settings.integer(Settings.BORDER_VIEW), 12);
        Map<UUID, Map<List<Integer>, Edge>> edgesByWorld = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (player.getGameMode() == GameMode.SPECTATOR || !fields.isChallengeWorld(world)) {
                continue;
            }
            collectEdges(world, player.getLocation(), view,
                    edgesByWorld.computeIfAbsent(world.getUID(), k -> new HashMap<>()));
        }
        Set<Object> wanted = new HashSet<>();
        edgesByWorld.forEach((world, edges) -> buildPieces(world, edges.values(), wanted));

        Iterator<Map.Entry<Object, List<Part>>> iterator = pieces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, List<Part>> entry = iterator.next();
            boolean valid = entry.getValue().stream().allMatch(part -> part.display().isValid());
            if (!wanted.contains(entry.getKey()) || !valid) {
                entry.getValue().forEach(part -> part.display().remove());
                iterator.remove();
            }
        }
        for (Object piece : wanted) {
            if (pieces.containsKey(piece)) {
                continue;
            }
            UUID worldId = piece instanceof Run run ? run.world() : ((Post) piece).world();
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                continue;
            }
            Color color = currentColor(world);
            pieces.put(piece, piece instanceof Run run ? spawnRun(world, run, color) : spawnPost(world, (Post) piece, color));
        }
    }

    /**
     * Chunk-aligned scan window around a player, so runs stay stable while walking inside a chunk. Heights are
     * taken relative to the player, so the border is also drawn down in a mine shaft or a cave, not just on the
     * surface far above.
     */
    private void collectEdges(World world, Location center, int view, Map<List<Integer>, Edge> edges) {
        int chunkRadius = (view + 15) / 16;
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int refY = center.getBlockY();
        for (int x = (cx - chunkRadius) << 4; x < (cx + chunkRadius + 1) << 4; x++) {
            for (int z = (cz - chunkRadius) << 4; z < (cz + chunkRadius + 1) << 4; z++) {
                if (!fields.isAllowed(world, x, z)) {
                    continue;
                }
                int inner = Integer.MIN_VALUE;
                for (int dir = 0; dir < 4; dir++) {
                    int dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
                    int dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
                    if (fields.isAllowed(world, x + dx, z + dz)) {
                        continue;
                    }
                    if (inner == Integer.MIN_VALUE) {
                        inner = groundBelow(world, x, z, refY);
                    }
                    if (inner <= world.getMinHeight()) {
                        break; // void inside the field (End island edge): nothing to stand on
                    }
                    // Two blocks above the player's feet: a wall next to them is covered up to head height.
                    int outer = groundBelow(world, x + dx, z + dz, refY + 2);
                    boolean alongZ = dx != 0;
                    int plane = alongZ ? (dx > 0 ? x + 1 : x) : (dz > 0 ? z + 1 : z);
                    int fieldSide = (dx + dz) > 0 ? -1 : 1;
                    int along = alongZ ? z : x;
                    int low = inner + 1;
                    int high = Math.max(inner, outer) + 1;
                    edges.put(List.of(alongZ ? 1 : 0, plane, along, fieldSide),
                            new Edge(alongZ, plane, fieldSide, along, low, high));
                }
            }
        }
    }

    /** Highest solid block at or below {@code fromY}; falls back to the surface above ground. */
    private static int groundBelow(World world, int x, int z, int fromY) {
        int start = Math.min(fromY, world.getMaxHeight() - 1);
        int stop = Math.max(world.getMinHeight(), start - 40);
        for (int y = start; y >= stop; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return y;
            }
        }
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return surface <= start ? surface : world.getMinHeight();
    }

    private static void buildPieces(UUID world, Iterable<Edge> edges, Set<Object> out) {
        Map<List<Integer>, TreeMap<Integer, Edge>> lines = new HashMap<>();
        Map<Long, List<Edge>> vertices = new HashMap<>();
        for (Edge edge : edges) {
            lines.computeIfAbsent(List.of(edge.alongZ() ? 1 : 0, edge.plane(), edge.fieldSide(), edge.low(), edge.high()),
                    k -> new TreeMap<>()).put(edge.along(), edge);
            for (int end = 0; end <= 1; end++) {
                int vx = edge.alongZ() ? edge.plane() : edge.along() + end;
                int vz = edge.alongZ() ? edge.along() + end : edge.plane();
                vertices.computeIfAbsent(FieldManager.pack(vx, vz), k -> new ArrayList<>()).add(edge);
            }
        }
        for (Map.Entry<List<Integer>, TreeMap<Integer, Edge>> line : lines.entrySet()) {
            Edge sample = line.getValue().firstEntry().getValue();
            Integer start = null;
            int previous = 0;
            for (int along : line.getValue().keySet()) {
                if (start != null && along == previous + 1) {
                    previous = along;
                    continue;
                }
                if (start != null) {
                    out.add(new Run(world, sample.alongZ(), sample.plane(), sample.fieldSide(), start,
                            previous - start + 1, sample.low(), sample.high()));
                }
                start = along;
                previous = along;
            }
            if (start != null) {
                out.add(new Run(world, sample.alongZ(), sample.plane(), sample.fieldSide(), start,
                        previous - start + 1, sample.low(), sample.high()));
            }
        }
        // Vertical lines wherever the profile jumps at a vertex (steps, trench corners, cliffs).
        for (Map.Entry<Long, List<Edge>> vertex : vertices.entrySet()) {
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            Set<Integer> profile = new HashSet<>();
            for (Edge edge : vertex.getValue()) {
                low = Math.min(low, edge.low());
                high = Math.max(high, edge.high());
                profile.add(edge.low() * 4096 + edge.high());
            }
            boolean corner = vertex.getValue().stream().anyMatch(Edge::alongZ)
                    && vertex.getValue().stream().anyMatch(edge -> !edge.alongZ());
            if (high > low && (corner || profile.size() > 1)) {
                out.add(new Post(world, FieldManager.unpackX(vertex.getKey()), FieldManager.unpackZ(vertex.getKey()),
                        low, high));
            }
        }
    }

    // ------------------------------------------------------------------ spawning

    private List<Part> spawnRun(World world, Run run, Color color) {
        List<Part> parts = new ArrayList<>();
        float length = run.length();
        double mid = run.start() + length / 2.0;
        // Curtain sits a hair inside the field so it never z-fights with block faces in the plane.
        double planeOffset = run.plane() + run.fieldSide() * 0.004;
        float yaw = run.alongZ() ? (float) (Math.PI / 2) : 0f;

        int wall = run.high() - run.low();
        if (wall > 0) {
            curtain(parts, world, run, planeOffset, mid, run.low(), wall, length, yaw, color, WALL_ALPHA);
        }
        double bandBottom = run.high();
        for (int[] band : FADE_BANDS) {
            float height = band[0] / 100f;
            curtain(parts, world, run, planeOffset, mid, bandBottom, height, length, yaw, color, band[1]);
            bandBottom += height;
        }
        // Crisp lines: top of the profile, and the floor edge when the terrain outside is higher.
        parts.add(line(world, run, run.high(), color));
        if (wall > 0) {
            parts.add(line(world, run, run.low(), color));
        }
        return parts;
    }

    /** One curtain band, as two back-to-back quads because text displays are single-sided. */
    private void curtain(List<Part> parts, World world, Run run, double plane, double mid, double bottom, float height,
                         float width, float yaw, Color color, int alpha) {
        Location location = run.alongZ()
                ? new Location(world, plane, bottom + height / 2.0, mid)
                : new Location(world, mid, bottom + height / 2.0, plane);
        for (float facing : new float[]{yaw, yaw + (float) Math.PI}) {
            parts.add(new Part(quadDisplay(world, location, width, height, facing, color, alpha), alpha));
        }
    }

    private static TextDisplay quadDisplay(World world, Location location, float width, float height, float facing,
                                           Color color, int alpha) {
        return world.spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.text(Component.text(" "));
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setDefaultBackground(false);
            entity.setShadowed(false);
            entity.setSeeThrough(false);
            entity.setBackgroundColor(withAlpha(color, alpha));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(0.6f);
            entity.setTransformation(quad(width, height, facing));
        });
    }

    /**
     * A text display with a single space renders a background of 1/8 x 1/4 block; scaling by (8w, 4h) and
     * shifting by (-0.1w, -0.5h) gives a w x h quad centred on the entity, then rotated around Y.
     */
    private static Transformation quad(float width, float height, float yaw) {
        Quaternionf rotation = new Quaternionf().rotateY(yaw);
        Vector3f translation = new Vector3f(-0.1f * width, -0.5f * height, 0f);
        rotation.transform(translation);
        return new Transformation(translation, rotation, new Vector3f(8f * width, 4f * height, 1f), new Quaternionf());
    }

    private Part line(World world, Run run, double y, Color color) {
        float half = LINE / 2f;
        Location location = run.alongZ()
                ? new Location(world, run.plane(), y, run.start())
                : new Location(world, run.start(), y, run.plane());
        Vector3f scale = run.alongZ()
                ? new Vector3f(LINE, LINE, run.length() + LINE)
                : new Vector3f(run.length() + LINE, LINE, LINE);
        return new Part(block(world, location, lineBlock(color), new Vector3f(-half, 0.002f, -half), scale), -1);
    }

    private List<Part> spawnPost(World world, Post post, Color color) {
        float half = LINE / 2f;
        Location location = new Location(world, post.x(), post.low(), post.z());
        return List.of(new Part(block(world, location, lineBlock(color), new Vector3f(-half, 0f, -half),
                new Vector3f(LINE, post.high() - post.low() + LINE, LINE)), -1));
    }

    private static BlockDisplay block(World world, Location location, Material material, Vector3f translation,
                                      Vector3f scale) {
        return world.spawn(location, BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(translation, new AxisAngle4f(), scale, new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setViewRange(0.6f);
        });
    }

    // ------------------------------------------------------------------ colour

    /** Called on every newly unlocked block: the whole outline of that world flashes green. */
    public void flash(World world) {
        flashUntil.put(world.getUID(), System.currentTimeMillis() + FLASH_MS);
    }

    private Color currentColor(World world) {
        Long until = flashUntil.get(world.getUID());
        if (until != null && until > System.currentTimeMillis()) {
            return SUCCESS;
        }
        double progress = 0;
        for (Player player : world.getPlayers()) {
            ExpansionManager.Push push = expansion.activePush(player);
            if (push != null) {
                progress = Math.max(progress, expansion.progress(push));
            }
        }
        if (progress > 0) {
            return PUSH_STAGES[Math.min(PUSH_STAGES.length - 1, (int) (progress * PUSH_STAGES.length))];
        }
        return settings.choice(Settings.BORDER_COLOR, BorderColor.class).color();
    }

    private Material lineBlock(Color color) {
        for (int i = 0; i < PUSH_STAGES.length; i++) {
            if (PUSH_STAGES[i].equals(color)) {
                return PUSH_BLOCKS[i];
            }
        }
        if (SUCCESS.equals(color)) {
            return Material.LIME_CONCRETE;
        }
        return settings.choice(Settings.BORDER_COLOR, BorderColor.class).block();
    }

    private static Color withAlpha(Color color, int alpha) {
        return Color.fromARGB(alpha, color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Every 2 ticks: recolour whole worlds when push progress, a flash or the colour setting changed. */
    public void updateColors() {
        if (pieces.isEmpty()) {
            return;
        }
        Map<UUID, List<Part>> byWorld = new HashMap<>();
        pieces.forEach((piece, parts) -> byWorld
                .computeIfAbsent(piece instanceof Run run ? run.world() : ((Post) piece).world(), k -> new ArrayList<>())
                .addAll(parts));
        for (Map.Entry<UUID, List<Part>> entry : byWorld.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            Color target = currentColor(world);
            if (target.equals(worldColors.get(entry.getKey()))) {
                continue;
            }
            worldColors.put(entry.getKey(), target);
            Material block = lineBlock(target);
            for (Part part : entry.getValue()) {
                if (!part.display().isValid()) {
                    continue;
                }
                if (part.display() instanceof TextDisplay text) {
                    text.setBackgroundColor(withAlpha(target, part.alpha()));
                } else if (part.display() instanceof BlockDisplay line) {
                    line.setBlock(block.createBlockData());
                }
            }
        }
    }

    public void clear() {
        pieces.values().forEach(parts -> parts.forEach(part -> part.display().remove()));
        pieces.clear();
        worldColors.clear();
    }
}
