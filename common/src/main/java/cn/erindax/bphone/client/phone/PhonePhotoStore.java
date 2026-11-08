package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.item.ItemNbt;

import com.mojang.blaze3d.platform.NativeImage;
import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhonePhotoData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PhonePhotoStore {
    private static final int PHOTO_PREVIEW_SIZE = 200;
    private static final String PHOTO_DIRECTORY_NAME = "beat_phone";

    private final String modId;
    private final Map<String, PhotoTexture> previewPhotoTextures = new HashMap<>();
    private final Map<String, PhotoTexture> fullPhotoTextures = new HashMap<>();

    PhonePhotoStore(String modId) {
        this.modId = Objects.requireNonNull(modId, "modId");
    }

    int maxPhotos() {
        return PhonePhotoData.MAX_PHOTOS;
    }

    int getPhotoCount(ItemStack phoneStack) {
        return PhonePhotoData.getPhotoCount(phoneStack);
    }

    boolean isPhotoLimitReached(ItemStack phoneStack) {
        return PhonePhotoData.isPhotoLimitReached(phoneStack);
    }

    String writePhoto(NativeImage image) {
        if (image == null) {
            return null;
        }

        try {
            Path photoDirectory = getPhotoDirectory();
            Files.createDirectories(photoDirectory);
            String fileName = "phone_" + System.currentTimeMillis() + ".png";
            Path photoPath = photoDirectory.resolve(fileName);
            image.writeToFile(photoPath.toFile());
            return fileName;
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Failed to write phone photo", exception);
            return null;
        }
    }

    boolean appendPhotoToStack(ItemStack phoneStack, String photoFileName, ItemStack captureStack) {
        return PhonePhotoData.appendPhoto(phoneStack, photoFileName, captureStack);
    }

    boolean removePhotoFromStack(ItemStack phoneStack, int viewerIndex, String expectedFileName) {
        String fileNameToDelete = PhonePhotoData.removePhotoAt(phoneStack, viewerIndex, expectedFileName);
        if (fileNameToDelete == null) {
            return false;
        }

        unloadTexture(fileNameToDelete);
        deletePhotoFile(fileNameToDelete);
        return true;
    }

    List<PhotoEntry> getPhotos(ItemStack phoneStack) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack)) {
            return List.of();
        }

        CompoundTag phoneTag = ItemNbt.getTag(phoneStack);
        if (phoneTag == null || !phoneTag.contains(PhonePhotoData.PHOTOS_TAG, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag photosTag = phoneTag.getList(PhonePhotoData.PHOTOS_TAG, Tag.TAG_COMPOUND);
        if (photosTag.isEmpty()) {
            return List.of();
        }

        List<PhotoEntry> photos = new ArrayList<>(photosTag.size());
        for (int index = photosTag.size() - 1; index >= 0; index--) {
            CompoundTag photoTag = photosTag.getCompound(index);
            String fileName = photoTag.contains(PhonePhotoData.PHOTO_FILE_TAG, Tag.TAG_STRING)
                    ? photoTag.getString(PhonePhotoData.PHOTO_FILE_TAG)
                    : "";
            photos.add(new PhotoEntry(fileName, readPhotoItemStack(photoTag)));
        }

        return photos;
    }

    PhotoTexture getOrLoadPhotoTexture(String fileName, boolean previewMode) {
        if (fileName.isEmpty()) {
            return null;
        }

        Map<String, PhotoTexture> textureCache = previewMode ? previewPhotoTextures : fullPhotoTextures;
        PhotoTexture cached = textureCache.get(fileName);
        if (cached != null) {
            return cached;
        }

        Path filePath = getPhotoDirectory().resolve(fileName);
        if (!Files.exists(filePath)) {
            return null;
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            NativeImage sourceImage = NativeImage.read(inputStream);
            DynamicTexture texture;
            int textureWidth;
            int textureHeight;

            if (previewMode) {
                NativeImage previewImage = null;
                try {
                    previewImage = resizeToSquare(sourceImage, PHOTO_PREVIEW_SIZE);
                    texture = new DynamicTexture(previewImage);
                    previewImage = null;
                } finally {
                    sourceImage.close();
                    if (previewImage != null) {
                        previewImage.close();
                    }
                }
                textureWidth = PHOTO_PREVIEW_SIZE;
                textureHeight = PHOTO_PREVIEW_SIZE;
            } else {
                textureWidth = sourceImage.getWidth();
                textureHeight = sourceImage.getHeight();
                texture = new DynamicTexture(sourceImage);
            }

            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(
                    modId,
                    (previewMode ? "phone_photo/preview_" : "phone_photo/full_")
                            + Integer.toUnsignedString(fileName.hashCode())
            );
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);

            PhotoTexture photoTexture = new PhotoTexture(textureId, textureWidth, textureHeight);
            textureCache.put(fileName, photoTexture);
            return photoTexture;
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Failed to load phone photo texture: {}", fileName, exception);
            return null;
        }
    }

    void releaseTextures() {
        releaseTextureMap(previewPhotoTextures);
        releaseTextureMap(fullPhotoTextures);
    }

    void unloadTexture(String fileName) {
        if (fileName.isEmpty()) {
            return;
        }

        releaseTexture(fileName, previewPhotoTextures);
        releaseTexture(fileName, fullPhotoTextures);
    }

    void deletePhotoFile(String fileName) {
        if (fileName.isEmpty()) {
            return;
        }

        try {
            Files.deleteIfExists(getPhotoDirectory().resolve(fileName));
        } catch (IOException ignored) {
        }
    }

    private ItemStack readPhotoItemStack(CompoundTag photoTag) {
        String itemIdRaw = photoTag.getString(PhonePhotoData.PHOTO_ITEM_TAG);
        if (itemIdRaw.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(itemIdRaw);
        if (itemId == null) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        int count = Math.max(1, photoTag.getInt(PhonePhotoData.PHOTO_COUNT_TAG));
        ItemStack stack = new ItemStack(item, count);
        if (photoTag.contains(PhonePhotoData.PHOTO_NAME_TAG, Tag.TAG_STRING)) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(photoTag.getString(PhonePhotoData.PHOTO_NAME_TAG)));
        }
        return stack;
    }

    private NativeImage resizeToSquare(NativeImage sourceImage, int size) {
        NativeImage resized = new NativeImage(size, size, false);
        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        int cropSize = Math.min(sourceWidth, sourceHeight);
        if (cropSize <= 0) {
            return resized;
        }

        int cropX = (sourceWidth - cropSize) / 2;
        int cropY = (sourceHeight - cropSize) / 2;

        for (int y = 0; y < size; y++) {
            int sourceY = cropY + Math.min(cropSize - 1, (int) ((long) y * cropSize / size));
            for (int x = 0; x < size; x++) {
                int sourceX = cropX + Math.min(cropSize - 1, (int) ((long) x * cropSize / size));
                resized.setPixelRGBA(x, y, sourceImage.getPixelRGBA(sourceX, sourceY));
            }
        }
        return resized;
    }

    private void releaseTextureMap(Map<String, PhotoTexture> textureMap) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            textureMap.clear();
            return;
        }

        for (PhotoTexture photoTexture : textureMap.values()) {
            minecraft.getTextureManager().release(photoTexture.textureId);
        }
        textureMap.clear();
    }

    private void releaseTexture(String fileName, Map<String, PhotoTexture> textureMap) {
        PhotoTexture removed = textureMap.remove(fileName);
        if (removed != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.getTextureManager().release(removed.textureId);
            }
        }
    }

    Path photoPath(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..")) {
            return null;
        }
        return getPhotoDirectory().resolve(fileName);
    }

    private Path getPhotoDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("screenshots")
                .resolve(PHOTO_DIRECTORY_NAME);
    }
}
