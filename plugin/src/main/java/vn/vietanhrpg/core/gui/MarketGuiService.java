package vn.vietanhrpg.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vn.vietanhrpg.core.market.MarketListing;
import vn.vietanhrpg.core.service.MarketService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarketGuiService implements Listener {
    private final MarketService market;

    public MarketGuiService(MarketService market) {
        this.market = market;
    }

    public void open(Player player, int requestedPage) {
        if (!market.enabled()) {
            player.sendMessage(Component.text("The player market is disabled.", NamedTextColor.RED));
            return;
        }
        int pageSize = market.pageSize();
        int total = market.listingCount();
        int maxPage = Math.max(0, (total - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));

        MarketHolder holder = new MarketHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Player Market - Page " + (page + 1), NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;

        List<MarketListing> listings = market.listings(page);
        for (int slot = 0; slot < listings.size() && slot < pageSize; slot++) {
            MarketListing listing = listings.get(slot);
            holder.listingBySlot.put(slot, listing.id());
            inventory.setItem(slot, listingItem(player, listing));
        }

        if (page > 0) inventory.setItem(45, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        inventory.setItem(47, button(Material.CHEST, "My listings", NamedTextColor.GOLD));
        inventory.setItem(49, info(total, page, maxPage));
        inventory.setItem(51, button(Material.ENDER_CHEST, "Claim deliveries", NamedTextColor.AQUA));
        if (page < maxPage) inventory.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (slot == 45 && holder.page > 0) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == 47) {
            player.closeInventory();
            market.sendMine(player);
            return;
        }
        if (slot == 49) {
            open(player, holder.page);
            return;
        }
        if (slot == 51) {
            market.claim(player, true);
            open(player, holder.page);
            return;
        }
        if (slot == 53) {
            open(player, holder.page + 1);
            return;
        }

        Long listingId = holder.listingBySlot.get(slot);
        if (listingId == null) return;
        market.buy(player, listingId);
        open(player, holder.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack listingItem(Player viewer, MarketListing listing) {
        ItemStack stack;
        try {
            stack = ItemStack.deserializeBytes(listing.itemData());
        } catch (Exception exception) {
            stack = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(line("Price: " + listing.price() + " Coin", NamedTextColor.GOLD));
        lore.add(line("Seller: " + listing.sellerName(), NamedTextColor.GRAY));
        lore.add(line("Listing: #" + listing.id(), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        if (listing.sellerUuid().equals(viewer.getUniqueId())) {
            lore.add(line("Your listing - /market cancel " + listing.id(), NamedTextColor.YELLOW));
        } else {
            lore.add(line("Click to purchase", NamedTextColor.GREEN));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack info(int total, int page, int maxPage) {
        ItemStack stack = button(Material.EMERALD, "Market", NamedTextColor.GREEN);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line(total + " active listing(s)", NamedTextColor.GRAY),
                line("Page " + (page + 1) + "/" + (maxPage + 1), NamedTextColor.GRAY),
                line("Click to refresh", NamedTextColor.YELLOW)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Material material, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name, color).decorate(TextDecoration.BOLD));
        stack.setItemMeta(meta);
        return stack;
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static final class MarketHolder implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        private final Map<Integer, Long> listingBySlot = new HashMap<>();

        private MarketHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
