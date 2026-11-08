package cn.erindax.bphone.notification;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.network.NetworkBufferUtils;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class NotificationNetworking {

    public static final ResourceLocation SYNC_LIST = id("notification_sync_list");
    public static final ResourceLocation SYNC_UNREAD = id("notification_sync_unread");
    public static final ResourceLocation REQUEST_SYNC = id("notification_request_sync");
    public static final ResourceLocation MARK_ALL_READ = id("notification_mark_all_read");
    public static final ResourceLocation MARK_READ = id("notification_mark_read");
    public static final ResourceLocation CLEAR = id("notification_clear");

    private static boolean initialized;

    private NotificationNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(SYNC_LIST, SYNC_UNREAD);

        NetworkManager.registerReceiver(NetworkManager.c2s(), REQUEST_SYNC, (buf, context) ->
                context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        syncTo(player);
                    }
                }));

        NetworkManager.registerReceiver(NetworkManager.c2s(), MARK_ALL_READ, (buf, context) ->
                context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        NotificationStore.markAllRead(player.getUUID());
                        syncTo(player);
                    }
                }));

        NetworkManager.registerReceiver(NetworkManager.c2s(), MARK_READ, (buf, context) -> {
            UUID notificationId = buf.readUUID();
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    NotificationStore.markRead(player.getUUID(), notificationId);
                    syncTo(player);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CLEAR, (buf, context) ->
                context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        NotificationStore.clear(player.getUUID());
                        syncTo(player);
                    }
                }));

        PlayerEvent.PLAYER_JOIN.register(NotificationNetworking::syncTo);
    }

    public static void syncTo(ServerPlayer player) {
        if (player == null || !NotificationStore.isAvailable()) {
            return;
        }

        List<PhoneNotification> list = NotificationStore.list(player.getUUID());
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarInt(list.size());
        for (PhoneNotification notification : list) {
            buf.writeUUID(notification.id());
            buf.writeLong(notification.time());
            buf.writeUtf(notification.source());
            buf.writeUtf(notification.title());
            buf.writeUtf(notification.body());
            buf.writeBoolean(notification.read());
        }
        NetworkManager.sendToPlayer(player, SYNC_LIST, buf);

        syncUnread(player);
    }

    public static void syncUnread(ServerPlayer player) {
        if (player == null || !NotificationStore.isAvailable()) {
            return;
        }
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarInt(NotificationStore.unreadCount(player.getUUID()));
        NetworkManager.sendToPlayer(player, SYNC_UNREAD, buf);
    }

    public static List<PhoneNotification> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<PhoneNotification> list = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new PhoneNotification(
                    buf.readUUID(),
                    buf.readLong(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean()));
        }
        return list;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, path);
    }
}
