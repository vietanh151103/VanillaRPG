package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.magmaguy.elitemobs.items.customitems.CustomItem;
import com.magmaguy.elitemobs.items.customenchantments.SoulbindEnchantment;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Ticket/Elite-Coin gacha for the former Elite item shop. Pulls never alter future odds. */
public final class GachaService implements Listener {
    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;
    private final ContentService content;
    private final NamespacedKey ticketKey;
    private final List<String> eliteItemPool = new ArrayList<>();
    private final Set<java.util.UUID> rolling = new HashSet<>();
    private YamlConfiguration config;

    public GachaService(JavaPlugin plugin, Database database, EconomyService economy,
                        ItemService items, ContentService content) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
        this.content = content;
        this.ticketKey = new NamespacedKey(plugin, "elite_gacha_ticket");
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gacha.yml"));
        loadEliteItemPool();
    }

    /** Uses the exact item list that EliteMobs exposes to its Custom Shop. */
    private void loadEliteItemPool() {
        eliteItemPool.clear();
        try {
            CustomItem.getCustomItems().forEach((id, item) -> {
                if (item.getItemType().name().equalsIgnoreCase("CUSTOM")) eliteItemPool.add(id);
            });
            // Some EliteMobs builds populate the item registry after plugin enable.
            // Keep the direct shop cache as a second source when it is available.
            if (eliteItemPool.isEmpty()) {
                CustomItem.getCustomItemStackShopList().forEach(stack -> {
                    String id = stack.getItemMeta() == null ? null : stack.getItemMeta().getDisplayName();
                    if (id != null && !id.isBlank()) eliteItemPool.add(id);
                });
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not read EliteMobs custom shop pool: " + exception.getMessage());
        }
    }

    public void open(Player player) {
        GachaHolder holder = new GachaHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Elite Gacha", NamedTextColor.DARK_PURPLE));
        holder.inventory = inventory;
        inventory.setItem(10, button(Material.PAPER, "Pull with ticket", NamedTextColor.AQUA,
                "Consumes 1 tradeable gacha ticket."));
        inventory.setItem(12, button(Material.GOLD_INGOT, "Single pull", NamedTextColor.GOLD,
                "Cost: " + singleCost() + " Elite Coins"));
        inventory.setItem(14, button(Material.CHEST, "5 pulls with tickets", NamedTextColor.LIGHT_PURPLE,
                "Consumes 5 tickets."));
        inventory.setItem(16, button(Material.ENDER_CHEST, "5 pulls", NamedTextColor.RED,
                "Cost: " + fiveCost() + " Elite Coins"));
        long spins = spins(player);
        ItemStack progress = button(Material.NETHER_STAR, "Spin rewards", NamedTextColor.GREEN,
                "Total pulls: " + spins, "Milestones give bonus rewards; no pity.");
        inventory.setItem(22, progress);
        player.openInventory(inventory);
    }

    public ItemStack ticket(int amount) {
        Material material = Material.matchMaterial(config.getString("ticket.material", "PAPER"));
        if (material == null) material = Material.PAPER;
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(config.getString("ticket.name", "Elite Gacha Ticket"), NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getStringList("ticket.lore")) lore.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isTicket(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer()
                .has(ticketKey, PersistentDataType.BYTE);
    }

    public long spins(Player player) {
        return database.counter(player.getUniqueId(), "gacha_spins");
    }

    public void pull(Player player, int count, boolean useTickets) {
        if (count != 1 && count != 5) return;
        if (!rolling.add(player.getUniqueId())) {
            player.sendMessage(Component.text("A gacha roll is already running.", NamedTextColor.YELLOW));
            return;
        }
        long cost = count == 1 ? singleCost() : fiveCost();
        if (useTickets) {
            if (!removeTickets(player, count)) {
                rolling.remove(player.getUniqueId());
                player.sendMessage(Component.text("You need " + count + " Elite Gacha Ticket(s).", NamedTextColor.RED));
                return;
            }
        } else if (!economy.takeCoin(player.getUniqueId(), cost)) {
            rolling.remove(player.getUniqueId());
            player.sendMessage(Component.text("Not enough Elite Coins.", NamedTextColor.RED));
            return;
        }
        animate(player, count);
    }

    private long singleCost() { return Math.max(1, config.getLong("single-coin-cost", 250)); }
    private long fiveCost() { return Math.max(singleCost(), config.getLong("five-coin-cost", 1125)); }

    private boolean removeTickets(Player player, int count) {
        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isTicket(stack)) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
        }
        if (remaining > 0) {
            // Restore any partial removal if the inventory did not contain enough tickets.
            player.getInventory().addItem(ticket(count - remaining));
            return false;
        }
        return true;
    }

    private String roll() {
        if (eliteItemPool.isEmpty()) return null;
        return eliteItemPool.get(ThreadLocalRandom.current().nextInt(eliteItemPool.size()));
    }

    private void animate(Player player, int count) {
        if (eliteItemPool.isEmpty()) {
            // EliteMobs may finish its late registry initialization after us.
            loadEliteItemPool();
        }
        if (eliteItemPool.isEmpty()) {
            rolling.remove(player.getUniqueId());
            player.sendMessage(Component.text("EliteMobs has no Custom Shop items loaded. Check EliteMobs startup/reload.", NamedTextColor.RED));
            return;
        }
        List<String> rewards = new ArrayList<>();
        for (int i = 0; i < count; i++) rewards.add(roll());
        AnimationHolder holder = new AnimationHolder();
        int inventorySize = count == 5 ? 54 : 27;
        Inventory inventory = Bukkit.createInventory(holder, inventorySize, Component.text("Elite Gacha - Rolling", NamedTextColor.DARK_PURPLE));
        holder.inventory = inventory;
        addIndicators(inventory, count);
        player.openInventory(inventory);
        final int[] tick = {0};
        holder.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int slot : rollingSlots(count)) {
                inventory.setItem(slot, preview(player, eliteItemPool.get(ThreadLocalRandom.current().nextInt(eliteItemPool.size()))));
            }
            tick[0]++;
            if (tick[0] >= 30) {
                holder.task.cancel();
                int[] slots = selectedSlots(count);
                for (int i = 0; i < rewards.size(); i++) inventory.setItem(slots[i], preview(player, rewards.get(i)));
                for (String id : rewards) giveEliteItem(player, id);
                long total = database.addCounter(player.getUniqueId(), "gacha_spins", count);
                grantMilestones(player, total - count, total);
                rolling.remove(player.getUniqueId());
                player.sendMessage(Component.text("Gacha complete: " + count + " pull(s). Total pulls: " + total, NamedTextColor.LIGHT_PURPLE));
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 20L);
            }
        }, 0L, 2L);
    }

    private int[] rollingSlots(int count) {
        if (count == 5) {
            List<Integer> slots = new ArrayList<>();
            int[] columns = {0, 2, 4, 6, 8};
            for (int row = 0; row < 5; row++) {
                for (int column : columns) slots.add(row * 9 + column);
            }
            return slots.stream().mapToInt(Integer::intValue).toArray();
        }
        return new int[]{11, 12, 13, 14, 15};
    }

    private int[] selectedSlots(int count) {
        if (count == 5) return new int[]{18, 20, 22, 24, 26};
        return new int[]{13};
    }

    private void addIndicators(Inventory inventory, int count) {
        ItemStack marker = button(Material.PURPLE_STAINED_GLASS_PANE, count == 5 ? "Selected row" : "Selected slot", NamedTextColor.LIGHT_PURPLE,
                "The roll stops here");
        if (count == 5) {
            // Separators sit at the center row between lanes 1-3-5-7-9.
            inventory.setItem(19, marker);
            inventory.setItem(21, marker);
            inventory.setItem(23, marker);
            inventory.setItem(25, marker);
        } else {
            inventory.setItem(4, marker);
            inventory.setItem(22, marker);
        }
    }

    private ItemStack preview(Player player, String id) {
        try {
            CustomItem custom = CustomItem.getCustomItem(id);
            if (custom != null) {
                ItemStack generated = custom.generateDefaultsItemStack(player, false, null);
                if (generated != null && !generated.getType().isAir()) return generated;
            }
        } catch (Exception ignored) {
            // Fall through to a readable placeholder if a third-party item cannot preview.
        }
        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(prettyId(id), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("EliteMobs item", NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private void giveEliteItem(Player player, String id) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "em loot give " + player.getName() + " " + id);
        player.sendMessage(Component.text("Gacha reward: " + prettyId(id), NamedTextColor.LIGHT_PURPLE));
    }

    private String prettyId(String id) {
        if (id == null) return "Unknown";
        return id.replace('_', ' ');
    }

    private void giveReward(Player player, Reward reward) {
        ItemStack stack = switch (reward.type().toUpperCase()) {
            case "ELITE_ITEM" -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "em loot give " + player.getName() + " " + reward.id());
                player.sendMessage(Component.text("Gacha milestone reward: EliteMobs item " + reward.id(), NamedTextColor.LIGHT_PURPLE));
                yield null;
            }
            case "UNIQUE" -> content.uniqueItem(reward.id()) == null ? null : items.createUniqueItem(content.uniqueItem(reward.id()));
            case "MATERIAL" -> content.material(reward.id()) == null ? null : items.createCustomMaterial(content.material(reward.id()), reward.amount());
            case "TICKET" -> ticket(reward.amount());
            case "SOULBIND_REMOVER" -> specialSoulbindRemover(reward.amount());
            default -> null;
        };
        if (reward.type().equalsIgnoreCase("ELITE_ITEM")) return;
        if (stack == null) {
            player.sendMessage(Component.text("A configured gacha reward is invalid; contact an admin.", NamedTextColor.RED));
            return;
        }
        deliver(player, stack);
    }

    private void deliver(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(Component.text("Gacha reward: ", NamedTextColor.GRAY).append(stack.displayName()));
    }

    private ItemStack specialSoulbindRemover(int amount) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Soulbind Remover", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Hold the target item in your off-hand, then right-click.", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "soulbind_remover"), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onSoulbindRemoverUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack remover = event.getItem();
        if (remover == null || !remover.hasItemMeta() || !remover.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "soulbind_remover"), PersistentDataType.BYTE)) return;
        ItemStack target = event.getPlayer().getInventory().getItemInOffHand();
        if (target.getType().isAir() || !target.hasItemMeta()) {
            event.getPlayer().sendMessage(Component.text("Put a soulbound item in your off-hand first.", NamedTextColor.RED));
            return;
        }
        ItemMeta targetMeta = target.getItemMeta();
        boolean removed = false;
        for (NamespacedKey key : new ArrayList<>(targetMeta.getPersistentDataContainer().getKeys())) {
            if (key.getKey().toLowerCase().contains("soulbind")) {
                targetMeta.getPersistentDataContainer().remove(key);
                removed = true;
            }
        }
        if (!removed) {
            event.getPlayer().sendMessage(Component.text("That item has no removable Soulbind marker.", NamedTextColor.YELLOW));
            return;
        }
        target.setItemMeta(targetMeta);
        remover.setAmount(remover.getAmount() - 1);
        event.getPlayer().sendMessage(Component.text("Soulbind removed.", NamedTextColor.GREEN));
        event.setCancelled(true);
    }

    private void grantMilestones(Player player, long before, long after) {
        ConfigurationSection section = config.getConfigurationSection("milestones");
        if (section == null) return;
        for (String raw : section.getKeys(false)) {
            long milestone;
            try { milestone = Long.parseLong(raw); } catch (NumberFormatException ignored) { continue; }
            if (before < milestone && after >= milestone) {
                String path = "milestones." + raw;
                giveReward(player, new Reward(section.getString(raw + ".type", "TICKET"),
                        section.getString(raw + ".id"), Math.max(1, section.getInt(raw + ".amount", 1))));
                player.sendMessage(Component.text("Spin milestone reached: " + milestone, NamedTextColor.GREEN));
            }
        }
    }

    private ItemStack button(Material material, String title, NamedTextColor color, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(title, color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof GachaHolder)
                && !(event.getView().getTopInventory().getHolder() instanceof AnimationHolder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        switch (event.getRawSlot()) {
            case 10 -> pull(player, 1, true);
            case 12 -> pull(player, 1, false);
            case 14 -> pull(player, 5, true);
            case 16 -> pull(player, 5, false);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GachaHolder
                || event.getView().getTopInventory().getHolder() instanceof AnimationHolder) event.setCancelled(true);
    }

    private record Reward(String type, String id, int amount) { }

    private static final class GachaHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class AnimationHolder implements InventoryHolder {
        private Inventory inventory;
        private org.bukkit.scheduler.BukkitTask task;
        @Override public Inventory getInventory() { return inventory; }
    }
}
