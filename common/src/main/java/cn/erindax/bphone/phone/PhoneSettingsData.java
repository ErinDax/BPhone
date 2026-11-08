package cn.erindax.bphone.phone;

import cn.erindax.bphone.item.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PhoneSettingsData {
    public static final String SETTINGS_TAG = "settings";
    public static final String KEY_SILENT = "silent";
    public static final String KEY_MESSAGE_POPUP = "message_popup";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_RINGTONE = "ringtone";
    public static final String KEY_LOCK_WALLPAPER = "lock_wallpaper";
    public static final String KEY_HOME_WALLPAPER = "home_wallpaper";
    public static final List<String> RINGTONES = List.of("default", "classic", "bell", "chime", "xylophone", "amethyst");
    public static final int MAX_KEY_LENGTH = 32;
    public static final int MAX_VALUE_LENGTH = PhonePhotoData.MAX_PHOTO_FILE_NAME_LENGTH + 16;

    private PhoneSettingsData() {
    }

    public static boolean isSilent(ItemStack phoneStack) {
        return getFlag(phoneStack, KEY_SILENT, false);
    }

    public static boolean isMessagePopupEnabled(ItemStack phoneStack) {
        return getFlag(phoneStack, KEY_MESSAGE_POPUP, true);
    }

    public static boolean isDarkMode(ItemStack phoneStack) {
        return getFlag(phoneStack, KEY_DARK_MODE, false);
    }

    public static String getRingtone(ItemStack phoneStack) {
        CompoundTag tag = phoneStack == null ? null : ItemNbt.getTag(phoneStack);
        if (tag == null || !tag.contains(SETTINGS_TAG, Tag.TAG_COMPOUND)) {
            return RINGTONES.get(0);
        }
        String value = tag.getCompound(SETTINGS_TAG).getString(KEY_RINGTONE);
        return RINGTONES.contains(value) ? value : RINGTONES.get(0);
    }

    public static boolean getFlag(ItemStack phoneStack, String key, boolean fallback) {
        CompoundTag tag = phoneStack == null ? null : ItemNbt.getTag(phoneStack);
        if (tag == null || !tag.contains(SETTINGS_TAG, Tag.TAG_COMPOUND)) {
            return fallback;
        }

        CompoundTag settings = tag.getCompound(SETTINGS_TAG);
        return settings.contains(key, Tag.TAG_BYTE) ? settings.getBoolean(key) : fallback;
    }

    public static String flagValue(boolean enabled) {
        return enabled ? "1" : "0";
    }

    public static boolean apply(ItemStack phoneStack, String key, String value) {
        if (phoneStack == null || phoneStack.isEmpty() || key == null || value == null) {
            return false;
        }

        switch (key) {
            case KEY_SILENT, KEY_MESSAGE_POPUP, KEY_DARK_MODE -> {
                if (!"1".equals(value) && !"0".equals(value)) {
                    return false;
                }
                CompoundTag tag = ItemNbt.copyTag(phoneStack);
                CompoundTag settings = tag.getCompound(SETTINGS_TAG);
                settings.putBoolean(key, "1".equals(value));
                tag.put(SETTINGS_TAG, settings);
                ItemNbt.setTag(phoneStack, tag);
                return true;
            }
            case KEY_RINGTONE -> {
                if (!RINGTONES.contains(value)) {
                    return false;
                }
                CompoundTag tag = ItemNbt.copyTag(phoneStack);
                CompoundTag settings = tag.getCompound(SETTINGS_TAG);
                settings.putString(key, value);
                tag.put(SETTINGS_TAG, settings);
                ItemNbt.setTag(phoneStack, tag);
                return true;
            }
            case KEY_LOCK_WALLPAPER -> {
                if (!PhoneWallpaperData.isValidWallpaper(phoneStack, value)) {
                    return false;
                }
                PhoneWallpaperData.setLockWallpaper(phoneStack, value);
                return true;
            }
            case KEY_HOME_WALLPAPER -> {
                if (!PhoneWallpaperData.isValidWallpaper(phoneStack, value)) {
                    return false;
                }
                PhoneWallpaperData.setHomeWallpaper(phoneStack, value);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
