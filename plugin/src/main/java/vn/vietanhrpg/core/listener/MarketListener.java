package vn.vietanhrpg.core.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import vn.vietanhrpg.core.service.MarketService;

public final class MarketListener implements Listener {
    private final MarketService market;

    public MarketListener(MarketService market) {
        this.market = market;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!market.enabled()) return;
        int pending = market.pendingCount(event.getPlayer().getUniqueId());
        if (pending > 0) {
            event.getPlayer().sendMessage(Component.text("You have " + pending
                    + " pending market delivery item(s). Use /market claim.", NamedTextColor.GOLD));
        }
    }
}
