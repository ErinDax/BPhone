package cn.erindax.bphone.client.airstrike;

import cn.erindax.bphone.network.NetworkBufferUtils;

import cn.erindax.bphone.airstrike.AirstrikeMode;
import cn.erindax.bphone.airstrike.AirstrikeNetworking;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class AirstrikeNetworkingClient {
    private static boolean initialized;

    private AirstrikeNetworkingClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.s2c(), AirstrikeNetworking.TARGET_STATE, (buf, context) -> {
            ResourceLocation dimensionId = buf.readResourceLocation();
            AirstrikeMode mode = AirstrikeMode.byOrdinal(buf.readVarInt());
            int pointCount = buf.readVarInt();
            List<BlockPos> points = new ArrayList<>(pointCount);
            for (int index = 0; index < pointCount; index++) {
                points.add(buf.readBlockPos());
            }
            context.queue(() -> AirstrikeClientState.setSelectedTarget(dimensionId, mode, points));
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), AirstrikeNetworking.TARGET_CLEARED, (buf, context) ->
                context.queue(AirstrikeClientState::clear));

        NetworkManager.registerReceiver(NetworkManager.s2c(), AirstrikeNetworking.STRIKE_LOCKED, (buf, context) -> {
            ResourceLocation dimensionId = buf.readResourceLocation();
            AirstrikeMode mode = AirstrikeMode.byOrdinal(buf.readVarInt());
            int pointCount = buf.readVarInt();
            List<BlockPos> points = new ArrayList<>(pointCount);
            for (int index = 0; index < pointCount; index++) {
                points.add(buf.readBlockPos());
            }
            int countdownTicks = buf.readVarInt();
            context.queue(() -> AirstrikeClientState.startLockedTarget(Minecraft.getInstance(), dimensionId, mode, points, countdownTicks));
        });
    }

    public static void requestModeCycle() {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        NetworkManager.sendToServer(AirstrikeNetworking.MODE_CYCLE, buf);
    }
}
