package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Applies one-way event scaling when the configured Nether population is reached. */
public final class DifficultyScalingService implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey scaledKey;
    private boolean netherActive;
    private BukkitTask task;

    public DifficultyScalingService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scaledKey = new NamespacedKey(plugin, "nether_difficulty_scaled");
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("difficulty-scaling.enabled", true)) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1L, 40L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public String profileDifficulty(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NETHER) {
            return "Overworld difficulty: Normal";
        }
        int players = netherPlayers();
        int required = requiredPlayers();
        if (players >= required) {
            return "Nether difficulty: Challenging (x" + multiplier() + ")";
        }
        return "Nether difficulty: Normal (" + players + "/" + required + " players)";
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (netherActive && event.getEntity() instanceof Monster monster
                && monster.getWorld().getEnvironment() == World.Environment.NETHER) {
            Bukkit.getScheduler().runTask(plugin, () -> scale(monster));
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { refresh(); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { refresh(); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { refresh(); }

    private void refresh() {
        if (!plugin.getConfig().getBoolean("difficulty-scaling.enabled", true)) return;
        boolean active = netherPlayers() >= requiredPlayers();
        if (active && !netherActive) {
            netherActive = true;
            Bukkit.broadcast(Component.text("Nether difficulty increased: " + netherPlayers()
                    + " players detected. Hostile mobs are now x" + multiplier() + ".", NamedTextColor.RED));
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() != World.Environment.NETHER) continue;
                for (Monster monster : world.getEntitiesByClass(Monster.class)) scale(monster);
            }
        } else if (!active && netherActive) {
            netherActive = false;
        }
    }

    private void scale(Monster monster) {
        if (!monster.isValid() || monster.isDead()
                || monster.getPersistentDataContainer().has(scaledKey, PersistentDataType.BYTE)) return;
        double factor = multiplier();
        AttributeInstance max = monster.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            double health = Math.max(1.0, max.getValue() * factor);
            max.setBaseValue(health);
            monster.setHealth(Math.min(health, monster.getHealth() * factor));
        }
        AttributeInstance attack = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(Math.max(0.1, attack.getValue() * factor));
        monster.getPersistentDataContainer().set(scaledKey, PersistentDataType.BYTE, (byte) 1);
    }

    private int netherPlayers() {
        int count = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.NETHER) count++;
        }
        return count;
    }

    private int requiredPlayers() {
        return Math.max(1, plugin.getConfig().getInt("difficulty-scaling.nether.min-players", 3));
    }

    private double multiplier() {
        return Math.max(1.0, plugin.getConfig().getDouble("difficulty-scaling.nether.multiplier", 1.2));
    }
}
