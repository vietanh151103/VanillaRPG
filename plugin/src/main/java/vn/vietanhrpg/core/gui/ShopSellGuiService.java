package vn.vietanhrpg.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import vn.vietanhrpg.core.service.ShopService;

import java.util.List;
import java.util.Map;

/** Chest-like escrow screen for selling several stacks of approved minerals at once. */
public final class ShopSellGuiService implements Listener {
    private static final int INPUT_SLOTS = 36;
    private static final int GUIDE_START = 36;
    private static final int GUIDE_SIZE = 9;
    private static final int PREVIOUS_SLOT = 45;
    private static final int SELL_SLOT = 49;
    private static final int CLOSE_SLOT = 51;
    private static final int NEXT_SLOT = 53;
    private final ShopService shop;

    public ShopSellGuiService(ShopService shop) {
        this.shop = shop;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int requestedGuidePage) {
        List<ShopService.SellPrice> prices = shop.sellPrices();
        int maxGuidePage = Math.max(0, (prices.size() - 1) / GUIDE_SIZE);
        int guidePage = Math.max(0, Math.min(requestedGuidePage, maxGuidePage));
        SellHolder holder = new SellHolder(guidePage);
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
        if (isInputSlot(rawSlot) && allowsInputClick(event.getClick())) return;

        event.setCancelled(true);
        if (rawSlot == SELL_SLOT) {
            sellAll(player, holder);
        } else if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        } else if (rawSlot == PREVIOUS_SLOT && holder.guidePage > 0) {
            holder.guidePage--;
            drawControls(player, holder);
        } else if (rawSlot == NEXT_SLOT) {
            int maxGuidePage = Math.max(0, (shop.sellPrices().size() - 1) / GUIDE_SIZE);
            if (holder.guidePage < maxGuidePage) {
                holder.guidePage++;
                drawControls(player, holder);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder)) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize() && !isInputSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        Player player = (Player) event.getPlayer();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            holder.inventory.setItem(slot, new ItemStack(Material.AIR));
            returnItem(player, item);
        }
    }

    private void drawControls(Player player, SellHolder holder) {
        Inventory inventory = holder.inventory;
        for (int slot = GUIDE_START; slot < GUIDE_START + GUIDE_SIZE; slot++) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
        }
        List<ShopService.SellPrice> prices = shop.sellPrices();
        int start = holder.guidePage * GUIDE_SIZE;
        for (int offset = 0; offset < GUIDE_SIZE && start + offset < prices.size(); offset++) {
            inventory.setItem(GUIDE_START + offset, guideItem(player, prices.get(start + offset)));
        }
        int maxGuidePage = Math.max(0, (prices.size() - 1) / GUIDE_SIZE);
        inventory.setItem(PREVIOUS_SLOT, holder.guidePage > 0
                ? button(Material.ARROW, "Previous prices", NamedTextColor.YELLOW) : new ItemStack(Material.AIR));
        inventory.setItem(SELL_SLOT, sellButton(player));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close and return unsold items", NamedTextColor.RED));
        inventory.setItem(NEXT_SLOT, holder.guidePage < maxGuidePage
                ? button(Material.ARROW, "Next prices", NamedTextColor.YELLOW) : new ItemStack(Material.AIR));
    }

    private void sellAll(Player player, SellHolder holder) {
        int totalSold = 0;
        int stacksSold = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            int sold = shop.sellEscrowSilently(player, item.clone());
            if (sold <= 0) continue;
            totalSold += sold;
            stacksSold++;
            if (sold >= item.getAmount()) {
                holder.inventory.setItem(slot, new ItemStack(Material.AIR));
            } else {
                ItemStack leftover = item.clone();
                leftover.setAmount(item.getAmount() - sold);
                holder.inventory.setItem(slot, leftover);
            }
        }
        if (stacksSold == 0) {
            player.sendMessage(Component.text("No eligible mineral stack could be sold. Check the price list and daily limits.", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Sold from " + stacksSold + " stack(s): " + totalSold
                    + " item(s). Unsold items remain in the chest.", NamedTextColor.GREEN));
        }
        drawControls(player, holder);
    }

    private ItemStack guideItem(Player player, ShopService.SellPrice price) {
        ItemStack stack = new ItemStack(price.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(pretty(price.material()), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        meta.lore(List.of(
                line("SELL PRICE: " + price.pricePerItem() + " Coin each", NamedTextColor.GOLD),
                line("Daily item limit: " + price.maxPerDay(), NamedTextColor.GRAY),
                line("You can still sell: " + shop.dailySellItemRemaining(player, price.material()), NamedTextColor.GREEN)
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

    private String pretty(Material material) {
        StringBuilder result = new StringBuilder();
        for (String part : material.name().toLowerCase().split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private void returnItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static final class SellHolder implements InventoryHolder {
        private Inventory inventory;
        private int guidePage;

        private SellHolder(int guidePage) {
            this.guidePage = guidePage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
