package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.api.market.MarketOrder;
import cn.erindax.bphone.api.market.MarketQuote;
import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.shop.ShopCatalog;
import cn.erindax.bphone.shop.ShopCategory;
import cn.erindax.bphone.shop.ShopEntry;
import cn.erindax.bphone.shop.ShopNetworking;
import cn.erindax.bphone.shop.TradeNetworking;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ShopNetworkingClient {
    private static final int MAX_CATEGORIES = 64;
    private static final int MAX_ENTRIES = 1024;
    private static boolean initialized;

    private ShopNetworkingClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.s2c(), ShopNetworking.CATALOG_SYNC, (buf, context) -> {
            boolean economy = buf.readBoolean();
            long balance = buf.readLong();
            int categoryCount = Math.min(MAX_CATEGORIES, buf.readVarInt());
            List<ShopCategory> categories = new ArrayList<>(categoryCount);
            int totalEntries = 0;
            for (int categoryIndex = 0; categoryIndex < categoryCount; categoryIndex++) {
                String id = buf.readUtf(ShopCatalog.MAX_ID_LENGTH);
                String name = buf.readUtf(ShopCatalog.MAX_NAME_LENGTH);
                ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                int entryCount = buf.readVarInt();
                List<ShopEntry> entries = new ArrayList<>(Math.min(entryCount, MAX_ENTRIES));
                for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                    String entryId = buf.readUtf(ShopCatalog.MAX_ID_LENGTH);
                    String entryName = buf.readUtf(ShopCatalog.MAX_NAME_LENGTH);
                    ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                    long price = buf.readVarLong();
                    String description = buf.readUtf(ShopCatalog.MAX_DESCRIPTION_LENGTH);
                    if (totalEntries < MAX_ENTRIES) {
                        entries.add(new ShopEntry(entryId, entryName, stack, price, description));
                        totalEntries++;
                    }
                }
                categories.add(new ShopCategory(id, name, icon, List.copyOf(entries)));
            }
            boolean stalls = buf.readBoolean();
            boolean market = buf.readBoolean();
            context.queue(() -> {
                PhoneClientShopState.applyCatalog(categories, economy, balance, stalls, market);
                if (Minecraft.getInstance().screen instanceof PhoneScreen phoneScreen) {
                    phoneScreen.handleShopCatalogUpdated();
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ShopNetworking.RESULT, (buf, context) -> {
            boolean success = buf.readBoolean();
            Component message = ComponentSerialization.STREAM_CODEC.decode(buf);
            boolean economy = buf.readBoolean();
            long balance = buf.readLong();
            context.queue(() -> {
                PhoneClientShopState.applyBalance(economy, balance);
                if (Minecraft.getInstance().screen instanceof PhoneScreen phoneScreen) {
                    phoneScreen.handleShopResult(success, message);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), TradeNetworking.STALL_SYNC, (buf, context) -> {
            boolean enabled = buf.readBoolean();
            boolean economy = buf.readBoolean();
            long balance = buf.readLong();
            int limit = buf.readVarInt();
            long maxPrice = buf.readVarLong();
            int count = Math.min(TradeNetworking.MAX_SYNC_LISTINGS * 4, buf.readVarInt());
            List<PhoneClientShopState.StallListing> listings = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                UUID id = buf.readUUID();
                boolean own = buf.readBoolean();
                String seller = buf.readUtf(32);
                ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                long price = buf.readVarLong();
                long createdAt = buf.readLong();
                listings.add(new PhoneClientShopState.StallListing(id, own, seller, stack, price, createdAt));
            }
            context.queue(() -> {
                PhoneClientShopState.applyStalls(enabled, economy, balance, limit, maxPrice, listings);
                if (Minecraft.getInstance().screen instanceof PhoneScreen phoneScreen) {
                    phoneScreen.handleShopCatalogUpdated();
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), TradeNetworking.MARKET_SYNC, (buf, context) -> {
            boolean available = buf.readBoolean();
            boolean economy = buf.readBoolean();
            long balance = buf.readLong();
            int quoteCount = Math.min(TradeNetworking.MAX_QUOTES, buf.readVarInt());
            List<MarketQuote> quotes = new ArrayList<>(quoteCount);
            for (int index = 0; index < quoteCount; index++) {
                String id = buf.readUtf(TradeNetworking.MAX_ID_LENGTH);
                Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
                ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                long price = buf.readLong();
                long previous = buf.readLong();
                long volume = buf.readLong();
                long bid = buf.readLong();
                long ask = buf.readLong();
                quotes.add(new MarketQuote(id, name, icon, price, previous, volume, bid, ask));
            }
            int orderCount = Math.min(TradeNetworking.MAX_ORDERS, buf.readVarInt());
            List<MarketOrder> orders = new ArrayList<>(orderCount);
            for (int index = 0; index < orderCount; index++) {
                String id = buf.readUtf(TradeNetworking.MAX_ID_LENGTH);
                String commodity = buf.readUtf(TradeNetworking.MAX_ID_LENGTH);
                boolean buy = buf.readBoolean();
                int quantity = buf.readVarInt();
                int filled = buf.readVarInt();
                long price = buf.readLong();
                long createdAt = buf.readLong();
                orders.add(new MarketOrder(id, commodity, buy, quantity, filled, price, createdAt));
            }
            context.queue(() -> {
                PhoneClientShopState.applyMarket(available, economy, balance, quotes, orders);
                if (Minecraft.getInstance().screen instanceof PhoneScreen phoneScreen) {
                    phoneScreen.handleShopCatalogUpdated();
                }
            });
        });
    }

    public static void requestCatalog() {
        NetworkManager.sendToServer(ShopNetworking.CATALOG_REQUEST, NetworkBufferUtils.create());
    }

    public static void requestPurchase(String categoryId, String entryId, int quantity) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(categoryId == null ? "" : categoryId, ShopCatalog.MAX_ID_LENGTH);
        buf.writeUtf(entryId == null ? "" : entryId, ShopCatalog.MAX_ID_LENGTH);
        buf.writeVarInt(quantity);
        NetworkManager.sendToServer(ShopNetworking.PURCHASE, buf);
    }

    public static void requestStalls() {
        NetworkManager.sendToServer(TradeNetworking.STALL_REQUEST, NetworkBufferUtils.create());
    }

    public static void createListing(int slot, int count, long price) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeVarInt(slot);
        buf.writeVarInt(count);
        buf.writeVarLong(price);
        NetworkManager.sendToServer(TradeNetworking.STALL_CREATE, buf);
    }

    public static void buyListing(UUID id, long price) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUUID(id);
        buf.writeVarLong(price);
        NetworkManager.sendToServer(TradeNetworking.STALL_BUY, buf);
    }

    public static void cancelListing(UUID id) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUUID(id);
        NetworkManager.sendToServer(TradeNetworking.STALL_CANCEL, buf);
    }

    public static void requestMarket() {
        NetworkManager.sendToServer(TradeNetworking.MARKET_REQUEST, NetworkBufferUtils.create());
    }

    public static void placeOrder(String commodity, boolean buy, int quantity, long price) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(commodity == null ? "" : commodity, TradeNetworking.MAX_ID_LENGTH);
        buf.writeBoolean(buy);
        buf.writeVarInt(quantity);
        buf.writeVarLong(price);
        NetworkManager.sendToServer(TradeNetworking.MARKET_ORDER, buf);
    }

    public static void cancelOrder(String orderId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(orderId == null ? "" : orderId, TradeNetworking.MAX_ID_LENGTH);
        NetworkManager.sendToServer(TradeNetworking.MARKET_CANCEL, buf);
    }
}
