package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.VanillaRpgPlugin;
import vn.vietanhrpg.core.content.BlueprintDefinition;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.content.UniqueItemDefinition;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.service.EconomyService;
import vn.vietanhrpg.core.service.BetterStructuresLootBridge;
import vn.vietanhrpg.core.world.MysticForestService;

import java.util.Locale;

public final class RpgCommand implements CommandExecutor {
    private final VanillaRpgPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;
    private final ContentService content;
    private final MysticForestService mysticForest;
    private final BetterStructuresLootBridge betterStructuresLoot;

    public RpgCommand(VanillaRpgPlugin plugin, Database database, EconomyService economy,
                      ItemService items, ContentService content, MysticForestService mysticForest,
                      BetterStructuresLootBridge betterStructuresLoot) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
        this.content = content;
        this.mysticForest = mysticForest;
        this.betterStructuresLoot = betterStructuresLoot;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vanillarpg.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mysticforest" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This test world is player-only.", NamedTextColor.RED));
                    return true;
                }
                mysticForest.open(player);
                return true;
            }
            case "reload" -> {
                if (plugin.reloadAll()) {
                    sender.sendMessage(Component.text("VanillaRPG " + plugin.getPluginMeta().getVersion()
                            + " config/content reloaded.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Reload cancelled because a YAML file is invalid. Check the server log.", NamedTextColor.RED));
                }
                return true;
            }
            case "givecoin" -> {
                if (args.length != 3) {
                    usage(sender);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                Long amount = parseAmount(args[2]);
                if (target == null || amount == null) {
                    sender.sendMessage(Component.text("Online player and valid amount required.", NamedTextColor.RED));
                    return true;
                }
                economy.ensure(target);
                economy.addCoin(target.getUniqueId(), amount);
                sender.sendMessage(Component.text("Added " + amount + " Coin to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            case "giveitem" -> {
                if (args.length < 3 || args.length > 4) {
                    usage(sender);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                MaterialDefinition definition = content.material(args[2]);
                int amount = 1;
                if (args.length == 4) {
                    Long parsed = parseAmount(args[3]);
                    if (parsed == null || parsed > 2304) {
                        sender.sendMessage(Component.text("Amount must be 1-2304.", NamedTextColor.RED));
                        return true;
                    }
                    amount = parsed.intValue();
                }
                if (target == null || definition == null) {
                    sender.sendMessage(Component.text("Online player and valid custom material ID required.", NamedTextColor.RED));
                    return true;
                }
                giveStacked(target, definition, amount);
                sender.sendMessage(Component.text("Gave " + amount + "x " + definition.id() + " to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            case "givegear" -> {
                if (args.length < 3 || args.length > 4) {
                    giveGearUsage(sender);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Online player required.", NamedTextColor.RED));
                    return true;
                }

                ItemStack gear;
                String displayId;
                if (args.length == 3) {
                    displayId = args[2].toLowerCase(Locale.ROOT);
                    gear = createGearFromId(displayId);
                } else {
                    displayId = args[2].toLowerCase(Locale.ROOT) + "_" + args[3].toLowerCase(Locale.ROOT);
                    gear = createGenericGear(args[2], args[3]);
                }

                if (gear == null) {
                    sender.sendMessage(Component.text("Unknown/unsupported gear: " + displayId, NamedTextColor.RED));
                    giveGearUsage(sender);
                    return true;
                }

                target.getInventory().addItem(gear);
                sender.sendMessage(Component.text("Gave " + displayId + " to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            case "giveblueprint" -> {
                if (args.length != 3) {
                    usage(sender);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                BlueprintDefinition definition = content.blueprint(resolveBlueprintId(args[2]));
                if (target == null || definition == null) {
                    sender.sendMessage(Component.text("Online player and valid blueprint ID required.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(items.createBlueprint(definition));
                sender.sendMessage(Component.text("Gave blueprint " + definition.id() + " to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            case "setrarity" -> {
                if (!(sender instanceof Player player) || args.length != 2) {
                    sender.sendMessage(Component.text("Player usage: /rpg setrarity <COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC>", NamedTextColor.YELLOW));
                    return true;
                }
                try {
                    Rarity rarity = Rarity.valueOf(args[1].toUpperCase(Locale.ROOT));
                    items.applyRarity(player.getInventory().getItemInMainHand(), rarity);
                    database.incrementCraft(player.getUniqueId(), rarity);
                    player.sendMessage(Component.text("Set held item to " + rarity.name() + ".", rarity.color()));
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(Component.text("Invalid rarity or unsupported held item.", NamedTextColor.RED));
                }
                return true;
            }
            case "loot" -> {
                if (!(sender instanceof Player player) || args.length != 3 || !args[1].equalsIgnoreCase("refresh")) {
                    lootRefreshUsage(sender);
                    return true;
                }
                BetterStructuresLootBridge.RefreshResult result = betterStructuresLoot.refreshTarget(player, args[2]);
                switch (result) {
                    case SUCCESS -> player.sendMessage(Component.text(
                            "Added RPG loot to the targeted container. Existing contents were kept.", NamedTextColor.GREEN));
                    case DISABLED -> player.sendMessage(Component.text(
                            "BetterStructures loot refresh is disabled in config.yml.", NamedTextColor.RED));
                    case INVALID_TIER -> lootRefreshUsage(player);
                    case NOT_A_CONTAINER -> player.sendMessage(Component.text(
                            "Look directly at a chest, barrel, or other container within 6 blocks.", NamedTextColor.RED));
                    case ALREADY_REFRESHED -> player.sendMessage(Component.text(
                            "This container already has VanillaRPG loot and cannot be refreshed again.", NamedTextColor.YELLOW));
                }
                return true;
            }
            default -> {
                usage(sender);
                return true;
            }
        }
    }

    private ItemStack createGearFromId(String rawId) {
        rawId = resolveGearId(rawId);
        UniqueItemDefinition unique = content.uniqueItem(rawId);
        if (unique != null) {
            return items.createUniqueItem(unique);
        }

        int underscore = rawId.indexOf('_');
        if (underscore <= 0 || underscore >= rawId.length() - 1) {
            return null;
        }
        String rarityRaw = rawId.substring(0, underscore);
        String materialRaw = rawId.substring(underscore + 1);
        return createGenericGear(rarityRaw, materialRaw);
    }

    private ItemStack createGenericGear(String rarityRaw, String materialRaw) {
        try {
            Rarity rarity = Rarity.valueOf(rarityRaw.toUpperCase(Locale.ROOT));
            Material material = Material.matchMaterial(materialRaw.toUpperCase(Locale.ROOT));
            if (material == null) {
                return null;
            }
            ItemStack gear = new ItemStack(material);
            if (!items.isSupportedWeapon(gear)) {
                return null;
            }
            items.applyRarity(gear, rarity);
            return gear;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void giveStacked(Player player, MaterialDefinition definition, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, definition.material().getMaxStackSize());
            ItemStack item = items.createCustomMaterial(definition, give);
            player.getInventory().addItem(item);
            remaining -= give;
        }
    }

    private String resolveGearId(String raw) {
        String id = raw.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (id) {
            case "hero_sword", "herosword", "hero" -> "ashbringer";
            case "demonic_blade", "demonicblade", "demonic" -> "bloodreaver";
            default -> id;
        };
    }

    private String resolveBlueprintId(String raw) {
        return resolveGearId(raw);
    }

    private Long parseAmount(String raw) {
        try {
            long amount = Long.parseLong(raw);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void giveGearUsage(CommandSender sender) {
        sender.sendMessage(Component.text("/rpg givegear <player> <gear-id>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Example: /rpg givegear tradao epic_netherite_sword", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Named gear: hero_sword | demonic_blade", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Alternative: /rpg givegear <player> <rarity> <material>", NamedTextColor.YELLOW));
    }

    private void lootRefreshUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Look at an existing BetterStructures chest/barrel, then use:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg loot refresh <surface|underground|dungeon|nether|end|high-dungeon>", NamedTextColor.YELLOW));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("/rpg reload", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg givecoin <player> <amount>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg giveitem <player> <material-id> [amount]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg givegear <player> <gear-id>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg giveblueprint <player> <blueprint-id>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg setrarity <rarity>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg loot refresh <tier>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg mysticforest", NamedTextColor.YELLOW));
    }
}
