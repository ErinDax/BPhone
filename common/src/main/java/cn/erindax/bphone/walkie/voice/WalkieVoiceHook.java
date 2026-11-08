package cn.erindax.bphone.walkie.voice;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModSounds;
import cn.erindax.bphone.item.WalkieRadioItem;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.PlayerActivationInfo;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
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

public final class WalkieVoiceHook {
    private static final short WALKIE_OUTPUT_DISTANCE = Short.MAX_VALUE;
    private static final int WALKIE_OUTPUT_ANGLE = 360;
    private static final float CONNECT_SOUND_VOLUME = 1.35F;
    private static final float CLOSE_SOUND_VOLUME = 1.25F;
    private static final float TALK_SOUND_VOLUME = 1.15F;
    private static final int RELEASE_GRACE_TICKS = 5;
    private static final Map<UUID, WalkieBridge> ACTIVE_BRIDGES = new HashMap<>();
    private static final Map<UUID, InteractionHand> TALKING_HANDS = new HashMap<>();

    private static boolean initialized;
    private static boolean addonLoaded;
    private static WalkieAddon addon;
    private static PlasmoVoiceServer voiceServer;
    private static ServerSourceLine sourceLine;

    private WalkieVoiceHook() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        TickEvent.SERVER_PRE.register(WalkieVoiceHook::onServerTick);
        LifecycleEvent.SERVER_STOPPING.register(server -> clearAllBridges());

        try {
            addon = new WalkieAddon();
            PlasmoVoiceServer.getAddonsLoader().load(addon);
            addonLoaded = true;
        } catch (Throwable throwable) {
            addon = null;
            addonLoaded = false;
            BPhone.LOGGER.warn("Unable to initialize Plasmo Voice walkie addon", throwable);
        }
    }

    public static void setTalking(ServerPlayer player, InteractionHand hand, boolean talking) {
        if (player == null || hand == null) {
            return;
        }

        synchronized (ACTIVE_BRIDGES) {
            if (talking) {
                if (player.getItemInHand(hand).getItem() instanceof WalkieRadioItem) {
                    TALKING_HANDS.put(player.getUUID(), hand);
                }
            } else {
                TALKING_HANDS.remove(player.getUUID());
            }
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
                InteractionHand talkingHand = TALKING_HANDS.get(serverPlayer.getUUID());
                if (talkingHand == null) {
                    continue;
                }

                ItemStack talkingStack = serverPlayer.getItemInHand(talkingHand);
                if (!(talkingStack.getItem() instanceof WalkieRadioItem)) {
                    TALKING_HANDS.remove(serverPlayer.getUUID());
                    continue;
                }

                VoiceServerPlayer voicePlayer = resolveVoicePlayer(serverPlayer.getUUID());
                if (voicePlayer == null) {
                    continue;
                }

                desiredPlayers.add(serverPlayer.getUUID());
                WalkieBridge bridge = ACTIVE_BRIDGES.get(serverPlayer.getUUID());
                if (bridge == null) {
                    bridge = WalkieBridge.create(serverPlayer, talkingStack);
                    if (bridge != null) {
                        ACTIVE_BRIDGES.put(serverPlayer.getUUID(), bridge);
                    }
                    continue;
                }

                bridge.refresh(serverPlayer);
            }

            ACTIVE_BRIDGES.entrySet().removeIf(entry -> {
                if (desiredPlayers.contains(entry.getKey())) {
                    entry.getValue().markStillHolding();
                    return false;
                }

                if (!entry.getValue().markMaybeReleased()) {
                    return false;
                }

                entry.getValue().close();
                return true;
            });
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
        UUID sourcePlayerId = resolveVoicePlayerId(sourcePlayer);
        if (sourcePlayerId == null || audioPacket == null || event == null) {
            return;
        }

        WalkieBridge bridge;
        synchronized (ACTIVE_BRIDGES) {
            bridge = ACTIVE_BRIDGES.get(sourcePlayerId);
        }

        if (bridge == null || bridge.closed) {
            return;
        }

        event.setCancelled(true);
        bridge.playAudio(sourcePlayer, audioPacket);
    }

    private static void handleAudioEnd(VoiceServerPlayer sourcePlayer, PlayerAudioEndPacket audioEndPacket, TcpPacketReceivedEvent event) {
        UUID sourcePlayerId = resolveVoicePlayerId(sourcePlayer);
        if (sourcePlayerId == null || audioEndPacket == null || event == null) {
            return;
        }

        WalkieBridge bridge;
        synchronized (ACTIVE_BRIDGES) {
            bridge = ACTIVE_BRIDGES.get(sourcePlayerId);
        }

        if (bridge == null || bridge.closed) {
            return;
        }

        event.setCancelled(true);
        bridge.playAudioEnd(audioEndPacket);
    }

    private static void clearAllBridges() {
        synchronized (ACTIVE_BRIDGES) {
            for (WalkieBridge bridge : ACTIVE_BRIDGES.values()) {
                bridge.close();
            }
            ACTIVE_BRIDGES.clear();
            TALKING_HANDS.clear();
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

    private static ServerPos3d resolveWalkiePosition(ServerPlayer player) {
        if (voiceServer == null) {
            return null;
        }

        return new ServerPos3d(
                voiceServer.getMinecraftServer().getWorld(player.serverLevel()),
                player.getX(),
                player.getEyeY() - 0.12D,
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    private static boolean canReceive(ServerPlayer sourcePlayer, ItemStack sourceStack, VoicePlayer targetVoicePlayer) {
        UUID targetId = resolveVoicePlayerId(targetVoicePlayer);
        if (sourcePlayer == null || targetId == null || sourcePlayer.getUUID().equals(targetId)) {
            return false;
        }

        ServerPlayer targetPlayer = sourcePlayer.server.getPlayerList().getPlayer(targetId);
        if (targetPlayer == null || targetPlayer.level() != sourcePlayer.level()) {
            return false;
        }

        if (!targetPlayer.position().closerThan(sourcePlayer.position(), WALKIE_OUTPUT_DISTANCE)) {
            return false;
        }

        return hasMatchingWalkie(targetPlayer, sourceStack);
    }

    private static Set<ServerPlayer> findMatchingReceivers(ServerPlayer sourcePlayer, ItemStack sourceStack) {
        Set<ServerPlayer> receivers = new HashSet<>();
        if (sourcePlayer == null || sourceStack == null || sourceStack.isEmpty()) {
            return receivers;
        }

        for (ServerPlayer targetPlayer : sourcePlayer.server.getPlayerList().getPlayers()) {
            if (targetPlayer == sourcePlayer || targetPlayer.level() != sourcePlayer.level()) {
                continue;
            }

            if (!targetPlayer.position().closerThan(sourcePlayer.position(), WALKIE_OUTPUT_DISTANCE)) {
                continue;
            }

            if (hasMatchingWalkie(targetPlayer, sourceStack)) {
                receivers.add(targetPlayer);
            }
        }

        return receivers;
    }

    private static void playNotifySound(ServerPlayer player, SoundEvent soundEvent, float volume, float pitch) {
        if (player == null || soundEvent == null) {
            return;
        }

        player.playNotifySound(soundEvent, SoundSource.PLAYERS, volume, pitch);
    }

    private static boolean hasMatchingWalkie(ServerPlayer player, ItemStack sourceStack) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof WalkieRadioItem && WalkieRadioItem.hasSameTuning(sourceStack, stack)) {
                return true;
            }
        }

        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof WalkieRadioItem && WalkieRadioItem.hasSameTuning(sourceStack, stack)) {
                return true;
            }
        }

        return false;
    }

    @Addon(
            id = "beat_walkie_server",
            name = "BEAT Walkie",
            scope = AddonLoaderScope.SERVER,
            version = "1.1.1",
            authors = {"CatLab Design"}
    )
    private static final class WalkieAddon implements AddonInitializer {
        @InjectPlasmoVoice
        private PlasmoVoiceServer server;

        @Override
        public void onAddonInitialize() {
            voiceServer = server;
            ensureSourceLine();
            if (sourceLine == null) {
                BPhone.LOGGER.warn("Unable to find Plasmo Voice proximity source line for walkie");
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

    private static final class WalkieBridge {
        private final UUID sourcePlayerId;
        private final ItemStack sourceTuning;
        private final MinecraftServer minecraftServer;
        private final Set<UUID> connectedReceiverIds = new HashSet<>();
        private final ServerStaticSource outputSource;
        private volatile boolean closed;
        private int releaseGraceTicks;

        private WalkieBridge(UUID sourcePlayerId, ItemStack sourceTuning, MinecraftServer minecraftServer,
                             ServerStaticSource outputSource) {
            this.sourcePlayerId = sourcePlayerId;
            this.sourceTuning = sourceTuning;
            this.minecraftServer = minecraftServer;
            this.outputSource = outputSource;
        }

        private static WalkieBridge create(ServerPlayer serverPlayer, ItemStack talkingStack) {
            if (sourceLine == null) {
                return null;
            }

            ServerPos3d position = resolveWalkiePosition(serverPlayer);
            if (position == null) {
                return null;
            }

            ItemStack sourceTuning = talkingStack.copy();
            try {
                ServerStaticSource outputSource = sourceLine.createStaticSource(position, false);
                outputSource.setIconVisible(false);
                outputSource.setName("");
                outputSource.setAngle(WALKIE_OUTPUT_ANGLE);
                outputSource.addFilter(player -> canReceive(serverPlayer, sourceTuning, player));
                WalkieBridge bridge = new WalkieBridge(
                        serverPlayer.getUUID(),
                        sourceTuning,
                        serverPlayer.server,
                        outputSource);
                bridge.playConnectSounds(serverPlayer);
                bridge.playTalkSound(serverPlayer);
                return bridge;
            } catch (Throwable throwable) {
                BPhone.LOGGER.warn("Unable to create walkie voice bridge", throwable);
                return null;
            }
        }

        private void refresh(ServerPlayer serverPlayer) {
            if (closed) {
                return;
            }

            markStillHolding();
            ServerPos3d position = resolveWalkiePosition(serverPlayer);
            if (position != null) {
                outputSource.setPosition(position);
                outputSource.setAngle(WALKIE_OUTPUT_ANGLE);
            }

            syncConnectedReceivers(serverPlayer);
        }

        private void markStillHolding() {
            releaseGraceTicks = 0;
        }

        private boolean markMaybeReleased() {
            releaseGraceTicks++;
            return releaseGraceTicks >= RELEASE_GRACE_TICKS;
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
                        WALKIE_OUTPUT_DISTANCE,
                        activationInfo
                );
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to broadcast walkie audio", throwable);
            }
        }

        private void playAudioEnd(PlayerAudioEndPacket audioEndPacket) {
            if (closed) {
                return;
            }

            try {
                outputSource.sendAudioEnd(audioEndPacket.getSequenceNumber(), WALKIE_OUTPUT_DISTANCE);
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to end walkie audio", throwable);
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            playCloseSounds();
            try {
                outputSource.remove();
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to remove walkie static source", throwable);
            }
        }

        private void playConnectSounds(ServerPlayer sourcePlayer) {
            for (ServerPlayer receiver : findMatchingReceivers(sourcePlayer, sourceTuning)) {
                connectedReceiverIds.add(receiver.getUUID());
                playNotifySound(receiver, ModSounds.WALKIE_ON_YOU.get(), CONNECT_SOUND_VOLUME, 1.0F);
            }
        }

        private void syncConnectedReceivers(ServerPlayer sourcePlayer) {
            Set<ServerPlayer> currentReceivers = findMatchingReceivers(sourcePlayer, sourceTuning);
            Set<UUID> currentReceiverIds = new HashSet<>();
            for (ServerPlayer receiver : currentReceivers) {
                currentReceiverIds.add(receiver.getUUID());
                if (connectedReceiverIds.add(receiver.getUUID())) {
                    playNotifySound(receiver, ModSounds.WALKIE_ON_YOU.get(), CONNECT_SOUND_VOLUME, 1.0F);
                }
            }

            connectedReceiverIds.removeIf(receiverId -> {
                if (currentReceiverIds.contains(receiverId)) {
                    return false;
                }

                ServerPlayer receiver = resolveServerPlayer(receiverId);
                if (receiver != null) {
                    playNotifySound(receiver, ModSounds.WALKIE_CLOSE.get(), CLOSE_SOUND_VOLUME, 1.0F);
                }
                return true;
            });
        }

        private void playCloseSounds() {
            for (UUID receiverId : connectedReceiverIds) {
                ServerPlayer receiver = resolveServerPlayer(receiverId);
                if (receiver != null) {
                    playNotifySound(receiver, ModSounds.WALKIE_CLOSE.get(), CLOSE_SOUND_VOLUME, 1.0F);
                }
            }
            connectedReceiverIds.clear();
        }

        private void playTalkSound(ServerPlayer sourcePlayer) {
            playNotifySound(sourcePlayer, ModSounds.WALKIE_TALK.get(), TALK_SOUND_VOLUME, 1.0F);
        }

        private ServerPlayer resolveServerPlayer(UUID playerId) {
            if (minecraftServer == null || playerId == null) {
                return null;
            }

            return minecraftServer.getPlayerList().getPlayer(playerId);
        }
    }
}
