package vn.vietanhrpg.core.content;

import org.bukkit.Material;
import vn.vietanhrpg.core.item.Rarity;

public record BaseRequirement(
        Material material,
        Rarity requiredRarity,
        String itemId
) {
}
