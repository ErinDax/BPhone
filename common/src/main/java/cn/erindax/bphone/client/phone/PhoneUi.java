package cn.erindax.bphone.client.phone;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class PhoneUi {
    private static final int[] LIGHT = {
            0xFFF2F3F5, 0xFFFFFFFF, 0xFFE5E6EB, 0xFFF5F6F8, 0xFF1F2329, 0xFF646A73, 0xFF8F959E, 0xFF1296DB,
            0xFF0F80BC, 0xFFE1F1FB, 0xFFA9D6F1, 0xFFF54A45, 0xFFDB3934, 0xFF06C755, 0xFFF0F1F3, 0xFFE3E5E8,
            0xFFF2F3F5, 0xFFA8DCF7, 0xFFFFFFFF, 0xFFE3E5E9
    };
    private static final int[] DARK = {
            0xFF111214, 0xFF1C1D20, 0xFF2C2E33, 0xFF25272B, 0xFFE8EAED, 0xFFA4A9B1, 0xFF7A8089, 0xFF3AA8F0,
            0xFF5BB8F5, 0xFF1E3442, 0xFF2D4B5E, 0xFFF25C57, 0xFFFF746F, 0xFF2BD46F, 0xFF2A2C31, 0xFF34373D,
            0xFF26282C, 0xFF1D4F70, 0xFF2A2C31, 0xFF2A2C31
    };
    static int PAGE;
    static int SURFACE;
    static int HAIRLINE;
    static int HOVER;
    static int TEXT;
    static int TEXT_SECONDARY;
    static int TEXT_TERTIARY;
    static int ACCENT;
    static int ACCENT_HOVER;
    static int ACCENT_SOFT;
    static int ACCENT_DISABLED;
    static int DANGER;
    static int DANGER_HOVER;
    static int SUCCESS;
    static int SECONDARY;
    static int SECONDARY_HOVER;
    static int FIELD;
    static int BUBBLE_OUT;
    static int BUBBLE_IN;
    static int SYSTEM_PILL;
    static final int WHITE = 0xFFFFFFFF;
    static final Style PLAIN = Style.EMPTY;
    static final Style BOLD = Style.EMPTY.withBold(true);
    private static boolean dark;

    static {
        load(LIGHT);
    }

    static void applyTheme(boolean darkMode) {
        if (dark != darkMode) {
            dark = darkMode;
            load(darkMode ? DARK : LIGHT);
        }
    }

    static boolean isDark() {
        return dark;
    }

    private static void load(int[] palette) {
        PAGE = palette[0];
        SURFACE = palette[1];
        HAIRLINE = palette[2];
        HOVER = palette[3];
        TEXT = palette[4];
        TEXT_SECONDARY = palette[5];
        TEXT_TERTIARY = palette[6];
        ACCENT = palette[7];
        ACCENT_HOVER = palette[8];
        ACCENT_SOFT = palette[9];
        ACCENT_DISABLED = palette[10];
        DANGER = palette[11];
        DANGER_HOVER = palette[12];
        SUCCESS = palette[13];
        SECONDARY = palette[14];
        SECONDARY_HOVER = palette[15];
        FIELD = palette[16];
        BUBBLE_OUT = palette[17];
        BUBBLE_IN = palette[18];
        SYSTEM_PILL = palette[19];
    }

    enum Tone {
        PRIMARY,
        SECONDARY,
        DANGER,
        DISABLED
    }

    private PhoneUi() {
    }

    static void renderHeader(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect header,
                             Component title, boolean back, boolean plus) {
        guiGraphics.fill(header.left, header.top, header.right(), header.bottom(), SURFACE);
        guiGraphics.fill(header.left, header.bottom() - 1, header.right(), header.bottom(), HAIRLINE);
        int reserve = back || plus ? header.height : layout.pad;
        Component fitted = fit(layout, title.getString(), BOLD, header.width - 2 * reserve, layout.titleScale);
        int y = header.top + (header.height - 1 - layout.titleHeight) / 2;
        drawCentered(guiGraphics, layout, fitted, header.left + header.width / 2, y, TEXT, layout.titleScale);
        if (back) {
            renderBack(screen, guiGraphics, layout, layout.backButton(header));
        }
        if (plus) {
            renderPlus(screen, guiGraphics, layout, layout.actionButton(header));
        }
    }

    static void renderBack(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds) {
        if (hovered(screen, bounds)) {
            int inset = layout.u(4);
            fillRounded(guiGraphics, bounds.left + inset, bounds.top + inset, bounds.width - 2 * inset,
                    bounds.height - 2 * inset - 1, layout.radius, SECONDARY);
        }
        int arm = Math.max(2, layout.titleHeight / 2);
        int tipX = bounds.left + (bounds.width - arm - 2) / 2;
        int centerY = bounds.top + (bounds.height - 1) / 2;
        for (int step = 0; step <= arm; step++) {
            guiGraphics.fill(tipX + step, centerY - step, tipX + step + 2, centerY - step + 1, ACCENT);
            guiGraphics.fill(tipX + step, centerY + step, tipX + step + 2, centerY + step + 1, ACCENT);
        }
    }

    static void renderPlus(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds) {
        if (hovered(screen, bounds)) {
            int inset = layout.u(4);
            fillRounded(guiGraphics, bounds.left + inset, bounds.top + inset, bounds.width - 2 * inset,
                    bounds.height - 2 * inset - 1, layout.radius, SECONDARY);
        }
        int arm = Math.max(2, layout.titleHeight / 2);
        int centerX = bounds.left + bounds.width / 2;
        int centerY = bounds.top + (bounds.height - 1) / 2;
        guiGraphics.fill(centerX - arm, centerY, centerX + arm + 1, centerY + 1, ACCENT);
        guiGraphics.fill(centerX, centerY - arm, centerX + 1, centerY + arm + 1, ACCENT);
    }

    static int renderIconHover(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds,
                               int background) {
        if (!hovered(screen, bounds)) {
            return background;
        }
        int inset = layout.u(4);
        fillRounded(guiGraphics, bounds.left + inset, bounds.top + inset, bounds.width - 2 * inset,
                bounds.height - 2 * inset - 1, layout.radius, SECONDARY);
        return SECONDARY;
    }

    static void renderSearchButton(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds) {
        int background = renderIconHover(screen, guiGraphics, layout, bounds, SURFACE);
        int size = Math.max(5, layout.titleHeight - 1);
        int stroke = Math.max(1, Math.round(layout.u(1.2F)));
        int centerX = bounds.left + bounds.width / 2 - 1;
        int centerY = bounds.top + (bounds.height - 1) / 2 - 1;
        int left = centerX - size / 2;
        int top = centerY - size / 2;
        fillRounded(guiGraphics, left, top, size, size, size / 2, ACCENT);
        int inner = Math.max(1, size - 2 * stroke);
        fillRounded(guiGraphics, left + stroke, top + stroke, inner, inner, inner / 2, background);
        int handle = Math.max(2, size / 2);
        drawLine(guiGraphics, left + size - stroke, top + size - stroke, left + size - stroke + handle,
                top + size - stroke + handle, stroke + 1, ACCENT);
    }

    static void renderMoreButton(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds) {
        renderIconHover(screen, guiGraphics, layout, bounds, SURFACE);
        int dot = Math.max(2, layout.u(2));
        int spacing = Math.max(2, layout.u(2));
        int total = dot * 3 + spacing * 2;
        int x = bounds.left + (bounds.width - total) / 2;
        int y = bounds.top + (bounds.height - 1 - dot) / 2;
        for (int index = 0; index < 3; index++) {
            guiGraphics.fill(x, y, x + dot, y + dot, ACCENT);
            x += dot + spacing;
        }
    }

    static void renderImageGlyph(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds, int color, int background) {
        int width = Math.max(8, bounds.width * 3 / 5);
        int height = Math.max(6, width * 3 / 4);
        int left = bounds.left + (bounds.width - width) / 2;
        int top = bounds.top + (bounds.height - height) / 2;
        int stroke = Math.max(1, layout.u(1));
        guiGraphics.fill(left, top, left + width, top + height, color);
        guiGraphics.fill(left + stroke, top + stroke, left + width - stroke, top + height - stroke, background);
        int innerWidth = width - 2 * stroke;
        int innerHeight = height - 2 * stroke;
        int peak = Math.max(2, innerHeight * 2 / 3);
        int base = top + height - stroke;
        int peakX = left + stroke + innerWidth / 3;
        for (int row = 0; row < peak; row++) {
            int half = peak - row;
            guiGraphics.fill(Math.max(left + stroke, peakX - half), base - 1 - row,
                    Math.min(left + width - stroke, peakX + half), base - row, color);
        }
        int sun = Math.max(1, innerWidth / 6);
        int sunLeft = left + width - stroke - 1 - sun * 2;
        guiGraphics.fill(sunLeft, top + stroke + 1, sunLeft + sun, top + stroke + 1 + sun, color);
    }

    static void renderGroupAvatar(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds, String title) {
        fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, Math.max(2, bounds.width / 5), ACCENT);
        String source = title == null ? "" : title.trim();
        String initial = source.isEmpty() ? "#" : new String(Character.toChars(source.codePointAt(0)));
        float textScale = bounds.width >= layout.avatarLarge ? layout.titleScale * 2.0F : layout.titleScale;
        Component text = Component.literal(initial).withStyle(BOLD);
        int textWidth = layout.width(text, textScale);
        int textHeight = Math.max(1, Math.round(8.0F * textScale));
        drawText(guiGraphics, layout, text, bounds.left + (bounds.width - textWidth) / 2,
                bounds.top + (bounds.height - textHeight) / 2, WHITE, textScale);
    }

    static void renderCheck(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect box, boolean checked, boolean enabled) {
        int radius = Math.max(1, box.width / 4);
        if (checked) {
            fillRounded(guiGraphics, box.left, box.top, box.width, box.height, radius, ACCENT);
            int thickness = Math.max(1, box.width / 8);
            int x0 = box.left + box.width / 4;
            int y0 = box.top + box.height / 2;
            int xm = box.left + box.width * 5 / 12;
            int ym = box.top + box.height * 2 / 3;
            int x1 = box.left + box.width * 3 / 4;
            int y1 = box.top + box.height / 3;
            drawLine(guiGraphics, x0, y0, xm, ym, thickness, WHITE);
            drawLine(guiGraphics, xm, ym, x1, y1, thickness, WHITE);
            return;
        }
        fillRounded(guiGraphics, box.left, box.top, box.width, box.height, radius, enabled ? TEXT_TERTIARY : HAIRLINE);
        fillRounded(guiGraphics, box.left + 1, box.top + 1, box.width - 2, box.height - 2, Math.max(1, radius - 1), SURFACE);
    }

    static void drawLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int thickness, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            guiGraphics.fill(x0, y0, x0 + thickness, y0 + thickness, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = x0 + Math.round((x1 - x0) * (float) step / steps);
            int y = y0 + Math.round((y1 - y0) * (float) step / steps);
            guiGraphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    static int badgeWidth(PhoneChatLayout layout, int count) {
        Component text = Component.literal(count > 99 ? "99+" : String.valueOf(count));
        int vertical = Math.max(1, layout.u(1.5F));
        int height = layout.captionHeight + 2 * vertical;
        int textWidth = layout.width(text, layout.captionScale);
        return Math.max(height, textWidth + 2 * layout.u(2));
    }

    static void renderBadge(GuiGraphics guiGraphics, PhoneChatLayout layout, int anchorX, int anchorY, int count) {
        Component text = Component.literal(count > 99 ? "99+" : String.valueOf(count));
        int vertical = Math.max(1, layout.u(1.5F));
        int height = layout.captionHeight + 2 * vertical;
        int textWidth = layout.width(text, layout.captionScale);
        int width = Math.max(height, textWidth + 2 * layout.u(2));
        int left = anchorX + Math.max(1, layout.u(1));
        int top = anchorY - height / 2;
        fillRounded(guiGraphics, left, top, width, height, height / 2, DANGER);
        drawText(guiGraphics, layout, text, left + (width - textWidth) / 2, top + vertical, WHITE, layout.captionScale);
    }

    static void renderSection(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect rect, String title) {
        int y = rect.top + (rect.height - layout.captionHeight) / 2 + layout.u(1);
        drawText(guiGraphics, layout, fit(layout, title, PLAIN, rect.width - 2 * layout.pad, layout.captionScale),
                rect.left + layout.pad, y, TEXT_SECONDARY, layout.captionScale);
    }

    static void renderNavigationRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                    UiRect row, Component label, String value) {
        if (hovered(screen, row)) {
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom() - 1, HOVER);
        }
        int chevronSpace = layout.u(8);
        int right = row.right() - layout.pad - chevronSpace;
        int y = row.top + (row.height - layout.bodyHeight) / 2;
        int valueWidth = 0;
        if (value != null) {
            int maxValueWidth = Math.max(layout.u(12), (right - row.left) / 2);
            Component valueText = fit(layout, value, PLAIN, maxValueWidth, layout.bodyScale);
            valueWidth = layout.width(valueText, layout.bodyScale);
            drawText(guiGraphics, layout, valueText, right - valueWidth, y, TEXT_TERTIARY, layout.bodyScale);
            valueWidth += layout.gap;
        }
        drawText(guiGraphics, layout, fit(layout, label.getString(), PLAIN, right - valueWidth - row.left - 2 * layout.pad, layout.bodyScale),
                row.left + layout.pad, y, TEXT, layout.bodyScale);
        renderChevron(guiGraphics, layout, row);
    }

    static void renderToggleRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                UiRect row, Component label, boolean enabled) {
        if (hovered(screen, row)) {
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom() - 1, HOVER);
        }
        int trackHeight = Math.max(layout.u(10), layout.bodyHeight + layout.u(2));
        int trackWidth = Math.max(trackHeight + layout.u(8), Math.round(trackHeight * 1.8F));
        int trackLeft = row.right() - layout.pad - trackWidth;
        int trackTop = row.top + (row.height - trackHeight) / 2;
        fillRounded(guiGraphics, trackLeft, trackTop, trackWidth, trackHeight, trackHeight / 2,
                enabled ? SUCCESS : SECONDARY_HOVER);
        int knob = trackHeight - 2;
        int knobLeft = enabled ? trackLeft + trackWidth - knob - 1 : trackLeft + 1;
        fillRounded(guiGraphics, knobLeft, trackTop + 1, knob, knob, knob / 2, WHITE);
        int y = row.top + (row.height - layout.bodyHeight) / 2;
        drawText(guiGraphics, layout, fit(layout, label.getString(), PLAIN, trackLeft - layout.gap - row.left - layout.pad, layout.bodyScale),
                row.left + layout.pad, y, TEXT, layout.bodyScale);
    }

    static void renderOptionRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row,
                                ResourceLocation icon, boolean face, Component label) {
        int background = hovered(screen, row) ? HOVER : SURFACE;
        guiGraphics.fill(row.left, row.top, row.right(), row.bottom() - 1, background);
        UiRect iconBounds = layout.rowAvatar(row, layout.avatarSmall);
        if (face) {
            renderFace(guiGraphics, icon, iconBounds, background);
        } else {
            guiGraphics.blit(icon, iconBounds.left, iconBounds.top, iconBounds.width, iconBounds.height,
                    0.0F, 0.0F, 24, 24, 24, 24);
        }
        int textLeft = layout.rowTextLeft(row, layout.avatarSmall);
        int textRight = row.right() - layout.pad - layout.u(8);
        drawText(guiGraphics, layout, fit(layout, label.getString(), PLAIN, textRight - textLeft, layout.bodyScale),
                textLeft, row.top + (row.height - layout.bodyHeight) / 2, TEXT, layout.bodyScale);
        renderChevron(guiGraphics, layout, row);
    }

    static void renderChevron(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row) {
        int arm = Math.max(2, layout.captionHeight / 2);
        int left = row.right() - layout.pad - arm - 1;
        int centerY = row.top + row.height / 2;
        for (int step = 0; step <= arm; step++) {
            guiGraphics.fill(left + step, centerY - arm + step, left + step + 1, centerY - arm + step + 1, TEXT_TERTIARY);
            guiGraphics.fill(left + step, centerY + arm - step, left + step + 1, centerY + arm - step + 1, TEXT_TERTIARY);
        }
    }

    static void renderTwoLineText(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row, int left, int right,
                                  String primary, int primaryColor, String secondary, int secondaryColor) {
        int width = Math.max(1, right - left);
        int blockHeight = layout.bodyHeight + layout.textGap + layout.captionHeight;
        int top = row.top + (row.height - blockHeight) / 2;
        drawText(guiGraphics, layout, fit(layout, primary, PLAIN, width, layout.bodyScale),
                left, top, primaryColor, layout.bodyScale);
        drawText(guiGraphics, layout, fit(layout, secondary, PLAIN, width, layout.captionScale),
                left, top + layout.bodyHeight + layout.textGap, secondaryColor, layout.captionScale);
    }

    static void renderButton(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds,
                             Component label, Tone tone) {
        boolean hover = tone != Tone.DISABLED && hovered(screen, bounds);
        int fill = switch (tone) {
            case PRIMARY -> hover ? ACCENT_HOVER : ACCENT;
            case SECONDARY -> hover ? SECONDARY_HOVER : SECONDARY;
            case DANGER -> hover ? DANGER_HOVER : DANGER;
            case DISABLED -> ACCENT_DISABLED;
        };
        int color = tone == Tone.SECONDARY ? TEXT : WHITE;
        Style style = tone == Tone.SECONDARY ? PLAIN : BOLD;
        fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, layout.radius, fill);
        Component fitted = fit(layout, label.getString(), style, bounds.width - 2 * layout.u(3), layout.bodyScale);
        drawCentered(guiGraphics, layout, fitted, bounds.left + bounds.width / 2,
                bounds.top + (bounds.height - layout.bodyHeight) / 2, color, layout.bodyScale);
    }

    static void renderField(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds, String value,
                            Component placeholder, int border, int fill) {
        fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, layout.radius, border);
        fillRounded(guiGraphics, bounds.left + 1, bounds.top + 1, bounds.width - 2, bounds.height - 2,
                Math.max(1, layout.radius - 1), fill);

        int padding = layout.u(5);
        int textLeft = bounds.left + padding;
        int available = Math.max(1, bounds.width - 2 * padding - 2);
        int y = bounds.top + (bounds.height - layout.bodyHeight) / 2;
        int caretX = textLeft;
        if (value.isEmpty()) {
            drawText(guiGraphics, layout, fit(layout, placeholder.getString(), PLAIN, available - 2, layout.bodyScale),
                    textLeft + 2, y, TEXT_TERTIARY, layout.bodyScale);
        } else {
            String visible = layout.font.plainSubstrByWidth(value, Mth.floor(available / layout.bodyScale), true);
            Component text = Component.literal(visible);
            drawText(guiGraphics, layout, text, textLeft, y, TEXT, layout.bodyScale);
            caretX = textLeft + layout.width(text, layout.bodyScale);
        }
        if ((Util.getMillis() / 500L) % 2L == 0L) {
            guiGraphics.fill(caretX, y - 1, caretX + 1, y + layout.bodyHeight + 1, ACCENT);
        }
    }

    static void renderEmptyState(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect area,
                                 Component title, Component hint) {
        int textWidth = area.width - 4 * layout.pad;
        List<FormattedCharSequence> hintLines = hint == null
                ? List.of()
                : layout.font.split(hint, Math.max(16, Mth.floor(textWidth / layout.captionScale)));
        int iconWidth = layout.u(30);
        int iconHeight = layout.u(21);
        int tail = Math.max(2, layout.u(4));
        int blockHeight = iconHeight + tail + layout.u(8) + layout.bodyHeight;
        if (!hintLines.isEmpty()) {
            blockHeight += layout.u(4) + (hintLines.size() - 1) * layout.captionLine + layout.captionHeight;
        }
        int top = area.top + Math.max(layout.u(8), (area.height - blockHeight) / 2 - layout.u(8));
        int centerX = area.left + area.width / 2;

        int iconLeft = centerX - iconWidth / 2;
        fillRounded(guiGraphics, iconLeft, top, iconWidth, iconHeight, Math.max(2, layout.u(6)), ACCENT_SOFT);
        for (int step = 0; step < tail; step++) {
            guiGraphics.fill(iconLeft + layout.u(6), top + iconHeight + step,
                    iconLeft + layout.u(6) + tail - step, top + iconHeight + step + 1, ACCENT_SOFT);
        }
        int dot = Math.max(2, layout.u(3));
        int dotGap = Math.max(2, layout.u(3));
        int dotsWidth = dot * 3 + dotGap * 2;
        int dotX = centerX - dotsWidth / 2;
        int dotY = top + (iconHeight - dot) / 2;
        for (int index = 0; index < 3; index++) {
            guiGraphics.fill(dotX, dotY, dotX + dot, dotY + dot, ACCENT);
            dotX += dot + dotGap;
        }

        int y = top + iconHeight + tail + layout.u(8);
        drawCentered(guiGraphics, layout, fit(layout, title.getString(), PLAIN, textWidth, layout.bodyScale),
                centerX, y, TEXT_SECONDARY, layout.bodyScale);
        y += layout.bodyHeight + layout.u(4);
        for (FormattedCharSequence line : hintLines) {
            int lineWidth = layout.width(line, layout.captionScale);
            drawSequence(guiGraphics, layout, line, centerX - lineWidth / 2, y, TEXT_TERTIARY, layout.captionScale);
            y += layout.captionLine;
        }
    }

    static int drawWrapped(GuiGraphics guiGraphics, PhoneChatLayout layout, Component text, int left, int top,
                           int maxWidth, int color, float textScale) {
        int y = top;
        for (FormattedCharSequence line : layout.font.split(text, Math.max(16, Mth.floor(maxWidth / textScale)))) {
            drawSequence(guiGraphics, layout, line, left, y, color, textScale);
            y += Math.max(1, Math.round(layout.font.lineHeight * textScale));
        }
        return y;
    }

    static int drawWrappedCentered(GuiGraphics guiGraphics, PhoneChatLayout layout, Component text, int centerX,
                                   int top, int maxWidth, int color, float textScale) {
        int y = top;
        for (FormattedCharSequence line : layout.font.split(text, Math.max(16, Mth.floor(maxWidth / textScale)))) {
            int lineWidth = layout.width(line, textScale);
            drawSequence(guiGraphics, layout, line, centerX - lineWidth / 2, y, color, textScale);
            y += Math.max(1, Math.round(layout.font.lineHeight * textScale));
        }
        return y;
    }

    static void renderFace(GuiGraphics guiGraphics, ResourceLocation texture, UiRect bounds, int background) {
        PlayerFaceRenderer.draw(guiGraphics, texture, bounds.left, bounds.top, bounds.width);
        if (bounds.width < 12) {
            return;
        }
        int radius = bounds.width >= 28 ? 2 : 1;
        for (int row = 0; row < radius; row++) {
            int inset = cornerInset(radius, row);
            int top = bounds.top + row;
            int bottom = bounds.bottom() - 1 - row;
            guiGraphics.fill(bounds.left, top, bounds.left + inset, top + 1, background);
            guiGraphics.fill(bounds.right() - inset, top, bounds.right(), top + 1, background);
            guiGraphics.fill(bounds.left, bottom, bounds.left + inset, bottom + 1, background);
            guiGraphics.fill(bounds.right() - inset, bottom, bounds.right(), bottom + 1, background);
        }
    }

    static void renderItem(GuiGraphics guiGraphics, ItemStack stack, UiRect bounds) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float itemScale = Math.min(bounds.width, bounds.height) / 16.0F;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(bounds.left, bounds.top, 0.0F);
        guiGraphics.pose().scale(itemScale, itemScale, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    static boolean hovered(PhoneScreen screen, UiRect bounds) {
        return screen.chatMouseX >= bounds.left && screen.chatMouseX < bounds.right()
                && screen.chatMouseY >= bounds.top && screen.chatMouseY < bounds.bottom();
    }

    static Component fit(PhoneChatLayout layout, String text, Style style, int maxWidth, float textScale) {
        String source = text == null ? "" : text;
        int limit = Mth.floor(Math.max(0, maxWidth) / textScale);
        Component full = Component.literal(source).withStyle(style);
        if (layout.font.width(full) <= limit) {
            return full;
        }
        String ellipsis = "...";
        int room = limit - layout.font.width(Component.literal(ellipsis).withStyle(style));
        if (room <= 0) {
            return Component.empty();
        }
        String head = layout.font.getSplitter().plainHeadByWidth(source, room, style);
        return Component.literal(head + ellipsis).withStyle(style);
    }

    static void drawText(GuiGraphics guiGraphics, PhoneChatLayout layout, Component text, int x, int y,
                         int color, float textScale) {
        PhoneScreenDraw.drawScaledText(guiGraphics, layout.font, text, x, y, color, false, textScale);
    }

    static void drawCentered(GuiGraphics guiGraphics, PhoneChatLayout layout, Component text, int centerX, int y,
                             int color, float textScale) {
        drawText(guiGraphics, layout, text, centerX - layout.width(text, textScale) / 2, y, color, textScale);
    }

    static void drawSequence(GuiGraphics guiGraphics, PhoneChatLayout layout, FormattedCharSequence text, int x, int y,
                             int color, float textScale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(textScale, textScale, 1.0F);
        guiGraphics.drawString(layout.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    static void fillRounded(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            guiGraphics.fill(x, y, x + width, y + height, color);
            return;
        }
        guiGraphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            guiGraphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            guiGraphics.fill(x + inset, y + height - 1 - row, x + width - inset, y + height - row, color);
        }
    }

    static int cornerInset(int radius, int row) {
        double offset = radius - row;
        return radius - (int) Math.floor(Math.sqrt((double) radius * radius - offset * offset));
    }

    static void drawCorner(GuiGraphics guiGraphics, int x, int y, int length,
                           int thickness, boolean leftSide, boolean topSide, int color) {
        int horizontalX = leftSide ? x : x - length;
        int horizontalY = topSide ? y : y - thickness;
        guiGraphics.fill(horizontalX, horizontalY, horizontalX + length, horizontalY + thickness, color);

        int verticalX = leftSide ? x : x - thickness;
        int verticalY = topSide ? y : y - length;
        guiGraphics.fill(verticalX, verticalY, verticalX + thickness, verticalY + length, color);
    }
}
