package net.kronig.gridlock.util;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Titles, sounds and fireworks for the big moments. */
public final class Effects {

    private Effects() {
    }

    public static void title(Player player, String title, String subtitle, int stayMillis) {
        player.showTitle(Title.title(Text.mm(title), Text.mm(subtitle),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(stayMillis), Duration.ofMillis(600))));
    }

    public static void titleAll(String title, String subtitle, int stayMillis) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            title(player, title, subtitle, stayMillis);
        }
    }

    /** Plays a sound at every player's own position, so distance does not matter. */
    public static void soundAll(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /** Title + sound for everyone: milestones, new dimensions, bonuses. */
    public static void celebrate(String title, String subtitle, Sound sound, float pitch) {
        titleAll(title, subtitle, 2500);
        soundAll(sound, 0.8f, pitch);
    }

    /** A short fireworks show around every online player. */
    public static void fireworks(Plugin plugin, int seconds, Color... colors) {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (ticks >= seconds * 20) {
                    cancel();
                    return;
                }
                if (ticks % 8 == 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        launch(player.getLocation(), colors);
                    }
                }
                ticks += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private static void launch(Location around, Color[] colors) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = around.clone().add(random.nextDouble(-4, 4), 1, random.nextDouble(-4, 4));
        at.getWorld().spawn(at, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect.Type[] types = FireworkEffect.Type.values();
            meta.addEffect(FireworkEffect.builder()
                    .with(types[random.nextInt(types.length)])
                    .withColor(colors[random.nextInt(colors.length)])
                    .withFade(colors[random.nextInt(colors.length)])
                    .flicker(random.nextBoolean())
                    .trail(random.nextBoolean())
                    .build());
            meta.setPower(random.nextInt(1, 3));
            firework.setFireworkMeta(meta);
        });
    }
}
