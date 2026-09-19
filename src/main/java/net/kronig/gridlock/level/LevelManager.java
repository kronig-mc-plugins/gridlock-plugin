package net.kronig.gridlock.level;

import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Level economy. In POOL mode the team pool is the single source of truth and is mirrored into every
 * player's vanilla XP bar; levels spent the vanilla way (enchanting, anvil) are taken from the pool.
 */
public final class LevelManager implements Listener {

    private final Settings settings;
    private final Supplier<GameData> data;
    private final Predicate<Player> inChallenge;
    /** Level each player's bar showed after the last sync – differences are vanilla spending. */
    private final Map<UUID, Integer> syncedLevel = new HashMap<>();
    private PaymentMode lastMode;

    public LevelManager(Settings settings, Supplier<GameData> data, Predicate<Player> inChallenge) {
        this.settings = settings;
        this.data = data;
        this.inChallenge = inChallenge;
        this.lastMode = mode();
    }

    public PaymentMode mode() {
        return settings.choice(Settings.PAYMENT_MODE, PaymentMode.class);
    }

    private boolean ingame() {
        GameState state = data.get().state;
        return state == GameState.RUNNING || state == GameState.WON;
    }

    /** Levels the given player can currently spend on the field. */
    public int available(Player player) {
        return mode() == PaymentMode.POOL ? data.get().poolLevels : player.getLevel();
    }

    public boolean tryPay(Player player, int cost) {
        if (cost <= 0) {
            return true;
        }
        if (mode() == PaymentMode.POOL) {
            if (data.get().poolLevels < cost) {
                return false;
            }
            data.get().poolLevels -= cost;
            sync();
            return true;
        }
        if (player.getLevel() < cost) {
            return false;
        }
        player.giveExpLevels(-cost);
        return true;
    }

    /** Applies start levels to everyone at the beginning of a round. */
    public void applyStartLevels(Iterable<? extends Player> players) {
        int start = settings.integer(Settings.START_LEVELS);
        syncedLevel.clear();
        if (mode() == PaymentMode.POOL) {
            data.get().poolLevels = start;
            data.get().poolPoints = 0;
            sync();
        } else {
            for (Player player : players) {
                resetExp(player, start);
            }
        }
    }

    /** Start levels for a player joining a running round for the first time. */
    public void applyLateJoinLevels(Player player) {
        if (mode() == PaymentMode.POOL) {
            syncedLevel.remove(player.getUniqueId());
            sync();
        } else {
            resetExp(player, settings.integer(Settings.START_LEVELS));
        }
    }

    private static void resetExp(Player player, int level) {
        player.setExp(0f);
        player.setLevel(level);
        player.setTotalExperience(0);
    }

    public void grantTimeLevels() {
        int amount = settings.integer(Settings.TIME_AMOUNT);
        if (mode() == PaymentMode.POOL) {
            data.get().poolLevels += amount;
            sync();
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR && inChallenge.test(player)) {
                    player.giveExpLevels(amount);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Text.prefixed("<green>+" + amount + " Level</green> <gray>Zeitbonus"
                    + (mode() == PaymentMode.POOL ? " für das Team" : "") + "!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    // ------------------------------------------------------------------ transfer mode

    /** Sends levels from one player to another (TRANSFER mode). */
    public boolean transfer(Player from, Player to, int levels) {
        if (levels <= 0 || from.getLevel() < levels) {
            return false;
        }
        from.giveExpLevels(-levels);
        to.giveExpLevels(levels);
        from.playSound(from.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.7f);
        to.playSound(to.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        from.sendMessage(Text.prefixed("<gray>Du hast <white>" + Text.escape(to.getName()) + "</white> <green>"
                + levels + " Level</green> überwiesen."));
        to.sendMessage(Text.prefixed("<white>" + Text.escape(from.getName()) + "</white> <gray>hat dir <green>"
                + levels + " Level</green> überwiesen!"));
        return true;
    }

    // ------------------------------------------------------------------ admin

    public void setPool(int levels) {
        data.get().poolLevels = Math.max(0, levels);
        data.get().poolPoints = 0;
        sync();
    }

    public int pool() {
        return data.get().poolLevels;
    }

    // ------------------------------------------------------------------ pool <-> XP bar

    /**
     * Mirrors the pool into every XP bar. Level drops since the last sync (enchanting, anvil, /xp) are
     * applied to the pool first, so vanilla spending costs the team.
     */
    public void sync() {
        PaymentMode mode = mode();
        if (mode != lastMode) {
            switchMode(lastMode, mode);
            lastMode = mode;
        }
        if (mode != PaymentMode.POOL || !ingame()) {
            syncedLevel.clear();
            return;
        }
        GameData gameData = data.get();
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (inChallenge.test(player)) {
                players.add(player);
            }
        }
        for (Player player : players) {
            Integer last = syncedLevel.get(player.getUniqueId());
            if (last != null && player.getLevel() != last) {
                gameData.poolLevels = Math.max(0, gameData.poolLevels + (player.getLevel() - last));
            }
        }
        float progress = Math.max(0f, Math.min(0.999f,
                gameData.poolPoints / (float) pointsForNext(gameData.poolLevels)));
        for (Player player : players) {
            if (player.getLevel() != gameData.poolLevels) {
                player.setLevel(gameData.poolLevels);
            }
            if (Math.abs(player.getExp() - progress) > 0.0001f) {
                player.setExp(progress);
            }
            syncedLevel.put(player.getUniqueId(), gameData.poolLevels);
        }
    }

    /** Keeps levels sensible when an admin switches the mode mid-round. */
    private void switchMode(PaymentMode from, PaymentMode to) {
        if (!ingame()) {
            return;
        }
        if (to == PaymentMode.POOL) {
            // Everyone's own levels go into the new pool.
            int sum = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (inChallenge.test(player)) {
                    sum += player.getLevel();
                }
            }
            data.get().poolLevels = sum;
            data.get().poolPoints = 0;
            syncedLevel.clear();
            Bukkit.broadcast(Text.prefixed("<gray>Team-Pool aktiv: alle Level zusammengelegt → <green>" + sum + " Level"));
        } else if (from == PaymentMode.POOL) {
            // Everyone keeps what their bar shows right now.
            syncedLevel.clear();
            Bukkit.broadcast(Text.prefixed("<gray>Team-Pool aus: jeder behält die aktuellen <green>"
                    + data.get().poolLevels + " Level</green> als eigene."));
        }
    }

    public void forget(Player player) {
        syncedLevel.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!ingame()) {
            return;
        }
        if (!settings.bool(Settings.VANILLA_XP)) {
            event.setAmount(0);
            return;
        }
        if (mode() != PaymentMode.POOL || event.getAmount() <= 0) {
            return;
        }
        int points = event.getAmount();
        event.setAmount(0);
        addPoolPoints(points);
    }

    /** The pool is shared, so a death must not wipe or drop it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (ingame() && mode() == PaymentMode.POOL) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    private void addPoolPoints(int points) {
        GameData gameData = data.get();
        gameData.poolPoints += points;
        while (gameData.poolPoints >= pointsForNext(gameData.poolLevels)) {
            gameData.poolPoints -= pointsForNext(gameData.poolLevels);
            gameData.poolLevels++;
        }
        sync();
    }

    /** Vanilla XP needed to go from {@code level} to {@code level + 1}. */
    private static int pointsForNext(int level) {
        if (level >= 31) {
            return 9 * level - 158;
        }
        if (level >= 16) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }
}
