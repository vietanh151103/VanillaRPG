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
import vn.vietanhrpg.core.shop.ShopEntry;
import vn.vietanhrpg.core.service.ShopService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopGuiService implements Listener {
    private final ShopService shop;
    private final ShopSellGuiService sellGui;

    public ShopGuiService(ShopService shop, ShopSellGuiService sellGui) {
        this.shop = shop;
        this.sellGui = sellGui;
    }

    public void open(Player player, int requestedPage) {
        if (!shop.enabled()) {
            player.sendMessage(Component.text("The server shop is disabled.", NamedTextColor.RED));
            return;
        }
        List<ShopEntry> entries = shop.entries();
        int pageSize = shop.pageSize();
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));

        ShopHolder holder = new ShopHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Shop - Buy Supplies - Page " + (page + 1), NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        int start = page * pageSize;
        for (int slot = 0; slot < pageSize && start + slot < entries.size(); slot++) {
            ShopEntry entry = entries.get(start + slot);
            holder.entryBySlot.put(slot, entry.id());
            inventory.setItem(slot, entryItem(entry));
        }

        if (page > 0) inventory.setItem(45, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        inventory.setItem(47, button(Material.GOLD_INGOT, "Open sell chest and price list", NamedTextColor.GOLD));
        inventory.setItem(49, info(player, entries.size(), page, maxPage));
        if (page < maxPage) inventory.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (slot == 45 && holder.page > 0) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == 47) {
            sellGui.open(player);
            return;
        }
        if (slot == 49) {
            open(player, holder.page);
            return;
        }
        if (slot == 53) {
            open(player, holder.page + 1);
            return;
        }
        String entryId = holder.entryBySlot.get(slot);
        if (entryId == null) return;
        shop.buy(player, entryId);
        open(player, holder.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack entryItem(ShopEntry entry) {
        ItemStack stack = new ItemStack(entry.material(), entry.amount());
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(line("Bundle: " + entry.amount(), NamedTextColor.GRAY));
        lore.add(line("BUY PRICE: " + entry.price() + " Coin", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        lore.add(Component.empty());
        lore.add(line("Click to buy", NamedTextColor.GREEN));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack info(Player player, int total, int page, int maxPage) {
        ItemStack stack = button(Material.EMERALD, "Everyday supplies", NamedTextColor.GREEN);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line(total + " configured item(s)", NamedTextColor.GRAY),
                line("Gold button: sell chest with each mineral's sell price.", NamedTextColor.YELLOW),
                line("Daily mineral sale remaining: " + shop.dailySellRemaining(player) + " Coin", NamedTextColor.GOLD),
                line("Rare gear and RPG materials are player-trade only.", NamedTextColor.YELLOW),
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

    private static final class ShopHolder implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        private final Map<Integer, String> entryBySlot = new HashMap<>();

        private ShopHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
