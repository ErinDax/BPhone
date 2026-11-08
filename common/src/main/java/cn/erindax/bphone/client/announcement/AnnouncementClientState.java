package cn.erindax.bphone.client.announcement;

import cn.erindax.bphone.announcement.Announcement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AnnouncementClientState {
    private static final List<Announcement> ANNOUNCEMENTS = new ArrayList<>();
    private static long lastRead;
    private static boolean loaded;

    private AnnouncementClientState() {
    }

    static synchronized void apply(List<Announcement> announcements, long lastReadTime) {
        ANNOUNCEMENTS.clear();
        ANNOUNCEMENTS.addAll(announcements);
        ANNOUNCEMENTS.sort(Comparator.comparing(Announcement::starred).reversed()
                .thenComparing(Comparator.comparingLong(Announcement::time).reversed()));
        lastRead = lastReadTime;
        loaded = true;
    }

    public static synchronized List<Announcement> getAnnouncements() {
        return List.copyOf(ANNOUNCEMENTS);
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized Announcement find(UUID id) {
        for (Announcement announcement : ANNOUNCEMENTS) {
            if (announcement.id().equals(id)) {
                return announcement;
            }
        }
        return null;
    }

    public static synchronized boolean isUnread(Announcement announcement) {
        return announcement != null && announcement.time() > lastRead;
    }

    public static synchronized int getUnreadCount() {
        int count = 0;
        for (Announcement announcement : ANNOUNCEMENTS) {
            if (announcement.time() > lastRead) {
                count++;
            }
        }
        return count;
    }

    public static synchronized Announcement newestUnreadStarred() {
        Announcement newest = null;
        for (Announcement announcement : ANNOUNCEMENTS) {
            if (announcement.starred() && announcement.time() > lastRead
                    && (newest == null || announcement.time() > newest.time())) {
                newest = announcement;
            }
        }
        return newest;
    }

    static synchronized void markReadLocally() {
        for (Announcement announcement : ANNOUNCEMENTS) {
            lastRead = Math.max(lastRead, announcement.time());
        }
    }

    public static synchronized void clear() {
        ANNOUNCEMENTS.clear();
        lastRead = 0L;
        loaded = false;
    }
}
