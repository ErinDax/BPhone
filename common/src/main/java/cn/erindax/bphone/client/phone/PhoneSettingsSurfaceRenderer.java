package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhoneSettingsData;
import cn.erindax.bphone.phone.PhoneWallpaperData;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneSettingsSurfaceRenderer {
    static final int PAGE_MAIN = 0;
    static final int PAGE_LOCK_WALLPAPER = 1;
    static final int PAGE_HOME_WALLPAPER = 2;
    static final int PAGE_ABOUT = 3;
    static final int PAGE_RINGTONE = 4;
    static final int PAGE_DISPLAY = 5;
    static final int PAGE_SOUND = 6;
    static final int PAGE_CHAT = 7;

    private static final int[] SECTIONS = {PAGE_DISPLAY, PAGE_SOUND, PAGE_CHAT, PAGE_ABOUT};

    private static final int ROW_LOCK_WALLPAPER = 1;
    private static final int ROW_HOME_WALLPAPER = 2;
    private static final int ROW_DARK_MODE = 3;
    private static final int ROW_RINGTONE = 4;
    private static final int ROW_SILENT = 5;
    private static final int ROW_MESSAGE_POPUP = 6;
    private static final int ROW_PUBLIC_KEY = 7;
    private static final int ROW_CLOSE_AFTER_SEND = 8;
    private static final int ROW_HIDE_VANILLA_CHAT = 9;
    private static final int ROW_NUMBER = 10;
    private static final int ROW_MODEL = 11;
    private static final int ROW_PHOTOS = 12;
    private static final int ROW_VERSION = 13;
    private static final int ROW_PANEL_SIDE = 14;
    private static final int ROW_PANEL_OPACITY = 15;
    private static final int ROW_WORLD_NAV = 16;
    private static final int ROW_MENTION_ALERT = 17;
    private static final int ROW_CHAT_HUD_MODE = 18;
    private static final int ROW_HUD_COUNT = 19;
    private static final int ROW_HUD_FADE = 20;
    private static final int ROW_HUD_OPACITY = 21;
    private static final int GROUP_WALLPAPER = 101;
    private static final int GROUP_APPEARANCE = 102;
    private static final int GROUP_CALLS = 103;
    private static final int GROUP_MESSAGES = 104;
    private static final int GROUP_PUBLIC = 105;
    private static final int GROUP_VANILLA = 106;
    private static final int GROUP_DEVICE = 107;
    private static final int GROUP_NAVIGATION = 108;
    private static final int NOTE_CHAT = 201;

    private static final int[] DISPLAY_ENTRIES = {
            GROUP_WALLPAPER, ROW_LOCK_WALLPAPER, ROW_HOME_WALLPAPER,
            GROUP_APPEARANCE, ROW_DARK_MODE, ROW_PANEL_SIDE, ROW_PANEL_OPACITY,
            GROUP_NAVIGATION, ROW_WORLD_NAV
    };
    private static final int[] SOUND_ENTRIES = {
            GROUP_CALLS, ROW_RINGTONE, ROW_SILENT, GROUP_MESSAGES, ROW_MESSAGE_POPUP
    };
    private static final int[] CHAT_ENTRIES_MODERN_HUD = {
            GROUP_PUBLIC, ROW_PUBLIC_KEY, ROW_CLOSE_AFTER_SEND, ROW_MENTION_ALERT,
            GROUP_VANILLA, ROW_CHAT_HUD_MODE, ROW_HUD_COUNT, ROW_HUD_FADE, ROW_HUD_OPACITY, NOTE_CHAT
    };
    private static final int[] CHAT_ENTRIES_SIMPLE_HUD = {
            GROUP_PUBLIC, ROW_PUBLIC_KEY, ROW_CLOSE_AFTER_SEND, ROW_MENTION_ALERT,
            GROUP_VANILLA, ROW_CHAT_HUD_MODE, NOTE_CHAT
    };
    private static final int[] ABOUT_ENTRIES = {
            GROUP_DEVICE, ROW_NUMBER, ROW_MODEL, ROW_PHOTOS, ROW_VERSION
    };
    private static final int[] NO_ENTRIES = {};

    private static final int TYPE_GROUP = 0;
    private static final int TYPE_ROW = 1;
    private static final int TYPE_NOTE = 2;

    private static final int CONTROL_NAVIGATION = 0;
    private static final int CONTROL_TOGGLE = 1;
    private static final int CONTROL_VALUE = 2;
    private static final int CONTROL_CYCLE = 3;

    private static final int MENU_LOCK = 0;
    private static final int MENU_HOME = 1;
    private static final int MENU_BOTH = 2;
    private static final int MENU_CANCEL = 3;
    private static final int MENU_COUNT = 4;

    private static final int PICKER_COLUMNS = 3;
    private static final int IMPORT_CELL = 0;
    private static final int FIRST_OPTION_CELL = 1;

    private record Item(int type, int id, UiRect rect, boolean first, boolean last) {
    }

    private PhoneSettingsSurfaceRenderer() {
    }

    static void renderSettingsSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);
        int page = screen.settingsWallpaperPage;
        switch (page) {
            case PAGE_LOCK_WALLPAPER -> renderWallpaperPicker(screen, guiGraphics, layout, true);
            case PAGE_HOME_WALLPAPER -> renderWallpaperPicker(screen, guiGraphics, layout, false);
            case PAGE_RINGTONE -> renderRingtones(screen, guiGraphics, layout);
            case PAGE_MAIN -> renderHome(screen, guiGraphics, layout);
            default -> renderSectionPage(screen, guiGraphics, layout, page);
        }
    }

    static void back(PhoneScreen screen) {
        if (screen.isPublicChatOnly()
                && screen.settingsWallpaperPage != PAGE_LOCK_WALLPAPER
                && screen.settingsWallpaperPage != PAGE_HOME_WALLPAPER
                && screen.settingsWallpaperPage != PAGE_RINGTONE) {
            screen.closeSettingsApp();
            return;
        }
        switch (screen.settingsWallpaperPage) {
            case PAGE_MAIN -> screen.closeSettingsApp();
            case PAGE_LOCK_WALLPAPER, PAGE_HOME_WALLPAPER -> screen.openSettingsPage(PAGE_DISPLAY);
            case PAGE_RINGTONE -> screen.openSettingsPage(PAGE_SOUND);
            default -> screen.openSettingsPage(PAGE_MAIN);
        }
    }

    static boolean handleClick(PhoneScreen screen, double mouseX, double mouseY) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int page = screen.settingsWallpaperPage;
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            back(screen);
            return true;
        }

        if (isWallpaperPage(page)) {
            List<String> options = wallpaperOptions(screen);
            UiRect viewport = pickerViewport(layout);
            if (!viewport.contains(mouseX, mouseY)) {
                return true;
            }
            if (pickerCell(layout, IMPORT_CELL, screen.settingsPickerScroll).contains(mouseX, mouseY)) {
                importWallpaper(screen, PhoneWallpaperImport.dialog(
                        Component.translatable("screen.bphone.phone.settings.wallpaper.import.dialog").getString(),
                        Component.translatable("screen.bphone.phone.settings.wallpaper.import.filter").getString()));
                return true;
            }
            for (int index = 0; index < options.size(); index++) {
                if (pickerCell(layout, FIRST_OPTION_CELL + index, screen.settingsPickerScroll).contains(mouseX, mouseY)) {
                    screen.applyPhoneSetting(wallpaperSettingKey(page), options.get(index));
                    screen.openSettingsPage(PAGE_DISPLAY);
                    return true;
                }
            }
            return true;
        }

        if (page == PAGE_RINGTONE) {
            List<String> ringtones = PhoneSettingsData.RINGTONES;
            for (int index = 0; index < ringtones.size(); index++) {
                if (ringtoneRow(layout, index).contains(mouseX, mouseY)) {
                    screen.applyPhoneSetting(PhoneSettingsData.KEY_RINGTONE, ringtones.get(index));
                    PhoneRingtones.preview(ringtones.get(index));
                    return true;
                }
            }
            return true;
        }

        if (page == PAGE_MAIN) {
            for (int index = 0; index < SECTIONS.length; index++) {
                if (homeCard(layout, index).contains(mouseX, mouseY)) {
                    screen.openSettingsPage(SECTIONS[index]);
                    return true;
                }
            }
            return true;
        }

        for (int index = 0; index < SECTIONS.length; index++) {
            if (chip(layout, index).contains(mouseX, mouseY)) {
                if (SECTIONS[index] != page) {
                    screen.openSettingsPage(SECTIONS[index]);
                }
                return true;
            }
        }
        if (!sectionViewport(layout).contains(mouseX, mouseY)) {
            return true;
        }
        for (Item item : items(screen, layout, page, screen.settingsPickerScroll)) {
            if (item.type() == TYPE_ROW && item.rect().contains(mouseX, mouseY)) {
                activate(screen, item.id());
                return true;
            }
        }
        return true;
    }

    static void scroll(PhoneScreen screen, double scrollY) {
        int page = screen.settingsWallpaperPage;
        if (page == PAGE_MAIN || page == PAGE_RINGTONE) {
            return;
        }
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        int limit = isWallpaperPage(page)
                ? pickerMaxScroll(layout, wallpaperOptions(screen).size())
                : sectionMaxScroll(screen, layout, page);
        screen.settingsPickerScroll = Mth.clamp(screen.settingsPickerScroll + delta, 0, limit);
    }

    static void handleFilesDrop(PhoneScreen screen, List<Path> paths) {
        if (!isWallpaperPage(screen.settingsWallpaperPage) || paths.isEmpty()) {
            return;
        }
        Path file = paths.get(0);
        importWallpaper(screen, () -> file);
    }

    static void renderWallpaperMenu(PhoneScreen screen, GuiGraphics guiGraphics) {
        if (!screen.wallpaperContextMenuOpen) {
            return;
        }

        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), 0x88000000);
        UiRect sheet = menuSheet(layout);
        fillRounded(guiGraphics, sheet.left, sheet.top, sheet.width, sheet.height, Math.max(2, layout.radius * 2), PAGE);
        Component[] labels = {
                Component.translatable("screen.bphone.phone.gallery.set_lock_wallpaper"),
                Component.translatable("screen.bphone.phone.gallery.set_home_wallpaper"),
                Component.translatable("screen.bphone.phone.gallery.set_both_wallpaper"),
                Component.translatable("screen.bphone.phone.bank.cancel")
        };
        for (int index = 0; index < MENU_COUNT; index++) {
            UiRect option = menuOption(layout, index);
            fillRounded(guiGraphics, option.left, option.top, option.width, option.height, layout.radius,
                    hovered(screen, option) ? HOVER : SURFACE);
            Component label = fit(layout, labels[index].getString(), index == MENU_CANCEL ? PLAIN : BOLD,
                    option.width - 2 * layout.pad, layout.bodyScale);
            drawCentered(guiGraphics, layout, label, option.left + option.width / 2,
                    option.top + (option.height - layout.bodyHeight) / 2,
                    index == MENU_CANCEL ? TEXT_SECONDARY : ACCENT, layout.bodyScale);
        }
    }

    static boolean handleWallpaperMenuClick(PhoneScreen screen, double mouseX, double mouseY) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        for (int index = 0; index < MENU_COUNT; index++) {
            if (!menuOption(layout, index).contains(mouseX, mouseY)) {
                continue;
            }
            switch (index) {
                case MENU_LOCK -> screen.applyWallpaperFromContextPhoto(true, false);
                case MENU_HOME -> screen.applyWallpaperFromContextPhoto(false, true);
                case MENU_BOTH -> screen.applyWallpaperFromContextPhoto(true, true);
                default -> {
                }
            }
            screen.closeWallpaperContextMenu();
            return true;
        }

        if (!menuSheet(layout).contains(mouseX, mouseY)) {
            screen.closeWallpaperContextMenu();
        }
        return true;
    }

    private static void renderHome(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.title.settings"), true, false);
        for (int index = 0; index < SECTIONS.length; index++) {
            UiRect card = homeCard(layout, index);
            int background = hovered(screen, card) ? ACCENT_SOFT : SURFACE;
            fillRounded(guiGraphics, card.left, card.top, card.width, card.height, cardRadius(layout), background);
            int icon = even(Math.max(layout.u(18), card.width / 3));
            int top = card.top + (card.height - icon - layout.gap - layout.bodyHeight) / 2;
            renderSectionIcon(guiGraphics, SECTIONS[index],
                    new UiRect(card.left + (card.width - icon) / 2, top, icon, icon), ACCENT, background);
            Component label = fit(layout, Component.translatable(sectionKey(SECTIONS[index])).getString(), BOLD,
                    card.width - 2 * layout.u(3), layout.bodyScale);
            drawCentered(guiGraphics, layout, label, card.left + card.width / 2, top + icon + layout.gap, TEXT,
                    layout.bodyScale);
        }
    }

    private static void renderSectionPage(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                          int page) {
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable(sectionKey(page)), true,
                false);
        for (int index = 0; index < SECTIONS.length; index++) {
            UiRect chip = chip(layout, index);
            boolean selected = SECTIONS[index] == page;
            if (selected) {
                fillRounded(guiGraphics, chip.left, chip.top, chip.width, chip.height, chip.height / 2, ACCENT);
            } else if (hovered(screen, chip)) {
                fillRounded(guiGraphics, chip.left, chip.top, chip.width, chip.height, chip.height / 2, SECONDARY_HOVER);
            }
            Component label = fit(layout, Component.translatable(tabKey(SECTIONS[index])).getString(),
                    selected ? BOLD : PLAIN, chip.width - 2 * layout.u(2), layout.bodyScale);
            drawCentered(guiGraphics, layout, label, chip.left + chip.width / 2,
                    chip.top + (chip.height - layout.bodyHeight) / 2, selected ? WHITE : TEXT_SECONDARY,
                    layout.bodyScale);
        }

        UiRect viewport = sectionViewport(layout);
        screen.settingsPickerScroll = Mth.clamp(screen.settingsPickerScroll, 0, sectionMaxScroll(screen, layout, page));
        boolean pointerInside = hovered(screen, viewport);
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (Item item : items(screen, layout, page, screen.settingsPickerScroll)) {
            UiRect rect = item.rect();
            if (rect.bottom() <= viewport.top || rect.top >= viewport.bottom()) {
                continue;
            }
            switch (item.type()) {
                case TYPE_GROUP -> renderGroup(guiGraphics, layout, rect, item.id());
                case TYPE_NOTE -> drawWrapped(guiGraphics, layout, Component.translatable(key(item.id())),
                        rect.left + layout.u(2), rect.top, rect.width - 2 * layout.u(2), TEXT_TERTIARY,
                        layout.captionScale);
                default -> renderRow(screen, guiGraphics, layout, item, pointerInside);
            }
        }
        guiGraphics.disableScissor();
    }

    private static void renderGroup(GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect rect, int group) {
        Component title = fit(layout, Component.translatable(key(group)).getString(), BOLD,
                rect.width - 2 * layout.u(2), layout.captionScale);
        drawText(guiGraphics, layout, title, rect.left + layout.u(2),
                rect.top + (rect.height - 1 - layout.captionHeight) / 2, TEXT_SECONDARY, layout.captionScale);
        guiGraphics.fill(rect.left, rect.bottom() - 1, rect.right(), rect.bottom(),
                (TEXT_TERTIARY & 0x00FFFFFF) | 0x66000000);
    }

    private static void renderRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, Item item,
                                  boolean pointerInside) {
        UiRect rect = item.rect();
        int row = item.id();
        int control = control(row);
        boolean hover = control != CONTROL_VALUE && pointerInside && hovered(screen, rect);
        int radius = Math.min(cardRadius(layout), rect.height / 2);
        int background = hover ? HOVER : SURFACE;
        fillRounded(guiGraphics, rect.left, rect.top, rect.width, rect.height, radius, background);
        if (!item.first()) {
            guiGraphics.fill(rect.left, rect.top, rect.right(), rect.top + radius, background);
        }
        if (!item.last()) {
            guiGraphics.fill(rect.left, rect.bottom() - radius, rect.right(), rect.bottom(), background);
        }

        int left = rect.left + layout.pad;
        int right = rect.right() - layout.pad;
        int inner = right - left;
        int top = rect.top + rowPad(layout);
        Component title = title(row);
        int titleWidth = titleWidth(screen, layout, row, inner);
        int titleHeight = wrappedHeight(layout, title, titleWidth, layout.bodyScale);
        Component value = valueText(screen, layout, row, inner);
        int valueWidth = layout.width(value, layout.bodyScale);
        if (control == CONTROL_VALUE) {
            int head = Math.max(titleHeight, layout.bodyHeight);
            drawWrapped(guiGraphics, layout, title, left, top + (head - titleHeight) / 2, titleWidth, TEXT,
                    layout.bodyScale);
            drawText(guiGraphics, layout, value, right - valueWidth, top + (head - layout.bodyHeight) / 2,
                    TEXT_SECONDARY, layout.bodyScale);
        } else {
            int descWidth = descWidth(screen, layout, row, inner);
            int descHeight = wrappedHeight(layout, description(row), descWidth, layout.captionScale);
            int textBlockH = titleHeight + layout.textGap + descHeight;
            int textTop = rect.top + Math.max(rowPad(layout), (rect.height - textBlockH) / 2);
            drawWrapped(guiGraphics, layout, title, left, textTop, titleWidth, TEXT, layout.bodyScale);
            drawWrapped(guiGraphics, layout, description(row), left, textTop + titleHeight + layout.textGap, descWidth,
                    TEXT_SECONDARY, layout.captionScale);

            int centerY = rect.top + rect.height / 2;
            if (control == CONTROL_TOGGLE) {
                int swH = switchHeight(layout);
                renderSwitch(guiGraphics, layout, right - switchWidth(layout), centerY - swH / 2, enabled(screen, row));
            } else if (control == CONTROL_CYCLE) {
                int chipPadX = layout.u(4);
                int chipH = layout.captionHeight + 2 * layout.u(2.5F);
                Component chipText = fit(layout, value(screen, row), BOLD, Math.max(layout.u(28), inner * 2 / 5), layout.captionScale);
                int chipW = layout.width(chipText, layout.captionScale) + 2 * chipPadX;
                int chipLeft = right - chipW;
                int chipTop = centerY - chipH / 2;
                fillRounded(guiGraphics, chipLeft, chipTop, chipW, chipH, chipH / 2, ACCENT_SOFT);
                drawCentered(guiGraphics, layout, chipText, chipLeft + chipW / 2,
                        chipTop + (chipH - layout.captionHeight) / 2, ACCENT, layout.captionScale);
            } else {
                renderArrow(guiGraphics, layout, right, centerY);
                Component navVal = fit(layout, value(screen, row), PLAIN,
                        Math.max(layout.u(24), inner / 3), layout.captionScale);
                int navW = layout.width(navVal, layout.captionScale);
                drawText(guiGraphics, layout, navVal, right - layout.chevronReserve() - navW,
                        centerY - layout.captionHeight / 2, TEXT_SECONDARY, layout.captionScale);
            }
        }
        if (!item.last()) {
            guiGraphics.fill(left, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
        }
    }

    private static void renderSwitch(GuiGraphics guiGraphics, PhoneChatLayout layout, int left, int top,
                                     boolean enabled) {
        int height = switchHeight(layout);
        int width = switchWidth(layout);
        fillRounded(guiGraphics, left, top, width, height, height / 2, enabled ? ACCENT : SECONDARY_HOVER);
        int knob = height - 2;
        fillRounded(guiGraphics, enabled ? left + width - knob - 1 : left + 1, top + 1, knob, knob, knob / 2, WHITE);
    }

    private static void renderArrow(GuiGraphics guiGraphics, PhoneChatLayout layout, int right, int centerY) {
        int density = pixelDensity();
        float arm = Math.max(2.0F, layout.chevronArm()) * density;
        float stroke = Math.max(1.2F, density * 0.75F);
        float rx = (right - 1) * density;
        float cy = centerY * density;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        drawSmoothSegment(guiGraphics, rx - arm, cy - arm, rx, cy, stroke, TEXT_TERTIARY);
        drawSmoothSegment(guiGraphics, rx, cy, rx - arm, cy + arm, stroke, TEXT_TERTIARY);
        guiGraphics.pose().popPose();
    }

    private static void renderSectionIcon(GuiGraphics guiGraphics, int page, UiRect box, int color,
                                          int background) {
        switch (page) {
            case PAGE_DISPLAY -> renderSlidersIcon(guiGraphics, box, color, background);
            case PAGE_SOUND -> renderBellIcon(guiGraphics, box, color);
            case PAGE_CHAT -> renderBubbleIcon(guiGraphics, box, color, background);
            default -> renderInfoIcon(guiGraphics, box, color, background);
        }
    }

    private static void renderSlidersIcon(GuiGraphics guiGraphics, UiRect box, int color, int background) {
        int stroke = Math.max(2, box.width / 11);
        int knob = even(stroke * 3);
        int halo = knob + 2 * Math.max(1, stroke / 2);
        int[] centers = {
                box.left + box.width * 2 / 3, box.left + box.width / 3, box.left + box.width * 3 / 5
        };
        for (int line = 0; line < 3; line++) {
            int centerY = box.top + box.height * (1 + 2 * line) / 6;
            guiGraphics.fill(box.left, centerY - stroke / 2, box.right(), centerY - stroke / 2 + stroke, color);
            fillRounded(guiGraphics, centers[line] - halo / 2, centerY - halo / 2, halo, halo, halo / 2, background);
            fillRounded(guiGraphics, centers[line] - knob / 2, centerY - knob / 2, knob, knob, knob / 2, color);
        }
    }

    private static void renderBellIcon(GuiGraphics guiGraphics, UiRect box, int color) {
        int knob = even(Math.max(2, box.width / 8));
        int clapper = even(Math.max(2, box.width / 6));
        int brim = Math.max(1, box.height / 10);
        int flare = Math.max(1, box.width / 10);
        int width = even(Math.round(box.width * 0.62F));
        int centerX = box.left + box.width / 2;
        int left = centerX - width / 2;
        int top = box.top + knob / 2;
        int bottom = box.bottom() - clapper / 2 - 1;
        fillRounded(guiGraphics, centerX - knob / 2, box.top, knob, knob, knob / 2, color);
        fillRounded(guiGraphics, left, top, width, bottom - top, width / 2, color);
        guiGraphics.fill(left, top + width / 2, left + width, bottom, color);
        guiGraphics.fill(left - flare, bottom - brim, left + width + flare, bottom, color);
        fillRounded(guiGraphics, centerX - clapper / 2, bottom + 1, clapper, clapper / 2, clapper / 4, color);
    }

    private static void renderBubbleIcon(GuiGraphics guiGraphics, UiRect box, int color, int background) {
        int tail = Math.max(2, box.height / 6);
        int height = even(Math.round(box.height * 0.72F));
        int top = box.top + (box.height - height - tail) / 2;
        fillRounded(guiGraphics, box.left, top, box.width, height, Math.max(2, box.width / 4), color);
        int tailLeft = box.left + box.width / 5;
        for (int row = 0; row < tail; row++) {
            guiGraphics.fill(tailLeft, top + height + row, tailLeft + 2 * (tail - row) - 1, top + height + row + 1,
                    color);
        }
        int dot = even(Math.max(2, box.width / 9));
        int dotTop = top + (height - dot) / 2;
        int dotLeft = box.left + (box.width - dot) / 2;
        for (int index = -1; index <= 1; index++) {
            fillRounded(guiGraphics, dotLeft + index * 2 * dot, dotTop, dot, dot, dot / 2, background);
        }
    }

    private static void renderInfoIcon(GuiGraphics guiGraphics, UiRect box, int color, int background) {
        fillRounded(guiGraphics, box.left, box.top, box.width, box.height, box.width / 2, color);
        int stem = even(Math.max(2, box.width / 9));
        int left = box.left + (box.width - stem) / 2;
        fillRounded(guiGraphics, left, box.top + Math.round(box.height * 0.22F), stem, stem, stem / 2, background);
        guiGraphics.fill(left, box.top + Math.round(box.height * 0.42F), left + stem,
                box.top + Math.round(box.height * 0.78F), background);
    }

    private static void renderWallpaperPicker(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                              boolean lock) {
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.translatable(lock
                ? "screen.bphone.phone.settings.lock_wallpaper"
                : "screen.bphone.phone.settings.home_wallpaper"), true, false);
        ItemStack phoneStack = screen.getSettingsPhoneStack();
        String current = lock ? PhoneWallpaperData.getLockWallpaper(phoneStack) : PhoneWallpaperData.getHomeWallpaper(phoneStack);
        List<String> options = wallpaperOptions(screen);
        UiRect viewport = pickerViewport(layout);
        screen.settingsPickerScroll = Mth.clamp(screen.settingsPickerScroll, 0, pickerMaxScroll(layout, options.size()));
        int border = Math.max(1, layout.u(2));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        renderImportCell(screen, guiGraphics, layout, pickerCell(layout, IMPORT_CELL, screen.settingsPickerScroll), border);
        for (int index = 0; index < options.size(); index++) {
            UiRect cell = pickerCell(layout, FIRST_OPTION_CELL + index, screen.settingsPickerScroll);
            if (cell.bottom() <= viewport.top || cell.top >= viewport.bottom()) {
                continue;
            }
            String option = options.get(index);
            if (option.equals(current)) {
                guiGraphics.fill(cell.left - border, cell.top - border, cell.right() + border, cell.bottom() + border, ACCENT);
            } else if (hovered(screen, cell)) {
                guiGraphics.fill(cell.left - border, cell.top - border, cell.right() + border, cell.bottom() + border, TEXT_TERTIARY);
            }
            PhoneWallpaperRenderer.drawWallpaper(screen, guiGraphics, option, cell, true);
        }
        drawWrapped(guiGraphics, layout, importHint(), viewport.left + layout.pad,
                pickerHintTop(layout, options.size(), screen.settingsPickerScroll), pickerHintWidth(layout),
                TEXT_TERTIARY, layout.captionScale);
        guiGraphics.disableScissor();
    }

    private static void renderImportCell(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                         UiRect cell, int border) {
        boolean busy = PhoneWallpaperImport.isBusy();
        if (!busy && hovered(screen, cell)) {
            guiGraphics.fill(cell.left - border, cell.top - border, cell.right() + border, cell.bottom() + border, TEXT_TERTIARY);
        }
        guiGraphics.fill(cell.left, cell.top, cell.right(), cell.bottom(), SURFACE);

        int arm = Math.max(3, cell.width / 6);
        int stroke = Math.max(1, layout.u(1.5F));
        int side = 2 * arm + stroke;
        int labelGap = layout.u(5);
        int left = cell.left + (cell.width - side) / 2;
        int top = cell.top + (cell.height - side - labelGap - layout.captionHeight) / 2;
        int color = busy ? TEXT_TERTIARY : ACCENT;
        guiGraphics.fill(left, top + arm, left + side, top + arm + stroke, color);
        guiGraphics.fill(left + arm, top, left + arm + stroke, top + side, color);
        Component label = fit(layout, Component.translatable(busy
                ? "screen.bphone.phone.settings.wallpaper.import.pending"
                : "screen.bphone.phone.settings.wallpaper.import").getString(), PLAIN,
                cell.width - 2 * layout.u(2), layout.captionScale);
        drawCentered(guiGraphics, layout, label, cell.left + cell.width / 2, top + side + labelGap, color,
                layout.captionScale);
    }

    private static void renderRingtones(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.settings.ringtone"), true, false);
        String current = PhoneSettingsData.getRingtone(screen.getSettingsPhoneStack());
        List<String> ringtones = PhoneSettingsData.RINGTONES;
        for (int index = 0; index < ringtones.size(); index++) {
            UiRect row = ringtoneRow(layout, index);
            String id = ringtones.get(index);
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), hovered(screen, row) ? HOVER : SURFACE);
            UiRect box = layout.checkBox(row);
            boolean selected = id.equals(current);
            if (selected) {
                renderCheck(guiGraphics, layout, box, true, true);
            }
            drawText(guiGraphics, layout, fit(layout, PhoneRingtones.name(id).getString(), selected ? BOLD : PLAIN,
                            box.left - layout.gap - row.left - layout.pad, layout.bodyScale),
                    row.left + layout.pad, row.top + (row.height - layout.bodyHeight) / 2, selected ? ACCENT : TEXT,
                    layout.bodyScale);
            if (index < ringtones.size() - 1) {
                guiGraphics.fill(row.left + layout.pad, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
        }
        UiRect last = ringtoneRow(layout, ringtones.size() - 1);
        drawWrapped(guiGraphics, layout, Component.translatable("screen.bphone.phone.settings.ringtone.hint"),
                layout.surface.left + layout.pad, last.bottom() + layout.u(6), layout.surface.width - 2 * layout.pad,
                TEXT_TERTIARY, layout.captionScale);
    }

    private static UiRect ringtoneRow(PhoneChatLayout layout, int index) {
        int top = layout.header().bottom() + layout.sectionGap + index * layout.itemHeight;
        return new UiRect(layout.surface.left, top, layout.surface.width, layout.itemHeight);
    }

    private static UiRect homeCard(PhoneChatLayout layout, int index) {
        int gap = layout.gap + layout.u(2);
        int width = (layout.surface.width - 2 * layout.pad - gap) / 2;
        int height = Math.round(width * 0.9F);
        int left = layout.surface.left + layout.pad + (index % 2) * (width + gap);
        int top = layout.header().bottom() + layout.pad + (index / 2) * (height + gap);
        return new UiRect(left, top, width, height);
    }

    private static UiRect chip(PhoneChatLayout layout, int index) {
        int gap = layout.u(2);
        int available = layout.surface.width - 2 * layout.pad;
        int width = (available - (SECTIONS.length - 1) * gap) / SECTIONS.length;
        return new UiRect(layout.surface.left + layout.pad + index * (width + gap),
                layout.header().bottom() + layout.gap, width, layout.bodyHeight + 2 * layout.u(4));
    }

    private static UiRect sectionViewport(PhoneChatLayout layout) {
        int top = chip(layout, 0).bottom() + layout.gap;
        return new UiRect(layout.surface.left, top, layout.surface.width, Math.max(1, layout.surface.bottom() - top));
    }

    private static List<Item> items(PhoneScreen screen, PhoneChatLayout layout, int page, int scroll) {
        int[] entries = entries(page);
        List<Item> items = new ArrayList<>(entries.length);
        UiRect viewport = sectionViewport(layout);
        int left = viewport.left + layout.pad;
        int width = viewport.width - 2 * layout.pad;
        int y = viewport.top - scroll;
        for (int index = 0; index < entries.length; index++) {
            int id = entries[index];
            if (isRow(id)) {
                int height = rowHeight(screen, layout, id, width - 2 * layout.pad);
                boolean first = index == 0 || !isRow(entries[index - 1]);
                boolean last = index == entries.length - 1 || !isRow(entries[index + 1]);
                items.add(new Item(TYPE_ROW, id, new UiRect(left, y, width, height), first, last));
                y += height;
            } else if (id == NOTE_CHAT) {
                y += layout.gap;
                int height = wrappedHeight(layout, Component.translatable(key(id)), width - 2 * layout.u(2),
                        layout.captionScale);
                items.add(new Item(TYPE_NOTE, id, new UiRect(left, y, width, height), false, false));
                y += height;
            } else {
                if (index > 0) {
                    y += layout.gap;
                }
                items.add(new Item(TYPE_GROUP, id, new UiRect(left, y, width, layout.sectionHeight), false, false));
                y += layout.sectionHeight + layout.u(3);
            }
        }
        return items;
    }

    private static int sectionMaxScroll(PhoneScreen screen, PhoneChatLayout layout, int page) {
        List<Item> items = items(screen, layout, page, 0);
        if (items.isEmpty()) {
            return 0;
        }
        UiRect viewport = sectionViewport(layout);
        return PhoneChatLayout.maxScroll(viewport,
                items.get(items.size() - 1).rect().bottom() + layout.pad - viewport.top);
    }

    private static int rowHeight(PhoneScreen screen, PhoneChatLayout layout, int row, int inner) {
        int titleHeight = wrappedHeight(layout, title(row), titleWidth(screen, layout, row, inner), layout.bodyScale);
        if (control(row) == CONTROL_VALUE) {
            return 2 * rowPad(layout) + Math.max(titleHeight, layout.bodyHeight);
        }
        int descWidth = descWidth(screen, layout, row, inner);
        int textStack = titleHeight + layout.textGap + wrappedHeight(layout, description(row), descWidth, layout.captionScale);
        return 2 * rowPad(layout) + Math.max(textStack, switchHeight(layout) + layout.u(2));
    }

    private static int controlReserve(PhoneScreen screen, PhoneChatLayout layout, int row, int inner) {
        return switch (control(row)) {
            case CONTROL_TOGGLE -> switchWidth(layout) + layout.gap;
            case CONTROL_CYCLE -> {
                Component chipText = fit(layout, value(screen, row), BOLD, Math.max(layout.u(28), inner * 2 / 5), layout.captionScale);
                yield layout.width(chipText, layout.captionScale) + 2 * layout.u(4) + layout.gap;
            }
            case CONTROL_NAVIGATION -> {
                Component navVal = fit(layout, value(screen, row), PLAIN, Math.max(layout.u(24), inner / 3), layout.captionScale);
                yield layout.width(navVal, layout.captionScale) + layout.chevronReserve() + layout.gap;
            }
            default -> layout.width(valueText(screen, layout, row, inner), layout.bodyScale) + layout.gap;
        };
    }

    private static int descWidth(PhoneScreen screen, PhoneChatLayout layout, int row, int inner) {
        return Math.max(layout.u(36), inner - controlReserve(screen, layout, row, inner));
    }

    private static int titleWidth(PhoneScreen screen, PhoneChatLayout layout, int row, int inner) {
        if (control(row) != CONTROL_VALUE) {
            return descWidth(screen, layout, row, inner);
        }
        return Math.max(layout.u(20),
                inner - layout.gap - layout.width(valueText(screen, layout, row, inner), layout.bodyScale));
    }

    private static Component valueText(PhoneScreen screen, PhoneChatLayout layout, int row, int inner) {
        int room = control(row) == CONTROL_VALUE
                ? Math.max(inner / 2, inner - layout.gap - layout.width(title(row), layout.bodyScale))
                : inner - layout.chevronReserve();
        return fit(layout, value(screen, row), PLAIN, room, layout.bodyScale);
    }

    private static int switchHeight(PhoneChatLayout layout) {
        return Math.max(layout.u(10), layout.bodyHeight + layout.u(2));
    }

    private static int switchWidth(PhoneChatLayout layout) {
        int height = switchHeight(layout);
        return Math.max(height + layout.u(8), Math.round(height * 1.8F));
    }

    private static int rowPad(PhoneChatLayout layout) {
        return layout.u(6);
    }

    private static int cardRadius(PhoneChatLayout layout) {
        return Math.max(2, layout.radius * 2);
    }

    private static int even(int value) {
        return value % 2 == 0 ? value : value + 1;
    }

    private static int[] entries(int page) {
        return switch (page) {
            case PAGE_DISPLAY -> DISPLAY_ENTRIES;
            case PAGE_SOUND -> SOUND_ENTRIES;
            case PAGE_CHAT -> PhoneClientPrefs.modernChatHud() ? CHAT_ENTRIES_MODERN_HUD : CHAT_ENTRIES_SIMPLE_HUD;
            case PAGE_ABOUT -> ABOUT_ENTRIES;
            default -> NO_ENTRIES;
        };
    }

    private static boolean isRow(int id) {
        return id < GROUP_WALLPAPER;
    }

    private static int control(int row) {
        return switch (row) {
            case ROW_LOCK_WALLPAPER, ROW_HOME_WALLPAPER, ROW_RINGTONE -> CONTROL_NAVIGATION;
            case ROW_NUMBER, ROW_MODEL, ROW_PHOTOS, ROW_VERSION -> CONTROL_VALUE;
            case ROW_PANEL_SIDE, ROW_PANEL_OPACITY, ROW_CHAT_HUD_MODE, ROW_HUD_COUNT, ROW_HUD_FADE, ROW_HUD_OPACITY -> CONTROL_CYCLE;
            default -> CONTROL_TOGGLE;
        };
    }

    private static String key(int id) {
        return "screen.bphone.phone.settings." + switch (id) {
            case ROW_LOCK_WALLPAPER -> "lock_wallpaper";
            case ROW_HOME_WALLPAPER -> "home_wallpaper";
            case ROW_DARK_MODE -> "dark_mode";
            case ROW_PANEL_SIDE -> "panel_side";
            case ROW_PANEL_OPACITY -> "panel_opacity";
            case ROW_WORLD_NAV -> "world_nav";
            case ROW_RINGTONE -> "ringtone";
            case ROW_SILENT -> "silent";
            case ROW_MESSAGE_POPUP -> "message_popup";
            case ROW_PUBLIC_KEY -> "public_key";
            case ROW_CLOSE_AFTER_SEND -> "close_after_send";
            case ROW_MENTION_ALERT -> "mention_alert";
            case ROW_HIDE_VANILLA_CHAT -> "hide_vanilla_chat";
            case ROW_CHAT_HUD_MODE -> "chat_hud_mode";
            case ROW_HUD_COUNT -> "hud_count";
            case ROW_HUD_FADE -> "hud_fade";
            case ROW_HUD_OPACITY -> "hud_opacity";
            case ROW_NUMBER -> "about.number";
            case ROW_MODEL -> "about.model";
            case ROW_PHOTOS -> "about.photos";
            case ROW_VERSION -> "about.version";
            case GROUP_WALLPAPER -> "group.wallpaper";
            case GROUP_APPEARANCE -> "group.appearance";
            case GROUP_NAVIGATION -> "group.navigation";
            case GROUP_CALLS -> "group.calls";
            case GROUP_MESSAGES -> "group.messages";
            case GROUP_PUBLIC -> "group.public";
            case GROUP_VANILLA -> "group.vanilla";
            case GROUP_DEVICE -> "group.device";
            default -> "chat.note";
        };
    }

    private static Component title(int row) {
        return Component.translatable(key(row)).withStyle(BOLD);
    }

    private static Component description(int row) {
        return Component.translatable(key(row) + ".desc");
    }

    private static String sectionKey(int page) {
        return switch (page) {
            case PAGE_DISPLAY -> "screen.bphone.phone.settings.section.display";
            case PAGE_SOUND -> "screen.bphone.phone.settings.section.sound";
            case PAGE_CHAT -> "screen.bphone.phone.settings.section.chat";
            default -> "screen.bphone.phone.settings.about";
        };
    }

    private static String tabKey(int page) {
        return switch (page) {
            case PAGE_DISPLAY -> "screen.bphone.phone.settings.tab.display";
            case PAGE_SOUND -> "screen.bphone.phone.settings.tab.sound";
            case PAGE_CHAT -> "screen.bphone.phone.settings.tab.chat";
            default -> "screen.bphone.phone.settings.tab.about";
        };
    }

    private static String value(PhoneScreen screen, int row) {
        ItemStack phoneStack = screen.getSettingsPhoneStack();
        return switch (row) {
            case ROW_LOCK_WALLPAPER -> wallpaperName(PhoneWallpaperData.getLockWallpaper(phoneStack)).getString();
            case ROW_HOME_WALLPAPER -> wallpaperName(PhoneWallpaperData.getHomeWallpaper(phoneStack)).getString();
            case ROW_PANEL_SIDE -> Component.translatable(PhoneClientPrefs.panelRightSide()
                    ? "screen.bphone.phone.settings.panel_side.right"
                    : "screen.bphone.phone.settings.panel_side.left").getString();
            case ROW_PANEL_OPACITY -> PhoneClientPrefs.panelOpacityPercent() + "%";
            case ROW_RINGTONE -> PhoneRingtones.name(PhoneSettingsData.getRingtone(phoneStack)).getString();
            case ROW_CHAT_HUD_MODE -> Component.translatable(switch (PhoneClientPrefs.chatHudMode()) {
                case PhoneClientPrefs.HUD_VANILLA -> "screen.bphone.phone.settings.chat_hud_mode.vanilla";
                case PhoneClientPrefs.HUD_HIDDEN -> "screen.bphone.phone.settings.chat_hud_mode.hidden";
                default -> "screen.bphone.phone.settings.chat_hud_mode.modern";
            }).getString();
            case ROW_HUD_COUNT -> Component.translatable("screen.bphone.phone.settings.hud_count.value",
                    PhoneClientPrefs.hudMaxMessages()).getString();
            case ROW_HUD_FADE -> PhoneClientPrefs.hudFadeSeconds() <= 0
                    ? Component.translatable("screen.bphone.phone.settings.hud_fade.always").getString()
                    : Component.translatable("screen.bphone.phone.settings.hud_fade.seconds",
                    PhoneClientPrefs.hudFadeSeconds()).getString();
            case ROW_HUD_OPACITY -> PhoneClientPrefs.hudOpacityPercent() + "%";
            case ROW_NUMBER -> screen.getOwnPhoneNumber();
            case ROW_MODEL -> phoneStack.isEmpty() ? "-" : phoneStack.getHoverName().getString();
            case ROW_PHOTOS -> screen.getPhotoCount() + " / " + screen.getMaxPhotoCount();
            case ROW_VERSION -> "BPhone " + Platform.getMod(BPhone.MOD_ID).getVersion();
            default -> "";
        };
    }

    private static boolean enabled(PhoneScreen screen, int row) {
        ItemStack phoneStack = screen.getSettingsPhoneStack();
        return switch (row) {
            case ROW_DARK_MODE -> PhoneSettingsData.isDarkMode(phoneStack);
            case ROW_WORLD_NAV -> PhoneClientPrefs.worldNavGuide();
            case ROW_SILENT -> PhoneSettingsData.isSilent(phoneStack);
            case ROW_MESSAGE_POPUP -> PhoneSettingsData.isMessagePopupEnabled(phoneStack);
            case ROW_PUBLIC_KEY -> PhoneClientPrefs.publicChatKey();
            case ROW_CLOSE_AFTER_SEND -> PhoneClientPrefs.closeAfterSend();
            case ROW_MENTION_ALERT -> PhoneClientPrefs.mentionAlert();
            case ROW_HIDE_VANILLA_CHAT -> PhoneClientPrefs.hideVanillaChat();
            default -> false;
        };
    }

    private static void activate(PhoneScreen screen, int row) {
        boolean flipped = !enabled(screen, row);
        switch (row) {
            case ROW_LOCK_WALLPAPER -> screen.openSettingsPage(PAGE_LOCK_WALLPAPER);
            case ROW_HOME_WALLPAPER -> screen.openSettingsPage(PAGE_HOME_WALLPAPER);
            case ROW_RINGTONE -> screen.openSettingsPage(PAGE_RINGTONE);
            case ROW_DARK_MODE -> screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE,
                    PhoneSettingsData.flagValue(flipped));
            case ROW_PANEL_SIDE -> {
                PhoneClientPrefs.setPanelRightSide(!PhoneClientPrefs.panelRightSide());
                screen.rebuildWidgets();
            }
            case ROW_PANEL_OPACITY -> PhoneClientPrefs.cyclePanelOpacityPercent();
            case ROW_WORLD_NAV -> PhoneClientPrefs.setWorldNavGuide(flipped);
            case ROW_SILENT -> screen.applyPhoneSetting(PhoneSettingsData.KEY_SILENT,
                    PhoneSettingsData.flagValue(flipped));
            case ROW_MESSAGE_POPUP -> screen.applyPhoneSetting(PhoneSettingsData.KEY_MESSAGE_POPUP,
                    PhoneSettingsData.flagValue(flipped));
            case ROW_PUBLIC_KEY -> PhoneClientPrefs.setPublicChatKey(flipped);
            case ROW_CLOSE_AFTER_SEND -> PhoneClientPrefs.setCloseAfterSend(flipped);
            case ROW_MENTION_ALERT -> PhoneClientPrefs.setMentionAlert(flipped);
            case ROW_HIDE_VANILLA_CHAT -> PhoneClientPrefs.setHideVanillaChat(flipped);
            case ROW_CHAT_HUD_MODE -> PhoneClientPrefs.cycleChatHudMode();
            case ROW_HUD_COUNT -> PhoneClientPrefs.cycleHudMaxMessages();
            case ROW_HUD_FADE -> PhoneClientPrefs.cycleHudFadeSeconds();
            case ROW_HUD_OPACITY -> PhoneClientPrefs.cycleHudOpacityPercent();
            default -> {
            }
        }
    }

    private static Component wallpaperName(String wallpaper) {
        if (PhoneWallpaperData.isPhoto(wallpaper)) {
            return Component.translatable("screen.bphone.phone.settings.wallpaper.photo");
        }
        String name = PhoneWallpaperData.getBuiltinName(wallpaper);
        return Component.translatable("screen.bphone.phone.settings.wallpaper.builtin."
                + (name == null || name.isEmpty() ? "default" : name));
    }

    private static List<String> wallpaperOptions(PhoneScreen screen) {
        List<String> options = new ArrayList<>();
        for (String builtin : PhoneWallpaperData.BUILTIN_WALLPAPERS) {
            options.add(PhoneWallpaperData.BUILTIN_PREFIX + builtin);
        }
        for (PhotoEntry photo : screen.getPhotos()) {
            if (photo.hasFile()) {
                options.add(PhoneWallpaperData.photoKey(photo.fileName));
            }
        }
        return options;
    }

    private static boolean isWallpaperPage(int page) {
        return page == PAGE_LOCK_WALLPAPER || page == PAGE_HOME_WALLPAPER;
    }

    private static String wallpaperSettingKey(int page) {
        return page == PAGE_LOCK_WALLPAPER ? PhoneSettingsData.KEY_LOCK_WALLPAPER : PhoneSettingsData.KEY_HOME_WALLPAPER;
    }

    private static void importWallpaper(PhoneScreen screen, Supplier<Path> source) {
        if (screen.isPhotoStorageFull()) {
            screen.showStatus("screen.bphone.phone.camera.storage_full");
            return;
        }
        int page = screen.settingsWallpaperPage;
        PhoneWallpaperImport.start(source, image -> applyImportedWallpaper(screen, page, image));
    }

    private static void applyImportedWallpaper(PhoneScreen screen, int page, Optional<byte[]> image) {
        if (Minecraft.getInstance().screen != screen || screen.settingsWallpaperPage != page) {
            return;
        }
        Optional<String> wallpaper = image.map(screen::importPhoto).map(PhoneWallpaperData::photoKey);
        if (wallpaper.isEmpty()) {
            screen.showStatus("screen.bphone.phone.settings.wallpaper.import.failed");
            return;
        }
        screen.applyPhoneSetting(wallpaperSettingKey(page), wallpaper.get());
        screen.openSettingsPage(PAGE_DISPLAY);
        screen.showStatus("screen.bphone.phone.gallery.wallpaper_set");
    }

    private static Component importHint() {
        return Component.translatable("screen.bphone.phone.settings.wallpaper.import.hint");
    }

    private static UiRect pickerViewport(PhoneChatLayout layout) {
        int top = layout.header().bottom();
        return new UiRect(layout.surface.left, top, layout.surface.width, Math.max(1, layout.surface.bottom() - top));
    }

    private static UiRect pickerCell(PhoneChatLayout layout, int index, int scroll) {
        UiRect viewport = pickerViewport(layout);
        int gap = layout.gap + layout.u(2);
        int cellWidth = Math.max(8, (viewport.width - 2 * layout.pad - (PICKER_COLUMNS - 1) * gap) / PICKER_COLUMNS);
        int cellHeight = Math.round(cellWidth * (float) PhoneScreen.DISPLAY_HEIGHT / PhoneScreen.DISPLAY_WIDTH);
        int column = index % PICKER_COLUMNS;
        int row = index / PICKER_COLUMNS;
        int left = viewport.left + layout.pad + column * (cellWidth + gap);
        int top = viewport.top + layout.pad + row * (cellHeight + gap) - scroll;
        return new UiRect(left, top, cellWidth, cellHeight);
    }

    private static int pickerMaxScroll(PhoneChatLayout layout, int optionCount) {
        UiRect viewport = pickerViewport(layout);
        int hintHeight = wrappedHeight(layout, importHint(), pickerHintWidth(layout), layout.captionScale);
        int contentHeight = pickerHintTop(layout, optionCount, 0) + hintHeight + layout.pad - viewport.top;
        return PhoneChatLayout.maxScroll(viewport, contentHeight);
    }

    private static int pickerHintTop(PhoneChatLayout layout, int optionCount, int scroll) {
        return pickerCell(layout, FIRST_OPTION_CELL + optionCount - 1, scroll).bottom() + layout.u(6);
    }

    private static int pickerHintWidth(PhoneChatLayout layout) {
        return layout.surface.width - 2 * layout.pad;
    }

    private static UiRect menuSheet(PhoneChatLayout layout) {
        UiRect surface = layout.surface;
        int height = 2 * layout.pad + MENU_COUNT * layout.itemHeight + (MENU_COUNT - 1) * layout.u(2) + layout.sectionGap;
        return new UiRect(surface.left, surface.bottom() - height, surface.width, height);
    }

    private static UiRect menuOption(PhoneChatLayout layout, int index) {
        UiRect sheet = menuSheet(layout);
        int top = sheet.top + layout.pad + index * (layout.itemHeight + layout.u(2))
                + (index == MENU_CANCEL ? layout.sectionGap : 0);
        return new UiRect(sheet.left + layout.pad, top, sheet.width - 2 * layout.pad, layout.itemHeight);
    }
}
