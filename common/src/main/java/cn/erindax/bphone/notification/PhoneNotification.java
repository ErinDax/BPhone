package cn.erindax.bphone.notification;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record PhoneNotification(UUID id, long time, String source, String title, String body, boolean read) {

    public static final int MAX_SOURCE_LENGTH = 24;
    public static final int MAX_TITLE_LENGTH = 48;
    public static final int MAX_BODY_LENGTH = 256;

    public static PhoneNotification create(String source, String title, String body) {
        return new PhoneNotification(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                trim(source, MAX_SOURCE_LENGTH),
                trim(title, MAX_TITLE_LENGTH),
                trim(body, MAX_BODY_LENGTH),
                false);
    }

    public PhoneNotification asRead() {
        return new PhoneNotification(id, time, source, title, body, true);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putLong("time", time);
        tag.putString("source", source);
        tag.putString("title", title);
        tag.putString("body", body);
        tag.putBoolean("read", read);
        return tag;
    }

    public static PhoneNotification load(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        String source = tag.contains("source") ? tag.getString("source") : "";
        String title = tag.contains("title") ? tag.getString("title") : "";
        String body = tag.contains("body") ? tag.getString("body") : "";
        return new PhoneNotification(id, tag.getLong("time"), source, title, body, tag.getBoolean("read"));
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
