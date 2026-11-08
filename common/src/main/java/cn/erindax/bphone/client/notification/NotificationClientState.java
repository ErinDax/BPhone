package cn.erindax.bphone.client.notification;

import cn.erindax.bphone.notification.PhoneNotification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NotificationClientState {

    private static volatile List<PhoneNotification> notifications = List.of();
    private static volatile int unreadCount;
    private static volatile boolean received;

    private NotificationClientState() {
    }

    public static List<PhoneNotification> getNotifications() {
        return notifications;
    }

    public static void setNotifications(List<PhoneNotification> value) {
        notifications = value == null ? List.of() : List.copyOf(value);
    }

    public static boolean hasReceived() {
        return received;
    }

    public static List<PhoneNotification> apply(List<PhoneNotification> value) {
        List<PhoneNotification> next = value == null ? List.of() : List.copyOf(value);
        List<PhoneNotification> arrived = new ArrayList<>();
        if (received) {
            Set<UUID> known = new HashSet<>();
            for (PhoneNotification notification : notifications) {
                known.add(notification.id());
            }
            for (PhoneNotification notification : next) {
                if (!notification.read() && !known.contains(notification.id())) {
                    arrived.add(notification);
                }
            }
        }
        notifications = next;
        received = true;
        return arrived;
    }

    public static int getUnreadCount() {
        return unreadCount;
    }

    public static void setUnreadCount(int value) {
        unreadCount = Math.max(0, value);
    }

    public static boolean hasUnread() {
        return unreadCount > 0;
    }

    public static void clear() {
        notifications = List.of();
        unreadCount = 0;
        received = false;
    }
}
