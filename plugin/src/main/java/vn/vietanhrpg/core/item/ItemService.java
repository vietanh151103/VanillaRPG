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
import vn.vietanhrpg.core.content.BlueprintDefinition;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.content.UniqueItemDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemService {
    private final JavaPlugin plugin;
    private final NamespacedKey rarityKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey materialIdKey;
    private final NamespacedKey blueprintIdKey;

    public ItemService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rarityKey = new NamespacedKey(plugin, "rarity");
        this.uuidKey = new NamespacedKey(plugin, "item_uuid");
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.materialIdKey = new NamespacedKey(plugin, "material_id");
        this.blueprintIdKey = new NamespacedKey(plugin, "blueprint_id");
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

    /**
     * Equipment which should receive the RPG rarity presentation. Weapons keep
     * their existing combat support; armour, bows and crossbows are included so
     * ordinary Vanilla equipment can consistently be shown as COMMON too.
     */
    public boolean isSupportedGear(ItemStack item) {
        if (isSupportedWeapon(item)) {
            return true;
        }
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String name = item.getType().name();
        return name.equals("BOW")
                || name.equals("CROSSBOW")
                || name.equals("TURTLE_HELMET")
                || name.equals("ELYTRA")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    /** Adds the COMMON presentation only to ordinary, unclassified equipment. */
    public boolean applyCommonRarityIfNeeded(ItemStack item) {
        if (!isSupportedGear(item) || hasStoredRarity(item)) {
            return false;
        }
        applyRarity(item, Rarity.COMMON);
        return true;
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

    private boolean hasStoredRarity(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(rarityKey, PersistentDataType.STRING);
    }

    public String itemId(ItemStack item) {
        return readString(item, itemIdKey);
    }

    public String customMaterialId(ItemStack item) {
        return readString(item, materialIdKey);
    }

    public String blueprintId(ItemStack item) {
        return readString(item, blueprintIdKey);
    }

    public boolean isCustomMaterial(ItemStack item, String id) {
        String stored = customMaterialId(item);
        return stored != null && stored.equalsIgnoreCase(id);
    }

    public void applyRarity(ItemStack item, Rarity rarity) {
        if (!isSupportedGear(item)) {
            throw new IllegalArgumentException("Only weapons, bows, crossbows and armour are supported");
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(rarityKey, PersistentDataType.STRING, rarity.name());
        if (!pdc.has(uuidKey, PersistentDataType.STRING)) {
            pdc.set(uuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        }

        // Generic upgrades must never inherit the identity of a named endgame item.
        pdc.remove(itemIdKey);
        clearAffixes(pdc);
        List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
        Collections.shuffle(pool);
        int count = Math.min(rarity.affixCount(), pool.size());
        for (int i = 0; i < count; i++) {
            Affix affix = pool.get(i);
            pdc.set(key(affix), PersistentDataType.DOUBLE, roll(affix));
        }

        meta.setEnchantmentGlintOverride(rarity.isEndgame() ? Boolean.TRUE : null);
        meta.displayName(genericDisplayName(item.getType(), rarity));
        meta.lore(buildGenericLore(pdc, rarity));
        item.setItemMeta(meta);
    }

    public ItemStack createCustomMaterial(MaterialDefinition definition, int amount) {
        ItemStack item = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(materialIdKey, PersistentDataType.STRING, definition.id());
        meta.displayName(Component.text(definition.name(), definition.color())
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        if (definition.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✧ RPG FORGING MATERIAL ✧", definition.color())
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("━━━━━━━━━━━━━━━━━━━━", definition.color())
                .decoration(TextDecoration.ITALIC, false));
        for (String line : definition.lore()) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("━━━━━━━━━━━━━━━━━━━━", definition.color())
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("VanillaRPG • " + definition.id(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBlueprint(BlueprintDefinition definition) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(blueprintIdKey, PersistentDataType.STRING, definition.id());
        meta.displayName(Component.text(definition.name(), definition.color())
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to learn this recipe.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Blueprint ID: " + definition.id(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createUniqueItem(UniqueItemDefinition definition) {
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(rarityKey, PersistentDataType.STRING, definition.rarity().name());
        pdc.set(itemIdKey, PersistentDataType.STRING, definition.id());
        pdc.set(uuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        clearAffixes(pdc);
        for (Map.Entry<Affix, Double> entry : definition.affixes().entrySet()) {
            pdc.set(key(entry.getKey()), PersistentDataType.DOUBLE, entry.getValue());
        }

        meta.setEnchantmentGlintOverride(definition.rarity().isEndgame() ? Boolean.TRUE : null);
        applyUniqueItemModel(meta, definition.id());
        meta.displayName(uniqueDisplayName(definition));
        meta.lore(buildUniqueLore(pdc, definition));
        item.setItemMeta(meta);
        return item;
    }

    public boolean refreshUniquePresentation(ItemStack item, UniqueItemDefinition definition) {
        if (item == null || item.getType().isAir() || definition == null || !item.hasItemMeta()) {
            return false;
        }
        String storedId = itemId(item);
        if (storedId == null || !storedId.equalsIgnoreCase(definition.id())) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        meta.setEnchantmentGlintOverride(definition.rarity().isEndgame() ? Boolean.TRUE : null);
        applyUniqueItemModel(meta, definition.id());
        meta.displayName(uniqueDisplayName(definition));
        meta.lore(buildUniqueLore(pdc, definition));
        item.setItemMeta(meta);
        return true;
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
        String id = itemId(item);
        if (id != null) {
            player.sendMessage(Component.text("Item ID: " + id, NamedTextColor.YELLOW));
        }
        String materialId = customMaterialId(item);
        if (materialId != null) {
            player.sendMessage(Component.text("Material ID: " + materialId, NamedTextColor.AQUA));
        }
        String blueprint = blueprintId(item);
        if (blueprint != null) {
            player.sendMessage(Component.text("Blueprint ID: " + blueprint, NamedTextColor.GOLD));
        }
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


    private Component genericDisplayName(Material type, Rarity rarity) {
        if (rarity == Rarity.LEGENDARY) {
            return Component.text("✦ [LEGENDARY] ✦ ", rarity.color())
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text(prettyMaterial(type), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false);
        }
        if (rarity == Rarity.MYTHIC) {
            return Component.text("✦✦ [MYTHIC] ✦✦ ", rarity.color())
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text(prettyMaterial(type), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false);
        }
        return Component.text("[" + rarity.name() + "] ", rarity.color())
                .append(Component.text(prettyMaterial(type), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private void applyUniqueItemModel(ItemMeta meta, String itemId) {
        if (itemId == null) {
            return;
        }
        switch (itemId.toLowerCase(Locale.ROOT)) {
            case "ashbringer" -> meta.setItemModel(new NamespacedKey("vanillarpg", "hero_sword"));
            case "bloodreaver" -> meta.setItemModel(new NamespacedKey("vanillarpg", "demonic_blade"));
            default -> { }
        }
    }

    private Component uniqueDisplayName(UniqueItemDefinition definition) {
        return Component.text(definition.name(), definition.rarity().color())
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> buildGenericLore(PersistentDataContainer pdc, Rarity rarity) {
        List<Component> lore = new ArrayList<>();
        if (rarity.isEndgame()) {
            lore.add(Component.text("━━━━━━━━━━━━━━━━━━━━", rarity.color())
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(rarity.name(), rarity.color())
                .decorate(rarity.isEndgame() ? TextDecoration.BOLD : TextDecoration.ITALIC)
                .decoration(TextDecoration.ITALIC, false));
        if (rarity != Rarity.COMMON) {
            lore.add(Component.empty());
        }
        appendAffixes(lore, pdc);
        if (rarity.isEndgame()) {
            lore.add(Component.text("━━━━━━━━━━━━━━━━━━━━", rarity.color())
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("VanillaRPG", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private List<Component> buildUniqueLore(PersistentDataContainer pdc, UniqueItemDefinition definition) {
        List<Component> lore = new ArrayList<>();
        NamedTextColor color = definition.rarity().color();
        lore.add(Component.text("━━━━━━━━━━━━━━━━━━━━", color).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(definition.rarity().name() + " UNIQUE", color)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        appendAffixes(lore, pdc);
        if (!definition.passiveLore().isEmpty()) {
            lore.add(Component.empty());
            for (int i = 0; i < definition.passiveLore().size(); i++) {
                String line = definition.passiveLore().get(i);
                NamedTextColor lineColor = i == 0 ? color : NamedTextColor.GRAY;
                Component component = Component.text(line, lineColor).decoration(TextDecoration.ITALIC, false);
                if (i == 0) {
                    component = component.decorate(TextDecoration.BOLD);
                }
                lore.add(component);
            }
        }
        lore.add(Component.text("━━━━━━━━━━━━━━━━━━━━", color).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("VanillaRPG • " + definition.id(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private void appendAffixes(List<Component> lore, PersistentDataContainer pdc) {
        for (Affix affix : Affix.values()) {
            Double value = pdc.get(key(affix), PersistentDataType.DOUBLE);
            if (value != null) {
                lore.add(Component.text(affix.display() + " " + affix.formatted(value), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
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

    private String readString(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
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
