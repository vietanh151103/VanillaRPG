package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.dungeon.DungeonGuiService;

public final class DungeonsCommand implements CommandExecutor {
    private final DungeonGuiService gui;

    public DungeonsCommand(DungeonGuiService gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Player-only command.", NamedTextColor.RED));
            return true;
        }
        gui.open(player);
        return true;
    }
}
