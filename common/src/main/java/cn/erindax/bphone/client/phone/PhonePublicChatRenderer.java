package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhonePublicChatRenderer {
    private PhonePublicChatRenderer() {
    }

    static int leadHeight(PhoneChatLayout layout) {
        return layout.rowHeight + layout.sectionHeight;
    }

    private static UiRect leadRow(PhoneChatLayout layout, UiRect viewport, int scroll) {
        return new UiRect(viewport.left, viewport.top - scroll, viewport.width, layout.rowHeight);
    }

    static boolean leadHit(PhoneChatLayout layout, UiRect viewport, int scroll, double mouseX, double mouseY) {
        return viewport.contains(mouseX, mouseY) && leadRow(layout, viewport, scroll).contains(mouseX, mouseY);
    }

    static void renderLead(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect viewport, int scroll) {
        UiRect row = leadRow(layout, viewport, scroll);
        if (row.bottom() + layout.sectionHeight <= viewport.top) {
            return;
        }

        PhoneClientChatState.Conversation conversation = PhonePublicChat.conversation();
        int background = hovered(screen, viewport) && hovered(screen, row) ? HOVER : SURFACE;
        guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), background);

        UiRect icon = layout.rowAvatar(row, layout.avatar);
        fillRounded(guiGraphics, icon.left, icon.top, icon.width, icon.height, Math.max(2, icon.width / 5), ACCENT);
        renderGlobe(guiGraphics, layout, icon, WHITE, ACCENT);

        ChatPayloads.EntryData last = conversation.lastEntry();
        int blockHeight = layout.bodyHeight + layout.textGap + layout.captionHeight;
        int top = row.top + (row.height - blockHeight) / 2;
        int reserve = 0;
        if (last != null) {
            Component timeText = Component.literal(PhoneChatThread.timeLabel(last.createdAt(), true));
            int timeWidth = layout.width(timeText, layout.captionScale);
            drawText(guiGraphics, layout, timeText, row.right() - layout.pad - timeWidth,
                    top + Math.max(0, layout.bodyHeight - layout.captionHeight), TEXT_TERTIARY, layout.captionScale);
            reserve = timeWidth;
        }
        if (conversation.unread > 0) {
            int badgeWidth = badgeWidth(layout, conversation.unread);
            renderBadge(guiGraphics, layout, row.right() - layout.pad - badgeWidth - Math.max(1, layout.u(1)),
                    top + layout.bodyHeight + layout.textGap + layout.captionHeight / 2, conversation.unread);
            reserve = Math.max(reserve, badgeWidth);
        }

        int textLeft = layout.rowTextLeft(row, layout.avatar);
        int textRight = row.right() - layout.pad - reserve - layout.gap;
        renderTwoLineText(guiGraphics, layout, row, textLeft, textRight,
                Component.translatable("phone.chat.public.title").getString(), TEXT, preview(last), TEXT_TERTIARY);

        UiRect section = new UiRect(viewport.left, row.bottom(), viewport.width, layout.sectionHeight);
        renderSection(guiGraphics, layout, section, Component.translatable("phone.chat.public.section").getString());
    }

    private static String preview(ChatPayloads.EntryData last) {
        if (last == null) {
            return Component.translatable("phone.chat.public.preview.empty").getString();
        }
        String body = last.body().replace('\n', ' ');
        if (last.kind() == ChatStore.ENTRY_SYSTEM) {
            return body;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        if (self != null && self.equals(last.senderUuid())) {
            return Component.translatable("screen.bphone.phone.chat.preview.outgoing", body).getString();
        }
        return Component.translatable("phone.chat.preview.group", last.senderName(), body).getString();
    }

    static void renderGlobe(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds, int color, int background) {
        int size = Math.max(8, bounds.width * 3 / 5);
        if ((bounds.width - size) % 2 != 0) {
            size++;
        }
        int left = bounds.left + (bounds.width - size) / 2;
        int top = bounds.top + (bounds.height - size) / 2;
        int stroke = Math.max(1, layout.u(1));
        fillRounded(guiGraphics, left, top, size, size, size / 2, color);
        fillRounded(guiGraphics, left + stroke, top + stroke, size - 2 * stroke, size - 2 * stroke,
                (size - 2 * stroke) / 2, background);

        int narrow = Math.max(3 * stroke, size * 2 / 5);
        if ((size - narrow) % 2 != 0) {
            narrow++;
        }
        int narrowLeft = left + (size - narrow) / 2;
        fillRounded(guiGraphics, narrowLeft, top, narrow, size, narrow / 2, color);
        fillRounded(guiGraphics, narrowLeft + stroke, top + stroke, narrow - 2 * stroke, size - 2 * stroke,
                Math.max(1, (narrow - 2 * stroke) / 2), background);

        int middle = top + (size - stroke) / 2;
        guiGraphics.fill(left + stroke, middle, left + size - stroke, middle + stroke, color);
    }

    private static UiRect draftField(PhoneChatLayout layout, Component sendLabel) {
        UiRect bar = layout.composer();
        UiRect send = layout.sendButton(sendLabel);
        int left = bar.left + layout.pad;
        return new UiRect(left, send.top, Math.max(layout.u(20), send.left - layout.gap - left), layout.fieldHeight);
    }

    static void render(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        PhoneClientChatState.Conversation conversation = PhonePublicChat.conversation();
        UiRect header = layout.threadHeader();
        guiGraphics.fill(header.left, header.top, header.right(), header.bottom(), SURFACE);
        guiGraphics.fill(header.left, header.bottom() - 1, header.right(), header.bottom(), HAIRLINE);
        renderBack(screen, guiGraphics, layout, layout.backButton(header));

        int titleWidth = header.width - 2 * header.height;
        int centerX = header.left + header.width / 2;
        int blockHeight = layout.titleHeight + layout.textGap + layout.captionHeight;
        int nameY = header.top + (header.height - 1 - blockHeight) / 2;
        drawCentered(guiGraphics, layout, fit(layout, Component.translatable("phone.chat.public.title").getString(), BOLD,
                titleWidth, layout.titleScale), centerX, nameY, TEXT, layout.titleScale);
        drawCentered(guiGraphics, layout, fit(layout,
                        Component.translatable("phone.chat.public.online", PhonePublicChat.onlineCount()).getString(), PLAIN,
                        titleWidth, layout.captionScale),
                centerX, nameY + layout.titleHeight + layout.textGap, TEXT_TERTIARY, layout.captionScale);

        UiRect viewport = layout.threadMessages();
        if (conversation.entries.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("phone.chat.public.empty"),
                    Component.translatable("phone.chat.public.hint"));
        } else {
            renderItems(app, guiGraphics, layout, viewport, conversation);
        }

        UiRect composer = layout.composer();
        guiGraphics.fill(composer.left, composer.top, composer.right(), composer.bottom(), SURFACE);
        guiGraphics.fill(composer.left, composer.top, composer.right(), composer.top + 1, HAIRLINE);
        Component sendLabel = Component.translatable("screen.bphone.phone.chat.send");
        renderField(guiGraphics, layout, draftField(layout, sendLabel), app.publicDraft,
                Component.translatable("phone.chat.public.placeholder"), FIELD, FIELD);
        renderButton(screen, guiGraphics, layout, layout.sendButton(sendLabel), sendLabel,
                app.canSendPublic() ? Tone.PRIMARY : Tone.DISABLED);
    }

    private static void renderItems(PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect viewport,
                                    PhoneClientChatState.Conversation conversation) {
        List<PhoneChatThread.Item> items = PhoneChatThread.build(layout, conversation, app.self(), false, viewport);
        int contentHeight = PhoneChatThread.contentHeight(layout, items);
        long tail = conversation.lastEntryId();
        if (app.publicTailId != 0L && tail != app.publicTailId && app.publicScroll > 0) {
            app.publicScroll += Math.max(0, contentHeight - app.publicContentHeight);
        }
        app.publicTailId = tail;
        app.publicContentHeight = contentHeight;
        app.publicScroll = Mth.clamp(app.publicScroll, 0, PhoneChatLayout.maxScroll(viewport, contentHeight));

        List<PhoneChatThread.Placed> placed = PhoneChatThread.place(layout, items, viewport, app.publicScroll,
                PhoneChatThread.avatarInset(layout));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (PhoneChatThread.Placed entry : placed) {
            PhoneChatThread.Item item = entry.item();
            if (entry.top() + item.height() <= viewport.top || entry.top() >= viewport.bottom()) {
                continue;
            }
            UiRect body = entry.body();
            switch (item.type()) {
                case PhoneChatThread.ITEM_MORE, PhoneChatThread.ITEM_TIME -> PhoneChatSurfaceRenderer.renderCaptionLines(
                        guiGraphics, layout, item, body, TEXT_TERTIARY);
                case PhoneChatThread.ITEM_SYSTEM -> {
                    fillRounded(guiGraphics, body.left, body.top, body.width, body.height, layout.radius, SYSTEM_PILL);
                    PhoneChatSurfaceRenderer.renderCaptionLines(guiGraphics, layout, item, body, TEXT_SECONDARY);
                }
                default -> {
                    PhoneChatSurfaceRenderer.renderMessage(app, guiGraphics, layout, viewport, entry);
                    if (item.incoming() && item.sender() != null) {
                        UiRect face = new UiRect(viewport.left + layout.pad, entry.top(), layout.avatarSmall,
                                layout.avatarSmall);
                        renderFace(guiGraphics, PhoneChatSurfaceRenderer.skinOf(item.entry().senderUuid()), face, PAGE);
                    }
                }
            }
        }
        guiGraphics.disableScissor();
    }

    static void click(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout, double mouseX, double mouseY, int button) {
        if (button == 0 && layout.backButton(layout.threadHeader()).contains(mouseX, mouseY)) {
            if (!app.back()) {
                screen.closeChatApp();
            }
            return;
        }
        if (button == 0 && layout.sendButton(Component.translatable("screen.bphone.phone.chat.send")).contains(mouseX, mouseY)) {
            app.sendPublic();
            return;
        }

        UiRect viewport = layout.threadMessages();
        if (button != 1 || !viewport.contains(mouseX, mouseY)) {
            return;
        }
        List<PhoneChatThread.Item> items = PhoneChatThread.build(layout, PhonePublicChat.conversation(), app.self(), false,
                viewport);
        for (PhoneChatThread.Placed placed : PhoneChatThread.place(layout, items, viewport, app.publicScroll,
                PhoneChatThread.avatarInset(layout))) {
            PhoneChatThread.Item item = placed.item();
            if (item.entry() == null || !placed.body().contains(mouseX, mouseY)) {
                continue;
            }
            if (item.type() == PhoneChatThread.ITEM_TEXT || item.type() == PhoneChatThread.ITEM_SYSTEM) {
                Minecraft.getInstance().keyboardHandler.setClipboard(item.entry().body());
                app.status(Component.translatable("phone.chat.copied"));
                return;
            }
        }
    }
}
