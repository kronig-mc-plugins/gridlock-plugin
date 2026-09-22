package net.kronig.gridlock.home;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.field.FieldManager;
import net.kronig.gridlock.game.GameData;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** /sethome and /home per player, only inside the field. */
public final class HomeManager {

    private final GridLockPlugin plugin;
    private final FieldManager fields;
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();

    public HomeManager(GridLockPlugin plugin, FieldManager fields) {
        this.plugin = plugin;
        this.fields = fields;
    }

    public BasicCommand setHomeCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (source.getSender() instanceof Player player) {
                    setHome(player);
                } else {
                    source.getSender().sendMessage(Text.prefixed("<red>Das geht nur ingame."));
                }
            }
        };
    }

    public BasicCommand homeCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (source.getSender() instanceof Player player) {
                    home(player);
                } else {
                    source.getSender().sendMessage(Text.prefixed("<red>Das geht nur ingame."));
                }
            }
        };
    }

    public void setHome(Player player) {
        if (!plugin.settings().bool(Settings.HOME_ENABLED)) {
            player.sendMessage(Text.prefixed("<red>Homes sind ausgeschaltet."));
            return;
        }
        if (!fields.isRestricted(player) || !fields.isAllowed(player.getLocation())) {
            player.sendMessage(Text.prefixed("<red>Dein Home muss im Feld liegen, während eine Runde läuft."));
            return;
        }
        Location at = player.getLocation();
        GameData.SavedLocation saved = new GameData.SavedLocation();
        saved.world = at.getWorld().getName();
        saved.x = at.getX();
        saved.y = at.getY();
        saved.z = at.getZ();
        saved.yaw = at.getYaw();
        saved.pitch = at.getPitch();
        plugin.data().homes.put(player.getUniqueId().toString(), saved);
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0);
        player.sendMessage(Text.prefixed("<green>Home gesetzt</green> <gray>bei <white>" + at.getBlockX() + ", "
                + at.getBlockY() + ", " + at.getBlockZ() + "</white>. Zurück mit <white>/home</white>."));
    }

    public void home(Player player) {
        if (!plugin.settings().bool(Settings.HOME_ENABLED)) {
            player.sendMessage(Text.prefixed("<red>Homes sind ausgeschaltet."));
            return;
        }
        String id = player.getUniqueId().toString();
        GameData.SavedLocation saved = plugin.data().homes.get(id);
        if (saved == null) {
            player.sendMessage(Text.prefixed("<red>Du hast noch kein Home. Setz eins mit <white>/sethome</white>."));
            return;
        }
        if (!fields.isRestricted(player)) {
            player.sendMessage(Text.prefixed("<red>/home geht nur während einer Runde."));
            return;
        }
        long cooldown = plugin.settings().integer(Settings.HOME_COOLDOWN) * 1000L;
        long last = plugin.data().lastHomeUse.getOrDefault(id, 0L);
        long wait = last + cooldown - System.currentTimeMillis();
        if (wait > 0) {
            player.sendMessage(Text.prefixed("<red>/home ist noch <white>" + (wait / 1000 + 1) + " s</white> gesperrt."));
            return;
        }
        if (warmups.containsKey(player.getUniqueId())) {
            player.sendMessage(Text.prefixed("<gray>Teleport läuft schon – bleib stehen."));
            return;
        }
        int warmup = plugin.settings().integer(Settings.HOME_WARMUP);
        if (warmup <= 0) {
            teleport(player, saved);
            return;
        }
        Location start = player.getLocation();
        player.sendMessage(Text.prefixed("<gray>Teleport in <white>" + warmup + " s</white> – nicht bewegen."));
        BukkitTask task = new BukkitRunnable() {
            int left = warmup;

            @Override
            public void run() {
                if (!player.isOnline() || player.getLocation().distanceSquared(start) > 1.0) {
                    cancel();
                    warmups.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(Text.prefixed("<red>Teleport abgebrochen – du hast dich bewegt."));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                    }
                    return;
                }
                if (left > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                    player.sendActionBar(Text.mm("<gray>Home in <white>" + left + "</white>…"));
                    left--;
                    return;
                }
                cancel();
                warmups.remove(player.getUniqueId());
                teleport(player, saved);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        warmups.put(player.getUniqueId(), task);
    }

    private void teleport(Player player, GameData.SavedLocation saved) {
        World world = Bukkit.getWorld(saved.world);
        if (world == null) {
            player.sendMessage(Text.prefixed("<red>Die Welt deines Homes gibt es nicht mehr."));
            return;
        }
        Location target = new Location(world, saved.x, saved.y, saved.z, saved.yaw, saved.pitch);
        if (!fields.isAllowed(target)) {
            // The column was sold in the meantime: land on the closest field block instead.
            long[] nearest = fields.nearestAllowed(world, target.getBlockX(), target.getBlockZ(), 8);
            if (nearest == null) {
                player.sendMessage(Text.prefixed("<red>Dein Home liegt nicht mehr im Feld."));
                return;
            }
            target = FieldManager.safeSpot(world, (int) nearest[0], (int) nearest[1], target.getY());
        }
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.5);
        player.teleport(target);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        plugin.data().lastHomeUse.put(player.getUniqueId().toString(), System.currentTimeMillis());
        player.sendMessage(Text.prefixed("<gray>Willkommen zu Hause."));
    }

    public void forget(Player player) {
        BukkitTask task = warmups.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}
