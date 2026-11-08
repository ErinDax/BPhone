package cn.erindax.bphone.announcement;

import cn.erindax.bphone.BPhone;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnnouncementStore {
    public static final int MAX_ANNOUNCEMENTS = 50;
    private static final String FILE_NAME = "bphone_announcements.nbt";
    private static final List<Announcement> ANNOUNCEMENTS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_READ = new HashMap<>();
    private static Path file;
    private static boolean dirty;

    private AnnouncementStore() {
    }

    public static synchronized void open(MinecraftServer server) {
        ANNOUNCEMENTS.clear();
        LAST_READ.clear();
        dirty = false;
        file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        load();
    }

    public static synchronized void close() {
        save();
        ANNOUNCEMENTS.clear();
        LAST_READ.clear();
        file = null;
    }

    public static synchronized boolean isOpen() {
        return file != null;
    }

    public static synchronized List<Announcement> list() {
        return List.copyOf(ANNOUNCEMENTS);
    }

    public static synchronized Announcement find(UUID id) {
        for (Announcement announcement : ANNOUNCEMENTS) {
            if (announcement.id().equals(id)) {
                return announcement;
            }
        }
        return null;
    }

    public static synchronized Announcement publish(UUID editId, String title, String body, String author, boolean starred) {
        long now = System.currentTimeMillis();
        if (editId != null) {
            for (int index = 0; index < ANNOUNCEMENTS.size(); index++) {
                Announcement existing = ANNOUNCEMENTS.get(index);
                if (existing.id().equals(editId)) {
                    Announcement updated = new Announcement(editId, title, body, existing.author(), existing.time(), now, starred);
                    ANNOUNCEMENTS.set(index, updated);
                    dirty = true;
                    return updated;
                }
            }
            return null;
        }
        Announcement created = new Announcement(UUID.randomUUID(), title, body, author, now, 0L, starred);
        ANNOUNCEMENTS.add(0, created);
        while (ANNOUNCEMENTS.size() > MAX_ANNOUNCEMENTS) {
            ANNOUNCEMENTS.remove(ANNOUNCEMENTS.size() - 1);
        }
        dirty = true;
        return created;
    }

    public static synchronized Announcement setStarred(UUID id, boolean starred) {
        for (int index = 0; index < ANNOUNCEMENTS.size(); index++) {
            Announcement existing = ANNOUNCEMENTS.get(index);
            if (existing.id().equals(id)) {
                Announcement updated = existing.withStarred(starred);
                ANNOUNCEMENTS.set(index, updated);
                dirty = true;
                return updated;
            }
        }
        return null;
    }

    public static synchronized boolean delete(UUID id) {
        boolean removed = ANNOUNCEMENTS.removeIf(announcement -> announcement.id().equals(id));
        dirty |= removed;
        return removed;
    }

    public static synchronized long lastRead(UUID player) {
        return LAST_READ.getOrDefault(player, 0L);
    }

    public static synchronized void markRead(UUID player) {
        LAST_READ.put(player, System.currentTimeMillis());
        dirty = true;
    }

    public static synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            for (Announcement announcement : ANNOUNCEMENTS) {
                list.add(announcement.save());
            }
            root.put("announcements", list);
            CompoundTag read = new CompoundTag();
            for (Map.Entry<UUID, Long> entry : LAST_READ.entrySet()) {
                read.putLong(entry.getKey().toString(), entry.getValue());
            }
            root.put("read", read);
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
            dirty = false;
        } catch (Exception exception) {
            BPhone.LOGGER.error("Failed to save announcements", exception);
        }
    }

    private static void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList("announcements", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size() && ANNOUNCEMENTS.size() < MAX_ANNOUNCEMENTS; index++) {
                ANNOUNCEMENTS.add(Announcement.load(list.getCompound(index)));
            }
            CompoundTag read = root.getCompound("read");
            for (String key : read.getAllKeys()) {
                try {
                    LAST_READ.put(UUID.fromString(key), read.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception exception) {
            BPhone.LOGGER.error("Failed to load announcements", exception);
        }
    }
}
