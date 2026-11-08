package cn.erindax.bphone.voice.svc;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.item.MegaphoneItem;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SvcMegaphoneVoiceHook {

    private static final short MEGAPHONE_OUTPUT_DISTANCE = 28;
    private static final int SPEAKING_GRACE_TICKS = 4;

    private static final Map<UUID, MegaphoneBridge> ACTIVE_BRIDGES = new HashMap<>();

    private static VoicechatServerApi serverApi;
    private static boolean tickRegistered;

    private SvcMegaphoneVoiceHook() {
    }

    public static void onServerStarted(VoicechatServerApi api) {
        serverApi = api;

        if (!tickRegistered) {
            tickRegistered = true;
            TickEvent.SERVER_PRE.register(SvcMegaphoneVoiceHook::onServerTick);
        }
    }

    public static void onServerStopped() {
        clearAllBridges();
        serverApi = null;
    }

    public static boolean isSpeaking(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        synchronized (ACTIVE_BRIDGES) {
            MegaphoneBridge bridge = ACTIVE_BRIDGES.get(playerId);
            return bridge != null && !bridge.closed && bridge.isSpeaking();
        }
    }

    public static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (serverApi == null || event == null || event.getSenderConnection() == null) {
            return;
        }

        UUID senderId = event.getSenderConnection().getPlayer().getUuid();

        MegaphoneBridge bridge;
        synchronized (ACTIVE_BRIDGES) {
            bridge = ACTIVE_BRIDGES.get(senderId);
        }

        if (bridge == null || bridge.closed) {
            return;
        }

        event.cancel();
        bridge.markSpeaking();
        bridge.routeAudio(event);
    }

    private static void onServerTick(MinecraftServer server) {
        if (serverApi == null) {
            clearAllBridges();
            return;
        }

        Set<UUID> desiredPlayers = new HashSet<>();
        synchronized (ACTIVE_BRIDGES) {
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                if (!MegaphoneItem.isUsingMegaphone(serverPlayer)) {
                    continue;
                }

                VoicechatConnection connection = serverApi.getConnectionOf(serverPlayer.getUUID());
                if (connection == null) {
                    continue;
                }

                desiredPlayers.add(serverPlayer.getUUID());
                MegaphoneBridge bridge = ACTIVE_BRIDGES.get(serverPlayer.getUUID());
                if (bridge == null) {
                    bridge = MegaphoneBridge.create(server, serverPlayer);
                    if (bridge != null) {
                        ACTIVE_BRIDGES.put(serverPlayer.getUUID(), bridge);
                    }
                    continue;
                }

                bridge.serverTick();
                bridge.refresh(serverPlayer);
            }

            ACTIVE_BRIDGES.entrySet().removeIf(entry -> {
                if (desiredPlayers.contains(entry.getKey())) {
                    return false;
                }

                entry.getValue().close();
                return true;
            });
        }
    }

    private static void clearAllBridges() {
        synchronized (ACTIVE_BRIDGES) {
            for (MegaphoneBridge bridge : ACTIVE_BRIDGES.values()) {
                bridge.close();
            }
            ACTIVE_BRIDGES.clear();
        }
    }

    private static final class MegaphoneBridge {
        private final UUID playerId;
        private final MinecraftServer minecraftServer;
        private LocationalAudioChannel outputChannel;
        private StaticAudioChannel monitorChannel;
        private volatile boolean closed;
        private int speakingTicksRemaining;

        private MegaphoneBridge(UUID playerId, MinecraftServer minecraftServer, LocationalAudioChannel outputChannel,
                                StaticAudioChannel monitorChannel) {
            this.playerId = playerId;
            this.minecraftServer = minecraftServer;
            this.outputChannel = outputChannel;
            this.monitorChannel = monitorChannel;
        }

        private static MegaphoneBridge create(MinecraftServer server, ServerPlayer serverPlayer) {
            if (serverApi == null) {
                return null;
            }

            try {
                UUID channelId = UUID.randomUUID();
                LocationalAudioChannel channel = serverApi.createLocationalAudioChannel(
                        channelId,
                        serverApi.fromServerLevel(serverPlayer.serverLevel()),
                        serverApi.createPosition(
                                serverPlayer.getX(),
                                serverPlayer.getEyeY() - 0.18D,
                                serverPlayer.getZ()
                        )
                );

                if (channel == null) {
                    return null;
                }

                channel.setDistance(MEGAPHONE_OUTPUT_DISTANCE);
                channel.setFilter(player -> !serverPlayer.getUUID().equals(player.getUuid()));

                return new MegaphoneBridge(serverPlayer.getUUID(), server, channel, null);
            } catch (Throwable throwable) {
                BPhone.LOGGER.warn("Unable to create SVC megaphone voice bridge", throwable);
                return null;
            }
        }

        private void refresh(ServerPlayer serverPlayer) {
            if (closed || serverApi == null || outputChannel == null) {
                return;
            }

            outputChannel.updateLocation(serverApi.createPosition(
                    serverPlayer.getX(),
                    serverPlayer.getEyeY() - 0.18D,
                    serverPlayer.getZ()
            ));
            outputChannel.setDistance(MEGAPHONE_OUTPUT_DISTANCE);
        }

        private void serverTick() {
            if (speakingTicksRemaining > 0) {
                speakingTicksRemaining--;
            }
        }

        private void markSpeaking() {
            speakingTicksRemaining = SPEAKING_GRACE_TICKS;
        }

        private boolean isSpeaking() {
            return speakingTicksRemaining > 0;
        }

        private void routeAudio(MicrophonePacketEvent event) {
            if (closed || serverApi == null || outputChannel == null) {
                return;
            }

            try {
                MicrophonePacket packet = event.getPacket();
                outputChannel.send(packet);
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to broadcast megaphone audio via SVC", throwable);
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            speakingTicksRemaining = 0;
            outputChannel = null;

        }
    }
}
