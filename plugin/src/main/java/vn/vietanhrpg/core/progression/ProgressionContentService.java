package vn.vietanhrpg.core.progression;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.item.Rarity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProgressionContentService {
    private final JavaPlugin plugin;
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private final Map<String, AchievementDefinition> achievements = new LinkedHashMap<>();
    private final Map<String, TitleDefinition> titles = new LinkedHashMap<>();

    public ProgressionContentService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        quests.clear();
        achievements.clear();
        titles.clear();
        File file = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadQuestGroup(yaml.getConfigurationSection("daily"), QuestPeriod.DAILY);
        loadQuestGroup(yaml.getConfigurationSection("weekly"), QuestPeriod.WEEKLY);
        loadAchievements(yaml.getConfigurationSection("achievements"));
        loadTitles(yaml.getConfigurationSection("titles"));
    }

    private void loadQuestGroup(ConfigurationSection section, QuestPeriod period) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection q = section.getConfigurationSection(id);
            if (q == null) continue;
            ProgressType type = parseProgressType(q.getString("type"));
            if (type == null) {
                plugin.getLogger().warning("Ignoring quest " + id + ": invalid type");
                continue;
            }
            Material material = Material.matchMaterial(q.getString("material", ""));
            Rarity rarity = parseRarity(q.getString("rarity"));
            long target = Math.max(1, q.getLong("target", 1));
            quests.put(id.toLowerCase(Locale.ROOT), new QuestDefinition(
                    id.toLowerCase(Locale.ROOT), period,
                    q.getString("name", pretty(id)),
                    q.getString("description", ""),
                    type, target, material, rarity, readReward(q.getConfigurationSection("reward"))
            ));
        }
    }

    private void loadAchievements(ConfigurationSection section) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection a = section.getConfigurationSection(id);
            if (a == null) continue;
            String counter = a.getString("counter", "").toLowerCase(Locale.ROOT);
            if (counter.isBlank()) continue;
            achievements.put(id.toLowerCase(Locale.ROOT), new AchievementDefinition(
                    id.toLowerCase(Locale.ROOT),
                    a.getString("name", pretty(id)),
                    a.getString("description", ""),
                    counter,
                    Math.max(1, a.getLong("target", 1)),
                    readReward(a.getConfigurationSection("reward"))
            ));
        }
    }

    private void loadTitles(ConfigurationSection section) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) continue;
            NamedTextColor color = parseColor(t.getString("color", "WHITE"));
            titles.put(id.toLowerCase(Locale.ROOT), new TitleDefinition(
                    id.toLowerCase(Locale.ROOT), t.getString("name", pretty(id)), color
            ));
        }
    }

    private RewardDefinition readReward(ConfigurationSection section) {
        if (section == null) return new RewardDefinition(0, 0, Map.of(), null);
        Map<String, Integer> custom = new LinkedHashMap<>();
        ConfigurationSection cs = section.getConfigurationSection("custom-materials");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                int amount = cs.getInt(key, 0);
                if (amount > 0) custom.put(key.toLowerCase(Locale.ROOT), amount);
            }
        }
        String title = section.getString("title");
        return new RewardDefinition(
                Math.max(0, section.getLong("coin", 0)),
                Math.max(0, section.getLong("token", 0)),
                Collections.unmodifiableMap(custom),
                title == null ? null : title.toLowerCase(Locale.ROOT)
        );
    }

    public QuestDefinition quest(String id) {
        return id == null ? null : quests.get(id.toLowerCase(Locale.ROOT));
    }

    public List<QuestDefinition> quests(QuestPeriod period) {
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition q : quests.values()) if (q.period() == period) result.add(q);
        return result;
    }

    public Map<String, AchievementDefinition> achievements() { return Collections.unmodifiableMap(achievements); }
    public AchievementDefinition achievement(String id) { return id == null ? null : achievements.get(id.toLowerCase(Locale.ROOT)); }
    public Map<String, TitleDefinition> titles() { return Collections.unmodifiableMap(titles); }
    public TitleDefinition title(String id) { return id == null ? null : titles.get(id.toLowerCase(Locale.ROOT)); }

    private ProgressType parseProgressType(String raw) {
        if (raw == null) return null;
        try { return ProgressType.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private Rarity parseRarity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Rarity.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private NamedTextColor parseColor(String raw) {
        return switch (raw.toUpperCase(Locale.ROOT)) {
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

    private String pretty(String id) {
        String[] parts = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return out.toString();
    }
}
