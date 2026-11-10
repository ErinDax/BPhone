package cn.erindax.bphone.client.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

final class PhoneMediaSurfaceRenderer {
    private static final int CAMERA_CONTROL_BAND = 0x59000000;
    private static final int MIN_SIDE_HINT_WIDTH = 64;

    private PhoneMediaSurfaceRenderer() {
    }

    static void renderGallerySurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        List<PhotoEntry> photos = screen.getPhotos();
        int pageCount = screen.getGalleryPageCount();
        UiRect contentBounds = screen.getMediaSurfaceBounds();
        int displayLeft = screen.displayX;
        int displayRight = screen.displayX + screen.displayWidth;
        int displayTop = screen.displayY;
        int displayBottom = screen.displayY + screen.displayHeight;

        int contentLeft = contentBounds.left;
        int contentRight = contentBounds.right();
        int contentTop = contentBounds.top;
        int contentBottom = contentBounds.bottom();
        int headerHeight = screen.getGalleryHeaderHeight();
        int headerBottom = Math.min(contentBottom, contentTop + headerHeight);
        int headerInset = Math.max(1, Math.round(2 * screen.scale));
        int headerBleed = Math.max(2, Math.round(4 * screen.scale));
        int headerLeft = contentLeft - headerBleed;
        int headerTop = contentTop + headerInset;
        int headerRight = contentRight + headerBleed;

        guiGraphics.fill(displayLeft, displayTop, displayRight, displayBottom, 0xFFFFFFFF);

        Component titleText = Component.translatable("screen.bphone.phone.gallery.title");
        int titleX = headerLeft + Math.round(6 * screen.scale);
        int titleY = headerTop + Math.round(7 * screen.scale);

        int maxTitleWidth = Math.round((contentRight - contentLeft) * 0.6F);
        float titleScale = PhoneScreenDraw.textScaleToFit(minecraft.font, titleText, maxTitleWidth, 0.6F);
        titleScale = Math.min(titleScale, 1.25F);
        int titleHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, titleScale);

        Component pageText = Component.literal((screen.galleryPage + 1) + " / " + pageCount);
        float pageScale = PhoneScreenDraw.textScaleToFit(minecraft.font, pageText,
                Math.round((contentRight - contentLeft) * 0.30F), 0.4F);
        pageScale = Math.min(pageScale, 0.85F);
        int scaledPageTextWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, pageText, pageScale);
        int scaledPageTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, pageScale);
        int pageChipX = headerRight - scaledPageTextWidth - Math.round(7 * screen.scale);
        int pageChipY = titleY + titleHeight - scaledPageTextHeight - Math.round(0.5F * screen.scale);

        int hintY = titleY + titleHeight + Math.max(5, Math.round(6 * screen.scale));
        Component hintText = Component.translatable("screen.bphone.phone.gallery.hint_open");
        int hintMaxWidth = Math.max(28, headerRight - headerLeft - Math.round(14 * screen.scale));
        float hintScale = PhoneScreenDraw.textScaleToFit(minecraft.font, hintText, hintMaxWidth);
        hintScale = Math.min(hintScale, 0.85F);
        int headerBandBottom = Math.min(headerBottom,
                hintY + PhoneScreenDraw.scaledTextHeight(minecraft.font, hintScale) + Math.max(6, Math.round(8 * screen.scale)));

        guiGraphics.fill(headerLeft, headerTop, headerRight, headerBandBottom, 0xFF6C92F4);

        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, titleText, titleX, titleY, 0xFFFFFFFF, false, titleScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, pageText, pageChipX, pageChipY, 0xFFF2F6FF, false, pageScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, hintText, titleX, hintY, 0xFFE8EEFF, false, hintScale);

        if (photos.isEmpty()) {
            Component emptyText = Component.translatable("screen.bphone.phone.gallery.empty");
            float emptyScale = PhoneScreenDraw.textScaleToFit(minecraft.font, emptyText,
                    contentRight - contentLeft - Math.round(20 * screen.scale), 0.8F);
            emptyScale = Math.min(emptyScale, 1.15F);
            int emptyWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, emptyText, emptyScale);
            int emptyHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, emptyScale);
            int availableEmptyHeight = contentBottom - headerBandBottom;
            int emptyX = contentLeft + (contentRight - contentLeft - emptyWidth) / 2;
            int emptyY = headerBandBottom + (availableEmptyHeight - emptyHeight) / 2 - Math.round(10 * screen.scale);

            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, emptyText, emptyX, emptyY, 0xFFAAAAAA, false, emptyScale);
            return;
        }

        int pageStart = screen.galleryPage * PhoneScreen.GALLERY_PAGE_SIZE;
        int pageEnd = Math.min(pageStart + PhoneScreen.GALLERY_PAGE_SIZE, photos.size());
        int pageItemCount = pageEnd - pageStart;

        for (int photoIndex = pageStart; photoIndex < pageEnd; photoIndex++) {
            int localIndex = photoIndex - pageStart;
            UiRect slotBounds = screen.getGallerySlotBounds(localIndex, pageItemCount);
            int cardX = slotBounds.left;
            int cardY = slotBounds.top;
            int cardRight = slotBounds.right();
            int cardBottom = slotBounds.bottom();

            guiGraphics.fill(cardX, cardY, cardRight, cardBottom, 0xFFE5E5EA);

            PhotoEntry photoEntry = photos.get(photoIndex);
            boolean renderedImage = false;
            if (photoEntry.hasFile()) {
                PhotoTexture photoTexture = screen.getOrLoadPhotoTexture(photoEntry.fileName, true);
                if (photoTexture != null) {
                    guiGraphics.blit(photoTexture.textureId, cardX, cardY, slotBounds.width, slotBounds.height,
                            0.0F, 0.0F, photoTexture.width, photoTexture.height,
                            photoTexture.width, photoTexture.height);
                    renderedImage = true;
                }
            }

            if (!renderedImage && !photoEntry.itemStack.isEmpty() && !screen.wallpaperContextMenuOpen) {
                int itemX = cardX + (slotBounds.width - 16) / 2;
                int itemY = cardY + (slotBounds.height - 16) / 2;
                guiGraphics.renderItem(photoEntry.itemStack, itemX, itemY);
                guiGraphics.renderItemDecorations(minecraft.font, photoEntry.itemStack, itemX, itemY);
            }
        }
    }

    static void renderPhotoViewerSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        List<PhotoEntry> photos = screen.getPhotos();
        if (photos.isEmpty()) {
            screen.photoViewerMode = false;
            screen.galleryMode = true;
            screen.viewerPhotoIndex = -1;
            screen.rebuildWidgets();
            return;
        }

        screen.viewerPhotoIndex = Mth.clamp(screen.viewerPhotoIndex, 0, photos.size() - 1);
        PhotoEntry photoEntry = photos.get(screen.viewerPhotoIndex);
        ViewerLayout viewerLayout = screen.getViewerLayout();
        UiRect contentBounds = screen.getMediaSurfaceBounds();
        int displayLeft = screen.displayX;
        int displayRight = screen.displayX + screen.displayWidth;
        int displayTop = screen.displayY;
        int displayBottom = screen.displayY + screen.displayHeight;

        Component pageText = Component.translatable("screen.bphone.phone.viewer.page",
                screen.viewerPhotoIndex + 1, photos.size());

        int contentLeft = contentBounds.left + Math.round(5 * screen.scale);
        int contentRight = contentBounds.right() - Math.round(5 * screen.scale);
        int contentTop = contentBounds.top + Math.round(8 * screen.scale);
        int contentBottom = contentBounds.bottom();
        int headerBottom = viewerLayout.areaY;

        guiGraphics.fill(displayLeft, displayTop, displayRight, displayBottom, 0xFFFFFFFF);
        guiGraphics.fill(contentLeft, contentTop, contentRight, headerBottom, 0xFFFFFFFF);

        int deleteButtonSize = Math.round(24 * screen.scale);
        int deleteButtonX = contentBounds.right() - deleteButtonSize - Math.round(6 * screen.scale);
        int titleX = contentLeft + Math.round(7 * screen.scale);
        int titleY = contentTop + Math.round(6 * screen.scale);
        int maxTitleWidth = Math.max(24, deleteButtonX - titleX - Math.round(8 * screen.scale));
        float titleScale = PhoneScreenDraw.textScaleToFit(minecraft.font, pageText, maxTitleWidth, 0.6F);
        titleScale = Math.min(titleScale, 1.25F);

        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, pageText, titleX, titleY, 0xFF000000, false, titleScale);

        guiGraphics.fill(viewerLayout.areaX, viewerLayout.areaY,
                viewerLayout.areaX + viewerLayout.areaWidth, viewerLayout.areaY + viewerLayout.areaHeight, 0xFFE5E5EA);

        boolean renderedImage = false;
        if (photoEntry.hasFile()) {
            PhotoTexture photoTexture = screen.getOrLoadPhotoTexture(photoEntry.fileName, false);
            if (photoTexture != null) {
                PhoneScreenDraw.drawPhotoInArea(guiGraphics, photoTexture,
                        viewerLayout.areaX, viewerLayout.areaY, viewerLayout.areaWidth, viewerLayout.areaHeight);
                renderedImage = true;
            }
        }

        if (!renderedImage) {
            if (!photoEntry.itemStack.isEmpty()) {
                int itemX = viewerLayout.areaX + (viewerLayout.areaWidth - 16) / 2;
                int itemY = viewerLayout.areaY + (viewerLayout.areaHeight - 16) / 2;
                guiGraphics.renderItem(photoEntry.itemStack, itemX, itemY);
                guiGraphics.renderItemDecorations(minecraft.font, photoEntry.itemStack, itemX, itemY);
            } else {
                Component missingText = Component.translatable("screen.bphone.phone.viewer.missing");
                int missingMaxWidth = Math.max(28, viewerLayout.areaWidth - Math.round(12 * screen.scale));
                float missingScale = PhoneScreenDraw.textScaleToFit(minecraft.font, missingText, missingMaxWidth);
                int missingWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, missingText, missingScale);
                int missingHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, missingScale);
                int missingX = viewerLayout.areaX + (viewerLayout.areaWidth - missingWidth) / 2;
                int missingY = viewerLayout.areaY + (viewerLayout.areaHeight - missingHeight) / 2;
                PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, missingText, missingX, missingY,
                        0xFF8E8E93, false, missingScale);
            }
        }
    }

    static void renderCameraSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        if (screen.capturePending) {
            return;
        }

        if (screen.isBankScanCameraActive()) {
            return;
        }

        UiRect controlBand = screen.getCameraControlBandBounds();
        guiGraphics.fill(controlBand.left, controlBand.top, controlBand.right(), controlBand.bottom(), CAMERA_CONTROL_BAND);
        UiRect rotateBounds = screen.getCameraRotateButtonBounds();
        int backdropInset = Math.max(1, Math.round(2 * screen.scale));
        int backdropLeft = rotateBounds.left - backdropInset;
        int backdropTop = rotateBounds.top - backdropInset;
        int backdropRight = rotateBounds.right() + backdropInset;
        int backdropBottom = rotateBounds.bottom() + backdropInset;
        guiGraphics.fill(backdropLeft + 1, backdropTop, backdropRight - 1, backdropBottom, CAMERA_CONTROL_BAND);
        guiGraphics.fill(backdropLeft, backdropTop + 1, backdropLeft + 1, backdropBottom - 1, CAMERA_CONTROL_BAND);
        guiGraphics.fill(backdropRight - 1, backdropTop + 1, backdropRight, backdropBottom - 1, CAMERA_CONTROL_BAND);
        renderCameraStorageWarning(screen, guiGraphics);
        renderCameraZoomIndicator(screen, guiGraphics);

        UiRect previewBounds = screen.getCameraPreviewBounds();
        PhotoEntry latestPhoto = screen.getLatestPhoto();
        boolean renderedPreview = false;
        if (latestPhoto != null && latestPhoto.hasFile()) {
            PhotoTexture previewTexture = screen.getOrLoadPhotoTexture(latestPhoto.fileName, true);
            if (previewTexture != null) {
                guiGraphics.fill(previewBounds.left, previewBounds.top,
                        previewBounds.right(), previewBounds.bottom(), 0xFFE7EBF2);
                guiGraphics.blit(previewTexture.textureId, previewBounds.left + 1, previewBounds.top + 1,
                        previewBounds.width - 2, previewBounds.height - 2,
                        0.0F, 0.0F, previewTexture.width, previewTexture.height,
                        previewTexture.width, previewTexture.height);
                renderedPreview = true;
            }
        }

        if (!renderedPreview) {
            int left = previewBounds.left;
            int top = previewBounds.top;
            int right = previewBounds.right();
            int bottom = previewBounds.bottom();
            guiGraphics.fill(left, top, right, top + 1, 0xFFE7EBF2);
            guiGraphics.fill(left, bottom - 1, right, bottom, 0xFFE7EBF2);
            guiGraphics.fill(left, top, left + 1, bottom, 0xFFE7EBF2);
            guiGraphics.fill(right - 1, top, right, bottom, 0xFFE7EBF2);
        }
    }

    private static void renderCameraStorageWarning(PhoneScreen screen, GuiGraphics guiGraphics) {
        if (!screen.isPhotoStorageFull()) {
            return;
        }

        UiRect shutterBounds = screen.getCameraShutterButtonBounds();
        UiRect captureBounds = screen.getCameraCaptureBounds();
        var font = Minecraft.getInstance().font;
        Component warningText = Component.translatable("screen.bphone.phone.camera.storage_full");
        int maxTextWidth = Math.max(Math.round(90 * screen.scale), shutterBounds.width + Math.round(36 * screen.scale));
        float textScale = PhoneScreenDraw.textScaleToFit(font, warningText, maxTextWidth, 0.45F);
        textScale = Math.min(textScale, 0.9F);
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, warningText, textScale);
        int textHeight = PhoneScreenDraw.scaledTextHeight(font, textScale);
        int textX = captureBounds.left + (captureBounds.width - textWidth) / 2;
        int textY = captureBounds.bottom() - textHeight - Math.max(8, Math.round(10 * screen.scale));

        PhoneScreenDraw.drawScaledText(guiGraphics, font, warningText, textX, textY, 0xFFF7F9FF, true, textScale);
    }

    private static void renderCameraZoomIndicator(PhoneScreen screen, GuiGraphics guiGraphics) {
        if (!screen.shouldShowCameraZoomIndicator()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        Component zoomText = Component.literal(screen.getCameraZoomLabel());
        float textScale = 0.8F;
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, zoomText, textScale);
        int textHeight = PhoneScreenDraw.scaledTextHeight(font, textScale);
        int indicatorPaddingX = Math.max(6, Math.round(7 * screen.scale));
        int indicatorPaddingY = Math.max(4, Math.round(5 * screen.scale));
        int indicatorWidth = textWidth + (indicatorPaddingX * 2);
        int indicatorHeight = textHeight + (indicatorPaddingY * 2) + Math.max(4, Math.round(5 * screen.scale));
        int indicatorX = screen.displayX + (screen.displayWidth - indicatorWidth) / 2;
        int indicatorY = screen.displayY + Math.max(10, Math.round(12 * screen.scale));

        int barInset = Math.max(4, Math.round(5 * screen.scale));
        int barHeight = Math.max(2, Math.round(3 * screen.scale));
        int barY = indicatorY + indicatorHeight - barInset - barHeight;
        int barWidth = Math.max(12, indicatorWidth - (barInset * 2));
        int minZoomLevel = screen.getMinCameraZoomLevel();
        int maxZoomLevel = screen.getMaxCameraZoomLevel();
        float zoomProgress = maxZoomLevel <= minZoomLevel
                ? 1.0F
                : (float) (screen.getCameraZoomLevel() - minZoomLevel) / (float) (maxZoomLevel - minZoomLevel);
        int fillWidth = Math.max(barHeight, Math.round(barWidth * Mth.clamp(zoomProgress, 0.0F, 1.0F)));

        guiGraphics.fill(indicatorX, indicatorY, indicatorX + indicatorWidth, indicatorY + indicatorHeight, 0x99000000);
        guiGraphics.fill(indicatorX + barInset, barY, indicatorX + barInset + barWidth, barY + barHeight, 0x33FFFFFF);
        guiGraphics.fill(indicatorX + barInset, barY, indicatorX + barInset + fillWidth, barY + barHeight, 0xFFE7EBF2);

        int textX = indicatorX + (indicatorWidth - textWidth) / 2;
        int textY = indicatorY + indicatorPaddingY;
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, zoomText, textX, textY, 0xFFFFFFFF, true, textScale);
    }

    static void renderCameraOverlayHints(PhoneScreen screen, GuiGraphics guiGraphics) {
        int edge = Math.max(4, Math.round(6 * screen.scale));
        int sideX = screen.frameX + screen.frameWidth + Math.max(8, Math.round(10 * screen.scale));
        int hintX;
        int hintY;
        int hintWidth;
        if (screen.width - sideX - edge >= MIN_SIDE_HINT_WIDTH) {
            hintX = sideX;
            hintY = screen.frameY + Math.max(28, Math.round(34 * screen.scale));
            hintWidth = screen.width - sideX - edge;
        } else {
            hintX = screen.frameX;
            hintY = screen.frameY + screen.frameHeight + Math.max(4, Math.round(6 * screen.scale));
            hintWidth = Math.max(48, screen.width - hintX - edge);
        }

        Component[] hints = {
                Component.translatable("screen.bphone.phone.camera.hint_drag"),
                Component.translatable("screen.bphone.phone.camera.hint_zoom"),
                Component.translatable("screen.bphone.phone.camera.hint_capture"),
                Component.translatable("screen.bphone.phone.camera.hint_rotate"),
                Component.translatable("screen.bphone.phone.camera.hint_exit")
        };
        var font = Minecraft.getInstance().font;
        int hintGap = Math.max(1, Math.round(2 * screen.scale));
        int pad = Math.max(3, Math.round(5 * screen.scale));
        float[] hintScales = new float[hints.length];
        int panelWidth = 0;
        int panelHeight = 0;
        for (int index = 0; index < hints.length; index++) {
            hintScales[index] = PhoneScreenDraw.textScaleToFit(font, hints[index], hintWidth - 2 * pad);
            panelWidth = Math.max(panelWidth, PhoneScreenDraw.scaledTextWidth(font, hints[index], hintScales[index]));
            panelHeight += PhoneScreenDraw.scaledTextHeight(font, hintScales[index]) + (index > 0 ? hintGap : 0);
        }
        panelHeight -= Math.max(1, Math.round(hintScales[hints.length - 1]));
        PhoneUi.fillRounded(guiGraphics, hintX, hintY, panelWidth + 2 * pad, panelHeight + 2 * pad, pad, 0x99000000);
        int lineY = hintY + pad;
        for (int index = 0; index < hints.length; index++) {
            PhoneScreenDraw.drawScaledText(guiGraphics, font, hints[index], hintX + pad, lineY, 0xFFF7F9FF, true,
                    hintScales[index]);
            lineY += PhoneScreenDraw.scaledTextHeight(font, hintScales[index]) + hintGap;
        }
    }
}
