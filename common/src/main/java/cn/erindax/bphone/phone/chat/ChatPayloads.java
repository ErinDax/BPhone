package cn.erindax.bphone.phone.chat;

import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatPayloads {
    public static final int MAX_BODY_LENGTH = 256;
    public static final int MAX_TITLE_LENGTH = 24;
    public static final int MAX_IMAGE_ID_LENGTH = 40;
    public static final int MAX_CONVERSATIONS = 64;
    public static final int MAX_ENTRIES = 300;
    public static final int MAX_MEMBERS = 64;
    public static final int MAX_FRIENDS = 128;

    private ChatPayloads() {
    }

    public record EntryData(long id, UUID senderUuid, String senderNumber, String senderName, int kind, String body,
                            String imageId, long createdAt) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            writeUuid(buf, senderUuid);
            buf.writeUtf(clip(senderNumber, PhoneData.PHONE_NUMBER_LENGTH), PhoneData.PHONE_NUMBER_LENGTH);
            buf.writeUtf(clip(senderName, PhoneData.MAX_CONTACT_NAME_LENGTH), PhoneData.MAX_CONTACT_NAME_LENGTH);
            buf.writeByte(kind);
            buf.writeUtf(clip(body, MAX_BODY_LENGTH), MAX_BODY_LENGTH);
            buf.writeUtf(clip(imageId, MAX_IMAGE_ID_LENGTH), MAX_IMAGE_ID_LENGTH);
            buf.writeLong(createdAt);
        }

        public static EntryData read(FriendlyByteBuf buf) {
            return new EntryData(buf.readVarLong(), readUuid(buf), buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH),
                    buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH), buf.readByte(), buf.readUtf(MAX_BODY_LENGTH),
                    buf.readUtf(MAX_IMAGE_ID_LENGTH), buf.readLong());
        }

        public static EntryData of(ChatStore.Entry entry) {
            return new EntryData(entry.id(), entry.senderUuid(), entry.senderNumber(), entry.senderName(), entry.kind(),
                    entry.body(), entry.imageId() == null ? "" : entry.imageId(), entry.createdAt());
        }
    }

    public record MemberData(UUID uuid, String number, String name, long lastReadId) {
        public void write(FriendlyByteBuf buf) {
            writeUuid(buf, uuid);
            buf.writeUtf(clip(number, PhoneData.PHONE_NUMBER_LENGTH), PhoneData.PHONE_NUMBER_LENGTH);
            buf.writeUtf(clip(name, PhoneData.MAX_CONTACT_NAME_LENGTH), PhoneData.MAX_CONTACT_NAME_LENGTH);
            buf.writeVarLong(lastReadId);
        }

        public static MemberData read(FriendlyByteBuf buf) {
            return new MemberData(readUuid(buf), buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH),
                    buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH), buf.readVarLong());
        }
    }

    public record ConversationData(long id, int kind, String title, String peerNumber, UUID ownerUuid, long updatedAt,
                                   long myLastReadId, int unread, boolean hasMore, List<MemberData> members,
                                   List<EntryData> entries) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeByte(kind);
            buf.writeUtf(clip(title, MAX_TITLE_LENGTH), MAX_TITLE_LENGTH);
            buf.writeUtf(clip(peerNumber, PhoneData.PHONE_NUMBER_LENGTH), PhoneData.PHONE_NUMBER_LENGTH);
            writeUuid(buf, ownerUuid);
            buf.writeLong(updatedAt);
            buf.writeVarLong(myLastReadId);
            buf.writeVarInt(unread);
            buf.writeBoolean(hasMore);
            List<MemberData> safeMembers = members.size() > MAX_MEMBERS ? members.subList(0, MAX_MEMBERS) : members;
            buf.writeVarInt(safeMembers.size());
            for (MemberData member : safeMembers) {
                member.write(buf);
            }
            writeEntries(buf, entries);
        }

        public static ConversationData read(FriendlyByteBuf buf) {
            long id = buf.readVarLong();
            int kind = buf.readByte();
            String title = buf.readUtf(MAX_TITLE_LENGTH);
            String peerNumber = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            UUID owner = readUuid(buf);
            long updatedAt = buf.readLong();
            long myLastReadId = buf.readVarLong();
            int unread = buf.readVarInt();
            boolean hasMore = buf.readBoolean();
            int memberCount = Math.min(buf.readVarInt(), MAX_MEMBERS);
            List<MemberData> members = new ArrayList<>(memberCount);
            for (int index = 0; index < memberCount; index++) {
                members.add(MemberData.read(buf));
            }
            return new ConversationData(id, kind, title, peerNumber, owner, updatedAt, myLastReadId, unread, hasMore,
                    members, readEntries(buf));
        }
    }

    public record FriendData(String name, String number, UUID uuid) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(clip(name, PhoneData.MAX_CONTACT_NAME_LENGTH), PhoneData.MAX_CONTACT_NAME_LENGTH);
            buf.writeUtf(clip(number, PhoneData.PHONE_NUMBER_LENGTH), PhoneData.PHONE_NUMBER_LENGTH);
            writeUuid(buf, uuid);
        }

        public static FriendData read(FriendlyByteBuf buf) {
            return new FriendData(buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH), buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH),
                    readUuid(buf));
        }
    }

    public record StateData(String nickname, List<FriendData> friends, List<FriendData> blocked,
                            List<ConversationData> conversations) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(clip(nickname, PhoneData.MAX_CONTACT_NAME_LENGTH), PhoneData.MAX_CONTACT_NAME_LENGTH);
            writeFriends(buf, friends);
            writeFriends(buf, blocked);
            List<ConversationData> safe = conversations.size() > MAX_CONVERSATIONS
                    ? conversations.subList(0, MAX_CONVERSATIONS)
                    : conversations;
            buf.writeVarInt(safe.size());
            for (ConversationData conversation : safe) {
                conversation.write(buf);
            }
        }

        public static StateData read(FriendlyByteBuf buf) {
            String nickname = buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH);
            List<FriendData> friends = readFriends(buf);
            List<FriendData> blocked = readFriends(buf);
            int count = Math.min(buf.readVarInt(), MAX_CONVERSATIONS);
            List<ConversationData> conversations = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                conversations.add(ConversationData.read(buf));
            }
            return new StateData(nickname, friends, blocked, conversations);
        }
    }

    public record SearchHitData(long conversationId, String title, EntryData entry) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarLong(conversationId);
            buf.writeUtf(clip(title, MAX_TITLE_LENGTH), MAX_TITLE_LENGTH);
            entry.write(buf);
        }

        public static SearchHitData read(FriendlyByteBuf buf) {
            return new SearchHitData(buf.readVarLong(), buf.readUtf(MAX_TITLE_LENGTH), EntryData.read(buf));
        }
    }

    public static void writeEntries(FriendlyByteBuf buf, List<EntryData> entries) {
        List<EntryData> safe = entries.size() > MAX_ENTRIES ? entries.subList(entries.size() - MAX_ENTRIES, entries.size()) : entries;
        buf.writeVarInt(safe.size());
        for (EntryData entry : safe) {
            entry.write(buf);
        }
    }

    public static List<EntryData> readEntries(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<EntryData> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(EntryData.read(buf));
        }
        return entries;
    }

    private static void writeFriends(FriendlyByteBuf buf, List<FriendData> friends) {
        List<FriendData> safe = friends.size() > MAX_FRIENDS ? friends.subList(0, MAX_FRIENDS) : friends;
        buf.writeVarInt(safe.size());
        for (FriendData friend : safe) {
            friend.write(buf);
        }
    }

    private static List<FriendData> readFriends(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_FRIENDS);
        List<FriendData> friends = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            friends.add(FriendData.read(buf));
        }
        return friends;
    }

    public static void writeUuid(FriendlyByteBuf buf, UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUUID(uuid);
        }
    }

    public static UUID readUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
