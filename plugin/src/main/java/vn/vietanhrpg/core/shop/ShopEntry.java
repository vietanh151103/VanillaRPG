package vn.vietanhrpg.core.shop;

import org.bukkit.Material;

public record ShopEntry(String id, Material material, int amount, long price, ShopCategory category) {
}
