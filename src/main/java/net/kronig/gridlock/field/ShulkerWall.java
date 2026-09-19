package net.kronig.gridlock.field;

import net.kronig.gridlock.GridLockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Cleanup only. Versions 1.5.0/1.5.1 (and a mislabelled 1.4.0 build) placed invisible shulker "posts" on the
 * border. Shulkers snap to block centres, so they ended up inside the field and blocked movement and clicks.
 * The feature is gone; this class removes every post that is still saved in a world.
 */
public final class ShulkerWall implements Listener {

    private final NamespacedKey tag;

    public ShulkerWall(GridLockPlugin plugin) {
        this.tag = new NamespacedKey(plugin, "border_post");
    }

    private boolean isPost(Entity entity) {
        return entity.getType() == EntityType.SHULKER && entity.getPersistentDataContainer().has(tag);
    }

    /** Removes all posts in loaded chunks. */
    public int cleanup() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isPost(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Posts in chunks that were not loaded during the cleanup are removed as soon as the chunk loads. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isPost(entity)) {
                entity.remove();
            }
        }
    }
}
