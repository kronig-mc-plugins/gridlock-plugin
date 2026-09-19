package net.kronig.gridlock.level;

import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.function.Supplier;

public final class LevelManager implements Listener {

    private final Settings settings;
    private final Supplier<GameData> data;
    private final BossBar poolBar = BossBar.bossBar(Text.mm("Team-Level"), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

    public LevelManager(Settings settings, Supplier<GameData> data) {
        this.settings = settings;
        this.data = data;
    }

    public PaymentMode mode() {
        return settings.choice(Settings.PAYMENT_MODE, PaymentMode.class);
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
            GameData gameData = data.get();
            if (gameData.poolLevels < cost) {
                return false;
            }
            gameData.poolLevels -= cost;
            updateBar();
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
        GameData gameData = data.get();
        if (mode() == PaymentMode.POOL) {
            gameData.poolLevels = start;
            gameData.poolPoints = 0;
            for (Player player : players) {
                resetExp(player, 0);
            }
        } else {
            for (Player player : players) {
                resetExp(player, start);
            }
        }
        updateBar();
    }

    /** Start levels for a player joining a running round for the first time. */
    public void applyLateJoinLevels(Player player) {
        resetExp(player, mode() == PaymentMode.POOL ? 0 : settings.integer(Settings.START_LEVELS));
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
            updateBar();
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.giveExpLevels(amount);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Text.prefixed("<green>+" + amount + " Level</green> <gray>Zeitbonus"
                    + (mode() == PaymentMode.POOL ? " für den Team-Pool" : "") + "!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    /** Moves own levels into the team pool. */
    public boolean deposit(Player player, int levels) {
        if (levels <= 0 || player.getLevel() < levels) {
            return false;
        }
        player.giveExpLevels(-levels);
        data.get().poolLevels += levels;
        updateBar();
        return true;
    }

    public void addLevels(int levels) {
        data.get().poolLevels = Math.max(0, data.get().poolLevels + levels);
        updateBar();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExpChange(PlayerExpChangeEvent event) {
        GameData gameData = data.get();
        if (gameData.state != GameState.RUNNING && gameData.state != GameState.WON) {
            return;
        }
        if (!settings.bool(Settings.VANILLA_XP)) {
            event.setAmount(0);
            return;
        }
        if (mode() != PaymentMode.POOL || event.getAmount() <= 0) {
            return;
        }
        int shared = Math.round(event.getAmount() * settings.integer(Settings.POOL_SHARE) / 100f);
        event.setAmount(event.getAmount() - shared);
        addPoolPoints(shared);
    }

    private void addPoolPoints(int points) {
        GameData gameData = data.get();
        gameData.poolPoints += points;
        while (gameData.poolPoints >= pointsForNext(gameData.poolLevels)) {
            gameData.poolPoints -= pointsForNext(gameData.poolLevels);
            gameData.poolLevels++;
        }
        updateBar();
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

    public void updateBar() {
        GameData gameData = data.get();
        boolean visible = mode() == PaymentMode.POOL && gameData.state.isIngame();
        if (!visible) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(poolBar);
            }
            return;
        }
        float progress = Math.max(0f, Math.min(1f, gameData.poolPoints / (float) pointsForNext(gameData.poolLevels)));
        poolBar.name(Text.mm("<green>✦ Team-Level: <white><bold>" + gameData.poolLevels + "</bold></white> <dark_gray>|</dark_gray> <gray>"
                + Math.round(progress * 100) + "% zum nächsten"));
        poolBar.progress(progress);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(poolBar);
        }
    }
}
