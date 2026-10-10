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
import vn.vietanhrpg.core.data.Database;

import java.util.List;

public final class ParticleGuiService implements Listener {
    private final Database database;

    public ParticleGuiService(Database database) {
        this.database = database;
    }

    public void open(Player player) {
        ParticleHolder holder = new ParticleHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("RPG Particle Settings", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        inventory.setItem(10, toggleItem(Material.BLAZE_POWDER, "Legendary Weapon Glow", database.getSetting(player.getUniqueId(), "legendary")));
        inventory.setItem(11, toggleItem(Material.REDSTONE, "Mythic Weapon Trail", database.getSetting(player.getUniqueId(), "mythic")));
        inventory.setItem(12, toggleItem(Material.IRON_SWORD, "Combat Particles", database.getSetting(player.getUniqueId(), "combat")));
        inventory.setItem(13, toggleItem(Material.FIREWORK_STAR, "Craft Effects", database.getSetting(player.getUniqueId(), "craft")));
        inventory.setItem(14, toggleItem(Material.AMETHYST_SHARD, "Forging Material Glow", database.getSetting(player.getUniqueId(), "materials")));
        inventory.setItem(15, toggleItem(Material.ENDER_EYE, "Show Other Players' RPG FX", database.getSetting(player.getUniqueId(), "others")));
        inventory.setItem(20, actionItem(Material.LIME_DYE, "Enable All", NamedTextColor.GREEN));
        inventory.setItem(24, actionItem(Material.GRAY_DYE, "Disable All", NamedTextColor.RED));
        player.openInventory(inventory);
    }

    public void setAll(Player player, boolean enabled) {
        for (String setting : List.of("legendary", "mythic", "combat", "craft", "materials", "others")) {
            database.setSetting(player.getUniqueId(), setting, enabled);
        }
    }

    public void set(Player player, String setting, boolean enabled) {
        database.setSetting(player.getUniqueId(), setting, enabled);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ParticleHolder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (slot) {
            case 10 -> toggle(player, "legendary");
            case 11 -> toggle(player, "mythic");
            case 12 -> toggle(player, "combat");
            case 13 -> toggle(player, "craft");
            case 14 -> toggle(player, "materials");
            case 15 -> toggle(player, "others");
            case 20 -> setAll(player, true);
            case 24 -> setAll(player, false);
            default -> { return; }
        }
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ParticleHolder) {
            event.setCancelled(true);
        }
    }

    private void toggle(Player player, String setting) {
        boolean current = database.getSetting(player.getUniqueId(), setting);
        database.setSetting(player.getUniqueId(), setting, !current);
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor stateColor = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        meta.displayName(Component.text(name, NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Status: " + (enabled ? "ON" : "OFF"), stateColor).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to toggle.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static final class ParticleHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
