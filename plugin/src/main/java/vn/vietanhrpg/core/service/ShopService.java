package vn.vietanhrpg.core.service;

import com.magmaguy.elitemobs.items.ItemTagger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.shop.ShopCategory;
import vn.vietanhrpg.core.shop.ShopEntry;
import vn.vietanhrpg.core.shop.ShopTransaction;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server shop for explicitly configured everyday supplies only. */
public final class ShopService {
    // Explicit whitelist: Paper 26.2's Material#isEdible does not reliably identify
    // food for this API target. Keeping this list also prevents rare consumables.
    private static final EnumSet<Material> BASIC_SHOP_MATERIALS = EnumSet.of(
            Material.APPLE, Material.BREAD, Material.BAKED_POTATO, Material.CARROT,
            Material.BEETROOT, Material.BEETROOT_SOUP, Material.COOKED_BEEF,
            Material.COOKED_CHICKEN, Material.COOKED_COD, Material.COOKED_MUTTON,
            Material.COOKED_PORKCHOP, Material.COOKED_RABBIT, Material.COOKED_SALMON,
            Material.COOKIE, Material.DRIED_KELP, Material.GLOW_BERRIES, Material.HONEY_BOTTLE,
            Material.MELON_SLICE, Material.MILK_BUCKET, Material.MUSHROOM_STEW,
            Material.POTATO, Material.PUMPKIN_PIE, Material.RABBIT_STEW, Material.ROTTEN_FLESH,
            Material.SWEET_BERRIES, Material.CHORUS_FRUIT,
            // Common building supplies only: no ores, precious blocks or RPG materials.
            Material.COBBLESTONE, Material.STONE, Material.DEEPSLATE, Material.DIRT,
            Material.SAND, Material.GRAVEL, Material.OAK_LOG, Material.OAK_PLANKS,
            Material.GLASS, Material.BRICKS
    );
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final Database database;

    public ShopService(JavaPlugin plugin, EconomyService economy, Database database) {
        this.plugin = plugin;
        this.economy = economy;
        this.database = database;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("shop.enabled", true);
    }

    public int pageSize() {
        return Math.max(9, Math.min(45, plugin.getConfig().getInt("shop.page-size", 45)));
    }

    public List<ShopEntry> entries() {
        List<ShopEntry> entries = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.items");
        if (section == null) return entries;

        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            Material material = Material.matchMaterial(item.getString("material", ""));
            int amount = item.getInt("amount", 1);
            long price = item.getLong("price", 0);
            // Paper 26.2 reports unreliable Material stack sizes for several blocks.
            // The approved shop list uses ordinary stackable supplies only.
            if (material == null || !isAllowedShopMaterial(material) || amount < 1 || amount > 64 || price < 1) {
                plugin.getLogger().warning("Ignoring invalid or non-approved shop item: " + id);
                continue;
            }
            entries.add(new ShopEntry(id, material, amount, price,
                    ShopCategory.parse(item.getString("category"), material)));
        }
        return entries;
    }

    public List<ShopEntry> entries(ShopCategory category) {
        return entries().stream().filter(entry -> entry.category() == category).toList();
    }

    public boolean buy(Player player, String id) {
        if (!enabled()) {
            player.sendMessage(Component.text("The server shop is disabled.", NamedTextColor.RED));
            return false;
        }
        ShopEntry entry = entries().stream().filter(value -> value.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        if (entry == null) {
            player.sendMessage(Component.text("That shop item is no longer available.", NamedTextColor.RED));
            return false;
        }

        ItemStack purchased = new ItemStack(entry.material(), entry.amount());
        if (!canFit(player, purchased)) {
            player.sendMessage(Component.text("Free inventory space before buying this item.", NamedTextColor.RED));
            return false;
        }
        if (!economy.takeCoin(player.getUniqueId(), entry.price())) {
            player.sendMessage(Component.text("You need " + entry.price() + " Coin to buy this.", NamedTextColor.RED));
            return false;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(purchased);
        if (!leftovers.isEmpty()) {
            // This should be unreachable after canFit, but never discard a paid item.
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        try {
            database.recordShopPurchase(player.getUniqueId(), entry.material().name(), entry.amount(), entry.price());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not record shop purchase for " + player.getName() + ": " + exception.getMessage());
        }
        player.sendMessage(Component.text("Bought " + entry.amount() + "x " + pretty(entry.material())
                + " for " + entry.price() + " Coin.", NamedTextColor.GREEN));
        return true;
    }

    public void sellHeld(Player player) {
        if (!enabled()) {
            player.sendMessage(Component.text("The server shop is disabled.", NamedTextColor.RED));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        int sold = sellEscrow(player, hand);
        if (sold <= 0) return;
        if (sold >= hand.getAmount()) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            ItemStack leftover = hand.clone();
            leftover.setAmount(hand.getAmount() - sold);
            player.getInventory().setItemInMainHand(leftover);
        }
    }

    /** Sells any priced stack without removing it. The caller removes the returned amount on success. */
    public int sellEscrow(Player player, ItemStack hand) {
        return sellEscrow(player, hand, true);
    }

    /** GUI batch sales show one summary after all stacks are processed. */
    public int sellEscrowSilently(Player player, ItemStack hand) {
        return sellEscrow(player, hand, false);
    }

    private int sellEscrow(Player player, ItemStack hand, boolean announce) {
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            message(player, announce, "Place an item stack in the sell slot first.", NamedTextColor.RED);
            return 0;
        }
        SellPreview preview = sellPreview(hand);
        if (preview.pricePerItem() <= 0) {
            message(player, announce, "This item has no sell price.", NamedTextColor.RED);
            return 0;
        }

        try {
            economy.addCoin(player.getUniqueId(), preview.payout());
            database.recordShopSale(player.getUniqueId(), hand.getType().name(), hand.getAmount(), Math.round(preview.payout()));
        } catch (RuntimeException exception) {
            message(player, announce, "Sale failed; your minerals were not sold.", NamedTextColor.RED);
            plugin.getLogger().severe("Shop sale failed for " + player.getName() + ": " + exception.getMessage());
            return 0;
        }
        message(player, announce, "Sold " + hand.getAmount() + "x " + pretty(hand.getType()) + " for "
                + formatCoin(preview.payout()) + " Coin.", NamedTextColor.GREEN);
        return hand.getAmount();
    }

    /** Calculates the full-stack payout without changing contents or balance. */
    public SellPreview sellPreview(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return SellPreview.unavailable(0);
        double unitPrice = ItemTagger.isEliteItem(item)
                ? ItemTagger.getItemValue(item) * eliteResalePercent() / 100.0
                : configuredSellPrice(item.getType());
        unitPrice = Math.max(0, Math.round(unitPrice * 100.0) / 100.0);
        return new SellPreview(item.getAmount(), unitPrice, item.getAmount(), unitPrice * item.getAmount());
    }

    /** Previews the whole sell chest. No daily or per-material caps apply. */
    public SellCartPreview sellCartPreview(List<ItemStack> items) {
        double payout = 0;
        int sellableItems = 0;
        int stacks = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            SellPreview preview = sellPreview(item);
            if (preview.payout() <= 0) continue;
            stacks++;
            sellableItems += item.getAmount();
            payout += preview.payout();
        }
        return new SellCartPreview(payout, sellableItems, stacks);
    }

    public List<ShopTransaction> history(Player player, int limit) {
        return database.shopHistory(player.getUniqueId(), limit);
    }

    private double configuredSellPrice(Material material) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.sell-items");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(id);
                if (item == null) continue;
                Material configured = Material.matchMaterial(item.getString("material", ""));
                if (configured == material) {
                    return Math.max(0, item.getDouble("price-per-item", 0));
                }
            }
        }
        return Math.max(0, plugin.getConfig().getDouble("shop.fallback-sell-price-per-item", 1));
    }

    private double eliteResalePercent() {
        JavaPlugin eliteMobs = (JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("EliteMobs");
        if (eliteMobs != null) {
            File economySettings = new File(eliteMobs.getDataFolder(), "EconomySettings.yml");
            if (economySettings.isFile()) {
                return Math.max(0, YamlConfiguration.loadConfiguration(economySettings).getDouble("itemResaleValue", 5.0));
            }
        }
        return Math.max(0, plugin.getConfig().getDouble("shop.elite-resale-percent-fallback", 5.0));
    }

    private String formatCoin(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.format(Locale.ROOT, "%.2f", amount);
    }

    public record SellPreview(int requestedAmount, double pricePerItem, int sellableAmount, double payout) {
        private static SellPreview unavailable(int requestedAmount) {
            return new SellPreview(requestedAmount, 0, 0, 0);
        }
    }

    public record SellCartPreview(double payout, int sellableItems, int eligibleStacks) {
    }

    private void message(Player player, boolean announce, String text, NamedTextColor color) {
        if (announce) player.sendMessage(Component.text(text, color));
    }

    private boolean isAllowedShopMaterial(Material material) {
        return BASIC_SHOP_MATERIALS.contains(material);
    }

    private boolean canFit(Player player, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private String pretty(Material material) {
        StringBuilder result = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
