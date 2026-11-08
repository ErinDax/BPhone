package cn.erindax.bphone.economy;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.api.economy.EconomyProvider;
import cn.erindax.bphone.api.economy.EconomyResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BuiltinEconomyProvider implements EconomyProvider {
    public static final String ID = "bphone";
    private static final String FILE_NAME = "bphone_wallets.nbt";

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Path file;
    private final long startingBalance;
    private boolean dirty;

    private BuiltinEconomyProvider(Path file, long startingBalance) {
        this.file = file;
        this.startingBalance = startingBalance;
    }

    public static BuiltinEconomyProvider open(MinecraftServer server, long startingBalance) {
        BuiltinEconomyProvider provider = new BuiltinEconomyProvider(
                server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME), Math.max(0L, startingBalance));
        provider.load();
        return provider;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public synchronized long getBalance(MinecraftServer server, UUID account) {
        return account == null ? 0L : account(account);
    }

    @Override
    public synchronized EconomyResult withdraw(MinecraftServer server, UUID account, long amount, Component reason) {
        if (account == null) {
            return EconomyResult.ACCOUNT_UNAVAILABLE;
        }
        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }
        long balance = account(account);
        if (balance < amount) {
            return EconomyResult.INSUFFICIENT_FUNDS;
        }
        put(account, balance - amount);
        return EconomyResult.SUCCESS;
    }

    @Override
    public synchronized EconomyResult deposit(MinecraftServer server, UUID account, long amount, Component reason) {
        if (account == null) {
            return EconomyResult.ACCOUNT_UNAVAILABLE;
        }
        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }
        try {
            put(account, Math.addExact(account(account), amount));
            return EconomyResult.SUCCESS;
        } catch (ArithmeticException exception) {
            return EconomyResult.FAILED;
        }
    }

    @Override
    public synchronized EconomyResult transfer(MinecraftServer server, UUID from, UUID to, long amount, Component reason) {
        if (from == null || to == null) {
            return EconomyResult.ACCOUNT_UNAVAILABLE;
        }
        if (amount <= 0L) {
            return EconomyResult.INVALID_AMOUNT;
        }
        long source = account(from);
        if (source < amount) {
            return EconomyResult.INSUFFICIENT_FUNDS;
        }
        long target = account(to);
        if (target > Long.MAX_VALUE - amount) {
            return EconomyResult.FAILED;
        }
        put(from, source - amount);
        put(to, account(to) + amount);
        return EconomyResult.SUCCESS;
    }

    public synchronized void set(UUID account, long amount) {
        if (account != null) {
            put(account, Math.max(0L, amount));
        }
    }

    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private long account(UUID account) {
        Long balance = balances.get(account);
        if (balance != null) {
            return balance;
        }
        if (startingBalance > 0L) {
            put(account, startingBalance);
        }
        return startingBalance;
    }

    private void put(UUID account, long balance) {
        balances.put(account, balance);
        dirty = true;
    }

    private void save() {
        try {
            CompoundTag root = new CompoundTag();
            CompoundTag accounts = new CompoundTag();
            for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
                accounts.putLong(entry.getKey().toString(), entry.getValue());
            }
            root.put("balances", accounts);
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
            dirty = false;
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to save wallets to {}", file, exception);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            CompoundTag accounts = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("balances");
            for (String key : accounts.getAllKeys()) {
                try {
                    balances.put(UUID.fromString(key), Math.max(0L, accounts.getLong(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to load wallets from {}", file, exception);
        }
    }
}
