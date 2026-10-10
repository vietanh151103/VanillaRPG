package vn.vietanhrpg.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.gui.ForgeGuiService;

public final class BlueprintCommand implements CommandExecutor {
    private final ForgeGuiService forgeGui;

    public BlueprintCommand(ForgeGuiService forgeGui) {
        this.forgeGui = forgeGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        forgeGui.openRecipes(player);
        return true;
    }
}
