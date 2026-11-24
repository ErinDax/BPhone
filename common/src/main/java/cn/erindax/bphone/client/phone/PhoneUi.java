package cn.erindax.bphone.client.phone;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class PhoneUi {
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
        int density = pixelDensity();
        float arm = Math.max(2.5F, layout.titleHeight * 0.48F) * density;
        float stroke = Math.max(1.4F, density * 0.85F);
        float tipX = (bounds.left + bounds.width * 0.5F) * density - arm * 0.45F;
        float centerY = (bounds.top + (bounds.height - 1) * 0.5F) * density;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        drawSmoothSegment(guiGraphics, tipX + arm, centerY - arm, tipX, centerY, stroke, ACCENT);
        drawSmoothSegment(guiGraphics, tipX, centerY, tipX + arm, centerY + arm, stroke, ACCENT);
        guiGraphics.pose().popPose();
    }

    static void renderPlus(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds) {
        if (hovered(screen, bounds)) {
            int inset = layout.u(4);
            fillRounded(guiGraphics, bounds.left + inset, bounds.top + inset, bounds.width - 2 * inset,
                    bounds.height - 2 * inset - 1, layout.radius, SECONDARY);
        }
        int density = pixelDensity();
        int arm = Math.max(2, layout.titleHeight / 2) * density;
        int halfStroke = Math.max(1, Math.round(density * 0.65F));
        int centerX = (bounds.left + bounds.width / 2) * density;
        int centerY = (bounds.top + (bounds.height - 1) / 2) * density;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        guiGraphics.fill(centerX - arm, centerY - halfStroke, centerX + arm + 1, centerY + halfStroke, ACCENT);
        guiGraphics.fill(centerX - halfStroke, centerY - arm, centerX + halfStroke, centerY + arm + 1, ACCENT);
        guiGraphics.pose().popPose();
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
            int density = pixelDensity();
            float width = box.width * density;
            float height = box.height * density;
            int thickness = Math.max(density, Math.round(width / 8.0F));
            int left = box.left * density - thickness / 2;
            int top = box.top * density - thickness / 2;
            int x0 = left + Math.round(width * 0.26F);
            int y0 = top + Math.round(height * 0.52F);
            int xm = left + Math.round(width * 0.43F);
            int ym = top + Math.round(height * 0.69F);
            int x1 = left + Math.round(width * 0.75F);
            int y1 = top + Math.round(height * 0.33F);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
            drawLine(guiGraphics, x0, y0, xm, ym, thickness, WHITE);
            drawLine(guiGraphics, xm, ym, x1, y1, thickness, WHITE);
            guiGraphics.pose().popPose();
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
        int right = row.right() - layout.pad - layout.chevronReserve();
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
        int textRight = row.right() - layout.pad - layout.chevronReserve();
        drawText(guiGraphics, layout, fit(layout, label.getString(), PLAIN, textRight - textLeft, layout.bodyScale),
                textLeft, row.top + (row.height - layout.bodyHeight) / 2, TEXT, layout.bodyScale);
        renderChevron(guiGraphics, layout, row);
    }

    static void renderChevron(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row) {
        int density = pixelDensity();
        float arm = Math.max(2.0F, layout.chevronArm()) * density;
        float stroke = Math.max(1.25F, density * 0.75F);
        float right = (row.right() - layout.pad - 1) * density;
        float centerY = (row.top + row.height * 0.5F) * density;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        drawSmoothSegment(guiGraphics, right - arm, centerY - arm, right, centerY, stroke, TEXT_TERTIARY);
        drawSmoothSegment(guiGraphics, right, centerY, right - arm, centerY + arm, stroke, TEXT_TERTIARY);
        guiGraphics.pose().popPose();
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
        renderField(guiGraphics, layout, bounds, value, placeholder, border, fill, true);
    }

    static void renderField(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds, String value,
                            Component placeholder, int border, int fill, boolean caret) {
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
        if (caret && (Util.getMillis() / 500L) % 2L == 0L) {
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

    static int wrappedHeight(PhoneChatLayout layout, Component text, int maxWidth, float textScale) {
        return layout.font.split(text, Math.max(16, Mth.floor(maxWidth / textScale))).size()
                * Math.max(1, Math.round(layout.font.lineHeight * textScale));
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

    static void renderToast(GuiGraphics guiGraphics, PhoneChatLayout layout, Component text) {
        UiRect surface = layout.surface;
        int width = surface.width - 2 * layout.pad;
        int textWidth = width - 2 * layout.pad;
        int height = wrappedHeight(layout, text, textWidth, layout.bodyScale) + 2 * layout.gap;
        int top = surface.bottom() - layout.pad - height;
        fillRounded(guiGraphics, surface.left + layout.pad, top, width, height, layout.radius * 2, TEXT);
        drawWrappedCentered(guiGraphics, layout, text, surface.left + surface.width / 2, top + layout.gap, textWidth,
                PAGE, layout.bodyScale);
    }

    static void renderFace(GuiGraphics guiGraphics, ResourceLocation texture, UiRect bounds, int background) {
        int radius = Math.max(2, bounds.width / 5);
        renderRoundedFace(guiGraphics, texture, bounds.left, bounds.top, bounds.width, radius, 1.0F);
    }

    public static void renderRoundedFace(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int size,
                                         int radius, float alphaFactor) {
        if (size <= 0 || texture == null) {
            return;
        }
        guiGraphics.flush();
        int density = pixelDensity();
        int left = x * density;
        int top = y * density;
        int right = (x + size) * density;
        int bottom = (y + size) * density;
        int corner = Math.max(0, Math.min(radius, size / 2)) * density;
        int baseAlpha = Mth.clamp(Math.round(alphaFactor * 255.0F), 0, 255);
        if (baseAlpha <= 0) {
            return;
        }

        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        emitRoundedSkinLayer(builder, matrix, left, top, right, bottom, corner, 8.0F / 64.0F, 16.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, baseAlpha);
        emitRoundedSkinLayer(builder, matrix, left, top, right, bottom, corner, 40.0F / 64.0F, 48.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, baseAlpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        if (!blendEnabled) {
            RenderSystem.disableBlend();
        }
        guiGraphics.pose().popPose();
    }

    private static void emitRoundedSkinLayer(BufferBuilder builder, Matrix4f matrix, int left, int top, int right, int bottom,
                                             int corner, float uMin, float uMax, float vMin, float vMax, int baseAlpha) {
        float width = Math.max(1, right - left);
        float height = Math.max(1, bottom - top);
        if (bottom - corner > top + corner) {
            addSkinQuad(builder, matrix, left, top + corner, right, bottom - corner, left, top, width, height, uMin, uMax, vMin, vMax, baseAlpha);
        }
        for (int row = 0; row < corner; row++) {
            double dy = corner - 0.5D - row;
            int innerInset = corner - (int) Math.floor(Math.sqrt(Math.max(0.0D, (corner - 0.65D) * (corner - 0.65D) - dy * dy)));
            int outerInset = Math.max(0, corner - (int) Math.ceil(Math.sqrt(Math.max(0.0D, (corner + 0.65D) * (corner + 0.65D) - dy * dy))) - 1);
            if (right - innerInset > left + innerInset) {
                addSkinQuad(builder, matrix, left + innerInset, top + row, right - innerInset, top + row + 1, left, top, width, height, uMin, uMax, vMin, vMax, baseAlpha);
                addSkinQuad(builder, matrix, left + innerInset, bottom - 1 - row, right - innerInset, bottom - row, left, top, width, height, uMin, uMax, vMin, vMax, baseAlpha);
            }
            int limit = Math.min(corner, innerInset);
            for (int col = outerInset; col < limit; col++) {
                double dx = corner - 0.5D - col;
                float cov = Mth.clamp((float) (corner + 0.5D - Math.hypot(dx, dy)), 0.0F, 1.0F);
                int a = Math.round(baseAlpha * cov);
                if (a <= 0) {
                    continue;
                }
                addSkinQuad(builder, matrix, left + col, top + row, left + col + 1, top + row + 1, left, top, width, height, uMin, uMax, vMin, vMax, a);
                if (right - 1 - col != left + col) {
                    addSkinQuad(builder, matrix, right - 1 - col, top + row, right - col, top + row + 1, left, top, width, height, uMin, uMax, vMin, vMax, a);
                }
                addSkinQuad(builder, matrix, left + col, bottom - 1 - row, left + col + 1, bottom - row, left, top, width, height, uMin, uMax, vMin, vMax, a);
                if (right - 1 - col != left + col) {
                    addSkinQuad(builder, matrix, right - 1 - col, bottom - 1 - row, right - col, bottom - row, left, top, width, height, uMin, uMax, vMin, vMax, a);
                }
            }
        }
    }

    private static void addSkinQuad(BufferBuilder builder, Matrix4f matrix, int qLeft, int qTop, int qRight, int qBottom,
                                    int baseLeft, int baseTop, float fullW, float fullH,
                                    float uMin, float uMax, float vMin, float vMax, int alpha) {
        float u0 = uMin + (qLeft - baseLeft) / fullW * (uMax - uMin);
        float u1 = uMin + (qRight - baseLeft) / fullW * (uMax - uMin);
        float v0 = vMin + (qTop - baseTop) / fullH * (vMax - vMin);
        float v1 = vMin + (qBottom - baseTop) / fullH * (vMax - vMin);
        builder.addVertex(matrix, qLeft, qTop, 0.0F).setUv(u0, v0).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, qLeft, qBottom, 0.0F).setUv(u0, v1).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, qRight, qBottom, 0.0F).setUv(u1, v1).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, qRight, qTop, 0.0F).setUv(u1, v0).setColor(255, 255, 255, alpha);
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

    public static void fillRounded(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) {
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            guiGraphics.fill(x, y, x + width, y + height, color);
            return;
        }
        int rgb = color & 0x00FFFFFF;
        int density = pixelDensity();
        int left = x * density;
        int top = y * density;
        int right = (x + width) * density;
        int bottom = (y + height) * density;
        int pr = r * density;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        guiGraphics.drawManaged(() -> {
            if (bottom - pr > top + pr) {
                guiGraphics.fill(left, top + pr, right, bottom - pr, color);
            }
            for (int row = 0; row < pr; row++) {
                double dy = pr - 0.5D - row;
                int innerInset = pr - (int) Math.floor(Math.sqrt(Math.max(0.0D, (pr - 0.65D) * (pr - 0.65D) - dy * dy)));
                int outerInset = Math.max(0, pr - (int) Math.ceil(Math.sqrt(Math.max(0.0D, (pr + 0.65D) * (pr + 0.65D) - dy * dy))) - 1);
                if (right - innerInset > left + innerInset) {
                    guiGraphics.fill(left + innerInset, top + row, right - innerInset, top + row + 1, color);
                    if (bottom - 1 - row != top + row) {
                        guiGraphics.fill(left + innerInset, bottom - 1 - row, right - innerInset, bottom - row, color);
                    }
                }
                int limit = Math.min(pr, innerInset);
                for (int col = outerInset; col < limit; col++) {
                    double dx = pr - 0.5D - col;
                    float cov = Mth.clamp((float) (pr + 0.5D - Math.hypot(dx, dy)), 0.0F, 1.0F);
                    int a = Math.round(baseAlpha * cov);
                    if (a <= 0) {
                        continue;
                    }
                    int edgeColor = (a << 24) | rgb;
                    guiGraphics.fill(left + col, top + row, left + col + 1, top + row + 1, edgeColor);
                    if (right - 1 - col != left + col) {
                        guiGraphics.fill(right - 1 - col, top + row, right - col, top + row + 1, edgeColor);
                    }
                    if (bottom - 1 - row != top + row) {
                        guiGraphics.fill(left + col, bottom - 1 - row, left + col + 1, bottom - row, edgeColor);
                        if (right - 1 - col != left + col) {
                            guiGraphics.fill(right - 1 - col, bottom - 1 - row, right - col, bottom - row, edgeColor);
                        }
                    }
                }
            }
        });
        guiGraphics.pose().popPose();
    }

    public static void strokeRounded(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius,
                                     int strokePhysPx, int color) {
        int baseAlpha = (color >>> 24) & 0xFF;
        if (width <= 0 || height <= 0 || strokePhysPx <= 0 || baseAlpha == 0) {
            return;
        }
        int rgb = color & 0x00FFFFFF;
        int density = pixelDensity();
        int left = x * density;
        int top = y * density;
        int right = (x + width) * density;
        int bottom = (y + height) * density;
        int pr = Math.min(radius, Math.min(width, height) / 2) * density;
        int t = strokePhysPx;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        guiGraphics.drawManaged(() -> {
            if (right - pr > left + pr) {
                guiGraphics.fill(left + pr, top, right - pr, top + t, color);
                guiGraphics.fill(left + pr, bottom - t, right - pr, bottom, color);
            }
            if (bottom - pr > top + pr) {
                guiGraphics.fill(left, top + pr, left + t, bottom - pr, color);
                guiGraphics.fill(right - t, top + pr, right, bottom - pr, color);
            }
            double innerR = Math.max(0.0D, pr - t);
            for (int row = 0; row < pr; row++) {
                double dy = pr - 0.5D - row;
                for (int col = 0; col < pr; col++) {
                    double dx = pr - 0.5D - col;
                    double dist = Math.hypot(dx, dy);
                    if (dist > pr + 0.6D || dist < innerR - 0.6D) {
                        continue;
                    }
                    float outerCov = Mth.clamp((float) (pr + 0.5D - dist), 0.0F, 1.0F);
                    float innerCov = innerR <= 0.0D ? 1.0F : Mth.clamp((float) (dist - (innerR - 0.5D)), 0.0F, 1.0F);
                    int a = Math.round(baseAlpha * outerCov * innerCov);
                    if (a <= 0) {
                        continue;
                    }
                    int edgeColor = (a << 24) | rgb;
                    guiGraphics.fill(left + col, top + row, left + col + 1, top + row + 1, edgeColor);
                    if (right - 1 - col != left + col) {
                        guiGraphics.fill(right - 1 - col, top + row, right - col, top + row + 1, edgeColor);
                    }
                    if (bottom - 1 - row != top + row) {
                        guiGraphics.fill(left + col, bottom - 1 - row, left + col + 1, bottom - row, edgeColor);
                        if (right - 1 - col != left + col) {
                            guiGraphics.fill(right - 1 - col, bottom - 1 - row, right - col, bottom - row, edgeColor);
                        }
                    }
                }
            }
        });
        guiGraphics.pose().popPose();
    }

    static void drawSmoothSegment(GuiGraphics guiGraphics, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001F) {
            return;
        }
        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        float half = thickness * 0.5F;
        int minX = Mth.floor(Math.min(x0, x1) - half - 1.0F);
        int maxX = Mth.ceil(Math.max(x0, x1) + half + 1.0F);
        int minY = Mth.floor(Math.min(y0, y1) - half - 1.0F);
        int maxY = Mth.ceil(Math.max(y0, y1) + half + 1.0F);
        float lenSq = length * length;
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float cx = px + 0.5F;
                float cy = py + 0.5F;
                float t = Mth.clamp(((cx - x0) * dx + (cy - y0) * dy) / lenSq, 0.0F, 1.0F);
                float dist = (float) Math.hypot(cx - (x0 + t * dx), cy - (y0 + t * dy));
                float cov = Mth.clamp(half + 0.5F - dist, 0.0F, 1.0F);
                int a = Math.round(baseAlpha * cov);
                if (a > 0) {
                    guiGraphics.fill(px, py, px + 1, py + 1, (a << 24) | rgb);
                }
            }
        }
    }

    public static int pixelDensity() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
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
