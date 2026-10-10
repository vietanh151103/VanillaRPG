package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.market.MarketCancelResult;
import vn.vietanhrpg.core.market.MarketCancelStatus;
import vn.vietanhrpg.core.market.MarketDelivery;
import vn.vietanhrpg.core.market.MarketListing;
import vn.vietanhrpg.core.market.MarketPurchaseResult;
import vn.vietanhrpg.core.market.MarketPurchaseStatus;
import vn.vietanhrpg.core.market.MarketTransaction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MarketService {
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final Database database;

    public MarketService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("market.enabled", true);
    }

    public int pageSize() {
        return Math.max(9, Math.min(45, plugin.getConfig().getInt("market.page-size", 45)));
    }

    public int listingCount() {
        return database.activeMarketListingCount();
    }

    public List<MarketListing> listings(int page) {
        int size = pageSize();
        return database.activeMarketListings(size, Math.max(0, page) * size);
    }

    public void sell(Player player, long price) {
        if (!ready(player)) return;
        long minPrice = Math.max(1, plugin.getConfig().getLong("market.min-price", 1));
        long maxPrice = Math.max(minPrice, plugin.getConfig().getLong("market.max-price", 1_000_000_000L));
        if (price < minPrice || price > maxPrice) {
            player.sendMessage(Component.text("Price must be between " + minPrice + " and " + maxPrice + " Coin.", NamedTextColor.RED));
            return;
        }

        int maxListings = Math.max(1, plugin.getConfig().getInt("market.max-active-listings-per-player", 10));
        if (database.activeMarketListingCount(player.getUniqueId()) >= maxListings) {
            player.sendMessage(Component.text("You already have the maximum of " + maxListings + " active listings.", NamedTextColor.RED));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage(Component.text("Hold the item stack you want to sell.", NamedTextColor.RED));
            return;
        }

        ItemStack escrow = hand.clone();
        byte[] serialized;
        try {
            serialized = escrow.serializeAsBytes();
        } catch (Exception exception) {
            player.sendMessage(Component.text("This item could not be serialized for the market.", NamedTextColor.RED));
            plugin.getLogger().warning("Could not serialize market item for " + player.getName() + ": " + exception.getMessage());
            return;
        }

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        try {
            long listingId = database.createMarketListing(player.getUniqueId(), player.getName(), serialized,
                    escrow.getType().getKey().asString(), escrow.getAmount(), price);
            player.sendMessage(Component.text("Listed " + escrow.getAmount() + "x " + pretty(escrow.getType().name())
                    + " for " + price + " Coin. Listing #" + listingId + ".", NamedTextColor.GREEN));
        } catch (RuntimeException exception) {
            player.getInventory().setItemInMainHand(escrow);
            player.sendMessage(Component.text("Listing failed; your item was returned.", NamedTextColor.RED));
            plugin.getLogger().severe("Market listing failed for " + player.getName() + ": " + exception.getMessage());
        }
    }

    public boolean buy(Player player, long listingId) {
        if (!ready(player)) return false;
        MarketPurchaseResult result;
        try {
            result = database.purchaseMarketListing(listingId, player.getUniqueId(), player.getName());
        } catch (RuntimeException exception) {
            player.sendMessage(Component.text("Purchase failed; no Coin or listing data was changed.", NamedTextColor.RED));
            plugin.getLogger().severe("Market purchase failed for " + player.getName() + ": " + exception.getMessage());
            return false;
        }

        if (result.status() == MarketPurchaseStatus.NOT_FOUND) {
            player.sendMessage(Component.text("That listing is no longer available.", NamedTextColor.RED));
            return false;
        }
        if (result.status() == MarketPurchaseStatus.OWN_LISTING) {
            player.sendMessage(Component.text("You cannot buy your own listing. Use /market cancel " + listingId + ".", NamedTextColor.RED));
            return false;
        }
        if (result.status() == MarketPurchaseStatus.INSUFFICIENT_COIN) {
            player.sendMessage(Component.text("You do not have enough Coin.", NamedTextColor.RED));
            return false;
        }

        player.sendMessage(Component.text("Purchase complete for " + result.price()
                + " Coin. The item was added to your market deliveries.", NamedTextColor.GREEN));
        claim(player, false);
        return true;
    }

    public boolean cancel(Player player, long listingId) {
        if (!ready(player)) return false;
        MarketCancelResult result;
        try {
            result = database.cancelMarketListing(listingId, player.getUniqueId());
        } catch (RuntimeException exception) {
            player.sendMessage(Component.text("Cancellation failed; the listing remains active.", NamedTextColor.RED));
            plugin.getLogger().severe("Market cancellation failed for " + player.getName() + ": " + exception.getMessage());
            return false;
        }
        if (result.status() == MarketCancelStatus.NOT_FOUND) {
            player.sendMessage(Component.text("That active listing was not found.", NamedTextColor.RED));
            return false;
        }
        if (result.status() == MarketCancelStatus.NOT_OWNER) {
            player.sendMessage(Component.text("You do not own that listing.", NamedTextColor.RED));
            return false;
        }
        player.sendMessage(Component.text("Listing #" + listingId + " cancelled. The item was added to your deliveries.", NamedTextColor.GREEN));
        claim(player, false);
        return true;
    }

    public int claim(Player player, boolean announceEmpty) {
        if (!ready(player)) return 0;
        List<MarketDelivery> deliveries = database.pendingMarketDeliveries(player.getUniqueId(), 100);
        if (deliveries.isEmpty()) {
            if (announceEmpty) player.sendMessage(Component.text("You have no pending market deliveries.", NamedTextColor.GRAY));
            return 0;
        }

        int claimed = 0;
        for (MarketDelivery delivery : deliveries) {
            ItemStack item;
            try {
                item = ItemStack.deserializeBytes(delivery.itemData());
            } catch (Exception exception) {
                plugin.getLogger().severe("Invalid market delivery #" + delivery.id() + ": " + exception.getMessage());
                continue;
            }
            if (!canFit(player, item)) break;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
            if (!database.claimMarketDelivery(delivery.id(), player.getUniqueId())) {
                plugin.getLogger().severe("Could not mark market delivery #" + delivery.id() + " as claimed after delivery.");
                break;
            }
            claimed++;
        }

        int remaining = database.pendingMarketDeliveryCount(player.getUniqueId());
        if (claimed > 0) {
            player.sendMessage(Component.text("Claimed " + claimed + " market "
                    + (claimed == 1 ? "delivery" : "deliveries") + ".", NamedTextColor.GREEN));
        }
        if (remaining > 0) {
            player.sendMessage(Component.text(remaining + " delivery item(s) remain. Free inventory space and use /market claim.", NamedTextColor.YELLOW));
        }
        return claimed;
    }

    public void sendMine(Player player) {
        if (!ready(player)) return;
        List<MarketListing> listings = database.activeMarketListings(player.getUniqueId(), 100);
        player.sendMessage(Component.text("Active market listings (" + listings.size() + "):", NamedTextColor.GOLD));
        if (listings.isEmpty()) {
            player.sendMessage(Component.text("None.", NamedTextColor.GRAY));
            return;
        }
        for (MarketListing listing : listings) {
            player.sendMessage(Component.text("#" + listing.id() + " - " + listing.amount() + "x "
                    + pretty(listing.itemType()) + " - " + listing.price() + " Coin", NamedTextColor.YELLOW));
        }
    }

    public void sendHistory(Player player) {
        if (!ready(player)) return;
        List<MarketTransaction> history = database.marketHistory(player.getUniqueId(), 10);
        player.sendMessage(Component.text("Recent market transactions:", NamedTextColor.GOLD));
        if (history.isEmpty()) {
            player.sendMessage(Component.text("None.", NamedTextColor.GRAY));
            return;
        }
        UUID playerId = player.getUniqueId();
        for (MarketTransaction transaction : history) {
            boolean bought = transaction.buyerUuid().equals(playerId);
            String action = bought ? "Bought from " + transaction.sellerName() : "Sold to " + transaction.buyerName();
            NamedTextColor color = bought ? NamedTextColor.AQUA : NamedTextColor.GREEN;
            player.sendMessage(Component.text(HISTORY_TIME.format(Instant.ofEpochMilli(transaction.createdAt()))
                    + " | " + action + " | " + transaction.amount() + "x " + pretty(transaction.itemType())
                    + " | " + transaction.price() + " Coin | #" + transaction.listingId(), color));
        }
    }

    public int pendingCount(UUID uuid) {
        return database.pendingMarketDeliveryCount(uuid);
    }

    private boolean ready(Player player) {
        if (!enabled()) {
            player.sendMessage(Component.text("The player market is disabled.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private boolean canFit(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= maxStack;
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private String pretty(String raw) {
        String normalized = raw == null ? "item" : raw;
        int namespace = normalized.indexOf(':');
        if (namespace >= 0) normalized = normalized.substring(namespace + 1);
        StringBuilder result = new StringBuilder();
        for (String part : normalized.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
