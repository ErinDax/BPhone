package cn.erindax.bphone.phone;

import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.block.entity.HomePhoneRegistry;
import cn.erindax.bphone.block.entity.HomePhoneRegistry.HomePhoneAddress;
import cn.erindax.bphone.item.HomePhoneHandsetItem;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhoneCallManager {
    private static final int UNANSWERED_CALL_TIMEOUT_TICKS = 20 * 20;
    private static final int MISSED_STATE_TICKS = 5 * 20;
    private static final int SPEAKER_EMPTY_HANGUP_TICKS = 10 * 20;
    private static final double HANDSET_RETURN_DISTANCE_SQR = Double.MAX_VALUE;
    private static final double SPEAKER_AUTO_HANGUP_DISTANCE_SQR = Double.MAX_VALUE;
    private static final Map<UUID, CallSession> ACTIVE_PLAYER_CALLS = new ConcurrentHashMap<>();
    private static final Map<HomePhoneAddress, CallSession> ACTIVE_HOME_PHONE_CALLS = new ConcurrentHashMap<>();
    private static final Map<HomePhoneAddress, Integer> SPEAKER_EMPTY_TICKS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private PhoneCallManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        PlayerEvent.PLAYER_QUIT.register(PhoneCallManager::handlePlayerQuit);
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            clearAllHomePhoneRinging();
            ACTIVE_PLAYER_CALLS.clear();
            ACTIVE_HOME_PHONE_CALLS.clear();
            SPEAKER_EMPTY_TICKS.clear();
        });
        TickEvent.SERVER_PRE.register(PhoneCallManager::handleServerTick);
    }

    public static void syncPlayer(ServerPlayer player) {
        CallSession session = ACTIVE_PLAYER_CALLS.get(player.getUUID());
        if (session == null) {
            PhoneNetworking.sendCallState(player, PhoneCallState.IDLE, "", "");
            return;
        }

        syncPlayerEndpoint(player, new PlayerEndpoint(player.getUUID()), session);
    }

    public static void syncHomePhone(ServerPlayer viewer, net.minecraft.core.BlockPos blockPos) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(endpoint.address());
        if (session == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        PhoneNetworking.sendHomePhoneCallState(
                viewer,
                blockPos,
                stateFor(endpoint, session),
                otherNumberFor(endpoint, session),
                otherNameFor(endpoint, session),
                otherProfileIdFor(endpoint, session)
        );
    }

    public static boolean hasActiveHomePhoneCall(ServerPlayer viewer, BlockPos blockPos) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null) {
            return false;
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(endpoint.address());
        return session != null && session.state != PhoneCallState.MISSED;
    }

    public static void startCall(ServerPlayer caller, String rawNumber) {
        MinecraftServer server = caller.getServer();
        if (server == null) {
            return;
        }

        if (!PhoneData.hasPhone(caller)) {
            caller.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.no_phone"));
            PhoneNetworking.sendCallState(caller, PhoneCallState.IDLE, "", "");
            return;
        }

        startCall(
                server,
                caller,
                new PlayerEndpoint(caller.getUUID()),
                PhoneData.getPhoneNumber(caller),
                caller.getGameProfile().getName(),
                rawNumber
        );
    }

    public static void startHomePhoneCall(ServerPlayer viewer, net.minecraft.core.BlockPos blockPos, String rawNumber) {
        MinecraftServer server = viewer.getServer();
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (server == null || endpoint == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        startCall(server, viewer, endpoint, endpoint.number(), endpoint.displayName(), rawNumber);
    }

    public static void answerCall(ServerPlayer player) {
        CallSession session = ACTIVE_PLAYER_CALLS.get(player.getUUID());
        if (session == null) {
            PhoneNetworking.sendCallState(player, PhoneCallState.IDLE, "", "");
            return;
        }

        answerEndpoint(new PlayerEndpoint(player.getUUID()), player);
    }

    public static void answerHomePhoneCall(ServerPlayer viewer, net.minecraft.core.BlockPos blockPos) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        answerEndpoint(endpoint, viewer);
    }

    public static void endCall(ServerPlayer player) {
        endEndpoint(new PlayerEndpoint(player.getUUID()), player);
    }

    public static void endHomePhoneCall(ServerPlayer viewer, net.minecraft.core.BlockPos blockPos) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        endEndpoint(endpoint, viewer);
    }

    public static void pickUpHomePhoneHandset(ServerPlayer viewer, BlockPos blockPos, InteractionHand hand) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null || viewer == null) {
            return;
        }

        HomePhoneBlockEntity homePhone = getHomePhone(viewer.getServer(), endpoint.address());
        if (homePhone == null) {
            return;
        }

        homePhone.setSpeakerEnabled(false);
        homePhone.setHandsetHolderId(viewer.getUUID());
        HomePhoneHandsetItem.ensurePlayerHasHandset(viewer, hand, endpoint.address(), endpoint.number());

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(endpoint.address());
        if (session != null && endpoint.equals(session.callee) && session.state == PhoneCallState.OUTGOING_RINGING) {
            answerEndpoint(endpoint, viewer);
            return;
        }

        syncHomePhone(viewer, blockPos);
    }

    public static boolean putDownHomePhoneHandset(ServerPlayer viewer, InteractionHand hand) {
        if (viewer == null || hand == null) {
            return false;
        }

        ItemStack stack = viewer.getItemInHand(hand);
        HomePhoneAddress address = HomePhoneHandsetItem.getBoundAddress(stack);
        if (address == null) {
            return false;
        }

        return putDownHomePhoneHandset(viewer, address);
    }

    public static boolean putDownHomePhoneHandset(ServerPlayer viewer, HomePhoneAddress address) {
        if (viewer == null || address == null) {
            return false;
        }

        HomePhoneBlockEntity homePhone = getHomePhone(viewer.getServer(), address);
        if (homePhone != null) {
            homePhone.setSpeakerEnabled(false);
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(address);
        if (session != null) {
            String phoneNumber = homePhone == null ? "" : homePhone.getPhoneNumber();
            endEndpoint(new HomePhoneEndpoint(address, phoneNumber), viewer);
            return true;
        }

        releaseHomePhoneHandset(viewer.getServer(), address, viewer);
        return true;
    }

    public static boolean putDownHomePhoneHandset(ServerPlayer viewer,
                                                  net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                                  BlockPos blockPos) {
        if (viewer == null || dimension == null || blockPos == null) {
            return false;
        }

        return putDownHomePhoneHandset(viewer, new HomePhoneAddress(dimension, blockPos.immutable()));
    }

    public static void toggleMute(ServerPlayer player) {
        CallSession session = ACTIVE_PLAYER_CALLS.get(player.getUUID());
        if (session == null || session.state != PhoneCallState.CONNECTED) {
            return;
        }
        session.muted = !session.muted;
        syncMuteSpeaker(session);
    }

    public static void toggleSpeaker(ServerPlayer player) {
        CallSession session = ACTIVE_PLAYER_CALLS.get(player.getUUID());
        if (session == null || session.state != PhoneCallState.CONNECTED) {
            return;
        }
        session.speakerEnabled = !session.speakerEnabled;
        syncMuteSpeaker(session);
    }

    public static void toggleHomePhoneMute(ServerPlayer viewer, BlockPos blockPos) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(endpoint.address());
        if (session == null || session.state != PhoneCallState.CONNECTED) {
            return;
        }
        session.muted = !session.muted;
        syncMuteSpeaker(session);
    }

    public static void toggleHomePhoneSpeaker(ServerPlayer viewer, BlockPos blockPos) {
        HomePhoneEndpoint endpoint = resolveHomePhoneEndpoint(viewer, blockPos);
        if (endpoint == null) {
            PhoneNetworking.sendHomePhoneCallState(viewer, blockPos, PhoneCallState.IDLE, "", "");
            return;
        }

        ServerLevel level = viewer.serverLevel();
        HomePhoneBlockEntity homePhone = HomePhoneRegistry.get(level, endpoint.address().blockPos());
        if (homePhone == null) {
            return;
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(endpoint.address());
        if (session == null || session.state == PhoneCallState.IDLE || session.state == PhoneCallState.MISSED) {
            homePhone.setSpeakerEnabled(false);
            clearSpeakerEmptyCounter(endpoint.address());
            syncHomePhone(viewer, blockPos);
            return;
        }

        boolean nextSpeakerEnabled = !homePhone.isSpeakerEnabled();
        homePhone.setSpeakerEnabled(nextSpeakerEnabled);
        clearSpeakerEmptyCounter(endpoint.address());
        if (nextSpeakerEnabled) {
            releaseHomePhoneHandset(viewer.getServer(), endpoint.address(), null);
        }
        syncHomePhone(viewer, blockPos);
    }

    public static void handleHomePhoneRemoved(ServerLevel level, BlockPos blockPos) {
        if (level == null || blockPos == null) {
            return;
        }

        MinecraftServer server = level.getServer();
        HomePhoneAddress address = new HomePhoneAddress(level.dimension(), blockPos.immutable());
        HomePhoneBlockEntity homePhone = HomePhoneRegistry.get(level, blockPos);
        if (homePhone != null) {
            homePhone.setRinging(false);
            homePhone.setSpeakerEnabled(false);
            clearSpeakerEmptyCounter(address);
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(address);
        if (session != null) {
            String phoneNumber = homePhone == null ? "" : homePhone.getPhoneNumber();
            endEndpoint(new HomePhoneEndpoint(address, phoneNumber), null);
            return;
        }

        releaseHomePhoneHandset(server, address, null);
    }

    public static List<HomePhoneSpeakerBridge> snapshotHomePhoneSpeakerBridges(MinecraftServer server) {
        List<HomePhoneSpeakerBridge> bridges = new ArrayList<>();
        if (server == null) {
            return bridges;
        }

        Set<CallSession> sessions = new HashSet<>(ACTIVE_HOME_PHONE_CALLS.values());
        for (CallSession session : sessions) {
            if (session.server != server || session.state != PhoneCallState.CONNECTED) {
                continue;
            }

            bridges.addAll(createSpeakerBridges(session));
        }
        return bridges;
    }

    public static List<PlayerCallBridge> snapshotPlayerCallBridges(MinecraftServer server) {
        List<PlayerCallBridge> bridges = new ArrayList<>();
        if (server == null) {
            return bridges;
        }

        Set<CallSession> sessions = new HashSet<>(ACTIVE_PLAYER_CALLS.values());
        sessions.addAll(ACTIVE_HOME_PHONE_CALLS.values());
        for (CallSession session : sessions) {
            if (session.server != server || session.state != PhoneCallState.CONNECTED) {
                continue;
            }

            addPlayerBridge(session, session.caller, session.callee, bridges);
            addPlayerBridge(session, session.callee, session.caller, bridges);
        }

        return bridges;
    }

    public static ServerPlayer findOnlineByNumber(MinecraftServer server, String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number) || !PhoneData.isMobilePhoneNumber(number)) {
            return null;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (number.equals(PhoneData.getPhoneNumber(player))) {
                return player;
            }
        }

        return null;
    }

    public static HomePhoneBlockEntity findHomePhoneByNumber(MinecraftServer server, String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number) || !PhoneData.isHomePhoneNumber(number)) {
            return null;
        }
        return HomePhoneRegistry.findByNumber(server, number);
    }

    public static String resolveContactName(MinecraftServer server, String rawNumber, String fallback) {
        if (server != null) {
            ServerPlayer targetPlayer = findOnlineByNumber(server, rawNumber);
            if (targetPlayer != null) {
                return targetPlayer.getGameProfile().getName();
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static void startCall(MinecraftServer server, ServerPlayer requester, Endpoint callerEndpoint,
                                  String callerNumber, String callerName, String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number)) {
            requester.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.invalid"));
            syncRequesterIdle(requester, callerEndpoint);
            return;
        }

        if (getSession(callerEndpoint) != null) {
            requester.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.already_busy"));
            syncRequester(requester, callerEndpoint);
            return;
        }

        if (callerNumber.equals(number)) {
            requester.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.self"));
            syncRequesterIdle(requester, callerEndpoint);
            return;
        }

        Endpoint calleeEndpoint = resolveTargetEndpoint(server, number);
        if (calleeEndpoint == null || (calleeEndpoint instanceof PlayerEndpoint calleePlayer
                && cn.erindax.bphone.phone.chat.ChatService.isBlockedBy(calleePlayer.playerId(), requester.getUUID()))) {
            requester.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.not_found", number));
            syncRequesterIdle(requester, callerEndpoint);
            return;
        }

        String calleeLabel = labelFor(calleeEndpoint, server, number);
        if (getSession(calleeEndpoint) != null) {
            requester.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.target_busy", calleeLabel));
            syncRequester(requester, callerEndpoint);
            return;
        }

        CallSession session = new CallSession(
                server,
                requester.getUUID(),
                callerEndpoint,
                calleeEndpoint,
                callerNumber,
                number,
                blankToNumber(callerName, callerNumber),
                calleeLabel,
                PhoneCallState.OUTGOING_RINGING,
                server.getTickCount()
        );
        indexSession(session);
        updatePlayerCallPoseFlags(session);
        clearHomePhoneSpeaker(session);
        applyHomePhoneRinging(session);
        syncSession(session);
    }

    private static void answerEndpoint(Endpoint endpoint, ServerPlayer viewer) {
        CallSession session = getSession(endpoint);
        if (session == null || !endpoint.equals(session.callee) || session.state != PhoneCallState.OUTGOING_RINGING) {
            syncEndpointForViewer(endpoint, viewer, null);
            return;
        }

        session.state = PhoneCallState.CONNECTED;
        ensureHomePhoneAudioEndpoint(session);
        updatePlayerCallPoseFlags(session);
        applyHomePhoneRinging(session);
        syncSession(session);
        syncEndpointForViewer(endpoint, viewer, session);
    }

    private static void endEndpoint(Endpoint endpoint, ServerPlayer viewer) {
        CallSession session = getSession(endpoint);
        if (session == null) {
            syncEndpointForViewer(endpoint, viewer, null);
            return;
        }

        recordCallLog(session);

        clearHomePhoneRinging(session);
        clearPlayerCallPoseFlags(session);
        clearHomePhoneSpeaker(session);
        clearHomePhoneHandsets(session);
        clearSpeakerEmptyCounters(session);
        removeSession(session);
        syncPlayersIdle(session);
        syncEndpointForViewer(endpoint, viewer, null);
    }

    private static void recordCallLog(CallSession session) {
        if (session.server == null) {
            return;
        }
        boolean connected = session.state == PhoneCallState.CONNECTED;
        String callerType = connected ? "OUTGOING" : "NO_ANSWER";
        String calleeType = connected ? "INCOMING" : "MISSED";
        int duration = connected ? (int) (session.server.getTickCount() - session.startTime) : 0;
        if (session.caller instanceof PlayerEndpoint callerEp) {
            CallLogStorageManager.addEntry(callerEp.playerId(), session.calleeNumber, session.calleeName, callerType, duration);
        }
        if (session.callee instanceof PlayerEndpoint calleeEp) {
            CallLogStorageManager.addEntry(calleeEp.playerId(), session.callerNumber, session.callerName, calleeType, duration);
        }
    }

    private static void recordMissedCallLog(CallSession session) {
        if (session.server == null) {
            return;
        }
        if (session.callee instanceof PlayerEndpoint calleeEp) {
            CallLogStorageManager.addEntry(calleeEp.playerId(), session.callerNumber, session.callerName, "MISSED", 0);
        }
    }

    private static Endpoint resolveTargetEndpoint(MinecraftServer server, String number) {
        ServerPlayer targetPlayer = findOnlineByNumber(server, number);
        if (targetPlayer != null && PhoneData.hasPhone(targetPlayer)) {
            return new PlayerEndpoint(targetPlayer.getUUID());
        }

        HomePhoneBlockEntity homePhone = findHomePhoneByNumber(server, number);
        if (homePhone != null && homePhone.getLevel() instanceof ServerLevel serverLevel) {
            return new HomePhoneEndpoint(new HomePhoneAddress(serverLevel.dimension(), homePhone.getBlockPos()), number);
        }

        return null;
    }

    private static HomePhoneEndpoint resolveHomePhoneEndpoint(ServerPlayer viewer, net.minecraft.core.BlockPos blockPos) {
        if (viewer == null || blockPos == null) {
            return null;
        }

        ServerLevel level = viewer.serverLevel();
        HomePhoneBlockEntity homePhone = HomePhoneRegistry.get(level, blockPos);
        if (homePhone == null) {
            return null;
        }

        return new HomePhoneEndpoint(new HomePhoneAddress(level.dimension(), blockPos.immutable()), homePhone.getPhoneNumber());
    }

    private static void syncRequester(ServerPlayer requester, Endpoint endpoint) {
        syncEndpointForViewer(endpoint, requester, getSession(endpoint));
    }

    private static void syncRequesterIdle(ServerPlayer requester, Endpoint endpoint) {
        syncEndpointForViewer(endpoint, requester, null);
    }

    private static void syncEndpointForViewer(Endpoint endpoint, ServerPlayer viewer, CallSession session) {
        if (endpoint instanceof PlayerEndpoint) {
            if (viewer != null) {
                if (session == null) {
                    PhoneNetworking.sendCallState(viewer, PhoneCallState.IDLE, "", "");
                } else {
                    syncPlayerEndpoint(viewer, endpoint, session);
                }
            }
            return;
        }

        if (endpoint instanceof HomePhoneEndpoint homePhoneEndpoint && viewer != null) {
            if (session == null) {
                PhoneNetworking.sendHomePhoneCallState(viewer, homePhoneEndpoint.address().blockPos(), PhoneCallState.IDLE, "", "");
            } else {
                PhoneNetworking.sendHomePhoneCallState(
                        viewer,
                        homePhoneEndpoint.address().blockPos(),
                        stateFor(homePhoneEndpoint, session),
                        otherNumberFor(homePhoneEndpoint, session),
                        otherNameFor(homePhoneEndpoint, session),
                        otherProfileIdFor(homePhoneEndpoint, session)
                );
            }
        }
    }

    private static void syncSession(CallSession session) {
        if (session.caller instanceof PlayerEndpoint callerEndpoint) {
            ServerPlayer caller = getPlayer(session.server, callerEndpoint.playerId());
            if (caller != null) {
                syncPlayerEndpoint(caller, callerEndpoint, session);
            }
        }

        if (session.callee instanceof PlayerEndpoint calleeEndpoint) {
            ServerPlayer callee = getPlayer(session.server, calleeEndpoint.playerId());
            if (callee != null) {
                syncPlayerEndpoint(callee, calleeEndpoint, session);
            }
        }
    }

    private static void syncMuteSpeaker(CallSession session) {
        if (session == null) {
            return;
        }
        if (session.caller instanceof PlayerEndpoint callerEndpoint) {
            ServerPlayer caller = getPlayer(session.server, callerEndpoint.playerId());
            if (caller != null) {
                PhoneNetworking.sendMuteSpeakerSync(caller, null, session.muted, session.speakerEnabled);
            }
        }
        if (session.callee instanceof PlayerEndpoint calleeEndpoint) {
            ServerPlayer callee = getPlayer(session.server, calleeEndpoint.playerId());
            if (callee != null) {
                PhoneNetworking.sendMuteSpeakerSync(callee, null, session.muted, session.speakerEnabled);
            }
        }
    }

    private static void syncPlayersIdle(CallSession session) {
        if (session.caller instanceof PlayerEndpoint callerEndpoint) {
            ServerPlayer caller = getPlayer(session.server, callerEndpoint.playerId());
            if (caller != null) {
                PhoneNetworking.sendCallState(caller, PhoneCallState.IDLE, "", "");
            }
        }

        if (session.callee instanceof PlayerEndpoint calleeEndpoint) {
            ServerPlayer callee = getPlayer(session.server, calleeEndpoint.playerId());
            if (callee != null) {
                PhoneNetworking.sendCallState(callee, PhoneCallState.IDLE, "", "");
            }
        }
    }

    private static void syncPlayerEndpoint(ServerPlayer player, Endpoint endpoint, CallSession session) {
        PhoneNetworking.sendCallState(
                player,
                stateFor(endpoint, session),
                otherNumberFor(endpoint, session),
                otherNameFor(endpoint, session),
                otherProfileIdFor(endpoint, session)
        );
    }

    private static void updatePlayerCallPoseFlags(CallSession session) {
        if (session == null || session.server == null) {
            return;
        }

        setPlayerCallPose(session.server, session.caller, session.state == PhoneCallState.OUTGOING_RINGING
                || session.state == PhoneCallState.CONNECTED);
        setPlayerCallPose(session.server, session.callee, session.state == PhoneCallState.CONNECTED);
    }

    private static void clearPlayerCallPoseFlags(CallSession session) {
        if (session == null || session.server == null) {
            return;
        }

        setPlayerCallPose(session.server, session.caller, false);
        setPlayerCallPose(session.server, session.callee, false);
    }

    private static void setPlayerCallPose(MinecraftServer server, Endpoint endpoint, boolean active) {
        if (!(endpoint instanceof PlayerEndpoint playerEndpoint) || server == null) {
            return;
        }

        ServerPlayer player = getPlayer(server, playerEndpoint.playerId());
        if (player != null) {
            PhoneCallPoseAccess.setPhoneCallPoseActive(player, active);
        }
    }

    private static PhoneCallState stateFor(Endpoint endpoint, CallSession session) {
        if (endpoint.equals(session.caller)) {
            return session.state;
        }
        if (session.state == PhoneCallState.CONNECTED) {
            return PhoneCallState.CONNECTED;
        }
        if (session.state == PhoneCallState.MISSED) {
            return PhoneCallState.MISSED;
        }
        return PhoneCallState.INCOMING_RINGING;
    }

    private static String otherNumberFor(Endpoint endpoint, CallSession session) {
        return endpoint.equals(session.caller) ? session.calleeNumber : session.callerNumber;
    }

    private static String otherNameFor(Endpoint endpoint, CallSession session) {
        return endpoint.equals(session.caller) ? session.calleeName : session.callerName;
    }

    private static UUID otherProfileIdFor(Endpoint endpoint, CallSession session) {
        Endpoint otherEndpoint = endpoint.equals(session.caller) ? session.callee : session.caller;
        return otherEndpoint instanceof PlayerEndpoint playerEndpoint ? playerEndpoint.playerId() : null;
    }

    private static String labelFor(Endpoint endpoint, MinecraftServer server, String fallbackNumber) {
        if (endpoint instanceof PlayerEndpoint playerEndpoint) {
            ServerPlayer player = getPlayer(server, playerEndpoint.playerId());
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        return blankToNumber(endpoint.displayName(), fallbackNumber);
    }

    private static String blankToNumber(String name, String fallbackNumber) {
        return name == null || name.isBlank() ? fallbackNumber : name;
    }

    private static CallSession getSession(Endpoint endpoint) {
        if (endpoint instanceof PlayerEndpoint playerEndpoint) {
            return ACTIVE_PLAYER_CALLS.get(playerEndpoint.playerId());
        }
        if (endpoint instanceof HomePhoneEndpoint homePhoneEndpoint) {
            return ACTIVE_HOME_PHONE_CALLS.get(homePhoneEndpoint.address());
        }
        return null;
    }

    private static void indexSession(CallSession session) {
        if (session.caller instanceof PlayerEndpoint callerEndpoint) {
            ACTIVE_PLAYER_CALLS.put(callerEndpoint.playerId(), session);
        } else if (session.caller instanceof HomePhoneEndpoint homePhoneEndpoint) {
            ACTIVE_HOME_PHONE_CALLS.put(homePhoneEndpoint.address(), session);
        }

        if (session.callee instanceof PlayerEndpoint calleeEndpoint) {
            ACTIVE_PLAYER_CALLS.put(calleeEndpoint.playerId(), session);
        } else if (session.callee instanceof HomePhoneEndpoint homePhoneEndpoint) {
            ACTIVE_HOME_PHONE_CALLS.put(homePhoneEndpoint.address(), session);
        }
    }

    private static void removeSession(CallSession session) {
        if (session.caller instanceof PlayerEndpoint callerEndpoint) {
            ACTIVE_PLAYER_CALLS.remove(callerEndpoint.playerId());
        } else if (session.caller instanceof HomePhoneEndpoint homePhoneEndpoint) {
            ACTIVE_HOME_PHONE_CALLS.remove(homePhoneEndpoint.address());
        }

        if (session.callee instanceof PlayerEndpoint calleeEndpoint) {
            ACTIVE_PLAYER_CALLS.remove(calleeEndpoint.playerId());
        } else if (session.callee instanceof HomePhoneEndpoint homePhoneEndpoint) {
            ACTIVE_HOME_PHONE_CALLS.remove(homePhoneEndpoint.address());
        }
    }

    private static ServerPlayer getPlayer(MinecraftServer server, UUID playerId) {
        return server == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    private static void applyHomePhoneRinging(CallSession session) {
        if (session == null) {
            return;
        }

        setHomePhoneRinging(session.caller, session.server, false);
        setHomePhoneRinging(session.callee, session.server, session.state == PhoneCallState.OUTGOING_RINGING);
    }

    private static void clearHomePhoneRinging(CallSession session) {
        if (session == null) {
            return;
        }

        setHomePhoneRinging(session.caller, session.server, false);
        setHomePhoneRinging(session.callee, session.server, false);
    }

    private static void clearHomePhoneSpeaker(CallSession session) {
        if (session == null) {
            return;
        }

        setHomePhoneSpeaker(session.caller, session.server, false);
        setHomePhoneSpeaker(session.callee, session.server, false);
        clearSpeakerEmptyCounters(session);
    }

    private static void ensureHomePhoneAudioEndpoint(CallSession session) {
        if (session == null) {
            return;
        }

        ensureHomePhoneAudioEndpoint(session.caller, session.server);
        ensureHomePhoneAudioEndpoint(session.callee, session.server);
    }

    private static void ensureHomePhoneAudioEndpoint(Endpoint endpoint, MinecraftServer server) {
        if (!(endpoint instanceof HomePhoneEndpoint homePhoneEndpoint) || server == null) {
            return;
        }

        HomePhoneBlockEntity homePhone = getHomePhone(server, homePhoneEndpoint.address());
        if (homePhone == null || homePhone.isSpeakerEnabled()) {
            return;
        }

        if (resolveActiveHandsetHolder(server, homePhoneEndpoint.address(), homePhone) == null) {
            homePhone.setSpeakerEnabled(true);
            clearSpeakerEmptyCounter(homePhoneEndpoint.address());
        }
    }

    private static void clearHomePhoneHandsets(CallSession session) {
        if (session == null) {
            return;
        }

        releaseHomePhoneHandset(session.server, session.caller, null);
        releaseHomePhoneHandset(session.server, session.callee, null);
    }

    private static void clearAllHomePhoneRinging() {
        Set<CallSession> sessions = new HashSet<>(ACTIVE_PLAYER_CALLS.values());
        sessions.addAll(ACTIVE_HOME_PHONE_CALLS.values());
        for (CallSession session : sessions) {
            clearHomePhoneRinging(session);
            clearHomePhoneSpeaker(session);
            clearHomePhoneHandsets(session);
        }
        SPEAKER_EMPTY_TICKS.clear();
    }

    private static void setHomePhoneRinging(Endpoint endpoint, MinecraftServer server, boolean ringing) {
        if (!(endpoint instanceof HomePhoneEndpoint homePhoneEndpoint) || server == null) {
            return;
        }

        ServerLevel level = server.getLevel(homePhoneEndpoint.address().dimension());
        HomePhoneBlockEntity homePhone = HomePhoneRegistry.get(level, homePhoneEndpoint.address().blockPos());
        if (homePhone != null) {
            homePhone.setRinging(ringing);
        }
    }

    private static void setHomePhoneSpeaker(Endpoint endpoint, MinecraftServer server, boolean speakerEnabled) {
        if (!(endpoint instanceof HomePhoneEndpoint homePhoneEndpoint) || server == null) {
            return;
        }

        ServerLevel level = server.getLevel(homePhoneEndpoint.address().dimension());
        HomePhoneBlockEntity homePhone = HomePhoneRegistry.get(level, homePhoneEndpoint.address().blockPos());
        if (homePhone != null) {
            homePhone.setSpeakerEnabled(speakerEnabled);
        }

        if (!speakerEnabled) {
            clearSpeakerEmptyCounter(homePhoneEndpoint.address());
        }
    }

    private static void clearSpeakerEmptyCounters(CallSession session) {
        if (session == null) {
            return;
        }

        clearSpeakerEmptyCounter(session.caller);
        clearSpeakerEmptyCounter(session.callee);
    }

    private static void clearSpeakerEmptyCounter(Endpoint endpoint) {
        if (endpoint instanceof HomePhoneEndpoint homePhoneEndpoint) {
            clearSpeakerEmptyCounter(homePhoneEndpoint.address());
        }
    }

    private static void clearSpeakerEmptyCounter(HomePhoneAddress address) {
        if (address != null) {
            SPEAKER_EMPTY_TICKS.remove(address);
        }
    }

    private static void releaseHomePhoneHandset(MinecraftServer server, Endpoint endpoint, ServerPlayer explicitHolder) {
        if (endpoint instanceof HomePhoneEndpoint homePhoneEndpoint) {
            releaseHomePhoneHandset(server, homePhoneEndpoint.address(), explicitHolder);
        }
    }

    private static void releaseHomePhoneHandset(MinecraftServer server, HomePhoneAddress address, ServerPlayer explicitHolder) {
        if (server == null || address == null) {
            return;
        }

        if (explicitHolder != null) {
            HomePhoneHandsetItem.removeBoundHandsets(explicitHolder, address);
        }

        HomePhoneBlockEntity homePhone = getHomePhone(server, address);
        if (homePhone == null) {
            return;
        }

        UUID handsetHolderId = homePhone.getHandsetHolderId();
        if (handsetHolderId != null && (explicitHolder == null || !handsetHolderId.equals(explicitHolder.getUUID()))) {
            ServerPlayer holder = getPlayer(server, handsetHolderId);
            if (holder != null) {
                HomePhoneHandsetItem.removeBoundHandsets(holder, address);
            }
        }

        homePhone.setHandsetHolderId(null);
    }

    private static List<HomePhoneSpeakerBridge> createSpeakerBridges(CallSession session) {
        if (session == null || session.server == null) {
            return List.of();
        }

        List<HomePhoneSpeakerBridge> bridges = new ArrayList<>(2);
        addSpeakerBridge(session, session.caller, session.callee, bridges);
        addSpeakerBridge(session, session.callee, session.caller, bridges);
        return bridges;
    }

    private static void addSpeakerBridge(CallSession session, Endpoint localEndpoint, Endpoint remoteEndpoint,
                                         List<HomePhoneSpeakerBridge> bridges) {
        if (!(localEndpoint instanceof HomePhoneEndpoint homePhoneEndpoint)) {
            return;
        }

        HomePhoneBlockEntity homePhone = getHomePhone(session.server, homePhoneEndpoint.address());
        if (homePhone == null) {
            return;
        }

        boolean speakerEnabled = homePhone.isSpeakerEnabled();
        UUID handsetHolderId = resolveActiveHandsetHolder(session.server, homePhoneEndpoint.address(), homePhone);
        if (!speakerEnabled && handsetHolderId == null) {
            return;
        }

        UUID remotePlayerId = remoteEndpoint instanceof PlayerEndpoint playerEndpoint ? playerEndpoint.playerId() : null;
        List<HomePhoneAddress> remoteBridgeAddresses = List.of();
        if (remoteEndpoint instanceof HomePhoneEndpoint remoteHomePhoneEndpoint) {
            HomePhoneBlockEntity remoteHomePhone = getHomePhone(session.server, remoteHomePhoneEndpoint.address());
            UUID remoteHandsetHolderId = resolveActiveHandsetHolder(
                    session.server,
                    remoteHomePhoneEndpoint.address(),
                    remoteHomePhone
            );
            if (remoteHomePhone != null && (remoteHomePhone.isSpeakerEnabled() || remoteHandsetHolderId != null)) {
                remoteBridgeAddresses = List.of(remoteHomePhoneEndpoint.address());
            }
        }

        bridges.add(new HomePhoneSpeakerBridge(
                homePhoneEndpoint.address(),
                speakerEnabled,
                handsetHolderId,
                remotePlayerId,
                remoteBridgeAddresses
        ));
    }

    private static void addPlayerBridge(CallSession session, Endpoint localEndpoint, Endpoint remoteEndpoint,
                                        List<PlayerCallBridge> bridges) {
        if (!(localEndpoint instanceof PlayerEndpoint localPlayerEndpoint)
                || !(remoteEndpoint instanceof PlayerEndpoint remotePlayerEndpoint)) {
            return;
        }

        bridges.add(new PlayerCallBridge(
                localPlayerEndpoint.playerId(),
                remotePlayerEndpoint.playerId(),
                blankToNumber(otherNameFor(localEndpoint, session), otherNumberFor(localEndpoint, session))
        ));
    }

    private static HomePhoneBlockEntity getHomePhone(MinecraftServer server, HomePhoneAddress address) {
        if (server == null || address == null) {
            return null;
        }

        ServerLevel level = server.getLevel(address.dimension());
        return HomePhoneRegistry.get(level, address.blockPos());
    }

    private static UUID resolveActiveHandsetHolder(MinecraftServer server, HomePhoneAddress address,
                                                   HomePhoneBlockEntity homePhone) {
        if (server == null || address == null || homePhone == null || homePhone.getHandsetHolderId() == null) {
            return null;
        }

        ServerPlayer holder = getPlayer(server, homePhone.getHandsetHolderId());
        if (holder == null || !HomePhoneHandsetItem.isHoldingBoundHandset(holder, address)) {
            homePhone.setHandsetHolderId(null);
            return null;
        }

        return holder.getUUID();
    }

    private static void notifyNoAnswer(CallSession session) {
        if (session == null || session.server == null || session.initiatorPlayerId == null) {
            return;
        }

        ServerPlayer initiator = getPlayer(session.server, session.initiatorPlayerId);
        if (initiator == null) {
            return;
        }

        initiator.sendSystemMessage(Component.translatable(
                "screen.bphone.phone.call.status.no_answer",
                blankToNumber(session.calleeName, session.calleeNumber)));
    }

    private static void handlePlayerQuit(ServerPlayer player) {
        if (ACTIVE_PLAYER_CALLS.containsKey(player.getUUID())) {
            endCall(player);
        }
    }

    private static void handleServerTick(MinecraftServer server) {
        enforceHeldHomePhoneHandsets(server);
        enforceSpeakerPresence(server);
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        long currentTick = server.getTickCount();
        List<CallSession> sessionsToMiss = new ArrayList<>();
        List<CallSession> sessionsToRemove = new ArrayList<>();
        Set<CallSession> sessions = new HashSet<>(ACTIVE_PLAYER_CALLS.values());
        sessions.addAll(ACTIVE_HOME_PHONE_CALLS.values());

        for (CallSession session : sessions) {
            if (session.state == PhoneCallState.OUTGOING_RINGING && currentTick - session.startTime >= UNANSWERED_CALL_TIMEOUT_TICKS) {
                sessionsToMiss.add(session);
            } else if (session.state == PhoneCallState.MISSED && currentTick - session.startTime >= MISSED_STATE_TICKS) {
                sessionsToRemove.add(session);
            }
        }

        for (CallSession session : sessionsToMiss) {
            session.state = PhoneCallState.MISSED;
            session.startTime = currentTick;
            recordMissedCallLog(session);
            clearHomePhoneRinging(session);
            clearPlayerCallPoseFlags(session);
            clearHomePhoneSpeaker(session);
            clearHomePhoneHandsets(session);
            clearSpeakerEmptyCounters(session);
            syncSession(session);
            notifyNoAnswer(session);
        }

        for (CallSession session : sessionsToRemove) {
            clearHomePhoneRinging(session);
            clearPlayerCallPoseFlags(session);
            clearHomePhoneSpeaker(session);
            clearHomePhoneHandsets(session);
            clearSpeakerEmptyCounters(session);
            removeSession(session);
            syncPlayersIdle(session);
        }
    }

    private static void enforceSpeakerPresence(MinecraftServer server) {
        if (server == null) {
            return;
        }

        Set<CallSession> sessions = new HashSet<>(ACTIVE_HOME_PHONE_CALLS.values());
        for (CallSession session : sessions) {
            if (session.server != server || session.state != PhoneCallState.CONNECTED) {
                clearSpeakerEmptyCounters(session);
                continue;
            }

            if (enforceSpeakerPresenceForEndpoint(server, session, session.caller)) {
                continue;
            }

            enforceSpeakerPresenceForEndpoint(server, session, session.callee);
        }
    }

    private static boolean enforceSpeakerPresenceForEndpoint(MinecraftServer server, CallSession session, Endpoint endpoint) {
        if (!(endpoint instanceof HomePhoneEndpoint homePhoneEndpoint)) {
            return false;
        }

        HomePhoneBlockEntity homePhone = getHomePhone(server, homePhoneEndpoint.address());
        if (homePhone == null || !homePhone.isSpeakerEnabled() || homePhone.getHandsetHolderId() != null) {
            clearSpeakerEmptyCounter(homePhoneEndpoint.address());
            return false;
        }

        if (hasNearbyPlayer(server, homePhoneEndpoint.address())) {
            clearSpeakerEmptyCounter(homePhoneEndpoint.address());
            return false;
        }

        int emptyTicks = SPEAKER_EMPTY_TICKS.getOrDefault(homePhoneEndpoint.address(), 0) + 1;
        if (emptyTicks < SPEAKER_EMPTY_HANGUP_TICKS) {
            SPEAKER_EMPTY_TICKS.put(homePhoneEndpoint.address(), emptyTicks);
            return false;
        }

        clearSpeakerEmptyCounter(homePhoneEndpoint.address());
        endEndpoint(new HomePhoneEndpoint(homePhoneEndpoint.address(), homePhone.getPhoneNumber()), null);
        return true;
    }

    private static boolean hasNearbyPlayer(MinecraftServer server, HomePhoneAddress address) {
        if (server == null || address == null) {
            return false;
        }

        ServerLevel level = server.getLevel(address.dimension());
        if (level == null) {
            return false;
        }

        BlockPos blockPos = address.blockPos();
        double centerX = blockPos.getX() + 0.5D;
        double centerY = blockPos.getY() + 0.5D;
        double centerZ = blockPos.getZ() + 0.5D;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.serverLevel() != level) {
                continue;
            }

            if (player.distanceToSqr(centerX, centerY, centerZ) <= SPEAKER_AUTO_HANGUP_DISTANCE_SQR) {
                return true;
            }
        }

        return false;
    }

    private static void enforceHeldHomePhoneHandsets(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (Map.Entry<HomePhoneAddress, HomePhoneBlockEntity> entry : HomePhoneRegistry.snapshotLoadedPhones(server).entrySet()) {
            HomePhoneAddress address = entry.getKey();
            HomePhoneBlockEntity homePhone = entry.getValue();
            UUID handsetHolderId = homePhone.getHandsetHolderId();
            if (handsetHolderId == null) {
                continue;
            }

            ServerPlayer holder = getPlayer(server, handsetHolderId);
            if (holder == null) {
                homePhone.setHandsetHolderId(null);
                continue;
            }

            if (shouldAutoReturnHandset(holder, address)) {
                notifyHandsetAutoReturned(holder, address, homePhone);
                putDownHomePhoneHandset(server, address, holder, homePhone);
                continue;
            }

            InteractionHand heldHand = HomePhoneHandsetItem.getHeldBoundHand(holder, address);
            HomePhoneHandsetItem.ensurePlayerHasHandset(
                    holder,
                    heldHand == null ? InteractionHand.MAIN_HAND : heldHand,
                    address,
                    homePhone.getPhoneNumber());
        }
    }

    private static boolean shouldAutoReturnHandset(ServerPlayer holder, HomePhoneAddress address) {
        if (holder == null || address == null) {
            return false;
        }

        if (!holder.serverLevel().dimension().equals(address.dimension())) {
            return true;
        }

        BlockPos blockPos = address.blockPos();
        double centerX = blockPos.getX() + 0.5D;
        double centerY = blockPos.getY() + 0.5D;
        double centerZ = blockPos.getZ() + 0.5D;
        return holder.distanceToSqr(centerX, centerY, centerZ) > HANDSET_RETURN_DISTANCE_SQR;
    }

    private static void putDownHomePhoneHandset(MinecraftServer server, HomePhoneAddress address,
                                                ServerPlayer holder, HomePhoneBlockEntity homePhone) {
        if (server == null || address == null) {
            return;
        }

        HomePhoneBlockEntity resolvedHomePhone = homePhone != null ? homePhone : getHomePhone(server, address);
        if (resolvedHomePhone != null) {
            resolvedHomePhone.setSpeakerEnabled(false);
        }

        CallSession session = ACTIVE_HOME_PHONE_CALLS.get(address);
        if (session != null) {
            String phoneNumber = resolvedHomePhone == null ? "" : resolvedHomePhone.getPhoneNumber();
            endEndpoint(new HomePhoneEndpoint(address, phoneNumber), holder);
            return;
        }

        releaseHomePhoneHandset(server, address, holder);
    }

    private static void notifyHandsetAutoReturned(ServerPlayer holder, HomePhoneAddress address,
                                                  HomePhoneBlockEntity homePhone) {
        if (holder == null || address == null) {
            return;
        }

        String phoneNumber = homePhone == null
                ? PhoneData.getHomePhoneNumber(address.dimension(), address.blockPos())
                : homePhone.getPhoneNumber();
        holder.sendSystemMessage(Component.translatable(
                "screen.bphone.home_phone.status.handset_returned_distance",
                phoneNumber));
    }

    private interface Endpoint {
        String number();

        String displayName();
    }

    private record PlayerEndpoint(UUID playerId) implements Endpoint {
        @Override
        public String number() {
            return "";
        }

        @Override
        public String displayName() {
            return "";
        }
    }

    private record HomePhoneEndpoint(HomePhoneAddress address, String number) implements Endpoint {
        @Override
        public String displayName() {
            return number;
        }
    }

    private static final class CallSession {
        private final MinecraftServer server;
        private final UUID initiatorPlayerId;
        private final Endpoint caller;
        private final Endpoint callee;
        private final String callerNumber;
        private final String calleeNumber;
        private final String callerName;
        private final String calleeName;
        private PhoneCallState state;
        private long startTime;
        boolean muted;
        boolean speakerEnabled;

        private CallSession(MinecraftServer server, UUID initiatorPlayerId, Endpoint caller, Endpoint callee,
                            String callerNumber, String calleeNumber, String callerName,
                            String calleeName, PhoneCallState state, long startTime) {
            this.server = server;
            this.initiatorPlayerId = initiatorPlayerId;
            this.caller = caller;
            this.callee = callee;
            this.callerNumber = callerNumber;
            this.calleeNumber = calleeNumber;
            this.callerName = callerName;
            this.calleeName = calleeName;
            this.state = state;
            this.startTime = startTime;
        }
    }

    public record HomePhoneSpeakerBridge(HomePhoneAddress address, boolean speakerEnabled, UUID handsetHolderId,
                                         UUID remotePlayerId, List<HomePhoneAddress> remoteBridgeAddresses) {
    }

    public record PlayerCallBridge(UUID listenerPlayerId, UUID remotePlayerId, String remoteDisplayName) {
    }
}
