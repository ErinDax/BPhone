package cn.erindax.bphone.phone;

import cn.erindax.bphone.item.ItemNbt;

import cn.erindax.bphone.BPhone;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PhoneWallpaperData {

    public static final String WALLPAPER_TAG = "wallpaper";
    public static final String LOCK_WALLPAPER_TAG = "lock_wallpaper";
    public static final String HOME_WALLPAPER_TAG = "home_wallpaper";

    public static final String BUILTIN_PREFIX = "builtin:";
    public static final String PHOTO_PREFIX = "photo:";

    public static final String DEFAULT_WALLPAPER = BUILTIN_PREFIX + "default";

    public static final String[] BUILTIN_WALLPAPERS = {
            "default",
            "wallpaper1",
            "wallpaper2"
    };

    private PhoneWallpaperData() {
    }

    public static String getLockWallpaper(ItemStack phoneStack) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack)) {
            return DEFAULT_WALLPAPER;
        }
        CompoundTag tag = ItemNbt.getTag(phoneStack);
        if (tag == null || !tag.contains(WALLPAPER_TAG)) {
            return DEFAULT_WALLPAPER;
        }
        CompoundTag wallpaperTag = tag.getCompound(WALLPAPER_TAG);
        String value = wallpaperTag.getString(LOCK_WALLPAPER_TAG);
        return value.isEmpty() ? DEFAULT_WALLPAPER : value;
    }

    public static String getHomeWallpaper(ItemStack phoneStack) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack)) {
            return DEFAULT_WALLPAPER;
        }
        CompoundTag tag = ItemNbt.getTag(phoneStack);
        if (tag == null || !tag.contains(WALLPAPER_TAG)) {
            return DEFAULT_WALLPAPER;
        }
        CompoundTag wallpaperTag = tag.getCompound(WALLPAPER_TAG);
        String value = wallpaperTag.getString(HOME_WALLPAPER_TAG);
        return value.isEmpty() ? DEFAULT_WALLPAPER : value;
    }

    public static void setLockWallpaper(ItemStack phoneStack, String wallpaper) {
        setWallpaper(phoneStack, LOCK_WALLPAPER_TAG, wallpaper);
    }

    public static void setHomeWallpaper(ItemStack phoneStack, String wallpaper) {
        setWallpaper(phoneStack, HOME_WALLPAPER_TAG, wallpaper);
    }

    private static void setWallpaper(ItemStack phoneStack, String slotTag, String wallpaper) {
        if (phoneStack.isEmpty()) return;
        CompoundTag tag = ItemNbt.copyTag(phoneStack);
        CompoundTag wallpaperTag = tag.contains(WALLPAPER_TAG)
                ? tag.getCompound(WALLPAPER_TAG)
                : new CompoundTag();
        wallpaperTag.putString(slotTag, wallpaper == null ? DEFAULT_WALLPAPER : wallpaper);
        tag.put(WALLPAPER_TAG, wallpaperTag);
        ItemNbt.setTag(phoneStack, tag);
    }

    public static void clearPhotoWallpaper(CompoundTag phoneTag, String photoFileName) {
        if (phoneTag == null || photoFileName == null || photoFileName.isBlank()
                || !phoneTag.contains(WALLPAPER_TAG)) {
            return;
        }

        String key = photoKey(photoFileName);
        CompoundTag wallpaperTag = phoneTag.getCompound(WALLPAPER_TAG);
        if (key.equals(wallpaperTag.getString(LOCK_WALLPAPER_TAG))) {
            wallpaperTag.remove(LOCK_WALLPAPER_TAG);
        }
        if (key.equals(wallpaperTag.getString(HOME_WALLPAPER_TAG))) {
            wallpaperTag.remove(HOME_WALLPAPER_TAG);
        }
        if (wallpaperTag.isEmpty()) {
            phoneTag.remove(WALLPAPER_TAG);
        } else {
            phoneTag.put(WALLPAPER_TAG, wallpaperTag);
        }
    }

    public static boolean isValidWallpaper(ItemStack phoneStack, String wallpaper) {
        if (isBuiltin(wallpaper)) {
            String name = getBuiltinName(wallpaper);
            for (String builtin : BUILTIN_WALLPAPERS) {
                if (builtin.equals(name)) {
                    return true;
                }
            }
            return false;
        }

        return isPhoto(wallpaper) && PhonePhotoData.hasPhoto(phoneStack, getPhotoFileName(wallpaper));
    }

    public static void setBothWallpapers(ItemStack phoneStack, String wallpaper) {
        setLockWallpaper(phoneStack, wallpaper);
        setHomeWallpaper(phoneStack, wallpaper);
    }

    public static boolean isBuiltin(String wallpaper) {
        return wallpaper != null && wallpaper.startsWith(BUILTIN_PREFIX);
    }

    public static boolean isPhoto(String wallpaper) {
        return wallpaper != null && wallpaper.startsWith(PHOTO_PREFIX);
    }

    public static String getBuiltinName(String wallpaper) {
        if (!isBuiltin(wallpaper)) return null;
        return wallpaper.substring(BUILTIN_PREFIX.length());
    }

    public static String getPhotoFileName(String wallpaper) {
        if (!isPhoto(wallpaper)) return null;
        return wallpaper.substring(PHOTO_PREFIX.length());
    }

    public static String builtinKey(int index) {
        if (index < 0 || index >= BUILTIN_WALLPAPERS.length) return DEFAULT_WALLPAPER;
        return BUILTIN_PREFIX + BUILTIN_WALLPAPERS[index];
    }

    public static String photoKey(String fileName) {
        return PHOTO_PREFIX + fileName;
    }

    public static ResourceLocation resolveBuiltinTexture(String wallpaper) {
        String name = getBuiltinName(wallpaper);
        if (name == null || name.isEmpty()) {
            name = "default";
        }
        return ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "textures/gui/wallpaper/" + name + ".png");
    }
}
