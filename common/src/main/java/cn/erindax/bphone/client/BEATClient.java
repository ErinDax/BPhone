package cn.erindax.bphone.client;

import com.mojang.blaze3d.platform.InputConstants;
import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModBlockEntities;
import cn.erindax.bphone.ModBlocks;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.block.HomePhoneBlock;
import cn.erindax.bphone.client.airstrike.AirstrikeClientConfig;
import cn.erindax.bphone.client.airstrike.AirstrikeClientState;
import cn.erindax.bphone.client.airstrike.AirstrikeNetworkingClient;
import cn.erindax.bphone.client.announcement.AnnouncementClientState;
import cn.erindax.bphone.client.announcement.AnnouncementNetworkingClient;
import cn.erindax.bphone.client.map.MapClientState;
import cn.erindax.bphone.client.map.MapNavigation;
import cn.erindax.bphone.client.map.MapNetworkingClient;
import cn.erindax.bphone.client.map.MapTerrainCache;
import cn.erindax.bphone.client.notification.NotificationClientState;
import cn.erindax.bphone.client.notification.NotificationNetworkingClient;
import cn.erindax.bphone.client.phone.ChatNetworkingClient;
import cn.erindax.bphone.client.phone.PhoneClientHooks;
import cn.erindax.bphone.client.phone.PhoneClientCallState;
import cn.erindax.bphone.client.phone.PhoneNetworkingClient;
import cn.erindax.bphone.client.phone.ShopNetworkingClient;
import cn.erindax.bphone.client.walkie.WalkieNetworkingClient;
import cn.erindax.bphone.item.HomePhoneHandsetItem;
import cn.erindax.bphone.item.MegaphoneItem;
import cn.erindax.bphone.item.WalkieRadioItem;
import cn.erindax.bphone.phone.PhoneCallState;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public final class BEATClient {
    private static boolean initialized;
    private static boolean propertyRegistered;
    private static boolean homePhoneSneakUseDown;
    private static boolean airstrikeModeCycleDown;
    private static boolean walkieOpenDown;
    private static boolean walkieTalkDown;
    private static InteractionHand walkieTalkHand = InteractionHand.MAIN_HAND;
    private static Method megaphoneVoiceTickMethod;
    private static Method megaphoneVoiceShutdownMethod;
    private static boolean megaphoneVoiceAvailable;
    private static Method phoneCallVoiceTickMethod;
    private static Method phoneCallVoiceShutdownMethod;
    private static boolean phoneCallVoiceAvailable;

    private BEATClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        registerModelProperties();
        AirstrikeClientConfig.init();
        AirstrikeNetworkingClient.init();
        PhoneNetworkingClient.init();
        NotificationNetworkingClient.init();
        ShopNetworkingClient.init();
        ChatNetworkingClient.init();
        AnnouncementNetworkingClient.init();
        MapNetworkingClient.init();
        cn.erindax.bphone.client.phone.DevUiSnapshots.init();
        initOptionalVoiceHooks();
        ClientTickEvent.CLIENT_POST.register(BEATClient::onClientTick);
        ClientGuiEvent.RENDER_HUD.register(MapNavigation::renderHud);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register((player) -> {
            homePhoneSneakUseDown = false;
            airstrikeModeCycleDown = false;
            walkieOpenDown = false;
            resetWalkieTalkState();
            AirstrikeClientState.clear();
            PhoneNetworkingClient.clearClientState();
            NotificationClientState.clear();
            AnnouncementClientState.clear();
            MapClientState.clear();
            MapNavigation.stop();
            MapTerrainCache.reset();
            PhoneClientHooks.clearMobileCallHotbarLock();
            PhoneClientHooks.clearBankReceiveWorldState(false);
            tickMegaphoneVoice(false);
            tickPhoneCallVoice(false);
        });
        ClientLifecycleEvent.CLIENT_STOPPING.register((client) -> {
            homePhoneSneakUseDown = false;
            airstrikeModeCycleDown = false;
            walkieOpenDown = false;
            resetWalkieTalkState();
            AirstrikeClientState.clear();
            PhoneNetworkingClient.clearClientState();
            NotificationClientState.clear();
            AnnouncementClientState.clear();
            MapTerrainCache.reset();
            PhoneClientHooks.clearMobileCallHotbarLock();
            PhoneClientHooks.clearBankReceiveWorldState(false);
            shutdownMegaphoneVoice();
            shutdownPhoneCallVoice();
        });
    }

    private static void onClientTick(Minecraft minecraft) {
        AirstrikeClientState.tick(minecraft);
        PhoneNetworkingClient.tick(minecraft);
        MapTerrainCache.tick(minecraft);
        MapNavigation.tick(minecraft);
        PhoneClientHooks.tickBankReceiveWorld(minecraft);
        handleWalkieOpen(minecraft);
        handleWalkieTalkState(minecraft);
        handleAirstrikeModeCycle(minecraft);
        handleHomePhoneSneakUse(minecraft);
        PhoneClientHooks.tickMobileCallHotbarLock();
        tickMegaphoneVoice(minecraft != null && MegaphoneItem.isUsingMegaphone(minecraft.player));
        tickPhoneCallVoice(isPhoneCallConnected());
    }


    private static void handleWalkieOpen(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.screen != null) {
            walkieOpenDown = false;
            return;
        }

        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (!attackDown) {
            walkieOpenDown = false;
            return;
        }

        if (walkieOpenDown) {
            return;
        }

        ItemStack mainHandItem = minecraft.player.getMainHandItem();
        ItemStack offHandItem = minecraft.player.getOffhandItem();
        InteractionHand hand;
        if (mainHandItem.is(ModItems.WALKIE.get())) {
            hand = InteractionHand.MAIN_HAND;
        } else if (offHandItem.is(ModItems.WALKIE.get())) {
            hand = InteractionHand.OFF_HAND;
        } else {
            return;
        }

        walkieOpenDown = true;
        minecraft.options.keyAttack.setDown(false);
        WalkieRadioItem.openWalkieScreenClient(hand);
    }

    private static void handleWalkieTalkState(Minecraft minecraft) {
        InteractionHand hand = resolveHeldWalkieHand(minecraft);
        boolean shouldTalk = minecraft != null
                && minecraft.player != null
                && minecraft.screen == null
                && hand != null
                && isKeyActuallyDown(minecraft, minecraft.options.keyUse);

        if (shouldTalk) {
            if (!walkieTalkDown || walkieTalkHand != hand) {
                if (walkieTalkDown) {
                    WalkieNetworkingClient.requestTalkState(walkieTalkHand, false);
                }
                walkieTalkDown = true;
                walkieTalkHand = hand;
                WalkieNetworkingClient.requestTalkState(hand, true);
            }
            return;
        }

        if (walkieTalkDown) {
            resetWalkieTalkState();
        }
    }

    private static InteractionHand resolveHeldWalkieHand(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        if (minecraft.player.getMainHandItem().is(ModItems.WALKIE.get())) {
            return InteractionHand.MAIN_HAND;
        }

        return minecraft.player.getOffhandItem().is(ModItems.WALKIE.get()) ? InteractionHand.OFF_HAND : null;
    }

    private static void resetWalkieTalkState() {
        if (!walkieTalkDown) {
            return;
        }

        WalkieNetworkingClient.requestTalkState(walkieTalkHand, false);
        walkieTalkDown = false;
    }

    private static boolean isKeyActuallyDown(Minecraft minecraft, KeyMapping keyMapping) {
        if (minecraft == null || keyMapping == null) {
            return false;
        }

        InputConstants.Key inputKey = InputConstants.getKey(keyMapping.saveString());
        if (inputKey.getValue() == InputConstants.UNKNOWN.getValue()) {
            return keyMapping.isDown();
        }

        long windowHandle = minecraft.getWindow().getWindow();
        if (inputKey.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(windowHandle, inputKey.getValue()) == GLFW.GLFW_PRESS;
        }

        if (inputKey.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(windowHandle, inputKey.getValue());
        }

        return keyMapping.isDown();
    }

    private static void handleAirstrikeModeCycle(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.screen != null) {
            airstrikeModeCycleDown = false;
            return;
        }

        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (!attackDown) {
            airstrikeModeCycleDown = false;
            return;
        }

        if (airstrikeModeCycleDown) {
            return;
        }

        ItemStack mainHandItem = minecraft.player.getMainHandItem();
        if (!mainHandItem.is(ModItems.AIRSTRIKE_RADIO.get())) {
            return;
        }

        airstrikeModeCycleDown = true;
        AirstrikeNetworkingClient.requestModeCycle();
    }

    private static void handleHomePhoneSneakUse(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            homePhoneSneakUseDown = false;
            return;
        }

        boolean useDown = minecraft.options.keyUse.isDown();
        if (!useDown || !minecraft.player.isShiftKeyDown()) {
            homePhoneSneakUseDown = false;
            return;
        }

        if (homePhoneSneakUseDown) {
            return;
        }

        homePhoneSneakUseDown = true;
        BlockPos targetPos = resolveTargetedHeldHomePhone(minecraft, minecraft.player);
        if (targetPos != null) {
            PhoneNetworkingClient.requestPutDownHomePhoneHandset(targetPos);
        }
    }

    private static BlockPos resolveTargetedHeldHomePhone(Minecraft minecraft, LocalPlayer player) {
        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }

        BlockPos blockPos = blockHitResult.getBlockPos();
        if (!(minecraft.level.getBlockState(blockPos).getBlock() instanceof HomePhoneBlock)) {
            return null;
        }

        if (isHandsetBoundTo(player.getMainHandItem(), minecraft, blockPos)
                || isHandsetBoundTo(player.getOffhandItem(), minecraft, blockPos)) {
            return blockPos;
        }

        return null;
    }

    private static boolean isHandsetBoundTo(ItemStack stack, Minecraft minecraft, BlockPos blockPos) {
        var address = HomePhoneHandsetItem.getBoundAddress(stack);
        return address != null
                && minecraft.level != null
                && address.dimension().equals(minecraft.level.dimension())
                && address.blockPos().equals(blockPos);
    }

    private static void initOptionalVoiceHooks() {
        cn.erindax.bphone.voice.VoiceProvider provider = cn.erindax.bphone.voice.VoiceProviderDetector.detect();

        if (provider == cn.erindax.bphone.voice.VoiceProvider.PLASMO_VOICE) {
            try {
                Class<?> hookClass = Class.forName("cn.erindax.bphone.client.voice.MegaphoneVoiceHook");
                hookClass.getMethod("init").invoke(null);
                megaphoneVoiceTickMethod = hookClass.getMethod("tick", boolean.class);
                megaphoneVoiceShutdownMethod = hookClass.getMethod("shutdown");
                megaphoneVoiceAvailable = true;
                BPhone.LOGGER.info("Plasmo Voice client hooks initialized.");
            } catch (Throwable throwable) {
                megaphoneVoiceTickMethod = null;
                megaphoneVoiceShutdownMethod = null;
                megaphoneVoiceAvailable = false;
                BPhone.LOGGER.warn("Unable to initialize Plasmo Voice megaphone client hook", throwable);
            }

            try {
                Class<?> hookClass = Class.forName("cn.erindax.bphone.client.voice.PhoneCallVoiceHook");
                hookClass.getMethod("init").invoke(null);
                phoneCallVoiceTickMethod = hookClass.getMethod("tick", boolean.class);
                phoneCallVoiceShutdownMethod = hookClass.getMethod("shutdown");
                phoneCallVoiceAvailable = true;
            } catch (Throwable throwable) {
                phoneCallVoiceTickMethod = null;
                phoneCallVoiceShutdownMethod = null;
                phoneCallVoiceAvailable = false;
                BPhone.LOGGER.warn("Unable to initialize Plasmo Voice phone call client hook", throwable);
            }
        } else if (provider == cn.erindax.bphone.voice.VoiceProvider.SIMPLE_VOICE_CHAT) {
            BPhone.LOGGER.info("Simple Voice Chat detected. Client voice effects are not supported on SVC and will be skipped.");
            megaphoneVoiceAvailable = false;
            phoneCallVoiceAvailable = false;
        } else {
            megaphoneVoiceAvailable = false;
            phoneCallVoiceAvailable = false;
        }
    }

    private static boolean isPhoneCallConnected() {
        return PhoneClientCallState.getState() == PhoneCallState.CONNECTED
                || PhoneClientCallState.hasHomePhoneState(PhoneCallState.CONNECTED);
    }

    private static void tickMegaphoneVoice(boolean shouldEnable) {
        if (!megaphoneVoiceAvailable || megaphoneVoiceTickMethod == null) {
            return;
        }

        try {
            megaphoneVoiceTickMethod.invoke(null, shouldEnable);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to tick megaphone voice hook", throwable);
        }
    }

    private static void shutdownMegaphoneVoice() {
        if (!megaphoneVoiceAvailable || megaphoneVoiceShutdownMethod == null) {
            return;
        }

        try {
            megaphoneVoiceShutdownMethod.invoke(null);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to shutdown megaphone voice hook", throwable);
        }
    }

    private static void tickPhoneCallVoice(boolean shouldEnable) {
        if (!phoneCallVoiceAvailable || phoneCallVoiceTickMethod == null) {
            return;
        }

        try {
            phoneCallVoiceTickMethod.invoke(null, shouldEnable);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to tick phone call voice hook", throwable);
        }
    }

    private static void shutdownPhoneCallVoice() {
        if (!phoneCallVoiceAvailable || phoneCallVoiceShutdownMethod == null) {
            return;
        }

        try {
            phoneCallVoiceShutdownMethod.invoke(null);
        } catch (Throwable throwable) {
            BPhone.LOGGER.debug("Unable to shutdown phone call voice hook", throwable);
        }
    }

    private static void registerModelProperties() {
        if (propertyRegistered) {
            return;
        }

        propertyRegistered = true;
        ResourceLocation phoneCallPoseProperty = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "call_pose");
        ResourceLocation phoneOnProperty = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "screen_on");
        ResourceLocation phoneQrProperty = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "bank_qr");
        ResourceLocation phoneChatQrProperty = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "chat_qr");
        ResourceLocation megaphoneTootingProperty = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "tooting");
        for (RegistrySupplier<Item> phoneSupplier : ModItems.PHONES) {
                Item phoneItem = phoneSupplier.get();
                ItemPropertiesRegistry.register(phoneItem, phoneCallPoseProperty,
                        (stack, level, entity, seed) -> entity != null && PhoneClientHooks.shouldUsePhoneCallPose(entity)
                                ? 1.0F
                                : 0.0F);
                ItemPropertiesRegistry.register(phoneItem, phoneOnProperty,
                        (stack, level, entity, seed) -> entity != null && PhoneClientHooks.shouldUsePhoneOnModel(entity)
                                ? 1.0F
                                : 0.0F);
                ItemPropertiesRegistry.register(phoneItem, phoneQrProperty,
                        (stack, level, entity, seed) -> entity != null && PhoneClientHooks.shouldUsePhoneQrModel(entity)
                                ? 1.0F
                                : 0.0F);
                ItemPropertiesRegistry.register(phoneItem, phoneChatQrProperty,
                        (stack, level, entity, seed) -> entity != null && PhoneClientHooks.shouldUseChatQrModel(entity)
                                ? 1.0F
                                : 0.0F);
            }
        ItemPropertiesRegistry.register(ModItems.MEGAPHONE.get(), megaphoneTootingProperty,
                (stack, level, entity, seed) -> entity != null
                        && entity.isUsingItem()
                        && entity.getUseItem() == stack
                        ? 1.0F
                        : 0.0F);
    }
}
