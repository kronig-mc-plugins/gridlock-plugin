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
import net.kronig.gridlock.gui.TransferMenu;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
            "menu", "settings", "config", "spawn", "vote", "ready", "info", "pay", "scoreboard", "sell", "stats",
            "spectate", "help", "forcestart", "reset", "reload", "level", "playtime", "unlock", "lock", "cleanup", "bonus");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
            "forcestart", "reset", "reload", "level", "playtime", "unlock", "lock", "cleanup", "bonus");
    private static final int MAX_FIELD_RADIUS = 25;

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
            case "pay", "überweisen", "ueberweisen" -> pay(sender, args);
            case "scoreboard", "sb" -> ifPlayer(sender, this::toggleScoreboard);
            case "sell", "verkaufen" -> ifPlayer(sender, player -> plugin.buyback().sell(player));
            case "stats", "statistik" -> stats(sender);
            case "spectate", "zuschauen" -> ifPlayer(sender, player -> spectate(player, args));
            case "bonus" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("stop")) {
                    if (plugin.bonus().active() == null) {
                        sender.sendMessage(Text.prefixed("<gray>Es läuft gerade kein Bonus."));
                    } else {
                        plugin.bonus().end();
                    }
                } else {
                    plugin.bonus().force(sender, args.length >= 2 ? net.kronig.gridlock.bonus.BonusType.parse(args[1]) : null);
                }
            }
            case "level" -> level(sender, args);
            case "playtime", "spielzeit" -> playtime(sender, args);
            case "ready", "bereit", "start" -> {
                if (sender instanceof Player player) {
                    plugin.game().toggleReady(player);
                } else {
                    plugin.game().forceStart(sender); // console cannot be "ready"
                }
            }
            case "forcestart" -> plugin.game().forceStart(sender);
            case "cleanup" -> {
                int removed = plugin.shulkerWall().cleanup();
                Bukkit.getWorlds().forEach(World::save);
                sender.sendMessage(Text.prefixed("<gray>" + removed + " alte Border-Pfosten entfernt, Welten gespeichert. "
                        + "Nicht geladene Chunks werden beim Laden automatisch aufgeräumt."));
            }
            case "reset" -> reset(sender, args);
            case "reload" -> {
                plugin.settings().load();
                plugin.motd().reloadIcon();
                plugin.onSettingsChanged();
                sender.sendMessage(Text.prefixed("<green>config.yml und Server-Icon neu geladen."));
            }
            case "unlock" -> ifPlayer(sender, player -> changeField(player, args, true));
            case "lock" -> ifPlayer(sender, player -> changeField(player, args, false));
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

    // ------------------------------------------------------------------ player commands

    private void pay(CommandSender sender, String[] args) {
        ifPlayer(sender, player -> {
            if (plugin.levels().mode() != PaymentMode.TRANSFER) {
                player.sendMessage(Text.prefixed("<gray>Überweisen ist nur im Bezahlmodus <white>"
                        + PaymentMode.TRANSFER.displayName() + "</white> möglich."));
                return;
            }
            if (args.length < 3) {
                new TransferMenu(plugin, player).open();
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || target.equals(player)) {
                player.sendMessage(Text.prefixed("<red>Spieler nicht gefunden (oder du selbst)."));
                return;
            }
            int amount = parseInt(args[2]);
            if (amount <= 0) {
                player.sendMessage(Text.prefixed("<red>Gib eine positive Zahl an."));
                return;
            }
            if (!plugin.levels().transfer(player, target, amount)) {
                player.sendMessage(Text.prefixed("<red>Du hast nur " + player.getLevel() + " Level."));
            }
        });
    }

    private void stats(CommandSender sender) {
        if (sender instanceof Player player) {
            new net.kronig.gridlock.gui.StatsMenu(plugin, player).open();
            return;
        }
        sender.sendMessage(Text.prefixed("<gold><bold>Diese Runde"));
        for (String line : plugin.stats().summaryLines()) {
            sender.sendMessage(Text.mm(line));
        }
    }

    private void spectate(Player player, String[] args) {
        if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.sendMessage(Text.prefixed("<red>Zuschauen geht nur im Zuschauermodus."));
            return;
        }
        if (args.length < 2) {
            new net.kronig.gridlock.gui.SpectateMenu(plugin, player).open();
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(player) || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.sendMessage(Text.prefixed("<red>Spieler nicht gefunden oder selbst Zuschauer."));
            return;
        }
        player.teleport(target.getLocation());
        player.setSpectatorTarget(target);
        player.sendMessage(Text.prefixed("<gray>Du siehst jetzt <white>" + Text.escape(target.getName()) + "</white> zu."));
    }

    private void toggleScoreboard(Player player) {
        String id = player.getUniqueId().toString();
        boolean hidden = !plugin.data().hiddenSidebar.remove(id);
        if (hidden) {
            plugin.data().hiddenSidebar.add(id);
        }
        plugin.sidebar().update();
        player.sendMessage(Text.prefixed("<gray>Dein Scoreboard ist jetzt " + (hidden ? "<red>aus" : "<green>an") + "<gray>."));
    }

    // ------------------------------------------------------------------ admin commands

    /** /gl level [spieler|pool|alle] [set|add|remove] [n] */
    private void level(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Text.prefixed("<gray>/gl level [spieler|pool|alle] [set|add|remove] [anzahl]"));
            return;
        }
        String operation = args[2].toLowerCase(Locale.ROOT);
        int amount = parseInt(args[3]);
        if (amount < 0 || !List.of("set", "add", "remove").contains(operation)) {
            sender.sendMessage(Text.prefixed("<red>Aktion: set, add oder remove – und eine Zahl ab 0."));
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        boolean pool = plugin.levels().mode() == PaymentMode.POOL;
        if (target.equals("pool") || pool) {
            int value = apply(plugin.levels().pool(), operation, amount);
            plugin.levels().setPool(value);
            sender.sendMessage(Text.prefixed("<gray>Team-Pool → <green>" + value + " Level"
                    + (pool && !target.equals("pool") ? " <dark_gray>(Team-Pool-Modus: gilt für alle)" : "")));
            return;
        }
        List<Player> targets = new ArrayList<>();
        if (target.equals("alle") || target.equals("all") || target.equals("@a")) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) {
                sender.sendMessage(Text.prefixed("<red>Spieler nicht online: <white>" + Text.escape(args[1])));
                return;
            }
            targets.add(player);
        }
        for (Player player : targets) {
            player.setLevel(apply(player.getLevel(), operation, amount));
        }
        sender.sendMessage(Text.prefixed("<gray>Level angepasst für <white>" + targets.size() + " Spieler<gray> ("
                + operation + " " + amount + ")."));
    }

    /** /gl playtime [spieler] [set|add|remove] [zeit] */
    private void playtime(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Text.prefixed("<gray>/gl playtime [spieler] [set|add|remove] [zeit, z. B. 1:30:00 oder 90m]"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            sender.sendMessage(Text.prefixed("<red>Spieler unbekannt: <white>" + Text.escape(args[1])));
            return;
        }
        String operation = args[2].toLowerCase(Locale.ROOT);
        long seconds = Text.parseDuration(args[3]);
        if (seconds < 0 || !List.of("set", "add", "remove").contains(operation)) {
            sender.sendMessage(Text.prefixed("<red>Aktion: set, add oder remove – Zeit z. B. 1:30:00, 45m oder 3600."));
            return;
        }
        String id = target.getUniqueId().toString();
        long current = plugin.data().playtime.getOrDefault(id, 0L);
        long value = switch (operation) {
            case "add" -> current + seconds;
            case "remove" -> Math.max(0, current - seconds);
            default -> seconds;
        };
        plugin.data().playtime.put(id, value);
        sender.sendMessage(Text.prefixed("<gray>Spielzeit von <white>" + Text.escape(String.valueOf(target.getName()))
                + "</white> → <gold>" + Text.time(value)));
    }

    /** /gl unlock|lock [radius] – square around the admin's position. */
    private void changeField(Player player, String[] args, boolean unlock) {
        int radius = args.length >= 2 ? parseInt(args[1]) : 0;
        if (radius < 0 || radius > MAX_FIELD_RADIUS) {
            player.sendMessage(Text.prefixed("<red>Radius 0 bis " + MAX_FIELD_RADIUS + "."));
            return;
        }
        int bx = player.getLocation().getBlockX();
        int bz = player.getLocation().getBlockZ();
        int changed = 0;
        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int z = bz - radius; z <= bz + radius; z++) {
                boolean done = unlock ? plugin.fields().unlock(player.getWorld(), x, z)
                        : plugin.fields().lock(player.getWorld(), x, z);
                if (done) {
                    changed++;
                }
            }
        }
        player.sendMessage(Text.prefixed("<gray>" + changed + " Blöcke " + (unlock ? "<green>freigeschaltet" : "<red>gesperrt")
                + "<gray>. Feld jetzt: <white>" + plugin.fields().size(player.getWorld()) + " Blöcke"));
    }

    private static int apply(int current, String operation, int amount) {
        return switch (operation) {
            case "add" -> current + amount;
            case "remove" -> Math.max(0, current - amount);
            default -> amount;
        };
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
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
        sender.sendMessage(Text.mm("<gray>/gl ready <dark_gray>– bereit / nicht bereit (Start, wenn alle bereit sind)"));
        sender.sendMessage(Text.mm("<gray>/gl info <dark_gray>– Feld & Timer"));
        sender.sendMessage(Text.mm("<gray>/gl pay [spieler] [level] <dark_gray>– Level überweisen (Modus Überweisen)"));
        sender.sendMessage(Text.mm("<gray>/gl scoreboard <dark_gray>– eigenes Scoreboard an/aus"));
        sender.sendMessage(Text.mm("<gray>/gl sell <dark_gray>– Block unter dir verkaufen"));
        sender.sendMessage(Text.mm("<gray>/gl stats <dark_gray>– Statistiken und Bestenliste"));
        sender.sendMessage(Text.mm("<gray>/gl spectate [spieler] <dark_gray>– als Zuschauer hinspringen"));
        sender.sendMessage(Text.mm("<gray>/sethome · /home <dark_gray>– eigener Home-Punkt im Feld"));
        sender.sendMessage(Text.mm("<gray>/timer <dark_gray>– Zeit anzeigen"));
        if (isAdmin(sender)) {
            sender.sendMessage(Text.mm("<gold>Admin:"));
            sender.sendMessage(Text.mm("<gray>/gl forcestart | reset | reload"));
            sender.sendMessage(Text.mm("<gray>/gl level [spieler|pool|alle] [set|add|remove] [n]"));
            sender.sendMessage(Text.mm("<gray>/gl playtime [spieler] [set|add|remove] [zeit]"));
            sender.sendMessage(Text.mm("<gray>/gl unlock | lock [radius] <dark_gray>– Feld unter dir"));
            sender.sendMessage(Text.mm("<gray>/gl cleanup <dark_gray>– übrig gebliebene Border-Pfosten entfernen"));
            sender.sendMessage(Text.mm("<gray>/gl bonus [typ|stop] <dark_gray>– Bonus jetzt starten"));
            sender.sendMessage(Text.mm("<gray>/timer pause | resume | reset | set | add | remove [zeit]"));
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
                case "pay", "level" -> filter(Stream.concat(
                        sub.equals("level") ? Stream.of("pool", "alle") : Stream.empty(),
                        Bukkit.getOnlinePlayers().stream().map(Player::getName)), current);
                case "playtime", "spielzeit", "spectate", "zuschauen" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), current);
                case "bonus" -> filter(Stream.concat(Stream.of("stop"), Arrays.stream(net.kronig.gridlock.bonus.BonusType.values())
                        .map(b -> b.name().toLowerCase(Locale.ROOT))), current);
                case "unlock", "lock" -> filter(Stream.of("0", "1", "2", "5"), current);
                case "reset" -> filter(Stream.of("confirm"), current);
                default -> List.of();
            };
        }
        if (args.length == 3 && (sub.equals("level") || sub.equals("playtime") || sub.equals("spielzeit"))) {
            return filter(Stream.of("set", "add", "remove"), current);
        }
        if (args.length == 3 && sub.equals("pay")) {
            return filter(Stream.of("1", "5", "10"), current);
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
