package vn.vietanhrpg.core.dungeon;

import org.bukkit.Material;
import vn.vietanhrpg.core.item.Rarity;

import java.util.List;

public record DungeonDefinition(
        String id,
        String name,
        String description,
        Material icon,
        int tier,
        boolean enabled,
        Rarity recommendedRarity,
        Rarity requiredRarity,
        long cooldownSeconds,
        long timeLimitSeconds,
        DungeonLocation spawn,
        boolean useWorldSpawn,
        double mobSpawnRadius,
        List<DungeonMobDefinition> mobs,
        DungeonRewardDefinition rewards
) {
    public int totalMobs() {
        return mobs.stream().mapToInt(m -> Math.max(0, m.count())).sum();
    }

    public boolean configured() {
        return enabled && spawn != null;
    }
}
