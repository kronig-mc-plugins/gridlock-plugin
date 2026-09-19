package net.kronig.gridlock.ui;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server list MOTD: fixed logo line plus a rotating line of live stats and slogans. */
public final class MotdManager implements Listener {

    /** Rough width of a MOTD line in "average" characters, used for centering. */
    private static final int LINE_WIDTH = 58;

    private static final int ICON_SIZE = 64;

    private final GridLockPlugin plugin;
    private CachedServerIcon icon;

    public MotdManager(GridLockPlugin plugin) {
        this.plugin = plugin;
        reloadIcon();
    }

    /** Loads plugins/GridLock/server-icon.png (bundled default on first start), scaled to 64x64 if needed. */
    public void reloadIcon() {
        icon = null;
        File file = new File(plugin.getDataFolder(), "server-icon.png");
        if (!file.exists()) {
            plugin.saveResource("server-icon.png", false);
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                plugin.getLogger().warning("server-icon.png ist kein gültiges Bild.");
                return;
            }
            if (image.getWidth() != ICON_SIZE || image.getHeight() != ICON_SIZE) {
                BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = scaled.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(image, 0, 0, ICON_SIZE, ICON_SIZE, null);
                graphics.dispose();
                image = scaled;
            }
            icon = Bukkit.loadServerIcon(image);
        } catch (Exception e) {
            plugin.getLogger().warning("Server-Icon konnte nicht geladen werden: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        if (icon != null && plugin.settings().bool(Settings.MOTD_ICON)) {
            event.setServerIcon(icon);
        }
        if (!plugin.settings().bool(Settings.MOTD_ENABLED)) {
            return;
        }
        List<String> rotation = rotation();
        int interval = plugin.settings().integer(Settings.MOTD_INTERVAL);
        String second = rotation.isEmpty() ? ""
                : rotation.get((int) ((System.currentTimeMillis() / 1000 / interval) % rotation.size()));

        String first = "<gradient:#ff3b3b:#ff9e3b><bold>▌ GridLock ▐</bold></gradient> <dark_gray>»</dark_gray> "
                + headline(plugin.data());
        event.motd(center(first).append(Component.newline()).append(center(second)));

        if (plugin.settings().bool(Settings.MOTD_HOVER)) {
            List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
            listed.clear();
            for (String line : hoverLines(plugin.data())) {
                listed.add(new PaperServerListPingEvent.ListedPlayerInfo(line, UUID.randomUUID()));
            }
        }
    }

    private static String headline(GameData data) {
        return switch (data.state) {
            case LOBBY -> "<aqua>Lobby offen";
            case STARTING -> "<yellow>Startet gleich!";
            case RUNNING -> data.timerPaused ? "<red>Pausiert" : "<green>Läuft • <gold>" + Text.time(data.timerSeconds);
            case WON -> "<gold>Geschafft!";
            case LOST -> "<red>Gescheitert";
        };
    }

    private List<String> rotation() {
        List<String> lines = new ArrayList<>();
        if (plugin.settings().bool(Settings.MOTD_STATS)) {
            lines.addAll(statLines(plugin.data()));
        }
        lines.addAll(plugin.settings().slogans());
        return lines;
    }

    private List<String> statLines(GameData data) {
        List<String> lines = new ArrayList<>();
        int online = Bukkit.getOnlinePlayers().size();
        switch (data.state) {
            case LOBBY, STARTING -> {
                SpawnPreset leader = plugin.game().winningPreset();
                lines.add("<gray>Wähle deinen Spawn – Favorit: <white>" + leader.displayName() + " "
                        + leader.difficulty().format());
                lines.add("<gray>" + online + " Spieler warten auf den Start");
            }
            case RUNNING -> {
                lines.add("<gray>Feld: <white>" + plugin.fields().totalSize() + " Blöcke</white> <dark_gray>•</dark_gray> <gray>Zeit: <gold>"
                        + Text.time(data.timerSeconds));
                lines.add(dimensionLine());
                lines.add(online == 0
                        ? "<gray>Niemand online – das Feld wartet auf dich"
                        : "<white>" + online + "</white> <gray>" + (online == 1 ? "Spieler kämpft" : "Spieler kämpfen")
                        + " um jeden Block");
            }
            case WON -> lines.add("<gold>✔ Enderdrache besiegt in <white>" + data.finishInfo + "</white>!");
            case LOST -> lines.add("<red>✖ Gescheitert: <gray>" + Text.escape(String.valueOf(data.finishInfo)));
        }
        return lines;
    }

    private String dimensionLine() {
        StringBuilder line = new StringBuilder();
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.fields().isChallengeWorld(world)) {
                continue;
            }
            int size = plugin.fields().size(world);
            if (line.length() > 0) {
                line.append(" <dark_gray>•</dark_gray> ");
            }
            String color = switch (world.getEnvironment()) {
                case NETHER -> "<red>";
                case THE_END -> "<light_purple>";
                default -> "<green>";
            };
            line.append(color).append(ExpansionManager.dimensionName(world)).append(" <white>")
                    .append(size == 0 ? "–" : String.valueOf(size));
        }
        return line.toString();
    }

    private List<String> hoverLines(GameData data) {
        List<String> lines = new ArrayList<>();
        lines.add("§c§lGridLock §7– 1x1-Challenge");
        lines.add("§8────────────────");
        switch (data.state) {
            case LOBBY, STARTING -> lines.add("§7Status: §bLobby");
            case RUNNING -> {
                lines.add("§7Status: §aLäuft" + (data.timerPaused ? " §c(pausiert)" : ""));
                lines.add("§7Zeit: §6" + Text.time(data.timerSeconds));
                lines.add("§7Feld gesamt: §f" + plugin.fields().totalSize() + " Blöcke");
            }
            case WON -> lines.add("§7Status: §6Geschafft in " + data.finishInfo);
            case LOST -> lines.add("§7Status: §cGescheitert");
        }
        lines.add("§7Online: §f" + Bukkit.getOnlinePlayers().size());
        return lines;
    }

    private static Component center(String miniMessage) {
        Component component = Text.mm(miniMessage);
        int length = Text.plain(component).length();
        int padding = Math.max(0, (LINE_WIDTH - length) / 2);
        return Component.text(" ".repeat(padding)).append(component);
    }
}
