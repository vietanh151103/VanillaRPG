package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.gui.ParticleGuiService;

import java.util.Set;

public final class ParticleCommand implements CommandExecutor {
    private static final Set<String> SETTINGS = Set.of("legendary", "mythic", "combat", "craft", "materials", "others");
    private final ParticleGuiService gui;

    public ParticleCommand(ParticleGuiService gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            gui.open(player);
            return true;
        }
        if (args.length == 2) {
            String setting = args[0].toLowerCase();
            String value = args[1].toLowerCase();
            if (!value.equals("on") && !value.equals("off")) {
                usage(player);
                return true;
            }
            boolean enabled = value.equals("on");
            if (setting.equals("all")) {
                gui.setAll(player, enabled);
                player.sendMessage(Component.text("All RPG particles: " + value.toUpperCase(),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }
            if (SETTINGS.contains(setting)) {
                gui.set(player, setting, enabled);
                player.sendMessage(Component.text(setting + " particles: " + value.toUpperCase(),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }
        }
        usage(player);
        return true;
    }

    private void usage(Player player) {
        player.sendMessage(Component.text("/particles", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/particles <all|legendary|mythic|combat|craft|materials|others> <on|off>", NamedTextColor.YELLOW));
    }
}
