package cn.erindax.bphone.notification;

import cn.erindax.bphone.BPhone;
import dev.architectury.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationStore {

    public static final int MAX_PER_PLAYER = 100;

    private static final String FILE_NAME = "beat_notifications.nbt";
    private static final Map<UUID, List<PhoneNotification>> CACHE = new ConcurrentHashMap<>();

    private static File storageFile;
    private static MinecraftServer currentServer;
    private static volatile boolean initialized;
    private static volatile boolean dirty;

    private NotificationStore() {
    }

    public static void init(MinecraftServer server) {
        if (initialized) {
            return;
        }
        try {
            storageFile = Platform.getGameFolder().resolve(FILE_NAME).toFile();
            CACHE.clear();
            load();
            currentServer = server;
            dirty = false;
            initialized = true;
            BPhone.LOGGER.info("Notification store initialized ({})", storageFile.getAbsolutePath());
        } catch (Exception e) {
            BPhone.LOGGER.error("Failed to initialize notification store", e);
        }
    }

    public static boolean isAvailable() {
        return initialized;
    }

    public static MinecraftServer server() {
        return currentServer;
    }

    public static void shutdown() {
        if (!initialized) {
            return;
        }
        save();
        CACHE.clear();
        currentServer = null;
        initialized = false;
        dirty = false;
    }

    public static void push(ServerPlayer player, String source, String title, String body) {
        if (player != null) {
            push(player.getUUID(), source, title, body);
        }
    }

    public static void push(UUID player, String source, String title, String body) {
        if (!initialized || player == null) {
            return;
        }
        PhoneNotification notification = PhoneNotification.create(source, title, body);
        List<PhoneNotification> list = CACHE.computeIfAbsent(player, key -> new ArrayList<>());
        synchronized (list) {
            list.add(0, notification);
            while (list.size() > MAX_PER_PLAYER) {
                list.remove(list.size() - 1);
            }
        }
        markDirty();
    }

    public static void pushAll(Iterable<ServerPlayer> players, String source, String title, String body) {
        if (players == null) {
            return;
        }
        for (ServerPlayer player : players) {
            push(player, source, title, body);
        }
    }

    public static int markAllRead(UUID player) {
        List<PhoneNotification> list = CACHE.get(player);
        if (list == null) {
            return 0;
        }
        int changed = 0;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                PhoneNotification notification = list.get(i);
                if (!notification.read()) {
                    list.set(i, notification.asRead());
                    changed++;
                }
            }
        }
        if (changed > 0) {
            markDirty();
        }
        return changed;
    }

    public static boolean markRead(UUID player, UUID notificationId) {
        List<PhoneNotification> list = CACHE.get(player);
        if (list == null || notificationId == null) {
            return false;
        }
        boolean changed = false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                PhoneNotification notification = list.get(i);
                if (notification.id().equals(notificationId) && !notification.read()) {
                    list.set(i, notification.asRead());
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public static void clear(UUID player) {
        if (player == null) {
            return;
        }
        if (CACHE.remove(player) != null) {
            markDirty();
        }
    }

    public static void forget(UUID player) {
        if (player != null) {
            CACHE.remove(player);
        }
    }

    public static List<PhoneNotification> list(UUID player) {
        List<PhoneNotification> list = player == null ? null : CACHE.get(player);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    public static int unreadCount(UUID player) {
        List<PhoneNotification> list = player == null ? null : CACHE.get(player);
        if (list == null) {
            return 0;
        }
        int count = 0;
        synchronized (list) {
            for (PhoneNotification notification : list) {
                if (!notification.read()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int totalCount(UUID player) {
        List<PhoneNotification> list = player == null ? null : CACHE.get(player);
        if (list == null) {
            return 0;
        }
        synchronized (list) {
            return list.size();
        }
    }

    private static void markDirty() {
        dirty = true;
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public static void save() {
        if (storageFile == null) {
            return;
        }
        try {
            CompoundTag root = new CompoundTag();
            for (Map.Entry<UUID, List<PhoneNotification>> entry : CACHE.entrySet()) {
                ListTag listTag = new ListTag();
                synchronized (entry.getValue()) {
                    for (PhoneNotification notification : entry.getValue()) {
                        listTag.add(notification.save());
                    }
                }
                root.put(entry.getKey().toString(), listTag);
            }
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                BPhone.LOGGER.warn("Unable to create notification storage directory {}", parent);
            }
            NbtIo.writeCompressed(root, storageFile.toPath());
            dirty = false;
        } catch (Exception e) {
            BPhone.LOGGER.error("Failed to save notification store", e);
        }
    }

    private static void load() {
        if (storageFile == null || !storageFile.exists()) {
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(storageFile.toPath(), NbtAccounter.unlimitedHeap());
            for (String key : root.getAllKeys()) {
                UUID player;
                try {
                    player = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                ListTag listTag = root.getList(key, Tag.TAG_COMPOUND);
                List<PhoneNotification> list = new ArrayList<>(listTag.size());
                for (int i = 0; i < listTag.size(); i++) {
                    list.add(PhoneNotification.load(listTag.getCompound(i)));
                }
                CACHE.put(player, list);
            }
            BPhone.LOGGER.info("Loaded notifications for {} players", CACHE.size());
        } catch (Exception e) {
            BPhone.LOGGER.error("Failed to load notification store", e);
        }
    }
}
