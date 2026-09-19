package net.kronig.gridlock.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Category;
import net.kronig.gridlock.config.PaymentMode;
import net.kronig.gridlock.config.Setting;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.game.GameState;
import net.kronig.gridlock.gui.CategoryMenu;
import net.kronig.gridlock.gui.MainMenu;
import net.kronig.gridlock.gui.SpawnMenu;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class GridLockCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of(
            "menu", "settings", "config", "spawn", "vote", "info", "pool", "start", "reset", "reload", "unlock", "help");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("start", "reset", "reload", "unlock");

    private final GridLockPlugin plugin;

    public GridLockCommand(GridLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBCOMMANDS.contains(sub) && !isAdmin(sender)) {
            sender.sendMessage(Text.prefixed("<red>Dafür brauchst du Admin-Rechte."));
            return;
        }
        switch (sub) {
            case "menu", "gui" -> ifPlayer(sender, player -> new MainMenu(plugin, player).open());
            case "settings", "einstellungen" -> settings(sender, args);
            case "config" -> config(sender, args);
            case "spawn" -> ifPlayer(sender, player -> new SpawnMenu(plugin, player).open());
            case "vote" -> vote(sender, args);
            case "info" -> info(sender);
            case "pool" -> pool(sender, args);
            case "start" -> plugin.game().start(sender);
            case "reset" -> reset(sender, args);
            case "reload" -> {
                plugin.settings().load();
                plugin.motd().reloadIcon();
                plugin.onSettingsChanged();
                sender.sendMessage(Text.prefixed("<green>config.yml und Server-Icon neu geladen."));
            }
            case "unlock" -> ifPlayer(sender, player -> {
                boolean added = plugin.fields().unlock(player.getWorld(),
                        player.getLocation().getBlockX(), player.getLocation().getBlockZ());
                player.sendMessage(Text.prefixed(added ? "<green>Block freigeschaltet." : "<gray>Der Block ist schon frei."));
            });
            default -> help(sender);
        }
    }

    private void settings(CommandSender sender, String[] args) {
        ifPlayer(sender, player -> {
            if (args.length >= 2) {
                for (Category category : Category.values()) {
                    if (category.name().equalsIgnoreCase(args[1]) || category.displayName().equalsIgnoreCase(args[1])) {
                        new CategoryMenu(plugin, player, category).open();
                        return;
                    }
                }
            }
            new MainMenu(plugin, player).open();
        });
    }

    private void config(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage(Text.prefixed("<white><bold>Alle Einstellungen</bold> <gray>(/gl config [key] [wert])"));
            for (Category category : Category.values()) {
                sender.sendMessage(Text.mm("<" + category.color() + "><bold>" + category.displayName()));
                for (Setting setting : Settings.byCategory(category)) {
                    sender.sendMessage(settingLine(setting));
                }
            }
            return;
        }
        Setting setting = Settings.all().get(args[1].toLowerCase(Locale.ROOT));
        if (setting == null) {
            sender.sendMessage(Text.prefixed("<red>Unbekannte Einstellung: <white>" + Text.escape(args[1])));
            return;
        }
        if (args.length == 2) {
            sender.sendMessage(Text.prefixed("<white><bold>" + setting.name() + "</bold> <dark_gray>(" + setting.key() + ")"));
            sender.sendMessage(Text.mm("<gray>" + Text.escape(setting.description())));
            sender.sendMessage(Text.mm("<gray>Aktuell: <aqua>" + Text.escape(setting.formatValue(plugin.settings().get(setting)))
                    + " <dark_gray>| Standard: " + Text.escape(setting.formatValue(setting.defaultValue()))
                    + " | Werte: " + Text.escape(setting.inputHint())));
            return;
        }
        if (!isAdmin(sender)) {
            sender.sendMessage(Text.prefixed("<red>Nur Admins können Einstellungen ändern."));
            return;
        }
        Object value = setting.parse(args[2]);
        if (value == null) {
            sender.sendMessage(Text.prefixed("<red>Ungültiger Wert. Erlaubt: <white>" + Text.escape(setting.inputHint())));
            return;
        }
        plugin.settings().set(setting, value);
        plugin.onSettingsChanged();
        sender.sendMessage(Text.prefixed("<gray>" + setting.name() + " → <aqua><bold>" + Text.escape(setting.formatValue(value))));
    }

    private net.kyori.adventure.text.Component settingLine(Setting setting) {
        String value = Text.escape(setting.formatValue(plugin.settings().get(setting)));
        String command = "/gl config " + setting.key() + " ";
        return Text.mm("<dark_gray> • <click:suggest_command:'" + command + "'><hover:show_text:'<gray>"
                + Text.escape(setting.description()).replace("'", "’") + "<newline><yellow>Klick zum Ändern'><gray>" + setting.name()
                + "</hover></click><dark_gray>: <aqua>" + value);
    }

    private void vote(CommandSender sender, String[] args) {
        ifPlayer(sender, player -> {
            if (args.length < 2) {
                new SpawnMenu(plugin, player).open();
                return;
            }
            SpawnPreset preset = SpawnPreset.parse(args[1]);
            if (preset == null) {
                player.sendMessage(Text.prefixed("<red>Unbekannter Spawn. Möglich: <white>"
                        + String.join(", ", Arrays.stream(SpawnPreset.values()).map(p -> p.name().toLowerCase(Locale.ROOT)).toList())));
                return;
            }
            plugin.game().vote(player, preset);
        });
    }

    private void info(CommandSender sender) {
        var data = plugin.data();
        sender.sendMessage(Text.prefixed("<white><bold>Status"));
        sender.sendMessage(Text.mm("<gray>Zeit: <gold>" + Text.time(data.timerSeconds)
                + (data.timerPaused ? " <red>(pausiert)" : "")));
        for (World world : Bukkit.getWorlds()) {
            if (plugin.fields().isChallengeWorld(world)) {
                sender.sendMessage(Text.mm("<gray>" + ExpansionManager.dimensionName(world) + ": <white>"
                        + plugin.fields().size(world) + " Blöcke <dark_gray>| <gray>nächster Block: <green>"
                        + plugin.fields().nextCost(world) + " Level"));
            }
        }
        if (plugin.levels().mode() == PaymentMode.POOL) {
            sender.sendMessage(Text.mm("<gray>Team-Pool: <green>" + data.poolLevels + " Level"));
        }
    }

    private void pool(CommandSender sender, String[] args) {
        ifPlayer(sender, player -> {
            if (plugin.levels().mode() != PaymentMode.POOL) {
                player.sendMessage(Text.prefixed("<gray>Der Team-Pool ist aus (Bezahlmodus: Spieler zahlt)."));
                return;
            }
            if (args.length < 3 || !args[1].equalsIgnoreCase("einzahlen")) {
                player.sendMessage(Text.prefixed("<gray>Team-Pool: <green>" + plugin.data().poolLevels
                        + " Level</green>. Einzahlen: <white>/gl pool einzahlen [level]"));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(Text.prefixed("<red>Das ist keine Zahl."));
                return;
            }
            if (plugin.levels().deposit(player, amount)) {
                Bukkit.broadcast(Text.prefixed("<white>" + Text.escape(player.getName()) + "</white> <gray>hat <green>"
                        + amount + " Level</green> in den Team-Pool eingezahlt."));
            } else {
                player.sendMessage(Text.prefixed("<red>Du hast nicht genug Level."));
            }
        });
    }

    private void reset(CommandSender sender, String[] args) {
        if (plugin.data().state == GameState.LOBBY) {
            sender.sendMessage(Text.prefixed("<gray>Es läuft keine Runde – ihr seid schon in der Lobby."));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            plugin.game().resetRound(sender);
            return;
        }
        sender.sendMessage(Text.prefixed("<red>Achtung:</red> <gray>Das löscht Oberwelt, Nether und End und startet den Server neu. "
                + "<click:run_command:'/gl reset confirm'><hover:show_text:'<red>Wirklich zurücksetzen'>"
                + "<red><bold>[Bestätigen]</bold></red></hover></click>"));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.prefixed("<white><bold>Befehle"));
        sender.sendMessage(Text.mm("<gray>/gl <dark_gray>– Menü mit allen Einstellungen"));
        sender.sendMessage(Text.mm("<gray>/gl settings [kategorie] <dark_gray>– Einstellungs-GUI"));
        sender.sendMessage(Text.mm("<gray>/gl config [key] [wert] <dark_gray>– Einstellung lesen/ändern"));
        sender.sendMessage(Text.mm("<gray>/gl spawn | vote [spawn] <dark_gray>– Spawn-Abstimmung"));
        sender.sendMessage(Text.mm("<gray>/gl info <dark_gray>– Feld & Timer"));
        sender.sendMessage(Text.mm("<gray>/gl pool einzahlen [n] <dark_gray>– Level in den Team-Pool"));
        sender.sendMessage(Text.mm("<gray>/timer pause | resume | reset"));
        if (isAdmin(sender)) {
            sender.sendMessage(Text.mm("<gray>/gl start | reset | reload | unlock <dark_gray>– Admin"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            return filter(SUBCOMMANDS.stream().filter(s -> isAdmin(sender) || !ADMIN_SUBCOMMANDS.contains(s)), prefix);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String current = args[args.length - 1];
        if (args.length == 2) {
            return switch (sub) {
                case "config" -> filter(Settings.all().keySet().stream(), current);
                case "settings", "einstellungen" -> filter(Arrays.stream(Category.values())
                        .map(c -> c.name().toLowerCase(Locale.ROOT)), current);
                case "vote" -> filter(Arrays.stream(SpawnPreset.values()).map(p -> p.name().toLowerCase(Locale.ROOT)), current);
                case "pool" -> filter(Stream.of("einzahlen"), current);
                case "reset" -> filter(Stream.of("confirm"), current);
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("config")) {
            Setting setting = Settings.all().get(args[1].toLowerCase(Locale.ROOT));
            if (setting == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            switch (setting.type()) {
                case BOOL -> values.addAll(List.of("an", "aus"));
                case INT -> values.addAll(List.of(String.valueOf(setting.min()), String.valueOf(setting.defaultValue()),
                        String.valueOf(setting.max())));
                case CHOICE -> setting.choices().forEach(c -> values.add(c.toLowerCase(Locale.ROOT)));
            }
            return filter(values.stream(), current);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().toList();
    }

    private static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission(GridLockPlugin.ADMIN_PERMISSION);
    }

    private static void ifPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            sender.sendMessage(Text.prefixed("<red>Das geht nur ingame."));
        }
    }
}
