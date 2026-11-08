package cn.erindax.bphone.voice;

import dev.architectury.platform.Platform;

public final class VoiceProviderDetector {

    private VoiceProviderDetector() {
    }

    public static VoiceProvider detect() {
        if (Platform.isModLoaded("plasmovoice")) {
            return VoiceProvider.PLASMO_VOICE;
        }

        if (Platform.isModLoaded("voicechat")) {
            return VoiceProvider.SIMPLE_VOICE_CHAT;
        }

        return VoiceProvider.NONE;
    }

    public static boolean isPlasmoVoice() {
        return Platform.isModLoaded("plasmovoice");
    }

    public static boolean isSimpleVoiceChat() {
        return Platform.isModLoaded("voicechat");
    }
}
