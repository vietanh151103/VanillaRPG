package vn.vietanhrpg.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.vietanhrpg.core.dungeon.DungeonContentService;
import vn.vietanhrpg.core.dungeon.DungeonDefinition;
import vn.vietanhrpg.core.dungeon.DungeonGuiService;
import vn.vietanhrpg.core.dungeon.DungeonRun;
import vn.vietanhrpg.core.dungeon.DungeonService;

import java.util.Locale;

public final class DungeonCommand implements CommandExecutor {
    private final DungeonContentService content;
    private final DungeonService service;
    private final DungeonGuiService gui;

    public DungeonCommand(DungeonContentService content, DungeonService service, DungeonGuiService gui) {
        this.content = content;
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Player-only command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            gui.open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> {
                if (args.length != 2) {
                    player.sendMessage(Component.text("Usage: /dungeon join <id>", NamedTextColor.YELLOW));
                    return true;
                }
                service.join(player, args[1]);
                return true;
            }
            case "leave" -> {
                service.leave(player);
                return true;
            }
            case "status" -> {
                DungeonRun run = service.activeRun(player.getUniqueId());
                if (run == null) {
                    player.sendMessage(Component.text("You are not inside a dungeon.", NamedTextColor.GRAY));
                    return true;
                }
                DungeonDefinition definition = content.dungeon(run.dungeonId());
                long remaining = Math.max(0, (run.deadlineAt() - System.currentTimeMillis() + 999L) / 1000L);
                player.sendMessage(Component.text(
                        (definition == null ? run.dungeonId() : definition.name())
                                + ": " + run.kills() + "/" + run.targetKills()
                                + " mobs, " + service.formatTime(remaining) + " remaining.",
                        NamedTextColor.AQUA));
                return true;
            }
            case "setspawn" -> {
                if (!player.hasPermission("vanillarpg.dungeon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length != 2 || content.dungeon(args[1]) == null) {
                    player.sendMessage(Component.text("Usage: /dungeon setspawn <dungeon-id>", NamedTextColor.YELLOW));
                    return true;
                }
                if (!content.setSpawn(args[1], player.getLocation())) {
                    player.sendMessage(Component.text("Could not save dungeon spawn.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text("Dungeon spawn saved and enabled for " + args[1] + ".", NamedTextColor.GREEN));
                return true;
            }
            case "resetcooldown" -> {
                if (!player.hasPermission("vanillarpg.dungeon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length != 2 || content.dungeon(args[1]) == null) {
                    player.sendMessage(Component.text("Usage: /dungeon resetcooldown <dungeon-id>", NamedTextColor.YELLOW));
                    return true;
                }
                service.resetCooldown(player.getUniqueId(), args[1]);
                player.sendMessage(Component.text("Cooldown reset for " + args[1] + ".", NamedTextColor.GREEN));
                return true;
            }
            case "reload" -> {
                if (!player.hasPermission("vanillarpg.dungeon.admin")) {
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                content.reload();
                player.sendMessage(Component.text("dungeons.yml reloaded.", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                usage(player);
                return true;
            }
        }
    }

    private void usage(Player player) {
        player.sendMessage(Component.text("/dungeons", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/dungeon join <id>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/dungeon leave", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/dungeon status", NamedTextColor.YELLOW));
        if (player.hasPermission("vanillarpg.dungeon.admin")) {
            player.sendMessage(Component.text("/dungeon setspawn <id>", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/dungeon resetcooldown <id>", NamedTextColor.GRAY));
            player.sendMessage(Component.text("/dungeon reload", NamedTextColor.GRAY));
        }
    }
}
