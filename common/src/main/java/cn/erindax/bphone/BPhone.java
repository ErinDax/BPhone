package cn.erindax.bphone;

import cn.erindax.bphone.airstrike.AirstrikeManager;
import cn.erindax.bphone.announcement.AnnouncementNetworking;
import cn.erindax.bphone.announcement.AnnouncementStore;
import cn.erindax.bphone.map.MapNetworking;
import cn.erindax.bphone.map.MapStore;
import cn.erindax.bphone.notification.NotificationNetworking;
import cn.erindax.bphone.notification.NotificationStore;
import cn.erindax.bphone.phone.CallLogStorageManager;
import cn.erindax.bphone.phone.PhoneNetworking;
import cn.erindax.bphone.phone.chat.ChatService;
import cn.erindax.bphone.shop.ShopCatalog;
import cn.erindax.bphone.shop.ShopNetworking;
import cn.erindax.bphone.shop.StallStore;
import cn.erindax.bphone.shop.TradeNetworking;
import cn.erindax.bphone.walkie.WalkieNetworking;
import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.File;

public final class BPhone {
    public static final String MOD_ID = "bphone";
    private static final String CHAT_DATABASE_FILE = "beat_chat.db";
    private static final String CALL_LOG_DATABASE_FILE = "beat_call_log.db";
    private static final String CHAT_IMAGE_DIRECTORY = "bphone_chat_images";
    private static final int NOTIFICATION_SAVE_INTERVAL_TICKS = 600;

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final Logger LOGGER = LogUtils.getLogger();
    private static boolean storageLifecycleRegistered;
    private static java.lang.reflect.Method megaphoneSpeakingMethod;

    private BPhone() {
    }

    public static void init() {
        ModSounds.init();
        ModParticles.init();
        ModBlocks.init();
        ModBlockEntities.init();
        ModItems.init();
        ModCreativeTabs.init();

        PhoneNetworking.init();
        NotificationNetworking.init();
        AnnouncementNetworking.init();
        ShopNetworking.init();
        TradeNetworking.init();
        MapNetworking.init();
        WalkieNetworking.init();
        AirstrikeManager.init();
        BPhoneCommands.init();

        registerStorageLifecycle();

        LifecycleEvent.SERVER_STARTED.register(EconomyBridge::onServerStarted);
        LifecycleEvent.SERVER_STOPPED.register(EconomyBridge::onServerStopped);
    }

    private static void registerStorageLifecycle() {
        if (storageLifecycleRegistered) {
            return;
        }
        storageLifecycleRegistered = true;
        LifecycleEvent.SERVER_STARTING.register(server -> {
            BPhoneConfig.load();
            ChatService.open(Platform.getGameFolder().resolve(CHAT_DATABASE_FILE),
                    Platform.getGameFolder().resolve(CHAT_IMAGE_DIRECTORY));
            openCallLogStorage();
            NotificationStore.init(server);
            AnnouncementStore.open(server);
            ShopCatalog.load(server);
            StallStore.open(server);
            MapStore.open(server);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            ChatService.close();
            CallLogStorageManager.shutdown();
            NotificationStore.shutdown();
            AnnouncementStore.close();
            StallStore.close();
            MapStore.close();
        });
        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % NOTIFICATION_SAVE_INTERVAL_TICKS == 0) {
                NotificationStore.saveIfDirty();
                AnnouncementStore.saveIfDirty();
                StallStore.saveIfDirty();
                EconomyBridge.saveIfDirty();
                MapStore.saveIfDirty();
            }
        });
    }

    private static void openCallLogStorage() {
        try {
            CallLogStorageManager.init(Platform.getGameFolder().resolve(CALL_LOG_DATABASE_FILE).toString());
        } catch (Throwable throwable) {
            LOGGER.error("Failed to open call log storage", throwable);
        }
    }

    public static File dataDir() {
        return Platform.getConfigFolder().resolve(MOD_ID).toFile();
    }

    public static boolean isMegaphoneSpeaking(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        cn.erindax.bphone.voice.VoiceProvider provider =
                cn.erindax.bphone.voice.VoiceProviderDetector.detect();
        if (provider == cn.erindax.bphone.voice.VoiceProvider.SIMPLE_VOICE_CHAT) {
            return cn.erindax.bphone.voice.svc.SvcMegaphoneVoiceHook.isSpeaking(player.getUUID());
        }
        if (provider == cn.erindax.bphone.voice.VoiceProvider.PLASMO_VOICE) {
            if (megaphoneSpeakingMethod == null) {
                return false;
            }
            try {
                Object result = megaphoneSpeakingMethod.invoke(null, player.getUUID());
                return result instanceof Boolean speaking && speaking;
            } catch (Throwable throwable) {
                LOGGER.debug("Unable to query megaphone speaking state", throwable);
                return false;
            }
        }
        return false;
    }
}
