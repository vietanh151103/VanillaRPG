package vn.vietanhrpg.core.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/** Lightweight deterministic terrain generator for the isolated Mystic Forest test world. */
public final class MysticForestChunkGenerator extends ChunkGenerator {
    private static final int SEA_LEVEL = 62;

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;
                int surface = surfaceHeight(worldX, worldZ);
                biome.setBiome(localX, localZ, Biome.OLD_GROWTH_PINE_TAIGA);
                data.setBlock(localX, 0, localZ, Material.BEDROCK);
                for (int y = 1; y < surface - 4; y++) data.setBlock(localX, y, localZ, Material.STONE);
                for (int y = Math.max(1, surface - 4); y < surface; y++) data.setBlock(localX, y, localZ, Material.DIRT);
                data.setBlock(localX, surface, localZ, surface % 9 == 0 ? Material.PODZOL : Material.MOSS_BLOCK);
                if (surface < SEA_LEVEL) {
                    for (int y = surface + 1; y <= SEA_LEVEL; y++) data.setBlock(localX, y, localZ, Material.WATER);
                }
            }
        }
        return data;
    }

    private int surfaceHeight(int x, int z) {
        double broad = valueNoise(x * 0.018, z * 0.018) * 9.0;
        double detail = valueNoise(x * 0.065 + 31.0, z * 0.065 - 17.0) * 3.0;
        double ridge = Math.max(0, valueNoise(x * 0.009 - 9.0, z * 0.009 + 22.0)) * 7.0;
        return (int) Math.round(SEA_LEVEL + broad + detail + ridge - 4.0);
    }

    private double valueNoise(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        double a = hash(x0, z0);
        double b = hash(x0 + 1, z0);
        double c = hash(x0, z0 + 1);
        double d = hash(x0 + 1, z0 + 1);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private double hash(int x, int z) {
        long value = x * 341873128712L + z * 132897987541L;
        value = (value ^ (value >>> 13)) * 1274126177L;
        return ((value ^ (value >>> 16)) & 0x7fffffff) / (double) 0x7fffffff * 2.0 - 1.0;
    }

    private double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private double lerp(double a, double b, double amount) {
        return a + (b - a) * amount;
    }
}
