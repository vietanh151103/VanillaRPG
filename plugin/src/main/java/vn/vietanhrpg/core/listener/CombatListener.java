package vn.vietanhrpg.core.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.content.ContentService;
import vn.vietanhrpg.core.content.UniqueItemDefinition;
import vn.vietanhrpg.core.item.Affix;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;
import vn.vietanhrpg.core.service.ParticleService;
import vn.vietanhrpg.core.service.MobHealthDisplayService;

import java.util.concurrent.ThreadLocalRandom;

public final class CombatListener implements Listener {
    private final JavaPlugin plugin;
    private final ItemService items;
    private final ContentService content;
    private final ParticleService particles;
    private final MobHealthDisplayService mobHealthDisplay;

    public CombatListener(JavaPlugin plugin, ItemService items, ContentService content, ParticleService particles,
                          MobHealthDisplayService mobHealthDisplay) {
        this.plugin = plugin;
        this.items = items;
        this.content = content;
        this.particles = particles;
        this.mobHealthDisplay = mobHealthDisplay;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (event.getEntity() instanceof Player && !plugin.getConfig().getBoolean("combat.apply-to-players", false)) {
            return;
        }

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!items.isSupportedWeapon(weapon)) {
            return;
        }

        Rarity rarity = items.rarity(weapon);
        double damageBonus = items.affix(weapon, Affix.DAMAGE_BONUS);
        double damage = event.getDamage() * (1.0 + damageBonus / 100.0);

        UniqueItemDefinition unique = null;
        String itemId = items.itemId(weapon);
        if (itemId != null) {
            unique = content.uniqueItem(itemId);
        }

        if (unique != null && event.getEntity() instanceof LivingEntity target) {
            damage = applyUniquePreCritPassive(unique, target, damage);
        }

        double critChance = items.affix(weapon, Affix.CRIT_CHANCE);
        boolean critical = critChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < critChance;
        if (critical) {
            double critDamage = items.affix(weapon, Affix.CRIT_DAMAGE);
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            if (plugin.getConfig().getBoolean("combat.crit-message", true)) {
                player.sendActionBar(Component.text("CRITICAL!", rarity.isEndgame() ? rarity.color() : NamedTextColor.GOLD));
            }
            particles.combatCrit(player, event.getEntity().getLocation().add(0, 0.8, 0), rarity);
        }

        event.setDamage(Math.max(0.0, damage));
        if (event.getEntity() instanceof LivingEntity target) {
            double dealt = event.getFinalDamage();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> mobHealthDisplay.showDamage(target, dealt));
        }

        double lifesteal = items.affix(weapon, Affix.LIFESTEAL);
        if (lifesteal > 0.0 && player.getHealth() > 0.0) {
            double rawHeal = event.getFinalDamage() * (lifesteal / 100.0);
            double cap = plugin.getConfig().getDouble("combat.lifesteal-max-heal-per-hit", 4.0);
            heal(player, Math.min(Math.max(0.0, rawHeal), Math.max(0.0, cap)));
        }

        if (critical && unique != null && "blood_pact".equals(unique.passiveId())) {
            double percent = unique.passiveValues().getOrDefault("crit-max-health-heal", 2.0);
            double maxHealth = maxHealth(player);
            heal(player, maxHealth * Math.max(0.0, percent) / 100.0);
        }
    }

    private double applyUniquePreCritPassive(UniqueItemDefinition definition, LivingEntity target, double damage) {
        if ("infernal_soul".equals(definition.passiveId()) && target.getFireTicks() > 0) {
            double bonus = definition.passiveValues().getOrDefault("burning-damage-bonus", 12.0);
            return damage * (1.0 + Math.max(0.0, bonus) / 100.0);
        }
        if ("blood_pact".equals(definition.passiveId())) {
            double maxHealth = maxHealth(target);
            if (maxHealth <= 0.0) {
                return damage;
            }
            double healthPercent = target.getHealth() / maxHealth * 100.0;
            double threshold = definition.passiveValues().getOrDefault("execute-threshold", 20.0);
            if (healthPercent <= threshold) {
                double bonus = definition.passiveValues().getOrDefault("execute-damage-bonus", 25.0);
                return damage * (1.0 + Math.max(0.0, bonus) / 100.0);
            }
        }
        return damage;
    }

    private void heal(Player player, double amount) {
        if (amount <= 0.0 || player.getHealth() <= 0.0) {
            return;
        }
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    private double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
