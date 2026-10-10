package vn.vietanhrpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.gui.ProgressionGuiService;

public final class AchievementCommand implements CommandExecutor {
    private final ProgressionGuiService gui;
    public AchievementCommand(ProgressionGuiService gui) { this.gui = gui; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        gui.openAchievements(player); return true;
    }
}
