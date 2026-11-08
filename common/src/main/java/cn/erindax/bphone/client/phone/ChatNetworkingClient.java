package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.chat.ChatImageStore;
import cn.erindax.bphone.phone.chat.ChatNetworking;
import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatService;
import cn.erindax.bphone.phone.chat.ChatStore;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatNetworkingClient {
    private static boolean initialized;

    private ChatNetworkingClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.STATE, (buf, context) -> {
            ChatPayloads.StateData state = ChatPayloads.StateData.read(buf);
            context.queue(() -> {
                PhoneClientChatState.applyState(state);
                withScreen(PhoneScreen::handleChatStateChanged);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.CONVERSATION, (buf, context) -> {
            ChatPayloads.ConversationData data = ChatPayloads.ConversationData.read(buf);
            context.queue(() -> {
                PhoneClientChatState.applyConversation(data);
                withScreen(PhoneScreen::handleChatStateChanged);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.CONVERSATION_REMOVED, (buf, context) -> {
            long id = buf.readVarLong();
            context.queue(() -> {
                PhoneClientChatState.removeConversation(id);
                withScreen(screen -> screen.handleChatConversationRemoved(id));
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.HISTORY_PAGE, (buf, context) -> {
            long id = buf.readVarLong();
            long aroundId = buf.readVarLong();
            boolean hasMore = buf.readBoolean();
            List<ChatPayloads.EntryData> entries = ChatPayloads.readEntries(buf);
            context.queue(() -> {
                PhoneClientChatState.applyHistory(id, aroundId, hasMore, entries);
                withScreen(screen -> screen.handleChatHistory(id, aroundId));
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.SEARCH_RESULT, (buf, context) -> {
            String query = buf.readUtf(ChatNetworking.MAX_SEARCH_LENGTH);
            int count = Math.min(buf.readVarInt(), 64);
            List<ChatPayloads.SearchHitData> hits = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                hits.add(ChatPayloads.SearchHitData.read(buf));
            }
            context.queue(() -> withScreen(screen -> screen.handleChatSearchResult(query, hits)));
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.IMAGE_DATA, (buf, context) -> {
            String imageId = buf.readUtf(ChatPayloads.MAX_IMAGE_ID_LENGTH);
            byte[] data = buf.readByteArray(ChatImageStore.MAX_IMAGE_BYTES + 1024);
            context.queue(() -> PhoneChatImages.receive(imageId, data));
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.READ_UPDATE, (buf, context) -> {
            long id = buf.readVarLong();
            UUID member = buf.readUUID();
            long lastReadId = buf.readVarLong();
            context.queue(() -> PhoneClientChatState.applyReadUpdate(id, member, lastReadId));
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.TOAST, (buf, context) -> {
            long id = buf.readVarLong();
            String title = buf.readUtf(ChatPayloads.MAX_TITLE_LENGTH);
            ChatPayloads.EntryData entry = ChatPayloads.EntryData.read(buf);
            context.queue(() -> showToast(id, title, entry));
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.REQUESTS, (buf, context) -> {
            int count = Math.min(buf.readVarInt(), 64);
            List<PhoneClientChatState.FriendRequest> requests = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                requests.add(new PhoneClientChatState.FriendRequest(buf.readVarLong(), buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH),
                        buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH), buf.readLong()));
            }
            context.queue(() -> PhoneClientChatState.applyRequests(requests));
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ChatNetworking.FOCUS, (buf, context) -> {
            long id = buf.readVarLong();
            context.queue(() -> withScreen(screen -> screen.handleChatFocus(id)));
        });
    }

    static Component preview(ChatPayloads.EntryData entry) {
        if (entry == null) {
            return Component.translatable("screen.bphone.phone.chat.preview.empty");
        }
        return switch (entry.kind()) {
            case ChatStore.ENTRY_IMAGE -> Component.translatable("phone.chat.preview.image");
            case ChatStore.ENTRY_SYSTEM -> systemText(entry.body());
            default -> Component.literal(entry.body());
        };
    }

    static Component systemText(String body) {
        String[] parts = body == null ? new String[0] : body.split(ChatService.SYSTEM_SEPARATOR, -1);
        if (parts.length == 0 || parts[0].isBlank()) {
            return Component.empty();
        }
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return Component.translatable(parts[0], args);
    }

    private static void showToast(long conversationId, String title, ChatPayloads.EntryData entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || entry == null) {
            return;
        }
        if (minecraft.screen instanceof PhoneScreen screen && screen.isViewingConversation(conversationId)) {
            return;
        }
        if (!PhoneNetworkingClient.isLocalMessagePopupEnabled()) {
            return;
        }
        PhoneClientChatState.Conversation conversation = PhoneClientChatState.getConversation(conversationId);
        Component body = conversation != null && conversation.isGroup()
                ? Component.translatable("toast.bphone.phone.chat.group_body", entry.senderName(), preview(entry))
                : preview(entry);
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("toast.bphone.phone.chat.from", title.isBlank() ? entry.senderName() : title), body);
    }

    private static void withScreen(java.util.function.Consumer<PhoneScreen> action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof PhoneScreen screen) {
            action.accept(screen);
        }
    }

    private static void send(net.minecraft.resources.ResourceLocation id, RegistryFriendlyByteBuf buf) {
        NetworkManager.sendToServer(id, buf);
    }

    public static void sendText(long conversationId, String number, String text) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        buf.writeUtf(PhoneData.normalizePhoneNumber(number), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeUtf(ChatPayloads.clip(text, ChatNetworking.MAX_MESSAGE_LENGTH), ChatNetworking.MAX_MESSAGE_LENGTH);
        send(ChatNetworking.SEND, buf);
    }

    public static void markRead(long conversationId, long entryId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        buf.writeVarLong(entryId);
        send(ChatNetworking.READ, buf);
    }

    public static void requestHistory(long conversationId, long beforeId, long aroundId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        buf.writeVarLong(beforeId);
        buf.writeVarLong(aroundId);
        send(ChatNetworking.HISTORY, buf);
    }

    public static void search(String query) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(ChatPayloads.clip(query, ChatNetworking.MAX_SEARCH_LENGTH), ChatNetworking.MAX_SEARCH_LENGTH);
        send(ChatNetworking.SEARCH, buf);
    }

    public static void requestImage(String imageId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(imageId, ChatPayloads.MAX_IMAGE_ID_LENGTH);
        send(ChatNetworking.IMAGE_REQUEST, buf);
    }

    public static void sendImageChunk(long uploadId, long conversationId, String number, int index, int count, byte[] data) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(uploadId);
        buf.writeVarLong(conversationId);
        buf.writeUtf(PhoneData.normalizePhoneNumber(number), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeVarInt(index);
        buf.writeVarInt(count);
        buf.writeByteArray(data);
        send(ChatNetworking.IMAGE_CHUNK, buf);
    }

    public static void createGroup(String name, List<String> numbers) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(ChatPayloads.clip(name, ChatService.MAX_GROUP_NAME_LENGTH), ChatService.MAX_GROUP_NAME_LENGTH);
        ChatNetworking.writeNumbers(buf, numbers);
        send(ChatNetworking.GROUP_CREATE, buf);
    }

    public static void invite(long conversationId, List<String> numbers) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        ChatNetworking.writeNumbers(buf, numbers);
        send(ChatNetworking.GROUP_INVITE, buf);
    }

    public static void rename(long conversationId, String name) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        buf.writeUtf(ChatPayloads.clip(name, ChatService.MAX_GROUP_NAME_LENGTH), ChatService.MAX_GROUP_NAME_LENGTH);
        send(ChatNetworking.GROUP_RENAME, buf);
    }

    public static void leave(long conversationId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        send(ChatNetworking.GROUP_LEAVE, buf);
    }

    public static void kick(long conversationId, String number) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        buf.writeUtf(PhoneData.normalizePhoneNumber(number), PhoneData.PHONE_NUMBER_LENGTH);
        send(ChatNetworking.GROUP_KICK, buf);
    }

    public static void block(String number, boolean block) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(PhoneData.normalizePhoneNumber(number), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeBoolean(block);
        send(ChatNetworking.BLOCK, buf);
    }

    public static void clear(long conversationId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(conversationId);
        send(ChatNetworking.CLEAR, buf);
    }

    public static void setNickname(String nickname) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(ChatPayloads.clip(nickname, PhoneData.MAX_CONTACT_NAME_LENGTH), PhoneData.MAX_CONTACT_NAME_LENGTH);
        send(ChatNetworking.NICKNAME, buf);
    }

    public static void requestFriend(String number) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(PhoneData.normalizePhoneNumber(number), PhoneData.PHONE_NUMBER_LENGTH);
        send(ChatNetworking.FRIEND_REQUEST, buf);
    }

    public static void respondFriendRequest(long requestId, boolean accept) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarLong(requestId);
        buf.writeBoolean(accept);
        send(ChatNetworking.FRIEND_RESPOND, buf);
    }

    public static void removeFriend(String number) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(PhoneData.normalizePhoneNumber(number), PhoneData.PHONE_NUMBER_LENGTH);
        send(ChatNetworking.FRIEND_REMOVE, buf);
    }
}
