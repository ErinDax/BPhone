package cn.erindax.bphone.client.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

final class PhoneChatLayout {
    static final int TAB_CHATS = 0;
    static final int TAB_CONTACTS = 1;
    static final int TAB_PROFILE = 2;
    static final int TAB_COUNT = 3;

    static final int PROFILE_ROW_NICKNAME = 0;
    static final int PROFILE_ROW_FRIENDS = 1;
    static final int PROFILE_ROW_QR = 2;
    static final int PROFILE_ROW_BLOCKED = 3;
    static final int PROFILE_ROW_COUNT = 4;

    static final int ADD_OPTION_ONLINE = 0;
    static final int ADD_OPTION_SCAN = 1;
    static final int ADD_OPTION_GROUP = 2;
    static final int ADD_OPTION_COUNT = 3;

    static final int ROW_REQUEST_SECTION = 0;
    static final int ROW_REQUEST = 1;
    static final int ROW_FRIEND_SECTION = 2;
    static final int ROW_FRIEND = 3;
    static final int ROW_HINT = 4;
    static final int ROW_CARD = 5;
    static final int ROW_ACTION = 6;
    static final int ROW_MEMBER_SECTION = 7;
    static final int ROW_MEMBER = 8;

    final Font font;
    final float scale;
    final int guiScale;
    final UiRect surface;
    final float titleScale;
    final float bodyScale;
    final float captionScale;
    final int titleHeight;
    final int bodyHeight;
    final int captionHeight;
    final int bodyLine;
    final int captionLine;
    final int pad;
    final int gap;
    final int textGap;
    final int radius;
    final int headerHeight;
    final int threadHeaderHeight;
    final int tabBarHeight;
    final int fieldHeight;
    final int composerHeight;
    final int buttonHeight;
    final int smallButtonHeight;
    final int avatar;
    final int avatarLarge;
    final int avatarSmall;
    final int rowHeight;
    final int itemHeight;
    final int sectionHeight;
    final int hintHeight;
    final int sectionGap;
    final int bubblePadX;
    final int bubblePadY;
    final int bubbleGap;
    final int bubbleRadius;
    final int messagePad;
    final int scrollStep;
    final int barHeight;
    final int cardHeight;

    PhoneChatLayout(PhoneScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        this.font = minecraft.font;
        this.scale = screen.scale;
        this.guiScale = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
        this.surface = new UiRect(screen.displayX, screen.displayY, screen.displayWidth, screen.displayHeight);
        this.titleScale = crisp(scale);
        this.bodyScale = crisp(0.82F * scale);
        this.captionScale = crisp(0.68F * scale);
        this.titleHeight = glyphHeight(titleScale);
        this.bodyHeight = glyphHeight(bodyScale);
        this.captionHeight = glyphHeight(captionScale);
        this.bodyLine = Math.max(bodyHeight + 1, Math.round(font.lineHeight * bodyScale));
        this.captionLine = Math.max(captionHeight + 1, Math.round(font.lineHeight * captionScale));
        this.pad = u(6);
        this.gap = u(4);
        this.textGap = Math.max(1, u(3));
        this.radius = Math.max(1, u(3));
        this.headerHeight = Math.max(u(24), titleHeight + u(12));
        this.threadHeaderHeight = Math.max(headerHeight, titleHeight + textGap + captionHeight + u(10));
        this.tabBarHeight = Math.max(u(26), bodyHeight + u(14));
        this.fieldHeight = Math.max(u(18), bodyHeight + u(10));
        this.composerHeight = Math.max(u(26), fieldHeight + u(8));
        this.buttonHeight = Math.max(u(20), bodyHeight + u(12));
        this.smallButtonHeight = Math.max(u(14), bodyHeight + u(6));
        this.avatar = faceSize(u(22));
        this.avatarLarge = faceSize(u(36));
        this.avatarSmall = faceSize(u(16));
        this.rowHeight = Math.max(avatar + u(10), bodyHeight + textGap + captionHeight + u(12));
        this.itemHeight = Math.max(Math.max(u(26), bodyHeight + u(16)), avatarSmall + u(8));
        this.sectionHeight = captionHeight + u(10);
        this.hintHeight = captionHeight + u(16);
        this.sectionGap = u(6);
        this.bubblePadX = u(5);
        this.bubblePadY = Math.max(2, u(3));
        this.bubbleGap = u(4);
        this.bubbleRadius = Math.max(1, u(4));
        this.messagePad = u(6);
        this.scrollStep = Math.max(8, Math.round(rowHeight * 0.6F));
        this.barHeight = fieldHeight + 2 * u(5);
        this.cardHeight = avatarLarge + 2 * u(10);
    }

    int u(float value) {
        return Math.max(1, Math.round(value * scale));
    }

    int width(Component text, float textScale) {
        return Mth.ceil(font.width(text) * textScale);
    }

    int width(FormattedCharSequence text, float textScale) {
        return Mth.ceil(font.width(text) * textScale);
    }

    private float crisp(float desired) {
        float snapped = Math.round(desired * guiScale) / (float) guiScale;
        float minimum = Math.min(1.0F, 2.0F / guiScale);
        return Mth.clamp(snapped, minimum, 1.0F);
    }

    private static int glyphHeight(float textScale) {
        return Math.max(1, Math.round(8.0F * textScale));
    }

    private int faceSize(int desired) {
        int best = desired;
        int bestDiff = Integer.MAX_VALUE;
        for (int size = Math.max(8, desired - 8); size <= desired + 8; size++) {
            if ((size * guiScale) % 8 == 0) {
                int diff = Math.abs(size - desired);
                if (diff < bestDiff) {
                    best = size;
                    bestDiff = diff;
                }
            }
        }
        return best;
    }

    UiRect header() {
        return new UiRect(surface.left, surface.top, surface.width, headerHeight);
    }

    UiRect threadHeader() {
        return new UiRect(surface.left, surface.top, surface.width, threadHeaderHeight);
    }

    UiRect backButton(UiRect header) {
        return new UiRect(header.left, header.top, header.height, header.height);
    }

    UiRect actionButton(UiRect header) {
        return new UiRect(header.right() - header.height, header.top, header.height, header.height);
    }

    UiRect tabBar() {
        return new UiRect(surface.left, surface.bottom() - tabBarHeight, surface.width, tabBarHeight);
    }

    UiRect tabSlot(int index) {
        UiRect bar = tabBar();
        int left = bar.left + bar.width * index / TAB_COUNT;
        int right = bar.left + bar.width * (index + 1) / TAB_COUNT;
        return new UiRect(left, bar.top, right - left, bar.height);
    }

    int tabAt(double mouseX) {
        for (int index = 0; index < TAB_COUNT; index++) {
            UiRect slot = tabSlot(index);
            if (mouseX >= slot.left && mouseX < slot.right()) {
                return index;
            }
        }
        return TAB_COUNT - 1;
    }

    UiRect listViewport() {
        int top = header().bottom();
        int bottom = tabBar().top;
        return new UiRect(surface.left, top, surface.width, Math.max(1, bottom - top));
    }

    UiRect pageViewport() {
        int top = header().bottom();
        return new UiRect(surface.left, top, surface.width, Math.max(1, surface.bottom() - top));
    }

    int listContentHeight(int count, boolean withHint) {
        if (count <= 0) {
            return 0;
        }
        return count * rowHeight + (withHint ? hintHeight : 0);
    }

    static int maxScroll(UiRect viewport, int contentHeight) {
        return Math.max(0, contentHeight - viewport.height);
    }

    UiRect listRow(UiRect viewport, int index, int scroll) {
        return new UiRect(viewport.left, viewport.top + index * rowHeight - scroll, viewport.width, rowHeight);
    }

    int listRowAt(UiRect viewport, int count, int scroll, double mouseX, double mouseY) {
        if (!viewport.contains(mouseX, mouseY) || mouseY >= viewport.bottom()) {
            return -1;
        }
        int local = Mth.floor(mouseY) - viewport.top + scroll;
        if (local < 0) {
            return -1;
        }
        int index = local / rowHeight;
        return index < count ? index : -1;
    }

    UiRect rowAvatar(UiRect row, int size) {
        return new UiRect(row.left + pad, row.top + (row.height - size) / 2, size, size);
    }

    int rowTextLeft(UiRect row, int size) {
        return row.left + pad + size + u(6);
    }

    int buttonWidth(Component label) {
        return Math.max(u(28), width(label, bodyScale) + 2 * u(6));
    }

    UiRect rowAction(UiRect row, Component label) {
        int width = buttonWidth(label);
        return new UiRect(row.right() - pad - width, row.top + (row.height - smallButtonHeight) / 2,
                width, smallButtonHeight);
    }

    UiRect[] requestButtons(UiRect row, Component accept, Component reject) {
        int rejectWidth = buttonWidth(reject);
        int acceptWidth = buttonWidth(accept);
        int top = row.top + (row.height - smallButtonHeight) / 2;
        int rejectLeft = row.right() - pad - rejectWidth;
        int acceptLeft = rejectLeft - gap - acceptWidth;
        return new UiRect[] {
                new UiRect(acceptLeft, top, acceptWidth, smallButtonHeight),
                new UiRect(rejectLeft, top, rejectWidth, smallButtonHeight)
        };
    }

    UiRect checkBox(UiRect row) {
        int size = Math.max(u(10), bodyHeight + u(2));
        return new UiRect(row.right() - pad - size, row.top + (row.height - size) / 2, size, size);
    }

    record Row(int kind, int index, int top, int height) {
        int bottom() {
            return top + height;
        }
    }

    List<Row> contactRows(int requestCount, int friendCount) {
        List<Row> rows = new ArrayList<>();
        int y = 0;
        if (requestCount > 0) {
            rows.add(new Row(ROW_REQUEST_SECTION, -1, y, sectionHeight));
            y += sectionHeight;
            for (int index = 0; index < requestCount; index++) {
                rows.add(new Row(ROW_REQUEST, index, y, rowHeight));
                y += rowHeight;
            }
        }
        if (friendCount > 0) {
            rows.add(new Row(ROW_FRIEND_SECTION, -1, y, sectionHeight));
            y += sectionHeight;
            for (int index = 0; index < friendCount; index++) {
                rows.add(new Row(ROW_FRIEND, index, y, rowHeight));
                y += rowHeight;
            }
            rows.add(new Row(ROW_HINT, -1, y, hintHeight));
        }
        return rows;
    }

    List<Row> infoRows(int actionCount, int memberCount) {
        List<Row> rows = new ArrayList<>();
        int y = 0;
        rows.add(new Row(ROW_CARD, -1, y, cardHeight));
        y += cardHeight + sectionGap;
        for (int index = 0; index < actionCount; index++) {
            rows.add(new Row(ROW_ACTION, index, y, itemHeight));
            y += itemHeight;
        }
        if (memberCount > 0) {
            rows.add(new Row(ROW_MEMBER_SECTION, -1, y, sectionHeight));
            y += sectionHeight;
            for (int index = 0; index < memberCount; index++) {
                rows.add(new Row(ROW_MEMBER, index, y, rowHeight));
                y += rowHeight;
            }
        }
        return rows;
    }

    static int contentHeight(List<Row> rows) {
        return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).bottom();
    }

    UiRect rowRect(UiRect viewport, Row row, int scroll) {
        return new UiRect(viewport.left, viewport.top + row.top() - scroll, viewport.width, row.height());
    }

    Row rowAt(UiRect viewport, List<Row> rows, int scroll, double mouseX, double mouseY) {
        if (!viewport.contains(mouseX, mouseY) || mouseY >= viewport.bottom()) {
            return null;
        }
        int local = Mth.floor(mouseY) - viewport.top + scroll;
        for (Row row : rows) {
            if (local >= row.top() && local < row.bottom()) {
                return row;
            }
        }
        return null;
    }

    UiRect profileCard() {
        int top = header().bottom() + sectionGap;
        return new UiRect(surface.left, top, surface.width, cardHeight);
    }

    UiRect profileAvatar() {
        UiRect card = profileCard();
        return cardAvatar(card);
    }

    UiRect cardAvatar(UiRect card) {
        return new UiRect(card.left + pad + u(2), card.top + (card.height - avatarLarge) / 2, avatarLarge, avatarLarge);
    }

    UiRect profileRow(int index) {
        int top = profileCard().bottom() + sectionGap + index * itemHeight;
        return new UiRect(surface.left, top, surface.width, itemHeight);
    }

    UiRect profileSaveButton(Component label) {
        UiRect row = profileRow(0);
        return inlineSave(row, label);
    }

    UiRect profileEditField(Component saveLabel) {
        UiRect row = profileRow(0);
        return inlineField(row, saveLabel);
    }

    UiRect inlineSave(UiRect row, Component label) {
        int width = buttonWidth(label);
        return new UiRect(row.right() - pad - width, row.top + (row.height - fieldHeight) / 2, width, fieldHeight);
    }

    UiRect inlineField(UiRect row, Component saveLabel) {
        UiRect save = inlineSave(row, saveLabel);
        int left = row.left + pad;
        return new UiRect(left, row.top + (row.height - fieldHeight) / 2, Math.max(u(20), save.left - gap - left), fieldHeight);
    }

    int profileHintTop() {
        return profileRow(PROFILE_ROW_COUNT - 1).bottom() + u(4);
    }

    UiRect profileAddButton(boolean editing) {
        int top = editing ? profileHintTop() + captionHeight + u(8) : profileRow(PROFILE_ROW_COUNT - 1).bottom() + u(10);
        return new UiRect(surface.left + pad, top, surface.width - 2 * pad, buttonHeight);
    }

    int addLabelTop() {
        return header().bottom() + u(8);
    }

    UiRect addField() {
        int top = addLabelTop() + captionHeight + u(4);
        return new UiRect(surface.left + pad, top, surface.width - 2 * pad, buttonHeight);
    }

    int addErrorTop() {
        return addField().bottom() + u(4);
    }

    UiRect addButton() {
        int top = addErrorTop() + captionHeight + u(6);
        return new UiRect(surface.left + pad, top, surface.width - 2 * pad, buttonHeight);
    }

    UiRect addOption(int index) {
        int top = addButton().bottom() + u(12) + index * itemHeight;
        return new UiRect(surface.left, top, surface.width, itemHeight);
    }

    int addFooterTop() {
        return addOption(ADD_OPTION_COUNT - 1).bottom() + u(8);
    }

    UiRect composer() {
        return new UiRect(surface.left, surface.bottom() - composerHeight, surface.width, composerHeight);
    }

    UiRect sendButton(Component label) {
        UiRect bar = composer();
        int width = buttonWidth(label);
        return new UiRect(bar.right() - pad - width, bar.top + (bar.height - fieldHeight) / 2, width, fieldHeight);
    }

    UiRect imageButton() {
        UiRect bar = composer();
        return new UiRect(bar.left + pad, bar.top + (bar.height - fieldHeight) / 2, fieldHeight, fieldHeight);
    }

    UiRect draftField(Component sendLabel) {
        UiRect bar = composer();
        UiRect send = sendButton(sendLabel);
        int left = imageButton().right() + gap;
        return new UiRect(left, bar.top + (bar.height - fieldHeight) / 2, Math.max(u(20), send.left - gap - left), fieldHeight);
    }

    UiRect threadMessages() {
        int top = threadHeader().bottom();
        int bottom = composer().top;
        return new UiRect(surface.left, top, surface.width, Math.max(1, bottom - top));
    }

    UiRect latestPill(UiRect viewport, Component label) {
        int width = width(label, captionScale) + 2 * u(6);
        int height = captionHeight + 2 * u(3);
        return new UiRect(viewport.left + (viewport.width - width) / 2, viewport.bottom() - u(6) - height, width, height);
    }

    UiRect topBar() {
        return new UiRect(surface.left, header().bottom(), surface.width, barHeight);
    }

    UiRect topBarAction(Component label) {
        UiRect bar = topBar();
        int width = buttonWidth(label);
        return new UiRect(bar.right() - pad - width, bar.top + (bar.height - fieldHeight) / 2, width, fieldHeight);
    }

    UiRect topBarField(Component label) {
        UiRect bar = topBar();
        int right = label == null ? bar.right() - pad : topBarAction(label).left - gap;
        int left = bar.left + pad;
        return new UiRect(left, bar.top + (bar.height - fieldHeight) / 2, Math.max(u(20), right - left), fieldHeight);
    }

    UiRect bottomBar() {
        int height = buttonHeight + 2 * u(5);
        return new UiRect(surface.left, surface.bottom() - height, surface.width, height);
    }

    UiRect bottomBarButton() {
        UiRect bar = bottomBar();
        return new UiRect(bar.left + pad, bar.top + (bar.height - buttonHeight) / 2, bar.width - 2 * pad, buttonHeight);
    }

    UiRect viewportBetween(int top, int bottom) {
        return new UiRect(surface.left, top, surface.width, Math.max(1, bottom - top));
    }

    int gridColumns() {
        return 3;
    }

    int gridCell() {
        int columns = gridColumns();
        return Math.max(u(16), (surface.width - (columns + 1) * gap) / columns);
    }

    UiRect gridSlot(UiRect viewport, int index, int scroll) {
        int columns = gridColumns();
        int cell = gridCell();
        int column = index % columns;
        int row = index / columns;
        int left = viewport.left + gap + column * (cell + gap);
        int top = viewport.top + gap + row * (cell + gap) - scroll;
        return new UiRect(left, top, cell, cell);
    }

    int gridContentHeight(int count) {
        if (count <= 0) {
            return 0;
        }
        int rows = (count + gridColumns() - 1) / gridColumns();
        return gap + rows * (gridCell() + gap);
    }

    int gridIndexAt(UiRect viewport, int count, int scroll, double mouseX, double mouseY) {
        if (!viewport.contains(mouseX, mouseY) || mouseY >= viewport.bottom()) {
            return -1;
        }
        for (int index = 0; index < count; index++) {
            if (gridSlot(viewport, index, scroll).contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }
}
