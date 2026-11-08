package cn.erindax.bphone.homephone.voice;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.block.entity.HomePhoneRegistry.HomePhoneAddress;
import cn.erindax.bphone.phone.PhoneCallManager;
import cn.erindax.bphone.phone.PhoneCallManager.HomePhoneSpeakerBridge;
import cn.erindax.bphone.phone.PhoneCallManager.PlayerCallBridge;
import cn.erindax.bphone.phone.PhoneData;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.PlayerActivationInfo;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.audio.source.ServerStaticSource;
import su.plo.voice.api.server.event.connection.TcpPacketReceivedEvent;
import su.plo.voice.api.server.event.connection.UdpPacketReceivedEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class HomePhoneVoiceHook {
    private static final short SPEAKER_OUTPUT_DISTANCE = Short.MAX_VALUE;
    private static final double SOURCE_LINE_DEFAULT_VOLUME = 1.0D;
    private static final double SPEAKER_INPUT_RANGE_SQR = Double.MAX_VALUE;
    private static final String SOURCE_NAME_PREFIX = "Phone : ";

    private static final Map<HomePhoneAddress, SpeakerBridge> ACTIVE_BRIDGES = new HashMap<>();
    private static final Map<UUID, PlayerBridge> ACTIVE_PLAYER_BRIDGES = new HashMap<>();
    private static boolean initialized;
    private static boolean addonLoaded;
    private static HomePhoneSpeakerAddon addon;
    private static PlasmoVoiceServer voiceServer;
    private static ServerSourceLine sourceLine;

    private HomePhoneVoiceHook() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        TickEvent.SERVER_PRE.register(HomePhoneVoiceHook::onServerTick);
        LifecycleEvent.SERVER_STOPPING.register(server -> clearAllBridges());

        try {
            addon = new HomePhoneSpeakerAddon();
            PlasmoVoiceServer.getAddonsLoader().load(addon);
            addonLoaded = true;
        } catch (Throwable throwable) {
            addon = null;
            addonLoaded = false;
            BPhone.LOGGER.warn("Unable to initialize Plasmo Voice home phone addon", throwable);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (!addonLoaded || sourceLine == null || voiceServer == null) {
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

                activeBridge.refresh();
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

    private static void handleAudioPacket(VoiceServerPlayer sourcePlayer, PlayerAudioPacket audioPacket) {
        if (sourcePlayer == null || audioPacket == null) {
            return;
        }

        UUID sourcePlayerId = getPlayerId(sourcePlayer);
        if (sourcePlayerId == null) {
            return;
        }

        Collection<SpeakerBridge> bridges = snapshotActiveBridges();
        for (SpeakerBridge bridge : bridges) {
            if (bridge.isRemotePlayer(sourcePlayerId)) {
                bridge.playIncomingAudio(sourcePlayer, audioPacket);
            }
        }

        for (SpeakerBridge bridge : bridges) {
            if (bridge.hasLocalTalker(sourcePlayerId)) {
                bridge.routeLocalAudio(sourcePlayerId, sourcePlayer, audioPacket);
            }
        }

        Collection<PlayerBridge> playerBridges = snapshotActivePlayerBridges();
        for (PlayerBridge bridge : playerBridges) {
            if (bridge.isRemotePlayer(sourcePlayerId)) {
                bridge.playIncomingAudio(sourcePlayer, audioPacket);
            }
        }
    }

    private static void handleAudioEnd(VoiceServerPlayer sourcePlayer, PlayerAudioEndPacket audioEndPacket) {
        if (sourcePlayer == null || audioEndPacket == null) {
            return;
        }

        UUID sourcePlayerId = getPlayerId(sourcePlayer);
        if (sourcePlayerId == null) {
            return;
        }

        Collection<SpeakerBridge> bridges = snapshotActiveBridges();
        for (SpeakerBridge bridge : bridges) {
            if (bridge.isRemotePlayer(sourcePlayerId)) {
                bridge.playIncomingAudioEnd(audioEndPacket);
            }
        }

        for (SpeakerBridge bridge : bridges) {
            if (bridge.hasLocalTalker(sourcePlayerId)) {
                bridge.routeLocalAudioEnd(sourcePlayerId, audioEndPacket);
            }
        }

        Collection<PlayerBridge> playerBridges = snapshotActivePlayerBridges();
        for (PlayerBridge bridge : playerBridges) {
            if (bridge.isRemotePlayer(sourcePlayerId)) {
                bridge.playIncomingAudioEnd(audioEndPacket);
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

    private static VoiceServerPlayer resolveVoicePlayer(UUID playerId) {
        if (voiceServer == null || playerId == null) {
            return null;
        }

        Optional<VoiceServerPlayer> voicePlayer = voiceServer.getPlayerManager().getPlayerById(playerId);
        if (voicePlayer.isEmpty()) {
            return null;
        }

        VoiceServerPlayer resolved = voicePlayer.get();
        return resolved.hasVoiceChat() && !resolved.isVoiceDisabled() ? resolved : null;
    }

    private static UUID getPlayerId(VoiceServerPlayer voicePlayer) {
        try {
            ServerPlayer player = voicePlayer.getInstance().getInstance();
            return player.getUUID();
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to resolve voice player id for home phone speaker", throwable);
            return null;
        }
    }

    private static ServerPos3d resolvePhonePosition(MinecraftServer server, HomePhoneAddress address) {
        if (voiceServer == null || server == null || address == null) {
            return null;
        }

        ServerLevel level = server.getLevel(address.dimension());
        if (level == null) {
            return null;
        }

        BlockPos blockPos = address.blockPos();
        return new ServerPos3d(
                voiceServer.getMinecraftServer().getWorld(level),
                blockPos.getX() + 0.5D,
                blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D,
                0.0F,
                0.0F
        );
    }

    @Addon(
            id = "beat_home_phone",
            name = "BEAT Home Phone",
            scope = AddonLoaderScope.SERVER,
            version = "1.1.1",
            authors = {"CatLab Design"}
    )
    private static final class HomePhoneSpeakerAddon implements AddonInitializer {
        @InjectPlasmoVoice
        private PlasmoVoiceServer server;

        @Override
        public void onAddonInitialize() {
            voiceServer = server;
            sourceLine = server.getSourceLineManager()
                    .createBuilder(this, "home_phone_speaker", "block.bphone.home_phone",
                            BPhone.MOD_ID + ":textures/gui/voice_overlay.png", 0)
                    .withPlayers(true)
                    .setDefaultVolume(SOURCE_LINE_DEFAULT_VOLUME)
                    .build();
        }

        @Override
        public void onAddonShutdown() {
            clearAllBridges();
            sourceLine = null;
            voiceServer = null;
        }

        @EventSubscribe
        public void onUdpPacketReceived(UdpPacketReceivedEvent event) {
            if (event.getPacket() instanceof PlayerAudioPacket audioPacket) {
                handleAudioPacket(event.getConnection().getPlayer(), audioPacket);
            }
        }

        @EventSubscribe
        public void onTcpPacketReceived(TcpPacketReceivedEvent event) {
            if (event.getPacket() instanceof PlayerAudioEndPacket audioEndPacket) {
                handleAudioEnd(event.getPlayer(), audioEndPacket);
            }
        }
    }

    private static final class SpeakerBridge {
        private final HomePhoneAddress address;
        private final String sourceName;
        private final ServerStaticSource outputSource;
        private final Map<UUID, ServerDirectSource> localTalkerDirectSources = new HashMap<>();
        private ServerDirectSource listenerDirectSource;
        private boolean blockOutputEnabled;
        private UUID listenerPlayerId;
        private volatile UUID remotePlayerId;
        private volatile List<HomePhoneAddress> remoteBridgeAddresses = List.of();
        private volatile Set<UUID> packetLocalTalkers = Set.of();
        private volatile Map<UUID, ServerDirectSource> packetDirectSources = Map.of();
        private volatile boolean closed;

        private SpeakerBridge(HomePhoneAddress address, String sourceName, boolean blockOutputEnabled, UUID listenerPlayerId,
                              UUID remotePlayerId, List<HomePhoneAddress> remoteBridgeAddresses,
                              ServerStaticSource outputSource) {
            this.address = address;
            this.sourceName = sourceName;
            this.blockOutputEnabled = blockOutputEnabled;
            this.listenerPlayerId = listenerPlayerId;
            this.remotePlayerId = remotePlayerId;
            this.remoteBridgeAddresses = remoteBridgeAddresses;
            this.outputSource = outputSource;
        }

        private static SpeakerBridge create(MinecraftServer server, HomePhoneSpeakerBridge bridge) {
            ServerPos3d position = resolvePhonePosition(server, bridge.address());
            if (position == null || sourceLine == null) {
                return null;
            }

            try {
                ServerStaticSource outputSource = sourceLine.createStaticSource(position, false);
                outputSource.setIconVisible(false);
                outputSource.setName(buildSourceName(bridge.address()));

                SpeakerBridge speakerBridge = new SpeakerBridge(
                        bridge.address(),
                        buildSourceName(bridge.address()),
                        bridge.speakerEnabled(),
                        bridge.handsetHolderId(),
                        bridge.remotePlayerId(),
                        bridge.remoteBridgeAddresses(),
                        outputSource
                );
                speakerBridge.refresh(server, bridge);
                return speakerBridge;
            } catch (Throwable throwable) {
                BPhone.LOGGER.warn("Unable to create home phone speaker bridge source", throwable);
                return null;
            }
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
            return !closed && packetLocalTalkers.contains(playerId);
        }

        private void refresh(MinecraftServer server, HomePhoneSpeakerBridge bridge) {
            if (closed) {
                return;
            }

            blockOutputEnabled = bridge.speakerEnabled();
            listenerPlayerId = bridge.handsetHolderId();
            remotePlayerId = bridge.remotePlayerId();
            remoteBridgeAddresses = bridge.remoteBridgeAddresses();
            refreshListenerSource(resolveVoicePlayer(listenerPlayerId));
            refreshLocalTalkers(server, resolveVoicePlayer(remotePlayerId));
        }

        private void refreshListenerSource(VoiceServerPlayer listenerPlayer) {
            if (closed || sourceLine == null || listenerPlayer == null) {
                removeListenerSource();
                return;
            }

            if (listenerDirectSource != null) {
                return;
            }

            try {
                listenerDirectSource = sourceLine.createDirectSource(listenerPlayer, false);
                listenerDirectSource.setIconVisible(false);
                listenerDirectSource.setCameraRelative(true);
                listenerDirectSource.setName(sourceName);
            } catch (Throwable throwable) {
                listenerDirectSource = null;
                BPhone.LOGGER.debug("Unable to create home phone handset listener source", throwable);
            }
        }

        private void refreshLocalTalkers(MinecraftServer server, VoiceServerPlayer remotePlayer) {
            if (closed || sourceLine == null) {
                return;
            }

            ServerLevel level = server.getLevel(address.dimension());
            if (level == null) {
                updatePacketLocalTalkers(Set.of());
                updatePacketDirectSources(Map.of());
                clearLocalTalkerSources();
                return;
            }

            BlockPos blockPos = address.blockPos();
            Map<UUID, ServerDirectSource> nextDirectSources = new HashMap<>();
            Set<UUID> nextTalkerIds = new HashSet<>();

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

                    if (resolveVoicePlayer(playerId) == null) {
                        continue;
                    }

                    nextTalkerIds.add(playerId);
                }
            }

            if (listenerPlayerId != null && resolveVoicePlayer(listenerPlayerId) != null) {
                nextTalkerIds.add(listenerPlayerId);
            }

            for (UUID talkerId : nextTalkerIds) {
                if (remotePlayer == null) {
                    continue;
                }

                ServerDirectSource talkerSource = localTalkerDirectSources.get(talkerId);
                if (talkerSource == null) {
                    try {
                        talkerSource = sourceLine.createDirectSource(remotePlayer, false);
                        talkerSource.setIconVisible(false);
                        talkerSource.setCameraRelative(true);
                        talkerSource.setName(sourceName);
                    } catch (Throwable throwable) {
                        BPhone.LOGGER.debug("Unable to create home phone talkback source", throwable);
                        continue;
                    }
                } else {
                    talkerSource.setName(sourceName);
                }

                nextDirectSources.put(talkerId, talkerSource);
            }

            updatePacketLocalTalkers(nextTalkerIds);
            updatePacketDirectSources(nextDirectSources);

            for (Map.Entry<UUID, ServerDirectSource> entry : localTalkerDirectSources.entrySet()) {
                if (!nextDirectSources.containsKey(entry.getKey())) {
                    removeSource(entry.getValue());
                }
            }

            localTalkerDirectSources.clear();
            localTalkerDirectSources.putAll(nextDirectSources);
        }

        private void removeListenerSource() {
            if (listenerDirectSource == null) {
                return;
            }

            removeSource(listenerDirectSource);
            listenerDirectSource = null;
        }

        private void clearLocalTalkerSources() {
            for (ServerDirectSource talkerSource : localTalkerDirectSources.values()) {
                removeSource(talkerSource);
            }
            localTalkerDirectSources.clear();
        }

        private void updatePacketLocalTalkers(Set<UUID> nextTalkers) {
            packetLocalTalkers = nextTalkers.isEmpty() ? Set.of() : Set.copyOf(nextTalkers);
        }

        private void updatePacketDirectSources(Map<UUID, ServerDirectSource> nextTalkers) {
            packetDirectSources = nextTalkers.isEmpty() ? Map.of() : Map.copyOf(nextTalkers);
        }

        private void playIncomingAudio(VoiceServerPlayer sourcePlayer, PlayerAudioPacket audioPacket) {
            if (closed) {
                return;
            }

            if (blockOutputEnabled) {
                try {
                    outputSource.sendAudioFrame(
                            audioPacket.getData(),
                            audioPacket.getSequenceNumber(),
                            SPEAKER_OUTPUT_DISTANCE,
                            new PlayerActivationInfo(sourcePlayer, audioPacket)
                    );
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to forward remote phone audio to home phone block", throwable);
                }
            }

            if (listenerDirectSource != null) {
                try {
                    listenerDirectSource.sendAudioFrame(
                            audioPacket.getData(),
                            audioPacket.getSequenceNumber(),
                            new PlayerActivationInfo(sourcePlayer, audioPacket)
                    );
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to forward remote phone audio to handset listener", throwable);
                }
            }
        }

        private void playIncomingAudioEnd(PlayerAudioEndPacket audioEndPacket) {
            if (closed) {
                return;
            }

            if (blockOutputEnabled) {
                try {
                    outputSource.sendAudioEnd(audioEndPacket.getSequenceNumber(), SPEAKER_OUTPUT_DISTANCE);
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to forward remote phone audio end to home phone block", throwable);
                }
            }

            if (listenerDirectSource != null) {
                try {
                    listenerDirectSource.sendAudioEnd(audioEndPacket.getSequenceNumber());
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to forward remote phone audio end to handset listener", throwable);
                }
            }
        }

        private void routeLocalAudio(UUID playerId, VoiceServerPlayer sourcePlayer, PlayerAudioPacket audioPacket) {
            if (closed) {
                return;
            }

            ServerDirectSource talkerSource = packetDirectSources.get(playerId);
            if (talkerSource != null) {
                try {
                    talkerSource.sendAudioFrame(
                            audioPacket.getData(),
                            audioPacket.getSequenceNumber(),
                            new PlayerActivationInfo(sourcePlayer, audioPacket)
                    );
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to forward local phone audio to remote player", throwable);
                }
            }

            for (HomePhoneAddress remoteBridgeAddress : remoteBridgeAddresses) {
                SpeakerBridge remoteBridge = getActiveBridge(remoteBridgeAddress);
                if (remoteBridge != null) {
                    remoteBridge.playIncomingAudio(sourcePlayer, audioPacket);
                }
            }
        }

        private void routeLocalAudioEnd(UUID playerId, PlayerAudioEndPacket audioEndPacket) {
            if (closed) {
                return;
            }

            ServerDirectSource talkerSource = packetDirectSources.get(playerId);
            if (talkerSource != null) {
                try {
                    talkerSource.sendAudioEnd(audioEndPacket.getSequenceNumber());
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to forward local phone audio end to remote player", throwable);
                }
            }

            for (HomePhoneAddress remoteBridgeAddress : remoteBridgeAddresses) {
                SpeakerBridge remoteBridge = getActiveBridge(remoteBridgeAddress);
                if (remoteBridge != null) {
                    remoteBridge.playIncomingAudioEnd(audioEndPacket);
                }
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            packetLocalTalkers = Set.of();
            packetDirectSources = Map.of();
            removeSource(outputSource);
            removeListenerSource();
            clearLocalTalkerSources();
        }

        private void removeSource(Object source) {
            if (source instanceof ServerStaticSource staticSource) {
                try {
                    staticSource.remove();
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to remove home phone speaker static source", throwable);
                }
                return;
            }

            if (source instanceof ServerDirectSource directSource) {
                try {
                    directSource.remove();
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to remove home phone speaker direct source", throwable);
                }
            }
        }
    }

    private static final class PlayerBridge {
        private final UUID listenerPlayerId;
        private final UUID remotePlayerId;
        private final String sourceName;
        private ServerDirectSource listenerSource;
        private volatile boolean closed;

        private PlayerBridge(UUID listenerPlayerId, UUID remotePlayerId, String sourceName) {
            this.listenerPlayerId = listenerPlayerId;
            this.remotePlayerId = remotePlayerId;
            this.sourceName = sourceName;
        }

        private static PlayerBridge create(PlayerCallBridge bridge) {
            if (bridge == null || sourceLine == null) {
                return null;
            }

            PlayerBridge playerBridge = new PlayerBridge(
                    bridge.listenerPlayerId(),
                    bridge.remotePlayerId(),
                    buildSourceName(bridge.remoteDisplayName())
            );
            playerBridge.refresh();
            return playerBridge;
        }

        private boolean matches(PlayerCallBridge bridge) {
            return !closed
                    && bridge != null
                    && listenerPlayerId.equals(bridge.listenerPlayerId())
                    && remotePlayerId.equals(bridge.remotePlayerId())
                    && sourceName.equals(buildSourceName(bridge.remoteDisplayName()));
        }

        private boolean isRemotePlayer(UUID playerId) {
            return !closed && remotePlayerId.equals(playerId);
        }

        private void refresh() {
            if (closed || sourceLine == null) {
                return;
            }

            VoiceServerPlayer listenerPlayer = resolveVoicePlayer(listenerPlayerId);
            if (listenerPlayer == null) {
                removeListenerSource();
                return;
            }

            if (listenerSource != null) {
                return;
            }

            try {
                listenerSource = sourceLine.createDirectSource(listenerPlayer, false);
                listenerSource.setIconVisible(false);
                listenerSource.setCameraRelative(true);
                listenerSource.setName(sourceName);
            } catch (Throwable throwable) {
                listenerSource = null;
                BPhone.LOGGER.debug("Unable to create mobile phone call listener source", throwable);
            }
        }

        private void playIncomingAudio(VoiceServerPlayer sourcePlayer, PlayerAudioPacket audioPacket) {
            if (closed || listenerSource == null) {
                return;
            }

            try {
                listenerSource.sendAudioFrame(
                        audioPacket.getData(),
                        audioPacket.getSequenceNumber(),
                        new PlayerActivationInfo(sourcePlayer, audioPacket)
                );
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to forward mobile phone audio to listener", throwable);
            }
        }

        private void playIncomingAudioEnd(PlayerAudioEndPacket audioEndPacket) {
            if (closed || listenerSource == null) {
                return;
            }

            try {
                listenerSource.sendAudioEnd(audioEndPacket.getSequenceNumber());
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to forward mobile phone audio end to listener", throwable);
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            removeListenerSource();
        }

        private void removeListenerSource() {
            if (listenerSource == null) {
                return;
            }

            try {
                listenerSource.remove();
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to remove mobile phone direct source", throwable);
            } finally {
                listenerSource = null;
            }
        }
    }

    private static SpeakerBridge getActiveBridge(HomePhoneAddress address) {
        synchronized (ACTIVE_BRIDGES) {
            return ACTIVE_BRIDGES.get(address);
        }
    }

    private static String buildSourceName(HomePhoneAddress address) {
        if (address == null) {
            return "Phone";
        }

        return SOURCE_NAME_PREFIX + PhoneData.getHomePhoneNumber(address.dimension(), address.blockPos());
    }

    private static String buildSourceName(String remoteDisplayName) {
        if (remoteDisplayName == null || remoteDisplayName.isBlank()) {
            return "Phone";
        }

        return SOURCE_NAME_PREFIX + remoteDisplayName;
    }
}
