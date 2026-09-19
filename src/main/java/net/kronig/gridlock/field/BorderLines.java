package net.kronig.gridlock.field;

import net.kronig.gridlock.config.BorderColor;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Server-side drawing of the border for players without the client mod.
 *
 * <p>For every boundary edge the real air spaces of the field column next to it are looked at (tunnel, staircase,
 * shaft, cave, surface) over the whole height around a player, not just at the player's own level. Each air space
 * gets a thin line along its floor, one along its ceiling if it has one, and lines on ledges of the terrain
 * outside. Lines carry a soft glow that fades out upwards (text-display backgrounds, the only displays with real
 * transparency). Corners get exactly one vertical post, shared by all edges meeting there.
 *
 * <p>Block-long pieces with the same height are merged into straight runs. While someone pushes against the
 * border, the whole outline of that world shifts towards green, and flashes green on every new block.
 */
public final class BorderLines {

    /**
     * A horizontal line on plane {@code plane} (x = plane if {@code alongZ}, else z = plane) at height {@code y},
     * spanning {@code start .. start + length} on the other axis. {@code fieldSide} is -1/+1: on which side of the
     * plane the field lies. The glow reaches up to {@code glowTop}, given in tenths of a block; equal to
     * {@code y * 10} means no glow (ceiling lines).
     */
    private record Run(UUID world, boolean alongZ, int plane, int fieldSide, int start, int length, int y, int glowTop) {
    }

    /** Vertical line at a vertex of the outline, from {@code low} to {@code high}. */
    private record Post(UUID world, int x, int z, int low, int high) {
    }

    /** One block-long piece of a {@link Run}. */
    private record Seg(boolean alongZ, int plane, int fieldSide, int along, int y, int glowTop) {
    }

    /** An air space in a column: from {@code bottom} (first free block) to {@code top} (exclusive). */
    private record Gap(int bottom, int top, boolean capped) {
    }

    /** A spawned display plus the alpha its colour should have (-1 = solid line). */
    private record Part(Display display, int alpha) {
    }

    /** Blocks scanned below and above a player for air spaces. */
    private static final int SCAN = 40;
    /** Share of the total glow height and of the strength per band, from the line upwards. */
    private static final double[][] FADE_BANDS = {{0.25, 1.0}, {0.3, 0.55}, {0.45, 0.2}};
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
    /** Players who draw the border themselves (client mod): no displays are built around them. */
    private Predicate<Player> selfRendering = player -> false;
    private Consumer<Display> spawnHook = display -> {
    };

    public BorderLines(Settings settings, FieldManager fields, ExpansionManager expansion, Supplier<GameData> data) {
        this.settings = settings;
        this.fields = fields;
        this.expansion = expansion;
        this.data = data;
    }

    public void setModHooks(Predicate<Player> selfRendering, Consumer<Display> spawnHook) {
        this.selfRendering = selfRendering;
        this.spawnHook = spawnHook;
    }

    /** Every display currently spawned for the border. */
    public List<Display> displays() {
        List<Display> all = new ArrayList<>();
        pieces.values().forEach(parts -> parts.forEach(part -> all.add(part.display())));
        return all;
    }

    // ------------------------------------------------------------------ geometry

    public void update() {
        if (!data.get().state.isIngame()) {
            clear();
            return;
        }
        int view = Math.max(settings.integer(Settings.BORDER_VIEW), 12);
        Map<UUID, Set<Seg>> segsByWorld = new HashMap<>();
        Map<UUID, Map<Long, List<int[]>>> postsByWorld = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (player.getGameMode() == GameMode.SPECTATOR || !fields.isChallengeWorld(world)
                    || selfRendering.test(player)) {
                continue;
            }
            collect(world, player.getLocation(), view,
                    segsByWorld.computeIfAbsent(world.getUID(), k -> new HashSet<>()),
                    postsByWorld.computeIfAbsent(world.getUID(), k -> new HashMap<>()));
        }
        Set<Object> wanted = new HashSet<>();
        segsByWorld.forEach((world, segs) -> buildRuns(world, segs, wanted));
        postsByWorld.forEach((world, posts) -> posts.forEach((vertex, ranges) -> {
            for (int[] range : merge(ranges)) {
                wanted.add(new Post(world, FieldManager.unpackX(vertex), FieldManager.unpackZ(vertex), range[0], range[1]));
            }
        }));

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
            List<Part> parts = piece instanceof Run run ? spawnRun(world, run, color) : spawnPost(world, (Post) piece, color);
            parts.forEach(part -> spawnHook.accept(part.display()));
            pieces.put(piece, parts);
        }
    }

    /** Chunk-aligned scan window around a player, so pieces stay stable while walking inside a chunk. */
    private void collect(World world, Location center, int view, Set<Seg> segs, Map<Long, List<int[]>> posts) {
        int chunkRadius = (view + 15) / 16;
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        int refY = center.getBlockY();
        int glowTenths = settings.integer(Settings.BORDER_GLOW_STRENGTH) > 0
                ? settings.integer(Settings.BORDER_GLOW_HEIGHT) : 0;
        Map<Long, List<Gap>> gapCache = new HashMap<>();
        for (int x = (cx - chunkRadius) << 4; x < (cx + chunkRadius + 1) << 4; x++) {
            for (int z = (cz - chunkRadius) << 4; z < (cz + chunkRadius + 1) << 4; z++) {
                if (!fields.isAllowed(world, x, z)) {
                    continue;
                }
                for (int dir = 0; dir < 4; dir++) {
                    int dx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
                    int dz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
                    if (!fields.isAllowed(world, x + dx, z + dz)) {
                        collectEdge(world, x, z, dx, dz, refY, glowTenths, gapCache, segs, posts);
                    }
                }
            }
        }
    }

    private void collectEdge(World world, int x, int z, int dx, int dz, int refY, int glowTenths,
                             Map<Long, List<Gap>> gapCache, Set<Seg> segs, Map<Long, List<int[]>> posts) {
        boolean alongZ = dx != 0;
        int plane = alongZ ? (dx > 0 ? x + 1 : x) : (dz > 0 ? z + 1 : z);
        int fieldSide = (dx + dz) > 0 ? -1 : 1;
        int along = alongZ ? z : x;
        int outerSurface = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        for (Gap gap : gaps(world, x, z, refY, gapCache)) {
            segs.add(new Seg(alongZ, plane, fieldSide, along, gap.bottom(),
                    Math.min(gap.bottom() * 10 + glowTenths, gap.top() * 10)));
            if (gap.capped()) {
                segs.add(new Seg(alongZ, plane, fieldSide, along, gap.top(), gap.top() * 10));
            }
            // Ledges of the terrain outside: solid below, free above, within this air space.
            int ledgeLimit = Math.min(gap.top(), gap.bottom() + SCAN);
            for (int y = gap.bottom() + 1; y < ledgeLimit; y++) {
                if (solid(world, x + dx, y - 1, z + dz) && !solid(world, x + dx, y, z + dz)) {
                    segs.add(new Seg(alongZ, plane, fieldSide, along, y, Math.min(y * 10 + glowTenths, gap.top() * 10)));
                }
            }
            // Posts at both ends, unless the outline simply continues there with the same air space.
            int postTop = gap.capped() ? gap.top() : Math.max(gap.bottom(), Math.min(gap.top(), outerSurface));
            if (postTop <= gap.bottom()) {
                continue;
            }
            for (int end = 0; end <= 1; end++) {
                int step = end == 0 ? -1 : 1;
                int nx = alongZ ? x : x + step;
                int nz = alongZ ? z + step : z;
                boolean continues = fields.isAllowed(world, nx, nz) && !fields.isAllowed(world, nx + dx, nz + dz)
                        && gaps(world, nx, nz, refY, gapCache).contains(gap);
                if (continues) {
                    continue;
                }
                long vertex = alongZ ? FieldManager.pack(plane, along + end) : FieldManager.pack(along + end, plane);
                posts.computeIfAbsent(vertex, k -> new ArrayList<>()).add(new int[]{gap.bottom(), postTop});
            }
        }
    }

    /** All air spaces of a column within the scan range around the player. */
    private static List<Gap> gaps(World world, int x, int z, int refY, Map<Long, List<Gap>> cache) {
        return cache.computeIfAbsent(FieldManager.pack(x, z), key -> {
            List<Gap> gaps = new ArrayList<>();
            int min = Math.max(world.getMinHeight(), refY - SCAN);
            int max = Math.min(world.getMaxHeight() - 1, refY + SCAN);
            int y = min;
            // Start on solid ground: an air space cut off by the lower scan limit has no floor to draw.
            while (y <= max && !solid(world, x, y, z)) {
                y++;
            }
            while (y <= max) {
                while (y <= max && solid(world, x, y, z)) {
                    y++;
                }
                if (y > max) {
                    break;
                }
                int bottom = y;
                while (y <= max && !solid(world, x, y, z)) {
                    y++;
                }
                gaps.add(new Gap(bottom, y, y <= max));
            }
            return gaps;
        });
    }

    private static boolean solid(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isSolid();
    }

    /** Merges overlapping or touching ranges, so a post is one clean piece instead of stacked duplicates. */
    private static List<int[]> merge(List<int[]> ranges) {
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], range[1]);
            } else {
                merged.add(new int[]{range[0], range[1]});
            }
        }
        return merged;
    }

    /** Joins block-long segments with the same height and glow into straight runs. */
    private static void buildRuns(UUID world, Set<Seg> segs, Set<Object> out) {
        Map<List<Integer>, TreeMap<Integer, Seg>> lines = new HashMap<>();
        for (Seg seg : segs) {
            lines.computeIfAbsent(List.of(seg.alongZ() ? 1 : 0, seg.plane(), seg.fieldSide(), seg.y(), seg.glowTop()),
                    k -> new TreeMap<>()).put(seg.along(), seg);
        }
        for (TreeMap<Integer, Seg> line : lines.values()) {
            Seg sample = line.firstEntry().getValue();
            Integer start = null;
            int previous = 0;
            for (int along : line.keySet()) {
                if (start != null && along == previous + 1) {
                    previous = along;
                    continue;
                }
                if (start != null) {
                    out.add(new Run(world, sample.alongZ(), sample.plane(), sample.fieldSide(), start,
                            previous - start + 1, sample.y(), sample.glowTop()));
                }
                start = along;
                previous = along;
            }
            if (start != null) {
                out.add(new Run(world, sample.alongZ(), sample.plane(), sample.fieldSide(), start,
                        previous - start + 1, sample.y(), sample.glowTop()));
            }
        }
    }

    // ------------------------------------------------------------------ spawning

    private List<Part> spawnRun(World world, Run run, Color color) {
        List<Part> parts = new ArrayList<>();
        float length = run.length();
        double mid = run.start() + length / 2.0;
        // The glow sits a hair inside the field so it never z-fights with block faces in the plane.
        double planeOffset = run.plane() + run.fieldSide() * 0.004;
        float yaw = run.alongZ() ? (float) (Math.PI / 2) : 0f;

        double fullGlow = settings.integer(Settings.BORDER_GLOW_HEIGHT) / 10.0;
        int strength = Math.round(settings.integer(Settings.BORDER_GLOW_STRENGTH) * 255 / 100f);
        double limit = run.glowTop() / 10.0;
        double bandBottom = run.y();
        for (double[] band : FADE_BANDS) {
            double bandTop = Math.min(bandBottom + fullGlow * band[0], limit);
            int alpha = (int) Math.round(strength * band[1]);
            if (bandTop - bandBottom < 0.01 || alpha <= 0) {
                break;
            }
            curtain(parts, world, run, planeOffset, mid, bandBottom, (float) (bandTop - bandBottom), length, yaw, color, alpha);
            bandBottom = bandTop;
        }
        parts.add(line(world, run, color));
        return parts;
    }

    /** One glow band, as two back-to-back quads because text displays are single-sided. */
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

    /** Thin square rod centred exactly on the edge, slightly longer so neighbours and posts close up. */
    private Part line(World world, Run run, Color color) {
        float lineWidth = lineWidth();
        float half = lineWidth / 2f;
        Location location = run.alongZ()
                ? new Location(world, run.plane(), run.y(), run.start())
                : new Location(world, run.start(), run.y(), run.plane());
        Vector3f scale = run.alongZ()
                ? new Vector3f(lineWidth, lineWidth, run.length() + lineWidth)
                : new Vector3f(run.length() + lineWidth, lineWidth, lineWidth);
        return new Part(block(world, location, lineBlock(color), new Vector3f(-half, -half, -half), scale), -1);
    }

    private List<Part> spawnPost(World world, Post post, Color color) {
        float lineWidth = lineWidth();
        float half = lineWidth / 2f;
        Location location = new Location(world, post.x(), post.low(), post.z());
        return List.of(new Part(block(world, location, lineBlock(color), new Vector3f(-half, -half, -half),
                new Vector3f(lineWidth, post.high() - post.low() + lineWidth, lineWidth)), -1));
    }

    private float lineWidth() {
        return settings.integer(Settings.BORDER_LINE_WIDTH) / 100f;
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
