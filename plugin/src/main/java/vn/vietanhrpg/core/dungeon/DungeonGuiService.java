package vn.vietanhrpg.core.dungeon;

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
import vn.vietanhrpg.core.data.Database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonGuiService implements Listener {
    private final DungeonContentService content;
    private final DungeonService service;
    private final Database database;

    public DungeonGuiService(DungeonContentService content, DungeonService service, Database database) {
        this.content = content;
        this.service = service;
        this.database = database;
    }

    public void open(Player player) {
        DungeonHolder holder = new DungeonHolder();
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("Dungeons", NamedTextColor.DARK_AQUA));
        holder.inventory = inventory;

        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        int index = 0;
        for (DungeonDefinition dungeon : content.dungeons()) {
            if (index >= slots.length) break;
            int slot = slots[index++];
            holder.dungeonBySlot.put(slot, dungeon.id());
            inventory.setItem(slot, dungeonItem(player, dungeon));
        }

        inventory.setItem(4, item(Material.COMPASS, "Dungeon Finder", NamedTextColor.GOLD,
                List.of(gray("Solo runs with vanilla mobs and RPG tuning."), gray("Click a configured dungeon to enter."))));
        inventory.setItem(40, item(Material.PLAYER_HEAD, "Back to Profile", NamedTextColor.YELLOW,
                List.of(gray("Click to return"))));
        player.openInventory(inventory);
    }

    private ItemStack dungeonItem(Player player, DungeonDefinition dungeon) {
        List<Component> lore = new ArrayList<>();
        lore.add(gray(dungeon.description()));
        lore.add(Component.empty());
        lore.add(line("Tier: " + dungeon.tier(), NamedTextColor.AQUA));
        lore.add(line("Recommended: " + dungeon.recommendedRarity().name() + "+", dungeon.recommendedRarity().color()));
        lore.add(line("Required weapon: " + dungeon.requiredRarity().name() + "+", dungeon.requiredRarity().color()));
        lore.add(gray("Objective: defeat " + dungeon.totalMobs() + " mobs"));
        lore.add(gray("Time limit: " + service.formatTime(dungeon.timeLimitSeconds())));
        lore.add(gray("Cooldown: " + service.formatTime(dungeon.cooldownSeconds())));
        lore.add(Component.empty());
        lore.add(yellow("Rewards: " + service.rewardSummary(dungeon)));
        lore.add(Component.empty());

        if (!dungeon.configured()) {
            lore.add(red("NOT CONFIGURED"));
            lore.add(gray("Admin: /dungeon setspawn " + dungeon.id()));
        } else if (service.occupied(dungeon.id())) {
            lore.add(red("OCCUPIED by " + service.occupiedBy(dungeon.id())));
        } else {
            long remaining = service.cooldownRemainingSeconds(player.getUniqueId(), dungeon);
            if (remaining > 0) lore.add(red("Cooldown: " + service.formatTime(remaining) + " remaining"));
            else lore.add(green("READY - Click to enter"));
        }

        return item(dungeon.icon(), dungeon.name(), tierColor(dungeon.tier()), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof DungeonHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (slot == 40) {
            player.closeInventory();
            player.performCommand("profile");
            return;
        }
        String dungeonId = holder.dungeonBySlot.get(slot);
        if (dungeonId == null) return;
        player.closeInventory();
        service.join(player, dungeonId);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DungeonHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.IRON_SWORD : material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name, color, true));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private NamedTextColor tierColor(int tier) {
        return switch (tier) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.BLUE;
            case 3 -> NamedTextColor.GOLD;
            default -> NamedTextColor.RED;
        };
    }

    private Component text(String s, NamedTextColor color, boolean bold) {
        Component component = Component.text(s, color).decoration(TextDecoration.ITALIC, false);
        return bold ? component.decorate(TextDecoration.BOLD) : component;
    }

    private Component line(String s, NamedTextColor color) { return text(s, color, false); }
    private Component gray(String s) { return line(s, NamedTextColor.GRAY); }
    private Component yellow(String s) { return line(s, NamedTextColor.YELLOW); }
    private Component green(String s) { return line(s, NamedTextColor.GREEN); }
    private Component red(String s) { return line(s, NamedTextColor.RED); }

    private static final class DungeonHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> dungeonBySlot = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
}
