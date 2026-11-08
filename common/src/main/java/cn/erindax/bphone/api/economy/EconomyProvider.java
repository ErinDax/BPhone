package cn.erindax.bphone.api.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public interface EconomyProvider {
    String id();

    long getBalance(MinecraftServer server, UUID account);

    EconomyResult withdraw(MinecraftServer server, UUID account, long amount, Component reason);

    EconomyResult deposit(MinecraftServer server, UUID account, long amount, Component reason);

    default EconomyResult transfer(MinecraftServer server, UUID from, UUID to, long amount, Component reason) {
        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }

        EconomyResult withdrawn = withdraw(server, from, amount, reason);
        if (!withdrawn.isSuccess()) {
            return withdrawn;
        }

        EconomyResult deposited = deposit(server, to, amount, reason);
        if (!deposited.isSuccess()) {
            deposit(server, from, amount, reason);
        }
        return deposited;
    }
}
