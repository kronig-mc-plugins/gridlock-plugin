package net.kronig.gridlock.game;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.spawn.SpawnPreset;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Everything alive in the lobby: the spinning logo cube, holograms, clickable vote pillars, the ready pillar
 * and ambient particles. All entities are non-persistent and recreated on every start.
 */
public final class LobbyShowcase implements Listener {

    private static final double STATION_RADIUS = 11;
    private static final int STATION_COUNT = SpawnPreset.values().length + 1; // + ready pillar
    private static final float CUBE_SCALE = 1.6f;
    private static final float CAGE_SCALE = 2.25f;
    private static final float TILT_X = 0.6155f; // atan(1/sqrt(2)): corner points up like the logo
    private static final float TILT_Z = (float) (Math.PI / 4);

    private record Station(SpawnPreset preset, ItemDisplay icon, TextDisplay label) {
    }

    private final GridLockPlugin plugin;
    private final NamespacedKey tag;
    private final List<Entity> spawned = new ArrayList<>();
    private final List<Station> stations = new ArrayList<>();
    private final Map<UUID, SpawnPreset> voteTargets = new HashMap<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();
    private final Random random = new Random();
    private World world;
    private BlockDisplay cube;
    private BlockDisplay cage;
    private TextDisplay statusBoard;
    private TextDisplay readyLabel;
    private ItemDisplay readyIcon;
    private UUID readyInteraction;
    private float angle;

    public LobbyShowcase(GridLockPlugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "lobby_showcase");
    }

    /** Block position of station {@code index}; index 0 (ready pillar) sits straight ahead of the spawn. */
    static int[] stationPosition(int index) {
        double angle = 2 * Math.PI * index / STATION_COUNT;
        return new int[]{(int) Math.round(Math.sin(angle) * STATION_RADIUS),
                (int) Math.round(Math.cos(angle) * STATION_RADIUS)};
    }

    static int stationCount() {
        return STATION_COUNT;
    }

    public void spawn(World lobbyWorld) {
        this.world = lobbyWorld;
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.addPluginChunkTicket(cx, cz, plugin);
            }
        }
        // Leftovers from a crash are tagged – remove them before spawning fresh ones.
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(tag)) {
                entity.remove();
            }
        }
        double top = LobbyBuilder.TOP;

        cube = spawnCube(new Location(world, 0.5, top + 3.9, 0.5), Material.GRASS_BLOCK, CUBE_SCALE);
        cage = spawnCube(new Location(world, 0.5, top + 3.9, 0.5), Material.RED_STAINED_GLASS, CAGE_SCALE);

        text(new Location(world, 0.5, top + 7.0, 0.5),
                "<gradient:#ff3b3b:#ff9e3b><bold>GRIDLOCK</bold></gradient>", 3.2f, false);
        text(new Location(world, 0.5, top + 6.35, 0.5),
                "<gray>1x1-Challenge <dark_gray>·</dark_gray> <white>Jeder Block zählt", 1.1f, false);

        text(new Location(world, -7.5, top + 3.4, -4.5), String.join("<newline>",
                "<gold><bold>So geht's</bold>",
                "<gray>① Stimm an einer Säule für euren Spawn",
                "<gray>② Alle klicken die <green>grüne Säule</green> = bereit",
                "<gray>③ Ihr startet auf einem <white>1x1-Feld</white>",
                "<gray>④ <white>Schleichen</white> + gegen die <red>Border</red> drücken",
                "<gray>    kostet Level und schaltet 1 Block frei",
                "<gray>⑤ Ziel: <light_purple>Enderdrache</light_purple>"), 0.9f, true);
        statusBoard = text(new Location(world, 8.5, top + 3.4, -4.5), "", 0.9f, true);

        SpawnPreset[] presets = SpawnPreset.values();
        for (int i = 0; i < presets.length; i++) {
            int[] pos = stationPosition(i + 1);
            Location base = new Location(world, pos[0] + 0.5, top + 1, pos[1] + 0.5);
            ItemDisplay icon = icon(base.clone().add(0, 2.35, 0), new ItemStack(presets[i].icon()));
            TextDisplay label = text(base.clone().add(0, 3.05, 0), "", 0.8f, false);
            UUID interaction = interaction(base);
            voteTargets.put(interaction, presets[i]);
            stations.add(new Station(presets[i], icon, label));
        }
        int[] readyPos = stationPosition(0);
        Location readyBase = new Location(world, readyPos[0] + 0.5, top + 1, readyPos[1] + 0.5);
        readyIcon = icon(readyBase.clone().add(0, 2.35, 0), new ItemStack(Material.LIME_DYE));
        readyLabel = text(readyBase.clone().add(0, 3.05, 0), "", 1.0f, false);
        readyInteraction = interaction(readyBase);
        // Texts are filled by the repeating task once the plugin is fully enabled.
    }

    public void despawn() {
        for (Entity entity : spawned) {
            entity.remove();
        }
        spawned.clear();
        stations.clear();
        voteTargets.clear();
    }

    // ------------------------------------------------------------------ entity helpers

    private <T extends Entity> T mark(T entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        spawned.add(entity);
        return entity;
    }

    private BlockDisplay spawnCube(Location location, Material material, float scale) {
        return mark(world.spawn(location, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(cubeTransformation(0, scale));
            display.setInterpolationDuration(2);
            display.setTeleportDuration(0);
        }));
    }

    private TextDisplay text(Location location, String miniMessage, float scale, boolean board) {
        return mark(world.spawn(location, TextDisplay.class, display -> {
            display.text(Text.mm(miniMessage));
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(board ? TextDisplay.TextAlignment.LEFT : TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setLineWidth(260);
            display.setBackgroundColor(board ? Color.fromARGB(150, 10, 10, 14) : Color.fromARGB(0, 0, 0, 0));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        }));
    }

    private ItemDisplay icon(Location location, ItemStack item) {
        return mark(world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration(2);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
        }));
    }

    private UUID interaction(Location base) {
        Interaction interaction = mark(world.spawn(base, Interaction.class, entity -> {
            entity.setInteractionWidth(1.4f);
            entity.setInteractionHeight(3.6f);
            entity.setResponsive(true);
        }));
        return interaction.getUniqueId();
    }

    /** Rotation around the cube's own centre: translation = -(rotation * half size). */
    private static Transformation cubeTransformation(float angle, float scale) {
        Quaternionf rotation = new Quaternionf().rotateY(angle).rotateX(TILT_X).rotateZ(TILT_Z);
        Vector3f half = new Vector3f(scale / 2f, scale / 2f, scale / 2f);
        rotation.transform(half);
        return new Transformation(half.negate(), rotation, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    // ------------------------------------------------------------------ animation

    /** Every 2 ticks: spin the logo and the station icons. */
    public void animate() {
        if (cube == null || !cube.isValid()) {
            return;
        }
        angle += 0.045f;
        cube.setInterpolationDelay(0);
        cube.setTransformation(cubeTransformation(angle, CUBE_SCALE));
        cage.setInterpolationDelay(0);
        cage.setTransformation(cubeTransformation(-angle * 0.6f, CAGE_SCALE));

        Quaternionf spin = new Quaternionf().rotateY(angle * 2);
        Transformation iconTransformation = new Transformation(new Vector3f(), spin,
                new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf());
        for (Station station : stations) {
            station.icon().setInterpolationDelay(0);
            station.icon().setTransformation(iconTransformation);
        }
        if (readyIcon != null) {
            readyIcon.setInterpolationDelay(0);
            readyIcon.setTransformation(new Transformation(new Vector3f(), spin,
                    new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
        }
    }

    /** Every few ticks: sparks rising from the red grid lines and a glow around the logo. */
    public void particles() {
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        double top = LobbyBuilder.TOP;
        for (int i = 0; i < 6; i++) {
            int x;
            int z;
            do {
                x = random.nextInt(29) - 14;
                z = random.nextInt(29) - 14;
            } while (Math.floorMod(x, 4) != 0 && Math.floorMod(z, 4) != 0 || x * x + z * z > 14 * 14);
            world.spawnParticle(Particle.END_ROD, x + random.nextDouble(), top + 1.05, z + random.nextDouble(),
                    0, 0, 0.04 + random.nextDouble() * 0.04, 0, 1);
        }
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 50, 40), 1.3f);
        world.spawnParticle(Particle.DUST, 0.5, top + 3.9, 0.5, 4, 1.3, 1.3, 1.3, 0, red);
    }

    /** Every 10 ticks: live vote counts, ready status and the status board. */
    public void updateTexts() {
        if (world == null || plugin.game() == null) {
            return;
        }
        GameData data = plugin.data();
        Map<SpawnPreset, Integer> votes = new HashMap<>();
        for (String name : data.votes.values()) {
            SpawnPreset preset = SpawnPreset.parse(name);
            if (preset != null) {
                votes.merge(preset, 1, Integer::sum);
            }
        }
        SpawnPreset leader = plugin.game().winningPreset();
        for (Station station : stations) {
            int count = votes.getOrDefault(station.preset(), 0);
            boolean leading = station.preset() == leader && !data.votes.isEmpty();
            station.label().text(Text.mm(String.join("<newline>",
                    (leading ? "<gold>★ " : "") + "<white><bold>" + station.preset().displayName(),
                    station.preset().difficulty().format(),
                    count > 0 ? "<green>" + count + (count == 1 ? " Stimme" : " Stimmen") : "<dark_gray>Klick = abstimmen")));
        }

        int online = Bukkit.getOnlinePlayers().size();
        int ready = plugin.game().readyCount();
        boolean lobby = data.state == GameState.LOBBY;
        if (readyLabel != null) {
            readyLabel.text(Text.mm(lobby
                    ? String.join("<newline>", "<green><bold>BEREIT?",
                    "<white>" + ready + "<gray>/<white>" + online + " <gray>bereit",
                    "<dark_gray>Klick = bereit / nicht bereit")
                    : "<yellow><bold>Startet…"));
        }
        if (statusBoard != null) {
            statusBoard.text(Text.mm(String.join("<newline>",
                    "<aqua><bold>Status</bold>",
                    "<gray>Spieler online: <white>" + online,
                    "<gray>Bereit: <green>" + ready + "<gray>/<white>" + online,
                    "<gray>Favorit: <white>" + leader.displayName(),
                    "<gray>  " + leader.difficulty().format(),
                    lobby ? "<gray>Wartet, bis alle bereit sind" : "<yellow>Die Runde startet!")));
        }
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && handle(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (handle(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    private boolean handle(Player player, Entity entity) {
        UUID id = entity.getUniqueId();
        boolean ready = id.equals(readyInteraction);
        SpawnPreset preset = voteTargets.get(id);
        if (!ready && preset == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(player.getUniqueId());
        if (last != null && now - last < 400) {
            return true;
        }
        clickCooldown.put(player.getUniqueId(), now);
        Location at = entity.getLocation().add(0, 2.4, 0);
        if (ready) {
            plugin.game().toggleReady(player);
            world.spawnParticle(Particle.HAPPY_VILLAGER, at, 15, 0.4, 0.4, 0.4, 0);
        } else {
            plugin.game().vote(player, preset);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 20, 0.3, 0.3, 0.3, 0.15);
        }
        updateTexts();
        return true;
    }
}
