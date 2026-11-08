package cn.erindax.bphone.map;

import cn.erindax.bphone.BPhone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class MapStore {
    private static final String FILE_NAME = "bphone_map.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final List<MapData.Marker> MARKERS = new ArrayList<>();
    private static final List<MapData.Road> ROADS = new ArrayList<>();
    private static final List<MapData.Area> AREAS = new ArrayList<>();
    private static final List<MapData.Line> LINES = new ArrayList<>();
    private static Path file;
    private static boolean dirty;
    private static long revision;

    private MapStore() {
    }

    public static synchronized void open(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        dirty = false;
        load();
    }

    public static synchronized void close() {
        saveIfDirty();
        clear();
        file = null;
    }

    public static synchronized boolean isOpen() {
        return file != null;
    }

    public static synchronized boolean reload() {
        if (file == null) {
            return false;
        }
        saveIfDirty();
        return load();
    }

    public static synchronized long revision() {
        return revision;
    }

    public static synchronized List<MapData.Marker> markers() {
        return List.copyOf(MARKERS);
    }

    public static synchronized List<MapData.Road> roads() {
        return List.copyOf(ROADS);
    }

    public static synchronized List<MapData.Area> areas() {
        return List.copyOf(AREAS);
    }

    public static synchronized List<MapData.Line> lines() {
        return List.copyOf(LINES);
    }

    public static synchronized MapData.Marker findMarker(String id) {
        for (MapData.Marker marker : MARKERS) {
            if (marker.id().equals(id)) {
                return marker;
            }
        }
        return null;
    }

    public static synchronized List<MapData.Marker> findMarkersByName(String name) {
        List<MapData.Marker> found = new ArrayList<>();
        for (MapData.Marker marker : MARKERS) {
            if (marker.name().equals(name) || marker.id().equals(name)) {
                found.add(marker);
            }
        }
        return found;
    }

    public static synchronized boolean upsertMarker(MapData.Marker marker) {
        return upsert(MARKERS, marker, marker.id(), MapData.Marker::id, MapData.MAX_MARKERS);
    }

    public static synchronized boolean upsertRoad(MapData.Road road) {
        return road.points().size() >= 2 && upsert(ROADS, road, road.id(), MapData.Road::id, MapData.MAX_ROADS);
    }

    public static synchronized boolean upsertArea(MapData.Area area) {
        return area.points().size() >= 3 && upsert(AREAS, area, area.id(), MapData.Area::id, MapData.MAX_AREAS);
    }

    public static synchronized boolean upsertLine(MapData.Line line) {
        return upsert(LINES, line, line.id(), MapData.Line::id, MapData.MAX_LINES);
    }

    public static synchronized boolean deleteMarker(String id) {
        boolean removed = MARKERS.removeIf(marker -> marker.id().equals(id));
        if (removed) {
            for (int index = 0; index < LINES.size(); index++) {
                MapData.Line line = LINES.get(index);
                if (line.stations().contains(id)) {
                    List<String> stations = new ArrayList<>(line.stations());
                    stations.removeIf(id::equals);
                    LINES.set(index, new MapData.Line(line.id(), line.name(), line.color(), List.copyOf(stations)));
                }
            }
            changed();
        }
        return removed;
    }

    public static synchronized boolean deleteRoad(String id) {
        return remove(ROADS, road -> road.id().equals(id));
    }

    public static synchronized boolean deleteArea(String id) {
        return remove(AREAS, area -> area.id().equals(id));
    }

    public static synchronized boolean deleteLine(String id) {
        return remove(LINES, line -> line.id().equals(id));
    }

    public static String newId(String prefix) {
        return prefix + "_" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36);
    }

    public static synchronized void saveIfDirty() {
        if (!dirty || file == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            JsonArray markers = new JsonArray();
            for (MapData.Marker marker : MARKERS) {
                JsonObject object = new JsonObject();
                object.addProperty("id", marker.id());
                object.addProperty("dimension", marker.dimension());
                object.addProperty("name", marker.name());
                object.addProperty("kind", MapData.MARKER_KIND_NAMES[marker.kind()]);
                object.addProperty("x", marker.x());
                object.addProperty("y", marker.y());
                object.addProperty("z", marker.z());
                if (!marker.note().isEmpty()) {
                    object.addProperty("note", marker.note());
                }
                markers.add(object);
            }
            root.add("markers", markers);
            JsonArray roads = new JsonArray();
            for (MapData.Road road : ROADS) {
                roads.add(shape(road.id(), road.dimension(), road.name(), MapData.ROAD_KIND_NAMES[road.kind()], road.points()));
            }
            root.add("roads", roads);
            JsonArray areas = new JsonArray();
            for (MapData.Area area : AREAS) {
                areas.add(shape(area.id(), area.dimension(), area.name(), MapData.AREA_KIND_NAMES[area.kind()], area.points()));
            }
            root.add("areas", areas);
            JsonArray lines = new JsonArray();
            for (MapData.Line line : LINES) {
                JsonObject object = new JsonObject();
                object.addProperty("id", line.id());
                object.addProperty("name", line.name());
                object.addProperty("color", String.format(Locale.ROOT, "#%06X", line.color() & 0xFFFFFF));
                JsonArray stations = new JsonArray();
                line.stations().forEach(stations::add);
                object.add("stations", stations);
                lines.add(object);
            }
            root.add("lines", lines);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to save map data to {}", file, exception);
        }
    }

    private static JsonObject shape(String id, String dimension, String name, String kind, List<MapData.Point> points) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("dimension", dimension);
        object.addProperty("name", name);
        object.addProperty("kind", kind);
        JsonArray array = new JsonArray();
        for (MapData.Point point : points) {
            JsonArray pair = new JsonArray();
            pair.add(point.x());
            pair.add(point.z());
            array.add(pair);
        }
        object.add("points", array);
        return object;
    }

    private static boolean load() {
        if (file == null || !Files.exists(file)) {
            clear();
            revision++;
            return true;
        }
        List<MapData.Marker> markers = new ArrayList<>();
        List<MapData.Road> roads = new ArrayList<>();
        List<MapData.Area> areas = new ArrayList<>();
        List<MapData.Line> lines = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "markers", new JsonArray())) {
                if (element.isJsonObject() && markers.size() < MapData.MAX_MARKERS) {
                    JsonObject object = element.getAsJsonObject();
                    markers.add(new MapData.Marker(id(object, "m"), dimension(object),
                            MapData.clip(GsonHelper.getAsString(object, "name", ""), MapData.MAX_NAME_LENGTH),
                            MapData.kindByName(MapData.MARKER_KIND_NAMES, GsonHelper.getAsString(object, "kind", "")),
                            GsonHelper.getAsInt(object, "x", 0), GsonHelper.getAsInt(object, "y", 64),
                            GsonHelper.getAsInt(object, "z", 0),
                            MapData.clip(GsonHelper.getAsString(object, "note", ""), MapData.MAX_NOTE_LENGTH)));
                }
            }
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "roads", new JsonArray())) {
                if (element.isJsonObject() && roads.size() < MapData.MAX_ROADS) {
                    JsonObject object = element.getAsJsonObject();
                    List<MapData.Point> points = points(object);
                    if (points.size() >= 2) {
                        roads.add(new MapData.Road(id(object, "r"), dimension(object),
                                MapData.clip(GsonHelper.getAsString(object, "name", ""), MapData.MAX_NAME_LENGTH),
                                MapData.kindByName(MapData.ROAD_KIND_NAMES, GsonHelper.getAsString(object, "kind", "")),
                                points));
                    }
                }
            }
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "areas", new JsonArray())) {
                if (element.isJsonObject() && areas.size() < MapData.MAX_AREAS) {
                    JsonObject object = element.getAsJsonObject();
                    List<MapData.Point> points = points(object);
                    if (points.size() >= 3) {
                        areas.add(new MapData.Area(id(object, "a"), dimension(object),
                                MapData.clip(GsonHelper.getAsString(object, "name", ""), MapData.MAX_NAME_LENGTH),
                                MapData.kindByName(MapData.AREA_KIND_NAMES, GsonHelper.getAsString(object, "kind", "")),
                                points));
                    }
                }
            }
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "lines", new JsonArray())) {
                if (element.isJsonObject() && lines.size() < MapData.MAX_LINES) {
                    JsonObject object = element.getAsJsonObject();
                    List<String> stations = new ArrayList<>();
                    for (JsonElement station : GsonHelper.getAsJsonArray(object, "stations", new JsonArray())) {
                        if (station.isJsonPrimitive() && stations.size() < MapData.MAX_LINE_STATIONS) {
                            stations.add(MapData.clip(station.getAsString(), MapData.MAX_ID_LENGTH));
                        }
                    }
                    lines.add(new MapData.Line(id(object, "l"),
                            MapData.clip(GsonHelper.getAsString(object, "name", ""), MapData.MAX_NAME_LENGTH),
                            parseColor(GsonHelper.getAsString(object, "color", "#E53935")), List.copyOf(stations)));
                }
            }
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to load map data from {}, keeping the previous map", file, exception);
            try {
                Files.copy(file, file.resolveSibling(FILE_NAME + ".broken-" + System.currentTimeMillis()));
            } catch (Exception copyException) {
                BPhone.LOGGER.warn("[BPhone] Failed to back up the unreadable map file", copyException);
            }
            return false;
        }
        clear();
        MARKERS.addAll(markers);
        ROADS.addAll(roads);
        AREAS.addAll(areas);
        LINES.addAll(lines);
        revision++;
        BPhone.LOGGER.info("[BPhone] Loaded map: {} markers, {} roads, {} areas, {} lines",
                MARKERS.size(), ROADS.size(), AREAS.size(), LINES.size());
        return true;
    }

    private static String id(JsonObject object, String prefix) {
        String id = MapData.clip(GsonHelper.getAsString(object, "id", ""), MapData.MAX_ID_LENGTH);
        return id.isEmpty() ? newId(prefix) : id;
    }

    private static String dimension(JsonObject object) {
        return MapData.clip(GsonHelper.getAsString(object, "dimension", "minecraft:overworld"), MapData.MAX_DIMENSION_LENGTH);
    }

    private static List<MapData.Point> points(JsonObject object) {
        List<MapData.Point> points = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(object, "points", new JsonArray())) {
            if (element.isJsonArray() && element.getAsJsonArray().size() >= 2 && points.size() < MapData.MAX_POINTS) {
                JsonArray pair = element.getAsJsonArray();
                points.add(new MapData.Point(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
            }
        }
        return List.copyOf(points);
    }

    private static int parseColor(String raw) {
        try {
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            return MapData.LINE_COLORS[0];
        }
    }

    private static void clear() {
        MARKERS.clear();
        ROADS.clear();
        AREAS.clear();
        LINES.clear();
    }

    private static void changed() {
        dirty = true;
        revision++;
    }

    private static <T> boolean upsert(List<T> list, T value, String id, java.util.function.Function<T, String> idOf, int limit) {
        for (int index = 0; index < list.size(); index++) {
            if (idOf.apply(list.get(index)).equals(id)) {
                list.set(index, value);
                changed();
                return true;
            }
        }
        if (list.size() >= limit) {
            return false;
        }
        list.add(value);
        changed();
        return true;
    }

    private static <T> boolean remove(List<T> list, Predicate<T> filter) {
        boolean removed = list.removeIf(filter);
        if (removed) {
            changed();
        }
        return removed;
    }
}
