package vn.vietanhrpg.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.service.ShopService;

import java.util.List;
import java.util.Map;

/** Chest-like escrow screen. Each eligible input stack carries a temporary sale preview on hover. */
public final class ShopSellGuiService implements Listener {
    private static final int INPUT_SLOTS = 36;
    private static final int INFO_SLOT = 45;
    private static final int SELL_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private final JavaPlugin plugin;
    private final ShopService shop;
    private final NamespacedKey previewKey;

    public ShopSellGuiService(JavaPlugin plugin, ShopService shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.previewKey = new NamespacedKey(plugin, "shop_sale_preview");
    }

    public void open(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Sell Minerals - Chest", NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        drawControls(player, holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();
        if (rawSlot >= topSize) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        if (isInputSlot(rawSlot) && allowsInputClick(event.getClick())) {
            // Never let the temporary preview metadata leave this virtual chest.
            event.setCurrentItem(undecorate(event.getCurrentItem()));
            schedulePreviewRefresh(player, holder);
            return;
        }

        event.setCancelled(true);
        if (rawSlot == SELL_SLOT) {
            sellAll(player, holder);
        } else if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder holder)) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize() && !isInputSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) schedulePreviewRefresh(player, holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        Player player = (Player) event.getPlayer();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            holder.inventory.setItem(slot, new ItemStack(Material.AIR));
            returnItem(player, undecorate(item));
        }
    }

    private void schedulePreviewRefresh(Player player, SellHolder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            decorateInputs(player, holder);
            drawControls(player, holder);
        });
    }

    private void decorateInputs(Player player, SellHolder holder) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            holder.inventory.setItem(slot, decorate(player, item));
        }
    }

    private ItemStack decorate(Player player, ItemStack item) {
        if (hasPreview(item)) return decorate(player, undecorate(item));
        // Only unmodified vanilla minerals receive a preview. Other items are left untouched and cannot be sold.
        if (item.hasItemMeta()) return item;
        ShopService.SellPreview preview = shop.sellPreview(player, item.getType(), item.getAmount());
        if (preview.pricePerItem() <= 0) return item;

        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        meta.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
        meta.lore(List.of(
                line("SELL PRICE: " + preview.pricePerItem() + " Coin each", NamedTextColor.GOLD),
                line("Stack total: " + (preview.requestedAmount() * preview.pricePerItem()) + " Coin", NamedTextColor.YELLOW),
                line("Can sell now: " + preview.sellableAmount() + "/" + preview.requestedAmount(), NamedTextColor.GREEN),
                line("Payout now: " + preview.payout() + " Coin", NamedTextColor.GOLD),
                line("Item limit left today: " + preview.itemRemaining(), NamedTextColor.GRAY),
                line("Coin limit left today: " + preview.coinRemaining(), NamedTextColor.GRAY)
        ));
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack undecorate(ItemStack item) {
        if (item == null || item.getType().isAir() || !hasPreview(item)) return item;
        return new ItemStack(item.getType(), item.getAmount());
    }

    private boolean hasPreview(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(previewKey, PersistentDataType.BYTE);
    }

    private void drawControls(Player player, SellHolder holder) {
        Inventory inventory = holder.inventory;
        for (int slot = INPUT_SLOTS; slot < INFO_SLOT; slot++) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
        }
        inventory.setItem(INFO_SLOT, info(player));
        inventory.setItem(SELL_SLOT, sellButton(player));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close and return unsold items", NamedTextColor.RED));
    }

    private void sellAll(Player player, SellHolder holder) {
        int totalSold = 0;
        int stacksSold = 0;
        long totalPayout = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            ItemStack plainItem = undecorate(item);
            ShopService.SellPreview preview = shop.sellPreview(player, plainItem.getType(), plainItem.getAmount());
            int sold = shop.sellEscrowSilently(player, plainItem);
            if (sold <= 0) continue;
            totalSold += sold;
            stacksSold++;
            totalPayout += sold * preview.pricePerItem();
            if (sold >= plainItem.getAmount()) {
                holder.inventory.setItem(slot, new ItemStack(Material.AIR));
            } else {
                ItemStack leftover = new ItemStack(plainItem.getType(), plainItem.getAmount() - sold);
                holder.inventory.setItem(slot, decorate(player, leftover));
            }
        }
        if (stacksSold == 0) {
            player.sendMessage(Component.text("No eligible mineral stack could be sold. Check each item's hover preview and daily limits.", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Sold " + totalSold + " item(s) from " + stacksSold + " stack(s) for "
                    + totalPayout + " Coin. Unsold items remain in the chest.", NamedTextColor.GREEN));
        }
        decorateInputs(player, holder);
        drawControls(player, holder);
    }

    private ItemStack info(Player player) {
        ItemStack stack = button(Material.CHEST, "Hover your items for sale details", NamedTextColor.AQUA);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line("Put plain minerals in any of the 36 chest slots.", NamedTextColor.GRAY),
                line("Hover a stack to view its price and current limits.", NamedTextColor.YELLOW),
                line("Daily Coin remaining: " + shop.dailySellRemaining(player), NamedTextColor.GOLD),
                line("Rare/RPG and modified items stay unsold.", NamedTextColor.RED)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack sellButton(Player player) {
        ItemStack stack = button(Material.GOLD_INGOT, "Sell all stacks", NamedTextColor.GOLD);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line("Sell every eligible stack in the 36 chest slots.", NamedTextColor.GRAY),
                line("Daily Coin remaining: " + shop.dailySellRemaining(player), NamedTextColor.GOLD),
                line("Invalid or limited items stay in the chest.", NamedTextColor.YELLOW)
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

    private boolean isInputSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < INPUT_SLOTS;
    }

    private boolean allowsInputClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.CREATIVE;
    }

    private void returnItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static final class SellHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
