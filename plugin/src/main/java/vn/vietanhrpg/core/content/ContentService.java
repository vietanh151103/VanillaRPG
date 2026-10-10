package vn.vietanhrpg.core.content;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.item.Affix;
import vn.vietanhrpg.core.item.Rarity;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ContentService {
    private final JavaPlugin plugin;
    private final Map<String, MaterialDefinition> materials = new LinkedHashMap<>();
    private final Map<String, BlueprintDefinition> blueprints = new LinkedHashMap<>();
    private final Map<String, UniqueItemDefinition> uniqueItems = new LinkedHashMap<>();
    private YamlConfiguration config;

    public ContentService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.config = loadMergedYaml("items.yml");
        materials.clear();
        blueprints.clear();
        uniqueItems.clear();
        loadMaterials();
        loadBlueprints();
        loadUniqueItems();
        plugin.getLogger().info("Loaded " + materials.size() + " custom materials, "
                + blueprints.size() + " blueprints and " + uniqueItems.size() + " unique items.");
    }

    public MaterialDefinition material(String id) {
        return materials.get(normalize(id));
    }

    public BlueprintDefinition blueprint(String id) {
        return blueprints.get(normalize(id));
    }

    public UniqueItemDefinition uniqueItem(String id) {
        return uniqueItems.get(normalize(id));
    }

    public List<BlueprintDefinition> blueprints() {
        return List.copyOf(blueprints.values());
    }

    public List<UniqueItemDefinition> uniqueItems() {
        return List.copyOf(uniqueItems.values());
    }

    private YamlConfiguration loadMergedYaml(String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) {
            plugin.saveResource(resourceName, false);
        }

        YamlConfiguration live = YamlConfiguration.loadConfiguration(target);
        try (var stream = plugin.getResource(resourceName)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                int liveVersion = live.getInt("content-version", 0);
                int defaultVersion = defaults.getInt("content-version", 0);
                if (liveVersion < defaultVersion) {
                    migrateContent(live, liveVersion, defaultVersion);
                }
                live.setDefaults(defaults);
                live.options().copyDefaults(true);
                live.save(target);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not merge " + resourceName + ": " + exception.getMessage());
        }
        return live;
    }

    private void migrateContent(YamlConfiguration live, int fromVersion, int toVersion) {
        if (fromVersion < 2 && toVersion >= 2) {
            // 0.2 test builds could persist Bloodreaver as LEGENDARY.
            // Bloodreaver is the MYTHIC showcase item by design.
            live.set("unique-items.bloodreaver.rarity", "MYTHIC");

            // The EPIC base marker is treated as a minimum rarity, so EPIC+ remains valid.
            Object oldRarity = live.get("unique-items.ashbringer.base.minimum-rarity");
            if (live.get("unique-items.ashbringer.base.rarity") == null && oldRarity != null) {
                live.set("unique-items.ashbringer.base.rarity", oldRarity);
            }
            live.set("unique-items.ashbringer.base.minimum-rarity", null);
            live.set("content-version", 2);
            plugin.getLogger().info("Migrated items.yml content from v" + fromVersion + " to v2.");
        }

        if (fromVersion < 3 && toVersion >= 3) {
            // Visual refresh for VanillaRPG forging materials. IDs stay unchanged so old
            // recipes and already-created items remain compatible.
            live.set("custom-materials.epic_shard.name", "✦ Epic Essence Shard ✦");
            live.set("custom-materials.epic_shard.color", "DARK_PURPLE");
            live.set("custom-materials.epic_shard.glint", true);
            live.set("custom-materials.epic_shard.lore", List.of(
                    "Condensed violet essence from high-tier encounters.",
                    "A forging catalyst used from EPIC progression onward.",
                    "It hums with unstable arcane light."
            ));

            live.set("custom-materials.legendary_shard.name", "✦ Legendary Starshard ✦");
            live.set("custom-materials.legendary_shard.color", "GOLD");
            live.set("custom-materials.legendary_shard.glint", true);
            live.set("custom-materials.legendary_shard.lore", List.of(
                    "A radiant fragment used in Legendary forging.",
                    "Its surface shines like a captured star."
            ));

            live.set("custom-materials.mythic_core.name", "✦✦ Crimson Mythic Heartcore ✦✦");
            live.set("custom-materials.mythic_core.color", "RED");
            live.set("custom-materials.mythic_core.glint", true);
            live.set("custom-materials.mythic_core.lore", List.of(
                    "A living core saturated with Mythic energy.",
                    "Reserved for the highest-tier forging rituals.",
                    "A crimson pulse can be felt through the shell."
            ));

            live.set("content-version", 3);
            plugin.getLogger().info("Migrated items.yml content from v" + fromVersion + " to v3 visual materials.");
        }

        if (fromVersion < 4 && toVersion >= 4) {
            // 0.2.8 adopts exact Blades of Majestica weapon names. Internal IDs stay
            // unchanged so existing blueprints, PDC item IDs and database data remain valid.
            live.set("unique-items.ashbringer.name", "Hero Sword");
            live.set("unique-items.bloodreaver.name", "Demonic Blade");
            live.set("blueprints.ashbringer.name", "Blueprint: Hero Sword");
            live.set("blueprints.bloodreaver.name", "Blueprint: Demonic Blade");
            live.set("content-version", 4);
            plugin.getLogger().info("Migrated items.yml content from v" + fromVersion + " to v4 Majestica weapon names.");
        }
    }

    private void loadMaterials() {
        ConfigurationSection root = config.getConfigurationSection("custom-materials");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            String path = "custom-materials." + rawId;
            Material material = parseMaterial(config.getString(path + ".material"), Material.PAPER, path);
            String name = config.getString(path + ".name", rawId);
            NamedTextColor color = parseColor(config.getString(path + ".color", "WHITE"));
            boolean glint = config.getBoolean(path + ".glint", false);
            List<String> lore = new ArrayList<>(config.getStringList(path + ".lore"));
            MaterialDefinition definition = new MaterialDefinition(
                    normalize(rawId), material, name, color, glint, Collections.unmodifiableList(lore));
            materials.put(definition.id(), definition);
        }
    }

    private void loadBlueprints() {
        ConfigurationSection root = config.getConfigurationSection("blueprints");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            String path = "blueprints." + rawId;
            String targetItem = normalize(config.getString(path + ".target-item", rawId));
            Material material = parseMaterial(config.getString(path + ".material"), Material.PAPER, path);
            String name = config.getString(path + ".name", "Blueprint: " + rawId);
            NamedTextColor color = parseColor(config.getString(path + ".color", "GOLD"));
            BlueprintDefinition definition = new BlueprintDefinition(
                    normalize(rawId), targetItem, material, name, color);
            blueprints.put(definition.id(), definition);
        }
    }

    private void loadUniqueItems() {
        ConfigurationSection root = config.getConfigurationSection("unique-items");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            String path = "unique-items." + rawId;
            Material material = parseMaterial(config.getString(path + ".material"), Material.NETHERITE_SWORD, path);
            Rarity rarity = parseRarity(config.getString(path + ".rarity", "LEGENDARY"));
            String name = config.getString(path + ".name", rawId);
            String blueprintId = normalize(config.getString(path + ".blueprint", rawId));

            Material baseMaterial = null;
            String baseMaterialRaw = config.getString(path + ".base.material");
            if (baseMaterialRaw != null && !baseMaterialRaw.isBlank()) {
                baseMaterial = Material.matchMaterial(baseMaterialRaw);
            }
            String rarityRaw = config.getString(path + ".base.rarity");
            if (rarityRaw == null) {
                // Backward compatibility with the first 0.2 test build.
                rarityRaw = config.getString(path + ".base.minimum-rarity");
            }
            Rarity requiredRarity = parseRarityNullable(rarityRaw);
            String baseItemId = normalizeNullable(config.getString(path + ".base.item-id"));
            BaseRequirement base = new BaseRequirement(baseMaterial, requiredRarity, baseItemId);

            long coin = Math.max(0, config.getLong(path + ".cost.coin", 0));
            long token = Math.max(0, config.getLong(path + ".cost.token", 0));
            Map<Material, Integer> vanillaMaterials = readVanillaMaterialMap(path + ".cost.materials");
            Map<String, Integer> customMaterials = readCustomMaterialMap(path + ".cost.custom-materials");
            Map<Affix, Double> affixes = readAffixes(path + ".affixes");
            String passiveId = normalizeNullable(config.getString(path + ".passive.id"));
            Map<String, Double> passiveValues = readDoubleMap(path + ".passive.values");
            List<String> passiveLore = new ArrayList<>(config.getStringList(path + ".passive.lore"));

            UniqueItemDefinition definition = new UniqueItemDefinition(
                    normalize(rawId), material, rarity, name, blueprintId, base, coin, token,
                    Collections.unmodifiableMap(vanillaMaterials),
                    Collections.unmodifiableMap(customMaterials),
                    Collections.unmodifiableMap(affixes),
                    passiveId,
                    Collections.unmodifiableMap(passiveValues),
                    Collections.unmodifiableList(passiveLore)
            );
            uniqueItems.put(definition.id(), definition);
        }
    }

    private Map<Material, Integer> readVanillaMaterialMap(String path) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unknown material in items.yml: " + key + " at " + path);
                continue;
            }
            result.put(material, Math.max(1, section.getInt(key, 1)));
        }
        return result;
    }

    private Map<String, Integer> readCustomMaterialMap(String path) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(normalize(key), Math.max(1, section.getInt(key, 1)));
        }
        return result;
    }

    private Map<Affix, Double> readAffixes(String path) {
        Map<Affix, Double> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            Affix.fromId(key).ifPresentOrElse(
                    affix -> result.put(affix, section.getDouble(key, 0.0)),
                    () -> plugin.getLogger().warning("Unknown affix in items.yml: " + key)
            );
        }
        return result;
    }

    private Map<String, Double> readDoubleMap(String path) {
        Map<String, Double> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(normalize(key), section.getDouble(key, 0.0));
        }
        return result;
    }

    private Material parseMaterial(String raw, Material fallback, String path) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + raw + "' at " + path + ", using " + fallback.name());
            return fallback;
        }
        return material;
    }

    private Rarity parseRarity(String raw) {
        Rarity rarity = parseRarityNullable(raw);
        return rarity == null ? Rarity.LEGENDARY : rarity;
    }

    private Rarity parseRarityNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown rarity in items.yml: " + raw);
            return null;
        }
    }

    private NamedTextColor parseColor(String raw) {
        if (raw == null) {
            return NamedTextColor.WHITE;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "BLACK" -> NamedTextColor.BLACK;
            case "DARK_BLUE" -> NamedTextColor.DARK_BLUE;
            case "DARK_GREEN" -> NamedTextColor.DARK_GREEN;
            case "DARK_AQUA" -> NamedTextColor.DARK_AQUA;
            case "DARK_RED" -> NamedTextColor.DARK_RED;
            case "DARK_PURPLE" -> NamedTextColor.DARK_PURPLE;
            case "GOLD" -> NamedTextColor.GOLD;
            case "GRAY" -> NamedTextColor.GRAY;
            case "DARK_GRAY" -> NamedTextColor.DARK_GRAY;
            case "BLUE" -> NamedTextColor.BLUE;
            case "GREEN" -> NamedTextColor.GREEN;
            case "AQUA" -> NamedTextColor.AQUA;
            case "RED" -> NamedTextColor.RED;
            case "LIGHT_PURPLE" -> NamedTextColor.LIGHT_PURPLE;
            case "YELLOW" -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
