package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.chat.ChatImageStore;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class PhoneChatImages {
    private static final int[] UPLOAD_SIZES = {512, 384, 256};
    private static final int MAX_TEXTURES = 48;
    private static final Map<String, PhotoTexture> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private PhoneChatImages() {
    }

    static PhotoTexture get(String imageId) {
        if (!ChatImageStore.isValidId(imageId) || FAILED.contains(imageId)) {
            return null;
        }

        PhotoTexture cached = TEXTURES.get(imageId);
        if (cached != null) {
            return cached;
        }
        if (REQUESTED.contains(imageId)) {
            return null;
        }

        Path file = cacheFile(imageId);
        if (Files.exists(file)) {
            PhotoTexture loaded = load(imageId, file);
            if (loaded != null) {
                return loaded;
            }
        }

        if (REQUESTED.add(imageId)) {
            ChatNetworkingClient.requestImage(imageId);
        }
        return null;
    }

    static boolean isFailed(String imageId) {
        return FAILED.contains(imageId);
    }

    static void receive(String imageId, byte[] data) {
        REQUESTED.remove(imageId);
        if (!ChatImageStore.isValidId(imageId) || data == null || data.length == 0) {
            FAILED.add(imageId);
            return;
        }

        Path file = cacheFile(imageId);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Unable to cache chat image {}", imageId, exception);
        }

        if (load(imageId, file) == null) {
            FAILED.add(imageId);
        }
    }

    static boolean upload(long conversationId, String peerNumber, Path photoFile) {
        byte[] data = encode(photoFile);
        if (data == null) {
            return false;
        }

        long uploadId = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        int count = (data.length + ChatImageStore.CHUNK_SIZE - 1) / ChatImageStore.CHUNK_SIZE;
        for (int index = 0; index < count; index++) {
            int from = index * ChatImageStore.CHUNK_SIZE;
            int to = Math.min(data.length, from + ChatImageStore.CHUNK_SIZE);
            ChatNetworkingClient.sendImageChunk(uploadId, conversationId, peerNumber, index, count,
                    Arrays.copyOfRange(data, from, to));
        }
        return true;
    }

    static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (PhotoTexture texture : TEXTURES.values()) {
            if (minecraft != null) {
                minecraft.getTextureManager().release(texture.textureId);
            }
        }
        TEXTURES.clear();
        REQUESTED.clear();
        FAILED.clear();
    }

    private static byte[] encode(Path photoFile) {
        if (photoFile == null || !Files.exists(photoFile)) {
            return null;
        }

        try (InputStream stream = Files.newInputStream(photoFile); NativeImage source = NativeImage.read(stream)) {
            for (int maxSize : UPLOAD_SIZES) {
                float factor = Math.min(1.0F, (float) maxSize / Math.max(source.getWidth(), source.getHeight()));
                int width = Math.max(1, Math.round(source.getWidth() * factor));
                int height = Math.max(1, Math.round(source.getHeight() * factor));
                try (NativeImage scaled = new NativeImage(width, height, false)) {
                    source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
                    byte[] bytes = scaled.asByteArray();
                    if (bytes.length <= ChatImageStore.MAX_IMAGE_BYTES) {
                        return bytes;
                    }
                }
            }
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Unable to encode chat image {}", photoFile, exception);
        }
        return null;
    }

    private static PhotoTexture load(String imageId, Path file) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        try (InputStream stream = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(stream);
            int width = image.getWidth();
            int height = image.getHeight();
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            ResourceLocation id = BPhone.id("chat_image/" + imageId);
            minecraft.getTextureManager().register(id, texture);
            PhotoTexture photoTexture = new PhotoTexture(id, width, height);
            TEXTURES.put(imageId, photoTexture);
            trim(minecraft);
            return photoTexture;
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Unable to load chat image {}", imageId, exception);
            return null;
        }
    }

    private static void trim(Minecraft minecraft) {
        Iterator<Map.Entry<String, PhotoTexture>> iterator = TEXTURES.entrySet().iterator();
        while (TEXTURES.size() > MAX_TEXTURES && iterator.hasNext()) {
            Map.Entry<String, PhotoTexture> eldest = iterator.next();
            minecraft.getTextureManager().release(eldest.getValue().textureId);
            iterator.remove();
        }
    }

    private static Path cacheFile(String imageId) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("bphone_cache").resolve("chat_images")
                .resolve(imageId + ".png");
    }
}
