package net.kronig.gridlock.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.util.Text;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class TimerCommand implements BasicCommand {

    private final GridLockPlugin plugin;

    public TimerCommand(GridLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            sender.sendMessage(Text.prefixed("<gray>Zeit: <gold>" + Text.time(plugin.data().timerSeconds)
                    + (plugin.data().timerPaused ? " <red>(pausiert)" : "")));
            return;
        }
        if (!sender.hasPermission(GridLockPlugin.ADMIN_PERMISSION)) {
            sender.sendMessage(Text.prefixed("<red>Nur Admins können den Timer steuern."));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pause", "stop" -> plugin.timer().setPaused(true, sender);
            case "resume", "start", "weiter" -> plugin.timer().setPaused(false, sender);
            case "reset" -> plugin.timer().reset(sender);
            case "set", "add", "remove" -> {
                long seconds = args.length >= 2 ? Text.parseDuration(args[1]) : -1;
                if (seconds < 0) {
                    sender.sendMessage(Text.prefixed("<red>Zeit z. B. 1:30:00, 45m, 2h oder 3600."));
                    return;
                }
                plugin.timer().adjust(args[0].toLowerCase(Locale.ROOT), seconds, sender);
            }
            default -> sender.sendMessage(Text.prefixed("<gray>/timer pause | resume | reset | set | add | remove [zeit]"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(GridLockPlugin.ADMIN_PERMISSION) || args.length > 2) {
            return List.of();
        }
        if (args.length == 2) {
            return List.of("set", "add", "remove").contains(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("1:00:00", "30m", "10m") : List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return List.of("pause", "resume", "reset", "set", "add", "remove").stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
