package vn.vietanhrpg.core.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.data.Database;

import java.util.UUID;

public final class EconomyService {
    private final JavaPlugin plugin;
    private final Database database;

    public EconomyService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void ensure(Player player) {
        database.ensurePlayer(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfig().getLong("starting-balance.coin", 250),
                plugin.getConfig().getLong("starting-balance.token", 0)
        );
    }

    public long coin(UUID uuid) {
        return database.getCoin(uuid);
    }

    public long token(UUID uuid) {
        return database.getToken(uuid);
    }

    public boolean takeCoin(UUID uuid, long amount) {
        if (amount < 0) {
            return false;
        }
        long current = coin(uuid);
        if (current < amount) {
            return false;
        }
        database.setCoin(uuid, current - amount);
        return true;
    }

    public boolean takeToken(UUID uuid, long amount) {
        if (amount < 0) {
            return false;
        }
        long current = token(uuid);
        if (current < amount) {
            return false;
        }
        database.setToken(uuid, current - amount);
        return true;
    }

    public void addCoin(UUID uuid, long amount) {
        database.setCoin(uuid, Math.max(0, coin(uuid) + amount));
    }

    public void addToken(UUID uuid, long amount) {
        database.setToken(uuid, Math.max(0, token(uuid) + amount));
    }

    public boolean transferCoin(UUID from, UUID to, long amount) {
        return database.transferCoin(from, to, amount);
    }
}
