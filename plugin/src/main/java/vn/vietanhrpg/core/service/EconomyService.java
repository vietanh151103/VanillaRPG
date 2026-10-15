package vn.vietanhrpg.core.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.magmaguy.elitemobs.economy.EconomyHandler;
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
        if (!EconomyHandler.isReady(uuid)) {
            return 0L;
        }
        return Math.max(0L, Math.round(EconomyHandler.checkCurrency(uuid)));
    }

    public long token(UUID uuid) {
        return database.getToken(uuid);
    }

    public boolean takeCoin(UUID uuid, long amount) {
        if (amount < 0 || !EconomyHandler.isReady(uuid)) {
            return false;
        }
        return EconomyHandler.tryWithdraw(uuid, amount);
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
        if (amount > 0 && EconomyHandler.isReady(uuid)) {
            EconomyHandler.addCurrency(uuid, amount);
        }
    }

    public void addCoin(UUID uuid, double amount) {
        if (amount > 0 && EconomyHandler.isReady(uuid)) {
            EconomyHandler.addCurrency(uuid, amount);
        }
    }

    public void addToken(UUID uuid, long amount) {
        database.setToken(uuid, Math.max(0, token(uuid) + amount));
    }

    public boolean transferCoin(UUID from, UUID to, long amount) {
        if (amount < 0 || !EconomyHandler.isReady(from) || !EconomyHandler.isReady(to)) {
            return false;
        }
        if (!EconomyHandler.tryWithdraw(from, amount)) {
            return false;
        }
        EconomyHandler.addCurrency(to, amount);
        return true;
    }
}
