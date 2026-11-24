package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.architectury.platform.Platform;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PhoneClientPrefs {
    public static final int HUD_MODERN = 0;
    public static final int HUD_VANILLA = 1;
    public static final int HUD_HIDDEN = 2;

    private static final int[] HUD_COUNTS = {4, 6, 8, 10};
    private static final int[] HUD_FADES = {6, 10, 15, 0};
    private static final int[] HUD_OPACITIES = {50, 70, 85, 95};
    private static final int[] PANEL_OPACITIES = {85, 92, 96, 100};

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

    public static boolean mentionAlert() {
        return values().mentionAlert;
    }

    public static int chatHudMode() {
        return Mth.clamp(values().chatHudMode, HUD_MODERN, HUD_HIDDEN);
    }

    public static boolean hideVanillaChat() {
        return chatHudMode() != HUD_VANILLA;
    }

    public static boolean modernChatHud() {
        return chatHudMode() == HUD_MODERN;
    }

    public static int hudMaxMessages() {
        return Mth.clamp(values().hudMaxMessages, 2, 12);
    }

    public static int hudFadeSeconds() {
        return values().hudFadeSeconds <= 0 ? 0 : Mth.clamp(values().hudFadeSeconds, 3, 60);
    }

    public static int hudOpacityPercent() {
        return Mth.clamp(values().hudOpacityPercent, 30, 100);
    }

    public static float hudOpacity() {
        return hudOpacityPercent() / 100.0F;
    }

    public static boolean panelRightSide() {
        return values().panelRightSide;
    }

    public static int panelOpacityPercent() {
        return Mth.clamp(values().panelOpacityPercent, 75, 100);
    }

    public static float panelOpacity() {
        return panelOpacityPercent() / 100.0F;
    }

    public static boolean worldNavGuide() {
        return values().worldNavGuide;
    }

    static void setPublicChatKey(boolean value) {
        values().publicChatKey = value;
        save();
    }

    static void setCloseAfterSend(boolean value) {
        values().closeAfterSend = value;
        save();
    }

    static void setMentionAlert(boolean value) {
        values().mentionAlert = value;
        save();
    }

    static void cycleChatHudMode() {
        values().chatHudMode = (chatHudMode() + 1) % 3;
        values().hideVanillaChat = values().chatHudMode == HUD_HIDDEN;
        save();
    }

    static void setHideVanillaChat(boolean value) {
        values().hideVanillaChat = value;
        values().chatHudMode = value ? HUD_HIDDEN : HUD_MODERN;
        save();
    }

    static void cycleHudMaxMessages() {
        values().hudMaxMessages = nextInRing(HUD_COUNTS, hudMaxMessages());
        save();
    }

    static void cycleHudFadeSeconds() {
        values().hudFadeSeconds = nextInRing(HUD_FADES, hudFadeSeconds());
        save();
    }

    static void cycleHudOpacityPercent() {
        values().hudOpacityPercent = nextInRing(HUD_OPACITIES, hudOpacityPercent());
        save();
    }

    static void setPanelRightSide(boolean value) {
        values().panelRightSide = value;
        save();
    }

    static void cyclePanelOpacityPercent() {
        values().panelOpacityPercent = nextInRing(PANEL_OPACITIES, panelOpacityPercent());
        save();
    }

    static void setWorldNavGuide(boolean value) {
        values().worldNavGuide = value;
        save();
    }

    private static int nextInRing(int[] choices, int current) {
        for (int index = 0; index < choices.length; index++) {
            if (choices[index] == current) {
                return choices[(index + 1) % choices.length];
            }
        }
        return choices[0];
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
                if (!read.migratedHudMode && read.hideVanillaChat) {
                    read.chatHudMode = HUD_HIDDEN;
                }
                read.migratedHudMode = true;
                values = read;
            }
        } catch (IOException | JsonSyntaxException exception) {
            BPhone.LOGGER.warn("Unable to load phone client preferences, using defaults", exception);
            values = new Values();
        }
    }

    private static void save() {
        try {
            values().migratedHudMode = true;
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
        private boolean migratedHudMode = true;
        private boolean mentionAlert = true;
        private int chatHudMode = HUD_MODERN;
        private int hudMaxMessages = 6;
        private int hudFadeSeconds = 10;
        private int hudOpacityPercent = 70;
        private boolean panelRightSide;
        private int panelOpacityPercent = 96;
        private boolean worldNavGuide = true;
    }
}
