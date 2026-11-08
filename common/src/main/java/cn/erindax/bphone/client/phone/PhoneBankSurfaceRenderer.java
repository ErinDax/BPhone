package cn.erindax.bphone.client.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.Locale;

final class PhoneBankSurfaceRenderer {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final int PAGE_FILL = 0xFFF0F4F8;
    private static final int HEADER_FILL = 0xFF1E3A8A;
    private static final int HEADER_GRADIENT = 0xFF3B82F6;
    private static final int CARD_FILL = 0xFFFFFFFF;
    private static final int CARD_SHADOW = 0x20000000;
    private static final int CARD_EDGE = 0xFFE5E7EB;
    private static final int FIELD_FILL = 0xFFFAFAFA;
    private static final int FIELD_EDGE = 0xFFD1D5DB;
    private static final int BUTTON_FILL = 0xFF3B82F6;
    private static final int BUTTON_EDGE = 0xFF1E40AF;
    private static final int BUTTON_SECONDARY_FILL = 0xFF9CA3AF;
    private static final int BUTTON_SECONDARY_EDGE = 0xFF4B5563;
    private static final int BUTTON_DISABLED = 0xFFD1D5DB;
    private static final int BUTTON_GRADIENT_TOP = 0x30FFFFFF;
    private static final int FOOTER_FILL = 0xFF374151;
    private static final int TEXT_DARK = 0xFF111827;
    private static final int TEXT_MUTED = 0xFF6B7280;
    private static final int TEXT_LIGHT = 0xFFFFFFFF;
    private static final int BALANCE_HIGHLIGHT = 0xFF10B981;
    private static final int OVERLAY_FILL = 0xB8141A22;

    private PhoneBankSurfaceRenderer() {
    }

    static void renderBankSurface(PhoneScreen screen, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Font font = minecraft.font;
        UiRect contentBounds = screen.getBankSurfaceBounds();
        int horizontalBleed = getHorizontalBleed(screen);
        guiGraphics.fill(contentBounds.left - horizontalBleed, contentBounds.top,
                contentBounds.right() + horizontalBleed, contentBounds.bottom(), PAGE_FILL);
        drawHeader(screen, guiGraphics, font);

        switch (screen.bankPage) {
            case PhoneScreen.BANK_PAGE_TRANSFER -> renderTransferPage(screen, guiGraphics, font);
            case PhoneScreen.BANK_PAGE_PAYMENT -> renderPaymentPage(screen, guiGraphics, font);
            case PhoneScreen.BANK_PAGE_RECEIVE -> renderReceivePage(screen, guiGraphics, font);
            case PhoneScreen.BANK_PAGE_SLIP -> renderSlipPage(screen, guiGraphics, font);
            default -> renderHomePage(screen, guiGraphics, font);
        }
    }

    static void renderBankCameraOverlay(PhoneScreen screen, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Font font = minecraft.font;
        UiRect cameraBounds = screen.getCameraViewBounds();
        UiRect receiveBounds = screen.getBankScanReceiveButtonBounds();
        UiRect eyeBounds = screen.getBankScanEyeButtonBounds();
        int headerHeight = Math.max(42, Math.round(cameraBounds.height * 0.16F));
        int footerHeight = Math.max(58, Math.round(cameraBounds.height * 0.24F));
        int footerTop = cameraBounds.bottom() - footerHeight;
        int horizontalBleed = getHorizontalBleed(screen);
        int overlayLeft = cameraBounds.left - horizontalBleed;
        int overlayRight = cameraBounds.right() + horizontalBleed;
        guiGraphics.fill(overlayLeft, cameraBounds.top,
                overlayRight, cameraBounds.top + headerHeight, OVERLAY_FILL);
        guiGraphics.fill(overlayLeft, footerTop,
                overlayRight, cameraBounds.bottom(), OVERLAY_FILL);

        drawCentered(guiGraphics, font, Component.translatable("screen.bphone.phone.bank.scan_title"),
                cameraBounds.left,
                cameraBounds.top + Math.round(headerHeight * 0.33F),
                cameraBounds.width, TEXT_LIGHT, 0.48F);
        int underlineWidth = Math.max(42, Math.round(cameraBounds.width * 0.23F));
        int underlineX = cameraBounds.left + (cameraBounds.width - underlineWidth) / 2;
        int underlineY = cameraBounds.top + Math.round(headerHeight * 0.64F);
        guiGraphics.fill(underlineX, underlineY, underlineX + underlineWidth,
                underlineY + Math.max(1, Math.round(1 * screen.scale)), 0xCCFFFFFF);
        drawCentered(guiGraphics, font, Component.translatable("screen.bphone.phone.bank.scan_hint"),
                cameraBounds.left,
                footerTop + Math.max(8, Math.round(10 * screen.scale)),
                cameraBounds.width, TEXT_LIGHT, 0.32F);

        drawScanFrame(guiGraphics, cameraBounds, headerHeight, footerHeight, screen.scale);
        drawButton(guiGraphics, font, receiveBounds, Component.translatable("screen.bphone.phone.bank.receive_qr"),
                true, screen.scale);
        drawEyeButton(guiGraphics, font, eyeBounds, screen.scale);
    }

    private static void renderHomePage(PhoneScreen screen, GuiGraphics guiGraphics, Font font) {
        drawFooter(screen, guiGraphics);
        drawAccountCard(screen, guiGraphics, font, screen.getBankAccountBounds());

        UiRect balanceBounds = screen.getBankBalanceBounds();
        drawCard(guiGraphics, balanceBounds);
        drawLabelValue(guiGraphics, font, balanceBounds,
                Component.translatable("screen.bphone.phone.bank.balance"),
                Component.literal(NUMBER_FORMAT.format(screen.bankBalance)),
                screen.scale, 0.48F, 0.72F);

        drawButton(guiGraphics, font, screen.getBankHomeTransferButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.transfer"), true, screen.scale);
        drawSecondaryButton(guiGraphics, font, screen.getBankHomeScanButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.scan"), true, screen.scale);
        drawStatus(screen, guiGraphics, font, screen.getBankHomeScanButtonBounds().bottom());
    }

    private static void renderTransferPage(PhoneScreen screen, GuiGraphics guiGraphics, Font font) {
        drawFooter(screen, guiGraphics);
        drawField(guiGraphics, font, screen.getBankTransferNumberBounds(),
                Component.translatable("screen.bphone.phone.bank.target"),
                screen.bankTransferNumber.isBlank()
                        ? Component.empty()
                        : Component.literal(screen.bankTransferNumber),
                screen.scale);
        drawField(guiGraphics, font, screen.getBankTransferAmountBounds(),
                Component.translatable("screen.bphone.phone.bank.amount"),
                amountText(screen), screen.scale);
        drawButton(guiGraphics, font, screen.getBankTransferButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.confirm"),
                screen.canSendBankTransfer(), screen.scale);
        drawSecondaryButton(guiGraphics, font, screen.getBankCancelButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.cancel"), true, screen.scale);
        drawStatus(screen, guiGraphics, font, screen.getBankCancelButtonBounds().bottom());
    }

    private static void renderPaymentPage(PhoneScreen screen, GuiGraphics guiGraphics, Font font) {
        drawFooter(screen, guiGraphics);
        UiRect balanceBounds = screen.getBankPaymentBalanceBounds();
        drawCard(guiGraphics, balanceBounds);
        drawLabelValue(guiGraphics, font, balanceBounds,
                Component.translatable("screen.bphone.phone.bank.balance"),
                Component.literal(NUMBER_FORMAT.format(screen.bankBalance)),
                screen.scale, 0.42F, 0.58F);

        UiRect targetBounds = screen.getBankPaymentTargetBounds();
        drawCard(guiGraphics, targetBounds);
        String target = screen.bankPaymentName == null || screen.bankPaymentName.isBlank()
                ? screen.bankTransferNumber
                : screen.bankPaymentName + " (" + screen.bankTransferNumber + ")";
        drawLabelValue(guiGraphics, font, targetBounds,
                Component.translatable("screen.bphone.phone.bank.pay_to"),
                Component.literal(target), screen.scale, 0.42F, 0.50F);

        drawField(guiGraphics, font, screen.getBankTransferAmountBounds(),
                Component.translatable("screen.bphone.phone.bank.amount"),
                amountText(screen), screen.scale);
        drawButton(guiGraphics, font, screen.getBankTransferButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.confirm"),
                screen.canSendBankTransfer(), screen.scale);
        drawSecondaryButton(guiGraphics, font, screen.getBankCancelButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.cancel"), true, screen.scale);
        drawStatus(screen, guiGraphics, font, screen.getBankCancelButtonBounds().bottom());
    }

    private static void renderReceivePage(PhoneScreen screen, GuiGraphics guiGraphics, Font font) {
        drawFooter(screen, guiGraphics);
        UiRect cardBounds = screen.getBankReceiveCardBounds();
        drawCard(guiGraphics, cardBounds);

        drawCentered(guiGraphics, font, Component.translatable("screen.bphone.phone.bank.receive_title"),
                cardBounds.left, cardBounds.top + Math.max(7, Math.round(8 * screen.scale)),
                cardBounds.width, TEXT_DARK, 0.34F);

        UiRect qrBounds = screen.getBankReceiveQrBounds();
        drawQr(guiGraphics, qrBounds);
        int numberTop = qrBounds.bottom() + Math.max(5, Math.round(6 * screen.scale));
        drawCentered(guiGraphics, font, Component.literal(screen.getOwnPhoneNumber()),
                cardBounds.left, numberTop, cardBounds.width, TEXT_DARK, 0.38F);
        int numberBottom = numberTop + PhoneScreenDraw.scaledTextHeight(font, 0.38F);
        int hintTop = Math.max(numberBottom + Math.max(8, Math.round(10 * screen.scale)),
                cardBounds.bottom() - Math.max(15, Math.round(18 * screen.scale)));
        drawCentered(guiGraphics, font, Component.translatable("screen.bphone.phone.bank.receive_hint"),
                cardBounds.left, hintTop, cardBounds.width, TEXT_MUTED, 0.26F,
                cardBounds.width - Math.max(10, Math.round(12 * screen.scale)));

        drawSecondaryButton(guiGraphics, font, screen.getBankCancelButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.cancel"), true, screen.scale);
        drawEyeButton(guiGraphics, font, screen.getBankReceiveEyeButtonBounds(), screen.scale);
    }

    private static void renderSlipPage(PhoneScreen screen, GuiGraphics guiGraphics, Font font) {
        drawFooter(screen, guiGraphics);
        UiRect cardBounds = screen.getBankSlipCardBounds();
        drawCard(guiGraphics, cardBounds);

        drawCentered(guiGraphics, font, Component.translatable("screen.bphone.phone.bank.slip_title"),
                cardBounds.left, cardBounds.top + Math.max(6, Math.round(7 * screen.scale)),
                cardBounds.width, BALANCE_HIGHLIGHT, 0.42F);

        String to = screen.bankSlipTargetName == null || screen.bankSlipTargetName.isBlank()
                ? screen.bankSlipTargetNumber
                : screen.bankSlipTargetName + " (" + screen.bankSlipTargetNumber + ")";
        int rowX = cardBounds.left + Math.max(8, Math.round(10 * screen.scale));
        int rowY = cardBounds.top + Math.max(27, Math.round(31 * screen.scale));
        int rowWidth = cardBounds.width - Math.max(16, Math.round(20 * screen.scale));
        rowY = drawSlipRow(guiGraphics, font, rowX, rowY, rowWidth,
                Component.translatable("screen.bphone.phone.bank.slip_from"),
                Component.literal(screen.getOwnPhoneNumber()), screen.scale);
        rowY = drawSlipRow(guiGraphics, font, rowX, rowY, rowWidth,
                Component.translatable("screen.bphone.phone.bank.slip_to"),
                Component.literal(to), screen.scale);
        rowY = drawSlipRow(guiGraphics, font, rowX, rowY, rowWidth,
                Component.translatable("screen.bphone.phone.bank.amount"),
                Component.literal(NUMBER_FORMAT.format(screen.bankSlipAmount)), screen.scale);
        rowY = drawSlipRow(guiGraphics, font, rowX, rowY, rowWidth,
                Component.translatable("screen.bphone.phone.bank.slip_time"),
                Component.literal(screen.bankSlipTime), screen.scale);
        drawSlipRow(guiGraphics, font, rowX, rowY, rowWidth,
                Component.translatable("screen.bphone.phone.bank.slip_balance"),
                Component.literal(NUMBER_FORMAT.format(screen.bankSlipBalance)), screen.scale);

        drawSecondaryButton(guiGraphics, font, screen.getBankCancelButtonBounds(),
                Component.translatable("screen.bphone.phone.bank.done"), true, screen.scale);
    }

    private static void drawHeader(PhoneScreen screen, GuiGraphics guiGraphics, Font font) {
        UiRect headerBounds = screen.getBankHeaderBounds();
        int horizontalBleed = getHorizontalBleed(screen);
        int headerLeft = headerBounds.left - horizontalBleed;
        int headerRight = headerBounds.right() + horizontalBleed;
        guiGraphics.fill(headerLeft, headerBounds.top, headerRight, headerBounds.bottom(), HEADER_FILL);
        int gradientHeight = Math.max(1, headerBounds.height / 2);
        guiGraphics.fillGradient(headerLeft, headerBounds.top,
                headerRight, headerBounds.top + gradientHeight,
                HEADER_GRADIENT, HEADER_FILL);
        drawCentered(guiGraphics, font, Component.translatable("screen.bphone.phone.bank.title"),
                headerBounds.left,
                headerBounds.top + (headerBounds.height - PhoneScreenDraw.scaledTextHeight(font, 0.55F)) / 2,
                headerBounds.width, TEXT_LIGHT, 0.55F);
    }

    private static void drawFooter(PhoneScreen screen, GuiGraphics guiGraphics) {
        UiRect contentBounds = screen.getBankSurfaceBounds();
        int footerHeight = Math.max(58, Math.round(contentBounds.height * 0.24F));
        int horizontalBleed = getHorizontalBleed(screen);
        guiGraphics.fill(contentBounds.left - horizontalBleed, contentBounds.bottom() - footerHeight,
                contentBounds.right() + horizontalBleed, contentBounds.bottom(), FOOTER_FILL);
    }

    private static int getHorizontalBleed(PhoneScreen screen) {
        return Math.max(4, Math.round(6 * screen.scale));
    }

    private static void drawScanFrame(GuiGraphics guiGraphics, UiRect cameraBounds,
                                      int headerHeight, int footerHeight, float scale) {
        int availableTop = cameraBounds.top + headerHeight + Math.max(18, Math.round(22 * scale));
        int availableBottom = cameraBounds.bottom() - footerHeight - Math.max(12, Math.round(14 * scale));
        int availableHeight = Math.max(24, availableBottom - availableTop);
        int size = Math.min(cameraBounds.width - Math.max(28, Math.round(34 * scale)),
                Math.min(availableHeight, Math.max(70, Math.round(92 * scale))));
        int left = cameraBounds.left + (cameraBounds.width - size) / 2;
        int top = availableTop + Math.max(0, (availableHeight - size) / 2);
        int right = left + size;
        int bottom = top + size;
        int cornerLength = Math.max(12, Math.round(17 * scale));
        int thickness = Math.max(2, Math.round(2 * scale));
        int shadow = 0x66000000;
        int color = 0xEFFFFFFF;

        drawCorner(guiGraphics, left + 1, top + 1, cornerLength, thickness, true, true, shadow);
        drawCorner(guiGraphics, right - 1, top + 1, cornerLength, thickness, false, true, shadow);
        drawCorner(guiGraphics, left + 1, bottom - 1, cornerLength, thickness, true, false, shadow);
        drawCorner(guiGraphics, right - 1, bottom - 1, cornerLength, thickness, false, false, shadow);
        drawCorner(guiGraphics, left, top, cornerLength, thickness, true, true, color);
        drawCorner(guiGraphics, right, top, cornerLength, thickness, false, true, color);
        drawCorner(guiGraphics, left, bottom, cornerLength, thickness, true, false, color);
        drawCorner(guiGraphics, right, bottom, cornerLength, thickness, false, false, color);
    }

    private static void drawCorner(GuiGraphics guiGraphics, int x, int y, int length,
                                   int thickness, boolean leftSide, boolean topSide, int color) {
        int horizontalX1 = leftSide ? x : x - length;
        int horizontalY1 = topSide ? y : y - thickness;
        guiGraphics.fill(horizontalX1, horizontalY1, horizontalX1 + length, horizontalY1 + thickness, color);

        int verticalX1 = leftSide ? x : x - thickness;
        int verticalY1 = topSide ? y : y - length;
        guiGraphics.fill(verticalX1, verticalY1, verticalX1 + thickness, verticalY1 + length, color);
    }

    private static void drawCard(GuiGraphics guiGraphics, UiRect bounds) {
        int shadowOffset = 2;
        guiGraphics.fill(bounds.left + shadowOffset, bounds.top + shadowOffset,
                bounds.right() + shadowOffset, bounds.bottom() + shadowOffset, CARD_SHADOW);
        guiGraphics.fill(bounds.left, bounds.top, bounds.right(), bounds.bottom(), CARD_EDGE);
        guiGraphics.fill(bounds.left + 1, bounds.top + 1, bounds.right() - 1, bounds.bottom() - 1, CARD_FILL);
        guiGraphics.fill(bounds.left + 1, bounds.top + 1, bounds.right() - 1, bounds.top + 2, 0x10FFFFFF);
    }

    private static void drawAccountCard(PhoneScreen screen, GuiGraphics guiGraphics, Font font, UiRect bounds) {
        drawCard(guiGraphics, bounds);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }

        int padding = Math.max(4, Math.round(5 * screen.scale));
        int headSize = Math.max(20, Math.round(24 * screen.scale));
        int headX = bounds.left + padding;
        int headY = bounds.top + (bounds.height - headSize) / 2;
        guiGraphics.fill(headX, headY, headX + headSize, headY + headSize, 0xFFE5E7EB);

        try {
            var playerInfo = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (playerInfo != null) {
                var skinLocation = playerInfo.getSkin().texture();
                guiGraphics.blit(skinLocation, headX, headY, headSize, headSize,
                        8.0F, 8.0F, 8, 8, 64, 64);
                guiGraphics.blit(skinLocation, headX, headY, headSize, headSize,
                        40.0F, 8.0F, 8, 8, 64, 64);
            }
        } catch (Exception ignored) {
            guiGraphics.fill(headX + 1, headY + 1, headX + headSize - 1, headY + headSize - 1, 0xFF9CA3AF);
        }

        int textX = headX + headSize + padding;
        int textWidth = bounds.right() - textX - padding;
        int textY = headY + Math.max(0, Math.round(1 * screen.scale));
        Component playerName = minecraft.player.getName();
        float nameScale = fitTextScale(font, playerName, textWidth, 0.58F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, playerName, textX, textY, TEXT_DARK, false, nameScale);

        int labelY = textY + PhoneScreenDraw.scaledTextHeight(font, nameScale) + Math.max(1, Math.round(1 * screen.scale));
        Component accountLabel = Component.translatable("screen.bphone.phone.bank.account");
        float labelScale = fitTextScale(font, accountLabel, textWidth, 0.42F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, accountLabel, textX, labelY, TEXT_MUTED, false, labelScale);

        int numberY = labelY + PhoneScreenDraw.scaledTextHeight(font, labelScale) + Math.max(1, Math.round(1 * screen.scale));
        Component accountNumber = Component.literal(screen.getOwnPhoneNumber());
        float numberScale = fitTextScale(font, accountNumber, textWidth, 0.52F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, accountNumber, textX, numberY, TEXT_DARK, false, numberScale);
    }

    private static void drawButton(GuiGraphics guiGraphics, Font font, UiRect bounds,
                                   Component label, boolean enabled, float scale) {
        drawButton(guiGraphics, font, bounds, label, enabled, scale, BUTTON_FILL, BUTTON_EDGE);
    }

    private static void drawSecondaryButton(GuiGraphics guiGraphics, Font font, UiRect bounds,
                                            Component label, boolean enabled, float scale) {
        drawButton(guiGraphics, font, bounds, label, enabled, scale, BUTTON_SECONDARY_FILL, BUTTON_SECONDARY_EDGE);
    }

    private static void drawEyeButton(GuiGraphics guiGraphics, Font font, UiRect bounds, float scale) {
        drawButton(guiGraphics, font, bounds, Component.empty(), true, scale);
        int size = Math.min(bounds.width, bounds.height);
        int eyeWidth = Math.max(7, Math.round(size * 0.62F));
        int eyeHeight = Math.max(5, Math.round(eyeWidth * 0.56F));
        int left = bounds.left + (bounds.width - eyeWidth) / 2;
        int top = bounds.top + (bounds.height - eyeHeight) / 2;
        for (int row = 0; row < eyeHeight; row++) {
            float offset = Math.abs((row + 0.5F) / eyeHeight * 2.0F - 1.0F);
            int inset = Math.round(eyeWidth / 2.0F * (1.0F - (float) Math.sqrt(1.0F - offset)));
            guiGraphics.fill(left + inset, top + row, left + eyeWidth - inset, top + row + 1, TEXT_LIGHT);
        }

        int pupilHeight = Math.max(1, Math.round(eyeHeight * 0.6F));
        if ((eyeHeight - pupilHeight) % 2 != 0) {
            pupilHeight++;
        }
        int pupilWidth = pupilHeight;
        if ((eyeWidth - pupilWidth) % 2 != 0) {
            pupilWidth--;
        }
        pupilWidth = Math.max(2, pupilWidth);
        int pupilLeft = left + (eyeWidth - pupilWidth) / 2;
        int pupilTop = top + (eyeHeight - pupilHeight) / 2;
        guiGraphics.fill(pupilLeft, pupilTop, pupilLeft + pupilWidth, pupilTop + pupilHeight, BUTTON_EDGE);
    }

    private static void drawButton(GuiGraphics guiGraphics, Font font, UiRect bounds,
                                   Component label, boolean enabled, float scale, int enabledFill, int enabledEdge) {
        int fill = enabled ? enabledFill : BUTTON_DISABLED;
        int edge = enabled ? enabledEdge : CARD_EDGE;
        if (enabled) {
            int shadowOffset = 2;
            guiGraphics.fill(bounds.left + shadowOffset, bounds.top + shadowOffset,
                    bounds.right() + shadowOffset, bounds.bottom() + shadowOffset, 0x30000000);
        }

        guiGraphics.fill(bounds.left, bounds.top, bounds.right(), bounds.bottom(), edge);
        guiGraphics.fill(bounds.left + 1, bounds.top + 1, bounds.right() - 1, bounds.bottom() - 1, fill);
        if (enabled) {
            int highlightHeight = Math.max(2, Math.round(4 * scale));
            guiGraphics.fillGradient(bounds.left + 2, bounds.top + 2,
                    bounds.right() - 2, bounds.top + highlightHeight,
                    BUTTON_GRADIENT_TOP, 0x00FFFFFF);
        }

        drawCentered(guiGraphics, font, label, bounds.left,
                bounds.top + (bounds.height - PhoneScreenDraw.scaledTextHeight(font, 0.40F)) / 2,
                bounds.width, TEXT_LIGHT, 0.40F);
    }

    private static void drawField(GuiGraphics guiGraphics, Font font, UiRect bounds,
                                  Component label, Component value, float scale) {
        int labelWidth = Math.max(1, bounds.width - 6);
        float labelScale = fitTextScale(font, label, labelWidth, 0.42F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, label,
                bounds.left + 1, bounds.top, TEXT_MUTED, false, labelScale);

        int labelHeight = PhoneScreenDraw.scaledTextHeight(font, labelScale);
        int fieldTop = bounds.top + labelHeight + Math.max(2, Math.round(3 * scale));
        guiGraphics.fill(bounds.left, fieldTop, bounds.right(), bounds.bottom(), FIELD_EDGE);
        guiGraphics.fill(bounds.left + 1, fieldTop + 1, bounds.right() - 1, bounds.bottom() - 1, FIELD_FILL);
        guiGraphics.fill(bounds.left + 1, fieldTop + 1, bounds.right() - 1, fieldTop + 2, 0x10000000);

        int valueTop = fieldTop + Math.max(5, Math.round(6 * scale));
        float valueScale = fitTextScale(font, value, labelWidth, 0.60F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, value,
                bounds.left + 3, valueTop, TEXT_DARK, false, valueScale);
    }

    private static void drawLabelValue(GuiGraphics guiGraphics, Font font, UiRect bounds,
                                       Component label, Component value, float scale,
                                       float labelMaxScale, float valueMaxScale) {
        int textWidth = Math.max(1, bounds.width - Math.max(10, Math.round(12 * scale)));
        int textX = bounds.left + Math.max(5, Math.round(6 * scale));
        int labelY = bounds.top + Math.max(5, Math.round(6 * scale));
        float labelScale = fitTextScale(font, label, textWidth, labelMaxScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, label, textX, labelY, TEXT_MUTED, false, labelScale);

        int valueY = labelY + PhoneScreenDraw.scaledTextHeight(font, labelScale) + Math.max(3, Math.round(4 * scale));
        float valueScale = fitTextScale(font, value, textWidth, valueMaxScale);
        int valueColor = label.getString().toLowerCase(Locale.ROOT).contains("balance") ? BALANCE_HIGHLIGHT : TEXT_DARK;
        PhoneScreenDraw.drawScaledText(guiGraphics, font, value, textX, valueY, valueColor, false, valueScale);
    }

    private static int drawSlipRow(GuiGraphics guiGraphics, Font font, int x, int y, int width,
                                   Component label, Component value, float scale) {
        float labelScale = fitTextScale(font, label, width, 0.38F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, label, x, y, TEXT_MUTED, false, labelScale);
        int valueY = y + PhoneScreenDraw.scaledTextHeight(font, labelScale) + Math.max(1, Math.round(2 * scale));
        float valueScale = fitTextScale(font, value, width, 0.46F);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, value, x, valueY, TEXT_DARK, false, valueScale);
        return valueY + PhoneScreenDraw.scaledTextHeight(font, valueScale) + Math.max(5, Math.round(6 * scale));
    }

    private static void drawStatus(PhoneScreen screen, GuiGraphics guiGraphics, Font font, int afterY) {
        UiRect contentBounds = screen.getBankSurfaceBounds();
        Component statusText = screen.bankStatus == null || screen.bankStatus.getString().isBlank()
                ? Component.translatable("screen.bphone.phone.bank.status.ready")
                : screen.bankStatus;
        int maxWidth = contentBounds.width - Math.max(12, Math.round(14 * screen.scale));
        int textHeight = PhoneScreenDraw.scaledTextHeight(font, 0.36F);
        int top = Math.min(contentBounds.bottom() - textHeight - Math.max(24, Math.round(28 * screen.scale)),
                afterY + Math.max(5, Math.round(6 * screen.scale)));
        drawCentered(guiGraphics, font, statusText, contentBounds.left, top,
                contentBounds.width, TEXT_MUTED, 0.36F, maxWidth);
    }

    private static Component amountText(PhoneScreen screen) {
        return screen.bankTransferAmount.isBlank()
                ? Component.empty()
                : Component.literal(NUMBER_FORMAT.format(screen.getBankTransferAmount()));
    }

    private static void drawCentered(GuiGraphics guiGraphics, Font font, Component text, int left, int top,
                                     int width, int color, float maxScale) {
        drawCentered(guiGraphics, font, text, left, top, width, color, maxScale, width - 6);
    }

    private static void drawCentered(GuiGraphics guiGraphics, Font font, Component text, int left, int top,
                                     int width, int color, float maxScale, int maxTextWidth) {
        float textScale = fitTextScale(font, text, Math.max(1, maxTextWidth), maxScale);
        int textWidth = PhoneScreenDraw.scaledTextWidth(font, text, textScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, font, text,
                left + (width - textWidth) / 2, top, color, false, textScale);
    }

    private static float fitTextScale(Font font, Component text, int maxWidth, float preferredScale) {
        int textWidth = Math.max(1, font.width(text));
        float fittedScale = (float) maxWidth / textWidth;
        return Math.max(0.22F, Math.min(preferredScale, fittedScale));
    }

    private static void drawQr(GuiGraphics guiGraphics, UiRect bounds) {
        guiGraphics.fill(bounds.left, bounds.top, bounds.right(), bounds.bottom(), 0xFFFFFFFF);
        guiGraphics.fill(bounds.left, bounds.top, bounds.right(), bounds.bottom(), 0xFF1A1F26);
        guiGraphics.fill(bounds.left + 2, bounds.top + 2, bounds.right() - 2, bounds.bottom() - 2, 0xFFFFFFFF);
        int cells = 9;
        int cell = Math.max(1, (bounds.width - 8) / cells);
        int startX = bounds.left + (bounds.width - (cell * cells)) / 2;
        int startY = bounds.top + (bounds.height - (cell * cells)) / 2;
        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                boolean finder = (row < 3 && col < 3) || (row < 3 && col > 5) || (row > 5 && col < 3);
                boolean fill = finder || ((row * 7 + col * 5 + row * col) % 4 == 0);
                if (fill) {
                    guiGraphics.fill(startX + col * cell, startY + row * cell,
                            startX + (col + 1) * cell - 1, startY + (row + 1) * cell - 1, 0xFF1A1F26);
                }
            }
        }
    }
}
