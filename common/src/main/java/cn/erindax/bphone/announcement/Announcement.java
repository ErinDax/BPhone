package cn.erindax.bphone.announcement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record Announcement(UUID id, String title, String body, String author, long time, long editedAt, boolean starred) {
    public static final int MAX_TITLE_LENGTH = 32;
    public static final int MAX_BODY_LENGTH = 1000;
    public static final int MAX_AUTHOR_LENGTH = 24;

    public Announcement withStarred(boolean value) {
        return new Announcement(id, title, body, author, time, editedAt, value);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(clip(title, MAX_TITLE_LENGTH), MAX_TITLE_LENGTH);
        buf.writeUtf(clip(body, MAX_BODY_LENGTH), MAX_BODY_LENGTH);
        buf.writeUtf(clip(author, MAX_AUTHOR_LENGTH), MAX_AUTHOR_LENGTH);
        buf.writeLong(time);
        buf.writeLong(editedAt);
        buf.writeBoolean(starred);
    }

    public static Announcement read(FriendlyByteBuf buf) {
        return new Announcement(buf.readUUID(), buf.readUtf(MAX_TITLE_LENGTH), buf.readUtf(MAX_BODY_LENGTH),
                buf.readUtf(MAX_AUTHOR_LENGTH), buf.readLong(), buf.readLong(), buf.readBoolean());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("title", title);
        tag.putString("body", body);
        tag.putString("author", author);
        tag.putLong("time", time);
        tag.putLong("edited", editedAt);
        tag.putBoolean("starred", starred);
        return tag;
    }

    public static Announcement load(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        return new Announcement(id, clip(tag.getString("title"), MAX_TITLE_LENGTH), clip(tag.getString("body"), MAX_BODY_LENGTH),
                clip(tag.getString("author"), MAX_AUTHOR_LENGTH), tag.getLong("time"), tag.getLong("edited"),
                tag.getBoolean("starred"));
    }

    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
