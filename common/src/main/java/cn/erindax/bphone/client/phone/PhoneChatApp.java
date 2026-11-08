package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.chat.ChatNetworking;
import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatService;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PhoneChatApp {
    static final int PAGE_TABS = 0;
    static final int PAGE_ADD = 1;
    static final int PAGE_SELECTOR = 2;
    static final int PAGE_THREAD = 3;
    static final int PAGE_SEARCH = 4;
    static final int PAGE_GROUP_CREATE = 5;
    static final int PAGE_INVITE = 6;
    static final int PAGE_INFO = 7;
    static final int PAGE_BLOCKED = 8;
    static final int PAGE_IMAGE_PICK = 9;
    static final int PAGE_IMAGE_VIEW = 10;

    static final int ACTION_RENAME = 0;
    static final int ACTION_INVITE = 1;
    static final int ACTION_CLEAR = 2;
    static final int ACTION_LEAVE = 3;
    static final int ACTION_ADD_FRIEND = 4;
    static final int ACTION_REMOVE_FRIEND = 5;
    static final int ACTION_BLOCK = 6;
    static final int ACTION_UNBLOCK = 7;

    private static final int FIELD_NONE = 0;
    private static final int FIELD_DRAFT = 1;
    private static final int FIELD_FRIEND = 2;
    private static final int FIELD_NICKNAME = 3;
    private static final int FIELD_SEARCH = 4;
    private static final int FIELD_GROUP = 5;
    private static final int FIELD_RENAME = 6;
    private static final int CONFIRM_TICKS = 60;
    private static final int HIGHLIGHT_TICKS = 60;
    private static final int PENDING_TICKS = 100;

    private final PhoneScreen screen;
    private final Deque<Integer> history = new ArrayDeque<>();
    private final Map<Long, Long> readSent = new HashMap<>();
    final Set<String> selected = new LinkedHashSet<>();
    int page = PAGE_TABS;
    int tab = PhoneChatLayout.TAB_CHATS;
    int listScroll;
    int contactsScroll;
    int selectorScroll;
    int threadScroll;
    int searchScroll;
    int pickScroll;
    int infoScroll;
    int blockedScroll;
    int groupScroll;
    long activeId;
    String activeNumber = "";
    String activeName = "";
    String draft = "";
    String friendNumber = "";
    String nicknameDraft = "";
    boolean nicknameEditing;
    long menuConversationId;
    String menuNumber = "";
    String searchQuery = "";
    String searchedQuery = "";
    List<ChatPayloads.SearchHitData> searchHits = List.of();
    int searchPendingTicks;
    String groupName = "";
    int groupPendingTicks;
    boolean renaming;
    String renameDraft = "";
    String viewImageId = "";
    long highlightId;
    int highlightTicks;
    long scrollToId;
    int historyPendingTicks;
    int confirmAction = -1;
    int confirmTicks;
    long anchorConversationId;
    long anchorTailId;
    int anchorContentHeight;

    PhoneChatApp(PhoneScreen screen) {
        this.screen = screen;
    }

    void reset() {
        history.clear();
        selected.clear();
        page = PAGE_TABS;
        tab = PhoneChatLayout.TAB_CHATS;
        listScroll = 0;
        contactsScroll = 0;
        selectorScroll = 0;
        threadScroll = 0;
        searchScroll = 0;
        pickScroll = 0;
        infoScroll = 0;
        blockedScroll = 0;
        groupScroll = 0;
        activeId = 0L;
        activeNumber = "";
        activeName = "";
        draft = "";
        friendNumber = "";
        nicknameDraft = "";
        nicknameEditing = false;
        menuConversationId = 0L;
        menuNumber = "";
        searchQuery = "";
        searchedQuery = "";
        searchHits = List.of();
        searchPendingTicks = 0;
        groupName = "";
        groupPendingTicks = 0;
        renaming = false;
        renameDraft = "";
        viewImageId = "";
        highlightId = 0L;
        highlightTicks = 0;
        scrollToId = 0L;
        historyPendingTicks = 0;
        confirmAction = -1;
        confirmTicks = 0;
        anchorConversationId = 0L;
        anchorTailId = 0L;
        anchorContentHeight = 0;
    }

    void open(int startTab) {
        reset();
        tab = Mth.clamp(startTab, 0, PhoneChatLayout.TAB_COUNT - 1);
    }

    UUID self() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }

    PhoneClientChatState.Conversation conversation() {
        if (activeId > 0L) {
            PhoneClientChatState.Conversation conversation = PhoneClientChatState.getConversation(activeId);
            if (conversation != null) {
                return conversation;
            }
        }
        if (!activeNumber.isEmpty()) {
            PhoneClientChatState.Conversation conversation = PhoneClientChatState.findDirect(activeNumber);
            if (conversation != null) {
                activeId = conversation.id;
                return conversation;
            }
        }
        return null;
    }

    boolean isGroup() {
        PhoneClientChatState.Conversation conversation = conversation();
        return conversation != null && conversation.isGroup();
    }

    boolean isOwner(PhoneClientChatState.Conversation conversation) {
        UUID self = self();
        return conversation != null && self != null && self.equals(conversation.ownerUuid);
    }

    String title(PhoneClientChatState.Conversation conversation) {
        if (conversation != null && conversation.isGroup()) {
            return conversation.title;
        }
        String number = conversation != null ? conversation.peerNumber : activeNumber;
        String friend = PhoneClientChatState.getFriendName(number);
        if (!friend.isBlank()) {
            return friend;
        }
        if (conversation != null && !conversation.title.isBlank()) {
            return conversation.title;
        }
        return activeName.isBlank() ? number : activeName;
    }

    String peerNumber() {
        PhoneClientChatState.Conversation conversation = conversation();
        if (conversation != null) {
            return conversation.isGroup() ? "" : conversation.peerNumber;
        }
        return activeNumber;
    }

    boolean isViewing(long conversationId) {
        return page == PAGE_THREAD && conversationId > 0L && activeId == conversationId;
    }

    private boolean isConversationPage() {
        return page == PAGE_THREAD || page == PAGE_INFO || page == PAGE_INVITE || page == PAGE_IMAGE_PICK
                || page == PAGE_IMAGE_VIEW;
    }

    void go(int next) {
        history.push(page);
        page = next;
        clearMenus();
        confirmAction = -1;
        renaming = false;
        nicknameEditing = false;
    }

    boolean back() {
        clearMenus();
        confirmAction = -1;
        renaming = false;
        if (history.isEmpty()) {
            return false;
        }
        page = history.pop();
        return true;
    }

    void resetToTabs(int nextTab) {
        history.clear();
        page = PAGE_TABS;
        tab = nextTab;
        activeId = 0L;
        activeNumber = "";
        activeName = "";
        draft = "";
        clearMenus();
        confirmAction = -1;
        renaming = false;
    }

    void clearMenus() {
        menuConversationId = 0L;
        menuNumber = "";
    }

    boolean escape() {
        if (nicknameEditing) {
            nicknameEditing = false;
            return true;
        }
        if (renaming) {
            renaming = false;
            return true;
        }
        if (confirmAction >= 0) {
            confirmAction = -1;
            return true;
        }
        if (menuConversationId != 0L || !menuNumber.isEmpty()) {
            clearMenus();
            return true;
        }
        return back();
    }

    void switchTab(int next) {
        clearMenus();
        nicknameEditing = false;
        tab = Mth.clamp(next, 0, PhoneChatLayout.TAB_COUNT - 1);
    }

    void openConversation(PhoneClientChatState.Conversation conversation) {
        if (conversation == null) {
            return;
        }
        activeId = conversation.id;
        activeNumber = conversation.isGroup() ? "" : conversation.peerNumber;
        activeName = conversation.title;
        enterThread();
        if (conversation.windowed) {
            requestLatest();
        }
    }

    void openDirect(String number, String name) {
        String normalized = PhoneData.normalizePhoneNumber(number);
        if (!PhoneData.isValidPhoneNumber(normalized)) {
            return;
        }
        PhoneClientChatState.Conversation conversation = PhoneClientChatState.findDirect(normalized);
        activeId = conversation == null ? 0L : conversation.id;
        activeNumber = normalized;
        activeName = name == null ? "" : name;
        enterThread();
        if (conversation != null && conversation.windowed) {
            requestLatest();
        }
    }

    private void enterThread() {
        draft = "";
        threadScroll = 0;
        scrollToId = 0L;
        highlightId = 0L;
        highlightTicks = 0;
        anchorConversationId = 0L;
        historyPendingTicks = 0;
        go(PAGE_THREAD);
    }

    void openSearchHit(ChatPayloads.SearchHitData hit) {
        PhoneClientChatState.Conversation conversation = PhoneClientChatState.getConversation(hit.conversationId());
        if (conversation == null) {
            status(Component.translatable("phone.chat.search.unavailable"));
            return;
        }
        activeId = conversation.id;
        activeNumber = conversation.isGroup() ? "" : conversation.peerNumber;
        activeName = conversation.title;
        enterThread();
        long target = hit.entry().id();
        if (PhoneChatThread.contains(conversation, target)) {
            scrollToId = target;
        } else {
            historyPendingTicks = PENDING_TICKS;
            ChatNetworkingClient.requestHistory(conversation.id, 0L, target);
        }
    }

    void requestLatest() {
        PhoneClientChatState.Conversation conversation = conversation();
        if (conversation == null) {
            return;
        }
        historyPendingTicks = PENDING_TICKS;
        ChatNetworkingClient.requestHistory(conversation.id, 0L, Long.MAX_VALUE);
    }

    void requestOlder(PhoneClientChatState.Conversation conversation) {
        if (conversation == null || !conversation.hasMore || historyPendingTicks > 0 || conversation.entries.isEmpty()) {
            return;
        }
        historyPendingTicks = PENDING_TICKS;
        ChatNetworkingClient.requestHistory(conversation.id, conversation.entries.get(0).id(), 0L);
    }

    void tick() {
        if (highlightTicks > 0) {
            highlightTicks--;
        }
        if (confirmTicks > 0 && --confirmTicks == 0) {
            confirmAction = -1;
        }
        if (historyPendingTicks > 0) {
            historyPendingTicks--;
        }
        if (searchPendingTicks > 0) {
            searchPendingTicks--;
        }
        if (groupPendingTicks > 0) {
            groupPendingTicks--;
        }
        if (page == PAGE_THREAD) {
            markRead();
        }
    }

    private void markRead() {
        PhoneClientChatState.Conversation conversation = conversation();
        if (conversation == null || conversation.entries.isEmpty()) {
            return;
        }
        long last = conversation.lastEntryId();
        if (last <= conversation.myLastReadId && conversation.unread == 0) {
            return;
        }
        Long sent = readSent.get(conversation.id);
        if (sent != null && sent >= last) {
            return;
        }
        readSent.put(conversation.id, last);
        ChatNetworkingClient.markRead(conversation.id, last);
        PhoneClientChatState.markLocallyRead(conversation.id, last);
    }

    void handleStateChanged() {
        if (isConversationPage() && activeId > 0L && PhoneClientChatState.getConversation(activeId) == null) {
            if (page == PAGE_THREAD && !activeNumber.isEmpty()) {
                activeId = 0L;
            } else {
                resetToTabs(PhoneChatLayout.TAB_CHATS);
            }
        }
    }

    void handleConversationRemoved(long conversationId) {
        readSent.remove(conversationId);
        if (menuConversationId == conversationId) {
            menuConversationId = 0L;
        }
        if (activeId == conversationId && isConversationPage()) {
            resetToTabs(PhoneChatLayout.TAB_CHATS);
        }
    }

    void handleHistory(long conversationId, long aroundId) {
        if (conversationId != activeId) {
            return;
        }
        historyPendingTicks = 0;
        if (aroundId == Long.MAX_VALUE) {
            threadScroll = 0;
            anchorConversationId = 0L;
        } else if (aroundId > 0L) {
            scrollToId = aroundId;
            anchorConversationId = 0L;
        }
    }

    void handleSearchResult(String query, List<ChatPayloads.SearchHitData> hits) {
        if (!query.equals(searchedQuery)) {
            return;
        }
        searchHits = List.copyOf(hits);
        searchPendingTicks = 0;
        searchScroll = 0;
    }

    void handleFocus(long conversationId) {
        if (page == PAGE_GROUP_CREATE && groupPendingTicks > 0) {
            PhoneClientChatState.Conversation conversation = PhoneClientChatState.getConversation(conversationId);
            groupPendingTicks = 0;
            groupName = "";
            selected.clear();
            resetToTabs(PhoneChatLayout.TAB_CHATS);
            if (conversation != null) {
                openConversation(conversation);
            }
            return;
        }
        if ((page == PAGE_THREAD || page == PAGE_IMAGE_PICK) && activeId <= 0L) {
            activeId = conversationId;
        }
    }

    boolean canSend() {
        if (screen.isHomePhoneMode() || ChatService.sanitize(draft).isEmpty()) {
            return false;
        }
        return activeId > 0L || PhoneData.isValidPhoneNumber(activeNumber);
    }

    void send() {
        if (!canSend()) {
            return;
        }
        ChatNetworkingClient.sendText(Math.max(0L, activeId), activeId > 0L ? "" : activeNumber, ChatService.sanitize(draft));
        draft = "";
        threadScroll = 0;
        anchorConversationId = 0L;
    }

    void sendPhoto(PhotoEntry photo) {
        if (photo == null || !photo.hasFile()) {
            return;
        }
        Path path = screen.getPhotoPath(photo.fileName);
        boolean sent = PhoneChatImages.upload(Math.max(0L, activeId), activeId > 0L ? "" : activeNumber, path);
        if (!sent) {
            status(Component.translatable("screen.bphone.phone.chat.error.image_invalid"));
            return;
        }
        status(Component.translatable("phone.chat.image.sending"));
        back();
        threadScroll = 0;
        anchorConversationId = 0L;
    }

    void viewImage(String imageId) {
        if (imageId == null || imageId.isBlank()) {
            return;
        }
        viewImageId = imageId;
        go(PAGE_IMAGE_VIEW);
    }

    boolean canAddFriend(String rawNumber) {
        String normalized = PhoneData.normalizePhoneNumber(rawNumber);
        return !screen.isHomePhoneMode()
                && PhoneData.isValidPhoneNumber(normalized)
                && PhoneData.isMobilePhoneNumber(normalized)
                && !normalized.equals(screen.getOwnPhoneNumber())
                && !PhoneClientChatState.hasFriend(normalized);
    }

    void requestFriend(String rawNumber) {
        String normalized = PhoneData.normalizePhoneNumber(rawNumber);
        if (canAddFriend(normalized)) {
            ChatNetworkingClient.requestFriend(normalized);
        }
    }

    void submitFriendNumber() {
        if (!canAddFriend(friendNumber)) {
            return;
        }
        requestFriend(friendNumber);
        friendNumber = "";
        back();
    }

    void startNicknameEdit() {
        nicknameDraft = PhoneClientChatState.getOwnNickname();
        nicknameEditing = true;
    }

    void saveNickname() {
        ChatNetworkingClient.setNickname(ChatService.sanitize(nicknameDraft));
        nicknameEditing = false;
    }

    void runSearch() {
        String query = ChatPayloads.clip(searchQuery.trim(), ChatNetworking.MAX_SEARCH_LENGTH).trim();
        searchedQuery = query;
        searchScroll = 0;
        if (query.isEmpty()) {
            searchHits = List.of();
            searchPendingTicks = 0;
            return;
        }
        searchHits = List.of();
        searchPendingTicks = PENDING_TICKS;
        ChatNetworkingClient.search(query);
    }

    void openGroupCreate() {
        groupName = "";
        selected.clear();
        groupScroll = 0;
        groupPendingTicks = 0;
        go(PAGE_GROUP_CREATE);
    }

    void openInvite() {
        selected.clear();
        groupScroll = 0;
        go(PAGE_INVITE);
    }

    int selectionLimit() {
        if (page == PAGE_INVITE) {
            PhoneClientChatState.Conversation conversation = conversation();
            int members = conversation == null ? 0 : conversation.members.size();
            return Math.max(0, ChatService.MAX_GROUP_MEMBERS - members);
        }
        return ChatService.MAX_GROUP_MEMBERS - 1;
    }

    void toggleSelected(String number) {
        if (selected.remove(number)) {
            return;
        }
        if (selected.size() < Math.min(selectionLimit(), ChatNetworking.MAX_NUMBERS)) {
            selected.add(number);
        }
    }

    List<PhoneClientChatState.Friend> selectableFriends() {
        List<PhoneClientChatState.Friend> friends = PhoneClientChatState.getFriendList();
        if (page != PAGE_INVITE) {
            return friends;
        }
        PhoneClientChatState.Conversation conversation = conversation();
        if (conversation == null) {
            return List.of();
        }
        List<PhoneClientChatState.Friend> result = new ArrayList<>();
        for (PhoneClientChatState.Friend friend : friends) {
            boolean member = false;
            for (ChatPayloads.MemberData data : conversation.members) {
                if (data.number().equals(friend.number()) || (friend.uuid() != null && friend.uuid().equals(data.uuid()))) {
                    member = true;
                    break;
                }
            }
            if (!member) {
                result.add(friend);
            }
        }
        return result;
    }

    boolean canCreateGroup() {
        return groupPendingTicks <= 0 && !ChatService.sanitize(groupName).isEmpty() && !selected.isEmpty();
    }

    void createGroup() {
        if (!canCreateGroup()) {
            return;
        }
        String name = ChatPayloads.clip(ChatService.sanitize(groupName), ChatService.MAX_GROUP_NAME_LENGTH);
        groupPendingTicks = PENDING_TICKS;
        ChatNetworkingClient.createGroup(name, new ArrayList<>(selected));
    }

    void invite() {
        PhoneClientChatState.Conversation conversation = conversation();
        if (conversation == null || selected.isEmpty()) {
            return;
        }
        ChatNetworkingClient.invite(conversation.id, new ArrayList<>(selected));
        selected.clear();
        back();
    }

    int[] infoActions(PhoneClientChatState.Conversation conversation) {
        if (conversation != null && conversation.isGroup()) {
            return new int[] {ACTION_RENAME, ACTION_INVITE, ACTION_CLEAR, ACTION_LEAVE};
        }
        String number = peerNumber();
        int friendAction = PhoneClientChatState.hasFriend(number) ? ACTION_REMOVE_FRIEND : ACTION_ADD_FRIEND;
        int blockAction = PhoneClientChatState.isBlocked(number) ? ACTION_UNBLOCK : ACTION_BLOCK;
        if (conversation == null) {
            return new int[] {friendAction, blockAction};
        }
        return new int[] {friendAction, blockAction, ACTION_CLEAR};
    }

    boolean needsConfirm(int action) {
        return action == ACTION_CLEAR || action == ACTION_LEAVE || action == ACTION_REMOVE_FRIEND || action == ACTION_BLOCK;
    }

    void runAction(int action) {
        PhoneClientChatState.Conversation conversation = conversation();
        if (needsConfirm(action) && confirmAction != action) {
            confirmAction = action;
            confirmTicks = CONFIRM_TICKS;
            return;
        }
        confirmAction = -1;
        String number = peerNumber();
        switch (action) {
            case ACTION_RENAME -> {
                if (isOwner(conversation)) {
                    renameDraft = conversation.title;
                    renaming = true;
                }
            }
            case ACTION_INVITE -> openInvite();
            case ACTION_CLEAR -> {
                if (conversation != null) {
                    ChatNetworkingClient.clear(conversation.id);
                    readSent.remove(conversation.id);
                }
            }
            case ACTION_LEAVE -> {
                if (conversation != null) {
                    ChatNetworkingClient.leave(conversation.id);
                }
            }
            case ACTION_ADD_FRIEND -> requestFriend(number);
            case ACTION_REMOVE_FRIEND -> ChatNetworkingClient.removeFriend(number);
            case ACTION_BLOCK -> ChatNetworkingClient.block(number, true);
            case ACTION_UNBLOCK -> ChatNetworkingClient.block(number, false);
            default -> {
            }
        }
    }

    void saveRename() {
        PhoneClientChatState.Conversation conversation = conversation();
        String name = ChatPayloads.clip(ChatService.sanitize(renameDraft), ChatService.MAX_GROUP_NAME_LENGTH);
        if (conversation != null && !name.isEmpty()) {
            ChatNetworkingClient.rename(conversation.id, name);
        }
        renaming = false;
    }

    void kick(String number) {
        PhoneClientChatState.Conversation conversation = conversation();
        if (conversation != null && isOwner(conversation)) {
            ChatNetworkingClient.kick(conversation.id, number);
        }
        clearMenus();
    }

    void deleteFromList(PhoneClientChatState.Conversation conversation) {
        if (conversation != null) {
            ChatNetworkingClient.clear(conversation.id);
            readSent.remove(conversation.id);
        }
        clearMenus();
    }

    void requestFriendFromScan(String number) {
        requestFriend(number);
        resetToTabs(PhoneChatLayout.TAB_CHATS);
    }

    void selectOnlinePlayer(PlayerInfo info) {
        if (info == null || info.getProfile() == null) {
            return;
        }
        requestFriend(screen.getPlayerPhoneNumber(info.getProfile().getId()));
        back();
    }

    private int field() {
        return switch (page) {
            case PAGE_THREAD -> FIELD_DRAFT;
            case PAGE_ADD -> FIELD_FRIEND;
            case PAGE_SEARCH -> FIELD_SEARCH;
            case PAGE_GROUP_CREATE -> FIELD_GROUP;
            case PAGE_INFO -> renaming ? FIELD_RENAME : FIELD_NONE;
            case PAGE_TABS -> tab == PhoneChatLayout.TAB_PROFILE && nicknameEditing ? FIELD_NICKNAME : FIELD_NONE;
            default -> FIELD_NONE;
        };
    }

    private String value(int field) {
        return switch (field) {
            case FIELD_DRAFT -> draft;
            case FIELD_FRIEND -> friendNumber;
            case FIELD_NICKNAME -> nicknameDraft;
            case FIELD_SEARCH -> searchQuery;
            case FIELD_GROUP -> groupName;
            case FIELD_RENAME -> renameDraft;
            default -> "";
        };
    }

    private void setValue(int field, String value) {
        switch (field) {
            case FIELD_DRAFT -> draft = value;
            case FIELD_FRIEND -> friendNumber = value;
            case FIELD_NICKNAME -> nicknameDraft = value;
            case FIELD_SEARCH -> searchQuery = value;
            case FIELD_GROUP -> groupName = value;
            case FIELD_RENAME -> renameDraft = value;
            default -> {
            }
        }
    }

    private int limit(int field) {
        return switch (field) {
            case FIELD_DRAFT -> ChatNetworking.MAX_MESSAGE_LENGTH;
            case FIELD_FRIEND -> PhoneData.PHONE_NUMBER_LENGTH;
            case FIELD_NICKNAME -> PhoneData.MAX_CONTACT_NAME_LENGTH;
            case FIELD_SEARCH -> ChatNetworking.MAX_SEARCH_LENGTH;
            case FIELD_GROUP, FIELD_RENAME -> ChatService.MAX_GROUP_NAME_LENGTH;
            default -> 0;
        };
    }

    private boolean accepts(int field, char character) {
        if (Character.isISOControl(character)) {
            return false;
        }
        return field != FIELD_FRIEND || Character.isDigit(character);
    }

    private void submit(int field) {
        switch (field) {
            case FIELD_DRAFT -> send();
            case FIELD_FRIEND -> submitFriendNumber();
            case FIELD_NICKNAME -> saveNickname();
            case FIELD_SEARCH -> runSearch();
            case FIELD_GROUP -> createGroup();
            case FIELD_RENAME -> saveRename();
            default -> {
            }
        }
    }

    boolean keyPressed(int keyCode, boolean control) {
        int field = field();
        if (field == FIELD_NONE) {
            return false;
        }
        String value = value(field);
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (!value.isEmpty()) {
                setValue(field, value.substring(0, value.length() - 1));
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            submit(field);
            return true;
        }
        if (keyCode == InputConstants.KEY_V && control) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null) {
                for (int index = 0; index < clipboard.length(); index++) {
                    append(field, clipboard.charAt(index));
                }
            }
            return true;
        }
        return field == FIELD_FRIEND && digitKey(keyCode) != null && append(field, digitKey(keyCode).charAt(0));
    }

    boolean charTyped(char character) {
        int field = field();
        if (field == FIELD_NONE) {
            return false;
        }
        if (field == FIELD_FRIEND) {
            return true;
        }
        append(field, character);
        return true;
    }

    private boolean append(int field, char character) {
        String value = value(field);
        if (!accepts(field, character) || value.length() >= limit(field)) {
            return true;
        }
        setValue(field, value + character);
        return true;
    }

    private static String digitKey(int keyCode) {
        return switch (keyCode) {
            case InputConstants.KEY_0, InputConstants.KEY_NUMPAD0 -> "0";
            case InputConstants.KEY_1, InputConstants.KEY_NUMPAD1 -> "1";
            case InputConstants.KEY_2, InputConstants.KEY_NUMPAD2 -> "2";
            case InputConstants.KEY_3, InputConstants.KEY_NUMPAD3 -> "3";
            case InputConstants.KEY_4, InputConstants.KEY_NUMPAD4 -> "4";
            case InputConstants.KEY_5, InputConstants.KEY_NUMPAD5 -> "5";
            case InputConstants.KEY_6, InputConstants.KEY_NUMPAD6 -> "6";
            case InputConstants.KEY_7, InputConstants.KEY_NUMPAD7 -> "7";
            case InputConstants.KEY_8, InputConstants.KEY_NUMPAD8 -> "8";
            case InputConstants.KEY_9, InputConstants.KEY_NUMPAD9 -> "9";
            default -> null;
        };
    }

    void scroll(double scrollY) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        switch (page) {
            case PAGE_THREAD -> threadScroll = Math.max(0, threadScroll - delta);
            case PAGE_SELECTOR -> selectorScroll = Math.max(0, selectorScroll + delta);
            case PAGE_SEARCH -> searchScroll = Math.max(0, searchScroll + delta);
            case PAGE_GROUP_CREATE, PAGE_INVITE -> groupScroll = Math.max(0, groupScroll + delta);
            case PAGE_INFO -> infoScroll = Math.max(0, infoScroll + delta);
            case PAGE_BLOCKED -> blockedScroll = Math.max(0, blockedScroll + delta);
            case PAGE_IMAGE_PICK -> pickScroll = Math.max(0, pickScroll + delta);
            case PAGE_TABS -> {
                if (tab == PhoneChatLayout.TAB_CHATS) {
                    listScroll = Math.max(0, listScroll + delta);
                } else if (tab == PhoneChatLayout.TAB_CONTACTS) {
                    contactsScroll = Math.max(0, contactsScroll + delta);
                }
            }
            default -> {
            }
        }
    }

    void status(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(message, false);
        }
    }
}
