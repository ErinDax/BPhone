package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.api.market.MarketOrder;
import cn.erindax.bphone.api.market.MarketQuote;
import cn.erindax.bphone.shop.TradeNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_CATEGORIES;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_MARKET;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_MARKET_DETAIL;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_MARKET_ORDERS;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_STALLS;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_STALL_DETAIL;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_STALL_PICK;
import static cn.erindax.bphone.client.phone.PhoneShopSurfaceRenderer.PAGE_STALL_SELL;
import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneTradeSurfaceRenderer {
    private static final int TAB_OFFICIAL = 0;
    private static final int TAB_STALLS = 1;
    private static final int TAB_MARKET = 2;
    private static final int MAX_PRICE_DIGITS = 12;

    private record SellLayout(UiRect icon, int nameTop, int infoBottom, int labelTop, UiRect field, int slotsTop,
                              UiRect submit, int statusTop) {
    }

    private record MarketLayout(int infoTop, int infoBottom, UiRect buy, UiRect sell, UiRect minus, UiRect quantity,
                                UiRect plus, UiRect price, int totalTop, UiRect submit, int statusTop) {
    }

    private PhoneTradeSurfaceRenderer() {
    }

    static boolean handles(int page) {
        return page >= PAGE_STALLS && page <= PAGE_MARKET_ORDERS;
    }

    private static List<Integer> tabs() {
        List<Integer> tabs = new ArrayList<>(3);
        tabs.add(TAB_OFFICIAL);
        if (PhoneClientShopState.isStallsEnabled()) {
            tabs.add(TAB_STALLS);
        }
        if (PhoneClientShopState.isMarketAvailable()) {
            tabs.add(TAB_MARKET);
        }
        return tabs;
    }

    private static int activeTab(int page) {
        if (page >= PAGE_STALLS && page <= PAGE_STALL_SELL) {
            return TAB_STALLS;
        }
        if (page >= PAGE_MARKET && page <= PAGE_MARKET_ORDERS) {
            return TAB_MARKET;
        }
        return TAB_OFFICIAL;
    }

    static boolean showTabs(PhoneScreen screen) {
        int page = screen.shopPage;
        boolean topLevel = page == PAGE_CATEGORIES || page == PAGE_STALLS || page == PAGE_MARKET;
        return topLevel && tabs().size() > 1;
    }

    static int contentBottom(PhoneScreen screen, PhoneChatLayout layout) {
        return showTabs(screen) ? layout.tabBar().top : layout.surface.bottom();
    }

    private static UiRect tabSlot(PhoneChatLayout layout, int index, int count) {
        UiRect bar = layout.tabBar();
        int left = bar.left + bar.width * index / count;
        int right = bar.left + bar.width * (index + 1) / count;
        return new UiRect(left, bar.top, right - left, bar.height);
    }

    private static Component tabLabel(int tab) {
        return switch (tab) {
            case TAB_STALLS -> Component.translatable("screen.bphone.phone.shop.tab.stalls");
            case TAB_MARKET -> Component.translatable("screen.bphone.phone.shop.tab.market");
            default -> Component.translatable("screen.bphone.phone.shop.tab.official");
        };
    }

    static void renderTabs(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        UiRect bar = layout.tabBar();
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        guiGraphics.fill(bar.left, bar.top, bar.right(), bar.top + 1, HAIRLINE);
        List<Integer> tabs = tabs();
        int active = activeTab(screen.shopPage);
        for (int index = 0; index < tabs.size(); index++) {
            UiRect slot = tabSlot(layout, index, tabs.size());
            int tab = tabs.get(index);
            boolean selected = tab == active;
            if (!selected && hovered(screen, slot)) {
                guiGraphics.fill(slot.left, slot.top + 1, slot.right(), slot.bottom(), HOVER);
            }
            Component label = fit(layout, tabLabel(tab).getString(), selected ? BOLD : PLAIN,
                    slot.width - 2 * layout.u(3), layout.bodyScale);
            drawCentered(guiGraphics, layout, label, slot.left + slot.width / 2,
                    slot.top + (slot.height - layout.bodyHeight) / 2, selected ? ACCENT : TEXT_SECONDARY, layout.bodyScale);
            if (selected) {
                int width = Math.max(layout.u(12), slot.width / 3);
                int left = slot.left + (slot.width - width) / 2;
                guiGraphics.fill(left, slot.bottom() - Math.max(1, layout.u(2)), left + width, slot.bottom(), ACCENT);
            }
        }
    }

    static void handleTabClick(PhoneScreen screen, PhoneChatLayout layout, double mouseX) {
        List<Integer> tabs = tabs();
        for (int index = 0; index < tabs.size(); index++) {
            UiRect slot = tabSlot(layout, index, tabs.size());
            if (mouseX >= slot.left && mouseX < slot.right()) {
                openTab(screen, tabs.get(index));
                return;
            }
        }
    }

    private static void openTab(PhoneScreen screen, int tab) {
        if (tab == activeTab(screen.shopPage)) {
            return;
        }
        reset(screen);
        switch (tab) {
            case TAB_STALLS -> {
                navigate(screen, PAGE_STALLS);
                ShopNetworkingClient.requestStalls();
            }
            case TAB_MARKET -> {
                navigate(screen, PAGE_MARKET);
                ShopNetworkingClient.requestMarket();
            }
            default -> {
                navigate(screen, PAGE_CATEGORIES);
                ShopNetworkingClient.requestCatalog();
            }
        }
    }

    private static void navigate(PhoneScreen screen, int page) {
        screen.shopPage = page;
        screen.shopScrollOffset = 0;
        screen.shopStatus = Component.empty();
        screen.shopStatusError = false;
        screen.shopPurchasePending = false;
    }

    private static void moveKeepStatus(PhoneScreen screen, int page) {
        screen.shopPage = page;
        screen.shopScrollOffset = 0;
        screen.shopPurchasePending = false;
    }

    static void reset(PhoneScreen screen) {
        screen.shopStallId = null;
        screen.shopPickSlot = -1;
        screen.shopPriceInput = "";
        screen.shopMarketId = "";
        screen.shopMarketBuy = true;
        screen.shopMarketQuantity = 1;
    }

    static boolean back(PhoneScreen screen) {
        switch (screen.shopPage) {
            case PAGE_STALL_DETAIL, PAGE_STALL_PICK -> {
                screen.shopStallId = null;
                screen.shopPickSlot = -1;
                navigate(screen, PAGE_STALLS);
                return true;
            }
            case PAGE_STALL_SELL -> {
                screen.shopPickSlot = -1;
                screen.shopPriceInput = "";
                navigate(screen, PAGE_STALL_PICK);
                return true;
            }
            case PAGE_MARKET_DETAIL, PAGE_MARKET_ORDERS -> {
                screen.shopMarketId = "";
                screen.shopPriceInput = "";
                navigate(screen, PAGE_MARKET);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    static void onResult(PhoneScreen screen, boolean success) {
        if (!success) {
            return;
        }
        if (screen.shopPage == PAGE_STALL_SELL || screen.shopPage == PAGE_STALL_DETAIL) {
            screen.shopPickSlot = -1;
            screen.shopPriceInput = "";
            screen.shopStallId = null;
            navigate(screen, PAGE_STALLS);
        }
    }

    static void onUpdated(PhoneScreen screen) {
        int tab = activeTab(screen.shopPage);
        if (tab == TAB_STALLS && PhoneClientShopState.isStallsLoaded() && !PhoneClientShopState.isStallsEnabled()) {
            reset(screen);
            moveKeepStatus(screen, PAGE_CATEGORIES);
            return;
        }
        if (tab == TAB_MARKET && PhoneClientShopState.isMarketLoaded() && !PhoneClientShopState.isMarketAvailable()) {
            reset(screen);
            moveKeepStatus(screen, PAGE_CATEGORIES);
            return;
        }
        if (screen.shopPage == PAGE_STALL_DETAIL && PhoneClientShopState.findListing(screen.shopStallId) == null) {
            screen.shopStallId = null;
            moveKeepStatus(screen, PAGE_STALLS);
        } else if (screen.shopPage == PAGE_STALL_SELL && pickedStack(screen).isEmpty()) {
            screen.shopPickSlot = -1;
            moveKeepStatus(screen, PAGE_STALL_PICK);
        } else if (screen.shopPage == PAGE_MARKET_DETAIL && PhoneClientShopState.findQuote(screen.shopMarketId) == null) {
            screen.shopMarketId = "";
            moveKeepStatus(screen, PAGE_MARKET);
        }
    }

    static boolean keyPressed(PhoneScreen screen, int keyCode) {
        if (screen.shopPage != PAGE_STALL_SELL && screen.shopPage != PAGE_MARKET_DETAIL) {
            return false;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (!screen.shopPriceInput.isEmpty()) {
                screen.shopPriceInput = screen.shopPriceInput.substring(0, screen.shopPriceInput.length() - 1);
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (screen.shopPage == PAGE_STALL_SELL) {
                submitListing(screen);
            } else {
                submitOrder(screen);
            }
            return true;
        }
        return false;
    }

    static boolean charTyped(PhoneScreen screen, char codePoint) {
        if (screen.shopPage != PAGE_STALL_SELL && screen.shopPage != PAGE_MARKET_DETAIL) {
            return false;
        }
        if (!Character.isDigit(codePoint)) {
            return true;
        }
        if (screen.shopPriceInput.length() < MAX_PRICE_DIGITS && !(screen.shopPriceInput.isEmpty() && codePoint == '0')) {
            screen.shopPriceInput = screen.shopPriceInput + codePoint;
        }
        return true;
    }

    static void render(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        switch (screen.shopPage) {
            case PAGE_STALL_DETAIL -> renderStallDetail(screen, guiGraphics, layout);
            case PAGE_STALL_PICK -> renderPick(screen, guiGraphics, layout);
            case PAGE_STALL_SELL -> renderSell(screen, guiGraphics, layout);
            case PAGE_MARKET -> renderMarket(screen, guiGraphics, layout);
            case PAGE_MARKET_DETAIL -> renderMarketDetail(screen, guiGraphics, layout);
            case PAGE_MARKET_ORDERS -> renderOrders(screen, guiGraphics, layout);
            default -> renderStalls(screen, guiGraphics, layout);
        }
    }

    static void handleClick(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        switch (screen.shopPage) {
            case PAGE_STALL_DETAIL -> clickStallDetail(screen, layout, mouseX, mouseY);
            case PAGE_STALL_PICK -> clickPick(screen, layout, mouseX, mouseY);
            case PAGE_STALL_SELL -> clickSell(screen, layout, mouseX, mouseY);
            case PAGE_MARKET -> clickMarket(screen, layout, mouseX, mouseY);
            case PAGE_MARKET_DETAIL -> clickMarketDetail(screen, layout, mouseX, mouseY);
            case PAGE_MARKET_ORDERS -> clickOrders(screen, layout, mouseX, mouseY);
            default -> clickStalls(screen, layout, mouseX, mouseY);
        }
    }

    static void scroll(PhoneScreen screen, double scrollY) {
        if (screen.shopPage == PAGE_MARKET_DETAIL) {
            adjustMarketQuantity(screen, scrollY > 0 ? 1 : -1);
            return;
        }
        int count = switch (screen.shopPage) {
            case PAGE_STALLS -> PhoneClientShopState.getListings().size();
            case PAGE_STALL_PICK -> sellableSlots().size();
            case PAGE_MARKET -> marketRowCount();
            case PAGE_MARKET_ORDERS -> PhoneClientShopState.getOrders().size();
            default -> -1;
        };
        if (count < 0) {
            return;
        }
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        int maxOffset = PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(count, false));
        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset + delta, 0, maxOffset);
    }

    private static void renderStalls(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.stall.title"), true, canList());
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        if (!PhoneClientShopState.isStallsLoaded()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.shop.loading"), null);
            renderStatus(screen, guiGraphics, layout, viewport);
            return;
        }
        List<PhoneClientShopState.StallListing> listings = PhoneClientShopState.getListings();
        if (listings.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.stall.empty"),
                    Component.translatable("screen.bphone.phone.stall.empty_hint"));
            renderStatus(screen, guiGraphics, layout, viewport);
            return;
        }
        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(listings.size(), false)));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < listings.size(); index++) {
            UiRect row = layout.listRow(viewport, index, screen.shopScrollOffset);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            PhoneClientShopState.StallListing listing = listings.get(index);
            String secondary = listing.own()
                    ? Component.translatable("screen.bphone.phone.stall.mine").getString()
                    : Component.translatable("screen.bphone.phone.stall.seller", listing.seller()).getString();
            PhoneShopSurfaceRenderer.renderListRow(screen, guiGraphics, layout, row, listing.stack(), itemLabel(listing.stack()),
                    secondary, listing.own() ? ACCENT : TEXT_SECONDARY,
                    PhoneShopSurfaceRenderer.formatAmount(listing.price()), ACCENT);
        }
        guiGraphics.disableScissor();
        renderStatus(screen, guiGraphics, layout, viewport);
    }

    private static void clickStalls(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        if (layout.actionButton(layout.header()).contains(mouseX, mouseY)) {
            if (canList()) {
                screen.shopPickSlot = -1;
                navigate(screen, PAGE_STALL_PICK);
            }
            return;
        }
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        List<PhoneClientShopState.StallListing> listings = PhoneClientShopState.getListings();
        int index = layout.listRowAt(viewport, listings.size(), screen.shopScrollOffset, mouseX, mouseY);
        if (index >= 0) {
            screen.shopStallId = listings.get(index).id();
            navigate(screen, PAGE_STALL_DETAIL);
        }
    }

    private static boolean canList() {
        return PhoneClientShopState.isStallsEnabled() && PhoneClientShopState.isEconomyAvailable();
    }

    private static void renderStallDetail(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        PhoneClientShopState.StallListing listing = PhoneClientShopState.findListing(screen.shopStallId);
        if (listing == null) {
            renderHeader(screen, guiGraphics, layout, layout.header(),
                    Component.translatable("screen.bphone.phone.stall.title"), true, false);
            PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
            renderEmptyState(guiGraphics, layout, PhoneShopSurfaceRenderer.listViewport(screen, layout),
                    Component.translatable("screen.bphone.phone.stall.status.gone"), null);
            return;
        }
        String name = listing.stack().getHoverName().getString();
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.literal(name), true, false);
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);

        UiRect surface = layout.surface;
        int top = PhoneShopSurfaceRenderer.listViewport(screen, layout).top + layout.sectionGap;
        guiGraphics.fill(surface.left, top, surface.right(), surface.bottom(), SURFACE);
        int statusTop = statusTop(layout);
        UiRect action = actionButton(layout, statusTop);
        int centerX = surface.left + surface.width / 2;
        int textWidth = surface.width - 2 * layout.pad;

        guiGraphics.enableScissor(surface.left, top, surface.right(), action.top - layout.u(2));
        int y = top + layout.u(8);
        int iconSize = Mth.clamp((action.top - y) / 3, layout.u(16), layout.u(44));
        renderIconTile(guiGraphics, layout, listing.stack(),
                new UiRect(surface.left + (surface.width - iconSize) / 2, y, iconSize, iconSize));
        y += iconSize + layout.u(6);
        drawCentered(guiGraphics, layout, fit(layout, name, BOLD, textWidth, layout.bodyScale), centerX, y, TEXT,
                layout.bodyScale);
        y += layout.bodyHeight + layout.textGap;
        drawCentered(guiGraphics, layout, Component.literal("x" + listing.stack().getCount()), centerX, y,
                TEXT_TERTIARY, layout.captionScale);
        y += layout.captionHeight + layout.u(6);
        Component seller = listing.own()
                ? Component.translatable("screen.bphone.phone.stall.mine")
                : Component.translatable("screen.bphone.phone.stall.seller", listing.seller());
        drawCentered(guiGraphics, layout, fit(layout, seller.getString(), PLAIN, textWidth, layout.captionScale), centerX, y,
                TEXT_SECONDARY, layout.captionScale);
        y += layout.captionHeight + layout.u(8);
        drawCentered(guiGraphics, layout, Component.translatable("screen.bphone.phone.shop.price",
                        PhoneShopSurfaceRenderer.formatAmount(listing.price())).withStyle(BOLD),
                centerX, y, ACCENT, layout.bodyScale);
        guiGraphics.disableScissor();

        Component label;
        Tone tone;
        if (screen.shopPurchasePending) {
            label = Component.translatable("screen.bphone.phone.stall.processing");
            tone = Tone.DISABLED;
        } else if (listing.own()) {
            label = Component.translatable("screen.bphone.phone.stall.cancel");
            tone = Tone.DANGER;
        } else if (!PhoneClientShopState.isEconomyAvailable()) {
            label = Component.translatable("screen.bphone.phone.bank.status.no_economy");
            tone = Tone.DISABLED;
        } else if (PhoneClientShopState.getBalance() < listing.price()) {
            label = Component.translatable("screen.bphone.phone.bank.status.insufficient");
            tone = Tone.DISABLED;
        } else {
            label = Component.translatable("screen.bphone.phone.stall.buy");
            tone = Tone.PRIMARY;
        }
        renderButton(screen, guiGraphics, layout, action, label, tone);
        renderDetailStatus(screen, guiGraphics, layout, statusTop);
    }

    private static void clickStallDetail(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        PhoneClientShopState.StallListing listing = PhoneClientShopState.findListing(screen.shopStallId);
        if (listing == null || screen.shopPurchasePending) {
            return;
        }
        if (!actionButton(layout, statusTop(layout)).contains(mouseX, mouseY)) {
            return;
        }
        if (listing.own()) {
            setPending(screen);
            ShopNetworkingClient.cancelListing(listing.id());
        } else if (PhoneClientShopState.isEconomyAvailable() && PhoneClientShopState.getBalance() >= listing.price()) {
            setPending(screen);
            ShopNetworkingClient.buyListing(listing.id(), listing.price());
        }
    }

    private static List<Integer> sellableSlots() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < TradeNetworking.INVENTORY_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !ModItems.isPhone(stack)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static ItemStack slotStack(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || slot < 0 || slot >= TradeNetworking.INVENTORY_SLOTS) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    private static ItemStack pickedStack(PhoneScreen screen) {
        ItemStack stack = slotStack(screen.shopPickSlot);
        return stack.isEmpty() || ModItems.isPhone(stack) ? ItemStack.EMPTY : stack;
    }

    private static void renderPick(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.stall.pick_title"), true, false);
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        List<Integer> slots = sellableSlots();
        if (slots.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.stall.pick_empty"), null);
            return;
        }
        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(slots.size(), false)));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < slots.size(); index++) {
            UiRect row = layout.listRow(viewport, index, screen.shopScrollOffset);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            ItemStack stack = slotStack(slots.get(index));
            PhoneShopSurfaceRenderer.renderListRow(screen, guiGraphics, layout, row, stack,
                    stack.getHoverName().getString(), "x" + stack.getCount(), TEXT_SECONDARY, null, ACCENT);
        }
        guiGraphics.disableScissor();
    }

    private static void clickPick(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        List<Integer> slots = sellableSlots();
        int index = layout.listRowAt(viewport, slots.size(), screen.shopScrollOffset, mouseX, mouseY);
        if (index >= 0) {
            screen.shopPickSlot = slots.get(index);
            screen.shopPriceInput = "";
            navigate(screen, PAGE_STALL_SELL);
        }
    }

    private static SellLayout sellLayout(PhoneScreen screen, PhoneChatLayout layout) {
        UiRect surface = layout.surface;
        int left = surface.left + layout.pad;
        int width = surface.width - 2 * layout.pad;
        int statusTop = statusTop(layout);
        UiRect submit = actionButton(layout, statusTop);
        int slotsTop = submit.top - layout.u(5) - layout.captionHeight;
        UiRect field = new UiRect(left, slotsTop - layout.u(5) - layout.fieldHeight, width, layout.fieldHeight);
        int labelTop = field.top - layout.u(4) - layout.captionHeight;
        int contentTop = PhoneShopSurfaceRenderer.listViewport(screen, layout).top + layout.sectionGap + layout.u(8);
        int infoBottom = labelTop - layout.u(4);
        int available = infoBottom - contentTop - layout.u(6) - layout.bodyHeight - layout.textGap - layout.captionHeight;
        int iconSize = Mth.clamp(available, layout.u(16), layout.u(40));
        UiRect icon = new UiRect(surface.left + (surface.width - iconSize) / 2, contentTop, iconSize, iconSize);
        return new SellLayout(icon, icon.bottom() + layout.u(6), infoBottom, labelTop, field, slotsTop, submit, statusTop);
    }

    private static void renderSell(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.stall.sell_title"), true, false);
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
        ItemStack stack = pickedStack(screen);
        if (stack.isEmpty()) {
            renderEmptyState(guiGraphics, layout, PhoneShopSurfaceRenderer.listViewport(screen, layout),
                    Component.translatable("screen.bphone.phone.stall.item_missing"), null);
            return;
        }
        UiRect surface = layout.surface;
        SellLayout sell = sellLayout(screen, layout);
        int top = PhoneShopSurfaceRenderer.listViewport(screen, layout).top + layout.sectionGap;
        guiGraphics.fill(surface.left, top, surface.right(), surface.bottom(), SURFACE);
        int centerX = surface.left + surface.width / 2;
        int textWidth = surface.width - 2 * layout.pad;

        guiGraphics.enableScissor(surface.left, top, surface.right(), Math.max(top, sell.infoBottom()));
        renderIconTile(guiGraphics, layout, stack, sell.icon());
        drawCentered(guiGraphics, layout, fit(layout, stack.getHoverName().getString(), BOLD, textWidth, layout.bodyScale),
                centerX, sell.nameTop(), TEXT, layout.bodyScale);
        drawCentered(guiGraphics, layout, Component.literal("x" + stack.getCount()), centerX,
                sell.nameTop() + layout.bodyHeight + layout.textGap, TEXT_TERTIARY, layout.captionScale);
        guiGraphics.disableScissor();

        long maxPrice = PhoneClientShopState.getStallMaxPrice();
        Component label = Component.translatable("screen.bphone.phone.stall.price_label",
                PhoneShopSurfaceRenderer.formatAmount(maxPrice));
        drawText(guiGraphics, layout, fit(layout, label.getString(), PLAIN, textWidth, layout.captionScale),
                sell.field().left, sell.labelTop(), TEXT_SECONDARY, layout.captionScale);
        long price = parsePrice(screen.shopPriceInput);
        boolean priceValid = price > 0L && price <= maxPrice;
        renderField(guiGraphics, layout, sell.field(), formatInput(screen.shopPriceInput),
                Component.translatable("screen.bphone.phone.stall.price_placeholder"),
                screen.shopPriceInput.isEmpty() || priceValid ? ACCENT : DANGER, FIELD);

        int own = PhoneClientShopState.countOwnListings();
        int limit = PhoneClientShopState.getStallLimit();
        drawText(guiGraphics, layout, Component.translatable("screen.bphone.phone.stall.slots", own, limit),
                sell.field().left, sell.slotsTop(), own >= limit ? DANGER : TEXT_TERTIARY, layout.captionScale);

        Component submitLabel;
        Tone tone;
        if (screen.shopPurchasePending) {
            submitLabel = Component.translatable("screen.bphone.phone.stall.processing");
            tone = Tone.DISABLED;
        } else if (!PhoneClientShopState.isEconomyAvailable()) {
            submitLabel = Component.translatable("screen.bphone.phone.bank.status.no_economy");
            tone = Tone.DISABLED;
        } else if (own >= limit) {
            submitLabel = Component.translatable("screen.bphone.phone.stall.status.limit", limit);
            tone = Tone.DISABLED;
        } else {
            submitLabel = Component.translatable("screen.bphone.phone.stall.submit");
            tone = priceValid ? Tone.PRIMARY : Tone.DISABLED;
        }
        renderButton(screen, guiGraphics, layout, sell.submit(), submitLabel, tone);
        renderDetailStatus(screen, guiGraphics, layout, sell.statusTop());
    }

    private static void clickSell(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        if (sellLayout(screen, layout).submit().contains(mouseX, mouseY)) {
            submitListing(screen);
        }
    }

    private static void submitListing(PhoneScreen screen) {
        ItemStack stack = pickedStack(screen);
        long price = parsePrice(screen.shopPriceInput);
        if (stack.isEmpty() || screen.shopPurchasePending || !PhoneClientShopState.isEconomyAvailable()
                || price <= 0L || price > PhoneClientShopState.getStallMaxPrice()
                || PhoneClientShopState.countOwnListings() >= PhoneClientShopState.getStallLimit()) {
            return;
        }
        setPending(screen);
        ShopNetworkingClient.createListing(screen.shopPickSlot, stack.getCount(), price);
    }

    private static int marketRowCount() {
        if (!PhoneClientShopState.isMarketLoaded() || !PhoneClientShopState.isMarketAvailable()) {
            return 0;
        }
        return PhoneClientShopState.getQuotes().size() + 1;
    }

    private static void renderMarket(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.market.title"), true, false);
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        if (!PhoneClientShopState.isMarketLoaded()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.shop.loading"), null);
            renderStatus(screen, guiGraphics, layout, viewport);
            return;
        }
        if (!PhoneClientShopState.isMarketAvailable()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.market.unavailable"), null);
            renderStatus(screen, guiGraphics, layout, viewport);
            return;
        }
        List<MarketQuote> quotes = PhoneClientShopState.getQuotes();
        int count = quotes.size() + 1;
        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(count, false)));
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        UiRect ordersRow = layout.listRow(viewport, 0, screen.shopScrollOffset);
        if (ordersRow.bottom() > viewport.top && ordersRow.top < viewport.bottom()) {
            guiGraphics.fill(ordersRow.left, ordersRow.top, ordersRow.right(), ordersRow.bottom(), SURFACE);
            guiGraphics.fill(ordersRow.left, ordersRow.bottom() - 1, ordersRow.right(), ordersRow.bottom(), HAIRLINE);
            renderNavigationRow(screen, guiGraphics, layout, ordersRow,
                    Component.translatable("screen.bphone.phone.market.orders"),
                    String.valueOf(PhoneClientShopState.getOrders().size()));
        }
        for (int index = 0; index < quotes.size(); index++) {
            UiRect row = layout.listRow(viewport, index + 1, screen.shopScrollOffset);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            MarketQuote quote = quotes.get(index);
            PhoneShopSurfaceRenderer.renderListRow(screen, guiGraphics, layout, row, quote.icon(), quoteName(quote),
                    changeText(quote), changeColor(quote), PhoneShopSurfaceRenderer.formatAmount(quote.price()), ACCENT);
        }
        guiGraphics.disableScissor();
        if (quotes.isEmpty()) {
            int top = viewport.top + layout.rowHeight;
            renderEmptyState(guiGraphics, layout, new UiRect(viewport.left, top, viewport.width,
                    Math.max(1, viewport.bottom() - top)), Component.translatable("screen.bphone.phone.market.empty"), null);
        }
        renderStatus(screen, guiGraphics, layout, viewport);
    }

    private static void clickMarket(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        int index = layout.listRowAt(viewport, marketRowCount(), screen.shopScrollOffset, mouseX, mouseY);
        if (index < 0) {
            return;
        }
        if (index == 0) {
            navigate(screen, PAGE_MARKET_ORDERS);
            ShopNetworkingClient.requestMarket();
            return;
        }
        MarketQuote quote = PhoneClientShopState.getQuotes().get(index - 1);
        screen.shopMarketId = quote.id();
        screen.shopMarketBuy = true;
        screen.shopMarketQuantity = 1;
        screen.shopPriceInput = quote.price() > 0L ? String.valueOf(quote.price()) : "";
        navigate(screen, PAGE_MARKET_DETAIL);
    }

    private static MarketLayout marketLayout(PhoneScreen screen, PhoneChatLayout layout) {
        UiRect surface = layout.surface;
        int left = surface.left + layout.pad;
        int width = surface.width - 2 * layout.pad;
        int statusTop = statusTop(layout);
        UiRect submit = actionButton(layout, statusTop);
        int totalTop = submit.top - layout.u(5) - layout.bodyHeight;
        UiRect price = new UiRect(left, totalTop - layout.u(5) - layout.fieldHeight, width, layout.fieldHeight);
        int control = layout.fieldHeight;
        int quantityWidth = Math.max(control * 2, layout.u(36));
        int controlsWidth = control * 2 + quantityWidth + 2 * layout.gap;
        int controlsLeft = surface.left + (surface.width - controlsWidth) / 2;
        int controlsTop = price.top - layout.u(5) - control;
        UiRect minus = new UiRect(controlsLeft, controlsTop, control, control);
        UiRect quantity = new UiRect(minus.right() + layout.gap, controlsTop, quantityWidth, control);
        UiRect plus = new UiRect(quantity.right() + layout.gap, controlsTop, control, control);
        int toggleTop = controlsTop - layout.u(5) - layout.smallButtonHeight;
        int half = (width - layout.gap) / 2;
        UiRect buy = new UiRect(left, toggleTop, half, layout.smallButtonHeight);
        UiRect sell = new UiRect(left + width - half, toggleTop, half, layout.smallButtonHeight);
        int infoTop = PhoneShopSurfaceRenderer.listViewport(screen, layout).top + layout.sectionGap;
        return new MarketLayout(infoTop, toggleTop - layout.u(4), buy, sell, minus, quantity, plus, price, totalTop,
                submit, statusTop);
    }

    private static void renderMarketDetail(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        MarketQuote quote = PhoneClientShopState.findQuote(screen.shopMarketId);
        if (quote == null) {
            renderHeader(screen, guiGraphics, layout, layout.header(),
                    Component.translatable("screen.bphone.phone.market.title"), true, false);
            PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
            renderEmptyState(guiGraphics, layout, PhoneShopSurfaceRenderer.listViewport(screen, layout),
                    Component.translatable("screen.bphone.phone.market.empty"), null);
            return;
        }
        String name = quoteName(quote);
        renderHeader(screen, guiGraphics, layout, layout.header(), Component.literal(name), true, false);
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
        UiRect surface = layout.surface;
        MarketLayout market = marketLayout(screen, layout);
        guiGraphics.fill(surface.left, market.infoTop(), surface.right(), surface.bottom(), SURFACE);
        int left = surface.left + layout.pad;
        int textWidth = surface.width - 2 * layout.pad;

        guiGraphics.enableScissor(surface.left, market.infoTop(), surface.right(), Math.max(market.infoTop(), market.infoBottom()));
        int y = market.infoTop() + layout.u(6);
        UiRect icon = new UiRect(left, y, layout.avatar, layout.avatar);
        renderIconTile(guiGraphics, layout, quote.icon(), icon);
        int textLeft = icon.right() + layout.u(6);
        drawText(guiGraphics, layout, fit(layout, name, BOLD, surface.right() - layout.pad - textLeft, layout.bodyScale),
                textLeft, icon.top + (icon.height - layout.bodyHeight) / 2, TEXT, layout.bodyScale);
        y = icon.bottom() + layout.u(6);
        drawText(guiGraphics, layout, Component.translatable("screen.bphone.phone.market.price",
                        PhoneShopSurfaceRenderer.formatAmount(quote.price())).withStyle(BOLD),
                left, y, TEXT, layout.titleScale);
        y += layout.titleHeight + layout.u(4);
        drawText(guiGraphics, layout, Component.literal(changeText(quote)), left, y, changeColor(quote), layout.captionScale);
        y += layout.captionHeight + layout.u(3);
        drawText(guiGraphics, layout, fit(layout, Component.translatable("screen.bphone.phone.market.volume",
                        PhoneShopSurfaceRenderer.formatAmount(quote.volume())).getString(), PLAIN, textWidth, layout.captionScale),
                left, y, TEXT_SECONDARY, layout.captionScale);
        y += layout.captionHeight + layout.u(3);
        drawText(guiGraphics, layout, fit(layout, Component.translatable("screen.bphone.phone.market.bid_ask",
                        amountOrDash(quote.bestBid()), amountOrDash(quote.bestAsk())).getString(), PLAIN, textWidth,
                        layout.captionScale),
                left, y, TEXT_SECONDARY, layout.captionScale);
        guiGraphics.disableScissor();

        renderButton(screen, guiGraphics, layout, market.buy(), Component.translatable("screen.bphone.phone.market.buy"),
                screen.shopMarketBuy ? Tone.PRIMARY : Tone.SECONDARY);
        renderButton(screen, guiGraphics, layout, market.sell(), Component.translatable("screen.bphone.phone.market.sell"),
                screen.shopMarketBuy ? Tone.SECONDARY : Tone.DANGER);

        renderButton(screen, guiGraphics, layout, market.minus(), Component.literal("-"),
                screen.shopMarketQuantity > 1 ? Tone.SECONDARY : Tone.DISABLED);
        renderButton(screen, guiGraphics, layout, market.plus(), Component.literal("+"),
                screen.shopMarketQuantity < TradeNetworking.MAX_MARKET_QUANTITY ? Tone.SECONDARY : Tone.DISABLED);
        UiRect quantity = market.quantity();
        fillRounded(guiGraphics, quantity.left, quantity.top, quantity.width, quantity.height, layout.radius, FIELD);
        drawCentered(guiGraphics, layout, Component.literal(String.valueOf(screen.shopMarketQuantity)),
                quantity.left + quantity.width / 2, quantity.top + (quantity.height - layout.bodyHeight) / 2, TEXT,
                layout.bodyScale);

        long price = parsePrice(screen.shopPriceInput);
        renderField(guiGraphics, layout, market.price(), formatInput(screen.shopPriceInput),
                Component.translatable("screen.bphone.phone.market.price_placeholder"),
                screen.shopPriceInput.isEmpty() || price > 0L ? ACCENT : DANGER, FIELD);

        long total = orderTotal(screen);
        drawCentered(guiGraphics, layout, Component.translatable("screen.bphone.phone.market.total",
                        PhoneShopSurfaceRenderer.formatAmount(Math.max(0L, total))).withStyle(BOLD),
                surface.left + surface.width / 2, market.totalTop(), ACCENT, layout.bodyScale);

        Component label;
        Tone tone;
        if (screen.shopPurchasePending) {
            label = Component.translatable("screen.bphone.phone.market.status.processing");
            tone = Tone.DISABLED;
        } else if (!PhoneClientShopState.isEconomyAvailable()) {
            label = Component.translatable("screen.bphone.phone.bank.status.no_economy");
            tone = Tone.DISABLED;
        } else if (screen.shopMarketBuy && price > 0L && PhoneClientShopState.getBalance() < total) {
            label = Component.translatable("screen.bphone.phone.bank.status.insufficient");
            tone = Tone.DISABLED;
        } else {
            label = Component.translatable(screen.shopMarketBuy
                    ? "screen.bphone.phone.market.submit_buy" : "screen.bphone.phone.market.submit_sell");
            tone = price > 0L ? (screen.shopMarketBuy ? Tone.PRIMARY : Tone.DANGER) : Tone.DISABLED;
        }
        renderButton(screen, guiGraphics, layout, market.submit(), label, tone);
        renderDetailStatus(screen, guiGraphics, layout, market.statusTop());
    }

    private static void clickMarketDetail(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        MarketLayout market = marketLayout(screen, layout);
        int step = Screen.hasShiftDown() ? 10 : 1;
        if (market.buy().contains(mouseX, mouseY)) {
            screen.shopMarketBuy = true;
        } else if (market.sell().contains(mouseX, mouseY)) {
            screen.shopMarketBuy = false;
        } else if (market.minus().contains(mouseX, mouseY)) {
            adjustMarketQuantity(screen, -step);
        } else if (market.plus().contains(mouseX, mouseY)) {
            adjustMarketQuantity(screen, step);
        } else if (market.submit().contains(mouseX, mouseY)) {
            submitOrder(screen);
        }
    }

    private static void adjustMarketQuantity(PhoneScreen screen, int delta) {
        screen.shopMarketQuantity = Mth.clamp(screen.shopMarketQuantity + delta, 1, TradeNetworking.MAX_MARKET_QUANTITY);
    }

    private static long orderTotal(PhoneScreen screen) {
        long price = parsePrice(screen.shopPriceInput);
        if (price <= 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact(price, (long) screen.shopMarketQuantity);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static void submitOrder(PhoneScreen screen) {
        long price = parsePrice(screen.shopPriceInput);
        if (screen.shopPurchasePending || price <= 0L || !PhoneClientShopState.isEconomyAvailable()
                || PhoneClientShopState.findQuote(screen.shopMarketId) == null) {
            return;
        }
        if (screen.shopMarketBuy && PhoneClientShopState.getBalance() < orderTotal(screen)) {
            return;
        }
        screen.shopPurchasePending = true;
        screen.shopStatus = Component.translatable("screen.bphone.phone.market.status.processing");
        screen.shopStatusError = false;
        ShopNetworkingClient.placeOrder(screen.shopMarketId, screen.shopMarketBuy, screen.shopMarketQuantity, price);
    }

    private static void renderOrders(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout) {
        renderHeader(screen, guiGraphics, layout, layout.header(),
                Component.translatable("screen.bphone.phone.market.orders"), true, false);
        PhoneShopSurfaceRenderer.renderBalance(guiGraphics, layout);
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        List<MarketOrder> orders = PhoneClientShopState.getOrders();
        if (orders.isEmpty()) {
            renderEmptyState(guiGraphics, layout, viewport, Component.translatable("screen.bphone.phone.market.orders_empty"), null);
            renderStatus(screen, guiGraphics, layout, viewport);
            return;
        }
        screen.shopScrollOffset = Mth.clamp(screen.shopScrollOffset, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(orders.size(), false)));
        Component cancel = Component.translatable("screen.bphone.phone.market.cancel");
        guiGraphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < orders.size(); index++) {
            UiRect row = layout.listRow(viewport, index, screen.shopScrollOffset);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            MarketOrder order = orders.get(index);
            MarketQuote quote = PhoneClientShopState.findQuote(order.commodityId());
            guiGraphics.fill(row.left, row.top, row.right(), row.bottom(), SURFACE);
            guiGraphics.fill(row.left + layout.pad, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            UiRect icon = layout.rowAvatar(row, layout.avatar);
            renderIconTile(guiGraphics, layout, quote == null ? ItemStack.EMPTY : quote.icon(), icon);
            UiRect button = layout.rowAction(row, cancel);
            renderButton(screen, guiGraphics, layout, button, cancel,
                    screen.shopPurchasePending ? Tone.DISABLED : Tone.SECONDARY);
            String side = Component.translatable(order.buy()
                    ? "screen.bphone.phone.market.buy" : "screen.bphone.phone.market.sell").getString();
            String line = Component.translatable("screen.bphone.phone.market.order_line", side, order.filled(),
                    order.quantity(), PhoneShopSurfaceRenderer.formatAmount(order.price())).getString();
            renderTwoLineText(guiGraphics, layout, row, layout.rowTextLeft(row, layout.avatar), button.left - layout.gap,
                    quote == null ? order.commodityId() : quoteName(quote), TEXT, line,
                    order.buy() ? ACCENT : DANGER);
        }
        guiGraphics.disableScissor();
        renderStatus(screen, guiGraphics, layout, viewport);
    }

    private static void clickOrders(PhoneScreen screen, PhoneChatLayout layout, double mouseX, double mouseY) {
        if (screen.shopPurchasePending) {
            return;
        }
        UiRect viewport = PhoneShopSurfaceRenderer.listViewport(screen, layout);
        List<MarketOrder> orders = PhoneClientShopState.getOrders();
        int index = layout.listRowAt(viewport, orders.size(), screen.shopScrollOffset, mouseX, mouseY);
        if (index < 0) {
            return;
        }
        UiRect row = layout.listRow(viewport, index, screen.shopScrollOffset);
        if (layout.rowAction(row, Component.translatable("screen.bphone.phone.market.cancel")).contains(mouseX, mouseY)) {
            screen.shopPurchasePending = true;
            screen.shopStatus = Component.translatable("screen.bphone.phone.market.status.processing");
            screen.shopStatusError = false;
            ShopNetworkingClient.cancelOrder(orders.get(index).id());
        }
    }

    private static void setPending(PhoneScreen screen) {
        screen.shopPurchasePending = true;
        screen.shopStatus = Component.translatable("screen.bphone.phone.stall.processing");
        screen.shopStatusError = false;
    }

    private static int statusTop(PhoneChatLayout layout) {
        return layout.surface.bottom() - layout.pad - layout.captionHeight;
    }

    private static UiRect actionButton(PhoneChatLayout layout, int statusTop) {
        UiRect surface = layout.surface;
        return new UiRect(surface.left + layout.pad, statusTop - layout.u(4) - layout.buttonHeight,
                surface.width - 2 * layout.pad, layout.buttonHeight);
    }

    private static void renderDetailStatus(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, int top) {
        if (screen.shopStatus.getString().isBlank()) {
            return;
        }
        UiRect surface = layout.surface;
        drawCentered(guiGraphics, layout, fit(layout, screen.shopStatus.getString(), PLAIN,
                        surface.width - 2 * layout.pad, layout.captionScale),
                surface.left + surface.width / 2, top, screen.shopStatusError ? DANGER : SUCCESS, layout.captionScale);
    }

    private static void renderStatus(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout layout, UiRect viewport) {
        if (screen.shopStatus.getString().isBlank()) {
            return;
        }
        int height = layout.captionHeight + layout.u(8);
        UiRect strip = new UiRect(viewport.left, viewport.bottom() - height, viewport.width, height);
        guiGraphics.fill(strip.left, strip.top, strip.right(), strip.bottom(), SURFACE);
        guiGraphics.fill(strip.left, strip.top, strip.right(), strip.top + 1, HAIRLINE);
        drawCentered(guiGraphics, layout, fit(layout, screen.shopStatus.getString(), PLAIN,
                        strip.width - 2 * layout.pad, layout.captionScale),
                strip.left + strip.width / 2, strip.top + (strip.height - layout.captionHeight) / 2,
                screen.shopStatusError ? DANGER : SUCCESS, layout.captionScale);
    }

    private static void renderIconTile(GuiGraphics guiGraphics, PhoneChatLayout layout, ItemStack stack, UiRect icon) {
        fillRounded(guiGraphics, icon.left, icon.top, icon.width, icon.height, Math.max(2, layout.radius * 2), FIELD);
        int inset = Math.max(2, icon.width / 8);
        renderItem(guiGraphics, stack, new UiRect(icon.left + inset, icon.top + inset,
                icon.width - 2 * inset, icon.height - 2 * inset));
    }

    private static String itemLabel(ItemStack stack) {
        return stack.getHoverName().getString() + " x" + stack.getCount();
    }

    private static String quoteName(MarketQuote quote) {
        String name = quote.name() == null ? "" : quote.name().getString();
        return name.isBlank() ? quote.id() : name;
    }

    private static String changeText(MarketQuote quote) {
        if (quote.previousPrice() <= 0L) {
            return "--";
        }
        long diff = quote.price() - quote.previousPrice();
        double percent = diff * 100.0D / quote.previousPrice();
        return String.format(Locale.ROOT, "%+,d  %+.2f%%", diff, percent);
    }

    private static int changeColor(MarketQuote quote) {
        if (quote.previousPrice() <= 0L || quote.price() == quote.previousPrice()) {
            return TEXT_TERTIARY;
        }
        return quote.price() > quote.previousPrice() ? DANGER : SUCCESS;
    }

    private static String amountOrDash(long amount) {
        return amount > 0L ? PhoneShopSurfaceRenderer.formatAmount(amount) : "--";
    }

    private static long parsePrice(String input) {
        if (input == null || input.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private static String formatInput(String input) {
        long value = parsePrice(input);
        return value > 0L ? PhoneShopSurfaceRenderer.formatAmount(value) : input;
    }
}
