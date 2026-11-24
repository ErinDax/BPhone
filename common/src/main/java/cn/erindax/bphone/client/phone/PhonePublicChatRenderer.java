package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhonePublicChatRenderer {
    private static final String[] KAOMOJI = {
            "(^o^)", "(^_^)b", "(>_<)", "(T_T)",
            "(=^.^=)", "(o_O)", "(^_~)", "(^-^)/"
    };
    private static final int EMOJI_COLS = 4;
    private static final int EMOJI_ROWS = 2;

    private static final int MENU_REPLY = 0;
    private static final int MENU_MENTION = 1;
    private static final int MENU_DIRECT = 2;
    private static final int MENU_COPY = 3;

    private PhonePublicChatRenderer() {
    }

    static int leadHeight(PhoneChatLayout layout) {
        return layout.rowHeight + layout.u(6) + layout.sectionHeight;
    }

    private static UiRect leadCard(PhoneChatLayout layout, UiRect viewport, int scroll) {
        int margin = layout.u(4);
        return new UiRect(viewport.left + margin, viewport.top + layout.u(4) - scroll,
                viewport.width - 2 * margin, layout.rowHeight);
    }

    static boolean leadHit(PhoneChatLayout layout, UiRect viewport, int scroll, double mouseX, double mouseY) {
        return viewport.contains(mouseX, mouseY) && leadCard(layout, viewport, scroll).contains(mouseX, mouseY);
    }

    static void renderLead(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect viewport, int scroll) {
        UiRect row = leadCard(layout, viewport, scroll);
        if (row.bottom() + layout.sectionHeight <= viewport.top) {
            return;
        }

        PhoneClientChatState.Conversation conversation = PhonePublicChat.conversation();
        int cardRadius = Math.max(3, layout.radius + layout.u(2));
        int background = hovered(screen, viewport) && hovered(screen, row) ? HOVER : SURFACE;
        fillRounded(guiGraphics, row.left, row.top, row.width, row.height, cardRadius, background);
        strokeRounded(guiGraphics, row.left, row.top, row.width, row.height, cardRadius, 1, HAIRLINE);

        UiRect icon = layout.rowAvatar(row, layout.avatar);
        fillRounded(guiGraphics, icon.left, icon.top, icon.width, icon.height, Math.max(3, icon.width / 4), ACCENT);
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

        UiRect section = new UiRect(viewport.left, row.bottom() + layout.u(2), viewport.width, layout.sectionHeight);
        renderSection(guiGraphics, layout, section, Component.translatable("phone.chat.public.section").getString());
    }

    private static String preview(ChatPayloads.EntryData last) {
        if (last == null) {
            return Component.translatable("phone.chat.public.preview.empty").getString();
        }
        PhonePublicChat.QuoteParts quote = PhonePublicChat.parseQuote(last.body());
        String body = (quote != null ? quote.body() : last.body()).replace('\n', ' ');
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

    private static void renderGearButton(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds) {
        int bg = renderIconHover(screen, guiGraphics, layout, bounds, SURFACE);
        int ring = Math.max(6, layout.titleHeight + 1);
        if (ring % 2 != 0) {
            ring++;
        }
        int cx = bounds.left + bounds.width / 2;
        int cy = bounds.top + (bounds.height - 1) / 2;
        int stroke = Math.max(1, layout.u(1.4F));
        int tooth = Math.max(1, layout.u(1.2F));
        guiGraphics.fill(cx - tooth, cy - ring / 2 - 1, cx + tooth, cy + ring / 2 + 1, ACCENT);
        guiGraphics.fill(cx - ring / 2 - 1, cy - tooth, cx + ring / 2 + 1, cy + tooth, ACCENT);
        fillRounded(guiGraphics, cx - ring / 2, cy - ring / 2, ring, ring, ring / 2, ACCENT);
        int hole = Math.max(2, ring - 2 * stroke - 2);
        fillRounded(guiGraphics, cx - hole / 2, cy - hole / 2, hole, hole, hole / 2, bg);
    }

    private static UiRect emojiButton(PhoneChatLayout layout) {
        UiRect bar = layout.composer();
        return new UiRect(bar.left + layout.pad, bar.top + (bar.height - layout.fieldHeight) / 2,
                layout.fieldHeight, layout.fieldHeight);
    }

    private static UiRect draftField(PhoneChatLayout layout, Component sendLabel) {
        UiRect send = layout.sendButton(sendLabel);
        UiRect emoji = emojiButton(layout);
        int left = emoji.right() + layout.gap;
        return new UiRect(left, send.top, Math.max(layout.u(20), send.left - layout.gap - left), layout.fieldHeight);
    }

    private static int replyBarHeight(PhoneChatApp app, PhoneChatLayout layout) {
        return app.publicReplySnippet.isEmpty() ? 0 : layout.captionHeight + layout.u(8);
    }

    private static UiRect replyBar(PhoneChatApp app, PhoneChatLayout layout) {
        int h = replyBarHeight(app, layout);
        UiRect composer = layout.composer();
        return new UiRect(composer.left, composer.top - h, composer.width, h);
    }

    private static UiRect replyCancelButton(PhoneChatApp app, PhoneChatLayout layout) {
        UiRect bar = replyBar(app, layout);
        return new UiRect(bar.right() - bar.height, bar.top, bar.height, bar.height);
    }

    private static UiRect messagesViewport(PhoneChatApp app, PhoneChatLayout layout) {
        int top = layout.threadHeader().bottom();
        int bottom = layout.composer().top - replyBarHeight(app, layout);
        return new UiRect(layout.surface.left, top, layout.surface.width, Math.max(1, bottom - top));
    }

    private static UiRect emojiSheet(PhoneChatApp app, PhoneChatLayout layout) {
        int pad = layout.u(4);
        int width = layout.surface.width - 2 * layout.pad;
        int rowH = layout.smallButtonHeight + layout.u(2);
        int height = EMOJI_ROWS * rowH + (EMOJI_ROWS + 1) * pad;
        int top = messagesViewport(app, layout).bottom() - height - layout.u(3);
        return new UiRect(layout.surface.left + layout.pad, top, width, height);
    }

    private static UiRect emojiCell(PhoneChatApp app, PhoneChatLayout layout, int index) {
        UiRect sheet = emojiSheet(app, layout);
        int pad = layout.u(4);
        int cellW = (sheet.width - (EMOJI_COLS + 1) * pad) / EMOJI_COLS;
        int cellH = layout.smallButtonHeight + layout.u(2);
        int col = index % EMOJI_COLS;
        int row = index / EMOJI_COLS;
        return new UiRect(sheet.left + pad + col * (cellW + pad), sheet.top + pad + row * (cellH + pad), cellW, cellH);
    }

    static void render(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        PhoneClientChatState.Conversation conversation = PhonePublicChat.conversation();
        UiRect header = layout.threadHeader();
        guiGraphics.fill(header.left, header.top, header.right(), header.bottom(), SURFACE);
        guiGraphics.fill(header.left, header.bottom() - 1, header.right(), header.bottom(), HAIRLINE);
        renderBack(screen, guiGraphics, layout, layout.backButton(header));
        renderGearButton(screen, guiGraphics, layout, layout.actionButton(header));

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

        UiRect viewport = messagesViewport(app, layout);
        if (conversation.entries.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("phone.chat.public.empty"),
                    Component.translatable("phone.chat.public.hint"));
        } else {
            renderItems(app, guiGraphics, layout, viewport, conversation);
        }

        if (!app.publicReplySnippet.isEmpty()) {
            UiRect rBar = replyBar(app, layout);
            guiGraphics.fill(rBar.left, rBar.top, rBar.right(), rBar.bottom(), SURFACE);
            guiGraphics.fill(rBar.left, rBar.top, rBar.right(), rBar.top + 1, HAIRLINE);
            guiGraphics.fill(rBar.left + layout.pad, rBar.top + layout.u(3),
                    rBar.left + layout.pad + Math.max(1, layout.u(1.5F)), rBar.bottom() - layout.u(3), ACCENT);
            String prefix = app.publicReplySender.isEmpty()
                    ? app.publicReplySnippet
                    : Component.translatable("phone.chat.public.reply_prefix", app.publicReplySender, app.publicReplySnippet).getString();
            UiRect cancel = replyCancelButton(app, layout);
            int textLeft = rBar.left + layout.pad + layout.u(5);
            Component rComp = fit(layout, prefix, PLAIN, Math.max(16, cancel.left - layout.gap - textLeft), layout.captionScale);
            drawText(guiGraphics, layout, rComp, textLeft, rBar.top + (rBar.height - layout.captionHeight) / 2,
                    TEXT_SECONDARY, layout.captionScale);
            drawCentered(guiGraphics, layout, Component.literal("x").withStyle(BOLD),
                    cancel.left + cancel.width / 2, cancel.top + (cancel.height - layout.captionHeight) / 2,
                    hovered(screen, cancel) ? DANGER : TEXT_TERTIARY, layout.captionScale);
        }

        UiRect composer = layout.composer();
        guiGraphics.fill(composer.left, composer.top, composer.right(), composer.bottom(), SURFACE);
        guiGraphics.fill(composer.left, composer.top, composer.right(), composer.top + 1, HAIRLINE);

        UiRect emojiBtn = emojiButton(layout);
        int emojiBg = app.publicEmojiOpen ? ACCENT_SOFT : (hovered(screen, emojiBtn) ? SECONDARY_HOVER : SECONDARY);
        fillRounded(guiGraphics, emojiBtn.left, emojiBtn.top, emojiBtn.width, emojiBtn.height, layout.radius, emojiBg);
        renderSmileGlyph(guiGraphics, layout, emojiBtn, ACCENT);

        Component sendLabel = Component.translatable("screen.bphone.phone.chat.send");
        renderField(guiGraphics, layout, draftField(layout, sendLabel), app.publicDraft,
                Component.translatable("phone.chat.public.placeholder"),
                app.composerFocused ? ACCENT : FIELD, FIELD, app.composerFocused);
        renderButton(screen, guiGraphics, layout, layout.sendButton(sendLabel), sendLabel,
                app.canSendPublic() ? Tone.PRIMARY : Tone.DISABLED);

        if (app.publicEmojiOpen) {
            renderEmojiPopover(screen, app, guiGraphics, layout);
        }
        if (app.publicMenuEntry != null) {
            renderContextMenu(screen, app, guiGraphics, layout);
        }
    }

    private static void renderSmileGlyph(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect bounds, int color) {
        int size = Math.max(8, bounds.width * 3 / 5);
        if ((bounds.width - size) % 2 != 0) {
            size++;
        }
        int left = bounds.left + (bounds.width - size) / 2;
        int top = bounds.top + (bounds.height - size) / 2;
        strokeRounded(guiGraphics, left, top, size, size, size / 2, Math.max(1, pixelDensity()), color);
        int eye = Math.max(1, size / 7);
        int eyeY = top + Math.round(size * 0.34F);
        guiGraphics.fill(left + Math.round(size * 0.30F), eyeY, left + Math.round(size * 0.30F) + eye, eyeY + eye, color);
        guiGraphics.fill(left + size - Math.round(size * 0.30F) - eye, eyeY, left + size - Math.round(size * 0.30F), eyeY + eye, color);
        int mouthW = Math.max(3, size * 2 / 5);
        int mouthX = left + (size - mouthW) / 2;
        int mouthY = top + Math.round(size * 0.65F);
        guiGraphics.fill(mouthX, mouthY, mouthX + mouthW, mouthY + Math.max(1, size / 9), color);
    }

    private static void renderEmojiPopover(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect sheet = emojiSheet(app, layout);
        int r = Math.max(3, layout.radius + layout.u(2));
        fillRounded(guiGraphics, sheet.left - 1, sheet.top - 1, sheet.width + 2, sheet.height + 3, r + 1, 0x26000000);
        fillRounded(guiGraphics, sheet.left, sheet.top, sheet.width, sheet.height, r, SURFACE);
        strokeRounded(guiGraphics, sheet.left, sheet.top, sheet.width, sheet.height, r, 1, HAIRLINE);
        for (int i = 0; i < KAOMOJI.length; i++) {
            UiRect cell = emojiCell(app, layout, i);
            fillRounded(guiGraphics, cell.left, cell.top, cell.width, cell.height, layout.radius,
                    hovered(screen, cell) ? ACCENT_SOFT : FIELD);
            Component label = fit(layout, KAOMOJI[i], PLAIN, cell.width - 2, layout.captionScale);
            drawCentered(guiGraphics, layout, label, cell.left + cell.width / 2,
                    cell.top + (cell.height - layout.captionHeight) / 2, TEXT, layout.captionScale);
        }
    }

    private static List<Integer> menuActions(PhoneScreen screen, PhoneChatApp app, ChatPayloads.EntryData entry) {
        List<Integer> list = new ArrayList<>(4);
        if (entry.kind() == ChatStore.ENTRY_TEXT) {
            list.add(MENU_REPLY);
            UUID self = app.self();
            boolean otherPlayer = entry.senderUuid() != null && !Util.NIL_UUID.equals(entry.senderUuid())
                    && (self == null || !self.equals(entry.senderUuid()));
            if (!entry.senderName().isBlank() && otherPlayer) {
                list.add(MENU_MENTION);
            }
            if (otherPlayer && !screen.isHomePhoneMode()) {
                list.add(MENU_DIRECT);
            }
        }
        list.add(MENU_COPY);
        return list;
    }

    private static Component menuLabel(int action) {
        return Component.translatable(switch (action) {
            case MENU_REPLY -> "phone.chat.public.menu.reply";
            case MENU_MENTION -> "phone.chat.public.menu.mention";
            case MENU_DIRECT -> "phone.chat.public.menu.direct";
            default -> "phone.chat.public.menu.copy";
        });
    }

    private static UiRect contextMenuBounds(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout) {
        List<Integer> actions = menuActions(screen, app, app.publicMenuEntry);
        int itemH = layout.smallButtonHeight + layout.u(3);
        int pad = layout.u(3);
        int maxW = layout.u(58);
        for (int action : actions) {
            maxW = Math.max(maxW, layout.width(menuLabel(action), layout.captionScale) + 2 * layout.pad);
        }
        int width = Math.min(layout.surface.width - 2 * layout.pad, maxW);
        int height = actions.size() * itemH + 2 * pad;
        UiRect vp = messagesViewport(app, layout);
        int left = Mth.clamp(app.publicMenuX, vp.left + layout.pad, vp.right() - layout.pad - width);
        int top = Mth.clamp(app.publicMenuY, vp.top + layout.u(2), vp.bottom() - layout.u(2) - height);
        return new UiRect(left, top, width, height);
    }

    private static UiRect contextMenuItem(UiRect menu, PhoneChatLayout layout, int index) {
        int pad = layout.u(3);
        int itemH = layout.smallButtonHeight + layout.u(3);
        return new UiRect(menu.left + pad, menu.top + pad + index * itemH, menu.width - 2 * pad, itemH);
    }

    private static void renderContextMenu(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect menu = contextMenuBounds(screen, app, layout);
        int r = Math.max(3, layout.radius + layout.u(1));
        fillRounded(guiGraphics, menu.left - 1, menu.top - 1, menu.width + 2, menu.height + 3, r + 1, 0x30000000);
        fillRounded(guiGraphics, menu.left, menu.top, menu.width, menu.height, r, SURFACE);
        strokeRounded(guiGraphics, menu.left, menu.top, menu.width, menu.height, r, 1, HAIRLINE);
        List<Integer> actions = menuActions(screen, app, app.publicMenuEntry);
        for (int i = 0; i < actions.size(); i++) {
            UiRect row = contextMenuItem(menu, layout, i);
            if (hovered(screen, row)) {
                fillRounded(guiGraphics, row.left, row.top, row.width, row.height, layout.radius, HOVER);
            }
            Component label = menuLabel(actions.get(i));
            drawText(guiGraphics, layout, label, row.left + layout.u(5),
                    row.top + (row.height - layout.captionHeight) / 2, TEXT, layout.captionScale);
        }
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
                    if (item.incoming()) {
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
        if (app.publicMenuEntry != null) {
            UiRect menu = contextMenuBounds(screen, app, layout);
            if (button == 0 && menu.contains(mouseX, mouseY)) {
                List<Integer> actions = menuActions(screen, app, app.publicMenuEntry);
                for (int i = 0; i < actions.size(); i++) {
                    if (contextMenuItem(menu, layout, i).contains(mouseX, mouseY)) {
                        runMenuAction(app, app.publicMenuEntry, actions.get(i));
                        return;
                    }
                }
            }
            app.publicMenuEntry = null;
            return;
        }

        if (app.publicEmojiOpen) {
            UiRect sheet = emojiSheet(app, layout);
            if (button == 0 && sheet.contains(mouseX, mouseY)) {
                for (int i = 0; i < KAOMOJI.length; i++) {
                    if (emojiCell(app, layout, i).contains(mouseX, mouseY)) {
                        app.insertPublicText(KAOMOJI[i]);
                        return;
                    }
                }
                return;
            }
            if (button == 0 && emojiButton(layout).contains(mouseX, mouseY)) {
                app.publicEmojiOpen = false;
                return;
            }
            app.publicEmojiOpen = false;
        }

        UiRect header = layout.threadHeader();
        if (button == 0 && layout.backButton(header).contains(mouseX, mouseY)) {
            if (!app.back()) {
                screen.closeChatApp();
            }
            return;
        }
        if (button == 0 && layout.actionButton(header).contains(mouseX, mouseY)) {
            screen.openPublicChatSettings();
            return;
        }

        if (!app.publicReplySnippet.isEmpty() && button == 0 && replyCancelButton(app, layout).contains(mouseX, mouseY)) {
            app.clearPublicReply();
            return;
        }

        if (button == 0 && emojiButton(layout).contains(mouseX, mouseY)) {
            app.publicEmojiOpen = !app.publicEmojiOpen;
            return;
        }

        Component sendLabel = Component.translatable("screen.bphone.phone.chat.send");
        if (button == 0 && draftField(layout, sendLabel).contains(mouseX, mouseY)) {
            app.focusComposer();
            return;
        }
        if (button == 0 && layout.sendButton(sendLabel).contains(mouseX, mouseY)) {
            app.sendPublic();
            return;
        }

        UiRect viewport = messagesViewport(app, layout);
        if (!viewport.contains(mouseX, mouseY)) {
            return;
        }

        List<PhoneChatThread.Item> items = PhoneChatThread.build(layout, PhonePublicChat.conversation(), app.self(), false,
                viewport);
        for (PhoneChatThread.Placed placed : PhoneChatThread.place(layout, items, viewport, app.publicScroll,
                PhoneChatThread.avatarInset(layout))) {
            PhoneChatThread.Item item = placed.item();
            if (item.entry() == null) {
                continue;
            }
            if (item.isMessage() && item.incoming()) {
                UiRect face = new UiRect(viewport.left + layout.pad, placed.top(), layout.avatarSmall, layout.avatarSmall);
                if (face.contains(mouseX, mouseY)) {
                    app.publicMenuEntry = item.entry();
                    app.publicMenuX = Mth.floor(mouseX);
                    app.publicMenuY = Mth.floor(mouseY);
                    return;
                }
            }
            if (button == 1 && (item.type() == PhoneChatThread.ITEM_TEXT || item.type() == PhoneChatThread.ITEM_SYSTEM)
                    && placed.body().contains(mouseX, mouseY)) {
                app.publicMenuEntry = item.entry();
                app.publicMenuX = Mth.floor(mouseX);
                app.publicMenuY = Mth.floor(mouseY);
                return;
            }
        }

        if (button == 0) {
            app.unfocusComposer();
        }
    }

    private static void runMenuAction(PhoneChatApp app, ChatPayloads.EntryData entry, int action) {
        switch (action) {
            case MENU_REPLY -> app.setPublicReply(entry.senderName(), entry.body());
            case MENU_MENTION -> app.mentionInPublic(entry.senderName());
            case MENU_DIRECT -> app.startDirectFromPublic(entry.senderUuid(), entry.senderName());
            default -> {
                PhonePublicChat.QuoteParts parsed = PhonePublicChat.parseQuote(entry.body());
                String text = parsed != null ? parsed.body() : entry.body();
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                app.publicMenuEntry = null;
                app.status(Component.translatable("phone.chat.copied"));
            }
        }
    }
}
