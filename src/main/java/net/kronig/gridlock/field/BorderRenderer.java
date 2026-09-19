package net.kronig.gridlock.field;

import net.kronig.gridlock.config.BorderColor;
import net.kronig.gridlock.config.BorderStyle;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** Draws particle walls on every edge between an unlocked and a locked column near each player. */
public final class BorderRenderer {

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Color PUSH_DONE = Color.fromRGB(60, 255, 90);

    private final Settings settings;
    private final FieldManager fields;
    private final ExpansionManager expansion;
    private final Supplier<GameData> data;
    private java.util.function.Predicate<Player> selfRendering = player -> false;

    public void setSelfRendering(java.util.function.Predicate<Player> selfRendering) {
        this.selfRendering = selfRendering;
    }

    public BorderRenderer(Settings settings, FieldManager fields, ExpansionManager expansion, Supplier<GameData> data) {
        this.settings = settings;
        this.fields = fields;
        this.expansion = expansion;
        this.data = data;
    }

    public void render() {
        if (!data.get().state.isIngame() || !settings.choice(Settings.BORDER_STYLE, BorderStyle.class).particles()) {
            return;
        }
        Color base = settings.choice(Settings.BORDER_COLOR, BorderColor.class).color();
        int view = settings.integer(Settings.BORDER_VIEW);
        int density = settings.integer(Settings.BORDER_DENSITY);
        Particle.DustOptions normal = new Particle.DustOptions(base, 1.1f);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || !fields.isChallengeWorld(player.getWorld())
                    || selfRendering.test(player)) {
                continue;
            }
            renderFor(player, view, density, base, normal);
        }
    }

    private void renderFor(Player player, int view, int density, Color base, Particle.DustOptions normal) {
        World world = player.getWorld();
        double px = player.getX();
        double pz = player.getZ();
        double feet = player.getY();
        int bx = player.getLocation().getBlockX();
        int bz = player.getLocation().getBlockZ();
        ExpansionManager.Push push = expansion.activePush(player);

        for (int x = bx - view; x <= bx + view; x++) {
            for (int z = bz - view; z <= bz + view; z++) {
                if (!fields.isAllowed(world, x, z)) {
                    continue;
                }
                for (int[] dir : DIRECTIONS) {
                    int nx = x + dir[0];
                    int nz = z + dir[1];
                    if (fields.isAllowed(world, nx, nz)) {
                        continue;
                    }
                    // Edge line: fixed coordinate on one axis, spanning one block on the other.
                    double ex = dir[0] == 0 ? x : (dir[0] > 0 ? x + 1 : x);
                    double ez = dir[1] == 0 ? z : (dir[1] > 0 ? z + 1 : z);
                    double midX = dir[0] == 0 ? x + 0.5 : ex;
                    double midZ = dir[1] == 0 ? z + 0.5 : ez;
                    double distance = Math.hypot(midX - px, midZ - pz);
                    if (distance > view) {
                        continue;
                    }
                    Particle.DustOptions dust = normal;
                    boolean pushed = push != null && push.targets(world, nx, nz);
                    if (pushed) {
                        dust = new Particle.DustOptions(blend(base, expansion.progress(push)), 1.4f);
                    }
                    drawEdge(player, ex, ez, dir, feet, distance, density + (pushed ? 2 : 0), dust);
                }
            }
        }
    }

    private static Color blend(Color from, double progress) {
        int r = (int) (from.getRed() + (PUSH_DONE.getRed() - from.getRed()) * progress);
        int g = (int) (from.getGreen() + (PUSH_DONE.getGreen() - from.getGreen()) * progress);
        int b = (int) (from.getBlue() + (PUSH_DONE.getBlue() - from.getBlue()) * progress);
        return Color.fromRGB(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void drawEdge(Player player, double ex, double ez, int[] dir, double feet, double distance,
                                 int density, Particle.DustOptions dust) {
        int points = density + 1;
        double[] heights;
        if (distance <= 5) {
            heights = new double[]{-0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        } else if (distance <= 11) {
            heights = new double[]{0.1, 0.9, 1.7};
        } else {
            heights = new double[]{1.0};
        }
        for (int i = 0; i < points; i++) {
            double t = (i + 0.5) / points;
            double x = dir[0] == 0 ? ex + t : ex;
            double z = dir[1] == 0 ? ez + t : ez;
            for (double h : heights) {
                player.spawnParticle(Particle.DUST, x, feet + h, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }
}
