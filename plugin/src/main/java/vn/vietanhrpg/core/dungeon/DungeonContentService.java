package vn.vietanhrpg.core.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.item.Rarity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DungeonContentService {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, DungeonDefinition> dungeons = new LinkedHashMap<>();

    public DungeonContentService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dungeons.yml");
    }

    public void reload() {
        dungeons.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("dungeons");
        if (root == null) return;

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            try {
                DungeonDefinition definition = parse(rawId, section);
                dungeons.put(definition.id(), definition);
            } catch (Exception exception) {
                plugin.getLogger().warning("Skipping dungeon '" + rawId + "': " + exception.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + dungeons.size() + " dungeon definitions.");
    }


    public DungeonDefinition dungeon(String id) {
        if (id == null) return null;
        return dungeons.get(normalize(id));
    }

    public Collection<DungeonDefinition> dungeons() {
        return List.copyOf(dungeons.values());
    }

    public boolean setSpawn(String id, Location location) {
        String key = normalize(id);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("dungeons." + key);
        if (section == null) return false;
        String base = "dungeons." + key + ".spawn.";
        yaml.set(base + "world", location.getWorld().getName());
        yaml.set(base + "x", location.getX());
        yaml.set(base + "y", location.getY());
        yaml.set(base + "z", location.getZ());
        yaml.set(base + "yaw", location.getYaw());
        yaml.set(base + "pitch", location.getPitch());
        yaml.set("dungeons." + key + ".use-world-spawn", false);
        yaml.set("dungeons." + key + ".enabled", true);
        try {
            yaml.save(file);
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save dungeons.yml: " + exception.getMessage());
            return false;
        }
    }

    private DungeonDefinition parse(String rawId, ConfigurationSection section) {
        String id = normalize(rawId);
        String name = section.getString("name", pretty(id));
        String description = section.getString("description", "VanillaRPG dungeon");
        Material icon = material(section.getString("icon"), Material.IRON_SWORD);
        int tier = Math.max(1, section.getInt("tier", 1));
        boolean enabled = section.getBoolean("enabled", false);
        Rarity recommended = rarity(section.getString("recommended-rarity"), Rarity.UNCOMMON);
        Rarity required = rarity(section.getString("required-rarity"), Rarity.COMMON);
        long cooldown = Math.max(0, section.getLong("cooldown-seconds", 300));
        long timeLimit = Math.max(30, section.getLong("time-limit-seconds", 300));
        DungeonLocation spawn = DungeonLocation.from(section.getConfigurationSection("spawn"));
        boolean useWorldSpawn = section.getBoolean("use-world-spawn", false);
        double radius = Math.max(1.0, section.getDouble("mob-spawn-radius", 7.0));

        List<DungeonMobDefinition> mobs = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("mobs")) {
            EntityType type = entityType(value(map, "type", "ZOMBIE"));
            if (type == null || !type.isAlive() || !type.isSpawnable()) continue;
            String mobName = value(map, "name", pretty(type.name()));
            int count = Math.max(1, integer(map.get("count"), 1));
            double health = Math.max(0.1, decimal(map.get("health-multiplier"), 1.0));
            double damage = Math.max(0.1, decimal(map.get("damage-multiplier"), 1.0));
            mobs.add(new DungeonMobDefinition(
                    type, mobName, count, health, damage,
                    materialNullable(map.get("weapon")),
                    materialNullable(map.get("helmet")),
                    materialNullable(map.get("chestplate")),
                    materialNullable(map.get("leggings")),
                    materialNullable(map.get("boots"))
            ));
        }
        if (mobs.isEmpty()) {
            mobs.add(new DungeonMobDefinition(EntityType.ZOMBIE, "Dungeon Zombie", 6, 1.0, 1.0,
                    null, null, null, null, null));
        }

        ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
        long coinMin = rewardSection == null ? 0 : Math.max(0, rewardSection.getLong("coin.min", 0));
        long coinMax = rewardSection == null ? coinMin : Math.max(coinMin, rewardSection.getLong("coin.max", coinMin));
        long tokenMin = rewardSection == null ? 0 : Math.max(0, rewardSection.getLong("token.min", 0));
        long tokenMax = rewardSection == null ? tokenMin : Math.max(tokenMin, rewardSection.getLong("token.max", tokenMin));
        List<DungeonCustomReward> custom = new ArrayList<>();
        if (rewardSection != null) {
            ConfigurationSection customSection = rewardSection.getConfigurationSection("custom-items");
            if (customSection != null) {
                for (String itemId : customSection.getKeys(false)) {
                    ConfigurationSection reward = customSection.getConfigurationSection(itemId);
                    if (reward == null) continue;
                    double chance = Math.max(0.0, Math.min(1.0, reward.getDouble("chance", 1.0)));
                    int min = Math.max(1, reward.getInt("min", reward.getInt("amount", 1)));
                    int max = Math.max(min, reward.getInt("max", min));
                    custom.add(new DungeonCustomReward(normalize(itemId), chance, min, max));
                }
            }
        }

        return new DungeonDefinition(
                id, name, description, icon, tier, enabled, recommended, required,
                cooldown, timeLimit, spawn, useWorldSpawn, radius, List.copyOf(mobs),
                new DungeonRewardDefinition(coinMin, coinMax, tokenMin, tokenMax, List.copyOf(custom))
        );
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String pretty(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private EntityType entityType(String raw) {
        try { return EntityType.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private Rarity rarity(String raw, Rarity fallback) {
        if (raw == null) return fallback;
        try { return Rarity.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return fallback; }
    }

    private Material material(String raw, Material fallback) {
        Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private Material materialNullable(Object raw) {
        if (raw == null) return null;
        String value = String.valueOf(raw);
        if (value.isBlank() || value.equalsIgnoreCase("none")) return null;
        return Material.matchMaterial(value.toUpperCase(Locale.ROOT));
    }
}
