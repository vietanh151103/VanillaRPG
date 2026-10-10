package vn.vietanhrpg.core.market;

public record MarketCancelResult(
        MarketCancelStatus status,
        long listingId,
        long deliveryId
) {
    public static MarketCancelResult failure(MarketCancelStatus status, long listingId) {
        return new MarketCancelResult(status, listingId, 0);
    }
}
