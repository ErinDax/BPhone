package cn.erindax.bphone.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HomePhoneRegistry {
    private static final Map<HomePhoneAddress, HomePhoneBlockEntity> LOADED_PHONES = new ConcurrentHashMap<>();

    private HomePhoneRegistry() {
    }

    public static void register(HomePhoneBlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        LOADED_PHONES.put(new HomePhoneAddress(serverLevel.dimension(), blockEntity.getBlockPos()), blockEntity);
    }

    public static void unregister(HomePhoneBlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        LOADED_PHONES.remove(new HomePhoneAddress(serverLevel.dimension(), blockEntity.getBlockPos()));
    }

    public static HomePhoneBlockEntity get(ServerLevel level, BlockPos blockPos) {
        if (level == null) {
            return null;
        }

        HomePhoneAddress address = new HomePhoneAddress(level.dimension(), blockPos);
        HomePhoneBlockEntity cached = LOADED_PHONES.get(address);
        if (isValid(level, blockPos, cached)) {
            return cached;
        }

        if (cached != null) {
            LOADED_PHONES.remove(address);
        }

        if (level.getBlockEntity(blockPos) instanceof HomePhoneBlockEntity blockEntity) {
            LOADED_PHONES.put(address, blockEntity);
            return blockEntity;
        }

        return null;
    }

    public static HomePhoneBlockEntity findByNumber(MinecraftServer server, String number) {
        Iterator<Map.Entry<HomePhoneAddress, HomePhoneBlockEntity>> iterator = LOADED_PHONES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HomePhoneAddress, HomePhoneBlockEntity> entry = iterator.next();
            HomePhoneAddress address = entry.getKey();
            ServerLevel level = server.getLevel(address.dimension());
            HomePhoneBlockEntity blockEntity = entry.getValue();
            if (!isValid(level, address.blockPos(), blockEntity)) {
                iterator.remove();
                continue;
            }

            if (blockEntity.getPhoneNumber().equals(number)) {
                return blockEntity;
            }
        }

        return null;
    }

    public static Map<HomePhoneAddress, HomePhoneBlockEntity> snapshotLoadedPhones(MinecraftServer server) {
        Map<HomePhoneAddress, HomePhoneBlockEntity> snapshot = new HashMap<>();
        Iterator<Map.Entry<HomePhoneAddress, HomePhoneBlockEntity>> iterator = LOADED_PHONES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HomePhoneAddress, HomePhoneBlockEntity> entry = iterator.next();
            HomePhoneAddress address = entry.getKey();
            ServerLevel level = server.getLevel(address.dimension());
            HomePhoneBlockEntity blockEntity = entry.getValue();
            if (!isValid(level, address.blockPos(), blockEntity)) {
                iterator.remove();
                continue;
            }

            snapshot.put(address, blockEntity);
        }
        return snapshot;
    }

    private static boolean isValid(ServerLevel level, BlockPos blockPos, HomePhoneBlockEntity blockEntity) {
        return level != null
                && blockEntity != null
                && !blockEntity.isRemoved()
                && blockEntity.getLevel() == level
                && blockEntity.getBlockPos().equals(blockPos);
    }

    public record HomePhoneAddress(ResourceKey<Level> dimension, BlockPos blockPos) {
    }
}
