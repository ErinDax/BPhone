package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhoneContact;
import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneChatSurfaceRenderer {
    private static final int HIGHLIGHT = 0xFFFFB020;
    private static final int VIEWER_BACKDROP = 0xFF101114;
    private static final int SCAN_SHADE = 0xB8141A22;
    private static final int SCAN_SUCCESS = 0xFF06C755;
    private static final ResourceLocation CAMERA_ICON = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_camera.png");

    private PhoneChatSurfaceRenderer() {
    }

    static void render(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatApp app = screen.chat;
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);

        switch (app.page) {
            case PhoneChatApp.PAGE_ADD -> renderAddMenu(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_SELECTOR -> renderSelector(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_THREAD -> renderThread(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_SEARCH -> renderSearch(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_GROUP_CREATE, PhoneChatApp.PAGE_INVITE -> renderPicker(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_INFO -> renderInfo(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_BLOCKED -> renderBlocked(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_IMAGE_PICK -> renderImagePick(screen, app, guiGraphics, layout);
            case PhoneChatApp.PAGE_IMAGE_VIEW -> renderImageView(screen, app, guiGraphics, layout);
            default -> {
                if (app.tab == PhoneChatLayout.TAB_CONTACTS) {
                    renderContacts(screen, app, guiGraphics, layout);
                } else if (app.tab == PhoneChatLayout.TAB_PROFILE) {
                    renderProfile(screen, app, guiGraphics, layout);
                } else {
                    renderChats(screen, app, guiGraphics, layout);
                }
                renderTabBar(screen, app, guiGraphics, layout);
            }
        }
    }

    static boolean click(PhoneScreen screen, double mouseX, double mouseY, int button) {
        PhoneChatApp app = screen.chat;
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        switch (app.page) {
            case PhoneChatApp.PAGE_ADD -> clickAddMenu(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_SELECTOR -> clickSelector(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_THREAD -> clickThread(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_SEARCH -> clickSearch(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_GROUP_CREATE, PhoneChatApp.PAGE_INVITE -> clickPicker(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_INFO -> clickInfo(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_BLOCKED -> clickBlocked(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_IMAGE_PICK -> clickImagePick(screen, app, layout, mouseX, mouseY, button);
            case PhoneChatApp.PAGE_IMAGE_VIEW -> goBack(screen, app);
            default -> clickTabs(screen, app, layout, mouseX, mouseY, button);
        }
        return true;
    }

    private static void goBack(PhoneScreen screen, PhoneChatApp app) {
        if (!app.back()) {
            screen.closeChatApp();
        }
    }

    private static boolean backHit(PhoneChatLayout layout, UiRect header, double mouseX, double mouseY, int button) {
        return button == 0 && layout.backButton(header).contains(mouseX, mouseY);
    }

    private static void renderChats(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect header = layout.header();
        renderHeader(screen, guiGraphics, layout, header, Component.translatable("screen.bphone.phone.title.chat"), false, true);
        renderSearchButton(screen, guiGraphics, layout, layout.backButton(header));

        UiRect viewport = layout.listViewport();
        List<PhoneClientChatState.Conversation> conversations = PhoneClientChatState.getConversations();
        if (conversations.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("screen.bphone.phone.chat.empty"),
                    Component.translatable("screen.bphone.phone.chat.hint"));
            return;
        }

        int contentHeight = layout.listContentHeight(conversations.size(), true);
        app.listScroll = Mth.clamp(app.listScroll, 0, PhoneChatLayout.maxScroll(viewport, contentHeight));
        boolean pointerInside = hovered(screen, viewport);
        UUID self = app.self();

        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < conversations.size(); index++) {
            UiRect row = layout.listRow(viewport, index, app.listScroll);
            if (row.bottom() <= viewport.top) {
                continue;
            }
            if (row.top >= viewport.bottom()) {
                break;
            }

            PhoneClientChatState.Conversation conversation = conversations.get(index);
            boolean menu = app.menuConversationId == conversation.id;
            int background = !menu && pointerInside && hovered(screen, row) ? HOVER : SURFACE;
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), background);

            UiRect face = layout.rowAvatar(row, layout.avatar);
            renderConversationAvatar(screen, guiGraphics, layout, face, conversation, background);

            int textLeft = layout.rowTextLeft(row, layout.avatar);
            int textRight;
            if (menu) {
                Component label = listActionLabel(conversation);
                UiRect action = layout.rowAction(row, label);
                renderButton(screen, guiGraphics, layout, action, label, Tone.DANGER);
                textRight = action.left - layout.gap;
            } else {
                ChatPayloads.EntryData last = conversation.lastEntry();
                long time = last == null ? conversation.updatedAt : last.createdAt();
                Component timeText = Component.literal(PhoneChatThread.timeLabel(time, true));
                int timeWidth = layout.width(timeText, layout.captionScale);
                int blockHeight = layout.bodyHeight + layout.textGap + layout.captionHeight;
                int top = row.top + (row.height - blockHeight) / 2;
                drawText(guiGraphics, layout, timeText, row.right() - layout.pad - timeWidth,
                        top + Math.max(0, layout.bodyHeight - layout.captionHeight), TEXT_TERTIARY, layout.captionScale);
                int reserve = timeWidth;
                if (conversation.unread > 0) {
                    int badgeWidth = badgeWidth(layout, conversation.unread);
                    renderBadge(guiGraphics, layout, row.right() - layout.pad - badgeWidth - Math.max(1, layout.u(1)),
                            top + layout.bodyHeight + layout.textGap + layout.captionHeight / 2, conversation.unread);
                    reserve = Math.max(reserve, badgeWidth);
                }
                textRight = row.right() - layout.pad - reserve - layout.gap;
            }

            renderTwoLineText(guiGraphics, layout, row, textLeft, textRight, app.title(conversation), TEXT,
                    previewText(conversation, self), TEXT_TERTIARY);
            if (index < conversations.size() - 1) {
                guiGraphics.fill(textLeft, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
        }

        int hintTop = viewport.top + conversations.size() * layout.rowHeight - app.listScroll;
        renderListHint(guiGraphics, layout, viewport, hintTop);
        guiGraphics.disableScissor();
    }

    private static Component listActionLabel(PhoneClientChatState.Conversation conversation) {
        return conversation.isGroup()
                ? Component.translatable("phone.chat.list.clear")
                : Component.translatable("screen.bphone.phone.chat.delete");
    }

    static String previewText(PhoneClientChatState.Conversation conversation, UUID self) {
        ChatPayloads.EntryData last = conversation.lastEntry();
        if (last == null) {
            return Component.translatable("screen.bphone.phone.chat.preview.empty").getString();
        }
        String body = ChatNetworkingClient.preview(last).getString();
        if (last.kind() == ChatStore.ENTRY_SYSTEM) {
            return body;
        }
        if (self != null && self.equals(last.senderUuid())) {
            return Component.translatable("screen.bphone.phone.chat.preview.outgoing", body).getString();
        }
        if (conversation.isGroup()) {
            String sender = last.senderName().isBlank() ? last.senderNumber() : last.senderName();
            return Component.translatable("phone.chat.preview.group", sender, body).getString();
        }
        return body;
    }

    private static void renderConversationAvatar(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                                 UiRect face, PhoneClientChatState.Conversation conversation, int background) {
        if (conversation != null && conversation.isGroup()) {
            renderGroupAvatar(guiGraphics, layout, face, conversation.title);
        } else {
            String number = conversation == null ? screen.chat.activeNumber : conversation.peerNumber;
            renderFace(guiGraphics, screen.getChatProfileTexture(number), face, background);
        }
    }

    private static void clickTabs(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                  double mouseX, double mouseY, int button) {
        if (button == 0 && layout.tabBar().contains(mouseX, mouseY)) {
            app.switchTab(layout.tabAt(mouseX));
            return;
        }
        if (app.tab == PhoneChatLayout.TAB_CONTACTS) {
            clickContacts(screen, app, layout, mouseX, mouseY, button);
        } else if (app.tab == PhoneChatLayout.TAB_PROFILE) {
            clickProfile(screen, app, layout, mouseX, mouseY, button);
        } else {
            clickChats(screen, app, layout, mouseX, mouseY, button);
        }
    }

    private static void clickChats(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                   double mouseX, double mouseY, int button) {
        UiRect header = layout.header();
        if (button == 0 && layout.actionButton(header).contains(mouseX, mouseY)) {
            app.friendNumber = "";
            app.go(PhoneChatApp.PAGE_ADD);
            return;
        }
        if (button == 0 && layout.backButton(header).contains(mouseX, mouseY)) {
            app.searchQuery = "";
            app.searchedQuery = "";
            app.searchHits = List.of();
            app.searchScroll = 0;
            app.go(PhoneChatApp.PAGE_SEARCH);
            return;
        }

        List<PhoneClientChatState.Conversation> conversations = PhoneClientChatState.getConversations();
        UiRect viewport = layout.listViewport();
        int index = layout.listRowAt(viewport, conversations.size(), app.listScroll, mouseX, mouseY);
        if (button == 1) {
            if (index >= 0) {
                long id = conversations.get(index).id;
                app.menuConversationId = app.menuConversationId == id ? 0L : id;
            } else {
                app.clearMenus();
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (index < 0) {
            app.clearMenus();
            return;
        }

        PhoneClientChatState.Conversation conversation = conversations.get(index);
        if (app.menuConversationId == conversation.id) {
            UiRect row = layout.listRow(viewport, index, app.listScroll);
            if (layout.rowAction(row, listActionLabel(conversation)).contains(mouseX, mouseY)) {
                app.deleteFromList(conversation);
            }
            app.clearMenus();
            return;
        }
        if (app.menuConversationId != 0L) {
            app.clearMenus();
            return;
        }
        app.openConversation(conversation);
    }

    private static void renderContacts(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("phone.chat.tab.contacts"), false, true);

        UiRect viewport = layout.listViewport();
        List<PhoneClientChatState.FriendRequest> requests = PhoneClientChatState.getRequests();
        List<PhoneContact> friends = PhoneClientChatState.getFriends();
        if (requests.isEmpty() && friends.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("phone.chat.contacts.empty"),
                    Component.translatable("screen.bphone.phone.chat.hint"));
            return;
        }

        List<PhoneChatLayout.Row> rows = layout.contactRows(requests.size(), friends.size());
        app.contactsScroll = Mth.clamp(app.contactsScroll, 0,
                PhoneChatLayout.maxScroll(viewport, PhoneChatLayout.contentHeight(rows)));
        int scroll = app.contactsScroll;
        boolean pointerInside = hovered(screen, viewport);
        Component acceptLabel = Component.translatable("phone.chat.contacts.accept");
        Component rejectLabel = Component.translatable("phone.chat.contacts.reject");
        Component removeLabel = Component.translatable("phone.chat.contacts.remove");

        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (PhoneChatLayout.Row row : rows) {
            UiRect rect = layout.rowRect(viewport, row, scroll);
            if (rect.bottom() <= viewport.top || rect.top >= viewport.bottom()) {
                continue;
            }

            switch (row.kind()) {
                case PhoneChatLayout.ROW_REQUEST_SECTION -> renderSection(guiGraphics, layout, rect,
                        Component.translatable("phone.chat.contacts.requests").getString() + " (" + requests.size() + ")");
                case PhoneChatLayout.ROW_FRIEND_SECTION -> renderSection(guiGraphics, layout, rect,
                        Component.translatable("phone.chat.profile.friends_count").getString() + " (" + friends.size() + ")");
                case PhoneChatLayout.ROW_REQUEST -> {
                    PhoneClientChatState.FriendRequest request = requests.get(row.index());
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    UiRect face = layout.rowAvatar(rect, layout.avatar);
                    renderFace(guiGraphics, requestTexture(request), face, SURFACE);
                    UiRect[] buttons = layout.requestButtons(rect, acceptLabel, rejectLabel);
                    renderButton(screen, guiGraphics, layout, buttons[0], acceptLabel, Tone.PRIMARY);
                    renderButton(screen, guiGraphics, layout, buttons[1], rejectLabel, Tone.SECONDARY);
                    int textLeft = layout.rowTextLeft(rect, layout.avatar);
                    String name = request.fromName() == null || request.fromName().isBlank()
                            ? request.fromNumber() : request.fromName();
                    renderTwoLineText(guiGraphics, layout, rect, textLeft, buttons[0].left - layout.gap,
                            name, TEXT, request.fromNumber(), TEXT_TERTIARY);
                    if (row.index() < requests.size() - 1) {
                        guiGraphics.fill(textLeft, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                    }
                }
                case PhoneChatLayout.ROW_FRIEND -> {
                    PhoneContact friend = friends.get(row.index());
                    boolean menu = app.menuNumber.equals(friend.number());
                    int background = !menu && pointerInside && hovered(screen, rect) ? HOVER : SURFACE;
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), background);
                    UiRect face = layout.rowAvatar(rect, layout.avatar);
                    renderFace(guiGraphics, screen.getChatProfileTexture(friend.number()), face, background);
                    int textLeft = layout.rowTextLeft(rect, layout.avatar);
                    int textRight = rect.right() - layout.pad;
                    if (menu) {
                        UiRect removeBounds = layout.rowAction(rect, removeLabel);
                        renderButton(screen, guiGraphics, layout, removeBounds, removeLabel, Tone.DANGER);
                        textRight = removeBounds.left - layout.gap;
                    }
                    renderTwoLineText(guiGraphics, layout, rect, textLeft, textRight,
                            friend.displayName(), TEXT, friend.number(), TEXT_TERTIARY);
                    if (row.index() < friends.size() - 1) {
                        guiGraphics.fill(textLeft, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                    }
                }
                default -> renderListHint(guiGraphics, layout, viewport, rect.top);
            }
        }
        guiGraphics.disableScissor();
    }

    private static void clickContacts(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                      double mouseX, double mouseY, int button) {
        if (button == 0 && layout.actionButton(layout.header()).contains(mouseX, mouseY)) {
            app.friendNumber = "";
            app.go(PhoneChatApp.PAGE_ADD);
            return;
        }

        List<PhoneClientChatState.FriendRequest> requests = PhoneClientChatState.getRequests();
        List<PhoneContact> friends = PhoneClientChatState.getFriends();
        UiRect viewport = layout.listViewport();
        List<PhoneChatLayout.Row> rows = layout.contactRows(requests.size(), friends.size());
        PhoneChatLayout.Row row = layout.rowAt(viewport, rows, app.contactsScroll, mouseX, mouseY);

        if (button == 1) {
            if (row != null && row.kind() == PhoneChatLayout.ROW_FRIEND) {
                String number = friends.get(row.index()).number();
                app.menuNumber = app.menuNumber.equals(number) ? "" : number;
            } else {
                app.clearMenus();
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (row == null) {
            app.clearMenus();
            return;
        }

        UiRect rect = layout.rowRect(viewport, row, app.contactsScroll);
        if (row.kind() == PhoneChatLayout.ROW_REQUEST) {
            UiRect[] buttons = layout.requestButtons(rect,
                    Component.translatable("phone.chat.contacts.accept"),
                    Component.translatable("phone.chat.contacts.reject"));
            long requestId = requests.get(row.index()).id();
            if (buttons[0].contains(mouseX, mouseY)) {
                ChatNetworkingClient.respondFriendRequest(requestId, true);
            } else if (buttons[1].contains(mouseX, mouseY)) {
                ChatNetworkingClient.respondFriendRequest(requestId, false);
            }
            app.clearMenus();
            return;
        }
        if (row.kind() != PhoneChatLayout.ROW_FRIEND) {
            app.clearMenus();
            return;
        }

        PhoneContact friend = friends.get(row.index());
        if (app.menuNumber.equals(friend.number())) {
            if (layout.rowAction(rect, Component.translatable("phone.chat.contacts.remove")).contains(mouseX, mouseY)) {
                ChatNetworkingClient.removeFriend(friend.number());
            }
            app.clearMenus();
            return;
        }
        if (!app.menuNumber.isEmpty()) {
            app.clearMenus();
            return;
        }
        app.openDirect(friend.number(), friend.displayName());
    }

    private static void renderProfile(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("phone.chat.tab.profile"), false, false);

        Minecraft minecraft = Minecraft.getInstance();
        UiRect card = layout.profileCard();
        guiGraphics.fill(card.left, card.top, card.right(), card.bottom(), SURFACE);
        UiRect avatar = layout.profileAvatar();
        renderFace(guiGraphics, ownSkin(screen), avatar, SURFACE);

        String nickname = PhoneClientChatState.getOwnNickname();
        if (nickname.isBlank() && minecraft.player != null) {
            nickname = minecraft.player.getGameProfile().getName();
        }
        int textLeft = avatar.right() + layout.u(8);
        int textWidth = card.right() - layout.pad - textLeft;
        int blockHeight = layout.titleHeight + layout.textGap + layout.captionHeight;
        int nameY = card.top + (card.height - blockHeight) / 2;
        drawText(guiGraphics, layout, fit(layout, nickname, BOLD, textWidth, layout.titleScale),
                textLeft, nameY, TEXT, layout.titleScale);
        String numberLine = Component.translatable("phone.chat.profile.my_number").getString() + " " + screen.getOwnPhoneNumber();
        drawText(guiGraphics, layout, fit(layout, numberLine, PLAIN, textWidth, layout.captionScale),
                textLeft, nameY + layout.titleHeight + layout.textGap, TEXT_SECONDARY, layout.captionScale);

        Component saveLabel = Component.translatable("screen.bphone.phone.call.contacts.save_button");
        UiRect editRow = layout.profileRow(PhoneChatLayout.PROFILE_ROW_NICKNAME);
        UiRect friendsRow = layout.profileRow(PhoneChatLayout.PROFILE_ROW_FRIENDS);
        UiRect qrRow = layout.profileRow(PhoneChatLayout.PROFILE_ROW_QR);
        UiRect blockedRow = layout.profileRow(PhoneChatLayout.PROFILE_ROW_BLOCKED);
        guiGraphics.fill(editRow.left, editRow.top, editRow.right(), blockedRow.bottom(), SURFACE);

        if (app.nicknameEditing) {
            renderField(guiGraphics, layout, layout.profileEditField(saveLabel), app.nicknameDraft,
                    Component.translatable("phone.chat.profile.nickname"), ACCENT, FIELD);
            renderButton(screen, guiGraphics, layout, layout.profileSaveButton(saveLabel), saveLabel, Tone.PRIMARY);
        } else {
            renderNavigationRow(screen, guiGraphics, layout, editRow,
                    Component.translatable("phone.chat.profile.edit_nickname"), null);
        }
        guiGraphics.fill(editRow.left + layout.pad, editRow.bottom() - 1, editRow.right(), editRow.bottom(), HAIRLINE);
        renderNavigationRow(screen, guiGraphics, layout, friendsRow,
                Component.translatable("phone.chat.profile.friends_count"),
                String.valueOf(PhoneClientChatState.getFriends().size()));
        guiGraphics.fill(friendsRow.left + layout.pad, friendsRow.bottom() - 1, friendsRow.right(), friendsRow.bottom(), HAIRLINE);
        renderNavigationRow(screen, guiGraphics, layout, qrRow,
                Component.translatable("phone.chat.profile.my_qr"), null);
        guiGraphics.fill(qrRow.left + layout.pad, qrRow.bottom() - 1, qrRow.right(), qrRow.bottom(), HAIRLINE);
        renderNavigationRow(screen, guiGraphics, layout, blockedRow,
                Component.translatable("phone.chat.profile.blocked"),
                String.valueOf(PhoneClientChatState.getBlocked().size()));

        if (app.nicknameEditing) {
            Component hint = Component.translatable("phone.chat.profile.edit_hint");
            drawText(guiGraphics, layout, fit(layout, hint.getString(), PLAIN, layout.surface.width - 2 * layout.pad, layout.captionScale),
                    layout.surface.left + layout.pad, layout.profileHintTop(), TEXT_TERTIARY, layout.captionScale);
        }

        renderButton(screen, guiGraphics, layout, layout.profileAddButton(app.nicknameEditing),
                Component.translatable("phone.chat.add_menu.title"), Tone.PRIMARY);
    }

    private static void clickProfile(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                     double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (app.nicknameEditing) {
            Component saveLabel = Component.translatable("screen.bphone.phone.call.contacts.save_button");
            if (layout.profileEditField(saveLabel).contains(mouseX, mouseY)) {
                return;
            }
            app.saveNickname();
            return;
        }
        if (layout.profileRow(PhoneChatLayout.PROFILE_ROW_NICKNAME).contains(mouseX, mouseY)) {
            app.startNicknameEdit();
        } else if (layout.profileRow(PhoneChatLayout.PROFILE_ROW_FRIENDS).contains(mouseX, mouseY)) {
            app.switchTab(PhoneChatLayout.TAB_CONTACTS);
        } else if (layout.profileRow(PhoneChatLayout.PROFILE_ROW_QR).contains(mouseX, mouseY)) {
            screen.showChatQrToWorld();
        } else if (layout.profileRow(PhoneChatLayout.PROFILE_ROW_BLOCKED).contains(mouseX, mouseY)) {
            app.blockedScroll = 0;
            app.go(PhoneChatApp.PAGE_BLOCKED);
        } else if (layout.profileAddButton(false).contains(mouseX, mouseY)) {
            app.friendNumber = "";
            app.go(PhoneChatApp.PAGE_ADD);
        }
    }

    private static void renderAddMenu(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("phone.chat.add_menu.title"), true, false);

        int left = layout.surface.left + layout.pad;
        int innerWidth = layout.surface.width - 2 * layout.pad;
        drawText(guiGraphics, layout, fit(layout, Component.translatable("phone.chat.add_menu.by_number").getString(), PLAIN,
                innerWidth, layout.captionScale), left, layout.addLabelTop(), TEXT_SECONDARY, layout.captionScale);

        renderField(guiGraphics, layout, layout.addField(), app.friendNumber,
                Component.translatable("screen.bphone.phone.bank.number_placeholder"), ACCENT, SURFACE);

        Component error = addFriendError(screen, app);
        if (error != null) {
            drawText(guiGraphics, layout, fit(layout, error.getString(), PLAIN, innerWidth, layout.captionScale),
                    left, layout.addErrorTop(), DANGER, layout.captionScale);
        }

        boolean canAdd = app.canAddFriend(app.friendNumber);
        renderButton(screen, guiGraphics, layout, layout.addButton(),
                Component.translatable("screen.bphone.phone.chat.add_action"), canAdd ? Tone.PRIMARY : Tone.DISABLED);

        UiRect online = layout.addOption(PhoneChatLayout.ADD_OPTION_ONLINE);
        UiRect scan = layout.addOption(PhoneChatLayout.ADD_OPTION_SCAN);
        UiRect group = layout.addOption(PhoneChatLayout.ADD_OPTION_GROUP);
        guiGraphics.fill(online.left, online.top, group.right(), group.bottom(), SURFACE);
        renderOptionRow(screen, guiGraphics, layout, online, ownSkin(screen), true,
                Component.translatable("phone.chat.add_menu.by_online"));
        guiGraphics.fill(online.left + layout.pad, online.bottom() - 1, online.right(), online.bottom(), HAIRLINE);
        renderOptionRow(screen, guiGraphics, layout, scan, CAMERA_ICON, false,
                Component.translatable("phone.chat.add_menu.by_scan"));
        guiGraphics.fill(scan.left + layout.pad, scan.bottom() - 1, scan.right(), scan.bottom(), HAIRLINE);
        renderGroupOption(screen, guiGraphics, layout, group, Component.translatable("phone.chat.group.create"));

        String footer = Component.translatable("phone.chat.profile.my_number").getString() + " " + screen.getOwnPhoneNumber();
        drawCentered(guiGraphics, layout, fit(layout, footer, PLAIN, innerWidth, layout.captionScale),
                layout.surface.left + layout.surface.width / 2, layout.addFooterTop(), TEXT_TERTIARY, layout.captionScale);
    }

    private static void renderGroupOption(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row,
                                          Component label) {
        int background = hovered(screen, row) ? HOVER : SURFACE;
        guiGraphics.fill(row.left, row.top, row.right(), row.bottom() - 1, background);
        UiRect icon = layout.rowAvatar(row, layout.avatarSmall);
        renderGroupAvatar(guiGraphics, layout, icon, Component.translatable("phone.chat.group.icon").getString());
        int textLeft = layout.rowTextLeft(row, layout.avatarSmall);
        int textRight = row.right() - layout.pad - layout.u(8);
        drawText(guiGraphics, layout, fit(layout, label.getString(), PLAIN, textRight - textLeft, layout.bodyScale),
                textLeft, row.top + (row.height - layout.bodyHeight) / 2, TEXT, layout.bodyScale);
        renderChevron(guiGraphics, layout, row);
    }

    private static Component addFriendError(PhoneScreen screen, PhoneChatApp app) {
        String number = PhoneData.normalizePhoneNumber(app.friendNumber);
        if (app.friendNumber.length() < PhoneData.PHONE_NUMBER_LENGTH) {
            return null;
        }
        if (!PhoneData.isValidPhoneNumber(number) || !PhoneData.isMobilePhoneNumber(number)) {
            return Component.translatable("screen.bphone.phone.call.error.invalid");
        }
        if (number.equals(screen.getOwnPhoneNumber())) {
            return Component.translatable("phone.chat.add_menu.error.self");
        }
        if (PhoneClientChatState.hasFriend(number)) {
            return Component.translatable("phone.friend.error.already");
        }
        return null;
    }

    private static void clickAddMenu(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                     double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            goBack(screen, app);
        } else if (layout.addButton().contains(mouseX, mouseY)) {
            app.submitFriendNumber();
        } else if (layout.addOption(PhoneChatLayout.ADD_OPTION_ONLINE).contains(mouseX, mouseY)) {
            app.selectorScroll = 0;
            app.go(PhoneChatApp.PAGE_SELECTOR);
        } else if (layout.addOption(PhoneChatLayout.ADD_OPTION_SCAN).contains(mouseX, mouseY)) {
            screen.openChatScanCamera();
        } else if (layout.addOption(PhoneChatLayout.ADD_OPTION_GROUP).contains(mouseX, mouseY)) {
            app.openGroupCreate();
        }
    }

    private static void renderSelector(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.chat.select_player"), true, false);

        UiRect viewport = layout.pageViewport();
        List<PlayerInfo> players = screen.getChatAddSelectablePlayers();
        if (players.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("screen.bphone.phone.chat.no_players"), null);
            return;
        }

        int contentHeight = layout.listContentHeight(players.size(), false);
        app.selectorScroll = Mth.clamp(app.selectorScroll, 0, PhoneChatLayout.maxScroll(viewport, contentHeight));
        boolean pointerInside = hovered(screen, viewport);

        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < players.size(); index++) {
            UiRect row = layout.listRow(viewport, index, app.selectorScroll);
            if (row.bottom() <= viewport.top) {
                continue;
            }
            if (row.top >= viewport.bottom()) {
                break;
            }

            PlayerInfo player = players.get(index);
            int background = pointerInside && hovered(screen, row) ? HOVER : SURFACE;
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), background);
            UiRect face = layout.rowAvatar(row, layout.avatar);
            renderFace(guiGraphics, screen.getPlayerSkin(player), face, background);
            int textLeft = layout.rowTextLeft(row, layout.avatar);
            renderChevron(guiGraphics, layout, row);
            renderTwoLineText(guiGraphics, layout, row, textLeft, row.right() - layout.pad - layout.u(8),
                    player.getProfile().getName(), TEXT,
                    screen.getPlayerPhoneNumber(player.getProfile().getId()), TEXT_TERTIARY);
            if (index < players.size() - 1) {
                guiGraphics.fill(textLeft, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
        }
        guiGraphics.disableScissor();
    }

    private static void clickSelector(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                      double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            goBack(screen, app);
            return;
        }
        List<PlayerInfo> players = screen.getChatAddSelectablePlayers();
        int index = layout.listRowAt(layout.pageViewport(), players.size(), app.selectorScroll, mouseX, mouseY);
        if (index >= 0) {
            app.selectOnlinePlayer(players.get(index));
        }
    }

    private static void renderThread(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        PhoneClientChatState.Conversation conversation = app.conversation();
        UiRect header = layout.threadHeader();
        guiGraphics.fill(header.left, header.top, header.right(), header.bottom(), SURFACE);
        guiGraphics.fill(header.left, header.bottom() - 1, header.right(), header.bottom(), HAIRLINE);
        renderBack(screen, guiGraphics, layout, layout.backButton(header));
        renderMoreButton(screen, guiGraphics, layout, layout.actionButton(header));

        String title = app.title(conversation);
        String subtitle;
        if (conversation != null && conversation.isGroup()) {
            subtitle = Component.translatable("phone.chat.group.member_count", conversation.members.size()).getString();
        } else {
            String number = app.peerNumber();
            subtitle = PhoneClientChatState.isBlocked(number)
                    ? Component.translatable("phone.chat.blocked_tag", number).getString()
                    : number;
        }
        int titleWidth = header.width - 2 * header.height;
        int centerX = header.left + header.width / 2;
        int blockHeight = layout.titleHeight + layout.textGap + layout.captionHeight;
        int nameY = header.top + (header.height - 1 - blockHeight) / 2;
        drawCentered(guiGraphics, layout, fit(layout, title, BOLD, titleWidth, layout.titleScale),
                centerX, nameY, TEXT, layout.titleScale);
        drawCentered(guiGraphics, layout, fit(layout, subtitle, PLAIN, titleWidth, layout.captionScale),
                centerX, nameY + layout.titleHeight + layout.textGap, TEXT_TERTIARY, layout.captionScale);

        UiRect viewport = layout.threadMessages();
        if (conversation == null || (conversation.entries.isEmpty() && !conversation.hasMore)) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("screen.bphone.phone.chat.thread_empty"),
                    Component.translatable("screen.bphone.phone.chat.thread_hint"));
        } else {
            renderThreadItems(screen, app, guiGraphics, layout, viewport, conversation);
        }

        UiRect composer = layout.composer();
        guiGraphics.fill(composer.left, composer.top, composer.right(), composer.bottom(), SURFACE);
        guiGraphics.fill(composer.left, composer.top, composer.right(), composer.top + 1, HAIRLINE);
        UiRect imageButton = layout.imageButton();
        int imageBackground = hovered(screen, imageButton) ? SECONDARY_HOVER : SECONDARY;
        fillRounded(guiGraphics, imageButton.left, imageButton.top, imageButton.width, imageButton.height, layout.radius,
                imageBackground);
        renderImageGlyph(guiGraphics, layout, imageButton, ACCENT, imageBackground);
        Component sendLabel = Component.translatable("screen.bphone.phone.chat.send");
        renderField(guiGraphics, layout, layout.draftField(sendLabel), app.draft,
                Component.translatable("screen.bphone.phone.chat.message_placeholder"), FIELD, FIELD);
        renderButton(screen, guiGraphics, layout, layout.sendButton(sendLabel), sendLabel,
                app.canSend() ? Tone.PRIMARY : Tone.DISABLED);
    }

    private static List<PhoneChatThread.Item> threadItems(PhoneChatApp app, PhoneChatLayout layout, UiRect viewport,
                                                          PhoneClientChatState.Conversation conversation) {
        return PhoneChatThread.build(layout, conversation, app.self(), app.historyPendingTicks > 0, viewport);
    }

    private static void renderThreadItems(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics,
                                          PhoneChatLayout layout, UiRect viewport,
                                          PhoneClientChatState.Conversation conversation) {
        List<PhoneChatThread.Item> items = threadItems(app, layout, viewport, conversation);
        int contentHeight = PhoneChatThread.contentHeight(layout, items);
        int maxScroll = PhoneChatLayout.maxScroll(viewport, contentHeight);
        long tail = conversation.lastEntryId();
        if (app.anchorConversationId == conversation.id && tail != app.anchorTailId && app.threadScroll > 0) {
            app.threadScroll += contentHeight - app.anchorContentHeight;
        }
        app.anchorConversationId = conversation.id;
        app.anchorTailId = tail;
        app.anchorContentHeight = contentHeight;
        if (app.scrollToId > 0L) {
            int target = PhoneChatThread.scrollToEntry(layout, items, app.scrollToId, viewport);
            if (target >= 0) {
                app.threadScroll = target;
                app.highlightId = app.scrollToId;
                app.highlightTicks = 60;
                app.scrollToId = 0L;
            }
        }
        app.threadScroll = Mth.clamp(app.threadScroll, 0, maxScroll);
        if (app.threadScroll >= maxScroll && conversation.hasMore) {
            app.requestOlder(conversation);
        }

        List<PhoneChatThread.Placed> placed = PhoneChatThread.place(layout, items, viewport, app.threadScroll);
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (PhoneChatThread.Placed entry : placed) {
            PhoneChatThread.Item item = entry.item();
            if (entry.top() + item.height() <= viewport.top || entry.top() >= viewport.bottom()) {
                continue;
            }
            UiRect body = entry.body();
            switch (item.type()) {
                case PhoneChatThread.ITEM_MORE, PhoneChatThread.ITEM_TIME -> renderCaptionLines(guiGraphics, layout, item, body,
                        TEXT_TERTIARY);
                case PhoneChatThread.ITEM_SYSTEM -> {
                    fillRounded(guiGraphics, body.left, body.top, body.width, body.height, layout.radius, SYSTEM_PILL);
                    renderCaptionLines(guiGraphics, layout, item, body, TEXT_SECONDARY);
                }
                default -> renderMessage(app, guiGraphics, layout, viewport, entry);
            }
        }
        guiGraphics.disableScissor();

        if (conversation.windowed) {
            Component label = Component.translatable("phone.chat.thread.latest");
            UiRect pill = layout.latestPill(viewport, label);
            fillRounded(guiGraphics, pill.left, pill.top, pill.width, pill.height, pill.height / 2,
                    hovered(screen, pill) ? ACCENT_HOVER : ACCENT);
            drawCentered(guiGraphics, layout, label, pill.left + pill.width / 2, pill.top + (pill.height - layout.captionHeight) / 2,
                    WHITE, layout.captionScale);
        }
    }

    private static void renderCaptionLines(GuiGraphics guiGraphics, PhoneChatLayout layout, PhoneChatThread.Item item,
                                           UiRect body, int color) {
        int y = body.top + Math.max(1, layout.u(2));
        int centerX = body.left + body.width / 2;
        for (FormattedCharSequence line : item.lines()) {
            int width = layout.width(line, layout.captionScale);
            drawSequence(guiGraphics, layout, line, centerX - width / 2, y, color, layout.captionScale);
            y += layout.captionLine;
        }
    }

    private static void renderMessage(PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect viewport,
                                      PhoneChatThread.Placed placed) {
        PhoneChatThread.Item item = placed.item();
        UiRect body = placed.body();
        if (item.sender() != null) {
            Component sender = fit(layout, item.sender(), PLAIN, Math.round(viewport.width * 0.7F), layout.captionScale);
            drawText(guiGraphics, layout, sender, body.left, placed.top(), TEXT_TERTIARY, layout.captionScale);
        }

        if (app.highlightTicks > 0 && item.entry() != null && item.entry().id() == app.highlightId) {
            int outline = Math.max(1, layout.u(1.5F));
            fillRounded(guiGraphics, body.left - outline, body.top - outline, body.width + 2 * outline,
                    body.height + 2 * outline, layout.bubbleRadius + outline, HIGHLIGHT);
        }

        if (item.type() == PhoneChatThread.ITEM_IMAGE) {
            PhotoTexture texture = PhoneChatImages.get(item.entry().imageId());
            if (texture != null) {
                guiGraphics.blit(texture.textureId, body.left, body.top, body.width, body.height, 0.0F, 0.0F,
                        texture.width, texture.height, texture.width, texture.height);
            } else {
                fillRounded(guiGraphics, body.left, body.top, body.width, body.height, layout.bubbleRadius,
                        item.incoming() ? BUBBLE_IN : BUBBLE_OUT);
                renderImageGlyph(guiGraphics, layout, body, TEXT_TERTIARY, item.incoming() ? BUBBLE_IN : BUBBLE_OUT);
                if (PhoneChatImages.isFailed(item.entry().imageId())) {
                    Component failed = fit(layout, Component.translatable("phone.chat.image.failed").getString(), PLAIN,
                            body.width - 2, layout.captionScale);
                    drawCentered(guiGraphics, layout, failed, body.left + body.width / 2,
                            body.bottom() - layout.captionHeight - layout.u(2), TEXT_TERTIARY, layout.captionScale);
                }
            }
        } else {
            fillRounded(guiGraphics, body.left, body.top, body.width, body.height, layout.bubbleRadius,
                    item.incoming() ? BUBBLE_IN : BUBBLE_OUT);
            int lineY = body.top + layout.bubblePadY;
            for (FormattedCharSequence line : item.lines()) {
                drawSequence(guiGraphics, layout, line, body.left + layout.bubblePadX, lineY, TEXT, layout.bodyScale);
                lineY += layout.bodyLine;
            }
        }

        if (item.status() != null) {
            int color = item.statusRead() ? TEXT_TERTIARY : ACCENT;
            int width = layout.width(item.status(), layout.captionScale);
            if (item.statusBeside()) {
                drawText(guiGraphics, layout, item.status(), body.left - layout.gap - width,
                        body.bottom() - layout.captionHeight, color, layout.captionScale);
            } else {
                drawText(guiGraphics, layout, item.status(), body.right() - width, body.bottom() + layout.u(2),
                        TEXT_TERTIARY, layout.captionScale);
            }
        }
    }

    private static void clickThread(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                    double mouseX, double mouseY, int button) {
        UiRect header = layout.threadHeader();
        if (button == 0 && layout.backButton(header).contains(mouseX, mouseY)) {
            goBack(screen, app);
            return;
        }
        if (button == 0 && layout.actionButton(header).contains(mouseX, mouseY)) {
            app.infoScroll = 0;
            app.go(PhoneChatApp.PAGE_INFO);
            return;
        }
        if (button == 0 && layout.imageButton().contains(mouseX, mouseY)) {
            app.pickScroll = 0;
            app.go(PhoneChatApp.PAGE_IMAGE_PICK);
            return;
        }
        Component sendLabel = Component.translatable("screen.bphone.phone.chat.send");
        if (button == 0 && layout.sendButton(sendLabel).contains(mouseX, mouseY)) {
            app.send();
            return;
        }

        PhoneClientChatState.Conversation conversation = app.conversation();
        UiRect viewport = layout.threadMessages();
        if (conversation == null || !viewport.contains(mouseX, mouseY)) {
            return;
        }
        if (button == 0 && conversation.windowed
                && layout.latestPill(viewport, Component.translatable("phone.chat.thread.latest")).contains(mouseX, mouseY)) {
            app.requestLatest();
            return;
        }

        List<PhoneChatThread.Item> items = threadItems(app, layout, viewport, conversation);
        for (PhoneChatThread.Placed placed : PhoneChatThread.place(layout, items, viewport, app.threadScroll)) {
            PhoneChatThread.Item item = placed.item();
            if (!placed.body().contains(mouseX, mouseY) || item.entry() == null) {
                continue;
            }
            if (button == 0 && item.type() == PhoneChatThread.ITEM_IMAGE) {
                app.viewImage(item.entry().imageId());
                return;
            }
            if (button == 1 && item.type() == PhoneChatThread.ITEM_TEXT) {
                Minecraft.getInstance().keyboardHandler.setClipboard(item.entry().body());
                app.status(Component.translatable("phone.chat.copied"));
                return;
            }
        }
    }

    private static void renderSearch(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable("phone.chat.search.title"), true, false);
        UiRect bar = layout.topBar();
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        guiGraphics.fill(bar.left, bar.bottom() - 1, bar.right(), bar.bottom(), HAIRLINE);
        Component action = Component.translatable("phone.chat.search.action");
        renderField(guiGraphics, layout, layout.topBarField(action), app.searchQuery,
                Component.translatable("phone.chat.search.placeholder"), ACCENT, FIELD);
        renderButton(screen, guiGraphics, layout, layout.topBarAction(action), action,
                app.searchQuery.isBlank() ? Tone.DISABLED : Tone.PRIMARY);

        UiRect viewport = layout.viewportBetween(bar.bottom(), layout.surface.bottom());
        List<ChatPayloads.SearchHitData> hits = app.searchHits;
        if (app.searchPendingTicks > 0) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("phone.chat.search.searching"), null);
            return;
        }
        if (hits.isEmpty()) {
            Component title = app.searchedQuery.isEmpty()
                    ? Component.translatable("phone.chat.search.hint")
                    : Component.translatable("phone.chat.search.none");
            renderEmptyState(guiGraphics, layout, viewport, title, null);
            return;
        }

        app.searchScroll = Mth.clamp(app.searchScroll, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(hits.size(), false)));
        boolean pointerInside = hovered(screen, viewport);
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < hits.size(); index++) {
            UiRect row = layout.listRow(viewport, index, app.searchScroll);
            if (row.bottom() <= viewport.top) {
                continue;
            }
            if (row.top >= viewport.bottom()) {
                break;
            }
            ChatPayloads.SearchHitData hit = hits.get(index);
            int background = pointerInside && hovered(screen, row) ? HOVER : SURFACE;
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), background);
            PhoneClientChatState.Conversation conversation = PhoneClientChatState.getConversation(hit.conversationId());
            UiRect face = layout.rowAvatar(row, layout.avatar);
            if (conversation != null) {
                renderConversationAvatar(screen, guiGraphics, layout, face, conversation, background);
            } else {
                renderGroupAvatar(guiGraphics, layout, face, hit.title());
            }
            Component time = Component.literal(PhoneChatThread.timeLabel(hit.entry().createdAt(), true));
            int timeWidth = layout.width(time, layout.captionScale);
            int blockHeight = layout.bodyHeight + layout.textGap + layout.captionHeight;
            int top = row.top + (row.height - blockHeight) / 2;
            drawText(guiGraphics, layout, time, row.right() - layout.pad - timeWidth,
                    top + Math.max(0, layout.bodyHeight - layout.captionHeight), TEXT_TERTIARY, layout.captionScale);
            int textLeft = layout.rowTextLeft(row, layout.avatar);
            String title = conversation != null ? app.title(conversation) : hit.title();
            String sender = hit.entry().senderName().isBlank() ? hit.entry().senderNumber() : hit.entry().senderName();
            String snippet = Component.translatable("phone.chat.preview.group", sender, hit.entry().body()).getString();
            renderTwoLineText(guiGraphics, layout, row, textLeft, row.right() - layout.pad - timeWidth - layout.gap,
                    title, TEXT, snippet, TEXT_TERTIARY);
            if (index < hits.size() - 1) {
                guiGraphics.fill(textLeft, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
        }
        guiGraphics.disableScissor();
    }

    private static void clickSearch(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                    double mouseX, double mouseY, int button) {
        if (backHit(layout, layout.header(), mouseX, mouseY, button)) {
            goBack(screen, app);
            return;
        }
        if (button != 0) {
            return;
        }
        if (layout.topBarAction(Component.translatable("phone.chat.search.action")).contains(mouseX, mouseY)) {
            app.runSearch();
            return;
        }
        if (app.searchPendingTicks > 0) {
            return;
        }
        UiRect viewport = layout.viewportBetween(layout.topBar().bottom(), layout.surface.bottom());
        int index = layout.listRowAt(viewport, app.searchHits.size(), app.searchScroll, mouseX, mouseY);
        if (index >= 0) {
            app.openSearchHit(app.searchHits.get(index));
        }
    }

    private static UiRect pickerViewport(PhoneChatApp app, PhoneChatLayout layout) {
        int top = app.page == PhoneChatApp.PAGE_GROUP_CREATE ? layout.topBar().bottom() : layout.header().bottom();
        return layout.viewportBetween(top, layout.bottomBar().top);
    }

    private static void renderPicker(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        boolean create = app.page == PhoneChatApp.PAGE_GROUP_CREATE;
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable(create
                ? "phone.chat.group.create" : "phone.chat.group.invite"), true, false);
        if (create) {
            UiRect bar = layout.topBar();
            guiGraphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
            guiGraphics.fill(bar.left, bar.bottom() - 1, bar.right(), bar.bottom(), HAIRLINE);
            renderField(guiGraphics, layout, layout.topBarField(null), app.groupName,
                    Component.translatable("phone.chat.group.name_placeholder"), ACCENT, FIELD);
        }

        UiRect viewport = pickerViewport(app, layout);
        List<PhoneClientChatState.Friend> friends = app.selectableFriends();
        if (friends.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("phone.chat.group.no_friends"), null);
        } else {
            app.groupScroll = Mth.clamp(app.groupScroll, 0,
                    PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(friends.size(), false)));
            boolean full = app.selected.size() >= app.selectionLimit();
            boolean pointerInside = hovered(screen, viewport);
            guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
            for (int index = 0; index < friends.size(); index++) {
                UiRect row = layout.listRow(viewport, index, app.groupScroll);
                if (row.bottom() <= viewport.top) {
                    continue;
                }
                if (row.top >= viewport.bottom()) {
                    break;
                }
                PhoneClientChatState.Friend friend = friends.get(index);
                boolean checked = app.selected.contains(friend.number());
                int background = pointerInside && hovered(screen, row) ? HOVER : SURFACE;
                guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), background);
                UiRect face = layout.rowAvatar(row, layout.avatar);
                renderFace(guiGraphics, screen.getChatProfileTexture(friend.number()), face, background);
                UiRect box = layout.checkBox(row);
                renderCheck(guiGraphics, layout, box, checked, checked || !full);
                int textLeft = layout.rowTextLeft(row, layout.avatar);
                renderTwoLineText(guiGraphics, layout, row, textLeft, box.left - layout.gap,
                        friend.name(), TEXT, friend.number(), TEXT_TERTIARY);
                if (index < friends.size() - 1) {
                    guiGraphics.fill(textLeft, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
                }
            }
            guiGraphics.disableScissor();
        }

        UiRect bar = layout.bottomBar();
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.top + 1, HAIRLINE);
        Component label;
        boolean enabled;
        if (create) {
            label = app.groupPendingTicks > 0
                    ? Component.translatable("phone.chat.group.creating")
                    : Component.translatable("phone.chat.group.create_action", app.selected.size());
            enabled = app.canCreateGroup();
        } else {
            label = Component.translatable("phone.chat.group.invite_action", app.selected.size());
            enabled = !app.selected.isEmpty();
        }
        renderButton(screen, guiGraphics, layout, layout.bottomBarButton(), label, enabled ? Tone.PRIMARY : Tone.DISABLED);
    }

    private static void clickPicker(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                    double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            goBack(screen, app);
            return;
        }
        if (layout.bottomBarButton().contains(mouseX, mouseY)) {
            if (app.page == PhoneChatApp.PAGE_GROUP_CREATE) {
                app.createGroup();
            } else {
                app.invite();
            }
            return;
        }
        List<PhoneClientChatState.Friend> friends = app.selectableFriends();
        int index = layout.listRowAt(pickerViewport(app, layout), friends.size(), app.groupScroll, mouseX, mouseY);
        if (index >= 0) {
            app.toggleSelected(friends.get(index).number());
        }
    }

    private static List<ChatPayloads.MemberData> infoMembers(PhoneClientChatState.Conversation conversation) {
        return conversation != null && conversation.isGroup() ? conversation.members : List.of();
    }

    private static void renderInfo(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        PhoneClientChatState.Conversation conversation = app.conversation();
        boolean group = conversation != null && conversation.isGroup();
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable(group
                ? "phone.chat.info.group_title" : "phone.chat.info.title"), true, false);

        int[] actions = app.infoActions(conversation);
        List<ChatPayloads.MemberData> members = infoMembers(conversation);
        List<PhoneChatLayout.Row> rows = layout.infoRows(actions.length, members.size());
        UiRect viewport = layout.pageViewport();
        app.infoScroll = Mth.clamp(app.infoScroll, 0, PhoneChatLayout.maxScroll(viewport, PhoneChatLayout.contentHeight(rows)));
        UUID self = app.self();
        boolean owner = app.isOwner(conversation);
        Component kickLabel = Component.translatable("phone.chat.group.kick");

        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (PhoneChatLayout.Row row : rows) {
            UiRect rect = layout.rowRect(viewport, row, app.infoScroll);
            if (rect.bottom() <= viewport.top || rect.top >= viewport.bottom()) {
                continue;
            }
            switch (row.kind()) {
                case PhoneChatLayout.ROW_CARD -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    UiRect avatar = layout.cardAvatar(rect);
                    renderConversationAvatar(screen, guiGraphics, layout, avatar, conversation, SURFACE);
                    int textLeft = avatar.right() + layout.u(8);
                    int textWidth = rect.right() - layout.pad - textLeft;
                    int blockHeight = layout.titleHeight + layout.textGap + layout.captionHeight;
                    int nameY = rect.top + (rect.height - blockHeight) / 2;
                    drawText(guiGraphics, layout, fit(layout, app.title(conversation), BOLD, textWidth, layout.titleScale),
                            textLeft, nameY, TEXT, layout.titleScale);
                    String subtitle = group
                            ? Component.translatable("phone.chat.group.member_count", members.size()).getString()
                            : app.peerNumber();
                    drawText(guiGraphics, layout, fit(layout, subtitle, PLAIN, textWidth, layout.captionScale),
                            textLeft, nameY + layout.titleHeight + layout.textGap, TEXT_SECONDARY, layout.captionScale);
                }
                case PhoneChatLayout.ROW_ACTION -> renderInfoAction(screen, app, guiGraphics, layout, rect, actions[row.index()],
                        conversation, owner, row.index() < actions.length - 1);
                case PhoneChatLayout.ROW_MEMBER_SECTION -> renderSection(guiGraphics, layout, rect,
                        Component.translatable("phone.chat.group.members").getString() + " (" + members.size() + ")");
                case PhoneChatLayout.ROW_MEMBER -> {
                    ChatPayloads.MemberData member = members.get(row.index());
                    boolean menu = app.menuNumber.equals(member.number());
                    int background = !menu && hovered(screen, viewport) && hovered(screen, rect) ? HOVER : SURFACE;
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), background);
                    UiRect face = layout.rowAvatar(rect, layout.avatar);
                    renderFace(guiGraphics, memberTexture(member), face, background);
                    int textLeft = layout.rowTextLeft(rect, layout.avatar);
                    int textRight = rect.right() - layout.pad;
                    if (menu) {
                        UiRect kick = layout.rowAction(rect, kickLabel);
                        renderButton(screen, guiGraphics, layout, kick, kickLabel, Tone.DANGER);
                        textRight = kick.left - layout.gap;
                    } else {
                        Component tag = null;
                        if (member.uuid() != null && member.uuid().equals(conversation.ownerUuid)) {
                            tag = Component.translatable("phone.chat.group.owner");
                        } else if (member.uuid() != null && member.uuid().equals(self)) {
                            tag = Component.translatable("phone.chat.group.me");
                        }
                        if (tag != null) {
                            int tagWidth = layout.width(tag, layout.captionScale);
                            drawText(guiGraphics, layout, tag, rect.right() - layout.pad - tagWidth,
                                    rect.top + (rect.height - layout.captionHeight) / 2, ACCENT, layout.captionScale);
                            textRight -= tagWidth + layout.gap;
                        }
                    }
                    String name = member.name().isBlank() ? member.number() : member.name();
                    renderTwoLineText(guiGraphics, layout, rect, textLeft, textRight, name, TEXT, member.number(), TEXT_TERTIARY);
                    if (row.index() < members.size() - 1) {
                        guiGraphics.fill(textLeft, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                    }
                }
                default -> {
                }
            }
        }
        guiGraphics.disableScissor();
    }

    private static Component actionLabel(PhoneChatApp app, int action) {
        if (app.confirmAction == action) {
            return Component.translatable("phone.chat.info.confirm");
        }
        return Component.translatable(switch (action) {
            case PhoneChatApp.ACTION_RENAME -> "phone.chat.group.name";
            case PhoneChatApp.ACTION_INVITE -> "phone.chat.group.invite";
            case PhoneChatApp.ACTION_CLEAR -> "phone.chat.info.clear";
            case PhoneChatApp.ACTION_LEAVE -> "phone.chat.group.leave";
            case PhoneChatApp.ACTION_ADD_FRIEND -> "phone.chat.info.add_friend";
            case PhoneChatApp.ACTION_REMOVE_FRIEND -> "phone.chat.info.remove_friend";
            case PhoneChatApp.ACTION_BLOCK -> "phone.chat.info.block";
            default -> "phone.chat.info.unblock";
        });
    }

    private static void renderInfoAction(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                         UiRect rect, int action, PhoneClientChatState.Conversation conversation,
                                         boolean owner, boolean divider) {
        guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
        if (action == PhoneChatApp.ACTION_RENAME && app.renaming) {
            Component save = Component.translatable("screen.bphone.phone.call.contacts.save_button");
            renderField(guiGraphics, layout, layout.inlineField(rect, save), app.renameDraft,
                    Component.translatable("phone.chat.group.name_placeholder"), ACCENT, FIELD);
            renderButton(screen, guiGraphics, layout, layout.inlineSave(rect, save), save, Tone.PRIMARY);
        } else if (action == PhoneChatApp.ACTION_RENAME) {
            String value = conversation == null ? "" : conversation.title;
            if (owner) {
                renderNavigationRow(screen, guiGraphics, layout, rect, actionLabel(app, action), value);
            } else {
                int y = rect.top + (rect.height - layout.bodyHeight) / 2;
                drawText(guiGraphics, layout, actionLabel(app, action), rect.left + layout.pad, y, TEXT, layout.bodyScale);
                Component valueText = fit(layout, value, PLAIN, rect.width / 2, layout.bodyScale);
                drawText(guiGraphics, layout, valueText, rect.right() - layout.pad - layout.width(valueText, layout.bodyScale), y,
                        TEXT_TERTIARY, layout.bodyScale);
            }
        } else if (action == PhoneChatApp.ACTION_INVITE) {
            renderNavigationRow(screen, guiGraphics, layout, rect, actionLabel(app, action), null);
        } else {
            boolean danger = action == PhoneChatApp.ACTION_CLEAR || action == PhoneChatApp.ACTION_LEAVE
                    || action == PhoneChatApp.ACTION_REMOVE_FRIEND || action == PhoneChatApp.ACTION_BLOCK;
            if (hovered(screen, rect)) {
                guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom() - 1, HOVER);
            }
            Component label = actionLabel(app, action);
            drawCentered(guiGraphics, layout, fit(layout, label.getString(), PLAIN, rect.width - 2 * layout.pad, layout.bodyScale),
                    rect.left + rect.width / 2, rect.top + (rect.height - layout.bodyHeight) / 2,
                    danger ? DANGER : ACCENT, layout.bodyScale);
        }
        if (divider) {
            guiGraphics.fill(rect.left + layout.pad, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
        }
    }

    private static void clickInfo(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                  double mouseX, double mouseY, int button) {
        if (backHit(layout, layout.header(), mouseX, mouseY, button)) {
            goBack(screen, app);
            return;
        }
        PhoneClientChatState.Conversation conversation = app.conversation();
        int[] actions = app.infoActions(conversation);
        List<ChatPayloads.MemberData> members = infoMembers(conversation);
        List<PhoneChatLayout.Row> rows = layout.infoRows(actions.length, members.size());
        UiRect viewport = layout.pageViewport();
        PhoneChatLayout.Row row = layout.rowAt(viewport, rows, app.infoScroll, mouseX, mouseY);
        boolean owner = app.isOwner(conversation);
        UUID self = app.self();

        if (row != null && row.kind() == PhoneChatLayout.ROW_MEMBER) {
            ChatPayloads.MemberData member = members.get(row.index());
            boolean isSelf = member.uuid() != null && member.uuid().equals(self);
            if (button == 1) {
                app.menuNumber = owner && !isSelf && !app.menuNumber.equals(member.number()) ? member.number() : "";
                return;
            }
            if (button == 0 && app.menuNumber.equals(member.number())) {
                UiRect rect = layout.rowRect(viewport, row, app.infoScroll);
                if (layout.rowAction(rect, Component.translatable("phone.chat.group.kick")).contains(mouseX, mouseY)) {
                    app.kick(member.number());
                }
            }
            app.clearMenus();
            return;
        }
        if (button != 0) {
            app.clearMenus();
            return;
        }
        if (!app.menuNumber.isEmpty()) {
            app.clearMenus();
            return;
        }
        if (row == null || row.kind() != PhoneChatLayout.ROW_ACTION) {
            if (app.renaming) {
                app.saveRename();
            }
            return;
        }
        int action = actions[row.index()];
        if (action == PhoneChatApp.ACTION_RENAME && app.renaming) {
            UiRect rect = layout.rowRect(viewport, row, app.infoScroll);
            Component save = Component.translatable("screen.bphone.phone.call.contacts.save_button");
            if (layout.inlineSave(rect, save).contains(mouseX, mouseY)) {
                app.saveRename();
            }
            return;
        }
        if (app.renaming) {
            app.saveRename();
            return;
        }
        app.runAction(action);
    }

    private static void renderBlocked(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable("phone.chat.profile.blocked"), true, false);
        UiRect viewport = layout.pageViewport();
        List<PhoneClientChatState.Friend> blocked = PhoneClientChatState.getBlocked();
        if (blocked.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("phone.chat.blocked.empty"),
                    Component.translatable("phone.chat.blocked.hint"));
            return;
        }
        app.blockedScroll = Mth.clamp(app.blockedScroll, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(blocked.size(), false)));
        Component unblock = Component.translatable("phone.chat.blocked.remove");
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < blocked.size(); index++) {
            UiRect row = layout.listRow(viewport, index, app.blockedScroll);
            if (row.bottom() <= viewport.top) {
                continue;
            }
            if (row.top >= viewport.bottom()) {
                break;
            }
            PhoneClientChatState.Friend entry = blocked.get(index);
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), SURFACE);
            UiRect face = layout.rowAvatar(row, layout.avatar);
            ResourceLocation texture = entry.uuid() == null
                    ? DefaultPlayerSkin.getDefaultTexture()
                    : skinOf(entry.uuid());
            renderFace(guiGraphics, texture, face, SURFACE);
            UiRect action = layout.rowAction(row, unblock);
            renderButton(screen, guiGraphics, layout, action, unblock, Tone.SECONDARY);
            int textLeft = layout.rowTextLeft(row, layout.avatar);
            renderTwoLineText(guiGraphics, layout, row, textLeft, action.left - layout.gap,
                    entry.name().isBlank() ? entry.number() : entry.name(), TEXT, entry.number(), TEXT_TERTIARY);
            if (index < blocked.size() - 1) {
                guiGraphics.fill(textLeft, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
        }
        guiGraphics.disableScissor();
    }

    private static void clickBlocked(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                     double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            goBack(screen, app);
            return;
        }
        List<PhoneClientChatState.Friend> blocked = PhoneClientChatState.getBlocked();
        UiRect viewport = layout.pageViewport();
        int index = layout.listRowAt(viewport, blocked.size(), app.blockedScroll, mouseX, mouseY);
        if (index < 0) {
            return;
        }
        UiRect row = layout.listRow(viewport, index, app.blockedScroll);
        if (layout.rowAction(row, Component.translatable("phone.chat.blocked.remove")).contains(mouseX, mouseY)) {
            ChatNetworkingClient.block(blocked.get(index).number(), false);
        }
    }

    private static void renderImagePick(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable("phone.chat.image.pick"), true, false);
        UiRect viewport = layout.pageViewport();
        List<PhotoEntry> photos = screen.getPhotos();
        if (photos.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("phone.chat.image.none"),
                    Component.translatable("phone.chat.image.none_hint"));
            return;
        }
        app.pickScroll = Mth.clamp(app.pickScroll, 0, PhoneChatLayout.maxScroll(viewport, layout.gridContentHeight(photos.size())));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < photos.size(); index++) {
            UiRect slot = layout.gridSlot(viewport, index, app.pickScroll);
            if (slot.bottom() <= viewport.top || slot.top >= viewport.bottom()) {
                continue;
            }
            PhotoEntry photo = photos.get(index);
            PhotoTexture texture = photo.hasFile() ? screen.getOrLoadPhotoTexture(photo.fileName, true) : null;
            if (texture != null) {
                guiGraphics.blit(texture.textureId, slot.left, slot.top, slot.width, slot.height, 0.0F, 0.0F,
                        texture.width, texture.height, texture.width, texture.height);
            } else {
                guiGraphics.fill(slot.left, slot.top, slot.right(), slot.bottom(), SECONDARY);
                renderImageGlyph(guiGraphics, layout, slot, TEXT_TERTIARY, SECONDARY);
            }
            if (hovered(screen, slot) && hovered(screen, viewport)) {
                int stroke = Math.max(1, layout.u(1.5F));
                guiGraphics.fill(slot.left, slot.top, slot.right(), slot.top + stroke, ACCENT);
                guiGraphics.fill(slot.left, slot.bottom() - stroke, slot.right(), slot.bottom(), ACCENT);
                guiGraphics.fill(slot.left, slot.top, slot.left + stroke, slot.bottom(), ACCENT);
                guiGraphics.fill(slot.right() - stroke, slot.top, slot.right(), slot.bottom(), ACCENT);
            }
        }
        guiGraphics.disableScissor();
    }

    private static void clickImagePick(PhoneScreen screen, PhoneChatApp app, PhoneChatLayout layout,
                                       double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            goBack(screen, app);
            return;
        }
        List<PhotoEntry> photos = screen.getPhotos();
        int index = layout.gridIndexAt(layout.pageViewport(), photos.size(), app.pickScroll, mouseX, mouseY);
        if (index >= 0) {
            app.sendPhoto(photos.get(index));
        }
    }

    private static void renderImageView(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), VIEWER_BACKDROP);
        PhotoTexture texture = PhoneChatImages.get(app.viewImageId);
        int areaTop = surface.top + layout.headerHeight;
        int areaHeight = surface.height - 2 * layout.headerHeight;
        if (texture != null && texture.width > 0 && texture.height > 0) {
            float factor = Math.min((float) surface.width / texture.width, (float) areaHeight / texture.height);
            int width = Math.max(1, Math.round(texture.width * factor));
            int height = Math.max(1, Math.round(texture.height * factor));
            int left = surface.left + (surface.width - width) / 2;
            int top = areaTop + (areaHeight - height) / 2;
            guiGraphics.blit(texture.textureId, left, top, width, height, 0.0F, 0.0F, texture.width, texture.height,
                    texture.width, texture.height);
        } else {
            Component label = Component.translatable(PhoneChatImages.isFailed(app.viewImageId)
                    ? "phone.chat.image.failed" : "phone.chat.image.loading");
            drawCentered(guiGraphics, layout, label, surface.left + surface.width / 2,
                    areaTop + (areaHeight - layout.bodyHeight) / 2, WHITE, layout.bodyScale);
        }
        Component hint = fit(layout, Component.translatable("phone.chat.image.close_hint").getString(), PLAIN,
                surface.width - 2 * layout.pad, layout.captionScale);
        drawCentered(guiGraphics, layout, hint, surface.left + surface.width / 2,
                surface.bottom() - layout.headerHeight / 2 - layout.captionHeight / 2, 0xCCFFFFFF, layout.captionScale);
    }

    static void renderChatCameraOverlay(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect camera = screen.getCameraViewBounds();
        int headerHeight = Math.max(layout.headerHeight + layout.u(10), Math.round(camera.height * 0.15F));
        int footerHeight = Math.max(layout.u(54), Math.round(camera.height * 0.24F));
        int footerTop = camera.bottom() - footerHeight;
        int bleed = Math.max(4, layout.u(6));

        guiGraphics.fill(camera.left - bleed, camera.top, camera.right() + bleed, camera.top + headerHeight, SCAN_SHADE);
        guiGraphics.fill(camera.left - bleed, footerTop, camera.right() + bleed, camera.bottom(), SCAN_SHADE);

        int centerX = camera.left + camera.width / 2;
        int textWidth = camera.width - 2 * layout.pad;
        int titleY = camera.top + (headerHeight - layout.titleHeight - layout.u(5)) / 2;
        Component title = fit(layout, Component.translatable("phone.chat.add_menu.by_scan").getString(), BOLD,
                textWidth, layout.titleScale);
        drawCentered(guiGraphics, layout, title, centerX, titleY, WHITE, layout.titleScale);
        int underlineWidth = Math.max(layout.u(16), layout.width(title, layout.titleScale) / 2);
        guiGraphics.fill(centerX - underlineWidth / 2, titleY + layout.titleHeight + layout.u(3),
                centerX - underlineWidth / 2 + underlineWidth, titleY + layout.titleHeight + layout.u(3) + Math.max(1, layout.u(1.5F)),
                ACCENT);

        int hintY = footerTop + layout.u(8);
        hintY = drawWrappedCentered(guiGraphics, layout, Component.translatable("phone.chat.add_menu.scan_hint"),
                centerX, hintY, textWidth, WHITE, layout.captionScale);
        drawWrappedCentered(guiGraphics, layout, Component.translatable("phone.chat.add_menu.scan_move_hint"),
                centerX, hintY + layout.u(4), textWidth, 0xCCFFFFFF, layout.captionScale);

        int availableTop = camera.top + headerHeight + layout.u(14);
        int availableBottom = footerTop - layout.u(14);
        int availableHeight = Math.max(layout.u(24), availableBottom - availableTop);
        int size = Math.min(camera.width - layout.u(34), Math.min(availableHeight, layout.u(92)));
        int left = camera.left + (camera.width - size) / 2;
        int top = availableTop + Math.max(0, (availableHeight - size) / 2);
        int right = left + size;
        int bottom = top + size;
        int cornerLength = Math.max(8, layout.u(16));
        int thickness = Math.max(1, layout.u(2));
        int shadow = 0x66000000;
        int color = screen.chatScanHoverTicks > 0 ? SCAN_SUCCESS : 0xEFFFFFFF;

        drawCorner(guiGraphics, left + 1, top + 1, cornerLength, thickness, true, true, shadow);
        drawCorner(guiGraphics, right + 1, top + 1, cornerLength, thickness, false, true, shadow);
        drawCorner(guiGraphics, left + 1, bottom + 1, cornerLength, thickness, true, false, shadow);
        drawCorner(guiGraphics, right + 1, bottom + 1, cornerLength, thickness, false, false, shadow);
        drawCorner(guiGraphics, left, top, cornerLength, thickness, true, true, color);
        drawCorner(guiGraphics, right, top, cornerLength, thickness, false, true, color);
        drawCorner(guiGraphics, left, bottom, cornerLength, thickness, true, false, color);
        drawCorner(guiGraphics, right, bottom, cornerLength, thickness, false, false, color);

        if (screen.chatScanHoverTicks > 0) {
            float progress = Mth.clamp(screen.chatScanHoverTicks / 15.0F, 0.0F, 1.0F);
            int fillHeight = Math.round((size - 2 * thickness) * progress);
            guiGraphics.fill(left + thickness, bottom - thickness - fillHeight,
                    right - thickness, bottom - thickness, 0x3306C755);
        }
    }

    private static void renderTabBar(PhoneScreen screen, PhoneChatApp app, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect bar = layout.tabBar();
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.top + 1, HAIRLINE);

        Component[] labels = {
                Component.translatable("phone.chat.tab.chats"),
                Component.translatable("phone.chat.tab.contacts"),
                Component.translatable("phone.chat.tab.profile")
        };
        int requestCount = PhoneClientChatState.getRequestCount();
        int unreadCount = PhoneClientChatState.getTotalUnread();
        int indicatorHeight = Math.max(1, layout.u(2));
        int indicatorGap = layout.u(3);
        for (int index = 0; index < labels.length; index++) {
            UiRect slot = layout.tabSlot(index);
            boolean active = app.tab == index;
            Style style = active ? BOLD : PLAIN;
            String raw = labels[index].getString();
            int available = slot.width - 2 * layout.u(2);
            int rawWidth = Math.max(1, layout.font.width(Component.literal(raw).withStyle(style)));
            float labelScale = layout.bodyScale;
            if (rawWidth * labelScale > available) {
                labelScale = Math.max(0.5F, available / (float) rawWidth);
            }
            int labelHeight = Math.max(1, Math.round(8.0F * labelScale));
            Component label = fit(layout, raw, style, available, labelScale);
            int labelWidth = layout.width(label, labelScale);
            int blockHeight = labelHeight + indicatorGap + indicatorHeight;
            int labelX = slot.left + (slot.width - labelWidth) / 2;
            int labelY = slot.top + 1 + (slot.height - 1 - blockHeight) / 2;
            int color = active ? ACCENT : hovered(screen, slot) ? TEXT : TEXT_SECONDARY;
            drawText(guiGraphics, layout, label, labelX, labelY, color, labelScale);
            if (active) {
                int indicatorWidth = Math.max(layout.u(10), labelWidth / 2);
                int indicatorX = slot.left + (slot.width - indicatorWidth) / 2;
                int indicatorY = labelY + labelHeight + indicatorGap;
                guiGraphics.fill(indicatorX, indicatorY, indicatorX + indicatorWidth, indicatorY + indicatorHeight, ACCENT);
            }
            int badge = index == PhoneChatLayout.TAB_CONTACTS ? requestCount
                    : index == PhoneChatLayout.TAB_CHATS ? unreadCount : 0;
            if (badge > 0) {
                renderBadge(guiGraphics, layout, labelX + labelWidth, labelY, badge);
            }
        }
    }

    private static void renderListHint(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect viewport, int top) {
        if (top >= viewport.bottom()) {
            return;
        }
        Component hint = fit(layout, Component.translatable("phone.chat.list.delete_hint").getString(), PLAIN,
                viewport.width - 2 * layout.pad, layout.captionScale);
        int y = top + (layout.hintHeight - layout.captionHeight) / 2;
        drawCentered(guiGraphics, layout, hint, viewport.left + viewport.width / 2, y, TEXT_TERTIARY, layout.captionScale);
    }

    private static ResourceLocation ownSkin(PhoneScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && minecraft.player != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (info != null) {
                return screen.getPlayerSkin(info);
            }
        }
        return minecraft.player == null
                ? DefaultPlayerSkin.getDefaultTexture()
                : DefaultPlayerSkin.get(minecraft.player.getUUID()).texture();
    }

    private static ResourceLocation memberTexture(ChatPayloads.MemberData member) {
        if (member.uuid() == null) {
            return DefaultPlayerSkin.getDefaultTexture();
        }
        return skinOf(member.uuid());
    }

    private static ResourceLocation skinOf(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        return DefaultPlayerSkin.get(uuid).texture();
    }

    private static ResourceLocation requestTexture(PhoneClientChatState.FriendRequest request) {
        Minecraft minecraft = Minecraft.getInstance();
        String name = request.fromName() == null ? "" : request.fromName();
        if (!name.isBlank() && minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(name);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        String seed = name.isBlank() ? request.fromNumber() : name;
        UUID fallback = UUID.nameUUIDFromBytes(("OfflinePlayer:" + seed).getBytes(StandardCharsets.UTF_8));
        return DefaultPlayerSkin.get(fallback).texture();
    }
}
