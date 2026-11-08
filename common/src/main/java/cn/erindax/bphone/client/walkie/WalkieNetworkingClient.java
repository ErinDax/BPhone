package cn.erindax.bphone.client.walkie;

import cn.erindax.bphone.network.NetworkBufferUtils;

import cn.erindax.bphone.walkie.WalkieBand;
import cn.erindax.bphone.walkie.WalkieNetworking;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;

public final class WalkieNetworkingClient {
    private WalkieNetworkingClient() {
    }

    public static void requestTune(InteractionHand hand, WalkieBand band, int frequency) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeEnum(hand);
        buf.writeEnum(band);
        buf.writeVarInt(frequency);
        NetworkManager.sendToServer(WalkieNetworking.TUNE, buf);
    }

    public static void requestTransmit(InteractionHand hand) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeEnum(hand);
        NetworkManager.sendToServer(WalkieNetworking.TRANSMIT, buf);
    }

    public static void requestTalkState(InteractionHand hand, boolean talking) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeEnum(hand);
        buf.writeBoolean(talking);
        NetworkManager.sendToServer(WalkieNetworking.TALK_STATE, buf);
    }
}
