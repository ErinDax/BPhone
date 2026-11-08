package cn.erindax.bphone.phone.chat;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.phone.PhoneData;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatNetworking {
    public static final int MAX_MESSAGE_LENGTH = 96;
    public static final int MAX_SEARCH_LENGTH = 32;
    public static final int MAX_NUMBERS = 32;

    public static final ResourceLocation SEND = BPhone.id("chat_send");
    public static final ResourceLocation READ = BPhone.id("chat_read");
    public static final ResourceLocation HISTORY = BPhone.id("chat_history");
    public static final ResourceLocation SEARCH = BPhone.id("chat_search");
    public static final ResourceLocation IMAGE_CHUNK = BPhone.id("chat_image_chunk");
    public static final ResourceLocation IMAGE_REQUEST = BPhone.id("chat_image_request");
    public static final ResourceLocation GROUP_CREATE = BPhone.id("chat_group_create");
    public static final ResourceLocation GROUP_INVITE = BPhone.id("chat_group_invite");
    public static final ResourceLocation GROUP_RENAME = BPhone.id("chat_group_rename");
    public static final ResourceLocation GROUP_LEAVE = BPhone.id("chat_group_leave");
    public static final ResourceLocation GROUP_KICK = BPhone.id("chat_group_kick");
    public static final ResourceLocation BLOCK = BPhone.id("chat_block");
    public static final ResourceLocation CLEAR = BPhone.id("chat_clear");
    public static final ResourceLocation NICKNAME = BPhone.id("chat_nickname");
    public static final ResourceLocation FRIEND_REQUEST = BPhone.id("chat_friend_request");
    public static final ResourceLocation FRIEND_RESPOND = BPhone.id("chat_friend_respond");
    public static final ResourceLocation FRIEND_REMOVE = BPhone.id("chat_friend_remove");

    public static final ResourceLocation STATE = BPhone.id("chat_state");
    public static final ResourceLocation CONVERSATION = BPhone.id("chat_conversation");
    public static final ResourceLocation CONVERSATION_REMOVED = BPhone.id("chat_conversation_removed");
    public static final ResourceLocation HISTORY_PAGE = BPhone.id("chat_history_page");
    public static final ResourceLocation SEARCH_RESULT = BPhone.id("chat_search_result");
    public static final ResourceLocation IMAGE_DATA = BPhone.id("chat_image_data");
    public static final ResourceLocation READ_UPDATE = BPhone.id("chat_read_update");
    public static final ResourceLocation TOAST = BPhone.id("chat_toast");
    public static final ResourceLocation REQUESTS = BPhone.id("chat_requests");
    public static final ResourceLocation FOCUS = BPhone.id("chat_focus");

    private static boolean initialized;

    private ChatNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(STATE, CONVERSATION, CONVERSATION_REMOVED, HISTORY_PAGE, SEARCH_RESULT,
                IMAGE_DATA, READ_UPDATE, TOAST, REQUESTS, FOCUS);

        NetworkManager.registerReceiver(NetworkManager.c2s(), SEND, (buf, context) -> {
            long conversationId = buf.readVarLong();
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            String text = buf.readUtf(MAX_MESSAGE_LENGTH);
            context.queue(() -> ChatService.sendText((ServerPlayer) context.getPlayer(), conversationId, number, text));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), READ, (buf, context) -> {
            long conversationId = buf.readVarLong();
            long entryId = buf.readVarLong();
            context.queue(() -> ChatService.markRead((ServerPlayer) context.getPlayer(), conversationId, entryId));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), HISTORY, (buf, context) -> {
            long conversationId = buf.readVarLong();
            long beforeId = buf.readVarLong();
            long aroundId = buf.readVarLong();
            context.queue(() -> ChatService.history((ServerPlayer) context.getPlayer(), conversationId, beforeId, aroundId));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), SEARCH, (buf, context) -> {
            String query = buf.readUtf(MAX_SEARCH_LENGTH);
            context.queue(() -> ChatService.search((ServerPlayer) context.getPlayer(), query));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), IMAGE_CHUNK, (buf, context) -> {
            long uploadId = buf.readVarLong();
            long conversationId = buf.readVarLong();
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            int index = buf.readVarInt();
            int count = buf.readVarInt();
            byte[] data = buf.readByteArray(ChatImageStore.CHUNK_SIZE);
            context.queue(() -> ChatService.acceptImageChunk((ServerPlayer) context.getPlayer(), uploadId, conversationId,
                    number, index, count, data));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), IMAGE_REQUEST, (buf, context) -> {
            String imageId = buf.readUtf(ChatPayloads.MAX_IMAGE_ID_LENGTH);
            context.queue(() -> ChatService.requestImage((ServerPlayer) context.getPlayer(), imageId));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), GROUP_CREATE, (buf, context) -> {
            String name = buf.readUtf(ChatService.MAX_GROUP_NAME_LENGTH);
            List<String> numbers = readNumbers(buf);
            context.queue(() -> ChatService.createGroup((ServerPlayer) context.getPlayer(), name, numbers));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), GROUP_INVITE, (buf, context) -> {
            long conversationId = buf.readVarLong();
            List<String> numbers = readNumbers(buf);
            context.queue(() -> ChatService.invite((ServerPlayer) context.getPlayer(), conversationId, numbers));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), GROUP_RENAME, (buf, context) -> {
            long conversationId = buf.readVarLong();
            String name = buf.readUtf(ChatService.MAX_GROUP_NAME_LENGTH);
            context.queue(() -> ChatService.rename((ServerPlayer) context.getPlayer(), conversationId, name));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), GROUP_LEAVE, (buf, context) -> {
            long conversationId = buf.readVarLong();
            context.queue(() -> ChatService.leave((ServerPlayer) context.getPlayer(), conversationId));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), GROUP_KICK, (buf, context) -> {
            long conversationId = buf.readVarLong();
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            context.queue(() -> ChatService.kick((ServerPlayer) context.getPlayer(), conversationId, number));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), BLOCK, (buf, context) -> {
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            boolean block = buf.readBoolean();
            context.queue(() -> ChatService.block((ServerPlayer) context.getPlayer(), number, block));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CLEAR, (buf, context) -> {
            long conversationId = buf.readVarLong();
            context.queue(() -> ChatService.clear((ServerPlayer) context.getPlayer(), conversationId));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), NICKNAME, (buf, context) -> {
            String nickname = buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH);
            context.queue(() -> ChatService.setNickname((ServerPlayer) context.getPlayer(), nickname));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), FRIEND_REQUEST, (buf, context) -> {
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            context.queue(() -> ChatService.sendFriendRequest((ServerPlayer) context.getPlayer(), number));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), FRIEND_RESPOND, (buf, context) -> {
            long requestId = buf.readVarLong();
            boolean accept = buf.readBoolean();
            context.queue(() -> ChatService.respondFriendRequest((ServerPlayer) context.getPlayer(), requestId, accept));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), FRIEND_REMOVE, (buf, context) -> {
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            context.queue(() -> ChatService.removeFriend((ServerPlayer) context.getPlayer(), number));
        });

        PlayerEvent.PLAYER_JOIN.register(ChatService::syncOnJoin);
        PlayerEvent.PLAYER_QUIT.register(ChatService::forget);
    }

    public static void writeNumbers(RegistryFriendlyByteBuf buf, List<String> numbers) {
        int count = Math.min(numbers.size(), MAX_NUMBERS);
        buf.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buf.writeUtf(PhoneData.normalizePhoneNumber(numbers.get(index)), PhoneData.PHONE_NUMBER_LENGTH);
        }
    }

    private static List<String> readNumbers(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_NUMBERS);
        List<String> numbers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            numbers.add(buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH));
        }
        return numbers;
    }

    static void sendState(ServerPlayer player, ChatPayloads.StateData state) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        state.write(buf);
        NetworkManager.sendToPlayer(player, STATE, buf);
    }

    static void sendConversation(ServerPlayer player, ChatPayloads.ConversationData conversation) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        conversation.write(buf);
        NetworkManager.sendToPlayer(player, CONVERSATION, buf);
    }

    static void sendRemoved(ServerPlayer player, long conversationId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarLong(conversationId);
        NetworkManager.sendToPlayer(player, CONVERSATION_REMOVED, buf);
    }

    static void sendHistory(ServerPlayer player, long conversationId, long aroundId, boolean hasMore,
                            List<ChatPayloads.EntryData> entries) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarLong(conversationId);
        buf.writeVarLong(aroundId);
        buf.writeBoolean(hasMore);
        ChatPayloads.writeEntries(buf, entries);
        NetworkManager.sendToPlayer(player, HISTORY_PAGE, buf);
    }

    static void sendSearch(ServerPlayer player, String query, List<ChatPayloads.SearchHitData> hits) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeUtf(query, MAX_SEARCH_LENGTH);
        buf.writeVarInt(hits.size());
        for (ChatPayloads.SearchHitData hit : hits) {
            hit.write(buf);
        }
        NetworkManager.sendToPlayer(player, SEARCH_RESULT, buf);
    }

    static void sendImage(ServerPlayer player, String imageId, byte[] data) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeUtf(imageId, ChatPayloads.MAX_IMAGE_ID_LENGTH);
        buf.writeByteArray(data == null ? new byte[0] : data);
        NetworkManager.sendToPlayer(player, IMAGE_DATA, buf);
    }

    static void sendReadUpdate(ServerPlayer player, long conversationId, UUID member, long lastReadId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarLong(conversationId);
        buf.writeUUID(member);
        buf.writeVarLong(lastReadId);
        NetworkManager.sendToPlayer(player, READ_UPDATE, buf);
    }

    static void sendToast(ServerPlayer player, long conversationId, String title, ChatPayloads.EntryData entry) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarLong(conversationId);
        buf.writeUtf(ChatPayloads.clip(title, ChatPayloads.MAX_TITLE_LENGTH), ChatPayloads.MAX_TITLE_LENGTH);
        entry.write(buf);
        NetworkManager.sendToPlayer(player, TOAST, buf);
    }

    static void sendRequests(ServerPlayer player, List<ChatStore.Request> requests) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarInt(requests.size());
        for (ChatStore.Request request : requests) {
            buf.writeVarLong(request.id());
            buf.writeUtf(request.fromNumber(), PhoneData.PHONE_NUMBER_LENGTH);
            buf.writeUtf(ChatPayloads.clip(request.fromName(), PhoneData.MAX_CONTACT_NAME_LENGTH), PhoneData.MAX_CONTACT_NAME_LENGTH);
            buf.writeLong(request.createdAt());
        }
        NetworkManager.sendToPlayer(player, REQUESTS, buf);
    }

    static void sendFocus(ServerPlayer player, long conversationId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarLong(conversationId);
        NetworkManager.sendToPlayer(player, FOCUS, buf);
    }
}
