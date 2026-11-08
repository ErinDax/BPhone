package cn.erindax.bphone.announcement;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.network.NetworkBufferUtils;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class AnnouncementNetworking {
    public static final int MANAGE_PERMISSION = 2;
    public static final ResourceLocation SYNC = BPhone.id("announcement_sync");
    public static final ResourceLocation ALERT = BPhone.id("announcement_alert");
    public static final ResourceLocation READ = BPhone.id("announcement_read");
    public static final ResourceLocation PUBLISH = BPhone.id("announcement_publish");
    public static final ResourceLocation STAR = BPhone.id("announcement_star");
    public static final ResourceLocation DELETE = BPhone.id("announcement_delete");

    private static boolean initialized;

    private AnnouncementNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(SYNC, ALERT);

        NetworkManager.registerReceiver(NetworkManager.c2s(), READ, (buf, context) -> context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player && AnnouncementStore.isOpen()) {
                AnnouncementStore.markRead(player.getUUID());
                sync(player, false);
            }
        }));

        NetworkManager.registerReceiver(NetworkManager.c2s(), PUBLISH, (buf, context) -> {
            UUID id = buf.readBoolean() ? buf.readUUID() : null;
            String title = buf.readUtf(Announcement.MAX_TITLE_LENGTH);
            String body = buf.readUtf(Announcement.MAX_BODY_LENGTH);
            boolean starred = buf.readBoolean();
            context.queue(() -> publish((ServerPlayer) context.getPlayer(), id, title, body, starred));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), STAR, (buf, context) -> {
            UUID id = buf.readUUID();
            boolean starred = buf.readBoolean();
            context.queue(() -> star((ServerPlayer) context.getPlayer(), id, starred));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), DELETE, (buf, context) -> {
            UUID id = buf.readUUID();
            context.queue(() -> delete((ServerPlayer) context.getPlayer(), id));
        });

        PlayerEvent.PLAYER_JOIN.register(player -> sync(player, true));
    }

    public static boolean canManage(ServerPlayer player) {
        return player != null && player.hasPermissions(MANAGE_PERMISSION);
    }

    private static boolean ready(ServerPlayer player) {
        if (player == null || player.getServer() == null || !AnnouncementStore.isOpen()) {
            return false;
        }
        if (!canManage(player)) {
            player.displayClientMessage(Component.translatable("announcement.bphone.error.permission"), true);
            return false;
        }
        return true;
    }

    static String cleanTitle(String raw) {
        String value = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ');
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().filter(point -> !Character.isISOControl(point)).forEach(builder::appendCodePoint);
        return Announcement.clip(builder.toString().trim(), Announcement.MAX_TITLE_LENGTH);
    }

    static String cleanBody(String raw) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().filter(point -> point == '\n' || !Character.isISOControl(point)).forEach(builder::appendCodePoint);
        return Announcement.clip(builder.toString().strip(), Announcement.MAX_BODY_LENGTH);
    }

    public static void publish(ServerPlayer player, UUID id, String rawTitle, String rawBody, boolean starred) {
        if (!ready(player)) {
            return;
        }
        String title = cleanTitle(rawTitle);
        if (title.isEmpty()) {
            player.displayClientMessage(Component.translatable("announcement.bphone.error.title"), true);
            return;
        }
        Announcement previous = id == null ? null : AnnouncementStore.find(id);
        Announcement saved = AnnouncementStore.publish(id, title, cleanBody(rawBody), player.getGameProfile().getName(), starred);
        if (saved == null) {
            player.displayClientMessage(Component.translatable("announcement.bphone.error.missing"), true);
            return;
        }
        MinecraftServer server = player.getServer();
        syncAll(server);
        boolean fresh = previous == null;
        if (fresh || (starred && !previous.starred())) {
            alertAll(server, saved, player.getUUID());
        }
        player.displayClientMessage(Component.translatable(fresh ? "announcement.bphone.published" : "announcement.bphone.updated"), true);
    }

    public static Announcement publishAs(MinecraftServer server, String author, UUID authorId, String rawTitle,
                                         String rawBody, boolean starred) {
        if (server == null || !AnnouncementStore.isOpen()) {
            return null;
        }
        String title = cleanTitle(rawTitle);
        if (title.isEmpty()) {
            return null;
        }
        Announcement saved = AnnouncementStore.publish(null, title, cleanBody(rawBody), author, starred);
        if (saved != null) {
            syncAll(server);
            alertAll(server, saved, authorId);
        }
        return saved;
    }

    public static void star(ServerPlayer player, UUID id, boolean starred) {
        if (!ready(player)) {
            return;
        }
        Announcement previous = AnnouncementStore.find(id);
        Announcement updated = previous == null ? null : AnnouncementStore.setStarred(id, starred);
        if (updated == null) {
            player.displayClientMessage(Component.translatable("announcement.bphone.error.missing"), true);
            return;
        }
        syncAll(player.getServer());
        if (starred && !previous.starred()) {
            alertAll(player.getServer(), updated, player.getUUID());
        }
    }

    public static void delete(ServerPlayer player, UUID id) {
        if (!ready(player)) {
            return;
        }
        if (AnnouncementStore.delete(id)) {
            syncAll(player.getServer());
            player.displayClientMessage(Component.translatable("announcement.bphone.deleted"), true);
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            sync(player, false);
        }
    }

    private static void alertAll(MinecraftServer server, Announcement announcement, UUID author) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!player.getUUID().equals(author)) {
                RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
                announcement.write(buf);
                NetworkManager.sendToPlayer(player, ALERT, buf);
            }
        }
    }

    public static void sync(ServerPlayer player, boolean join) {
        if (player == null || !AnnouncementStore.isOpen()) {
            return;
        }
        List<Announcement> list = AnnouncementStore.list();
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeVarInt(list.size());
        for (Announcement announcement : list) {
            announcement.write(buf);
        }
        buf.writeLong(AnnouncementStore.lastRead(player.getUUID()));
        buf.writeBoolean(join);
        NetworkManager.sendToPlayer(player, SYNC, buf);
    }
}
