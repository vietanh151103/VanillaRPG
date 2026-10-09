package vn.vietanhrpg.core.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import vn.vietanhrpg.core.item.Affix;
import vn.vietanhrpg.core.item.ItemService;

import java.util.concurrent.ThreadLocalRandom;

public final class CombatListener implements Listener {
    private final JavaPlugin plugin;
    private final ItemService items;

    public CombatListener(JavaPlugin plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
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

        double damageBonus = items.affix(weapon, Affix.DAMAGE_BONUS);
        double damage = event.getDamage() * (1.0 + damageBonus / 100.0);

        double critChance = items.affix(weapon, Affix.CRIT_CHANCE);
        boolean critical = critChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < critChance;
        if (critical) {
            double critDamage = items.affix(weapon, Affix.CRIT_DAMAGE);
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            if (plugin.getConfig().getBoolean("combat.crit-message", true)) {
                player.sendActionBar(Component.text("CRITICAL!", NamedTextColor.GOLD));
            }
        }

        event.setDamage(Math.max(0.0, damage));

        double lifesteal = items.affix(weapon, Affix.LIFESTEAL);
        if (lifesteal > 0.0 && player.getHealth() > 0.0) {
            double rawHeal = event.getFinalDamage() * (lifesteal / 100.0);
            double cap = plugin.getConfig().getDouble("combat.lifesteal-max-heal-per-hit", 4.0);
            double heal = Math.min(Math.max(0.0, rawHeal), Math.max(0.0, cap));
            if (heal > 0.0) {
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                        ? 20.0
                        : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
            }
        }
    }
}
