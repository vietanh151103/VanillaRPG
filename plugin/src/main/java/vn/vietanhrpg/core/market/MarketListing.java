package vn.vietanhrpg.core.market;

import java.util.UUID;

public record MarketListing(
        long id,
        UUID sellerUuid,
        String sellerName,
        byte[] itemData,
        String itemType,
        int amount,
        long price,
        long createdAt
) {
}
