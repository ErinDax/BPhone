package cn.erindax.bphone.economy;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.api.economy.EconomyProvider;
import cn.erindax.bphone.api.economy.EconomyResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

public final class LegacyBeatEconomyProvider implements EconomyProvider {
    private static final String API_CLASS = "cn.erindax.beat.eco.BecoAPI";

    private final Method getBalance;
    private final Method deposit;
    private final Method withdraw;

    private LegacyBeatEconomyProvider(Method getBalance, Method deposit, Method withdraw) {
        this.getBalance = getBalance;
        this.deposit = deposit;
        this.withdraw = withdraw;
    }

    public static LegacyBeatEconomyProvider tryCreate() {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Method getBalance = findMethod(api, "getBalance", ServerPlayer.class);
            if (getBalance == null) {
                BPhone.LOGGER.warn("[BPhone] {} has no getBalance(ServerPlayer), economy stays disabled", API_CLASS);
                return null;
            }

            Method deposit = findMethod(api, "deposit", ServerPlayer.class, long.class);
            Method withdraw = findMethod(api, "withdraw", ServerPlayer.class, long.class);
            BPhone.LOGGER.info("[BPhone] Connected to BEAT economy through {} (deposit={}, withdraw={})",
                    API_CLASS, deposit != null, withdraw != null);
            return new LegacyBeatEconomyProvider(getBalance, deposit, withdraw);
        } catch (ClassNotFoundException exception) {
            BPhone.LOGGER.info("[BPhone] BEAT is loaded but {} was not found", API_CLASS);
            return null;
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Failed to connect to BEAT economy", throwable);
            return null;
        }
    }

    @Override
    public String id() {
        return "beat_legacy";
    }

    @Override
    public long getBalance(MinecraftServer server, UUID account) {
        ServerPlayer player = onlinePlayer(server, account);
        if (player == null) {
            return 0L;
        }

        try {
            Object result = getBalance.invoke(null, player);
            return result instanceof Number number ? number.longValue() : 0L;
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] BEAT getBalance failed", throwable);
            return 0L;
        }
    }

    @Override
    public EconomyResult withdraw(MinecraftServer server, UUID account, long amount, Component reason) {
        return invokeChange(withdraw, server, account, amount, true);
    }

    @Override
    public EconomyResult deposit(MinecraftServer server, UUID account, long amount, Component reason) {
        return invokeChange(deposit, server, account, amount, false);
    }

    private EconomyResult invokeChange(Method method, MinecraftServer server, UUID account, long amount,
                                       boolean requireFunds) {
        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        if (method == null) {
            return EconomyResult.UNAVAILABLE;
        }

        ServerPlayer player = onlinePlayer(server, account);
        if (player == null) {
            return EconomyResult.ACCOUNT_UNAVAILABLE;
        }

        if (requireFunds && getBalance(server, account) < amount) {
            return EconomyResult.INSUFFICIENT_FUNDS;
        }

        try {
            Object result = method.invoke(null, player, amount);
            return result instanceof Boolean success && success ? EconomyResult.SUCCESS : EconomyResult.FAILED;
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] BEAT economy call {} failed", method.getName(), throwable);
            return EconomyResult.FAILED;
        }
    }

    private static ServerPlayer onlinePlayer(MinecraftServer server, UUID account) {
        if (server == null || account == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(account);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
