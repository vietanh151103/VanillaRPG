package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.service.ForgeService;

public final class ForgeCommand implements CommandExecutor {
    private final ForgeService forge;
    private final ItemService items;

    public ForgeCommand(ForgeService forge, ItemService items) {
        this.forge = forge;
        this.items = items;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /forge <upgrade|reroll|inspect>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "upgrade" -> forge.upgrade(player);
            case "reroll" -> forge.reroll(player);
            case "inspect" -> items.inspect(player, player.getInventory().getItemInMainHand());
            default -> player.sendMessage(Component.text("Usage: /forge <upgrade|reroll|inspect>", NamedTextColor.YELLOW));
        }
        return true;
    }
}
