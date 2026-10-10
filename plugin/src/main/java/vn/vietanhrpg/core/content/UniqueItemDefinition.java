package vn.vietanhrpg.core.content;

import org.bukkit.Material;
import vn.vietanhrpg.core.item.Affix;
import vn.vietanhrpg.core.item.Rarity;

import java.util.List;
import java.util.Map;

public record UniqueItemDefinition(
        String id,
        Material material,
        Rarity rarity,
        String name,
        String blueprintId,
        BaseRequirement base,
        long coin,
        long token,
        Map<Material, Integer> materials,
        Map<String, Integer> customMaterials,
        Map<Affix, Double> affixes,
        String passiveId,
        Map<String, Double> passiveValues,
        List<String> passiveLore
) {
}
