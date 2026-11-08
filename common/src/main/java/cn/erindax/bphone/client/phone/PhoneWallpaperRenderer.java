package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhoneWallpaperData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class PhoneWallpaperRenderer {
    private static final int FALLBACK_WIDTH = 144;
    private static final int FALLBACK_HEIGHT = 266;
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZES = new HashMap<>();

    private PhoneWallpaperRenderer() {
    }

    static void drawWallpaper(PhoneScreen screen, GuiGraphics guiGraphics, String wallpaper, UiRect area, boolean preview) {
        guiGraphics.fill(area.left, area.top, area.right(), area.bottom(), 0xFF000000);
        String key = wallpaper;
        if (PhoneWallpaperData.isPhoto(key)) {
            PhotoTexture texture = screen.getOrLoadPhotoTexture(PhoneWallpaperData.getPhotoFileName(key), preview);
            if (texture != null) {
                PhoneScreenDraw.drawTextureInArea(guiGraphics, texture.textureId, texture.width, texture.height,
                        area.left, area.top, area.width, area.height, 1.0F);
                return;
            }
            key = PhoneWallpaperData.DEFAULT_WALLPAPER;
        }

        ResourceLocation texture = PhoneWallpaperData.resolveBuiltinTexture(key);
        int[] size = textureSize(texture);
        PhoneScreenDraw.drawTextureInArea(guiGraphics, texture, size[0], size[1],
                area.left, area.top, area.width, area.height, 1.0F);
    }

    private static int[] textureSize(ResourceLocation texture) {
        return TEXTURE_SIZES.computeIfAbsent(texture, location -> {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                return new int[] {FALLBACK_WIDTH, FALLBACK_HEIGHT};
            }
            try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                return new int[] {Math.max(1, image.getWidth()), Math.max(1, image.getHeight())};
            } catch (Exception exception) {
                BPhone.LOGGER.debug("Unable to read wallpaper size of {}", location, exception);
                return new int[] {FALLBACK_WIDTH, FALLBACK_HEIGHT};
            }
        });
    }
}
