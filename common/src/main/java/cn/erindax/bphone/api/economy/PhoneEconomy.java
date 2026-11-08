package cn.erindax.bphone.api.economy;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

public final class PhoneEconomy {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile EconomyProvider provider;

    private PhoneEconomy() {
    }

    public static synchronized void register(EconomyProvider newProvider) {
        Objects.requireNonNull(newProvider, "provider");
        EconomyProvider previous = provider;
        provider = newProvider;
        if (previous != null && previous != newProvider) {
            LOGGER.info("[BPhone] Economy provider {} replaced by {}", previous.id(), newProvider.id());
        } else {
            LOGGER.info("[BPhone] Economy provider registered: {}", newProvider.id());
        }
    }

    public static synchronized boolean unregister(EconomyProvider target) {
        if (target == null || provider != target) {
            return false;
        }

        provider = null;
        LOGGER.info("[BPhone] Economy provider unregistered: {}", target.id());
        return true;
    }

    public static Optional<EconomyProvider> provider() {
        return Optional.ofNullable(provider);
    }

    public static boolean isAvailable() {
        return provider != null;
    }
}
