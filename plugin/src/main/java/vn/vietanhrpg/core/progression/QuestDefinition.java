package vn.vietanhrpg.core.progression;

import org.bukkit.Material;
import vn.vietanhrpg.core.item.Rarity;

public record QuestDefinition(
        String id,
        QuestPeriod period,
        String name,
        String description,
        ProgressType type,
        long target,
        Material material,
        Rarity rarity,
        RewardDefinition reward
) {
}
