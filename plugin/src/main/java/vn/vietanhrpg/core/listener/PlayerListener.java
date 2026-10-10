package vn.vietanhrpg.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.UniqueItemDefinition;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.progression.ProgressionService;

public final class PlayerListener implements Listener {
    private final JavaPlugin plugin;
    private final Database database;
    private final ItemService items;
    private final ContentService content;
    private final ProgressionService progression;

    public PlayerListener(JavaPlugin plugin, Database database, ItemService items, ContentService content, ProgressionService progression) {
        this.plugin = plugin;
        this.database = database;
        this.items = items;
        this.content = content;
        this.progression = progression;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        database.ensurePlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                plugin.getConfig().getLong("starting-balance.coin", 250),
                plugin.getConfig().getLong("starting-balance.token", 0)
        );
        progression.ensure(event.getPlayer());
        progression.applyTitleDisplay(event.getPlayer());

        // Refresh old 0.2.x named weapons in the player's inventory so their
        // custom_name immediately matches Blades of Majestica on first join.
        for (ItemStack stack : event.getPlayer().getInventory().getContents()) {
            String itemId = items.itemId(stack);
            if (itemId == null) {
                continue;
            }
            UniqueItemDefinition definition = content.uniqueItem(itemId);
            if (definition != null) {
                items.refreshUniquePresentation(stack, definition);
            }
        }
    }
}
