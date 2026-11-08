package cn.erindax.bphone.client.notification;

import cn.erindax.bphone.client.phone.PhoneNetworkingClient;
import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.notification.NotificationNetworking;
import cn.erindax.bphone.notification.PhoneNotification;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public final class NotificationNetworkingClient {

    private static final int MAX_TOASTS_PER_SYNC = 3;
    private static final int TOAST_TEXT_LENGTH = 40;
    private static boolean initialized;

    private NotificationNetworkingClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.s2c(), NotificationNetworking.SYNC_LIST, (buf, context) -> {
            var list = NotificationNetworking.readList(buf);
            context.queue(() -> {
                boolean firstSync = !NotificationClientState.hasReceived();
                List<PhoneNotification> arrived = NotificationClientState.apply(list);
                if (firstSync) {
                    announceUnread(list);
                } else {
                    announce(arrived);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), NotificationNetworking.SYNC_UNREAD, (buf, context) -> {
            int unread = buf.readVarInt();
            context.queue(() -> NotificationClientState.setUnreadCount(unread));
        });
    }

    public static void requestSync() {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        NetworkManager.sendToServer(NotificationNetworking.REQUEST_SYNC, buf);
    }

    public static void requestMarkAllRead() {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        NetworkManager.sendToServer(NotificationNetworking.MARK_ALL_READ, buf);
    }

    public static void requestMarkRead(UUID notificationId) {
        if (notificationId == null) {
            return;
        }
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUUID(notificationId);
        NetworkManager.sendToServer(NotificationNetworking.MARK_READ, buf);
    }

    public static void requestClear() {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        NetworkManager.sendToServer(NotificationNetworking.CLEAR, buf);
    }

    private static void announceUnread(List<PhoneNotification> list) {
        int unread = 0;
        for (PhoneNotification notification : list) {
            if (!notification.read()) {
                unread++;
            }
        }
        if (unread > 0 && PhoneNetworkingClient.isLocalMessagePopupEnabled()) {
            showToast(Component.translatable("toast.bphone.phone.notification.title"),
                    Component.translatable("toast.bphone.phone.notification.unread", unread));
        }
    }

    private static void announce(List<PhoneNotification> arrived) {
        if (arrived.isEmpty() || !PhoneNetworkingClient.isLocalMessagePopupEnabled()) {
            return;
        }

        int shown = 0;
        for (PhoneNotification notification : arrived) {
            if (shown >= MAX_TOASTS_PER_SYNC) {
                break;
            }
            String title = notification.source().isBlank()
                    ? notification.title()
                    : "[" + notification.source() + "] " + notification.title();
            showToast(Component.literal(clip(title)), Component.literal(clip(notification.body())));
            shown++;
        }
    }

    private static void showToast(Component title, Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
    }

    private static String clip(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= TOAST_TEXT_LENGTH ? value : value.substring(0, TOAST_TEXT_LENGTH) + "...";
    }
}
