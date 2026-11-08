package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class PhoneRingtones {
    private static SoundInstance preview;

    private PhoneRingtones() {
    }

    static SoundEvent sound(String id) {
        return switch (id == null ? "" : id) {
            case "classic" -> ModSounds.HOME_PHONE_RING.get();
            case "bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "chime" -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case "xylophone" -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case "amethyst" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            default -> ModSounds.PHONE_RING.get();
        };
    }

    static int loopDelay(String id) {
        return switch (id == null ? "" : id) {
            case "bell", "chime" -> 8;
            case "xylophone" -> 5;
            case "amethyst" -> 10;
            default -> 0;
        };
    }

    static Component name(String id) {
        return Component.translatable("screen.bphone.phone.settings.ringtone." + id);
    }

    static void preview(String id) {
        stopPreview();
        Minecraft minecraft = Minecraft.getInstance();
        preview = SimpleSoundInstance.forUI(sound(id), 1.0F, 0.8F);
        minecraft.getSoundManager().play(preview);
    }

    static void stopPreview() {
        if (preview != null) {
            Minecraft.getInstance().getSoundManager().stop(preview);
            preview = null;
        }
    }
}
