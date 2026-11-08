package cn.erindax.bphone.api.market;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

public final class PhoneMarket {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile MarketProvider provider;

    private PhoneMarket() {
    }

    public static synchronized void register(MarketProvider newProvider) {
        Objects.requireNonNull(newProvider, "provider");
        MarketProvider previous = provider;
        provider = newProvider;
        if (previous != null && previous != newProvider) {
            LOGGER.info("[BPhone] Market provider {} replaced by {}", previous.id(), newProvider.id());
        } else {
            LOGGER.info("[BPhone] Market provider registered: {}", newProvider.id());
        }
    }

    public static synchronized boolean unregister(MarketProvider target) {
        if (target == null || provider != target) {
            return false;
        }
        provider = null;
        LOGGER.info("[BPhone] Market provider unregistered: {}", target.id());
        return true;
    }

    public static Optional<MarketProvider> provider() {
        return Optional.ofNullable(provider);
    }

    public static boolean isAvailable() {
        return provider != null;
    }
}
