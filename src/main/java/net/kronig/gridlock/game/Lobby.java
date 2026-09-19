package net.kronig.gridlock.game;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.gui.MainMenu;
import net.kronig.gridlock.gui.SpawnMenu;
import net.kronig.gridlock.util.ItemBuilder;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.Random;

/** The empty waiting world where players pick a spawn before the round starts. */
public final class Lobby implements Listener {

    public static final String WORLD_NAME = "gridlock_lobby";
    private static final int PLATFORM_Y = 100;

    private final GridLockPlugin plugin;
    private final NamespacedKey itemKey;
    private World world;

    public Lobby(GridLockPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
    }

    public void load() {
        world = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generator(new VoidGenerator())
                .generateStructures(false)
                .createWorld();
        if (world == null) {
            throw new IllegalStateException("Lobby-Welt konnte nicht erstellt werden");
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setSpawnLocation(spawn());
        buildPlatform();
    }

    public World world() {
        return world;
    }

    public Location spawn() {
        return new Location(world, 0.5, PLATFORM_Y + 1, 0.5, 0f, 0f);
    }

    private void buildPlatform() {
        if (world.getBlockAt(0, PLATFORM_Y, 0).getType() != Material.AIR) {
            return;
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > 7.3) {
                    continue;
                }
                Material material;
                if (distance > 6.3) {
                    material = Material.RED_CONCRETE;
                } else if (x == 0 && z == 0) {
                    material = Material.SEA_LANTERN;
                } else if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    material = Material.RED_STAINED_GLASS;
                } else {
                    material = (x + z) % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
                }
                world.getBlockAt(x, PLATFORM_Y, z).setType(material, false);
            }
        }
        // Little red frame posts that hint at the 1x1 border.
        for (int[] corner : new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}}) {
            Block post = world.getBlockAt(corner[0] * 5, PLATFORM_Y + 1, corner[1] * 5);
            post.setType(Material.RED_NETHER_BRICK_WALL, false);
            post.getRelative(0, 1, 0).setType(Material.REDSTONE_LAMP, false);
        }
    }

    public boolean isLobby(World other) {
        return world != null && world.equals(other);
    }

    public void send(Player player) {
        player.teleport(spawn());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setExp(0f);
        player.setLevel(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        giveItems(player);
    }

    public void giveItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, ItemBuilder.of(Material.COMPASS).glow(true)
                .name("<aqua><bold>Spawn wählen</bold> <gray>(Rechtsklick)")
                .tag(itemKey, "spawn").build());
        player.getInventory().setItem(4, ItemBuilder.of(Material.COMPARATOR)
                .name("<gold><bold>Menü & Einstellungen</bold> <gray>(Rechtsklick)")
                .tag(itemKey, "menu").build());
        if (player.hasPermission(GridLockPlugin.ADMIN_PERMISSION)) {
            player.getInventory().setItem(8, ItemBuilder.of(Material.LIME_DYE).glow(true)
                    .name("<green><bold>Challenge starten</bold> <gray>(Rechtsklick)")
                    .tag(itemKey, "start").build());
        }
    }

    private boolean isProtected(Player player) {
        return isLobby(player.getWorld()) && player.getGameMode() != GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !event.getAction().isRightClick()) {
            return;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (tag) {
            case "spawn" -> new SpawnMenu(plugin, player).open();
            case "menu" -> new MainMenu(plugin, player).open();
            case "start" -> plugin.game().start(player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isLobby(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isLobby(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isProtected(player)
                && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < PLATFORM_Y - 30 && isLobby(event.getTo().getWorld())) {
            event.getPlayer().teleport(spawn());
        }
    }

    /** Generates nothing at all. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, PLATFORM_Y + 1, 0.5);
        }
    }
}
