package cn.erindax.bphone;

import cn.erindax.bphone.api.economy.EconomyProvider;
import cn.erindax.bphone.api.economy.EconomyResult;
import cn.erindax.bphone.api.economy.PhoneEconomy;
import cn.erindax.bphone.economy.BuiltinEconomyProvider;
import cn.erindax.bphone.economy.LegacyBeatEconomyProvider;
import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyBridge {
    private static LegacyBeatEconomyProvider legacyProvider;
    private static BuiltinEconomyProvider builtinProvider;

    private EconomyBridge() {
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!PhoneEconomy.isAvailable() && Platform.isModLoaded("beat")) {
            legacyProvider = LegacyBeatEconomyProvider.tryCreate();
            if (legacyProvider != null) {
                PhoneEconomy.register(legacyProvider);
            }
        }

        if (!PhoneEconomy.isAvailable() && BPhoneConfig.builtinEconomy()) {
            builtinProvider = BuiltinEconomyProvider.open(server, BPhoneConfig.startingBalance());
            PhoneEconomy.register(builtinProvider);
        }

        if (!PhoneEconomy.isAvailable()) {
            BPhone.LOGGER.info("[BPhone] No economy provider registered, bank and shop payments are disabled");
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        if (legacyProvider != null) {
            PhoneEconomy.unregister(legacyProvider);
            legacyProvider = null;
        }
        if (builtinProvider != null) {
            builtinProvider.saveIfDirty();
            PhoneEconomy.unregister(builtinProvider);
            builtinProvider = null;
        }
    }

    public static void saveIfDirty() {
        BuiltinEconomyProvider provider = builtinProvider;
        if (provider != null) {
            provider.saveIfDirty();
        }
    }

    public static BuiltinEconomyProvider activeBuiltin() {
        BuiltinEconomyProvider provider = builtinProvider;
        return provider != null && PhoneEconomy.provider().orElse(null) == provider ? provider : null;
    }

    public static String providerId() {
        return PhoneEconomy.provider().map(EconomyProvider::id).orElse("");
    }

    public static boolean available() {
        return PhoneEconomy.isAvailable();
    }

    public static long balanceOf(ServerPlayer player) {
        return player == null ? 0L : balanceOf(player.getServer(), player.getUUID());
    }

    public static long balanceOf(MinecraftServer server, java.util.UUID account) {
        EconomyProvider provider = PhoneEconomy.provider().orElse(null);
        if (provider == null || server == null || account == null) {
            return 0L;
        }

        try {
            return provider.getBalance(server, account);
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Economy provider {} failed to read a balance", provider.id(), throwable);
            return 0L;
        }
    }

    public static EconomyResult withdrawFrom(MinecraftServer server, java.util.UUID account, long amount, Component reason) {
        EconomyProvider provider = PhoneEconomy.provider().orElse(null);
        if (provider == null || server == null || account == null) {
            return EconomyResult.UNAVAILABLE;
        }

        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        try {
            return nonNull(provider.withdraw(server, account, amount, reason));
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Economy provider {} failed to withdraw", provider.id(), throwable);
            return EconomyResult.FAILED;
        }
    }

    public static EconomyResult withdraw(ServerPlayer player, long amount, Component reason) {
        EconomyProvider provider = PhoneEconomy.provider().orElse(null);
        if (provider == null || player == null || player.getServer() == null) {
            return EconomyResult.UNAVAILABLE;
        }

        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        try {
            return nonNull(provider.withdraw(player.getServer(), player.getUUID(), amount, reason));
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Economy provider {} failed to withdraw", provider.id(), throwable);
            return EconomyResult.FAILED;
        }
    }

    public static EconomyResult deposit(ServerPlayer player, long amount, Component reason) {
        EconomyProvider provider = PhoneEconomy.provider().orElse(null);
        if (provider == null || player == null || player.getServer() == null) {
            return EconomyResult.UNAVAILABLE;
        }

        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        try {
            return nonNull(provider.deposit(player.getServer(), player.getUUID(), amount, reason));
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Economy provider {} failed to deposit", provider.id(), throwable);
            return EconomyResult.FAILED;
        }
    }

    public static EconomyResult depositTo(MinecraftServer server, java.util.UUID account, long amount, Component reason) {
        EconomyProvider provider = PhoneEconomy.provider().orElse(null);
        if (provider == null || server == null || account == null) {
            return EconomyResult.UNAVAILABLE;
        }

        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        try {
            return nonNull(provider.deposit(server, account, amount, reason));
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Economy provider {} failed to deposit", provider.id(), throwable);
            return EconomyResult.FAILED;
        }
    }

    public static EconomyResult transfer(ServerPlayer from, ServerPlayer to, long amount, Component reason) {
        EconomyProvider provider = PhoneEconomy.provider().orElse(null);
        if (provider == null || from == null || to == null || from.getServer() == null) {
            return EconomyResult.UNAVAILABLE;
        }

        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        try {
            return nonNull(provider.transfer(from.getServer(), from.getUUID(), to.getUUID(), amount, reason));
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Economy provider {} failed to transfer", provider.id(), throwable);
            return EconomyResult.FAILED;
        }
    }

    private static EconomyResult nonNull(EconomyResult result) {
        return result == null ? EconomyResult.FAILED : result;
    }
}
