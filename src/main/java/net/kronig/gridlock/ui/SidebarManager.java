package net.kronig.gridlock.ui;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player sidebar with timer, own playtime and field info. */
public final class SidebarManager {

    private static final int MAX_LINES = 15;
    private static final String OBJECTIVE = "gridlock";

    private final GridLockPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public SidebarManager(GridLockPlugin plugin) {
        this.plugin = plugin;
    }

    public void update() {
        boolean enabled = plugin.settings().bool(Settings.TIMER_SIDEBAR);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled || plugin.data().hiddenSidebar.contains(player.getUniqueId().toString())) {
                remove(player);
                continue;
            }
            Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> createBoard());
            if (player.getScoreboard() != board) {
                player.setScoreboard(board);
            }
            render(board, lines(player));
        }
    }

    private Scoreboard createBoard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                Text.mm("<gradient:#ff3b3b:#ff9e3b><bold>◼ GridLock ◼</bold></gradient>"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        return board;
    }

    private static void render(Scoreboard board, List<String> lines) {
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = "line" + i;
            if (i >= count) {
                board.resetScores(entry);
                continue;
            }
            var score = objective.getScore(entry);
            score.setScore(count - i);
            score.customName(Text.mm(lines.get(i)));
        }
    }

    private List<String> lines(Player player) {
        GameData data = plugin.data();
        List<String> lines = new ArrayList<>();
        lines.add("<dark_gray>" + "─".repeat(16));
        if (data.state == GameState.LOBBY || data.state == GameState.STARTING) {
            SpawnPreset leader = plugin.game().winningPreset();
            lines.add("<gray>Status: <aqua>" + (data.state == GameState.LOBBY ? "Lobby" : "Startet…"));
            lines.add("");
            lines.add("<gray>Spawn-Favorit:");
            lines.add(" <white>" + leader.displayName());
            lines.add(" " + leader.difficulty().format());
            lines.add("<gray>Stimmen: <white>" + data.votes.size() + "<gray>/<white>" + Bukkit.getOnlinePlayers().size());
            lines.add("<gray>Bereit: <green>" + plugin.game().readyCount() + "<gray>/<white>" + Bukkit.getOnlinePlayers().size());
            lines.add("");
            lines.add("<gray>Säule klicken: <white>abstimmen");
            lines.add("<gray>Grüne Säule: <white>bereit");
        } else {
            World world = player.getWorld();
            String status = switch (data.state) {
                case WON -> "<gold>✔ Geschafft";
                case LOST -> "<red>✖ Gescheitert";
                default -> data.timerPaused ? "<red>⏸ Pausiert" : "<green>● Läuft";
            };
            lines.add("<gray>Status: " + status);
            lines.add("<gray>Zeit: <gold><bold>" + Text.time(data.timerSeconds));
            lines.add("<gray>Deine Zeit: <yellow>" + Text.time(plugin.timer().playtime(player)));
            lines.add("");
            if (plugin.fields().isChallengeWorld(world)) {
                lines.add("<gray>Welt: <white>" + ExpansionManager.dimensionName(world));
                lines.add("<gray>Feld: <white>" + plugin.fields().size(world) + " <gray>Blöcke");
                lines.add("<gray>Nächster: <green>" + plugin.fields().nextCost(world) + " Level");
            }
            lines.add("");
            // Own levels per player only make sense outside the shared team pool.
            boolean showLevels = plugin.levels().mode() != PaymentMode.POOL;
            lines.add(showLevels ? "<gray>Spieler: <dark_gray>Level · Zeit" : "<gray>Spielzeit:");
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            online.sort(Comparator.comparingLong((Player p) -> plugin.timer().playtime(p)).reversed());
            int shown = Math.min(online.size(), MAX_LINES - lines.size() - 1);
            for (int i = 0; i < shown; i++) {
                Player other = online.get(i);
                String color = other.equals(player) ? "<yellow>" : "<white>";
                String level = showLevels ? " <green>" + other.getLevel() + "L</green>" : "";
                lines.add(" " + color + Text.escape(other.getName()) + level + " <dark_gray>"
                        + Text.time(plugin.timer().playtime(other)));
            }
        }
        lines.add("<dark_gray>" + "─".repeat(16) + " ");
        return lines;
    }

    public void remove(Player player) {
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board != null && player.getScoreboard() == board) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void forget(Player player) {
        boards.remove(player.getUniqueId());
    }
}
