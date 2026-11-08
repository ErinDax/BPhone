package cn.erindax.bphone.client.announcement;

import cn.erindax.bphone.announcement.Announcement;
import cn.erindax.bphone.announcement.AnnouncementNetworking;
import cn.erindax.bphone.network.NetworkBufferUtils;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AnnouncementNetworkingClient {
    private static final int PREVIEW_LENGTH = 80;
    private static boolean initialized;

    private AnnouncementNetworkingClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.s2c(), AnnouncementNetworking.SYNC, (buf, context) -> {
            int count = Math.min(buf.readVarInt(), 64);
            List<Announcement> list = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                list.add(Announcement.read(buf));
            }
            long lastRead = buf.readLong();
            boolean join = buf.readBoolean();
            context.queue(() -> {
                AnnouncementClientState.apply(list, lastRead);
                if (join) {
                    showMissed();
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), AnnouncementNetworking.ALERT, (buf, context) -> {
            Announcement announcement = Announcement.read(buf);
            context.queue(() -> alert(announcement));
        });
    }

    public static boolean canManage() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasPermissions(AnnouncementNetworking.MANAGE_PERMISSION);
    }

    private static void showMissed() {
        Announcement starred = AnnouncementClientState.newestUnreadStarred();
        if (starred != null) {
            prominent(starred);
            return;
        }
        int unread = AnnouncementClientState.getUnreadCount();
        if (unread > 0) {
            toast(Component.translatable("announcement.bphone.toast.title"),
                    Component.translatable("announcement.bphone.toast.missed", unread));
        }
    }

    private static void alert(Announcement announcement) {
        if (announcement.starred()) {
            prominent(announcement);
        } else {
            toast(Component.translatable("announcement.bphone.toast.title"), Component.literal(announcement.title()));
        }
    }

    private static void prominent(Announcement announcement) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setTimes(10, 80, 20);
        minecraft.gui.setSubtitle(Component.literal(announcement.title()).withStyle(ChatFormatting.YELLOW));
        minecraft.gui.setTitle(Component.translatable("announcement.bphone.alert.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0F));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.6F));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("announcement.bphone.alert.chat",
                    announcement.title(), preview(announcement.body())).withStyle(ChatFormatting.GOLD), false);
        }
        toast(Component.translatable("announcement.bphone.alert.title"), Component.literal(announcement.title()));
    }

    private static void toast(Component title, Component body) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
    }

    private static String preview(String body) {
        String flat = body == null ? "" : body.replace('\n', ' ').trim();
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH) + "...";
    }

    public static void markRead() {
        AnnouncementClientState.markReadLocally();
        NetworkManager.sendToServer(AnnouncementNetworking.READ, NetworkBufferUtils.create());
    }

    public static void publish(UUID id, String title, String body, boolean starred) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeBoolean(id != null);
        if (id != null) {
            buf.writeUUID(id);
        }
        buf.writeUtf(Announcement.clip(title, Announcement.MAX_TITLE_LENGTH), Announcement.MAX_TITLE_LENGTH);
        buf.writeUtf(Announcement.clip(body, Announcement.MAX_BODY_LENGTH), Announcement.MAX_BODY_LENGTH);
        buf.writeBoolean(starred);
        NetworkManager.sendToServer(AnnouncementNetworking.PUBLISH, buf);
    }

    public static void star(UUID id, boolean starred) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUUID(id);
        buf.writeBoolean(starred);
        NetworkManager.sendToServer(AnnouncementNetworking.STAR, buf);
    }

    public static void delete(UUID id) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUUID(id);
        NetworkManager.sendToServer(AnnouncementNetworking.DELETE, buf);
    }
}
