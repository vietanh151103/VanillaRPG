package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.content.UniqueItemDefinition;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.progression.ProgressionService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ForgeService {
    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;
    private final ContentService content;
    private final BlueprintService blueprints;
    private ParticleService particles;
    private ProgressionService progression;

    public ForgeService(JavaPlugin plugin, Database database, EconomyService economy, ItemService items,
                        ContentService content, BlueprintService blueprints) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
        this.content = content;
        this.blueprints = blueprints;
    }

    public void setParticleService(ParticleService particles) {
        this.particles = particles;
    }

    public void setProgressionService(ProgressionService progression) {
        this.progression = progression;
    }

    public void upgrade(Player player) {
        UpgradeStatus status = upgradeStatus(player);
        if (!status.supported()) {
            player.sendMessage(Component.text("Cannot upgrade the held item.", NamedTextColor.RED));
            player.sendMessage(Component.text("Held: " + status.heldLabel(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("Required: sword, axe, trident or mace.", NamedTextColor.YELLOW));
            return;
        }

        if (status.blueprintStage()) {
            player.sendMessage(Component.text("This item has reached the Blueprint progression stage.", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Held: " + status.heldLabel(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("Open /forge -> Blueprint Recipes for Legendary/Mythic forging.", NamedTextColor.YELLOW));
            return;
        }

        if (!status.cost().met()) {
            sendCostFailure(player, "Cannot upgrade " + status.current().name() + " -> " + status.next().name(), status.cost());
            return;
        }

        Cost cost = loadUpgradeCost(status.current(), status.next());
        pay(player, cost);
        ItemStack held = player.getInventory().getItemInMainHand();
        items.applyRarity(held, status.next());
        database.incrementCraft(player.getUniqueId(), status.next());
        if (progression != null) progression.recordForge(player, status.next());
        player.sendMessage(Component.text("Upgraded to " + status.next().name() + ".", status.next().color()));
    }

    public void reroll(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.isSupportedWeapon(held)) {
            player.sendMessage(Component.text("Cannot reroll the held item.", NamedTextColor.RED));
            player.sendMessage(Component.text("Held: " + describeHeld(held), NamedTextColor.GRAY));
            player.sendMessage(Component.text("Required: sword, axe, trident or mace.", NamedTextColor.YELLOW));
            return;
        }
        Rarity rarity = items.rarity(held);
        if (rarity == Rarity.COMMON) {
            player.sendMessage(Component.text("Common items have no affixes to reroll.", NamedTextColor.RED));
            return;
        }
        if (items.itemId(held) != null && rarity.isEndgame()
                && plugin.getConfig().getBoolean("forge.lock-named-endgame-reroll", true)) {
            player.sendMessage(Component.text("Named Legendary/Mythic items keep fixed endgame stats in 0.2.", NamedTextColor.GOLD));
            return;
        }

        long coin = plugin.getConfig().getLong("forge.reroll-cost." + rarity.name(), 500);
        Map<String, Integer> custom = readCustomRequirements(
                plugin.getConfig().getConfigurationSection("forge.reroll-custom-materials." + rarity.name()));
        Cost cost = new Cost(Math.max(0, coin), 0, Map.of(), custom);
        CostStatus costStatus = costStatus(player, cost);
        if (!costStatus.met()) {
            sendCostFailure(player, "Cannot reroll " + rarity.name(), costStatus);
            return;
        }
        pay(player, cost);
        items.applyRarity(held, rarity);
        player.sendMessage(Component.text("Rerolled " + rarity.name() + " affixes.", NamedTextColor.AQUA));
    }

    public void craftUnique(Player player, String itemId) {
        UniqueItemDefinition definition = content.uniqueItem(itemId);
        if (definition == null) {
            player.sendMessage(Component.text("Unknown recipe: " + itemId, NamedTextColor.RED));
            return;
        }

        UniqueCraftStatus status = uniqueStatus(player, definition);
        if (!status.ready()) {
            sendUniqueFailure(player, definition, status);
            return;
        }

        Cost cost = new Cost(definition.coin(), definition.token(), definition.materials(), definition.customMaterials());
        pay(player, cost);
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack result = items.createUniqueItem(definition);
        held.getEnchantments().forEach((enchantment, level) -> result.addUnsafeEnchantment(enchantment, level));
        player.getInventory().setItemInMainHand(result);
        database.incrementCraft(player.getUniqueId(), definition.rarity());
        if (progression != null) progression.recordForge(player, definition.rarity());
        player.sendMessage(Component.text("Forged ", NamedTextColor.GREEN)
                .append(Component.text(definition.name(), definition.rarity().color())));
        if (particles != null) {
            particles.craftEffect(player, definition.rarity(), definition.name());
        }
    }

    public UpgradeStatus upgradeStatus(Player player) {
        economy.ensure(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldLabel = describeHeld(held);
        if (!items.isSupportedWeapon(held)) {
            return new UpgradeStatus(false, Rarity.COMMON, null, false, heldLabel, emptyCostStatus());
        }

        Rarity current = items.rarity(held);
        if (current.atLeast(Rarity.EPIC)) {
            return new UpgradeStatus(true, current, current.next(), true, heldLabel, emptyCostStatus());
        }

        Rarity next = current.next();
        if (next == null || next.atLeast(Rarity.LEGENDARY)) {
            return new UpgradeStatus(true, current, next, true, heldLabel, emptyCostStatus());
        }

        Cost cost = loadUpgradeCost(current, next);
        return new UpgradeStatus(true, current, next, false, heldLabel, costStatus(player, cost));
    }

    public UniqueCraftStatus uniqueStatus(Player player, UniqueItemDefinition definition) {
        economy.ensure(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean learned = blueprints.has(player, definition.blueprintId());
        BaseStatus baseStatus = new BaseStatus(
                matchesBase(held, definition),
                requiredBaseLabel(definition),
                describeHeld(held)
        );
        Cost cost = new Cost(definition.coin(), definition.token(), definition.materials(), definition.customMaterials());
        return new UniqueCraftStatus(learned, baseStatus, costStatus(player, cost));
    }

    public String displayCustomMaterial(String id) {
        MaterialDefinition definition = content.material(id);
        return definition == null ? prettyId(id) : definition.name();
    }

    private boolean matchesBase(ItemStack held, UniqueItemDefinition definition) {
        if (held == null || held.getType().isAir()) {
            return false;
        }
        var base = definition.base();
        if (base.itemId() != null) {
            String itemId = items.itemId(held);
            return itemId != null && itemId.equalsIgnoreCase(base.itemId());
        }
        if (base.material() != null && held.getType() != base.material()) {
            return false;
        }
        return base.requiredRarity() == null || items.rarity(held).atLeast(base.requiredRarity());
    }

    private String requiredBaseLabel(UniqueItemDefinition definition) {
        var base = definition.base();
        if (base.itemId() != null) {
            UniqueItemDefinition baseDefinition = content.uniqueItem(base.itemId());
            if (baseDefinition != null) {
                return baseDefinition.name() + " [" + baseDefinition.rarity().name() + "]";
            }
            return prettyId(base.itemId());
        }
        String material = base.material() == null ? "required base item" : base.material().name();
        if (base.requiredRarity() == null) {
            return material;
        }
        return base.requiredRarity().name() + "+ " + material;
    }

    private String describeHeld(ItemStack held) {
        if (held == null || held.getType().isAir()) {
            return "EMPTY HAND";
        }
        String id = items.itemId(held);
        Rarity rarity = items.rarity(held);
        if (id != null) {
            UniqueItemDefinition definition = content.uniqueItem(id);
            String name = definition == null ? prettyId(id) : definition.name();
            return name + " [" + rarity.name() + "]";
        }
        return rarity.name() + " " + held.getType().name();
    }

    private Cost loadUpgradeCost(Rarity current, Rarity next) {
        String path = "forge.upgrade." + current.name() + "-to-" + next.name();
        long coin = plugin.getConfig().getLong(path + ".coin", 0);
        long token = plugin.getConfig().getLong(path + ".token", 0);
        Map<Material, Integer> materials = readVanillaRequirements(plugin.getConfig().getConfigurationSection(path + ".materials"));
        Map<String, Integer> custom = readCustomRequirements(plugin.getConfig().getConfigurationSection(path + ".custom-materials"));
        return new Cost(Math.max(0, coin), Math.max(0, token), materials, custom);
    }

    private CostStatus costStatus(Player player, Cost cost) {
        ResourceStatus coin = new ResourceStatus(economy.coin(player.getUniqueId()), cost.coin());
        ResourceStatus token = new ResourceStatus(economy.token(player.getUniqueId()), cost.token());

        Map<String, ResourceStatus> vanilla = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> requirement : cost.materials().entrySet()) {
            vanilla.put(requirement.getKey().name(),
                    new ResourceStatus(countVanillaMaterial(player, requirement.getKey()), requirement.getValue()));
        }

        Map<String, ResourceStatus> custom = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> requirement : cost.customMaterials().entrySet()) {
            custom.put(requirement.getKey(),
                    new ResourceStatus(countCustomMaterial(player, requirement.getKey()), requirement.getValue()));
        }

        return new CostStatus(coin, token,
                Collections.unmodifiableMap(vanilla),
                Collections.unmodifiableMap(custom));
    }

    private CostStatus emptyCostStatus() {
        ResourceStatus empty = new ResourceStatus(0, 0);
        return new CostStatus(empty, empty, Map.of(), Map.of());
    }

    private void sendUniqueFailure(Player player, UniqueItemDefinition definition, UniqueCraftStatus status) {
        player.sendMessage(Component.text("Cannot forge " + definition.name() + ".", NamedTextColor.RED));
        if (!status.blueprintLearned()) {
            var blueprint = content.blueprint(definition.blueprintId());
            String blueprintName = blueprint == null ? definition.blueprintId() : blueprint.name();
            player.sendMessage(Component.text("[X] " + blueprintName + " is not learned.", NamedTextColor.RED));
        }
        if (!status.base().met()) {
            player.sendMessage(Component.text("[X] Base required: " + status.base().required(), NamedTextColor.RED));
            player.sendMessage(Component.text("    Held: " + status.base().held(), NamedTextColor.GRAY));
        }
        sendMissingResources(player, status.cost());
        player.sendMessage(Component.text("Open /forge -> Blueprint Recipes to see the full checklist.", NamedTextColor.YELLOW));
    }

    private void sendCostFailure(Player player, String title, CostStatus status) {
        player.sendMessage(Component.text(title + ".", NamedTextColor.RED));
        sendMissingResources(player, status);
    }

    private void sendMissingResources(Player player, CostStatus status) {
        if (!status.coin().met()) {
            player.sendMessage(Component.text("[X] Coin: " + status.coin().have() + "/" + status.coin().need(), NamedTextColor.RED));
        }
        if (!status.token().met()) {
            player.sendMessage(Component.text("[X] Token: " + status.token().have() + "/" + status.token().need(), NamedTextColor.RED));
        }
        status.vanilla().forEach((name, value) -> {
            if (!value.met()) {
                player.sendMessage(Component.text("[X] " + name + ": " + value.have() + "/" + value.need(), NamedTextColor.RED));
            }
        });
        status.custom().forEach((id, value) -> {
            if (!value.met()) {
                player.sendMessage(Component.text("[X] " + displayCustomMaterial(id) + ": "
                        + value.have() + "/" + value.need(), NamedTextColor.RED));
            }
        });
    }

    private void pay(Player player, Cost cost) {
        economy.takeCoin(player.getUniqueId(), cost.coin());
        economy.takeToken(player.getUniqueId(), cost.token());
        removeVanillaMaterials(player, cost.materials());
        removeCustomMaterials(player, cost.customMaterials());
    }

    private int countVanillaMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() != material
                    || items.customMaterialId(stack) != null
                    || items.blueprintId(stack) != null
                    || items.itemId(stack) != null) {
                continue;
            }
            count += stack.getAmount();
        }
        return count;
    }

    private int countCustomMaterial(Player player, String id) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && items.isCustomMaterial(stack, id)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeVanillaMaterials(Player player, Map<Material, Integer> requirements) {
        for (Map.Entry<Material, Integer> requirement : requirements.entrySet()) {
            int remaining = requirement.getValue();
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                ItemStack stack = storage[slot];
                if (stack == null || stack.getType() != requirement.getKey()
                        || items.customMaterialId(stack) != null
                        || items.blueprintId(stack) != null
                        || items.itemId(stack) != null) {
                    continue;
                }
                int taken = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - taken);
                remaining -= taken;
                if (stack.getAmount() <= 0) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }
    }

    private void removeCustomMaterials(Player player, Map<String, Integer> requirements) {
        for (Map.Entry<String, Integer> requirement : requirements.entrySet()) {
            int remaining = requirement.getValue();
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
                ItemStack stack = storage[slot];
                if (stack == null || !items.isCustomMaterial(stack, requirement.getKey())) {
                    continue;
                }
                int taken = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - taken);
                remaining -= taken;
                if (stack.getAmount() <= 0) {
                    player.getInventory().setItem(slot, null);
                }
            }
        }
    }

    private Map<Material, Integer> readVanillaRequirements(ConfigurationSection section) {
        Map<Material, Integer> materials = new LinkedHashMap<>();
        if (section == null) {
            return materials;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unknown material in config: " + key);
                continue;
            }
            materials.put(material, Math.max(1, section.getInt(key, 1)));
        }
        return materials;
    }

    private Map<String, Integer> readCustomRequirements(ConfigurationSection section) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        if (section == null) {
            return materials;
        }
        for (String key : section.getKeys(false)) {
            materials.put(key.toLowerCase(), Math.max(1, section.getInt(key, 1)));
        }
        return materials;
    }

    private String prettyId(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String[] words = id.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase());
            }
        }
        return builder.toString();
    }

    private record Cost(long coin, long token, Map<Material, Integer> materials, Map<String, Integer> customMaterials) {
    }

    public record ResourceStatus(long have, long need) {
        public boolean met() {
            return have >= need;
        }
    }

    public record CostStatus(ResourceStatus coin, ResourceStatus token,
                             Map<String, ResourceStatus> vanilla,
                             Map<String, ResourceStatus> custom) {
        public boolean met() {
            if (!coin.met() || !token.met()) {
                return false;
            }
            for (ResourceStatus status : vanilla.values()) {
                if (!status.met()) {
                    return false;
                }
            }
            for (ResourceStatus status : custom.values()) {
                if (!status.met()) {
                    return false;
                }
            }
            return true;
        }
    }

    public record BaseStatus(boolean met, String required, String held) {
    }

    public record UniqueCraftStatus(boolean blueprintLearned, BaseStatus base, CostStatus cost) {
        public boolean ready() {
            return blueprintLearned && base.met() && cost.met();
        }
    }

    public record UpgradeStatus(boolean supported, Rarity current, Rarity next,
                                boolean blueprintStage, String heldLabel, CostStatus cost) {
        public boolean ready() {
            return supported && !blueprintStage && cost.met();
        }
    }
}
