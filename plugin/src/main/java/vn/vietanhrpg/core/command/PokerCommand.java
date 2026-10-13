package vn.vietanhrpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.vietanhrpg.core.service.PokerTableService;

public final class PokerCommand implements CommandExecutor {
    private final PokerTableService poker;

    public PokerCommand(PokerTableService poker) {
        this.poker = poker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can play poker.");
            return true;
        }
        poker.command(player, args);
        return true;
    }
}
