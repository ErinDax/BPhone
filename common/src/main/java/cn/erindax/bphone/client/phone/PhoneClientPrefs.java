package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PhoneClientPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve("bphone-phone-client.json");

    private static Values values = new Values();
    private static boolean loaded;

    private PhoneClientPrefs() {
    }

    public static boolean publicChatKey() {
        return values().publicChatKey;
    }

    public static boolean closeAfterSend() {
        return values().closeAfterSend;
    }

    public static boolean hideVanillaChat() {
        return values().hideVanillaChat;
    }

    static void setPublicChatKey(boolean value) {
        values().publicChatKey = value;
        save();
    }

    static void setCloseAfterSend(boolean value) {
        values().closeAfterSend = value;
        save();
    }

    static void setHideVanillaChat(boolean value) {
        values().hideVanillaChat = value;
        save();
    }

    private static Values values() {
        if (!loaded) {
            loaded = true;
            load();
        }
        return values;
    }

    private static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                return;
            }
            Values read = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), Values.class);
            if (read != null) {
                values = read;
            }
        } catch (IOException | JsonSyntaxException exception) {
            BPhone.LOGGER.warn("Unable to load phone client preferences, using defaults", exception);
            values = new Values();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(values), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BPhone.LOGGER.warn("Unable to save phone client preferences", exception);
        }
    }

    private static final class Values {
        private boolean publicChatKey = true;
        private boolean closeAfterSend;
        private boolean hideVanillaChat;
    }
}
