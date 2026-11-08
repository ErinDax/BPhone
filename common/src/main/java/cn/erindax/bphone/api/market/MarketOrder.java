package cn.erindax.bphone.api.market;

public record MarketOrder(String id, String commodityId, boolean buy, int quantity, int filled, long price, long createdAt) {
}
