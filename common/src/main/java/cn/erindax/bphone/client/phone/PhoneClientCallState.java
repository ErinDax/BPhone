package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.CallLogEntry;
import cn.erindax.bphone.phone.PhoneCallState;
import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PhoneClientCallState {
    private static CallSnapshot mobileState = CallSnapshot.idle();
    private static final Map<BlockPos, CallSnapshot> homePhoneStates = new HashMap<>();
    private static boolean mobileMuted;
    private static boolean mobileSpeakerEnabled;
    private static final Map<BlockPos, Boolean> homePhoneMuted = new HashMap<>();
    private static final Map<BlockPos, Boolean> homePhoneSpeakerEnabled = new HashMap<>();

    private PhoneClientCallState() {
    }

    public static void apply(PhoneCallState nextState, String nextNumber, String nextName) {
        apply(nextState, nextNumber, nextName, null);
    }

    public static void apply(PhoneCallState nextState, String nextNumber, String nextName, UUID nextProfileId) {
        mobileState = mobileState.next(nextState, nextNumber, nextName, nextProfileId);
    }

    public static void applyHomePhone(BlockPos blockPos, PhoneCallState nextState, String nextNumber, String nextName) {
        applyHomePhone(blockPos, nextState, nextNumber, nextName, null);
    }

    public static void applyHomePhone(BlockPos blockPos, PhoneCallState nextState, String nextNumber, String nextName,
                                      UUID nextProfileId) {
        if (blockPos == null) {
            apply(nextState, nextNumber, nextName, nextProfileId);
            return;
        }

        BlockPos key = blockPos.immutable();
        CallSnapshot previous = homePhoneStates.getOrDefault(key, CallSnapshot.idle());
        homePhoneStates.put(key, previous.next(nextState, nextNumber, nextName, nextProfileId));
    }

    public static void clear() {
        mobileState = mobileState.next(PhoneCallState.IDLE, "", "", null);
        homePhoneStates.clear();
    }

    public static void clearHomePhone(BlockPos blockPos) {
        if (blockPos != null) {
            homePhoneStates.remove(blockPos);
        }
    }

    public static void setMuted(BlockPos blockPos, boolean muted) {
        if (blockPos == null) {
            mobileMuted = muted;
        } else {
            homePhoneMuted.put(blockPos.immutable(), muted);
        }
    }

    public static boolean isMuted(BlockPos blockPos) {
        if (blockPos == null) {
            return mobileMuted;
        }
        return homePhoneMuted.getOrDefault(blockPos, false);
    }

    public static void setSpeakerEnabled(BlockPos blockPos, boolean enabled) {
        if (blockPos == null) {
            mobileSpeakerEnabled = enabled;
        } else {
            homePhoneSpeakerEnabled.put(blockPos.immutable(), enabled);
        }
    }

    public static boolean isSpeakerEnabled(BlockPos blockPos) {
        if (blockPos == null) {
            return mobileSpeakerEnabled;
        }
        return homePhoneSpeakerEnabled.getOrDefault(blockPos, false);
    }

    public static PhoneCallState getState() {
        return mobileState.state();
    }

    public static PhoneCallState getState(BlockPos blockPos) {
        return snapshot(blockPos).state();
    }

    public static String getOtherNumber() {
        return mobileState.otherNumber();
    }

    public static String getOtherNumber(BlockPos blockPos) {
        return snapshot(blockPos).otherNumber();
    }

    public static String getOtherName() {
        return mobileState.otherName();
    }

    public static String getOtherName(BlockPos blockPos) {
        return snapshot(blockPos).otherName();
    }

    public static UUID getOtherProfileId() {
        return mobileState.otherProfileId();
    }

    public static UUID getOtherProfileId(BlockPos blockPos) {
        return snapshot(blockPos).otherProfileId();
    }

    public static long getRevision() {
        return mobileState.revision();
    }

    public static long getRevision(BlockPos blockPos) {
        return snapshot(blockPos).revision();
    }

    public static int getConnectedDurationTicks() {
        return connectedDurationTicks(mobileState);
    }

    public static int getConnectedDurationTicks(BlockPos blockPos) {
        return connectedDurationTicks(snapshot(blockPos));
    }

    public static boolean hasHomePhoneState(PhoneCallState state) {
        return !getHomePhonesInState(state).isEmpty();
    }

    public static Set<BlockPos> getHomePhonesInState(PhoneCallState state) {
        if (state == null || homePhoneStates.isEmpty()) {
            return Set.of();
        }

        Set<BlockPos> matching = new HashSet<>();
        for (Map.Entry<BlockPos, CallSnapshot> entry : homePhoneStates.entrySet()) {
            if (entry.getValue().state() == state) {
                matching.add(entry.getKey().immutable());
            }
        }

        return matching.isEmpty() ? Set.of() : Set.copyOf(matching);
    }

    private static CallSnapshot snapshot(BlockPos blockPos) {
        if (blockPos == null) {
            return mobileState;
        }
        return homePhoneStates.getOrDefault(blockPos, CallSnapshot.idle());
    }

    private static List<CallLogEntry> callLogEntries = List.of();
    private static long callLogRevision;

    public static void setCallLogEntries(List<CallLogEntry> entries) {
        callLogEntries = entries == null ? List.of() : List.copyOf(entries);
        callLogRevision++;
    }

    public static List<CallLogEntry> getCallLogEntries() {
        return callLogEntries;
    }

    public static long getCallLogRevision() {
        return callLogRevision;
    }

    private static int connectedDurationTicks(CallSnapshot snapshot) {
        if (snapshot.state() != PhoneCallState.CONNECTED || snapshot.connectedSinceMillis() < 0L) {
            return 0;
        }

        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - snapshot.connectedSinceMillis());
        return (int) Math.min(Integer.MAX_VALUE, (elapsedMillis * 20L) / 1000L);
    }

    private record CallSnapshot(PhoneCallState state, String otherNumber, String otherName, UUID otherProfileId,
                                long revision, long connectedSinceMillis) {
        private static CallSnapshot idle() {
            return new CallSnapshot(PhoneCallState.IDLE, "", "", null, 0L, -1L);
        }

        private CallSnapshot next(PhoneCallState nextState, String nextNumber, String nextName, UUID nextProfileId) {
            PhoneCallState normalizedState = nextState == null ? PhoneCallState.IDLE : nextState;
            String normalizedNumber = PhoneData.normalizePhoneNumber(nextNumber);
            String normalizedName = nextName == null ? "" : nextName;
            UUID normalizedProfileId = normalizedState == PhoneCallState.IDLE ? null : nextProfileId;

            long nextConnectedSinceMillis = connectedSinceMillis;
            if (normalizedState != PhoneCallState.CONNECTED) {
                nextConnectedSinceMillis = -1L;
            } else {
                boolean enteringConnected = state != PhoneCallState.CONNECTED;
                boolean peerChanged = !otherNumber.equals(normalizedNumber)
                        || !otherName.equals(normalizedName)
                        || !java.util.Objects.equals(otherProfileId, normalizedProfileId);
                if (enteringConnected || peerChanged || connectedSinceMillis < 0L) {
                    nextConnectedSinceMillis = System.currentTimeMillis();
                }
            }

            return new CallSnapshot(
                    normalizedState,
                    normalizedNumber,
                    normalizedName,
                    normalizedProfileId,
                    revision + 1L,
                    nextConnectedSinceMillis);
        }
    }
}
