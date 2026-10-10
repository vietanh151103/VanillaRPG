package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.gui.MarketGuiService;
import vn.vietanhrpg.core.service.MarketService;

import java.util.Locale;

public final class MarketCommand implements CommandExecutor {
    private final MarketService market;
    private final MarketGuiService gui;

    public MarketCommand(MarketService market, MarketGuiService gui) {
        this.market = market;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("vanillarpg.market.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("browse")) {
            gui.open(player, 0);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> {
                if (!player.hasPermission("vanillarpg.market.sell")) {
                    player.sendMessage(Component.text("No permission to sell.", NamedTextColor.RED));
                    return true;
                }
                Long price = number(player, args, "/market sell <price>");
                if (price != null) market.sell(player, price);
            }
            case "buy" -> {
                Long id = number(player, args, "/market buy <listing-id>");
                if (id != null) market.buy(player, id);
            }
            case "cancel" -> {
                Long id = number(player, args, "/market cancel <listing-id>");
                if (id != null) market.cancel(player, id);
            }
            case "claim" -> market.claim(player, true);
            case "mine" -> market.sendMine(player);
            case "history" -> market.sendHistory(player);
            default -> usage(player);
        }
        return true;
    }

    private Long number(Player player, String[] args, String usage) {
        if (args.length != 2) {
            player.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
            return null;
        }
        try {
            long value = Long.parseLong(args[1]);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Value must be a positive whole number.", NamedTextColor.RED));
            return null;
        }
    }

    private void usage(Player player) {
        player.sendMessage(Component.text("/market - browse listings", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/market sell <price> - list the held stack", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/market buy <id>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/market cancel <id>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/market claim", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/market mine", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/market history", NamedTextColor.YELLOW));
    }
}
