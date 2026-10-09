package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ForgeService {
    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;

    public ForgeService(JavaPlugin plugin, Database database, EconomyService economy, ItemService items) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
    }

    public void upgrade(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.isSupportedWeapon(held)) {
            player.sendMessage(Component.text("V1 only supports swords, axes, tridents and maces.", NamedTextColor.RED));
            return;
        }

        Rarity current = items.rarity(held);
        Rarity next = current.next();
        if (next == null) {
            player.sendMessage(Component.text("This item is already Legendary.", NamedTextColor.GOLD));
            return;
        }
        if (next == Rarity.LEGENDARY && !plugin.getConfig().getBoolean("forge.allow-legendary-without-blueprint", false)) {
            player.sendMessage(Component.text("Legendary crafting is locked until the Blueprint/Boss module is enabled.", NamedTextColor.GOLD));
            return;
        }

        Cost cost = loadUpgradeCost(current, next);
        if (economy.coin(player.getUniqueId()) < cost.coin()) {
            player.sendMessage(Component.text("Need " + cost.coin() + " Coin.", NamedTextColor.RED));
            return;
        }
        if (economy.token(player.getUniqueId()) < cost.token()) {
            player.sendMessage(Component.text("Need " + cost.token() + " Token.", NamedTextColor.RED));
            return;
        }
        for (Map.Entry<Material, Integer> requirement : cost.materials().entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(requirement.getKey()), requirement.getValue())) {
                player.sendMessage(Component.text("Need " + requirement.getValue() + "x " + requirement.getKey().name() + ".", NamedTextColor.RED));
                return;
            }
        }

        if (!economy.takeCoin(player.getUniqueId(), cost.coin())) {
            player.sendMessage(Component.text("Could not charge Coin.", NamedTextColor.RED));
            return;
        }
        if (!economy.takeToken(player.getUniqueId(), cost.token())) {
            economy.addCoin(player.getUniqueId(), cost.coin());
            player.sendMessage(Component.text("Could not charge Token.", NamedTextColor.RED));
            return;
        }

        removeMaterials(player, cost.materials());
        items.applyRarity(held, next);
        database.incrementCraft(player.getUniqueId(), next);
        player.sendMessage(Component.text("Upgraded to " + next.name() + ".", next.color()));
    }

    public void reroll(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.isSupportedWeapon(held)) {
            player.sendMessage(Component.text("Hold a supported weapon first.", NamedTextColor.RED));
            return;
        }
        Rarity rarity = items.rarity(held);
        if (rarity == Rarity.COMMON) {
            player.sendMessage(Component.text("Common items have no affixes to reroll.", NamedTextColor.RED));
            return;
        }
        long cost = plugin.getConfig().getLong("forge.reroll-cost." + rarity.name(), 500);
        if (!economy.takeCoin(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Need " + cost + " Coin to reroll.", NamedTextColor.RED));
            return;
        }
        items.applyRarity(held, rarity);
        player.sendMessage(Component.text("Rerolled " + rarity.name() + " affixes for " + cost + " Coin.", NamedTextColor.AQUA));
    }

    private Cost loadUpgradeCost(Rarity current, Rarity next) {
        String path = "forge.upgrade." + current.name() + "-to-" + next.name();
        long coin = plugin.getConfig().getLong(path + ".coin", 0);
        long token = plugin.getConfig().getLong(path + ".token", 0);
        Map<Material, Integer> materials = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path + ".materials");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("Unknown material in config: " + key);
                    continue;
                }
                int amount = Math.max(1, section.getInt(key, 1));
                materials.put(material, amount);
            }
        }
        return new Cost(Math.max(0, coin), Math.max(0, token), materials);
    }

    private void removeMaterials(Player player, Map<Material, Integer> requirements) {
        for (Map.Entry<Material, Integer> requirement : requirements.entrySet()) {
            int remaining = requirement.getValue();
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                ItemStack stack = storage[slot];
                if (stack == null || stack.getType() != requirement.getKey()) {
                    continue;
                }
                int taken = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - taken);
                remaining -= taken;
                if (stack.getAmount() <= 0) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }
    }

    private record Cost(long coin, long token, Map<Material, Integer> materials) {
    }
}
