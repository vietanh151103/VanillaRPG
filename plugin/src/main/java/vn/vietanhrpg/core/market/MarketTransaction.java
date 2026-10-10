package vn.vietanhrpg.core.market;

import java.util.UUID;

public record MarketTransaction(
        long id,
        long listingId,
        UUID buyerUuid,
        String buyerName,
        UUID sellerUuid,
        String sellerName,
        String itemType,
        int amount,
        long price,
        long createdAt
) {
}
