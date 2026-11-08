package cn.erindax.bphone.phone.chat;

import cn.erindax.bphone.BPhone;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ChatStore {
    public static final int KIND_DIRECT = 0;
    public static final int KIND_GROUP = 1;
    public static final int ENTRY_TEXT = 0;
    public static final int ENTRY_IMAGE = 1;
    public static final int ENTRY_SYSTEM = 2;
    private static final int SCHEMA_VERSION = 2;

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS chat_friends (
                owner_uuid TEXT NOT NULL,
                friend_number TEXT NOT NULL,
                friend_name TEXT NOT NULL,
                profile_id TEXT,
                PRIMARY KEY (owner_uuid, friend_number)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS owner_nicknames (
                owner_uuid TEXT PRIMARY KEY,
                nickname TEXT NOT NULL DEFAULT ''
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS friend_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_uuid TEXT NOT NULL,
                from_number TEXT NOT NULL,
                from_name TEXT NOT NULL,
                to_number TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at INTEGER NOT NULL,
                responded_at INTEGER
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS chat_conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind INTEGER NOT NULL,
                direct_key TEXT UNIQUE,
                title TEXT NOT NULL DEFAULT '',
                owner_uuid TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS chat_members (
                conversation_id INTEGER NOT NULL,
                member_uuid TEXT NOT NULL,
                member_number TEXT NOT NULL,
                member_name TEXT NOT NULL,
                last_read_id INTEGER NOT NULL DEFAULT 0,
                cleared_id INTEGER NOT NULL DEFAULT 0,
                joined_at INTEGER NOT NULL,
                PRIMARY KEY (conversation_id, member_uuid)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS chat_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL,
                sender_uuid TEXT,
                sender_number TEXT NOT NULL,
                sender_name TEXT NOT NULL,
                kind INTEGER NOT NULL,
                body TEXT NOT NULL,
                image_id TEXT,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS chat_blocks (
                owner_uuid TEXT NOT NULL,
                owner_number TEXT NOT NULL,
                blocked_uuid TEXT NOT NULL,
                blocked_number TEXT NOT NULL,
                blocked_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (owner_uuid, blocked_uuid)
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_friends_owner ON chat_friends(owner_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_friends_number ON chat_friends(friend_number)",
            "CREATE INDEX IF NOT EXISTS idx_requests_to ON friend_requests(to_number, status)",
            "CREATE INDEX IF NOT EXISTS idx_requests_from ON friend_requests(from_number, to_number, status)",
            "CREATE INDEX IF NOT EXISTS idx_members_member ON chat_members(member_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_entries_conversation ON chat_entries(conversation_id, id)",
            "CREATE INDEX IF NOT EXISTS idx_entries_image ON chat_entries(image_id)"
    };

    private final Connection connection;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ChatStore(String dbPath) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA temp_store=MEMORY");
                statement.execute("PRAGMA busy_timeout=3000");
                for (String sql : SCHEMA) {
                    statement.execute(sql);
                }
            }
            migrate();
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("Failed to open chat database", exception);
        }
    }

    public void close() {
        writeVoid(() -> {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException exception) {
                BPhone.LOGGER.warn("Failed to close chat database", exception);
            }
            return null;
        });
    }

    public record Friend(String name, String number, UUID uuid) {
    }

    public record Blocked(UUID uuid, String number, String name) {
    }

    public record Request(long id, UUID fromUuid, String fromNumber, String fromName, String toNumber, long createdAt) {
    }

    public record Conversation(long id, int kind, String title, UUID owner, long createdAt, long updatedAt) {
    }

    public record Member(UUID uuid, String number, String name, long lastReadId, long clearedId, long joinedAt) {
    }

    public record Entry(long id, long conversationId, UUID senderUuid, String senderNumber, String senderName,
                        int kind, String body, String imageId, long createdAt) {
    }

    public record SearchHit(long conversationId, Entry entry) {
    }

    public List<Friend> friends(UUID owner) {
        return readList(() -> query("SELECT friend_name, friend_number, profile_id FROM chat_friends WHERE owner_uuid = ? "
                + "ORDER BY friend_name COLLATE NOCASE", statement -> statement.setString(1, owner.toString()),
                rs -> new Friend(rs.getString(1), rs.getString(2), parseUuid(rs.getString(3)))));
    }

    public Friend friend(UUID owner, String number) {
        List<Friend> found = readList(() -> query("SELECT friend_name, friend_number, profile_id FROM chat_friends "
                        + "WHERE owner_uuid = ? AND friend_number = ?",
                statement -> {
                    statement.setString(1, owner.toString());
                    statement.setString(2, number);
                },
                rs -> new Friend(rs.getString(1), rs.getString(2), parseUuid(rs.getString(3)))));
        return found.isEmpty() ? null : found.get(0);
    }

    public int friendCount(UUID owner) {
        return readInt(() -> count("SELECT COUNT(*) FROM chat_friends WHERE owner_uuid = ?",
                statement -> statement.setString(1, owner.toString())));
    }

    public int friendCountOfNumber(String number) {
        return readInt(() -> count("SELECT COUNT(*) FROM chat_friends WHERE friend_number = ?",
                statement -> statement.setString(1, number)));
    }

    public void addFriend(UUID owner, String number, String name, UUID profileId) {
        writeVoid(() -> update("INSERT OR REPLACE INTO chat_friends (owner_uuid, friend_number, friend_name, profile_id) "
                + "VALUES (?, ?, ?, ?)", statement -> {
            statement.setString(1, owner.toString());
            statement.setString(2, number);
            statement.setString(3, name);
            statement.setString(4, profileId == null ? null : profileId.toString());
        }));
    }

    public void removeFriend(UUID owner, String number) {
        writeVoid(() -> update("DELETE FROM chat_friends WHERE owner_uuid = ? AND friend_number = ?", statement -> {
            statement.setString(1, owner.toString());
            statement.setString(2, number);
        }));
    }

    public void renameFriendEverywhere(String number, String name) {
        writeVoid(() -> update("UPDATE chat_friends SET friend_name = ? WHERE friend_number = ?", statement -> {
            statement.setString(1, name);
            statement.setString(2, number);
        }));
    }

    public String nickname(UUID owner) {
        List<String> found = readList(() -> query("SELECT nickname FROM owner_nicknames WHERE owner_uuid = ?",
                statement -> statement.setString(1, owner.toString()), rs -> rs.getString(1)));
        return found.isEmpty() || found.get(0) == null ? "" : found.get(0);
    }

    public void setNickname(UUID owner, String nickname) {
        writeVoid(() -> update("INSERT OR REPLACE INTO owner_nicknames (owner_uuid, nickname) VALUES (?, ?)", statement -> {
            statement.setString(1, owner.toString());
            statement.setString(2, nickname == null ? "" : nickname);
        }));
    }

    public boolean createRequest(UUID fromUuid, String fromNumber, String fromName, String toNumber) {
        return writeBool(() -> {
            if (count("SELECT COUNT(*) FROM friend_requests WHERE from_number = ? AND to_number = ? AND status = 'pending'",
                    statement -> {
                        statement.setString(1, fromNumber);
                        statement.setString(2, toNumber);
                    }) > 0) {
                return false;
            }
            return update("INSERT INTO friend_requests (from_uuid, from_number, from_name, to_number, status, created_at) "
                    + "VALUES (?, ?, ?, ?, 'pending', ?)", statement -> {
                statement.setString(1, fromUuid.toString());
                statement.setString(2, fromNumber);
                statement.setString(3, fromName);
                statement.setString(4, toNumber);
                statement.setLong(5, System.currentTimeMillis());
            }) > 0;
        });
    }

    public List<Request> incomingRequests(String toNumber) {
        return readList(() -> query("SELECT id, from_uuid, from_number, from_name, to_number, created_at FROM friend_requests "
                        + "WHERE to_number = ? AND status = 'pending' ORDER BY created_at DESC",
                statement -> statement.setString(1, toNumber),
                rs -> new Request(rs.getLong(1), parseUuid(rs.getString(2)), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6))));
    }

    public int countIncomingPending(String toNumber) {
        return readInt(() -> count("SELECT COUNT(*) FROM friend_requests WHERE to_number = ? AND status = 'pending'",
                statement -> statement.setString(1, toNumber)));
    }

    public boolean respondRequest(long id, boolean accept) {
        return writeBool(() -> update("UPDATE friend_requests SET status = ?, responded_at = ? WHERE id = ? AND status = 'pending'",
                statement -> {
                    statement.setString(1, accept ? "accepted" : "rejected");
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setLong(3, id);
                }) > 0);
    }

    public void cancelPendingBetween(String numberA, String numberB) {
        writeVoid(() -> update("UPDATE friend_requests SET status = 'cancelled', responded_at = ? WHERE status = 'pending' "
                + "AND ((from_number = ? AND to_number = ?) OR (from_number = ? AND to_number = ?))", statement -> {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, numberA);
            statement.setString(3, numberB);
            statement.setString(4, numberB);
            statement.setString(5, numberA);
        }));
    }

    public long lastRejectedAt(String fromNumber, String toNumber) {
        List<Long> found = readList(() -> query("SELECT MAX(responded_at) FROM friend_requests WHERE from_number = ? "
                        + "AND to_number = ? AND status = 'rejected'",
                statement -> {
                    statement.setString(1, fromNumber);
                    statement.setString(2, toNumber);
                }, rs -> rs.getLong(1)));
        return found.isEmpty() ? 0L : found.get(0);
    }

    public void block(UUID owner, String ownerNumber, UUID blocked, String number, String name) {
        writeVoid(() -> update("INSERT OR REPLACE INTO chat_blocks (owner_uuid, owner_number, blocked_uuid, blocked_number, "
                + "blocked_name, created_at) VALUES (?, ?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, owner.toString());
            statement.setString(2, ownerNumber);
            statement.setString(3, blocked.toString());
            statement.setString(4, number);
            statement.setString(5, name);
            statement.setLong(6, System.currentTimeMillis());
        }));
    }

    public boolean isBlockedByNumber(String ownerNumber, UUID other) {
        if (ownerNumber == null || other == null) {
            return false;
        }
        return readInt(() -> count("SELECT COUNT(*) FROM chat_blocks WHERE owner_number = ? AND blocked_uuid = ?", statement -> {
            statement.setString(1, ownerNumber);
            statement.setString(2, other.toString());
        })) > 0;
    }

    public void unblock(UUID owner, String number) {
        writeVoid(() -> update("DELETE FROM chat_blocks WHERE owner_uuid = ? AND blocked_number = ?", statement -> {
            statement.setString(1, owner.toString());
            statement.setString(2, number);
        }));
    }

    public boolean isBlocked(UUID owner, UUID other) {
        if (owner == null || other == null) {
            return false;
        }
        return readInt(() -> count("SELECT COUNT(*) FROM chat_blocks WHERE owner_uuid = ? AND blocked_uuid = ?", statement -> {
            statement.setString(1, owner.toString());
            statement.setString(2, other.toString());
        })) > 0;
    }

    public List<Blocked> blocked(UUID owner) {
        return readList(() -> query("SELECT blocked_uuid, blocked_number, blocked_name FROM chat_blocks WHERE owner_uuid = ? "
                        + "ORDER BY created_at DESC", statement -> statement.setString(1, owner.toString()),
                rs -> new Blocked(parseUuid(rs.getString(1)), rs.getString(2), rs.getString(3))));
    }

    public long findDirect(UUID a, UUID b) {
        List<Long> found = readList(() -> query("SELECT id FROM chat_conversations WHERE direct_key = ?",
                statement -> statement.setString(1, directKey(a, b)), rs -> rs.getLong(1)));
        return found.isEmpty() ? 0L : found.get(0);
    }

    public long directConversation(UUID a, String aNumber, String aName, UUID b, String bNumber, String bName) {
        return writeLong(() -> {
            String key = directKey(a, b);
            List<Long> found = query("SELECT id FROM chat_conversations WHERE direct_key = ?",
                    statement -> statement.setString(1, key), rs -> rs.getLong(1));
            long now = System.currentTimeMillis();
            long id;
            if (found.isEmpty()) {
                id = insert("INSERT INTO chat_conversations (kind, direct_key, title, owner_uuid, created_at, updated_at) "
                        + "VALUES (?, ?, '', NULL, ?, ?)", statement -> {
                    statement.setInt(1, KIND_DIRECT);
                    statement.setString(2, key);
                    statement.setLong(3, now);
                    statement.setLong(4, now);
                });
            } else {
                id = found.get(0);
            }
            ensureMember(id, a, aNumber, aName, now);
            ensureMember(id, b, bNumber, bName, now);
            return id;
        });
    }

    public long createGroup(String title, UUID owner, List<Member> members) {
        return writeLong(() -> {
            long now = System.currentTimeMillis();
            long id = insert("INSERT INTO chat_conversations (kind, direct_key, title, owner_uuid, created_at, updated_at) "
                    + "VALUES (?, NULL, ?, ?, ?, ?)", statement -> {
                statement.setInt(1, KIND_GROUP);
                statement.setString(2, title);
                statement.setString(3, owner.toString());
                statement.setLong(4, now);
                statement.setLong(5, now);
            });
            for (Member member : members) {
                ensureMember(id, member.uuid(), member.number(), member.name(), now);
            }
            return id;
        });
    }

    public Conversation conversation(long id) {
        List<Conversation> found = readList(() -> query("SELECT id, kind, title, owner_uuid, created_at, updated_at "
                        + "FROM chat_conversations WHERE id = ?", statement -> statement.setLong(1, id),
                rs -> new Conversation(rs.getLong(1), rs.getInt(2), rs.getString(3), parseUuid(rs.getString(4)),
                        rs.getLong(5), rs.getLong(6))));
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Member> members(long conversationId) {
        return readList(() -> query("SELECT member_uuid, member_number, member_name, last_read_id, cleared_id, joined_at "
                        + "FROM chat_members WHERE conversation_id = ? ORDER BY joined_at, member_name",
                statement -> statement.setLong(1, conversationId),
                rs -> new Member(parseUuid(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6))));
    }

    public Member member(long conversationId, UUID uuid) {
        for (Member member : members(conversationId)) {
            if (member.uuid().equals(uuid)) {
                return member;
            }
        }
        return null;
    }

    public void addMember(long conversationId, UUID uuid, String number, String name) {
        writeVoid(() -> {
            ensureMember(conversationId, uuid, number, name, System.currentTimeMillis());
            return null;
        });
    }

    public void removeMember(long conversationId, UUID uuid) {
        writeVoid(() -> update("DELETE FROM chat_members WHERE conversation_id = ? AND member_uuid = ?", statement -> {
            statement.setLong(1, conversationId);
            statement.setString(2, uuid.toString());
        }));
    }

    public void renameGroup(long conversationId, String title) {
        writeVoid(() -> update("UPDATE chat_conversations SET title = ? WHERE id = ?", statement -> {
            statement.setString(1, title);
            statement.setLong(2, conversationId);
        }));
    }

    public void setOwner(long conversationId, UUID owner) {
        writeVoid(() -> update("UPDATE chat_conversations SET owner_uuid = ? WHERE id = ?", statement -> {
            statement.setString(1, owner == null ? null : owner.toString());
            statement.setLong(2, conversationId);
        }));
    }

    public void renameMemberEverywhere(UUID uuid, String name) {
        writeVoid(() -> update("UPDATE chat_members SET member_name = ? WHERE member_uuid = ?", statement -> {
            statement.setString(1, name);
            statement.setString(2, uuid.toString());
        }));
    }

    public List<String> deleteConversation(long conversationId) {
        return writeList(() -> {
            List<String> images = query("SELECT image_id FROM chat_entries WHERE conversation_id = ? AND image_id IS NOT NULL",
                    statement -> statement.setLong(1, conversationId), rs -> rs.getString(1));
            update("DELETE FROM chat_entries WHERE conversation_id = ?", statement -> statement.setLong(1, conversationId));
            update("DELETE FROM chat_members WHERE conversation_id = ?", statement -> statement.setLong(1, conversationId));
            update("DELETE FROM chat_conversations WHERE id = ?", statement -> statement.setLong(1, conversationId));
            return images;
        });
    }

    public List<Long> conversationsOf(UUID member, int limit) {
        return readList(() -> query("""
                SELECT c.id FROM chat_conversations c
                JOIN chat_members m ON m.conversation_id = c.id AND m.member_uuid = ?
                WHERE c.kind = 1 OR EXISTS (SELECT 1 FROM chat_entries e WHERE e.conversation_id = c.id AND e.id > m.cleared_id)
                ORDER BY c.updated_at DESC LIMIT ?
                """, statement -> {
            statement.setString(1, member.toString());
            statement.setInt(2, limit);
        }, rs -> rs.getLong(1)));
    }

    public int groupCount(UUID member) {
        return readInt(() -> count("SELECT COUNT(*) FROM chat_conversations c JOIN chat_members m ON m.conversation_id = c.id "
                + "WHERE c.kind = 1 AND m.member_uuid = ?", statement -> statement.setString(1, member.toString())));
    }

    public Entry addEntry(long conversationId, UUID senderUuid, String senderNumber, String senderName,
                          int kind, String body, String imageId) {
        return writeObject(() -> {
            long now = System.currentTimeMillis();
            long id = insert("INSERT INTO chat_entries (conversation_id, sender_uuid, sender_number, sender_name, kind, body, "
                    + "image_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", statement -> {
                statement.setLong(1, conversationId);
                statement.setString(2, senderUuid == null ? null : senderUuid.toString());
                statement.setString(3, senderNumber);
                statement.setString(4, senderName);
                statement.setInt(5, kind);
                statement.setString(6, body);
                statement.setString(7, imageId);
                statement.setLong(8, now);
            });
            update("UPDATE chat_conversations SET updated_at = ? WHERE id = ?", statement -> {
                statement.setLong(1, now);
                statement.setLong(2, conversationId);
            });
            if (senderUuid != null) {
                update("UPDATE chat_members SET last_read_id = ? WHERE conversation_id = ? AND member_uuid = ?", statement -> {
                    statement.setLong(1, id);
                    statement.setLong(2, conversationId);
                    statement.setString(3, senderUuid.toString());
                });
            }
            return new Entry(id, conversationId, senderUuid, senderNumber, senderName, kind, body, imageId, now);
        });
    }

    public List<Entry> latestEntries(long conversationId, long clearedId, int limit) {
        List<Entry> entries = readList(() -> query(entrySelect() + "WHERE conversation_id = ? AND id > ? ORDER BY id DESC LIMIT ?",
                statement -> {
                    statement.setLong(1, conversationId);
                    statement.setLong(2, clearedId);
                    statement.setInt(3, limit);
                }, ChatStore::readEntry));
        java.util.Collections.reverse(entries);
        return entries;
    }

    public List<Entry> entriesBefore(long conversationId, long clearedId, long beforeId, int limit) {
        List<Entry> entries = readList(() -> query(entrySelect()
                        + "WHERE conversation_id = ? AND id > ? AND id < ? ORDER BY id DESC LIMIT ?",
                statement -> {
                    statement.setLong(1, conversationId);
                    statement.setLong(2, clearedId);
                    statement.setLong(3, beforeId);
                    statement.setInt(4, limit);
                }, ChatStore::readEntry));
        java.util.Collections.reverse(entries);
        return entries;
    }

    public List<Entry> entriesFrom(long conversationId, long fromId, int limit) {
        return readList(() -> query(entrySelect() + "WHERE conversation_id = ? AND id >= ? ORDER BY id ASC LIMIT ?",
                statement -> {
                    statement.setLong(1, conversationId);
                    statement.setLong(2, fromId);
                    statement.setInt(3, limit);
                }, ChatStore::readEntry));
    }

    public boolean hasEntriesBefore(long conversationId, long clearedId, long beforeId) {
        return readInt(() -> count("SELECT COUNT(*) FROM chat_entries WHERE conversation_id = ? AND id > ? AND id < ?",
                statement -> {
                    statement.setLong(1, conversationId);
                    statement.setLong(2, clearedId);
                    statement.setLong(3, beforeId);
                })) > 0;
    }

    public long maxEntryId(long conversationId) {
        List<Long> found = readList(() -> query("SELECT COALESCE(MAX(id), 0) FROM chat_entries WHERE conversation_id = ?",
                statement -> statement.setLong(1, conversationId), rs -> rs.getLong(1)));
        return found.isEmpty() ? 0L : found.get(0);
    }

    public int unreadCount(long conversationId, UUID member, long fromId) {
        return readInt(() -> count("SELECT COUNT(*) FROM chat_entries WHERE conversation_id = ? AND id > ? "
                + "AND (sender_uuid IS NULL OR sender_uuid <> ?)", statement -> {
            statement.setLong(1, conversationId);
            statement.setLong(2, fromId);
            statement.setString(3, member.toString());
        }));
    }

    public boolean markRead(long conversationId, UUID member, long entryId) {
        return writeBool(() -> update("UPDATE chat_members SET last_read_id = ? WHERE conversation_id = ? AND member_uuid = ? "
                + "AND last_read_id < ?", statement -> {
            statement.setLong(1, entryId);
            statement.setLong(2, conversationId);
            statement.setString(3, member.toString());
            statement.setLong(4, entryId);
        }) > 0);
    }

    public void clearHistory(long conversationId, UUID member) {
        writeVoid(() -> {
            long max = query("SELECT COALESCE(MAX(id), 0) FROM chat_entries WHERE conversation_id = ?",
                    statement -> statement.setLong(1, conversationId), rs -> rs.getLong(1)).get(0);
            return update("UPDATE chat_members SET cleared_id = ?, last_read_id = MAX(last_read_id, ?) "
                    + "WHERE conversation_id = ? AND member_uuid = ?", statement -> {
                statement.setLong(1, max);
                statement.setLong(2, max);
                statement.setLong(3, conversationId);
                statement.setString(4, member.toString());
            });
        });
    }

    public List<SearchHit> search(UUID member, String text, int limit) {
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return readList(() -> query("""
                SELECT e.id, e.conversation_id, e.sender_uuid, e.sender_number, e.sender_name, e.kind, e.body, e.image_id, e.created_at
                FROM chat_entries e
                JOIN chat_members m ON m.conversation_id = e.conversation_id AND m.member_uuid = ?
                WHERE e.kind = 0 AND e.id > m.cleared_id AND e.body LIKE ? ESCAPE '\\'
                ORDER BY e.id DESC LIMIT ?
                """, statement -> {
            statement.setString(1, member.toString());
            statement.setString(2, pattern);
            statement.setInt(3, limit);
        }, rs -> {
            Entry entry = readEntry(rs);
            return new SearchHit(entry.conversationId(), entry);
        }));
    }

    public boolean canSeeImage(UUID member, String imageId) {
        return readInt(() -> count("""
                SELECT COUNT(*) FROM chat_entries e
                JOIN chat_members m ON m.conversation_id = e.conversation_id AND m.member_uuid = ?
                WHERE e.image_id = ?
                """, statement -> {
            statement.setString(1, member.toString());
            statement.setString(2, imageId);
        })) > 0;
    }

    public boolean isImageReferenced(String imageId) {
        return readInt(() -> count("SELECT COUNT(*) FROM chat_entries WHERE image_id = ?",
                statement -> statement.setString(1, imageId))) > 0;
    }

    private void ensureMember(long conversationId, UUID uuid, String number, String name, long now) throws SQLException {
        int updated = update("UPDATE chat_members SET member_number = ?, member_name = ? WHERE conversation_id = ? AND member_uuid = ?",
                statement -> {
                    statement.setString(1, number);
                    statement.setString(2, name);
                    statement.setLong(3, conversationId);
                    statement.setString(4, uuid.toString());
                });
        if (updated > 0) {
            return;
        }
        long startId = query("SELECT COALESCE(MAX(id), 0) FROM chat_entries WHERE conversation_id = ?",
                statement -> statement.setLong(1, conversationId), rs -> rs.getLong(1)).get(0);
        update("INSERT INTO chat_members (conversation_id, member_uuid, member_number, member_name, last_read_id, cleared_id, joined_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", statement -> {
            statement.setLong(1, conversationId);
            statement.setString(2, uuid.toString());
            statement.setString(3, number);
            statement.setString(4, name);
            statement.setLong(5, startId);
            statement.setLong(6, startId);
            statement.setLong(7, now);
        });
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }
        if (version >= SCHEMA_VERSION) {
            return;
        }

        boolean legacy;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'chat_messages'")) {
            legacy = rs.next();
        }

        if (legacy) {
            migrateLegacyMessages();
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    private record LegacyRow(UUID owner, String otherNumber, UUID other, String text, boolean incoming, long time, String otherName) {
    }

    private void migrateLegacyMessages() throws SQLException {
        List<LegacyRow> rows = query("SELECT owner_uuid, other_number, other_profile_id, message_text, is_incoming, timestamp, "
                        + "other_name FROM chat_messages ORDER BY timestamp, id", statement -> {
                },
                rs -> new LegacyRow(parseUuid(rs.getString(1)), rs.getString(2), parseUuid(rs.getString(3)), rs.getString(4),
                        rs.getInt(5) == 1, rs.getLong(6), rs.getString(7)));
        if (rows.isEmpty()) {
            return;
        }

        Map<UUID, String> names = new HashMap<>();
        Map<UUID, String> numbers = new HashMap<>();
        for (LegacyRow row : rows) {
            if (row.other() != null) {
                if (row.otherName() != null && !row.otherName().isBlank()) {
                    names.put(row.other(), row.otherName());
                }
                numbers.put(row.other(), row.otherNumber());
            }
        }

        Set<String> outgoingKeys = new HashSet<>();
        for (LegacyRow row : rows) {
            if (!row.incoming() && row.owner() != null && row.other() != null) {
                outgoingKeys.add(row.owner() + "|" + row.other() + "|" + row.text() + "|" + (row.time() / 60000L));
            }
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int migrated = 0;
            for (LegacyRow row : rows) {
                if (row.owner() == null || row.other() == null || row.text() == null || row.text().isBlank()) {
                    continue;
                }
                UUID sender = row.incoming() ? row.other() : row.owner();
                UUID receiver = row.incoming() ? row.owner() : row.other();
                if (row.incoming()) {
                    long minute = row.time() / 60000L;
                    String base = sender + "|" + receiver + "|" + row.text() + "|";
                    if (outgoingKeys.contains(base + minute) || outgoingKeys.contains(base + (minute - 1))
                            || outgoingKeys.contains(base + (minute + 1))) {
                        continue;
                    }
                }
                String senderNumber = numbers.getOrDefault(sender, "00000");
                String receiverNumber = numbers.getOrDefault(receiver, "00000");
                String senderName = names.getOrDefault(sender, senderNumber);
                String receiverName = names.getOrDefault(receiver, receiverNumber);
                String key = directKey(sender, receiver);
                List<Long> found = query("SELECT id FROM chat_conversations WHERE direct_key = ?",
                        statement -> statement.setString(1, key), rs -> rs.getLong(1));
                long conversationId;
                if (found.isEmpty()) {
                    conversationId = insert("INSERT INTO chat_conversations (kind, direct_key, title, owner_uuid, created_at, updated_at) "
                            + "VALUES (0, ?, '', NULL, ?, ?)", statement -> {
                        statement.setString(1, key);
                        statement.setLong(2, row.time());
                        statement.setLong(3, row.time());
                    });
                    ensureMember(conversationId, sender, senderNumber, senderName, row.time());
                    ensureMember(conversationId, receiver, receiverNumber, receiverName, row.time());
                } else {
                    conversationId = found.get(0);
                }
                long entryId = insert("INSERT INTO chat_entries (conversation_id, sender_uuid, sender_number, sender_name, kind, body, "
                        + "image_id, created_at) VALUES (?, ?, ?, ?, 0, ?, NULL, ?)", statement -> {
                    statement.setLong(1, conversationId);
                    statement.setString(2, sender.toString());
                    statement.setString(3, senderNumber);
                    statement.setString(4, senderName);
                    statement.setString(5, row.text());
                    statement.setLong(6, row.time());
                });
                update("UPDATE chat_conversations SET updated_at = MAX(updated_at, ?) WHERE id = ?", statement -> {
                    statement.setLong(1, row.time());
                    statement.setLong(2, conversationId);
                });
                update("UPDATE chat_members SET last_read_id = ? WHERE conversation_id = ?", statement -> {
                    statement.setLong(1, entryId);
                    statement.setLong(2, conversationId);
                });
                migrated++;
            }
            connection.commit();
            BPhone.LOGGER.info("[BPhone] Migrated {} legacy chat messages", migrated);
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String directKey(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    private static String entrySelect() {
        return "SELECT id, conversation_id, sender_uuid, sender_number, sender_name, kind, body, image_id, created_at FROM chat_entries ";
    }

    private static Entry readEntry(ResultSet rs) throws SQLException {
        return new Entry(rs.getLong(1), rs.getLong(2), parseUuid(rs.getString(3)), rs.getString(4), rs.getString(5),
                rs.getInt(6), rs.getString(7), rs.getString(8), rs.getLong(9));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws SQLException;
    }

    private <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        }
    }

    private int count(String sql, Binder binder) throws SQLException {
        List<Integer> result = query(sql, binder, rs -> rs.getInt(1));
        return result.isEmpty() ? 0 : result.get(0);
    }

    private int update(String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        }
    }

    private long insert(String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private <T> List<T> readList(SqlWork<List<T>> work) {
        lock.readLock().lock();
        try {
            return work.run();
        } catch (SQLException exception) {
            BPhone.LOGGER.warn("Chat database read failed", exception);
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    private int readInt(SqlWork<Integer> work) {
        lock.readLock().lock();
        try {
            Integer value = work.run();
            return value == null ? 0 : value;
        } catch (SQLException exception) {
            BPhone.LOGGER.warn("Chat database read failed", exception);
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> T writeObject(SqlWork<T> work) {
        lock.writeLock().lock();
        try {
            return work.run();
        } catch (SQLException exception) {
            BPhone.LOGGER.warn("Chat database write failed", exception);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void writeVoid(SqlWork<?> work) {
        writeObject(work);
    }

    private boolean writeBool(SqlWork<Boolean> work) {
        Boolean value = writeObject(work);
        return value != null && value;
    }

    private long writeLong(SqlWork<Long> work) {
        Long value = writeObject(work);
        return value == null ? 0L : value;
    }

    private <T> List<T> writeList(SqlWork<List<T>> work) {
        List<T> value = writeObject(work);
        return value == null ? new ArrayList<>() : value;
    }
}