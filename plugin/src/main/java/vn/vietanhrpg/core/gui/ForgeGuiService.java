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
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.UniqueItemDefinition;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.service.ForgeService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ForgeGuiService implements Listener {
    private final ForgeService forge;
    private final ItemService items;
    private final ContentService content;
    private final Database database;

    public ForgeGuiService(ForgeService forge, ItemService items, ContentService content, Database database) {
        this.forge = forge;
        this.items = items;
        this.content = content;
        this.database = database;
    }

    public void open(Player player) {
        ForgeHolder holder = new ForgeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("VanillaRPG Forge", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        inventory.setItem(10, upgradeMenuItem(player));
        inventory.setItem(12, menuItem(Material.AMETHYST_SHARD, "Reroll Affixes", NamedTextColor.AQUA,
                "Reroll the held weapon.", "Epic rerolls also consume Epic Shards."));
        inventory.setItem(14, menuItem(Material.SPYGLASS, "Inspect Held Item", NamedTextColor.YELLOW,
                "Show rarity, IDs and affixes."));
        inventory.setItem(16, menuItem(Material.NETHER_STAR, "Blueprint Recipes", NamedTextColor.GOLD,
                "Legendary and Mythic forging.", "Shows what you have and what is missing."));
        inventory.setItem(22, menuItem(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    public void openRecipes(Player player) {
        RecipeHolder holder = new RecipeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Blueprint Recipes", NamedTextColor.GOLD));
        holder.inventory = inventory;

        int slot = 0;
        for (UniqueItemDefinition definition : content.uniqueItems()) {
            while (slot < inventory.getSize() && isBorderSlot(slot, inventory.getSize())) {
                slot++;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            boolean learned = database.hasBlueprint(player.getUniqueId(), definition.blueprintId());
            ItemStack icon = recipeIcon(player, definition, learned);
            inventory.setItem(slot, icon);
            holder.recipeBySlot.put(slot, definition.id());
            slot++;
        }
        inventory.setItem(49, menuItem(Material.ARROW, "Back", NamedTextColor.YELLOW));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ForgeHolder) && !(holder instanceof RecipeHolder)) {
            return;
        }
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (holder instanceof ForgeHolder) {
            switch (rawSlot) {
                case 10 -> {
                    player.closeInventory();
                    forge.upgrade(player);
                }
                case 12 -> {
                    player.closeInventory();
                    forge.reroll(player);
                }
                case 14 -> {
                    player.closeInventory();
                    items.inspect(player, player.getInventory().getItemInMainHand());
                }
                case 16 -> openRecipes(player);
                case 22 -> player.closeInventory();
                default -> { }
            }
            return;
        }

        RecipeHolder recipeHolder = (RecipeHolder) holder;
        if (rawSlot == 49) {
            open(player);
            return;
        }
        String itemId = recipeHolder.recipeBySlot.get(rawSlot);
        if (itemId == null) {
            return;
        }
        UniqueItemDefinition definition = content.uniqueItem(itemId);
        if (definition == null) {
            return;
        }
        player.closeInventory();
        forge.craftUnique(player, itemId);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ForgeHolder || holder instanceof RecipeHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack upgradeMenuItem(Player player) {
        ForgeService.UpgradeStatus status = forge.upgradeStatus(player);
        List<Component> lore = new ArrayList<>();
        lore.add(gray("Held: " + status.heldLabel()));

        if (!status.supported()) {
            lore.add(red("[X] Hold a sword, axe, trident or mace."));
            return menuItem(Material.ANVIL, "Upgrade", NamedTextColor.RED, lore);
        }

        lore.add(line("Current: " + status.current().name(), status.current().color()));
        if (status.blueprintStage()) {
            lore.add(gold("Next: Blueprint forging"));
            lore.add(yellow("Use Blueprint Recipes for Legendary/Mythic items."));
            return menuItem(Material.ANVIL, "Upgrade", NamedTextColor.GOLD, lore);
        }

        lore.add(line("Next: " + status.next().name(), status.next().color()));
        lore.add(Component.empty());
        lore.add(yellow("Requirements:"));
        appendCostStatus(lore, status.cost());
        lore.add(Component.empty());
        lore.add(status.ready() ? green("READY - click to upgrade.") : red("Missing requirements."));
        return menuItem(Material.ANVIL, "Upgrade", status.ready() ? NamedTextColor.GREEN : NamedTextColor.RED, lore);
    }

    private ItemStack recipeIcon(Player player, UniqueItemDefinition definition, boolean learned) {
        ForgeService.UniqueCraftStatus status = forge.uniqueStatus(player, definition);
        Material material = learned ? definition.material() : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = learned ? definition.rarity().color() : NamedTextColor.DARK_GRAY;
        String prefix = learned ? "\u2726 " : "LOCKED: ";
        meta.displayName(Component.text(prefix + definition.name(), color)
                .decorate(learned ? TextDecoration.BOLD : TextDecoration.ITALIC)
                .decoration(TextDecoration.ITALIC, false));
        if (learned && definition.rarity().isEndgame()) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(line("Rarity: " + definition.rarity().name(), definition.rarity().color()));
        var blueprint = content.blueprint(definition.blueprintId());
        String blueprintName = blueprint == null ? definition.blueprintId() : blueprint.name();
        lore.add(status.blueprintLearned()
                ? green("[OK] Learned: " + blueprintName)
                : red("[X] Missing: " + blueprintName));
        lore.add(Component.empty());

        lore.add(yellow("Base item:"));
        lore.add(status.base().met()
                ? green("[OK] Need: " + status.base().required())
                : red("[X] Need: " + status.base().required()));
        lore.add(gray("     Held: " + status.base().held()));

        lore.add(Component.empty());
        lore.add(yellow("Costs - have / need:"));
        appendCostStatus(lore, status.cost());

        lore.add(Component.empty());
        if (status.ready()) {
            lore.add(green("READY TO FORGE"));
            lore.add(green("Click to forge using the held base item."));
        } else {
            lore.add(red("MISSING REQUIREMENTS"));
            lore.add(gray("Red lines show exactly what is missing."));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void appendCostStatus(List<Component> lore, ForgeService.CostStatus status) {
        lore.add(resourceLine("Coin", status.coin(), NamedTextColor.GOLD));
        lore.add(resourceLine("Token", status.token(), NamedTextColor.AQUA));
        status.vanilla().forEach((name, value) ->
                lore.add(resourceLine(name, value, NamedTextColor.GRAY)));
        status.custom().forEach((id, value) ->
                lore.add(resourceLine(forge.displayCustomMaterial(id), value, NamedTextColor.AQUA)));
    }

    private Component resourceLine(String name, ForgeService.ResourceStatus status, NamedTextColor metColor) {
        NamedTextColor color = status.met() ? metColor : NamedTextColor.RED;
        String prefix = status.met() ? "[OK] " : "[X] ";
        return line(prefix + name + ": " + status.have() + " / " + status.need(), color);
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, String... description) {
        List<Component> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(gray(line));
        }
        return menuItem(material, name, color, lore);
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component gray(String text) {
        return line(text, NamedTextColor.GRAY);
    }

    private Component green(String text) {
        return line(text, NamedTextColor.GREEN);
    }

    private Component red(String text) {
        return line(text, NamedTextColor.RED);
    }

    private Component gold(String text) {
        return line(text, NamedTextColor.GOLD);
    }

    private Component yellow(String text) {
        return line(text, NamedTextColor.YELLOW);
    }

    private boolean isBorderSlot(int slot, int size) {
        if (slot < 9 || slot >= size - 9) {
            return true;
        }
        int column = slot % 9;
        return column == 0 || column == 8;
    }

    private static final class ForgeHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class RecipeHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> recipeBySlot = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
