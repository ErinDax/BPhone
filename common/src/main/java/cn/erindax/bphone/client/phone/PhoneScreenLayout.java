package cn.erindax.bphone.client.phone;

import net.minecraft.util.Mth;

final class PhoneScreenLayout {
    private static final int GALLERY_COLUMNS = 3;
    private static final int GALLERY_ROWS = 4;
    private static final int GALLERY_PAGE_SIZE = GALLERY_COLUMNS * GALLERY_ROWS;
    private static final int CALL_DIAL_COLUMNS = 3;
    private static final int CALL_DIAL_ROWS = 4;
    private static final int CAMERA_VIEW_X = 9;
    private static final int CAMERA_VIEW_Y = 27;
    private static final int CAMERA_VIEW_WIDTH = 142;
    private static final int CAMERA_VIEW_HEIGHT = 266;
    private static final int CAMERA_VIEW_SIDE_TRIM = 3;

    private final float scale;
    private final int frameX;
    private final int frameY;
    private final int displayX;
    private final int displayY;
    private final int displayWidth;
    private final int displayHeight;
    private final boolean landscape;

    PhoneScreenLayout(float scale, int frameX, int frameY, int displayX, int displayY, int displayWidth, int displayHeight,
                      boolean landscape) {
        this.scale = scale;
        this.frameX = frameX;
        this.frameY = frameY;
        this.displayX = displayX;
        this.displayY = displayY;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.landscape = landscape;
    }

    UiRect mediaSurfaceBounds() {
        int horizontalInset = 0;
        int topInset = Math.max(1, Math.round(2 * scale));
        int bottomInset = Math.max(1, Math.round(2 * scale));
        return new UiRect(
                displayX + horizontalInset,
                displayY + topInset,
                displayWidth - (horizontalInset * 2),
                displayHeight - topInset - bottomInset);
    }

    ViewerLayout viewerLayout() {
        UiRect contentBounds = mediaSurfaceBounds();
        return new ViewerLayout(
                contentBounds.left + Math.round(5 * scale),
                contentBounds.top + Math.round(30 * scale),
                Math.max(24, contentBounds.width - Math.round(10 * scale)),
                Math.max(24, contentBounds.height - Math.round(72 * scale)));
    }

    GalleryLayout galleryLayout() {
        UiRect contentBounds = mediaSurfaceBounds();
        int gridTopInset = galleryGridTopInset();
        int footerHeight = galleryFooterHeight();
        int rowGap = Math.max(1, Math.round(2 * scale));
        int columnGap = Math.max(1, Math.round(2 * scale));
        int horizontalPadding = Math.max(0, Math.round(4 * scale));

        int availableWidth = contentBounds.width - (horizontalPadding * 2) - ((GALLERY_COLUMNS - 1) * columnGap);
        int availableHeight = contentBounds.height - gridTopInset - footerHeight - ((GALLERY_ROWS - 1) * rowGap);

        int slotSize = availableWidth / GALLERY_COLUMNS;
        int maxSlotByHeight = availableHeight / GALLERY_ROWS;
        slotSize = Math.min(slotSize, maxSlotByHeight);
        slotSize = Math.max(10, slotSize);

        int totalRowWidth = (GALLERY_COLUMNS * slotSize) + ((GALLERY_COLUMNS - 1) * columnGap);
        int totalHeight = (GALLERY_ROWS * slotSize) + ((GALLERY_ROWS - 1) * rowGap);
        int startX = contentBounds.left + (contentBounds.width - totalRowWidth) / 2;
        int startY = contentBounds.top + gridTopInset;

        int maxStartY = contentBounds.top + contentBounds.height - footerHeight - totalHeight;
        if (startY > maxStartY) {
            startY = maxStartY;
        }

        return new GalleryLayout(startX, startY, slotSize, columnGap, rowGap);
    }

    private int galleryGridTopInset() {
        return Math.max(34, Math.round(40 * scale));
    }

    int galleryHeaderHeight() {
        return Math.max(54, Math.round(60 * scale));
    }

    int galleryFooterHeight() {
        return Math.max(16, Math.round(20 * scale));
    }

    int galleryPageCount(int photoCount) {
        if (photoCount <= 0) {
            return 1;
        }

        return (photoCount + GALLERY_PAGE_SIZE - 1) / GALLERY_PAGE_SIZE;
    }

    int galleryPhotoIndexAt(double mouseX, double mouseY, int galleryPage, int photoCount) {
        if (photoCount <= 0) {
            return -1;
        }

        int pageStart = galleryPage * GALLERY_PAGE_SIZE;
        int pageEnd = Math.min(pageStart + GALLERY_PAGE_SIZE, photoCount);
        int pageItemCount = pageEnd - pageStart;

        for (int photoIndex = pageStart; photoIndex < pageEnd; photoIndex++) {
            int localIndex = photoIndex - pageStart;
            UiRect slotBounds = gallerySlotBounds(localIndex, pageItemCount);
            if (slotBounds.contains(mouseX, mouseY)) {
                return photoIndex;
            }
        }

        return -1;
    }

    UiRect gallerySlotBounds(int localIndex, int pageItemCount) {
        GalleryLayout galleryLayout = galleryLayout();
        int row = localIndex / GALLERY_COLUMNS;
        int column = localIndex % GALLERY_COLUMNS;
        int rowStartIndex = row * GALLERY_COLUMNS;
        int rowItemCount = Math.min(GALLERY_COLUMNS, Math.max(1, pageItemCount - rowStartIndex));
        int fullRowWidth = (GALLERY_COLUMNS * galleryLayout.slotSize)
                + ((GALLERY_COLUMNS - 1) * galleryLayout.columnGap);
        int rowWidth = (rowItemCount * galleryLayout.slotSize)
                + (Math.max(0, rowItemCount - 1) * galleryLayout.columnGap);
        int rowX = galleryLayout.startX + Math.max(0, (fullRowWidth - rowWidth) / 2);
        int x = rowX + column * (galleryLayout.slotSize + galleryLayout.columnGap);
        int y = galleryLayout.startY + row * (galleryLayout.slotSize + galleryLayout.rowGap);
        return new UiRect(x, y, galleryLayout.slotSize, galleryLayout.slotSize);
    }

    UiRect callSurfaceBounds() {
        int horizontalInset = 0;
        int topInset = Math.max(7, Math.round(9 * scale));
        int bottomInset = Math.max(18, Math.round(20 * scale));
        return new UiRect(
                displayX + horizontalInset,
                displayY + topInset,
                displayWidth - (horizontalInset * 2),
                displayHeight - topInset - bottomInset);
    }

    UiRect callBackdropBounds() {
        int horizontalExpand = 0;
        int topExpand = 0;
        int bottomExpand = 0;
        return new UiRect(
                displayX - horizontalExpand,
                displayY - topExpand,
                displayWidth + (horizontalExpand * 2),
                displayHeight + topExpand + bottomExpand);
    }

    UiRect mediaBackdropBounds() {
        int horizontalExpand = 0;
        int topExpand = 0;
        int bottomExpand = 0;
        return new UiRect(
                displayX - horizontalExpand,
                displayY - topExpand,
                displayWidth + (horizontalExpand * 2),
                displayHeight + topExpand + bottomExpand);
    }

    int callHeaderHeight() {
        return Math.max(18, Math.round(22 * scale));
    }

    UiRect callDialMenuBounds() {
        UiRect contentBounds = callSurfaceBounds();
        int iconSize = Math.max(16, Math.round(20 * scale));
        int gap = Math.max(10, Math.round(12 * scale));
        int totalWidth = (iconSize * 3) + (gap * 2);
        int groupStartX = contentBounds.left + Math.max(0, (contentBounds.width - totalWidth) / 2);
        int iconX = groupStartX;
        int iconY = contentBounds.bottom() - iconSize - Math.max(3, Math.round(5 * scale));
        return new UiRect(iconX, iconY, iconSize, iconSize);
    }

    UiRect callRecentsMenuBounds() {
        UiRect dialBounds = callDialMenuBounds();
        int gap = Math.max(10, Math.round(12 * scale));
        return new UiRect(dialBounds.right() + gap, dialBounds.top, dialBounds.width, dialBounds.height);
    }

    UiRect callListMenuBounds() {
        UiRect recentsBounds = callRecentsMenuBounds();
        int gap = Math.max(10, Math.round(12 * scale));
        return new UiRect(recentsBounds.right() + gap, recentsBounds.top, recentsBounds.width, recentsBounds.height);
    }

    UiRect addContactHeaderButtonBounds() {
        UiRect panelBounds = contactPanelBounds();
        int inset = Math.max(7, Math.round(8 * scale));
        int rowHeight = Math.max(16, Math.round(18 * scale));
        int top = panelBounds.top + inset;
        return new UiRect(panelBounds.left + inset, top,
                panelBounds.width - (inset * 2), rowHeight);
    }

    UiRect callNumberDisplayBounds() {
        UiRect contentBounds = callSurfaceBounds();
        int displayTop = contentBounds.top + callHeaderHeight() + Math.max(1, Math.round(1 * scale));
        int numberHeight = Math.max(30, Math.round(36 * scale));
        int horizontalPadding = Math.max(2, Math.round(2 * scale));
        return new UiRect(contentBounds.left + horizontalPadding, displayTop,
                contentBounds.width - (horizontalPadding * 2), numberHeight);
    }

    UiRect callDialPadBounds() {
        UiRect contentBounds = callSurfaceBounds();
        UiRect numberBounds = callNumberDisplayBounds();
        int top = numberBounds.bottom() + Math.max(2, Math.round(3 * scale));
        int bottomPadding = (contentBounds.bottom() - callDialMenuBounds().top) + callMenuBottomReserve();
        int height = contentBounds.bottom() - top - bottomPadding;
        return new UiRect(contentBounds.left, top, contentBounds.width, height);
    }

    int callMenuBottomReserve() {
        return Math.max(30, Math.round(36 * scale));
    }

    UiRect dialPadCellBounds(int index) {
        UiRect dialPadBounds = callDialPadBounds();
        int gap = Math.max(4, Math.round(4 * scale));
        int maxCellByWidth = Math.max(18, (dialPadBounds.width - (gap * (CALL_DIAL_COLUMNS - 1))) / CALL_DIAL_COLUMNS);
        int maxCellByHeight = Math.max(18, (dialPadBounds.height - (gap * (CALL_DIAL_ROWS - 1))) / CALL_DIAL_ROWS);
        int preferredCellSize = Math.max(18, Math.round(22 * scale));
        int cellSize = Mth.clamp(preferredCellSize, 18, Math.min(maxCellByWidth, maxCellByHeight));
        int totalWidth = (cellSize * CALL_DIAL_COLUMNS) + (gap * (CALL_DIAL_COLUMNS - 1));
        int totalHeight = (cellSize * CALL_DIAL_ROWS) + (gap * (CALL_DIAL_ROWS - 1));
        int startX = dialPadBounds.left + Math.max(0, (dialPadBounds.width - totalWidth) / 2);
        int startY = dialPadBounds.top + Math.max(1, Math.round(2 * scale));
        int row = index / CALL_DIAL_COLUMNS;
        int column = index % CALL_DIAL_COLUMNS;
        int x = startX + column * (cellSize + gap);
        int y = startY + row * (cellSize + gap);
        return new UiRect(x, y, cellSize, cellSize);
    }

    UiRect dialDeleteButtonBounds() {
        return dialPadCellBounds(9);
    }

    UiRect dialCallButtonBounds() {
        return dialPadCellBounds(11);
    }

    UiRect contactPanelBounds() {
        UiRect contentBounds = callSurfaceBounds();
        int panelTop = contentBounds.top + callHeaderHeight() + Math.max(4, Math.round(5 * scale));
        int panelBottom = callDialMenuBounds().top - callMenuBottomReserve();
        return new UiRect(contentBounds.left, panelTop,
                contentBounds.width, Math.max(24, panelBottom - panelTop));
    }

    UiRect contactSaveButtonBounds() {
        UiRect addRow = addContactHeaderButtonBounds();
        int gap = Math.max(3, Math.round(4 * scale));
        int buttonHeight = Math.max(16, Math.round(18 * scale));
        int top = addRow.bottom() + gap;
        return new UiRect(addRow.left, top, addRow.width, buttonHeight);
    }

    UiRect contactRowsBounds(boolean canSaveCurrentNumber) {
        UiRect panelBounds = contactPanelBounds();
        int inset = Math.max(7, Math.round(8 * scale));
        int top;
        if (canSaveCurrentNumber) {
            top = contactSaveButtonBounds().bottom() + Math.max(4, Math.round(5 * scale));
        } else {
            UiRect addRow = addContactHeaderButtonBounds();
            int gap = Math.max(3, Math.round(4 * scale));
            top = addRow.bottom() + gap;
        }
        return new UiRect(panelBounds.left + inset, top,
                panelBounds.width - (inset * 2), Math.max(20, panelBounds.bottom() - inset - top));
    }

    UiRect contactRowBounds(int index, int contactCount, boolean canSaveCurrentNumber) {
        UiRect rowsBounds = contactRowsBounds(canSaveCurrentNumber);
        int rowGap = Math.max(3, Math.round(4 * scale));
        int rowHeight = Math.max(16, Math.round(18 * scale));
        int top = rowsBounds.top + index * (rowHeight + rowGap);
        return new UiRect(rowsBounds.left, top, rowsBounds.width, rowHeight);
    }

    UiRect contactDeleteButtonBounds(int index, int contactCount, boolean canSaveCurrentNumber) {
        UiRect rowBounds = contactRowBounds(index, contactCount, canSaveCurrentNumber);
        int size = Math.min(rowBounds.height - Math.max(4, Math.round(6 * scale)), Math.max(12, Math.round(15 * scale)));
        int padding = Math.max(3, Math.round(4 * scale));
        int x = rowBounds.right() - size - padding;
        int y = rowBounds.top + (rowBounds.height - size) / 2;
        return new UiRect(x, y, size, size);
    }

    UiRect contactEditButtonBounds(int index, int contactCount, boolean canSaveCurrentNumber) {
        UiRect deleteBounds = contactDeleteButtonBounds(index, contactCount, canSaveCurrentNumber);
        int gap = Math.max(2, Math.round(2 * scale));
        return new UiRect(deleteBounds.left - deleteBounds.width - gap, deleteBounds.top, deleteBounds.width, deleteBounds.height);
    }

    int contactIndexAt(double mouseX, double mouseY, int contactCount, boolean canSaveCurrentNumber) {
        for (int index = 0; index < contactCount; index++) {
            if (contactRowBounds(index, contactCount, canSaveCurrentNumber).contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    UiRect addContactPopupBounds() {
        UiRect contentBounds = callSurfaceBounds();
        int panelInset = Math.max(4, Math.round(6 * scale));
        int panelWidth = contentBounds.width - (panelInset * 2);
        int fieldHeight = Math.max(14, Math.round(16 * scale));
        int innerInset = Math.max(8, Math.round(10 * scale));
        int titleHeight = Math.max(18, Math.round(22 * scale));
        int topPadding = Math.max(12, Math.round(14 * scale));
        int bottomPadding = Math.max(12, Math.round(14 * scale));
        int gap = Math.max(10, Math.round(12 * scale));
        int buttonGap = Math.max(12, Math.round(14 * scale));
        int buttonHeight = Math.max(12, Math.round(14 * scale));
        int contentHeight = topPadding + titleHeight + gap + fieldHeight + gap + fieldHeight + buttonGap + buttonHeight + bottomPadding;
        int panelX = contentBounds.left + panelInset;
        int panelY = contentBounds.top + (contentBounds.height - contentHeight) / 2;
        return new UiRect(panelX, panelY, panelWidth, contentHeight);
    }

    UiRect addContactNameBounds() {
        UiRect popupBounds = addContactPopupBounds();
        int fieldHeight = Math.max(14, Math.round(16 * scale));
        int innerInset = Math.max(8, Math.round(10 * scale));
        int titleHeight = Math.max(18, Math.round(22 * scale));
        int topPadding = Math.max(12, Math.round(14 * scale));
        int gap = Math.max(10, Math.round(12 * scale));
        int top = popupBounds.top + topPadding + titleHeight + gap;
        return new UiRect(popupBounds.left + innerInset, top,
                popupBounds.width - (innerInset * 2), fieldHeight);
    }

    UiRect addContactNumberBounds() {
        UiRect nameBounds = addContactNameBounds();
        int gap = Math.max(10, Math.round(12 * scale));
        int fieldHeight = Math.max(14, Math.round(16 * scale));
        return new UiRect(nameBounds.left, nameBounds.bottom() + gap,
                nameBounds.width, fieldHeight);
    }

    UiRect addContactSaveButtonBounds() {
        UiRect numberBounds = addContactNumberBounds();
        int gap = Math.max(12, Math.round(14 * scale));
        int buttonHeight = Math.max(12, Math.round(14 * scale));
        int buttonWidth = Math.max(32, Math.round(40 * scale));
        int buttonGap = Math.max(4, Math.round(5 * scale));
        int totalGroupWidth = buttonWidth * 2 + buttonGap;
        int left = numberBounds.left + (numberBounds.width - totalGroupWidth) / 2;
        return new UiRect(left, numberBounds.bottom() + gap, buttonWidth, buttonHeight);
    }

    UiRect addContactCancelButtonBounds() {
        UiRect saveBounds = addContactSaveButtonBounds();
        int gap = Math.max(4, Math.round(5 * scale));
        return new UiRect(saveBounds.right() + gap, saveBounds.top, saveBounds.width, saveBounds.height);
    }

    UiRect callConnectButtonBounds() {
        int buttonSize = Math.max(30, Math.round(36 * scale));
        int buttonY = displayY + displayHeight - buttonSize - Math.round(66 * scale);
        int gap = Math.max(5, Math.round(6 * scale));
        int centerX = displayX + (displayWidth / 2);
        return new UiRect(centerX - buttonSize - (gap / 2), buttonY, buttonSize, buttonSize);
    }

    UiRect callHangupButtonBounds() {
        int buttonSize = Math.max(30, Math.round(36 * scale));
        int buttonY = displayY + displayHeight - buttonSize - Math.round(66 * scale);
        int centerX = displayX + (displayWidth / 2);
        return new UiRect(centerX - buttonSize / 2, buttonY, buttonSize, buttonSize);
    }

    UiRect callMuteButtonBounds() {
        int hangupSize = Math.max(30, Math.round(36 * scale));
        int muteSize = Math.max(18, Math.round(22 * scale));
        int buttonY = displayY + displayHeight - hangupSize - Math.round(66 * scale);
        int centerX = displayX + (displayWidth / 2);
        int gap = Math.max(10, Math.round(12 * scale));
        return new UiRect(centerX - hangupSize / 2 - gap - muteSize, buttonY + (hangupSize - muteSize) / 2, muteSize, muteSize);
    }

    UiRect callSpeakerButtonBounds() {
        int hangupSize = Math.max(30, Math.round(36 * scale));
        int speakerSize = Math.max(18, Math.round(22 * scale));
        int buttonY = displayY + displayHeight - hangupSize - Math.round(66 * scale);
        int centerX = displayX + (displayWidth / 2);
        int gap = Math.max(10, Math.round(12 * scale));
        return new UiRect(centerX + hangupSize / 2 + gap, buttonY + (hangupSize - speakerSize) / 2, speakerSize, speakerSize);
    }

    private int portraitDisplayWidth() {
        return landscape ? displayHeight : displayWidth;
    }

    private int portraitDisplayHeight() {
        return landscape ? displayWidth : displayHeight;
    }

    private UiRect phoneRect(int u, int v, int width, int height) {
        if (!landscape) {
            return new UiRect(displayX + u, displayY + v, width, height);
        }

        return new UiRect(displayX + v, displayY + portraitDisplayWidth() - u - width, height, width);
    }

    private int cameraShutterSize() {
        return Math.round(42 * scale);
    }

    private int cameraShutterU() {
        return (portraitDisplayWidth() - cameraShutterSize()) / 2;
    }

    private int cameraShutterV() {
        return portraitDisplayHeight() - cameraShutterSize() - Math.round(18 * scale);
    }

    UiRect cameraPreviewBounds() {
        int previewSize = Math.max(20, Math.round(30 * scale));
        int previewU = Math.max(6, Math.round(8 * scale));
        int previewV = cameraShutterV() + cameraShutterSize() / 2 - previewSize / 2;
        return phoneRect(previewU, previewV, previewSize, previewSize);
    }

    UiRect cameraShutterButtonBounds() {
        int shutterSize = cameraShutterSize();
        return phoneRect(cameraShutterU(), cameraShutterV(), shutterSize, shutterSize);
    }

    UiRect cameraFlipButtonBounds() {
        int shutterSize = cameraShutterSize();
        int flipButtonSize = Math.round(32 * scale);
        int flipU = cameraShutterU() + shutterSize + Math.max(2, Math.round(4 * scale));
        int flipV = cameraShutterV() + Math.max(0, (shutterSize - flipButtonSize) / 2);
        return phoneRect(flipU, flipV, flipButtonSize, flipButtonSize);
    }

    UiRect cameraRotateButtonBounds() {
        int buttonSize = Math.max(14, Math.round(20 * scale));
        int margin = Math.max(5, Math.round(8 * scale));
        return phoneRect(portraitDisplayWidth() - margin - buttonSize, margin, buttonSize, buttonSize);
    }

    UiRect cameraCaptureBounds() {
        return phoneRect(0, 0, portraitDisplayWidth(), Math.max(1, cameraShutterV()));
    }

    UiRect cameraControlBandBounds() {
        int bandV = Math.max(0, cameraShutterV());
        return phoneRect(0, bandV, portraitDisplayWidth(), Math.max(0, portraitDisplayHeight() - bandV));
    }

    UiRect cameraViewBounds() {
        return new UiRect(displayX, displayY, displayWidth, displayHeight);
    }
}
