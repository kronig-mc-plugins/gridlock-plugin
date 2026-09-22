package net.kronig.gridlock.bonus;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.util.Effects;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random bonus events during a round. Times are measured in challenge-timer seconds, so a paused timer also
 * pauses the bonus clock and everything survives a restart.
 */
public final class BonusManager {

    private final GridLockPlugin plugin;

    public BonusManager(GridLockPlugin plugin) {
        this.plugin = plugin;
    }

    private GameData data() {
        return plugin.data();
    }

    public BonusType active() {
        GameData data = data();
        if (data.activeBonus == null || data.state != GameState.RUNNING && data.state != GameState.WON) {
            return null;
        }
        return BonusType.parse(data.activeBonus);
    }

    public long remainingSeconds() {
        return Math.max(0, data().bonusEndsAt - data().timerSeconds);
    }

    /** Seconds until the next bonus starts, or -1 if bonuses are off. */
    public long untilNext() {
        if (!plugin.settings().bool(Settings.BONUS_ENABLED)) {
            return -1;
        }
        return Math.max(0, data().nextBonusAt - data().timerSeconds);
    }

    /** Called at the start of a round. */
    public void reset() {
        GameData data = data();
        data.activeBonus = null;
        data.bonusEndsAt = 0;
        data.freeBlockUsed.clear();
        data.nextBonusAt = plugin.settings().integer(Settings.BONUS_INTERVAL) * 60L;
    }

    /** Once per second while the timer runs. */
    public void tickSecond() {
        GameData data = data();
        if (data.state != GameState.RUNNING || data.timerPaused) {
            return;
        }
        if (data.activeBonus != null) {
            if (data.timerSeconds >= data.bonusEndsAt) {
                end();
            }
            return;
        }
        if (!plugin.settings().bool(Settings.BONUS_ENABLED)) {
            return;
        }
        if (data.nextBonusAt <= 0) {
            data.nextBonusAt = data.timerSeconds + plugin.settings().integer(Settings.BONUS_INTERVAL) * 60L;
        }
        if (data.timerSeconds >= data.nextBonusAt) {
            BonusType[] types = BonusType.values();
            start(types[ThreadLocalRandom.current().nextInt(types.length)]);
        }
    }

    public void start(BonusType type) {
        GameData data = data();
        data.activeBonus = type.name();
        data.bonusEndsAt = data.timerSeconds + plugin.settings().integer(Settings.BONUS_DURATION) * 60L;
        data.freeBlockUsed.clear();
        Effects.celebrate("<" + type.color() + "><bold>★ " + type.displayName() + " ★",
                "<gray>" + type.description() + " <white>(" + plugin.settings().integer(Settings.BONUS_DURATION) + " min)",
                Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
        Effects.soundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
        Bukkit.broadcast(Text.prefixed("<" + type.color() + "><bold>Bonus: " + type.displayName() + "!</bold></" + type.color()
                + "> <gray>" + type.description()));
        plugin.onSettingsChanged();
    }

    public void end() {
        GameData data = data();
        BonusType type = BonusType.parse(data.activeBonus);
        data.activeBonus = null;
        data.bonusEndsAt = 0;
        data.freeBlockUsed.clear();
        data.nextBonusAt = data.timerSeconds + plugin.settings().integer(Settings.BONUS_INTERVAL) * 60L;
        if (type != null) {
            Bukkit.broadcast(Text.prefixed("<gray>Bonus <white>" + type.displayName() + "</white> ist vorbei."));
            Effects.soundAll(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        }
        plugin.onSettingsChanged();
    }

    /** Admin: start a specific bonus now (or a random one). */
    public void force(CommandSender sender, BonusType type) {
        if (data().state != GameState.RUNNING) {
            sender.sendMessage(Text.prefixed("<red>Boni gibt es nur während einer laufenden Runde."));
            return;
        }
        if (type == null) {
            BonusType[] types = BonusType.values();
            type = types[ThreadLocalRandom.current().nextInt(types.length)];
        }
        start(type);
    }

    // ------------------------------------------------------------------ effects on the game

    public int adjustCost(int cost) {
        if (active() == BonusType.HALF_PRICE && cost > 0) {
            return Math.max(1, cost / 2);
        }
        return cost;
    }

    public boolean hasFreeBlock(Player player) {
        return active() == BonusType.FREE_BLOCK && !data().freeBlockUsed.contains(player.getUniqueId().toString());
    }

    public void useFreeBlock(Player player) {
        data().freeBlockUsed.add(player.getUniqueId().toString());
    }

    public int timeLevelMultiplier() {
        return active() == BonusType.DOUBLE_TIME ? 2 : 1;
    }

    public double xpMultiplier() {
        return active() == BonusType.XP_RUSH ? 2.0 : 1.0;
    }
}
