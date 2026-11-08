package cn.erindax.bphone.voice.svc;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModSounds;
import cn.erindax.bphone.item.WalkieRadioItem;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SvcWalkieVoiceHook {

    private static final short WALKIE_OUTPUT_DISTANCE = Short.MAX_VALUE;
    private static final float WALKIE_VOICE_GAIN = 2.35F;
    private static final float CONNECT_SOUND_VOLUME = 1.35F;
    private static final float CLOSE_SOUND_VOLUME = 1.25F;
    private static final float TALK_SOUND_VOLUME = 1.15F;
    private static final int RELEASE_GRACE_TICKS = 5;

    private static final Map<UUID, InteractionHand> TALKING_HANDS = new HashMap<>();
    private static final Map<UUID, WalkieBridge> ACTIVE_BRIDGES = new HashMap<>();

    private static VoicechatServerApi serverApi;
    private static boolean tickRegistered;

    private SvcWalkieVoiceHook() {
    }

    public static void onServerStarted(VoicechatServerApi api) {
        serverApi = api;

        if (!tickRegistered) {
            tickRegistered = true;
            TickEvent.SERVER_PRE.register(SvcWalkieVoiceHook::onServerTick);
        }
    }

    public static void onServerStopped() {
        clearAllBridges();
        serverApi = null;
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

    public static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (serverApi == null || event == null || event.getSenderConnection() == null) {
            return;
        }

        UUID senderId = event.getSenderConnection().getPlayer().getUuid();

        WalkieBridge bridge;
        synchronized (ACTIVE_BRIDGES) {
            bridge = ACTIVE_BRIDGES.get(senderId);
        }

        if (bridge == null || bridge.closed) {
            return;
        }

        event.cancel();
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
                InteractionHand talkingHand = TALKING_HANDS.get(serverPlayer.getUUID());
                if (talkingHand == null) {
                    continue;
                }

                ItemStack talkingStack = serverPlayer.getItemInHand(talkingHand);
                if (!(talkingStack.getItem() instanceof WalkieRadioItem)) {
                    TALKING_HANDS.remove(serverPlayer.getUUID());
                    continue;
                }

                desiredPlayers.add(serverPlayer.getUUID());
                WalkieBridge bridge = ACTIVE_BRIDGES.get(serverPlayer.getUUID());
                if (bridge == null) {
                    bridge = WalkieBridge.create(server, serverPlayer, talkingStack);
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

    private static void clearAllBridges() {
        synchronized (ACTIVE_BRIDGES) {
            for (WalkieBridge bridge : ACTIVE_BRIDGES.values()) {
                bridge.close();
            }
            ACTIVE_BRIDGES.clear();
            TALKING_HANDS.clear();
        }
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

    private static void playNotifySound(ServerPlayer player, SoundEvent soundEvent, float volume, float pitch) {
        if (player == null || soundEvent == null) {
            return;
        }

        player.playNotifySound(soundEvent, SoundSource.PLAYERS, volume, pitch);
    }

    private static final class WalkieBridge {
        private final UUID sourcePlayerId;
        private final ItemStack sourceTuning;
        private final MinecraftServer minecraftServer;
        private final Map<UUID, LocationalAudioChannel> receiverChannels = new HashMap<>();
        private final Set<UUID> connectedReceiverIds = new HashSet<>();
        private OpusDecoder decoder;
        private OpusEncoder encoder;
        private volatile boolean closed;
        private int releaseGraceTicks;

        private WalkieBridge(UUID sourcePlayerId, ItemStack sourceTuning, MinecraftServer minecraftServer) {
            this.sourcePlayerId = sourcePlayerId;
            this.sourceTuning = sourceTuning;
            this.minecraftServer = minecraftServer;
        }

        private static WalkieBridge create(MinecraftServer server, ServerPlayer serverPlayer, ItemStack talkingStack) {
            if (serverApi == null) {
                return null;
            }

            ItemStack sourceTuning = talkingStack.copy();
            WalkieBridge bridge = new WalkieBridge(serverPlayer.getUUID(), sourceTuning, server);
            bridge.playConnectSounds(serverPlayer);
            playNotifySound(serverPlayer, ModSounds.WALKIE_TALK.get(), TALK_SOUND_VOLUME, 1.0F);
            return bridge;
        }

        private void refresh(ServerPlayer serverPlayer) {
            if (closed || serverApi == null) {
                return;
            }

            markStillHolding();
            syncReceiverChannels(serverPlayer);
        }

        private void markStillHolding() {
            releaseGraceTicks = 0;
        }

        private boolean markMaybeReleased() {
            releaseGraceTicks++;
            return releaseGraceTicks >= RELEASE_GRACE_TICKS;
        }

        private void routeAudio(MicrophonePacketEvent event) {
            if (closed || serverApi == null) {
                return;
            }

            MicrophonePacket packet = event.getPacket();
            ServerPlayer sourcePlayer = minecraftServer.getPlayerList().getPlayer(sourcePlayerId);
            if (sourcePlayer == null) {
                return;
            }

            byte[] amplifiedAudio = amplifyPacket(packet);
            Set<ServerPlayer> receivers = findMatchingReceivers(sourcePlayer, sourceTuning);
            for (ServerPlayer receiver : receivers) {
                LocationalAudioChannel channel = getOrCreateChannel(receiver);
                if (channel != null) {
                    try {
                        channel.updateLocation(serverApi.createPosition(
                                sourcePlayer.getX(),
                                sourcePlayer.getEyeY() - 0.12D,
                                sourcePlayer.getZ()
                        ));
                        channel.setDistance(WALKIE_OUTPUT_DISTANCE);

                        if (amplifiedAudio != null) {
                            channel.send(amplifiedAudio);
                        } else {
                            channel.send(packet);
                        }
                    } catch (Throwable throwable) {
                        BPhone.LOGGER.debug("Unable to route walkie audio via SVC", throwable);
                    }
                }
            }
        }

        private byte[] amplifyPacket(MicrophonePacket packet) {
            if (packet == null || serverApi == null || WALKIE_VOICE_GAIN <= 1.0F
                    || packet.getOpusEncodedData() == null || packet.getOpusEncodedData().length == 0) {
                return null;
            }

            try {
                if (decoder == null || decoder.isClosed()) {
                    decoder = serverApi.createDecoder();
                }
                if (encoder == null || encoder.isClosed()) {
                    encoder = serverApi.createEncoder();
                }

                short[] samples = decoder.decode(packet.getOpusEncodedData());
                if (samples == null || samples.length == 0) {
                    return null;
                }
                for (int index = 0; index < samples.length; index++) {
                    samples[index] = boostSample(samples[index]);
                }

                byte[] boostedData = encoder.encode(samples);
                if (boostedData != null && boostedData.length > 0) {
                    return boostedData;
                }
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to amplify walkie audio via SVC", throwable);
                closeCodec();
            }

            return null;
        }

        private short boostSample(short sample) {
            int boosted = Math.round(sample * WALKIE_VOICE_GAIN);
            if (boosted > Short.MAX_VALUE) {
                return Short.MAX_VALUE;
            }
            if (boosted < Short.MIN_VALUE) {
                return Short.MIN_VALUE;
            }
            return (short) boosted;
        }

        private LocationalAudioChannel getOrCreateChannel(ServerPlayer receiver) {
            if (serverApi == null) {
                return null;
            }

            LocationalAudioChannel existing = receiverChannels.get(receiver.getUUID());
            if (existing != null) {
                return existing;
            }

            try {
                VoicechatConnection connection = serverApi.getConnectionOf(receiver.getUUID());
                if (connection == null) {
                    return null;
                }

                UUID channelId = UUID.randomUUID();
                LocationalAudioChannel channel = serverApi.createLocationalAudioChannel(
                        channelId,
                        serverApi.fromServerLevel(receiver.serverLevel()),
                        serverApi.createPosition(
                                receiver.getX(),
                                receiver.getEyeY(),
                                receiver.getZ()
                        )
                );

                if (channel != null) {
                    channel.setDistance(WALKIE_OUTPUT_DISTANCE);
                    channel.setFilter(player -> receiver.getUUID().equals(player.getUuid()));
                    receiverChannels.put(receiver.getUUID(), channel);
                }

                return channel;
            } catch (Throwable throwable) {
                BPhone.LOGGER.debug("Unable to create SVC locational channel for walkie", throwable);
                return null;
            }
        }

        private void syncReceiverChannels(ServerPlayer sourcePlayer) {
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

                ServerPlayer receiver = minecraftServer.getPlayerList().getPlayer(receiverId);
                if (receiver != null) {
                    playNotifySound(receiver, ModSounds.WALKIE_CLOSE.get(), CLOSE_SOUND_VOLUME, 1.0F);
                }
                receiverChannels.remove(receiverId);
                return true;
            });
        }

        private void playConnectSounds(ServerPlayer sourcePlayer) {
            for (ServerPlayer receiver : findMatchingReceivers(sourcePlayer, sourceTuning)) {
                connectedReceiverIds.add(receiver.getUUID());
                playNotifySound(receiver, ModSounds.WALKIE_ON_YOU.get(), CONNECT_SOUND_VOLUME, 1.0F);
            }
        }

        private void close() {
            if (closed) {
                return;
            }

            closed = true;
            playCloseSounds();
            receiverChannels.clear();
            closeCodec();
        }

        private void closeCodec() {
            if (decoder != null) {
                try {
                    decoder.close();
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to close walkie opus decoder", throwable);
                } finally {
                    decoder = null;
                }
            }
            if (encoder != null) {
                try {
                    encoder.close();
                } catch (Throwable throwable) {
                    BPhone.LOGGER.debug("Unable to close walkie opus encoder", throwable);
                } finally {
                    encoder = null;
                }
            }
        }

        private void playCloseSounds() {
            for (UUID receiverId : connectedReceiverIds) {
                ServerPlayer receiver = minecraftServer.getPlayerList().getPlayer(receiverId);
                if (receiver != null) {
                    playNotifySound(receiver, ModSounds.WALKIE_CLOSE.get(), CLOSE_SOUND_VOLUME, 1.0F);
                }
            }
            connectedReceiverIds.clear();
        }
    }
}
