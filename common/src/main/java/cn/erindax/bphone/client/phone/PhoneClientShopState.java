package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.api.market.MarketOrder;
import cn.erindax.bphone.api.market.MarketQuote;
import cn.erindax.bphone.shop.ShopCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class PhoneClientShopState {
    record StallListing(UUID id, boolean own, String seller, ItemStack stack, long price, long createdAt) {
    }

    private static volatile List<ShopCategory> categories = List.of();
    private static volatile boolean loaded;
    private static volatile boolean economyAvailable;
    private static volatile long balance;
    private static volatile boolean stallsEnabled;
    private static volatile boolean marketAvailable;
    private static volatile List<StallListing> listings = List.of();
    private static volatile boolean stallsLoaded;
    private static volatile int stallLimit;
    private static volatile long stallMaxPrice;
    private static volatile List<MarketQuote> quotes = List.of();
    private static volatile List<MarketOrder> orders = List.of();
    private static volatile boolean marketLoaded;

    private PhoneClientShopState() {
    }

    static List<ShopCategory> getCategories() {
        return categories;
    }

    static boolean isLoaded() {
        return loaded;
    }

    static boolean isEconomyAvailable() {
        return economyAvailable;
    }

    static long getBalance() {
        return balance;
    }

    static boolean isStallsEnabled() {
        return stallsEnabled;
    }

    static boolean isMarketAvailable() {
        return marketAvailable;
    }

    static List<StallListing> getListings() {
        return listings;
    }

    static boolean isStallsLoaded() {
        return stallsLoaded;
    }

    static int getStallLimit() {
        return stallLimit;
    }

    static long getStallMaxPrice() {
        return stallMaxPrice;
    }

    static int countOwnListings() {
        int count = 0;
        for (StallListing listing : listings) {
            if (listing.own()) {
                count++;
            }
        }
        return count;
    }

    static StallListing findListing(UUID id) {
        if (id == null) {
            return null;
        }
        for (StallListing listing : listings) {
            if (listing.id().equals(id)) {
                return listing;
            }
        }
        return null;
    }

    static List<MarketQuote> getQuotes() {
        return quotes;
    }

    static List<MarketOrder> getOrders() {
        return orders;
    }

    static boolean isMarketLoaded() {
        return marketLoaded;
    }

    static MarketQuote findQuote(String id) {
        if (id == null) {
            return null;
        }
        for (MarketQuote quote : quotes) {
            if (quote.id().equals(id)) {
                return quote;
            }
        }
        return null;
    }

    static void applyCatalog(List<ShopCategory> value, boolean economy, long currentBalance, boolean stalls, boolean market) {
        categories = value == null ? List.of() : List.copyOf(value);
        economyAvailable = economy;
        balance = currentBalance;
        stallsEnabled = stalls;
        marketAvailable = market;
        loaded = true;
    }

    static void applyBalance(boolean economy, long currentBalance) {
        economyAvailable = economy;
        balance = currentBalance;
    }

    static void applyStalls(boolean enabled, boolean economy, long currentBalance, int limit, long maxPrice,
                            List<StallListing> value) {
        stallsEnabled = enabled;
        economyAvailable = economy;
        balance = currentBalance;
        stallLimit = limit;
        stallMaxPrice = maxPrice;
        listings = value == null ? List.of() : List.copyOf(value);
        stallsLoaded = true;
    }

    static void applyMarket(boolean available, boolean economy, long currentBalance, List<MarketQuote> quoteList,
                            List<MarketOrder> orderList) {
        marketAvailable = available;
        economyAvailable = economy;
        balance = currentBalance;
        quotes = quoteList == null ? List.of() : List.copyOf(quoteList);
        orders = orderList == null ? List.of() : List.copyOf(orderList);
        marketLoaded = true;
    }

    public static void clear() {
        categories = List.of();
        loaded = false;
        economyAvailable = false;
        balance = 0L;
        stallsEnabled = false;
        marketAvailable = false;
        listings = List.of();
        stallsLoaded = false;
        stallLimit = 0;
        stallMaxPrice = 0L;
        quotes = List.of();
        orders = List.of();
        marketLoaded = false;
    }
}
