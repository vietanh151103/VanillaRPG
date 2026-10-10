package vn.vietanhrpg.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.progression.AchievementDefinition;
import vn.vietanhrpg.core.progression.ProgressionContentService;
import vn.vietanhrpg.core.progression.ProgressionService;
import vn.vietanhrpg.core.progression.QuestPeriod;
import vn.vietanhrpg.core.progression.QuestProgressView;
import vn.vietanhrpg.core.progression.TitleDefinition;
import vn.vietanhrpg.core.service.EconomyService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProgressionGuiService implements Listener {
    private final Database database;
    private final EconomyService economy;
    private final ProgressionService progression;
    private final ProgressionContentService content;

    public ProgressionGuiService(Database database, EconomyService economy,
                                 ProgressionService progression, ProgressionContentService content) {
        this.database = database;
        this.economy = economy;
        this.progression = progression;
        this.content = content;
    }

    public void openProfile(Player player) {
        progression.ensure(player);
        ProfileHolder holder = new ProfileHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("RPG Profile", NamedTextColor.GOLD));
        holder.inventory = inventory;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        skull.setOwningPlayer(player);
        skull.displayName(text(player.getName(), NamedTextColor.GOLD, true));
        List<Component> headLore = new ArrayList<>();
        headLore.add(gray("Title: " + progression.selectedTitleName(player)));
        headLore.add(gray("Blueprints: " + database.blueprintCount(player.getUniqueId())));
        headLore.add(gray("Dungeon clears: " + database.getStat(player.getUniqueId(), "dungeon_clears")));
        skull.lore(headLore);
        head.setItemMeta(skull);
        inventory.setItem(4, head);

        inventory.setItem(10, item(Material.GOLD_INGOT, "Coin", NamedTextColor.GOLD,
                List.of(gray(String.valueOf(economy.coin(player.getUniqueId()))))));
        inventory.setItem(11, item(Material.AMETHYST_SHARD, "Token", NamedTextColor.AQUA,
                List.of(gray(String.valueOf(economy.token(player.getUniqueId()))))));

        int dailyDone = progression.completedCount(player, QuestPeriod.DAILY);
        int dailyTotal = progression.totalCount(player, QuestPeriod.DAILY);
        inventory.setItem(13, item(Material.WRITABLE_BOOK, "Daily Quests", NamedTextColor.GREEN,
                List.of(gray(dailyDone + " / " + dailyTotal + " completed"), yellow("Click to view"))));

        int weeklyDone = progression.completedCount(player, QuestPeriod.WEEKLY);
        int weeklyTotal = progression.totalCount(player, QuestPeriod.WEEKLY);
        inventory.setItem(14, item(Material.CLOCK, "Weekly Quests", NamedTextColor.AQUA,
                List.of(gray(weeklyDone + " / " + weeklyTotal + " completed"), yellow("Click to view"))));

        inventory.setItem(15, item(Material.NAME_TAG, "Titles", NamedTextColor.LIGHT_PURPLE,
                List.of(gray("Selected: " + progression.selectedTitleName(player)), yellow("Click to choose"))));

        inventory.setItem(16, item(Material.NETHER_STAR, "Achievements", NamedTextColor.YELLOW,
                List.of(gray(database.achievements(player.getUniqueId()).size() + " unlocked"), yellow("Click to view"))));

        inventory.setItem(21, item(Material.ENDER_EYE, "Dungeons", NamedTextColor.DARK_AQUA,
                List.of(gray(database.getStat(player.getUniqueId(), "dungeon_clears") + " clears"), yellow("Click to open"))));

        List<Component> craftLore = new ArrayList<>();
        craftLore.add(line("Rare: " + database.getStat(player.getUniqueId(), "rare_crafted"), NamedTextColor.BLUE));
        craftLore.add(line("Epic: " + database.getStat(player.getUniqueId(), "epic_crafted"), NamedTextColor.DARK_PURPLE));
        craftLore.add(line("Legendary: " + database.getStat(player.getUniqueId(), "legendary_crafted"), NamedTextColor.GOLD));
        craftLore.add(line("Mythic: " + database.getStat(player.getUniqueId(), "mythic_crafted"), NamedTextColor.RED));
        inventory.setItem(22, item(Material.SMITHING_TABLE, "Forge Statistics", NamedTextColor.WHITE, craftLore));

        player.openInventory(inventory);
    }

    public void openQuests(Player player) {
        progression.ensure(player);
        QuestHolder holder = new QuestHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Daily & Weekly Quests", NamedTextColor.GREEN));
        holder.inventory = inv;
        int[] dailySlots = {10, 12, 14};
        int[] weeklySlots = {28, 30, 32};
        List<QuestProgressView> daily = progression.quests(player, QuestPeriod.DAILY);
        List<QuestProgressView> weekly = progression.quests(player, QuestPeriod.WEEKLY);
        for (int i = 0; i < daily.size() && i < dailySlots.length; i++) inv.setItem(dailySlots[i], questItem(daily.get(i)));
        for (int i = 0; i < weekly.size() && i < weeklySlots.length; i++) inv.setItem(weeklySlots[i], questItem(weekly.get(i)));
        inv.setItem(4, item(Material.SUNFLOWER, "Daily Quests", NamedTextColor.GREEN,
                List.of(gray("Reset key: " + progression.periodKey(QuestPeriod.DAILY)))));
        inv.setItem(22, item(Material.CLOCK, "Weekly Quests", NamedTextColor.AQUA,
                List.of(gray("Reset key: " + progression.periodKey(QuestPeriod.WEEKLY)))));
        inv.setItem(49, item(Material.PLAYER_HEAD, "Back to Profile", NamedTextColor.YELLOW, List.of()));
        player.openInventory(inv);
    }

    public void openAchievements(Player player) {
        progression.ensure(player);
        AchievementHolder holder = new AchievementHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Achievements", NamedTextColor.YELLOW));
        holder.inventory = inv;
        int slot = 10;
        for (AchievementDefinition a : content.achievements().values()) {
            while (slot < 44 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            if (slot >= 44) break;
            boolean unlocked = database.hasAchievement(player.getUniqueId(), a.id());
            long progressValue = progression.achievementProgress(player, a);
            List<Component> lore = new ArrayList<>();
            lore.add(gray(a.description()));
            lore.add(gray("Progress: " + Math.min(progressValue, a.target()) + " / " + a.target()));
            lore.add(unlocked ? green("UNLOCKED") : red("LOCKED"));
            lore.add(yellow("Reward: " + progression.rewardSummary(a.reward())));
            inv.setItem(slot++, item(unlocked ? Material.ENCHANTED_GOLDEN_APPLE : Material.GRAY_DYE,
                    a.name(), unlocked ? NamedTextColor.GOLD : NamedTextColor.GRAY, lore));
        }
        inv.setItem(49, item(Material.PLAYER_HEAD, "Back to Profile", NamedTextColor.YELLOW, List.of()));
        player.openInventory(inv);
    }

    public void openTitles(Player player) {
        progression.ensure(player);
        TitleHolder holder = new TitleHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Titles", NamedTextColor.LIGHT_PURPLE));
        holder.inventory = inv;
        inv.setItem(4, item(Material.BARRIER, "No Title", NamedTextColor.GRAY,
                List.of(yellow("Click to clear selected title"))));
        holder.titleBySlot.put(4, "none");
        int slot = 10;
        String selected = database.selectedTitle(player.getUniqueId());
        for (TitleDefinition title : content.titles().values()) {
            while (slot < 44 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
            if (slot >= 44) break;
            boolean unlocked = database.hasTitle(player.getUniqueId(), title.id());
            boolean active = title.id().equalsIgnoreCase(selected == null ? "" : selected);
            List<Component> lore = new ArrayList<>();
            lore.add(unlocked ? green("UNLOCKED") : red("LOCKED"));
            if (active) lore.add(yellow("SELECTED"));
            if (unlocked) lore.add(gray("Click to select"));
            inv.setItem(slot, item(unlocked ? Material.NAME_TAG : Material.GRAY_DYE,
                    "[" + title.name() + "]", title.color(), lore));
            holder.titleBySlot.put(slot, title.id());
            slot++;
        }
        inv.setItem(49, item(Material.PLAYER_HEAD, "Back to Profile", NamedTextColor.YELLOW, List.of()));
        player.openInventory(inv);
    }

    private ItemStack questItem(QuestProgressView view) {
        var q = view.definition();
        List<Component> lore = new ArrayList<>();
        lore.add(gray(q.description()));
        lore.add(Component.empty());
        lore.add(gray("Progress: " + Math.min(view.progress(), q.target()) + " / " + q.target()));
        lore.add(view.rewarded() ? green("COMPLETED") : (view.complete() ? green("COMPLETE") : yellow("IN PROGRESS")));
        lore.add(Component.empty());
        lore.add(yellow("Reward: " + progression.rewardSummary(q.reward())));
        Material icon = view.rewarded() ? Material.LIME_DYE : view.complete() ? Material.EMERALD : Material.PAPER;
        return item(icon, q.name(), view.rewarded() ? NamedTextColor.GREEN : NamedTextColor.GOLD, lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ProfileHolder || holder instanceof QuestHolder || holder instanceof AchievementHolder || holder instanceof TitleHolder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (holder instanceof ProfileHolder) {
            if (slot == 13 || slot == 14) openQuests(player);
            else if (slot == 15) openTitles(player);
            else if (slot == 16) openAchievements(player);
            else if (slot == 21) {
                player.closeInventory();
                player.performCommand("dungeons");
            }
            return;
        }
        if (slot == 49) {
            openProfile(player);
            return;
        }
        if (holder instanceof TitleHolder titleHolder) {
            String titleId = titleHolder.titleBySlot.get(slot);
            if (titleId == null) return;
            if (!progression.selectTitle(player, titleId)) {
                player.sendMessage(Component.text("That title is still locked.", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text(titleId.equals("none") ? "Title cleared." : "Title selected.", NamedTextColor.GREEN));
            openTitles(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ProfileHolder || holder instanceof QuestHolder || holder instanceof AchievementHolder || holder instanceof TitleHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name, color, true));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String s, NamedTextColor color, boolean bold) {
        Component c = Component.text(s, color).decoration(TextDecoration.ITALIC, false);
        return bold ? c.decorate(TextDecoration.BOLD) : c;
    }
    private Component line(String s, NamedTextColor color) { return text(s, color, false); }
    private Component gray(String s) { return line(s, NamedTextColor.GRAY); }
    private Component yellow(String s) { return line(s, NamedTextColor.YELLOW); }
    private Component green(String s) { return line(s, NamedTextColor.GREEN); }
    private Component red(String s) { return line(s, NamedTextColor.RED); }

    private static final class ProfileHolder implements InventoryHolder { private Inventory inventory; public Inventory getInventory(){return inventory;} }
    private static final class QuestHolder implements InventoryHolder { private Inventory inventory; public Inventory getInventory(){return inventory;} }
    private static final class AchievementHolder implements InventoryHolder { private Inventory inventory; public Inventory getInventory(){return inventory;} }
    private static final class TitleHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> titleBySlot = new HashMap<>();
        public Inventory getInventory(){return inventory;}
    }
}
