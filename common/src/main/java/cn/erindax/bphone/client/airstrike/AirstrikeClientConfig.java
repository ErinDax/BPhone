package cn.erindax.bphone.client.airstrike;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModSounds;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AirstrikeClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve("beat-client.json");

    private static ConfigData configData = new ConfigData();
    private static boolean loaded;

    private AirstrikeClientConfig() {
    }

    public static void init() {
        if (loaded) {
            return;
        }

        loaded = true;
        load();
    }

    public static SoundEvent getTargetLockedSound(Minecraft minecraft) {
        VoiceLanguage voiceLanguage = VoiceLanguage.fromSerializedName(configData.airstrikeRadioVoiceLanguage);
        return voiceLanguage.resolve(minecraft) == VoiceLanguage.TH_TH
                ? ModSounds.AIRSTRIKE_TARGET_LOCKED_TH_TH.get()
                : ModSounds.AIRSTRIKE_TARGET_LOCKED_EN_US.get();
    }

    private static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }

            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            ConfigData loadedData = GSON.fromJson(json, ConfigData.class);
            if (loadedData != null) {
                configData = loadedData;
            }
        } catch (IOException | JsonSyntaxException exception) {
            BPhone.LOGGER.warn("Unable to load BEAT client config, using defaults", exception);
            configData = new ConfigData();
            save();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(configData), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BPhone.LOGGER.warn("Unable to save BEAT client config", exception);
        }
    }

    private enum VoiceLanguage {
        AUTO("auto"),
        EN_US("en_us"),
        TH_TH("th_th");

        private final String serializedName;

        VoiceLanguage(String serializedName) {
            this.serializedName = serializedName;
        }

        private static VoiceLanguage fromSerializedName(String serializedName) {
            if (serializedName != null) {
                for (VoiceLanguage value : values()) {
                    if (value.serializedName.equalsIgnoreCase(serializedName)) {
                        return value;
                    }
                }
            }

            return AUTO;
        }

        private VoiceLanguage resolve(Minecraft minecraft) {
            if (this != AUTO || minecraft == null) {
                return this == AUTO ? EN_US : this;
            }

            String languageCode = minecraft.getLanguageManager().getSelected();
            return languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("th_")
                    ? TH_TH
                    : EN_US;
        }
    }

    private static final class ConfigData {
        private String airstrikeRadioVoiceLanguage = "auto";
    }
}
