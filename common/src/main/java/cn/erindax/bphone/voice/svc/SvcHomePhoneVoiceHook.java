package cn.erindax.bphone.voice.svc;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.block.entity.HomePhoneRegistry.HomePhoneAddress;
import cn.erindax.bphone.phone.PhoneCallManager;
import cn.erindax.bphone.phone.PhoneCallManager.HomePhoneSpeakerBridge;
import cn.erindax.bphone.phone.PhoneCallManager.PlayerCallBridge;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SvcHomePhoneVoiceHook {

    private static final short SPEAKER_OUTPUT_DISTANCE = Short.MAX_VALUE;
    private static final double SPEAKER_INPUT_RANGE_SQR = Double.MAX_VALUE;

    private static final Map<HomePhoneAddress, SpeakerBridge> ACTIVE_BRIDGES = new HashMap<>();
    private static final Map<UUID, PlayerBridge> ACTIVE_PLAYER_BRIDGES = new HashMap<>();

    private static VoicechatServerApi serverApi;
    private static boolean tickRegistered;

    private SvcHomePhoneVoiceHook() {
    }

    public static void onServerStarted(VoicechatServerApi api) {
        serverApi = api;

        if (!tickRegistered) {
            tickRegistered = true;
            TickEvent.SERVER_PRE.register(SvcHomePhoneVoiceHook::onServerTick);
        }
    }

    public static void onServerStopped() {
        clearAllBridges();
        serverApi = null;
    }

    public static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (serverApi == null || event == null || event.getSenderConnection() == null) {
            return;
        }

        UUID senderId = event.getSenderConnection().getPlayer().getUuid();
        boolean handledByPhone = false;

        Collection<SpeakerBridge> bridges = snapshotActiveBridges();
        for (SpeakerBridge bridge : bridges) {
            if (bridge.isRemotePlayer(senderId)) {
                handledByPhone |= bridge.playIncomingAudio(event.getPacket());
            }
        }

        for (SpeakerBridge bridge : bridges) {
            if (bridge.hasLocalTalker(senderId)) {
                handledByPhone |= bridge.routeLocalAudio(senderId, event);
            }
        }

        Collection<PlayerBridge> playerBridges = snapshotActivePlayerBridges();
        for (PlayerBridge bridge : playerBridges) {
            if (bridge.isRemotePlayer(senderId)) {
                handledByPhone |= bridge.playIncomingAudio(event.getPacket());
            }
        }

        if (handledByPhone) {
            event.cancel();
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (serverApi == null) {
            clearAllBridges();
            return;
        }

        if (server.getTickCount() % 10 != 0) {
            return;
        }

        Map<HomePhoneAddress, HomePhoneSpeakerBridge> desiredByAddress = new HashMap<>();
        for (HomePhoneSpeakerBridge bridge : PhoneCallManager.snapshotHomePhoneSpeakerBridges(server)) {
            desiredByAddress.put(bridge.address(), bridge);
        }

        Map<UUID, PlayerCallBridge> desiredPlayerBridges = new HashMap<>();
        for (PlayerCallBridge bridge : PhoneCallManager.snapshotPlayerCallBridges(server)) {
            desiredPlayerBridges.put(bridge.listenerPlayerId(), bridge);
        }

        synchronized (ACTIVE_BRIDGES) {
            ACTIVE_BRIDGES.entrySet().removeIf(entry -> {
                SpeakerBridge activeBridge = entry.getValue();
                HomePhoneSpeakerBridge desiredBridge = desiredByAddress.get(entry.getKey());
                if (desiredBridge == null) {
                    activeBridge.close();
                    return true;
                }

                if (!activeBridge.matches(desiredBridge)) {
                    activeBridge.close();
                    return true;
                }

                activeBridge.refresh(server, desiredBridge);
                return false;
            });

            for (HomePhoneSpeakerBridge desiredBridge : desiredByAddress.values()) {
                if (ACTIVE_BRIDGES.containsKey(desiredBridge.address())) {
                    continue;
                }

                SpeakerBridge bridge = SpeakerBridge.create(server, desiredBridge);
                if (bridge != null) {
                    ACTIVE_BRIDGES.put(desiredBridge.address(), bridge);
                }
            }
        }

        synchronized (ACTIVE_PLAYER_BRIDGES) {
            ACTIVE_PLAYER_BRIDGES.entrySet().removeIf(entry -> {
                PlayerBridge activeBridge = entry.getValue();
                PlayerCallBridge desiredBridge = desiredPlayerBridges.get(entry.getKey());
                if (desiredBridge == null) {
                    activeBridge.close();
                    return true;
                }

                if (!activeBridge.matches(desiredBridge)) {
                    activeBridge.close();
                    return true;
                }

                return false;
            });

            for (PlayerCallBridge desiredBridge : desiredPlayerBridges.values()) {
                if (ACTIVE_PLAYER_BRIDGES.containsKey(desiredBridge.listenerPlayerId())) {
                    continue;
                }

                PlayerBridge bridge = PlayerBridge.create(desiredBridge);
                if (bridge != null) {
                    ACTIVE_PLAYER_BRIDGES.put(desiredBridge.listenerPlayerId(), bridge);
                }
            }
        }
    }

    private static Collection<SpeakerBridge> snapshotActiveBridges() {
        synchronized (ACTIVE_BRIDGES) {
            return List.copyOf(ACTIVE_BRIDGES.values());
        }
    }

    private static Collection<PlayerBridge> snapshotActivePlayerBridges() {
        synchronized (ACTIVE_PLAYER_BRIDGES) {
            return List.copyOf(ACTIVE_PLAYER_BRIDGES.values());
        }
    }

    private static void clearAllBridges() {
        synchronized (ACTIVE_BRIDGES) {
            for (SpeakerBridge bridge : ACTIVE_BRIDGES.values()) {
                bridge.close();
            }
            ACTIVE_BRIDGES.clear();
        }

        synchronized (ACTIVE_PLAYER_BRIDGES) {
            for (PlayerBridge bridge : ACTIVE_PLAYER_BRIDGES.values()) {
                bridge.close();
            }
            ACTIVE_PLAYER_BRIDGES.clear();
        }
    }

    private static boolean sendPhoneAudio(AudioChannel channel, MicrophonePacket packet, String debugContext) {
        if (channel == null || packet == null) {
            return false;
        }

        byte[] opusData = packet.getOpusEncodedData();
        if (opusData == null || opusData.length == 0) {
            return false;
        }

        try {
            channel.send(opusData);
            return true;
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug(debugContext, throwable);
            return false;
        }
    }

    private static void flushChannel(AudioChannel channel) {
        if (channel == null) {
            return;
        }

        try {
            channel.flush();
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to flush SVC phone audio channel", throwable);
        }
    }

    private static final class SpeakerBridge {
        private final HomePhoneAddress address;
        private LocationalAudioChannel speakerChannel;
        private StaticAudioChannel handsetChannel;
        private StaticAudioChannel remotePlayerChannel;
        private boolean blockOutputEnabled;
        private UUID listenerPlayerId;
        private volatile UUID remotePlayerId;
        private volatile List<HomePhoneAddress> remoteBridgeAddresses;
        private volatile Set<UUID> localTalkerIds;
        private volatile boolean closed;

        private SpeakerBridge(HomePhoneAddress address, boolean blockOutputEnabled, UUID listenerPlayerId,
                              UUID remotePlayerId, List<HomePhoneAddress> remoteBridgeAddresses) {
            this.address = address;
            this.blockOutputEnabled = blockOutputEnabled;
            this.listenerPlayerId = listenerPlayerId;
            this.remotePlayerId = remotePlayerId;
            this.remoteBridgeAddresses = remoteBridgeAddresses;
            this.localTalkerIds = Set.of();
        }

        private static SpeakerBridge create(MinecraftServer server, HomePhoneSpeakerBridge bridge) {
            if (serverApi == null) {
                return null;
            }

            SpeakerBridge speakerBridge = new SpeakerBridge(
                    bridge.address(),
                    bridge.speakerEnabled(),
                    bridge.handsetHolderId(),
                    bridge.remotePlayerId(),
                    bridge.remoteBridgeAddresses()
            );
            speakerBridge.refresh(server, bridge);
            return speakerBridge;
        }

        private boolean matches(HomePhoneSpeakerBridge bridge) {
            return !closed
                    && blockOutputEnabled == bridge.speakerEnabled()
                    && java.util.Objects.equals(listenerPlayerId, bridge.handsetHolderId())
                    && java.util.Objects.equals(remotePlayerId, bridge.remotePlayerId())
                    && remoteBridgeAddresses.equals(bridge.remoteBridgeAddresses());
        }

        private boolean isRemotePlayer(UUID playerId) {
            return !closed && remotePlayerId != null && remotePlayerId.equals(playerId);
        }

        private boolean hasLocalTalker(UUID playerId) {
            return !closed && localTalkerIds.contains(playerId);
        }

        private void refresh(MinecraftServer server, HomePhoneSpeakerBridge bridge) {
            if (closed || serverApi == null) {
                return;
            }

            blockOutputEnabled = bridge.speakerEnabled();
            listenerPlayerId = bridge.handsetHolderId();
            remotePlayerId = bridge.remotePlayerId();
            remoteBridgeAddresses = bridge.remoteBridgeAddresses();

            refreshSpeakerChannel(server);
            refreshHandsetChannel();
            refreshRemotePlayerChannel();
            refreshLocalTalkers(server);
        }

        private void refreshSpeakerChannel(MinecraftServer server) {
            if (!blockOutputEnabled || serverApi == null) {
                flushChannel(speakerChannel);
                speakerChannel = null;
                return;
            }

            if (speakerChannel != null) {
                BlockPos blockPos = address.blockPos();
                speakerChannel.updateLocation(serverApi.createPosition(
                        blockPos.getX() + 0.5D,
                        blockPos.getY() + 0.5D,
                        blockPos.getZ() + 0.5D
                ));
                return;
            }

            ServerLevel level = server.getLevel(address.dimension());
            if (level == null) {
                return;
            }

            try {
                BlockPos blockPos = address.blockPos();
                UUID channelId = UUID.randomUUID();
                speakerChannel = serverApi.createLocationalAudioChannel(
                        channelId,
                        serverApi.fromServerLevel(level),
                        serverApi.createPosition(
                                blockPos.getX() + 0.5D,
                                blockPos.getY() + 0.5D,
                                blockPos.getZ() + 0.5D
                        )
                );

                if (speakerChannel != null) {
                    speakerChannel.setDistance(SPEAKER_OUTPUT_DISTANCE);
                }
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to create SVC home phone speaker channel", throwable);
            }
        }

        private void refreshHandsetChannel() {
            if (listenerPlayerId == null || serverApi == null) {
                flushChannel(handsetChannel);
                handsetChannel = null;
                return;
            }

            if (handsetChannel != null) {
                return;
            }

            try {
                VoicechatConnection connection = serverApi.getConnectionOf(listenerPlayerId);
                if (connection == null) {
                    return;
                }

                UUID channelId = UUID.randomUUID();
                handsetChannel = serverApi.createStaticAudioChannel(
                        channelId,
                        connection.getPlayer().getServerLevel(),
                        connection
                );
            } catch (Throwable throwable) {
                handsetChannel = null;
                BPhone.LOGGER.debug("Unable to create SVC home phone handset channel", throwable);
            }
        }

        private void refreshRemotePlayerChannel() {
            if (remotePlayerId == null || serverApi == null) {
                flushChannel(remotePlayerChannel);
                remotePlayerChannel = null;
                return;
            }

            if (remotePlayerChannel != null) {
                return;
            }

            try {
                VoicechatConnection connection = serverApi.getConnectionOf(remotePlayerId);
                if (connection == null) {
                    return;
                }

                remotePlayerChannel = serverApi.createStaticAudioChannel(
                        UUID.randomUUID(),
                        connection.getPlayer().getServerLevel(),
                        connection
                );
            } catch (Throwable throwable) {
                remotePlayerChannel = null;
                BPhone.LOGGER.debug("Unable to create SVC home phone remote player channel", throwable);
            }
        }

        private void refreshLocalTalkers(MinecraftServer server) {
            if (closed || serverApi == null) {
                return;
            }

            Set<UUID> nextTalkerIds = new HashSet<>();
            ServerLevel level = server.getLevel(address.dimension());
            if (level == null) {
                localTalkerIds = Set.of();
                return;
            }

            BlockPos blockPos = address.blockPos();
            if (blockOutputEnabled) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    UUID playerId = player.getUUID();
                    if ((remotePlayerId != null && playerId.equals(remotePlayerId)) || player.level() != level) {
                        continue;
                    }

                    if (player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D)
                            > SPEAKER_INPUT_RANGE_SQR) {
                        continue;
                    }

                    if (serverApi.getConnectionOf(playerId) == null) {
                        continue;
                    }

                    nextTalkerIds.add(playerId);
                }
            }

            if (listenerPlayerId != null && serverApi.getConnectionOf(listenerPlayerId) != null) {
                nextTalkerIds.add(listenerPlayerId);
            }

            localTalkerIds = nextTalkerIds.isEmpty() ? Set.of() : Set.copyOf(nextTalkerIds);
        }

        private boolean playIncomingAudio(MicrophonePacket packet) {
            if (closed || serverApi == null) {
                return false;
            }

            if (packet == null || packet.getOpusEncodedData() == null) {
                return false;
            }

            boolean sent = false;
            if (blockOutputEnabled && speakerChannel != null) {
                sent |= sendPhoneAudio(speakerChannel, packet,
                        "Unable to forward remote phone audio to home phone block via SVC");
            }

            if (handsetChannel != null) {
                sent |= sendPhoneAudio(handsetChannel, packet,
                        "Unable to forward remote phone audio to handset via SVC");
            }

            return sent;
        }

        private boolean routeLocalAudio(UUID playerId, MicrophonePacketEvent event) {
            if (closed || serverApi == null || (remotePlayerId == null && remoteBridgeAddresses.isEmpty())) {
                return false;
            }

            MicrophonePacket packet = event.getPacket();
            if (packet == null || packet.getOpusEncodedData() == null) {
                return false;
            }

            boolean sent = false;
            if (remotePlayerChannel != null) {
                sent |= sendPhoneAudio(remotePlayerChannel, packet,
                        "Unable to forward local phone audio to remote player via SVC");
            }

            for (HomePhoneAddress remoteBridgeAddress : remoteBridgeAddresses) {
                SpeakerBridge remoteBridge = getActiveBridge(remoteBridgeAddress);
                if (remoteBridge != null) {
                    sent |= remoteBridge.playIncomingAudio(packet);
                }
            }

            return sent;
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            localTalkerIds = Set.of();
            flushChannel(speakerChannel);
            flushChannel(handsetChannel);
            flushChannel(remotePlayerChannel);
            speakerChannel = null;
            handsetChannel = null;
            remotePlayerChannel = null;
        }
    }

    private static final class PlayerBridge {
        private final UUID listenerPlayerId;
        private final UUID remotePlayerId;
        private StaticAudioChannel listenerChannel;
        private volatile boolean closed;

        private PlayerBridge(UUID listenerPlayerId, UUID remotePlayerId) {
            this.listenerPlayerId = listenerPlayerId;
            this.remotePlayerId = remotePlayerId;
        }

        private static PlayerBridge create(PlayerCallBridge bridge) {
            if (bridge == null || serverApi == null) {
                return null;
            }

            PlayerBridge playerBridge = new PlayerBridge(
                    bridge.listenerPlayerId(),
                    bridge.remotePlayerId()
            );
            playerBridge.refreshChannel();
            return playerBridge;
        }

        private boolean matches(PlayerCallBridge bridge) {
            return !closed
                    && bridge != null
                    && listenerPlayerId.equals(bridge.listenerPlayerId())
                    && remotePlayerId.equals(bridge.remotePlayerId());
        }

        private boolean isRemotePlayer(UUID playerId) {
            return !closed && remotePlayerId.equals(playerId);
        }

        private void refreshChannel() {
            if (closed || serverApi == null || listenerChannel != null) {
                return;
            }

            try {
                VoicechatConnection connection = serverApi.getConnectionOf(listenerPlayerId);
                if (connection == null) {
                    return;
                }

                UUID channelId = UUID.randomUUID();
                listenerChannel = serverApi.createStaticAudioChannel(
                        channelId,
                        connection.getPlayer().getServerLevel(),
                        connection
                );
            } catch (Throwable throwable) {
                listenerChannel = null;
                BPhone.LOGGER.debug("Unable to create SVC mobile phone listener channel", throwable);
            }
        }

        private boolean playIncomingAudio(MicrophonePacket packet) {
            if (closed || listenerChannel == null || serverApi == null
                    || packet == null || packet.getOpusEncodedData() == null) {
                return false;
            }

            try {
                return sendPhoneAudio(listenerChannel, packet,
                        "Unable to forward mobile phone audio to listener via SVC");
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to forward mobile phone audio to listener via SVC", throwable);
                return false;
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            flushChannel(listenerChannel);
            listenerChannel = null;
        }
    }

    private static SpeakerBridge getActiveBridge(HomePhoneAddress address) {
        synchronized (ACTIVE_BRIDGES) {
            return ACTIVE_BRIDGES.get(address);
        }
    }
}
