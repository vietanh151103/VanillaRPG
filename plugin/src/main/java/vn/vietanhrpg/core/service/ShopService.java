package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.shop.ShopEntry;

import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final EnumSet<Material> SELLABLE_MINERALS = EnumSet.of(
            Material.COAL, Material.RAW_COPPER, Material.RAW_IRON, Material.RAW_GOLD,
            Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ,
            Material.AMETHYST_SHARD, Material.EMERALD, Material.DIAMOND,
            Material.CHARCOAL, Material.COPPER_INGOT, Material.IRON_INGOT,
            Material.GOLD_INGOT, Material.FLINT
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
            entries.add(new ShopEntry(id, material, amount, price));
        }
        return entries;
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

    /** Sells an eligible unmodified stack without removing it. The caller removes the returned amount on success. */
    public int sellEscrow(Player player, ItemStack hand) {
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage(Component.text("Place a plain mineral stack in the sell slot first.", NamedTextColor.RED));
            return 0;
        }
        if (hand.hasItemMeta() || !SELLABLE_MINERALS.contains(hand.getType())) {
            player.sendMessage(Component.text("The shop only buys plain configured minerals, not rare or RPG items.", NamedTextColor.RED));
            return 0;
        }
        SellOffer offer = sellOffer(hand.getType());
        if (offer == null) {
            player.sendMessage(Component.text("The shop is not buying this mineral right now.", NamedTextColor.RED));
            return 0;
        }

        long remainingCoin = dailySellRemaining(player);
        int remainingItems = Math.max(0, offer.maxPerDay()
                - database.shopSaleItemAmountToday(player.getUniqueId(), todayKey(), hand.getType().name()));
        int quantity = (int) Math.min(hand.getAmount(), Math.min(remainingCoin / offer.pricePerItem(), remainingItems));
        if (quantity <= 0) {
            player.sendMessage(Component.text("You reached today's Coin or item sale limit. Try again tomorrow.", NamedTextColor.RED));
            return 0;
        }
        long payout = quantity * offer.pricePerItem();

        try {
            if (!database.creditShopSale(player.getUniqueId(), todayKey(), hand.getType().name(), quantity, payout,
                    dailySellLimit(), offer.maxPerDay())) {
                player.sendMessage(Component.text("Sale limit changed; your minerals were not sold.", NamedTextColor.RED));
                return 0;
            }
        } catch (RuntimeException exception) {
            player.sendMessage(Component.text("Sale failed; your minerals were not sold.", NamedTextColor.RED));
            plugin.getLogger().severe("Shop sale failed for " + player.getName() + ": " + exception.getMessage());
            return 0;
        }

        long remainingAfter = dailySellRemaining(player);
        int itemRemainingAfter = Math.max(0, offer.maxPerDay()
                - database.shopSaleItemAmountToday(player.getUniqueId(), todayKey(), hand.getType().name()));
        player.sendMessage(Component.text("Sold " + quantity + "x " + pretty(hand.getType()) + " for " + payout
                + " Coin. Remaining today: " + itemRemainingAfter + " item(s), " + remainingAfter + " Coin.", NamedTextColor.GREEN));
        return quantity;
    }

    public long dailySellRemaining(Player player) {
        return Math.max(0, dailySellLimit() - database.shopSaleCoinToday(player.getUniqueId(), todayKey()));
    }

    private long dailySellLimit() {
        return Math.max(0, plugin.getConfig().getLong("shop.max-sell-coin-per-day", 750));
    }

    private SellOffer sellOffer(Material material) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.sell-items");
        if (section == null) return null;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            Material configured = Material.matchMaterial(item.getString("material", ""));
            if (configured == material && SELLABLE_MINERALS.contains(configured)) {
                long price = Math.max(0, item.getLong("price-per-item", 0));
                int max = Math.max(1, item.getInt("max-per-day", 64));
                return price < 1 ? null : new SellOffer(price, max);
            }
        }
        return null;
    }

    private String todayKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private record SellOffer(long pricePerItem, int maxPerDay) {
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
