package vn.vietanhrpg.core.content;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public record BlueprintDefinition(
        String id,
        String targetItemId,
        Material material,
        String name,
        NamedTextColor color
) {
}
