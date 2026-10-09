package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.service.EconomyService;

public final class CoinCommand implements CommandExecutor {
    private final EconomyService economy;

    public CoinCommand(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        economy.ensure(player);

        if (args.length == 0) {
            player.sendMessage(Component.text("Coin: " + economy.coin(player.getUniqueId()), NamedTextColor.GOLD));
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pay")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Player is not online.", NamedTextColor.RED));
                return true;
            }
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException exception) {
                player.sendMessage(Component.text("Amount must be a whole number.", NamedTextColor.RED));
                return true;
            }
            if (amount <= 0) {
                player.sendMessage(Component.text("Amount must be greater than 0.", NamedTextColor.RED));
                return true;
            }
            economy.ensure(target);
            if (!economy.transferCoin(player.getUniqueId(), target.getUniqueId(), amount)) {
                player.sendMessage(Component.text("Not enough Coin or invalid transfer.", NamedTextColor.RED));
                return true;
            }
            player.sendMessage(Component.text("Paid " + target.getName() + " " + amount + " Coin.", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Received " + amount + " Coin from " + player.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        player.sendMessage(Component.text("Usage: /coin [pay <player> <amount>]", NamedTextColor.YELLOW));
        return true;
    }
}
