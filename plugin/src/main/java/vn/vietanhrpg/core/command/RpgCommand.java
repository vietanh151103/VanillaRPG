package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.VanillaRpgPlugin;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.service.EconomyService;

public final class RpgCommand implements CommandExecutor {
    private final VanillaRpgPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;

    public RpgCommand(VanillaRpgPlugin plugin, Database database, EconomyService economy, ItemService items) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vanillarpg.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("VanillaRPG config reloaded.", NamedTextColor.GREEN));
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
            case "givetoken" -> {
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
                economy.addToken(target.getUniqueId(), amount);
                sender.sendMessage(Component.text("Added " + amount + " Token to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            case "setrarity" -> {
                if (!(sender instanceof Player player) || args.length != 2) {
                    sender.sendMessage(Component.text("Player usage: /rpg setrarity <COMMON|UNCOMMON|RARE|EPIC|LEGENDARY>", NamedTextColor.YELLOW));
                    return true;
                }
                try {
                    Rarity rarity = Rarity.valueOf(args[1].toUpperCase());
                    items.applyRarity(player.getInventory().getItemInMainHand(), rarity);
                    database.incrementCraft(player.getUniqueId(), rarity);
                    player.sendMessage(Component.text("Set held item to " + rarity.name() + ".", rarity.color()));
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(Component.text("Invalid rarity or unsupported held item.", NamedTextColor.RED));
                }
                return true;
            }
            default -> {
                usage(sender);
                return true;
            }
        }
    }

    private Long parseAmount(String raw) {
        try {
            long amount = Long.parseLong(raw);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("/rpg reload", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg givecoin <player> <amount>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg givetoken <player> <amount>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg setrarity <rarity>", NamedTextColor.YELLOW));
    }
}
