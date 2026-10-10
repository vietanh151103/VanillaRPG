package vn.vietanhrpg.core.market;

public record MarketDelivery(
        long id,
        long listingId,
        byte[] itemData,
        String reason,
        long createdAt
) {
}
