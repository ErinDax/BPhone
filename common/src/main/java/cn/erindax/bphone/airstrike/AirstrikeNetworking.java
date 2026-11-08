package cn.erindax.bphone.airstrike;

import cn.erindax.bphone.network.NetworkBufferUtils;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.item.AirstrikeRadioItem;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AirstrikeNetworking {
    public static final ResourceLocation TARGET_STATE = id("airstrike_target_state");
    public static final ResourceLocation TARGET_CLEARED = id("airstrike_target_cleared");
    public static final ResourceLocation STRIKE_LOCKED = id("airstrike_strike_locked");
    public static final ResourceLocation MODE_CYCLE = id("airstrike_mode_cycle");
    private static boolean initialized;

    private AirstrikeNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(TARGET_STATE, TARGET_CLEARED, STRIKE_LOCKED);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MODE_CYCLE, (buf, context) -> context.queue(() -> {
            if (!(context.getPlayer() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof AirstrikeRadioItem)) {
                return;
            }

            AirstrikeMode nextMode = AirstrikeRadioItem.cycleMode(stack);
            AirstrikeManager.handleModeCycle(player, nextMode);
        }));
    }

    public static void sendTargetState(ServerPlayer player, ResourceLocation dimensionId, AirstrikeMode mode, BlockPos... points) {
        if (player == null || dimensionId == null || points == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeResourceLocation(dimensionId);
        buf.writeVarInt(mode.ordinal());
        buf.writeVarInt(points.length);
        for (BlockPos point : points) {
            buf.writeBlockPos(point);
        }
        NetworkManager.sendToPlayer(player, TARGET_STATE, buf);
    }

    public static void sendTargetCleared(ServerPlayer player) {
        if (player == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        NetworkManager.sendToPlayer(player, TARGET_CLEARED, buf);
    }

    public static void sendStrikeLocked(ServerPlayer player, ResourceLocation dimensionId, AirstrikeMode mode,
                                        int countdownTicks, BlockPos... points) {
        if (player == null || dimensionId == null || points == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeResourceLocation(dimensionId);
        buf.writeVarInt(mode.ordinal());
        buf.writeVarInt(points.length);
        for (BlockPos point : points) {
            buf.writeBlockPos(point);
        }
        buf.writeVarInt(countdownTicks);
        NetworkManager.sendToPlayer(player, STRIKE_LOCKED, buf);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, path);
    }
}
