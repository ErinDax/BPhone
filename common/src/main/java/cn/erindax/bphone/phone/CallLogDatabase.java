package cn.erindax.bphone.phone;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class CallLogDatabase {
    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS call_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            owner_uuid TEXT NOT NULL,
            other_number TEXT NOT NULL,
            other_name TEXT DEFAULT '',
            call_type TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            duration_ticks INTEGER DEFAULT 0
        )
        """;

    private static final String CREATE_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_call_log_owner_time ON call_log(owner_uuid, timestamp DESC)";

    private static final int MAX_ENTRIES_PER_OWNER = 50;

    private final Connection connection;
    private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();

    public CallLogDatabase(String dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initializeDatabase();
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize call log database", e);
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA temp_store=MEMORY");
            stmt.execute("PRAGMA busy_timeout=3000");
            stmt.execute(CREATE_TABLE);
            stmt.execute(CREATE_INDEX);
        }
    }

    public void addEntry(UUID ownerUuid, String otherNumber, String otherName, String callType, int durationTicks) {
        dbLock.writeLock().lock();
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO call_log (owner_uuid, other_number, other_name, call_type, timestamp, duration_ticks) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, otherNumber);
            stmt.setString(3, otherName == null ? "" : otherName);
            stmt.setString(4, callType);
            stmt.setLong(5, System.currentTimeMillis());
            stmt.setInt(6, durationTicks);
            stmt.executeUpdate();
            trimEntries(ownerUuid);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    public List<CallLogEntry> getEntries(UUID ownerUuid, int limit) {
        dbLock.readLock().lock();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id, other_number, other_name, call_type, timestamp, duration_ticks FROM call_log WHERE owner_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setInt(2, limit);
            List<CallLogEntry> entries = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new CallLogEntry(
                            rs.getLong("id"),
                            rs.getString("other_number"),
                            rs.getString("other_name"),
                            rs.getString("call_type"),
                            rs.getLong("timestamp"),
                            rs.getInt("duration_ticks")
                    ));
                }
            }
            return entries;
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        } finally {
            dbLock.readLock().unlock();
        }
    }

    private void trimEntries(UUID ownerUuid) throws SQLException {
        try (PreparedStatement countStmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM call_log WHERE owner_uuid = ?")) {
            countStmt.setString(1, ownerUuid.toString());
            try (ResultSet rs = countStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > MAX_ENTRIES_PER_OWNER) {
                    try (PreparedStatement delStmt = connection.prepareStatement(
                            "DELETE FROM call_log WHERE id IN (SELECT id FROM call_log WHERE owner_uuid = ? ORDER BY timestamp ASC LIMIT ?)")) {
                        int excess = rs.getInt(1) - MAX_ENTRIES_PER_OWNER;
                        delStmt.setString(1, ownerUuid.toString());
                        delStmt.setInt(2, excess);
                        delStmt.executeUpdate();
                    }
                }
            }
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
