package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.announcement.Announcement;
import cn.erindax.bphone.client.announcement.AnnouncementClientState;
import cn.erindax.bphone.client.announcement.AnnouncementEditScreen;
import cn.erindax.bphone.client.announcement.AnnouncementNetworkingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneAnnouncementsSurfaceRenderer {
    static final int STAR_COLOR = 0xFFFFB020;
    private static final String STAR = "\u2605";

    private PhoneAnnouncementsSurfaceRenderer() {
    }

    static void render(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);

        Announcement detail = screen.getAnnouncementDetail();
        if (detail != null) {
            renderDetail(screen, guiGraphics, layout, detail);
            return;
        }

        boolean manage = AnnouncementNetworkingClient.canManage();
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.announcements.title"), true, manage);
        List<Announcement> announcements = AnnouncementClientState.getAnnouncements();
        UiRect viewport = layout.pageViewport();
        if (announcements.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("screen.bphone.phone.announcements.empty"),
                    manage ? Component.translatable("screen.bphone.phone.announcements.empty_manage") : null);
            return;
        }

        screen.announcementsScroll = Mth.clamp(screen.announcementsScroll, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(announcements.size(), false)));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < announcements.size(); index++) {
            UiRect row = layout.listRow(viewport, index, screen.announcementsScroll);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            renderRow(screen, guiGraphics, layout, row, announcements.get(index));
        }
        guiGraphics.disableScissor();
    }

    private static void renderRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row,
                                  Announcement announcement) {
        guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), hovered(screen, row) ? HOVER : SURFACE);
        guiGraphics.fill(row.left + layout.pad, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
        boolean unread = AnnouncementClientState.isUnread(announcement);
        int dot = Math.max(2, layout.u(4));
        int textLeft = row.left + layout.pad + dot + layout.u(4);
        if (unread) {
            fillRounded(guiGraphics, row.left + layout.pad, row.top + (row.height - dot) / 2, dot, dot, dot / 2, DANGER);
        }

        int right = row.right() - layout.pad;
        int blockHeight = layout.bodyHeight + layout.textGap + layout.captionHeight;
        int top = row.top + (row.height - blockHeight) / 2;
        Component time = Component.literal(formatTime(announcement.time()));
        int timeWidth = layout.width(time, layout.captionScale);
        drawText(guiGraphics, layout, time, right - timeWidth, top + Math.max(0, layout.bodyHeight - layout.captionHeight),
                TEXT_TERTIARY, layout.captionScale);

        int titleLeft = textLeft;
        if (announcement.starred()) {
            Component star = Component.literal(STAR).withStyle(BOLD);
            drawText(guiGraphics, layout, star, titleLeft, top, STAR_COLOR, layout.bodyScale);
            titleLeft += layout.width(star, layout.bodyScale) + layout.u(2);
        }
        drawText(guiGraphics, layout, fit(layout, announcement.title(), unread ? BOLD : PLAIN,
                        right - timeWidth - layout.gap - titleLeft, layout.bodyScale),
                titleLeft, top, TEXT, layout.bodyScale);
        String preview = announcement.body().replace('\n', ' ');
        drawText(guiGraphics, layout, fit(layout, preview, PLAIN, right - textLeft, layout.captionScale),
                textLeft, top + layout.bodyHeight + layout.textGap, TEXT_SECONDARY, layout.captionScale);
    }

    private static UiRect actionBar(PhoneChatLayout layout) {
        return layout.bottomBar();
    }

    private static UiRect[] actionButtons(PhoneChatLayout layout) {
        UiRect bar = actionBar(layout);
        int width = (bar.width - 4 * layout.pad) / 3;
        int top = bar.top + (bar.height - layout.smallButtonHeight) / 2;
        UiRect[] buttons = new UiRect[3];
        for (int index = 0; index < 3; index++) {
            buttons[index] = new UiRect(bar.left + layout.pad + index * (width + layout.pad), top, width, layout.smallButtonHeight);
        }
        return buttons;
    }

    private static UiRect detailViewport(PhoneChatLayout layout, boolean manage) {
        int top = layout.header().bottom();
        int bottom = manage ? actionBar(layout).top : layout.surface.bottom();
        return layout.viewportBetween(top, bottom);
    }

    private static void renderDetail(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                     Announcement announcement) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.announcements.detail"), true, false);
        boolean manage = AnnouncementNetworkingClient.canManage();
        UiRect viewport = detailViewport(layout, manage);
        int left = viewport.left + layout.pad;
        int width = viewport.width - 2 * layout.pad;

        List<FormattedCharSequence> titleLines = layout.font.split(Component.literal(announcement.title()).withStyle(BOLD),
                Math.max(16, Mth.floor(width / layout.bodyScale)));
        List<FormattedCharSequence> bodyLines = layout.font.split(Component.literal(announcement.body()),
                Math.max(16, Mth.floor(width / layout.bodyScale)));
        int contentHeight = layout.u(8) + (announcement.starred() ? layout.captionHeight + layout.u(4) : 0)
                + titleLines.size() * layout.bodyLine + layout.u(4) + layout.captionHeight + layout.u(12)
                + bodyLines.size() * layout.bodyLine + layout.u(8);
        screen.announcementDetailScroll = Mth.clamp(screen.announcementDetailScroll, 0,
                PhoneChatLayout.maxScroll(viewport, contentHeight));

        guiGraphics.fill(viewport.left, viewport.top, viewport.right(), viewport.bottom(), SURFACE);
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        int y = viewport.top + layout.u(8) - screen.announcementDetailScroll;
        if (announcement.starred()) {
            Component badge = Component.translatable("screen.bphone.phone.announcements.starred", STAR);
            drawText(guiGraphics, layout, badge, left, y, STAR_COLOR, layout.captionScale);
            y += layout.captionHeight + layout.u(4);
        }
        for (FormattedCharSequence line : titleLines) {
            drawSequence(guiGraphics, layout, line, left, y, TEXT, layout.bodyScale);
            y += layout.bodyLine;
        }
        y += layout.u(4);
        String meta = announcement.author() + "  " + formatTime(announcement.time());
        if (announcement.editedAt() > 0L) {
            meta += "  " + Component.translatable("screen.bphone.phone.announcements.edited").getString();
        }
        drawText(guiGraphics, layout, fit(layout, meta, PLAIN, width, layout.captionScale), left, y, TEXT_TERTIARY,
                layout.captionScale);
        y += layout.captionHeight + layout.u(6);
        guiGraphics.fill(left, y, left + width, y + 1, HAIRLINE);
        y += layout.u(6);
        for (FormattedCharSequence line : bodyLines) {
            drawSequence(guiGraphics, layout, line, left, y, TEXT_SECONDARY, layout.bodyScale);
            y += layout.bodyLine;
        }
        guiGraphics.disableScissor();

        if (manage) {
            UiRect bar = actionBar(layout);
            guiGraphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
            guiGraphics.fill(bar.left, bar.top, bar.right(), bar.top + 1, HAIRLINE);
            UiRect[] buttons = actionButtons(layout);
            renderButton(screen, guiGraphics, layout, buttons[0],
                    Component.translatable("screen.bphone.phone.announcements.edit"), Tone.SECONDARY);
            renderButton(screen, guiGraphics, layout, buttons[1], Component.translatable(announcement.starred()
                    ? "screen.bphone.phone.announcements.unstar" : "screen.bphone.phone.announcements.star"), Tone.PRIMARY);
            renderButton(screen, guiGraphics, layout, buttons[2], Component.translatable(screen.announcementDeleteConfirm > 0
                    ? "screen.bphone.phone.announcements.delete_confirm" : "screen.bphone.phone.announcements.delete"),
                    Tone.DANGER);
        }
    }

    static boolean handleClick(PhoneScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect header = layout.header();
        Announcement detail = screen.getAnnouncementDetail();
        boolean manage = AnnouncementNetworkingClient.canManage();
        if (detail != null) {
            if (layout.backButton(header).contains(mouseX, mouseY)) {
                screen.closeAnnouncementDetail();
                return true;
            }
            if (!manage) {
                return true;
            }
            UiRect[] buttons = actionButtons(layout);
            if (buttons[0].contains(mouseX, mouseY)) {
                openEditor(screen, detail);
            } else if (buttons[1].contains(mouseX, mouseY)) {
                AnnouncementNetworkingClient.star(detail.id(), !detail.starred());
            } else if (buttons[2].contains(mouseX, mouseY)) {
                if (screen.announcementDeleteConfirm > 0) {
                    AnnouncementNetworkingClient.delete(detail.id());
                    screen.closeAnnouncementDetail();
                } else {
                    screen.announcementDeleteConfirm = 60;
                }
            }
            return true;
        }

        if (layout.backButton(header).contains(mouseX, mouseY)) {
            screen.closeAnnouncementsApp();
            return true;
        }
        if (manage && layout.actionButton(header).contains(mouseX, mouseY)) {
            openEditor(screen, null);
            return true;
        }
        List<Announcement> announcements = AnnouncementClientState.getAnnouncements();
        int index = layout.listRowAt(layout.pageViewport(), announcements.size(), screen.announcementsScroll, mouseX, mouseY);
        if (index >= 0) {
            screen.openAnnouncementDetail(announcements.get(index).id());
        }
        return true;
    }

    static void scroll(PhoneScreen screen, double scrollY) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        if (screen.getAnnouncementDetail() != null) {
            screen.announcementDetailScroll = Math.max(0, screen.announcementDetailScroll + delta);
        } else {
            screen.announcementsScroll = Math.max(0, screen.announcementsScroll + delta);
        }
    }

    static void renderBanner(PhoneScreen screen, GuiGraphics guiGraphics, UiRect bounds, boolean hovered) {
        Announcement pinned = AnnouncementClientState.newestUnreadStarred();
        if (pinned == null) {
            return;
        }
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, Math.max(2, layout.radius * 2),
                hovered ? 0xF5FFF1CC : 0xE8FFF7E0);
        Component star = Component.literal(STAR).withStyle(BOLD);
        int starWidth = layout.width(star, layout.titleScale);
        int starLeft = bounds.left + layout.pad;
        drawText(guiGraphics, layout, star, starLeft, bounds.top + (bounds.height - layout.titleHeight) / 2, STAR_COLOR,
                layout.titleScale);
        int textLeft = starLeft + starWidth + layout.u(4);
        int textWidth = bounds.right() - layout.pad - textLeft;
        int blockHeight = layout.captionHeight + layout.textGap + layout.bodyHeight;
        int top = bounds.top + (bounds.height - blockHeight) / 2;
        drawText(guiGraphics, layout, fit(layout, Component.translatable("screen.bphone.phone.announcements.banner").getString(),
                BOLD, textWidth, layout.captionScale), textLeft, top, 0xFFB7791F, layout.captionScale);
        drawText(guiGraphics, layout, fit(layout, pinned.title(), PLAIN, textWidth, layout.bodyScale), textLeft,
                top + layout.captionHeight + layout.textGap, 0xFF3A2E10, layout.bodyScale);
    }

    private static void openEditor(PhoneScreen screen, Announcement announcement) {
        Minecraft.getInstance().setScreen(new AnnouncementEditScreen(screen, announcement));
    }

    static String formatTime(long time) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(time));
    }
}
