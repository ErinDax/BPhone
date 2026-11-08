package cn.erindax.bphone.notification;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class NotificationApi {

    private NotificationApi() {
    }

    public static void push(ServerPlayer player, String source, String title, String body) {
        if (player == null) {
            return;
        }
        NotificationStore.push(player, source, title, body);
        syncLater(player.getServer(), player.getUUID());
    }

    public static void push(UUID player, String source, String title, String body) {
        NotificationStore.push(player, source, title, body);
        syncLater(NotificationStore.server(), player);
    }

    public static void pushAll(MinecraftServer server, String source, String title, String body) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            NotificationStore.push(player, source, title, body);
            syncLater(server, player.getUUID());
        }
    }

    public static List<PhoneNotification> list(UUID player) {
        return NotificationStore.list(player);
    }

    public static int unreadCount(UUID player) {
        return NotificationStore.unreadCount(player);
    }

    public static void markAllRead(UUID player) {
        NotificationStore.markAllRead(player);
        syncLater(NotificationStore.server(), player);
    }

    public static void clear(UUID player) {
        NotificationStore.clear(player);
        syncLater(NotificationStore.server(), player);
    }

    public static boolean isAvailable() {
        return NotificationStore.isAvailable();
    }

    private static void syncLater(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null || !NotificationStore.isAvailable()) {
            return;
        }
        server.execute(() -> NotificationNetworking.syncTo(server.getPlayerList().getPlayer(playerId)));
    }
}
