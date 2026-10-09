package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.service.EconomyService;

public final class TokenCommand implements CommandExecutor {
    private final EconomyService economy;

    public TokenCommand(EconomyService economy) {
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        economy.ensure(player);
        player.sendMessage(Component.text("Token: " + economy.token(player.getUniqueId()), NamedTextColor.AQUA));
        return true;
    }
}
