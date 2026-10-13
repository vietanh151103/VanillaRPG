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
import vn.vietanhrpg.core.shop.ShopCategory;
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

    public void open(Player player) {
        if (!shop.enabled()) {
            player.sendMessage(Component.text("The server shop is disabled.", NamedTextColor.RED));
            return;
        }
        CategoryHolder holder = new CategoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Shop Categories", NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        inventory.setItem(11, categoryItem(ShopCategory.FOOD, shop.entries(ShopCategory.FOOD).size()));
        inventory.setItem(13, sellItem(player));
        inventory.setItem(15, categoryItem(ShopCategory.BUILDING, shop.entries(ShopCategory.BUILDING).size()));
        inventory.setItem(22, historyItem());
        player.openInventory(inventory);
    }

    private void openBuy(Player player, ShopCategory category, int requestedPage) {
        List<ShopEntry> entries = shop.entries(category);
        int pageSize = shop.pageSize();
        int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        BuyHolder holder = new BuyHolder(category, page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Shop - " + category.displayName() + " - Page " + (page + 1), NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        int start = page * pageSize;
        for (int slot = 0; slot < pageSize && start + slot < entries.size(); slot++) {
            ShopEntry entry = entries.get(start + slot);
            holder.entryBySlot.put(slot, entry.id());
            inventory.setItem(slot, entryItem(entry));
        }
        if (page > 0) inventory.setItem(45, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        inventory.setItem(47, button(Material.COMPASS, "Back to categories", NamedTextColor.AQUA));
        inventory.setItem(49, sellItem(player));
        inventory.setItem(51, info(category, entries.size(), page, maxPage));
        if (page < maxPage) inventory.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof CategoryHolder) && !(holder instanceof BuyHolder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (holder instanceof CategoryHolder) {
            if (slot == 11) openBuy(player, ShopCategory.FOOD, 0);
            else if (slot == 13) sellGui.open(player);
            else if (slot == 15) openBuy(player, ShopCategory.BUILDING, 0);
            else if (slot == 22) player.performCommand("shop history");
            return;
        }

        BuyHolder buy = (BuyHolder) holder;
        if (slot == 45 && buy.page > 0) {
            openBuy(player, buy.category, buy.page - 1);
            return;
        }
        if (slot == 47) {
            open(player);
            return;
        }
        if (slot == 49) {
            sellGui.open(player);
            return;
        }
        if (slot == 53) {
            openBuy(player, buy.category, buy.page + 1);
            return;
        }
        String entryId = buy.entryBySlot.get(slot);
        if (entryId == null) return;
        shop.buy(player, entryId);
        openBuy(player, buy.category, buy.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof CategoryHolder || holder instanceof BuyHolder) event.setCancelled(true);
    }

    private ItemStack categoryItem(ShopCategory category, int count) {
        ItemStack stack = button(category.icon(), category.displayName(), NamedTextColor.GREEN);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line(count + " available bundle(s)", NamedTextColor.GRAY),
                line("Click to browse", NamedTextColor.YELLOW)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack sellItem(Player player) {
        ItemStack stack = button(Material.GOLD_INGOT, "Sell minerals", NamedTextColor.GOLD);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line("Open the 36-slot sell chest", NamedTextColor.GRAY),
                line("Daily Elite Coins remaining: " + shop.dailySellRemaining(player), NamedTextColor.GOLD),
                line("Click to sell", NamedTextColor.YELLOW)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack historyItem() {
        ItemStack stack = button(Material.BOOK, "Recent shop history", NamedTextColor.AQUA);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(line("Click or use /shop history", NamedTextColor.YELLOW)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack entryItem(ShopEntry entry) {
        ItemStack stack = new ItemStack(entry.material(), entry.amount());
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(line("Bundle: " + entry.amount(), NamedTextColor.GRAY));
        lore.add(line("BUY PRICE: " + entry.price() + " Elite Coins", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        lore.add(Component.empty());
        lore.add(line("Click to buy", NamedTextColor.GREEN));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack info(ShopCategory category, int total, int page, int maxPage) {
        ItemStack stack = button(Material.EMERALD, category.displayName() + " supplies", NamedTextColor.GREEN);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line(total + " configured bundle(s)", NamedTextColor.GRAY),
                line("Page " + (page + 1) + "/" + (maxPage + 1), NamedTextColor.GRAY),
                line("Use Sell minerals for mining resources.", NamedTextColor.YELLOW)
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

    private static final class CategoryHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class BuyHolder implements InventoryHolder {
        private Inventory inventory;
        private final ShopCategory category;
        private final int page;
        private final Map<Integer, String> entryBySlot = new HashMap<>();

        private BuyHolder(ShopCategory category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
