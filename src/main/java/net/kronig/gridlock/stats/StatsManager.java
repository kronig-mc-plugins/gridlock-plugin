package net.kronig.gridlock.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Per-round statistics (live in GameData) and the all-time record list in stats.json. */
public final class StatsManager {

    public static final class PlayerRecord {
        public String name;
        public int blocks;
        public int deaths;
        public int levelsSpent;
        public long playtime;
    }

    public static final class RoundRecord {
        public String date;
        public String result;
        public String preset;
        public long seconds;
        public int totalBlocks;
        public int blocksSold;
        public Map<String, PlayerRecord> players = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final GridLockPlugin plugin;
    private final Path file;
    private List<RoundRecord> history;

    public StatsManager(GridLockPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("stats.json");
    }

    // ------------------------------------------------------------------ live counters

    public void countBlock(UUID player) {
        plugin.data().blocksBought.merge(player.toString(), 1, Integer::sum);
    }

    public void countDeath(UUID player) {
        plugin.data().deaths.merge(player.toString(), 1, Integer::sum);
    }

    public void countLevels(UUID player, int levels) {
        if (levels > 0) {
            plugin.data().levelsSpent.merge(player.toString(), levels, Integer::sum);
        }
    }

    public void reset() {
        GameData data = plugin.data();
        data.blocksBought.clear();
        data.deaths.clear();
        data.levelsSpent.clear();
        data.blocksSold = 0;
        data.roundStartedEpoch = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ current round

    /** All players that took part in the current round, sorted by blocks bought. */
    public List<PlayerRecord> currentPlayers() {
        GameData data = plugin.data();
        Map<String, PlayerRecord> records = new LinkedHashMap<>();
        for (String id : data.initializedPlayers) {
            PlayerRecord record = new PlayerRecord();
            OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(id));
            record.name = offline.getName() != null ? offline.getName() : id.substring(0, 8);
            record.blocks = data.blocksBought.getOrDefault(id, 0);
            record.deaths = data.deaths.getOrDefault(id, 0);
            record.levelsSpent = data.levelsSpent.getOrDefault(id, 0);
            record.playtime = data.playtime.getOrDefault(id, 0L);
            records.put(id, record);
        }
        List<PlayerRecord> list = new ArrayList<>(records.values());
        list.sort(Comparator.comparingInt((PlayerRecord r) -> r.blocks).reversed());
        return list;
    }

    /** Chat summary of the current round. */
    public List<String> summaryLines() {
        GameData data = plugin.data();
        List<String> lines = new ArrayList<>();
        lines.add("<gray>Zeit: <gold><bold>" + Text.time(data.timerSeconds));
        StringBuilder fields = new StringBuilder();
        for (World world : Bukkit.getWorlds()) {
            if (plugin.fields().isChallengeWorld(world) && plugin.fields().size(world) > 0) {
                if (!fields.isEmpty()) {
                    fields.append(" <dark_gray>·</dark_gray> ");
                }
                fields.append("<gray>").append(ExpansionManager.dimensionName(world)).append(" <white>")
                        .append(plugin.fields().size(world));
            }
        }
        lines.add("<gray>Feld: <white>" + plugin.fields().totalSize() + " Blöcke</white>  " + fields
                + (data.blocksSold > 0 ? " <dark_gray>(" + data.blocksSold + " verkauft)" : ""));
        List<PlayerRecord> players = currentPlayers();
        if (!players.isEmpty()) {
            PlayerRecord mvp = players.getFirst();
            lines.add("<gray>Meiste Blöcke: <yellow>" + Text.escape(mvp.name) + " <white>" + mvp.blocks);
            PlayerRecord spender = players.stream().max(Comparator.comparingInt(r -> r.levelsSpent)).orElse(mvp);
            lines.add("<gray>Meiste Level ausgegeben: <yellow>" + Text.escape(spender.name) + " <white>" + spender.levelsSpent);
            PlayerRecord deaths = players.stream().max(Comparator.comparingInt(r -> r.deaths)).orElse(mvp);
            if (deaths.deaths > 0) {
                lines.add("<gray>Meiste Tode: <red>" + Text.escape(deaths.name) + " <white>" + deaths.deaths);
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------ history

    private List<RoundRecord> history() {
        if (history == null) {
            history = new ArrayList<>();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    List<RoundRecord> loaded = GSON.fromJson(reader, new TypeToken<List<RoundRecord>>() {
                    }.getType());
                    if (loaded != null) {
                        history = loaded;
                    }
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "stats.json konnte nicht gelesen werden", e);
                }
            }
        }
        return history;
    }

    /** Stores the finished round in stats.json. */
    public void recordRound(GameState result) {
        GameData data = plugin.data();
        RoundRecord record = new RoundRecord();
        record.date = LocalDate.now().toString();
        record.result = result.name();
        SpawnPreset preset = SpawnPreset.parse(data.preset);
        record.preset = preset != null ? preset.displayName() : "?";
        record.seconds = data.timerSeconds;
        record.totalBlocks = plugin.fields().totalSize();
        record.blocksSold = data.blocksSold;
        for (PlayerRecord player : currentPlayers()) {
            record.players.put(player.name, player);
        }
        history().add(record);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(history(), writer);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "stats.json konnte nicht gespeichert werden", e);
        }
    }

    /** Fastest wins first. */
    public List<RoundRecord> bestRounds(int limit) {
        List<RoundRecord> wins = new ArrayList<>();
        for (RoundRecord record : history()) {
            if (GameState.WON.name().equals(record.result)) {
                wins.add(record);
            }
        }
        wins.sort(Comparator.comparingLong(r -> r.seconds));
        return wins.subList(0, Math.min(limit, wins.size()));
    }

    public int roundsPlayed() {
        return history().size();
    }
}
