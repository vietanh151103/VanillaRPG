package vn.vietanhrpg.core.dungeon;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record DungeonMobDefinition(
        EntityType type,
        String name,
        int count,
        double healthMultiplier,
        double damageMultiplier,
        Material weapon,
        Material helmet,
        Material chestplate,
        Material leggings,
        Material boots
) {}
