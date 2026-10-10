package vn.vietanhrpg.core.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record DungeonLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public Location toBukkit() {
        World target = Bukkit.getWorld(world);
        if (target == null) return null;
        return new Location(target, x, y, z, yaw, pitch);
    }

    public static DungeonLocation from(ConfigurationSection section) {
        if (section == null) return null;
        String world = section.getString("world");
        if (world == null || world.isBlank()) return null;
        return new DungeonLocation(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    public static DungeonLocation from(Location location) {
        return new DungeonLocation(
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );
    }
}
