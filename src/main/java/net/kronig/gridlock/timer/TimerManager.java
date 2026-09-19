package net.kronig.gridlock.timer;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Global challenge timer, per-player playtime and time-based levels. Ticks once per second. */
public final class TimerManager {

    private final GridLockPlugin plugin;

    public TimerManager(GridLockPlugin plugin) {
        this.plugin = plugin;
    }

    public void tickSecond() {
        GameData data = plugin.data();
        if (data.state == GameState.RUNNING && !data.timerPaused) {
            boolean anyoneOnline = !Bukkit.getOnlinePlayers().isEmpty();
            if (anyoneOnline || plugin.settings().bool(Settings.TIMER_RUN_EMPTY)) {
                data.timerSeconds++;
                tickTimeLevels(data);
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.lobby().isLobby(player.getWorld())) {
                    data.playtime.merge(player.getUniqueId().toString(), 1L, Long::sum);
                }
            }
        }
        showActionBars(data);
    }

    private void tickTimeLevels(GameData data) {
        if (!plugin.settings().bool(Settings.TIME_LEVELS)) {
            data.secondsSinceTimeLevel = 0;
            return;
        }
        data.secondsSinceTimeLevel++;
        if (data.secondsSinceTimeLevel >= plugin.settings().integer(Settings.TIME_INTERVAL) * 60L) {
            data.secondsSinceTimeLevel = 0;
            plugin.levels().grantTimeLevels();
        }
    }

    private void showActionBars(GameData data) {
        if (!plugin.settings().bool(Settings.TIMER_ACTIONBAR)) {
            return;
        }
        Component bar = actionBar(data);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.actionBars().isFree(player)) {
                player.sendActionBar(bar);
            }
        }
    }

    private Component actionBar(GameData data) {
        String time = Text.time(data.timerSeconds);
        return switch (data.state) {
            case LOBBY -> {
                SpawnPreset leader = plugin.game().winningPreset();
                yield Text.mm("<aqua>Lobby</aqua> <dark_gray>|</dark_gray> <gray>Spawn: <white>" + leader.displayName()
                        + "</white> <dark_gray>|</dark_gray> <gray>Kompass = abstimmen");
            }
            case STARTING -> Text.mm("<yellow>Die Challenge startet…");
            case RUNNING -> data.timerPaused
                    ? Text.mm("<red>⏸ Timer pausiert</red> <dark_gray>|</dark_gray> <gray>" + time)
                    : Text.mm("<gold>⏱ <bold>" + time + "</bold>");
            case WON -> Text.mm("<gold>✔ Geschafft in <bold>" + data.finishInfo + "</bold>");
            case LOST -> Text.mm("<red>✖ Gescheitert: <gray>" + Text.escape(String.valueOf(data.finishInfo)));
        };
    }

    public void setPaused(boolean paused, CommandSender sender) {
        GameData data = plugin.data();
        if (data.state != GameState.RUNNING) {
            sender.sendMessage(Text.prefixed("<red>Der Timer läuft nur während einer Runde."));
            return;
        }
        if (data.timerPaused == paused) {
            sender.sendMessage(Text.prefixed("<gray>Der Timer ist bereits " + (paused ? "pausiert." : "aktiv.")));
            return;
        }
        data.timerPaused = paused;
        Bukkit.broadcast(Text.prefixed(paused
                ? "<red>⏸ Timer pausiert</red> <gray>bei " + Text.time(data.timerSeconds)
                : "<green>▶ Timer läuft weiter</green> <gray>ab " + Text.time(data.timerSeconds)));
    }

    public void reset(CommandSender sender) {
        GameData data = plugin.data();
        data.timerSeconds = 0;
        data.secondsSinceTimeLevel = 0;
        Bukkit.broadcast(Text.prefixed("<gray>Der Timer wurde von <white>" + Text.escape(sender.getName())
                + "</white> zurückgesetzt."));
    }

    /** Admin: set/add/remove seconds on the challenge timer. */
    public void adjust(String operation, long seconds, CommandSender sender) {
        GameData data = plugin.data();
        data.timerSeconds = switch (operation) {
            case "add" -> data.timerSeconds + seconds;
            case "remove" -> Math.max(0, data.timerSeconds - seconds);
            default -> seconds;
        };
        Bukkit.broadcast(Text.prefixed("<gray>Timer von <white>" + Text.escape(sender.getName())
                + "</white> auf <gold>" + Text.time(data.timerSeconds) + "</gold> gesetzt."));
    }

    public long playtime(Player player) {
        return plugin.data().playtime.getOrDefault(player.getUniqueId().toString(), 0L);
    }
}
