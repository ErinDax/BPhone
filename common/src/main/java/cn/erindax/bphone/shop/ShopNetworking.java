package cn.erindax.bphone.shop;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.EconomyBridge;
import cn.erindax.bphone.api.economy.EconomyResult;
import cn.erindax.bphone.api.market.PhoneMarket;
import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.PhoneNetworking;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ShopNetworking {
    public static final ResourceLocation CATALOG_REQUEST = BPhone.id("shop_catalog_request");
    public static final ResourceLocation CATALOG_SYNC = BPhone.id("shop_catalog_sync");
    public static final ResourceLocation PURCHASE = BPhone.id("shop_purchase");
    public static final ResourceLocation RESULT = BPhone.id("shop_result");
    public static final int MAX_QUANTITY = 64;
    private static boolean initialized;

    private ShopNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(CATALOG_SYNC, RESULT);

        NetworkManager.registerReceiver(NetworkManager.c2s(), CATALOG_REQUEST, (buf, context) ->
                context.queue(() -> sendCatalog((ServerPlayer) context.getPlayer())));

        NetworkManager.registerReceiver(NetworkManager.c2s(), PURCHASE, (buf, context) -> {
            String categoryId = buf.readUtf(ShopCatalog.MAX_ID_LENGTH);
            String entryId = buf.readUtf(ShopCatalog.MAX_ID_LENGTH);
            int quantity = buf.readVarInt();
            context.queue(() -> purchase((ServerPlayer) context.getPlayer(), categoryId, entryId, quantity));
        });
    }

    public static Component displayName(String name, ItemStack stack) {
        return name == null || name.isBlank() ? stack.getHoverName() : Component.literal(name);
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            sendCatalog(player);
        }
    }

    private static void sendCatalog(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        ShopCatalog.reloadIfChanged(player.getServer());
        List<ShopCategory> categories = ShopCatalog.categories();
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeBoolean(EconomyBridge.available());
        buf.writeLong(EconomyBridge.balanceOf(player));
        buf.writeVarInt(categories.size());
        for (ShopCategory category : categories) {
            buf.writeUtf(category.id(), ShopCatalog.MAX_ID_LENGTH);
            buf.writeUtf(category.name(), ShopCatalog.MAX_NAME_LENGTH);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, category.icon());
            buf.writeVarInt(category.entries().size());
            for (ShopEntry entry : category.entries()) {
                buf.writeUtf(entry.id(), ShopCatalog.MAX_ID_LENGTH);
                buf.writeUtf(entry.name(), ShopCatalog.MAX_NAME_LENGTH);
                ItemStack.STREAM_CODEC.encode(buf, entry.stack());
                buf.writeVarLong(entry.price());
                buf.writeUtf(entry.description(), ShopCatalog.MAX_DESCRIPTION_LENGTH);
            }
        }
        buf.writeBoolean(ShopCatalog.stallsEnabled());
        buf.writeBoolean(PhoneMarket.isAvailable());
        NetworkManager.sendToPlayer(player, CATALOG_SYNC, buf);
    }

    private static void purchase(ServerPlayer player, String categoryId, String entryId, int quantity) {
        if (player == null || player.getServer() == null) {
            return;
        }

        if (!PhoneData.hasPhone(player)) {
            sendResult(player, false, Component.translatable("screen.bphone.phone.call.error.no_phone"));
            return;
        }

        ShopCatalog.reloadIfChanged(player.getServer());
        ShopEntry entry = ShopCatalog.find(categoryId, entryId);
        if (entry == null) {
            sendResult(player, false, Component.translatable("screen.bphone.phone.shop.status.not_found"));
            sendCatalog(player);
            return;
        }

        if (quantity < 1 || quantity > MAX_QUANTITY) {
            sendResult(player, false, Component.translatable("screen.bphone.phone.shop.status.invalid_quantity"));
            return;
        }

        if (!EconomyBridge.available()) {
            sendResult(player, false, Component.translatable("screen.bphone.phone.bank.status.no_economy"));
            return;
        }

        long total;
        try {
            total = Math.multiplyExact(entry.price(), (long) quantity);
        } catch (ArithmeticException exception) {
            sendResult(player, false, Component.translatable("screen.bphone.phone.shop.status.invalid_quantity"));
            return;
        }

        Component itemName = displayName(entry.name(), entry.stack());
        EconomyResult result = EconomyBridge.withdraw(player, total,
                Component.translatableWithFallback("economy.bphone.reason.shop", "Phone shop %s x%s", itemName, quantity));
        if (!result.isSuccess()) {
            sendResult(player, false, failureMessage(result));
            return;
        }

        giveItems(player, entry.stack(), quantity);
        Component message = Component.translatable("screen.bphone.phone.shop.status.purchased", itemName, quantity, total);
        sendResult(player, true, message);
        PhoneNetworking.sendPhoneToast(player, Component.translatable("toast.bphone.phone.shop.title"), message);
    }

    static Component failureMessage(EconomyResult result) {
        return switch (result) {
            case INSUFFICIENT_FUNDS -> Component.translatable("screen.bphone.phone.bank.status.insufficient");
            case UNAVAILABLE -> Component.translatable("screen.bphone.phone.bank.status.no_economy");
            case INVALID_AMOUNT -> Component.translatable("screen.bphone.phone.shop.status.invalid_quantity");
            default -> Component.translatable("screen.bphone.phone.shop.status.failed");
        };
    }

    static void giveItems(ServerPlayer player, ItemStack template, int quantity) {
        long remaining = (long) template.getCount() * quantity;
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        while (remaining > 0L) {
            int count = (int) Math.min(maxStackSize, remaining);
            remaining -= count;
            ItemStack stack = template.copyWithCount(count);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    static void sendResult(ServerPlayer player, boolean success, Component message) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeBoolean(success);
        ComponentSerialization.STREAM_CODEC.encode(buf, message);
        buf.writeBoolean(EconomyBridge.available());
        buf.writeLong(EconomyBridge.balanceOf(player));
        NetworkManager.sendToPlayer(player, RESULT, buf);
    }
}
