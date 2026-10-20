package vn.vietanhrpg.core.listener;

import com.magmaguy.elitemobs.api.utils.EliteItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;

/**
 * AuctionHouse lists the item in the player's main hand through its sell
 * command. EliteMobs soulbinding is stored in item data, rather than in a
 * fixed lore line, so AuctionHouse's text blacklist cannot identify it.
 */
public final class SoulboundAuctionListener implements Listener {
    private static final Set<String> AUCTION_COMMANDS = Set.of("ah", "auctionhouse", "auction");

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAuctionSell(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length < 2) return;

        String root = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];
        if (!AUCTION_COMMANDS.contains(root.toLowerCase(Locale.ROOT))
                || !"sell".equalsIgnoreCase(parts[1])) return;

        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR || !isSoulbound(held)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text(
                "Không thể đăng bán vật phẩm Soulbound trên AuctionHouse.", NamedTextColor.RED));
    }

    private boolean isSoulbound(ItemStack item) {
        try {
            return item.hasItemMeta()
                    && Boolean.TRUE.equals(EliteItemManager.hasSoulbindEnchantment(item.getItemMeta()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
