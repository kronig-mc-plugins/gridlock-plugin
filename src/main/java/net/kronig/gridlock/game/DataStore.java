package net.kronig.gridlock.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class DataStore {

    private static final Gson GSON = new GsonBuilder().create();

    private final JavaPlugin plugin;
    private final Path file;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.json");
    }

    public GameData load() {
        if (!Files.exists(file)) {
            return new GameData();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            GameData data = GSON.fromJson(reader, GameData.class);
            return data != null ? data : new GameData();
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "data.json konnte nicht gelesen werden, starte mit leeren Daten", e);
            return new GameData();
        }
    }

    public void save(GameData data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("data.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "data.json konnte nicht gespeichert werden", e);
        }
    }
}
