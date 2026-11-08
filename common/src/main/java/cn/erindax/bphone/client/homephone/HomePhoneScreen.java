package cn.erindax.bphone.client.homephone;

import com.mojang.blaze3d.platform.InputConstants;
import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.client.phone.PhoneClientCallState;
import cn.erindax.bphone.client.phone.PhoneNetworkingClient;
import cn.erindax.bphone.phone.PhoneCallState;
import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class HomePhoneScreen extends Screen {
    private static final float UI_SCALE = 0.935F;
    private static final int BG_COLOR = 0xFFE9EDF2;
    private static final int BORDER_COLOR = 0xFFBEC8D4;
    private static final int TEXT_TITLE = 0xFF1F2732;
    private static final int TEXT_STATUS = 0xFF202732;
    private static final int TEXT_HINT = 0xFF667180;
    private static final ResourceLocation QUESTION_MARK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BPhone.MOD_ID, "textures/gui/question_mark.png");

    private final BlockPos homePhonePos;
    private String dialedNumber = "";
    private String activeCallNumber = "";
    private String activeCallName = "";
    private UUID activeCallProfileId;
    private boolean activeCallIncoming;
    private boolean activeCallConnected;
    private boolean activeCallMissed;
    private boolean speakerEnabled;
    private Boolean pendingSpeakerEnabled;
    private int activeCallTicks;
    private long observedCallStateRevision = Long.MIN_VALUE;
    private int syncCooldown;

    private int panelX;
    private int panelY;
    private int panelW = ui(352);
    private int panelH = ui(192);

    public HomePhoneScreen(BlockPos homePhonePos) {
        super(Component.translatable("block.bphone.home_phone"));
        this.homePhonePos = homePhonePos.immutable();
    }

    @Override
    protected void init() {
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;
        rebuildWidgets();
        requestCallSync();
        applyCallStateFromServer();
        syncSpeakerState();
    }

    @Override
    public void tick() {
        if (syncCooldown <= 0) {
            requestCallSync();
            syncCooldown = 15;
        } else {
            syncCooldown--;
        }

        applyCallStateFromServer();
        syncSpeakerState();
        if (activeCallConnected) {
            activeCallTicks = PhoneClientCallState.getConnectedDurationTicks(homePhonePos);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xAA000000);

        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF191C21);
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0xFFFFFFFF);
        guiGraphics.fill(panelX + 8, panelY + 8, panelX + panelW - 8, panelY + panelH - 8, BG_COLOR);

        drawUiText(guiGraphics, Component.translatable("block.bphone.home_phone"), panelX + ui(18), panelY + ui(18), TEXT_TITLE);

        int artBoxX = panelX + ui(18);
        int artBoxY = panelY + ui(36);
        int artBoxH = ui(60);
        int artBoxW = ui(120);
        drawBox(guiGraphics, artBoxX, artBoxY, artBoxW, artBoxH);
        renderPhoneArt(guiGraphics, artBoxX, artBoxY, artBoxW, artBoxH);

        int statusBoxX = artBoxX + artBoxW + ui(8);
        int statusBoxY = artBoxY;
        int statusBoxW = panelW - (artBoxX - panelX) * 2 - artBoxW - ui(8);
        int statusBoxH = ui(28);
        drawBox(guiGraphics, statusBoxX, statusBoxY, statusBoxW, statusBoxH);
        renderCallStatus(guiGraphics, statusBoxX, statusBoxY, statusBoxW, statusBoxH);

        int inputBoxX = statusBoxX;
        int inputBoxY = statusBoxY + statusBoxH + ui(6);
        int inputBoxW = statusBoxW;
        int inputBoxH = ui(18);
        drawBox(guiGraphics, inputBoxX, inputBoxY, inputBoxW, inputBoxH);
        renderInput(guiGraphics, inputBoxX, inputBoxY, inputBoxW, inputBoxH);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawBox(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, BORDER_COLOR);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
    }

    private void renderCallStatus(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        Component titleLine;
        Component detailLine;

        if (activeCallConnected) {
            titleLine = Component.translatable("screen.bphone.phone.call.status.connected");
            detailLine = Component.literal(formatCallDuration());
        } else if (activeCallMissed) {
            titleLine = Component.translatable("screen.bphone.phone.call.status.no_answer_short");
            detailLine = Component.literal(labelForDisplay(activeCallName, activeCallNumber));
        } else if (activeCallIncoming) {
            titleLine = Component.translatable("screen.bphone.phone.call.status.incoming");
            detailLine = Component.literal(labelForDisplay(activeCallName, activeCallNumber));
        } else if (!activeCallNumber.isEmpty()) {
            titleLine = Component.translatable("screen.bphone.phone.call.status.dialing");
            detailLine = Component.literal(labelForDisplay(activeCallName, activeCallNumber));
        } else {
            titleLine = Component.translatable("screen.bphone.home_phone.status.ready");
            detailLine = Component.translatable("screen.bphone.phone.call.hint_input");
        }

        drawUiText(guiGraphics, titleLine, x + ui(6), y + ui(4), activeCallMissed ? 0xFFDD0000 : TEXT_STATUS);
        drawUiText(guiGraphics, detailLine, x + ui(6), y + ui(14), TEXT_HINT);

        String ownNumber = getOwnPhoneNumber();
        Component ownNumberText = Component.literal(ownNumber);
        int chipW = uiTextWidth(ownNumberText) + ui(8);
        int chipH = Math.max(ui(12), uiTextHeight() + ui(4));
        int chipX = x + w - chipW - ui(4);
        int chipY = y + ui(4);
        drawBox(guiGraphics, chipX, chipY, chipW, chipH);
        int innerX = chipX + 1;
        int innerY = chipY + 1;
        int innerW = Math.max(1, chipW - 2);
        int innerH = Math.max(1, chipH - 2);
        int numberTextX = innerX + Math.max(0, (innerW - uiTextWidth(ownNumberText)) / 2);
        int numberTextY = innerY + Math.max(0, (innerH - uiTextHeight()) / 2) + ui(1);
        drawUiText(guiGraphics, ownNumberText, numberTextX, numberTextY, TEXT_STATUS);
    }

    private void renderInput(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        Component textComponent = dialedNumber.isEmpty()
                ? Component.translatable("screen.bphone.phone.call.placeholder")
                : Component.literal(dialedNumber);
        int color = dialedNumber.isEmpty() ? 0xFF999999 : TEXT_STATUS;
        int textX = x + ui(6);
        int textY = y + Math.max(0, (h - uiTextHeight()) / 2) + ui(1);
        drawUiText(guiGraphics, textComponent, textX, textY, color);
    }

    private void renderPhoneArt(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (activeCallIncoming && !activeCallConnected) {
            int faceSize = Math.max(22, Math.min(w, h) - 20);
            int faceX = x + (w - faceSize) / 2;
            int faceY = y + (h - faceSize) / 2;
            PlayerFaceRenderer.draw(guiGraphics, QUESTION_MARK_TEXTURE, faceX, faceY, faceSize);
            return;
        }

        if (activeCallConnected) {
            int faceSize = Math.max(22, Math.min(w, h) - 20);
            int faceX = x + (w - faceSize) / 2;
            int faceY = y + (h - faceSize) / 2;
            ResourceLocation callerSkin = resolveCallerSkinTexture();
            if (callerSkin == null) {
                PlayerFaceRenderer.draw(guiGraphics, QUESTION_MARK_TEXTURE, faceX, faceY, faceSize);
                return;
            }
            PlayerFaceRenderer.draw(guiGraphics, callerSkin, faceX, faceY, faceSize);
            return;
        }

        ItemStack previewStack = new ItemStack(ModItems.HOME_PHONE.get());
        int targetSize = Math.max(22, Math.min(w, h) - 20);
        float blockScale = Math.max(3.0F, targetSize / 10.0F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + w / 2f, y + h / 2f, 120.0F);
        guiGraphics.pose().scale(blockScale, blockScale, 1.0F);
        guiGraphics.renderItem(previewStack, -8, -8);
        guiGraphics.pose().popPose();
    }

    private ResourceLocation resolveCallerSkinTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getConnection() != null) {
            if (activeCallProfileId != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(activeCallProfileId);
                if (info != null) {
                    return info.getSkin().texture();
                }

                return DefaultPlayerSkin.get(activeCallProfileId).texture();
            }

            String callerName = activeCallName == null ? "" : activeCallName.strip();
            if (!callerName.isBlank()) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(callerName);
                if (info != null) {
                    return info.getSkin().texture();
                }
            }
        }

        return null;
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        setFocused(null);

        int keypadX = panelX + ui(18);
        int keypadY = panelY + ui(102);
        int gap = ui(5);
        int btnW = (panelW - ui(36) - (gap * 3)) / 4;
        int btnH = ui(18);

        for (int i = 0; i < 12; i++) {
            int col = i % 4;
            int row = i / 4;
            int x = keypadX + col * (btnW + gap);
            int y = keypadY + row * (btnH + gap);

            final int btnIdx = i;
            Component label = getBtnLabel(i);
            FlatButtonStyle style = getBtnStyle(i);

            Button btn = new FlatButton(x, y, btnW, btnH, label, b -> {
                onBtnClick(btnIdx);
                rebuildWidgets();
            }, style);
            addRenderableWidget(btn);
        }
    }

    private void onBtnClick(int i) {
        if (i < 9)
            appendDigit(String.valueOf(i + 1));
        else if (i == 9)
            appendDigit("0");
        else if (i == 10)
            onSecondaryAction();
        else
            onPrimaryAction();
    }

    private void onSecondaryAction() {
        if (canToggleSpeaker()) {
            toggleSpeaker();
            return;
        }

        if (activeCallIncoming && !activeCallConnected) {
            endCall();
            return;
        }

        if (activeCallNumber.isEmpty()) {
            removeLastDigit();
        }
    }

    private void onPrimaryAction() {
        if (activeCallIncoming && !activeCallConnected) {
            connectCall();
        } else if (!activeCallNumber.isEmpty()) {
            endCall();
        } else if (PhoneData.isValidPhoneNumber(dialedNumber)) {
            startCall(dialedNumber);
        }
    }

    private Component getBtnLabel(int i) {
        if (i < 9)
            return Component.literal(String.valueOf(i + 1));
        if (i == 9)
            return Component.literal("0");
        if (i == 10) {
            if (canToggleSpeaker())
                return Component.translatable("screen.bphone.home_phone.key.speaker");
            if (activeCallIncoming && !activeCallConnected)
                return Component.translatable("screen.bphone.home_phone.key.end");
            if (!activeCallNumber.isEmpty())
                return Component.empty();
            return Component.translatable("screen.bphone.home_phone.key.delete");
        }
        if (activeCallIncoming && !activeCallConnected)
            return Component.translatable("screen.bphone.home_phone.key.answer");
        if (!activeCallNumber.isEmpty())
            return Component.translatable("screen.bphone.home_phone.key.end");
        return Component.translatable("screen.bphone.home_phone.key.call");
    }

    private FlatButtonStyle getBtnStyle(int i) {
        if (i == 10) {
            if (canToggleSpeaker())
                return speakerEnabled ? FlatButtonStyle.SUCCESS : FlatButtonStyle.NORMAL;
            if (!activeCallNumber.isEmpty() && !activeCallIncoming)
                return FlatButtonStyle.NORMAL;
            return FlatButtonStyle.DANGER;
        }
        if (i == 11) {
            if (!activeCallNumber.isEmpty() && !activeCallIncoming)
                return FlatButtonStyle.DANGER;
            return FlatButtonStyle.SUCCESS;
        }
        return FlatButtonStyle.NORMAL;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        String digit = getDigitForKey(keyCode);
        if (digit != null) {
            appendDigit(digit);
            rebuildWidgets();
            return true;
        }
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            removeLastDigit();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String getDigitForKey(int k) {
        return switch (k) {
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

    private void appendDigit(String s) {
        if (dialedNumber.length() < PhoneData.PHONE_NUMBER_LENGTH)
            dialedNumber += s;
    }

    private void removeLastDigit() {
        if (!dialedNumber.isEmpty())
            dialedNumber = dialedNumber.substring(0, dialedNumber.length() - 1);
    }

    private void startCall(String n) {
        String norm = PhoneData.normalizePhoneNumber(n);
        if (!PhoneData.isValidPhoneNumber(norm))
            return;
        activeCallNumber = norm;
        activeCallName = "";
        activeCallProfileId = null;
        activeCallIncoming = false;
        activeCallConnected = false;
        activeCallMissed = false;
        pendingSpeakerEnabled = null;
        activeCallTicks = 0;
        PhoneNetworkingClient.requestCall(norm, homePhonePos);
    }

    private void connectCall() {
        if (activeCallIncoming && !activeCallConnected)
            PhoneNetworkingClient.requestAnswer(homePhonePos);
    }

    private void toggleSpeaker() {
        if (canToggleSpeaker()) {
            pendingSpeakerEnabled = !speakerEnabled;
            speakerEnabled = pendingSpeakerEnabled;
            rebuildWidgets();
            PhoneNetworkingClient.requestToggleSpeaker(homePhonePos);
        }
    }

    private void endCall() {
        PhoneNetworkingClient.requestEndCall(homePhonePos);
        if (!activeCallNumber.isEmpty())
            dialedNumber = activeCallNumber;
        activeCallNumber = "";
        activeCallName = "";
        activeCallProfileId = null;
        activeCallIncoming = false;
        activeCallConnected = false;
        activeCallMissed = false;
        speakerEnabled = false;
        pendingSpeakerEnabled = null;
        activeCallTicks = 0;
    }

    private void requestCallSync() {
        PhoneNetworkingClient.requestSync(homePhonePos);
    }

    private void applyCallStateFromServer() {
        long nextRev = PhoneClientCallState.getRevision(homePhonePos);
        if (nextRev == observedCallStateRevision)
            return;

        observedCallStateRevision = nextRev;
        PhoneCallState state = PhoneClientCallState.getState(homePhonePos);
        String otherN = PhoneClientCallState.getOtherNumber(homePhonePos);
        String otherM = PhoneClientCallState.getOtherName(homePhonePos);
        UUID otherProfileId = PhoneClientCallState.getOtherProfileId(homePhonePos);

        if (state == PhoneCallState.IDLE) {
            if (!activeCallNumber.isEmpty()) dialedNumber = activeCallNumber;
            activeCallNumber = "";
            activeCallName = "";
            activeCallProfileId = null;
            activeCallIncoming = false;
            activeCallConnected = false;
            activeCallMissed = false;
            speakerEnabled = false;
            pendingSpeakerEnabled = null;
            activeCallTicks = 0;
        } else {
            activeCallIncoming = state == PhoneCallState.INCOMING_RINGING;
            activeCallConnected = state == PhoneCallState.CONNECTED;
            activeCallMissed = state == PhoneCallState.MISSED;
            speakerEnabled = resolveSpeakerDisplayState();
            activeCallNumber = otherN;
            activeCallName = otherM;
            activeCallProfileId = otherProfileId;
            activeCallTicks = activeCallConnected
                    ? PhoneClientCallState.getConnectedDurationTicks(homePhonePos)
                    : 0;
        }
        rebuildWidgets();
    }

    private String getOwnPhoneNumber() {
        HomePhoneBlockEntity be = getClientHomePhone();
        if (be != null)
            return be.getPhoneNumber();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null)
            return PhoneData.getHomePhoneNumber(mc.level.dimension(), homePhonePos);
        return "00000";
    }

    private HomePhoneBlockEntity getClientHomePhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return null;
        return mc.level.getBlockEntity(homePhonePos) instanceof HomePhoneBlockEntity be ? be : null;
    }

    private boolean isSpeakerEnabledFromBlock() {
        HomePhoneBlockEntity be = getClientHomePhone();
        return be != null && be.isSpeakerEnabled();
    }

    private void syncSpeakerState() {
        boolean nextSpeakerEnabled = resolveSpeakerDisplayState();
        if (speakerEnabled != nextSpeakerEnabled) {
            speakerEnabled = nextSpeakerEnabled;
            rebuildWidgets();
        }
    }

    private boolean resolveSpeakerDisplayState() {
        if (activeCallNumber.isEmpty() || activeCallIncoming || activeCallMissed) {
            pendingSpeakerEnabled = null;
            return false;
        }

        boolean blockSpeakerEnabled = isSpeakerEnabledFromBlock();
        if (pendingSpeakerEnabled != null) {
            if (blockSpeakerEnabled == pendingSpeakerEnabled.booleanValue()) {
                pendingSpeakerEnabled = null;
            } else {
                return pendingSpeakerEnabled;
            }
        }

        return blockSpeakerEnabled;
    }

    private boolean canToggleSpeaker() {
        return !activeCallIncoming && !activeCallNumber.isEmpty() && !activeCallMissed;
    }

    private String formatCallDuration() {
        int sec = activeCallTicks / 20;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private String labelForDisplay(String name, String num) {
        return name == null || name.isBlank() ? num : name + " (" + num + ")";
    }

    private record FlatButtonStyle(int fill, int hover, int text, int border) {
        static final FlatButtonStyle NORMAL = new FlatButtonStyle(0xFFFFFFFF, 0xFFF2F4F7, 0xFF202732, BORDER_COLOR);
        static final FlatButtonStyle SUCCESS = new FlatButtonStyle(0xFFE9F2E7, 0xFFD8EAD3, 0xFF1F3A21, BORDER_COLOR);
        static final FlatButtonStyle DANGER = new FlatButtonStyle(0xFFF2E7E9, 0xFFEAD3D8, 0xFF4A2027, BORDER_COLOR);
    }

    private final class FlatButton extends Button {
        private final FlatButtonStyle style;

        private FlatButton(int x, int y, int w, int h, Component msg, OnPress press, FlatButtonStyle style) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int fill = this.isHoveredOrFocused() ? style.hover : style.fill;
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, style.border);
            guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
            int tx = getX() + (width - uiTextWidth(getMessage())) / 2;
            int ty = getY() + Math.max(0, (height - uiTextHeight()) / 2);
            drawUiText(guiGraphics, getMessage(), tx, ty, style.text);
        }
    }

    private static int ui(int value) {
        return Math.max(1, Math.round(value * UI_SCALE));
    }

    private int uiTextWidth(Component text) {
        return Math.max(1, Math.round(font.width(text) * UI_SCALE));
    }

    private int uiTextHeight() {
        return Math.max(1, Math.round(font.lineHeight * UI_SCALE));
    }

    private void drawUiText(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(UI_SCALE, UI_SCALE, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }
}
