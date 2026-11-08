package cn.erindax.bphone.api.market;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record MarketQuote(String id, Component name, ItemStack icon, long price, long previousPrice, long volume,
                          long bestBid, long bestAsk) {
}
