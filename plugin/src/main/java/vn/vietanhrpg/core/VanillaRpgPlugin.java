package vn.vietanhrpg.core;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.command.AchievementCommand;
import vn.vietanhrpg.core.command.BlueprintCommand;
import vn.vietanhrpg.core.command.CoinCommand;
import vn.vietanhrpg.core.command.DungeonCommand;
import vn.vietanhrpg.core.command.DungeonsCommand;
import vn.vietanhrpg.core.command.ForgeCommand;
import vn.vietanhrpg.core.command.MarketCommand;
import vn.vietanhrpg.core.command.ParticleCommand;
import vn.vietanhrpg.core.command.ProfileCommand;
import vn.vietanhrpg.core.command.QuestCommand;
import vn.vietanhrpg.core.command.RpgCommand;
import vn.vietanhrpg.core.command.ShopCommand;
import vn.vietanhrpg.core.command.TitleCommand;
import vn.vietanhrpg.core.command.TokenCommand;
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
import vn.vietanhrpg.core.listener.BlueprintListener;
import vn.vietanhrpg.core.listener.CombatListener;
import vn.vietanhrpg.core.listener.MarketListener;
import vn.vietanhrpg.core.listener.PlayerListener;
import vn.vietanhrpg.core.listener.ProgressionListener;
import vn.vietanhrpg.core.listener.WeaponEffectListener;
import vn.vietanhrpg.core.service.BlueprintService;
import vn.vietanhrpg.core.service.BetterStructuresLootBridge;
import vn.vietanhrpg.core.service.EconomyService;
import vn.vietanhrpg.core.service.ForgeService;
import vn.vietanhrpg.core.service.MarketService;
import vn.vietanhrpg.core.service.ParticleService;
import vn.vietanhrpg.core.service.ShopService;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("quests.yml");
        saveResourceIfMissing("dungeons.yml");
        saveResourceIfMissing("gacha.yml");
        saveResourceIfMissing("bosses.yml");
        if (!validateConfigurationFiles()) {
            getLogger().severe("Configuration validation failed. Existing files were left unchanged; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        upgradeLegacyQuestResource();
        ensureDungeonProgressionDefaults();
        upgradeLegacyDungeonResource();
        ensureOneChunkDungeonDefault();

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
        this.dungeonContentService = new DungeonContentService(this);
        this.dungeonContentService.reload();
        this.dungeonService = new DungeonService(this, database, economyService, itemService, contentService, progressionService, dungeonContentService);
        this.dungeonGuiService = new DungeonGuiService(dungeonContentService, dungeonService, database);
        this.marketService = new MarketService(this, database);
        this.marketSellGuiService = new MarketSellGuiService(marketService);
        this.marketGuiService = new MarketGuiService(marketService, marketSellGuiService);
        this.shopService = new ShopService(this, economyService, database);
        this.shopSellGuiService = new ShopSellGuiService(this, shopService);
        this.shopGuiService = new ShopGuiService(shopService, shopSellGuiService);

        registerCommand("coin", new CoinCommand(economyService));
        registerCommand("token", new TokenCommand(economyService));
        registerCommand("profile", new ProfileCommand(progressionGuiService));
        registerCommand("quests", new QuestCommand(progressionGuiService));
        registerCommand("achievements", new AchievementCommand(progressionGuiService));
        registerCommand("titles", new TitleCommand(progressionGuiService));
        registerCommand("dungeons", new DungeonsCommand(dungeonGuiService));
        registerCommand("dungeon", new DungeonCommand(dungeonContentService, dungeonService, dungeonGuiService));
        registerCommand("forge", new ForgeCommand(forgeService, itemService, forgeGuiService));
        registerCommand("blueprints", new BlueprintCommand(forgeGuiService));
        registerCommand("particles", new ParticleCommand(particleGuiService));
        registerCommand("market", new MarketCommand(marketService, marketGuiService));
        registerCommand("shop", new ShopCommand(shopGuiService, shopService));
        BetterStructuresLootBridge betterStructuresLoot = new BetterStructuresLootBridge(this, itemService, contentService);
        registerCommand("rpg", new RpgCommand(this, database, economyService, itemService, contentService,
                new MysticForestService(this), betterStructuresLoot));

        getServer().getPluginManager().registerEvents(new PlayerListener(this, database, itemService, contentService, progressionService), this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(progressionService), this);
        getServer().getPluginManager().registerEvents(new DungeonListener(dungeonService), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, itemService, contentService, particleService), this);
        getServer().getPluginManager().registerEvents(new BlueprintListener(blueprintService, itemService), this);
        getServer().getPluginManager().registerEvents(new WeaponEffectListener(particleService), this);
        getServer().getPluginManager().registerEvents(new MarketListener(marketService), this);
        getServer().getPluginManager().registerEvents(forgeGuiService, this);
        getServer().getPluginManager().registerEvents(particleGuiService, this);
        getServer().getPluginManager().registerEvents(progressionGuiService, this);
        getServer().getPluginManager().registerEvents(dungeonGuiService, this);
        getServer().getPluginManager().registerEvents(marketGuiService, this);
        getServer().getPluginManager().registerEvents(marketSellGuiService, this);
        getServer().getPluginManager().registerEvents(shopGuiService, this);
        getServer().getPluginManager().registerEvents(shopSellGuiService, this);
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
        dungeonService.start();
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


    private void ensureOneChunkDungeonDefault() {
        File target = new File(getDataFolder(), "dungeons.yml");
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(target);
            String base = "dungeons.one_chunk_dungeon";
            if (yaml.contains(base)) return;

            yaml.set(base + ".enabled", true);
            yaml.set(base + ".name", "One Chunk Dungeon");
            yaml.set(base + ".description", "A compact dungeon world used to test direct world teleporting.");
            yaml.set(base + ".icon", "DEEPSLATE_BRICKS");
            yaml.set(base + ".tier", 2);
            yaml.set(base + ".recommended-rarity", "RARE");
            yaml.set(base + ".required-rarity", "RARE");
            yaml.set(base + ".cooldown-seconds", 600);
            yaml.set(base + ".time-limit-seconds", 480);
            yaml.set(base + ".use-world-spawn", true);
            yaml.set(base + ".spawn.world", "dungeon_one_chunk");
            yaml.set(base + ".spawn.x", 0.0);
            yaml.set(base + ".spawn.y", 64.0);
            yaml.set(base + ".spawn.z", 0.0);
            yaml.set(base + ".spawn.yaw", 0.0);
            yaml.set(base + ".spawn.pitch", 0.0);
            yaml.set(base + ".mob-spawn-radius", 7.0);

            java.util.List<java.util.Map<String, Object>> mobs = new java.util.ArrayList<>();
            java.util.Map<String, Object> guard = new java.util.LinkedHashMap<>();
            guard.put("type", "ZOMBIE");
            guard.put("name", "Dungeon Guard");
            guard.put("count", 5);
            guard.put("health-multiplier", 1.45);
            guard.put("damage-multiplier", 1.20);
            guard.put("weapon", "IRON_SWORD");
            guard.put("helmet", "CHAINMAIL_HELMET");
            mobs.add(guard);
            java.util.Map<String, Object> archer = new java.util.LinkedHashMap<>();
            archer.put("type", "SKELETON");
            archer.put("name", "Bone Archer");
            archer.put("count", 4);
            archer.put("health-multiplier", 1.25);
            archer.put("damage-multiplier", 1.15);
            archer.put("helmet", "CHAINMAIL_HELMET");
            mobs.add(archer);
            java.util.Map<String, Object> brute = new java.util.LinkedHashMap<>();
            brute.put("type", "HUSK");
            brute.put("name", "Dungeon Brute");
            brute.put("count", 2);
            brute.put("health-multiplier", 1.75);
            brute.put("damage-multiplier", 1.30);
            brute.put("weapon", "IRON_AXE");
            brute.put("chestplate", "CHAINMAIL_CHESTPLATE");
            mobs.add(brute);
            yaml.set(base + ".mobs", mobs);

            yaml.set(base + ".rewards.coin.min", 700);
            yaml.set(base + ".rewards.coin.max", 1200);
            yaml.set(base + ".rewards.token.min", 1);
            yaml.set(base + ".rewards.token.max", 2);
            yaml.set(base + ".rewards.custom-items.epic_shard.chance", 0.55);
            yaml.set(base + ".rewards.custom-items.epic_shard.min", 1);
            yaml.set(base + ".rewards.custom-items.epic_shard.max", 2);
            yaml.set(base + ".rewards.custom-items.legendary_shard.chance", 0.05);
            yaml.set(base + ".rewards.custom-items.legendary_shard.min", 1);
            yaml.set(base + ".rewards.custom-items.legendary_shard.max", 1);
            yaml.save(target);
            getLogger().info("Added One Chunk Dungeon world preset to dungeons.yml.");
        } catch (Exception exception) {
            getLogger().warning("Could not add One Chunk Dungeon preset: " + exception.getMessage());
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
