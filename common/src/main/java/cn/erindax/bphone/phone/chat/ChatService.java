package cn.erindax.bphone.phone.chat;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhoneCallManager;
import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ChatService {
    public static final int MAX_FRIENDS = 100;
    public static final int MAX_PENDING_REQUESTS = 10;
    public static final long REJECT_COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(24);
    public static final int MAX_GROUP_MEMBERS = 30;
    public static final int MAX_GROUPS_PER_PLAYER = 20;
    public static final int MAX_GROUP_NAME_LENGTH = 16;
    public static final int SNAPSHOT_ENTRIES = 30;
    public static final int HISTORY_PAGE = 30;
    public static final String SYSTEM_SEPARATOR = "\u0001";

    private static ChatStore store;
    private static ChatImageStore images;

    private ChatService() {
    }

    public static synchronized void open(Path databaseFile, Path imageDirectory) {
        close();
        try {
            store = new ChatStore(databaseFile.toString());
            images = new ChatImageStore(imageDirectory);
        } catch (Throwable throwable) {
            store = null;
            images = null;
            BPhone.LOGGER.error("Failed to open chat storage", throwable);
        }
    }

    public static synchronized void close() {
        if (store != null) {
            store.close();
        }
        store = null;
        images = null;
    }

    public static boolean available() {
        return store != null;
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace('\r', ' ').replace('\n', ' ').replace(SYSTEM_SEPARATOR, " ").trim();
        return text.length() <= ChatNetworking.MAX_MESSAGE_LENGTH ? text : text.substring(0, ChatNetworking.MAX_MESSAGE_LENGTH);
    }

    public static String displayName(ServerPlayer player) {
        String nickname = store == null ? "" : store.nickname(player.getUUID());
        return nickname == null || nickname.isBlank() ? player.getGameProfile().getName() : nickname;
    }

    public static boolean isBlockedBy(UUID owner, UUID other) {
        return store != null && store.isBlocked(owner, other);
    }

    public static void syncOnJoin(ServerPlayer player) {
        if (store == null || player.getServer() == null) {
            return;
        }

        UUID uuid = player.getUUID();
        MinecraftServer server = player.getServer();
        String name = displayName(player);
        store.renameMemberEverywhere(uuid, name);
        CompletableFuture.supplyAsync(() -> state(uuid)).whenCompleteAsync((state, error) -> {
            if (error != null) {
                BPhone.LOGGER.error("Failed to build chat state for {}", uuid, error);
                return;
            }
            ServerPlayer current = server.getPlayerList().getPlayer(uuid);
            if (current != null && state != null) {
                ChatNetworking.sendState(current, state);
                syncRequests(current);
            }
        }, server);
    }

    public static void forget(ServerPlayer player) {
        if (images != null && player != null) {
            images.forget(player.getUUID());
        }
    }

    public static void syncState(ServerPlayer player) {
        if (store == null || player == null) {
            return;
        }
        ChatPayloads.StateData state = state(player.getUUID());
        if (state != null) {
            ChatNetworking.sendState(player, state);
        }
    }

    public static void syncRequests(ServerPlayer player) {
        if (store == null || player == null) {
            return;
        }
        ChatNetworking.sendRequests(player, store.incomingRequests(PhoneData.getPhoneNumber(player)));
    }

    static ChatPayloads.StateData state(UUID viewer) {
        if (store == null) {
            return null;
        }
        List<ChatPayloads.FriendData> friends = new ArrayList<>();
        for (ChatStore.Friend friend : store.friends(viewer)) {
            friends.add(new ChatPayloads.FriendData(friend.name(), friend.number(), friend.uuid()));
        }
        List<ChatPayloads.FriendData> blocked = new ArrayList<>();
        for (ChatStore.Blocked entry : store.blocked(viewer)) {
            blocked.add(new ChatPayloads.FriendData(entry.name(), entry.number(), entry.uuid()));
        }
        List<ChatPayloads.ConversationData> conversations = new ArrayList<>();
        for (long id : store.conversationsOf(viewer, ChatPayloads.MAX_CONVERSATIONS)) {
            ChatPayloads.ConversationData snapshot = snapshot(id, viewer);
            if (snapshot != null) {
                conversations.add(snapshot);
            }
        }
        return new ChatPayloads.StateData(store.nickname(viewer), friends, blocked, conversations);
    }

    static ChatPayloads.ConversationData snapshot(long id, UUID viewer) {
        ChatStore.Conversation conversation = store.conversation(id);
        if (conversation == null) {
            return null;
        }
        List<ChatStore.Member> members = store.members(id);
        ChatStore.Member me = null;
        for (ChatStore.Member member : members) {
            if (member.uuid().equals(viewer)) {
                me = member;
            }
        }
        if (me == null) {
            return null;
        }

        String title = conversation.title();
        String peerNumber = "";
        if (conversation.kind() == ChatStore.KIND_DIRECT) {
            for (ChatStore.Member member : members) {
                if (!member.uuid().equals(viewer)) {
                    peerNumber = member.number();
                    ChatStore.Friend friend = store.friend(viewer, member.number());
                    title = friend != null ? friend.name() : member.name();
                }
            }
        }

        List<ChatStore.Entry> entries = store.latestEntries(id, me.clearedId(), SNAPSHOT_ENTRIES);
        boolean hasMore = !entries.isEmpty() && store.hasEntriesBefore(id, me.clearedId(), entries.get(0).id());
        int unread = store.unreadCount(id, viewer, Math.max(me.lastReadId(), me.clearedId()));
        List<ChatPayloads.MemberData> memberData = new ArrayList<>(members.size());
        for (ChatStore.Member member : members) {
            memberData.add(new ChatPayloads.MemberData(member.uuid(), member.number(), member.name(), member.lastReadId()));
        }
        List<ChatPayloads.EntryData> entryData = new ArrayList<>(entries.size());
        for (ChatStore.Entry entry : entries) {
            entryData.add(ChatPayloads.EntryData.of(entry));
        }
        return new ChatPayloads.ConversationData(id, conversation.kind(), title, peerNumber, conversation.owner(),
                conversation.updatedAt(), me.lastReadId(), unread, hasMore, memberData, entryData);
    }

    static void broadcast(MinecraftServer server, long conversationId) {
        if (server == null || store == null) {
            return;
        }
        for (ChatStore.Member member : store.members(conversationId)) {
            ServerPlayer online = server.getPlayerList().getPlayer(member.uuid());
            if (online != null) {
                ChatPayloads.ConversationData snapshot = snapshot(conversationId, member.uuid());
                if (snapshot != null) {
                    ChatNetworking.sendConversation(online, snapshot);
                }
            }
        }
    }

    private static void toastOthers(ServerPlayer sender, long conversationId, ChatStore.Entry entry) {
        MinecraftServer server = sender.getServer();
        if (server == null || entry == null) {
            return;
        }
        ChatPayloads.EntryData data = ChatPayloads.EntryData.of(entry);
        for (ChatStore.Member member : store.members(conversationId)) {
            if (member.uuid().equals(sender.getUUID())) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(member.uuid());
            if (online != null && PhoneData.hasPhone(online)) {
                ChatPayloads.ConversationData snapshot = snapshot(conversationId, member.uuid());
                ChatNetworking.sendToast(online, conversationId, snapshot == null ? "" : snapshot.title(), data);
            }
        }
    }

    private static void status(ServerPlayer player, String key, Object... args) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    private static boolean ready(ServerPlayer player) {
        if (player == null || store == null || player.getServer() == null) {
            return false;
        }
        if (!PhoneData.hasPhone(player)) {
            status(player, "screen.bphone.phone.call.error.no_phone");
            return false;
        }
        return true;
    }

    private record Peer(UUID uuid, String number, String name) {
    }

    private static Peer resolvePeer(ServerPlayer viewer, String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number) || !PhoneData.isMobilePhoneNumber(number)) {
            return null;
        }
        ServerPlayer online = PhoneCallManager.findOnlineByNumber(viewer.getServer(), number);
        if (online != null) {
            return new Peer(online.getUUID(), number, displayName(online));
        }
        ChatStore.Friend friend = store.friend(viewer.getUUID(), number);
        if (friend != null && friend.uuid() != null) {
            return new Peer(friend.uuid(), number, friend.name());
        }
        for (long id : store.conversationsOf(viewer.getUUID(), ChatPayloads.MAX_CONVERSATIONS)) {
            ChatStore.Conversation conversation = store.conversation(id);
            if (conversation == null || conversation.kind() != ChatStore.KIND_DIRECT) {
                continue;
            }
            for (ChatStore.Member member : store.members(id)) {
                if (member.number().equals(number) && !member.uuid().equals(viewer.getUUID())) {
                    return new Peer(member.uuid(), number, member.name());
                }
            }
        }
        return null;
    }

    private static long resolveConversation(ServerPlayer sender, long conversationId, String peerNumber) {
        UUID uuid = sender.getUUID();
        if (conversationId > 0L) {
            ChatStore.Conversation conversation = store.conversation(conversationId);
            if (conversation == null || store.member(conversationId, uuid) == null) {
                status(sender, "screen.bphone.phone.chat.error.not_member");
                return 0L;
            }
            if (conversation.kind() == ChatStore.KIND_DIRECT) {
                for (ChatStore.Member member : store.members(conversationId)) {
                    if (!member.uuid().equals(uuid) && store.isBlocked(member.uuid(), uuid)) {
                        status(sender, "screen.bphone.phone.chat.error.blocked_by");
                        return 0L;
                    }
                }
            }
            return conversationId;
        }

        String number = PhoneData.normalizePhoneNumber(peerNumber);
        String ownNumber = PhoneData.getPhoneNumber(sender);
        if (number.equals(ownNumber)) {
            status(sender, "screen.bphone.phone.chat.error.self");
            return 0L;
        }
        Peer peer = resolvePeer(sender, number);
        if (peer == null) {
            status(sender, "screen.bphone.phone.chat.error.unavailable", number);
            return 0L;
        }
        if (store.isBlocked(peer.uuid(), uuid)) {
            status(sender, "screen.bphone.phone.chat.error.blocked_by");
            return 0L;
        }
        return store.directConversation(uuid, ownNumber, displayName(sender), peer.uuid(), peer.number(), peer.name());
    }

    public static void sendText(ServerPlayer sender, long conversationId, String peerNumber, String raw) {
        if (!ready(sender)) {
            return;
        }
        String text = sanitize(raw);
        if (text.isEmpty()) {
            status(sender, "screen.bphone.phone.chat.error.empty");
            return;
        }
        long id = resolveConversation(sender, conversationId, peerNumber);
        if (id <= 0L) {
            return;
        }
        ChatStore.Entry entry = store.addEntry(id, sender.getUUID(), PhoneData.getPhoneNumber(sender), displayName(sender),
                ChatStore.ENTRY_TEXT, text, null);
        if (entry == null) {
            status(sender, "screen.bphone.phone.chat.error.send_failed");
            return;
        }
        broadcast(sender.getServer(), id);
        if (conversationId <= 0L) {
            ChatNetworking.sendFocus(sender, id);
        }
        toastOthers(sender, id, entry);
    }

    public static void acceptImageChunk(ServerPlayer sender, long uploadId, long conversationId, String peerNumber,
                                        int index, int count, byte[] data) {
        if (!ready(sender) || images == null) {
            return;
        }
        ChatImageStore.CompletedUpload upload = images.acceptChunk(sender.getUUID(), uploadId, conversationId, peerNumber,
                index, count, data);
        if (upload == null) {
            return;
        }
        long id = resolveConversation(sender, upload.conversationId(), upload.peerNumber());
        if (id <= 0L) {
            return;
        }
        String imageId = images.save(upload.data());
        if (imageId == null) {
            status(sender, "screen.bphone.phone.chat.error.image_invalid");
            return;
        }
        ChatStore.Entry entry = store.addEntry(id, sender.getUUID(), PhoneData.getPhoneNumber(sender), displayName(sender),
                ChatStore.ENTRY_IMAGE, "", imageId);
        if (entry == null) {
            images.delete(imageId);
            status(sender, "screen.bphone.phone.chat.error.send_failed");
            return;
        }
        broadcast(sender.getServer(), id);
        if (upload.conversationId() <= 0L) {
            ChatNetworking.sendFocus(sender, id);
        }
        toastOthers(sender, id, entry);
    }

    public static void requestImage(ServerPlayer player, String imageId) {
        if (player == null || store == null || images == null || !ChatImageStore.isValidId(imageId)) {
            return;
        }
        byte[] data = store.canSeeImage(player.getUUID(), imageId) ? images.load(imageId) : null;
        ChatNetworking.sendImage(player, imageId, data);
    }

    public static void markRead(ServerPlayer player, long conversationId, long entryId) {
        if (player == null || store == null || player.getServer() == null) {
            return;
        }
        if (store.member(conversationId, player.getUUID()) == null) {
            return;
        }
        long clamped = Math.min(entryId, store.maxEntryId(conversationId));
        if (clamped <= 0L || !store.markRead(conversationId, player.getUUID(), clamped)) {
            return;
        }
        for (ChatStore.Member member : store.members(conversationId)) {
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(member.uuid());
            if (online != null) {
                ChatNetworking.sendReadUpdate(online, conversationId, player.getUUID(), clamped);
            }
        }
    }

    public static void history(ServerPlayer player, long conversationId, long beforeId, long aroundId) {
        if (player == null || store == null) {
            return;
        }
        ChatStore.Member me = store.member(conversationId, player.getUUID());
        if (me == null) {
            return;
        }
        List<ChatPayloads.EntryData> result = new ArrayList<>();
        boolean hasMore;
        if (aroundId > 0L) {
            List<ChatStore.Entry> before = store.entriesBefore(conversationId, me.clearedId(), aroundId, 20);
            long start = before.isEmpty() ? Math.max(aroundId, me.clearedId() + 1L) : before.get(0).id();
            for (ChatStore.Entry entry : store.entriesFrom(conversationId, start, ChatPayloads.MAX_ENTRIES)) {
                result.add(ChatPayloads.EntryData.of(entry));
            }
            hasMore = store.hasEntriesBefore(conversationId, me.clearedId(), start);
        } else {
            List<ChatStore.Entry> page = store.entriesBefore(conversationId, me.clearedId(),
                    beforeId <= 0L ? Long.MAX_VALUE : beforeId, HISTORY_PAGE);
            for (ChatStore.Entry entry : page) {
                result.add(ChatPayloads.EntryData.of(entry));
            }
            hasMore = !page.isEmpty() && store.hasEntriesBefore(conversationId, me.clearedId(), page.get(0).id());
        }
        ChatNetworking.sendHistory(player, conversationId, aroundId, hasMore, result);
    }

    public static void search(ServerPlayer player, String raw) {
        if (player == null || store == null) {
            return;
        }
        String query = raw == null ? "" : raw.trim();
        if (query.isEmpty()) {
            ChatNetworking.sendSearch(player, "", List.of());
            return;
        }
        Map<Long, String> titles = new HashMap<>();
        List<ChatPayloads.SearchHitData> hits = new ArrayList<>();
        for (ChatStore.SearchHit hit : store.search(player.getUUID(), query, 50)) {
            String title = titles.computeIfAbsent(hit.conversationId(), id -> {
                ChatPayloads.ConversationData snapshot = snapshot(id, player.getUUID());
                return snapshot == null ? "" : snapshot.title();
            });
            hits.add(new ChatPayloads.SearchHitData(hit.conversationId(), title, ChatPayloads.EntryData.of(hit.entry())));
        }
        ChatNetworking.sendSearch(player, query, hits);
    }

    private static String systemBody(String key, String... args) {
        StringBuilder builder = new StringBuilder(key);
        for (String arg : args) {
            builder.append(SYSTEM_SEPARATOR).append(arg == null ? "" : arg);
        }
        return ChatPayloads.clip(builder.toString(), ChatPayloads.MAX_BODY_LENGTH);
    }

    private static void systemEntry(long conversationId, String key, String... args) {
        store.addEntry(conversationId, null, "00000", "", ChatStore.ENTRY_SYSTEM, systemBody(key, args), null);
    }

    private static String sanitizeGroupName(String raw) {
        String name = sanitize(raw);
        return name.length() <= MAX_GROUP_NAME_LENGTH ? name : name.substring(0, MAX_GROUP_NAME_LENGTH);
    }

    public static void createGroup(ServerPlayer player, String rawName, List<String> numbers) {
        if (!ready(player)) {
            return;
        }
        String name = sanitizeGroupName(rawName);
        if (name.isEmpty()) {
            status(player, "screen.bphone.phone.chat.group.error.name");
            return;
        }
        UUID uuid = player.getUUID();
        if (store.groupCount(uuid) >= MAX_GROUPS_PER_PLAYER) {
            status(player, "screen.bphone.phone.chat.group.error.too_many", MAX_GROUPS_PER_PLAYER);
            return;
        }
        String ownNumber = PhoneData.getPhoneNumber(player);
        Map<UUID, ChatStore.Member> members = new LinkedHashMap<>();
        members.put(uuid, new ChatStore.Member(uuid, ownNumber, displayName(player), 0L, 0L, 0L));
        for (String raw : numbers) {
            String number = PhoneData.normalizePhoneNumber(raw);
            ChatStore.Friend friend = store.friend(uuid, number);
            if (friend == null || friend.uuid() == null || store.isBlocked(friend.uuid(), uuid)) {
                continue;
            }
            members.putIfAbsent(friend.uuid(), new ChatStore.Member(friend.uuid(), number, friend.name(), 0L, 0L, 0L));
            if (members.size() >= MAX_GROUP_MEMBERS) {
                break;
            }
        }
        if (members.size() < 2) {
            status(player, "screen.bphone.phone.chat.group.error.members");
            return;
        }
        long id = store.createGroup(name, uuid, new ArrayList<>(members.values()));
        if (id <= 0L) {
            status(player, "screen.bphone.phone.chat.error.send_failed");
            return;
        }
        systemEntry(id, "phone.chat.system.group_created", displayName(player), name);
        broadcast(player.getServer(), id);
        ChatNetworking.sendFocus(player, id);
    }

    private static ChatStore.Conversation group(ServerPlayer player, long conversationId) {
        ChatStore.Conversation conversation = store.conversation(conversationId);
        if (conversation == null || conversation.kind() != ChatStore.KIND_GROUP
                || store.member(conversationId, player.getUUID()) == null) {
            status(player, "screen.bphone.phone.chat.error.not_member");
            return null;
        }
        return conversation;
    }

    public static void invite(ServerPlayer player, long conversationId, List<String> numbers) {
        if (!ready(player)) {
            return;
        }
        ChatStore.Conversation conversation = group(player, conversationId);
        if (conversation == null) {
            return;
        }
        UUID uuid = player.getUUID();
        int size = store.members(conversationId).size();
        List<String> added = new ArrayList<>();
        for (String raw : numbers) {
            if (size >= MAX_GROUP_MEMBERS) {
                status(player, "screen.bphone.phone.chat.group.error.full", MAX_GROUP_MEMBERS);
                break;
            }
            String number = PhoneData.normalizePhoneNumber(raw);
            ChatStore.Friend friend = store.friend(uuid, number);
            if (friend == null || friend.uuid() == null || store.member(conversationId, friend.uuid()) != null
                    || store.isBlocked(friend.uuid(), uuid)) {
                continue;
            }
            store.addMember(conversationId, friend.uuid(), number, friend.name());
            added.add(friend.name());
            size++;
        }
        if (added.isEmpty()) {
            return;
        }
        systemEntry(conversationId, "phone.chat.system.group_invited", displayName(player), String.join(", ", added));
        broadcast(player.getServer(), conversationId);
    }

    public static void rename(ServerPlayer player, long conversationId, String rawName) {
        if (!ready(player)) {
            return;
        }
        ChatStore.Conversation conversation = group(player, conversationId);
        if (conversation == null) {
            return;
        }
        if (!player.getUUID().equals(conversation.owner())) {
            status(player, "screen.bphone.phone.chat.group.error.owner_only");
            return;
        }
        String name = sanitizeGroupName(rawName);
        if (name.isEmpty() || name.equals(conversation.title())) {
            return;
        }
        store.renameGroup(conversationId, name);
        systemEntry(conversationId, "phone.chat.system.group_renamed", displayName(player), name);
        broadcast(player.getServer(), conversationId);
    }

    public static void leave(ServerPlayer player, long conversationId) {
        if (player == null || store == null) {
            return;
        }
        ChatStore.Conversation conversation = group(player, conversationId);
        if (conversation == null) {
            return;
        }
        UUID uuid = player.getUUID();
        store.removeMember(conversationId, uuid);
        ChatNetworking.sendRemoved(player, conversationId);
        List<ChatStore.Member> remaining = store.members(conversationId);
        if (remaining.isEmpty()) {
            for (String imageId : store.deleteConversation(conversationId)) {
                if (images != null && !store.isImageReferenced(imageId)) {
                    images.delete(imageId);
                }
            }
            return;
        }
        if (uuid.equals(conversation.owner())) {
            store.setOwner(conversationId, remaining.get(0).uuid());
        }
        systemEntry(conversationId, "phone.chat.system.group_left", displayName(player));
        broadcast(player.getServer(), conversationId);
    }

    public static void kick(ServerPlayer player, long conversationId, String rawNumber) {
        if (!ready(player)) {
            return;
        }
        ChatStore.Conversation conversation = group(player, conversationId);
        if (conversation == null) {
            return;
        }
        if (!player.getUUID().equals(conversation.owner())) {
            status(player, "screen.bphone.phone.chat.group.error.owner_only");
            return;
        }
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        for (ChatStore.Member member : store.members(conversationId)) {
            if (member.number().equals(number) && !member.uuid().equals(player.getUUID())) {
                store.removeMember(conversationId, member.uuid());
                ServerPlayer target = player.getServer().getPlayerList().getPlayer(member.uuid());
                if (target != null) {
                    ChatNetworking.sendRemoved(target, conversationId);
                }
                systemEntry(conversationId, "phone.chat.system.group_kicked", displayName(player), member.name());
                broadcast(player.getServer(), conversationId);
                return;
            }
        }
    }

    public static void block(ServerPlayer player, String rawNumber, boolean block) {
        if (player == null || store == null) {
            return;
        }
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number) || number.equals(PhoneData.getPhoneNumber(player))) {
            return;
        }
        if (block) {
            Peer peer = resolvePeer(player, number);
            if (peer == null) {
                status(player, "screen.bphone.phone.chat.error.unavailable", number);
                return;
            }
            store.block(player.getUUID(), PhoneData.getPhoneNumber(player), peer.uuid(), number, peer.name());
            store.cancelPendingBetween(PhoneData.getPhoneNumber(player), number);
            status(player, "screen.bphone.phone.chat.blocked", peer.name());
        } else {
            store.unblock(player.getUUID(), number);
            status(player, "screen.bphone.phone.chat.unblocked", number);
        }
        syncState(player);
        syncRequests(player);
    }

    public static void clear(ServerPlayer player, long conversationId) {
        if (player == null || store == null) {
            return;
        }
        ChatStore.Conversation conversation = store.conversation(conversationId);
        if (conversation == null || store.member(conversationId, player.getUUID()) == null) {
            return;
        }
        store.clearHistory(conversationId, player.getUUID());
        if (conversation.kind() == ChatStore.KIND_DIRECT) {
            ChatNetworking.sendRemoved(player, conversationId);
        } else {
            ChatPayloads.ConversationData snapshot = snapshot(conversationId, player.getUUID());
            if (snapshot != null) {
                ChatNetworking.sendConversation(player, snapshot);
            }
        }
    }

    public static void setNickname(ServerPlayer player, String raw) {
        if (player == null || store == null) {
            return;
        }
        String nickname = sanitize(raw);
        if (nickname.length() > PhoneData.MAX_CONTACT_NAME_LENGTH) {
            nickname = nickname.substring(0, PhoneData.MAX_CONTACT_NAME_LENGTH);
        }
        store.setNickname(player.getUUID(), nickname);
        String shown = displayName(player);
        store.renameMemberEverywhere(player.getUUID(), shown);
        store.renameFriendEverywhere(PhoneData.getPhoneNumber(player), shown);
        syncState(player);
    }

    public static void sendFriendRequest(ServerPlayer sender, String rawNumber) {
        if (!ready(sender)) {
            return;
        }
        String target = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(target) || !PhoneData.isMobilePhoneNumber(target)) {
            sender.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.invalid"));
            return;
        }
        String ownNumber = PhoneData.getPhoneNumber(sender);
        if (ownNumber.equals(target)) {
            sender.sendSystemMessage(Component.translatable("screen.bphone.phone.chat.error.self"));
            return;
        }
        UUID uuid = sender.getUUID();
        if (store.friend(uuid, target) != null) {
            sender.sendSystemMessage(Component.translatable("phone.friend.error.already"));
            return;
        }
        if (store.isBlockedByNumber(target, uuid)) {
            sender.sendSystemMessage(Component.translatable("phone.friend.error.unreachable"));
            return;
        }
        long rejectedAt = store.lastRejectedAt(ownNumber, target);
        long remaining = rejectedAt + REJECT_COOLDOWN_MILLIS - System.currentTimeMillis();
        if (rejectedAt > 0L && remaining > 0L) {
            long hours = Math.max(1L, (remaining + TimeUnit.HOURS.toMillis(1) - 1) / TimeUnit.HOURS.toMillis(1));
            sender.sendSystemMessage(Component.translatable("phone.friend.error.cooldown", hours));
            return;
        }
        if (store.friendCount(uuid) >= MAX_FRIENDS) {
            sender.sendSystemMessage(Component.translatable("phone.friend.error.self_full", MAX_FRIENDS));
            return;
        }
        if (store.friendCountOfNumber(target) >= MAX_FRIENDS) {
            sender.sendSystemMessage(Component.translatable("phone.friend.error.target_friends_full"));
            return;
        }
        if (store.countIncomingPending(target) >= MAX_PENDING_REQUESTS) {
            sender.sendSystemMessage(Component.translatable("phone.friend.error.target_full"));
            return;
        }
        String ownName = displayName(sender);
        if (!store.createRequest(uuid, ownNumber, ownName, target)) {
            sender.sendSystemMessage(Component.translatable("phone.friend.error.duplicate"));
            return;
        }
        sender.sendSystemMessage(Component.translatable("phone.friend.sent", target));
        ServerPlayer online = PhoneCallManager.findOnlineByNumber(sender.getServer(), target);
        if (online != null) {
            syncRequests(online);
            online.sendSystemMessage(Component.translatable("phone.friend.incoming", ownName));
        }
    }

    public static void respondFriendRequest(ServerPlayer responder, long requestId, boolean accept) {
        if (responder == null || store == null) {
            return;
        }
        String ownNumber = PhoneData.getPhoneNumber(responder);
        ChatStore.Request request = null;
        for (ChatStore.Request candidate : store.incomingRequests(ownNumber)) {
            if (candidate.id() == requestId) {
                request = candidate;
            }
        }
        if (request == null || request.fromUuid() == null) {
            responder.sendSystemMessage(Component.translatable("phone.friend.error.expired"));
            syncRequests(responder);
            return;
        }
        UUID uuid = responder.getUUID();
        if (accept) {
            if (store.friendCount(uuid) >= MAX_FRIENDS) {
                responder.sendSystemMessage(Component.translatable("phone.friend.error.self_full", MAX_FRIENDS));
                return;
            }
            if (store.friendCount(request.fromUuid()) >= MAX_FRIENDS) {
                responder.sendSystemMessage(Component.translatable("phone.friend.error.target_friends_full"));
                return;
            }
        }
        if (!store.respondRequest(requestId, accept)) {
            responder.sendSystemMessage(Component.translatable("phone.friend.error.expired"));
            syncRequests(responder);
            return;
        }

        String ownName = displayName(responder);
        ServerPlayer requester = responder.getServer() == null ? null
                : responder.getServer().getPlayerList().getPlayer(request.fromUuid());
        String requesterName = requester != null ? displayName(requester) : request.fromName();
        if (accept) {
            store.addFriend(uuid, request.fromNumber(), requesterName, request.fromUuid());
            store.addFriend(request.fromUuid(), ownNumber, ownName, uuid);
            store.cancelPendingBetween(ownNumber, request.fromNumber());
            responder.sendSystemMessage(Component.translatable("phone.friend.accepted", requesterName));
        } else {
            responder.sendSystemMessage(Component.translatable("phone.friend.rejected", requesterName));
        }
        syncState(responder);
        syncRequests(responder);
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(accept ? "phone.friend.accepted_by" : "phone.friend.rejected_by", ownName));
            syncState(requester);
            syncRequests(requester);
        }
    }

    public static void removeFriend(ServerPlayer player, String rawNumber) {
        if (player == null || store == null) {
            return;
        }
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        ChatStore.Friend friend = store.friend(player.getUUID(), number);
        if (friend == null) {
            return;
        }
        store.removeFriend(player.getUUID(), number);
        if (friend.uuid() != null) {
            store.removeFriend(friend.uuid(), PhoneData.getPhoneNumber(player));
            ServerPlayer target = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(friend.uuid());
            if (target != null) {
                target.sendSystemMessage(Component.translatable("phone.friend.removed_by", displayName(player)));
                syncState(target);
            }
        }
        player.sendSystemMessage(Component.translatable("phone.friend.removed", number));
        syncState(player);
    }
}
