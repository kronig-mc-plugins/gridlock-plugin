package net.kronig.gridlock.field;

import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.level.LevelManager;
import net.kronig.gridlock.ui.ActionBars;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Tracks players sneaking against the border and unlocks the column once they held long enough. */
public final class ExpansionManager {

    /** Ticks without a push attempt after which progress is dropped. */
    private static final int PUSH_TIMEOUT = 8;

    public static final class Push {
        final UUID world;
        final int x;
        final int z;
        int progress;
        long lastPush;

        Push(UUID world, int x, int z, long now) {
            this.world = world;
            this.x = x;
            this.z = z;
            this.lastPush = now;
        }

        public boolean targets(World world, int x, int z) {
            return this.world.equals(world.getUID()) && this.x == x && this.z == z;
        }

        public int x() {
            return x;
        }

        public int z() {
            return z;
        }
    }

    private final Settings settings;
    private final FieldManager fields;
    private final LevelManager levels;
    private final ActionBars actionBars;
    private final Map<UUID, Push> pushes = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lastHint = new HashMap<>();
    private long tick;

    public ExpansionManager(Settings settings, FieldManager fields, LevelManager levels, ActionBars actionBars) {
        this.settings = settings;
        this.fields = fields;
        this.levels = levels;
        this.actionBars = actionBars;
    }

    public Push activePush(Player player) {
        return pushes.get(player.getUniqueId());
    }

    public double progress(Push push) {
        return Math.min(1.0, push.progress / (double) settings.integer(Settings.HOLD_TICKS));
    }

    /** Called by the movement listener whenever a restricted player walks into the border. */
    public void onBlocked(Player player, int targetX, int targetZ) {
        if (!player.isSneaking()) {
            hint(player);
            return;
        }
        UUID id = player.getUniqueId();
        Long cooldown = cooldownUntil.get(id);
        if (cooldown != null && cooldown > tick) {
            return;
        }
        Push push = pushes.get(id);
        if (push == null || !push.targets(player.getWorld(), targetX, targetZ)) {
            pushes.put(id, new Push(player.getWorld().getUID(), targetX, targetZ, tick));
        } else {
            push.lastPush = tick;
        }
    }

    private void hint(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastHint.get(player.getUniqueId());
        if (last != null && now - last < 2500) {
            return;
        }
        lastHint.put(player.getUniqueId(), now);
        int cost = fields.nextCost(player.getWorld());
        actionBars.show(player, Text.mm("<red>▌ Border</red> <gray>– <white>schleichen</white> + dagegen drücken zum Erweitern <dark_gray>(</dark_gray>"
                + "<green>" + cost + " Level</green><dark_gray>)</dark_gray>"), 2000);
    }

    public void tick() {
        tick++;
        int hold = settings.integer(Settings.HOLD_TICKS);
        Iterator<Map.Entry<UUID, Push>> iterator = pushes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Push> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Push push = entry.getValue();
            if (player == null || !player.isSneaking() || !fields.isRestricted(player)
                    || !player.getWorld().getUID().equals(push.world) || tick - push.lastPush > PUSH_TIMEOUT) {
                iterator.remove();
                continue;
            }
            int cost = fields.nextCost(player.getWorld());
            if (levels.available(player) < cost) {
                iterator.remove();
                cooldownUntil.put(player.getUniqueId(), tick + 20);
                actionBars.show(player, Text.mm("<red>✖ Nicht genug Level</red> <gray>– du brauchst <white>" + cost
                        + "</white>, " + (levels.mode() == PaymentMode.POOL ? "der Pool hat" : "du hast") + " <white>"
                        + levels.available(player) + "</white>"), 1500);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                continue;
            }
            push.progress++;
            double progress = progress(push);
            actionBars.show(player, Text.mm("<gold>Erweitere…</gold> " + Text.progressBar(progress, 12, "green", "dark_gray")
                    + " <gray>" + cost + " Level"), 500);
            if (push.progress % 4 == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 0.5f + (float) progress);
            }
            if (push.progress >= hold) {
                iterator.remove();
                cooldownUntil.put(player.getUniqueId(), tick + 6);
                complete(player, push, cost);
            }
        }
    }

    private void complete(Player player, Push push, int cost) {
        World world = player.getWorld();
        if (fields.isAllowed(world, push.x, push.z)) {
            return;
        }
        if (!levels.tryPay(player, cost)) {
            return;
        }
        fields.unlock(world, push.x, push.z);
        int size = fields.size(world);

        Location center = new Location(world, push.x + 0.5, player.getY() + 1, push.z + 0.5);
        world.spawnParticle(Particle.HAPPY_VILLAGER, center, 25, 0.3, 1.0, 0.3, 0);
        world.spawnParticle(Particle.END_ROD, center, 10, 0.2, 0.8, 0.2, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        actionBars.show(player, Text.mm("<green>✔ Feld erweitert!</green> <gray>" + dimensionName(world) + ": <white>"
                + size + "</white> Blöcke <dark_gray>|</dark_gray> nächster: <white>" + fields.nextCost(world) + " Level"), 2000);

        for (Player other : world.getPlayers()) {
            if (!other.equals(player)) {
                other.playSound(other.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 0.8f);
                actionBars.show(other, Text.mm("<green>+1</green> <gray>" + Text.escape(player.getName())
                        + " hat das Feld erweitert <dark_gray>(</dark_gray><white>" + size + "</white><dark_gray>)"), 1500);
            }
        }
        if (settings.bool(Settings.MILESTONES) && isMilestone(size)) {
            Bukkit.broadcast(Text.prefixed("<gold>★</gold> Das Feld im <white>" + dimensionName(world)
                    + "</white> ist jetzt <gold><bold>" + size + " Blöcke</bold></gold> groß!"));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        }
    }

    private static boolean isMilestone(int size) {
        return switch (size) {
            case 10, 25, 50, 100, 250, 500 -> true;
            default -> size >= 1000 && size % 1000 == 0;
        };
    }

    public static String dimensionName(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Oberwelt";
        };
    }

    public void forget(Player player) {
        pushes.remove(player.getUniqueId());
        cooldownUntil.remove(player.getUniqueId());
        lastHint.remove(player.getUniqueId());
    }
}
