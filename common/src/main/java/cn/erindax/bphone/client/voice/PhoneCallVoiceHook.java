package cn.erindax.bphone.client.voice;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.client.voice.filter.PhoneCallFilter;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.audio.filter.AudioFilter;

import java.util.Optional;

public final class PhoneCallVoiceHook {
    private static final PhoneCallFilter FILTER = new PhoneCallFilter();

    private static PhoneCallAddon addon;
    private static PlasmoVoiceClient voiceClient;
    private static InputDevice attachedDevice;
    private static boolean addonLoaded;

    private PhoneCallVoiceHook() {
    }

    public static void init() {
        if (addonLoaded) {
            return;
        }

        try {
            addon = new PhoneCallAddon();
            PlasmoVoiceClient.getAddonsLoader().load(addon);
            addonLoaded = true;
        } catch (Throwable throwable) {
            addon = null;
            addonLoaded = false;
            BPhone.LOGGER.warn("Unable to initialize Plasmo Voice phone call addon", throwable);
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
            BPhone.LOGGER.debug("Unable to access Plasmo Voice input device for phone calls", throwable);
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
            BPhone.LOGGER.debug("Failed to remove phone call filter", throwable);
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
            BPhone.LOGGER.debug("Failed to unload Plasmo Voice phone call addon", throwable);
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
            BPhone.LOGGER.debug("Failed to attach phone call filter", throwable);
            return false;
        }
    }

    @Addon(
            id = "beat_phone_call",
            name = "BEAT Phone Call",
            scope = AddonLoaderScope.CLIENT,
            version = "1.1.1",
            authors = {"CatLab Design"}
    )
    private static final class PhoneCallAddon implements AddonInitializer {
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
