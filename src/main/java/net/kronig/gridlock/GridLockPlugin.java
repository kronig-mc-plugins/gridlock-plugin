package net.kronig.gridlock;

import net.kronig.gridlock.command.GridLockCommand;
import net.kronig.gridlock.command.TimerCommand;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.field.BorderLines;
import net.kronig.gridlock.field.BorderListener;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.field.FieldManager;
import net.kronig.gridlock.field.HardStop;
import net.kronig.gridlock.field.ShulkerWall;
import net.kronig.gridlock.game.DataStore;
import net.kronig.gridlock.game.GameController;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.game.Lobby;
import net.kronig.gridlock.gui.GuiListener;
import net.kronig.gridlock.level.LevelManager;
import net.kronig.gridlock.mod.ModLink;
import net.kronig.gridlock.timer.TimerManager;
import net.kronig.gridlock.ui.ActionBars;
import net.kronig.gridlock.ui.MotdManager;
import net.kronig.gridlock.ui.SidebarManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GridLockPlugin extends JavaPlugin {

    public static final String ADMIN_PERMISSION = "gridlock.admin";

    private Settings settings;
    private DataStore dataStore;
    private GameData data;
    private Lobby lobby;
    private FieldManager fields;
    private LevelManager levels;
    private ActionBars actionBars;
    private ExpansionManager expansion;
    private BorderListener borderListener;
    private BorderLines borderLines;
    private ShulkerWall shulkerWall;
    private HardStop hardStop;
    private ModLink modLink;
    private boolean linesDirty;
    private GameController game;
    private TimerManager timer;
    private SidebarManager sidebar;
    private MotdManager motd;

    @Override
    public void onLoad() {
        // A requested reset deletes the challenge worlds before the server loads them.
        if (Files.exists(resetFlag())) {
            getLogger().info("Reset angefordert – lösche Oberwelt, Nether und End …");
            GameController.deleteWorlds(this);
            try {
                Files.deleteIfExists(resetFlag());
            } catch (IOException e) {
                getLogger().warning("reset.flag konnte nicht gelöscht werden: " + e.getMessage());
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();

        dataStore = new DataStore(this);
        data = dataStore.load();
        if (data.state == GameState.STARTING) {
            data.state = GameState.LOBBY;
        }

        lobby = new Lobby(this);
        lobby.load();

        fields = new FieldManager(settings, this::data, lobby::world);
        fields.loadFrom(data);
        levels = new LevelManager(settings, this::data, player -> fields.isChallengeWorld(player.getWorld()));
        actionBars = new ActionBars();
        expansion = new ExpansionManager(settings, fields, levels, actionBars);
        borderListener = new BorderListener(this, fields, expansion);
        borderLines = new BorderLines(settings, fields, expansion, this::data);
        // Many unlocks in one tick (e.g. /gl unlock 25) only redraw the line once.
        shulkerWall = new ShulkerWall(this);
        hardStop = new HardStop(this, fields);
        modLink = new ModLink(this, fields, expansion);
        modLink.register();
        fields.onColumnChange(modLink::onFieldChange);
        borderLines.setModHooks(modLink::hasMod, modLink::hideFromModPlayers);
        fields.onUnlock(world -> {
            linesDirty = true;
            borderLines.flash(world);
        });
        game = new GameController(this);
        timer = new TimerManager(this);
        sidebar = new SidebarManager(this);
        motd = new MotdManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(), this);
        pm.registerEvents(lobby, this);
        pm.registerEvents(lobby.showcase(), this);
        pm.registerEvents(levels, this);
        pm.registerEvents(borderListener, this);
        pm.registerEvents(shulkerWall, this);
        pm.registerEvents(hardStop, this);
        pm.registerEvents(new Listener() {
            // Terrain changed next to the border: redraw right away instead of waiting for the next interval.
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onBreak(BlockBreakEvent event) {
                linesDirty = true;
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onPlace(BlockPlaceEvent event) {
                linesDirty = true;
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onExplode(EntityExplodeEvent event) {
                linesDirty = true;
            }
        }, this);
        pm.registerEvents(game, this);
        pm.registerEvents(motd, this);

        registerCommand("gridlock", "GridLock-Menü, Einstellungen und Verwaltung", List.of("gl", "grid"),
                new GridLockCommand(this));
        registerCommand("timer", "Challenge-Timer anzeigen und steuern", new TimerCommand(this));

        var scheduler = Bukkit.getScheduler();
        scheduler.runTaskTimer(this, expansion::tick, 1L, 1L);
        scheduler.runTaskTimer(this, hardStop::tick, 1L, 1L);
        scheduler.runTaskTimer(this, modLink::tick, 2L, 2L);
        scheduler.runTaskTimer(this, borderLines::update, 10L, 10L);
        scheduler.runTaskTimer(this, () -> {
            if (linesDirty) {
                linesDirty = false;
                // Look settings are baked into the displays, so redraw them from scratch.
        borderLines.clear();
        borderLines.update();
            }
        }, 1L, 1L);
        scheduler.runTaskTimer(this, borderLines::updateColors, 2L, 2L);
        scheduler.runTaskTimer(this, borderListener::safetyNet, 10L, 10L);
        // Entities of already loaded chunks are only reliably available once the server is ticking.
        scheduler.runTaskLater(this, () -> {
            int removed = shulkerWall.cleanup();
            if (removed > 0) {
                getLogger().info(removed + " alte Border-Pfosten (Shulker) entfernt.");
            }
        }, 40L);
        scheduler.runTaskTimer(this, () -> {
            timer.tickSecond();
            sidebar.update();
        }, 20L, 20L);
        scheduler.runTaskTimer(this, levels::sync, 5L, 5L);
        scheduler.runTaskTimer(this, lobby.showcase()::animate, 2L, 2L);
        scheduler.runTaskTimer(this, lobby.showcase()::particles, 5L, 5L);
        scheduler.runTaskTimer(this, lobby.showcase()::updateTexts, 10L, 10L);
        scheduler.runTaskTimer(this, this::saveData, 20L * 60, 20L * 60);

        // Players that were online during /reload.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (data.state == GameState.LOBBY) {
                lobby.send(player);
            }
        }
        getLogger().info("GridLock aktiv – Status: " + data.state);
    }

    @Override
    public void onDisable() {
        if (borderLines != null) {
            borderLines.clear();
        }
        if (lobby != null) {
            lobby.showcase().despawn();
        }
        if (hardStop != null) {
            hardStop.clearAll();
        }
        if (data != null && dataStore != null && fields != null) {
            saveData();
        }
    }

    public void saveData() {
        fields.saveTo(data);
        dataStore.save(data);
    }

    /** Replaces all round data (used by the reset) and persists it immediately. */
    public void replaceData(GameData fresh) {
        this.data = fresh;
        fields.loadFrom(fresh);
        dataStore.save(fresh);
    }

    public Path resetFlag() {
        return getDataFolder().toPath().resolve("reset.flag");
    }

    /** Re-applies anything that depends on settings. */
    public void onSettingsChanged() {
        levels.sync();
        sidebar.update();
        borderLines.update();
    }

    public void forget(Player player) {
        expansion.forget(player);
        borderListener.forget(player);
        hardStop.forget(player);
        modLink.forget(player);
        actionBars.forget(player);
        sidebar.forget(player);
        levels.forget(player);
    }

    public SidebarManager sidebar() {
        return sidebar;
    }

    public ModLink modLink() {
        return modLink;
    }

    public BorderLines borderLines() {
        return borderLines;
    }

    public ShulkerWall shulkerWall() {
        return shulkerWall;
    }

    public MotdManager motd() {
        return motd;
    }

    public Settings settings() {
        return settings;
    }

    public GameData data() {
        return data;
    }

    public Lobby lobby() {
        return lobby;
    }

    public FieldManager fields() {
        return fields;
    }

    public LevelManager levels() {
        return levels;
    }

    public ActionBars actionBars() {
        return actionBars;
    }

    public GameController game() {
        return game;
    }

    public TimerManager timer() {
        return timer;
    }
}
