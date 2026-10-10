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

/** Chest-like escrow screen for selling one stack of approved minerals. */
public final class ShopSellGuiService implements Listener {
    private static final int INPUT_SLOT = 13;
    private final ShopService shop;

    public ShopSellGuiService(ShopService shop) {
        this.shop = shop;
    }

    public void open(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Sell Minerals", NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        inventory.setItem(4, info(player));
        inventory.setItem(22, button(Material.GOLD_INGOT, "Sell stack", NamedTextColor.GOLD));
        inventory.setItem(26, button(Material.BARRIER, "Close and return item", NamedTextColor.RED));
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
        if (rawSlot == INPUT_SLOT && allowsInputClick(event.getClick())) return;

        event.setCancelled(true);
        if (rawSlot == 22) {
            sell(player, holder);
        } else if (rawSlot == 26) {
            player.closeInventory();
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
        ItemStack item = holder.inventory.getItem(INPUT_SLOT);
        if (item == null || item.getType().isAir()) return;
        holder.inventory.setItem(INPUT_SLOT, new ItemStack(Material.AIR));
        returnItem((Player) event.getPlayer(), item);
    }

    private void sell(Player player, SellHolder holder) {
        ItemStack item = holder.inventory.getItem(INPUT_SLOT);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(Component.text("Place a mineral stack in the center slot first.", NamedTextColor.RED));
            return;
        }
        int sold = shop.sellEscrow(player, item.clone());
        if (sold <= 0) return;
        if (sold >= item.getAmount()) {
            holder.inventory.setItem(INPUT_SLOT, new ItemStack(Material.AIR));
        } else {
            ItemStack leftover = item.clone();
            leftover.setAmount(item.getAmount() - sold);
            holder.inventory.setItem(INPUT_SLOT, leftover);
        }
        holder.inventory.setItem(4, info(player));
    }

    private ItemStack info(Player player) {
        ItemStack stack = button(Material.CHEST, "Mineral buyback", NamedTextColor.AQUA);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                line("Place one plain mineral stack in the center", NamedTextColor.GRAY),
                line("Then click Sell stack", NamedTextColor.YELLOW),
                line("Daily Coin remaining: " + shop.dailySellRemaining(player), NamedTextColor.GOLD),
                line("Rare/RPG items are rejected and returned.", NamedTextColor.RED)
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

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
