package vn.vietanhrpg.core.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Optional BetterStructures integration. The BetterStructures API is deliberately
 * accessed reflectively so VanillaRPG-Core remains buildable and usable without it.
 */
public final class BetterStructuresLootBridge {
    private final JavaPlugin plugin;
    private final ItemService items;
    private final ContentService content;
    private final NamespacedKey filledKey;
    private final Listener listener = new Listener() { };
    private final Set<String> pendingContainers = ConcurrentHashMap.newKeySet();

    public BetterStructuresLootBridge(JavaPlugin plugin, ItemService items, ContentService content) {
        this.plugin = plugin;
        this.items = items;
        this.content = content;
        this.filledKey = new NamespacedKey(plugin, "betterstructures_rpg_loot");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerIfAvailable() {
        if (!plugin.getConfig().getBoolean("betterstructures-loot.enabled", true)) {
            plugin.getLogger().info("BetterStructures RPG loot bridge is disabled in config.yml.");
            return;
        }

        Plugin betterStructures = Bukkit.getPluginManager().getPlugin("BetterStructures");
        if (betterStructures == null || !betterStructures.isEnabled()) {
            plugin.getLogger().info("BetterStructures not detected; RPG chest-loot integration is inactive.");
            return;
        }

        try {
            ClassLoader loader = betterStructures.getClass().getClassLoader();
            Class<?> rawEventClass = Class.forName("com.magmaguy.betterstructures.api.ChestFillEvent", true, loader);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                throw new IllegalStateException("ChestFillEvent is not a Bukkit Event");
            }

            Method containerMethod = rawEventClass.getMethod("getContainer");
            Method treasureMethod = rawEventClass.getMethod("getTreasureConfigFilename");
            EventExecutor executor = (ignored, event) -> handleChestFill(event, containerMethod, treasureMethod);
            PluginManager manager = Bukkit.getPluginManager();
            manager.registerEvent((Class) rawEventClass, listener, EventPriority.MONITOR, executor, plugin, false);
            plugin.getLogger().info("Enabled BetterStructures RPG chest-loot integration.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not hook BetterStructures chest loot: " + exception.getMessage());
        }
    }

    /**
     * Adds this plugin's supplemental loot to one administrator-selected container.
     * This deliberately never scans an area: a radius scan cannot reliably separate
     * an old structure container from a player's own chest.
     */
    public RefreshResult refreshTarget(Player player, String tierInput) {
        if (!plugin.getConfig().getBoolean("betterstructures-loot.enabled", true)
                || !plugin.getConfig().getBoolean("betterstructures-loot.manual-refresh-enabled", true)) {
            return RefreshResult.DISABLED;
        }

        LootTier tier = LootTier.fromCommandValue(tierInput);
        if (tier == null) {
            return RefreshResult.INVALID_TIER;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof Container container)) {
            return RefreshResult.NOT_A_CONTAINER;
        }
        if (container.getPersistentDataContainer().has(filledKey, PersistentDataType.BYTE)) {
            return RefreshResult.ALREADY_REFRESHED;
        }

        addRpgLoot(container, tier);
        applyCommonPresentation(container);
        container.getPersistentDataContainer().set(filledKey, PersistentDataType.BYTE, (byte) 1);
        container.update();
        return RefreshResult.SUCCESS;
    }

    private void handleChestFill(Event event, Method containerMethod, Method treasureMethod) {
        try {
            Object rawContainer = containerMethod.invoke(event);
            Object rawTreasure = treasureMethod.invoke(event);
            if (!(rawContainer instanceof Container container) || !(rawTreasure instanceof String treasureFile)) {
                return;
            }

            LootTier tier = LootTier.fromTreasureFile(treasureFile);
            if (tier == null || container.getPersistentDataContainer().has(filledKey, PersistentDataType.BYTE)) {
                return;
            }

            String locationKey = container.getWorld().getUID() + ":" + container.getX() + ":" + container.getY() + ":" + container.getZ();
            if (!pendingContainers.add(locationKey)) {
                return;
            }

            // BetterStructures may still be writing its own table during the event.
            // Add VanillaRPG rewards on the next tick so both tables are preserved.
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    addRpgLoot(container, tier);
                    applyCommonPresentation(container);
                    container.getPersistentDataContainer().set(filledKey, PersistentDataType.BYTE, (byte) 1);
                    container.update();
                } finally {
                    pendingContainers.remove(locationKey);
                }
            });
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Could not process BetterStructures chest loot: " + exception.getMessage());
        }
    }

    private void addRpgLoot(Container container, LootTier tier) {
        Inventory inventory = container.getInventory();
        String base = "betterstructures-loot." + tier.configKey;

        addCustomIfRolled(inventory, "epic_shard", plugin.getConfig().getDouble(base + ".epic-shard-chance", 0.0),
                plugin.getConfig().getInt(base + ".epic-shard-min", 1), plugin.getConfig().getInt(base + ".epic-shard-max", 1));
        addCustomIfRolled(inventory, "legendary_shard", plugin.getConfig().getDouble(base + ".legendary-shard-chance", 0.0),
                plugin.getConfig().getInt(base + ".legendary-shard-min", 1), plugin.getConfig().getInt(base + ".legendary-shard-max", 1));
        addCustomIfRolled(inventory, "mythic_core", plugin.getConfig().getDouble(base + ".mythic-core-chance", 0.0),
                1, 1);

        addVanillaIfRolled(inventory, Material.ECHO_SHARD, plugin.getConfig().getDouble(base + ".echo-shard-chance", 0.0),
                plugin.getConfig().getInt(base + ".echo-shard-min", 1), plugin.getConfig().getInt(base + ".echo-shard-max", 1));
        addVanillaIfRolled(inventory, Material.HEAVY_CORE, plugin.getConfig().getDouble(base + ".heavy-core-chance", 0.0), 1, 1);
        addVanillaIfRolled(inventory, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                plugin.getConfig().getDouble(base + ".netherite-template-chance", 0.0), 1, 1);

        addGearIfRolled(inventory, tier, Rarity.COMMON, plugin.getConfig().getDouble(base + ".common-gear-chance", 0.0));
        addGearIfRolled(inventory, tier, Rarity.UNCOMMON, plugin.getConfig().getDouble(base + ".uncommon-gear-chance", 0.0));
        addGearIfRolled(inventory, tier, Rarity.RARE, plugin.getConfig().getDouble(base + ".rare-gear-chance", 0.0));
        addGearIfRolled(inventory, tier, Rarity.EPIC, plugin.getConfig().getDouble(base + ".epic-gear-chance", 0.0));
    }

    private void applyCommonPresentation(Container container) {
        for (ItemStack stack : container.getInventory().getContents()) {
            items.applyCommonRarityIfNeeded(stack);
        }
    }

    private void addCustomIfRolled(Inventory inventory, String id, double chance, int min, int max) {
        if (!roll(chance)) {
            return;
        }
        MaterialDefinition definition = content.material(id);
        if (definition != null) {
            addIfSpace(inventory, items.createCustomMaterial(definition, amount(min, max)));
        }
    }

    private void addVanillaIfRolled(Inventory inventory, Material material, double chance, int min, int max) {
        if (roll(chance)) {
            addIfSpace(inventory, new ItemStack(material, amount(min, max)));
        }
    }

    private void addGearIfRolled(Inventory inventory, LootTier tier, Rarity rarity, double chance) {
        if (!roll(chance)) {
            return;
        }
        ItemStack weapon = new ItemStack(randomWeaponMaterial(tier, rarity));
        items.applyRarity(weapon, rarity);
        addIfSpace(inventory, weapon);
    }

    private Material randomWeaponMaterial(LootTier tier, Rarity rarity) {
        Material[] options = switch (rarity) {
            case COMMON -> new Material[]{Material.STONE_SWORD, Material.STONE_AXE};
            case UNCOMMON -> new Material[]{Material.IRON_SWORD, Material.IRON_AXE};
            case RARE -> new Material[]{Material.DIAMOND_SWORD, Material.DIAMOND_AXE};
            case EPIC -> tier == LootTier.HIGH_DUNGEON
                    ? new Material[]{Material.NETHERITE_SWORD, Material.NETHERITE_AXE}
                    : new Material[]{Material.DIAMOND_SWORD, Material.DIAMOND_AXE};
            default -> new Material[]{Material.IRON_SWORD};
        };
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    private void addIfSpace(Inventory inventory, ItemStack item) {
        if (inventory.firstEmpty() >= 0) {
            inventory.addItem(item);
        }
    }

    private static boolean roll(double chance) {
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, chance);
    }

    private static int amount(int min, int max) {
        int lower = Math.max(1, Math.min(min, max));
        int upper = Math.max(lower, Math.max(min, max));
        return ThreadLocalRandom.current().nextInt(lower, upper + 1);
    }

    public enum RefreshResult {
        SUCCESS,
        DISABLED,
        INVALID_TIER,
        NOT_A_CONTAINER,
        ALREADY_REFRESHED
    }

    private enum LootTier {
        SURFACE("surface"),
        UNDERGROUND("underground"),
        DUNGEON("dungeon"),
        NETHER("nether"),
        END("end"),
        HIGH_DUNGEON("high-dungeon");

        private final String configKey;

        LootTier(String configKey) {
            this.configKey = configKey;
        }

        private static LootTier fromTreasureFile(String filename) {
            String value = filename.toLowerCase(Locale.ROOT);
            if (value.contains("special")) return HIGH_DUNGEON;
            if (value.contains("barrel_food")) return DUNGEON;
            if (value.contains("nether")) return NETHER;
            if (value.contains("end")) return END;
            if (value.contains("underground")) return UNDERGROUND;
            if (value.contains("surface")) return SURFACE;
            return null;
        }

        private static LootTier fromCommandValue(String value) {
            String normalized = value.toLowerCase(Locale.ROOT).replace('_', '-');
            for (LootTier tier : values()) {
                if (tier.configKey.equals(normalized)) {
                    return tier;
                }
            }
            return null;
        }
    }
}
