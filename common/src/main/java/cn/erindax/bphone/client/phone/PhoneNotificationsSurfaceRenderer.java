package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.client.notification.NotificationClientState;
import cn.erindax.bphone.client.notification.NotificationNetworkingClient;
import cn.erindax.bphone.notification.PhoneNotification;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneNotificationsSurfaceRenderer {
    private PhoneNotificationsSurfaceRenderer() {
    }

    static void render(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);

        PhoneNotification detail = screen.getNotificationDetail();
        if (detail != null) {
            renderDetail(screen, guiGraphics, layout, detail);
            return;
        }

        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.notifications.title"), true, false);
        List<PhoneNotification> notifications = NotificationClientState.getNotifications();
        UiRect viewport = listViewport(layout);
        if (notifications.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport,
                    Component.translatable("screen.bphone.phone.notifications.empty"),
                    Component.translatable("screen.bphone.phone.notifications.empty_hint"));
        } else {
            screen.notificationsScrollOffset = Mth.clamp(screen.notificationsScrollOffset, 0,
                    maxScroll(layout, notifications.size()));
            guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
            for (int index = 0; index < notifications.size(); index++) {
                UiRect row = layout.listRow(viewport, index, screen.notificationsScrollOffset);
                if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                    continue;
                }
                renderRow(screen, guiGraphics, layout, row, notifications.get(index));
            }
            guiGraphics.disableScissor();
        }
        renderFooter(screen, guiGraphics, layout, !notifications.isEmpty());
    }

    static boolean handleClick(PhoneScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect back = layout.backButton(layout.header());
        if (screen.getNotificationDetail() != null) {
            if (back.contains(mouseX, mouseY)) {
                screen.closeNotificationDetail();
            }
            return true;
        }

        if (back.contains(mouseX, mouseY)) {
            screen.closeNotificationsApp();
            return true;
        }

        List<PhoneNotification> notifications = NotificationClientState.getNotifications();
        if (!notifications.isEmpty()) {
            if (markAllButton(layout).contains(mouseX, mouseY)) {
                NotificationNetworkingClient.requestMarkAllRead();
                return true;
            }
            if (clearButton(layout).contains(mouseX, mouseY)) {
                NotificationNetworkingClient.requestClear();
                screen.notificationsScrollOffset = 0;
                return true;
            }
        }

        int index = layout.listRowAt(listViewport(layout), notifications.size(), screen.notificationsScrollOffset,
                mouseX, mouseY);
        if (index >= 0) {
            PhoneNotification notification = notifications.get(index);
            if (!notification.read()) {
                NotificationNetworkingClient.requestMarkRead(notification.id());
            }
            screen.openNotificationDetail(notification.id());
        }
        return true;
    }

    static void scroll(PhoneScreen screen, double scrollY) {
        if (screen.getNotificationDetail() != null) {
            return;
        }
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        screen.notificationsScrollOffset = Mth.clamp(screen.notificationsScrollOffset + delta, 0,
                maxScroll(layout, NotificationClientState.getNotifications().size()));
    }

    private static int maxScroll(PhoneChatLayout layout, int count) {
        return PhoneChatLayout.maxScroll(listViewport(layout), layout.listContentHeight(count, false));
    }

    private static UiRect footer(PhoneChatLayout layout) {
        UiRect surface = layout.surface;
        return new UiRect(surface.left, surface.bottom() - layout.tabBarHeight, surface.width, layout.tabBarHeight);
    }

    private static UiRect listViewport(PhoneChatLayout layout) {
        int top = layout.header().bottom();
        int bottom = footer(layout).top;
        return new UiRect(layout.surface.left, top, layout.surface.width, Math.max(1, bottom - top));
    }

    private static UiRect markAllButton(PhoneChatLayout layout) {
        UiRect footer = footer(layout);
        int width = (footer.width - 3 * layout.pad) / 2;
        int top = footer.top + (footer.height - layout.smallButtonHeight) / 2;
        return new UiRect(footer.left + layout.pad, top, width, layout.smallButtonHeight);
    }

    private static UiRect clearButton(PhoneChatLayout layout) {
        UiRect markAll = markAllButton(layout);
        return new UiRect(markAll.right() + layout.pad, markAll.top, markAll.width, markAll.height);
    }

    private static void renderRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row,
                                  PhoneNotification notification) {
        guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), hovered(screen, row) ? HOVER : SURFACE);
        guiGraphics.fill(row.left + layout.pad, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);

        int dot = Math.max(2, layout.u(4));
        int textLeft = row.left + layout.pad + dot + layout.u(4);
        if (!notification.read()) {
            fillRounded(guiGraphics, row.left + layout.pad, row.top + (row.height - dot) / 2, dot, dot, dot / 2, ACCENT);
        }

        int right = row.right() - layout.pad;
        int blockHeight = layout.bodyHeight + layout.textGap + layout.captionHeight;
        int top = row.top + (row.height - blockHeight) / 2;
        Component time = Component.literal(formatTime(notification.time()));
        int timeWidth = layout.width(time, layout.captionScale);
        drawText(guiGraphics, layout, time, right - timeWidth, top + Math.max(0, layout.bodyHeight - layout.captionHeight),
                TEXT_TERTIARY, layout.captionScale);

        String titleLine = notification.source().isBlank()
                ? notification.title()
                : "[" + notification.source() + "] " + notification.title();
        drawText(guiGraphics, layout,
                fit(layout, titleLine, notification.read() ? PLAIN : BOLD, right - timeWidth - layout.gap - textLeft, layout.bodyScale),
                textLeft, top, TEXT, layout.bodyScale);
        drawText(guiGraphics, layout, fit(layout, notification.body(), PLAIN, right - textLeft, layout.captionScale),
                textLeft, top + layout.bodyHeight + layout.textGap, TEXT_SECONDARY, layout.captionScale);
    }

    private static void renderFooter(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, boolean enabled) {
        UiRect footer = footer(layout);
        guiGraphics.fill(footer.left, footer.top, footer.right(), footer.bottom(), SURFACE);
        guiGraphics.fill(footer.left, footer.top, footer.right(), footer.top + 1, HAIRLINE);
        renderButton(screen, guiGraphics, layout, markAllButton(layout),
                Component.translatable("screen.bphone.phone.notifications.mark_all_read"),
                enabled ? Tone.SECONDARY : Tone.DISABLED);
        renderButton(screen, guiGraphics, layout, clearButton(layout),
                Component.translatable("screen.bphone.phone.notifications.clear_all"),
                enabled ? Tone.DANGER : Tone.DISABLED);
    }

    private static void renderDetail(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                     PhoneNotification notification) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.notifications.detail"), true, false);
        UiRect surface = layout.surface;
        int left = surface.left + layout.pad;
        int width = surface.width - 2 * layout.pad;
        int top = layout.header().bottom() + layout.sectionGap;
        int bottom = surface.bottom() - layout.pad;
        guiGraphics.fill(surface.left, top, surface.right(), bottom, SURFACE);

        int y = top + layout.u(8);
        String meta = notification.source().isBlank()
                ? formatTime(notification.time())
                : notification.source() + "  " + formatTime(notification.time());
        drawText(guiGraphics, layout, fit(layout, meta, PLAIN, width, layout.captionScale), left, y, TEXT_TERTIARY,
                layout.captionScale);
        y += layout.captionHeight + layout.u(6);
        y = drawWrapped(guiGraphics, layout, Component.literal(notification.title()).withStyle(BOLD), left, y, width,
                TEXT, layout.bodyScale);
        y += layout.u(6);
        guiGraphics.fill(left, y, left + width, y + 1, HAIRLINE);
        y += layout.u(6);
        guiGraphics.enableScissor(surface.left, top, surface.right(), bottom);
        drawWrapped(guiGraphics, layout, Component.literal(notification.body()), left, y, width, TEXT_SECONDARY,
                layout.bodyScale);
        guiGraphics.disableScissor();
    }

    private static String formatTime(long time) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(time));
    }
}
