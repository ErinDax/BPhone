package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.CallLogEntry;
import cn.erindax.bphone.phone.PhoneContact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

final class PhoneCallSurfaceRenderer {
    private static final String[] BACKSPACE_ICON = {
            "....##########",
            "...#.........#",
            "..#..#....#..#",
            ".#....#..#...#",
            "#......##....#",
            "#......##....#",
            ".#....#..#...#",
            "..#..#....#..#",
            "...#.........#",
            "....##########"
    };

    private PhoneCallSurfaceRenderer() {
    }

    static void renderCallAppSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Font font = PhoneFont.get();
        int radius = Math.max(3, Math.round(6 * screen.scale));
        UiRect contentBounds = screen.getCallSurfaceBounds();
        UiRect numberBounds = screen.getCallNumberDisplayBounds();
        String ownNumber = screen.getOwnPhoneNumber();
        int headerHeight = screen.getCallHeaderHeight();
        int rowTop = contentBounds.top + Math.max(5, Math.round(6 * screen.scale));
        int rowHeight = Math.max(14, Math.round(16 * screen.scale));
        int chipPadding = Math.max(4, Math.round(5 * screen.scale));
        int ownChipWidth = getOwnNumberChipWidth(font, ownNumber, chipPadding, screen.scale);
        int ownChipX = contentBounds.right() - ownChipWidth - Math.max(4, Math.round(6 * screen.scale));
        int titlePaddingLeft = Math.max(5, Math.round(7 * screen.scale));

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth, screen.displayY + screen.displayHeight, PhoneUi.PAGE);
        guiGraphics.fill(contentBounds.left, contentBounds.top, contentBounds.right(), contentBounds.top + headerHeight, PhoneUi.SURFACE);
        guiGraphics.fill(contentBounds.left, contentBounds.top + headerHeight - 1, contentBounds.right(), contentBounds.top + headerHeight, PhoneUi.HAIRLINE);

        Component titleText = Component.translatable("screen.bphone.phone.call.title");
        PhoneUi.fillRounded(guiGraphics, ownChipX, rowTop, ownChipWidth, rowHeight, rowHeight / 2, PhoneUi.ACCENT_SOFT);
        renderOwnNumberChip(guiGraphics, font, ownNumber, ownChipX, rowTop, ownChipWidth, rowHeight, screen.scale);

        int titleMaxWidth = Math.max(24, ownChipX - contentBounds.left - titlePaddingLeft - Math.round(8 * screen.scale));
        float titleScale = PhoneScreenDraw.textScaleToFit(font, titleText, titleMaxWidth, 0.45F);
        int titleHeight = PhoneScreenDraw.scaledTextHeight(font, titleScale);
        int titleX = contentBounds.left + titlePaddingLeft;
        int titleY = rowTop + Math.max(0, (rowHeight - titleHeight) / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, titleText, titleX, titleY, PhoneUi.TEXT, false, titleScale);

        int numInset = Math.max(4, Math.round(6 * screen.scale));
        PhoneUi.fillRounded(guiGraphics, numberBounds.left + numInset, numberBounds.top,
                numberBounds.width - 2 * numInset, numberBounds.height, radius, PhoneUi.SURFACE);
        PhoneUi.strokeRounded(guiGraphics, numberBounds.left + numInset, numberBounds.top,
                numberBounds.width - 2 * numInset, numberBounds.height, radius, 1, PhoneUi.HAIRLINE);

        Component numberText = screen.dialedNumber.isEmpty()
                ? Component.translatable("screen.bphone.phone.call.placeholder")
                : Component.literal(screen.dialedNumber);
        float numberScale = PhoneScreenDraw.textScaleToFit(font, numberText, numberBounds.width - Math.round(16 * screen.scale));
        int numberTextWidth = PhoneScreenDraw.scaledTextWidth(font, numberText, numberScale);
        int numberTextHeight = PhoneScreenDraw.scaledTextHeight(font, numberScale);
        int numberTextX = numberBounds.left + (numberBounds.width - numberTextWidth) / 2;
        int numberTextY = numberBounds.top + (numberBounds.height - numberTextHeight) / 2;
        int numberTextColor = screen.dialedNumber.isEmpty() ? PhoneUi.TEXT_TERTIARY : PhoneUi.TEXT;
        PhoneScreenDraw.drawScaledText(guiGraphics, font, numberText, numberTextX, numberTextY,
                numberTextColor, false, numberScale);

        for (int i = 0; i < PhoneScreen.CALL_DIAL_DIGITS.length; i++) {
            String digit = PhoneScreen.CALL_DIAL_DIGITS[i];
            if (digit.isEmpty()) {
                continue;
            }

            UiRect buttonBounds = screen.getDialPadCellBounds(i);
            boolean hovered = PhoneUi.hovered(screen, buttonBounds);
            renderDialPadButton(guiGraphics, font, buttonBounds, Component.literal(digit),
                    hovered ? PhoneUi.HOVER : PhoneUi.SURFACE, PhoneUi.HAIRLINE, PhoneUi.TEXT, screen.scale);
        }

        renderDialDeleteButton(guiGraphics, screen.getDialDeleteButtonBounds(),
                screen.dialedNumber.isEmpty() ? PhoneUi.TEXT_TERTIARY : PhoneUi.TEXT_SECONDARY);
        UiRect callBounds = screen.getDialCallButtonBounds();
        renderHandsetCircleButton(guiGraphics, callBounds, PhoneUi.SUCCESS, false);
        renderCallMenuTabs(screen, guiGraphics, true, false, false);
    }

    private static void renderDialDeleteButton(GuiGraphics guiGraphics, UiRect bounds, int color) {
        int density = PhoneUi.pixelDensity();
        int columns = BACKSPACE_ICON[0].length();
        int rows = BACKSPACE_ICON.length;
        int pixel = Math.max(1, Math.round(bounds.width * density * 0.5F / columns));
        int left = bounds.left * density + (bounds.width * density - columns * pixel) / 2;
        int top = bounds.top * density + (bounds.height * density - rows * pixel) / 2;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        for (int row = 0; row < rows; row++) {
            String line = BACKSPACE_ICON[row];
            for (int column = 0; column < columns; column++) {
                if (line.charAt(column) == '#') {
                    int x = left + column * pixel;
                    int y = top + row * pixel;
                    guiGraphics.fill(x, y, x + pixel, y + pixel, color);
                }
            }
        }
        guiGraphics.pose().popPose();
    }

    static void renderCallRecentsSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Font font = PhoneFont.get();
        float captionScale = new PhoneChatLayout(screen).captionScale;
        UiRect contentBounds = screen.getCallSurfaceBounds();
        int headerHeight = screen.getCallHeaderHeight();

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth, screen.displayY + screen.displayHeight, PhoneUi.PAGE);
        guiGraphics.fill(contentBounds.left, contentBounds.top, contentBounds.right(), contentBounds.top + headerHeight, PhoneUi.SURFACE);
        guiGraphics.fill(contentBounds.left, contentBounds.top + headerHeight - 1, contentBounds.right(), contentBounds.top + headerHeight, PhoneUi.HAIRLINE);

        Component titleText = Component.translatable("screen.bphone.phone.call.recents.title");
        int titleMaxWidth = Math.max(24, contentBounds.width - Math.max(16, Math.round(20 * screen.scale)));
        float titleScale = PhoneScreenDraw.textScaleToFit(font, titleText, titleMaxWidth, 0.45F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, titleText,
                contentBounds.left + Math.max(8, Math.round(10 * screen.scale)),
                contentBounds.top + Math.max(6, Math.round(8 * screen.scale)),
                PhoneUi.TEXT, false, titleScale);

        List<CallLogEntry> entries = PhoneClientCallState.getCallLogEntries();
        int rowHeight = Math.max(16, Math.round(20 * screen.scale));
        int topPadding = Math.max(8, Math.round(10 * screen.scale));
        int top = contentBounds.top + headerHeight + topPadding;
        int left = contentBounds.left + Math.max(6, Math.round(8 * screen.scale));
        int width = contentBounds.width - Math.max(12, Math.round(16 * screen.scale));
        int bottomClip = contentBounds.bottom() - Math.max(8, Math.round(10 * screen.scale));
        int visibleRows = Math.max(1, (bottomClip - top) / rowHeight);
        int scrollOffset = screen.recentsScrollOffset;

        if (entries.isEmpty()) {
            drawCenteredFittedText(guiGraphics, font,
                    Component.translatable("screen.bphone.phone.call.recents.empty"),
                    contentBounds.left + contentBounds.width / 2, top + rowHeight * 2,
                    width, PhoneUi.TEXT_SECONDARY, false, 0.35F);
        } else {
            int end = Math.min(entries.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < end; i++) {
                CallLogEntry entry = entries.get(i);
                int y = top + (i - scrollOffset) * rowHeight;
                if (y + rowHeight < contentBounds.top || y > bottomClip) {
                    continue;
                }
                PhoneUi.fillRounded(guiGraphics, left, y, width, rowHeight - 1, Math.max(2, Math.round(4 * screen.scale)), PhoneUi.SURFACE);

                int indicatorSize = Math.max(8, Math.round(10 * screen.scale));
                int indicatorX = left + Math.max(3, Math.round(4 * screen.scale));
                int indicatorY = y + (rowHeight - indicatorSize) / 2;

                String typeIndicator;
                int typeColor;
                switch (entry.callType()) {
                    case "INCOMING" -> { typeIndicator = "\u2193"; typeColor = PhoneUi.SUCCESS; }
                    case "MISSED" -> { typeIndicator = "\u2715"; typeColor = PhoneUi.DANGER; }
                    default -> { typeIndicator = "\u2191"; typeColor = PhoneUi.ACCENT; }
                }
                PhoneUi.fillRounded(guiGraphics, indicatorX, indicatorY, indicatorSize, indicatorSize, indicatorSize / 2, typeColor);
                float indScale = Math.min(0.75F, (indicatorSize - 2) / (float) Math.max(1, font.lineHeight));
                int indW = PhoneScreenDraw.scaledTextWidth(font, Component.literal(typeIndicator), indScale);
                int indH = PhoneScreenDraw.scaledTextHeight(font, indScale);
                PhoneScreenDraw.drawScaledText(guiGraphics, font, Component.literal(typeIndicator),
                        indicatorX + (indicatorSize - indW) / 2, indicatorY + (indicatorSize - indH) / 2,
                        0xFFFFFFFF, false, indScale);

                int textLeft = indicatorX + indicatorSize + Math.max(4, Math.round(5 * screen.scale));
                int textWidth = width - (textLeft - left);

                String name = entry.otherName() == null || entry.otherName().isBlank() ? entry.otherNumber() : entry.otherName();
                Component nameText = Component.literal(name);
                float nameScale = PhoneScreenDraw.textScaleToFit(font, nameText,
                        textWidth - Math.max(40, Math.round(50 * screen.scale)), 0.35F);
                PhoneScreenDraw.drawScaledText(guiGraphics, font, nameText,
                        textLeft, y + Math.max(1, Math.round(2 * screen.scale)),
                        PhoneUi.TEXT, false, nameScale);

                Component timeText = formatRelativeTime(entry.timestamp());
                float timeScale = Math.min(captionScale, PhoneScreenDraw.textScaleToFit(font, timeText,
                        Math.max(30, Math.round(40 * screen.scale)), 0.33F));
                int timeWidth = PhoneScreenDraw.scaledTextWidth(font, timeText, timeScale);
                int timeX = left + width - timeWidth - Math.max(3, Math.round(4 * screen.scale));
                int timeY = y + Math.max(1, Math.round(2 * screen.scale));
                PhoneScreenDraw.drawScaledText(guiGraphics, font, timeText, timeX, timeY, PhoneUi.TEXT_TERTIARY, false, timeScale);

                Component subLabel = entry.callType().equals("INCOMING") || entry.callType().equals("OUTGOING")
                        ? formatDuration(entry.durationTicks()) : null;
                if (subLabel != null) {
                    float subScale = Math.min(captionScale, PhoneScreenDraw.textScaleToFit(font, subLabel,
                            textWidth, 0.33F));
                    int subY = y + Math.round(rowHeight * 0.55F);
                    PhoneScreenDraw.drawScaledText(guiGraphics, font, subLabel,
                            textLeft, subY, PhoneUi.TEXT_SECONDARY, false, subScale);
                }
            }

            if (entries.size() > visibleRows) {
                int scrollBarLeft = left + width - Math.max(2, Math.round(3 * screen.scale));
                int scrollBarWidth = Math.max(2, Math.round(3 * screen.scale));
                int scrollBarHeight = bottomClip - top;
                int thumbHeight = Math.max(8, scrollBarHeight * visibleRows / entries.size());
                int thumbTop = top + (scrollBarHeight - thumbHeight) * scrollOffset / Math.max(1, entries.size() - visibleRows);
                guiGraphics.fill(scrollBarLeft, top, scrollBarLeft + scrollBarWidth, bottomClip, 0x20AAAAAA);
                guiGraphics.fill(scrollBarLeft, thumbTop, scrollBarLeft + scrollBarWidth, thumbTop + thumbHeight, 0x60AAAAAA);
            }
        }

        renderCallMenuTabs(screen, guiGraphics, false, false, true);
    }

    private static Component formatRelativeTime(long timestampMs) {
        long now = System.currentTimeMillis();
        long diff = now - timestampMs;
        if (diff < 0) return Component.empty();
        long seconds = diff / 1000;
        if (seconds < 60) return Component.translatable("screen.bphone.phone.call.time.just_now");
        long minutes = seconds / 60;
        if (minutes < 60) return Component.translatable("screen.bphone.phone.call.time.minutes_ago", minutes);
        long hours = minutes / 60;
        if (hours < 24) return Component.translatable("screen.bphone.phone.call.time.hours_ago", hours);
        long days = hours / 24;
        if (days == 1) return Component.translatable("screen.bphone.phone.call.time.yesterday");
        return Component.translatable("screen.bphone.phone.call.time.days_ago", days);
    }

    private static Component formatDuration(int ticks) {
        if (ticks <= 0) return null;
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0) {
            return Component.translatable("screen.bphone.phone.call.duration.minutes", minutes, seconds);
        }
        return Component.translatable("screen.bphone.phone.call.duration.seconds", seconds);
    }

    static void renderCallContactsSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Font font = PhoneFont.get();
        int radius = Math.max(3, Math.round(5 * screen.scale));
        UiRect contentBounds = screen.getCallSurfaceBounds();
        String ownNumber = screen.getOwnPhoneNumber();
        int headerHeight = screen.getCallHeaderHeight();
        int rowTop = contentBounds.top + Math.max(5, Math.round(6 * screen.scale));
        int rowHeight = Math.max(14, Math.round(16 * screen.scale));
        int chipPadding = Math.max(4, Math.round(5 * screen.scale));
        int ownChipWidth = getOwnNumberChipWidth(font, ownNumber, chipPadding, screen.scale);
        int ownChipX = contentBounds.right() - ownChipWidth - Math.max(4, Math.round(6 * screen.scale));
        int titlePaddingLeft = Math.max(5, Math.round(7 * screen.scale));
        int panelTop = contentBounds.top + headerHeight + Math.max(4, Math.round(5 * screen.scale));
        int panelBottom = screen.getCallDialMenuBounds().top - screen.getCallMenuBottomReserve();
        UiRect saveButtonBounds = screen.getContactSaveButtonBounds();
        String saveCandidateNumber = screen.getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !screen.hasSavedContact(saveCandidateNumber);
        List<PhoneContact> contacts = screen.getPhoneContacts();

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth, screen.displayY + screen.displayHeight, PhoneUi.PAGE);
        guiGraphics.fill(contentBounds.left, contentBounds.top, contentBounds.right(), contentBounds.top + headerHeight, PhoneUi.SURFACE);
        guiGraphics.fill(contentBounds.left, contentBounds.top + headerHeight - 1, contentBounds.right(), contentBounds.top + headerHeight, PhoneUi.HAIRLINE);

        Component titleText = Component.translatable("screen.bphone.phone.call.contacts.title");
        PhoneUi.fillRounded(guiGraphics, ownChipX, rowTop, ownChipWidth, rowHeight, rowHeight / 2, PhoneUi.ACCENT_SOFT);
        renderOwnNumberChip(guiGraphics, font, ownNumber, ownChipX, rowTop, ownChipWidth, rowHeight, screen.scale);

        int titleMaxWidth = Math.max(24, ownChipX - contentBounds.left - titlePaddingLeft - Math.round(8 * screen.scale));
        float titleScale = PhoneScreenDraw.textScaleToFit(font, titleText, titleMaxWidth, 0.45F);
        int titleHeight = PhoneScreenDraw.scaledTextHeight(font, titleScale);
        int titleX = contentBounds.left + titlePaddingLeft;
        int titleY = rowTop + Math.max(0, (rowHeight - titleHeight) / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, titleText, titleX, titleY, PhoneUi.TEXT, false, titleScale);

        UiRect addButtonBounds = screen.getAddContactHeaderButtonBounds();
        PhoneUi.fillRounded(guiGraphics, addButtonBounds.left, addButtonBounds.top, addButtonBounds.width, addButtonBounds.height,
                radius, PhoneUi.hovered(screen, addButtonBounds) ? PhoneUi.ACCENT_HOVER : PhoneUi.ACCENT);
        Component addText = Component.translatable("screen.bphone.phone.call.contacts.add_label");
        int addTextMaxWidth = Math.max(20, addButtonBounds.width - Math.max(8, Math.round(10 * screen.scale)));
        float addTextScale = PhoneScreenDraw.textScaleToFit(font, addText, addTextMaxWidth, 0.38F);
        int addTextWidth = PhoneScreenDraw.scaledTextWidth(font, addText, addTextScale);
        int addTextHeight = PhoneScreenDraw.scaledTextHeight(font, addTextScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, addText,
                addButtonBounds.left + (addButtonBounds.width - addTextWidth) / 2,
                addButtonBounds.top + (addButtonBounds.height - addTextHeight) / 2,
                0xFFFFFFFF, false, addTextScale);

        if (canSaveNumber) {
            PhoneUi.fillRounded(guiGraphics, saveButtonBounds.left, saveButtonBounds.top, saveButtonBounds.width, saveButtonBounds.height,
                    radius, PhoneUi.hovered(screen, saveButtonBounds) ? PhoneUi.HOVER : PhoneUi.SURFACE);
            PhoneUi.strokeRounded(guiGraphics, saveButtonBounds.left, saveButtonBounds.top, saveButtonBounds.width, saveButtonBounds.height,
                    radius, 1, PhoneUi.HAIRLINE);
            Component saveText = Component.translatable("screen.bphone.phone.call.contacts.save_current", saveCandidateNumber);
            float saveScale = PhoneScreenDraw.textScaleToFit(font, saveText,
                    saveButtonBounds.width - Math.round(10 * screen.scale), 0.40F);
            int saveTextWidth = PhoneScreenDraw.scaledTextWidth(font, saveText, saveScale);
            int saveTextHeight = PhoneScreenDraw.scaledTextHeight(font, saveScale);
            int saveTextX = saveButtonBounds.left + (saveButtonBounds.width - saveTextWidth) / 2;
            int saveTextY = saveButtonBounds.top + (saveButtonBounds.height - saveTextHeight) / 2;
            PhoneScreenDraw.drawScaledText(guiGraphics, font, saveText, saveTextX, saveTextY, PhoneUi.ACCENT, false, saveScale);
        }

        if (contacts.isEmpty()) {
            Component emptyText = Component.translatable("screen.bphone.phone.call.contacts.empty");
            Component hintText = canSaveNumber
                    ? Component.translatable("screen.bphone.phone.call.contacts.hint_save")
                    : Component.translatable("screen.bphone.phone.call.contacts.hint");
            int textAreaLeft = contentBounds.left + Math.max(7, Math.round(8 * screen.scale));
            int textAreaRight = contentBounds.right() - Math.max(7, Math.round(8 * screen.scale));
            int textAreaWidth = textAreaRight - textAreaLeft;
            int emptyAreaTop = canSaveNumber ? saveButtonBounds.bottom() + Math.max(5, Math.round(6 * screen.scale)) : panelTop;
            int emptyAreaHeight = panelBottom - emptyAreaTop;
            float emptyScale = PhoneScreenDraw.textScaleToFit(font, emptyText,
                    textAreaWidth, 0.40F);
            float hintScale = PhoneScreenDraw.textScaleToFit(font, hintText,
                    textAreaWidth, 0.35F);
            int emptyWidth = PhoneScreenDraw.scaledTextWidth(font, emptyText, emptyScale);
            int hintWidth = PhoneScreenDraw.scaledTextWidth(font, hintText, hintScale);
            int emptyX = textAreaLeft + ((textAreaWidth - emptyWidth) / 2);
            int emptyY = emptyAreaTop + (emptyAreaHeight / 2) - Math.max(8, Math.round(9 * screen.scale));
            int hintX = textAreaLeft + ((textAreaWidth - hintWidth) / 2);
            int hintY = emptyY + PhoneScreenDraw.scaledTextHeight(font, emptyScale) + Math.max(4, Math.round(5 * screen.scale));
            PhoneScreenDraw.drawScaledText(guiGraphics, font, emptyText, emptyX, emptyY, PhoneUi.TEXT_SECONDARY, false, emptyScale);
            PhoneScreenDraw.drawScaledText(guiGraphics, font, hintText, hintX, hintY, PhoneUi.TEXT_TERTIARY, false, hintScale);
        } else {
            for (int index = 0; index < contacts.size(); index++) {
                PhoneContact contact = contacts.get(index);
                UiRect rowBounds = screen.getContactRowBounds(index, contacts.size());
                UiRect deleteBounds = screen.getContactDeleteButtonBounds(index, contacts.size());
                UiRect editBounds = screen.getContactEditButtonBounds(index, contacts.size());
                PhoneUi.fillRounded(guiGraphics, rowBounds.left, rowBounds.top, rowBounds.width, rowBounds.height,
                        radius, PhoneUi.hovered(screen, rowBounds) ? PhoneUi.HOVER : PhoneUi.SURFACE);

                Component nameText = Component.literal(contact.displayName());
                Component numberText = Component.literal(contact.number());
                Component lineText = contact.displayName().equals(contact.number())
                        ? numberText : nameText;
                int textLeft = rowBounds.left + Math.max(5, Math.round(6 * screen.scale));
                int textRight = editBounds.left - Math.max(4, Math.round(6 * screen.scale));
                int textMaxWidth = Math.max(18, textRight - textLeft);
                boolean showNumberOnly = contact.displayName().equals(contact.number());
                float lineScale = PhoneScreenDraw.textScaleToFit(font, lineText, textMaxWidth, showNumberOnly ? 0.35F : 0.40F);
                int lineHeight = PhoneScreenDraw.scaledTextHeight(font, lineScale);
                PhoneScreenDraw.drawScaledText(guiGraphics, font, lineText, textLeft,
                        rowBounds.top + (rowBounds.height - lineHeight) / 2,
                        showNumberOnly ? PhoneUi.TEXT_SECONDARY : PhoneUi.TEXT, false, lineScale);

                guiGraphics.blit(PhoneScreen.DELETE_BUTTON_TEXTURE, deleteBounds.left, deleteBounds.top,
                        deleteBounds.width, deleteBounds.height, 0.0F, 0.0F, 24, 24, 24, 24);

                PhoneUi.fillRounded(guiGraphics, editBounds.left, editBounds.top, editBounds.width, editBounds.height,
                        Math.max(2, editBounds.width / 4), PhoneUi.ACCENT);
                Component editIcon = Component.literal("✎");
                float editScale = PhoneScreenDraw.textScaleToFit(font, editIcon,
                        editBounds.width - Math.max(2, Math.round(3 * screen.scale)), 0.40F);
                int editIconWidth = PhoneScreenDraw.scaledTextWidth(font, editIcon, editScale);
                int editIconHeight = PhoneScreenDraw.scaledTextHeight(font, editScale);
                PhoneScreenDraw.drawScaledText(guiGraphics, font, editIcon,
                        editBounds.left + (editBounds.width - editIconWidth) / 2,
                        editBounds.top + (editBounds.height - editIconHeight) / 2,
                        0xFFFFFFFF, false, editScale);
            }
        }

        renderCallMenuTabs(screen, guiGraphics, false, true, false);
    }

    static void renderCallSessionSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Font font = PhoneFont.get();
        if (screen.activeCallNumber.isEmpty()) {
            screen.endCallSession(true);
            return;
        }

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth,
                screen.displayY + screen.displayHeight, PhoneUi.PAGE);
        UiRect contentBounds = screen.getCallSurfaceBounds();
        int numberInsetX = Math.max(7, Math.round(9 * screen.scale));

        int statusY = contentBounds.top + Math.max(24, Math.round(32 * screen.scale));
        int nameBlockTop = statusY + Math.max(24, Math.round(28 * screen.scale));
        UiRect sessionNumberBounds = new UiRect(
                contentBounds.left + numberInsetX,
                nameBlockTop,
                Math.max(24, contentBounds.width - (numberInsetX * 2)),
                Math.max(30, Math.round(44 * screen.scale)));
        int subY = sessionNumberBounds.bottom() + Math.max(6, Math.round(8 * screen.scale));

        Component statusText = Component.translatable(screen.activeCallConnected
                ? "screen.bphone.phone.call.status.connected"
                : screen.activeCallMissed
                ? "screen.bphone.phone.call.status.no_answer_short"
                : screen.activeCallIncoming
                ? "screen.bphone.phone.call.status.incoming"
                : "screen.bphone.phone.call.status.dialing");
        int textHorizontalInset = Math.max(6, Math.round(8 * screen.scale));
        int sessionTextMaxWidth = Math.max(18, contentBounds.width - (textHorizontalInset * 2));
        drawCenteredFittedText(guiGraphics, font, statusText,
                contentBounds.left + (contentBounds.width / 2), statusY, sessionTextMaxWidth,
                PhoneUi.TEXT_SECONDARY, false, 0.40F);

        String activeName = screen.activeCallName == null ? "" : screen.activeCallName.strip();
        String activeNumber = screen.activeCallNumber == null ? "" : screen.activeCallNumber.strip();
        boolean showSubNumber = !activeName.isBlank() && !activeName.equals(activeNumber);
        Component numberText = Component.literal(activeName.isBlank() ? activeNumber : activeName);
        float numberScale = PhoneScreenDraw.textScaleToFit(font, numberText,
                sessionNumberBounds.width - Math.round(10 * screen.scale), 0.40F);
        int numberWidth = PhoneScreenDraw.scaledTextWidth(font, numberText, numberScale);
        int numberHeight = PhoneScreenDraw.scaledTextHeight(font, numberScale);
        int numberX = sessionNumberBounds.left + (sessionNumberBounds.width - numberWidth) / 2;
        int lineGap = Math.max(6, Math.round(8 * screen.scale));
        int subNumberHeight = 0;
        float subNumberScale = 0.0F;
        Component subNumberText = null;
        if (showSubNumber) {
            subNumberText = Component.literal(activeNumber);
            subNumberScale = PhoneScreenDraw.textScaleToFit(font, subNumberText,
                    sessionNumberBounds.width - Math.round(12 * screen.scale), 0.40F);
            subNumberHeight = PhoneScreenDraw.scaledTextHeight(font, subNumberScale);
        }
        int textGroupHeight = numberHeight + (showSubNumber ? lineGap + subNumberHeight : 0);
        int numberY = sessionNumberBounds.top + Math.max(0, (sessionNumberBounds.height - textGroupHeight) / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, numberText, numberX, numberY, PhoneUi.TEXT, false, numberScale);

        if (showSubNumber) {
            int subNumberWidth = PhoneScreenDraw.scaledTextWidth(font, subNumberText, subNumberScale);
            int subNumberX = sessionNumberBounds.left + (sessionNumberBounds.width - subNumberWidth) / 2;
            int subNumberY = numberY + numberHeight + lineGap;
            PhoneScreenDraw.drawScaledText(guiGraphics, font, subNumberText, subNumberX, subNumberY,
                    PhoneUi.TEXT_SECONDARY, false, subNumberScale);
        }

        Component subText = screen.activeCallConnected
                ? Component.literal(screen.formatCallDuration())
                : screen.activeCallMissed
                ? Component.translatable("screen.bphone.phone.call.status.no_answer_detail")
                : screen.activeCallIncoming
                ? Component.translatable("screen.bphone.phone.call.status.tap_connect")
                : Component.translatable("screen.bphone.phone.call.status.waiting_answer");
        drawCenteredFittedText(guiGraphics, font, subText,
                contentBounds.left + (contentBounds.width / 2), subY, sessionTextMaxWidth,
                PhoneUi.TEXT_TERTIARY, false, 0.40F);

        UiRect connectButtonBounds = screen.getCallConnectButtonBounds();
        UiRect hangupButtonBounds = screen.getCallHangupButtonBounds();

        if (screen.activeCallIncoming && !screen.activeCallConnected) {
            int gap = Math.max(5, Math.round(6 * screen.scale));
            int incomingHangupX = connectButtonBounds.right() + gap;
            renderHandsetCircleButton(guiGraphics, connectButtonBounds, PhoneUi.SUCCESS, false);
            renderHandsetCircleButton(guiGraphics, new UiRect(incomingHangupX, connectButtonBounds.top,
                    connectButtonBounds.width, connectButtonBounds.height), PhoneUi.DANGER, true);
        } else {
            renderHandsetCircleButton(guiGraphics, hangupButtonBounds, PhoneUi.DANGER, true);
        }

        if (screen.activeCallConnected) {
            UiRect muteBounds = screen.getCallMuteButtonBounds();
            UiRect speakerBounds = screen.getCallSpeakerButtonBounds();
            renderCallActionButton(guiGraphics, muteBounds,
                    Component.translatable("screen.bphone.phone.call.key.mute"),
                    screen.callMuted ? PhoneUi.DANGER : PhoneUi.SURFACE,
                    screen.callMuted ? PhoneUi.DANGER : PhoneUi.HAIRLINE,
                    screen.callMuted ? 0xFFFFFFFF : PhoneUi.TEXT_SECONDARY);
            renderCallActionButton(guiGraphics, speakerBounds,
                    Component.translatable("screen.bphone.phone.call.key.speaker"),
                    screen.callSpeakerEnabled ? PhoneUi.ACCENT : PhoneUi.SURFACE,
                    screen.callSpeakerEnabled ? PhoneUi.ACCENT : PhoneUi.HAIRLINE,
                    screen.callSpeakerEnabled ? 0xFFFFFFFF : PhoneUi.TEXT_SECONDARY);
        }
    }

    private static void renderCallActionButton(GuiGraphics guiGraphics, UiRect bounds, Component label,
                                                 int fillColor, int borderColor, int textColor) {
        int r = Math.max(3, bounds.height / 3);
        PhoneUi.fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, r, fillColor);
        PhoneUi.strokeRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, r, 1, borderColor);
        Font font = PhoneFont.get();
        float labelScale = PhoneScreenDraw.textScaleToFit(font, label, Math.max(1, bounds.width - 4));
        int textX = bounds.left + (bounds.width - PhoneScreenDraw.scaledTextWidth(font, label, labelScale)) / 2;
        int textY = bounds.top + (bounds.height - PhoneScreenDraw.scaledTextHeight(font, labelScale)) / 2;
        PhoneScreenDraw.drawScaledText(guiGraphics, font, label, textX, textY, textColor, false, labelScale);
    }

    static void renderCallBackdrop(PhoneScreen screen, GuiGraphics guiGraphics) {
        UiRect backdropBounds = screen.getCallBackdropBounds();
        if (screen.callSessionMode) {
            guiGraphics.blit(PhoneScreen.DEFAULT_BACKGROUND_TEXTURE, backdropBounds.left, backdropBounds.top,
                    0.0F, 0.0F, backdropBounds.width, backdropBounds.height,
                    PhoneScreen.DISPLAY_WIDTH, PhoneScreen.DISPLAY_HEIGHT);
            guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), backdropBounds.bottom(), 0xE6FFFFFF);
            return;
        }

        UiRect numberBounds = screen.getCallNumberDisplayBounds();
        int topBandBottom = numberBounds.top - Math.max(6, Math.round(8 * screen.scale));
        int accentInset = Math.max(10, Math.round(12 * screen.scale));
        int accentTop = backdropBounds.top + Math.max(10, Math.round(12 * screen.scale));
        int accentBottom = accentTop + Math.max(24, Math.round(30 * screen.scale));
        int footerShadeHeight = Math.max(48, Math.round(58 * screen.scale));

        guiGraphics.blit(PhoneScreen.DEFAULT_BACKGROUND_TEXTURE, backdropBounds.left, backdropBounds.top,
                0.0F, 0.0F, backdropBounds.width, backdropBounds.height,
                PhoneScreen.DISPLAY_WIDTH, PhoneScreen.DISPLAY_HEIGHT);
        guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), backdropBounds.bottom(), 0xE6FFFFFF);
        guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), topBandBottom, 0xA7ECECF1);
        guiGraphics.fill(backdropBounds.left + accentInset, accentTop,
                backdropBounds.right() - accentInset, accentBottom, 0x11000000);
        guiGraphics.fill(backdropBounds.left, backdropBounds.bottom() - footerShadeHeight,
                backdropBounds.right(), backdropBounds.bottom(), 0xAAFFFFFF);
    }

    static void renderMediaBackdrop(PhoneScreen screen, GuiGraphics guiGraphics) {
        UiRect backdropBounds = screen.getMediaBackdropBounds();
        if (screen.photoViewerMode) {
            guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), backdropBounds.bottom(), 0xFF000000);
            return;
        }
        int topBandHeight = Math.max(34, Math.round(40 * screen.scale));
        int bottomBandHeight = Math.max(24, Math.round(30 * screen.scale));
        int accentInset = Math.max(8, Math.round(10 * screen.scale));
        int accentTop = backdropBounds.top + Math.max(10, Math.round(12 * screen.scale));
        int accentBottom = accentTop + Math.max(24, Math.round(28 * screen.scale));

        guiGraphics.blit(PhoneScreen.DEFAULT_BACKGROUND_TEXTURE, backdropBounds.left, backdropBounds.top,
                0.0F, 0.0F, backdropBounds.width, backdropBounds.height,
                PhoneScreen.DISPLAY_WIDTH, PhoneScreen.DISPLAY_HEIGHT);
        guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), backdropBounds.bottom(), 0xDCFFFFFF);
        guiGraphics.fill(backdropBounds.left, backdropBounds.top,
                backdropBounds.right(), backdropBounds.top + topBandHeight, 0xB7ECECF1);
        guiGraphics.fill(backdropBounds.left, backdropBounds.bottom() - bottomBandHeight,
                backdropBounds.right(), backdropBounds.bottom(), 0xEEFFFFFF);
        guiGraphics.fill(backdropBounds.left + accentInset, accentTop,
                backdropBounds.right() - accentInset, accentBottom, 0x11000000);
    }

    private static void renderCallMenuTabs(PhoneScreen screen, GuiGraphics guiGraphics, boolean dialPageActive,
                                           boolean contactsPageActive, boolean recentsPageActive) {
        UiRect dialBounds = screen.getCallDialMenuBounds();
        UiRect recentsBounds = screen.getCallRecentsMenuBounds();
        UiRect listBounds = screen.getCallListMenuBounds();

        int tabBarTop = dialBounds.top - Math.max(2, Math.round(3 * screen.scale));
        int labelReserve = Math.max(12, Math.round(14 * screen.scale));
        int tabBarBottom = screen.displayY + screen.displayHeight;
        int contentBoundsRight = screen.getCallSurfaceBounds().right();
        int contentBoundsLeft = screen.getCallSurfaceBounds().left;

        guiGraphics.fill(contentBoundsLeft, tabBarTop, contentBoundsRight, tabBarBottom, PhoneUi.SURFACE);
        guiGraphics.fill(contentBoundsLeft, tabBarTop, contentBoundsRight, tabBarTop + 1, PhoneUi.HAIRLINE);

        renderCallMenuTab(screen, guiGraphics, dialBounds, 0,
                Component.translatable("screen.bphone.phone.call.tab.dial"), dialPageActive);
        renderCallMenuTab(screen, guiGraphics, recentsBounds, 1,
                Component.translatable("screen.bphone.phone.call.tab.recents"), recentsPageActive);
        renderCallMenuTab(screen, guiGraphics, listBounds, 2,
                Component.translatable("screen.bphone.phone.call.tab.contacts"), contactsPageActive);
    }

    private static void renderHandsetCircleButton(GuiGraphics guiGraphics, UiRect bounds, int bgColor, boolean hangup) {
        PhoneUi.fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, bounds.width / 2, bgColor);
        int density = PhoneUi.pixelDensity();
        float cx = (bounds.left + bounds.width * 0.5F) * density;
        float cy = (bounds.top + bounds.height * 0.5F) * density;
        float r = bounds.width * 0.24F * density;
        float stroke = Math.max(2.0F, bounds.width * 0.11F * density);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        if (hangup) {
            PhoneUi.drawSmoothSegment(guiGraphics, cx - r, cy + r * 0.15F, cx - r * 0.35F, cy - r * 0.32F, stroke, 0xFFFFFFFF);
            PhoneUi.drawSmoothSegment(guiGraphics, cx - r * 0.35F, cy - r * 0.32F, cx + r * 0.35F, cy - r * 0.32F, stroke, 0xFFFFFFFF);
            PhoneUi.drawSmoothSegment(guiGraphics, cx + r * 0.35F, cy - r * 0.32F, cx + r, cy + r * 0.15F, stroke, 0xFFFFFFFF);
        } else {
            PhoneUi.drawSmoothSegment(guiGraphics, cx - r * 0.72F, cy - r * 0.72F, cx - r * 0.32F, cy + r * 0.18F, stroke, 0xFFFFFFFF);
            PhoneUi.drawSmoothSegment(guiGraphics, cx - r * 0.32F, cy + r * 0.18F, cx + r * 0.72F, cy + r * 0.72F, stroke, 0xFFFFFFFF);
        }
        guiGraphics.pose().popPose();
    }

    private static void renderCallMenuTab(PhoneScreen screen, GuiGraphics guiGraphics,
                                          UiRect bounds, int iconKind, Component label, boolean active) {
        Font font = PhoneFont.get();
        int iconColor = active ? PhoneUi.ACCENT : PhoneUi.TEXT_SECONDARY;
        renderCallTabGlyph(guiGraphics, bounds, iconKind, iconColor);

        int labelY = bounds.bottom() + Math.max(1, Math.round(2 * screen.scale));
        int labelMaxWidth = bounds.width + Math.max(8, Math.round(10 * screen.scale));
        float labelScale = Math.min(new PhoneChatLayout(screen).captionScale,
                PhoneScreenDraw.textScaleToFit(font, label, labelMaxWidth, 0.32F));
        int labelWidth = PhoneScreenDraw.scaledTextWidth(font, label, labelScale);
        int labelX = bounds.left + (bounds.width - labelWidth) / 2;
        PhoneScreenDraw.drawScaledText(guiGraphics, font, label, labelX, labelY, iconColor, false, labelScale);

        if (active) {
            int underlineInset = Math.max(4, Math.round(6 * screen.scale));
            int underlineHeight = Math.max(1, Math.round(2 * screen.scale));
            int underlineY = labelY + PhoneScreenDraw.scaledTextHeight(font, labelScale) + Math.max(1, Math.round(1 * screen.scale));
            guiGraphics.fill(bounds.left + underlineInset, underlineY,
                    bounds.right() - underlineInset, underlineY + underlineHeight, PhoneUi.ACCENT);
        }
    }

    private static void renderCallTabGlyph(GuiGraphics guiGraphics, UiRect bounds, int kind, int color) {
        int size = Math.max(10, Math.round(bounds.width * 0.68F));
        if (size % 2 != 0) {
            size++;
        }
        int left = bounds.left + (bounds.width - size) / 2;
        int top = bounds.top + (bounds.height - size) / 2;
        if (kind == 0) {
            int dot = Math.max(2, size / 5);
            int gap = Math.max(1, (size - 3 * dot) / 2);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    PhoneUi.fillRounded(guiGraphics, left + c * (dot + gap), top + r * (dot + gap), dot, dot, dot / 2, color);
                }
            }
        } else if (kind == 1) {
            int stroke = Math.max(1, PhoneUi.pixelDensity());
            PhoneUi.strokeRounded(guiGraphics, left, top, size, size, size / 2, stroke, color);
            int cx = left + size / 2;
            int cy = top + size / 2;
            guiGraphics.fill(cx, cy - size / 4, cx + 1, cy + 1, color);
            guiGraphics.fill(cx, cy, cx + size / 4, cy + 1, color);
        } else {
            int head = Math.max(4, size * 2 / 5);
            PhoneUi.fillRounded(guiGraphics, left + (size - head) / 2, top + 1, head, head, head / 2, color);
            int bodyW = Math.max(6, Math.round(size * 0.76F));
            int bodyH = Math.max(4, Math.round(size * 0.38F));
            PhoneUi.fillRounded(guiGraphics, left + (size - bodyW) / 2, top + size - bodyH - 1, bodyW, bodyH, bodyH / 2, color);
        }
    }

    static void renderAddContactSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Font font = PhoneFont.get();
        UiRect popupBounds = screen.getAddContactPopupBounds();
        UiRect nameBounds = screen.getAddContactNameBounds();
        UiRect numberBounds = screen.getAddContactNumberBounds();
        UiRect saveBounds = screen.getAddContactSaveButtonBounds();
        UiRect cancelBounds = screen.getAddContactCancelButtonBounds();

        UiRect backdropBounds = screen.getCallBackdropBounds();
        guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), backdropBounds.bottom(), 0x88000000);

        int popupCornerRadius = Math.max(6, Math.round(8 * screen.scale));
        int fieldRadius = Math.max(3, Math.round(4 * screen.scale));
        PhoneUi.fillRounded(guiGraphics, popupBounds.left, popupBounds.top, popupBounds.width, popupBounds.height, popupCornerRadius, PhoneUi.SURFACE);
        PhoneUi.strokeRounded(guiGraphics, popupBounds.left, popupBounds.top, popupBounds.width, popupBounds.height, popupCornerRadius, 1, PhoneUi.HAIRLINE);

        int innerInset = Math.max(8, Math.round(10 * screen.scale));
        int titleHeight = Math.max(18, Math.round(22 * screen.scale));
        int topPadding = Math.max(12, Math.round(14 * screen.scale));
        int titleX = popupBounds.left + innerInset;
        int titleY = popupBounds.top + topPadding;
        Component titleText = screen.editingContactOriginalNumber != null
                ? Component.translatable("screen.bphone.phone.call.contacts.edit_title")
                : Component.translatable("screen.bphone.phone.call.contacts.add_title");
        float titleScale = PhoneScreenDraw.textScaleToFit(font, titleText,
                popupBounds.width - (innerInset * 2), 0.40F);
        int titleTextHeight = PhoneScreenDraw.scaledTextHeight(font, titleScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, titleText, titleX, titleY + (titleHeight - titleTextHeight) / 2, PhoneUi.TEXT, false, titleScale);

        boolean nameActive = screen.addContactNameFieldFocused;
        PhoneUi.fillRounded(guiGraphics, nameBounds.left, nameBounds.top, nameBounds.width, nameBounds.height, fieldRadius, PhoneUi.FIELD);
        PhoneUi.strokeRounded(guiGraphics, nameBounds.left, nameBounds.top, nameBounds.width, nameBounds.height, fieldRadius, 1,
                nameActive ? PhoneUi.ACCENT : PhoneUi.HAIRLINE);
        String displayName = screen.addContactName.isEmpty() ? "" : screen.addContactName;
        Component nameText = nameActive ? Component.literal(displayName + "_") : (displayName.isEmpty() ? Component.translatable("screen.bphone.phone.call.contacts.name_label") : Component.literal(displayName));
        int nameFieldInset = Math.max(4, Math.round(5 * screen.scale));
        float nameFieldScale = PhoneScreenDraw.textScaleToFit(font, nameText,
                nameBounds.width - (nameFieldInset * 2), 0.35F);
        int nameTextHeight = PhoneScreenDraw.scaledTextHeight(font, nameFieldScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, nameText,
                nameBounds.left + nameFieldInset, nameBounds.top + (nameBounds.height - nameTextHeight) / 2,
                displayName.isEmpty() && !nameActive ? PhoneUi.TEXT_TERTIARY : PhoneUi.TEXT, false, nameFieldScale);

        boolean numberActive = !nameActive;
        PhoneUi.fillRounded(guiGraphics, numberBounds.left, numberBounds.top, numberBounds.width, numberBounds.height, fieldRadius, PhoneUi.FIELD);
        PhoneUi.strokeRounded(guiGraphics, numberBounds.left, numberBounds.top, numberBounds.width, numberBounds.height, fieldRadius, 1,
                numberActive ? PhoneUi.ACCENT : PhoneUi.HAIRLINE);
        String displayNumber = screen.addContactNumber.isEmpty() ? "" : screen.addContactNumber;
        Component numberText = numberActive ? Component.literal(displayNumber + "_") : (displayNumber.isEmpty() ? Component.translatable("screen.bphone.phone.call.contacts.number_label") : Component.literal(displayNumber));
        float numberFieldScale = PhoneScreenDraw.textScaleToFit(font, numberText,
                numberBounds.width - (nameFieldInset * 2), 0.35F);
        int numberTextHeight = PhoneScreenDraw.scaledTextHeight(font, numberFieldScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, numberText,
                numberBounds.left + nameFieldInset, numberBounds.top + (numberBounds.height - numberTextHeight) / 2,
                displayNumber.isEmpty() && !numberActive ? PhoneUi.TEXT_TERTIARY : PhoneUi.TEXT, false, numberFieldScale);

        boolean canSave = !screen.addContactNumber.isEmpty();
        PhoneUi.fillRounded(guiGraphics, saveBounds.left, saveBounds.top, saveBounds.width, saveBounds.height, fieldRadius,
                canSave ? PhoneUi.ACCENT : PhoneUi.ACCENT_DISABLED);
        Component saveText = Component.translatable("screen.bphone.phone.call.contacts.save_button");
        float saveScale = PhoneScreenDraw.textScaleToFit(font, saveText,
                saveBounds.width - Math.round(8 * screen.scale), 0.40F);
        int saveTextWidth = PhoneScreenDraw.scaledTextWidth(font, saveText, saveScale);
        int saveTextHeight = PhoneScreenDraw.scaledTextHeight(font, saveScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, saveText,
                saveBounds.left + (saveBounds.width - saveTextWidth) / 2,
                saveBounds.top + (saveBounds.height - saveTextHeight) / 2,
                0xFFFFFFFF, false, saveScale);

        PhoneUi.fillRounded(guiGraphics, cancelBounds.left, cancelBounds.top, cancelBounds.width, cancelBounds.height, fieldRadius, PhoneUi.SECONDARY);
        Component cancelText = Component.translatable("screen.bphone.phone.call.contacts.cancel_button");
        float cancelScale = PhoneScreenDraw.textScaleToFit(font, cancelText,
                cancelBounds.width - Math.round(8 * screen.scale), 0.40F);
        int cancelTextWidth = PhoneScreenDraw.scaledTextWidth(font, cancelText, cancelScale);
        int cancelTextHeight = PhoneScreenDraw.scaledTextHeight(font, cancelScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, cancelText,
                cancelBounds.left + (cancelBounds.width - cancelTextWidth) / 2,
                cancelBounds.top + (cancelBounds.height - cancelTextHeight) / 2,
                PhoneUi.TEXT, false, cancelScale);
    }

    private static void renderDialPadButton(GuiGraphics guiGraphics, Font font, UiRect bounds, Component text,
                                            int fillColor, int borderColor, int textColor, float scale) {
        int radius = bounds.width / 2;
        PhoneUi.fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, radius, fillColor);
        PhoneUi.strokeRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, radius, 1, borderColor);

        float textScale = PhoneScreenDraw.textScaleToFit(font, text, bounds.width - Math.round(8 * scale), 0.40F);
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, text, textScale);
        int textHeight = PhoneScreenDraw.scaledTextHeight(font, textScale);
        int textX = bounds.left + (bounds.width - textWidth) / 2;
        int textY = bounds.top + (bounds.height - textHeight) / 2;
        PhoneScreenDraw.drawScaledText(guiGraphics, font, text, textX, textY, textColor, false, textScale);
    }

    private static int getOwnNumberChipWidth(Font font, String ownNumber, int chipPadding, float scale) {
        return Math.max(Math.round(30 * scale), font.width(ownNumber) + (chipPadding * 2));
    }

    private static void renderOwnNumberChip(GuiGraphics guiGraphics, Font font, String ownNumber,
                                            int chipX, int chipY, int chipWidth, int chipHeight, float scale) {
        Component numberText = Component.literal(ownNumber);
        int innerPaddingX = Math.max(2, Math.round(3 * scale));
        int availableWidth = Math.max(1, chipWidth - (innerPaddingX * 2));
        int availableHeight = Math.max(1, chipHeight);
        float textScale = Math.min(
                PhoneScreenDraw.textScaleToFit(font, numberText, availableWidth, 0.40F),
                Math.min(1.0F, (float) availableHeight / Math.max(1, font.lineHeight))
        );
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, numberText, textScale);
        int textHeight = PhoneScreenDraw.scaledTextHeight(font, textScale);
        int textX = chipX + (chipWidth - textWidth) / 2;
        int textY = chipY + (chipHeight - textHeight) / 2;
        PhoneScreenDraw.drawScaledText(guiGraphics, font, numberText, textX, textY, 0xFF007AFF, false, textScale);
    }

    private static void drawCenteredFittedText(GuiGraphics guiGraphics, Font font, Component text,
                                               int centerX, int topY, int maxWidth, int color,
                                               boolean shadow, float minScale) {
        drawCenteredFittedText(guiGraphics, font, text, centerX, topY, maxWidth, color, shadow, minScale, 1.0F);
    }

    private static void drawCenteredFittedText(GuiGraphics guiGraphics, Font font, Component text,
                                               int centerX, int topY, int maxWidth, int color,
                                               boolean shadow, float minScale, float maxScale) {
        if (maxWidth <= 0) {
            return;
        }

        float textScale = Mth.clamp(PhoneScreenDraw.textScaleToFit(font, text, maxWidth, minScale), minScale, maxScale);
        int maxUnscaledWidth = Math.max(1, Mth.floor(maxWidth / Math.max(0.01F, textScale)));
        Component fittedText = clipToWidth(font, text, maxUnscaledWidth);
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, fittedText, textScale);
        int textX = centerX - (textWidth / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, fittedText, textX, topY, color, shadow, textScale);
    }

    private static Component clipToWidth(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String source = text.getString();
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return Component.literal("");
        }

        int end = source.length();
        while (end > 0 && font.width(source.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return Component.literal(source.substring(0, Math.max(0, end)) + ellipsis);
    }
}
