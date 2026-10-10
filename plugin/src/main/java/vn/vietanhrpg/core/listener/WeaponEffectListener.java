package vn.vietanhrpg.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import vn.vietanhrpg.core.service.ParticleService;

public final class WeaponEffectListener implements Listener {
    private final ParticleService particles;

    public WeaponEffectListener(ParticleService particles) {
        this.particles = particles;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        particles.weaponSwing(event.getPlayer());
    }
}
