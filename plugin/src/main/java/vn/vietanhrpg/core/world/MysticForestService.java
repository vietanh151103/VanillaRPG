package vn.vietanhrpg.core.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/** A safe showcase world for the server-side Mystic Forest biome simulation. */
public final class MysticForestService {
    private static final String WORLD_NAME = "vanillarpg_mystic_forest_v2";
    private final JavaPlugin plugin;

    public MysticForestService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        World world = Bukkit.getWorld(WORLD_NAME);
        boolean firstCreate = world == null;
        if (world == null) {
            world = new WorldCreator(WORLD_NAME)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(true)
                    .seed(0x4D595354494346L)
                    .generator(new MysticForestChunkGenerator())
                    .createWorld();
        }
        if (world == null) {
            player.sendMessage(Component.text("Could not create the Mystic Forest test world.", NamedTextColor.RED));
            return;
        }
        if (firstCreate) buildShowcase(world);
        Location destination = world.getSpawnLocation().clone().add(0.5, 1.0, 0.5);
        player.teleport(destination);
        player.sendMessage(Component.text("Mystic Forest test world loaded. Base biome: Old Growth Pine Taiga.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("This is a server-side simulation; no client mod is required.", NamedTextColor.GRAY));
    }

    private void buildShowcase(World world) {
        Location center = world.getSpawnLocation();
        int radius = 64;
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int y = world.getHighestBlockYAt(x, z);
                world.setBiome(x, Math.max(0, y), z, Biome.OLD_GROWTH_PINE_TAIGA);
                int patch = Math.floorMod(x * 73428767 + z * 912931, 97);
                if (Math.abs(x - centerX) <= 3 && Math.abs(z - centerZ) <= 3) {
                    world.getBlockAt(x, y, z).setType(Material.MOSS_BLOCK, false);
                    world.getBlockAt(x, y + 1, z).setType(Material.MOSS_CARPET, false);
                } else if (patch < 5) {
                    world.getBlockAt(x, y + 1, z).setType(Material.GLOW_LICHEN, false);
                } else if (patch == 12) {
                    world.getBlockAt(x, y, z).setType(Material.MOSSY_COBBLESTONE, false);
                }
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 42; i++) {
            int x = centerX + random.nextInt(-radius + 6, radius - 5);
            int z = centerZ + random.nextInt(-radius + 6, radius - 5);
            int y = world.getHighestBlockYAt(x, z);
            if (Math.abs(x - centerX) < 7 && Math.abs(z - centerZ) < 7) continue;
            world.generateTree(new Location(world, x, y + 1, z), random.nextBoolean() ? TreeType.TALL_REDWOOD : TreeType.BIG_TREE);
        }
        world.setSpawnLocation(centerX, world.getHighestBlockYAt(centerX, centerZ) + 1, centerZ);
        plugin.getLogger().info("Built Mystic Forest showcase in " + WORLD_NAME + ".");
    }
}
