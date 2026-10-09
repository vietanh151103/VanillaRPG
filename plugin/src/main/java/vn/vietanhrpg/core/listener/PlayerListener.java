package vn.vietanhrpg.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.data.Database;

public final class PlayerListener implements Listener {
    private final JavaPlugin plugin;
    private final Database database;

    public PlayerListener(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        database.ensurePlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                plugin.getConfig().getLong("starting-balance.coin", 250),
                plugin.getConfig().getLong("starting-balance.token", 0)
        );
    }
}
