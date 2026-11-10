package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.shop.ShopCategory;
import cn.erindax.bphone.shop.ShopEntry;
import cn.erindax.bphone.shop.ShopNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneShopSurfaceRenderer {
    static final int PAGE_CATEGORIES = 0;
    static final int PAGE_ITEMS = 1;
    static final int PAGE_DETAIL = 2;
    static final int PAGE_STALLS = 3;
    static final int PAGE_STALL_DETAIL = 4;
    static final int PAGE_STALL_PICK = 5;
    static final int PAGE_STALL_SELL = 6;
    static final int PAGE_MARKET = 7;
    static final int PAGE_MARKET_DETAIL = 8;
    static final int PAGE_MARKET_ORDERS = 9;

    private record DetailLayout(UiRect icon, int nameTop, int descriptionTop, int descriptionBottom, int priceTop,
                                UiRect minus, UiRect quantity, UiRect plus, int totalTop, UiRect buy, int statusTop) {
    }

    private PhoneShopSurfaceRenderer() {
    }

    static void render(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);

        ShopCategory category = screen.getShopCategory();
        ShopEntry entry = screen.getShopEntry();
        if (PhoneTradeSurfaceRenderer.handles(screen.shopPage)) {
            PhoneTradeSurfaceRenderer.render(screen, guiGraphics, layout);
        } else if (screen.shopPage == PAGE_DETAIL && category != null && entry != null) {
            renderDetail(screen, guiGraphics, layout, entry);
        } else if (screen.shopPage == PAGE_ITEMS && category != null) {
            renderItems(screen, guiGraphics, layout, category);
        } else {
            renderCategories(screen, guiGraphics, layout);
        }
        if (PhoneTradeSurfaceRenderer.showTabs(screen)) {
            PhoneTradeSurfaceRenderer.renderTabs(screen, guiGraphics, layout);
        }
    }

    static boolean handleClick(PhoneScreen screen, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        PhoneChatLayout layout = new PhoneChatLayout(screen);
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            screen.shopBack();
            return true;
        }
        if (PhoneTradeSurfaceRenderer.showTabs(screen) && layout.tabBar().contains(mouseX, mouseY)) {
            PhoneTradeSurfaceRenderer.handleTabClick(screen, layout, mouseX);
            return true;
        }
        if (PhoneTradeSurfaceRenderer.handles(screen.shopPage)) {
            PhoneTradeSurfaceRenderer.handleClick(screen, layout, mouseX, mouseY);
            return true;
        }

        ShopCategory category = screen.getShopCategory();
        ShopEntry entry = screen.getShopEntry();
        if (screen.shopPage == PAGE_DETAIL && category != null && entry != null) {
            DetailLayout detail = detailLayout(layout);
            if (detail.minus().contains(mouseX, mouseY)) {
                screen.adjustShopQuantity(-1);
            } else if (detail.plus().contains(mouseX, mouseY)) {
                screen.adjustShopQuantity(1);
            } else if (detail.buy().contains(mouseX, mouseY) && canBuy(screen, entry)) {
                screen.requestShopPurchase();
            }
            return true;
        }

        UiRect viewport = listViewport(screen, layout);
        if (screen.shopPage == PAGE_ITEMS && category != null) {
            int index = layout.listRowAt(viewport, category.entries().size(), screen.shopScrollOffset, mouseX, mouseY);
            if (index >= 0) {
                screen.openShopEntry(index);
            }
            return true;
        }

        List<ShopCategory> categories = PhoneClientShopState.getCategories();
        int index = layout.listRowAt(viewport, categories.size(), screen.shopScrollOffset, mouseX, mouseY);
        if (index >= 0) {
            screen.openShopCategory(index);
        }
        return true;
    }

    static void scroll(PhoneScreen screen, double scrollY) {
        if (PhoneTradeSurfaceRenderer.handles(screen.shopPage)) {
            PhoneTradeSurfaceRenderer.scroll(screen, scrollY);
            return;
        }
        if (screen.shopPage == PAGE_DETAIL) {
            screen.adjustShopQuantity(scrollY > 0 ? 1 : -1);
            return;
        }

        PhoneChatLayout layout = new PhoneChatLayout(screen);
        ShopCategory category = screen.getShopCategory();
        int count = screen.shopPage == PAGE_ITEMS && category != null
                ? category.entries().size()
                : PhoneClientShopState.getCategories().size();
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        int maxOffset = PhoneChatLayout.maxScroll(listViewport(screen, layout), layout.listContentHeight(count, false));
        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset + delta, 0, maxOffset);
    }

    static Component entryName(ShopEntry entry) {
        return ShopNetworking.displayName(entry.name(), entry.stack());
    }

    private static boolean canBuy(PhoneScreen screen, ShopEntry entry) {
        return PhoneClientShopState.isEconomyAvailable()
                && PhoneClientShopState.getBalance() >= totalPrice(entry, screen.shopQuantity)
                && !screen.shopPurchasePending;
    }

    private static long totalPrice(ShopEntry entry, int quantity) {
        try {
            return Math.multiplyExact(entry.price(), (long) Math.max(1, quantity));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void renderCategories(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.shop.title"), true, false);
        renderBalance(guiGraphics, layout);
        UiRect viewport = listViewport(screen, layout);
        List<ShopCategory> categories = PhoneClientShopState.getCategories();
        if (!PhoneClientShopState.isLoaded()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.shop.loading"), null);
            return;
        }
        if (categories.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.shop.empty"),
                    Component.translatable("screen.bphone.phone.shop.empty_hint"));
            return;
        }

        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(categories.size(), false)));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < categories.size(); index++) {
            UiRect row = layout.listRow(viewport, index, screen.shopScrollOffset);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            ShopCategory category = categories.get(index);
            renderListRow(screen, guiGraphics, layout, row, category.icon(), category.name(),
                    Component.translatable("screen.bphone.phone.shop.item_count", category.entries().size()).getString(),
                    null);
        }
        guiGraphics.disableScissor();
    }

    private static void renderItems(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout,
                                    ShopCategory category) {
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.literal(category.name()), true, false);
        renderBalance(guiGraphics, layout);
        UiRect viewport = listViewport(screen, layout);
        List<ShopEntry> entries = category.entries();
        if (entries.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.shop.empty"), null);
            return;
        }

        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(entries.size(), false)));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < entries.size(); index++) {
            UiRect row = layout.listRow(viewport, index, screen.shopScrollOffset);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            ShopEntry entry = entries.get(index);
            String secondary = entry.description().isBlank()
                    ? "x" + entry.stack().getCount()
                    : "x" + entry.stack().getCount() + "  " + entry.description();
            renderListRow(screen, guiGraphics, layout, row, entry.stack(), entryName(entry).getString(), secondary,
                    formatAmount(entry.price()));
        }
        guiGraphics.disableScissor();
    }

    private static void renderDetail(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, ShopEntry entry) {
        Component name = entryName(entry);
        renderHeader(screen, guiGraphics, layout, layout.header(), name, true, false);
        renderBalance(guiGraphics, layout);
        DetailLayout detail = detailLayout(layout);
        UiRect surface = layout.surface;
        int centerX = surface.left + surface.width / 2;
        int textWidth = surface.width - 2 * layout.pad;
        guiGraphics.fill(surface.left, balanceStrip(layout).bottom() + layout.sectionGap, surface.right(),
                surface.bottom(), SURFACE);

        UiRect icon = detail.icon();
        fillRounded(guiGraphics, icon.left, icon.top, icon.width, icon.height, Math.max(2, layout.radius * 2), FIELD);
        int inset = Math.max(2, icon.width / 8);
        renderItem(guiGraphics, entry.stack(), new UiRect(icon.left + inset, icon.top + inset,
                icon.width - 2 * inset, icon.height - 2 * inset));

        drawCentered(guiGraphics, layout, fit(layout, name.getString(), BOLD, textWidth, layout.bodyScale),
                centerX, detail.nameTop(), TEXT, layout.bodyScale);
        Component count = Component.literal("x" + entry.stack().getCount());
        drawCentered(guiGraphics, layout, count, centerX, detail.nameTop() + layout.bodyHeight + layout.textGap,
                TEXT_TERTIARY, layout.captionScale);

        if (!entry.description().isBlank() && detail.descriptionBottom() > detail.descriptionTop()) {
            guiGraphics.enableScissor(surface.left, detail.descriptionTop(), surface.right(), detail.descriptionBottom());
            drawWrappedCentered(guiGraphics, layout, Component.literal(entry.description()), centerX,
                    detail.descriptionTop(), textWidth, TEXT_SECONDARY, layout.captionScale);
            guiGraphics.disableScissor();
        }

        drawCentered(guiGraphics, layout, Component.translatable("screen.bphone.phone.shop.price", formatAmount(entry.price())),
                centerX, detail.priceTop(), TEXT_SECONDARY, layout.bodyScale);

        renderButton(screen, guiGraphics, layout, detail.minus(), Component.literal("-"),
                screen.shopQuantity > 1 ? Tone.SECONDARY : Tone.DISABLED);
        renderButton(screen, guiGraphics, layout, detail.plus(), Component.literal("+"),
                screen.shopQuantity < ShopNetworking.MAX_QUANTITY ? Tone.SECONDARY : Tone.DISABLED);
        UiRect quantity = detail.quantity();
        fillRounded(guiGraphics, quantity.left, quantity.top, quantity.width, quantity.height, layout.radius, FIELD);
        drawCentered(guiGraphics, layout, Component.literal(String.valueOf(screen.shopQuantity)),
                quantity.left + quantity.width / 2, quantity.top + (quantity.height - layout.bodyHeight) / 2, TEXT,
                layout.bodyScale);

        long total = totalPrice(entry, screen.shopQuantity);
        drawCentered(guiGraphics, layout, Component.translatable("screen.bphone.phone.shop.total", formatAmount(total))
                        .withStyle(BOLD),
                centerX, detail.totalTop(), ACCENT, layout.bodyScale);

        Component buyLabel;
        Tone buyTone;
        if (!PhoneClientShopState.isEconomyAvailable()) {
            buyLabel = Component.translatable("screen.bphone.phone.bank.status.no_economy");
            buyTone = Tone.DISABLED;
        } else if (PhoneClientShopState.getBalance() < total) {
            buyLabel = Component.translatable("screen.bphone.phone.bank.status.insufficient");
            buyTone = Tone.DISABLED;
        } else if (screen.shopPurchasePending) {
            buyLabel = Component.translatable("screen.bphone.phone.shop.status.processing");
            buyTone = Tone.DISABLED;
        } else {
            buyLabel = Component.translatable("screen.bphone.phone.shop.buy");
            buyTone = Tone.PRIMARY;
        }
        renderButton(screen, guiGraphics, layout, detail.buy(), buyLabel, buyTone);

        if (!screen.shopStatus.getString().isBlank()) {
            drawCentered(guiGraphics, layout, fit(layout, screen.shopStatus.getString(), PLAIN, textWidth, layout.captionScale),
                    centerX, detail.statusTop(), screen.shopStatusError ? DANGER : SUCCESS, layout.captionScale);
        }
    }

    static void renderListRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row,
                              ItemStack icon, String primary, String secondary, String trailing) {
        renderListRow(screen, guiGraphics, layout, row, icon, primary, secondary, TEXT_SECONDARY, trailing, ACCENT);
    }

    static void renderListRow(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect row,
                              ItemStack icon, String primary, String secondary, int secondaryColor,
                              String trailing, int trailingColor) {
        guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), hovered(screen, row) ? HOVER : SURFACE);
        guiGraphics.fill(row.left + layout.pad, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
        UiRect iconBounds = layout.rowAvatar(row, layout.avatar);
        fillRounded(guiGraphics, iconBounds.left, iconBounds.top, iconBounds.width, iconBounds.height, layout.radius, FIELD);
        int inset = Math.max(1, iconBounds.width / 10);
        renderItem(guiGraphics, icon, new UiRect(iconBounds.left + inset, iconBounds.top + inset,
                iconBounds.width - 2 * inset, iconBounds.height - 2 * inset));

        int textLeft = layout.rowTextLeft(row, layout.avatar);
        int right = row.right() - layout.pad - layout.chevronReserve();
        if (trailing != null) {
            Component price = Component.literal(trailing).withStyle(BOLD);
            int priceWidth = layout.width(price, layout.bodyScale);
            drawText(guiGraphics, layout, price, right - priceWidth, row.top + (row.height - layout.bodyHeight) / 2,
                    trailingColor, layout.bodyScale);
            right -= priceWidth + layout.gap;
        }
        renderTwoLineText(guiGraphics, layout, row, textLeft, right, primary, TEXT, secondary, secondaryColor);
        renderChevron(guiGraphics, layout, row);
    }

    static void renderBalance(GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect strip = balanceStrip(layout);
        guiGraphics.fill(strip.left, strip.top, strip.right(), strip.bottom(), SURFACE);
        guiGraphics.fill(strip.left, strip.bottom() - 1, strip.right(), strip.bottom(), HAIRLINE);
        Component text = PhoneClientShopState.isEconomyAvailable()
                ? Component.translatable("screen.bphone.phone.shop.balance", formatAmount(PhoneClientShopState.getBalance()))
                : Component.translatable("screen.bphone.phone.bank.status.no_economy");
        drawText(guiGraphics, layout, fit(layout, text.getString(), PLAIN, strip.width - 2 * layout.pad, layout.captionScale),
                strip.left + layout.pad, strip.top + (strip.height - layout.captionHeight) / 2,
                PhoneClientShopState.isEconomyAvailable() ? TEXT_SECONDARY : DANGER, layout.captionScale);
    }

    static UiRect balanceStrip(PhoneChatLayout layout) {
        UiRect header = layout.header();
        return new UiRect(layout.surface.left, header.bottom(), layout.surface.width, layout.captionHeight + layout.u(10));
    }

    static UiRect listViewport(PhoneScreen screen, PhoneChatLayout layout) {
        int top = balanceStrip(layout).bottom();
        int bottom = PhoneTradeSurfaceRenderer.contentBottom(screen, layout);
        return new UiRect(layout.surface.left, top, layout.surface.width, Math.max(1, bottom - top));
    }

    private static DetailLayout detailLayout(PhoneChatLayout layout) {
        UiRect surface = layout.surface;
        int left = surface.left + layout.pad;
        int width = surface.width - 2 * layout.pad;
        int statusTop = surface.bottom() - layout.pad - layout.captionHeight;
        UiRect buy = new UiRect(left, statusTop - layout.u(4) - layout.buttonHeight, width, layout.buttonHeight);
        int totalTop = buy.top - layout.u(6) - layout.bodyHeight;
        int control = layout.fieldHeight;
        int quantityWidth = Math.max(control * 2, layout.u(36));
        int controlsWidth = control * 2 + quantityWidth + 2 * layout.gap;
        int controlsLeft = surface.left + (surface.width - controlsWidth) / 2;
        int controlsTop = totalTop - layout.u(6) - control;
        UiRect minus = new UiRect(controlsLeft, controlsTop, control, control);
        UiRect quantity = new UiRect(minus.right() + layout.gap, controlsTop, quantityWidth, control);
        UiRect plus = new UiRect(quantity.right() + layout.gap, controlsTop, control, control);
        int priceTop = controlsTop - layout.u(6) - layout.bodyHeight;

        int contentTop = balanceStrip(layout).bottom() + layout.sectionGap + layout.u(8);
        int available = priceTop - layout.u(8) - contentTop - layout.bodyHeight - layout.textGap - layout.captionHeight;
        int iconSize = Mth.clamp(available / 2, layout.u(16), layout.u(44));
        UiRect icon = new UiRect(surface.left + (surface.width - iconSize) / 2, contentTop, iconSize, iconSize);
        int nameTop = icon.bottom() + layout.u(6);
        int descriptionTop = nameTop + layout.bodyHeight + layout.textGap + layout.captionHeight + layout.u(6);
        int descriptionBottom = priceTop - layout.u(6);
        return new DetailLayout(icon, nameTop, descriptionTop, descriptionBottom, priceTop, minus, quantity, plus,
                totalTop, buy, statusTop);
    }

    static String formatAmount(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
