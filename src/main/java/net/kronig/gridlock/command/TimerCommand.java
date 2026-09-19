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
            default -> sender.sendMessage(Text.prefixed("<gray>/timer pause | resume | reset"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1 || !source.getSender().hasPermission(GridLockPlugin.ADMIN_PERMISSION)) {
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return List.of("pause", "resume", "reset").stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
