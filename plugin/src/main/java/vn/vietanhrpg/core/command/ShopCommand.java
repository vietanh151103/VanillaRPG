package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.gui.ShopGuiService;
import vn.vietanhrpg.core.service.ShopService;
import vn.vietanhrpg.core.shop.ShopTransaction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ShopCommand implements CommandExecutor {
    private final ShopGuiService gui;
    private final ShopService shop;

    public ShopCommand(ShopGuiService gui, ShopService shop) {
        this.gui = gui;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("vanillarpg.shop.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("history")) {
            showHistory(player);
            return true;
        }
        gui.open(player);
        return true;
    }

    private void showHistory(Player player) {
        List<ShopTransaction> entries = shop.history(player, 10);
        player.sendMessage(Component.text("--- Recent shop history ---", NamedTextColor.AQUA));
        if (entries.isEmpty()) {
            player.sendMessage(Component.text("No shop purchases or sales recorded yet.", NamedTextColor.GRAY));
            return;
        }
        DateTimeFormatter time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
        for (ShopTransaction entry : entries) {
            NamedTextColor color = entry.direction().equals("SELL") ? NamedTextColor.GREEN : NamedTextColor.GOLD;
            String verb = entry.direction().equals("SELL") ? "SOLD" : "BOUGHT";
            player.sendMessage(Component.text(time.format(Instant.ofEpochMilli(entry.createdAt())) + "  " + verb + " "
                    + entry.amount() + "x " + pretty(entry.material()) + " for " + entry.coinAmount() + " Coin", color));
        }
    }

    private String pretty(String material) {
        StringBuilder result = new StringBuilder();
        for (String part : material.toLowerCase().split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
