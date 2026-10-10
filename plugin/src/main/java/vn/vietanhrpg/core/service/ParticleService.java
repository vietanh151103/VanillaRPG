package vn.vietanhrpg.core.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import vn.vietanhrpg.core.data.Database;
import vn.vietanhrpg.core.item.ItemService;
import vn.vietanhrpg.core.item.Rarity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ParticleService {
    private final JavaPlugin plugin;
    private final Database database;
    private final ItemService items;
    private final Map<UUID, WeaponSnapshot> previousWeaponFrames = new HashMap<>();
    private final Map<UUID, Long> lastSwingNanos = new HashMap<>();
    private BukkitTask task;
    private long auraStep;

    public ParticleService(JavaPlugin plugin, Database database, ItemService items) {
        this.plugin = plugin;
        this.database = database;
        this.items = items;
    }

    public void start() {
        stop();
        auraStep = 0L;
        previousWeaponFrames.clear();
        lastSwingNanos.clear();
        long interval = Math.max(2L, plugin.getConfig().getLong("particles.weapon-interval-ticks", 4L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAuras, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        previousWeaponFrames.clear();
        lastSwingNanos.clear();
    }

    public void combatCrit(Player attacker, Location location, Rarity rarity) {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)
                || !plugin.getConfig().getBoolean("particles.combat-effects", true)
                || !database.getSetting(attacker.getUniqueId(), "combat")) {
            return;
        }

        Color color = rarity == Rarity.MYTHIC
                ? Color.fromRGB(255, 42, 68)
                : Color.fromRGB(255, 205, 55);
        int rays = rarity == Rarity.MYTHIC ? 8 : 6;
        for (int i = 0; i < rays; i++) {
            double angle = (Math.PI * 2.0 * i / rays) + auraStep * 0.22;
            Location start = location.clone().add(0, 0.22, 0);
            Location target = start.clone().add(
                    Math.cos(angle) * 0.42,
                    0.08 + (i % 2) * 0.12,
                    Math.sin(angle) * 0.42
            );
            spawnTrail(attacker, start, target, color, 4);
        }
    }

    /**
     * Called from PlayerAnimationEvent. Produces a short luminous slash instead of smoke.
     * The persistent aura setting for the held rarity also controls this effect.
     */
    public void weaponSwing(Player owner) {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)
                || !plugin.getConfig().getBoolean("particles.weapon-effects-enabled", false)) {
            return;
        }

        ItemStack held = owner.getInventory().getItemInMainHand();
        if (!items.isSupportedWeapon(held)) {
            return;
        }

        Rarity rarity = items.rarity(held);
        if (!rarity.isEndgame()) {
            return;
        }

        String setting = rarity == Rarity.MYTHIC ? "mythic" : "legendary";
        if (!database.getSetting(owner.getUniqueId(), setting)) {
            return;
        }

        boolean enabled = rarity == Rarity.MYTHIC
                ? plugin.getConfig().getBoolean("particles.mythic.swing-trail", true)
                : plugin.getConfig().getBoolean("particles.legendary.swing-trail", true);
        if (!enabled) {
            return;
        }

        long cooldownMs = Math.max(80L, plugin.getConfig().getLong("particles.swing-cooldown-ms", 180L));
        long now = System.nanoTime();
        Long last = lastSwingNanos.get(owner.getUniqueId());
        if (last != null && now - last < cooldownMs * 1_000_000L) {
            return;
        }
        lastSwingNanos.put(owner.getUniqueId(), now);

        double viewDistance = Math.max(8.0, plugin.getConfig().getDouble("particles.view-distance", 48.0));
        double maxDistanceSquared = viewDistance * viewDistance;
        WeaponFrame frame = weaponFrame(owner);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!canView(viewer, owner, maxDistanceSquared)) {
                continue;
            }
            if (rarity == Rarity.MYTHIC) {
                spawnMythicSlash(viewer, owner, frame);
            } else {
                spawnLegendarySlash(viewer, owner, frame);
            }
        }
    }

    public void craftEffect(Player player, Rarity rarity, String itemName) {
        if (!rarity.isEndgame()) {
            return;
        }
        if (plugin.getConfig().getBoolean("particles.enabled", true)
                && plugin.getConfig().getBoolean("particles.craft-effects", true)
                && database.getSetting(player.getUniqueId(), "craft")) {
            Location center = player.getLocation().add(0, 0.7, 0);
            boolean mythic = rarity == Rarity.MYTHIC;
            Color primary = mythic ? Color.fromRGB(220, 0, 36) : Color.fromRGB(255, 177, 24);
            Color accent = mythic ? Color.fromRGB(255, 92, 112) : Color.fromRGB(255, 235, 130);
            int rays = mythic ? 20 : 14;

            for (int i = 0; i < rays; i++) {
                double angle = Math.PI * 2.0 * i / rays;
                double radius = 0.25 + (i % 4) * 0.07;
                Location start = center.clone().add(
                        Math.cos(angle) * radius,
                        (i % 3) * 0.08,
                        Math.sin(angle) * radius
                );
                Location target = center.clone().add(
                        Math.cos(angle) * (0.85 + (i % 3) * 0.09),
                        0.45 + (i % 5) * 0.12,
                        Math.sin(angle) * (0.85 + (i % 3) * 0.09)
                );
                spawnTrail(player, start, target, i % 3 == 0 ? accent : primary, mythic ? 8 : 7);
            }

            player.getWorld().playSound(center,
                    mythic ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ITEM_TOTEM_USE,
                    mythic ? 1.0f : 0.9f,
                    mythic ? 0.8f : 1.15f);
        }

        if (plugin.getConfig().getBoolean("particles.broadcast-endgame-crafts", true)) {
            NamedTextColor color = rarity.color();
            Bukkit.broadcast(Component.text("✦ ", color)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" forged ", NamedTextColor.GRAY))
                    .append(Component.text(itemName + " [" + rarity.name() + "]", color)
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(" ✦", color)));
        }
    }

    private void tickAuras() {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) {
            previousWeaponFrames.clear();
            return;
        }
        auraStep++;
        double viewDistance = Math.max(8.0, plugin.getConfig().getDouble("particles.view-distance", 48.0));
        double maxDistanceSquared = viewDistance * viewDistance;

        for (Player owner : Bukkit.getOnlinePlayers()) {
            UUID ownerId = owner.getUniqueId();
            ItemStack held = owner.getInventory().getItemInMainHand();
            WeaponFrame frame = weaponFrame(owner);

            String customMaterialId = items.customMaterialId(held);
            if (customMaterialId != null
                    && plugin.getConfig().getBoolean("particles.forging-materials.enabled", true)
                    && database.getSetting(ownerId, "materials")) {
                previousWeaponFrames.remove(ownerId);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!canView(viewer, owner, maxDistanceSquared)) {
                        continue;
                    }
                    spawnForgingMaterialGlow(viewer, owner, frame.hand(), customMaterialId);
                }
                continue;
            }

            if (!items.isSupportedWeapon(held)) {
                previousWeaponFrames.remove(ownerId);
                continue;
            }
            if (!plugin.getConfig().getBoolean("particles.weapon-effects-enabled", false)) {
                previousWeaponFrames.remove(ownerId);
                continue;
            }
            Rarity rarity = items.rarity(held);
            if (!rarity.isEndgame()) {
                previousWeaponFrames.remove(ownerId);
                continue;
            }
            String setting = rarity == Rarity.MYTHIC ? "mythic" : "legendary";
            if (!database.getSetting(ownerId, setting)) {
                previousWeaponFrames.remove(ownerId);
                continue;
            }

            WeaponSnapshot current = snapshot(frame, rarity);
            WeaponSnapshot previous = previousWeaponFrames.put(ownerId, current);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!canView(viewer, owner, maxDistanceSquared)) {
                    continue;
                }
                if (rarity == Rarity.MYTHIC) {
                    spawnMythicWeaponTrail(viewer, owner, frame);
                    if (plugin.getConfig().getBoolean("particles.mythic.motion-trail", true)) {
                        spawnMotionAfterimage(viewer, previous, current, true);
                    }
                } else {
                    spawnLegendaryWeaponTrail(viewer, owner, frame);
                    if (plugin.getConfig().getBoolean("particles.legendary.motion-trail", true)) {
                        spawnMotionAfterimage(viewer, previous, current, false);
                    }
                }
            }
        }

        previousWeaponFrames.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        lastSwingNanos.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private boolean canView(Player viewer, Player owner, double maxDistanceSquared) {
        if (!viewer.getWorld().equals(owner.getWorld())) {
            return false;
        }
        if (viewer != owner && !database.getSetting(viewer.getUniqueId(), "others")) {
            return false;
        }
        return viewer.getLocation().distanceSquared(owner.getLocation()) <= maxDistanceSquared;
    }

    private WeaponFrame weaponFrame(Player player) {
        Location eye = player.getEyeLocation();

        Vector horizontalForward = eye.getDirection().clone().setY(0);
        if (horizontalForward.lengthSquared() < 0.0001) {
            horizontalForward = new Vector(0, 0, 1);
        }
        horizontalForward.normalize();
        Vector right = new Vector(-horizontalForward.getZ(), 0, horizontalForward.getX()).normalize();
        Vector up = new Vector(0, 1, 0);

        Location hand = eye.clone()
                .add(horizontalForward.clone().multiply(0.32))
                .add(right.clone().multiply(0.38))
                .add(0, -0.48, 0);

        Vector blade = eye.getDirection().clone();
        if (blade.lengthSquared() < 0.0001) {
            blade = horizontalForward.clone();
        }
        blade.normalize().multiply(0.82);
        blade.add(new Vector(0, -0.08, 0));

        return new WeaponFrame(hand, blade, right, up);
    }

    private void spawnLegendaryWeaponTrail(Player viewer, Player owner, WeaponFrame frame) {
        int segments = clamp(plugin.getConfig().getInt("particles.legendary.weapon-points", 6), 3, 10);
        int duration = clamp(plugin.getConfig().getInt("particles.legendary.trail-duration-ticks", 6), 2, 12);
        Color gold = Color.fromRGB(255, 184, 28);
        Color paleGold = Color.fromRGB(255, 239, 150);
        double phase = (auraStep + owner.getEntityId() * 0.17) * 0.48;

        for (int i = 0; i < segments; i++) {
            double t0 = 0.06 + (0.76 * i / Math.max(1.0, segments));
            double t1 = Math.min(0.99, t0 + 0.28);
            Location start = bladePoint(frame, t0, phase, 0.018);
            Location target = bladePoint(frame, t1, phase + 0.38, 0.018);
            spawnTrail(viewer, start, target, i >= segments - 2 ? paleGold : gold, duration);
        }

        // A bright runner sweeps from hilt to tip and makes the gold aura read as a light streak.
        double runner = 0.08 + 0.82 * ((auraStep % 11) / 10.0);
        Location runnerStart = bladePoint(frame, runner, phase, 0.012);
        Location runnerTarget = bladePoint(frame, Math.min(0.995, runner + 0.22), phase + 0.22, 0.012);
        spawnTrail(viewer, runnerStart, runnerTarget, paleGold, 3);
    }

    private void spawnMythicWeaponTrail(Player viewer, Player owner, WeaponFrame frame) {
        int segments = clamp(plugin.getConfig().getInt("particles.mythic.weapon-points", 9), 5, 14);
        int duration = clamp(plugin.getConfig().getInt("particles.mythic.trail-duration-ticks", 7), 3, 14);
        Color deepRed = Color.fromRGB(150, 0, 26);
        Color scarlet = Color.fromRGB(255, 30, 62);
        Color hotRed = Color.fromRGB(255, 120, 138);
        double basePhase = (auraStep + owner.getEntityId() * 0.21) * 0.68;

        // Two longer crimson ribbons twist around the blade. Only TRAIL is used: no smoke/ash/end-rod.
        for (int i = 0; i < segments; i++) {
            double t0 = 0.03 + (0.80 * i / Math.max(1.0, segments));
            double t1 = Math.min(0.995, t0 + 0.27);

            Location a0 = spiralPoint(frame, t0, basePhase, 0.066);
            Location a1 = spiralPoint(frame, t1, basePhase + 0.24, 0.066);
            spawnTrail(viewer, a0, a1, i % 3 == 0 ? hotRed : scarlet, duration);

            Location b0 = spiralPoint(frame, t0, basePhase + Math.PI, 0.066);
            Location b1 = spiralPoint(frame, t1, basePhase + Math.PI + 0.24, 0.066);
            spawnTrail(viewer, b0, b1, i % 4 == 0 ? scarlet : deepRed, duration);
        }

        // Bright comet streak travelling along one ribbon.
        double runner = 0.06 + 0.86 * ((auraStep % 12) / 11.0);
        Location runnerStart = spiralPoint(frame, runner, basePhase, 0.074);
        Location runnerTarget = spiralPoint(frame, Math.min(0.999, runner + 0.25), basePhase + 0.28, 0.074);
        spawnTrail(viewer, runnerStart, runnerTarget, hotRed, 4);

        // Second, shorter counter-runner gives the Mythic weapon a braided-light look.
        double counter = 0.10 + 0.72 * (((auraStep + 5) % 12) / 11.0);
        Location counterStart = spiralPoint(frame, counter, basePhase + Math.PI, 0.064);
        Location counterTarget = spiralPoint(frame, Math.min(0.99, counter + 0.17), basePhase + Math.PI + 0.20, 0.064);
        spawnTrail(viewer, counterStart, counterTarget, scarlet, 3);
    }

    private void spawnMotionAfterimage(Player viewer, WeaponSnapshot previous, WeaponSnapshot current, boolean mythic) {
        if (previous == null || previous.rarity() != current.rarity()) {
            return;
        }
        if (previous.tip().getWorld() == null || current.tip().getWorld() == null
                || !previous.tip().getWorld().equals(current.tip().getWorld())) {
            return;
        }

        double min = Math.max(0.01, plugin.getConfig().getDouble("particles.motion-min-distance", 0.055));
        double max = Math.max(min, plugin.getConfig().getDouble("particles.motion-max-distance", 2.5));
        double tipDistance = previous.tip().distance(current.tip());
        if (tipDistance < min || tipDistance > max) {
            return;
        }

        if (mythic) {
            Color scarlet = Color.fromRGB(255, 32, 65);
            Color hotRed = Color.fromRGB(255, 132, 148);
            spawnTrail(viewer, previous.tip(), current.tip(), hotRed, 7);
            spawnTrail(viewer, previous.mid(), current.mid(), scarlet, 6);

            // Offset afterimage follows the opposite side of the blade, producing a ribbon rather than a dot.
            Location pOffset = previous.tip().clone().add(previous.right().clone().multiply(0.045));
            Location cOffset = current.tip().clone().add(current.right().clone().multiply(-0.045));
            spawnTrail(viewer, pOffset, cOffset, scarlet, 6);
        } else {
            Color gold = Color.fromRGB(255, 190, 35);
            Color pale = Color.fromRGB(255, 240, 160);
            spawnTrail(viewer, previous.tip(), current.tip(), pale, 5);
            if (tipDistance > min * 2.0) {
                spawnTrail(viewer, previous.mid(), current.mid(), gold, 4);
            }
        }
    }

    private void spawnLegendarySlash(Player viewer, Player owner, WeaponFrame frame) {
        Color gold = Color.fromRGB(255, 190, 35);
        Color pale = Color.fromRGB(255, 244, 175);
        int segments = clamp(plugin.getConfig().getInt("particles.legendary.swing-points", 7), 5, 12);
        Location[] arc = slashArc(owner, frame, segments, 0.86, 0.0);
        for (int i = 0; i < arc.length - 1; i++) {
            spawnTrail(viewer, arc[i], arc[i + 1], i >= arc.length - 3 ? pale : gold, 3);
        }
    }

    private void spawnMythicSlash(Player viewer, Player owner, WeaponFrame frame) {
        Color deepRed = Color.fromRGB(150, 0, 28);
        Color scarlet = Color.fromRGB(255, 28, 62);
        Color hotRed = Color.fromRGB(255, 135, 150);
        int segments = clamp(plugin.getConfig().getInt("particles.mythic.swing-points", 10), 7, 16);

        Location[] outer = slashArc(owner, frame, segments, 1.02, 0.03);
        Location[] inner = slashArc(owner, frame, segments, 0.82, -0.07);
        for (int i = 0; i < outer.length - 1; i++) {
            spawnTrail(viewer, outer[i], outer[i + 1], i % 3 == 0 ? hotRed : scarlet, 4);
            spawnTrail(viewer, inner[i], inner[i + 1], deepRed, 4);
        }

        // A short bright cut across the centre makes each swing feel sharp rather than smoky.
        int middle = outer.length / 2;
        if (middle + 1 < outer.length) {
            spawnTrail(viewer, inner[Math.max(0, middle - 1)], outer[Math.min(outer.length - 1, middle + 1)], hotRed, 3);
        }
    }

    private Location[] slashArc(Player owner, WeaponFrame frame, int segments, double radius, double verticalOffset) {
        Location[] points = new Location[segments + 1];
        Vector forward = frame.blade().clone();
        if (forward.lengthSquared() < 0.0001) {
            forward = owner.getEyeLocation().getDirection().clone();
        }
        forward.normalize();

        Location center = owner.getEyeLocation().clone()
                .add(forward.clone().multiply(0.56))
                .add(0, -0.47 + verticalOffset, 0);

        for (int i = 0; i <= segments; i++) {
            double normalized = i / (double) segments;
            double angle = -1.18 + normalized * 2.36;
            double side = Math.sin(angle) * radius;
            double depth = (Math.cos(angle) - 0.30) * radius * 0.28;
            double lift = Math.cos(angle * 0.80) * 0.11 + Math.sin(normalized * Math.PI) * 0.07;
            points[i] = center.clone()
                    .add(frame.right().clone().multiply(side))
                    .add(forward.clone().multiply(depth))
                    .add(frame.up().clone().multiply(lift));
        }
        return points;
    }

    private Location bladePoint(WeaponFrame frame, double t, double phase, double amplitude) {
        Location point = frame.hand().clone().add(frame.blade().clone().multiply(t));
        point.add(frame.right().clone().multiply(Math.sin(phase + t * Math.PI * 1.8) * amplitude));
        point.add(frame.up().clone().multiply(Math.cos(phase + t * Math.PI * 1.4) * amplitude * 0.65));
        return point;
    }

    private Location spiralPoint(WeaponFrame frame, double t, double phaseOffset, double radius) {
        double angle = phaseOffset + t * Math.PI * 3.8;
        Location point = frame.hand().clone().add(frame.blade().clone().multiply(t));
        point.add(frame.right().clone().multiply(Math.cos(angle) * radius));
        point.add(frame.up().clone().multiply(Math.sin(angle) * radius));
        return point;
    }

    private WeaponSnapshot snapshot(WeaponFrame frame, Rarity rarity) {
        Location tip = frame.hand().clone().add(frame.blade().clone().multiply(0.98));
        Location mid = frame.hand().clone().add(frame.blade().clone().multiply(0.58));
        return new WeaponSnapshot(
                frame.hand().clone(),
                mid,
                tip,
                frame.right().clone(),
                rarity
        );
    }

    private void spawnForgingMaterialGlow(Player viewer, Player owner, Location hand, String id) {
        int points = clamp(plugin.getConfig().getInt("particles.forging-materials.points", 3), 2, 5);
        Color primary;
        Color accent;

        switch (id.toLowerCase()) {
            case "legendary_shard" -> {
                primary = Color.fromRGB(255, 188, 32);
                accent = Color.fromRGB(255, 238, 150);
            }
            case "mythic_core" -> {
                primary = Color.fromRGB(205, 0, 35);
                accent = Color.fromRGB(255, 90, 110);
            }
            default -> {
                primary = Color.fromRGB(160, 55, 235);
                accent = Color.fromRGB(225, 155, 255);
            }
        }

        double phase = (auraStep + owner.getEntityId() * 0.13) * 0.65;
        for (int i = 0; i < points; i++) {
            double a0 = phase + i * (Math.PI * 2.0 / points);
            double a1 = a0 + 0.75;
            double radius = 0.07 + i * 0.008;
            Location start = hand.clone().add(
                    Math.cos(a0) * radius,
                    0.015 + i * 0.018,
                    Math.sin(a0) * radius
            );
            Location target = hand.clone().add(
                    Math.cos(a1) * radius,
                    0.05 + i * 0.018,
                    Math.sin(a1) * radius
            );
            spawnTrail(viewer, start, target, i == points - 1 ? accent : primary, 5);
        }
    }

    private void spawnTrail(Player viewer, Location start, Location target, Color color, int duration) {
        viewer.spawnParticle(
                Particle.TRAIL,
                start,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                new Particle.Trail(target, color, Math.max(1, duration))
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record WeaponFrame(Location hand, Vector blade, Vector right, Vector up) {
    }

    private record WeaponSnapshot(Location hand, Location mid, Location tip, Vector right, Rarity rarity) {
    }
}
