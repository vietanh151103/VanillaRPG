package vn.vietanhrpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.service.GachaService;

public final class GachaCommand implements CommandExecutor {
    private final GachaService gacha;
    public GachaCommand(GachaService gacha) { this.gacha = gacha; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("odds") || args[0].equalsIgnoreCase("pool"))) {
            gacha.openOdds(player);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("ticket")) {
            if (!player.hasPermission("vanillarpg.admin")) {
                player.sendMessage("No permission.");
                return true;
            }
            int amount = 1;
            if (args.length >= 2) {
                try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[1]))); }
                catch (NumberFormatException ignored) { player.sendMessage("Usage: /gacha ticket [amount]"); return true; }
            }
            ItemStack ticket = gacha.ticket(amount);
            player.getInventory().addItem(ticket).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage("Given " + amount + " Elite Gacha Ticket(s).");
            return true;
        }
        gacha.open(player);
        return true;
    }
}
