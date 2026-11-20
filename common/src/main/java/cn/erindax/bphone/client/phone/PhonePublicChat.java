package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhonePublicChat {
    public static final long CONVERSATION_ID = -1L;
    private static final int MAX_ENTRIES = 300;
    private static final int MAX_BODY_LENGTH = 1024;
    private static final String CHAT_KEY = "chat.type.text";
    private static final Pattern BRACKETED = Pattern.compile("^<([^<>\\s]{1,40})> (.+)$", Pattern.DOTALL);
    private static final List<ChatPayloads.EntryData> ENTRIES = new ArrayList<>();
    private static Pending pending;
    private static long nextId = 1L;
    private static long lastReadId;
    private static long revision;
    private static long snapshotRevision = -1L;
    private static PhoneClientChatState.Conversation snapshot;

    private record Pending(UUID uuid, String name, String body, boolean plain) {
    }

    private PhonePublicChat() {
    }

    public static synchronized void beginPlayerMessage(ChatType.Bound bound, PlayerChatMessage message, GameProfile profile) {
        UUID uuid = profile != null && profile.getId() != null ? profile.getId() : message.sender();
        String name = clean(bound.name().getString());
        if (name.isEmpty() && profile != null && profile.getName() != null) {
            name = profile.getName();
        }
        pending = new Pending(uuid, name, message.decoratedContent().getString(), bound.chatType().is(ChatType.CHAT));
    }

    public static synchronized void endPlayerMessage() {
        pending = null;
    }

    public static synchronized void accept(Component shown) {
        Pending sender = pending;
        pending = null;
        if (shown == null) {
            return;
        }
        String text = clean(shown.getString());
        if (text.isEmpty()) {
            return;
        }

        String[] parsed = parseChat(shown);
        if (parsed != null) {
            addPlayer(sender != null ? sender.uuid() : lookup(parsed[0], true), parsed[0], parsed[1]);
            return;
        }
        if (sender != null && sender.plain()) {
            addPlayer(sender.uuid(), sender.name(), sender.body());
            return;
        }
        Matcher matcher = BRACKETED.matcher(text);
        if (matcher.matches()) {
            UUID uuid = sender != null ? sender.uuid() : lookup(matcher.group(1), false);
            if (uuid != null) {
                addPlayer(uuid, matcher.group(1), matcher.group(2));
                return;
            }
        }
        addSystem(text);
    }

    static synchronized void addPlayer(UUID uuid, String name, String body) {
        String text = clean(body);
        if (text.isEmpty()) {
            return;
        }
        add(new ChatPayloads.EntryData(nextId++, uuid == null ? Util.NIL_UUID : uuid, "", name == null ? "" : name,
                ChatStore.ENTRY_TEXT, text, "", System.currentTimeMillis()));
    }

    static synchronized void addSystem(String body) {
        String text = clean(body);
        if (text.isEmpty()) {
            return;
        }
        add(new ChatPayloads.EntryData(nextId++, Util.NIL_UUID, "", "", ChatStore.ENTRY_SYSTEM, text, "",
                System.currentTimeMillis()));
    }

    private static void add(ChatPayloads.EntryData entry) {
        ENTRIES.add(entry);
        if (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
        revision++;
    }

    static synchronized PhoneClientChatState.Conversation conversation() {
        if (snapshot == null || snapshotRevision != revision) {
            long updatedAt = ENTRIES.isEmpty() ? 0L : ENTRIES.get(ENTRIES.size() - 1).createdAt();
            snapshot = new PhoneClientChatState.Conversation(new ChatPayloads.ConversationData(CONVERSATION_ID,
                    ChatStore.KIND_GROUP, "", "", null, updatedAt, lastReadId, countUnread(), false, List.of(), ENTRIES));
            snapshotRevision = revision;
        }
        return snapshot;
    }

    static boolean isPublic(PhoneClientChatState.Conversation conversation) {
        return conversation != null && conversation.id == CONVERSATION_ID;
    }

    static synchronized void markRead() {
        long last = nextId - 1L;
        if (lastReadId != last) {
            lastReadId = last;
            revision++;
        }
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        pending = null;
        lastReadId = nextId - 1L;
        revision++;
    }

    static int onlineCount() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? 0 : connection.getListedOnlinePlayers().size();
    }

    private static int countUnread() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        int count = 0;
        for (ChatPayloads.EntryData entry : ENTRIES) {
            if (entry.id() > lastReadId && entry.kind() == ChatStore.ENTRY_TEXT
                    && (self == null || !self.equals(entry.senderUuid()))) {
                count++;
            }
        }
        return count;
    }

    private static String[] parseChat(Component shown) {
        if (!(shown.getContents() instanceof TranslatableContents contents) || !CHAT_KEY.equals(contents.getKey())
                || contents.getArgs().length < 2 || !shown.getSiblings().isEmpty()) {
            return null;
        }
        String name = clean(argument(contents.getArgs()[0]));
        String body = clean(argument(contents.getArgs()[1]));
        return name.isEmpty() || body.isEmpty() ? null : new String[] {name, body};
    }

    private static String argument(Object value) {
        return value instanceof Component component ? component.getString() : String.valueOf(value);
    }

    private static UUID lookup(String name, boolean fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && (name.equals(minecraft.player.getGameProfile().getName())
                || name.equals(clean(minecraft.player.getDisplayName().getString())))) {
            return minecraft.player.getUUID();
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(name);
            if (info != null) {
                return info.getProfile().getId();
            }
            for (PlayerInfo candidate : connection.getOnlinePlayers()) {
                Component display = candidate.getTabListDisplayName();
                if (display != null && name.equals(clean(display.getString()))) {
                    return candidate.getProfile().getId();
                }
            }
        }
        return fallback ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)) : null;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String stripped = ChatFormatting.stripFormatting(value);
        if (stripped == null) {
            return "";
        }
        stripped = stripped.replace("\r", "").strip();
        return stripped.length() > MAX_BODY_LENGTH ? stripped.substring(0, MAX_BODY_LENGTH) : stripped;
    }
}
