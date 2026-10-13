package vn.vietanhrpg.core.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Adds balanced VanillaRPG rewards to vanilla/TerraformGenerator loot tables. */
public final class VanillaLootBridge implements Listener {
    private final JavaPlugin plugin;
    private final ItemService items;
    private final ContentService content;
    private final NamespacedKey injectedKey;

    public VanillaLootBridge(JavaPlugin plugin, ItemService items, ContentService content) {
        this.plugin = plugin;
        this.items = items;
        this.content = content;
        this.injectedKey = new NamespacedKey(plugin, "vanilla_loot_rpg_rewards");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!plugin.getConfig().getBoolean("vanilla-loot.enabled", true)) {
            return;
        }

        LootTier tier = LootTier.from(event.getLootTable().getKey().getKey());
        if (tier == null) {
            return;
        }

        InventoryHolder holder = event.getInventoryHolder();
        if (holder instanceof Container container
                && container.getPersistentDataContainer().has(injectedKey, PersistentDataType.BYTE)) {
            return;
        }

        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        int before = loot.size();
        String base = "betterstructures-loot." + tier.configKey;
        int maxExtra = Math.max(1, plugin.getConfig().getInt("vanilla-loot.max-extra-items", 2));

        addCustomIfRolled(loot, "epic_shard", base + ".epic-shard-chance", base + ".epic-shard-min", base + ".epic-shard-max");
        addCustomIfRolled(loot, "legendary_shard", base + ".legendary-shard-chance", base + ".legendary-shard-min", base + ".legendary-shard-max");
        addCustomIfRolled(loot, "mythic_core", base + ".mythic-core-chance", null, null);
        addVanillaIfRolled(loot, Material.ECHO_SHARD, base + ".echo-shard-chance", base + ".echo-shard-min", base + ".echo-shard-max");
        addVanillaIfRolled(loot, Material.HEAVY_CORE, base + ".heavy-core-chance", null, null);
        addVanillaIfRolled(loot, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, base + ".netherite-template-chance", null, null);
        addGearIfRolled(loot, tier, Rarity.COMMON, base + ".common-gear-chance");
        addGearIfRolled(loot, tier, Rarity.UNCOMMON, base + ".uncommon-gear-chance");
        addGearIfRolled(loot, tier, Rarity.RARE, base + ".rare-gear-chance");
        addGearIfRolled(loot, tier, Rarity.EPIC, base + ".epic-gear-chance");

        if (loot.size() > before + maxExtra) {
            loot.subList(before + maxExtra, loot.size()).clear();
        }
        if (loot.size() > before) {
            event.setLoot(loot);
            if (holder instanceof Container container) {
                container.getPersistentDataContainer().set(injectedKey, PersistentDataType.BYTE, (byte) 1);
                container.update();
            }
        }
    }

    private void addCustomIfRolled(List<ItemStack> loot, String id, String chancePath, String minPath, String maxPath) {
        if (!roll(plugin.getConfig().getDouble(chancePath, 0.0))) return;
        MaterialDefinition definition = content.material(id);
        if (definition == null) return;
        int min = minPath == null ? 1 : plugin.getConfig().getInt(minPath, 1);
        int max = maxPath == null ? min : plugin.getConfig().getInt(maxPath, min);
        loot.add(items.createCustomMaterial(definition, amount(min, max)));
    }

    private void addVanillaIfRolled(List<ItemStack> loot, Material material, String chancePath, String minPath, String maxPath) {
        if (!roll(plugin.getConfig().getDouble(chancePath, 0.0))) return;
        int min = minPath == null ? 1 : plugin.getConfig().getInt(minPath, 1);
        int max = maxPath == null ? min : plugin.getConfig().getInt(maxPath, min);
        loot.add(new ItemStack(material, amount(min, max)));
    }

    private void addGearIfRolled(List<ItemStack> loot, LootTier tier, Rarity rarity, String chancePath) {
        if (!roll(plugin.getConfig().getDouble(chancePath, 0.0))) return;
        Material[] options = switch (rarity) {
            case COMMON -> new Material[]{Material.STONE_SWORD, Material.STONE_AXE};
            case UNCOMMON -> new Material[]{Material.IRON_SWORD, Material.IRON_AXE};
            case RARE -> new Material[]{Material.DIAMOND_SWORD, Material.DIAMOND_AXE};
            case EPIC -> tier == LootTier.HIGH_DUNGEON
                    ? new Material[]{Material.NETHERITE_SWORD, Material.NETHERITE_AXE}
                    : new Material[]{Material.DIAMOND_SWORD, Material.DIAMOND_AXE};
            default -> new Material[]{Material.IRON_SWORD};
        };
        ItemStack gear = new ItemStack(options[ThreadLocalRandom.current().nextInt(options.length)]);
        items.applyRarity(gear, rarity);
        loot.add(gear);
    }

    private static boolean roll(double chance) {
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, chance);
    }

    private static int amount(int min, int max) {
        int lower = Math.max(1, Math.min(min, max));
        int upper = Math.max(lower, Math.max(min, max));
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }

    private enum LootTier {
        SURFACE("surface"), UNDERGROUND("underground"), DUNGEON("dungeon"), NETHER("nether"), END("end"), HIGH_DUNGEON("high-dungeon");

        private final String configKey;

        LootTier(String configKey) { this.configKey = configKey; }

        private static LootTier from(String key) {
            String value = key.toLowerCase(Locale.ROOT);
            if (!value.startsWith("chests/")) return null;
            if (value.contains("ancient_city") || value.contains("end_city") || value.contains("trial_chambers")) return HIGH_DUNGEON;
            if (value.contains("bastion") || value.contains("nether_bridge") || value.contains("nether_fortress")) return NETHER;
            if (value.contains("stronghold") || value.contains("dungeon") || value.contains("mineshaft")) return DUNGEON;
            if (value.contains("shipwreck") || value.contains("buried_treasure") || value.contains("village")
                    || value.contains("desert_pyramid") || value.contains("jungle_temple") || value.contains("igloo")
                    || value.contains("underwater_ruin") || value.contains("ruins")) return SURFACE;
            return UNDERGROUND;
        }
    }
}
