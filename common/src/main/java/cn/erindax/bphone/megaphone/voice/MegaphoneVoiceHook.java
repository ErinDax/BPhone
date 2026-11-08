package cn.erindax.bphone.megaphone.voice;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.item.MegaphoneItem;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
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
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.audio.line.VoiceSourceLine;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MegaphoneVoiceHook {
    private static final short MEGAPHONE_OUTPUT_DISTANCE = 28;
    private static final int MEGAPHONE_OUTPUT_ANGLE = 120;
    private static final int SPEAKING_GRACE_TICKS = 4;

    private static final Map<UUID, MegaphoneBridge> ACTIVE_BRIDGES = new HashMap<>();
    private static boolean initialized;
    private static boolean addonLoaded;
    private static MegaphoneAddon addon;
    private static PlasmoVoiceServer voiceServer;
    private static ServerSourceLine sourceLine;

    private MegaphoneVoiceHook() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        TickEvent.SERVER_PRE.register(MegaphoneVoiceHook::onServerTick);
        LifecycleEvent.SERVER_STOPPING.register(server -> clearAllBridges());

        try {
            addon = new MegaphoneAddon();
            PlasmoVoiceServer.getAddonsLoader().load(addon);
            addonLoaded = true;
        } catch (Throwable throwable) {
            addon = null;
            addonLoaded = false;
            BPhone.LOGGER.warn("Unable to initialize Plasmo Voice megaphone addon", throwable);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (!addonLoaded || voiceServer == null) {
            clearAllBridges();
            return;
        }

        ensureSourceLine();
        if (sourceLine == null) {
            clearAllBridges();
            return;
        }

        Set<UUID> desiredPlayers = new HashSet<>();
        synchronized (ACTIVE_BRIDGES) {
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                if (!MegaphoneItem.isUsingMegaphone(serverPlayer)) {
                    continue;
                }

                VoiceServerPlayer voicePlayer = resolveVoicePlayer(serverPlayer.getUUID());
                if (voicePlayer == null) {
                    continue;
                }

                desiredPlayers.add(serverPlayer.getUUID());
                MegaphoneBridge bridge = ACTIVE_BRIDGES.get(serverPlayer.getUUID());
                if (bridge == null) {
                    bridge = MegaphoneBridge.create(serverPlayer, voicePlayer);
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

    public static boolean isSpeaking(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        synchronized (ACTIVE_BRIDGES) {
            MegaphoneBridge bridge = ACTIVE_BRIDGES.get(playerId);
            return bridge != null && !bridge.closed && bridge.isSpeaking();
        }
    }

    private static void ensureSourceLine() {
        if (sourceLine != null || voiceServer == null) {
            return;
        }

        sourceLine = voiceServer.getSourceLineManager()
                .getLineByName(VoiceSourceLine.PROXIMITY_NAME)
                .orElse(null);
    }

    private static void handleAudioPacket(VoiceServerPlayer sourcePlayer, PlayerAudioPacket audioPacket, UdpPacketReceivedEvent event) {
        if (sourcePlayer == null || audioPacket == null || event == null) {
            return;
        }

        UUID sourcePlayerId = getPlayerId(sourcePlayer);
        if (sourcePlayerId == null) {
            return;
        }

        MegaphoneBridge bridge;
        synchronized (ACTIVE_BRIDGES) {
            bridge = ACTIVE_BRIDGES.get(sourcePlayerId);
        }
        if (bridge == null || bridge.closed) {
            return;
        }

        event.setCancelled(true);
        bridge.markSpeaking();
        bridge.playAudio(sourcePlayer, audioPacket);
    }

    private static void handleAudioEnd(VoiceServerPlayer sourcePlayer, PlayerAudioEndPacket audioEndPacket, TcpPacketReceivedEvent event) {
        if (sourcePlayer == null || audioEndPacket == null || event == null) {
            return;
        }

        UUID sourcePlayerId = getPlayerId(sourcePlayer);
        if (sourcePlayerId == null) {
            return;
        }

        MegaphoneBridge bridge;
        synchronized (ACTIVE_BRIDGES) {
            bridge = ACTIVE_BRIDGES.get(sourcePlayerId);
        }
        if (bridge == null || bridge.closed) {
            return;
        }

        event.setCancelled(true);
        bridge.stopSpeaking();
        bridge.playAudioEnd(audioEndPacket);
    }

    private static void clearAllBridges() {
        synchronized (ACTIVE_BRIDGES) {
            for (MegaphoneBridge bridge : ACTIVE_BRIDGES.values()) {
                bridge.close();
            }
            ACTIVE_BRIDGES.clear();
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
            return voicePlayer.getInstance().getUuid();
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to resolve voice player id for megaphone", throwable);
            return null;
        }
    }

    private static ServerPos3d resolveMegaphonePosition(ServerPlayer player) {
        VoiceServerPlayer voicePlayer = resolveVoicePlayer(player.getUUID());
        if (voicePlayer == null || voiceServer == null) {
            return null;
        }

        return new ServerPos3d(
                voiceServer.getMinecraftServer().getWorld(player.serverLevel()),
                player.getX(),
                player.getEyeY() - 0.18D,
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    @Addon(
            id = "beat_megaphone_server",
            name = "BEAT Megaphone",
            scope = AddonLoaderScope.SERVER,
            version = "1.1.1",
            authors = {"CatLab Design"}
    )
    private static final class MegaphoneAddon implements AddonInitializer {
        @InjectPlasmoVoice
        private PlasmoVoiceServer server;

        @Override
        public void onAddonInitialize() {
            voiceServer = server;
            ensureSourceLine();
            if (sourceLine == null) {
                BPhone.LOGGER.warn("Unable to find Plasmo Voice proximity source line for megaphone");
            }
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
                handleAudioPacket(event.getConnection().getPlayer(), audioPacket, event);
            }
        }

        @EventSubscribe
        public void onTcpPacketReceived(TcpPacketReceivedEvent event) {
            if (event.getPacket() instanceof PlayerAudioEndPacket audioEndPacket) {
                handleAudioEnd(event.getPlayer(), audioEndPacket, event);
            }
        }
    }

    private static final class MegaphoneBridge {
        private final UUID playerId;
        private final ServerStaticSource outputSource;
        private volatile ServerDirectSource monitorSource;
        private volatile boolean closed;
        private int speakingTicksRemaining;

        private MegaphoneBridge(UUID playerId, ServerStaticSource outputSource, ServerDirectSource monitorSource) {
            this.playerId = playerId;
            this.outputSource = outputSource;
            this.monitorSource = monitorSource;
        }

        private static MegaphoneBridge create(ServerPlayer serverPlayer, VoiceServerPlayer voicePlayer) {
            if (sourceLine == null) {
                return null;
            }

            ServerPos3d position = resolveMegaphonePosition(serverPlayer);
            if (position == null) {
                return null;
            }

            try {
                ServerStaticSource outputSource = sourceLine.createStaticSource(position, false);
                outputSource.setIconVisible(false);
                outputSource.setName("");
                outputSource.setAngle(MEGAPHONE_OUTPUT_ANGLE);
                outputSource.addFilter(player -> !serverPlayer.getUUID().equals(resolveVoicePlayerId(player)));

                return new MegaphoneBridge(serverPlayer.getUUID(), outputSource, null);
            } catch (Throwable throwable) {
                BPhone.LOGGER.warn("Unable to create megaphone voice bridge", throwable);
                return null;
            }
        }

        private void refresh(ServerPlayer serverPlayer) {
            if (closed) {
                return;
            }

            ServerPos3d position = resolveMegaphonePosition(serverPlayer);
            if (position != null) {
                outputSource.setPosition(position);
                outputSource.setAngle(MEGAPHONE_OUTPUT_ANGLE);
            }
        }

        private void serverTick() {
            if (speakingTicksRemaining > 0) {
                speakingTicksRemaining--;
            }
        }

        private void markSpeaking() {
            speakingTicksRemaining = SPEAKING_GRACE_TICKS;
        }

        private void stopSpeaking() {
            speakingTicksRemaining = 0;
        }

        private boolean isSpeaking() {
            return speakingTicksRemaining > 0;
        }

        private void playAudio(VoiceServerPlayer sourcePlayer, PlayerAudioPacket audioPacket) {
            if (closed) {
                return;
            }

            PlayerActivationInfo activationInfo = new PlayerActivationInfo(sourcePlayer, audioPacket);
            try {
                outputSource.sendAudioFrame(
                        audioPacket.getData(),
                        audioPacket.getSequenceNumber(),
                        MEGAPHONE_OUTPUT_DISTANCE,
                        activationInfo
                );
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to broadcast megaphone audio", throwable);
            }
        }

        private void playAudioEnd(PlayerAudioEndPacket audioEndPacket) {
            if (closed) {
                return;
            }

            try {
                outputSource.sendAudioEnd(audioEndPacket.getSequenceNumber(), MEGAPHONE_OUTPUT_DISTANCE);
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to end megaphone broadcast audio", throwable);
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            speakingTicksRemaining = 0;

            try {
                outputSource.remove();
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to remove megaphone static source", throwable);
            }
        }
    }

    private static UUID resolveVoicePlayerId(VoicePlayer voicePlayer) {
        if (voicePlayer == null) {
            return null;
        }

        try {
            return voicePlayer.getInstance().getUuid();
        } catch (Throwable throwable) {
            return null;
        }
    }
}
