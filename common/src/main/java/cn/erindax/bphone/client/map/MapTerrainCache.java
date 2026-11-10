package cn.erindax.bphone.client.map;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.phone.PhoneData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MapTerrainCache {
    public static final int TILE_SIZE = 128;
    private static final int MAX_LOADED_TILES = 192;
    private static final int CHUNKS_PER_TICK = 3;
    private static final long RESCAN_MILLIS = 30_000L;
    private static final int SAVE_INTERVAL_TICKS = 1200;

    private static final Map<Long, Tile> TILES = new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<Long> MISSING = new HashSet<>();
    private static final Map<Long, Long> SCANNED = new HashMap<>();
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static String worldKey = "";
    private static String dimensionKey = "";
    private static Path directory;
    private static int ticks;
    private static boolean carryingPhone;
    private static int textureSerial;
    private static int loadBudget;

    private static final class Tile {
        final int tileX;
        final int tileZ;
        final NativeImage image;
        DynamicTexture texture;
        ResourceLocation textureId;
        boolean textureDirty;
        DynamicTexture cityTexture;
        ResourceLocation cityTextureId;
        boolean cityDirty;
        boolean diskDirty;

        Tile(int tileX, int tileZ, NativeImage image) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.image = image;
        }
    }

    private MapTerrainCache() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            if (!worldKey.isEmpty()) {
                reset();
            }
            return;
        }
        String world = worldKey(minecraft);
        String dimension = minecraft.level.dimension().location().toString();
        if (!world.equals(worldKey) || !dimension.equals(dimensionKey)) {
            reset();
            worldKey = world;
            dimensionKey = dimension;
            directory = minecraft.gameDirectory.toPath().resolve("bphone").resolve("mapcache")
                    .resolve(world).resolve(sanitize(dimension));
        }
        if (minecraft.level.dimensionType().hasCeiling()) {
            return;
        }
        ticks++;
        if (ticks % 20 == 1) {
            carryingPhone = PhoneData.hasPhone(minecraft.player);
        }
        if (!carryingPhone) {
            return;
        }
        if (QUEUE.isEmpty() && ticks % 40 == 0) {
            refill(minecraft);
        }
        for (int count = 0; count < CHUNKS_PER_TICK && !QUEUE.isEmpty(); count++) {
            long key = QUEUE.poll();
            scan(minecraft.level, ChunkPos.getX(key), ChunkPos.getZ(key));
        }
        if (ticks % SAVE_INTERVAL_TICKS == 0) {
            saveDirty();
        }
    }

    public static void reset() {
        saveDirty();
        for (Tile tile : TILES.values()) {
            release(tile);
        }
        TILES.clear();
        MISSING.clear();
        SCANNED.clear();
        QUEUE.clear();
        worldKey = "";
        dimensionKey = "";
        directory = null;
    }

    public static boolean isAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && !minecraft.level.dimensionType().hasCeiling() && directory != null;
    }

    public static void beginFrame() {
        loadBudget = 8;
    }

    public static ResourceLocation texture(int tileX, int tileZ) {
        Tile tile = tile(tileX, tileZ, false);
        if (tile == null) {
            return null;
        }
        if (tile.texture == null) {
            tile.texture = new DynamicTexture(tile.image);
            tile.textureId = BPhone.id("mapcache/t" + (textureSerial++));
            Minecraft.getInstance().getTextureManager().register(tile.textureId, tile.texture);
            tile.textureDirty = false;
        } else if (tile.textureDirty) {
            tile.texture.upload();
            tile.textureDirty = false;
        }
        return tile.textureId;
    }

    public static ResourceLocation cityTexture(int tileX, int tileZ) {
        Tile tile = tile(tileX, tileZ, false);
        if (tile == null) {
            return null;
        }
        if (tile.cityTexture == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, TILE_SIZE, TILE_SIZE, false);
            recolor(tile.image, image);
            tile.cityTexture = new DynamicTexture(image);
            tile.cityTextureId = BPhone.id("mapcache/c" + (textureSerial++));
            Minecraft.getInstance().getTextureManager().register(tile.cityTextureId, tile.cityTexture);
            tile.cityDirty = false;
        } else if (tile.cityDirty) {
            recolor(tile.image, tile.cityTexture.getPixels());
            tile.cityTexture.upload();
            tile.cityDirty = false;
        }
        return tile.cityTextureId;
    }

    private static void recolor(NativeImage source, NativeImage target) {
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                target.setPixelRGBA(x, y, cityPixel(source.getPixelRGBA(x, y)));
            }
        }
    }

    private static int cityPixel(int abgr) {
        if ((abgr >>> 24) == 0) {
            return 0;
        }
        int red = abgr & 0xFF;
        int green = (abgr >> 8) & 0xFF;
        int blue = (abgr >> 16) & 0xFF;
        if (Math.abs(red - green) <= 2 && blue > 100 && blue >= red * 3) {
            float shade = 0.75F + 0.35F * blue / 255.0F;
            return abgr(56.0F * shade, 106.0F * shade, 110.0F * shade);
        }
        float light = (red * 0.30F + green * 0.59F + blue * 0.11F) / 255.0F;
        return abgr(11.0F + 12.0F * light, 25.0F + 14.0F * light, 29.0F + 14.0F * light);
    }

    private static int abgr(float red, float green, float blue) {
        int r = Math.min(255, Math.round(red));
        int g = Math.min(255, Math.round(green));
        int b = Math.min(255, Math.round(blue));
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static void refill(Minecraft minecraft) {
        ChunkPos center = minecraft.player.chunkPosition();
        int radius = Math.min(16, minecraft.options.getEffectiveRenderDistance());
        long now = System.currentTimeMillis();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    long key = ChunkPos.asLong(chunkX, chunkZ);
                    Long last = SCANNED.get(key);
                    if (last != null && now - last < RESCAN_MILLIS) {
                        continue;
                    }
                    if (minecraft.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) {
                        QUEUE.add(key);
                    }
                }
            }
        }
    }

    private static void scan(ClientLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        SCANNED.put(ChunkPos.asLong(chunkX, chunkZ), System.currentTimeMillis());
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        Tile tile = tile(Math.floorDiv(baseX, TILE_SIZE), Math.floorDiv(baseZ, TILE_SIZE), true);
        int offsetX = baseX - tile.tileX * TILE_SIZE;
        int offsetZ = baseZ - tile.tileZ * TILE_SIZE;
        int minY = level.getMinBuildHeight();
        int[] northHeights = new int[16];
        for (int dx = 0; dx < 16; dx++) {
            int neighbour = level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + dx, baseZ - 1) - 1;
            northHeights[dx] = neighbour < minY ? Integer.MIN_VALUE : neighbour;
        }
        boolean changed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                BlockState state = null;
                MapColor color = MapColor.NONE;
                while (y >= minY) {
                    state = chunk.getBlockState(CURSOR.set(x, y, z));
                    color = state.getMapColor(level, CURSOR);
                    if (color != MapColor.NONE) {
                        break;
                    }
                    y--;
                }
                int pixel = 0;
                if (color != MapColor.NONE && state != null) {
                    MapColor.Brightness brightness;
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        int depth = 0;
                        int waterY = y;
                        while (waterY > minY && depth < 12) {
                            waterY--;
                            depth++;
                            if (!chunk.getBlockState(CURSOR.set(x, waterY, z)).getFluidState().is(FluidTags.WATER)) {
                                break;
                            }
                        }
                        double shade = depth * 0.1D + ((x + z) & 1) * 0.2D;
                        brightness = shade < 0.5D ? MapColor.Brightness.HIGH
                                : shade > 0.9D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    } else {
                        int north = northHeights[dx] == Integer.MIN_VALUE ? y : northHeights[dx];
                        double slope = (y - north) * 0.8D + (((x + z) & 1) - 0.5D) * 0.4D;
                        brightness = slope > 0.6D ? MapColor.Brightness.HIGH
                                : slope < -0.6D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    }
                    pixel = color.calculateRGBColor(brightness);
                }
                northHeights[dx] = color == MapColor.NONE ? Integer.MIN_VALUE : y;
                if (tile.image.getPixelRGBA(offsetX + dx, offsetZ + dz) != pixel) {
                    tile.image.setPixelRGBA(offsetX + dx, offsetZ + dz, pixel);
                    changed = true;
                }
            }
        }
        if (changed) {
            tile.textureDirty = true;
            tile.cityDirty = true;
            tile.diskDirty = true;
        }
    }

    private static Tile tile(int tileX, int tileZ, boolean create) {
        long key = ChunkPos.asLong(tileX, tileZ);
        Tile tile = TILES.get(key);
        if (tile != null) {
            return tile;
        }
        if (!create && MISSING.contains(key)) {
            return null;
        }
        if (!create) {
            if (loadBudget <= 0) {
                return null;
            }
            loadBudget--;
        }
        NativeImage image = read(tileX, tileZ);
        if (image == null) {
            if (!create) {
                MISSING.add(key);
                return null;
            }
            image = new NativeImage(NativeImage.Format.RGBA, TILE_SIZE, TILE_SIZE, true);
        }
        MISSING.remove(key);
        tile = new Tile(tileX, tileZ, image);
        TILES.put(key, tile);
        evict();
        return tile;
    }

    private static void evict() {
        Iterator<Map.Entry<Long, Tile>> iterator = TILES.entrySet().iterator();
        while (TILES.size() > MAX_LOADED_TILES && iterator.hasNext()) {
            Tile oldest = iterator.next().getValue();
            save(oldest);
            release(oldest);
            iterator.remove();
        }
    }

    private static NativeImage read(int tileX, int tileZ) {
        Path file = file(tileX, tileZ);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(stream);
            if (image.getWidth() == TILE_SIZE && image.getHeight() == TILE_SIZE) {
                return image;
            }
            image.close();
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Failed to read map tile {}", file, exception);
        }
        return null;
    }

    private static void saveDirty() {
        for (Tile tile : new ArrayList<>(TILES.values())) {
            save(tile);
        }
    }

    private static void save(Tile tile) {
        Path file = file(tile.tileX, tile.tileZ);
        if (!tile.diskDirty || file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            tile.image.writeToFile(file);
            tile.diskDirty = false;
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Failed to save map tile {}", file, exception);
        }
    }

    private static void release(Tile tile) {
        if (tile.cityTexture != null) {
            Minecraft.getInstance().getTextureManager().release(tile.cityTextureId);
            tile.cityTexture = null;
        }
        if (tile.texture != null) {
            Minecraft.getInstance().getTextureManager().release(tile.textureId);
            tile.texture = null;
        } else {
            tile.image.close();
        }
    }

    private static Path file(int tileX, int tileZ) {
        return directory == null ? null : directory.resolve(tileX + "_" + tileZ + ".png");
    }

    private static String worldKey(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path folder = root.getFileName();
            return "local_" + sanitize(folder == null ? "world" : folder.toString());
        }
        ServerData server = minecraft.getCurrentServer();
        return server == null ? "unknown" : "server_" + sanitize(server.ip);
    }

    private static String sanitize(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            builder.append(Character.isLetterOrDigit(character) && character < 128 || character == '.' || character == '-'
                    ? character : '_');
        }
        return builder.isEmpty() ? "_" : builder.toString();
    }
}
