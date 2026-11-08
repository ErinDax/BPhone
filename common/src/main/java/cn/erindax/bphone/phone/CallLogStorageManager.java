package cn.erindax.bphone.phone;

import java.util.List;
import java.util.UUID;

public final class CallLogStorageManager {
    private static final Object LOCK = new Object();
    private static CallLogStorageManager instance;

    private final CallLogDatabase database;

    private CallLogStorageManager(String dbPath) {
        this.database = new CallLogDatabase(dbPath);
    }

    public static CallLogStorageManager getInstance() {
        synchronized (LOCK) {
            if (instance == null) {
                throw new IllegalStateException("CallLogStorageManager not initialized. Call init() first.");
            }
            return instance;
        }
    }

    public static boolean isAvailable() {
        synchronized (LOCK) {
            return instance != null;
        }
    }

    public static void init(String dbPath) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new CallLogStorageManager(dbPath);
            }
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (instance == null) {
                return;
            }
            instance.database.close();
            instance = null;
        }
    }

    public static void addEntry(UUID ownerUuid, String otherNumber, String otherName, String callType, int durationTicks) {
        synchronized (LOCK) {
            if (instance == null) {
                return;
            }
            instance.database.addEntry(ownerUuid, otherNumber, otherName, callType, durationTicks);
        }
    }

    public static List<CallLogEntry> getEntries(UUID ownerUuid, int limit) {
        synchronized (LOCK) {
            if (instance == null) {
                return List.of();
            }
            return instance.database.getEntries(ownerUuid, limit);
        }
    }
}
