package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatStore;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class PhoneChatThread {
    static final int ITEM_MORE = 0;
    static final int ITEM_TIME = 1;
    static final int ITEM_SYSTEM = 2;
    static final int ITEM_TEXT = 3;
    static final int ITEM_IMAGE = 4;
    private static final long TIME_GAP_MILLIS = 5L * 60L * 1000L;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    record Item(int type, ChatPayloads.EntryData entry, List<FormattedCharSequence> lines, boolean incoming,
                String sender, Component status, boolean statusBeside, boolean statusRead, int bodyWidth, int bodyHeight,
                int height) {
        boolean isMessage() {
            return type == ITEM_TEXT || type == ITEM_IMAGE;
        }
    }

    record Placed(Item item, int top, UiRect body) {
    }

    private PhoneChatThread() {
    }

    static List<Item> build(PhoneChatLayout layout, PhoneClientChatState.Conversation conversation, UUID self,
                            boolean loading, UiRect viewport) {
        List<Item> items = new ArrayList<>();
        if (conversation == null) {
            return items;
        }

        int captionWidth = Math.max(layout.u(20), viewport.width - 4 * layout.pad);
        if (conversation.hasMore) {
            items.add(caption(layout, ITEM_MORE, null, Component.translatable(loading
                    ? "phone.chat.thread.loading" : "phone.chat.thread.more"), captionWidth));
        }

        boolean group = conversation.isGroup();
        Component read = Component.translatable("phone.chat.read");
        Component unread = Component.translatable("phone.chat.unread");
        int statusWidth = Math.max(layout.width(read, layout.captionScale), layout.width(unread, layout.captionScale));
        int maxBubble = Math.round(viewport.width * 0.72F);
        if (!group) {
            maxBubble = Math.min(maxBubble, viewport.width - 2 * layout.pad - statusWidth - layout.gap);
        }
        maxBubble = Math.max(layout.u(30), maxBubble);
        int wrapWidth = Math.max(16, Mth.floor((maxBubble - 2 * layout.bubblePadX) / layout.bodyScale));

        long lastOutgoing = 0L;
        for (ChatPayloads.EntryData entry : conversation.entries) {
            if (entry.kind() != ChatStore.ENTRY_SYSTEM && self != null && self.equals(entry.senderUuid())) {
                lastOutgoing = entry.id();
            }
        }

        int others = Math.max(0, conversation.members.size() - 1);
        long previousTime = Long.MIN_VALUE;
        for (ChatPayloads.EntryData entry : conversation.entries) {
            if (previousTime == Long.MIN_VALUE || entry.createdAt() - previousTime >= TIME_GAP_MILLIS) {
                items.add(caption(layout, ITEM_TIME, entry, Component.literal(timeLabel(entry.createdAt(), false)), captionWidth));
            }
            previousTime = entry.createdAt();

            if (entry.kind() == ChatStore.ENTRY_SYSTEM) {
                items.add(caption(layout, ITEM_SYSTEM, entry, ChatNetworkingClient.systemText(entry.body()), captionWidth));
                continue;
            }

            boolean incoming = self == null || !self.equals(entry.senderUuid());
            String sender = group && incoming ? (entry.senderName().isBlank() ? entry.senderNumber() : entry.senderName()) : null;
            Component status = null;
            boolean beside = false;
            boolean statusRead = true;
            if (!incoming) {
                int readers = conversation.readCount(entry);
                if (!group) {
                    status = readers > 0 ? read : unread;
                    beside = true;
                    statusRead = readers > 0;
                } else if (entry.id() == lastOutgoing) {
                    status = others > 0 && readers >= others
                            ? Component.translatable("phone.chat.read_all")
                            : Component.translatable("phone.chat.read_count", readers);
                }
            }

            int senderHeight = sender == null ? 0 : layout.captionHeight + layout.u(2);
            int statusHeight = status == null || beside ? 0 : layout.u(2) + layout.captionHeight;
            if (entry.kind() == ChatStore.ENTRY_IMAGE) {
                int[] size = imageSize(layout, entry.imageId(), viewport);
                items.add(new Item(ITEM_IMAGE, entry, List.of(), incoming, sender, status, beside, statusRead, size[0], size[1],
                        senderHeight + size[1] + statusHeight));
                continue;
            }

            List<FormattedCharSequence> lines = layout.font.split(Component.literal(entry.body()), wrapWidth);
            if (lines.isEmpty()) {
                lines = List.of(FormattedCharSequence.EMPTY);
            }
            int widest = 0;
            for (FormattedCharSequence line : lines) {
                widest = Math.max(widest, layout.font.width(line));
            }
            int width = Math.max(layout.u(16), Mth.ceil(widest * layout.bodyScale) + 2 * layout.bubblePadX);
            int height = (lines.size() - 1) * layout.bodyLine + layout.bodyHeight + 2 * layout.bubblePadY;
            items.add(new Item(ITEM_TEXT, entry, lines, incoming, sender, status, beside, statusRead, width, height,
                    senderHeight + height + statusHeight));
        }
        return items;
    }

    private static Item caption(PhoneChatLayout layout, int type, ChatPayloads.EntryData entry, Component text, int maxWidth) {
        List<FormattedCharSequence> lines = layout.font.split(text, Math.max(16, Mth.floor(maxWidth / layout.captionScale)));
        if (lines.isEmpty()) {
            lines = List.of(FormattedCharSequence.EMPTY);
        }
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, layout.width(line, layout.captionScale));
        }
        int padX = layout.u(5);
        int padY = Math.max(1, layout.u(2));
        int width = widest + 2 * padX;
        int height = (lines.size() - 1) * layout.captionLine + layout.captionHeight + 2 * padY;
        return new Item(type, entry, lines, false, null, null, false, true, width, height, height);
    }

    private static int[] imageSize(PhoneChatLayout layout, String imageId, UiRect viewport) {
        int maxWidth = Math.min(Math.round(viewport.width * 0.55F), layout.u(72));
        int maxHeight = layout.u(96);
        PhotoTexture texture = PhoneChatImages.get(imageId);
        if (texture == null || texture.width <= 0 || texture.height <= 0) {
            int size = layout.u(48);
            return new int[] {size, size};
        }
        float factor = Math.min((float) maxWidth / texture.width, (float) maxHeight / texture.height);
        int width = Math.max(layout.u(20), Math.round(texture.width * factor));
        int height = Math.max(layout.u(20), Math.round(texture.height * factor));
        return new int[] {width, height};
    }

    static int senderHeight(PhoneChatLayout layout, Item item) {
        return item.sender() == null ? 0 : layout.captionHeight + layout.u(2);
    }

    static int contentHeight(PhoneChatLayout layout, List<Item> items) {
        if (items.isEmpty()) {
            return 0;
        }
        int total = 2 * layout.messagePad;
        for (int index = 0; index < items.size(); index++) {
            total += items.get(index).height();
            if (index > 0) {
                total += layout.bubbleGap;
            }
        }
        return total;
    }

    static List<Placed> place(PhoneChatLayout layout, List<Item> items, UiRect viewport, int scroll) {
        List<Placed> placed = new ArrayList<>(items.size());
        int cursor = viewport.bottom() - layout.messagePad + scroll;
        for (int index = items.size() - 1; index >= 0; index--) {
            Item item = items.get(index);
            int top = cursor - item.height();
            int bodyTop = top + senderHeight(layout, item);
            int left;
            if (item.isMessage()) {
                left = item.incoming() ? viewport.left + layout.pad : viewport.right() - layout.pad - item.bodyWidth();
            } else {
                left = viewport.left + (viewport.width - item.bodyWidth()) / 2;
            }
            placed.add(new Placed(item, top, new UiRect(left, bodyTop, item.bodyWidth(), item.bodyHeight())));
            cursor = top - layout.bubbleGap;
        }
        Collections.reverse(placed);
        return placed;
    }

    static int scrollToEntry(PhoneChatLayout layout, List<Item> items, long entryId, UiRect viewport) {
        int fromBottom = layout.messagePad;
        for (int index = items.size() - 1; index >= 0; index--) {
            Item item = items.get(index);
            if (item.entry() != null && item.entry().id() == entryId && item.type() >= ITEM_SYSTEM) {
                return Math.max(0, fromBottom + item.height() / 2 - viewport.height / 2);
            }
            fromBottom += item.height() + layout.bubbleGap;
        }
        return -1;
    }

    static boolean contains(PhoneClientChatState.Conversation conversation, long entryId) {
        if (conversation == null) {
            return false;
        }
        for (ChatPayloads.EntryData entry : conversation.entries) {
            if (entry.id() == entryId) {
                return true;
            }
        }
        return false;
    }

    static String timeLabel(long millis, boolean compact) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone);
        LocalDate today = LocalDate.now(zone);
        LocalDate day = time.toLocalDate();
        String clock = time.format(CLOCK);
        if (day.equals(today)) {
            return clock;
        }
        if (day.equals(today.minusDays(1))) {
            return compact
                    ? Component.translatable("phone.chat.time.yesterday").getString()
                    : Component.translatable("phone.chat.time.yesterday_at", clock).getString();
        }
        String date = day.getYear() == today.getYear() ? time.format(MONTH_DAY) : time.format(FULL_DATE);
        return compact ? date : date + " " + clock;
    }
}
