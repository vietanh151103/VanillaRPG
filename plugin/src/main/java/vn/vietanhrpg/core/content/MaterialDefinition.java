package vn.vietanhrpg.core.content;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

public record MaterialDefinition(
        String id,
        Material material,
        String name,
        NamedTextColor color,
        boolean glint,
        List<String> lore
) {
}
