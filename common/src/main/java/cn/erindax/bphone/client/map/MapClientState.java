package cn.erindax.bphone.client.map;

import cn.erindax.bphone.map.MapData;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class MapClientState {
    private static volatile List<MapData.Marker> markers = List.of();
    private static volatile List<MapData.Road> roads = List.of();
    private static volatile List<MapData.Area> areas = List.of();
    private static volatile List<MapData.Line> lines = List.of();
    private static volatile long version;
    private static RoadGraph graph;
    private static long graphVersion = -1L;
    private static String graphDimension = "";

    private MapClientState() {
    }

    static void applyMarkers(List<MapData.Marker> value) {
        markers = List.copyOf(value);
        version++;
    }

    static void applyRoads(List<MapData.Road> value) {
        roads = List.copyOf(value);
        version++;
    }

    static void applyAreas(List<MapData.Area> value) {
        areas = List.copyOf(value);
        version++;
    }

    static void applyLines(List<MapData.Line> value) {
        lines = List.copyOf(value);
        version++;
    }

    public static void clear() {
        markers = List.of();
        roads = List.of();
        areas = List.of();
        lines = List.of();
        version++;
        graph = null;
        graphVersion = -1L;
    }

    public static long version() {
        return version;
    }

    public static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().location().toString();
    }

    public static List<MapData.Marker> markers(String dimension) {
        List<MapData.Marker> result = new ArrayList<>();
        for (MapData.Marker marker : markers) {
            if (marker.dimension().equals(dimension)) {
                result.add(marker);
            }
        }
        return result;
    }

    public static List<MapData.Road> roads(String dimension) {
        List<MapData.Road> result = new ArrayList<>();
        for (MapData.Road road : roads) {
            if (road.dimension().equals(dimension)) {
                result.add(road);
            }
        }
        return result;
    }

    public static List<MapData.Area> areas(String dimension) {
        List<MapData.Area> result = new ArrayList<>();
        for (MapData.Area area : areas) {
            if (area.dimension().equals(dimension)) {
                result.add(area);
            }
        }
        return result;
    }

    public static List<MapData.Marker> allMarkers() {
        return markers;
    }

    public static List<MapData.Line> lines() {
        return lines;
    }

    public static MapData.Marker marker(String id) {
        for (MapData.Marker marker : markers) {
            if (marker.id().equals(id)) {
                return marker;
            }
        }
        return null;
    }

    public static MapData.Line line(String id) {
        for (MapData.Line line : lines) {
            if (line.id().equals(id)) {
                return line;
            }
        }
        return null;
    }

    public static List<MapData.Line> linesThrough(String markerId) {
        List<MapData.Line> result = new ArrayList<>();
        for (MapData.Line line : lines) {
            if (line.stations().contains(markerId)) {
                result.add(line);
            }
        }
        return result;
    }

    public static synchronized RoadGraph graph(String dimension) {
        if (graph == null || graphVersion != version || !graphDimension.equals(dimension)) {
            graph = RoadGraph.build(roads(dimension));
            graphVersion = version;
            graphDimension = dimension;
        }
        return graph;
    }
}
