package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PhonePublicChatHud {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final long FADE_OUT_MS = 1400L;
    private static final long SLIDE_IN_MS = 180L;
    private static final Style BOLD = Style.EMPTY.withBold(true);

    private record PreparedItem(ChatPayloads.EntryData entry, boolean system, boolean self, boolean mention,
                                String sender, String time, String quoteHeader,
                                List<FormattedCharSequence> lines, int boxWidth, int boxHeight,
                                float alpha, int slideOffset) {
    }

    private PhonePublicChatHud() {
    }

    public static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !PhoneClientPrefs.modernChatHud()) {
            return;
        }
        if (minecraft.screen instanceof PhoneScreen || minecraft.screen instanceof ChatScreen) {
            return;
        }

        int maxMessages = PhoneClientPrefs.hudMaxMessages();
        List<ChatPayloads.EntryData> recent = PhonePublicChat.recentEntries(maxMessages * 2);
        if (recent.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int fadeSeconds = PhoneClientPrefs.hudFadeSeconds();
        long lifetimeMs = fadeSeconds <= 0 ? Long.MAX_VALUE : fadeSeconds * 1000L;
        Font font = PhoneFont.get();
        UUID selfUuid = minecraft.player.getUUID();

        int maxAreaWidth = Mth.clamp(Math.round(graphics.guiWidth() * 0.34F), 150, 260);
        int avatarSize = 18;
        int avatarGap = 5;
        int maxBubbleWidth = maxAreaWidth - avatarSize - avatarGap;
        float bodyScale = 0.84F;
        float metaScale = 0.72F;
        int bodyLineH = Math.max(8, Math.round(font.lineHeight * bodyScale));
        int metaLineH = Math.max(7, Math.round(font.lineHeight * metaScale));

        List<PreparedItem> visible = new ArrayList<>();
        for (int idx = recent.size() - 1; idx >= 0 && visible.size() < maxMessages; idx--) {
            ChatPayloads.EntryData entry = recent.get(idx);
            long age = Math.max(0L, now - entry.createdAt());
            if (fadeSeconds > 0 && age >= lifetimeMs) {
                continue;
            }
            float alpha = 1.0F;
            if (fadeSeconds > 0 && age > lifetimeMs - FADE_OUT_MS) {
                float t = (age - (lifetimeMs - FADE_OUT_MS)) / (float) FADE_OUT_MS;
                alpha = Mth.clamp(1.0F - t * t, 0.0F, 1.0F);
            }
            if (alpha <= 0.03F) {
                continue;
            }
            int slideOffset = 0;
            if (age < SLIDE_IN_MS) {
                float p = 1.0F - age / (float) SLIDE_IN_MS;
                slideOffset = -Math.round(8.0F * p * p);
            }

            if (entry.kind() == ChatStore.ENTRY_SYSTEM) {
                int wrapW = Math.max(40, Mth.floor((maxAreaWidth - 14) / metaScale));
                List<FormattedCharSequence> lines = font.split(Component.literal(entry.body()), wrapW);
                if (lines.isEmpty()) {
                    continue;
                }
                int widest = 0;
                for (FormattedCharSequence line : lines) {
                    widest = Math.max(widest, Math.round(font.width(line) * metaScale));
                }
                int w = Math.min(maxAreaWidth, widest + 12);
                int h = lines.size() * metaLineH + 6;
                visible.add(0, new PreparedItem(entry, true, false, false, "", "", null, lines, w, h, alpha, slideOffset));
                continue;
            }

            boolean isSelf = selfUuid.equals(entry.senderUuid());
            boolean mention = !isSelf && PhonePublicChat.mentionsSelf(entry.body());
            String sender = entry.senderName() == null || entry.senderName().isBlank() ? "?" : entry.senderName();
            String time = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.createdAt()), ZoneId.systemDefault()).format(CLOCK);

            PhonePublicChat.QuoteParts quote = PhonePublicChat.parseQuote(entry.body());
            String displayBody = quote != null ? quote.body() : entry.body();
            String quoteHeader = quote != null ? quote.header() : null;

            int padX = 7;
            int padY = 5;
            int wrapW = Math.max(40, Mth.floor((maxBubbleWidth - padX * 2) / bodyScale));
            List<FormattedCharSequence> lines = font.split(Component.literal(displayBody), wrapW);
            if (lines.isEmpty()) {
                lines = List.of(FormattedCharSequence.EMPTY);
            }
            int contentW = 0;
            for (FormattedCharSequence line : lines) {
                contentW = Math.max(contentW, Math.round(font.width(line) * bodyScale));
            }
            int headerW = Math.round(font.width(Component.literal(sender).withStyle(BOLD)) * metaScale)
                    + 6 + Math.round(font.width(time) * metaScale);
            if (mention) {
                headerW += 22;
            }
            contentW = Math.max(contentW, headerW);
            if (quoteHeader != null) {
                int qW = Math.min(maxBubbleWidth - padX * 2, Math.round(font.width(quoteHeader) * metaScale) + 10);
                contentW = Math.max(contentW, qW);
            }
            int boxW = Math.min(maxBubbleWidth, contentW + padX * 2);
            int quoteH = quoteHeader != null ? metaLineH + 5 : 0;
            int boxH = padY * 2 + metaLineH + 2 + quoteH + lines.size() * bodyLineH;
            visible.add(0, new PreparedItem(entry, false, isSelf, mention, sender, time, quoteHeader,
                    lines, boxW, boxH, alpha, slideOffset));
        }

        if (visible.isEmpty()) {
            return;
        }

        float bgOpacity = PhoneClientPrefs.hudOpacity();
        int baseLeft = 8;
        int cursorBottom = graphics.guiHeight() - 44;
        int itemGap = 4;

        for (int i = visible.size() - 1; i >= 0; i--) {
            PreparedItem item = visible.get(i);
            int rowHeight = item.system ? item.boxHeight : Math.max(avatarSize + 2, item.boxHeight);
            int top = cursorBottom - rowHeight;
            if (top < 24) {
                break;
            }
            int x = baseLeft + item.slideOffset;
            if (item.system) {
                int bgA = Mth.clamp(Math.round(bgOpacity * item.alpha * 195.0F), 0, 255);
                int textA = Mth.clamp(Math.round(item.alpha * 230.0F), 4, 255);
                PhoneUi.fillRounded(graphics, x, top, item.boxWidth, item.boxHeight, 5, (bgA << 24) | 0x161920);
                int ly = top + 3;
                for (FormattedCharSequence line : item.lines) {
                    drawScaledSeq(graphics, font, line, x + 6, ly, (textA << 24) | 0xC8CDD6, metaScale);
                    ly += metaLineH;
                }
            } else {
                ResourceLocation skin = PhoneChatSurfaceRenderer.skinOf(item.entry.senderUuid());
                PhoneUi.renderRoundedFace(graphics, skin, x, top + 1, avatarSize, 4, item.alpha);

                int bx = x + avatarSize + avatarGap;
                int bgA = Mth.clamp(Math.round(bgOpacity * item.alpha * 235.0F), 0, 255);
                int borderA = Mth.clamp(Math.round(item.alpha * (item.mention ? 210.0F : 48.0F)), 0, 255);
                int bgRgb = item.mention ? 0x24201A : 0x161920;
                int borderRgb = item.mention ? 0xFFB020 : 0xFFFFFF;
                PhoneUi.fillRounded(graphics, bx, top, item.boxWidth, item.boxHeight, 6, (bgA << 24) | bgRgb);
                PhoneUi.strokeRounded(graphics, bx, top, item.boxWidth, item.boxHeight, 6, 1, (borderA << 24) | borderRgb);

                int textA = Mth.clamp(Math.round(item.alpha * 255.0F), 4, 255);
                int subA = Mth.clamp(Math.round(item.alpha * 165.0F), 4, 255);
                int nameColor = (textA << 24) | (item.self ? 0x6EE7A0 : 0x66C2FF);

                int innerX = bx + 7;
                int innerY = top + 5;
                Component nameComp = Component.literal(item.sender).withStyle(BOLD);
                drawScaledComp(graphics, font, nameComp, innerX, innerY, nameColor, metaScale);
                int afterNameX = innerX + Math.round(font.width(nameComp) * metaScale) + 5;
                if (item.mention) {
                    int badgeA = Mth.clamp(Math.round(item.alpha * 220.0F), 0, 255);
                    PhoneUi.fillRounded(graphics, afterNameX, innerY - 1, 16, metaLineH + 1, 3, (badgeA << 24) | 0xFFB020);
                    drawScaledComp(graphics, font, Component.literal("@").withStyle(BOLD), afterNameX + 5, innerY,
                            (textA << 24) | 0x1A1610, metaScale);
                }
                int timeW = Math.round(font.width(item.time) * metaScale);
                drawScaledComp(graphics, font, Component.literal(item.time), bx + item.boxWidth - 6 - timeW, innerY,
                        (subA << 24) | 0x9AA2AE, metaScale);

                int cy = innerY + metaLineH + 2;
                if (item.quoteHeader != null) {
                    int qMaxW = item.boxWidth - 14;
                    int qA = Mth.clamp(Math.round(item.alpha * 55.0F), 0, 255);
                    PhoneUi.fillRounded(graphics, innerX, cy, qMaxW, metaLineH + 3, 3, (qA << 24) | 0xFFFFFF);
                    graphics.fill(innerX, cy + 1, innerX + 2, cy + metaLineH + 2, (textA << 24) | 0x3AA8F0);
                    String clippedQ = font.plainSubstrByWidth(item.quoteHeader, Math.max(12, Mth.floor((qMaxW - 8) / metaScale)));
                    drawScaledComp(graphics, font, Component.literal(clippedQ), innerX + 5, cy + 2,
                            (subA << 24) | 0xC4CAD4, metaScale);
                    cy += metaLineH + 5;
                }

                for (FormattedCharSequence line : item.lines) {
                    drawScaledSeq(graphics, font, line, innerX, cy, (textA << 24) | 0xF2F4F8, bodyScale);
                    cy += bodyLineH;
                }
            }
            cursorBottom = top - itemGap;
        }
    }

    private static void drawScaledComp(GuiGraphics graphics, Font font, Component text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawScaledSeq(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
