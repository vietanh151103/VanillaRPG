package vn.vietanhrpg.core;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.command.CoinCommand;
import vn.vietanhrpg.core.command.ForgeCommand;
import vn.vietanhrpg.core.command.ProfileCommand;
import vn.vietanhrpg.core.command.RpgCommand;
import vn.vietanhrpg.core.command.TokenCommand;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.listener.CombatListener;
import vn.vietanhrpg.core.listener.PlayerListener;
import vn.vietanhrpg.core.service.EconomyService;
import vn.vietanhrpg.core.service.ForgeService;

import java.io.File;

public final class VanillaRpgPlugin extends JavaPlugin {
    private Database database;
    private EconomyService economyService;
    private ItemService itemService;
    private ForgeService forgeService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("quests.yml");
        saveResourceIfMissing("dungeons.yml");
        saveResourceIfMissing("gacha.yml");
        saveResourceIfMissing("bosses.yml");
        saveResourceIfMissing("items.yml");

        try {
            this.database = new Database(this);
            this.database.open();
        } catch (Exception exception) {
            getLogger().severe("Could not open rpg.db. Plugin will be disabled.");
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.economyService = new EconomyService(this, database);
        this.itemService = new ItemService(this);
        this.forgeService = new ForgeService(this, database, economyService, itemService);

        registerCommand("coin", new CoinCommand(economyService));
        registerCommand("token", new TokenCommand(economyService));
        registerCommand("profile", new ProfileCommand(database, economyService));
        registerCommand("forge", new ForgeCommand(forgeService, itemService));
        registerCommand("rpg", new RpgCommand(this, database, economyService, itemService));

        getServer().getPluginManager().registerEvents(new PlayerListener(this, database), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, itemService), this);

        getServer().getOnlinePlayers().forEach(player -> database.ensurePlayer(
                player.getUniqueId(),
                player.getName(),
                getConfig().getLong("starting-balance.coin", 250),
                getConfig().getLong("starting-balance.token", 0)
        ));

        getLogger().info("VanillaRPG-Core 0.1.0 enabled. Data: " + new File(getDataFolder(), "rpg.db").getPath());
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command missing from plugin.yml: " + name);
        }
        command.setExecutor(executor);
    }

    private void saveResourceIfMissing(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.exists()) {
            saveResource(name, false);
        }
    }

    public Database database() {
        return database;
    }

    public EconomyService economy() {
        return economyService;
    }

    public ItemService items() {
        return itemService;
    }
}
