package net.kronig.gridlock.game;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.DeathMode;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.spawn.SpawnFinder;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Round lifecycle: lobby → spawn search → countdown → running → won/lost → reset. */
public final class GameController implements Listener {

    private static final int COUNTDOWN_SECONDS = 5;

    private final GridLockPlugin plugin;
    private final SpawnFinder spawnFinder;

    public GameController(GridLockPlugin plugin) {
        this.plugin = plugin;
        this.spawnFinder = new SpawnFinder(plugin);
    }

    private GameData data() {
        return plugin.data();
    }

    public World overworld() {
        return Bukkit.getWorlds().getFirst();
    }

    public Location spawnLocation() {
        GameData data = data();
        World world = data.spawnWorld != null ? Bukkit.getWorld(data.spawnWorld) : null;
        if (world == null) {
            return overworld().getSpawnLocation();
        }
        return new Location(world, data.spawnX, data.spawnY, data.spawnZ);
    }

    // ------------------------------------------------------------------ voting

    public void vote(Player player, SpawnPreset preset) {
        if (data().state != GameState.LOBBY) {
            player.sendMessage(Text.prefixed("<red>Abstimmen geht nur in der Lobby."));
            return;
        }
        String id = player.getUniqueId().toString();
        if (preset == null) {
            data().votes.remove(id);
            player.sendMessage(Text.prefixed("<gray>Deine Stimme wurde zurückgezogen."));
            return;
        }
        data().votes.put(id, preset.name());
        Bukkit.broadcast(Text.prefixed("<white>" + Text.escape(player.getName()) + "</white> <gray>stimmt für <white>"
                + preset.displayName() + "</white> " + preset.difficulty().format()));
    }

    /** Current favourite for display; ties show the first one, the real tie-break happens at start. */
    public SpawnPreset winningPreset() {
        return leaders().getFirst();
    }

    private List<SpawnPreset> leaders() {
        Map<SpawnPreset, Integer> counts = new EnumMap<>(SpawnPreset.class);
        for (String name : data().votes.values()) {
            SpawnPreset preset = SpawnPreset.parse(name);
            if (preset != null) {
                counts.merge(preset, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return List.of(SpawnPreset.RANDOM);
        }
        int best = counts.values().stream().max(Integer::compare).orElse(0);
        List<SpawnPreset> leaders = new ArrayList<>();
        counts.forEach((preset, count) -> {
            if (count == best) {
                leaders.add(preset);
            }
        });
        return leaders;
    }

    // ------------------------------------------------------------------ start

    public void start(CommandSender sender) {
        if (!sender.hasPermission(GridLockPlugin.ADMIN_PERMISSION)) {
            sender.sendMessage(Text.prefixed("<red>Nur Admins können die Challenge starten."));
            return;
        }
        if (data().state != GameState.LOBBY) {
            sender.sendMessage(Text.prefixed("<red>Die Challenge läuft bereits. Neue Runde: <white>/gl reset"));
            return;
        }
        List<SpawnPreset> leaders = leaders();
        SpawnPreset voted = leaders.get(ThreadLocalRandom.current().nextInt(leaders.size()));
        if (leaders.size() > 1) {
            Bukkit.broadcast(Text.prefixed("<gray>Gleichstand! Der Zufall entscheidet: <white>" + voted.displayName()));
        }
        SpawnPreset preset = voted.resolve(ThreadLocalRandom.current());
        if (voted == SpawnPreset.RANDOM) {
            Bukkit.broadcast(Text.prefixed("<light_purple>Zufall</light_purple> <gray>hat ausgelost: <white>"
                    + preset.displayName() + "</white> " + preset.difficulty().format()));
        }
        data().state = GameState.STARTING;
        data().preset = preset.name();
        Bukkit.broadcast(Text.prefixed("<gray>Suche Spawn: <white>" + preset.displayName() + "</white> "
                + preset.difficulty().format() + " <gray>…"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Text.mm("<gradient:#ff3b3b:#ff9e3b><bold>GridLock"),
                    Text.mm("<gray>Suche Spawn: <white>" + preset.displayName()),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(10), Duration.ofMillis(300))));
        }

        World world = overworld();
        spawnFinder.find(world, preset, plugin.settings().integer(Settings.SPAWN_RADIUS))
                .whenComplete((location, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || location == null) {
                        plugin.getLogger().warning("Spawn-Suche fehlgeschlagen: " + error);
                        data().state = GameState.LOBBY;
                        Bukkit.broadcast(Text.prefixed("<red>Spawn-Suche fehlgeschlagen, bitte nochmal starten."));
                        return;
                    }
                    countdown(location);
                }));
    }

    private void countdown(Location spawn) {
        World world = spawn.getWorld();
        GameData data = data();
        data.spawnWorld = world.getName();
        data.spawnX = spawn.getX();
        data.spawnY = spawn.getY();
        data.spawnZ = spawn.getZ();
        data.fieldOrigins.clear();
        plugin.fields().clear();
        plugin.fields().unlock(world, spawn.getBlockX(), spawn.getBlockZ());
        world.setSpawnLocation(spawn);
        plugin.getLogger().info("Spawn gefunden (" + data.preset + "): " + spawn.getBlockX() + " " + spawn.getBlockY()
                + " " + spawn.getBlockZ());
        world.setGameRule(GameRules.RESPAWN_RADIUS, 0);
        world.setTime(0);
        world.setStorm(false);
        plugin.saveData();

        new BukkitRunnable() {
            int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (remaining > 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(Title.title(Text.mm("<gold><bold>" + remaining),
                                Text.mm("<gray>Die Challenge beginnt…"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f + remaining * 0.1f);
                    }
                    remaining--;
                    return;
                }
                cancel();
                launch();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void launch() {
        GameData data = data();
        data.state = GameState.RUNNING;
        data.timerSeconds = 0;
        data.timerPaused = false;
        data.secondsSinceTimeLevel = 0;
        data.finishInfo = null;
        data.initializedPlayers.clear();
        data.playtime.clear();

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : players) {
            prepare(player);
            data.initializedPlayers.add(player.getUniqueId().toString());
            player.showTitle(Title.title(Text.mm("<green><bold>Los geht's!"),
                    Text.mm("<gray>Schleich gegen die <red>Border</red>, um für Level zu erweitern"),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
        }
        plugin.levels().applyStartLevels(players);
        Bukkit.broadcast(Text.prefixed("<green>Die Challenge läuft!</green> <gray>Ihr habt ein 1x1-Feld. "
                + "Schleicht gegen die rote Border, um für Level zu erweitern. Ziel: den Enderdrachen besiegen."));
        plugin.saveData();
    }

    /** Puts a player into the round: at the spawn, survival, empty inventory. */
    private void prepare(Player player) {
        player.teleport(spawnLocation());
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setRespawnLocation(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameData data = data();
        switch (data.state) {
            case LOBBY, STARTING -> plugin.lobby().send(player);
            case RUNNING, WON -> {
                String id = player.getUniqueId().toString();
                if (!data.initializedPlayers.contains(id)) {
                    prepare(player);
                    plugin.levels().applyLateJoinLevels(player);
                    data.initializedPlayers.add(id);
                    player.sendMessage(Text.prefixed("<gray>Willkommen! Die Challenge läuft bereits – du startest im Feld."));
                } else if (plugin.lobby().isLobby(player.getWorld())) {
                    player.teleport(spawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
            case LOST -> {
                if (plugin.lobby().isLobby(player.getWorld())) {
                    player.teleport(spawnLocation());
                }
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        plugin.levels().sync();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.forget(event.getPlayer());
    }

    // ------------------------------------------------------------------ win / lose

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon) || data().state != GameState.RUNNING) {
            return;
        }
        GameData data = data();
        data.state = GameState.WON;
        data.finishInfo = Text.time(data.timerSeconds);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Text.mm("<gold><bold>Geschafft!"),
                    Text.mm("<gray>Drache besiegt in <white>" + data.finishInfo),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.broadcast(Text.prefixed("<gold><bold>Challenge geschafft!</bold></gold> <gray>Zeit: <white>" + data.finishInfo
                + "</white> <dark_gray>|</dark_gray> Feld gesamt: <white>" + plugin.fields().totalSize() + " Blöcke"));
        plugin.saveData();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (data().state != GameState.RUNNING
                || plugin.settings().choice(Settings.DEATH_MODE, DeathMode.class) != DeathMode.HARDCORE) {
            return;
        }
        Player dead = event.getPlayer();
        GameData data = data();
        data.state = GameState.LOST;
        data.finishInfo = dead.getName() + " nach " + Text.time(data.timerSeconds);
        Component cause = event.deathMessage() != null ? event.deathMessage() : Component.text(dead.getName() + " ist gestorben");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Text.mm("<red><bold>Challenge gescheitert"),
                    cause.colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.6f, 0.6f);
        }
        Bukkit.broadcast(Text.prefixed("<red><bold>Gescheitert!</bold></red> <gray>" + Text.escape(dead.getName())
                + " ist gestorben. Zeit: <white>" + Text.time(data.timerSeconds)
                + "</white>. Neue Runde: <white>/gl reset"));
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isDead()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        });
        plugin.saveData();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (data().state == GameState.LOST) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        }
    }

    // ------------------------------------------------------------------ reset

    public void resetRound(CommandSender sender) {
        if (!sender.hasPermission(GridLockPlugin.ADMIN_PERMISSION)) {
            sender.sendMessage(Text.prefixed("<red>Nur Admins können eine neue Runde starten."));
            return;
        }
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.writeString(plugin.resetFlag(), "reset");
        } catch (IOException e) {
            sender.sendMessage(Text.prefixed("<red>Reset-Markierung konnte nicht geschrieben werden: " + e.getMessage()));
            return;
        }
        GameData fresh = new GameData();
        fresh.hiddenSidebar.addAll(data().hiddenSidebar);
        plugin.replaceData(fresh);
        Bukkit.broadcast(Text.prefixed("<red>Neue Runde!</red> <gray>Die Welt wird gelöscht, der Server startet neu…"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.kick(Text.mm("<gradient:#ff3b3b:#ff9e3b><bold>GridLock</bold></gradient>\n\n"
                        + "<gray>Neue Runde wird vorbereitet.\n<white>Gleich wieder joinen!"));
            }
            Bukkit.shutdown();
        }, 40L);
    }

    /** Deletes the challenge worlds before the server loads them. Called from onLoad. */
    public static void deleteWorlds(GridLockPlugin plugin) {
        String levelName = "world";
        java.nio.file.Path properties = java.nio.file.Path.of("server.properties");
        if (Files.exists(properties)) {
            java.util.Properties props = new java.util.Properties();
            try (var reader = Files.newBufferedReader(properties)) {
                props.load(reader);
                levelName = props.getProperty("level-name", "world");
            } catch (IOException e) {
                plugin.getLogger().warning("server.properties nicht lesbar, nehme 'world': " + e.getMessage());
            }
        }
        java.io.File container = Bukkit.getWorldContainer();
        for (String name : new String[]{levelName, levelName + "_nether", levelName + "_the_end"}) {
            java.nio.file.Path dir = container.toPath().resolve(name);
            if (!Files.exists(dir)) {
                continue;
            }
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                plugin.getLogger().info("Welt gelöscht: " + name);
            } catch (IOException | java.io.UncheckedIOException e) {
                plugin.getLogger().severe("Welt " + name + " konnte nicht gelöscht werden: " + e.getMessage());
            }
        }
    }
}
