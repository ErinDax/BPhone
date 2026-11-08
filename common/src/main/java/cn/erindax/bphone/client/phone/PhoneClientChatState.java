package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.PhoneContact;
import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PhoneClientChatState {
    public record FriendRequest(long id, String fromNumber, String fromName, long createdAt) {
    }

    public record Friend(String name, String number, UUID uuid) {
        public PhoneContact contact() {
            return new PhoneContact(name, number);
        }
    }

    public static final class Conversation {
        public final long id;
        public final int kind;
        public final String title;
        public final String peerNumber;
        public final UUID ownerUuid;
        public final long updatedAt;
        public final List<ChatPayloads.MemberData> members;
        public final List<ChatPayloads.EntryData> entries;
        public long myLastReadId;
        public int unread;
        public boolean hasMore;
        public boolean windowed;

        Conversation(ChatPayloads.ConversationData data) {
            this.id = data.id();
            this.kind = data.kind();
            this.title = data.title();
            this.peerNumber = data.peerNumber();
            this.ownerUuid = data.ownerUuid();
            this.updatedAt = data.updatedAt();
            this.members = new ArrayList<>(data.members());
            this.entries = new ArrayList<>(data.entries());
            this.myLastReadId = data.myLastReadId();
            this.unread = data.unread();
            this.hasMore = data.hasMore();
        }

        public boolean isGroup() {
            return kind == ChatStore.KIND_GROUP;
        }

        public ChatPayloads.EntryData lastEntry() {
            return entries.isEmpty() ? null : entries.get(entries.size() - 1);
        }

        public long lastEntryId() {
            ChatPayloads.EntryData last = lastEntry();
            return last == null ? 0L : last.id();
        }

        public int readCount(ChatPayloads.EntryData entry) {
            int count = 0;
            for (ChatPayloads.MemberData member : members) {
                if (entry.senderUuid() != null && entry.senderUuid().equals(member.uuid())) {
                    continue;
                }
                if (member.lastReadId() >= entry.id()) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final Map<String, Friend> FRIENDS = new LinkedHashMap<>();
    private static final Map<String, Friend> BLOCKED = new LinkedHashMap<>();
    private static final Map<Long, Conversation> CONVERSATIONS = new LinkedHashMap<>();
    private static final List<FriendRequest> REQUESTS = new ArrayList<>();
    private static String ownNickname = "";
    private static long revision;

    private PhoneClientChatState() {
    }

    public static synchronized void applyState(ChatPayloads.StateData state) {
        FRIENDS.clear();
        BLOCKED.clear();
        Map<Long, Conversation> previous = new LinkedHashMap<>(CONVERSATIONS);
        CONVERSATIONS.clear();
        ownNickname = state.nickname() == null ? "" : state.nickname();
        for (ChatPayloads.FriendData friend : state.friends()) {
            FRIENDS.put(friend.number(), new Friend(friend.name(), friend.number(), friend.uuid()));
        }
        for (ChatPayloads.FriendData blocked : state.blocked()) {
            BLOCKED.put(blocked.number(), new Friend(blocked.name(), blocked.number(), blocked.uuid()));
        }
        for (ChatPayloads.ConversationData data : state.conversations()) {
            CONVERSATIONS.put(data.id(), merge(previous.get(data.id()), data));
        }
        revision++;
    }

    public static synchronized void applyConversation(ChatPayloads.ConversationData data) {
        CONVERSATIONS.put(data.id(), merge(CONVERSATIONS.get(data.id()), data));
        revision++;
    }

    private static Conversation merge(Conversation previous, ChatPayloads.ConversationData data) {
        Conversation next = new Conversation(data);
        if (previous == null || previous.entries.isEmpty() || next.entries.isEmpty()) {
            return next;
        }
        long firstNew = next.entries.get(0).id();
        List<ChatPayloads.EntryData> older = new ArrayList<>();
        for (ChatPayloads.EntryData entry : previous.entries) {
            if (entry.id() < firstNew) {
                older.add(entry);
            }
        }
        if (!older.isEmpty() && !previous.windowed && older.get(older.size() - 1).id() < firstNew) {
            next.entries.addAll(0, older);
            next.hasMore = previous.hasMore;
        }
        return next;
    }

    public static synchronized void removeConversation(long id) {
        CONVERSATIONS.remove(id);
        revision++;
    }

    public static synchronized void applyHistory(long id, long aroundId, boolean hasMore, List<ChatPayloads.EntryData> entries) {
        Conversation conversation = CONVERSATIONS.get(id);
        if (conversation == null) {
            return;
        }
        if (aroundId > 0L) {
            long latest = conversation.lastEntryId();
            conversation.entries.clear();
            conversation.entries.addAll(entries);
            conversation.windowed = !entries.isEmpty() && entries.get(entries.size() - 1).id() < latest;
            conversation.hasMore = hasMore;
        } else {
            long oldest = conversation.entries.isEmpty() ? Long.MAX_VALUE : conversation.entries.get(0).id();
            List<ChatPayloads.EntryData> older = new ArrayList<>();
            for (ChatPayloads.EntryData entry : entries) {
                if (entry.id() < oldest) {
                    older.add(entry);
                }
            }
            conversation.entries.addAll(0, older);
            conversation.hasMore = hasMore;
        }
        revision++;
    }

    public static synchronized void applyReadUpdate(long id, UUID member, long lastReadId) {
        Conversation conversation = CONVERSATIONS.get(id);
        if (conversation == null) {
            return;
        }
        for (int index = 0; index < conversation.members.size(); index++) {
            ChatPayloads.MemberData data = conversation.members.get(index);
            if (data.uuid() != null && data.uuid().equals(member) && data.lastReadId() < lastReadId) {
                conversation.members.set(index, new ChatPayloads.MemberData(data.uuid(), data.number(), data.name(), lastReadId));
            }
        }
        revision++;
    }

    public static synchronized void markLocallyRead(long id, long lastReadId) {
        Conversation conversation = CONVERSATIONS.get(id);
        if (conversation != null && conversation.myLastReadId < lastReadId) {
            conversation.myLastReadId = lastReadId;
            conversation.unread = 0;
            revision++;
        }
    }

    public static synchronized void applyRequests(List<FriendRequest> requests) {
        REQUESTS.clear();
        REQUESTS.addAll(requests);
        revision++;
    }

    public static synchronized List<FriendRequest> getRequests() {
        return List.copyOf(REQUESTS);
    }

    public static synchronized int getRequestCount() {
        return REQUESTS.size();
    }

    public static synchronized List<Conversation> getConversations() {
        List<Conversation> list = new ArrayList<>(CONVERSATIONS.values());
        list.sort(Comparator.comparingLong((Conversation conversation) -> conversation.updatedAt).reversed());
        return list;
    }

    public static synchronized Conversation getConversation(long id) {
        return CONVERSATIONS.get(id);
    }

    public static synchronized Conversation findDirect(String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        for (Conversation conversation : CONVERSATIONS.values()) {
            if (!conversation.isGroup() && conversation.peerNumber.equals(number)) {
                return conversation;
            }
        }
        return null;
    }

    public static synchronized int getTotalUnread() {
        int total = 0;
        for (Conversation conversation : CONVERSATIONS.values()) {
            total += conversation.unread;
        }
        return total;
    }

    public static synchronized List<Friend> getFriendList() {
        return List.copyOf(FRIENDS.values());
    }

    public static synchronized List<PhoneContact> getFriends() {
        List<PhoneContact> contacts = new ArrayList<>(FRIENDS.size());
        for (Friend friend : FRIENDS.values()) {
            contacts.add(friend.contact());
        }
        return contacts;
    }

    public static synchronized List<Friend> getBlocked() {
        return List.copyOf(BLOCKED.values());
    }

    public static synchronized boolean isBlocked(String rawNumber) {
        return BLOCKED.containsKey(PhoneData.normalizePhoneNumber(rawNumber));
    }

    public static synchronized boolean hasFriend(String rawNumber) {
        return FRIENDS.containsKey(PhoneData.normalizePhoneNumber(rawNumber));
    }

    public static synchronized String getFriendName(String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        Friend friend = FRIENDS.get(number);
        if (friend != null) {
            return friend.name();
        }
        Conversation conversation = findDirect(number);
        return conversation == null ? "" : conversation.title;
    }

    public static synchronized UUID getFriendProfileId(String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        Friend friend = FRIENDS.get(number);
        if (friend != null && friend.uuid() != null) {
            return friend.uuid();
        }
        Conversation conversation = findDirect(number);
        if (conversation != null) {
            for (ChatPayloads.MemberData member : conversation.members) {
                if (member.number().equals(number)) {
                    return member.uuid();
                }
            }
        }
        return null;
    }

    public static synchronized String getOwnNickname() {
        return ownNickname;
    }

    public static synchronized long getRevision() {
        return revision;
    }

    public static synchronized void clear() {
        FRIENDS.clear();
        BLOCKED.clear();
        CONVERSATIONS.clear();
        REQUESTS.clear();
        ownNickname = "";
        revision++;
    }
}
