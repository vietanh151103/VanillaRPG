package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.service.EconomyService;

public final class ProfileCommand implements CommandExecutor {
    private final Database database;
    private final EconomyService economy;

    public ProfileCommand(Database database, EconomyService economy) {
        this.database = database;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        economy.ensure(player);
        var uuid = player.getUniqueId();
        player.sendMessage(Component.text("==== " + player.getName() + " RPG Profile ====", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Coin: " + economy.coin(uuid), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Token: " + economy.token(uuid), NamedTextColor.AQUA));
        player.sendMessage(Component.text("Rare crafted: " + database.getStat(uuid, "rare_crafted"), NamedTextColor.BLUE));
        player.sendMessage(Component.text("Epic crafted: " + database.getStat(uuid, "epic_crafted"), NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("Legendary crafted: " + database.getStat(uuid, "legendary_crafted"), NamedTextColor.GOLD));
        return true;
    }
}
