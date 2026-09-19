package net.kronig.gridlock.mod;

import net.kronig.gridlock.GridLockPlugin;
import net.kronig.gridlock.config.BorderColor;
import net.kronig.gridlock.config.Settings;
import net.kronig.gridlock.field.ExpansionManager;
import net.kronig.gridlock.field.FieldManager;
import net.kronig.gridlock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Link to the optional GridLock client mod. Players with the mod get the field sent to their client, which then
 * collides with the border itself (hard stop, nothing in the way) and draws it natively. Everyone else keeps the
 * server-side behaviour; both kinds of players can play side by side.
 *
 * <p>Protocol (all big-endian): client sends {@code gridlock:hello} with an int protocol version. Server sends
 * {@code gridlock:field} messages, first byte = type:
 * <pre>
 * 0 RESET    boolean active, int endFreeRadius, int rgb, int glowHeight(1/10), int glowStrength(%), int lineWidth(1/100),
 *            int count, count x (int x, int z)
 * 1 ADD      int count, count x (int x, int z)
 * 2 REMOVE   int count, count x (int x, int z)
 * 3 PROGRESS float progress 0..1 (someone is buying a block: the border shifts towards green)
 * 4 FLASH    (a block was bought: flash green)
 * </pre>
 */
public final class ModLink implements PluginMessageListener {

    public static final String HELLO_CHANNEL = "gridlock:hello";
    public static final String FIELD_CHANNEL = "gridlock:field";
    public static final int PROTOCOL = 1;
    /** Sent by the mod instead of a protocol version when it has to switch itself off. */
    public static final int SIGN_OFF = -1;
    /** Columns per message, far below the plugin message size limit. */
    private static final int CHUNK = 4000;

    private final GridLockPlugin plugin;
    private final FieldManager fields;
    private final ExpansionManager expansion;
    /** Mod players -> signature of what was last sent (world, active, look settings). */
    private final Map<UUID, String> signatures = new HashMap<>();
    private final Map<UUID, Float> lastProgress = new HashMap<>();

    public ModLink(GridLockPlugin plugin, FieldManager fields, ExpansionManager expansion) {
        this.plugin = plugin;
        this.fields = fields;
        this.expansion = expansion;
    }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, HELLO_CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, FIELD_CHANNEL);
    }

    /**
     * True once the field has really been delivered to the player's mod. Until then (and if the mod signs off
     * again) the player is treated like everyone else: server-side border and server-side stop.
     */
    public boolean hasMod(Player player) {
        String signature = signatures.get(player.getUniqueId());
        return signature != null && !signature.isEmpty();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO_CHANNEL.equals(channel) || message.length < 4) {
            return;
        }
        int version = ByteBuffer.wrap(message).getInt();
        if (version == SIGN_OFF) {
            signOff(player);
            return;
        }
        if (version != PROTOCOL) {
            player.sendMessage(Text.prefixed("<yellow>Dein GridLock-Mod passt nicht zu diesem Server (Mod-Protokoll "
                    + version + ", Server " + PROTOCOL + "). Du spielst ohne Mod-Funktionen, bitte aktualisieren."));
            return;
        }
        signatures.put(player.getUniqueId(), "");
        sync(player);
    }

    /** The mod ran into a problem: give the player the normal server-side border back. */
    private void signOff(Player player) {
        if (signatures.remove(player.getUniqueId()) == null) {
            return;
        }
        lastProgress.remove(player.getUniqueId());
        for (Entity display : plugin.borderLines().displays()) {
            player.showEntity(plugin, display);
        }
        plugin.getLogger().warning(player.getName() + ": GridLock-Mod hat sich abgemeldet, zurück zur Server-Border.");
        player.sendMessage(Text.prefixed("<yellow>Dein GridLock-Mod hat ein Problem gemeldet. Du siehst wieder die normale Border."));
    }

    /** Hides a freshly spawned server-side border display from everyone who renders the border themselves. */
    public void hideFromModPlayers(Entity display) {
        for (UUID id : signatures.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && hasMod(player)) {
                player.hideEntity(plugin, display);
            }
        }
    }

    /** Every 2 ticks: resend on world/state/look changes, stream the push progress. */
    public void tick() {
        for (UUID id : List.copyOf(signatures.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (!signature(player).equals(signatures.get(id))) {
                sync(player);
            }
            if (!hasMod(player)) {
                continue;
            }
            float progress = 0f;
            for (Player other : player.getWorld().getPlayers()) {
                ExpansionManager.Push push = expansion.activePush(other);
                if (push != null) {
                    progress = Math.max(progress, (float) expansion.progress(push));
                }
            }
            Float last = lastProgress.get(id);
            if (last == null || last != progress) {
                lastProgress.put(id, progress);
                float value = progress;
                send(player, out -> {
                    out.writeByte(3);
                    out.writeFloat(value);
                });
            }
        }
    }

    private String signature(Player player) {
        World world = player.getWorld();
        return world.getUID() + "|" + fields.isRestricted(player) + "|" + freeRadius(world) + "|"
                + plugin.settings().get(Settings.BORDER_COLOR) + "|" + plugin.settings().integer(Settings.BORDER_GLOW_HEIGHT)
                + "|" + plugin.settings().integer(Settings.BORDER_GLOW_STRENGTH) + "|"
                + plugin.settings().integer(Settings.BORDER_LINE_WIDTH);
    }

    private int freeRadius(World world) {
        return world.getEnvironment() == World.Environment.THE_END ? plugin.settings().integer(Settings.END_FREE_RADIUS) : 0;
    }

    /** Sends the complete state of the player's current world. */
    public void sync(Player player) {
        if (!player.getListeningPluginChannels().contains(FIELD_CHANNEL)) {
            // Bukkit silently drops messages on channels the client has not registered yet. Keep the signature
            // empty, so tick() tries again until the registration has arrived.
            signatures.put(player.getUniqueId(), "");
            return;
        }
        if (!hasMod(player)) {
            // First successful delivery: from now on the client draws the border itself.
            plugin.getLogger().info(player.getName() + " nutzt den GridLock-Mod.");
            player.sendMessage(Text.prefixed("<green>GridLock-Mod erkannt</green> <gray>– harter Border-Stopp und Client-Border aktiv."));
            for (Entity display : plugin.borderLines().displays()) {
                player.hideEntity(plugin, display);
            }
        }
        signatures.put(player.getUniqueId(), signature(player));
        World world = player.getWorld();
        boolean active = fields.isRestricted(player);
        long[] columns = active ? fields.columns(world) : new long[0];
        Color color = plugin.settings().choice(Settings.BORDER_COLOR, BorderColor.class).color();
        int first = Math.min(CHUNK, columns.length);
        send(player, out -> {
            out.writeByte(0);
            out.writeBoolean(active);
            out.writeInt(freeRadius(world));
            out.writeInt(color.asRGB());
            out.writeInt(plugin.settings().integer(Settings.BORDER_GLOW_HEIGHT));
            out.writeInt(plugin.settings().integer(Settings.BORDER_GLOW_STRENGTH));
            out.writeInt(plugin.settings().integer(Settings.BORDER_LINE_WIDTH));
            writeColumns(out, columns, 0, first);
        });
        for (int from = first; from < columns.length; from += CHUNK) {
            int start = from;
            int end = Math.min(columns.length, from + CHUNK);
            send(player, out -> {
                out.writeByte(1);
                writeColumns(out, columns, start, end);
            });
        }
    }

    /** A column was unlocked or locked again. */
    public void onFieldChange(World world, int x, int z, boolean unlocked) {
        for (UUID id : signatures.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.getWorld().equals(world)) {
                continue;
            }
            send(player, out -> {
                out.writeByte(unlocked ? 1 : 2);
                out.writeInt(1);
                out.writeInt(x);
                out.writeInt(z);
            });
            if (unlocked) {
                send(player, out -> out.writeByte(4));
            }
        }
    }

    private static void writeColumns(DataOutputStream out, long[] columns, int from, int to) throws IOException {
        out.writeInt(to - from);
        for (int i = from; i < to; i++) {
            out.writeInt(FieldManager.unpackX(columns[i]));
            out.writeInt(FieldManager.unpackZ(columns[i]));
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private void send(Player player, Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        player.sendPluginMessage(plugin, FIELD_CHANNEL, bytes.toByteArray());
    }

    public void forget(Player player) {
        signatures.remove(player.getUniqueId());
        lastProgress.remove(player.getUniqueId());
    }
}
