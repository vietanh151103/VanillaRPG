package vn.vietanhrpg.core;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.command.AchievementCommand;
import vn.vietanhrpg.core.command.DungeonCommand;
import vn.vietanhrpg.core.command.DungeonsCommand;
import vn.vietanhrpg.core.command.GachaCommand;
import vn.vietanhrpg.core.command.MarketCommand;
import vn.vietanhrpg.core.command.ParticleCommand;
import vn.vietanhrpg.core.command.PokerCommand;
import vn.vietanhrpg.core.command.ProfileCommand;
import vn.vietanhrpg.core.command.QuestCommand;
import vn.vietanhrpg.core.command.RpgCommand;
import vn.vietanhrpg.core.command.ShopCommand;
import vn.vietanhrpg.core.command.TitleCommand;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.dungeon.DungeonContentService;
import vn.vietanhrpg.core.dungeon.DungeonGuiService;
import vn.vietanhrpg.core.dungeon.DungeonListener;
import vn.vietanhrpg.core.dungeon.DungeonService;
import vn.vietanhrpg.core.gui.ForgeGuiService;
import vn.vietanhrpg.core.gui.MarketGuiService;
import vn.vietanhrpg.core.gui.MarketSellGuiService;
import vn.vietanhrpg.core.gui.ParticleGuiService;
import vn.vietanhrpg.core.gui.ProgressionGuiService;
import vn.vietanhrpg.core.gui.ShopGuiService;
import vn.vietanhrpg.core.gui.ShopSellGuiService;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.listener.CombatListener;
import vn.vietanhrpg.core.listener.MarketListener;
import vn.vietanhrpg.core.listener.PlayerListener;
import vn.vietanhrpg.core.listener.ProgressionListener;
import vn.vietanhrpg.core.listener.WeaponEffectListener;
import vn.vietanhrpg.core.service.BlueprintService;
import vn.vietanhrpg.core.service.BetterStructuresLootBridge;
import vn.vietanhrpg.core.service.EconomyService;
import vn.vietanhrpg.core.service.ForgeService;
import vn.vietanhrpg.core.service.GachaService;
import vn.vietanhrpg.core.service.MarketService;
import vn.vietanhrpg.core.service.MobHealthDisplayService;
import vn.vietanhrpg.core.service.ParticleService;
import vn.vietanhrpg.core.service.PokerTableService;
import vn.vietanhrpg.core.service.ShopService;
import vn.vietanhrpg.core.service.VanillaLootBridge;
import vn.vietanhrpg.core.progression.ProgressionContentService;
import vn.vietanhrpg.core.progression.ProgressionService;
import vn.vietanhrpg.core.world.MysticForestService;

import java.io.File;
import java.io.IOException;

public final class VanillaRpgPlugin extends JavaPlugin {
    private Database database;
    private EconomyService economyService;
    private ItemService itemService;
    private ContentService contentService;
    private BlueprintService blueprintService;
    private ForgeService forgeService;
    private ParticleService particleService;
    private ForgeGuiService forgeGuiService;
    private ParticleGuiService particleGuiService;
    private ProgressionContentService progressionContentService;
    private ProgressionService progressionService;
    private ProgressionGuiService progressionGuiService;
    private DungeonContentService dungeonContentService;
    private DungeonService dungeonService;
    private DungeonGuiService dungeonGuiService;
    private MarketService marketService;
    private MarketGuiService marketGuiService;
    private MarketSellGuiService marketSellGuiService;
    private ShopService shopService;
    private ShopGuiService shopGuiService;
    private ShopSellGuiService shopSellGuiService;
    private MobHealthDisplayService mobHealthDisplayService;
    private PokerTableService pokerTableService;
    private GachaService gachaService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("quests.yml");
        saveResourceIfMissing("dungeons.yml");
        saveResourceIfMissing("gacha.yml");
        saveResourceIfMissing("bosses.yml");
        saveResourceIfMissing("schematics/README.txt");
        if (!validateConfigurationFiles()) {
            getLogger().severe("Configuration validation failed. Existing files were left unchanged; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        upgradeLegacyQuestResource();
        upgradeLegacyDungeonResource();

        try {
            this.database = new Database(this);
            this.database.open();
        } catch (Exception exception) {
            getLogger().severe("Could not open rpg.db. Plugin will be disabled.");
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("EliteMobs") == null) {
            getLogger().severe("EliteMobs is required because VanillaRPG-Core now uses Elite Coins.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.economyService = new EconomyService(this, database);
        this.itemService = new ItemService(this);
        this.contentService = new ContentService(this);
        this.contentService.reload();
        this.blueprintService = new BlueprintService(database, contentService, itemService);
        this.forgeService = new ForgeService(this, database, economyService, itemService, contentService, blueprintService);
        this.particleService = new ParticleService(this, database, itemService);
        this.forgeService.setParticleService(particleService);
        this.forgeGuiService = new ForgeGuiService(forgeService, itemService, contentService, database);
        this.particleGuiService = new ParticleGuiService(database);
        this.progressionContentService = new ProgressionContentService(this);
        this.progressionContentService.reload();
        this.progressionService = new ProgressionService(this, database, economyService, itemService, contentService, progressionContentService);
        this.progressionGuiService = new ProgressionGuiService(database, economyService, progressionService, progressionContentService);
        this.forgeService.setProgressionService(progressionService);
        boolean dungeonsEnabled = getConfig().getBoolean("dungeons.enabled", false);
        if (dungeonsEnabled) {
            this.dungeonContentService = new DungeonContentService(this);
            this.dungeonContentService.reload();
            this.dungeonService = new DungeonService(this, database, economyService, itemService, contentService, progressionService, dungeonContentService);
            this.dungeonGuiService = new DungeonGuiService(dungeonContentService, dungeonService, database);
        }
        this.marketService = new MarketService(this, database);
        this.marketSellGuiService = new MarketSellGuiService(marketService);
        this.marketGuiService = new MarketGuiService(marketService, marketSellGuiService);
        this.shopService = new ShopService(this, economyService, database);
        this.shopSellGuiService = new ShopSellGuiService(this, shopService);
        this.gachaService = new GachaService(this, database, economyService, itemService, contentService, progressionService);
        this.shopGuiService = new ShopGuiService(shopService, shopSellGuiService, gachaService);
        this.mobHealthDisplayService = new MobHealthDisplayService(this);
        this.pokerTableService = new PokerTableService(this);

        registerCommand("profile", new ProfileCommand(progressionGuiService));
        registerCommand("quests", new QuestCommand(progressionGuiService));
        registerCommand("achievements", new AchievementCommand(progressionGuiService));
        registerCommand("titles", new TitleCommand(progressionGuiService));
        if (dungeonsEnabled) {
            registerCommand("dungeons", new DungeonsCommand(dungeonGuiService));
            registerCommand("dungeon", new DungeonCommand(dungeonContentService, dungeonService, dungeonGuiService));
        }
        registerCommand("particles", new ParticleCommand(particleGuiService));
        registerCommand("market", new MarketCommand(marketService, marketGuiService));
        registerCommand("shop", new ShopCommand(shopGuiService, shopService));
        registerCommand("poker", new PokerCommand(pokerTableService));
        registerCommand("gacha", new GachaCommand(gachaService));
        BetterStructuresLootBridge betterStructuresLoot = new BetterStructuresLootBridge(this, itemService, contentService);
        VanillaLootBridge vanillaLoot = new VanillaLootBridge(this, itemService, contentService);
        registerCommand("rpg", new RpgCommand(this, database, economyService, itemService, contentService,
                new MysticForestService(this), betterStructuresLoot, progressionService));

        getServer().getPluginManager().registerEvents(new PlayerListener(this, database, itemService, contentService, progressionService), this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(progressionService), this);
        if (dungeonsEnabled) getServer().getPluginManager().registerEvents(new DungeonListener(dungeonService), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, itemService, contentService,
                particleService, mobHealthDisplayService), this);
        getServer().getPluginManager().registerEvents(new WeaponEffectListener(particleService), this);
        getServer().getPluginManager().registerEvents(new MarketListener(marketService), this);
        getServer().getPluginManager().registerEvents(particleGuiService, this);
        getServer().getPluginManager().registerEvents(progressionGuiService, this);
        if (dungeonsEnabled) getServer().getPluginManager().registerEvents(dungeonGuiService, this);
        getServer().getPluginManager().registerEvents(marketGuiService, this);
        getServer().getPluginManager().registerEvents(marketSellGuiService, this);
        getServer().getPluginManager().registerEvents(shopGuiService, this);
        getServer().getPluginManager().registerEvents(shopSellGuiService, this);
        getServer().getPluginManager().registerEvents(mobHealthDisplayService, this);
        getServer().getPluginManager().registerEvents(pokerTableService, this);
        getServer().getPluginManager().registerEvents(gachaService, this);
        getServer().getPluginManager().registerEvents(vanillaLoot, this);
        betterStructuresLoot.registerIfAvailable();

        getServer().getOnlinePlayers().forEach(player -> {
            database.ensurePlayer(
                    player.getUniqueId(),
                    player.getName(),
                    getConfig().getLong("starting-balance.coin", 250),
                    getConfig().getLong("starting-balance.token", 0)
            );
            progressionService.ensure(player);
            progressionService.applyTitleDisplay(player);
        });

        particleService.start();
        if (dungeonService != null) dungeonService.start();
        mobHealthDisplayService.start();
        getLogger().info("VanillaRPG-Core " + getPluginMeta().getVersion()
                + " enabled. Data: " + new File(getDataFolder(), "rpg.db").getPath());
    }

    @Override
    public void onDisable() {
        if (particleService != null) {
            particleService.stop();
        }
        if (dungeonService != null) {
            dungeonService.stop();
        }
        if (mobHealthDisplayService != null) {
            mobHealthDisplayService.stop();
        }
        if (pokerTableService != null) {
            pokerTableService.shutdown();
        }
        if (progressionService != null) {
            progressionService.clearTitleNameTags();
        }
        if (database != null) {
            database.close();
        }
    }

    public boolean reloadAll() {
        if (!validateConfigurationFiles()) {
            getLogger().severe("Reload cancelled because one or more YAML files are invalid. Active data was not replaced.");
            return false;
        }
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (contentService != null) {
            contentService.reload();
        }
        if (progressionContentService != null) {
            progressionContentService.reload();
        }
        if (dungeonContentService != null) {
            dungeonContentService.reload();
        }
        if (gachaService != null) {
            gachaService.reload();
        }
        if (particleService != null) {
            particleService.start();
        }
        return true;
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command missing from plugin.yml: " + name);
        }
        command.setExecutor(executor);
    }

    private void upgradeLegacyQuestResource() {
        File target = new File(getDataFolder(), "quests.yml");
        try {
            String text = java.nio.file.Files.readString(target.toPath());
            if (text.contains("Reserved for V2") || (!text.contains("daily:") && !text.contains("weekly:"))) {
                saveResource("quests.yml", true);
                getLogger().info("Upgraded legacy placeholder quests.yml to the 0.3 progression config.");
            }
        } catch (Exception exception) {
            getLogger().warning("Could not inspect quests.yml for migration: " + exception.getMessage());
        }
    }

    private void ensureDungeonProgressionDefaults() {
        File target = new File(getDataFolder(), "quests.yml");
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target);
            boolean changed = false;

            if (!yaml.contains("daily.dungeon_delver")) {
                yaml.set("daily.dungeon_delver.name", "Dungeon Delver");
                yaml.set("daily.dungeon_delver.description", "Clear one dungeon.");
                yaml.set("daily.dungeon_delver.type", "DUNGEON_CLEAR");
                yaml.set("daily.dungeon_delver.target", 1);
                yaml.set("daily.dungeon_delver.reward.coin", 400);
                yaml.set("daily.dungeon_delver.reward.token", 1);
                changed = true;
            }
            if (!yaml.contains("weekly.dungeon_runner")) {
                yaml.set("weekly.dungeon_runner.name", "Dungeon Runner");
                yaml.set("weekly.dungeon_runner.description", "Clear 5 dungeons this week.");
                yaml.set("weekly.dungeon_runner.type", "DUNGEON_CLEAR");
                yaml.set("weekly.dungeon_runner.target", 5);
                yaml.set("weekly.dungeon_runner.reward.coin", 2200);
                yaml.set("weekly.dungeon_runner.reward.token", 3);
                yaml.set("weekly.dungeon_runner.reward.custom-materials.epic_shard", 2);
                changed = true;
            }
            if (!yaml.contains("achievements.first_dungeon")) {
                yaml.set("achievements.first_dungeon.name", "First Delve");
                yaml.set("achievements.first_dungeon.description", "Clear your first dungeon.");
                yaml.set("achievements.first_dungeon.counter", "dungeon_clears");
                yaml.set("achievements.first_dungeon.target", 1);
                yaml.set("achievements.first_dungeon.reward.coin", 500);
                yaml.set("achievements.first_dungeon.reward.token", 1);
                yaml.set("achievements.first_dungeon.reward.title", "delver");
                changed = true;
            }
            if (!yaml.contains("achievements.veteran_delver")) {
                yaml.set("achievements.veteran_delver.name", "Veteran Delver");
                yaml.set("achievements.veteran_delver.description", "Clear 25 dungeons.");
                yaml.set("achievements.veteran_delver.counter", "dungeon_clears");
                yaml.set("achievements.veteran_delver.target", 25);
                yaml.set("achievements.veteran_delver.reward.coin", 5000);
                yaml.set("achievements.veteran_delver.reward.token", 8);
                yaml.set("achievements.veteran_delver.reward.title", "dungeon_veteran");
                changed = true;
            }
            if (!yaml.contains("titles.delver")) {
                yaml.set("titles.delver.name", "Delver");
                yaml.set("titles.delver.color", "DARK_AQUA");
                changed = true;
            }
            if (!yaml.contains("titles.dungeon_veteran")) {
                yaml.set("titles.dungeon_veteran.name", "Dungeon Veteran");
                yaml.set("titles.dungeon_veteran.color", "GOLD");
                changed = true;
            }
            if (changed) {
                yaml.save(target);
                getLogger().info("Merged 0.4 dungeon quests/achievements into quests.yml.");
            }
        } catch (Exception exception) {
            getLogger().warning("Could not merge 0.4 dungeon progression defaults: " + exception.getMessage());
        }
    }

    private void upgradeLegacyDungeonResource() {
        File target = new File(getDataFolder(), "dungeons.yml");
        try {
            String text = java.nio.file.Files.readString(target.toPath());
            if (text.contains("Reserved for V3") || !text.contains("dungeons:")) {
                saveResource("dungeons.yml", true);
                getLogger().info("Upgraded legacy placeholder dungeons.yml to the 0.4 dungeon config.");
            }
        } catch (Exception exception) {
            getLogger().warning("Could not inspect dungeons.yml for migration: " + exception.getMessage());
        }
    }


    private void saveResourceIfMissing(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.exists()) {
            saveResource(name, false);
        }
    }

    private boolean validateConfigurationFiles() {
        boolean valid = true;
        for (String name : java.util.List.of(
                "config.yml", "items.yml", "quests.yml", "dungeons.yml", "gacha.yml", "bosses.yml")) {
            valid &= validateYaml(name);
        }
        return valid;
    }

    private boolean validateYaml(String name) {
        File target = new File(getDataFolder(), name);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(target);
            return true;
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().severe("Invalid " + name + ": " + exception.getMessage());
            return false;
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

    public ContentService content() {
        return contentService;
    }
}
