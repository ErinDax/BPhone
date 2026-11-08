package cn.erindax.bphone.api.market;

import net.minecraft.network.chat.Component;

public record MarketResult(boolean success, Component message) {
    public static MarketResult ok(Component message) {
        return new MarketResult(true, message == null ? Component.empty() : message);
    }

    public static MarketResult fail(Component message) {
        return new MarketResult(false, message == null ? Component.empty() : message);
    }
}
