package cn.erindax.bphone.shop;

import cn.erindax.bphone.BPhone;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StallStore {
    public static final int MAX_LISTINGS = 500;
    private static final String FILE_NAME = "bphone_stalls.nbt";
    private static final List<Listing> LISTINGS = new ArrayList<>();
    private static final Map<UUID, Long> PROCEEDS = new HashMap<>();
    private static final Map<UUID, long[]> OFFLINE_SALES = new HashMap<>();
    private static Path file;
    private static HolderLookup.Provider registries;
    private static boolean dirty;

    public record Listing(UUID id, UUID seller, String sellerName, ItemStack stack, long price, long createdAt) {
    }

    private StallStore() {
    }

    public static synchronized void open(MinecraftServer server) {
        LISTINGS.clear();
        PROCEEDS.clear();
        OFFLINE_SALES.clear();
        dirty = false;
        registries = server.registryAccess();
        file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        load();
    }

    public static synchronized void close() {
        save();
        LISTINGS.clear();
        PROCEEDS.clear();
        OFFLINE_SALES.clear();
        file = null;
        registries = null;
    }

    public static synchronized boolean isOpen() {
        return file != null;
    }

    public static synchronized List<Listing> listings() {
        return List.copyOf(LISTINGS);
    }

    public static synchronized int count() {
        return LISTINGS.size();
    }

    public static synchronized int countBySeller(UUID seller) {
        int count = 0;
        for (Listing listing : LISTINGS) {
            if (listing.seller().equals(seller)) {
                count++;
            }
        }
        return count;
    }

    public static synchronized Listing find(UUID id) {
        for (Listing listing : LISTINGS) {
            if (listing.id().equals(id)) {
                return listing;
            }
        }
        return null;
    }

    public static synchronized void add(Listing listing) {
        LISTINGS.add(0, listing);
        dirty = true;
    }

    public static synchronized Listing remove(UUID id) {
        for (int index = 0; index < LISTINGS.size(); index++) {
            if (LISTINGS.get(index).id().equals(id)) {
                dirty = true;
                return LISTINGS.remove(index);
            }
        }
        return null;
    }

    public static synchronized void addProceeds(UUID seller, long amount) {
        if (amount > 0L) {
            PROCEEDS.merge(seller, amount, Long::sum);
            dirty = true;
        }
    }

    public static synchronized long takeProceeds(UUID seller) {
        Long amount = PROCEEDS.remove(seller);
        if (amount != null) {
            dirty = true;
        }
        return amount == null ? 0L : amount;
    }

    public static synchronized void recordOfflineSale(UUID seller, long amount) {
        long[] sales = OFFLINE_SALES.computeIfAbsent(seller, key -> new long[2]);
        sales[0]++;
        sales[1] += amount;
        dirty = true;
    }

    public static synchronized long[] takeOfflineSales(UUID seller) {
        long[] sales = OFFLINE_SALES.remove(seller);
        if (sales != null) {
            dirty = true;
        }
        return sales;
    }

    public static synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private static void save() {
        if (file == null || registries == null) {
            return;
        }
        try {
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            for (Listing listing : LISTINGS) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("id", listing.id());
                tag.putUUID("seller", listing.seller());
                tag.putString("seller_name", listing.sellerName());
                tag.put("stack", listing.stack().save(registries));
                tag.putLong("price", listing.price());
                tag.putLong("created", listing.createdAt());
                list.add(tag);
            }
            root.put("listings", list);
            CompoundTag proceeds = new CompoundTag();
            for (Map.Entry<UUID, Long> entry : PROCEEDS.entrySet()) {
                proceeds.putLong(entry.getKey().toString(), entry.getValue());
            }
            root.put("proceeds", proceeds);
            CompoundTag sales = new CompoundTag();
            for (Map.Entry<UUID, long[]> entry : OFFLINE_SALES.entrySet()) {
                sales.putLongArray(entry.getKey().toString(), entry.getValue());
            }
            root.put("offline_sales", sales);
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
            dirty = false;
        } catch (Exception exception) {
            BPhone.LOGGER.error("Failed to save player stalls", exception);
        }
    }

    private static void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ListTag list = root.getList("listings", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                CompoundTag tag = list.getCompound(index);
                ItemStack stack = ItemStack.parse(registries, tag.getCompound("stack")).orElse(ItemStack.EMPTY);
                if (stack.isEmpty() || !tag.hasUUID("id") || !tag.hasUUID("seller")) {
                    continue;
                }
                LISTINGS.add(new Listing(tag.getUUID("id"), tag.getUUID("seller"), tag.getString("seller_name"), stack,
                        tag.getLong("price"), tag.getLong("created")));
            }
            CompoundTag proceeds = root.getCompound("proceeds");
            for (String key : proceeds.getAllKeys()) {
                try {
                    PROCEEDS.put(UUID.fromString(key), proceeds.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
            CompoundTag sales = root.getCompound("offline_sales");
            for (String key : sales.getAllKeys()) {
                long[] value = sales.getLongArray(key);
                if (value.length == 2) {
                    try {
                        OFFLINE_SALES.put(UUID.fromString(key), value);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception exception) {
            BPhone.LOGGER.error("Failed to load player stalls", exception);
        }
    }
}
