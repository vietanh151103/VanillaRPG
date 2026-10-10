package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.vietanhrpg.core.content.BlueprintDefinition;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;

import java.util.Set;

public final class BlueprintService {
    private final Database database;
    private final ContentService content;
    private final ItemService items;

    public BlueprintService(Database database, ContentService content, ItemService items) {
        this.database = database;
        this.content = content;
        this.items = items;
    }

    public boolean has(Player player, String blueprintId) {
        return database.hasBlueprint(player.getUniqueId(), blueprintId);
    }

    public Set<String> learned(Player player) {
        return database.blueprints(player.getUniqueId());
    }

    public int count(Player player) {
        return database.blueprintCount(player.getUniqueId());
    }

    public boolean learnFromHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String blueprintId = items.blueprintId(held);
        if (blueprintId == null) {
            return false;
        }
        return learn(player, blueprintId, held);
    }

    public boolean learn(Player player, String blueprintId, ItemStack source) {
        BlueprintDefinition definition = content.blueprint(blueprintId);
        if (definition == null) {
            player.sendMessage(Component.text("Unknown blueprint: " + blueprintId, NamedTextColor.RED));
            return false;
        }
        if (!database.learnBlueprint(player.getUniqueId(), definition.id())) {
            player.sendMessage(Component.text("You already know " + definition.name() + ".", NamedTextColor.YELLOW));
            return false;
        }

        if (source != null) {
            if (source.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                source.setAmount(source.getAmount() - 1);
            }
        }
        player.sendMessage(Component.text("Blueprint learned: ", NamedTextColor.GREEN)
                .append(Component.text(definition.name(), definition.color())));
        return true;
    }
}
