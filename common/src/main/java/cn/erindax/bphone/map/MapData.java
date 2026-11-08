package cn.erindax.bphone.map;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class MapData {
    public static final int MAX_ID_LENGTH = 40;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_NOTE_LENGTH = 80;
    public static final int MAX_DIMENSION_LENGTH = 64;
    public static final int MAX_MARKERS = 800;
    public static final int MAX_ROADS = 500;
    public static final int MAX_AREAS = 300;
    public static final int MAX_LINES = 40;
    public static final int MAX_POINTS = 128;
    public static final int MAX_LINE_STATIONS = 64;

    public static final int MARKER_GENERIC = 0;
    public static final int MARKER_STATION = 1;
    public static final int MARKER_SHOP = 2;
    public static final int MARKER_FOOD = 3;
    public static final int MARKER_HOSPITAL = 4;
    public static final int MARKER_POLICE = 5;
    public static final int MARKER_GOVERNMENT = 6;
    public static final int MARKER_SCHOOL = 7;
    public static final int MARKER_PARK = 8;
    public static final int MARKER_HOTEL = 9;
    public static final int MARKER_BANK = 10;
    public static final int MARKER_LANDMARK = 11;
    public static final int MARKER_LABEL = 12;
    public static final int MARKER_KIND_COUNT = 13;

    public static final int ROAD_STREET = 0;
    public static final int ROAD_AVENUE = 1;
    public static final int ROAD_PATH = 2;
    public static final int ROAD_RAIL = 3;
    public static final int ROAD_KIND_COUNT = 4;

    public static final int AREA_DISTRICT = 0;
    public static final int AREA_PARK = 1;
    public static final int AREA_WATER = 2;
    public static final int AREA_BUILDING = 3;
    public static final int AREA_PLAZA = 4;
    public static final int AREA_RESTRICTED = 5;
    public static final int AREA_KIND_COUNT = 6;

    public static final int[] LINE_COLORS = {
            0xE53935, 0x1E88E5, 0x43A047, 0xFB8C00, 0x8E24AA, 0x00ACC1, 0xF4B400, 0x6D4C41, 0xD81B60, 0x546E7A
    };

    public static final String[] MARKER_KIND_NAMES = {
            "generic", "station", "shop", "food", "hospital", "police", "government", "school", "park", "hotel", "bank",
            "landmark", "label"
    };
    public static final String[] ROAD_KIND_NAMES = {"street", "avenue", "path", "rail"};
    public static final String[] AREA_KIND_NAMES = {"district", "park", "water", "building", "plaza", "restricted"};

    public static int kindByName(String[] names, String name) {
        for (int index = 0; index < names.length; index++) {
            if (names[index].equalsIgnoreCase(name)) {
                return index;
            }
        }
        return 0;
    }

    public record Point(int x, int z) {
    }

    public record Marker(String id, String dimension, String name, int kind, int x, int y, int z, String note) {
    }

    public record Road(String id, String dimension, String name, int kind, List<Point> points) {
    }

    public record Area(String id, String dimension, String name, int kind, List<Point> points) {
    }

    public record Line(String id, String name, int color, List<String> stations) {
    }

    private MapData() {
    }

    public static String clip(String value, int max) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        StringBuilder builder = new StringBuilder(text.length());
        text.codePoints().filter(point -> !Character.isISOControl(point)).forEach(builder::appendCodePoint);
        String clean = builder.toString();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    public static int clampKind(int kind, int count) {
        return kind < 0 || kind >= count ? 0 : kind;
    }

    public static void writeMarker(FriendlyByteBuf buf, Marker marker) {
        buf.writeUtf(marker.id(), MAX_ID_LENGTH);
        buf.writeUtf(marker.dimension(), MAX_DIMENSION_LENGTH);
        buf.writeUtf(marker.name(), MAX_NAME_LENGTH);
        buf.writeByte(marker.kind());
        buf.writeVarInt(marker.x());
        buf.writeVarInt(marker.y());
        buf.writeVarInt(marker.z());
        buf.writeUtf(marker.note(), MAX_NOTE_LENGTH);
    }

    public static Marker readMarker(FriendlyByteBuf buf) {
        return new Marker(buf.readUtf(MAX_ID_LENGTH), buf.readUtf(MAX_DIMENSION_LENGTH), buf.readUtf(MAX_NAME_LENGTH),
                clampKind(buf.readByte(), MARKER_KIND_COUNT), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(MAX_NOTE_LENGTH));
    }

    public static void writeRoad(FriendlyByteBuf buf, Road road) {
        buf.writeUtf(road.id(), MAX_ID_LENGTH);
        buf.writeUtf(road.dimension(), MAX_DIMENSION_LENGTH);
        buf.writeUtf(road.name(), MAX_NAME_LENGTH);
        buf.writeByte(road.kind());
        writePoints(buf, road.points());
    }

    public static Road readRoad(FriendlyByteBuf buf) {
        return new Road(buf.readUtf(MAX_ID_LENGTH), buf.readUtf(MAX_DIMENSION_LENGTH), buf.readUtf(MAX_NAME_LENGTH),
                clampKind(buf.readByte(), ROAD_KIND_COUNT), readPoints(buf));
    }

    public static void writeArea(FriendlyByteBuf buf, Area area) {
        buf.writeUtf(area.id(), MAX_ID_LENGTH);
        buf.writeUtf(area.dimension(), MAX_DIMENSION_LENGTH);
        buf.writeUtf(area.name(), MAX_NAME_LENGTH);
        buf.writeByte(area.kind());
        writePoints(buf, area.points());
    }

    public static Area readArea(FriendlyByteBuf buf) {
        return new Area(buf.readUtf(MAX_ID_LENGTH), buf.readUtf(MAX_DIMENSION_LENGTH), buf.readUtf(MAX_NAME_LENGTH),
                clampKind(buf.readByte(), AREA_KIND_COUNT), readPoints(buf));
    }

    public static void writeLine(FriendlyByteBuf buf, Line line) {
        buf.writeUtf(line.id(), MAX_ID_LENGTH);
        buf.writeUtf(line.name(), MAX_NAME_LENGTH);
        buf.writeInt(line.color());
        List<String> stations = line.stations().size() > MAX_LINE_STATIONS
                ? line.stations().subList(0, MAX_LINE_STATIONS) : line.stations();
        buf.writeVarInt(stations.size());
        for (String station : stations) {
            buf.writeUtf(station, MAX_ID_LENGTH);
        }
    }

    public static Line readLine(FriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_ID_LENGTH);
        String name = buf.readUtf(MAX_NAME_LENGTH);
        int color = buf.readInt() & 0xFFFFFF;
        int count = Math.min(buf.readVarInt(), MAX_LINE_STATIONS);
        List<String> stations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            stations.add(buf.readUtf(MAX_ID_LENGTH));
        }
        return new Line(id, name, color, List.copyOf(stations));
    }

    public static void writePoints(FriendlyByteBuf buf, List<Point> points) {
        List<Point> safe = points.size() > MAX_POINTS ? points.subList(0, MAX_POINTS) : points;
        buf.writeVarInt(safe.size());
        for (Point point : safe) {
            buf.writeVarInt(point.x());
            buf.writeVarInt(point.z());
        }
    }

    public static List<Point> readPoints(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_POINTS);
        List<Point> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(new Point(buf.readVarInt(), buf.readVarInt()));
        }
        return List.copyOf(points);
    }
}
