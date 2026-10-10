package vn.vietanhrpg.core.market;

public record MarketPurchaseResult(
        MarketPurchaseStatus status,
        long listingId,
        long deliveryId,
        long price,
        String sellerName
) {
    public static MarketPurchaseResult failure(MarketPurchaseStatus status, long listingId) {
        return new MarketPurchaseResult(status, listingId, 0, 0, "");
    }
}
