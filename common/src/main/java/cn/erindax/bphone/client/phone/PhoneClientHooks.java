package cn.erindax.bphone.client.phone;

import com.mojang.blaze3d.platform.InputConstants;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.client.homephone.HomePhoneScreen;
import cn.erindax.bphone.phone.PhoneCallPoseAccess;
import cn.erindax.bphone.phone.PhoneCallState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class PhoneClientHooks {
    private static boolean mobileCallSlotLockActive;
    private static boolean mobileCallSlotLockOffhand;
    private static int mobileCallLockedHotbarSlot = -1;
    private static boolean bankReceiveWorldActive;
    private static boolean bankReceiveWorldIsChat;
    private static boolean bankReceiveEscDown;
    private static InteractionHand bankReceiveWorldHand = InteractionHand.MAIN_HAND;

    private PhoneClientHooks() {
    }

    public static void openScreen(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new PhoneScreen(hand));
        }
    }

    public static void openHomePhoneScreen(BlockPos blockPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(new HomePhoneScreen(blockPos));
        }
    }

    public static boolean shouldHideCrosshair() {
        PhoneScreen phoneScreen = getOpenPhoneScreen();
        return phoneScreen != null && phoneScreen.isCameraModeActive();
    }

    public static boolean shouldHideBlockOutline() {
        PhoneScreen phoneScreen = getOpenPhoneScreen();
        return phoneScreen != null && phoneScreen.isCameraModeActive();
    }

    public static boolean shouldRaisePhoneArm(LivingEntity entity) {
        LocalPlayer localPlayer = getLocalPlayer();
        if (entity instanceof Player player && player != localPlayer) {
            return (PhoneCallPoseAccess.isPhoneCallPoseActive(player)
                    || PhoneCallPoseAccess.isPhoneCameraPoseActive(player)
                    || PhoneCallPoseAccess.isPhoneBankQrPoseActive(player)
                    || PhoneCallPoseAccess.isPhoneChatQrPoseActive(player))
                    && (isPhone(player.getMainHandItem()) || isPhone(player.getOffhandItem()));
        }

        if (localPlayer == null || entity != localPlayer) {
            return false;
        }

        if (isMobileCallActive()) {
            return isPhone(localPlayer.getMainHandItem()) || isPhone(localPlayer.getOffhandItem())
                    || hasPhoneInHotbar(localPlayer);
        }

        if (bankReceiveWorldActive) {
            return isPhone(localPlayer.getMainHandItem()) || isPhone(localPlayer.getOffhandItem())
                    || hasPhoneInHotbar(localPlayer);
        }

        PhoneScreen phoneScreen = getOpenPhoneScreen();
        if (phoneScreen == null || phoneScreen.isHomePhoneMode()) {
            return false;
        }

        if (phoneScreen.isCameraModeActive()) {
            return true;
        }

        return resolvePhoneHand(localPlayer, phoneScreen) != null;
    }

    public static boolean shouldUseSelfieCamera(LivingEntity entity) {
        LocalPlayer localPlayer = getLocalPlayer();
        if (entity instanceof Player player && player != localPlayer) {
            return PhoneCallPoseAccess.isPhoneCameraSelfieActive(player);
        }

        if (localPlayer == null || entity != localPlayer) {
            return false;
        }

        PhoneScreen phoneScreen = getOpenPhoneScreen();
        if (phoneScreen != null && phoneScreen.isHomePhoneMode()) {
            return false;
        }
        return phoneScreen != null && phoneScreen.isSelfieCameraModeActive();
    }

    public static float getActivePhoneCameraZoomFactor() {
        PhoneScreen phoneScreen = getOpenPhoneScreen();
        if (phoneScreen == null || phoneScreen.isHomePhoneMode() || !phoneScreen.isCameraModeActive()) {
            return 1.0F;
        }

        return phoneScreen.getCameraZoomFactor();
    }

    public static HumanoidArm getRaisedPhoneArm(Player player) {
        if (player instanceof LocalPlayer localPlayer) {
            if (isMobileCallActive()) {
                if (isPhone(localPlayer.getOffhandItem())) {
                    return localPlayer.getMainArm().getOpposite();
                }
                if (isPhone(localPlayer.getMainHandItem()) || hasPhoneInHotbar(localPlayer)) {
                    return localPlayer.getMainArm();
                }
            }
        }

        if (player instanceof LocalPlayer localPlayer && bankReceiveWorldActive) {
            if (bankReceiveWorldHand == InteractionHand.OFF_HAND && isPhone(localPlayer.getOffhandItem())) {
                return localPlayer.getMainArm().getOpposite();
            }
            if (isPhone(localPlayer.getMainHandItem()) || hasPhoneInHotbar(localPlayer)) {
                return localPlayer.getMainArm();
            }
        }

        if (PhoneCallPoseAccess.isPhoneCallPoseActive(player)) {
            if (isPhone(player.getOffhandItem())) {
                return player.getMainArm().getOpposite();
            }

            if (isPhone(player.getMainHandItem())) {
                return player.getMainArm();
            }
        }

        if (PhoneCallPoseAccess.isPhoneCameraPoseActive(player)) {
            if (isPhone(player.getOffhandItem())) {
                return player.getMainArm().getOpposite();
            }

            if (isPhone(player.getMainHandItem())) {
                return player.getMainArm();
            }
        }

        if (PhoneCallPoseAccess.isPhoneBankQrPoseActive(player)
                || PhoneCallPoseAccess.isPhoneChatQrPoseActive(player)) {
            if (isPhone(player.getOffhandItem())) {
                return player.getMainArm().getOpposite();
            }

            if (isPhone(player.getMainHandItem())) {
                return player.getMainArm();
            }
        }

        PhoneScreen phoneScreen = getOpenPhoneScreen();
        if (phoneScreen == null) {
            return player.getMainArm();
        }
        if (phoneScreen.isHomePhoneMode()) {
            return player.getMainArm();
        }

        InteractionHand phoneHand = resolvePhoneHand(player, phoneScreen);
        if (phoneHand == null) {
            return player.getMainArm();
        }

        return phoneHand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
    }

    public static HumanoidArm getRaisedPhoneArm(LivingEntity entity) {
        if (entity instanceof Player player) {
            return getRaisedPhoneArm(player);
        }

        return HumanoidArm.RIGHT;
    }

    public static boolean shouldUsePhoneCallPose(LivingEntity entity) {
        LocalPlayer localPlayer = getLocalPlayer();
        if (entity instanceof Player player && player != localPlayer) {
            return PhoneCallPoseAccess.isPhoneCallPoseActive(player)
                    && (isPhone(player.getMainHandItem()) || isPhone(player.getOffhandItem()));
        }

        if (localPlayer == null || entity != localPlayer) {
            return false;
        }

        return isMobileCallActive()
                && (isPhone(localPlayer.getMainHandItem()) || isPhone(localPlayer.getOffhandItem())
                        || hasPhoneInHotbar(localPlayer));
    }

    public static boolean shouldUsePhoneOnModel(LivingEntity entity) {
        LocalPlayer localPlayer = getLocalPlayer();
        if (entity instanceof Player player && player != localPlayer) {
            return PhoneCallPoseAccess.isPhoneScreenOnActive(player)
                    && !PhoneCallPoseAccess.isPhoneBankQrPoseActive(player)
                    && !PhoneCallPoseAccess.isPhoneChatQrPoseActive(player)
                    && !PhoneCallPoseAccess.isPhoneCallPoseActive(player)
                    && (isPhone(player.getMainHandItem()) || isPhone(player.getOffhandItem()));
        }

        if (localPlayer == null || entity != localPlayer) {
            return false;
        }

        if (isMobileCallActive()) {
            return false;
        }

        PhoneScreen phoneScreen = getOpenPhoneScreen();
        return phoneScreen != null && !phoneScreen.isHomePhoneMode();
    }

    public static boolean shouldUsePhoneQrModel(LivingEntity entity) {
        LocalPlayer localPlayer = getLocalPlayer();
        if (entity instanceof Player player && player != localPlayer) {
            return PhoneCallPoseAccess.isPhoneBankQrPoseActive(player)
                    && (isPhone(player.getMainHandItem()) || isPhone(player.getOffhandItem()));
        }

        if (localPlayer == null || entity != localPlayer) {
            return false;
        }

        return bankReceiveWorldActive && !bankReceiveWorldIsChat
                && (isPhone(localPlayer.getMainHandItem()) || isPhone(localPlayer.getOffhandItem())
                        || hasPhoneInHotbar(localPlayer));
    }

    public static boolean shouldUseChatQrModel(LivingEntity entity) {
        LocalPlayer localPlayer = getLocalPlayer();
        if (entity instanceof Player player && player != localPlayer) {
            return PhoneCallPoseAccess.isPhoneChatQrPoseActive(player)
                    && (isPhone(player.getMainHandItem()) || isPhone(player.getOffhandItem()));
        }

        if (localPlayer == null || entity != localPlayer) {
            return false;
        }

        return bankReceiveWorldActive && bankReceiveWorldIsChat
                && (isPhone(localPlayer.getMainHandItem()) || isPhone(localPlayer.getOffhandItem())
                        || hasPhoneInHotbar(localPlayer));
    }

    public static boolean isLocalPhoneCameraPoseActive() {
        PhoneScreen phoneScreen = getOpenPhoneScreen();
        return phoneScreen != null && !phoneScreen.isHomePhoneMode()
                && phoneScreen.isCameraModeActive()
                && !bankReceiveWorldActive;
    }

    public static boolean isLocalPhoneCameraSelfieActive() {
        PhoneScreen phoneScreen = getOpenPhoneScreen();
        return phoneScreen != null && !phoneScreen.isHomePhoneMode() && phoneScreen.isSelfieCameraModeActive();
    }

    public static boolean isLocalPhoneScreenOnActive() {
        PhoneScreen phoneScreen = getOpenPhoneScreen();
        return phoneScreen != null && !phoneScreen.isHomePhoneMode() && !bankReceiveWorldActive;
    }

    public static void tickMobileCallHotbarLock() {
        LocalPlayer localPlayer = getLocalPlayer();
        if (localPlayer == null || !isMobileCallActive()) {
            clearMobileCallHotbarLock();
            return;
        }

        boolean mainHandPhone = isPhone(localPlayer.getMainHandItem());
        boolean offhandPhone = isPhone(localPlayer.getOffhandItem());
        int selectedSlot = localPlayer.getInventory().selected;

        if (offhandPhone) {
            if (!mobileCallSlotLockActive || !mobileCallSlotLockOffhand) {
                mobileCallLockedHotbarSlot = selectedSlot;
                mobileCallSlotLockOffhand = true;
                mobileCallSlotLockActive = true;
            }
            restoreSelectedHotbarSlot(localPlayer);
            return;
        }

        mobileCallSlotLockOffhand = false;
        if (mainHandPhone) {
            mobileCallLockedHotbarSlot = selectedSlot;
            mobileCallSlotLockActive = true;
            return;
        }

        if (mobileCallSlotLockActive
                && isValidHotbarSlot(mobileCallLockedHotbarSlot)
                && isPhone(localPlayer.getInventory().getItem(mobileCallLockedHotbarSlot))) {
            restoreSelectedHotbarSlot(localPlayer);
            return;
        }

        int phoneSlot = findPhoneHotbarSlot(localPlayer);
        if (phoneSlot >= 0) {
            mobileCallLockedHotbarSlot = phoneSlot;
            mobileCallSlotLockActive = true;
            localPlayer.getInventory().selected = phoneSlot;
            return;
        }

        clearMobileCallHotbarLock();
    }

    public static void clearMobileCallHotbarLock() {
        mobileCallSlotLockActive = false;
        mobileCallSlotLockOffhand = false;
        mobileCallLockedHotbarSlot = -1;
    }

    public static void showBankReceiveQrToWorld(InteractionHand hand) {
        showBankReceiveQrToWorld(hand, false);
    }

    public static void showBankReceiveQrToWorld(InteractionHand hand, boolean chat) {
        Minecraft minecraft = Minecraft.getInstance();
        bankReceiveWorldActive = true;
        bankReceiveWorldIsChat = chat;
        bankReceiveWorldHand = hand == null ? InteractionHand.MAIN_HAND : hand;
        ensureBankReceivePhoneSelected(minecraft);
        bankReceiveEscDown = minecraft != null && isEscapeDown(minecraft);
        PhoneNetworkingClient.requestBankReceiveState(true, chat);
    }

    public static void tickBankReceiveWorld(Minecraft minecraft) {
        if (!bankReceiveWorldActive) {
            bankReceiveEscDown = false;
            return;
        }

        if (minecraft == null || minecraft.player == null) {
            clearBankReceiveWorldState(false);
            return;
        }

        if (!isPhone(minecraft.player.getMainHandItem()) && !isPhone(minecraft.player.getOffhandItem())
                && !hasPhoneInHotbar(minecraft.player)) {
            clearBankReceiveWorldState(true);
            return;
        }
        ensureBankReceivePhoneSelected(minecraft);

        boolean escapeDown = isEscapeDown(minecraft);
        if (escapeDown && !bankReceiveEscDown) {
            InteractionHand hand = bankReceiveWorldHand;
            if (bankReceiveWorldIsChat) {
                clearBankReceiveWorldState(true);
                minecraft.setScreen(PhoneScreen.chatProfileScreen(hand));
                bankReceiveEscDown = true;
                return;
            }
            bankReceiveWorldActive = false;
            minecraft.setScreen(PhoneScreen.bankReceiveScreen(hand));
        }
        bankReceiveEscDown = escapeDown;
    }

    public static void clearBankReceiveWorldState(boolean notifyServer) {
        if (bankReceiveWorldActive && notifyServer) {
            PhoneNetworkingClient.requestBankReceiveState(false, bankReceiveWorldIsChat);
        }
        bankReceiveWorldActive = false;
        bankReceiveWorldIsChat = false;
        bankReceiveEscDown = false;
        bankReceiveWorldHand = InteractionHand.MAIN_HAND;
    }

    private static LocalPlayer getLocalPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.player;
    }

    private static PhoneScreen getOpenPhoneScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !(minecraft.screen instanceof PhoneScreen phoneScreen)) {
            return null;
        }

        return phoneScreen;
    }

    private static InteractionHand resolvePhoneHand(Player player, PhoneScreen phoneScreen) {
        if (isPhone(player.getItemInHand(phoneScreen.getOpenHand()))) {
            return phoneScreen.getOpenHand();
        }

        if (isPhone(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }

        if (isPhone(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }

        return null;
    }

    private static boolean isPhone(ItemStack stack) {
        return ModItems.isPhone(stack);
    }

    private static boolean isEscapeDown(Minecraft minecraft) {
        if (minecraft == null || minecraft.getWindow() == null) {
            return false;
        }

        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE);
    }

    private static boolean hasPhoneInHotbar(LocalPlayer player) {
        return findPhoneHotbarSlot(player) >= 0;
    }

    private static int findPhoneHotbarSlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (isPhone(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static void ensureBankReceivePhoneSelected(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || isPhone(minecraft.player.getMainHandItem())
                || isPhone(minecraft.player.getOffhandItem())) {
            return;
        }

        int phoneSlot = findPhoneHotbarSlot(minecraft.player);
        if (phoneSlot >= 0) {
            minecraft.player.getInventory().selected = phoneSlot;
        }
    }

    private static void restoreSelectedHotbarSlot(LocalPlayer player) {
        if (isValidHotbarSlot(mobileCallLockedHotbarSlot)
                && player.getInventory().selected != mobileCallLockedHotbarSlot) {
            player.getInventory().selected = mobileCallLockedHotbarSlot;
        }
    }

    private static boolean isValidHotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    private static boolean isMobileCallActive() {
        PhoneCallState state = PhoneClientCallState.getState();
        return state == PhoneCallState.OUTGOING_RINGING
                || state == PhoneCallState.CONNECTED;
    }

}
