package cn.erindax.bphone.client.phone;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.announcement.Announcement;
import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.client.announcement.AnnouncementClientState;
import cn.erindax.bphone.client.announcement.AnnouncementNetworkingClient;
import cn.erindax.bphone.client.notification.NotificationClientState;
import cn.erindax.bphone.client.notification.NotificationNetworkingClient;
import cn.erindax.bphone.notification.PhoneNotification;
import cn.erindax.bphone.phone.CallLogEntry;
import cn.erindax.bphone.phone.PhoneCallPoseAccess;
import cn.erindax.bphone.phone.PhoneCallState;
import cn.erindax.bphone.phone.PhoneContact;
import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.PhoneSettingsData;
import cn.erindax.bphone.phone.PhoneWallpaperData;
import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.shop.ShopCategory;
import cn.erindax.bphone.shop.ShopEntry;
import cn.erindax.bphone.shop.ShopNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

public final class PhoneScreen extends Screen {
    private static final ResourceLocation UNLOCK_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_unlock.png");
    private static final ResourceLocation APP_CALL_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_call.png");
    private static final ResourceLocation APP_CHAT_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_chat.png");
    private static final ResourceLocation APP_SHOP_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_shop.png");
    private static final ResourceLocation APP_GOOGLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_setting.png");
    private static final ResourceLocation APP_GALLERY_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_gallery.png");
    private static final ResourceLocation APP_BANK_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_bank.png");
    private static final ResourceLocation APP_CAMERA_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_camera.png");
    private static final ResourceLocation APP_NOTIFICATION_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_notification.png");
    private static final ResourceLocation APP_ANNOUNCEMENT_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_announcement.png");
    private static final ResourceLocation APP_MAP_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/phone_app_map.png");
    static final ResourceLocation CALL_MENU_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/call_menu.png");
    static final ResourceLocation LIST_MENU_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/list_menu.png");
    static final ResourceLocation CALL_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/call_button.png");
    static final ResourceLocation TIME_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/time_button.png");
    private static final ResourceLocation SHUTTER_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/shutter_button.png");
    private static final ResourceLocation CAMFLIP_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/camflip_button.png");
    static final ResourceLocation DELETE_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/delete_button.png");
    static final ResourceLocation ANSWER_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/answer_button.png");
    static final ResourceLocation HANGUP_BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/phone_gui/hangup_button.png");
    static final ResourceLocation DEFAULT_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID,
            "textures/gui/wallpaper/default.png");
    static final int DISPLAY_WIDTH = 186;
    static final int DISPLAY_HEIGHT = 300;
    private static final float PANEL_MARGIN_RATIO = 0.02F;
    private static final float PANEL_MAX_WIDTH_RATIO = 0.46F;
    private static final int PHOTO_MAX_EDGE = 1920;
    private static final int UNLOCK_TEXTURE_SIZE = 24;
    private static final int ICON_SIZE = 30;
    private static final int ICON_TEXTURE_SIZE = 30;
    static final int GALLERY_COLUMNS = 3;
    private static final int GALLERY_ROWS = 4;
    static final int GALLERY_PAGE_SIZE = GALLERY_COLUMNS * GALLERY_ROWS;
    private static final int CALL_DIAL_COLUMNS = 3;
    private static final int CALL_DIAL_ROWS = 4;
    private static final int CALL_MAX_NUMBER_LENGTH = PhoneData.PHONE_NUMBER_LENGTH;
    private static final int CAMERA_DEFAULT_MIN_FOV = 18;
    private static final int CAMERA_DEFAULT_MAX_FOV = 110;
    private static final int CAMERA_REAR_MIN_ZOOM_LEVEL = -4;
    private static final int CAMERA_REAR_MAX_ZOOM_LEVEL = 4;
    private static final int CAMERA_SELFIE_MIN_ZOOM_LEVEL = -3;
    private static final int CAMERA_SELFIE_MAX_ZOOM_LEVEL = 3;
    private static final float CAMERA_REAR_MAX_ZOOM_FACTOR = 4.0F;
    private static final float CAMERA_SELFIE_MAX_ZOOM_FACTOR = 3.0F;
    private static final int CAMERA_ZOOM_INDICATOR_TICKS = 30;
    private static final int STATUS_TICKS = 60;
    private static final double CAMERA_LOOK_TURN_SCALE = 3.0D;
    static final int BANK_SCAN_HOLD_TICKS = 28;
    static final int BANK_PAGE_HOME = 0;
    static final int BANK_PAGE_TRANSFER = 1;
    static final int BANK_PAGE_SCAN = 2;
    static final int BANK_PAGE_PAYMENT = 3;
    static final int BANK_PAGE_RECEIVE = 4;
    static final int BANK_PAGE_SLIP = 5;
    static final String[] CALL_DIAL_DIGITS = {
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "", "0", ""
    };
    float scale;
    int displayX;
    int displayY;
    int displayWidth;
    int displayHeight;
    private boolean unlocked;
    boolean cameraMode;
    private boolean layoutFullScreen;
    private boolean layoutRightSide;
    private final PhonePanelBuffer panelBuffer = new PhonePanelBuffer();
    boolean galleryMode;
    boolean photoViewerMode;
    boolean callAppMode;
    boolean callContactsMode;
    int recentsScrollOffset;
    int chatMouseX = -1;
    int chatMouseY = -1;
    final PhoneChatApp chat = new PhoneChatApp(this);
    boolean chatScanActive;
    int chatScanHoverTicks;
    UUID chatScanHoverTargetId;
    boolean callSessionMode;
    boolean addContactMode;
    String addContactName = "";
    String addContactNumber = "";
    boolean addContactNameFieldFocused = true;
    String editingContactOriginalNumber = null;
    boolean chatAppMode;
    boolean bankAppMode;
    boolean capturePending;
    private int captureFlashTicks;
    private Component statusMessage;
    private int statusTicks;
    int galleryPage;
    int viewerPhotoIndex = -1;
    String dialedNumber = "";
    String activeCallNumber = "";
    String activeCallName = "";
    String bankTransferNumber = "";
    String bankTransferAmount = "";
    String bankPaymentName = "";
    String bankSlipTargetNumber = "";
    String bankSlipTargetName = "";
    String bankSlipTime = "";
    Component bankStatus = Component.empty();
    long bankBalance;
    long bankSlipAmount;
    long bankSlipBalance;
    int bankPage = BANK_PAGE_HOME;
    boolean bankAmountFocused;
    private boolean bankReceiveActive;
    private boolean closingForBankReceiveWorld;
    private UUID bankScanHoverTargetId;
    int bankScanHoverTicks;
    private int bankScanRequestCooldown;
    boolean activeCallIncoming;
    boolean activeCallConnected;
    boolean activeCallMissed;
    int activeCallTicks;
    boolean callMuted;
    boolean callSpeakerEnabled;
    private long observedCallStateRevision = Long.MIN_VALUE;
    private boolean selfieCameraMode;
    private CameraType savedCameraType;
    private int savedCameraFov = -1;
    private int cameraZoomLevel;
    private float cameraZoomFactor = 1.0F;
    private int cameraZoomIndicatorTicks;
    private boolean cameraMoveMode;
    private double lastCameraMoveMouseX;
    private double lastCameraMoveMouseY;
    private boolean savedRawMouseMotion;
    private boolean outsideLookActive;
    private double savedLookCursorX;
    private double savedLookCursorY;
    private double lastOutsideLookRawX;
    private double lastOutsideLookRawY;
    private final InteractionHand openHand;
    private final BlockPos homePhonePos;
    private final boolean homePhoneMode;
    private final boolean startBankReceivePage;
    private boolean startChatProfilePage;
    private boolean startPublicChat;
    private boolean publicChatOnly;
    private final PhonePhotoStore photoStore = new PhonePhotoStore(BPhone.MOD_ID);
    private PhoneScreenLayout layoutState;
    private int callSyncCooldown;
    private int bankSyncCooldown;
    boolean settingsAppMode;
    private double lastMouseX = -1;
    private double lastMouseY = -1;
    int wallpaperContextPhotoIndex = -1;
    boolean wallpaperContextMenuOpen;
    int settingsWallpaperPage;
    int settingsPickerScroll;
    boolean notificationsAppMode;
    int notificationsScrollOffset;
    private UUID notificationDetailId;
    boolean announcementsAppMode;
    int announcementsScroll;
    private UUID announcementDetailId;
    int announcementDetailScroll;
    int announcementDeleteConfirm;
    private boolean homeWidgetsShown;
    private UUID homeBannerId;
    boolean mapAppMode;
    final PhoneMapApp map = new PhoneMapApp(this);
    boolean shopAppMode;
    int shopPage = PhoneShopSurfaceRenderer.PAGE_CATEGORIES;
    private String shopCategoryId = "";
    private String shopEntryId = "";
    int shopQuantity = 1;
    int shopScrollOffset;
    Component shopStatus = Component.empty();
    boolean shopStatusError;
    boolean shopPurchasePending;
    UUID shopStallId;
    int shopPickSlot = -1;
    String shopPriceInput = "";
    String shopMarketId = "";
    boolean shopMarketBuy = true;
    int shopMarketQuantity = 1;

    public PhoneScreen() {
        this(InteractionHand.MAIN_HAND);
    }

    public PhoneScreen(InteractionHand openHand) {
        this(openHand, false);
    }

    private PhoneScreen(InteractionHand openHand, boolean startBankReceivePage) {
        this(openHand, startBankReceivePage, false);
    }

    private PhoneScreen(InteractionHand openHand, boolean startBankReceivePage, boolean startChatProfilePage) {
        super(Component.translatable("item.bphone.phone"));
        this.openHand = openHand;
        this.homePhonePos = null;
        this.homePhoneMode = false;
        this.startBankReceivePage = startBankReceivePage;
        this.startChatProfilePage = startChatProfilePage;
    }

    public PhoneScreen(BlockPos homePhonePos) {
        super(Component.translatable("block.bphone.home_phone"));
        this.openHand = InteractionHand.MAIN_HAND;
        this.homePhonePos = homePhonePos == null ? null : homePhonePos.immutable();
        this.homePhoneMode = this.homePhonePos != null;
        this.startBankReceivePage = false;
        this.startChatProfilePage = false;
        this.unlocked = true;
        this.callAppMode = true;
    }

    static PhoneScreen bankReceiveScreen(InteractionHand openHand) {
        return new PhoneScreen(openHand == null ? InteractionHand.MAIN_HAND : openHand, true);
    }

    static PhoneScreen chatProfileScreen(InteractionHand openHand) {
        return new PhoneScreen(openHand == null ? InteractionHand.MAIN_HAND : openHand, false, true);
    }

    static PhoneScreen publicChatScreen(InteractionHand openHand) {
        PhoneScreen screen = new PhoneScreen(openHand == null ? InteractionHand.MAIN_HAND : openHand, false);
        screen.startPublicChat = true;
        screen.publicChatOnly = openHand == null;
        return screen;
    }

    @Override
    protected void init() {
        updateLayout();
        rebuildWidgets();
        requestCallSync();
        applyCallStateFromServer();
        if (startBankReceivePage && !homePhoneMode) {
            openBankReceivePage();
        }
        if (startChatProfilePage && !homePhoneMode) {
            startChatProfilePage = false;
            openChatApp(PhoneChatLayout.TAB_PROFILE);
        }
        if (startPublicChat && !homePhoneMode) {
            startPublicChat = false;
            openChatApp(PhoneChatLayout.TAB_CHATS);
            chat.openPublic(publicChatOnly);
        }
    }

    @Override
    public void onClose() {
        if (!closingForBankReceiveWorld) {
            setBankReceiveActive(false);
        }
        closeCamera();
        galleryMode = false;
        photoViewerMode = false;
        callAppMode = false;
        callContactsMode = false;
        callSessionMode = false;
        
        addContactMode = false;
        chatAppMode = false;
        chat.reset();
        bankAppMode = false;
        capturePending = false;
        captureFlashTicks = 0;
        galleryPage = 0;
        viewerPhotoIndex = -1;
        settingsAppMode = false;
        wallpaperContextMenuOpen = false;
        wallpaperContextPhotoIndex = -1;
        settingsWallpaperPage = 0;
        settingsPickerScroll = 0;
        notificationsAppMode = false;
        notificationsScrollOffset = 0;
        notificationDetailId = null;
        announcementsAppMode = false;
        announcementsScroll = 0;
        announcementDetailId = null;
        announcementDetailScroll = 0;
        announcementDeleteConfirm = 0;
        shopAppMode = false;
        resetShopState();
        mapAppMode = false;
        map.close();
        dialedNumber = "";
        activeCallNumber = "";
        activeCallName = "";
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        bankSlipTargetNumber = "";
        bankSlipTargetName = "";
        bankSlipTime = "";
        bankStatus = Component.empty();
        bankBalance = 0L;
        bankSlipAmount = 0L;
        bankSlipBalance = 0L;
        bankPage = BANK_PAGE_HOME;
        activeCallIncoming = false;
        activeCallConnected = false;
        activeCallMissed = false;
        activeCallTicks = 0;
        observedCallStateRevision = PhoneClientCallState.getRevision(homePhonePos);
        selfieCameraMode = false;
        savedCameraType = null;
        savedCameraFov = -1;
        cameraZoomLevel = 0;
        cameraZoomFactor = 1.0F;
        cameraZoomIndicatorTicks = 0;
        callSyncCooldown = 0;
        bankSyncCooldown = 0;
        bankScanHoverTargetId = null;
        bankScanHoverTicks = 0;
        bankScanRequestCooldown = 0;
        setOutsideLookMode(false);
        setCameraMoveMode(false, 0.0D, 0.0D);
        releasePhotoTextures();
        super.onClose();
    }

    @Override
    protected void rebuildWidgets() {
        updateLayout();
        clearWidgets();
        setFocused(null);
        homeWidgetsShown = false;
        if (photoViewerMode) {
            addPhotoViewerWidgets();
        } else if (callSessionMode) {
            addCallSessionWidgets();
        } else if (callContactsMode) {
            addCallContactsWidgets();
        } else if (callAppMode) {
            addCallAppWidgets();
        } else if (chatAppMode) {
            addChatAppWidgets();
        } else if (bankAppMode) {
            addBankWidgets();
        } else if (cameraMode && !isBankScanCameraMode()) {
            addCameraWidgets();
        } else if (galleryMode) {
            addGalleryWidgets();
        } else if (settingsAppMode) {
            addSettingsWidgets();
        } else if (notificationsAppMode) {
            addNotificationsWidgets();
        } else if (announcementsAppMode) {
            addNotificationsWidgets();
        } else if (shopAppMode) {
            addShopWidgets();
        } else if (mapAppMode) {
        } else if (addContactMode) {
        } else if (unlocked) {
            addHomeWidgets();
        } else {
            addLockWidgets();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        pollOutsideLook();
        PhoneUi.applyTheme(!homePhoneMode && isDarkTheme());
        if (layoutFullScreen != isPhotoCameraMode() || layoutRightSide != PhoneClientPrefs.panelRightSide()) {
            rebuildWidgets();
        }
        chatMouseX = mouseX;
        chatMouseY = mouseY;
        if (capturePending) {
            completePendingCapture();
            return;
        }
        boolean bankScanCameraMode = isBankScanCameraMode();
        boolean chatScanCameraMode = isChatScanCameraMode();
        if (cameraMode) {
            renderSurface(guiGraphics, partialTick);
            if (bankScanCameraMode) {
                renderBankCameraOverlay(guiGraphics);
            } else if (chatScanCameraMode) {
                PhoneChatSurfaceRenderer.renderChatCameraOverlay(this, guiGraphics);
            }
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            if (!bankScanCameraMode && !chatScanCameraMode) {
                renderCameraOverlayHints(guiGraphics);
            }
            if (captureFlashTicks > 0) {
                renderCaptureFlash(guiGraphics);
            }
            renderStatusToast(guiGraphics);
            return;
        }

        UiRect panel = new UiRect(displayX, displayY, displayWidth, displayHeight);
        panelBuffer.begin(guiGraphics);
        try {
            if (callSessionMode || callAppMode || callContactsMode || addContactMode || chatAppMode || bankAppMode) {
                renderCallBackdrop(guiGraphics);
            } else if (galleryMode || photoViewerMode) {
                renderMediaBackdrop(guiGraphics);
            } else if (settingsAppMode || notificationsAppMode || announcementsAppMode || shopAppMode || mapAppMode) {
                renderAppBackdrop(guiGraphics);
            } else {
                PhoneWallpaperRenderer.drawWallpaper(this, guiGraphics, currentWallpaperKey(!unlocked), panel, false);
            }

            renderSurface(guiGraphics, partialTick);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderStatusToast(guiGraphics);
        } finally {
            panelBuffer.end(guiGraphics, panel, panelRadius());
        }
    }

    private void renderStatusToast(GuiGraphics guiGraphics) {
        if (statusTicks > 0) {
            PhoneUi.renderToast(guiGraphics, new PhoneChatLayout(this), statusMessage);
        }
    }

    @Override
    public void removed() {
        setOutsideLookMode(false);
        panelBuffer.release();
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (settingsAppMode) {
            PhoneSettingsSurfaceRenderer.handleFilesDrop(this, paths);
        }
    }

    @Override
    public void tick() {
        if (homePhoneMode) {
            if (callSyncCooldown <= 0) {
                requestCallSync();
                callSyncCooldown = 10;
            } else {
                callSyncCooldown--;
            }
        }
        if (bankAppMode) {
            if (bankSyncCooldown <= 0) {
                PhoneNetworkingClient.requestBankSync();
                bankSyncCooldown = 20;
            } else {
                bankSyncCooldown--;
            }
        }
        if (chatAppMode) {
            chat.tick();
        }
        if (mapAppMode) {
            map.tick();
        }
        if (announcementDeleteConfirm > 0) {
            announcementDeleteConfirm--;
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
        if (homeWidgetsShown && !Objects.equals(currentBannerId(), homeBannerId)) {
            rebuildWidgets();
        }
        applyCallStateFromServer();
        if (isCallScreenLocked()
                && (cameraMode || galleryMode || photoViewerMode || callAppMode || callContactsMode
                || addContactMode || mapAppMode
                || chatAppMode || bankAppMode || !callSessionMode)) {
            setBankReceiveActive(false);
            closeCamera();
            cameraMode = false;
            galleryMode = false;
            photoViewerMode = false;
            callAppMode = false;
            callContactsMode = false;
            
            addContactMode = false;
            chatAppMode = false;
            chatScanActive = false;
            bankAppMode = false;
            settingsAppMode = false;
            notificationsAppMode = false;
            announcementsAppMode = false;
            shopAppMode = false;
            if (mapAppMode) {
                mapAppMode = false;
                map.close();
            }
            wallpaperContextMenuOpen = false;
            callSessionMode = true;
            rebuildWidgets();
        }
        if (captureFlashTicks > 0) {
            captureFlashTicks--;
        }

        if (cameraZoomIndicatorTicks > 0) {
            cameraZoomIndicatorTicks--;
        }

        if (activeCallConnected) {
            activeCallTicks = PhoneClientCallState.getConnectedDurationTicks(homePhonePos);
        }

        if (allowsWorldMovement()) {
            syncCameraMovementKeys();
        } else {
            releaseWorldMovementKeys();
        }

        if (bankScanRequestCooldown > 0) {
            bankScanRequestCooldown--;
        }

        if (isBankScanCameraMode()) {
            tickBankScanHover();
        } else {
            resetBankScanHover();
        }

        if (isChatScanCameraMode()) {
            tickChatScanHover();
        } else {
            chatScanHoverTicks = 0;
            chatScanHoverTargetId = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (allowsWorldMovement() && !(isPhotoCameraMode() && !cameraMoveMode && keyCode == InputConstants.KEY_SPACE)
                && handleMovementKey(keyCode, scanCode, true)) {
            return true;
        }

        if (isChatScanCameraMode()) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                if (cameraMoveMode) {
                    setCameraMoveMode(false, lastCameraMoveMouseX, lastCameraMoveMouseY);
                } else {
                    closeChatScanCamera();
                }
            }
            return true;
        }

        if (isBankScanCameraMode() && keyCode == InputConstants.KEY_ESCAPE) {
            if (cameraMoveMode) {
                setCameraMoveMode(false, lastCameraMoveMouseX, lastCameraMoveMouseY);
                return true;
            }
            closeBankScanCamera();
            return true;
        }

        if (cameraMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (cameraMoveMode) {
                setCameraMoveMode(false, lastCameraMoveMouseX, lastCameraMoveMouseY);
                return true;
            }

            closeCamera();
            capturePending = false;
            captureFlashTicks = 0;
            unlocked = true;
            rebuildWidgets();
            return true;
        }

        if (isBankScanCameraMode() && !cameraMoveMode && (keyCode == InputConstants.KEY_SPACE
                || keyCode == InputConstants.KEY_RETURN
                || keyCode == InputConstants.KEY_NUMPADENTER)) {
            tryScanBankPaymentTarget();
            return true;
        }

        if (isPhotoCameraMode() && (keyCode == InputConstants.KEY_SPACE
                || keyCode == InputConstants.KEY_RETURN
                || keyCode == InputConstants.KEY_NUMPADENTER)) {
            requestPhotoCapture();
            return true;
        }

        if (callSessionMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (isCallScreenLocked()) {
                closeScreenKeepCall();
                return true;
            }
            endCallSession(true);
            return true;
        }

        if (callAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (isCallScreenLocked()) {
                closeScreenKeepCall();
                return true;
            }
            closeCallApp();
            return true;
        }

        if (callContactsMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (isCallScreenLocked()) {
                closeScreenKeepCall();
                return true;
            }
            openCallDialPage();
            return true;
        }

        if (addContactMode && keyCode == InputConstants.KEY_ESCAPE) {
            editingContactOriginalNumber = null;
            addContactName = "";
            addContactNumber = "";
            addContactNameFieldFocused = true;
            openCallContactsPage();
            return true;
        }

        if (chatAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (!chat.escape()) {
                closeChatApp();
            }
            return true;
        }

        if (bankAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (bankPage == BANK_PAGE_TRANSFER || bankPage == BANK_PAGE_PAYMENT
                    || bankPage == BANK_PAGE_RECEIVE || bankPage == BANK_PAGE_SLIP) {
                openBankHomePage();
                return true;
            }
            closeBankApp();
            return true;
        }

        if (settingsAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            PhoneSettingsSurfaceRenderer.back(this);
            return true;
        }

        if (notificationsAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (notificationDetailId != null) {
                closeNotificationDetail();
            } else {
                closeNotificationsApp();
            }
            return true;
        }

        if (announcementsAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (announcementDetailId != null) {
                closeAnnouncementDetail();
            } else {
                closeAnnouncementsApp();
            }
            return true;
        }

        if (mapAppMode && map.keyPressed(keyCode, hasControlDown())) {
            return true;
        }

        if (mapAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            if (!map.escape()) {
                closeMapApp();
            }
            return true;
        }

        if (shopAppMode && PhoneTradeSurfaceRenderer.keyPressed(this, keyCode)) {
            return true;
        }

        if (shopAppMode && keyCode == InputConstants.KEY_ESCAPE) {
            shopBack();
            return true;
        }

        if (galleryMode && wallpaperContextMenuOpen && keyCode == InputConstants.KEY_ESCAPE) {
            closeWallpaperContextMenu();
            return true;
        }

        if (callAppMode) {
            String digit = getDigitForKey(keyCode);
            if (digit != null) {
                appendDialDigit(digit);
                return true;
            }

            if (keyCode == InputConstants.KEY_BACKSPACE) {
                removeLastDialDigit();
                return true;
            }

            if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && !dialedNumber.isEmpty()) {
                startCallSession(dialedNumber);
                return true;
            }
        }

        if (addContactMode && keyCode == InputConstants.KEY_BACKSPACE) {
            if (addContactNameFieldFocused && !addContactName.isEmpty()) {
                addContactName = addContactName.substring(0, addContactName.length() - 1);
                return true;
            }
            if (!addContactNameFieldFocused && !addContactNumber.isEmpty()) {
                addContactNumber = addContactNumber.substring(0, addContactNumber.length() - 1);
                return true;
            }
        }

        if (chatAppMode && chat.keyPressed(keyCode, hasControlDown())) {
            return true;
        }

        if (bankAppMode && acceptsBankNumericInput()) {
            String digit = getDigitForKey(keyCode);
            if (digit != null) {
                appendBankDigit(digit);
                return true;
            }

            if (keyCode == InputConstants.KEY_BACKSPACE) {
                removeLastBankDigit();
                return true;
            }

            if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                    && canSendBankTransfer()) {
                sendBankTransfer();
                return true;
            }
        }

        if (callSessionMode && activeCallIncoming && !activeCallConnected
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            connectActiveCall();
            return true;
        }

        if (callSessionMode && activeCallMissed
                && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            endCallSession(true);
            return true;
        }

        if (photoViewerMode && keyCode == InputConstants.KEY_ESCAPE) {
            photoViewerMode = false;
            galleryMode = true;
            rebuildWidgets();
            return true;
        }

        if (photoViewerMode && keyCode == InputConstants.KEY_LEFT) {
            stepViewer(-1);
            return true;
        }

        if (photoViewerMode && keyCode == InputConstants.KEY_RIGHT) {
            stepViewer(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (allowsWorldMovement() && handleMovementKey(keyCode, scanCode, false)) {
            return true;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (chatAppMode) {
            return chat.charTyped(codePoint) || super.charTyped(codePoint, modifiers);
        }

        if (mapAppMode && map.charTyped(codePoint)) {
            return true;
        }

        if (shopAppMode && PhoneTradeSurfaceRenderer.charTyped(this, codePoint)) {
            return true;
        }

        if (bankAppMode && acceptsBankNumericInput() && Character.isDigit(codePoint)) {
            appendBankDigit(String.valueOf(codePoint));
            return true;
        }

        if (addContactMode) {
            if (addContactNameFieldFocused && isAcceptedChatCharacter(codePoint) && addContactName.length() < PhoneData.MAX_CONTACT_NAME_LENGTH) {
                addContactName = addContactName + codePoint;
                return true;
            }
            if (!addContactNameFieldFocused && Character.isDigit(codePoint) && addContactNumber.length() < PhoneData.PHONE_NUMBER_LENGTH) {
                addContactNumber = addContactNumber + codePoint;
                return true;
            }
            return false;
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (outsideLookActive && button == 1) {
            return true;
        }
        if (!cameraMode && !homePhoneMode && !isInsidePhonePanel(mouseX, mouseY)) {
            if (chatAppMode) {
                chat.unfocusComposer();
            }
            if (button == 1) {
                setOutsideLookMode(true);
                return true;
            }
            return true;
        }
        if (isBankScanCameraMode()) {
            if (button == 0 && getBankScanReceiveButtonBounds().contains(mouseX, mouseY)) {
                openBankReceivePage();
                return true;
            }

            if (button == 0 && getBankScanEyeButtonBounds().contains(mouseX, mouseY)) {
                setCameraMoveMode(true, mouseX, mouseY);
                return true;
            }

            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (handled) {
                return true;
            }

            if ((button == 0 || button == 1) && !getScanPanelBounds().contains(mouseX, mouseY) && !cameraMoveMode) {
                tryScanBankPaymentTarget();
                return true;
            }

            return false;
        }

        if (isChatScanCameraMode()) {
            if (button == 0) {
                if (cameraMoveMode) {
                    setCameraMoveMode(false, mouseX, mouseY);
                } else {
                    setCameraMoveMode(true, mouseX, mouseY);
                }
            }
            return true;
        }

        if (cameraMode && button == 1 && !capturePending) {
            requestPhotoCapture();
            return true;
        }

        if (cameraMode && cameraMoveMode && button == 0) {
            setCameraMoveMode(false, mouseX, mouseY);
            return true;
        }

        if (button == 0 && cameraMode && !cameraMoveMode && !capturePending && getCameraPreviewBounds().contains(mouseX, mouseY)) {
            openGallery();
            return true;
        }

        if (button == 0 && callSessionMode) {
            if (activeCallIncoming && !activeCallConnected && getCallConnectButtonBounds().contains(mouseX, mouseY)) {
                connectActiveCall();
                return true;
            }

            if (activeCallMissed && getCallConnectButtonBounds().contains(mouseX, mouseY)) {
                endCallSession(true);
                return true;
            }

            if (activeCallIncoming && !activeCallConnected) {
                UiRect connectBounds = getCallConnectButtonBounds();
                int gap = Math.max(5, Math.round(6 * scale));
                UiRect incomingHangupBounds = new UiRect(connectBounds.right() + gap, connectBounds.top,
                        connectBounds.width, connectBounds.height);
                if (incomingHangupBounds.contains(mouseX, mouseY)) {
                    endCallSession(true);
                    return true;
                }
            } else if (getCallHangupButtonBounds().contains(mouseX, mouseY)) {
                endCallSession(true);
                return true;
            }

            if (getCallMuteButtonBounds().contains(mouseX, mouseY)) {
                callMuted = !callMuted;
                if (homePhoneMode) {
                    PhoneNetworkingClient.requestToggleMute(homePhonePos);
                } else {
                    PhoneNetworkingClient.requestToggleMute();
                }
                return true;
            }

            if (getCallSpeakerButtonBounds().contains(mouseX, mouseY)) {
                callSpeakerEnabled = !callSpeakerEnabled;
                if (homePhoneMode) {
                    PhoneNetworkingClient.requestToggleSpeaker(homePhonePos);
                } else {
                    PhoneNetworkingClient.requestToggleSpeaker();
                }
                return true;
            }
        }

        if (button == 0 && callAppMode) {
            if (getCallListMenuBounds().contains(mouseX, mouseY)) {
                openCallContactsPage();
                return true;
            }

            if (getCallDialMenuBounds().contains(mouseX, mouseY)) {
                return true;
            }

            int clickedDigitIndex = getDialPadDigitIndexAt(mouseX, mouseY);
            if (clickedDigitIndex >= 0) {
                appendDialDigit(CALL_DIAL_DIGITS[clickedDigitIndex]);
                return true;
            }

            if (getDialDeleteButtonBounds().contains(mouseX, mouseY)) {
                removeLastDialDigit();
                return true;
            }

            if (!dialedNumber.isEmpty() && getDialCallButtonBounds().contains(mouseX, mouseY)) {
                startCallSession(dialedNumber);
                return true;
            }
        }

        if (button == 0 && callContactsMode && !addContactMode) {
            if (getCallDialMenuBounds().contains(mouseX, mouseY)) {
                openCallDialPage();
                return true;
            }

            if (getCallListMenuBounds().contains(mouseX, mouseY)) {
                return true;
            }

            String saveCandidateNumber = getContactSaveCandidateNumber();
            if (!saveCandidateNumber.isEmpty() && !hasSavedContact(saveCandidateNumber)
                    && getContactSaveButtonBounds().contains(mouseX, mouseY)) {
                requestSaveContact(saveCandidateNumber, "");
                return true;
            }

            List<PhoneContact> contacts = getPhoneContacts();
            int contactIndex = getContactIndexAt(mouseX, mouseY, contacts);
            if (contactIndex >= 0 && contactIndex < contacts.size()) {
                PhoneContact contact = contacts.get(contactIndex);
                if (getContactDeleteButtonBounds(contactIndex, contacts.size()).contains(mouseX, mouseY)) {
                    requestDeleteContact(contact.number());
                } else if (getContactEditButtonBounds(contactIndex, contacts.size()).contains(mouseX, mouseY)) {
                    openEditContactPage(contact);
                } else {
                    startCallSession(contact.number());
                }
                return true;
            }
        }

        if (chatAppMode) {
            return PhoneChatSurfaceRenderer.click(this, mouseX, mouseY, button);
        }

        if (notificationsAppMode) {
            return PhoneNotificationsSurfaceRenderer.handleClick(this, mouseX, mouseY, button);
        }

        if (announcementsAppMode) {
            return PhoneAnnouncementsSurfaceRenderer.handleClick(this, mouseX, mouseY, button);
        }

        if (shopAppMode) {
            return PhoneShopSurfaceRenderer.handleClick(this, mouseX, mouseY, button);
        }

        if (mapAppMode) {
            return PhoneMapSurfaceRenderer.click(this, mouseX, mouseY, button);
        }

        if (galleryMode && wallpaperContextMenuOpen) {
            return PhoneSettingsSurfaceRenderer.handleWallpaperMenuClick(this, mouseX, mouseY);
        }

        if (button == 0 && bankAppMode) {
            if (getBankBackButtonBounds().contains(mouseX, mouseY)) {
                if (bankPage == BANK_PAGE_HOME) {
                    closeBankApp();
                } else {
                    openBankHomePage();
                }
                return true;
            }

            if (bankPage == BANK_PAGE_HOME && getBankHomeReceiveButtonBounds().contains(mouseX, mouseY)) {
                openBankReceivePage();
                return true;
            }

            if (bankPage == BANK_PAGE_TRANSFER && getBankTransferNumberBounds().contains(mouseX, mouseY)) {
                bankAmountFocused = false;
                return true;
            }

            if (bankPage == BANK_PAGE_TRANSFER && getBankTransferAmountBounds().contains(mouseX, mouseY)) {
                bankAmountFocused = true;
                return true;
            }

            if (bankPage == BANK_PAGE_HOME && getBankHomeTransferButtonBounds().contains(mouseX, mouseY)) {
                openBankTransferPage();
                return true;
            }

            if (bankPage == BANK_PAGE_HOME && getBankHomeScanButtonBounds().contains(mouseX, mouseY)) {
                openBankScanCamera();
                return true;
            }

            if (bankPage == BANK_PAGE_TRANSFER && getBankTransferButtonBounds().contains(mouseX, mouseY)
                    && canSendBankTransfer()) {
                sendBankTransfer();
                return true;
            }

            if (bankPage == BANK_PAGE_PAYMENT && getBankTransferButtonBounds().contains(mouseX, mouseY)
                    && canSendBankTransfer()) {
                sendBankTransfer();
                return true;
            }

            if (bankPage == BANK_PAGE_RECEIVE && getBankReceiveEyeButtonBounds().contains(mouseX, mouseY)) {
                showBankReceiveQrToWorld();
                return true;
            }

            if ((bankPage == BANK_PAGE_TRANSFER || bankPage == BANK_PAGE_PAYMENT
                    || bankPage == BANK_PAGE_RECEIVE || bankPage == BANK_PAGE_SLIP)
                    && getBankCancelButtonBounds().contains(mouseX, mouseY)) {
                openBankHomePage();
                return true;
            }
        }

        if (button == 0 && galleryMode) {
            int clickedPhotoIndex = getGalleryPhotoIndexAt(mouseX, mouseY, getPhotos());
            if (clickedPhotoIndex >= 0) {
                openPhotoViewer(clickedPhotoIndex);
                return true;
            }
        }

        if (button == 1 && galleryMode) {
            int clickedPhotoIndex = getGalleryPhotoIndexAt(mouseX, mouseY, getPhotos());
            if (clickedPhotoIndex >= 0) {
                wallpaperContextPhotoIndex = clickedPhotoIndex;
                wallpaperContextMenuOpen = true;
                rebuildWidgets();
                return true;
            }
        }

        if (button == 0 && settingsAppMode) {
            return PhoneSettingsSurfaceRenderer.handleClick(this, mouseX, mouseY);
        }

        if (button == 0 && addContactMode) {
            if (getAddContactSaveButtonBounds().contains(mouseX, mouseY)) {
                if (!addContactNumber.isEmpty()) {
                    if (editingContactOriginalNumber != null && !editingContactOriginalNumber.equals(addContactNumber)) {
                        requestDeleteContact(editingContactOriginalNumber);
                    }
                    String displayName = addContactName.isBlank() ? addContactNumber : addContactName;
                    requestSaveContact(addContactNumber, displayName);
                    editingContactOriginalNumber = null;
                    addContactName = "";
                    addContactNumber = "";
                    addContactNameFieldFocused = true;
                    openCallContactsPage();
                }
                return true;
            }

            if (getAddContactCancelButtonBounds().contains(mouseX, mouseY)) {
                editingContactOriginalNumber = null;
                addContactName = "";
                addContactNumber = "";
                addContactNameFieldFocused = true;
                openCallContactsPage();
                return true;
            }

            if (getAddContactNameBounds().contains(mouseX, mouseY)) {
                addContactNameFieldFocused = true;
                return true;
            }

            if (getAddContactNumberBounds().contains(mouseX, mouseY)) {
                addContactNameFieldFocused = false;
                return true;
            }
        }

        if (button == 0 && callContactsMode && !addContactMode) {
            if (getAddContactHeaderButtonBounds().contains(mouseX, mouseY)) {
                openAddContactPage();
                return true;
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            return true;
        }

        if (cameraMode && cameraMoveMode && button == 0) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (outsideLookActive) {
            return true;
        }
        if (cameraMode && cameraMoveMode) {
            lastCameraMoveMouseX = mouseX;
            lastCameraMoveMouseY = mouseY;
            return true;
        }

        if (mapAppMode) {
            PhoneMapSurfaceRenderer.drag(this, mouseX, mouseY, button, dragX, dragY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (outsideLookActive) {
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (cameraMode && cameraMoveMode) {
            rotateCameraPlayer(mouseX - lastCameraMoveMouseX, mouseY - lastCameraMoveMouseY);
            lastCameraMoveMouseX = mouseX;
            lastCameraMoveMouseY = mouseY;
            return;
        }

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (outsideLookActive && button == 1) {
            setOutsideLookMode(false);
            return true;
        }
        if (cameraMode && cameraMoveMode && button == 0) {
            return true;
        }

        if (mapAppMode) {
            PhoneMapSurfaceRenderer.release(this, mouseX, mouseY, button);
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (cameraMode && scrollY != 0.0D) {
            if (isBankScanCameraMode() || isChatScanCameraMode()) {
                return true;
            }
            adjustCameraZoom(scrollY);
            return true;
        }

        if (chatAppMode && scrollY != 0.0D) {
            chat.scroll(scrollY);
            return true;
        }

        if (notificationsAppMode && scrollY != 0.0D) {
            PhoneNotificationsSurfaceRenderer.scroll(this, scrollY);
            return true;
        }

        if (announcementsAppMode && scrollY != 0.0D) {
            PhoneAnnouncementsSurfaceRenderer.scroll(this, scrollY);
            return true;
        }

        if (shopAppMode && scrollY != 0.0D) {
            PhoneShopSurfaceRenderer.scroll(this, scrollY);
            return true;
        }

        if (mapAppMode && scrollY != 0.0D) {
            PhoneMapSurfaceRenderer.scroll(this, mouseX, mouseY, scrollY);
            return true;
        }

        if (settingsAppMode && scrollY != 0.0D) {
            PhoneSettingsSurfaceRenderer.scroll(this, scrollY);
            return true;
        }

        if (callAppMode && !callSessionMode && !callContactsMode && scrollY != 0.0D) {
            List<CallLogEntry> entries = PhoneClientCallState.getCallLogEntries();
            int rowHeight = Math.max(16, Math.round(20 * scale));
            UiRect contentBounds = getCallSurfaceBounds();
            int headerHeight = getCallHeaderHeight();
            int topPadding = Math.max(8, Math.round(10 * scale));
            int scrollTop = contentBounds.top + headerHeight + topPadding;
            int bottomClip = contentBounds.bottom() - Math.max(8, Math.round(10 * scale));
            int visibleRows = Math.max(1, (bottomClip - scrollTop) / rowHeight);
            int maxOffset = Math.max(0, entries.size() - visibleRows);
            recentsScrollOffset = Mth.clamp(recentsScrollOffset + (int) -Math.signum(scrollY), 0, maxOffset);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateLayout() {
        int margin = Math.max(3, Math.round(height * PANEL_MARGIN_RATIO));
        scale = Math.min((float) (height - 2 * margin) / DISPLAY_HEIGHT,
                width * PANEL_MAX_WIDTH_RATIO / DISPLAY_WIDTH);
        layoutFullScreen = isPhotoCameraMode();
        layoutRightSide = PhoneClientPrefs.panelRightSide();
        if (layoutFullScreen) {
            displayX = 0;
            displayY = 0;
            displayWidth = width;
            displayHeight = height;
        } else {
            displayWidth = Math.round(DISPLAY_WIDTH * scale);
            displayHeight = Math.round(DISPLAY_HEIGHT * scale);
            displayX = layoutRightSide ? width - margin - displayWidth : margin;
            displayY = (height - displayHeight) / 2;
        }
        layoutState = new PhoneScreenLayout(scale, displayX, displayY, displayWidth, displayHeight);
    }

    private void addLockWidgets() {
        int buttonSize = Math.max(24, Math.round(32 * scale));
        int x = displayX + (displayWidth - buttonSize) / 2;
        int y = displayY + displayHeight - buttonSize - Math.round(38 * scale);

        addRenderableWidget(new ImageButton(
                x, y, buttonSize, buttonSize,
                0, 0, 0, UNLOCK_TEXTURE, UNLOCK_TEXTURE_SIZE, UNLOCK_TEXTURE_SIZE,
                button -> {
                    unlocked = true;
                    rebuildWidgets();
                }) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                int r = this.width / 2;
                int bg = this.isHovered() ? 0x991C1F26 : 0x7214161B;
                PhoneUi.fillRounded(guiGraphics, this.getX(), this.getY(), this.width, this.height, r, bg);
                PhoneUi.strokeRounded(guiGraphics, this.getX(), this.getY(), this.width, this.height, r, 1, 0x55FFFFFF);
                int bodyW = Math.max(8, Math.round(this.width * 0.42F));
                int bodyH = Math.max(6, Math.round(this.height * 0.34F));
                int bodyX = this.getX() + (this.width - bodyW) / 2;
                int bodyY = this.getY() + Math.round(this.height * 0.46F);
                int shackleW = Math.max(6, Math.round(bodyW * 0.72F));
                int shackleH = Math.max(5, Math.round(bodyH * 0.85F));
                int shackleX = this.getX() + (this.width - shackleW) / 2;
                int shackleY = bodyY - shackleH + 2;
                PhoneUi.strokeRounded(guiGraphics, shackleX, shackleY, shackleW, shackleH + 2, shackleW / 2,
                        Math.max(1, PhoneUi.pixelDensity()), 0xF2FFFFFF);
                PhoneUi.fillRounded(guiGraphics, bodyX, bodyY, bodyW, bodyH, Math.max(2, bodyW / 4), 0xF2FFFFFF);
            }
        });
    }

    private void addHomeWidgets() {
        int scaledIconSize = Math.round(ICON_SIZE * scale);
        int appsPerRow = 4;
        int usableWidth = Math.max(0, displayWidth - (scaledIconSize * appsPerRow));
        int space = Math.max(2, usableWidth / (appsPerRow + 1));
        int leftover = usableWidth - (space * (appsPerRow + 1));
        int leftInset = space + (leftover / 2);
        int iconGap = space;
        int rowGap = Math.round(23 * scale);

        int iconX1 = displayX + leftInset;
        int iconX2 = iconX1 + scaledIconSize + iconGap;
        int iconX3 = iconX2 + scaledIconSize + iconGap;
        int iconX4 = iconX3 + scaledIconSize + iconGap;
        int iconY1 = displayY + Math.round(displayHeight * 0.40F);
        int iconY2 = iconY1 + scaledIconSize + rowGap;

        addRenderableWidget(createAppIcon(iconX1, iconY1, APP_CALL_TEXTURE,
                Component.translatable("screen.bphone.phone.app.call"), this::openCallApp));
        addRenderableWidget(createAppIcon(iconX2, iconY1, APP_CHAT_TEXTURE,
                Component.translatable("screen.bphone.phone.app.chat"), this::openChatApp,
                () -> PhoneClientChatState.getRequestCount() + PhoneClientChatState.getTotalUnread()));
        addRenderableWidget(createAppIcon(iconX3, iconY1, APP_SHOP_TEXTURE,
                Component.translatable("screen.bphone.phone.app.shop"), this::openShopApp));
        addRenderableWidget(createAppIcon(iconX4, iconY1, APP_GOOGLE_TEXTURE,
                Component.translatable("screen.bphone.phone.app.settings"), this::openSettingsApp));

        addRenderableWidget(createAppIcon(iconX1, iconY2, APP_GALLERY_TEXTURE,
                Component.translatable("screen.bphone.phone.app.gallery"), this::openGallery));
        addRenderableWidget(createAppIcon(iconX2, iconY2, APP_BANK_TEXTURE,
                Component.translatable("screen.bphone.phone.app.bank"), this::openBankApp));
        addRenderableWidget(createAppIcon(iconX3, iconY2, APP_CAMERA_TEXTURE,
                Component.translatable("screen.bphone.phone.app.camera"), this::openCamera));
        addRenderableWidget(createAppIcon(iconX4, iconY2, APP_NOTIFICATION_TEXTURE,
                Component.translatable("screen.bphone.phone.app.notifications"), this::openNotificationsApp,
                NotificationClientState::getUnreadCount));

        int iconY3 = iconY2 + scaledIconSize + rowGap;
        addRenderableWidget(createAppIcon(iconX1, iconY3, APP_ANNOUNCEMENT_TEXTURE,
                Component.translatable("screen.bphone.phone.app.announcements"), () -> openAnnouncementsApp(null),
                AnnouncementClientState::getUnreadCount));
        addRenderableWidget(createAppIcon(iconX2, iconY3, APP_MAP_TEXTURE,
                Component.translatable("screen.bphone.phone.app.map"), this::openMapApp));

        homeWidgetsShown = true;
        homeBannerId = currentBannerId();
        if (homeBannerId != null) {
            int inset = Math.round(8 * scale);
            int bannerHeight = Math.round(24 * scale);
            UiRect banner = new UiRect(displayX + inset, iconY1 - bannerHeight - Math.round(12 * scale),
                    displayWidth - 2 * inset, bannerHeight);
            UUID bannerId = homeBannerId;
            addRenderableWidget(new ImageButton(banner.left, banner.top, banner.width, banner.height, 0, 0, 0,
                    APP_ANNOUNCEMENT_TEXTURE, ICON_SIZE, ICON_SIZE, button -> openAnnouncementsApp(bannerId)) {
                @Override
                public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                    PhoneAnnouncementsSurfaceRenderer.renderBanner(PhoneScreen.this, guiGraphics,
                            new UiRect(getX(), getY(), width, height), isHovered());
                }
            });
        }
    }

    private UUID currentBannerId() {
        Announcement pinned = AnnouncementClientState.newestUnreadStarred();
        return pinned == null ? null : pinned.id();
    }

    private ImageButton createAppIcon(int x, int y, ResourceLocation texture, Component label, Runnable clickAction) {
        return createAppIcon(x, y, texture, label, clickAction, () -> 0);
    }

    private ImageButton createAppIcon(int x, int y, ResourceLocation texture, Component label, Runnable clickAction,
                                      IntSupplier badgeCount) {
        int scaledIconSize = Math.round(ICON_SIZE * scale);
        return new ImageButton(
                x, y, scaledIconSize, scaledIconSize,
                0, 0, 0, texture, ICON_SIZE, ICON_SIZE,
                button -> clickAction.run()) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                guiGraphics.blit(this.resourceLocation, this.getX(), this.getY(), this.width, this.height, 0.0F, 0.0F,
                        ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) {
                    return;
                }

                Font font = PhoneFont.get();
                PhoneChatLayout layout = new PhoneChatLayout(PhoneScreen.this);
                int paddingX = Math.max(2, Math.round(3 * scale));
                int paddingY = Math.max(1, Math.round(2 * scale));
                int labelWidth = Math.max(this.width + Math.round(18 * scale), Math.round(46 * scale)) - 2 * paddingX;
                float labelScale = Math.min(layout.captionScale, (float) labelWidth / Math.max(1, font.width(label)));
                int labelTextWidth = PhoneScreenDraw.scaledTextWidth(font, label, labelScale);
                int labelX = this.getX() + (this.width - labelTextWidth) / 2;
                int labelY = this.getY() + this.height + Math.max(2, Math.round(3 * scale));
                int labelHeight = PhoneScreenDraw.scaledTextHeight(font, labelScale);
                int pillHeight = labelHeight + 2 * paddingY;
                PhoneUi.fillRounded(guiGraphics, labelX - paddingX, labelY - paddingY,
                        labelTextWidth + 2 * paddingX, pillHeight, pillHeight / 2, 0x80000000);
                PhoneScreenDraw.drawScaledText(guiGraphics, font, label, labelX, labelY, 0xFFFFFFFF, false, labelScale);

                int badge = badgeCount.getAsInt();
                if (badge > 0) {
                    PhoneUi.renderBadge(guiGraphics, layout, this.getX() + this.width - layout.u(7),
                            this.getY() + layout.u(2), badge);
                }
            }
        };
    }

    private void addGalleryWidgets() {
    }

    private void addCallAppWidgets() {
    }

    private void addCallContactsWidgets() {
    }

    private void addCallSessionWidgets() {
    }

    private void addChatAppWidgets() {
    }

    private void addBankWidgets() {
    }

    private void addSettingsWidgets() {
    }

    private void addNotificationsWidgets() {
    }

    private void addShopWidgets() {
    }

    private void addPhotoViewerWidgets() {
        UiRect contentBounds = getMediaSurfaceBounds();
        int deleteButtonSize = Math.round(24 * scale);
        int deleteButtonX = contentBounds.right() - deleteButtonSize - Math.round(6 * scale);
        int deleteButtonY = contentBounds.top + Math.round(8 * scale);

        addRenderableWidget(new ImageButton(
                deleteButtonX, deleteButtonY, deleteButtonSize, deleteButtonSize,
                0, 0, 0, DELETE_BUTTON_TEXTURE, 24, 24,
                button -> deleteCurrentPhoto()) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                guiGraphics.blit(this.resourceLocation, this.getX(), this.getY(), this.width, this.height,
                        0.0F, 0.0F, 24, 24, 24, 24);
            }
        });

    }

    private void addCameraWidgets() {
        UiRect shutterBounds = getCameraShutterButtonBounds();
        UiRect flipButtonBounds = getCameraFlipButtonBounds();

        addRenderableWidget(new ImageButton(
                flipButtonBounds.left, flipButtonBounds.top, flipButtonBounds.width, flipButtonBounds.height,
                0, 0, 0, CAMFLIP_BUTTON_TEXTURE, 24, 24,
                button -> toggleCameraFlip()) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                guiGraphics.blit(this.resourceLocation, this.getX(), this.getY(), this.width, this.height, 0.0F, 0.0F,
                        24, 24, 24, 24);
            }
        });

        addRenderableWidget(new ImageButton(
                shutterBounds.left, shutterBounds.top, shutterBounds.width, shutterBounds.height,
                0, 0, 0, SHUTTER_BUTTON_TEXTURE, 60, 60,
                button -> setCameraMoveMode(true,
                        button.getX() + (button.getWidth() / 2.0D),
                        button.getY() + (button.getHeight() / 2.0D))) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                guiGraphics.blit(this.resourceLocation, this.getX(), this.getY(), this.width, this.height, 0.0F, 0.0F,
                        60, 60, 60, 60);
            }
        });
    }

    private static class ImageButton extends Button {
        protected final ResourceLocation resourceLocation;
        private final int textureWidth;
        private final int textureHeight;

        ImageButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffTex,
                    ResourceLocation resourceLocation, int textureWidth, int textureHeight, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.resourceLocation = resourceLocation;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.blit(resourceLocation, getX(), getY(), width, height, 0.0F, 0.0F,
                    textureWidth, textureHeight, textureWidth, textureHeight);
        }
    }

    private void renderSurface(GuiGraphics guiGraphics, float partialTick) {
        if (isChatScanCameraMode()) {
            return;
        }

        if (photoViewerMode) {
            renderPhotoViewerSurface(guiGraphics);
            return;
        }

        if (callSessionMode) {
            renderCallSessionSurface(guiGraphics);
            return;
        }

        if (callContactsMode) {
            renderCallContactsSurface(guiGraphics);
            if (addContactMode) {
                renderAddContactSurface(guiGraphics);
            }
            return;
        }

        if (callAppMode) {
            renderCallAppSurface(guiGraphics);
            return;
        }

        if (chatAppMode) {
            renderChatAppSurface(guiGraphics);
            return;
        }

        if (cameraMode) {
            renderCameraSurface(guiGraphics, partialTick);
            return;
        }

        if (bankAppMode) {
            renderBankSurface(guiGraphics);
            return;
        }

        if (galleryMode) {
            renderGallerySurface(guiGraphics);
            PhoneSettingsSurfaceRenderer.renderWallpaperMenu(this, guiGraphics);
            return;
        }

        if (settingsAppMode) {
            PhoneSettingsSurfaceRenderer.renderSettingsSurface(this, guiGraphics);
            return;
        }

        if (notificationsAppMode) {
            PhoneNotificationsSurfaceRenderer.render(this, guiGraphics);
            return;
        }

        if (announcementsAppMode) {
            PhoneAnnouncementsSurfaceRenderer.render(this, guiGraphics);
            return;
        }

        if (shopAppMode) {
            PhoneShopSurfaceRenderer.render(this, guiGraphics);
            return;
        }

        if (mapAppMode) {
            PhoneMapSurfaceRenderer.render(this, guiGraphics);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || (unlocked && !homeWidgetsShown)) {
            return;
        }

        long dayTime = minecraft.level.getDayTime();
        long gameTime = dayTime % 24000L;
        int hours = (int) ((gameTime + 6000) / 1000) % 24;
        int minutes = (int) ((gameTime % 1000) * 60 / 1000);
        Component timeText = Component.literal(String.format("%02d:%02d", hours, minutes));

        if (!unlocked) {
            float timeScale = Math.max(1.65F, scale * 2.15F);
            int textWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, timeText, timeScale);
            int textHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, timeScale);
            int textX = displayX + (displayWidth - textWidth) / 2;
            int textY = displayY + Math.round(displayHeight * 0.21F) - (textHeight / 2);

            Component dayText = Component.translatable("screen.bphone.phone.home.day", dayTime / 24000L + 1);
            float dayScale = new PhoneChatLayout(this).bodyScale;
            int dayWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, dayText, dayScale);
            int dayHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, dayScale);
            int dayX = displayX + (displayWidth - dayWidth) / 2;
            int dayY = textY + textHeight + Math.round(4 * scale);

            int padX = Math.max(10, Math.round(14 * scale));
            int padY = Math.max(6, Math.round(8 * scale));
            int cardW = Math.max(textWidth, dayWidth) + 2 * padX;
            int cardH = (dayY + dayHeight - textY) + 2 * padY;
            int cardX = displayX + (displayWidth - cardW) / 2;
            int cardY = textY - padY;
            int cardR = Math.max(6, Math.round(10 * scale));
            PhoneUi.fillRounded(guiGraphics, cardX, cardY, cardW, cardH, cardR, 0x5212151E);
            PhoneUi.strokeRounded(guiGraphics, cardX, cardY, cardW, cardH, cardR, 1, 0x36FFFFFF);

            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, timeText, textX, textY, 0xFFFFFFFF, false, timeScale);
            PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, dayText, dayX, dayY, 0xEBFFFFFF, false, dayScale);
            return;
        }

        float timeScale = Math.max(1.3F, scale * 1.65F);
        int timeWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, timeText, timeScale);
        int timeHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, timeScale);
        int timeX = displayX + (displayWidth - timeWidth) / 2;
        int timeY = displayY + Math.round(displayHeight * 0.10F);

        Component dayText = Component.translatable("screen.bphone.phone.home.day", dayTime / 24000L + 1);
        float dayScale = new PhoneChatLayout(this).bodyScale;
        int dayWidth = PhoneScreenDraw.scaledTextWidth(minecraft.font, dayText, dayScale);
        int dayHeight = PhoneScreenDraw.scaledTextHeight(minecraft.font, dayScale);
        int dayX = displayX + (displayWidth - dayWidth) / 2;
        int dayY = timeY + timeHeight + Math.round(3 * scale);

        int padX = Math.max(8, Math.round(12 * scale));
        int padY = Math.max(5, Math.round(6 * scale));
        int cardW = Math.max(timeWidth, dayWidth) + 2 * padX;
        int cardH = (dayY + dayHeight - timeY) + 2 * padY;
        int cardX = displayX + (displayWidth - cardW) / 2;
        int cardY = timeY - padY;
        int cardR = Math.max(5, Math.round(8 * scale));
        PhoneUi.fillRounded(guiGraphics, cardX, cardY, cardW, cardH, cardR, 0x4C12151E);
        PhoneUi.strokeRounded(guiGraphics, cardX, cardY, cardW, cardH, cardR, 1, 0x30FFFFFF);

        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, timeText, timeX, timeY, 0xFFFFFFFF, false, timeScale);
        PhoneScreenDraw.drawScaledText(guiGraphics, minecraft.font, dayText, dayX, dayY, 0xEBFFFFFF, false, dayScale);
    }

    private void renderGallerySurface(GuiGraphics guiGraphics) {
        PhoneMediaSurfaceRenderer.renderGallerySurface(this, guiGraphics);
    }

    private void renderPhotoViewerSurface(GuiGraphics guiGraphics) {
        PhoneMediaSurfaceRenderer.renderPhotoViewerSurface(this, guiGraphics);
    }

    private void renderCallAppSurface(GuiGraphics guiGraphics) {
        PhoneCallSurfaceRenderer.renderCallAppSurface(this, guiGraphics);
    }

    private void renderCallContactsSurface(GuiGraphics guiGraphics) {
        PhoneCallSurfaceRenderer.renderCallContactsSurface(this, guiGraphics);
    }

    private void renderCallSessionSurface(GuiGraphics guiGraphics) {
        PhoneCallSurfaceRenderer.renderCallSessionSurface(this, guiGraphics);
    }

    private void renderAddContactSurface(GuiGraphics guiGraphics) {
        PhoneCallSurfaceRenderer.renderAddContactSurface(this, guiGraphics);
    }

    private void renderChatAppSurface(GuiGraphics guiGraphics) {
        PhoneChatSurfaceRenderer.render(this, guiGraphics);
    }

    private void renderBankSurface(GuiGraphics guiGraphics) {
        PhoneBankSurfaceRenderer.renderBankSurface(this, guiGraphics);
    }

    private void renderBankCameraOverlay(GuiGraphics guiGraphics) {
        PhoneBankSurfaceRenderer.renderBankCameraOverlay(this, guiGraphics);
    }

    private void renderCallBackdrop(GuiGraphics guiGraphics) {
        PhoneCallSurfaceRenderer.renderCallBackdrop(this, guiGraphics);
    }

    private void renderMediaBackdrop(GuiGraphics guiGraphics) {
        PhoneCallSurfaceRenderer.renderMediaBackdrop(this, guiGraphics);
    }

    private void renderCameraSurface(GuiGraphics guiGraphics, float partialTick) {
        PhoneMediaSurfaceRenderer.renderCameraSurface(this, guiGraphics);
    }

    private void renderCameraOverlayHints(GuiGraphics guiGraphics) {
        PhoneMediaSurfaceRenderer.renderCameraOverlayHints(this, guiGraphics);
    }

    ViewerLayout getViewerLayout() {
        return new ViewerLayout(
                displayX + Math.round(6 * scale),
                displayY + Math.round(34 * scale),
                displayWidth - Math.round(12 * scale),
                displayHeight - Math.round(82 * scale));
    }

    private PhoneScreenLayout getLayoutState() {
        if (layoutState == null) {
            updateLayout();
        }
        return layoutState;
    }

    GalleryLayout computeGalleryLayout() {
        return getLayoutState().galleryLayout();
    }

    int getGalleryHeaderHeight() {
        return getLayoutState().galleryHeaderHeight();
    }

    private int getGalleryFooterHeight() {
        return getLayoutState().galleryFooterHeight();
    }

    int getGalleryPageCount() {
        return getLayoutState().galleryPageCount(getPhotos().size());
    }

    UiRect getGallerySlotBounds(int localIndex, int pageItemCount) {
        return getLayoutState().gallerySlotBounds(localIndex, pageItemCount);
    }

    private int getGalleryPhotoIndexAt(double mouseX, double mouseY, List<PhotoEntry> photos) {
        return getLayoutState().galleryPhotoIndexAt(mouseX, mouseY, galleryPage, photos.size());
    }

    UiRect getCallSurfaceBounds() {
        return getLayoutState().callSurfaceBounds();
    }

    UiRect getCallBackdropBounds() {
        return getLayoutState().callBackdropBounds();
    }

    UiRect getMediaBackdropBounds() {
        return getLayoutState().mediaBackdropBounds();
    }

    UiRect getMediaSurfaceBounds() {
        return getLayoutState().mediaSurfaceBounds();
    }

    int getCallHeaderHeight() {
        return getLayoutState().callHeaderHeight();
    }

    UiRect getCallDialMenuBounds() {
        return getLayoutState().callDialMenuBounds();
    }

    UiRect getCallRecentsMenuBounds() {
        return getLayoutState().callRecentsMenuBounds();
    }

    UiRect getCallListMenuBounds() {
        return getLayoutState().callListMenuBounds();
    }

    UiRect getAddContactHeaderButtonBounds() {
        return getLayoutState().addContactHeaderButtonBounds();
    }

    UiRect getAddContactPopupBounds() {
        return getLayoutState().addContactPopupBounds();
    }

    UiRect getAddContactNameBounds() {
        return getLayoutState().addContactNameBounds();
    }

    UiRect getAddContactNumberBounds() {
        return getLayoutState().addContactNumberBounds();
    }

    UiRect getAddContactSaveButtonBounds() {
        return getLayoutState().addContactSaveButtonBounds();
    }

    UiRect getAddContactCancelButtonBounds() {
        return getLayoutState().addContactCancelButtonBounds();
    }

    UiRect getCallNumberDisplayBounds() {
        return getLayoutState().callNumberDisplayBounds();
    }

    private UiRect getCallDialPadBounds() {
        return getLayoutState().callDialPadBounds();
    }

    int getCallMenuBottomReserve() {
        return getLayoutState().callMenuBottomReserve();
    }

    UiRect getDialPadCellBounds(int index) {
        return getLayoutState().dialPadCellBounds(index);
    }

    private int getDialPadDigitIndexAt(double mouseX, double mouseY) {
        for (int i = 0; i < CALL_DIAL_DIGITS.length; i++) {
            if (!CALL_DIAL_DIGITS[i].isEmpty() && getDialPadCellBounds(i).contains(mouseX, mouseY)) {
                return i;
            }
        }

        return -1;
    }

    UiRect getDialDeleteButtonBounds() {
        return getLayoutState().dialDeleteButtonBounds();
    }

    UiRect getDialCallButtonBounds() {
        return getLayoutState().dialCallButtonBounds();
    }

    private UiRect getContactPanelBounds() {
        return getLayoutState().contactPanelBounds();
    }

    UiRect getContactSaveButtonBounds() {
        return getLayoutState().contactSaveButtonBounds();
    }

    private UiRect getContactRowsBounds() {
        String saveCandidateNumber = getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !hasSavedContact(saveCandidateNumber);
        return getLayoutState().contactRowsBounds(canSaveNumber);
    }

    UiRect getContactRowBounds(int index, int contactCount) {
        String saveCandidateNumber = getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !hasSavedContact(saveCandidateNumber);
        return getLayoutState().contactRowBounds(index, contactCount, canSaveNumber);
    }

    UiRect getContactDeleteButtonBounds(int index, int contactCount) {
        String saveCandidateNumber = getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !hasSavedContact(saveCandidateNumber);
        return getLayoutState().contactDeleteButtonBounds(index, contactCount, canSaveNumber);
    }

    UiRect getContactEditButtonBounds(int index, int contactCount) {
        String saveCandidateNumber = getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !hasSavedContact(saveCandidateNumber);
        return getLayoutState().contactEditButtonBounds(index, contactCount, canSaveNumber);
    }

    private int getContactIndexAt(double mouseX, double mouseY, List<PhoneContact> contacts) {
        String saveCandidateNumber = getContactSaveCandidateNumber();
        boolean canSaveNumber = !saveCandidateNumber.isEmpty() && !hasSavedContact(saveCandidateNumber);
        return getLayoutState().contactIndexAt(mouseX, mouseY, contacts.size(), canSaveNumber);
    }

    UiRect getCallConnectButtonBounds() {
        return getLayoutState().callConnectButtonBounds();
    }

    UiRect getCallHangupButtonBounds() {
        if (activeCallConnected) {
            UiRect connectBounds = getLayoutState().callConnectButtonBounds();
            return new UiRect(
                    displayX + ((displayWidth - connectBounds.width) / 2),
                    connectBounds.top,
                    connectBounds.width,
                    connectBounds.height
            );
        }
        return getLayoutState().callHangupButtonBounds();
    }

    UiRect getCallMuteButtonBounds() {
        return getLayoutState().callMuteButtonBounds();
    }

    UiRect getCallSpeakerButtonBounds() {
        return getLayoutState().callSpeakerButtonBounds();
    }

    UiRect getBankBackButtonBounds() {
        return new PhoneBankLayout(this).back();
    }

    UiRect getBankHomeTransferButtonBounds() {
        return new PhoneBankLayout(this).tile(0);
    }

    UiRect getBankHomeScanButtonBounds() {
        return new PhoneBankLayout(this).tile(1);
    }

    UiRect getBankHomeReceiveButtonBounds() {
        return new PhoneBankLayout(this).tile(2);
    }

    UiRect getBankTransferNumberBounds() {
        return new PhoneBankLayout(this).numberField();
    }

    UiRect getBankTransferAmountBounds() {
        return new PhoneBankLayout(this).amountField();
    }

    UiRect getBankTransferButtonBounds() {
        return new PhoneBankLayout(this).confirm();
    }

    UiRect getBankCancelButtonBounds() {
        return new PhoneBankLayout(this).cancel();
    }

    UiRect getBankReceiveEyeButtonBounds() {
        return new PhoneBankLayout(this).receiveEye();
    }

    UiRect getBankScanReceiveButtonBounds() {
        return new PhoneBankLayout(this).scanReceive(getScanPanelBounds());
    }

    UiRect getBankScanEyeButtonBounds() {
        return new PhoneBankLayout(this).scanEye(getScanPanelBounds());
    }

    List<PhoneContact> getPhoneContacts() {
        if (homePhoneMode) {
            HomePhoneBlockEntity homePhone = getClientHomePhone();
            return homePhone == null ? List.of() : homePhone.getContacts();
        }
        return PhoneData.getContacts(getOpenPhoneStack());
    }

    String getPlayerPhoneNumber(UUID uuid) {
        if (uuid == null) {
            return "00000";
        }
        int hash = uuid.hashCode();
        int number = 10000 + Math.floorMod(hash, 90000);
        return String.format(java.util.Locale.ROOT, "%05d", number);
    }

    List<PlayerInfo> getChatAddSelectablePlayers() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) {
            return List.of();
        }

        List<PlayerInfo> list = new ArrayList<>();
        String ownNumber = getOwnPhoneNumber();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null) {
                continue;
            }

            UUID uuid = info.getProfile().getId();
            String numberStr = getPlayerPhoneNumber(uuid);

            if (numberStr.equals(ownNumber)) {
                continue;
            }

            if (PhoneClientChatState.hasFriend(numberStr)) {
                continue;
            }

            list.add(info);
        }
        return list;
    }

    ResourceLocation getPlayerSkin(PlayerInfo info) {
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
    }

    ResourceLocation getChatProfileTexture(String number) {
        if (homePhoneMode) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                return DefaultPlayerSkin.get(minecraft.player.getUUID()).texture();
            }
            return DefaultPlayerSkin.getDefaultTexture();
        }

        Minecraft minecraft = Minecraft.getInstance();
        UUID profileId = PhoneClientChatState.getFriendProfileId(number);
        if (minecraft != null && minecraft.getConnection() != null) {
            if (profileId != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(profileId);
                if (info != null) {
                    return info.getSkin().texture();
                }
            }

            String friendName = PhoneClientChatState.getFriendName(number);
            if (!friendName.isBlank()) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(friendName);
                if (info != null) {
                    return info.getSkin().texture();
                }
            }
        }

        if (profileId != null) {
            return DefaultPlayerSkin.get(profileId).texture();
        }

        if (minecraft != null && minecraft.player != null) {
            return DefaultPlayerSkin.get(minecraft.player.getUUID()).texture();
        }

        return DefaultPlayerSkin.getDefaultTexture();
    }

    boolean hasSavedContact(String rawNumber) {
        String normalized = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(normalized)) {
            return false;
        }
        if (homePhoneMode) {
            HomePhoneBlockEntity homePhone = getClientHomePhone();
            return homePhone != null && homePhone.hasContact(normalized);
        }
        return PhoneData.hasContact(getOpenPhoneStack(), normalized);
    }

    String getContactSaveCandidateNumber() {
        String normalized = PhoneData.normalizePhoneNumber(dialedNumber);
        if (!PhoneData.isValidPhoneNumber(normalized)) {
            return "";
        }
        return normalized.equals(getOwnPhoneNumber()) ? "" : normalized;
    }

    private String getSavedContactName(String rawNumber) {
        String normalized = PhoneData.normalizePhoneNumber(rawNumber);
        for (PhoneContact contact : getPhoneContacts()) {
            if (contact.number().equals(normalized)) {
                return contact.displayName();
            }
        }
        return "";
    }

    UiRect getCameraPreviewBounds() {
        return getLayoutState().cameraPreviewBounds();
    }

    UiRect getCameraShutterButtonBounds() {
        return getLayoutState().cameraShutterButtonBounds();
    }

    private UiRect getCameraFlipButtonBounds() {
        return getLayoutState().cameraFlipButtonBounds();
    }

    UiRect getScanPanelBounds() {
        return new UiRect(displayX, displayY, displayWidth, displayHeight);
    }

    int panelRadius() {
        return Math.max(4, Math.round(10 * scale));
    }

    public boolean isInsidePhonePanel(double mouseX, double mouseY) {
        if (layoutFullScreen) {
            return true;
        }
        return mouseX >= displayX && mouseX < displayX + displayWidth
                && mouseY >= displayY && mouseY < displayY + displayHeight;
    }

    public boolean isTextInputActive() {
        if (addContactMode) {
            return true;
        }
        if (chatAppMode && chat.isTextInputActive()) {
            return true;
        }
        return mapAppMode && map.isTextInputActive();
    }

    public boolean allowsWorldMovement() {
        if (homePhoneMode) {
            return false;
        }
        if (cameraMode) {
            return cameraMoveMode;
        }
        return !isTextInputActive();
    }

    private void setOutsideLookMode(boolean enabled) {
        if (outsideLookActive == enabled) {
            return;
        }
        outsideLookActive = enabled;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        long windowHandle = minecraft.getWindow().getWindow();
        if (enabled) {
            double[] cx = new double[1];
            double[] cy = new double[1];
            GLFW.glfwGetCursorPos(windowHandle, cx, cy);
            savedLookCursorX = cx[0];
            savedLookCursorY = cy[0];
            if (GLFW.glfwRawMouseMotionSupported()) {
                savedRawMouseMotion = GLFW.glfwGetInputMode(windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE;
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
            }
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            GLFW.glfwGetCursorPos(windowHandle, cx, cy);
            lastOutsideLookRawX = cx[0];
            lastOutsideLookRawY = cy[0];
        } else {
            if (GLFW.glfwRawMouseMotionSupported()) {
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION,
                        savedRawMouseMotion ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            }
            InputConstants.grabOrReleaseMouse(windowHandle, GLFW.GLFW_CURSOR_NORMAL, savedLookCursorX, savedLookCursorY);
            minecraft.mouseHandler.setIgnoreFirstMove();
        }
    }

    private void pollOutsideLook() {
        if (!outsideLookActive) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getWindow() == null) {
            setOutsideLookMode(false);
            return;
        }
        long windowHandle = minecraft.getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS
                || cameraMode || homePhoneMode) {
            setOutsideLookMode(false);
            return;
        }
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, cx, cy);
        double dx = cx[0] - lastOutsideLookRawX;
        double dy = cy[0] - lastOutsideLookRawY;
        lastOutsideLookRawX = cx[0];
        lastOutsideLookRawY = cy[0];
        if (dx == 0.0D && dy == 0.0D) {
            return;
        }
        double sens = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
        double factor = sens * sens * sens * 8.0D;
        double invert = minecraft.options.invertYMouse().get() ? -1.0D : 1.0D;
        minecraft.player.turn(dx * factor, dy * factor * invert);
    }

    private void releaseWorldMovementKeys() {
        if (cameraMode && cameraMoveMode) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keySprint.setDown(false);
        minecraft.options.keyShift.setDown(false);
    }

    UiRect getCameraControlBandBounds() {
        return getLayoutState().cameraControlBandBounds();
    }

    public boolean isPhotoCameraMode() {
        return cameraMode && !isBankScanCameraMode() && !isChatScanCameraMode();
    }

    private void rotateCameraPlayer(double dragX, double dragY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        var player = minecraft.player;
        player.turn(dragX * CAMERA_LOOK_TURN_SCALE, dragY * CAMERA_LOOK_TURN_SCALE);
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());
    }

    private void applyCallStateFromServer() {
        if (publicChatOnly) {
            return;
        }

        long nextRevision = PhoneClientCallState.getRevision(homePhonePos);
        if (nextRevision == observedCallStateRevision) {
            return;
        }

        observedCallStateRevision = nextRevision;
        PhoneCallState state = PhoneClientCallState.getState(homePhonePos);
        String otherNumber = PhoneClientCallState.getOtherNumber(homePhonePos);
        String otherName = PhoneClientCallState.getOtherName(homePhonePos);
        String savedContactName = getSavedContactName(otherNumber);
        if (!savedContactName.isBlank()) {
            otherName = savedContactName;
        }
        boolean shouldRebuild = false;

        if (state == PhoneCallState.IDLE) {
            boolean hadCallState = !activeCallNumber.isEmpty() || activeCallConnected || activeCallIncoming
                    || activeCallMissed || !activeCallName.isEmpty();
            if (callSessionMode) {
                callSessionMode = false;
                callContactsMode = false;
                
                addContactMode = false;
                callAppMode = true;
                shouldRebuild = true;
            }

            if (hadCallState) {
                activeCallNumber = "";
                activeCallName = "";
                activeCallIncoming = false;
                activeCallConnected = false;
                activeCallMissed = false;
                activeCallTicks = 0;
                shouldRebuild = true;
            }
        } else {
            boolean nextIncoming = state == PhoneCallState.INCOMING_RINGING;
            boolean nextConnected = state == PhoneCallState.CONNECTED;
            boolean nextMissed = state == PhoneCallState.MISSED;
            boolean stateChanged = !Objects.equals(activeCallNumber, otherNumber)
                    || !Objects.equals(activeCallName, otherName)
                    || activeCallIncoming != nextIncoming
                    || activeCallConnected != nextConnected
                    || activeCallMissed != nextMissed
                    || !callSessionMode;

            activeCallNumber = otherNumber;
            activeCallName = otherName;
            activeCallIncoming = nextIncoming;
            activeCallConnected = nextConnected;
            activeCallMissed = nextMissed;
            activeCallTicks = nextConnected ? PhoneClientCallState.getConnectedDurationTicks(homePhonePos) : 0;

            if (!cameraMode && !galleryMode && !photoViewerMode) {
                callAppMode = false;
                callContactsMode = false;
                
                addContactMode = false;
                callSessionMode = true;
                shouldRebuild = true;
            } else if (stateChanged) {
                shouldRebuild = true;
            }
        }

        if (shouldRebuild) {
            rebuildWidgets();
        }
    }

    private boolean handleMovementKey(int keyCode, int scanCode, boolean pressed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        return setMovementKeyState(minecraft.options.keyUp, keyCode, scanCode, pressed)
                || setMovementKeyState(minecraft.options.keyLeft, keyCode, scanCode, pressed)
                || setMovementKeyState(minecraft.options.keyDown, keyCode, scanCode, pressed)
                || setMovementKeyState(minecraft.options.keyRight, keyCode, scanCode, pressed)
                || setMovementKeyState(minecraft.options.keyJump, keyCode, scanCode, pressed)
                || setMovementKeyState(minecraft.options.keySprint, keyCode, scanCode, pressed)
                || setMovementKeyState(minecraft.options.keyShift, keyCode, scanCode, pressed);
    }

    private boolean setMovementKeyState(KeyMapping keyMapping, int keyCode, int scanCode, boolean pressed) {
        if (!keyMapping.matches(keyCode, scanCode)) {
            return false;
        }

        keyMapping.setDown(pressed);
        return true;
    }

    private void setCameraMoveMode(boolean enabled, double mouseX, double mouseY) {
        cameraMoveMode = enabled;
        lastCameraMoveMouseX = mouseX;
        lastCameraMoveMouseY = mouseY;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        if (enabled) {
            if (GLFW.glfwRawMouseMotionSupported()) {
                savedRawMouseMotion = GLFW.glfwGetInputMode(windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE;
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
            }
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        } else {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyDown.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keySprint.setDown(false);
            minecraft.options.keyShift.setDown(false);
            minecraft.options.keyUse.setDown(false);
            minecraft.options.keyAttack.setDown(false);
            if (GLFW.glfwRawMouseMotionSupported()) {
                GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_RAW_MOUSE_MOTION,
                        savedRawMouseMotion ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            }
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    private void syncCameraMovementKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        syncMovementKey(windowHandle, minecraft.options.keyUp);
        syncMovementKey(windowHandle, minecraft.options.keyLeft);
        syncMovementKey(windowHandle, minecraft.options.keyDown);
        syncMovementKey(windowHandle, minecraft.options.keyRight);
        syncMovementKey(windowHandle, minecraft.options.keyJump);
        syncMovementKey(windowHandle, minecraft.options.keySprint);
        syncMovementKey(windowHandle, minecraft.options.keyShift);
    }

    private void syncMovementKey(long windowHandle, KeyMapping keyMapping) {
        if (keyMapping == null) {
            return;
        }

        InputConstants.Key inputKey = InputConstants.getKey(keyMapping.saveString());
        if (inputKey.getType() != InputConstants.Type.KEYSYM) {
            return;
        }

        keyMapping.setDown(InputConstants.isKeyDown(windowHandle, inputKey.getValue()));
    }

    PhotoEntry getLatestPhoto() {
        List<PhotoEntry> photos = getPhotos();
        if (photos.isEmpty()) {
            return null;
        }

        return photos.get(0);
    }

    private void openCallApp() {
        setBankReceiveActive(false);
        closeCamera();
        photoViewerMode = false;
        galleryMode = false;
        unlocked = true;
        callAppMode = true;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        observedCallStateRevision = Long.MIN_VALUE;
        requestCallSync();
        applyCallStateFromServer();
        rebuildWidgets();
    }

    private void closeCallApp() {
        if (homePhoneMode) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return;
        }
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        unlocked = true;
        rebuildWidgets();
    }

    private void openChatApp() {
        openChatApp(PhoneChatLayout.TAB_CHATS);
    }

    private void openChatApp(int tab) {
        setBankReceiveActive(false);
        closeCamera();
        photoViewerMode = false;
        galleryMode = false;
        unlocked = true;
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = true;
        chatScanActive = false;
        chatScanHoverTicks = 0;
        bankAppMode = false;
        chat.open(tab);
        rebuildWidgets();
    }

    private boolean isChatScanCameraMode() {
        return chatAppMode && cameraMode && chatScanActive;
    }

    void openChatScanCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        chatScanActive = true;
        chatScanHoverTicks = 0;
        chatScanHoverTargetId = null;
        cameraMode = true;
        setCameraMoveMode(false, 0.0D, 0.0D);
        if (minecraft != null) {
            if (savedCameraType == null) {
                savedCameraType = minecraft.options.getCameraType();
            }
            if (savedCameraFov < 0) {
                savedCameraFov = getCurrentCameraFov(minecraft);
            }
            selfieCameraMode = false;
            cameraZoomLevel = 0;
            cameraZoomFactor = 1.0F;
            cameraZoomIndicatorTicks = 0;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            applyCameraZoom(minecraft);
        }
        rebuildWidgets();
    }

    private void closeChatScanCamera() {
        closeCamera();
        chatScanActive = false;
        chatScanHoverTicks = 0;
        chatScanHoverTargetId = null;
        rebuildWidgets();
    }

    void closeChatApp() {
        if (publicChatOnly) {
            onClose();
            return;
        }
        chatAppMode = false;
        chatScanActive = false;
        chatScanHoverTicks = 0;
        chat.reset();
        unlocked = true;
        rebuildWidgets();
    }

    void handleChatStateChanged() {
        chat.handleStateChanged();
    }

    void handleChatConversationRemoved(long conversationId) {
        chat.handleConversationRemoved(conversationId);
    }

    void handleChatHistory(long conversationId, long aroundId) {
        chat.handleHistory(conversationId, aroundId);
    }

    void handleChatSearchResult(String query, List<ChatPayloads.SearchHitData> hits) {
        chat.handleSearchResult(query, hits);
    }

    void handleChatFocus(long conversationId) {
        chat.handleFocus(conversationId);
    }

    boolean isViewingConversation(long conversationId) {
        return chatAppMode && chat.isViewing(conversationId);
    }

    private void openBankApp() {
        closeCamera();
        photoViewerMode = false;
        galleryMode = false;
        unlocked = true;
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = true;
        bankPage = BANK_PAGE_HOME;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.translatable("screen.bphone.phone.bank.status.syncing");
        bankSyncCooldown = 0;
        PhoneNetworkingClient.requestBankSync();
        rebuildWidgets();
    }

    private void closeBankApp() {
        setBankReceiveActive(false);
        closeCamera();
        bankAppMode = false;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.empty();
        bankPage = BANK_PAGE_HOME;
        bankSyncCooldown = 0;
        unlocked = true;
        rebuildWidgets();
    }

    private void openBankHomePage() {
        setBankReceiveActive(false);
        closeCamera();
        bankPage = BANK_PAGE_HOME;
        bankAppMode = true;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.empty();
        bankSyncCooldown = 0;
        PhoneNetworkingClient.requestBankSync();
        rebuildWidgets();
    }

    private void openBankTransferPage() {
        setBankReceiveActive(false);
        closeCamera();
        bankPage = BANK_PAGE_TRANSFER;
        bankAmountFocused = false;
        bankAppMode = true;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.translatable("screen.bphone.phone.bank.transfer_hint");
        rebuildWidgets();
    }

    private void openBankScanCamera() {
        setBankReceiveActive(false);
        Minecraft minecraft = Minecraft.getInstance();
        cameraMode = true;
        bankAppMode = true;
        bankPage = BANK_PAGE_SCAN;
        galleryMode = false;
        photoViewerMode = false;
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.translatable("screen.bphone.phone.bank.status.scan_ready");
        setCameraMoveMode(false, 0.0D, 0.0D);
        if (minecraft != null) {
            if (savedCameraType == null) {
                savedCameraType = minecraft.options.getCameraType();
            }
            if (savedCameraFov < 0) {
                savedCameraFov = getCurrentCameraFov(minecraft);
            }
            selfieCameraMode = false;
            cameraZoomLevel = 0;
            cameraZoomFactor = 1.0F;
            cameraZoomIndicatorTicks = 0;
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            applyCameraZoom(minecraft);
        }
        rebuildWidgets();
    }

    private void closeBankScanCamera() {
        closeCamera();
        cameraMode = false;
        bankAppMode = true;
        bankPage = BANK_PAGE_HOME;
        bankStatus = Component.empty();
        resetBankScanHover();
        rebuildWidgets();
    }

    private void openBankReceivePage() {
        closeCamera();
        cameraMode = false;
        bankAppMode = true;
        bankPage = BANK_PAGE_RECEIVE;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.translatable("screen.bphone.phone.bank.status.receive_ready");
        setBankReceiveActive(true);
        rebuildWidgets();
    }

    private void showBankReceiveQrToWorld() {
        setBankReceiveActive(true);
        closingForBankReceiveWorld = true;
        PhoneClientHooks.showBankReceiveQrToWorld(openHand);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    void showChatQrToWorld() {
        if (homePhoneMode) {
            return;
        }

        setBankReceiveActive(false);
        closingForBankReceiveWorld = true;
        PhoneClientHooks.showBankReceiveQrToWorld(openHand, true);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void openBankPaymentPage(String targetNumber, String targetName) {
        setBankReceiveActive(false);
        closeCamera();
        bankAppMode = true;
        bankPage = BANK_PAGE_PAYMENT;
        bankAmountFocused = true;
        bankTransferNumber = PhoneData.normalizePhoneNumber(targetNumber);
        bankTransferAmount = "";
        bankPaymentName = targetName == null ? "" : targetName;
        clearBankSlip();
        resetBankScanHover();
        bankStatus = Component.translatable("screen.bphone.phone.bank.status.enter_amount");
        rebuildWidgets();
    }

    private void openAddContactPage() {
        setBankReceiveActive(false);
        if (isCallScreenLocked()) {
            callAppMode = false;
            
            addContactMode = false;
            callSessionMode = true;
            rebuildWidgets();
            return;
        }
        callAppMode = false;
        
        addContactMode = true;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        observedCallStateRevision = Long.MIN_VALUE;
        requestCallSync();
        applyCallStateFromServer();
        addContactName = "";
        addContactNumber = "";
        addContactNameFieldFocused = true;
        editingContactOriginalNumber = null;
        rebuildWidgets();
    }

    private void openEditContactPage(PhoneContact contact) {
        setBankReceiveActive(false);
        addContactMode = true;
        callAppMode = false;
        
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        addContactName = contact.displayName();
        addContactNumber = contact.number();
        addContactNameFieldFocused = true;
        editingContactOriginalNumber = contact.number();
        rebuildWidgets();
    }

    private void openCallContactsPage() {
        setBankReceiveActive(false);
        if (isCallScreenLocked()) {
            callAppMode = false;
            callContactsMode = false;
            
            addContactMode = false;
            callSessionMode = true;
            rebuildWidgets();
            return;
        }
        callAppMode = false;
        callContactsMode = true;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        observedCallStateRevision = Long.MIN_VALUE;
        requestCallSync();
        applyCallStateFromServer();
        rebuildWidgets();
    }

    private void openCallDialPage() {
        setBankReceiveActive(false);
        if (isCallScreenLocked()) {
            callAppMode = false;
            callContactsMode = false;
            
            addContactMode = false;
            editingContactOriginalNumber = null;
            callSessionMode = true;
            rebuildWidgets();
            return;
        }
        callAppMode = true;
        callContactsMode = false;
        
        addContactMode = false;
        editingContactOriginalNumber = null;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        observedCallStateRevision = Long.MIN_VALUE;
        requestCallSync();
        applyCallStateFromServer();
        rebuildWidgets();
    }

    private void startCallSession(String number) {
        setBankReceiveActive(false);
        String normalized = PhoneData.normalizePhoneNumber(number);
        if (!PhoneData.isValidPhoneNumber(normalized)) {
            return;
        }

        dialedNumber = normalized;
        activeCallNumber = normalized;
        activeCallName = getSavedContactName(normalized);
        activeCallIncoming = false;
        activeCallConnected = false;
        activeCallMissed = false;
        activeCallTicks = 0;
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = true;
        chatAppMode = false;
        bankAppMode = false;
        if (homePhoneMode) {
            PhoneNetworkingClient.requestCall(normalized, homePhonePos);
        } else {
            PhoneNetworkingClient.requestCall(normalized);
        }
        rebuildWidgets();
    }

    private void connectActiveCall() {
        if (!callSessionMode || activeCallNumber.isEmpty() || activeCallConnected || !activeCallIncoming) {
            return;
        }

        if (homePhoneMode) {
            PhoneNetworkingClient.requestAnswer(homePhonePos);
        } else {
            PhoneNetworkingClient.requestAnswer();
        }
    }

    void endCallSession(boolean returnToList) {
        if (!activeCallNumber.isEmpty() || PhoneClientCallState.getState(homePhonePos) != PhoneCallState.IDLE) {
            if (homePhoneMode) {
                PhoneNetworkingClient.requestEndCall(homePhonePos);
            } else {
                PhoneNetworkingClient.requestEndCall();
            }
        }
        if (!activeCallNumber.isEmpty()) {
            dialedNumber = activeCallNumber;
        }
        activeCallName = "";
        activeCallNumber = "";
        activeCallIncoming = false;
        activeCallConnected = false;
        activeCallMissed = false;
        activeCallTicks = 0;
        callMuted = false;
        callSpeakerEnabled = false;
        callContactsMode = false;
        
        addContactMode = false;
        callAppMode = returnToList;
        if (!returnToList) {
            unlocked = true;
        }
        rebuildWidgets();
    }

    String formatCallDuration() {
        int totalSeconds = Math.max(0, activeCallTicks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void appendDialDigit(String digit) {
        if (digit == null || digit.isEmpty() || dialedNumber.length() >= CALL_MAX_NUMBER_LENGTH) {
            return;
        }

        dialedNumber = dialedNumber + digit;
    }

    private void removeLastDialDigit() {
        if (dialedNumber.isEmpty()) {
            return;
        }

        dialedNumber = dialedNumber.substring(0, dialedNumber.length() - 1);
    }

    private String getDigitForKey(int keyCode) {
        return switch (keyCode) {
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

    private void appendBankDigit(String digit) {
        if (digit == null || digit.isEmpty()) {
            return;
        }

        if (bankPage == BANK_PAGE_TRANSFER && !bankAmountFocused) {
            if (bankTransferNumber.length() < PhoneData.PHONE_NUMBER_LENGTH) {
                bankTransferNumber = bankTransferNumber + digit;
                bankAmountFocused = bankTransferNumber.length() >= PhoneData.PHONE_NUMBER_LENGTH;
                return;
            }
            bankAmountFocused = true;
        }

        if ((bankPage == BANK_PAGE_TRANSFER || bankPage == BANK_PAGE_PAYMENT) && bankTransferAmount.length() < 9) {
            bankTransferAmount = bankTransferAmount + digit;
        }
    }

    private void removeLastBankDigit() {
        if (bankPage == BANK_PAGE_TRANSFER && !bankAmountFocused) {
            if (!bankTransferNumber.isEmpty()) {
                bankTransferNumber = bankTransferNumber.substring(0, bankTransferNumber.length() - 1);
            }
            return;
        }

        if (!bankTransferAmount.isEmpty()) {
            bankTransferAmount = bankTransferAmount.substring(0, bankTransferAmount.length() - 1);
            return;
        }

        if (bankPage == BANK_PAGE_TRANSFER) {
            bankAmountFocused = false;
            if (!bankTransferNumber.isEmpty()) {
                bankTransferNumber = bankTransferNumber.substring(0, bankTransferNumber.length() - 1);
            }
        }
    }

    boolean canSendBankTransfer() {
        return (bankPage == BANK_PAGE_TRANSFER || bankPage == BANK_PAGE_PAYMENT)
                && PhoneData.isValidPhoneNumber(bankTransferNumber)
                && getBankTransferAmount() > 0L;
    }

    long getBankTransferAmount() {
        if (bankTransferAmount.isBlank()) {
            return 0L;
        }

        try {
            return Math.max(0L, Long.parseLong(bankTransferAmount));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void sendBankTransfer() {
        if (!canSendBankTransfer()) {
            return;
        }

        long amount = getBankTransferAmount();
        PhoneNetworkingClient.requestBankTransfer(bankTransferNumber, getBankTransferAmount());
        bankTransferAmount = "";
        bankStatus = Component.translatable("screen.bphone.phone.bank.status.processing", amount);
    }

    void handleBankState(long balance, Component status) {
        bankBalance = Math.max(0L, balance);
        if (status != null && !status.getString().isBlank()) {
            bankStatus = status;
        }
    }

    void handleBankScanResult(String targetNumber, String targetName) {
        openBankPaymentPage(targetNumber, targetName);
    }

    void handleBankTransferReceipt(String targetNumber, String targetName, long amount, long balance) {
        bankSlipTargetNumber = PhoneData.normalizePhoneNumber(targetNumber);
        bankSlipTargetName = targetName == null ? "" : targetName;
        bankSlipAmount = Math.max(0L, amount);
        bankSlipBalance = Math.max(0L, balance);
        bankSlipTime = java.time.LocalTime.now().withNano(0).toString();
        closeCamera();
        bankPage = BANK_PAGE_SLIP;
        bankAppMode = true;
        cameraMode = false;
        bankTransferNumber = "";
        bankTransferAmount = "";
        bankPaymentName = "";
        bankStatus = Component.translatable("screen.bphone.phone.bank.status.transfer_done");
        rebuildWidgets();
    }

    private boolean acceptsBankNumericInput() {
        return bankPage == BANK_PAGE_TRANSFER || bankPage == BANK_PAGE_PAYMENT;
    }

    private boolean isBankScanCameraMode() {
        return bankAppMode && cameraMode && bankPage == BANK_PAGE_SCAN;
    }

    boolean isBankScanCameraActive() {
        return isBankScanCameraMode();
    }

    private void tickBankScanHover() {
        Player targetPlayer = getBankScanTargetPlayer();
        if (targetPlayer == null) {
            resetBankScanHover();
            return;
        }

        UUID targetId = targetPlayer.getUUID();
        if (!targetId.equals(bankScanHoverTargetId)) {
            bankScanHoverTargetId = targetId;
            bankScanHoverTicks = 1;
            return;
        }

        if (bankScanHoverTicks < BANK_SCAN_HOLD_TICKS) {
            bankScanHoverTicks++;
            return;
        }

        if (bankScanRequestCooldown <= 0) {
            requestBankScanTarget(targetPlayer);
        }
    }

    private void resetBankScanHover() {
        bankScanHoverTargetId = null;
        bankScanHoverTicks = 0;
    }

    private void tickChatScanHover() {
        Player targetPlayer = getScanTargetPlayer(true);
        if (targetPlayer == null) {
            chatScanHoverTargetId = null;
            chatScanHoverTicks = 0;
            return;
        }

        UUID targetId = targetPlayer.getUUID();
        if (!targetId.equals(chatScanHoverTargetId)) {
            chatScanHoverTargetId = targetId;
            chatScanHoverTicks = 1;
            return;
        }

        if (chatScanHoverTicks < 15) {
            chatScanHoverTicks++;
            return;
        }

        String friendNum = getPlayerPhoneNumber(targetId);
        closeCamera();
        chatScanActive = false;
        chatScanHoverTicks = 0;
        chatScanHoverTargetId = null;
        chat.requestFriendFromScan(friendNum);
        rebuildWidgets();
    }

    private void tryScanBankPaymentTarget() {
        Player targetPlayer = getBankScanTargetPlayer();
        if (targetPlayer == null) {
            bankStatus = Component.translatable("screen.bphone.phone.bank.status.scan_failed");
            resetBankScanHover();
            return;
        }

        requestBankScanTarget(targetPlayer);
    }

    private Player getBankScanTargetPlayer() {
        return getScanTargetPlayer(false);
    }

    private Player getScanTargetPlayer(boolean chatCode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || !(minecraft.hitResult instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof Player targetPlayer)) {
            return null;
        }

        if (targetPlayer == minecraft.player || !isHoldingPhone(targetPlayer)) {
            return null;
        }

        boolean showingCode = chatCode
                ? PhoneCallPoseAccess.isPhoneChatQrPoseActive(targetPlayer)
                : PhoneCallPoseAccess.isPhoneBankQrPoseActive(targetPlayer);
        return showingCode ? targetPlayer : null;
    }

    private void requestBankScanTarget(Player targetPlayer) {
        if (targetPlayer == null) {
            return;
        }

        bankStatus = Component.translatable("screen.bphone.phone.bank.status.scanning");
        PhoneNetworkingClient.requestBankScanTarget(targetPlayer.getUUID());
        bankScanRequestCooldown = Math.max(20, BANK_SCAN_HOLD_TICKS);
    }

    private boolean isHoldingPhone(Player player) {
        return player != null
                && (ModItems.isPhone(player.getMainHandItem())
                || ModItems.isPhone(player.getOffhandItem()));
    }

    private void clearBankSlip() {
        bankSlipTargetNumber = "";
        bankSlipTargetName = "";
        bankSlipTime = "";
        bankSlipAmount = 0L;
        bankSlipBalance = 0L;
    }

    private void setBankReceiveActive(boolean active) {
        if (bankReceiveActive == active) {
            return;
        }

        bankReceiveActive = active;
        if (!homePhoneMode) {
            PhoneNetworkingClient.requestBankReceiveState(active);
        }
    }

    private boolean isAcceptedChatCharacter(char codePoint) {
        return !Character.isISOControl(codePoint);
    }

    String getOwnPhoneNumber() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return "00000";
        }
        if (!homePhoneMode) {
            return PhoneData.getPhoneNumber(minecraft.player);
        }

        HomePhoneBlockEntity homePhone = getClientHomePhone();
        if (homePhone != null) {
            return homePhone.getPhoneNumber();
        }
        if (minecraft.level != null && homePhonePos != null) {
            return PhoneData.getHomePhoneNumber(minecraft.level.dimension(), homePhonePos);
        }
        return "00000";
    }

    private void openGallery() {
        setBankReceiveActive(false);
        galleryMode = true;
        closeCamera();
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        photoViewerMode = false;
        galleryPage = 0;
        rebuildWidgets();
    }

    private void openCamera() {
        setBankReceiveActive(false);
        Minecraft minecraft = Minecraft.getInstance();
        cameraMode = true;
        galleryMode = false;
        photoViewerMode = false;
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        bankAppMode = false;
        setCameraMoveMode(false, 0.0D, 0.0D);
        if (minecraft != null) {
            if (savedCameraType == null) {
                savedCameraType = minecraft.options.getCameraType();
            }
            if (savedCameraFov < 0) {
                savedCameraFov = getCurrentCameraFov(minecraft);
            }
            cameraZoomLevel = 0;
            cameraZoomFactor = 1.0F;
            cameraZoomIndicatorTicks = 0;
            minecraft.options.setCameraType(selfieCameraMode ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            applyCameraZoom(minecraft);
        }
        rebuildWidgets();
    }

    private void closeCamera() {
        if (!cameraMode) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        cameraMode = false;
        setCameraMoveMode(false, 0.0D, 0.0D);
        if (minecraft != null && savedCameraType != null) {
            minecraft.options.setCameraType(savedCameraType);
        }
        restoreCameraZoom(minecraft);
        savedCameraType = null;
    }

    private void toggleCameraFlip() {
        Minecraft minecraft = Minecraft.getInstance();
        selfieCameraMode = !selfieCameraMode;
        cameraZoomLevel = Mth.clamp(cameraZoomLevel, getMinCameraZoomLevel(), getMaxCameraZoomLevel());
        cameraZoomFactor = getCameraZoomFactorForLevel(cameraZoomLevel);
        if (cameraMode && minecraft != null) {
            minecraft.options.setCameraType(selfieCameraMode ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            applyCameraZoom(minecraft);
        }
    }

    private void adjustCameraZoom(double scrollDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!cameraMode || minecraft == null) {
            return;
        }

        if (savedCameraFov < 0) {
            savedCameraFov = getCurrentCameraFov(minecraft);
        }

        int minZoomLevel = getMinCameraZoomLevel();
        int maxZoomLevel = getMaxCameraZoomLevel();
        if (maxZoomLevel <= minZoomLevel) {
            cameraZoomLevel = 0;
            cameraZoomFactor = 1.0F;
            cameraZoomIndicatorTicks = CAMERA_ZOOM_INDICATOR_TICKS;
            applyCameraZoom(minecraft);
            return;
        }

        int zoomDelta = scrollDelta > 0.0D ? 1 : -1;
        cameraZoomLevel = Mth.clamp(cameraZoomLevel + zoomDelta, minZoomLevel, maxZoomLevel);
        cameraZoomFactor = getCameraZoomFactorForLevel(cameraZoomLevel);
        cameraZoomIndicatorTicks = CAMERA_ZOOM_INDICATOR_TICKS;
        applyCameraZoom(minecraft);
    }

    private void applyCameraZoom(Minecraft minecraft) {
        cameraZoomFactor = getCameraZoomFactorForLevel(
                Mth.clamp(cameraZoomLevel, getMinCameraZoomLevel(), getMaxCameraZoomLevel()));
    }

    private void restoreCameraZoom(Minecraft minecraft) {
        savedCameraFov = -1;
        cameraZoomLevel = 0;
        cameraZoomFactor = 1.0F;
        cameraZoomIndicatorTicks = 0;
    }

    private int getCurrentCameraFov(Minecraft minecraft) {
        return minecraft.options.fov().get();
    }

    float getCameraZoomFactor() {
        return cameraZoomFactor;
    }

    private float getMinCameraZoomFactor() {
        Minecraft minecraft = Minecraft.getInstance();
        int baseFov = minecraft == null
                ? Math.max(CAMERA_DEFAULT_MIN_FOV, savedCameraFov > 0 ? savedCameraFov : 70)
                : Math.max(CAMERA_DEFAULT_MIN_FOV, savedCameraFov > 0 ? savedCameraFov : getCurrentCameraFov(minecraft));
        return Math.min(1.0F, (float) baseFov / CAMERA_DEFAULT_MAX_FOV);
    }

    private float getMaxCameraZoomFactor() {
        return selfieCameraMode ? CAMERA_SELFIE_MAX_ZOOM_FACTOR : CAMERA_REAR_MAX_ZOOM_FACTOR;
    }

    int getMinCameraZoomLevel() {
        return selfieCameraMode ? CAMERA_SELFIE_MIN_ZOOM_LEVEL : CAMERA_REAR_MIN_ZOOM_LEVEL;
    }

    int getMaxCameraZoomLevel() {
        return selfieCameraMode ? CAMERA_SELFIE_MAX_ZOOM_LEVEL : CAMERA_REAR_MAX_ZOOM_LEVEL;
    }

    int getCameraZoomLevel() {
        return cameraZoomLevel;
    }

    private float getCameraZoomFactorForLevel(int zoomLevel) {
        if (zoomLevel == 0) {
            return 1.0F;
        }

        if (zoomLevel > 0) {
            float zoomProgress = (float) zoomLevel / Math.max(1, getMaxCameraZoomLevel());
            return 1.0F + ((getMaxCameraZoomFactor() - 1.0F) * zoomProgress);
        }

        float zoomProgress = (float) Math.abs(zoomLevel) / Math.max(1, Math.abs(getMinCameraZoomLevel()));
        return 1.0F - ((1.0F - getMinCameraZoomFactor()) * zoomProgress);
    }

    boolean shouldShowCameraZoomIndicator() {
        return cameraMode && cameraZoomIndicatorTicks > 0;
    }

    String getCameraZoomLabel() {
        return "x" + cameraZoomLevel;
    }

    private void openPhotoViewer(int photoIndex) {
        setBankReceiveActive(false);
        List<PhotoEntry> photos = getPhotos();
        if (photos.isEmpty()) {
            return;
        }

        photoViewerMode = true;
        closeCamera();
        galleryMode = false;
        callAppMode = false;
        callContactsMode = false;
        
        addContactMode = false;
        callSessionMode = false;
        chatAppMode = false;
        viewerPhotoIndex = Mth.clamp(photoIndex, 0, photos.size() - 1);
        rebuildWidgets();
    }

    private void stepViewer(int delta) {
        List<PhotoEntry> photos = getPhotos();
        if (photos.isEmpty()) {
            return;
        }

        int photoCount = photos.size();
        viewerPhotoIndex = Math.floorMod(viewerPhotoIndex + delta, photoCount);
        rebuildWidgets();
    }

    private void deleteCurrentPhoto() {
        List<PhotoEntry> photos = getPhotos();
        if (photos.isEmpty()) {
            photoViewerMode = false;
            galleryMode = true;
            viewerPhotoIndex = -1;
            rebuildWidgets();
            return;
        }

        viewerPhotoIndex = Mth.clamp(viewerPhotoIndex, 0, photos.size() - 1);
        PhotoEntry photoEntry = photos.get(viewerPhotoIndex);
        if (!removePhotoFromPhone(viewerPhotoIndex, photoEntry.fileName)) {
            return;
        }

        int remainingPhotos = photos.size() - 1;
        if (remainingPhotos <= 0) {
            photoViewerMode = false;
            galleryMode = true;
            viewerPhotoIndex = -1;
        } else {
            viewerPhotoIndex = Math.min(viewerPhotoIndex, remainingPhotos - 1);
        }
        rebuildWidgets();
    }

    private void captureAndSavePhoto() {
        String photoFileName = capturePhotoToFile();
        if (photoFileName == null) {
            return;
        }

        registerPhoto(photoFileName, sanitizePhotoCaptureStack(captureTargetItem()));
    }

    private boolean registerPhoto(String photoFileName, ItemStack captureStack) {
        if (!appendPhotoToPhone(photoFileName, captureStack)) {
            deletePhotoFile(photoFileName);
            return false;
        }

        PhoneNetworkingClient.requestAppendPhotoMetadata(openHand, photoFileName, captureStack);
        return true;
    }

    String importPhoto(byte[] pngData) {
        String photoFileName = isPhotoStorageFull() ? null : photoStore.writePhoto(pngData);
        return photoFileName != null && registerPhoto(photoFileName, ItemStack.EMPTY) ? photoFileName : null;
    }

    private void requestPhotoCapture() {
        if (!isPhotoCameraMode() || capturePending || isPhotoStorageFull()) {
            return;
        }

        capturePending = true;
    }

    private void completePendingCapture() {
        if (!capturePending) {
            return;
        }

        capturePending = false;
        if (!isPhotoCameraMode()) {
            return;
        }

        captureAndSavePhoto();
        captureFlashTicks = 3;
    }

    private String capturePhotoToFile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        var renderTarget = minecraft.getMainRenderTarget();
        if (renderTarget == null || renderTarget.width <= 0 || renderTarget.height <= 0) {
            return null;
        }

        NativeImage framebufferImage = null;
        NativeImage scaledImage = null;
        try {
            framebufferImage = Screenshot.takeScreenshot(renderTarget);
            int sourceWidth = framebufferImage.getWidth();
            int sourceHeight = framebufferImage.getHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                return null;
            }

            int longEdge = Math.max(sourceWidth, sourceHeight);
            if (longEdge <= PHOTO_MAX_EDGE) {
                return photoStore.writePhoto(framebufferImage);
            }

            scaledImage = new NativeImage(
                    Math.max(1, Math.round((float) sourceWidth * PHOTO_MAX_EDGE / longEdge)),
                    Math.max(1, Math.round((float) sourceHeight * PHOTO_MAX_EDGE / longEdge)), false);
            framebufferImage.resizeSubRectTo(0, 0, sourceWidth, sourceHeight, scaledImage);
            return photoStore.writePhoto(scaledImage);
        } catch (Exception exception) {
            BPhone.LOGGER.warn("Failed to capture phone photo", exception);
            return null;
        } finally {
            if (framebufferImage != null) {
                framebufferImage.close();
            }
            if (scaledImage != null) {
                scaledImage.close();
            }
        }
    }

    private ItemStack captureTargetItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return ItemStack.EMPTY;
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            Entity entity = entityHitResult.getEntity();

            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (!stack.isEmpty()) {
                    return stack.copy();
                }
            }

            if (entity instanceof ItemFrame itemFrame) {
                ItemStack stack = itemFrame.getItem();
                if (!stack.isEmpty()) {
                    return stack.copy();
                }
            }

            if (entity instanceof LivingEntity livingEntity) {
                ItemStack mainHand = livingEntity.getMainHandItem();
                if (!mainHand.isEmpty()) {
                    return mainHand.copy();
                }

                ItemStack offHand = livingEntity.getOffhandItem();
                if (!offHand.isEmpty()) {
                    return offHand.copy();
                }
            }
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockState blockState = minecraft.level.getBlockState(blockHitResult.getBlockPos());
            Item item = blockState.getBlock().asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }

        return ItemStack.EMPTY;
    }

    private ItemStack sanitizePhotoCaptureStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int count = Mth.clamp(stack.getCount(), 1, Math.max(1, stack.getMaxStackSize()));
        ItemStack snapshot = new ItemStack(stack.getItem(), count);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            snapshot.set(DataComponents.CUSTOM_NAME, stack.getHoverName());
        }
        return snapshot;
    }

    private boolean appendPhotoToPhone(String photoFileName, ItemStack captureStack) {
        return photoStore.appendPhotoToStack(getOpenPhoneStack(), photoFileName, captureStack);
    }

    private boolean removePhotoFromPhone(int viewerIndex, String expectedFileName) {
        boolean removed = photoStore.removePhotoFromStack(getOpenPhoneStack(), viewerIndex, expectedFileName);
        if (removed && expectedFileName != null && !expectedFileName.isBlank()) {
            PhoneNetworkingClient.requestDeletePhotoMetadata(openHand, expectedFileName);
        }
        return removed;
    }

    List<PhotoEntry> getPhotos() {
        return photoStore.getPhotos(getOpenPhoneStack());
    }

    Path getPhotoPath(String fileName) {
        return photoStore.photoPath(fileName);
    }

    int getPhotoCount() {
        return photoStore.getPhotoCount(getOpenPhoneStack());
    }

    int getMaxPhotoCount() {
        return photoStore.maxPhotos();
    }

    boolean isPhotoStorageFull() {
        return photoStore.isPhotoLimitReached(getOpenPhoneStack());
    }

    PhotoTexture getOrLoadPhotoTexture(String fileName, boolean previewMode) {
        return photoStore.getOrLoadPhotoTexture(fileName, previewMode);
    }

    private void releasePhotoTextures() {
        photoStore.releaseTextures();
    }

    private void unloadPhotoTexture(String fileName) {
        photoStore.unloadTexture(fileName);
    }

    private void deletePhotoFile(String fileName) {
        photoStore.deletePhotoFile(fileName);
    }

    private ItemStack getOpenPhoneStack() {
        if (homePhoneMode) {
            return ItemStack.EMPTY;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceHandStack = minecraft.player.getItemInHand(openHand);
        if (ModItems.isPhone(sourceHandStack)) {
            return sourceHandStack;
        }

        ItemStack mainHandStack = minecraft.player.getMainHandItem();
        if (ModItems.isPhone(mainHandStack)) {
            return mainHandStack;
        }

        ItemStack offHandStack = minecraft.player.getOffhandItem();
        if (ModItems.isPhone(offHandStack)) {
            return offHandStack;
        }

        return ItemStack.EMPTY;
    }

    void openSettingsApp() {
        setBankReceiveActive(false);
        closeCamera();
        unlocked = true;
        settingsAppMode = true;
        settingsWallpaperPage = PhoneSettingsSurfaceRenderer.PAGE_MAIN;
        settingsPickerScroll = 0;
        rebuildWidgets();
    }

    void openPublicChatSettings() {
        chatAppMode = false;
        settingsAppMode = true;
        settingsWallpaperPage = PhoneSettingsSurfaceRenderer.PAGE_CHAT;
        settingsPickerScroll = 0;
        rebuildWidgets();
    }

    void closeSettingsApp() {
        PhoneRingtones.stopPreview();
        settingsAppMode = false;
        settingsWallpaperPage = PhoneSettingsSurfaceRenderer.PAGE_MAIN;
        settingsPickerScroll = 0;
        if (publicChatOnly) {
            chatAppMode = true;
            chat.openPublic(true);
            rebuildWidgets();
            return;
        }
        unlocked = true;
        rebuildWidgets();
    }

    void openSettingsPage(int page) {
        PhoneRingtones.stopPreview();
        settingsWallpaperPage = page;
        settingsPickerScroll = 0;
        rebuildWidgets();
    }

    ItemStack getSettingsPhoneStack() {
        return getOpenPhoneStack();
    }

    void applyPhoneSetting(String key, String value) {
        ItemStack stack = getOpenPhoneStack();
        if (stack.isEmpty() || homePhoneMode) {
            return;
        }

        if (PhoneSettingsData.apply(stack, key, value)) {
            PhoneNetworkingClient.requestUpdateSetting(openHand, key, value);
        }
    }

    void applyWallpaperFromContextPhoto(boolean lockScreen, boolean homeScreen) {
        List<PhotoEntry> photos = getPhotos();
        if (wallpaperContextPhotoIndex < 0 || wallpaperContextPhotoIndex >= photos.size()) {
            return;
        }

        PhotoEntry photo = photos.get(wallpaperContextPhotoIndex);
        if (!photo.hasFile()) {
            return;
        }

        String key = PhoneWallpaperData.photoKey(photo.fileName);
        if (lockScreen) {
            applyPhoneSetting(PhoneSettingsData.KEY_LOCK_WALLPAPER, key);
        }
        if (homeScreen) {
            applyPhoneSetting(PhoneSettingsData.KEY_HOME_WALLPAPER, key);
        }
        showStatus("screen.bphone.phone.gallery.wallpaper_set");
    }

    void closeWallpaperContextMenu() {
        wallpaperContextMenuOpen = false;
        wallpaperContextPhotoIndex = -1;
        rebuildWidgets();
    }

    private void renderAppBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(displayX, displayY, displayX + displayWidth, displayY + displayHeight, PhoneUi.PAGE);
    }

    private String currentWallpaperKey(boolean lockScreen) {
        ItemStack stack = getOpenPhoneStack();
        return lockScreen ? PhoneWallpaperData.getLockWallpaper(stack) : PhoneWallpaperData.getHomeWallpaper(stack);
    }

    void openNotificationsApp() {
        setBankReceiveActive(false);
        closeCamera();
        unlocked = true;
        notificationsAppMode = true;
        notificationsScrollOffset = 0;
        notificationDetailId = null;
        NotificationNetworkingClient.requestSync();
        rebuildWidgets();
    }

    void closeNotificationsApp() {
        notificationsAppMode = false;
        notificationsScrollOffset = 0;
        notificationDetailId = null;
        unlocked = true;
        rebuildWidgets();
    }

    PhoneNotification getNotificationDetail() {
        if (notificationDetailId == null) {
            return null;
        }

        for (PhoneNotification notification : NotificationClientState.getNotifications()) {
            if (notification.id().equals(notificationDetailId)) {
                return notification;
            }
        }
        return null;
    }

    void openNotificationDetail(UUID notificationId) {
        notificationDetailId = notificationId;
    }

    void openAnnouncementsApp(UUID detailId) {
        setBankReceiveActive(false);
        closeCamera();
        unlocked = true;
        announcementsAppMode = true;
        announcementsScroll = 0;
        announcementDetailId = detailId;
        announcementDetailScroll = 0;
        announcementDeleteConfirm = 0;
        AnnouncementNetworkingClient.markRead();
        rebuildWidgets();
    }

    void closeAnnouncementsApp() {
        announcementsAppMode = false;
        announcementsScroll = 0;
        announcementDetailId = null;
        announcementDetailScroll = 0;
        announcementDeleteConfirm = 0;
        unlocked = true;
        rebuildWidgets();
    }

    Announcement getAnnouncementDetail() {
        return announcementDetailId == null ? null : AnnouncementClientState.find(announcementDetailId);
    }

    void openAnnouncementDetail(UUID announcementId) {
        announcementDetailId = announcementId;
        announcementDetailScroll = 0;
        announcementDeleteConfirm = 0;
    }

    void closeAnnouncementDetail() {
        announcementDetailId = null;
        announcementDetailScroll = 0;
        announcementDeleteConfirm = 0;
    }

    void closeNotificationDetail() {
        notificationDetailId = null;
    }

    void openMapApp() {
        setBankReceiveActive(false);
        closeCamera();
        unlocked = true;
        mapAppMode = true;
        map.open();
        rebuildWidgets();
    }

    void closeMapApp() {
        mapAppMode = false;
        map.close();
        unlocked = true;
        rebuildWidgets();
    }

    void openShopApp() {
        setBankReceiveActive(false);
        closeCamera();
        unlocked = true;
        shopAppMode = true;
        resetShopState();
        ShopNetworkingClient.requestCatalog();
        rebuildWidgets();
    }

    void closeShopApp() {
        shopAppMode = false;
        resetShopState();
        unlocked = true;
        rebuildWidgets();
    }

    private void resetShopState() {
        shopPage = PhoneShopSurfaceRenderer.PAGE_CATEGORIES;
        shopCategoryId = "";
        shopEntryId = "";
        shopQuantity = 1;
        shopScrollOffset = 0;
        shopStatus = Component.empty();
        shopStatusError = false;
        shopPurchasePending = false;
        PhoneTradeSurfaceRenderer.reset(this);
    }

    ShopCategory getShopCategory() {
        for (ShopCategory category : PhoneClientShopState.getCategories()) {
            if (category.id().equals(shopCategoryId)) {
                return category;
            }
        }
        return null;
    }

    ShopEntry getShopEntry() {
        ShopCategory category = getShopCategory();
        if (category == null) {
            return null;
        }

        for (ShopEntry entry : category.entries()) {
            if (entry.id().equals(shopEntryId)) {
                return entry;
            }
        }
        return null;
    }

    void openShopCategory(int index) {
        List<ShopCategory> categories = PhoneClientShopState.getCategories();
        if (index < 0 || index >= categories.size()) {
            return;
        }

        shopCategoryId = categories.get(index).id();
        shopEntryId = "";
        shopPage = PhoneShopSurfaceRenderer.PAGE_ITEMS;
        shopScrollOffset = 0;
        shopStatus = Component.empty();
        shopStatusError = false;
    }

    void openShopEntry(int index) {
        ShopCategory category = getShopCategory();
        if (category == null || index < 0 || index >= category.entries().size()) {
            return;
        }

        shopEntryId = category.entries().get(index).id();
        shopPage = PhoneShopSurfaceRenderer.PAGE_DETAIL;
        shopQuantity = 1;
        shopStatus = Component.empty();
        shopStatusError = false;
        shopPurchasePending = false;
    }

    void shopBack() {
        if (PhoneTradeSurfaceRenderer.back(this)) {
            return;
        }

        if (shopPage == PhoneShopSurfaceRenderer.PAGE_DETAIL) {
            shopPage = PhoneShopSurfaceRenderer.PAGE_ITEMS;
            shopEntryId = "";
            shopStatus = Component.empty();
            shopStatusError = false;
            shopPurchasePending = false;
            return;
        }

        if (shopPage == PhoneShopSurfaceRenderer.PAGE_ITEMS) {
            shopPage = PhoneShopSurfaceRenderer.PAGE_CATEGORIES;
            shopCategoryId = "";
            shopScrollOffset = 0;
            return;
        }

        closeShopApp();
    }

    void adjustShopQuantity(int delta) {
        shopQuantity = Mth.clamp(shopQuantity + delta, 1, ShopNetworking.MAX_QUANTITY);
    }

    void requestShopPurchase() {
        ShopCategory category = getShopCategory();
        ShopEntry entry = getShopEntry();
        if (category == null || entry == null || shopPurchasePending) {
            return;
        }

        shopPurchasePending = true;
        shopStatus = Component.translatable("screen.bphone.phone.shop.status.processing");
        shopStatusError = false;
        ShopNetworkingClient.requestPurchase(category.id(), entry.id(), shopQuantity);
    }

    void handleShopResult(boolean success, Component message) {
        shopPurchasePending = false;
        if (!shopAppMode) {
            return;
        }

        PhoneTradeSurfaceRenderer.onResult(this, success);
        shopStatus = message == null ? Component.empty() : message;
        shopStatusError = !success;
    }

    void handleShopCatalogUpdated() {
        if (!shopAppMode) {
            return;
        }

        if (PhoneTradeSurfaceRenderer.handles(shopPage)) {
            PhoneTradeSurfaceRenderer.onUpdated(this);
            return;
        }

        if (shopPage != PhoneShopSurfaceRenderer.PAGE_CATEGORIES && getShopCategory() == null) {
            shopPage = PhoneShopSurfaceRenderer.PAGE_CATEGORIES;
            shopCategoryId = "";
            shopEntryId = "";
        } else if (shopPage == PhoneShopSurfaceRenderer.PAGE_DETAIL && getShopEntry() == null) {
            shopPage = PhoneShopSurfaceRenderer.PAGE_ITEMS;
            shopEntryId = "";
        }
    }

    ResourceLocation resolveWallpaperTexture(boolean lockScreen) {
        ItemStack stack = getOpenPhoneStack();
        String key = lockScreen
                ? PhoneWallpaperData.getLockWallpaper(stack)
                : PhoneWallpaperData.getHomeWallpaper(stack);
        if (PhoneWallpaperData.isBuiltin(key)) {
            return PhoneWallpaperData.resolveBuiltinTexture(key);
        }
        return null;
    }

    String getWallpaperPhotoFileName(boolean lockScreen) {
        ItemStack stack = getOpenPhoneStack();
        String key = lockScreen
                ? PhoneWallpaperData.getLockWallpaper(stack)
                : PhoneWallpaperData.getHomeWallpaper(stack);
        return PhoneWallpaperData.getPhotoFileName(key);
    }

    void setWallpaperFromPhoto(String photoFileName, boolean lockScreen, boolean homeScreen) {
        String key = PhoneWallpaperData.photoKey(photoFileName);
        if (lockScreen) {
            applyPhoneSetting(PhoneSettingsData.KEY_LOCK_WALLPAPER, key);
        }
        if (homeScreen) {
            applyPhoneSetting(PhoneSettingsData.KEY_HOME_WALLPAPER, key);
        }
    }

    void setWallpaperBuiltin(String builtinName, boolean lockScreen, boolean homeScreen) {
        String key = PhoneWallpaperData.BUILTIN_PREFIX + builtinName;
        if (lockScreen) {
            applyPhoneSetting(PhoneSettingsData.KEY_LOCK_WALLPAPER, key);
        }
        if (homeScreen) {
            applyPhoneSetting(PhoneSettingsData.KEY_HOME_WALLPAPER, key);
        }
    }

    private void requestCallSync() {
        if (homePhoneMode) {
            PhoneNetworkingClient.requestSync(homePhonePos);
        } else {
            PhoneNetworkingClient.requestSync();
        }
    }

    private void requestSaveContact(String number, String suggestedName) {
        if (homePhoneMode) {
            PhoneNetworkingClient.requestSaveContact(number, suggestedName, homePhonePos);
        } else {
            PhoneNetworkingClient.requestSaveContact(number, suggestedName);
        }
    }

    private void requestDeleteContact(String number) {
        if (homePhoneMode) {
            PhoneNetworkingClient.requestDeleteContact(number, homePhonePos);
        } else {
            PhoneNetworkingClient.requestDeleteContact(number);
        }
    }

    private HomePhoneBlockEntity getClientHomePhone() {
        if (!homePhoneMode || homePhonePos == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }

        return minecraft.level.getBlockEntity(homePhonePos) instanceof HomePhoneBlockEntity homePhone ? homePhone : null;
    }

    boolean isPublicChatOnly() {
        return publicChatOnly;
    }

    private boolean isDarkTheme() {
        ItemStack stack = getOpenPhoneStack();
        if (!publicChatOnly || !stack.isEmpty()) {
            return PhoneSettingsData.isDarkMode(stack);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = minecraft.player.getInventory().getItem(slot);
                if (ModItems.isPhone(candidate)) {
                    return PhoneSettingsData.isDarkMode(candidate);
                }
            }
        }
        return true;
    }

    private boolean isCallScreenLocked() {
        if (homePhoneMode || publicChatOnly) {
            return false;
        }

        PhoneCallState state = PhoneClientCallState.getState();
        if (state == PhoneCallState.OUTGOING_RINGING
                || state == PhoneCallState.INCOMING_RINGING
                || state == PhoneCallState.CONNECTED) {
            return true;
        }

        return callSessionMode && !activeCallNumber.isEmpty() && !activeCallMissed;
    }

    private void closeScreenKeepCall() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    void showStatus(String translationKey, Object... args) {
        statusMessage = Component.translatable(translationKey, args);
        statusTicks = STATUS_TICKS;
    }

    private void renderCaptureFlash(GuiGraphics guiGraphics) {
        int flashX = displayX;
        int flashY = displayY;
        int flashWidth = displayWidth;
        int flashHeight = displayHeight;
        int alpha = switch (captureFlashTicks) {
            case 3 -> 220;
            case 2 -> 132;
            default -> 72;
        };

        guiGraphics.fill(flashX, flashY, flashX + flashWidth, flashY + flashHeight,
                (alpha << 24) | 0x00FFFFFF);
    }

    public boolean isCameraModeActive() {
        return cameraMode;
    }

    public boolean isBankReceivePoseActive() {
        return bankReceiveActive;
    }

    public boolean isCameraMoveModeActive() {
        return cameraMode && cameraMoveMode;
    }

    public boolean isSelfieCameraModeActive() {
        return cameraMode && selfieCameraMode;
    }

    public boolean isHomePhoneMode() {
        return homePhoneMode;
    }

    InteractionHand getOpenHand() {
        return openHand;
    }

}
