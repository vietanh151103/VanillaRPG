package vn.vietanhrpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.gui.ProgressionGuiService;

public final class QuestCommand implements CommandExecutor {
    private final ProgressionGuiService gui;
    public QuestCommand(ProgressionGuiService gui) { this.gui = gui; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        gui.openQuests(player); return true;
    }
}
