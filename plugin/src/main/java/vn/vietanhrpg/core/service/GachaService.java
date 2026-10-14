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
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

/** Ticket/Elite-Coin gacha for the former Elite item shop. Pulls never alter future odds. */
public final class GachaService implements Listener {
    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;
    private final ContentService content;
    private final NamespacedKey ticketKey;
    private final List<PoolEntry> eliteItemPool = new ArrayList<>();
    private int eliteItemPoolWeight;
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
        eliteItemPoolWeight = 0;
        for (Map<?, ?> configured : config.getMapList("elite-item-pool")) {
            Object rawId = configured.get("id");
            if (rawId == null || rawId.toString().isBlank()) continue;
            int weight = positiveInt(configured.get("weight"), 1);
            addPoolEntry("ELITE_ITEM", rawId.toString().trim(), 1, weight);
        }
        if (eliteItemPool.isEmpty()) {
            List<ItemStack> specialBlacksmithItems = new ArrayList<>();
            List<String> fallbackItems = new ArrayList<>();
            try {
                // This is the exact dynamic source used by EliteMobs' Special Blacksmith.
                // It returns ItemStacks, not file IDs: retain them directly so no item is
                // discarded merely because its displayed name is not a configuration ID.
                CustomItem.getCustomItemStackShopList().forEach(stack -> {
                    if (stack != null && !stack.getType().isAir()) specialBlacksmithItems.add(stack.clone());
                });
                if (specialBlacksmithItems.isEmpty()) {
                    CustomItem.getCustomItems().forEach((id, item) -> {
                        if (item.getItemType().name().equalsIgnoreCase("CUSTOM")) fallbackItems.add(id);
                    });
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not read EliteMobs custom shop pool: " + exception.getMessage());
            }
            int groupWeight = positiveInt(config.get("blacksmith-pool-weight"), 750);
            if (!specialBlacksmithItems.isEmpty()) addWeightedShopStacks(specialBlacksmithItems, groupWeight);
            else addWeightedBlacksmithItems(fallbackItems, groupWeight);
        }
        for (Map<?, ?> configured : config.getMapList("bonus-pool")) {
            Object rawType = configured.get("type");
            String type = rawType == null ? "" : rawType.toString().trim().toUpperCase(Locale.ROOT);
            if (type.isBlank()) continue;
            Object rawBonusId = configured.get("id");
            String id = rawBonusId == null ? "" : rawBonusId.toString().trim();
            addPoolEntry(type, id, positiveInt(configured.get("amount"), 1), positiveInt(configured.get("weight"), 1));
        }
        plugin.getLogger().info("Loaded " + eliteItemPool.size() + " Elite gacha rewards with total weight " + eliteItemPoolWeight + ".");
    }

    private void addWeightedBlacksmithItems(List<String> rawIds, int groupWeight) {
        List<String> ids = rawIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) return;
        int base = groupWeight / ids.size();
        int remainder = groupWeight % ids.size();
        for (int index = 0; index < ids.size(); index++) {
            addPoolEntry("ELITE_ITEM", ids.get(index), 1, base + (index < remainder ? 1 : 0));
        }
    }

    private void addWeightedShopStacks(List<ItemStack> rawStacks, int groupWeight) {
        if (rawStacks.isEmpty()) return;
        int base = groupWeight / rawStacks.size();
        int remainder = groupWeight % rawStacks.size();
        for (int index = 0; index < rawStacks.size(); index++) {
            ItemStack stack = rawStacks.get(index).clone();
            addPoolEntry("EM_STACK", stackName(stack), 1, base + (index < remainder ? 1 : 0), stack);
        }
    }

    private void addPoolEntry(String type, String id, int amount, int weight) {
        addPoolEntry(type, id, amount, weight, null);
    }

    private void addPoolEntry(String type, String id, int amount, int weight, ItemStack directStack) {
        eliteItemPool.add(new PoolEntry(type, id, amount, weight, directStack));
        eliteItemPoolWeight += weight;
    }

    private static int positiveInt(Object raw, int fallback) {
        if (raw instanceof Number number) return Math.max(1, number.intValue());
        try { return Math.max(1, Integer.parseInt(String.valueOf(raw))); }
        catch (NumberFormatException ignored) { return fallback; }
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
        ItemStack progress = progressItem(spins);
        inventory.setItem(22, progress);
        inventory.setItem(24, button(Material.BOOK, "Pool odds", NamedTextColor.AQUA,
                "View every current reward and its exact chance.",
                "Blacksmith items: " + blacksmithChance() + "% of the pool."));
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
        if (eliteItemPool.isEmpty()) loadEliteItemPool();
        removeUnavailableRewards();
        if (eliteItemPool.isEmpty()) {
            rolling.remove(player.getUniqueId());
            player.sendMessage(Component.text("The Elite gacha pool has no valid loaded items. No currency was charged.", NamedTextColor.RED));
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

    private void removeUnavailableRewards() {
        List<String> unavailable = eliteItemPool.stream()
                .filter(entry -> entry.type().equals("ELITE_ITEM"))
                .map(PoolEntry::id)
                .filter(id -> findCustomItem(id) == null)
                .toList();
        if (unavailable.isEmpty()) return;
        eliteItemPool.removeIf(entry -> entry.type().equals("ELITE_ITEM") && unavailable.contains(entry.id()));
        eliteItemPoolWeight = eliteItemPool.stream().mapToInt(PoolEntry::weight).sum();
        plugin.getLogger().warning("Removed unavailable Elite gacha rewards: " + String.join(", ", unavailable));
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

    private PoolEntry roll() {
        if (eliteItemPool.isEmpty() || eliteItemPoolWeight <= 0) return null;
        int target = ThreadLocalRandom.current().nextInt(eliteItemPoolWeight);
        for (PoolEntry entry : eliteItemPool) {
            target -= entry.weight();
            if (target < 0) return entry;
        }
        return eliteItemPool.get(eliteItemPool.size() - 1);
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
        List<PoolEntry> rewards = new ArrayList<>();
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
                inventory.setItem(slot, preview(player, roll()));
            }
            tick[0]++;
            if (tick[0] >= 30) {
                holder.task.cancel();
                int[] slots = selectedSlots(count);
                for (int i = 0; i < rewards.size(); i++) inventory.setItem(slots[i], preview(player, rewards.get(i)));
                for (PoolEntry reward : rewards) giveReward(player, reward);
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

    private ItemStack preview(Player player, PoolEntry reward) {
        if (reward == null) return button(Material.BARRIER, "Unavailable reward", NamedTextColor.RED);
        if (reward.type().equals("EM_STACK") && reward.directStack() != null) return reward.directStack().clone();
        if (!reward.type().equals("ELITE_ITEM")) return specialRewardIcon(reward);
        String id = reward.id();
        try {
            CustomItem custom = findCustomItem(id);
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

    private CustomItem findCustomItem(String id) {
        CustomItem custom = CustomItem.getCustomItem(id);
        if (custom != null) return custom;
        if (id.endsWith(".yml")) return CustomItem.getCustomItem(id.substring(0, id.length() - 4));
        return CustomItem.getCustomItem(id + ".yml");
    }

    private String prettyId(String id) {
        if (id == null) return "Unknown";
        return id.replace('_', ' ');
    }

    private void giveReward(Player player, PoolEntry reward) {
        ItemStack stack = switch (reward.type().toUpperCase()) {
            case "EM_STACK" -> reward.directStack() == null ? null : reward.directStack().clone();
            case "ELITE_ITEM" -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "em loot give " + player.getName() + " " + reward.id());
                player.sendMessage(Component.text("Gacha reward: " + prettyId(reward.id()), NamedTextColor.LIGHT_PURPLE));
                yield null;
            }
            case "UNIQUE" -> content.uniqueItem(reward.id()) == null ? null : items.createUniqueItem(content.uniqueItem(reward.id()));
            case "MATERIAL" -> content.material(reward.id()) == null ? null : items.createCustomMaterial(content.material(reward.id()), reward.amount());
            case "TICKET" -> ticket(reward.amount());
            case "SOULBIND_REMOVER" -> specialSoulbindRemover(reward.amount());
            case "COINS" -> {
                economy.addCoin(player.getUniqueId(), reward.amount());
                player.sendMessage(Component.text("Gacha reward: " + reward.amount() + " Elite Coins", NamedTextColor.GOLD));
                yield null;
            }
            default -> null;
        };
        if (reward.type().equalsIgnoreCase("ELITE_ITEM") || reward.type().equalsIgnoreCase("COINS")) return;
        if (stack == null) {
            player.sendMessage(Component.text("A configured gacha reward is invalid; contact an admin.", NamedTextColor.RED));
            return;
        }
        deliver(player, stack);
    }

    private void giveReward(Player player, Reward reward) {
        giveReward(player, new PoolEntry(reward.type(), reward.id(), reward.amount(), 1, null));
    }

    private ItemStack specialRewardIcon(PoolEntry reward) {
        return switch (reward.type()) {
            case "SOULBIND_REMOVER" -> specialSoulbindRemover(reward.amount());
            case "TICKET" -> ticket(reward.amount());
            case "COINS" -> button(Material.GOLD_NUGGET, reward.amount() + " Elite Coins", NamedTextColor.GOLD,
                    "Instantly added to your EliteMobs balance.");
            default -> button(Material.CHEST, prettyId(reward.type()), NamedTextColor.GOLD);
        };
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

    private String stackName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stack.getItemMeta().displayName());
        }
        return stack.getType().translationKey();
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

    private ItemStack progressItem(long spins) {
        List<String> lore = new ArrayList<>();
        lore.add("Total pulls: " + spins);
        ConfigurationSection milestones = config.getConfigurationSection("milestones");
        if (milestones == null) return button(Material.NETHER_STAR, "Spin rewards", NamedTextColor.GREEN, lore.toArray(String[]::new));
        milestones.getKeys(false).stream()
                .map(Long::parseLong).sorted().forEach(milestone -> {
                    ConfigurationSection reward = config.getConfigurationSection("milestones." + milestone);
                    if (reward == null) return;
                    String state = spins >= milestone ? "Reached" : (milestone - spins) + " pulls left";
                    lore.add(milestone + " pulls: " + rewardLabel(reward) + " (" + state + ")");
                });
        lore.add("Milestones are fixed bonuses, not pity.");
        return button(Material.NETHER_STAR, "Spin rewards", NamedTextColor.GREEN, lore.toArray(String[]::new));
    }

    private String rewardLabel(ConfigurationSection reward) {
        String type = reward.getString("type", "TICKET").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, reward.getInt("amount", 1));
        return switch (type) {
            case "SOULBIND_REMOVER" -> "Soulbind Remover x" + amount;
            case "TICKET" -> "Gacha Ticket x" + amount;
            case "COINS" -> amount + " Elite Coins";
            default -> prettyId(reward.getString("id", type));
        };
    }

    private String blacksmithChance() {
        if (eliteItemPoolWeight <= 0) return "0.00";
        int blacksmithWeight = eliteItemPool.stream().filter(entry -> entry.type().equals("ELITE_ITEM"))
                .mapToInt(PoolEntry::weight).sum();
        return String.format(Locale.ROOT, "%.2f", blacksmithWeight * 100.0 / eliteItemPoolWeight);
    }

    public void openOdds(Player player) { openOdds(player, 0); }

    private void openOdds(Player player, int page) {
        removeUnavailableRewards();
        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(eliteItemPool.size() / (double) perPage));
        int normalizedPage = Math.max(0, Math.min(page, pages - 1));
        OddsHolder holder = new OddsHolder(normalizedPage);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Elite Gacha Odds " + (normalizedPage + 1) + "/" + pages, NamedTextColor.DARK_AQUA));
        holder.inventory = inventory;
        int start = normalizedPage * perPage;
        for (int index = start; index < Math.min(start + perPage, eliteItemPool.size()); index++) {
            PoolEntry entry = eliteItemPool.get(index);
            ItemStack icon = preview(player, entry);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore() && meta.lore() != null) lore.addAll(meta.lore());
            lore.add(Component.text(String.format(Locale.ROOT, "Chance: %.3f%%", entry.weight() * 100.0 / eliteItemPoolWeight), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Weight: " + entry.weight() + " / " + eliteItemPoolWeight, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(index - start, icon);
        }
        if (normalizedPage > 0) inventory.setItem(45, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        inventory.setItem(49, button(Material.NETHER_STAR, "Pool summary", NamedTextColor.GREEN,
                "Blacksmith items: " + blacksmithChance() + "%", "Bonus rewards: "
                        + String.format(Locale.ROOT, "%.2f", 100.0 - Double.parseDouble(blacksmithChance())) + "%"));
        if (normalizedPage + 1 < pages) inventory.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        player.openInventory(inventory);
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
                && !(event.getView().getTopInventory().getHolder() instanceof AnimationHolder)
                && !(event.getView().getTopInventory().getHolder() instanceof OddsHolder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getView().getTopInventory().getHolder() instanceof OddsHolder odds) {
            if (event.getRawSlot() == 45) openOdds(player, odds.page - 1);
            if (event.getRawSlot() == 53) openOdds(player, odds.page + 1);
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> pull(player, 1, true);
            case 12 -> pull(player, 1, false);
            case 14 -> pull(player, 5, true);
            case 16 -> pull(player, 5, false);
            case 24 -> openOdds(player);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GachaHolder
                || event.getView().getTopInventory().getHolder() instanceof AnimationHolder
                || event.getView().getTopInventory().getHolder() instanceof OddsHolder) event.setCancelled(true);
    }

    private record Reward(String type, String id, int amount) { }
    private record PoolEntry(String type, String id, int amount, int weight, ItemStack directStack) { }

    private static final class GachaHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class AnimationHolder implements InventoryHolder {
        private Inventory inventory;
        private org.bukkit.scheduler.BukkitTask task;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class OddsHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;
        private OddsHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
