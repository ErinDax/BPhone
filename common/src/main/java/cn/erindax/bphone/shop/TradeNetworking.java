package cn.erindax.bphone.shop;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.EconomyBridge;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.api.economy.EconomyResult;
import cn.erindax.bphone.api.market.MarketOrder;
import cn.erindax.bphone.api.market.MarketProvider;
import cn.erindax.bphone.api.market.MarketQuote;
import cn.erindax.bphone.api.market.MarketResult;
import cn.erindax.bphone.api.market.PhoneMarket;
import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.phone.PhoneData;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TradeNetworking {
    public static final ResourceLocation STALL_REQUEST = BPhone.id("stall_request");
    public static final ResourceLocation STALL_SYNC = BPhone.id("stall_sync");
    public static final ResourceLocation STALL_CREATE = BPhone.id("stall_create");
    public static final ResourceLocation STALL_BUY = BPhone.id("stall_buy");
    public static final ResourceLocation STALL_CANCEL = BPhone.id("stall_cancel");
    public static final ResourceLocation MARKET_REQUEST = BPhone.id("market_request");
    public static final ResourceLocation MARKET_SYNC = BPhone.id("market_sync");
    public static final ResourceLocation MARKET_ORDER = BPhone.id("market_order");
    public static final ResourceLocation MARKET_CANCEL = BPhone.id("market_cancel");
    public static final int MAX_SYNC_LISTINGS = 120;
    public static final int MAX_ITEM_BYTES = 4096;
    public static final int MAX_QUOTES = 128;
    public static final int MAX_ORDERS = 64;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_MARKET_QUANTITY = 100_000;
    public static final int INVENTORY_SLOTS = 36;
    private static boolean initialized;

    private TradeNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(STALL_SYNC, MARKET_SYNC);

        NetworkManager.registerReceiver(NetworkManager.c2s(), STALL_REQUEST, (buf, context) ->
                context.queue(() -> sendStalls((ServerPlayer) context.getPlayer())));

        NetworkManager.registerReceiver(NetworkManager.c2s(), STALL_CREATE, (buf, context) -> {
            int slot = buf.readVarInt();
            int count = buf.readVarInt();
            long price = buf.readVarLong();
            context.queue(() -> createListing((ServerPlayer) context.getPlayer(), slot, count, price));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), STALL_BUY, (buf, context) -> {
            UUID id = buf.readUUID();
            long price = buf.readVarLong();
            context.queue(() -> buyListing((ServerPlayer) context.getPlayer(), id, price));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), STALL_CANCEL, (buf, context) -> {
            UUID id = buf.readUUID();
            context.queue(() -> cancelListing((ServerPlayer) context.getPlayer(), id));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), MARKET_REQUEST, (buf, context) ->
                context.queue(() -> sendMarket((ServerPlayer) context.getPlayer())));

        NetworkManager.registerReceiver(NetworkManager.c2s(), MARKET_ORDER, (buf, context) -> {
            String commodity = buf.readUtf(MAX_ID_LENGTH);
            boolean buy = buf.readBoolean();
            int quantity = buf.readVarInt();
            long price = buf.readVarLong();
            context.queue(() -> placeOrder((ServerPlayer) context.getPlayer(), commodity, buy, quantity, price));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), MARKET_CANCEL, (buf, context) -> {
            String orderId = buf.readUtf(MAX_ID_LENGTH);
            context.queue(() -> cancelOrder((ServerPlayer) context.getPlayer(), orderId));
        });

        PlayerEvent.PLAYER_JOIN.register(TradeNetworking::settleOnJoin);
    }

    private static boolean ready(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        if (!PhoneData.hasPhone(player)) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.call.error.no_phone"));
            return false;
        }
        return true;
    }

    private static boolean stallsReady(ServerPlayer player) {
        if (!ready(player)) {
            return false;
        }
        ShopCatalog.reloadIfChanged(player.getServer());
        if (!ShopCatalog.stallsEnabled() || !StallStore.isOpen()) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.disabled"));
            sendStalls(player);
            return false;
        }
        if (!EconomyBridge.available()) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.bank.status.no_economy"));
            return false;
        }
        return true;
    }

    private static int encodedSize(ServerPlayer player, ItemStack stack) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            return buf.readableBytes();
        } catch (Exception exception) {
            return Integer.MAX_VALUE;
        } finally {
            buf.release();
        }
    }

    public static void createListing(ServerPlayer player, int slot, int count, long price) {
        if (!stallsReady(player)) {
            return;
        }
        if (price <= 0L || price > ShopCatalog.stallMaxPrice()) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.invalid_price",
                    ShopCatalog.stallMaxPrice()));
            return;
        }
        if (StallStore.countBySeller(player.getUUID()) >= ShopCatalog.stallLimit()) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.limit",
                    ShopCatalog.stallLimit()));
            return;
        }
        if (StallStore.count() >= StallStore.MAX_LISTINGS) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.full"));
            return;
        }
        if (slot < 0 || slot >= INVENTORY_SLOTS) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.item_changed"));
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty() || stack.getCount() != count) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.item_changed"));
            sendStalls(player);
            return;
        }
        if (ModItems.isPhone(stack)) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.no_phone_item"));
            return;
        }
        if (encodedSize(player, stack) > MAX_ITEM_BYTES) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.too_complex"));
            return;
        }

        ItemStack escrow = player.getInventory().removeItemNoUpdate(slot);
        player.containerMenu.broadcastChanges();
        StallStore.add(new StallStore.Listing(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName(),
                escrow, price, System.currentTimeMillis()));
        ShopNetworking.sendResult(player, true, Component.translatable("screen.bphone.phone.stall.status.listed",
                escrow.getHoverName(), escrow.getCount(), price));
        sendStalls(player);
    }

    public static void buyListing(ServerPlayer buyer, UUID id, long expectedPrice) {
        if (!stallsReady(buyer)) {
            return;
        }
        StallStore.Listing listing = StallStore.find(id);
        if (listing == null) {
            ShopNetworking.sendResult(buyer, false, Component.translatable("screen.bphone.phone.stall.status.gone"));
            sendStalls(buyer);
            return;
        }
        if (listing.seller().equals(buyer.getUUID())) {
            ShopNetworking.sendResult(buyer, false, Component.translatable("screen.bphone.phone.stall.status.own"));
            return;
        }
        if (listing.price() != expectedPrice) {
            ShopNetworking.sendResult(buyer, false, Component.translatable("screen.bphone.phone.stall.status.gone"));
            sendStalls(buyer);
            return;
        }

        Component itemName = listing.stack().getHoverName();
        Component reason = Component.translatableWithFallback("economy.bphone.reason.stall", "Phone stall %s x%s",
                itemName, listing.stack().getCount());
        EconomyResult paid = EconomyBridge.withdraw(buyer, listing.price(), reason);
        if (!paid.isSuccess()) {
            ShopNetworking.sendResult(buyer, false, ShopNetworking.failureMessage(paid));
            return;
        }
        if (StallStore.remove(id) == null) {
            EconomyBridge.deposit(buyer, listing.price(), reason);
            ShopNetworking.sendResult(buyer, false, Component.translatable("screen.bphone.phone.stall.status.gone"));
            sendStalls(buyer);
            return;
        }

        ShopNetworking.giveItems(buyer, listing.stack(), 1);
        MinecraftServer server = buyer.getServer();
        EconomyResult credited = EconomyBridge.depositTo(server, listing.seller(), listing.price(), reason);
        if (!credited.isSuccess()) {
            StallStore.addProceeds(listing.seller(), listing.price());
        }
        ServerPlayer seller = server.getPlayerList().getPlayer(listing.seller());
        if (seller != null) {
            seller.sendSystemMessage(Component.translatable("screen.bphone.phone.stall.sold", itemName,
                    listing.stack().getCount(), buyer.getGameProfile().getName(), listing.price()));
            sendStalls(seller);
        } else {
            StallStore.recordOfflineSale(listing.seller(), listing.price());
        }
        ShopNetworking.sendResult(buyer, true, Component.translatable("screen.bphone.phone.stall.status.bought",
                itemName, listing.stack().getCount(), listing.price()));
        sendStalls(buyer);
    }

    public static void cancelListing(ServerPlayer player, UUID id) {
        if (!ready(player) || !StallStore.isOpen()) {
            return;
        }
        StallStore.Listing listing = StallStore.find(id);
        if (listing == null || !listing.seller().equals(player.getUUID())) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.stall.status.gone"));
            sendStalls(player);
            return;
        }
        if (StallStore.remove(id) != null) {
            ShopNetworking.giveItems(player, listing.stack(), 1);
            ShopNetworking.sendResult(player, true, Component.translatable("screen.bphone.phone.stall.status.cancelled",
                    listing.stack().getHoverName()));
        }
        sendStalls(player);
    }

    private static void settleOnJoin(ServerPlayer player) {
        if (player == null || !StallStore.isOpen()) {
            return;
        }
        long pending = StallStore.takeProceeds(player.getUUID());
        if (pending > 0L) {
            EconomyResult result = EconomyBridge.deposit(player, pending,
                    Component.translatableWithFallback("economy.bphone.reason.stall_settle", "Phone stall income"));
            if (!result.isSuccess()) {
                StallStore.addProceeds(player.getUUID(), pending);
            }
        }
        long[] sales = StallStore.takeOfflineSales(player.getUUID());
        if (sales != null && sales[0] > 0L) {
            player.sendSystemMessage(Component.translatable("screen.bphone.phone.stall.offline_sales", sales[0], sales[1]));
        }
    }

    public static void sendStalls(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        boolean enabled = ShopCatalog.stallsEnabled() && StallStore.isOpen();
        List<StallStore.Listing> all = enabled ? StallStore.listings() : List.of();
        List<StallStore.Listing> visible = new ArrayList<>();
        for (StallStore.Listing listing : all) {
            if (listing.seller().equals(player.getUUID())) {
                visible.add(listing);
            }
        }
        for (StallStore.Listing listing : all) {
            if (visible.size() >= MAX_SYNC_LISTINGS) {
                break;
            }
            if (!listing.seller().equals(player.getUUID())) {
                visible.add(listing);
            }
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeBoolean(enabled);
        buf.writeBoolean(EconomyBridge.available());
        buf.writeLong(EconomyBridge.balanceOf(player));
        buf.writeVarInt(ShopCatalog.stallLimit());
        buf.writeVarLong(ShopCatalog.stallMaxPrice());
        buf.writeVarInt(visible.size());
        for (StallStore.Listing listing : visible) {
            buf.writeUUID(listing.id());
            buf.writeBoolean(listing.seller().equals(player.getUUID()));
            buf.writeUtf(listing.sellerName(), 32);
            ItemStack.STREAM_CODEC.encode(buf, listing.stack());
            buf.writeVarLong(listing.price());
            buf.writeLong(listing.createdAt());
        }
        NetworkManager.sendToPlayer(player, STALL_SYNC, buf);
    }

    private static MarketProvider market(ServerPlayer player) {
        MarketProvider provider = PhoneMarket.provider().orElse(null);
        if (provider == null) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.market.status.unavailable"));
        }
        return provider;
    }

    public static void sendMarket(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        MarketProvider provider = PhoneMarket.provider().orElse(null);
        List<MarketQuote> quotes = List.of();
        List<MarketOrder> orders = List.of();
        if (provider != null) {
            try {
                quotes = provider.quotes(player.getServer(), player.getUUID());
                orders = provider.orders(player.getServer(), player.getUUID());
            } catch (Throwable throwable) {
                BPhone.LOGGER.warn("[BPhone] Market provider {} failed to list quotes", provider.id(), throwable);
            }
        }
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeBoolean(provider != null);
        buf.writeBoolean(EconomyBridge.available());
        buf.writeLong(EconomyBridge.balanceOf(player));
        List<MarketQuote> safeQuotes = quotes == null ? List.of() : quotes.subList(0, Math.min(quotes.size(), MAX_QUOTES));
        buf.writeVarInt(safeQuotes.size());
        for (MarketQuote quote : safeQuotes) {
            buf.writeUtf(clip(quote.id()), MAX_ID_LENGTH);
            ComponentSerialization.STREAM_CODEC.encode(buf, quote.name() == null ? Component.literal(clip(quote.id())) : quote.name());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, quote.icon() == null ? ItemStack.EMPTY : quote.icon());
            buf.writeLong(quote.price());
            buf.writeLong(quote.previousPrice());
            buf.writeLong(quote.volume());
            buf.writeLong(quote.bestBid());
            buf.writeLong(quote.bestAsk());
        }
        List<MarketOrder> safeOrders = orders == null ? List.of() : orders.subList(0, Math.min(orders.size(), MAX_ORDERS));
        buf.writeVarInt(safeOrders.size());
        for (MarketOrder order : safeOrders) {
            buf.writeUtf(clip(order.id()), MAX_ID_LENGTH);
            buf.writeUtf(clip(order.commodityId()), MAX_ID_LENGTH);
            buf.writeBoolean(order.buy());
            buf.writeVarInt(Math.max(0, order.quantity()));
            buf.writeVarInt(Math.max(0, order.filled()));
            buf.writeLong(order.price());
            buf.writeLong(order.createdAt());
        }
        NetworkManager.sendToPlayer(player, MARKET_SYNC, buf);
    }

    public static void placeOrder(ServerPlayer player, String commodity, boolean buy, int quantity, long price) {
        if (!ready(player)) {
            return;
        }
        MarketProvider provider = market(player);
        if (provider == null) {
            return;
        }
        if (quantity <= 0 || quantity > MAX_MARKET_QUANTITY || price <= 0L) {
            ShopNetworking.sendResult(player, false, Component.translatable("screen.bphone.phone.market.status.invalid"));
            return;
        }
        MarketResult result;
        try {
            result = provider.placeOrder(player, commodity, buy, quantity, price);
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Market provider {} failed to place an order", provider.id(), throwable);
            result = MarketResult.fail(Component.translatable("screen.bphone.phone.market.status.failed"));
        }
        sendMarketResult(player, result);
    }

    public static void cancelOrder(ServerPlayer player, String orderId) {
        if (!ready(player)) {
            return;
        }
        MarketProvider provider = market(player);
        if (provider == null) {
            return;
        }
        MarketResult result;
        try {
            result = provider.cancelOrder(player, orderId);
        } catch (Throwable throwable) {
            BPhone.LOGGER.warn("[BPhone] Market provider {} failed to cancel an order", provider.id(), throwable);
            result = MarketResult.fail(Component.translatable("screen.bphone.phone.market.status.failed"));
        }
        sendMarketResult(player, result);
    }

    private static void sendMarketResult(ServerPlayer player, MarketResult result) {
        MarketResult safe = result == null ? MarketResult.fail(Component.translatable("screen.bphone.phone.market.status.failed")) : result;
        Component message = safe.message() == null || safe.message().getString().isEmpty()
                ? Component.translatable(safe.success() ? "screen.bphone.phone.market.status.placed" : "screen.bphone.phone.market.status.failed")
                : safe.message();
        ShopNetworking.sendResult(player, safe.success(), message);
        sendMarket(player);
    }

    private static String clip(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_ID_LENGTH ? value : value.substring(0, MAX_ID_LENGTH);
    }
}
