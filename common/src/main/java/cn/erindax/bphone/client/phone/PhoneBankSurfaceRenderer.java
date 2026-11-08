package cn.erindax.bphone.client.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneBankSurfaceRenderer {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final int SCAN_SHADE = 0xB8141A22;
    private static final int SCAN_SUCCESS = 0xFF06C755;
    private static final int CARD_TEXT_SOFT = 0xCCFFFFFF;
    private static final int QR_DARK = 0xFF1A1F26;

    private PhoneBankSurfaceRenderer() {
    }

    static void renderBankSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneBankLayout layout = new PhoneBankLayout(screen);
        UiRect surface = layout.ui.surface;
        guiGraphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);
        renderHeader(screen, guiGraphics, layout.ui, layout.header, title(screen), true, false);

        switch (screen.bankPage) {
            case PhoneScreen.BANK_PAGE_TRANSFER -> renderForm(screen, guiGraphics, layout, false);
            case PhoneScreen.BANK_PAGE_PAYMENT -> renderForm(screen, guiGraphics, layout, true);
            case PhoneScreen.BANK_PAGE_RECEIVE -> renderReceivePage(screen, guiGraphics, layout);
            case PhoneScreen.BANK_PAGE_SLIP -> renderSlipPage(screen, guiGraphics, layout);
            default -> renderHomePage(screen, guiGraphics, layout);
        }
    }

    static void renderBankCameraOverlay(PhoneScreen screen, GuiGraphics guiGraphics) {
        PhoneBankLayout layout = new PhoneBankLayout(screen);
        PhoneChatLayout ui = layout.ui;
        UiRect camera = screen.getCameraViewBounds();
        int headerHeight = layout.scanHeaderHeight(camera);
        int footerTop = layout.scanFooterTop(camera);
        int bleed = Math.max(4, ui.u(6));
        guiGraphics.fill(camera.left - bleed, camera.top, camera.right() + bleed, camera.top + headerHeight, SCAN_SHADE);
        guiGraphics.fill(camera.left - bleed, footerTop, camera.right() + bleed, camera.bottom(), SCAN_SHADE);

        int centerX = camera.left + camera.width / 2;
        int textWidth = camera.width - 2 * ui.pad;
        int titleY = camera.top + (headerHeight - ui.titleHeight - ui.u(5)) / 2;
        Component title = fit(ui, Component.translatable("screen.bphone.phone.bank.scan_title").getString(), BOLD,
                textWidth, ui.titleScale);
        drawCentered(guiGraphics, ui, title, centerX, titleY, WHITE, ui.titleScale);
        int underlineWidth = Math.max(ui.u(16), ui.width(title, ui.titleScale) / 2);
        int underlineY = titleY + ui.titleHeight + ui.u(3);
        guiGraphics.fill(centerX - underlineWidth / 2, underlineY,
                centerX - underlineWidth / 2 + underlineWidth, underlineY + Math.max(1, ui.u(1.5F)), ACCENT);

        drawWrappedCentered(guiGraphics, ui, Component.translatable("screen.bphone.phone.bank.scan_hint"),
                centerX, footerTop + ui.u(8), textWidth, WHITE, ui.captionScale);

        UiRect receive = layout.scanReceive(camera);
        int availableTop = camera.top + headerHeight + ui.u(14);
        int availableBottom = footerTop - ui.u(14);
        int availableHeight = Math.max(ui.u(24), availableBottom - availableTop);
        int size = Math.min(camera.width - ui.u(34), Math.min(availableHeight, ui.u(92)));
        int left = camera.left + (camera.width - size) / 2;
        int top = availableTop + Math.max(0, (availableHeight - size) / 2);
        int right = left + size;
        int bottom = top + size;
        int cornerLength = Math.max(8, ui.u(16));
        int thickness = Math.max(1, ui.u(2));
        int shadow = 0x66000000;
        int color = screen.bankScanHoverTicks > 0 ? SCAN_SUCCESS : 0xEFFFFFFF;
        drawCorner(guiGraphics, left + 1, top + 1, cornerLength, thickness, true, true, shadow);
        drawCorner(guiGraphics, right + 1, top + 1, cornerLength, thickness, false, true, shadow);
        drawCorner(guiGraphics, left + 1, bottom + 1, cornerLength, thickness, true, false, shadow);
        drawCorner(guiGraphics, right + 1, bottom + 1, cornerLength, thickness, false, false, shadow);
        drawCorner(guiGraphics, left, top, cornerLength, thickness, true, true, color);
        drawCorner(guiGraphics, right, top, cornerLength, thickness, false, true, color);
        drawCorner(guiGraphics, left, bottom, cornerLength, thickness, true, false, color);
        drawCorner(guiGraphics, right, bottom, cornerLength, thickness, false, false, color);
        if (screen.bankScanHoverTicks > 0) {
            float progress = Mth.clamp(screen.bankScanHoverTicks / (float) PhoneScreen.BANK_SCAN_HOLD_TICKS, 0.0F, 1.0F);
            int fillHeight = Math.round((size - 2 * thickness) * progress);
            guiGraphics.fill(left + thickness, bottom - thickness - fillHeight,
                    right - thickness, bottom - thickness, 0x3306C755);
        }

        renderButton(screen, guiGraphics, ui, receive, Component.translatable("screen.bphone.phone.bank.receive_qr"),
                Tone.PRIMARY);
        UiRect eye = layout.scanEye(camera);
        fillRounded(guiGraphics, eye.left, eye.top, eye.width, eye.height, ui.radius,
                hovered(screen, eye) ? 0xB3000000 : 0x80000000);
        drawEye(guiGraphics, eye, WHITE, QR_DARK);
    }

    private static Component title(PhoneScreen screen) {
        return Component.translatable(switch (screen.bankPage) {
            case PhoneScreen.BANK_PAGE_TRANSFER -> "screen.bphone.phone.bank.transfer";
            case PhoneScreen.BANK_PAGE_PAYMENT -> "screen.bphone.phone.bank.scan_title";
            case PhoneScreen.BANK_PAGE_RECEIVE -> "screen.bphone.phone.bank.receive_qr";
            case PhoneScreen.BANK_PAGE_SLIP -> "screen.bphone.phone.bank.slip_title";
            default -> "screen.bphone.phone.bank.title";
        });
    }

    private static void renderHomePage(PhoneScreen screen, GuiGraphics guiGraphics, PhoneBankLayout layout) {
        PhoneChatLayout ui = layout.ui;
        UiRect card = layout.card();
        fillRounded(guiGraphics, card.left, card.top, card.width, card.height, 2 * ui.radius, ACCENT);
        int inner = ui.u(10);
        int textLeft = card.left + inner;
        int textWidth = card.width - 2 * inner;
        int y = card.top + inner;

        Minecraft minecraft = Minecraft.getInstance();
        UiRect face = new UiRect(textLeft, y, ui.avatarSmall, ui.avatarSmall);
        renderFace(guiGraphics, ownSkin(minecraft), face, ACCENT);
        String name = minecraft.player == null ? "" : minecraft.player.getName().getString();
        int nameLeft = face.right() + ui.gap;
        drawText(guiGraphics, ui, fit(ui, name, BOLD, card.right() - inner - nameLeft, ui.bodyScale),
                nameLeft, y + (ui.avatarSmall - ui.bodyHeight) / 2, WHITE, ui.bodyScale);

        y = face.bottom() + ui.u(8);
        drawText(guiGraphics, ui, fit(ui, Component.translatable("screen.bphone.phone.bank.balance").getString(), PLAIN,
                textWidth, ui.captionScale), textLeft, y, CARD_TEXT_SOFT, ui.captionScale);
        y += ui.captionHeight + ui.textGap;
        drawAmount(guiGraphics, layout, NUMBER_FORMAT.format(screen.bankBalance), textLeft, y, textWidth, false, WHITE);
        y += layout.heroHeight + ui.u(6);
        String account = Component.translatable("screen.bphone.phone.bank.account").getString() + "  "
                + screen.getOwnPhoneNumber();
        drawText(guiGraphics, ui, fit(ui, account, PLAIN, textWidth, ui.captionScale), textLeft, y, CARD_TEXT_SOFT,
                ui.captionScale);

        renderTile(screen, guiGraphics, layout, 0, "screen.bphone.phone.bank.transfer");
        renderTile(screen, guiGraphics, layout, 1, "screen.bphone.phone.bank.scan");
        renderTile(screen, guiGraphics, layout, 2, "screen.bphone.phone.bank.receive_title");
        renderStatus(screen, guiGraphics, ui, layout.homeStatusTop());
    }

    private static void renderTile(PhoneScreen screen, GuiGraphics guiGraphics, PhoneBankLayout layout, int index,
                                   String key) {
        PhoneChatLayout ui = layout.ui;
        UiRect tile = layout.tile(index);
        fillRounded(guiGraphics, tile.left, tile.top, tile.width, tile.height, 2 * ui.radius,
                hovered(screen, tile) ? HOVER : SURFACE);
        int icon = layout.tileIcon();
        int x = tile.left + (tile.width - icon) / 2;
        int y = tile.top + ui.u(10);
        int thickness = Math.max(1, Math.round(icon / 9.0F));
        switch (index) {
            case 0 -> drawTransferGlyph(guiGraphics, x, y, icon, thickness);
            case 1 -> drawScanGlyph(guiGraphics, x, y, icon, thickness);
            default -> drawQrGlyph(guiGraphics, x, y, icon);
        }
        Component label = fit(ui, Component.translatable(key).getString(), PLAIN, tile.width - 2 * ui.u(3), ui.bodyScale);
        drawCentered(guiGraphics, ui, label, tile.left + tile.width / 2, y + icon + ui.u(6), TEXT, ui.bodyScale);
    }

    private static void renderForm(PhoneScreen screen, GuiGraphics guiGraphics, PhoneBankLayout layout, boolean payment) {
        PhoneChatLayout ui = layout.ui;
        int labelLeft = layout.left + ui.u(2);
        if (payment) {
            UiRect payee = layout.payee();
            fillRounded(guiGraphics, payee.left, payee.top, payee.width, payee.height, 2 * ui.radius, SURFACE);
            String name = screen.bankPaymentName == null || screen.bankPaymentName.isBlank()
                    ? screen.bankTransferNumber
                    : screen.bankPaymentName;
            renderGroupAvatar(guiGraphics, ui, ui.rowAvatar(payee, ui.avatar), name);
            String detail = Component.translatable("screen.bphone.phone.bank.pay_to").getString() + "  "
                    + screen.bankTransferNumber;
            renderTwoLineText(guiGraphics, ui, payee, ui.rowTextLeft(payee, ui.avatar), payee.right() - ui.pad,
                    name, TEXT, detail, TEXT_SECONDARY);
        } else {
            drawText(guiGraphics, ui, Component.translatable("screen.bphone.phone.bank.target"), labelLeft,
                    layout.numberLabelTop(), TEXT_SECONDARY, ui.captionScale);
            boolean numberFocused = !screen.bankAmountFocused;
            renderField(guiGraphics, ui, layout.numberField(), screen.bankTransferNumber,
                    Component.translatable("screen.bphone.phone.bank.number_placeholder"),
                    numberFocused ? ACCENT : HAIRLINE, SURFACE, numberFocused);
        }

        drawText(guiGraphics, ui, Component.translatable("screen.bphone.phone.bank.amount"), labelLeft,
                layout.amountLabelTop(), TEXT_SECONDARY, ui.captionScale);
        boolean amountFocused = payment || screen.bankAmountFocused;
        String amount = screen.bankTransferAmount.isBlank() ? "" : NUMBER_FORMAT.format(screen.getBankTransferAmount());
        renderField(guiGraphics, ui, layout.amountField(), amount,
                Component.literal("0"), amountFocused ? ACCENT : HAIRLINE, SURFACE, amountFocused);
        String available = Component.translatable("screen.bphone.phone.bank.balance").getString() + "  "
                + NUMBER_FORMAT.format(screen.bankBalance);
        drawText(guiGraphics, ui, fit(ui, available, PLAIN, layout.width, ui.captionScale), labelLeft,
                layout.availableTop(), TEXT_TERTIARY, ui.captionScale);

        renderOutlineButton(screen, guiGraphics, ui, layout.cancel(),
                Component.translatable("screen.bphone.phone.bank.cancel"));
        renderButton(screen, guiGraphics, ui, layout.confirm(), Component.translatable("screen.bphone.phone.bank.confirm"),
                screen.canSendBankTransfer() ? Tone.PRIMARY : Tone.DISABLED);
        renderStatus(screen, guiGraphics, ui, layout.formStatusTop());
    }

    private static void renderReceivePage(PhoneScreen screen, GuiGraphics guiGraphics, PhoneBankLayout layout) {
        PhoneChatLayout ui = layout.ui;
        UiRect card = layout.receiveCard();
        fillRounded(guiGraphics, card.left, card.top, card.width, card.height, 2 * ui.radius, SURFACE);
        int centerX = card.left + card.width / 2;
        int textWidth = card.width - 2 * ui.pad;
        Component title = fit(ui, Component.translatable("screen.bphone.phone.bank.receive_title").getString(), BOLD,
                textWidth, ui.bodyScale);
        drawCentered(guiGraphics, ui, title, centerX, card.top + ui.u(10), TEXT, ui.bodyScale);

        UiRect qr = layout.receiveQr();
        drawQr(guiGraphics, qr, screen.getOwnPhoneNumber());
        int y = qr.bottom() + ui.u(8);
        drawCentered(guiGraphics, ui, fit(ui, screen.getOwnPhoneNumber(), BOLD, textWidth, ui.titleScale), centerX, y,
                TEXT, ui.titleScale);
        y += ui.titleHeight + ui.u(6);
        drawWrappedCentered(guiGraphics, ui, Component.translatable("screen.bphone.phone.bank.receive_hint"), centerX, y,
                textWidth, TEXT_SECONDARY, ui.captionScale);

        renderOutlineButton(screen, guiGraphics, ui, layout.cancel(),
                Component.translatable("screen.bphone.phone.bank.cancel"));
        UiRect eye = layout.receiveEye();
        renderButton(screen, guiGraphics, ui, eye, Component.empty(), Tone.PRIMARY);
        drawEye(guiGraphics, eye, WHITE, ACCENT);
    }

    private static void renderSlipPage(PhoneScreen screen, GuiGraphics guiGraphics, PhoneBankLayout layout) {
        PhoneChatLayout ui = layout.ui;
        UiRect card = layout.slipCard();
        fillRounded(guiGraphics, card.left, card.top, card.width, card.height, 2 * ui.radius, SURFACE);
        int centerX = card.left + card.width / 2;
        int textWidth = card.width - 2 * ui.pad;
        int check = layout.slipCheck();
        int y = card.top + ui.u(12);
        fillRounded(guiGraphics, centerX - check / 2, y, check, check, check / 2, SUCCESS);
        drawCheckMark(guiGraphics, centerX - check / 2, y, check);
        y += check + ui.u(8);
        Component done = fit(ui, Component.translatable("screen.bphone.phone.bank.status.transfer_done").getString(),
                BOLD, textWidth, ui.bodyScale);
        drawCentered(guiGraphics, ui, done, centerX, y, TEXT, ui.bodyScale);
        y += ui.bodyHeight + ui.u(6);
        drawAmount(guiGraphics, layout, NUMBER_FORMAT.format(screen.bankSlipAmount), card.left + ui.pad, y, textWidth,
                true, TEXT);
        y += layout.heroHeight + ui.u(10);
        guiGraphics.fill(card.left + ui.pad, y, card.right() - ui.pad, y + 1, HAIRLINE);
        y += ui.u(8);

        String to = screen.bankSlipTargetName == null || screen.bankSlipTargetName.isBlank()
                ? screen.bankSlipTargetNumber
                : screen.bankSlipTargetName + " (" + screen.bankSlipTargetNumber + ")";
        y = renderSlipRow(guiGraphics, ui, card, y, "screen.bphone.phone.bank.slip_from", screen.getOwnPhoneNumber());
        y = renderSlipRow(guiGraphics, ui, card, y, "screen.bphone.phone.bank.slip_to", to);
        y = renderSlipRow(guiGraphics, ui, card, y, "screen.bphone.phone.bank.slip_time", screen.bankSlipTime);
        renderSlipRow(guiGraphics, ui, card, y, "screen.bphone.phone.bank.slip_balance",
                NUMBER_FORMAT.format(screen.bankSlipBalance));

        renderButton(screen, guiGraphics, ui, layout.cancel(), Component.translatable("screen.bphone.phone.bank.done"),
                Tone.PRIMARY);
    }

    private static int renderSlipRow(GuiGraphics guiGraphics, PhoneChatLayout ui, UiRect card, int y, String key,
                                     String value) {
        int left = card.left + ui.pad;
        int right = card.right() - ui.pad;
        Component label = Component.translatable(key);
        int labelWidth = ui.width(label, ui.captionScale);
        drawText(guiGraphics, ui, label, left, y + (ui.bodyHeight - ui.captionHeight) / 2, TEXT_SECONDARY,
                ui.captionScale);
        Component fitted = fit(ui, value, PLAIN, right - left - labelWidth - ui.gap, ui.bodyScale);
        drawText(guiGraphics, ui, fitted, right - ui.width(fitted, ui.bodyScale), y, TEXT, ui.bodyScale);
        return y + ui.bodyHeight + ui.u(8);
    }

    private static void renderStatus(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout ui, int top) {
        if (screen.bankStatus == null || screen.bankStatus.getString().isBlank()) {
            return;
        }
        drawWrappedCentered(guiGraphics, ui, screen.bankStatus, ui.surface.left + ui.surface.width / 2, top,
                ui.surface.width - 4 * ui.pad, TEXT_SECONDARY, ui.captionScale);
    }

    private static void renderOutlineButton(PhoneScreen screen, GuiGraphics guiGraphics, PhoneChatLayout ui,
                                            UiRect bounds, Component label) {
        fillRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, ui.radius, HAIRLINE);
        fillRounded(guiGraphics, bounds.left + 1, bounds.top + 1, bounds.width - 2, bounds.height - 2,
                Math.max(1, ui.radius - 1), hovered(screen, bounds) ? HOVER : SURFACE);
        Component fitted = fit(ui, label.getString(), PLAIN, bounds.width - 2 * ui.u(3), ui.bodyScale);
        drawCentered(guiGraphics, ui, fitted, bounds.left + bounds.width / 2,
                bounds.top + (bounds.height - ui.bodyHeight) / 2, TEXT, ui.bodyScale);
    }

    private static void drawAmount(GuiGraphics guiGraphics, PhoneBankLayout layout, String amount, int left, int top,
                                   int width, boolean centered, int color) {
        PhoneChatLayout ui = layout.ui;
        Component text = Component.literal(amount).withStyle(BOLD);
        float scale = ui.width(text, layout.heroScale) <= width ? layout.heroScale : ui.titleScale;
        Component fitted = fit(ui, amount, BOLD, width, scale);
        int y = top + layout.heroHeight - Math.max(1, Math.round(8.0F * scale));
        int x = centered ? left + (width - ui.width(fitted, scale)) / 2 : left;
        drawText(guiGraphics, ui, fitted, x, y, color, scale);
    }

    private static ResourceLocation ownSkin(Minecraft minecraft) {
        return minecraft.player == null ? DefaultPlayerSkin.getDefaultTexture() : minecraft.player.getSkin().texture();
    }

    private static void drawTransferGlyph(GuiGraphics guiGraphics, int x, int y, int size, int thickness) {
        int left = x + size / 6;
        int right = x + size - size / 6 - thickness;
        int upper = y + size * 3 / 8 - thickness / 2;
        int lower = y + size * 5 / 8 - thickness / 2;
        int head = Math.max(2, size / 5);
        drawLine(guiGraphics, left, upper, right, upper, thickness, ACCENT);
        drawLine(guiGraphics, right - head, upper - head, right, upper, thickness, ACCENT);
        drawLine(guiGraphics, left, lower, right, lower, thickness, ACCENT);
        drawLine(guiGraphics, left, lower, left + head, lower + head, thickness, ACCENT);
    }

    private static void drawScanGlyph(GuiGraphics guiGraphics, int x, int y, int size, int thickness) {
        int length = Math.max(2, size * 3 / 10);
        drawCorner(guiGraphics, x, y, length, thickness, true, true, ACCENT);
        drawCorner(guiGraphics, x + size, y, length, thickness, false, true, ACCENT);
        drawCorner(guiGraphics, x, y + size, length, thickness, true, false, ACCENT);
        drawCorner(guiGraphics, x + size, y + size, length, thickness, false, false, ACCENT);
        int lineTop = y + (size - thickness) / 2;
        guiGraphics.fill(x + 2 * thickness, lineTop, x + size - 2 * thickness, lineTop + thickness, ACCENT);
    }

    private static void drawQrGlyph(GuiGraphics guiGraphics, int x, int y, int size) {
        int finder = Math.max(3, size * 3 / 8);
        int ring = Math.max(1, finder / 6);
        drawFinder(guiGraphics, x, y, finder, ring, ACCENT);
        drawFinder(guiGraphics, x + size - finder, y, finder, ring, ACCENT);
        drawFinder(guiGraphics, x, y + size - finder, finder, ring, ACCENT);
        int dot = Math.max(1, (size - finder) / 4);
        int dotLeft = x + size - finder;
        int dotTop = y + size - finder;
        guiGraphics.fill(dotLeft, dotTop, dotLeft + dot, dotTop + dot, ACCENT);
        guiGraphics.fill(x + size - dot, dotTop, x + size, dotTop + dot, ACCENT);
        guiGraphics.fill(dotLeft + (finder - dot) / 2, dotTop + (finder - dot) / 2,
                dotLeft + (finder + dot) / 2, dotTop + (finder + dot) / 2, ACCENT);
        guiGraphics.fill(dotLeft, y + size - dot, dotLeft + dot, y + size, ACCENT);
        guiGraphics.fill(x + size - dot, y + size - dot, x + size, y + size, ACCENT);
    }

    private static void drawFinder(GuiGraphics guiGraphics, int x, int y, int size, int thickness, int color) {
        guiGraphics.fill(x, y, x + size, y + thickness, color);
        guiGraphics.fill(x, y + size - thickness, x + size, y + size, color);
        guiGraphics.fill(x, y + thickness, x + thickness, y + size - thickness, color);
        guiGraphics.fill(x + size - thickness, y + thickness, x + size, y + size - thickness, color);
        int inner = Math.max(1, size - 4 * thickness);
        int offset = (size - inner) / 2;
        guiGraphics.fill(x + offset, y + offset, x + offset + inner, y + offset + inner, color);
    }

    private static void drawCheckMark(GuiGraphics guiGraphics, int x, int y, int size) {
        int thickness = Math.max(1, size / 8);
        int x0 = x + size * 27 / 100;
        int y0 = y + size / 2;
        int xm = x + size * 43 / 100;
        int ym = y + size * 65 / 100;
        int x1 = x + size * 72 / 100;
        int y1 = y + size * 34 / 100;
        drawLine(guiGraphics, x0, y0, xm, ym, thickness, WHITE);
        drawLine(guiGraphics, xm, ym, x1, y1, thickness, WHITE);
    }

    private static void drawEye(GuiGraphics guiGraphics, UiRect bounds, int color, int pupilColor) {
        int size = Math.min(bounds.width, bounds.height);
        int eyeWidth = Math.max(7, Math.round(size * 0.56F));
        int eyeHeight = Math.max(5, Math.round(eyeWidth * 0.56F));
        int left = bounds.left + (bounds.width - eyeWidth) / 2;
        int top = bounds.top + (bounds.height - eyeHeight) / 2;
        for (int row = 0; row < eyeHeight; row++) {
            float offset = Math.abs((row + 0.5F) / eyeHeight * 2.0F - 1.0F);
            int inset = Math.round(eyeWidth / 2.0F * (1.0F - (float) Math.sqrt(1.0F - offset)));
            guiGraphics.fill(left + inset, top + row, left + eyeWidth - inset, top + row + 1, color);
        }
        int pupil = Math.max(2, Math.round(eyeHeight * 0.6F));
        if ((eyeHeight - pupil) % 2 != 0) {
            pupil++;
        }
        int pupilWidth = (eyeWidth - pupil) % 2 != 0 ? pupil - 1 : pupil;
        pupilWidth = Math.max(2, pupilWidth);
        int pupilLeft = left + (eyeWidth - pupilWidth) / 2;
        int pupilTop = top + (eyeHeight - pupil) / 2;
        guiGraphics.fill(pupilLeft, pupilTop, pupilLeft + pupilWidth, pupilTop + pupil, pupilColor);
    }

    private static void drawQr(GuiGraphics guiGraphics, UiRect plate, String seed) {
        int density = pixelDensity();
        int platePixels = plate.width * density;
        int module = Math.max(1, platePixels / PhoneBankLayout.QR_CELLS);
        fillRounded(guiGraphics, plate.left, plate.top, plate.width, plate.height, Math.max(1, module / density), WHITE);
        int codeSize = module * PhoneBankLayout.QR_MODULES;
        int originX = plate.left * density + (platePixels - codeSize) / 2;
        int originY = plate.top * density + (plate.height * density - codeSize) / 2;
        Random random = new Random(seed == null ? 0L : seed.hashCode());
        int last = PhoneBankLayout.QR_MODULES - 1;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        guiGraphics.drawManaged(() -> {
            for (int row = 0; row < PhoneBankLayout.QR_MODULES; row++) {
                for (int col = 0; col < PhoneBankLayout.QR_MODULES; col++) {
                    boolean dark;
                    int finderRow = row < 8 ? row : row > last - 8 ? row - (last - 6) : -1;
                    int finderCol = col < 8 ? col : col > last - 8 ? col - (last - 6) : -1;
                    boolean inFinderZone = finderRow >= -1 && finderCol >= -1
                            && (row < 8 || row > last - 8) && (col < 8 || col > last - 8)
                            && !(row > last - 8 && col > last - 8);
                    if (inFinderZone) {
                        dark = finderModule(row < 8 ? row : finderRow, col < 8 ? col : finderCol);
                    } else if (row == 6 || col == 6) {
                        dark = (row + col) % 2 == 0;
                    } else {
                        dark = random.nextInt(100) < 47;
                    }
                    if (dark) {
                        int x = originX + col * module;
                        int y = originY + row * module;
                        guiGraphics.fill(x, y, x + module, y + module, QR_DARK);
                    }
                }
            }
        });
        guiGraphics.pose().popPose();
    }

    private static boolean finderModule(int row, int col) {
        if (row < 0 || col < 0 || row > 6 || col > 6) {
            return false;
        }
        boolean ring = row == 0 || row == 6 || col == 0 || col == 6;
        boolean core = row >= 2 && row <= 4 && col >= 2 && col <= 4;
        return ring || core;
    }
}
