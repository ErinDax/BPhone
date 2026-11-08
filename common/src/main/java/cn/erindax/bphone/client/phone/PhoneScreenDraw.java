package cn.erindax.bphone.client.phone;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class PhoneScreenDraw {
    private PhoneScreenDraw() {
    }

    static float textScaleToFit(Font font, Component text, int maxWidth) {
        return textScaleToFit(font, text, maxWidth, 0.45F);
    }

    static float textScaleToFit(Font font, Component text, int maxWidth, float minScale) {
        int textWidth = Math.max(1, font.width(text));
        return Math.min(1.0F, Math.max(minScale, (float) maxWidth / textWidth));
    }

    static int scaledTextWidth(Font font, Component text, float textScale) {
        return Math.max(1, Math.round(font.width(text) * textScale));
    }

    static int scaledTextHeight(Font font, float textScale) {
        return Math.max(1, Math.round(font.lineHeight * textScale));
    }

    static void drawScaledText(GuiGraphics guiGraphics, Font font, Component text,
                               int x, int y, int color, boolean shadow, float textScale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(textScale, textScale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, shadow);
        guiGraphics.pose().popPose();
    }

    static void drawPhotoInArea(GuiGraphics guiGraphics, PhotoTexture photoTexture,
                                int areaX, int areaY, int areaWidth, int areaHeight) {
        boolean landscapePhoto = photoTexture.width > photoTexture.height;
        boolean landscapeArea = areaWidth > areaHeight;
        if (landscapePhoto != landscapeArea) {
            drawTextureFitted(guiGraphics, photoTexture.textureId, photoTexture.width, photoTexture.height,
                    areaX, areaY, areaWidth, areaHeight);
            return;
        }

        drawTextureInArea(guiGraphics, photoTexture.textureId, photoTexture.width, photoTexture.height,
                areaX, areaY, areaWidth, areaHeight, 1.03F);
    }

    static void drawTextureFitted(GuiGraphics guiGraphics, ResourceLocation textureId, int textureWidth, int textureHeight,
                                  int areaX, int areaY, int areaWidth, int areaHeight) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            return;
        }

        float scaleFactor = Math.min((float) areaWidth / textureWidth, (float) areaHeight / textureHeight);
        int drawWidth = Math.max(1, Math.round(textureWidth * scaleFactor));
        int drawHeight = Math.max(1, Math.round(textureHeight * scaleFactor));
        int drawX = areaX + (areaWidth - drawWidth) / 2;
        int drawY = areaY + (areaHeight - drawHeight) / 2;
        guiGraphics.blit(textureId, drawX, drawY, drawWidth, drawHeight,
                0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    static void drawTextureInArea(GuiGraphics guiGraphics, ResourceLocation textureId, int textureWidth, int textureHeight,
                                  int areaX, int areaY, int areaWidth, int areaHeight, float zoomMultiplier) {
        if (textureWidth <= 0 || textureHeight <= 0) {
            return;
        }

        float scaleFactor = Math.max((float) areaWidth / textureWidth, (float) areaHeight / textureHeight);
        scaleFactor *= zoomMultiplier;
        int drawWidth = Math.max(1, Math.round(textureWidth * scaleFactor));
        int drawHeight = Math.max(1, Math.round(textureHeight * scaleFactor));
        int drawX = areaX + (areaWidth - drawWidth) / 2;
        int drawY = areaY + (areaHeight - drawHeight) / 2;

        guiGraphics.enableScissor(areaX, areaY, areaX + areaWidth, areaY + areaHeight);
        guiGraphics.blit(textureId, drawX, drawY, drawWidth, drawHeight,
                0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
        guiGraphics.disableScissor();
    }
}
