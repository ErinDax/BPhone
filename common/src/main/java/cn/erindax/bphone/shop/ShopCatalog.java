package cn.erindax.bphone.shop;

import cn.erindax.bphone.BPhone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShopCatalog {
    public static final int MAX_ID_LENGTH = 32;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int MAX_CATEGORIES = 32;
    private static final int MAX_ENTRIES = 512;
    private static final String FILE_NAME = "shop.json";
    private static final String DEFAULT_CONTENT = """
            {
              "player_stalls": {"enabled": false, "max_listings": 10, "max_price": 1000000},
              "categories": [
                {
                  "id": "food",
                  "name": "食物",
                  "icon": {"id": "minecraft:bread"},
                  "items": [
                    {"id": "water", "name": "矿泉水", "stack": {"id": "minecraft:potion", "count": 1, "components": {"minecraft:potion_contents": {"potion": "minecraft:water"}}}, "price": 5, "description": "解渴用的瓶装水"},
                    {"id": "bread", "stack": {"id": "minecraft:bread", "count": 4}, "price": 20},
                    {"id": "cooked_beef", "stack": {"id": "minecraft:cooked_beef", "count": 4}, "price": 40},
                    {"id": "golden_carrot", "stack": {"id": "minecraft:golden_carrot", "count": 4}, "price": 80, "description": "饱和度很高的食物"}
                  ]
                },
                {
                  "id": "tools",
                  "name": "工具",
                  "icon": {"id": "minecraft:iron_pickaxe"},
                  "items": [
                    {"id": "torch", "stack": {"id": "minecraft:torch", "count": 16}, "price": 16},
                    {"id": "shears", "stack": {"id": "minecraft:shears"}, "price": 60},
                    {"id": "iron_pickaxe", "stack": {"id": "minecraft:iron_pickaxe"}, "price": 120}
                  ]
                },
                {
                  "id": "blocks",
                  "name": "建材",
                  "icon": {"id": "minecraft:oak_planks"},
                  "items": [
                    {"id": "oak_planks", "stack": {"id": "minecraft:oak_planks", "count": 64}, "price": 32},
                    {"id": "glass", "stack": {"id": "minecraft:glass", "count": 16}, "price": 24}
                  ]
                }
              ]
            }
            """;

    private static volatile List<ShopCategory> categories = List.of();
    private static volatile boolean stallsEnabled;
    private static volatile int stallLimit = 10;
    private static volatile long stallMaxPrice = 1_000_000L;
    private static long loadedModified = Long.MIN_VALUE;

    private ShopCatalog() {
    }

    public static List<ShopCategory> categories() {
        return categories;
    }

    public static boolean stallsEnabled() {
        return stallsEnabled;
    }

    public static int stallLimit() {
        return stallLimit;
    }

    public static long stallMaxPrice() {
        return stallMaxPrice;
    }

    public static ShopEntry find(String categoryId, String entryId) {
        if (categoryId == null || entryId == null) {
            return null;
        }

        for (ShopCategory category : categories) {
            if (!category.id().equals(categoryId)) {
                continue;
            }
            for (ShopEntry entry : category.entries()) {
                if (entry.id().equals(entryId)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static synchronized void load(MinecraftServer server) {
        if (server == null) {
            return;
        }

        Path path = path();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_CONTENT, StandardCharsets.UTF_8);
            }
            loadedModified = Files.getLastModifiedTime(path).toMillis();
            String content = Files.readString(path, StandardCharsets.UTF_8);
            categories = parse(server, content);
            parseStalls(content);
            BPhone.LOGGER.info("[BPhone] Loaded {} shop categories from {}", categories.size(), path);
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to load shop catalog {}, keeping the previous catalog", path, exception);
        }
    }

    public static synchronized void reloadIfChanged(MinecraftServer server) {
        Path path = path();
        try {
            long modified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : Long.MIN_VALUE;
            if (modified != loadedModified || modified == Long.MIN_VALUE) {
                load(server);
            }
        } catch (IOException exception) {
            BPhone.LOGGER.warn("[BPhone] Failed to check shop catalog {}", path, exception);
        }
    }

    public static synchronized boolean setStallsEnabled(MinecraftServer server, boolean enabled) {
        Path path = path();
        try {
            if (!Files.exists(path)) {
                load(server);
            }
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject stalls = GsonHelper.getAsJsonObject(root, "player_stalls", new JsonObject());
            stalls.addProperty("enabled", enabled);
            root.add("player_stalls", stalls);
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            Files.writeString(path, gson.toJson(root), StandardCharsets.UTF_8);
            load(server);
            return true;
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to update {}", path, exception);
            return false;
        }
    }

    private static Path path() {
        return BPhone.dataDir().toPath().resolve(FILE_NAME);
    }

    private static List<ShopCategory> parse(MinecraftServer server, String content) {
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<ShopCategory> result = new ArrayList<>();
        Set<String> categoryIds = new HashSet<>();
        int totalEntries = 0;
        for (JsonElement element : GsonHelper.getAsJsonArray(root, "categories", new JsonArray())) {
            if (result.size() >= MAX_CATEGORIES || !element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String id = clip(GsonHelper.getAsString(object, "id", ""), MAX_ID_LENGTH);
            if (id.isBlank() || !categoryIds.add(id)) {
                BPhone.LOGGER.warn("[BPhone] Skipping shop category with a missing or duplicate id: {}", id);
                continue;
            }

            List<ShopEntry> entries = new ArrayList<>();
            Set<String> entryIds = new HashSet<>();
            for (JsonElement entryElement : GsonHelper.getAsJsonArray(object, "items", new JsonArray())) {
                if (totalEntries >= MAX_ENTRIES) {
                    break;
                }
                ShopEntry entry = parseEntry(ops, entryElement, id);
                if (entry == null) {
                    continue;
                }
                if (!entryIds.add(entry.id())) {
                    BPhone.LOGGER.warn("[BPhone] Skipping duplicate shop item {}/{}", id, entry.id());
                    continue;
                }
                entries.add(entry);
                totalEntries++;
            }

            ItemStack icon = object.has("icon") ? parseStack(ops, object.get("icon"), id + "/icon") : ItemStack.EMPTY;
            if (icon.isEmpty() && !entries.isEmpty()) {
                icon = entries.get(0).stack().copyWithCount(1);
            }
            String name = clip(GsonHelper.getAsString(object, "name", id), MAX_NAME_LENGTH);
            result.add(new ShopCategory(id, name, icon, List.copyOf(entries)));
        }
        return List.copyOf(result);
    }

    private static void parseStalls(String content) {
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        JsonObject stalls = GsonHelper.getAsJsonObject(root, "player_stalls", new JsonObject());
        stallsEnabled = GsonHelper.getAsBoolean(stalls, "enabled", false);
        stallLimit = Math.max(1, Math.min(100, GsonHelper.getAsInt(stalls, "max_listings", 10)));
        stallMaxPrice = Math.max(1L, GsonHelper.getAsLong(stalls, "max_price", 1_000_000L));
    }

    private static ShopEntry parseEntry(RegistryOps<JsonElement> ops, JsonElement element, String categoryId) {
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        String id = clip(GsonHelper.getAsString(object, "id", ""), MAX_ID_LENGTH);
        long price = GsonHelper.getAsLong(object, "price", 0L);
        ItemStack stack = object.has("stack")
                ? parseStack(ops, object.get("stack"), categoryId + "/" + id)
                : ItemStack.EMPTY;
        if (id.isBlank() || price <= 0L || stack.isEmpty()) {
            BPhone.LOGGER.warn("[BPhone] Skipping shop item {}/{}: id, stack and a positive price are required",
                    categoryId, id);
            return null;
        }

        String name = clip(GsonHelper.getAsString(object, "name", ""), MAX_NAME_LENGTH);
        String description = clip(GsonHelper.getAsString(object, "description", ""), MAX_DESCRIPTION_LENGTH);
        return new ShopEntry(id, name, stack, price, description);
    }

    private static ItemStack parseStack(RegistryOps<JsonElement> ops, JsonElement element, String context) {
        return ItemStack.CODEC.parse(ops, element)
                .resultOrPartial(error -> BPhone.LOGGER.warn("[BPhone] Invalid shop item stack {}: {}", context, error))
                .orElse(ItemStack.EMPTY);
    }

    private static String clip(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
