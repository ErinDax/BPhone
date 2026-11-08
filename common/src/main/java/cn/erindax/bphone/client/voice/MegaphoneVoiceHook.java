package cn.erindax.bphone.client.voice;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.client.voice.filter.MegaphoneFilter;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.audio.filter.AudioFilter;

import java.util.Optional;

public final class MegaphoneVoiceHook {
    private static final MegaphoneFilter FILTER = new MegaphoneFilter();

    private static MegaphoneAddon addon;
    private static PlasmoVoiceClient voiceClient;
    private static InputDevice attachedDevice;
    private static boolean addonLoaded;

    private MegaphoneVoiceHook() {
    }

    public static void init() {
        if (addonLoaded) {
            return;
        }

        try {
            addon = new MegaphoneAddon();
            PlasmoVoiceClient.getAddonsLoader().load(addon);
            addonLoaded = true;
        } catch (Throwable throwable) {
            addon = null;
            addonLoaded = false;
            BPhone.LOGGER.warn("Unable to initialize Plasmo Voice megaphone addon", throwable);
        }
    }

    public static void tick(boolean shouldEnable) {
        if (!addonLoaded || voiceClient == null) {
            detach();
            return;
        }

        Optional<InputDevice> deviceOptional;
        try {
            deviceOptional = voiceClient.getDeviceManager().getInputDevice();
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to access Plasmo Voice input device for megaphone", throwable);
            detach();
            return;
        }

        if (deviceOptional.isEmpty()) {
            detach();
            return;
        }

        InputDevice currentDevice = deviceOptional.get();
        if (shouldEnable) {
            attach(currentDevice);
        } else {
            detach();
        }
    }

    public static void detach() {
        if (attachedDevice == null) {
            return;
        }

        try {
            attachedDevice.removeFilter(FILTER);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Failed to remove megaphone filter", throwable);
        } finally {
            attachedDevice = null;
        }
    }

    public static void shutdown() {
        detach();
        voiceClient = null;

        if (!addonLoaded || addon == null) {
            return;
        }

        try {
            PlasmoVoiceClient.getAddonsLoader().unload(addon);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Failed to unload Plasmo Voice megaphone addon", throwable);
        } finally {
            addonLoaded = false;
            addon = null;
        }
    }

    private static void attach(InputDevice device) {
        if (attachedDevice == device) {
            ensureFilterPresent(device);
            return;
        }

        detach();
        if (ensureFilterPresent(device)) {
            attachedDevice = device;
        }
    }

    private static boolean ensureFilterPresent(InputDevice device) {
        try {
            if (!device.getFilters().contains(FILTER)) {
                device.addFilter(FILTER, AudioFilter.Priority.HIGH);
            }
            return device.getFilters().contains(FILTER);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Failed to attach megaphone filter", throwable);
            return false;
        }
    }

    @Addon(
            id = "beat_megaphone",
            name = "BEAT Megaphone",
            scope = AddonLoaderScope.CLIENT,
            version = "1.1.1",
            authors = {"CatLab Design"}
    )
    private static final class MegaphoneAddon implements AddonInitializer {
        @InjectPlasmoVoice
        private PlasmoVoiceClient client;

        @Override
        public void onAddonInitialize() {
            voiceClient = client;
            client.getAddonConfig(this);
        }

        @Override
        public void onAddonShutdown() {
            detach();
            voiceClient = null;
        }
    }
}
