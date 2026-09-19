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
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.Random;

/** The empty waiting world where players pick a spawn before the round starts. */
public final class Lobby implements Listener {

    public static final String WORLD_NAME = "gridlock_lobby";
    private static final int PLATFORM_Y = LobbyBuilder.TOP;
    /** Bump to rebuild the lobby on existing servers. */
    private static final int LOBBY_VERSION = 3;

    private final GridLockPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey versionKey;
    private final LobbyShowcase showcase;
    private World world;

    public Lobby(GridLockPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
        this.versionKey = new NamespacedKey(plugin, "lobby_version");
        this.showcase = new LobbyShowcase(plugin);
    }

    public LobbyShowcase showcase() {
        return showcase;
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
        world.setTime(18000); // night: the red grid glows best in the dark
        world.setStorm(false);
        world.setSpawnLocation(spawn());
        Integer built = world.getPersistentDataContainer().get(versionKey, PersistentDataType.INTEGER);
        if (built == null || built < LOBBY_VERSION) {
            plugin.getLogger().info("Baue die Lobby …");
            new LobbyBuilder(world).build();
            buildStations();
            world.getPersistentDataContainer().set(versionKey, PersistentDataType.INTEGER, LOBBY_VERSION);
        }
        showcase.spawn(world);
    }

    /** Pillars for the vote stations (index 0 = green ready pillar). */
    private void buildStations() {
        for (int i = 0; i < LobbyShowcase.stationCount(); i++) {
            int[] pos = LobbyShowcase.stationPosition(i);
            world.getBlockAt(pos[0], PLATFORM_Y + 1, pos[1]).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            world.getBlockAt(pos[0], PLATFORM_Y + 2, pos[1])
                    .setType(i == 0 ? Material.EMERALD_BLOCK : Material.CHISELED_POLISHED_BLACKSTONE, false);
        }
    }

    public World world() {
        return world;
    }

    /** In front of the logo, looking at it and at the green ready pillar behind it. */
    public Location spawn() {
        return new Location(world, 0.5, PLATFORM_Y + 1, -6.5, 0f, -8f);
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
        updateReadyItem(player);
    }

    /** Slot 9: green when ready, grey when not. */
    public void updateReadyItem(Player player) {
        if (!isLobby(player.getWorld())) {
            return;
        }
        boolean ready = plugin.game().isReady(player);
        player.getInventory().setItem(8, ItemBuilder.of(ready ? Material.LIME_DYE : Material.GRAY_DYE).glow(ready)
                .name(ready ? "<green><bold>Bereit ✔</bold> <gray>(Rechtsklick = doch nicht)"
                        : "<yellow><bold>Bereit machen</bold> <gray>(Rechtsklick)")
                .lore("<gray>Die Challenge startet, sobald alle bereit sind.")
                .tag(itemKey, "start").build());
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
            case "start" -> plugin.game().toggleReady(player);
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

    /** The decorative nether portal must not lead anywhere. */
    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        if (isLobby(event.getFrom().getWorld())) {
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
            return new Location(world, 0.5, PLATFORM_Y + 1, -6.5);
        }
    }
}
