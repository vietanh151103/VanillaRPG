package vn.vietanhrpg.core.dungeon;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class DungeonListener implements Listener {
    private final DungeonService service;

    public DungeonListener(DungeonService service) {
        this.service = service;
    }

    @EventHandler
    public void onDungeonMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!service.isDungeonMob(entity)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        service.handleDungeonMobDeath(entity);
    }

    @EventHandler
    public void onCrossPlayerDamage(EntityDamageByEntityEvent event) {
        UUID victimOwner = service.dungeonMobOwner(event.getEntity());
        Player attackingPlayer = playerFromDamager(event.getDamager());
        if (victimOwner != null && attackingPlayer != null && !victimOwner.equals(attackingPlayer.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        UUID attackerOwner = ownerFromDamager(event.getDamager());
        if (attackerOwner != null && event.getEntity() instanceof Player player
                && !attackerOwner.equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDungeonMobTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = service.dungeonMobOwner(event.getEntity());
        if (owner == null) return;
        if (event.getTarget() instanceof Player player && !owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (service.isDungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (service.isDungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if (service.isDungeonWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (service.activeRun(player.getUniqueId()) == null) return;
        service.fail(player, "Dungeon failed: you died.", false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (service.activeRun(player.getUniqueId()) == null) return;
        service.fail(player, "Dungeon abandoned: disconnected.", true);
    }

    private Player playerFromDamager(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private UUID ownerFromDamager(Entity damager) {
        UUID direct = service.dungeonMobOwner(damager);
        if (direct != null) return direct;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity) return service.dungeonMobOwner(entity);
        }
        return null;
    }
}
