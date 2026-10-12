package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Shows current/max HP above every loaded hostile mob. */
public final class MobHealthDisplayService implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey baseNameKey;
    private final Map<UUID, LivingEntity> tracked = new HashMap<>();
    private final Map<UUID, Component> baseNames = new HashMap<>();
    private BukkitTask task;

    public MobHealthDisplayService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.baseNameKey = new NamespacedKey(plugin, "mob_health_base_name");
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("mob-health-display.enabled", true)) {
            plugin.getLogger().info("Mob health display is disabled in config.yml.");
            return;
        }
        long interval = Math.max(1L, plugin.getConfig().getLong("mob-health-display.update-interval-ticks", 10L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::scanAndUpdate, 1L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        tracked.clear();
        baseNames.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity mob && isCombatMob(mob)) {
            Bukkit.getScheduler().runTask(plugin, () -> trackAndUpdate(mob));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity mob && isCombatMob(mob)) {
            Bukkit.getScheduler().runTask(plugin, () -> trackAndUpdate(mob));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity mob && isCombatMob(mob)) {
            Bukkit.getScheduler().runTask(plugin, () -> trackAndUpdate(mob));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        tracked.remove(id);
        baseNames.remove(id);
    }

    private void scanAndUpdate() {
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (isCombatMob(entity)) {
                    trackAndUpdate(entity);
                }
            }
        }
        Iterator<Map.Entry<UUID, LivingEntity>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = iterator.next();
            if (!entry.getValue().isValid() || entry.getValue().isDead()) {
                baseNames.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    public void showDamage(LivingEntity target, double damage) {
        if (!plugin.getConfig().getBoolean("mob-health-display.enabled", true) || !isCombatMob(target)) {
            return;
        }
        trackAndUpdate(target);
        if (damage > 0.0 && plugin.getConfig().getBoolean("mob-health-display.floating-damage.enabled", true)
                && target.isValid()) {
            spawnFloatingDamage(target, damage);
        }
    }

    private void spawnFloatingDamage(LivingEntity target, double damage) {
        var location = target.getLocation().clone().add(
                (Math.random() - 0.5) * 0.7, target.getHeight() + 0.35, (Math.random() - 0.5) * 0.7);
        TextDisplay display = target.getWorld().spawn(location, TextDisplay.class);
        display.setText("§c§l-" + format(damage));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(false);
        display.setPersistent(false);
        long duration = Math.max(1L, plugin.getConfig().getLong("mob-health-display.floating-damage.duration-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) display.remove();
        }, duration);
    }

    private void trackAndUpdate(LivingEntity mob) {
        if (!mob.isValid() || mob.isDead()) return;
        UUID id = mob.getUniqueId();
        if (!tracked.containsKey(id)) {
            tracked.put(id, mob);
            String stored = mob.getPersistentDataContainer().get(baseNameKey, PersistentDataType.STRING);
            Component base = stored == null
                    ? (mob.customName() == null ? Component.text(pretty(mob)) : mob.customName())
                    : Component.text(stored, NamedTextColor.WHITE);
            baseNames.put(id, base);
            if (stored == null) {
                mob.getPersistentDataContainer().set(baseNameKey, PersistentDataType.STRING,
                        PlainTextComponentSerializer.plainText().serialize(base));
            }
        }
        Component base = baseNames.get(id);
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? Math.max(1.0, mob.getHealth()) : Math.max(1.0, maxHealth.getValue());
        double current = Math.max(0.0, Math.min(mob.getHealth(), max));
        mob.customName(base.append(Component.text("  ❤ " + format(current) + "/" + format(max) + " HP", NamedTextColor.RED)));
        mob.setCustomNameVisible(true);
    }

    private boolean isCombatMob(LivingEntity entity) {
        return entity instanceof Monster || entity instanceof Boss || entity instanceof Slime;
    }

    private String pretty(LivingEntity entity) {
        String raw = entity.getType().name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (char character : raw.toCharArray()) {
            if (upper && Character.isLetter(character)) {
                result.append(Character.toUpperCase(character));
                upper = false;
            } else {
                result.append(character);
            }
            if (character == ' ') upper = true;
        }
        return result.toString();
    }

    private String format(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
