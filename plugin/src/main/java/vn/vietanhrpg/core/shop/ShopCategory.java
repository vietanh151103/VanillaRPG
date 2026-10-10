package vn.vietanhrpg.core.shop;

import org.bukkit.Material;

public enum ShopCategory {
    FOOD("Food", Material.BREAD),
    BUILDING("Building", Material.BRICKS);

    private final String displayName;
    private final Material icon;

    ShopCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public static ShopCategory parse(String value, Material material) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return switch (material) {
            case COBBLESTONE, STONE, DEEPSLATE, DIRT, SAND, GRAVEL, OAK_LOG, OAK_PLANKS, GLASS, BRICKS -> BUILDING;
            default -> FOOD;
        };
    }
}
