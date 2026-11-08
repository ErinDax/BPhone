package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhoneSettingsData;
import cn.erindax.bphone.phone.PhoneWallpaperData;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneSettingsSurfaceRenderer {
    static final int PAGE_MAIN = 0;
    static final int PAGE_LOCK_WALLPAPER = 1;
    static final int PAGE_HOME_WALLPAPER = 2;
    static final int PAGE_ABOUT = 3;
    static final int PAGE_RINGTONE = 4;

    private static final int ROW_SECTION_DISPLAY = 0;
    private static final int ROW_LOCK_WALLPAPER = 1;
    private static final int ROW_HOME_WALLPAPER = 2;
    private static final int ROW_SECTION_SOUND = 3;
    private static final int ROW_SILENT = 4;
    private static final int ROW_MESSAGE_POPUP = 5;
    private static final int ROW_SECTION_ABOUT = 6;
    private static final int ROW_ABOUT = 7;
    private static final int ROW_DARK_MODE = 8;
    private static final int ROW_RINGTONE = 9;
    private static final int[] MAIN_ROWS = {
            ROW_SECTION_DISPLAY, ROW_LOCK_WALLPAPER, ROW_HOME_WALLPAPER, ROW_DARK_MODE,
            ROW_SECTION_SOUND, ROW_RINGTONE, ROW_SILENT, ROW_MESSAGE_POPUP,
            ROW_SECTION_ABOUT, ROW_ABOUT
    };

    private static final int MENU_LOCK = 0;
    private static final int MENU_HOME = 1;
    private static final int MENU_BOTH = 2;
    private static final int MENU_CANCEL = 3;
    private static final int MENU_COUNT = 4;

    private static final int PICKER_COLUMNS = 3;

    private record Row(int kind, UiRect rect) {
    }

    private PhoneSettingsSurfaceRenderer() {
    }

    static void renderSettingsSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);
        switch (screen.settingsWallpaperPage) {
            case PAGE_LOCK_WALLPAPER -> renderWallpaperPicker(screen, guiGraphics, layout, true);
            case PAGE_HOME_WALLPAPER -> renderWallpaperPicker(screen, guiGraphics, layout, false);
            case PAGE_ABOUT -> renderAbout(screen, guiGraphics, layout);
            case PAGE_RINGTONE -> renderRingtones(screen, guiGraphics, layout);
            default -> renderMain(screen, guiGraphics, layout);
        }
    }

    static boolean handleClick(PhoneScreen screen, double mouseX, double mouseY) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            if (screen.settingsWallpaperPage == PAGE_MAIN) {
                screen.closeSettingsApp();
            } else {
                screen.openSettingsPage(PAGE_MAIN);
            }
            return true;
        }

        if (screen.settingsWallpaperPage == PAGE_LOCK_WALLPAPER || screen.settingsWallpaperPage == PAGE_HOME_WALLPAPER) {
            boolean lock = screen.settingsWallpaperPage == PAGE_LOCK_WALLPAPER;
            List<String> options = wallpaperOptions(screen);
            UiRect viewport = pickerViewport(layout);
            if (!viewport.contains(mouseX, mouseY)) {
                return true;
            }
            for (int index = 0; index < options.size(); index++) {
                if (pickerCell(layout, index, screen.settingsPickerScroll).contains(mouseX, mouseY)) {
                    screen.applyPhoneSetting(lock ? PhoneSettingsData.KEY_LOCK_WALLPAPER : PhoneSettingsData.KEY_HOME_WALLPAPER,
                            options.get(index));
                    screen.openSettingsPage(PAGE_MAIN);
                    return true;
                }
            }
            return true;
        }

        if (screen.settingsWallpaperPage == PAGE_RINGTONE) {
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

        if (screen.settingsWallpaperPage != PAGE_MAIN) {
            return true;
        }

        ItemStack phoneStack = screen.getSettingsPhoneStack();
        for (Row row : mainRows(layout)) {
            if (!row.rect().contains(mouseX, mouseY)) {
                continue;
            }
            switch (row.kind()) {
                case ROW_LOCK_WALLPAPER -> screen.openSettingsPage(PAGE_LOCK_WALLPAPER);
                case ROW_HOME_WALLPAPER -> screen.openSettingsPage(PAGE_HOME_WALLPAPER);
                case ROW_DARK_MODE -> screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE,
                        PhoneSettingsData.flagValue(!PhoneSettingsData.isDarkMode(phoneStack)));
                case ROW_RINGTONE -> screen.openSettingsPage(PAGE_RINGTONE);
                case ROW_SILENT -> screen.applyPhoneSetting(PhoneSettingsData.KEY_SILENT,
                        PhoneSettingsData.flagValue(!PhoneSettingsData.isSilent(phoneStack)));
                case ROW_MESSAGE_POPUP -> screen.applyPhoneSetting(PhoneSettingsData.KEY_MESSAGE_POPUP,
                        PhoneSettingsData.flagValue(!PhoneSettingsData.isMessagePopupEnabled(phoneStack)));
                case ROW_ABOUT -> screen.openSettingsPage(PAGE_ABOUT);
                default -> {
                }
            }
            return true;
        }
        return true;
    }

    static void scroll(PhoneScreen screen, double scrollY) {
        if (screen.settingsWallpaperPage != PAGE_LOCK_WALLPAPER && screen.settingsWallpaperPage != PAGE_HOME_WALLPAPER) {
            return;
        }
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        screen.settingsPickerScroll = Mth.clamp(screen.settingsPickerScroll + delta, 0,
                pickerMaxScroll(layout, wallpaperOptions(screen).size()));
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

    private static void renderMain(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.title.settings"), true, false);
        ItemStack phoneStack = screen.getSettingsPhoneStack();
        UiRect surface = layout.surface;
        guiGraphics.enableScissor(surface.left, layout.header().bottom(), surface.right(), surface.bottom());
        for (Row row : mainRows(layout)) {
            UiRect rect = row.rect();
            switch (row.kind()) {
                case ROW_SECTION_DISPLAY -> renderSection(guiGraphics, layout, rect,
                        Component.translatable("screen.bphone.phone.settings.section.display").getString());
                case ROW_SECTION_SOUND -> renderSection(guiGraphics, layout, rect,
                        Component.translatable("screen.bphone.phone.settings.section.sound").getString());
                case ROW_SECTION_ABOUT -> renderSection(guiGraphics, layout, rect,
                        Component.translatable("screen.bphone.phone.settings.section.about").getString());
                case ROW_LOCK_WALLPAPER -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderNavigationRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.lock_wallpaper"),
                            wallpaperName(PhoneWallpaperData.getLockWallpaper(phoneStack)).getString());
                    guiGraphics.fill(rect.left + layout.pad, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                }
                case ROW_HOME_WALLPAPER -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderNavigationRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.home_wallpaper"),
                            wallpaperName(PhoneWallpaperData.getHomeWallpaper(phoneStack)).getString());
                    guiGraphics.fill(rect.left + layout.pad, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                }
                case ROW_DARK_MODE -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderToggleRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.dark_mode"),
                            PhoneSettingsData.isDarkMode(phoneStack));
                }
                case ROW_RINGTONE -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderNavigationRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.ringtone"),
                            PhoneRingtones.name(PhoneSettingsData.getRingtone(phoneStack)).getString());
                    guiGraphics.fill(rect.left + layout.pad, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                }
                case ROW_SILENT -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderToggleRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.silent"),
                            PhoneSettingsData.isSilent(phoneStack));
                    guiGraphics.fill(rect.left + layout.pad, rect.bottom() - 1, rect.right(), rect.bottom(), HAIRLINE);
                }
                case ROW_MESSAGE_POPUP -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderToggleRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.message_popup"),
                            PhoneSettingsData.isMessagePopupEnabled(phoneStack));
                }
                case ROW_ABOUT -> {
                    guiGraphics.fill(rect.left, rect.top, rect.right(), rect.bottom(), SURFACE);
                    renderNavigationRow(screen, guiGraphics, layout, rect,
                            Component.translatable("screen.bphone.phone.settings.about"), null);
                }
                default -> {
                }
            }
        }
        guiGraphics.disableScissor();
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
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < options.size(); index++) {
            UiRect cell = pickerCell(layout, index, screen.settingsPickerScroll);
            if (cell.bottom() <= viewport.top || cell.top >= viewport.bottom()) {
                continue;
            }
            String option = options.get(index);
            int border = Math.max(1, layout.u(2));
            if (option.equals(current)) {
                guiGraphics.fill(cell.left - border, cell.top - border, cell.right() + border, cell.bottom() + border, ACCENT);
            } else if (hovered(screen, cell)) {
                guiGraphics.fill(cell.left - border, cell.top - border, cell.right() + border, cell.bottom() + border, TEXT_TERTIARY);
            }
            PhoneWallpaperRenderer.drawWallpaper(screen, guiGraphics, option, cell, true);
        }
        guiGraphics.disableScissor();
    }

    private static void renderAbout(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.settings.about"), true, false);
        ItemStack phoneStack = screen.getSettingsPhoneStack();
        String version = Platform.getMod(BPhone.MOD_ID).getVersion();
        Component[] labels = {
                Component.translatable("screen.bphone.phone.settings.about.number"),
                Component.translatable("screen.bphone.phone.settings.about.model"),
                Component.translatable("screen.bphone.phone.settings.about.photos"),
                Component.translatable("screen.bphone.phone.settings.about.version")
        };
        String[] values = {
                screen.getOwnPhoneNumber(),
                phoneStack.isEmpty() ? "-" : phoneStack.getHoverName().getString(),
                screen.getPhotoCount() + " / " + screen.getMaxPhotoCount(),
                "BPhone " + version
        };
        int top = layout.header().bottom() + layout.sectionGap;
        for (int index = 0; index < labels.length; index++) {
            UiRect row = new UiRect(layout.surface.left, top + index * layout.itemHeight, layout.surface.width, layout.itemHeight);
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), SURFACE);
            if (index < labels.length - 1) {
                guiGraphics.fill(row.left + layout.pad, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
            int y = row.top + (row.height - layout.bodyHeight) / 2;
            int half = (row.width - 2 * layout.pad) / 2;
            Component value = fit(layout, values[index], PLAIN, half, layout.bodyScale);
            int valueWidth = layout.width(value, layout.bodyScale);
            drawText(guiGraphics, layout, value, row.right() - layout.pad - valueWidth, y, TEXT_SECONDARY, layout.bodyScale);
            drawText(guiGraphics, layout, fit(layout, labels[index].getString(), PLAIN,
                    row.width - 3 * layout.pad - valueWidth, layout.bodyScale), row.left + layout.pad, y, TEXT, layout.bodyScale);
        }
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

    private static List<Row> mainRows(PhoneChatLayout layout) {
        List<Row> rows = new ArrayList<>(MAIN_ROWS.length);
        int y = layout.header().bottom();
        for (int kind : MAIN_ROWS) {
            boolean section = kind == ROW_SECTION_DISPLAY || kind == ROW_SECTION_SOUND || kind == ROW_SECTION_ABOUT;
            int height = section ? layout.sectionHeight + layout.u(2) : layout.itemHeight;
            rows.add(new Row(kind, new UiRect(layout.surface.left, y, layout.surface.width, height)));
            y += height;
        }
        return rows;
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

    private static int pickerMaxScroll(PhoneChatLayout layout, int count) {
        if (count <= 0) {
            return 0;
        }
        UiRect first = pickerCell(layout, 0, 0);
        UiRect last = pickerCell(layout, count - 1, 0);
        int contentHeight = last.bottom() - first.top + 2 * layout.pad;
        return PhoneChatLayout.maxScroll(pickerViewport(layout), contentHeight);
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
