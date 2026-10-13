package vn.vietanhrpg.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
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

        // EliteMobs owns item names, rarity and lore. VanillaRPG item
        // presentation is opt-in so it cannot overwrite EliteMobs gear.
        if (!itemPresentationEnabled()) {
            return;
        }

        // Refresh old 0.2.x named weapons in the player's inventory so their
        // custom_name immediately matches Blades of Majestica on first join.
        for (ItemStack stack : event.getPlayer().getInventory().getContents()) {
            String itemId = items.itemId(stack);
            if (itemId != null) {
                UniqueItemDefinition definition = content.uniqueItem(itemId);
                if (definition != null) {
                    items.refreshUniquePresentation(stack, definition);
                }
            }
            items.applyCommonRarityIfNeeded(stack);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!itemPresentationEnabled()) {
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            items.applyCommonRarityIfNeeded(event.getItem().getItemStack());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        if (!itemPresentationEnabled()) {
            return;
        }
        items.applyCommonRarityIfNeeded(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!itemPresentationEnabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        // The clicked item may be placed into the player inventory after this
        // event. Scan one tick later, so chest transfers and shift-click work.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack stack : player.getInventory().getContents()) {
                items.applyCommonRarityIfNeeded(stack);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!itemPresentationEnabled()) {
            return;
        }
        // This fires while a vanilla loot table fills a container, before a
        // player can hover the generated weapon or armour.
        for (ItemStack stack : event.getLoot()) {
            items.applyCommonRarityIfNeeded(stack);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!itemPresentationEnabled()) {
            return;
        }
        ItemStack result = event.getInventory().getResult();
        if (items.applyCommonRarityIfNeeded(result)) {
            event.getInventory().setResult(result);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!itemPresentationEnabled()) {
            return;
        }
        ItemStack result = event.getResult();
        if (items.applyCommonRarityIfNeeded(result)) {
            event.setResult(result);
        }
    }

    private boolean itemPresentationEnabled() {
        return plugin.getConfig().getBoolean("item-presentation.enabled", false);
    }
}
