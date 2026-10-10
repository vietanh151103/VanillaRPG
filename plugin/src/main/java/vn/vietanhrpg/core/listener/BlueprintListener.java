package vn.vietanhrpg.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.service.BlueprintService;

public final class BlueprintListener implements Listener {
    private final BlueprintService blueprints;
    private final ItemService items;

    public BlueprintListener(BlueprintService blueprints, ItemService items) {
        this.blueprints = blueprints;
        this.items = items;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (items.blueprintId(event.getItem()) == null) {
            return;
        }
        event.setCancelled(true);
        blueprints.learn(event.getPlayer(), items.blueprintId(event.getItem()), event.getItem());
    }
}
