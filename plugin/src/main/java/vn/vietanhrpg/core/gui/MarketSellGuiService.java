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
import vn.vietanhrpg.core.service.MarketService;

import java.util.List;
import java.util.Map;

/** A one-item escrow screen. The item always returns to the player unless the listing is created. */
public final class MarketSellGuiService implements Listener {
    private static final int INPUT_SLOT = 13;
    private final MarketService market;

    public MarketSellGuiService(MarketService market) {
        this.market = market;
    }

    public void open(Player player) {
        if (!market.enabled()) {
            player.sendMessage(Component.text("The player market is disabled.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("vanillarpg.market.sell")) {
            player.sendMessage(Component.text("No permission to sell.", NamedTextColor.RED));
            return;
        }

        SellHolder holder = new SellHolder(market.guiDefaultPrice());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("List an Item", NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        redraw(holder);
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

        if (rawSlot == INPUT_SLOT && allowsInputClick(event.getClick())) {
            return;
        }

        event.setCancelled(true);
        switch (rawSlot) {
            case 10 -> changePrice(holder, -1_000);
            case 11 -> changePrice(holder, -100);
            case 12 -> changePrice(holder, -10);
            case 14 -> changePrice(holder, 10);
            case 15 -> changePrice(holder, 100);
            case 16 -> changePrice(holder, 1_000);
            case 22 -> confirm(player, holder);
            case 26 -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder)) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        ItemStack escrow = holder.inventory.getItem(INPUT_SLOT);
        if (escrow == null || escrow.getType().isAir()) return;

        holder.inventory.setItem(INPUT_SLOT, new ItemStack(Material.AIR));
        returnItem((Player) event.getPlayer(), escrow);
    }

    private void confirm(Player player, SellHolder holder) {
        ItemStack escrow = holder.inventory.getItem(INPUT_SLOT);
        if (escrow == null || escrow.getType().isAir()) {
            player.sendMessage(Component.text("Place an item stack in the center slot first.", NamedTextColor.RED));
            return;
        }

        escrow = escrow.clone();
        holder.inventory.setItem(INPUT_SLOT, new ItemStack(Material.AIR));
        if (market.listEscrow(player, escrow, holder.price)) {
            player.closeInventory();
        } else {
            returnItem(player, escrow);
        }
    }

    private void changePrice(SellHolder holder, long difference) {
        long changed;
        try {
            changed = Math.addExact(holder.price, difference);
        } catch (ArithmeticException ignored) {
            changed = difference > 0 ? market.maxPrice() : market.minPrice();
        }
        holder.price = Math.max(market.minPrice(), Math.min(market.maxPrice(), changed));
        redraw(holder);
    }

    private void redraw(SellHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.setItem(4, priceInfo(holder.price));
        inventory.setItem(10, button(Material.RED_DYE, "-1,000", NamedTextColor.RED));
        inventory.setItem(11, button(Material.RED_DYE, "-100", NamedTextColor.RED));
        inventory.setItem(12, button(Material.RED_DYE, "-10", NamedTextColor.RED));
        inventory.setItem(14, button(Material.LIME_DYE, "+10", NamedTextColor.GREEN));
        inventory.setItem(15, button(Material.LIME_DYE, "+100", NamedTextColor.GREEN));
        inventory.setItem(16, button(Material.LIME_DYE, "+1,000", NamedTextColor.GREEN));
        inventory.setItem(22, button(Material.EMERALD_BLOCK, "Confirm listing", NamedTextColor.GREEN));
        inventory.setItem(26, button(Material.BARRIER, "Cancel and return item", NamedTextColor.RED));
    }

    private ItemStack priceInfo(long price) {
        ItemStack stack = button(Material.GOLD_INGOT, "Price: " + price + " Coin", NamedTextColor.GOLD);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line("Allowed: " + market.minPrice() + " - " + market.maxPrice() + " Coin", NamedTextColor.GRAY),
                line("Put one item stack in the center slot", NamedTextColor.YELLOW)
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

    private boolean allowsInputClick(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.CREATIVE;
    }

    private void returnItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static final class SellHolder implements InventoryHolder {
        private Inventory inventory;
        private long price;

        private SellHolder(long price) {
            this.price = price;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
