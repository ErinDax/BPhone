package cn.erindax.bphone.phone;

import cn.erindax.bphone.item.ItemNbt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PhonePhotoData {
    public static final int MAX_PHOTOS = 302;
    public static final int MAX_PHOTO_FILE_NAME_LENGTH = 128;
    public static final String PHOTOS_TAG = "photos";
    public static final String PHOTO_FILE_TAG = "file";
    public static final String PHOTO_ITEM_TAG = "item";
    public static final String PHOTO_COUNT_TAG = "count";
    public static final String PHOTO_NAME_TAG = "name";
    public static final String PHOTO_TIME_TAG = "time";

    private PhonePhotoData() {
    }

    public static int getPhotoCount(ItemStack phoneStack) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack)) {
            return 0;
        }

        CompoundTag phoneTag = ItemNbt.getTag(phoneStack);
        if (phoneTag == null || !phoneTag.contains(PHOTOS_TAG, Tag.TAG_LIST)) {
            return 0;
        }

        return phoneTag.getList(PHOTOS_TAG, Tag.TAG_COMPOUND).size();
    }

    public static boolean isPhotoLimitReached(ItemStack phoneStack) {
        return getPhotoCount(phoneStack) >= MAX_PHOTOS;
    }

    public static boolean hasPhoto(ItemStack phoneStack, String photoFileName) {
        if (phoneStack.isEmpty() || photoFileName == null || photoFileName.isBlank() || !ItemNbt.hasTag(phoneStack)) {
            return false;
        }

        CompoundTag phoneTag = ItemNbt.getTag(phoneStack);
        if (phoneTag == null || !phoneTag.contains(PHOTOS_TAG, Tag.TAG_LIST)) {
            return false;
        }

        ListTag photosTag = phoneTag.getList(PHOTOS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < photosTag.size(); index++) {
            if (photoFileName.equals(photosTag.getCompound(index).getString(PHOTO_FILE_TAG))) {
                return true;
            }
        }
        return false;
    }

    public static boolean appendPhoto(ItemStack phoneStack, String photoFileName, ItemStack captureStack) {
        if (phoneStack.isEmpty() || photoFileName == null || photoFileName.isBlank()) {
            return false;
        }

        CompoundTag phoneTag = ItemNbt.copyTag(phoneStack);
        ListTag photos = phoneTag.contains(PHOTOS_TAG, Tag.TAG_LIST)
                ? phoneTag.getList(PHOTOS_TAG, Tag.TAG_COMPOUND)
                : new ListTag();

        if (photos.size() >= MAX_PHOTOS) {
            return false;
        }

        CompoundTag photoTag = new CompoundTag();
        photoTag.putString(PHOTO_FILE_TAG, photoFileName);
        photoTag.putLong(PHOTO_TIME_TAG, System.currentTimeMillis());

        if (captureStack != null && !captureStack.isEmpty()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(captureStack.getItem());
            photoTag.putString(PHOTO_ITEM_TAG, itemId.toString());
            photoTag.putInt(PHOTO_COUNT_TAG, Math.max(1, captureStack.getCount()));

            if (captureStack.has(DataComponents.CUSTOM_NAME)) {
                photoTag.putString(PHOTO_NAME_TAG, captureStack.getHoverName().getString());
            }
        }

        photos.add(photoTag);
        phoneTag.put(PHOTOS_TAG, photos);
        ItemNbt.setTag(phoneStack, phoneTag);
        return true;
    }

    public static String removePhotoAt(ItemStack phoneStack, int viewerIndex, String expectedFileName) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack)) {
            return null;
        }

        CompoundTag phoneTag = ItemNbt.copyTag(phoneStack);
        if (!phoneTag.contains(PHOTOS_TAG, Tag.TAG_LIST)) {
            return null;
        }

        ListTag photosTag = phoneTag.getList(PHOTOS_TAG, Tag.TAG_COMPOUND);
        if (photosTag.isEmpty()) {
            return null;
        }

        int nbtIndex = photosTag.size() - 1 - viewerIndex;
        if (nbtIndex < 0 || nbtIndex >= photosTag.size()) {
            return null;
        }

        CompoundTag removedPhoto = photosTag.getCompound(nbtIndex);
        String removedFileName = removedPhoto.getString(PHOTO_FILE_TAG);
        photosTag.remove(nbtIndex);
        updatePhotoListTag(phoneTag, photosTag);
        String fileName = removedFileName.isEmpty() ? expectedFileName : removedFileName;
        PhoneWallpaperData.clearPhotoWallpaper(phoneTag, fileName);
        ItemNbt.setTag(phoneStack, phoneTag);
        return fileName;
    }

    public static boolean removePhotoByFileName(ItemStack phoneStack, String expectedFileName) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack) || expectedFileName == null || expectedFileName.isBlank()) {
            return false;
        }

        CompoundTag phoneTag = ItemNbt.copyTag(phoneStack);
        if (!phoneTag.contains(PHOTOS_TAG, Tag.TAG_LIST)) {
            return false;
        }

        ListTag photosTag = phoneTag.getList(PHOTOS_TAG, Tag.TAG_COMPOUND);
        for (int index = photosTag.size() - 1; index >= 0; index--) {
            if (expectedFileName.equals(photosTag.getCompound(index).getString(PHOTO_FILE_TAG))) {
                photosTag.remove(index);
                updatePhotoListTag(phoneTag, photosTag);
                PhoneWallpaperData.clearPhotoWallpaper(phoneTag, expectedFileName);
                ItemNbt.setTag(phoneStack, phoneTag);
                return true;
            }
        }

        return false;
    }

    private static void updatePhotoListTag(CompoundTag phoneTag, ListTag photosTag) {
        if (photosTag.isEmpty()) {
            phoneTag.remove(PHOTOS_TAG);
        } else {
            phoneTag.put(PHOTOS_TAG, photosTag);
        }
    }
}
