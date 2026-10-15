package vn.vietanhrpg.core.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.service.EconomyService;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ProgressionService {
    private static final String TITLE_TEAM_PREFIX = "vrpgT";
    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;
    private final ContentService content;
    private final ProgressionContentService progressionContent;

    public ProgressionService(JavaPlugin plugin, Database database, EconomyService economy,
                              ItemService items, ContentService content,
                              ProgressionContentService progressionContent) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
        this.content = content;
        this.progressionContent = progressionContent;
    }

    public void ensure(Player player) {
        economy.ensure(player);
        ensureAssignments(player.getUniqueId(), QuestPeriod.DAILY);
        ensureAssignments(player.getUniqueId(), QuestPeriod.WEEKLY);
        refreshAchievements(player);
    }

    public List<QuestProgressView> quests(Player player, QuestPeriod period) {
        ensure(player);
        String key = periodKey(period);
        List<String> ids = database.questAssignments(player.getUniqueId(), period.name(), key);
        List<QuestProgressView> result = new ArrayList<>();
        for (String id : ids) {
            QuestDefinition q = progressionContent.quest(id);
            if (q == null) continue;
            result.add(new QuestProgressView(
                    q,
                    database.questProgress(player.getUniqueId(), q.id(), key),
                    database.questRewarded(player.getUniqueId(), q.id(), key),
                    key
            ));
        }
        return result;
    }

    public int completedCount(Player player, QuestPeriod period) {
        int count = 0;
        for (QuestProgressView view : quests(player, period)) if (view.rewarded()) count++;
        return count;
    }

    public int totalCount(Player player, QuestPeriod period) {
        return quests(player, period).size();
    }

    public void recordKillHostile(Player player) {
        record(player, ProgressType.KILL_HOSTILE, null, null, 1, "kills_hostile");
    }

    public void recordMine(Player player, Material material) {
        String specific = "mined_" + material.name().toLowerCase(Locale.ROOT);
        database.addCounter(player.getUniqueId(), "mined_blocks", 1);
        record(player, ProgressType.MINE_BLOCK, material, null, 1, specific);
    }

    public void recordFish(Player player) {
        record(player, ProgressType.FISH, null, null, 1, "fish_caught");
    }

    public void recordForge(Player player, Rarity rarity) {
        database.addCounter(player.getUniqueId(), "forge_any", 1);
        record(player, ProgressType.FORGE_RARITY, null, rarity, 1,
                "forge_" + rarity.name().toLowerCase(Locale.ROOT));
    }

    public void recordDungeonClear(Player player) {
        record(player, ProgressType.DUNGEON_CLEAR, null, null, 1, "dungeon_clears");
    }

    public void recordBossKill(Player player) {
        record(player, ProgressType.BOSS_KILL, null, null, 1, "boss_kills");
    }

    private void record(Player player, ProgressType type, Material material, Rarity rarity, long amount, String counterId) {
        ensure(player);
        long newCounter = database.addCounter(player.getUniqueId(), counterId, amount);
        checkAchievements(player, counterId, newCounter);
        for (QuestPeriod period : QuestPeriod.values()) {
            String key = periodKey(period);
            for (String questId : database.questAssignments(player.getUniqueId(), period.name(), key)) {
                QuestDefinition q = progressionContent.quest(questId);
                if (q == null || q.type() != type || !matches(q, material, rarity)) continue;
                if (database.questRewarded(player.getUniqueId(), q.id(), key)) continue;
                long progress = database.addQuestProgress(player.getUniqueId(), q.id(), key, amount);
                if (progress >= q.target()) {
                    database.markQuestRewarded(player.getUniqueId(), q.id(), key);
                    recordAchievementCounter(player, "quests_completed", 1);
                    grantReward(player, q.reward());
                    player.sendMessage(Component.text("Quest complete: ", NamedTextColor.GREEN)
                            .append(Component.text(q.name(), NamedTextColor.GOLD)));
                    player.sendMessage(Component.text("Reward: " + rewardSummary(q.reward()), NamedTextColor.YELLOW));
                }
            }
        }
    }

    private boolean matches(QuestDefinition q, Material material, Rarity rarity) {
        if (q.type() == ProgressType.MINE_BLOCK && q.material() != null) return q.material() == material;
        if (q.type() == ProgressType.FORGE_RARITY && q.rarity() != null) return q.rarity() == rarity;
        return true;
    }

    private void checkAchievements(Player player, String counterId, long currentValue) {
        for (AchievementDefinition achievement : progressionContent.achievements().values()) {
            if (!achievement.counterId().equalsIgnoreCase(counterId)) continue;
            if (currentValue < achievement.target()) continue;
            if (!database.unlockAchievement(player.getUniqueId(), achievement.id())) continue;
            grantReward(player, achievement.reward());
            player.sendMessage(Component.text("Achievement unlocked: ", NamedTextColor.AQUA)
                    .append(Component.text(achievement.name(), NamedTextColor.GOLD)));
            if (achievement.reward().titleId() != null) {
                TitleDefinition title = progressionContent.title(achievement.reward().titleId());
                if (title != null) {
                    player.sendMessage(Component.text("New title: [" + title.name() + "]", title.color()));
                }
            }
        }
    }

    /** Records a permanent milestone counter used by achievements/titles. */
    public void recordAchievementCounter(Player player, String counterId, long amount) {
        if (amount <= 0) return;
        long total = database.addCounter(player.getUniqueId(), counterId, amount);
        checkAchievements(player, counterId, total);
    }

    /** Checks achievements after a counter maintained by another service changes. */
    public void checkAchievementCounter(Player player, String counterId, long currentValue) {
        checkAchievements(player, counterId, currentValue);
    }

    /** Re-evaluates milestones from counters earned before a content update. */
    public void refreshAchievements(Player player) {
        for (AchievementDefinition achievement : progressionContent.achievements().values()) {
            checkAchievements(player, achievement.counterId(), database.counter(player.getUniqueId(), achievement.counterId()));
        }
    }

    public void grantReward(Player player, RewardDefinition reward) {
        if (reward.coin() > 0) economy.addCoin(player.getUniqueId(), reward.coin());
        if (reward.token() > 0) economy.addToken(player.getUniqueId(), reward.token());
        for (Map.Entry<String, Integer> entry : reward.customMaterials().entrySet()) {
            MaterialDefinition definition = content.material(entry.getKey());
            if (definition == null) continue;
            int remaining = entry.getValue();
            while (remaining > 0) {
                int amount = Math.min(remaining, definition.material().getMaxStackSize());
                ItemStack stack = items.createCustomMaterial(definition, amount);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (ItemStack extra : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
                remaining -= amount;
            }
        }
        if (reward.titleId() != null && progressionContent.title(reward.titleId()) != null) {
            database.unlockTitle(player.getUniqueId(), reward.titleId());
        }
    }

    public String rewardSummary(RewardDefinition reward) {
        List<String> parts = new ArrayList<>();
        if (reward.coin() > 0) parts.add(reward.coin() + " Elite Coins");
        if (reward.token() > 0) parts.add(reward.token() + " Token");
        for (Map.Entry<String, Integer> e : reward.customMaterials().entrySet()) {
            MaterialDefinition material = content.material(e.getKey());
            parts.add(e.getValue() + "x " + (material == null ? e.getKey() : material.name()));
        }
        if (reward.titleId() != null) {
            TitleDefinition title = progressionContent.title(reward.titleId());
            if (title != null) parts.add("Title [" + title.name() + "]");
        }
        return parts.isEmpty() ? "None" : String.join(", ", parts);
    }

    public long achievementProgress(Player player, AchievementDefinition achievement) {
        return database.counter(player.getUniqueId(), achievement.counterId());
    }

    public String selectedTitleName(Player player) {
        String id = database.selectedTitle(player.getUniqueId());
        TitleDefinition title = progressionContent.title(id);
        return title == null ? "None" : title.name();
    }

    public TitleDefinition selectedTitle(Player player) {
        return progressionContent.title(database.selectedTitle(player.getUniqueId()));
    }


    public void applyTitleDisplay(Player player) {
        TitleDefinition title = selectedTitle(player);
        if (title == null) {
            if (database.selectedTitle(player.getUniqueId()) != null) database.setSelectedTitle(player.getUniqueId(), null);
            player.displayName(Component.text(player.getName(), NamedTextColor.WHITE));
            clearTitleNameTag(player);
            return;
        }
        player.displayName(Component.text("[" + title.name() + "] ", title.color())
                .append(Component.text(player.getName(), NamedTextColor.WHITE)));
        applyTitleNameTag(player, title);
    }

    private void applyTitleNameTag(Player player, TitleDefinition title) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TITLE_TEAM_PREFIX + player.getUniqueId().toString().replace("-", "").substring(0, 10);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) team = scoreboard.registerNewTeam(teamName);
        for (String entry : List.copyOf(team.getEntries())) team.removeEntry(entry);
        team.prefix(Component.text("[" + title.name() + "] ", title.color()));
        team.addEntry(player.getName());
    }

    private void clearTitleNameTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TITLE_TEAM_PREFIX + player.getUniqueId().toString().replace("-", "").substring(0, 10);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) team.unregister();
    }

    /** Removes the plugin-owned nametag teams during plugin shutdown. */
    public void clearTitleNameTags() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : List.copyOf(scoreboard.getTeams())) {
            if (team.getName().startsWith(TITLE_TEAM_PREFIX)) team.unregister();
        }
    }

    public boolean selectTitle(Player player, String titleId) {
        if (titleId == null || titleId.equalsIgnoreCase("none")) {
            database.setSelectedTitle(player.getUniqueId(), null);
            applyTitleDisplay(player);
            return true;
        }
        if (!database.hasTitle(player.getUniqueId(), titleId)) return false;
        if (progressionContent.title(titleId) == null) return false;
        database.setSelectedTitle(player.getUniqueId(), titleId);
        applyTitleDisplay(player);
        return true;
    }

    public boolean grantTitle(Player player, String titleId, boolean select) {
        if (progressionContent.title(titleId) == null) return false;
        database.unlockTitle(player.getUniqueId(), titleId);
        if (select) {
            database.setSelectedTitle(player.getUniqueId(), titleId);
            applyTitleDisplay(player);
        }
        return true;
    }

    public void unlockConfiguredStarterTitles(Player player) {
        // Reserved for future starter titles. Keeping method makes migrations easy.
    }

    private void ensureAssignments(UUID uuid, QuestPeriod period) {
        String key = periodKey(period);
        List<String> current = database.questAssignments(uuid, period.name(), key);
        int desired = 3;
        int valid = 0;
        for (String id : current) {
            QuestDefinition definition = progressionContent.quest(id);
            if (definition != null && definition.period() == period) valid++;
        }
        if (valid >= Math.min(desired, progressionContent.quests(period).size())) return;
        List<QuestDefinition> pool = new ArrayList<>(progressionContent.quests(period));
        pool.sort(Comparator.comparing(QuestDefinition::id));
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ key.hashCode() ^ period.ordinal() * 7919L;
        Collections.shuffle(pool, new Random(seed));
        List<String> ids = pool.stream().limit(desired).map(QuestDefinition::id).toList();
        database.saveQuestAssignments(uuid, period.name(), key, ids);
    }

    public String periodKey(QuestPeriod period) {
        LocalDate date = LocalDate.now();
        if (period == QuestPeriod.DAILY) return date.toString();
        WeekFields wf = WeekFields.ISO;
        return date.get(wf.weekBasedYear()) + "-W" + String.format("%02d", date.get(wf.weekOfWeekBasedYear()));
    }
}
