package vn.vietanhrpg.core.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemService {
    private final JavaPlugin plugin;
    private final NamespacedKey rarityKey;
    private final NamespacedKey uuidKey;

    public ItemService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rarityKey = new NamespacedKey(plugin, "rarity");
        this.uuidKey = new NamespacedKey(plugin, "item_uuid");
    }

    public boolean isSupportedWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.equals("TRIDENT")
                || name.equals("MACE");
    }

    public Rarity rarity(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Rarity.COMMON;
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        if (stored == null) {
            return Rarity.COMMON;
        }
        try {
            return Rarity.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return Rarity.COMMON;
        }
    }

    public void applyRarity(ItemStack item, Rarity rarity) {
        if (!isSupportedWeapon(item)) {
            throw new IllegalArgumentException("Only swords, axes, tridents and maces are supported in V1");
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(rarityKey, PersistentDataType.STRING, rarity.name());
        if (!pdc.has(uuidKey, PersistentDataType.STRING)) {
            pdc.set(uuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }

        clearAffixes(pdc);
        List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
        Collections.shuffle(pool);
        int count = Math.min(rarity.affixCount(), pool.size());
        for (int i = 0; i < count; i++) {
            Affix affix = pool.get(i);
            pdc.set(key(affix), PersistentDataType.DOUBLE, roll(affix));
        }

        meta.displayName(Component.text("[" + rarity.name() + "] ", rarity.color())
                .append(Component.text(prettyMaterial(item.getType()), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(buildLore(pdc, rarity));
        item.setItemMeta(meta);
    }

    public double affix(ItemStack item, Affix affix) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return 0.0;
        }
        Double value = item.getItemMeta().getPersistentDataContainer().get(key(affix), PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    public void inspect(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            player.sendMessage(Component.text("Hold an item first.", NamedTextColor.RED));
            return;
        }
        Rarity rarity = rarity(item);
        player.sendMessage(Component.text("--- Item Inspect ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Type: " + item.getType().name(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Rarity: " + rarity.name(), rarity.color()));
        if (item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String uuid = pdc.get(uuidKey, PersistentDataType.STRING);
            if (uuid != null) {
                player.sendMessage(Component.text("UUID: " + uuid, NamedTextColor.DARK_GRAY));
            }
            for (Affix affix : Affix.values()) {
                Double value = pdc.get(key(affix), PersistentDataType.DOUBLE);
                if (value != null) {
                    player.sendMessage(Component.text(affix.display() + ": " + affix.formatted(value), NamedTextColor.AQUA));
                }
            }
        }
    }

    private List<Component> buildLore(PersistentDataContainer pdc, Rarity rarity) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(rarity.name(), rarity.color()).decoration(TextDecoration.ITALIC, false));
        if (rarity != Rarity.COMMON) {
            lore.add(Component.empty());
        }
        for (Affix affix : Affix.values()) {
            Double value = pdc.get(key(affix), PersistentDataType.DOUBLE);
            if (value != null) {
                lore.add(Component.text(affix.display() + " " + affix.formatted(value), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text("VanillaRPG", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private void clearAffixes(PersistentDataContainer pdc) {
        for (Affix affix : Affix.values()) {
            pdc.remove(key(affix));
        }
    }

    private NamespacedKey key(Affix affix) {
        return new NamespacedKey(plugin, "affix_" + affix.id());
    }

    private double roll(Affix affix) {
        String path = "affixes." + affix.id();
        double min = plugin.getConfig().getDouble(path + ".min", 1.0);
        double max = plugin.getConfig().getDouble(path + ".max", min);
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
        double value = ThreadLocalRandom.current().nextDouble(min, max + 0.0001);
        return Math.round(value * 10.0) / 10.0;
    }

    private String prettyMaterial(Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(raw.length());
        boolean upper = true;
        for (char c : raw.toCharArray()) {
            if (upper && Character.isLetter(c)) {
                builder.append(Character.toUpperCase(c));
                upper = false;
            } else {
                builder.append(c);
            }
            if (c == ' ') {
                upper = true;
            }
        }
        return builder.toString();
    }
}
