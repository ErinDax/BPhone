package cn.erindax.bphone.api.market;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public interface MarketProvider {
    String id();

    List<MarketQuote> quotes(MinecraftServer server, UUID viewer);

    List<MarketOrder> orders(MinecraftServer server, UUID viewer);

    MarketResult placeOrder(ServerPlayer player, String commodityId, boolean buy, int quantity, long price);

    MarketResult cancelOrder(ServerPlayer player, String orderId);
}
