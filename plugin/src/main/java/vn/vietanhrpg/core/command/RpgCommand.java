package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.VanillaRpgPlugin;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.world.MysticForestService;
import vn.vietanhrpg.core.progression.ProgressionService;

import java.util.Locale;

public final class RpgCommand implements CommandExecutor {
    private final VanillaRpgPlugin plugin;
    private final Database database;
    private final MysticForestService mysticForest;
    private final ProgressionService progression;

    public RpgCommand(VanillaRpgPlugin plugin, Database database,
                      MysticForestService mysticForest,
                      ProgressionService progression) {
        this.plugin = plugin;
        this.database = database;
        this.mysticForest = mysticForest;
        this.progression = progression;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vanillarpg.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> usage(sender);
            case "version", "info" -> sender.sendMessage(Component.text(
                    "VanillaRPG-Core " + plugin.getPluginMeta().getVersion() + " is enabled.", NamedTextColor.GREEN));
            case "mysticforest" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This test world is player-only.", NamedTextColor.RED));
                    return true;
                }
                mysticForest.open(player);
                return true;
            }
            case "reload" -> {
                if (plugin.reloadAll()) {
                    sender.sendMessage(Component.text("VanillaRPG " + plugin.getPluginMeta().getVersion()
                            + " config/content reloaded.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Reload cancelled because a YAML file is invalid. Check the server log.", NamedTextColor.RED));
                }
                return true;
            }
            default -> {
                usage(sender);
                return true;
            }
        }
        return true;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("/rpg help", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg version", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg reload", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rpg mysticforest", NamedTextColor.YELLOW));
    }
}
