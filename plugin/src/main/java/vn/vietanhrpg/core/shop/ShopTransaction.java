package vn.vietanhrpg.core.shop;

import java.util.UUID;

public record ShopTransaction(long id, UUID playerUuid, String direction, String material,
                              int amount, long coinAmount, long createdAt) {
}
