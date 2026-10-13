package vn.vietanhrpg.core.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.GameRule;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.MaterialDefinition;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.progression.ProgressionService;
import vn.vietanhrpg.core.service.EconomyService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonService {
    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final ItemService items;
    private final ContentService content;
    private final ProgressionService progression;
    private final DungeonContentService dungeons;
    private final NamespacedKey ownerKey;
    private final NamespacedKey dungeonKey;
    private final DungeonWorldLoader worldLoader;
    private final Map<UUID, DungeonRun> activeRuns = new HashMap<>();
    private final Map<String, UUID> occupied = new HashMap<>();
    private BukkitTask tickTask;

    public DungeonService(JavaPlugin plugin, Database database, EconomyService economy,
                          ItemService items, ContentService content, ProgressionService progression,
                          DungeonContentService dungeons) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.items = items;
        this.content = content;
        this.progression = progression;
        this.dungeons = dungeons;
        this.ownerKey = new NamespacedKey(plugin, "dungeon_owner");
        this.dungeonKey = new NamespacedKey(plugin, "dungeon_id");
        this.worldLoader = new DungeonWorldLoader(plugin);
    }

    public void start() {
        stopTickOnly();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        stopTickOnly();
        for (DungeonRun run : new ArrayList<>(activeRuns.values())) {
            Player player = Bukkit.getPlayer(run.playerId());
            cleanupMobs(run);
            if (player != null && player.isOnline() && run.returnLocation() != null) {
                player.teleport(run.returnLocation());
            }
        }
        activeRuns.clear();
        occupied.clear();
    }

    private void stopTickOnly() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public boolean join(Player player, String dungeonId) {
        DungeonDefinition definition = dungeons.dungeon(dungeonId);
        if (definition == null) {
            player.sendMessage(Component.text("Unknown dungeon: " + dungeonId, NamedTextColor.RED));
            return false;
        }
        if (activeRuns.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already inside a dungeon. Use /dungeon leave first.", NamedTextColor.RED));
            return false;
        }
        if (!definition.configured()) {
            player.sendMessage(Component.text("This dungeon is not configured yet.", NamedTextColor.RED));
            player.sendMessage(Component.text("Admin: stand at the dungeon entrance and use /dungeon setspawn " + definition.id(), NamedTextColor.YELLOW));
            return false;
        }
        Location target = resolveDungeonSpawn(definition);
        if (target == null) {
            player.sendMessage(Component.text("Dungeon world folder is missing or could not be loaded: " + definition.spawn().world(), NamedTextColor.RED));
            player.sendMessage(Component.text("Place the world folder beside your main world folder, then try again.", NamedTextColor.YELLOW));
            return false;
        }
        UUID occupant = occupied.get(definition.id());
        if (occupant != null && !occupant.equals(player.getUniqueId())) {
            Player other = Bukkit.getPlayer(occupant);
            player.sendMessage(Component.text(definition.name() + " is currently occupied"
                    + (other == null ? "." : " by " + other.getName() + "."), NamedTextColor.RED));
            return false;
        }

        long remaining = cooldownRemainingSeconds(player.getUniqueId(), definition);
        if (remaining > 0) {
            player.sendMessage(Component.text("Dungeon cooldown: " + formatTime(remaining) + " remaining.", NamedTextColor.RED));
            return false;
        }

        ItemStack heldWeapon = player.getInventory().getItemInMainHand();
        if (!items.isSupportedWeapon(heldWeapon)) {
            player.sendMessage(Component.text("Hold a supported weapon before entering the dungeon.", NamedTextColor.RED));
            return false;
        }
        Rarity heldRarity = items.rarity(heldWeapon);
        if (!heldRarity.atLeast(definition.requiredRarity())) {
            player.sendMessage(Component.text("Required weapon rarity: " + definition.requiredRarity().name() + "+.", NamedTextColor.RED));
            player.sendMessage(Component.text("Held weapon rarity: " + heldRarity.name() + ".", NamedTextColor.GRAY));
            return false;
        }

        economy.ensure(player);
        long now = System.currentTimeMillis();
        DungeonRun run = new DungeonRun(
                player.getUniqueId(), definition.id(), player.getLocation().clone(),
                now, now + definition.timeLimitSeconds() * 1000L, definition.totalMobs()
        );
        activeRuns.put(player.getUniqueId(), run);
        occupied.put(definition.id(), player.getUniqueId());

        player.teleport(target);
        spawnMobs(run, definition, target);
        player.sendMessage(Component.text("Entered " + definition.name() + " [Tier " + definition.tier() + "]", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Objective: defeat " + run.targetKills() + " dungeon mobs within "
                + formatTime(definition.timeLimitSeconds()) + ".", NamedTextColor.YELLOW));
        return true;
    }

    public void leave(Player player) {
        DungeonRun run = activeRuns.get(player.getUniqueId());
        if (run == null) {
            player.sendMessage(Component.text("You are not inside a dungeon.", NamedTextColor.RED));
            return;
        }
        finishRun(player, run, false, "Dungeon abandoned.", true);
    }

    public void fail(Player player, String reason, boolean teleportBack) {
        DungeonRun run = activeRuns.get(player.getUniqueId());
        if (run == null) return;
        finishRun(player, run, false, reason, teleportBack);
    }

    public void handleDungeonMobDeath(LivingEntity entity) {
        String ownerRaw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        String dungeonId = entity.getPersistentDataContainer().get(dungeonKey, PersistentDataType.STRING);
        if (ownerRaw == null || dungeonId == null) return;
        UUID owner;
        try { owner = UUID.fromString(ownerRaw); }
        catch (IllegalArgumentException exception) { return; }

        DungeonRun run = activeRuns.get(owner);
        if (run == null || !run.dungeonId().equalsIgnoreCase(dungeonId)) return;
        run.spawnedMobs().remove(entity.getUniqueId());
        int kills = run.addKill();
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) return;

        if (kills >= run.targetKills()) {
            complete(player, run);
        } else {
            player.sendActionBar(Component.text("Dungeon: " + kills + "/" + run.targetKills() + " defeated", NamedTextColor.YELLOW));
        }
    }

    public boolean isDungeonMob(Entity entity) {
        return entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    public UUID dungeonMobOwner(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException exception) { return null; }
    }

    public DungeonRun activeRun(UUID playerId) {
        return activeRuns.get(playerId);
    }

    public boolean occupied(String dungeonId) {
        return occupied.containsKey(dungeonId.toLowerCase());
    }

    public String occupiedBy(String dungeonId) {
        UUID uuid = occupied.get(dungeonId.toLowerCase());
        if (uuid == null) return null;
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "another player" : player.getName();
    }


    public boolean isDungeonWorld(World world) {
        if (world == null) return false;
        String keyValue = world.getKey().getKey();
        String legacyName = world.getName();
        for (DungeonDefinition definition : dungeons.dungeons()) {
            if (definition.spawn() == null) continue;
            String configured = definition.spawn().world();
            if (configured.equalsIgnoreCase(keyValue) || configured.equalsIgnoreCase(legacyName)) {
                return true;
            }
        }
        return false;
    }

    private Location resolveDungeonSpawn(DungeonDefinition definition) {
        if (definition.spawn() == null) return null;
        String worldName = definition.spawn().world();

        World world = Bukkit.getWorld(worldLoader.keyFor(worldName));
        if (world == null) {
            world = worldLoader.load(worldName);
        }
        if (world == null) return null;

        prepareDungeonWorld(world);
        if (definition.useWorldSpawn()) {
            Location spawn = world.getSpawnLocation().clone();
            spawn.setYaw(definition.spawn().yaw());
            spawn.setPitch(definition.spawn().pitch());
            return spawn;
        }
        return new Location(world, definition.spawn().x(), definition.spawn().y(), definition.spawn().z(),
                definition.spawn().yaw(), definition.spawn().pitch());
    }

    private void prepareDungeonWorld(World world) {
        try { world.setGameRule(GameRule.DO_MOB_SPAWNING, false); } catch (Exception ignored) {}
        try { world.setGameRule(GameRule.MOB_GRIEFING, false); } catch (Exception ignored) {}
        try { world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); } catch (Exception ignored) {}
        try { world.setGameRule(GameRule.DO_WEATHER_CYCLE, false); } catch (Exception ignored) {}
        try { world.setGameRule(GameRule.KEEP_INVENTORY, true); } catch (Exception ignored) {}
        world.setStorm(false);
        world.setThundering(false);
    }

    public void resetCooldown(UUID playerId, String dungeonId) {
        database.setDungeonLastClear(playerId, dungeonId, 0L);
    }

    public long cooldownRemainingSeconds(UUID playerId, DungeonDefinition definition) {
        long lastClear = database.dungeonLastClear(playerId, definition.id());
        if (lastClear <= 0 || definition.cooldownSeconds() <= 0) return 0;
        long readyAt = lastClear + definition.cooldownSeconds() * 1000L;
        return Math.max(0, (readyAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    public String rewardSummary(DungeonDefinition definition) {
        DungeonRewardDefinition reward = definition.rewards();
        List<String> parts = new ArrayList<>();
        if (reward.coinMax() > 0) parts.add(range(reward.coinMin(), reward.coinMax()) + " Elite Coins");
        if (reward.tokenMax() > 0) parts.add(range(reward.tokenMin(), reward.tokenMax()) + " Token");
        for (DungeonCustomReward custom : reward.customRewards()) {
            MaterialDefinition material = content.material(custom.itemId());
            String name = material == null ? custom.itemId() : material.name();
            String amount = custom.minAmount() == custom.maxAmount()
                    ? String.valueOf(custom.minAmount())
                    : custom.minAmount() + "-" + custom.maxAmount();
            parts.add(Math.round(custom.chance() * 100.0) + "% " + amount + "x " + name);
        }
        return parts.isEmpty() ? "None" : String.join(", ", parts);
    }

    public String formatTime(long seconds) {
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes <= 0) return rest + "s";
        return minutes + "m " + rest + "s";
    }

    private void complete(Player player, DungeonRun run) {
        DungeonDefinition definition = dungeons.dungeon(run.dungeonId());
        if (definition == null) {
            finishRun(player, run, false, "Dungeon configuration disappeared during the run.", true);
            return;
        }

        List<String> rewards = grantRewards(player, definition.rewards());
        database.setDungeonLastClear(player.getUniqueId(), definition.id(), System.currentTimeMillis());
        database.incrementDungeonClear(player.getUniqueId());
        progression.recordDungeonClear(player);

        long elapsedSeconds = Math.max(1, (System.currentTimeMillis() - run.startedAt()) / 1000L);
        finishRun(player, run, true, "Dungeon cleared in " + formatTime(elapsedSeconds) + "!", true);
        if (!rewards.isEmpty()) {
            player.sendMessage(Component.text("Rewards: " + String.join(", ", rewards), NamedTextColor.YELLOW));
        }
    }

    private List<String> grantRewards(Player player, DungeonRewardDefinition reward) {
        Random random = ThreadLocalRandom.current();
        List<String> granted = new ArrayList<>();
        long coin = randomLong(random, reward.coinMin(), reward.coinMax());
        long token = randomLong(random, reward.tokenMin(), reward.tokenMax());
        if (coin > 0) {
            economy.addCoin(player.getUniqueId(), coin);
            granted.add(coin + " Elite Coins");
        }
        if (token > 0) {
            economy.addToken(player.getUniqueId(), token);
            granted.add(token + " Token");
        }
        for (DungeonCustomReward custom : reward.customRewards()) {
            if (random.nextDouble() > custom.chance()) continue;
            MaterialDefinition definition = content.material(custom.itemId());
            if (definition == null) continue;
            int amount = randomInt(random, custom.minAmount(), custom.maxAmount());
            giveCustomMaterial(player, definition, amount);
            granted.add(amount + "x " + definition.name());
        }
        return granted;
    }

    private void giveCustomMaterial(Player player, MaterialDefinition definition, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, definition.material().getMaxStackSize());
            ItemStack stack = items.createCustomMaterial(definition, give);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            remaining -= give;
        }
    }

    private void finishRun(Player player, DungeonRun run, boolean success, String message, boolean teleportBack) {
        activeRuns.remove(run.playerId());
        occupied.remove(run.dungeonId());
        cleanupMobs(run);
        if (teleportBack && run.returnLocation() != null && player.isOnline()) {
            player.teleport(run.returnLocation());
        }
        player.sendMessage(Component.text(message, success ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void cleanupMobs(DungeonRun run) {
        for (UUID entityId : new ArrayList<>(run.spawnedMobs())) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        run.spawnedMobs().clear();
    }

    private void spawnMobs(DungeonRun run, DungeonDefinition definition, Location center) {
        World world = center.getWorld();
        for (DungeonMobDefinition mob : definition.mobs()) {
            for (int i = 0; i < mob.count(); i++) {
                Location spawn = findSpawnLocation(center, definition.mobSpawnRadius());
                Entity raw = world.spawnEntity(spawn, mob.type());
                if (!(raw instanceof LivingEntity entity)) {
                    raw.remove();
                    continue;
                }
                customizeMob(entity, mob, definition.tier());
                entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, run.playerId().toString());
                entity.getPersistentDataContainer().set(dungeonKey, PersistentDataType.STRING, definition.id());
                entity.setPersistent(true);
                entity.setRemoveWhenFarAway(false);
                run.spawnedMobs().add(entity.getUniqueId());
            }
        }
    }

    private void customizeMob(LivingEntity entity, DungeonMobDefinition mob, int tier) {
        NamedTextColor color = switch (Math.min(4, Math.max(1, tier))) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.BLUE;
            case 3 -> NamedTextColor.GOLD;
            default -> NamedTextColor.RED;
        };
        entity.customName(Component.text(mob.name(), color));
        entity.setCustomNameVisible(true);

        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double value = Math.max(1.0, maxHealth.getBaseValue() * mob.healthMultiplier());
            maxHealth.setBaseValue(value);
            entity.setHealth(value);
        }
        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(Math.max(0.1, attack.getBaseValue() * mob.damageMultiplier()));
        }

        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            setEquipment(equipment, mob);
        }
    }

    private void setEquipment(EntityEquipment equipment, DungeonMobDefinition mob) {
        if (mob.weapon() != null) equipment.setItemInMainHand(new ItemStack(mob.weapon()));
        if (mob.helmet() != null) equipment.setHelmet(new ItemStack(mob.helmet()));
        if (mob.chestplate() != null) equipment.setChestplate(new ItemStack(mob.chestplate()));
        if (mob.leggings() != null) equipment.setLeggings(new ItemStack(mob.leggings()));
        if (mob.boots() != null) equipment.setBoots(new ItemStack(mob.boots()));
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    private Location findSpawnLocation(Location center, double radius) {
        Random random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 1.5 + random.nextDouble() * Math.max(0.5, radius - 1.5);
            Location base = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            for (int dy = -2; dy <= 3; dy++) {
                Location candidate = base.clone().add(0, dy, 0);
                if (candidate.getBlock().isPassable()
                        && candidate.clone().add(0, 1, 0).getBlock().isPassable()
                        && !candidate.clone().add(0, -1, 0).getBlock().isPassable()) {
                    return candidate.add(0.5, 0.05, 0.5);
                }
            }
        }
        return center.clone();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (DungeonRun run : new ArrayList<>(activeRuns.values())) {
            Player player = Bukkit.getPlayer(run.playerId());
            if (player == null || !player.isOnline()) continue;
            long remainingMs = run.deadlineAt() - now;
            if (remainingMs <= 0) {
                fail(player, "Dungeon failed: time limit reached.", true);
                continue;
            }
            long remaining = (remainingMs + 999L) / 1000L;
            player.sendActionBar(Component.text(
                    "Dungeon " + run.kills() + "/" + run.targetKills() + "  |  " + formatTime(remaining),
                    remaining <= 30 ? NamedTextColor.RED : NamedTextColor.AQUA
            ));
        }
    }

    private long randomLong(Random random, long min, long max) {
        if (max <= min) return min;
        return min + (long) Math.floor(random.nextDouble() * (max - min + 1));
    }

    private int randomInt(Random random, int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    private String range(long min, long max) {
        return min == max ? String.valueOf(min) : min + "-" + max;
    }
}
