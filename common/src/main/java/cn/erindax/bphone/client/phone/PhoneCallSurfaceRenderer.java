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
        Minecraft minecraft = Minecraft.getInstance();
        UiRect contentBounds = screen.getCallSurfaceBounds();
        UiRect numberBounds = screen.getCallNumberDisplayBounds();
        String ownNumber = screen.getOwnPhoneNumber();
        int headerHeight = screen.getCallHeaderHeight();
        int rowTop = contentBounds.top + Math.max(5, Math.round(6 * screen.scale));
        int rowHeight = Math.max(14, Math.round(16 * screen.scale));
        int chipPadding = Math.max(4, Math.round(5 * screen.scale));
        int ownChipWidth = getOwnNumberChipWidth(minecraft.font, ownNumber, chipPadding, screen.scale);
        int ownChipX = contentBounds.right() - ownChipWidth;
        int titlePaddingLeft = Math.max(5, Math.round(7 * screen.scale));

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth, screen.displayY + screen.displayHeight, 0xFFFFFFFF);
        guiGraphics.fill(contentBounds.left, contentBounds.top, contentBounds.right(), contentBounds.top + headerHeight, 0xDEF9F9FB);

        Component titleText = Component.translatable("screen.bphone.phone.call.title");
        renderOwnNumberChip(guiGraphics, minecraft.font, ownNumber, ownChipX, rowTop, ownChipWidth, rowHeight, screen.scale);

        int titleMaxWidth = Math.max(24, ownChipX - contentBounds.left - titlePaddingLeft - Math.round(8 * screen.scale));
        float titleScale = PhoneScreenDraw.textScaleToFit(minecraft.font, titleText, titleMaxWidth, 0.45F);
        int titleHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, titleScale);
        int titleX = contentBounds.left + titlePaddingLeft;
        int titleY = rowTop + Math.max(0, (rowHeight - titleHeight) / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, titleText, titleX, titleY, 0xFF000000, false, titleScale);

        guiGraphics.fill(numberBounds.left, numberBounds.top, numberBounds.right(), numberBounds.bottom(), 0xFFEAEBEE);
        guiGraphics.fill(numberBounds.left + 1, numberBounds.top + 1, numberBounds.right() - 1, numberBounds.bottom() - 1, 0xFFF2F2F7);

        Component numberText = screen.dialedNumber.isEmpty()
                ? Component.translatable("screen.bphone.phone.call.placeholder")
                : Component.literal(screen.dialedNumber);
        float numberScale = PhoneScreenDraw.textScaleToFit(minecraft.font, numberText, numberBounds.width - Math.round(10 * screen.scale));
        int numberTextWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, numberText, numberScale);
        int numberTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, numberScale);
        int numberTextX = numberBounds.left + (numberBounds.width - numberTextWidth) / 2;
        int numberTextY = numberBounds.top + (numberBounds.height - numberTextHeight) / 2;
        int numberTextColor = screen.dialedNumber.isEmpty() ? 0xFF3C3C43 : 0xFF000000;
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, numberText, numberTextX, numberTextY,
                numberTextColor, false, numberScale);

        for (int i = 0; i < PhoneScreen.CALL_DIAL_DIGITS.length; i++) {
            String digit = PhoneScreen.CALL_DIAL_DIGITS[i];
            if (digit.isEmpty()) {
                continue;
            }

            UiRect buttonBounds = screen.getDialPadCellBounds(i);
            renderDialPadButton(guiGraphics, minecraft.font, buttonBounds, Component.literal(digit),
                    0xFFEAEBEE, 0xFFFFFFFF, 0xFF000000, screen.scale);
        }

        renderDialDeleteButton(guiGraphics, screen.getDialDeleteButtonBounds(),
                screen.dialedNumber.isEmpty() ? 0xFFC7C7CC : 0xFF3C3C43);
        UiRect callBounds = screen.getDialCallButtonBounds();
        guiGraphics.blit(PhoneScreen.ANSWER_BUTTON_TEXTURE, callBounds.left, callBounds.top,
                callBounds.width, callBounds.height, 0.0F, 0.0F, 24, 24, 24, 24);
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
        Minecraft minecraft = Minecraft.getInstance();
        float captionScale = new PhoneChatLayout(screen).captionScale;
        UiRect contentBounds = screen.getCallSurfaceBounds();
        int headerHeight = screen.getCallHeaderHeight();

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth, screen.displayY + screen.displayHeight, 0xFFFFFFFF);
        guiGraphics.fill(contentBounds.left, contentBounds.top, contentBounds.right(), contentBounds.top + headerHeight, 0xDEF9F9FB);

        Component titleText = Component.translatable("screen.bphone.phone.call.recents.title");
        int titleMaxWidth = Math.max(24, contentBounds.width - Math.max(16, Math.round(20 * screen.scale)));
        float titleScale = PhoneScreenDraw.textScaleToFit(minecraft.font, titleText, titleMaxWidth, 0.45F);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, titleText,
                contentBounds.left + Math.max(8, Math.round(10 * screen.scale)),
                contentBounds.top + Math.max(6, Math.round(8 * screen.scale)),
                0xFF000000, false, titleScale);

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
            drawCenteredFittedText(guiGraphics, minecraft.font,
                    Component.translatable("screen.bphone.phone.call.recents.empty"),
                    contentBounds.left + contentBounds.width / 2, top + rowHeight * 2,
                    width, 0xFF636366, false, 0.35F);
        } else {
            int end = Math.min(entries.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < end; i++) {
                CallLogEntry entry = entries.get(i);
                int y = top + (i - scrollOffset) * rowHeight;
                if (y + rowHeight < contentBounds.top || y > bottomClip) {
                    continue;
                }
                guiGraphics.fill(left, y, left + width, y + rowHeight, 0xFFFFFFFF);
                guiGraphics.fill(left, y + rowHeight - 1, left + width, y + rowHeight, 0xFFE5E5EA);

                int indicatorSize = Math.max(8, Math.round(10 * screen.scale));
                int indicatorX = left + Math.max(2, Math.round(3 * screen.scale));
                int indicatorY = y + (rowHeight - indicatorSize) / 2;

                String typeIndicator;
                int typeColor;
                switch (entry.callType()) {
                    case "INCOMING" -> { typeIndicator = "\u2193"; typeColor = 0xFF34C759; }
                    case "MISSED" -> { typeIndicator = "\u2715"; typeColor = 0xFFFF3B30; }
                    default -> { typeIndicator = "\u2191"; typeColor = 0xFF007AFF; }
                }
                guiGraphics.fill(indicatorX, indicatorY, indicatorX + indicatorSize, indicatorY + indicatorSize, typeColor);
                int indTextX = indicatorX + (indicatorSize - minecraft.font.width(typeIndicator)) / 2;
                int indTextY = indicatorY + (indicatorSize - minecraft.font.lineHeight) / 2;
                guiGraphics.drawString(minecraft.font, typeIndicator, indTextX, indTextY, 0xFFFFFFFF, false);

                int textLeft = indicatorX + indicatorSize + Math.max(4, Math.round(5 * screen.scale));
                int textWidth = width - (textLeft - left);

                String name = entry.otherName() == null || entry.otherName().isBlank() ? entry.otherNumber() : entry.otherName();
                Component nameText = Component.literal(name);
                float nameScale = PhoneScreenDraw.textScaleToFit(minecraft.font, nameText,
                        textWidth - Math.max(40, Math.round(50 * screen.scale)), 0.35F);
                PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, nameText,
                        textLeft, y + Math.max(1, Math.round(2 * screen.scale)),
                        0xFF000000, false, nameScale);

                Component timeText = formatRelativeTime(entry.timestamp());
                float timeScale = Math.min(captionScale, PhoneScreenDraw.textScaleToFit(minecraft.font, timeText,
                        Math.max(30, Math.round(40 * screen.scale)), 0.33F));
                int timeWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, timeText, timeScale);
                int timeX = left + width - timeWidth - Math.max(2, Math.round(3 * screen.scale));
                int timeY = y + Math.max(1, Math.round(2 * screen.scale));
                PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, timeText, timeX, timeY, 0xFF636366, false, timeScale);

                Component subLabel = entry.callType().equals("INCOMING") || entry.callType().equals("OUTGOING")
                        ? formatDuration(entry.durationTicks()) : null;
                if (subLabel != null) {
                    float subScale = Math.min(captionScale, PhoneScreenDraw.textScaleToFit(minecraft.font, subLabel,
                            textWidth, 0.33F));
                    int subY = y + Math.round(rowHeight * 0.55F);
                    PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, subLabel,
                            textLeft, subY, 0xFF636366, false, subScale);
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
        Minecraft minecraft = Minecraft.getInstance();
        UiRect contentBounds = screen.getCallSurfaceBounds();
        String ownNumber = screen.getOwnPhoneNumber();
        int headerHeight = screen.getCallHeaderHeight();
        int rowTop = contentBounds.top + Math.max(5, Math.round(6 * screen.scale));
        int rowHeight = Math.max(14, Math.round(16 * screen.scale));
        int chipPadding = Math.max(4, Math.round(5 * screen.scale));
        int ownChipWidth = getOwnNumberChipWidth(minecraft.font, ownNumber, chipPadding, screen.scale);
        int ownChipX = contentBounds.right() - ownChipWidth;
        int titlePaddingLeft = Math.max(5, Math.round(7 * screen.scale));
        int panelTop = contentBounds.top + headerHeight + Math.max(4, Math.round(5 * screen.scale));
        int panelBottom = screen.getCallDialMenuBounds().top - screen.getCallMenuBottomReserve();
        UiRect saveButtonBounds = screen.getContactSaveButtonBounds();
        String saveCandidateNumber = screen.getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !screen.hasSavedContact(saveCandidateNumber);
        List<PhoneContact> contacts = screen.getPhoneContacts();

        guiGraphics.fill(screen.displayX, screen.displayY, screen.displayX + screen.displayWidth, screen.displayY + screen.displayHeight, 0xFFFFFFFF);
        guiGraphics.fill(contentBounds.left, contentBounds.top, contentBounds.right(), contentBounds.top + headerHeight, 0xDEF9F9FB);

        Component titleText = Component.translatable("screen.bphone.phone.call.contacts.title");
        renderOwnNumberChip(guiGraphics, minecraft.font, ownNumber, ownChipX, rowTop, ownChipWidth, rowHeight, screen.scale);

        int titleMaxWidth = Math.max(24, ownChipX - contentBounds.left - titlePaddingLeft - Math.round(8 * screen.scale));
        float titleScale = PhoneScreenDraw.textScaleToFit(minecraft.font, titleText, titleMaxWidth, 0.45F);
        int titleHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, titleScale);
        int titleX = contentBounds.left + titlePaddingLeft;
        int titleY = rowTop + Math.max(0, (rowHeight - titleHeight) / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, titleText, titleX, titleY, 0xFF000000, false, titleScale);

        UiRect addButtonBounds = screen.getAddContactHeaderButtonBounds();
        guiGraphics.fill(addButtonBounds.left, addButtonBounds.top, addButtonBounds.right(), addButtonBounds.bottom(), 0xFFEAEBEE);
        guiGraphics.fill(addButtonBounds.left + 1, addButtonBounds.top + 1, addButtonBounds.right() - 1, addButtonBounds.bottom() - 1, 0xFFFFFFFF);
        Component addText = Component.translatable("screen.bphone.phone.call.contacts.add_label");
        int addTextMaxWidth = Math.max(20, addButtonBounds.width - Math.max(8, Math.round(10 * screen.scale)));
        float addTextScale = PhoneScreenDraw.textScaleToFit(minecraft.font, addText, addTextMaxWidth, 0.38F);
        int addTextWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, addText, addTextScale);
        int addTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, addTextScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, addText,
                addButtonBounds.left + (addButtonBounds.width - addTextWidth) / 2,
                addButtonBounds.top + (addButtonBounds.height - addTextHeight) / 2,
                0xFF007AFF, false, addTextScale);

        if (canSaveNumber) {
            guiGraphics.fill(saveButtonBounds.left, saveButtonBounds.top, saveButtonBounds.right(), saveButtonBounds.bottom(), 0xFFEAEBEE);
            guiGraphics.fill(saveButtonBounds.left + 1, saveButtonBounds.top + 1,
                    saveButtonBounds.right() - 1, saveButtonBounds.bottom() - 1, 0xFFFFFFFF);
            Component saveText = Component.translatable("screen.bphone.phone.call.contacts.save_current", saveCandidateNumber);
            float saveScale = PhoneScreenDraw.textScaleToFit(minecraft.font, saveText,
                    saveButtonBounds.width - Math.round(10 * screen.scale), 0.40F);
            int saveTextWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, saveText, saveScale);
            int saveTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, saveScale);
            int saveTextX = saveButtonBounds.left + (saveButtonBounds.width - saveTextWidth) / 2;
            int saveTextY = saveButtonBounds.top + (saveButtonBounds.height - saveTextHeight) / 2;
            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, saveText, saveTextX, saveTextY, 0xFF007AFF, false, saveScale);
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
            float emptyScale = PhoneScreenDraw.textScaleToFit(minecraft.font, emptyText,
                    textAreaWidth, 0.40F);
            float hintScale = PhoneScreenDraw.textScaleToFit(minecraft.font, hintText,
                    textAreaWidth, 0.35F);
            int emptyWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, emptyText, emptyScale);
            int hintWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, hintText, hintScale);
            int emptyX = textAreaLeft + ((textAreaWidth - emptyWidth) / 2);
            int emptyY = emptyAreaTop + (emptyAreaHeight / 2) - Math.max(8, Math.round(9 * screen.scale));
            int hintX = textAreaLeft + ((textAreaWidth - hintWidth) / 2);
            int hintY = emptyY + PhoneScreenDraw.scaledTextHeight(minecraft.font, emptyScale) + Math.max(4, Math.round(5 * screen.scale));
            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, emptyText, emptyX, emptyY, 0xFF000000, false, emptyScale);
            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, hintText, hintX, hintY, 0xFF636366, false, hintScale);
        } else {
            for (int index = 0; index < contacts.size(); index++) {
                PhoneContact contact = contacts.get(index);
                UiRect rowBounds = screen.getContactRowBounds(index, contacts.size());
                UiRect deleteBounds = screen.getContactDeleteButtonBounds(index, contacts.size());
                UiRect editBounds = screen.getContactEditButtonBounds(index, contacts.size());
                guiGraphics.fill(rowBounds.left, rowBounds.top, rowBounds.right(), rowBounds.bottom(), 0xFFEAEBEE);
                guiGraphics.fill(rowBounds.left + 1, rowBounds.top + 1, rowBounds.right() - 1, rowBounds.bottom() - 1, 0xFFEAEBEE);

                Component nameText = Component.literal(contact.displayName());
                Component numberText = Component.literal(contact.number());
                Component lineText = contact.displayName().equals(contact.number())
                        ? numberText : nameText;
                int textLeft = rowBounds.left + Math.max(5, Math.round(6 * screen.scale));
                int textRight = editBounds.left - Math.max(4, Math.round(6 * screen.scale));
                int textMaxWidth = Math.max(18, textRight - textLeft);
                boolean showNumberOnly = contact.displayName().equals(contact.number());
                float lineScale = PhoneScreenDraw.textScaleToFit(minecraft.font, lineText, textMaxWidth, showNumberOnly ? 0.35F : 0.40F);
                int lineHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, lineScale);
                PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, lineText, textLeft,
                        rowBounds.top + (rowBounds.height - lineHeight) / 2,
                        showNumberOnly ? 0xFF8E8E93 : 0xFF000000, false, lineScale);

                guiGraphics.blit(PhoneScreen.DELETE_BUTTON_TEXTURE, deleteBounds.left, deleteBounds.top,
                        deleteBounds.width, deleteBounds.height, 0.0F, 0.0F, 24, 24, 24, 24);

                guiGraphics.fill(editBounds.left, editBounds.top, editBounds.right(), editBounds.bottom(), 0xFF007AFF);
                guiGraphics.fill(editBounds.left + 1, editBounds.top + 1, editBounds.right() - 1, editBounds.bottom() - 1, 0xFF007AFF);
                Component editIcon = Component.literal("✎");
                float editScale = PhoneScreenDraw.textScaleToFit(minecraft.font, editIcon,
                        editBounds.width - Math.max(2, Math.round(3 * screen.scale)), 0.40F);
                int editIconWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, editIcon, editScale);
                int editIconHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, editScale);
                PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, editIcon,
                        editBounds.left + (editBounds.width - editIconWidth) / 2,
                        editBounds.top + (editBounds.height - editIconHeight) / 2,
                        0xFFFFFFFF, false, editScale);
            }
        }

        renderCallMenuTabs(screen, guiGraphics, false, true, false);
    }

    static void renderCallSessionSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (screen.activeCallNumber.isEmpty()) {
            screen.endCallSession(true);
            return;
        }

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
        drawCenteredFittedText(guiGraphics, minecraft.font, statusText,
                contentBounds.left + (contentBounds.width / 2), statusY, sessionTextMaxWidth,
                0xFF000000, false, 0.40F);

        String activeName = screen.activeCallName == null ? "" : screen.activeCallName.strip();
        String activeNumber = screen.activeCallNumber == null ? "" : screen.activeCallNumber.strip();
        boolean showSubNumber = !activeName.isBlank() && !activeName.equals(activeNumber);
        Component numberText = Component.literal(activeName.isBlank() ? activeNumber : activeName);
        float numberScale = PhoneScreenDraw.textScaleToFit(minecraft.font, numberText,
                sessionNumberBounds.width - Math.round(10 * screen.scale), 0.40F);
        int numberWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, numberText, numberScale);
        int numberHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, numberScale);
        int numberX = sessionNumberBounds.left + (sessionNumberBounds.width - numberWidth) / 2;
        int lineGap = Math.max(6, Math.round(8 * screen.scale));
        int subNumberHeight = 0;
        float subNumberScale = 0.0F;
        Component subNumberText = null;
        if (showSubNumber) {
            subNumberText = Component.literal(activeNumber);
            subNumberScale = PhoneScreenDraw.textScaleToFit(minecraft.font, subNumberText,
                    sessionNumberBounds.width - Math.round(12 * screen.scale), 0.40F);
            subNumberHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, subNumberScale);
        }
        int textGroupHeight = numberHeight + (showSubNumber ? lineGap + subNumberHeight : 0);
        int numberY = sessionNumberBounds.top + Math.max(0, (sessionNumberBounds.height - textGroupHeight) / 2);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, numberText, numberX, numberY, 0xFF000000, false, numberScale);

        if (showSubNumber) {
            int subNumberWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, subNumberText, subNumberScale);
            int subNumberX = sessionNumberBounds.left + (sessionNumberBounds.width - subNumberWidth) / 2;
            int subNumberY = numberY + numberHeight + lineGap;
            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, subNumberText, subNumberX, subNumberY,
                    0xFF636366, false, subNumberScale);
        }

        Component subText = screen.activeCallConnected
                ? Component.literal(screen.formatCallDuration())
                : screen.activeCallMissed
                ? Component.translatable("screen.bphone.phone.call.status.no_answer_detail")
                : screen.activeCallIncoming
                ? Component.translatable("screen.bphone.phone.call.status.tap_connect")
                : Component.translatable("screen.bphone.phone.call.status.waiting_answer");
        drawCenteredFittedText(guiGraphics, minecraft.font, subText,
                contentBounds.left + (contentBounds.width / 2), subY, sessionTextMaxWidth,
                0xFF636366, false, 0.40F);

        UiRect connectButtonBounds = screen.getCallConnectButtonBounds();
        UiRect hangupButtonBounds = screen.getCallHangupButtonBounds();

        if (screen.activeCallIncoming && !screen.activeCallConnected) {
            int gap = Math.max(5, Math.round(6 * screen.scale));
            int incomingHangupX = connectButtonBounds.right() + gap;
            guiGraphics.blit(PhoneScreen.ANSWER_BUTTON_TEXTURE, 
                    connectButtonBounds.left, connectButtonBounds.top,
                    connectButtonBounds.width, connectButtonBounds.height, 
                    0.0F, 0.0F, 24, 24, 24, 24);
            guiGraphics.blit(PhoneScreen.HANGUP_BUTTON_TEXTURE, 
                    incomingHangupX, connectButtonBounds.top,
                    connectButtonBounds.width, connectButtonBounds.height, 
                    0.0F, 0.0F, 24, 24, 24, 24);
        } else {
            guiGraphics.blit(PhoneScreen.HANGUP_BUTTON_TEXTURE, 
                    hangupButtonBounds.left, hangupButtonBounds.top,
                    hangupButtonBounds.width, hangupButtonBounds.height, 
                    0.0F, 0.0F, 24, 24, 24, 24);
        }

        if (screen.activeCallConnected) {
            UiRect muteBounds = screen.getCallMuteButtonBounds();
            UiRect speakerBounds = screen.getCallSpeakerButtonBounds();
            renderCallActionButton(guiGraphics, muteBounds,
                    Component.translatable("screen.bphone.phone.call.key.mute"),
                    screen.callMuted ? 0xFFFF3B30 : 0xFFF2F2F7,
                    screen.callMuted ? 0xFFE6352B : 0xFFEAEBEE,
                    screen.callMuted ? 0xFFFFFFFF : 0xFF8E8E93);
            renderCallActionButton(guiGraphics, speakerBounds,
                    Component.translatable("screen.bphone.phone.call.key.speaker"),
                    screen.callSpeakerEnabled ? 0xFF007AFF : 0xFFF2F2F7,
                    screen.callSpeakerEnabled ? 0xFF006AD6 : 0xFFEAEBEE,
                    screen.callSpeakerEnabled ? 0xFFFFFFFF : 0xFF8E8E93);
        }
    }

    private static void renderCallActionButton(GuiGraphics guiGraphics, UiRect bounds, Component label,
                                                 int fillColor, int borderColor, int textColor) {
        guiGraphics.fill(bounds.left, bounds.top, bounds.right(), bounds.bottom(), borderColor);
        guiGraphics.fill(bounds.left + 1, bounds.top + 1, bounds.right() - 1, bounds.bottom() - 1, fillColor);
        Font font = Minecraft.getInstance().font;
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
        int tabBarBottom = dialBounds.bottom() + labelReserve;
        int contentBoundsRight = screen.getCallSurfaceBounds().right();
        int contentBoundsLeft = screen.getCallSurfaceBounds().left;

        guiGraphics.fill(contentBoundsLeft, tabBarTop, contentBoundsRight, tabBarBottom, 0xFFFFFFFF);
        guiGraphics.fill(contentBoundsLeft, tabBarTop, contentBoundsRight, tabBarTop + 1, 0xFFC7C7CC);

        renderCallMenuTab(screen, guiGraphics, dialBounds, PhoneScreen.CALL_MENU_TEXTURE,
                Component.translatable("screen.bphone.phone.call.tab.dial"), dialPageActive);
        renderCallMenuTab(screen, guiGraphics, recentsBounds, PhoneScreen.TIME_BUTTON_TEXTURE,
                Component.translatable("screen.bphone.phone.call.tab.recents"), recentsPageActive);
        renderCallMenuTab(screen, guiGraphics, listBounds, PhoneScreen.LIST_MENU_TEXTURE,
                Component.translatable("screen.bphone.phone.call.tab.contacts"), contactsPageActive);
    }

    private static void renderCallMenuTab(PhoneScreen screen, GuiGraphics guiGraphics,
                                          UiRect bounds, ResourceLocation texture, Component label, boolean active) {
        Minecraft minecraft = Minecraft.getInstance();

        guiGraphics.blit(texture, bounds.left, bounds.top, bounds.width, bounds.height,
                0.0F, 0.0F, 24, 24, 24, 24);

        int labelY = bounds.bottom() + Math.max(1, Math.round(2 * screen.scale));
        int labelMaxWidth = bounds.width + Math.max(8, Math.round(10 * screen.scale));
        float labelScale = Math.min(new PhoneChatLayout(screen).captionScale,
                PhoneScreenDraw.textScaleToFit(minecraft.font, label, labelMaxWidth, 0.32F));
        int labelWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, label, labelScale);
        int labelX = bounds.left + (bounds.width - labelWidth) / 2;
        int labelColor = active ? 0xFF007AFF : 0xFF8E8E93;
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, label, labelX, labelY, labelColor, false, labelScale);

        if (active) {
            int underlineInset = Math.max(4, Math.round(6 * screen.scale));
            int underlineHeight = Math.max(1, Math.round(2 * screen.scale));
            int underlineY = labelY + PhoneScreenDraw.scaledTextHeight(minecraft.font, labelScale) + Math.max(1, Math.round(1 * screen.scale));
            guiGraphics.fill(bounds.left + underlineInset, underlineY,
                    bounds.right() - underlineInset, underlineY + underlineHeight, 0xFF007AFF);
        }
    }

    static void renderAddContactSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        UiRect contentBounds = screen.getCallSurfaceBounds();
        UiRect popupBounds = screen.getAddContactPopupBounds();
        UiRect nameBounds = screen.getAddContactNameBounds();
        UiRect numberBounds = screen.getAddContactNumberBounds();
        UiRect saveBounds = screen.getAddContactSaveButtonBounds();
        UiRect cancelBounds = screen.getAddContactCancelButtonBounds();

        UiRect backdropBounds = screen.getCallBackdropBounds();
        guiGraphics.fill(backdropBounds.left, backdropBounds.top, backdropBounds.right(), backdropBounds.bottom(), 0x80000000);

        int popupCornerRadius = Math.max(8, Math.round(10 * screen.scale));
        guiGraphics.fill(popupBounds.left, popupBounds.top, popupBounds.right(), popupBounds.bottom(), 0xFFEAEBEE);
        guiGraphics.fill(popupBounds.left + 1, popupBounds.top + 1, popupBounds.right() - 1, popupBounds.bottom() - 1, 0xFFFFFFFF);

        int innerInset = Math.max(8, Math.round(10 * screen.scale));
        int titleHeight = Math.max(18, Math.round(22 * screen.scale));
        int topPadding = Math.max(12, Math.round(14 * screen.scale));
        int titleX = popupBounds.left + innerInset;
        int titleY = popupBounds.top + topPadding;
        Component titleText = screen.editingContactOriginalNumber != null
                ? Component.translatable("screen.bphone.phone.call.contacts.edit_title")
                : Component.translatable("screen.bphone.phone.call.contacts.add_title");
        float titleScale = PhoneScreenDraw.textScaleToFit(minecraft.font, titleText,
                popupBounds.width - (innerInset * 2), 0.40F);
        int titleTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, titleScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, titleText, titleX, titleY + (titleHeight - titleTextHeight) / 2, 0xFF000000, false, titleScale);

        guiGraphics.fill(nameBounds.left, nameBounds.top, nameBounds.right(), nameBounds.bottom(), 0xFFC7C7CC);
        guiGraphics.fill(nameBounds.left + 1, nameBounds.top + 1, nameBounds.right() - 1, nameBounds.bottom() - 1, 0xFFFFFFFF);
        String displayName = screen.addContactName.isEmpty() ? "" : screen.addContactName;
        boolean nameActive = screen.addContactNameFieldFocused;
        Component nameText = nameActive ? Component.literal(displayName + "_") : (displayName.isEmpty() ? Component.translatable("screen.bphone.phone.call.contacts.name_label") : Component.literal(displayName));
        int nameFieldInset = Math.max(4, Math.round(5 * screen.scale));
        float nameFieldScale = PhoneScreenDraw.textScaleToFit(minecraft.font, nameText,
                nameBounds.width - (nameFieldInset * 2), 0.35F);
        int nameTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, nameFieldScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, nameText,
                nameBounds.left + nameFieldInset, nameBounds.top + (nameBounds.height - nameTextHeight) / 2,
                displayName.isEmpty() && !nameActive ? 0xFF636366 : 0xFF000000, false, nameFieldScale);

        guiGraphics.fill(numberBounds.left, numberBounds.top, numberBounds.right(), numberBounds.bottom(), 0xFFC7C7CC);
        guiGraphics.fill(numberBounds.left + 1, numberBounds.top + 1, numberBounds.right() - 1, numberBounds.bottom() - 1, 0xFFFFFFFF);
        String displayNumber = screen.addContactNumber.isEmpty() ? "" : screen.addContactNumber;
        boolean numberActive = !nameActive;
        Component numberText = numberActive ? Component.literal(displayNumber + "_") : (displayNumber.isEmpty() ? Component.translatable("screen.bphone.phone.call.contacts.number_label") : Component.literal(displayNumber));
        float numberFieldScale = PhoneScreenDraw.textScaleToFit(minecraft.font, numberText,
                numberBounds.width - (nameFieldInset * 2), 0.35F);
        int numberTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, numberFieldScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, numberText,
                numberBounds.left + nameFieldInset, numberBounds.top + (numberBounds.height - numberTextHeight) / 2,
                displayNumber.isEmpty() && !numberActive ? 0xFF8E8E93 : 0xFF000000, false, numberFieldScale);

        boolean canSave = !screen.addContactNumber.isEmpty();
        int saveFill = canSave ? 0xFF34C759 : 0xFFD1D1D6;
        int saveBorder = canSave ? 0xFF4CD964 : 0xFFE5E5EA;
        guiGraphics.fill(saveBounds.left, saveBounds.top, saveBounds.right(), saveBounds.bottom(), saveBorder);
        guiGraphics.fill(saveBounds.left + 1, saveBounds.top + 1, saveBounds.right() - 1, saveBounds.bottom() - 1, saveFill);
        Component saveText = Component.translatable("screen.bphone.phone.call.contacts.save_button");
        float saveScale = PhoneScreenDraw.textScaleToFit(minecraft.font, saveText,
                saveBounds.width - Math.round(8 * screen.scale), 0.40F);
        int saveTextWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, saveText, saveScale);
        int saveTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, saveScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, saveText,
                saveBounds.left + (saveBounds.width - saveTextWidth) / 2,
                saveBounds.top + (saveBounds.height - saveTextHeight) / 2,
                0xFFFFFFFF, false, saveScale);

        guiGraphics.fill(cancelBounds.left, cancelBounds.top, cancelBounds.right(), cancelBounds.bottom(), 0xFFEAEBEE);
        guiGraphics.fill(cancelBounds.left + 1, cancelBounds.top + 1, cancelBounds.right() - 1, cancelBounds.bottom() - 1, 0xFFFFFFFF);
        Component cancelText = Component.translatable("screen.bphone.phone.call.contacts.cancel_button");
        float cancelScale = PhoneScreenDraw.textScaleToFit(minecraft.font, cancelText,
                cancelBounds.width - Math.round(8 * screen.scale), 0.40F);
        int cancelTextWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, cancelText, cancelScale);
        int cancelTextHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, cancelScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, cancelText,
                cancelBounds.left + (cancelBounds.width - cancelTextWidth) / 2,
                cancelBounds.top + (cancelBounds.height - cancelTextHeight) / 2,
                0xFF007AFF, false, cancelScale);
    }

    private static void renderDialPadButton(GuiGraphics guiGraphics, Font font, UiRect bounds, Component text,
                                            int fillColor, int borderColor, int textColor, float scale) {
        guiGraphics.blit(PhoneScreen.CALL_BUTTON_TEXTURE, bounds.left, bounds.top,
                bounds.width, bounds.height, 0.0F, 0.0F, 32, 32, 32, 32);

        float textScale = PhoneScreenDraw.textScaleToFit(font, text, bounds.width - Math.round(8 * scale), 0.40F);
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, text, textScale);
        int textHeight = PhoneScreenDraw.scaledTextHeight(font, textScale);

        int xOffset;
        int yOffset;
        if (text.getString().length() > 1) {
            xOffset = Math.max(1, Math.round(1.5F * scale));
            yOffset = Math.max(1, Math.round(1.2F * scale));
        } else {
            xOffset = Math.round(0.5F * scale);
            yOffset = Math.max(1, Math.round(0.8F * scale));
        }

        int textX = bounds.left + (bounds.width - textWidth) / 2 + xOffset;
        int textY = bounds.top + (bounds.height - textHeight) / 2 - yOffset;
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
