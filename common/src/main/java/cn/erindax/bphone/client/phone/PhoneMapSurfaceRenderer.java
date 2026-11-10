package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.client.map.MapClientState;
import cn.erindax.bphone.client.map.MapDraw;
import cn.erindax.bphone.client.map.MapNavigation;
import cn.erindax.bphone.client.map.MapTerrainCache;
import cn.erindax.bphone.map.MapData;
import cn.erindax.bphone.map.PhoneMapPins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.erindax.bphone.client.phone.PhoneUi.*;

final class PhoneMapSurfaceRenderer {
    private static final int[] MARKER_COLORS = {
            0xFF607D8B, 0xFF1E88E5, 0xFFFB8C00, 0xFFF4511E, 0xFFE53935, 0xFF3949AB, 0xFF6D4C41, 0xFF8E24AA,
            0xFF43A047, 0xFF00897B, 0xFFF9A825, 0xFFD81B60, 0xFF455A64
    };
    private static final int PIN_COLOR = 0xFFEA4335;
    private static final int PLAYER_COLOR = 0xFF1A73E8;
    private static final int ROUTE_COLOR = 0xFF1A73E8;
    private static final float[] ROAD_BLOCKS = {5.0F, 9.0F, 2.5F, 2.0F};
    private static final float[] ROAD_MIN_PX = {2.2F, 3.2F, 1.2F, 1.4F};
    private static final int BUTTON_LAYERS = 0;
    private static final int BUTTON_LOCATE = 1;
    private static final int BUTTON_ZOOM_IN = 2;
    private static final int BUTTON_ZOOM_OUT = 3;
    private static final int BUTTON_EDIT = 4;
    private static final double CLICK_SLOP = 3.0D;
    private static final Map<String, int[]> TRIANGLES = new HashMap<>();
    private static long triangleVersion = -1L;

    private PhoneMapSurfaceRenderer() {
    }

    record View(UiRect viewport, double centerX, double centerZ, float zoom) {
        float sx(double x) {
            return viewport.left + viewport.width / 2.0F + (float) ((x - centerX) * zoom);
        }

        float sy(double z) {
            return viewport.top + viewport.height / 2.0F + (float) ((z - centerZ) * zoom);
        }

        double wx(double screenX) {
            return centerX + (screenX - (viewport.left + viewport.width / 2.0D)) / zoom;
        }

        double wz(double screenY) {
            return centerZ + (screenY - (viewport.top + viewport.height / 2.0D)) / zoom;
        }

        boolean near(float x, float y, float margin) {
            return x >= viewport.left - margin && x <= viewport.right() + margin
                    && y >= viewport.top - margin && y <= viewport.bottom() + margin;
        }
    }

    private record Label(FormattedCharSequence text, float x, float y, float scale, int color, int priority) {
    }

    static void render(PhoneScreen screen, GuiGraphics graphics) {
        PhoneMapApp app = screen.map;
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        UiRect surface = layout.surface;
        graphics.fill(surface.left, surface.top, surface.right(), surface.bottom(), PAGE);
        switch (app.page) {
            case PhoneMapApp.PAGE_SEARCH -> renderSearch(screen, app, graphics, layout);
            case PhoneMapApp.PAGE_LINE -> renderLine(screen, app, graphics, layout);
            case PhoneMapApp.PAGE_FORM -> renderForm(screen, app, graphics, layout);
            case PhoneMapApp.PAGE_LINE_FORM -> renderLineForm(screen, app, graphics, layout);
            default -> renderMap(screen, app, graphics, layout);
        }
    }

    static boolean click(PhoneScreen screen, double mouseX, double mouseY, int button) {
        PhoneMapApp app = screen.map;
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        switch (app.page) {
            case PhoneMapApp.PAGE_SEARCH -> clickSearch(screen, app, layout, mouseX, mouseY, button);
            case PhoneMapApp.PAGE_LINE -> clickLine(app, layout, mouseX, mouseY, button);
            case PhoneMapApp.PAGE_FORM -> clickForm(app, layout, mouseX, mouseY, button);
            case PhoneMapApp.PAGE_LINE_FORM -> clickLineForm(app, layout, mouseX, mouseY, button);
            default -> clickMap(screen, app, layout, mouseX, mouseY, button);
        }
        return true;
    }

    static void drag(PhoneScreen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
        PhoneMapApp app = screen.map;
        if (app.page != PhoneMapApp.PAGE_MAP || !app.pressing || button != app.pressButton || button != 0) {
            return;
        }
        if (!app.dragged && Math.hypot(mouseX - app.pressX, mouseY - app.pressY) > CLICK_SLOP) {
            app.dragged = true;
            app.pan(mouseX - app.pressX, mouseY - app.pressY);
            return;
        }
        if (app.dragged) {
            app.pan(dragX, dragY);
        }
    }

    static void release(PhoneScreen screen, double mouseX, double mouseY, int button) {
        PhoneMapApp app = screen.map;
        if (!app.pressing || button != app.pressButton) {
            return;
        }
        app.pressing = false;
        if (!app.dragged && app.page == PhoneMapApp.PAGE_MAP) {
            tap(screen, app, new PhoneChatLayout(screen), mouseX, mouseY, button);
        }
    }

    static void scroll(PhoneScreen screen, double mouseX, double mouseY, double scrollY) {
        PhoneMapApp app = screen.map;
        PhoneChatLayout layout = new PhoneChatLayout(screen);
        int delta = -(int) Math.signum(scrollY) * layout.scrollStep;
        switch (app.page) {
            case PhoneMapApp.PAGE_SEARCH -> app.searchScroll = Math.max(0, app.searchScroll + delta);
            case PhoneMapApp.PAGE_LINE -> app.lineScroll = Math.max(0, app.lineScroll + delta);
            case PhoneMapApp.PAGE_LINE_FORM -> app.lineFormScroll = Math.max(0, app.lineFormScroll + delta);
            case PhoneMapApp.PAGE_MAP -> {
                UiRect viewport = mapViewport(layout);
                if (viewport.contains(mouseX, mouseY)) {
                    app.zoomAround(scrollY > 0 ? 1.25F : 0.8F, mouseX, mouseY, viewport);
                }
            }
            default -> {
            }
        }
    }

    static UiRect mapViewport(PhoneChatLayout layout) {
        int top = layout.header().bottom();
        return new UiRect(layout.surface.left, top, layout.surface.width, Math.max(1, layout.surface.bottom() - top));
    }

    private static View view(PhoneMapApp app, PhoneChatLayout layout) {
        return new View(mapViewport(layout), app.centerX, app.centerZ, app.zoom);
    }

    private static void renderMap(PhoneScreen screen, PhoneMapApp app, GuiGraphics graphics, PhoneChatLayout layout) {
        UiRect header = layout.header();
        renderHeader(screen, graphics, layout, header, Component.translatable("map.bphone.title"), true, false);
        renderSearchButton(screen, graphics, layout, layout.actionButton(header));

        UiRect viewport = mapViewport(layout);
        View view = view(app, layout);
        String dimension = app.dimension();
        boolean dark = isDark();
        boolean terrain = app.style == PhoneMapApp.STYLE_TERRAIN && MapTerrainCache.isAvailable();

        graphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        MapDraw draw = new MapDraw(graphics);
        int background = terrain ? (dark ? 0xFF2A2D33 : 0xFFD9D5CE) : (dark ? 0xFF1E2126 : 0xFFF1EEE8);
        draw.rect(viewport.left, viewport.top, viewport.right(), viewport.bottom(), background);
        draw.flush();
        if (terrain) {
            renderTerrain(draw, view);
        }
        List<MapData.Area> areas = MapClientState.areas(dimension);
        List<MapData.Road> roads = MapClientState.roads(dimension);
        renderAreas(draw, view, areas, dark);
        renderRoads(draw, view, roads, dark);
        renderTransit(draw, view, dimension, layout);
        renderRoute(draw, view, layout);
        renderDraft(screen, draw, view, app, layout);
        draw.flush();

        List<Label> labels = new ArrayList<>();
        renderAreaLabels(draw, view, areas, layout, dark);
        renderRoadLabels(draw, view, roads, layout, dark);
        renderMarkers(screen, graphics, draw, view, app, layout, dimension, labels);
        placeLabels(draw, view, labels, layout, dark);
        renderPlayer(draw, view, layout);
        draw.flush();
        graphics.disableScissor();
        draw.clearDepth();

        renderNorth(graphics, draw, layout, viewport);
        renderScale(graphics, draw, layout, viewport, app, overlayBottom(app, layout, viewport));
        renderFloatingButtons(screen, graphics, draw, app, layout, viewport);
        if (MapNavigation.isActive()) {
            renderNavBanner(screen, graphics, draw, layout, viewport);
        }
        if (app.editing) {
            renderEditBar(screen, graphics, app, layout, viewport);
        } else if (app.selectType != PhoneMapApp.SELECT_NONE) {
            renderSheet(screen, graphics, app, layout, viewport);
        }
        if (app.statusTicks > 0 && !app.status.getString().isEmpty()) {
            renderStatus(graphics, layout, viewport, app, overlayBottom(app, layout, viewport));
        }
    }

    private static void renderTerrain(MapDraw draw, View view) {
        MapTerrainCache.beginFrame();
        int size = MapTerrainCache.TILE_SIZE;
        int minTileX = Math.floorDiv(Mth.floor(view.wx(view.viewport().left)), size);
        int maxTileX = Math.floorDiv(Mth.floor(view.wx(view.viewport().right())), size);
        int minTileZ = Math.floorDiv(Mth.floor(view.wz(view.viewport().top)), size);
        int maxTileZ = Math.floorDiv(Mth.floor(view.wz(view.viewport().bottom())), size);
        if ((maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1) > 160) {
            return;
        }
        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                ResourceLocation texture = MapTerrainCache.texture(tileX, tileZ);
                if (texture != null) {
                    draw.texture(texture, view.sx(tileX * size), view.sy(tileZ * size),
                            view.sx((tileX + 1) * size), view.sy((tileZ + 1) * size), 0.0F, 0.0F, 1.0F, 1.0F);
                }
            }
        }
    }

    private static int[] areaFill(int kind, boolean dark) {
        return switch (kind) {
            case MapData.AREA_PARK -> dark ? new int[] {0x9945704A, 0xFF4E7D51} : new int[] {0xB0A8D5A2, 0xFF8BC383};
            case MapData.AREA_WATER -> dark ? new int[] {0xAA2B4A6B, 0xFF3D6188} : new int[] {0xC09DC8EE, 0xFF7FB2E3};
            case MapData.AREA_BUILDING -> dark ? new int[] {0xC03A3D44, 0xFF4A4E56} : new int[] {0xC8D6D0C8, 0xFFBDB5AA};
            case MapData.AREA_PLAZA -> dark ? new int[] {0x996B6150, 0xFF85785F} : new int[] {0xB0EFE3C8, 0xFFD9C9A5};
            case MapData.AREA_RESTRICTED -> dark ? new int[] {0x806E3B3B, 0xFFB35C5C} : new int[] {0x80F2B8B5, 0xFFE57373};
            default -> dark ? new int[] {0x1A5BB8F5, 0x995BB8F5} : new int[] {0x141E88E5, 0x991E88E5};
        };
    }

    private static int[] triangles(MapData.Area area) {
        if (triangleVersion != MapClientState.version()) {
            TRIANGLES.clear();
            triangleVersion = MapClientState.version();
        }
        return TRIANGLES.computeIfAbsent(area.id(), id -> {
            double[] xs = new double[area.points().size()];
            double[] zs = new double[area.points().size()];
            for (int index = 0; index < xs.length; index++) {
                xs[index] = area.points().get(index).x();
                zs[index] = area.points().get(index).z();
            }
            return MapDraw.triangulate(xs, zs);
        });
    }

    private static void renderAreas(MapDraw draw, View view, List<MapData.Area> areas, boolean dark) {
        for (int pass = 0; pass < 2; pass++) {
            for (MapData.Area area : areas) {
                boolean district = area.kind() == MapData.AREA_DISTRICT;
                if ((pass == 0) != district) {
                    continue;
                }
                int count = area.points().size();
                float[] xs = new float[count];
                float[] ys = new float[count];
                if (!project(view, area.points(), xs, ys)) {
                    continue;
                }
                int[] colors = areaFill(area.kind(), dark);
                draw.fillTriangles(xs, ys, triangles(area), colors[0]);
                draw.outline(xs, ys, count, district || area.kind() == MapData.AREA_RESTRICTED ? 1.2F : 1.0F,
                        colors[1], district || area.kind() == MapData.AREA_RESTRICTED);
            }
        }
    }

    private static boolean project(View view, List<MapData.Point> points, float[] xs, float[] ys) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            xs[index] = view.sx(points.get(index).x() + 0.5D);
            ys[index] = view.sy(points.get(index).z() + 0.5D);
            minX = Math.min(minX, xs[index]);
            minY = Math.min(minY, ys[index]);
            maxX = Math.max(maxX, xs[index]);
            maxY = Math.max(maxY, ys[index]);
        }
        UiRect viewport = view.viewport();
        return maxX >= viewport.left - 8 && minX <= viewport.right() + 8 && maxY >= viewport.top - 8
                && minY <= viewport.bottom() + 8;
    }

    private static float roadWidth(int kind, float zoom) {
        return Mth.clamp(ROAD_BLOCKS[kind] * zoom, ROAD_MIN_PX[kind], 36.0F);
    }

    private static void renderRoads(MapDraw draw, View view, List<MapData.Road> roads, boolean dark) {
        int[] order = {MapData.ROAD_RAIL, MapData.ROAD_PATH, MapData.ROAD_STREET, MapData.ROAD_AVENUE};
        List<float[][]> projected = new ArrayList<>(roads.size());
        for (MapData.Road road : roads) {
            int count = road.points().size();
            float[] xs = new float[count];
            float[] ys = new float[count];
            projected.add(project(view, road.points(), xs, ys) ? new float[][] {xs, ys} : null);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int kind : order) {
                for (int index = 0; index < roads.size(); index++) {
                    MapData.Road road = roads.get(index);
                    float[][] points = projected.get(index);
                    if (road.kind() != kind || points == null) {
                        continue;
                    }
                    float width = roadWidth(kind, view.zoom());
                    int count = road.points().size();
                    if (kind == MapData.ROAD_RAIL) {
                        if (pass == 0) {
                            draw.polyline(points[0], points[1], count, width + 1.0F, dark ? 0xFF8A8F99 : 0xFF7B7F87);
                        } else {
                            for (int segment = 0; segment + 1 < count; segment++) {
                                draw.dashed(points[0][segment], points[1][segment], points[0][segment + 1],
                                        points[1][segment + 1], Math.max(0.8F, width - 1.0F), 3.0F,
                                        dark ? 0xFF2A2D33 : 0xFFFFFFFF);
                            }
                        }
                        continue;
                    }
                    if (pass == 0) {
                        int casing = kind == MapData.ROAD_AVENUE ? (dark ? 0xFF7A6332 : 0xFFE0A946)
                                : (dark ? 0xFF3B3F47 : 0xFFC9C3B8);
                        draw.polyline(points[0], points[1], count, width + Math.max(1.5F, width * 0.35F), casing);
                    } else {
                        int fill = kind == MapData.ROAD_AVENUE ? (dark ? 0xFFA88A4A : 0xFFFFD27F)
                                : (dark ? 0xFF5A5F69 : 0xFFFFFFFF);
                        draw.polyline(points[0], points[1], count, width, fill);
                    }
                }
            }
        }
    }

    private static void renderTransit(MapDraw draw, View view, String dimension, PhoneChatLayout layout) {
        for (MapData.Line line : MapClientState.lines()) {
            List<float[]> points = new ArrayList<>();
            for (String station : line.stations()) {
                MapData.Marker marker = MapClientState.marker(station);
                if (marker != null && marker.dimension().equals(dimension)) {
                    points.add(new float[] {view.sx(marker.x() + 0.5D), view.sy(marker.z() + 0.5D)});
                }
            }
            if (points.size() < 2) {
                continue;
            }
            float[] xs = new float[points.size()];
            float[] ys = new float[points.size()];
            for (int index = 0; index < points.size(); index++) {
                xs[index] = points.get(index)[0];
                ys[index] = points.get(index)[1];
            }
            float width = Math.max(2.0F, layout.u(2.5F));
            draw.polyline(xs, ys, xs.length, width + 2.0F, 0xFFFFFFFF);
            draw.polyline(xs, ys, xs.length, width, 0xFF000000 | line.color());
        }
    }

    private static void renderRoute(MapDraw draw, View view, PhoneChatLayout layout) {
        List<double[]> route = MapNavigation.route();
        LocalPlayer player = PhoneMapApp.player();
        if (!MapNavigation.isActive() || route.size() < 2 || player == null || MapNavigation.isOtherDimension()) {
            return;
        }
        int start = Math.min(MapNavigation.nextIndex(), route.size() - 1);
        int count = route.size() - start + 1;
        float[] xs = new float[count];
        float[] ys = new float[count];
        xs[0] = view.sx(player.getX());
        ys[0] = view.sy(player.getZ());
        for (int index = start; index < route.size(); index++) {
            xs[index - start + 1] = view.sx(route.get(index)[0]);
            ys[index - start + 1] = view.sy(route.get(index)[1]);
        }
        float width = Math.max(2.5F, layout.u(3.0F));
        draw.polyline(xs, ys, count, width + 2.5F, 0xFFFFFFFF);
        draw.polyline(xs, ys, count, width, ROUTE_COLOR);
        draw.disc(xs[count - 1], ys[count - 1], width + 1.5F, 0xFFFFFFFF);
        draw.disc(xs[count - 1], ys[count - 1], width, ROUTE_COLOR);
    }

    private static void renderDraft(PhoneScreen screen, MapDraw draw, View view, PhoneMapApp app, PhoneChatLayout layout) {
        if (app.tool != PhoneMapApp.TOOL_ROAD && app.tool != PhoneMapApp.TOOL_AREA) {
            return;
        }
        int count = app.draft.size();
        float[] xs = new float[count + 1];
        float[] ys = new float[count + 1];
        for (int index = 0; index < count; index++) {
            xs[index] = view.sx(app.draft.get(index).x() + 0.5D);
            ys[index] = view.sy(app.draft.get(index).z() + 0.5D);
        }
        if (app.tool == PhoneMapApp.TOOL_AREA && count >= 3) {
            double[] wx = new double[count];
            double[] wz = new double[count];
            for (int index = 0; index < count; index++) {
                wx[index] = app.draft.get(index).x();
                wz[index] = app.draft.get(index).z();
            }
            draw.fillTriangles(xs, ys, MapDraw.triangulate(wx, wz), 0x401A73E8);
            draw.dashed(xs[count - 1], ys[count - 1], xs[0], ys[0], 1.2F, 3.0F, 0xCC1A73E8);
        }
        if (count >= 2) {
            draw.polyline(xs, ys, count, 2.0F, 0xFF1A73E8);
        }
        if (count >= 1 && hovered(screen, view.viewport())) {
            draw.dashed(xs[count - 1], ys[count - 1], screen.chatMouseX, screen.chatMouseY, 1.0F, 2.5F, 0xAA1A73E8);
        }
        float radius = Math.max(2.0F, layout.u(2.2F));
        for (int index = 0; index < count; index++) {
            draw.disc(xs[index], ys[index], radius + 1.0F, 0xFFFFFFFF);
            draw.disc(xs[index], ys[index], radius, index == count - 1 ? 0xFFF4511E : 0xFF1A73E8);
        }
    }

    private static void renderAreaLabels(MapDraw draw, View view, List<MapData.Area> areas, PhoneChatLayout layout,
                                         boolean dark) {
        for (MapData.Area area : areas) {
            if (area.name().isEmpty()) {
                continue;
            }
            boolean district = area.kind() == MapData.AREA_DISTRICT;
            if (!district && view.zoom() < 0.8F) {
                continue;
            }
            double sumX = 0.0D;
            double sumZ = 0.0D;
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            for (MapData.Point point : area.points()) {
                sumX += point.x() + 0.5D;
                sumZ += point.z() + 0.5D;
                float x = view.sx(point.x() + 0.5D);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
            float x = view.sx(sumX / area.points().size());
            float y = view.sy(sumZ / area.points().size());
            if (!view.near(x, y, 20.0F)) {
                continue;
            }
            float scale = district ? layout.bodyScale : layout.captionScale;
            FormattedCharSequence text = Component.literal(area.name()).getVisualOrderText();
            float width = layout.font.width(text) * scale;
            if (width > (maxX - minX) * 1.2F && !district) {
                continue;
            }
            int color = district ? (dark ? 0xFF9FB8D9 : 0xFF3D5A80) : (dark ? 0xFFD0D4DA : 0xFF4A4F57);
            draw.outlinedText(layout.font, text, x - width / 2.0F, y - layout.font.lineHeight * scale / 2.0F, scale, 0.0F,
                    color, dark ? 0xFF15171A : 0xFFFFFFFF);
        }
    }

    private static void renderRoadLabels(MapDraw draw, View view, List<MapData.Road> roads, PhoneChatLayout layout,
                                         boolean dark) {
        if (view.zoom() < 0.6F) {
            return;
        }
        float scale = layout.captionScale;
        for (MapData.Road road : roads) {
            if (road.name().isEmpty() || road.kind() == MapData.ROAD_RAIL) {
                continue;
            }
            FormattedCharSequence text = Component.literal(road.name()).getVisualOrderText();
            float textWidth = layout.font.width(text) * scale;
            float bestLength = 0.0F;
            float[] best = null;
            List<MapData.Point> points = road.points();
            for (int index = 0; index + 1 < points.size(); index++) {
                float x0 = view.sx(points.get(index).x() + 0.5D);
                float y0 = view.sy(points.get(index).z() + 0.5D);
                float x1 = view.sx(points.get(index + 1).x() + 0.5D);
                float y1 = view.sy(points.get(index + 1).z() + 0.5D);
                float length = (float) Math.hypot(x1 - x0, y1 - y0);
                float midX = (x0 + x1) / 2.0F;
                float midY = (y0 + y1) / 2.0F;
                if (length > bestLength && view.near(midX, midY, -4.0F)) {
                    bestLength = length;
                    best = new float[] {x0, y0, x1, y1};
                }
            }
            if (best == null || bestLength < textWidth + 8.0F) {
                continue;
            }
            float angle = (float) Math.atan2(best[3] - best[1], best[2] - best[0]);
            if (angle > Math.PI / 2.0D) {
                angle -= (float) Math.PI;
            } else if (angle < -Math.PI / 2.0D) {
                angle += (float) Math.PI;
            }
            float midX = (best[0] + best[2]) / 2.0F;
            float midY = (best[1] + best[3]) / 2.0F;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float halfWidth = textWidth / 2.0F;
            float halfHeight = layout.font.lineHeight * scale / 2.0F - 0.5F;
            float originX = midX - cos * halfWidth + sin * halfHeight;
            float originY = midY - sin * halfWidth - cos * halfHeight;
            draw.outlinedText(layout.font, text, originX, originY, scale, angle,
                    dark ? 0xFFE3E6EA : 0xFF3A3F47, dark ? 0xFF1E2126 : 0xFFFFFFFF);
        }
    }

    private static boolean minorAtZoom(int kind, float zoom) {
        if (zoom >= 0.35F) {
            return false;
        }
        return kind != MapData.MARKER_STATION && kind != MapData.MARKER_LANDMARK && kind != MapData.MARKER_GOVERNMENT
                && kind != MapData.MARKER_HOSPITAL && kind != MapData.MARKER_LABEL;
    }

    private static int labelPriority(int kind) {
        return switch (kind) {
            case MapData.MARKER_LABEL -> 4;
            case MapData.MARKER_STATION, MapData.MARKER_LANDMARK -> 3;
            case MapData.MARKER_GOVERNMENT, MapData.MARKER_HOSPITAL, MapData.MARKER_POLICE -> 2;
            default -> 1;
        };
    }

    private static void renderMarkers(PhoneScreen screen, GuiGraphics graphics, MapDraw draw, View view, PhoneMapApp app,
                                      PhoneChatLayout layout, String dimension, List<Label> labels) {
        boolean dark = isDark();
        float radius = Math.max(4.0F, layout.u(5.5F));
        List<float[]> glyphs = new ArrayList<>();
        List<MapData.Marker> glyphMarkers = new ArrayList<>();
        for (MapData.Marker marker : MapClientState.markers(dimension)) {
            float x = view.sx(marker.x() + 0.5D);
            float y = view.sy(marker.z() + 0.5D);
            if (!view.near(x, y, 40.0F)) {
                continue;
            }
            boolean selected = app.selectType == PhoneMapApp.SELECT_MARKER && app.selectId.equals(marker.id());
            if (marker.kind() == MapData.MARKER_LABEL) {
                FormattedCharSequence text = Component.literal(marker.name()).getVisualOrderText();
                float scale = layout.bodyScale;
                float width = layout.font.width(text) * scale;
                labels.add(new Label(text, x - width / 2.0F, y - layout.font.lineHeight * scale / 2.0F, scale,
                        dark ? 0xFFE8EAED : 0xFF2B2F36, 4));
                if (selected || app.editing) {
                    draw.disc(x, y, 2.0F, selected ? ACCENT : 0x991A73E8);
                }
                continue;
            }
            int color = MARKER_COLORS[marker.kind()];
            if (minorAtZoom(marker.kind(), view.zoom()) && !selected) {
                draw.disc(x, y, 2.5F, 0xFFFFFFFF);
                draw.disc(x, y, 1.8F, color);
                continue;
            }
            float r = selected ? radius * 1.3F : radius;
            if (selected) {
                draw.disc(x, y, r + 3.5F, (color & 0x00FFFFFF) | 0x55000000);
            }
            draw.disc(x, y, r + 1.2F, 0xFFFFFFFF);
            draw.disc(x, y, r, color);
            glyphs.add(new float[] {x, y, r});
            glyphMarkers.add(marker);
            boolean showName = selected || view.zoom() >= 0.5F || labelPriority(marker.kind()) >= 3;
            if (showName && !marker.name().isEmpty()) {
                FormattedCharSequence text = Component.literal(marker.name()).getVisualOrderText();
                float scale = layout.captionScale;
                labels.add(new Label(text, x + r + 2.5F, y - layout.font.lineHeight * scale / 2.0F, scale,
                        dark ? 0xFFE8EAED : 0xFF2B2F36, selected ? 9 : labelPriority(marker.kind())));
            }
        }

        float pinRadius = Math.max(3.5F, layout.u(4.5F));
        for (PhoneMapPins.Pin pin : app.pinsHere()) {
            float x = view.sx(pin.x() + 0.5D);
            float y = view.sy(pin.z() + 0.5D);
            if (!view.near(x, y, 30.0F)) {
                continue;
            }
            boolean selected = app.selectType == PhoneMapApp.SELECT_PIN && app.selectId.equals(pin.id());
            drawPin(draw, x, y, selected ? pinRadius * 1.3F : pinRadius, PIN_COLOR);
            if (!pin.name().isEmpty() && (selected || view.zoom() >= 0.5F)) {
                FormattedCharSequence text = Component.literal(pin.name()).getVisualOrderText();
                float scale = layout.captionScale;
                labels.add(new Label(text, x + pinRadius + 2.5F, y - pinRadius * 2.2F, scale, PIN_COLOR, selected ? 9 : 2));
            }
        }
        if (app.selectType == PhoneMapApp.SELECT_POINT && app.selectDimension.equals(dimension)) {
            drawPin(draw, view.sx(app.selectX + 0.5D), view.sy(app.selectZ + 0.5D), pinRadius * 1.3F, ACCENT);
        }
        MapNavigation.Target target = MapNavigation.target();
        if (target != null && target.dimension().equals(dimension)) {
            float x = view.sx(target.x());
            float y = view.sy(target.z());
            if (view.near(x, y, 10.0F)) {
                draw.disc(x, y, Math.max(2.5F, layout.u(3.0F)) + 1.5F, 0xFFFFFFFF);
                draw.disc(x, y, Math.max(2.5F, layout.u(3.0F)), PIN_COLOR);
            }
        }
        draw.flush();

        for (int index = 0; index < glyphs.size(); index++) {
            float[] glyph = glyphs.get(index);
            MapData.Marker marker = glyphMarkers.get(index);
            Component text = Component.translatable("map.bphone.glyph." + MapData.MARKER_KIND_NAMES[marker.kind()]);
            float scale = Math.min(layout.captionScale, glyph[2] * 1.5F / 8.0F);
            int width = Math.round(layout.font.width(text) * scale);
            int height = Math.round(8.0F * scale);
            drawText(graphics, layout, text, Math.round(glyph[0] - width / 2.0F), Math.round(glyph[1] - height / 2.0F),
                    0xFFFFFFFF, scale);
        }
    }

    private static void drawPin(MapDraw draw, float x, float y, float radius, int color) {
        float headY = y - radius * 1.7F;
        draw.triangle(x - radius * 0.75F, headY + radius * 0.45F, x, y, x + radius * 0.75F, headY + radius * 0.45F, 0xFFFFFFFF);
        draw.disc(x, headY, radius + 1.0F, 0xFFFFFFFF);
        draw.triangle(x - radius * 0.6F, headY + radius * 0.5F, x, y - 0.8F, x + radius * 0.6F, headY + radius * 0.5F, color);
        draw.disc(x, headY, radius, color);
        draw.disc(x, headY, radius * 0.38F, 0xFFFFFFFF);
    }

    private static void placeLabels(MapDraw draw, View view, List<Label> labels, PhoneChatLayout layout, boolean dark) {
        labels.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        List<float[]> placed = new ArrayList<>();
        for (Label label : labels) {
            float width = layout.font.width(label.text()) * label.scale();
            float height = layout.font.lineHeight * label.scale();
            float[] box = {label.x() - 1.0F, label.y() - 1.0F, label.x() + width + 1.0F, label.y() + height + 1.0F};
            if (!view.near(box[0], box[1], 0.0F) && !view.near(box[2], box[3], 0.0F)) {
                continue;
            }
            boolean overlaps = false;
            for (float[] other : placed) {
                if (box[0] < other[2] && box[2] > other[0] && box[1] < other[3] && box[3] > other[1]) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps && label.priority() < 9) {
                continue;
            }
            placed.add(box);
            draw.outlinedText(layout.font, label.text(), label.x(), label.y(), label.scale(), 0.0F, label.color(),
                    dark ? 0xFF15171A : 0xFFFFFFFF);
        }
    }

    private static void renderPlayer(MapDraw draw, View view, PhoneChatLayout layout) {
        LocalPlayer player = PhoneMapApp.player();
        if (player == null) {
            return;
        }
        float x = view.sx(player.getX());
        float y = view.sy(player.getZ());
        if (!view.near(x, y, 12.0F)) {
            return;
        }
        float size = Math.max(4.0F, layout.u(5.0F));
        float yaw = (float) Math.toRadians(player.getYRot());
        float angle = (float) Math.atan2(Math.cos(yaw), -Math.sin(yaw));
        draw.disc(x, y, size * 2.0F, 0x2A1A73E8);
        MapNavigation.arrow(draw, x, y, angle, size + 1.6F, 0xFFFFFFFF);
        MapNavigation.arrow(draw, x, y, angle, size, PLAYER_COLOR);
    }

    private static int floatingSize(PhoneChatLayout layout) {
        return Math.max(12, layout.u(17));
    }

    private static UiRect navBanner(PhoneChatLayout layout, UiRect viewport) {
        int height = layout.bodyHeight + layout.textGap + layout.captionHeight + 2 * layout.u(5);
        return new UiRect(viewport.left + layout.pad, viewport.top + layout.pad, viewport.width - 2 * layout.pad, height);
    }

    private static UiRect navStopButton(PhoneChatLayout layout, UiRect banner) {
        Component label = Component.translatable("map.bphone.nav.stop");
        int width = layout.buttonWidth(label);
        return new UiRect(banner.right() - layout.u(5) - width, banner.top + (banner.height - layout.smallButtonHeight) / 2,
                width, layout.smallButtonHeight);
    }

    private static int[] floatingButtons(PhoneMapApp app) {
        return app.canEdit()
                ? new int[] {BUTTON_LAYERS, BUTTON_LOCATE, BUTTON_ZOOM_IN, BUTTON_ZOOM_OUT, BUTTON_EDIT}
                : new int[] {BUTTON_LAYERS, BUTTON_LOCATE, BUTTON_ZOOM_IN, BUTTON_ZOOM_OUT};
    }

    private static UiRect floatingButton(PhoneChatLayout layout, UiRect viewport, int slot) {
        int size = floatingSize(layout);
        int top = viewport.top + layout.pad;
        if (MapNavigation.isActive()) {
            top = navBanner(layout, viewport).bottom() + layout.gap;
        }
        return new UiRect(viewport.right() - layout.pad - size, top + slot * (size + layout.gap), size, size);
    }

    private static void renderFloatingButtons(PhoneScreen screen, GuiGraphics graphics, MapDraw draw, PhoneMapApp app,
                                              PhoneChatLayout layout, UiRect viewport) {
        int[] buttons = floatingButtons(app);
        for (int slot = 0; slot < buttons.length; slot++) {
            UiRect rect = floatingButton(layout, viewport, slot);
            int type = buttons[slot];
            boolean active = (type == BUTTON_EDIT && app.editing) || (type == BUTTON_LOCATE && app.follow)
                    || (type == BUTTON_LAYERS && app.style == PhoneMapApp.STYLE_PLAIN);
            fillRounded(graphics, rect.left - 1, rect.top - 1, rect.width + 2, rect.height + 2, layout.radius + 1, HAIRLINE);
            fillRounded(graphics, rect.left, rect.top, rect.width, rect.height, layout.radius,
                    active ? ACCENT : hovered(screen, rect) ? HOVER : SURFACE);
            int ink = active ? WHITE : ACCENT;
            float cx = rect.left + rect.width / 2.0F;
            float cy = rect.top + rect.height / 2.0F;
            float unit = rect.width / 16.0F;
            switch (type) {
                case BUTTON_LAYERS -> {
                    draw.quad(cx, cy - 1.5F * unit - 3.0F * unit, cx + 5.0F * unit, cy - 1.5F * unit, cx,
                            cy - 1.5F * unit + 3.0F * unit, cx - 5.0F * unit, cy - 1.5F * unit, ink);
                    draw.quad(cx, cy + 1.5F * unit - 3.0F * unit, cx + 5.0F * unit, cy + 1.5F * unit, cx,
                            cy + 1.5F * unit + 3.0F * unit, cx - 5.0F * unit, cy + 1.5F * unit, (ink & 0x00FFFFFF) | 0x99000000);
                }
                case BUTTON_LOCATE -> {
                    draw.disc(cx, cy, 4.5F * unit, ink);
                    draw.disc(cx, cy, 3.2F * unit, active ? ACCENT : SURFACE);
                    draw.disc(cx, cy, 1.8F * unit, ink);
                    draw.rect(cx - 0.6F * unit, cy - 6.5F * unit, cx + 0.6F * unit, cy - 4.5F * unit, ink);
                    draw.rect(cx - 0.6F * unit, cy + 4.5F * unit, cx + 0.6F * unit, cy + 6.5F * unit, ink);
                    draw.rect(cx - 6.5F * unit, cy - 0.6F * unit, cx - 4.5F * unit, cy + 0.6F * unit, ink);
                    draw.rect(cx + 4.5F * unit, cy - 0.6F * unit, cx + 6.5F * unit, cy + 0.6F * unit, ink);
                }
                case BUTTON_ZOOM_IN -> {
                    draw.rect(cx - 4.5F * unit, cy - 0.7F * unit, cx + 4.5F * unit, cy + 0.7F * unit, ink);
                    draw.rect(cx - 0.7F * unit, cy - 4.5F * unit, cx + 0.7F * unit, cy + 4.5F * unit, ink);
                }
                case BUTTON_ZOOM_OUT -> draw.rect(cx - 4.5F * unit, cy - 0.7F * unit, cx + 4.5F * unit, cy + 0.7F * unit, ink);
                default -> {
                    draw.segment(cx - 3.8F * unit, cy + 3.8F * unit, cx + 3.2F * unit, cy - 3.2F * unit, 2.4F * unit, ink);
                    draw.triangle(cx - 5.0F * unit, cy + 5.0F * unit, cx - 4.6F * unit, cy + 2.4F * unit,
                            cx - 2.4F * unit, cy + 4.6F * unit, ink);
                }
            }
            draw.flush();
        }
    }

    private static void renderNavBanner(PhoneScreen screen, GuiGraphics graphics, MapDraw draw, PhoneChatLayout layout,
                                        UiRect viewport) {
        UiRect banner = navBanner(layout, viewport);
        fillRounded(graphics, banner.left, banner.top, banner.width, banner.height, layout.radius * 2, 0xF01A73E8);
        UiRect stop = navStopButton(layout, banner);
        Component stopLabel = Component.translatable("map.bphone.nav.stop");
        fillRounded(graphics, stop.left, stop.top, stop.width, stop.height, layout.radius,
                hovered(screen, stop) ? 0x40FFFFFF : 0x26FFFFFF);
        drawCentered(graphics, layout, stopLabel, stop.left + stop.width / 2, stop.top + (stop.height - layout.bodyHeight) / 2,
                WHITE, layout.bodyScale);
        MapNavigation.Target target = MapNavigation.target();
        int textLeft = banner.left + layout.u(6);
        int textRight = stop.left - layout.gap;
        String title = target == null ? "" : target.name();
        String subtitle;
        if (MapNavigation.isOtherDimension()) {
            subtitle = Component.translatable("map.bphone.nav.other_dimension").getString();
        } else {
            double remaining = MapNavigation.remaining();
            subtitle = MapNavigation.formatDistance(remaining)
                    + (remaining >= 0 ? " · " + MapNavigation.formatDuration(remaining) : "");
        }
        int top = banner.top + (banner.height - layout.bodyHeight - layout.textGap - layout.captionHeight) / 2;
        drawText(graphics, layout, fit(layout, title, BOLD, textRight - textLeft, layout.bodyScale), textLeft, top, WHITE,
                layout.bodyScale);
        drawText(graphics, layout, fit(layout, subtitle, PLAIN, textRight - textLeft, layout.captionScale), textLeft,
                top + layout.bodyHeight + layout.textGap, 0xDDFFFFFF, layout.captionScale);
    }

    private static UiRect sheet(PhoneChatLayout layout, UiRect viewport) {
        int height = 2 * layout.u(6) + layout.bodyHeight + layout.textGap + layout.captionHeight + layout.u(6)
                + layout.buttonHeight;
        return new UiRect(viewport.left + layout.pad, viewport.bottom() - layout.pad - height,
                viewport.width - 2 * layout.pad, height);
    }

    private static UiRect[] sheetButtons(PhoneChatLayout layout, UiRect sheet) {
        int inset = layout.u(6);
        int top = sheet.bottom() - inset - layout.buttonHeight;
        int width = (sheet.width - 2 * inset - layout.gap) / 2;
        return new UiRect[] {
                new UiRect(sheet.left + inset, top, width, layout.buttonHeight),
                new UiRect(sheet.left + inset + width + layout.gap, top, sheet.width - 2 * inset - layout.gap - width,
                        layout.buttonHeight)
        };
    }

    private static Component secondaryAction(PhoneMapApp app) {
        if (app.selectType == PhoneMapApp.SELECT_PIN) {
            return Component.translatable(app.deleteConfirm > 0 ? "map.bphone.confirm" : "map.bphone.pin.delete");
        }
        return Component.translatable("map.bphone.pin.save");
    }

    private static void renderSheet(PhoneScreen screen, GuiGraphics graphics, PhoneMapApp app, PhoneChatLayout layout,
                                    UiRect viewport) {
        UiRect sheet = sheet(layout, viewport);
        fillRounded(graphics, sheet.left - 1, sheet.top - 1, sheet.width + 2, sheet.height + 2, layout.radius * 2 + 1,
                HAIRLINE);
        fillRounded(graphics, sheet.left, sheet.top, sheet.width, sheet.height, layout.radius * 2, SURFACE);
        int inset = layout.u(6);
        int textLeft = sheet.left + inset;
        int textWidth = sheet.width - 2 * inset;
        drawText(graphics, layout, fit(layout, app.selectName, BOLD, textWidth, layout.bodyScale), textLeft,
                sheet.top + inset, TEXT, layout.bodyScale);
        drawText(graphics, layout, fit(layout, selectionSubtitle(app), PLAIN, textWidth, layout.captionScale), textLeft,
                sheet.top + inset + layout.bodyHeight + layout.textGap, TEXT_TERTIARY, layout.captionScale);
        UiRect[] buttons = sheetButtons(layout, sheet);
        boolean navigating = app.isNavigatingToSelection();
        renderButton(screen, graphics, layout, buttons[0], Component.translatable(navigating
                ? "map.bphone.nav.stop" : "map.bphone.nav.start"), navigating ? Tone.SECONDARY : Tone.PRIMARY);
        renderButton(screen, graphics, layout, buttons[1], secondaryAction(app),
                app.selectType == PhoneMapApp.SELECT_PIN ? Tone.DANGER : Tone.SECONDARY);
    }

    private static String selectionSubtitle(PhoneMapApp app) {
        StringBuilder builder = new StringBuilder();
        if (app.selectType == PhoneMapApp.SELECT_MARKER) {
            MapData.Marker marker = app.selectedMarker();
            if (marker != null) {
                builder.append(kindName(marker.kind()).getString());
                for (MapData.Line line : MapClientState.linesThrough(marker.id())) {
                    builder.append(" · ").append(line.name());
                }
            }
        } else if (app.selectType == PhoneMapApp.SELECT_PIN) {
            builder.append(Component.translatable("map.bphone.pin").getString());
        }
        if (app.selectDimension.equals(app.dimension())) {
            if (!builder.isEmpty()) {
                builder.append(" · ");
            }
            builder.append(MapNavigation.formatDistance(app.distanceTo(app.selectX, app.selectZ)));
        }
        builder.append(builder.isEmpty() ? "" : " · ")
                .append(String.format(Locale.ROOT, "%d, %d, %d", app.selectX, app.selectY, app.selectZ));
        return builder.toString();
    }

    private static UiRect editBar(PhoneChatLayout layout, UiRect viewport) {
        int height = layout.captionHeight + layout.buttonHeight + 3 * layout.u(5);
        return new UiRect(viewport.left, viewport.bottom() - height, viewport.width, height);
    }

    private static Component[] editLabels(PhoneMapApp app) {
        if (app.tool == PhoneMapApp.TOOL_MARKER) {
            return new Component[] {Component.translatable("map.bphone.edit.cancel")};
        }
        if (app.tool == PhoneMapApp.TOOL_ROAD || app.tool == PhoneMapApp.TOOL_AREA) {
            return new Component[] {
                    Component.translatable("map.bphone.edit.undo"),
                    Component.translatable("map.bphone.edit.cancel"),
                    Component.translatable("map.bphone.edit.next")
            };
        }
        return new Component[] {
                Component.translatable("map.bphone.edit.tool.marker"),
                Component.translatable("map.bphone.edit.tool.road"),
                Component.translatable("map.bphone.edit.tool.area"),
                Component.translatable("map.bphone.edit.tool.line")
        };
    }

    private static UiRect editButton(PhoneChatLayout layout, UiRect bar, int index, int count) {
        int inset = layout.u(5);
        int top = bar.bottom() - inset - layout.buttonHeight;
        int width = (bar.width - 2 * inset - (count - 1) * layout.gap) / count;
        return new UiRect(bar.left + inset + index * (width + layout.gap), top, width, layout.buttonHeight);
    }

    private static void renderEditBar(PhoneScreen screen, GuiGraphics graphics, PhoneMapApp app, PhoneChatLayout layout,
                                      UiRect viewport) {
        UiRect bar = editBar(layout, viewport);
        graphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        graphics.fill(bar.left, bar.top, bar.right(), bar.top + 1, HAIRLINE);
        Component hint = switch (app.tool) {
            case PhoneMapApp.TOOL_MARKER -> Component.translatable("map.bphone.edit.hint.marker");
            case PhoneMapApp.TOOL_ROAD, PhoneMapApp.TOOL_AREA -> Component.translatable("map.bphone.edit.hint.points",
                    app.draft.size());
            default -> Component.translatable("map.bphone.edit.hint.idle");
        };
        drawCentered(graphics, layout, fit(layout, hint.getString(), PLAIN, bar.width - 2 * layout.pad, layout.captionScale),
                bar.left + bar.width / 2, bar.top + layout.u(5), TEXT_SECONDARY, layout.captionScale);
        Component[] labels = editLabels(app);
        for (int index = 0; index < labels.length; index++) {
            Tone tone = Tone.SECONDARY;
            if (app.tool == PhoneMapApp.TOOL_ROAD || app.tool == PhoneMapApp.TOOL_AREA) {
                if (index == 0 && app.draft.isEmpty()) {
                    tone = Tone.DISABLED;
                } else if (index == 2) {
                    tone = app.canFinishDraft() ? Tone.PRIMARY : Tone.DISABLED;
                }
            }
            renderButton(screen, graphics, layout, editButton(layout, bar, index, labels.length), labels[index], tone);
        }
    }

    private static int overlayBottom(PhoneMapApp app, PhoneChatLayout layout, UiRect viewport) {
        if (app.editing) {
            return editBar(layout, viewport).top;
        }
        if (app.selectType != PhoneMapApp.SELECT_NONE) {
            return sheet(layout, viewport).top;
        }
        return viewport.bottom();
    }

    private static void renderStatus(GuiGraphics graphics, PhoneChatLayout layout, UiRect viewport, PhoneMapApp app,
                                     int bottom) {
        Component text = fit(layout, app.status.getString(), PLAIN, viewport.width - 4 * layout.pad, layout.captionScale);
        int width = layout.width(text, layout.captionScale) + 2 * layout.u(6);
        int height = layout.captionHeight + 2 * layout.u(4);
        int left = viewport.left + (viewport.width - width) / 2;
        int top = bottom - layout.pad - height - layout.captionHeight - layout.u(8);
        fillRounded(graphics, left, top, width, height, height / 2, 0xE0202328);
        drawCentered(graphics, layout, text, left + width / 2, top + layout.u(4), WHITE, layout.captionScale);
    }

    private static void renderNorth(GuiGraphics graphics, MapDraw draw, PhoneChatLayout layout, UiRect viewport) {
        int size = Math.max(10, layout.u(13));
        int left = viewport.left + layout.pad;
        int top = viewport.top + layout.pad;
        if (MapNavigation.isActive()) {
            top = navBanner(layout, viewport).bottom() + layout.gap;
        }
        draw.disc(left + size / 2.0F, top + size / 2.0F, size / 2.0F + 0.8F, HAIRLINE);
        draw.disc(left + size / 2.0F, top + size / 2.0F, size / 2.0F, SURFACE);
        draw.flush();
        Component north = Component.translatable("map.bphone.north");
        int width = layout.width(north, layout.captionScale);
        drawText(graphics, layout, north, left + (size - width) / 2, top + (size - layout.captionHeight) / 2 + 1, DANGER,
                layout.captionScale);
    }

    private static void renderScale(GuiGraphics graphics, MapDraw draw, PhoneChatLayout layout, UiRect viewport,
                                    PhoneMapApp app, int bottom) {
        double target = 40.0D / app.zoom;
        double magnitude = Math.pow(10.0D, Math.floor(Math.log10(target)));
        double nice = target / magnitude >= 5 ? 5 * magnitude : target / magnitude >= 2 ? 2 * magnitude : magnitude;
        float length = (float) (nice * app.zoom);
        int left = viewport.left + layout.pad;
        int y = bottom - layout.pad - 2;
        int color = isDark() ? 0xFFE8EAED : 0xFF2B2F36;
        draw.rect(left - 1, y - 1, left + length + 1, y + 2, 0xCCFFFFFF);
        draw.rect(left, y, left + length, y + 1, color);
        draw.rect(left, y - 3, left + 1, y + 1, color);
        draw.rect(left + length - 1, y - 3, left + length, y + 1, color);
        draw.flush();
        Component text = Component.literal(MapNavigation.formatDistance(nice));
        drawText(graphics, layout, text, left, y - 3 - layout.captionHeight - 1, color, layout.captionScale);
    }

    private static void clickMap(PhoneScreen screen, PhoneMapApp app, PhoneChatLayout layout, double mouseX, double mouseY,
                                 int button) {
        UiRect header = layout.header();
        if (button == 0 && layout.backButton(header).contains(mouseX, mouseY)) {
            if (!app.escape()) {
                screen.closeMapApp();
            }
            return;
        }
        if (button == 0 && layout.actionButton(header).contains(mouseX, mouseY)) {
            app.openSearch();
            return;
        }
        UiRect viewport = mapViewport(layout);
        if (!viewport.contains(mouseX, mouseY)) {
            return;
        }
        if (button == 0) {
            if (MapNavigation.isActive()) {
                UiRect banner = navBanner(layout, viewport);
                if (navStopButton(layout, banner).contains(mouseX, mouseY)) {
                    app.stopNavigation();
                    return;
                }
                if (banner.contains(mouseX, mouseY)) {
                    app.recenter();
                    return;
                }
            }
            int[] buttons = floatingButtons(app);
            for (int slot = 0; slot < buttons.length; slot++) {
                if (floatingButton(layout, viewport, slot).contains(mouseX, mouseY)) {
                    switch (buttons[slot]) {
                        case BUTTON_LAYERS -> app.toggleStyle();
                        case BUTTON_LOCATE -> app.recenter();
                        case BUTTON_ZOOM_IN -> app.zoomAround(1.5F, viewport.left + viewport.width / 2.0D,
                                viewport.top + viewport.height / 2.0D, viewport);
                        case BUTTON_ZOOM_OUT -> app.zoomAround(1.0F / 1.5F, viewport.left + viewport.width / 2.0D,
                                viewport.top + viewport.height / 2.0D, viewport);
                        default -> app.toggleEditing();
                    }
                    return;
                }
            }
            if (app.editing) {
                UiRect bar = editBar(layout, viewport);
                if (bar.contains(mouseX, mouseY)) {
                    clickEditBar(app, layout, bar, mouseX, mouseY);
                    return;
                }
            } else if (app.selectType != PhoneMapApp.SELECT_NONE) {
                UiRect sheet = sheet(layout, viewport);
                if (sheet.contains(mouseX, mouseY)) {
                    UiRect[] buttonsRect = sheetButtons(layout, sheet);
                    if (buttonsRect[0].contains(mouseX, mouseY)) {
                        if (app.isNavigatingToSelection()) {
                            app.stopNavigation();
                        } else {
                            app.navigateToSelection();
                        }
                    } else if (buttonsRect[1].contains(mouseX, mouseY)) {
                        if (app.selectType == PhoneMapApp.SELECT_PIN) {
                            app.deleteSelectedPin();
                        } else {
                            app.startPinForm();
                        }
                    }
                    return;
                }
            }
        } else if (button == 1 && app.editing && editBar(layout, viewport).contains(mouseX, mouseY)) {
            return;
        }
        app.pressing = true;
        app.pressButton = button;
        app.pressX = mouseX;
        app.pressY = mouseY;
        app.dragged = false;
    }

    private static void clickEditBar(PhoneMapApp app, PhoneChatLayout layout, UiRect bar, double mouseX, double mouseY) {
        int count = editLabels(app).length;
        for (int index = 0; index < count; index++) {
            if (!editButton(layout, bar, index, count).contains(mouseX, mouseY)) {
                continue;
            }
            if (app.tool == PhoneMapApp.TOOL_MARKER) {
                app.cancelTool();
            } else if (app.tool == PhoneMapApp.TOOL_ROAD || app.tool == PhoneMapApp.TOOL_AREA) {
                switch (index) {
                    case 0 -> app.undoDraft();
                    case 1 -> app.cancelTool();
                    default -> app.finishDraft();
                }
            } else {
                switch (index) {
                    case 0 -> app.startTool(PhoneMapApp.TOOL_MARKER);
                    case 1 -> app.startTool(PhoneMapApp.TOOL_ROAD);
                    case 2 -> app.startTool(PhoneMapApp.TOOL_AREA);
                    default -> {
                        app.searchTab = PhoneMapApp.SEARCH_LINES;
                        app.openSearch();
                    }
                }
            }
            return;
        }
    }

    private static void tap(PhoneScreen screen, PhoneMapApp app, PhoneChatLayout layout, double mouseX, double mouseY,
                            int button) {
        UiRect viewport = mapViewport(layout);
        View view = view(app, layout);
        int worldX = Mth.floor(view.wx(mouseX));
        int worldZ = Mth.floor(view.wz(mouseY));
        if (app.tool == PhoneMapApp.TOOL_MARKER) {
            if (button == 0) {
                app.placeMarker(worldX, worldZ);
            }
            return;
        }
        if (app.tool == PhoneMapApp.TOOL_ROAD || app.tool == PhoneMapApp.TOOL_AREA) {
            if (button == 0) {
                app.addDraftPoint(worldX, worldZ);
            } else {
                app.undoDraft();
            }
            return;
        }
        if (button == 1) {
            if (!app.editing) {
                app.selectPoint(worldX, worldZ);
            }
            return;
        }
        String dimension = app.dimension();
        MapData.Marker marker = hitMarker(view, layout, dimension, mouseX, mouseY);
        if (app.editing) {
            if (marker != null) {
                app.editMarker(marker);
                return;
            }
            MapData.Road road = hitRoad(view, dimension, mouseX, mouseY);
            if (road != null) {
                app.editRoad(road);
                return;
            }
            MapData.Area area = hitArea(dimension, view.wx(mouseX), view.wz(mouseY));
            if (area != null) {
                app.editArea(area);
            }
            return;
        }
        PhoneMapPins.Pin pin = hitPin(view, layout, app, mouseX, mouseY);
        if (pin != null) {
            app.selectPin(pin);
            return;
        }
        if (marker != null) {
            app.selectMarker(marker);
            return;
        }
        if (viewport.contains(mouseX, mouseY)) {
            app.clearSelection();
        }
    }

    private static MapData.Marker hitMarker(View view, PhoneChatLayout layout, String dimension, double mouseX, double mouseY) {
        float radius = Math.max(4.0F, layout.u(5.5F)) + 2.5F;
        MapData.Marker best = null;
        double bestDistance = radius;
        for (MapData.Marker marker : MapClientState.markers(dimension)) {
            double distance = Math.hypot(view.sx(marker.x() + 0.5D) - mouseX, view.sy(marker.z() + 0.5D) - mouseY);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = marker;
            }
        }
        return best;
    }

    private static PhoneMapPins.Pin hitPin(View view, PhoneChatLayout layout, PhoneMapApp app, double mouseX, double mouseY) {
        float radius = Math.max(3.5F, layout.u(4.5F));
        for (PhoneMapPins.Pin pin : app.pinsHere()) {
            float x = view.sx(pin.x() + 0.5D);
            float y = view.sy(pin.z() + 0.5D) - radius * 1.7F;
            if (Math.hypot(x - mouseX, y - mouseY) <= radius + 2.5F) {
                return pin;
            }
        }
        return null;
    }

    private static MapData.Road hitRoad(View view, String dimension, double mouseX, double mouseY) {
        MapData.Road best = null;
        double bestDistance = 5.0D;
        for (MapData.Road road : MapClientState.roads(dimension)) {
            List<MapData.Point> points = road.points();
            for (int index = 0; index + 1 < points.size(); index++) {
                double ax = view.sx(points.get(index).x() + 0.5D);
                double ay = view.sy(points.get(index).z() + 0.5D);
                double bx = view.sx(points.get(index + 1).x() + 0.5D);
                double by = view.sy(points.get(index + 1).z() + 0.5D);
                double dx = bx - ax;
                double dy = by - ay;
                double lengthSquared = dx * dx + dy * dy;
                double t = lengthSquared < 1.0E-6D ? 0.0D
                        : Mth.clamp(((mouseX - ax) * dx + (mouseY - ay) * dy) / lengthSquared, 0.0D, 1.0D);
                double distance = Math.hypot(ax + dx * t - mouseX, ay + dy * t - mouseY);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = road;
                }
            }
        }
        return best;
    }

    private static MapData.Area hitArea(String dimension, double worldX, double worldZ) {
        MapData.Area found = null;
        for (MapData.Area area : MapClientState.areas(dimension)) {
            List<MapData.Point> points = area.points();
            boolean inside = false;
            for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
                double xi = points.get(i).x() + 0.5D;
                double zi = points.get(i).z() + 0.5D;
                double xj = points.get(j).x() + 0.5D;
                double zj = points.get(j).z() + 0.5D;
                if ((zi > worldZ) != (zj > worldZ) && worldX < (xj - xi) * (worldZ - zi) / (zj - zi) + xi) {
                    inside = !inside;
                }
            }
            if (inside) {
                found = area;
            }
        }
        return found;
    }

    private static UiRect searchTabs(PhoneChatLayout layout) {
        UiRect bar = layout.topBar();
        int height = layout.bodyHeight + 2 * layout.u(5);
        return new UiRect(bar.left, bar.bottom(), bar.width, height);
    }

    private static UiRect searchTab(PhoneChatLayout layout, int index) {
        UiRect tabs = searchTabs(layout);
        int left = tabs.left + tabs.width * index / 3;
        int right = tabs.left + tabs.width * (index + 1) / 3;
        return new UiRect(left, tabs.top, right - left, tabs.height);
    }

    private static UiRect searchViewport(PhoneChatLayout layout) {
        return layout.viewportBetween(searchTabs(layout).bottom(), layout.surface.bottom());
    }

    private static boolean showNewLineRow(PhoneMapApp app) {
        return app.searchTab == PhoneMapApp.SEARCH_LINES && app.canEdit();
    }

    private static void renderSearch(PhoneScreen screen, PhoneMapApp app, GuiGraphics graphics, PhoneChatLayout layout) {
        renderHeader(screen, graphics, layout, layout.header(), Component.translatable("map.bphone.search.title"), true, false);
        UiRect bar = layout.topBar();
        graphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        renderField(graphics, layout, layout.topBarField(null), app.query,
                Component.translatable("map.bphone.search.placeholder"), ACCENT, FIELD);
        UiRect tabs = searchTabs(layout);
        graphics.fill(tabs.left, tabs.top, tabs.right(), tabs.bottom(), SURFACE);
        graphics.fill(tabs.left, tabs.bottom() - 1, tabs.right(), tabs.bottom(), HAIRLINE);
        String[] keys = {"map.bphone.search.places", "map.bphone.search.lines", "map.bphone.search.pins"};
        for (int index = 0; index < 3; index++) {
            UiRect slot = searchTab(layout, index);
            boolean active = app.searchTab == index;
            Component label = fit(layout, Component.translatable(keys[index]).getString(), active ? BOLD : PLAIN,
                    slot.width - 2 * layout.u(2), layout.bodyScale);
            drawCentered(graphics, layout, label, slot.left + slot.width / 2, slot.top + (slot.height - layout.bodyHeight) / 2,
                    active ? ACCENT : hovered(screen, slot) ? TEXT : TEXT_SECONDARY, layout.bodyScale);
            if (active) {
                int width = Math.max(layout.u(10), slot.width / 3);
                graphics.fill(slot.left + (slot.width - width) / 2, slot.bottom() - Math.max(1, layout.u(2)) - 1,
                        slot.left + (slot.width + width) / 2, slot.bottom() - 1, ACCENT);
            }
        }

        UiRect viewport = searchViewport(layout);
        int offset = showNewLineRow(app) ? 1 : 0;
        int count = searchCount(app) + offset;
        if (count == 0) {
            String empty = switch (app.searchTab) {
                case PhoneMapApp.SEARCH_LINES -> "map.bphone.search.no_lines";
                case PhoneMapApp.SEARCH_PINS -> "map.bphone.search.no_pins";
                default -> app.query.isBlank() ? "map.bphone.search.no_places" : "map.bphone.search.none";
            };
            renderEmptyState(graphics, layout, viewport, Component.translatable(empty),
                    app.searchTab == PhoneMapApp.SEARCH_PINS ? Component.translatable("map.bphone.search.pins_hint") : null);
            return;
        }
        app.searchScroll = Mth.clamp(app.searchScroll, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(count, false)));
        boolean pointerInside = hovered(screen, viewport);
        List<MapData.Marker> places = app.searchTab == PhoneMapApp.SEARCH_PLACES ? app.searchPlaces() : List.of();
        List<MapData.Line> lines = app.searchTab == PhoneMapApp.SEARCH_LINES ? app.searchLines() : List.of();
        List<PhoneMapPins.Pin> pins = app.searchTab == PhoneMapApp.SEARCH_PINS ? app.searchPins() : List.of();
        graphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        MapDraw draw = new MapDraw(graphics);
        for (int index = 0; index < count; index++) {
            UiRect row = layout.listRow(viewport, index, app.searchScroll);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            int background = pointerInside && hovered(screen, row) ? HOVER : SURFACE;
            graphics.fill(row.left, row.top, row.right(), row.bottom(), background);
            UiRect icon = layout.rowAvatar(row, layout.avatarSmall);
            int textLeft = layout.rowTextLeft(row, layout.avatarSmall);
            int textRight = row.right() - layout.pad - layout.chevronReserve();
            if (index < offset) {
                fillRounded(graphics, icon.left, icon.top, icon.width, icon.height, icon.width / 2, ACCENT);
                drawCentered(graphics, layout, Component.literal("+").withStyle(BOLD), icon.left + icon.width / 2,
                        icon.top + (icon.height - layout.bodyHeight) / 2, WHITE, layout.bodyScale);
                drawText(graphics, layout, Component.translatable("map.bphone.line.new"), textLeft,
                        row.top + (row.height - layout.bodyHeight) / 2, ACCENT, layout.bodyScale);
            } else if (app.searchTab == PhoneMapApp.SEARCH_PLACES) {
                MapData.Marker marker = places.get(index - offset);
                kindIcon(graphics, draw, layout, icon, marker.kind());
                renderTwoLineText(graphics, layout, row, textLeft, textRight, marker.name(), TEXT,
                        kindName(marker.kind()).getString() + " · "
                                + MapNavigation.formatDistance(app.distanceTo(marker.x(), marker.z())), TEXT_TERTIARY);
            } else if (app.searchTab == PhoneMapApp.SEARCH_LINES) {
                MapData.Line line = lines.get(index - offset);
                fillRounded(graphics, icon.left, icon.top, icon.width, icon.height, icon.width / 2, 0xFF000000 | line.color());
                renderTwoLineText(graphics, layout, row, textLeft, textRight, line.name(), TEXT,
                        Component.translatable("map.bphone.line.stations", line.stations().size()).getString(), TEXT_TERTIARY);
            } else {
                PhoneMapPins.Pin pin = pins.get(index - offset);
                fillRounded(graphics, icon.left, icon.top, icon.width, icon.height, icon.width / 2, PIN_COLOR);
                fillRounded(graphics, icon.left + icon.width / 3, icon.top + icon.height / 3, icon.width / 3,
                        icon.height / 3, icon.width / 6, WHITE);
                String where = pin.dimension().equals(app.dimension())
                        ? MapNavigation.formatDistance(app.distanceTo(pin.x(), pin.z()))
                        : Component.translatable("map.bphone.nav.other_dimension").getString();
                renderTwoLineText(graphics, layout, row, textLeft, textRight, pin.name(), TEXT,
                        where + " · " + pin.x() + ", " + pin.y() + ", " + pin.z(), TEXT_TERTIARY);
            }
            renderChevron(graphics, layout, row);
            if (index < count - 1) {
                graphics.fill(textLeft, row.bottom() - 1, row.right(), row.bottom(), HAIRLINE);
            }
        }
        draw.flush();
        graphics.disableScissor();
    }

    private static int searchCount(PhoneMapApp app) {
        return switch (app.searchTab) {
            case PhoneMapApp.SEARCH_LINES -> app.searchLines().size();
            case PhoneMapApp.SEARCH_PINS -> app.searchPins().size();
            default -> app.searchPlaces().size();
        };
    }

    private static void clickSearch(PhoneScreen screen, PhoneMapApp app, PhoneChatLayout layout, double mouseX,
                                    double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            app.back();
            return;
        }
        for (int index = 0; index < 3; index++) {
            if (searchTab(layout, index).contains(mouseX, mouseY)) {
                app.searchTab = index;
                app.searchScroll = 0;
                return;
            }
        }
        int offset = showNewLineRow(app) ? 1 : 0;
        int index = layout.listRowAt(searchViewport(layout), searchCount(app) + offset, app.searchScroll, mouseX, mouseY);
        if (index < 0) {
            return;
        }
        if (index < offset) {
            app.openLineForm(null);
            return;
        }
        switch (app.searchTab) {
            case PhoneMapApp.SEARCH_LINES -> app.openLine(app.searchLines().get(index - offset));
            case PhoneMapApp.SEARCH_PINS -> app.showPin(app.searchPins().get(index - offset));
            default -> app.showMarker(app.searchPlaces().get(index - offset));
        }
    }

    private static void renderLine(PhoneScreen screen, PhoneMapApp app, GuiGraphics graphics, PhoneChatLayout layout) {
        MapData.Line line = MapClientState.line(app.lineId);
        UiRect header = layout.header();
        renderHeader(screen, graphics, layout, header, line == null
                ? Component.translatable("map.bphone.search.lines") : Component.literal(line.name()), true, false);
        if (line != null && app.canEdit()) {
            renderMoreButton(screen, graphics, layout, layout.actionButton(header));
        }
        UiRect viewport = layout.pageViewport();
        if (line == null || line.stations().isEmpty()) {
            renderEmptyState(graphics, layout, viewport, Component.translatable("map.bphone.line.empty"), null);
            return;
        }
        List<String> stations = line.stations();
        app.lineScroll = Mth.clamp(app.lineScroll, 0,
                PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(stations.size(), false)));
        int color = 0xFF000000 | line.color();
        boolean pointerInside = hovered(screen, viewport);
        graphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
        for (int index = 0; index < stations.size(); index++) {
            UiRect row = layout.listRow(viewport, index, app.lineScroll);
            if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                continue;
            }
            MapData.Marker marker = MapClientState.marker(stations.get(index));
            graphics.fill(row.left, row.top, row.right(), row.bottom(), pointerInside && hovered(screen, row) ? HOVER : SURFACE);
            int railX = row.left + layout.pad + layout.avatarSmall / 2;
            int railWidth = Math.max(2, layout.u(3));
            int railTop = index == 0 ? row.top + row.height / 2 : row.top;
            int railBottom = index == stations.size() - 1 ? row.top + row.height / 2 : row.bottom();
            graphics.fill(railX - railWidth / 2, railTop, railX - railWidth / 2 + railWidth, railBottom, color);
            int dot = Math.max(5, layout.u(7));
            fillRounded(graphics, railX - dot / 2, row.top + (row.height - dot) / 2, dot, dot, dot / 2, color);
            fillRounded(graphics, railX - dot / 2 + 1, row.top + (row.height - dot) / 2 + 1, dot - 2, dot - 2, (dot - 2) / 2, WHITE);
            int textLeft = layout.rowTextLeft(row, layout.avatarSmall);
            String name = marker == null ? stations.get(index) : marker.name();
            String detail = marker == null ? "" : marker.dimension().equals(app.dimension())
                    ? MapNavigation.formatDistance(app.distanceTo(marker.x(), marker.z()))
                    : Component.translatable("map.bphone.nav.other_dimension").getString();
            renderTwoLineText(graphics, layout, row, textLeft, row.right() - layout.pad - layout.chevronReserve(), name, TEXT, detail,
                    TEXT_TERTIARY);
            renderChevron(graphics, layout, row);
        }
        graphics.disableScissor();
    }

    private static void clickLine(PhoneMapApp app, PhoneChatLayout layout, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        UiRect header = layout.header();
        if (layout.backButton(header).contains(mouseX, mouseY)) {
            app.back();
            return;
        }
        MapData.Line line = MapClientState.line(app.lineId);
        if (line == null) {
            return;
        }
        if (app.canEdit() && layout.actionButton(header).contains(mouseX, mouseY)) {
            app.openLineForm(line);
            return;
        }
        int index = layout.listRowAt(layout.pageViewport(), line.stations().size(), app.lineScroll, mouseX, mouseY);
        if (index >= 0) {
            MapData.Marker marker = MapClientState.marker(line.stations().get(index));
            if (marker != null) {
                app.showMarker(marker);
            }
        }
    }

    private record FormLayout(int labelTop, UiRect field, UiRect kindRow, UiRect previous, UiRect next, int infoTop,
                              UiRect save, UiRect delete) {
    }

    private static FormLayout formLayout(PhoneMapApp app, PhoneChatLayout layout) {
        int left = layout.surface.left + layout.pad;
        int width = layout.surface.width - 2 * layout.pad;
        int labelTop = layout.header().bottom() + layout.u(8);
        UiRect field = new UiRect(left, labelTop + layout.captionHeight + layout.u(4), width, layout.buttonHeight);
        UiRect kindRow = null;
        UiRect previous = null;
        UiRect next = null;
        int infoTop = field.bottom() + layout.u(8);
        if (app.formKindCount() > 0) {
            kindRow = new UiRect(left, field.bottom() + layout.u(8), width, layout.buttonHeight);
            int arrow = layout.buttonHeight;
            previous = new UiRect(kindRow.left, kindRow.top, arrow, arrow);
            next = new UiRect(kindRow.right() - arrow, kindRow.top, arrow, arrow);
            infoTop = kindRow.bottom() + layout.u(8);
        }
        boolean existing = !app.formId.isEmpty() && app.formType != PhoneMapApp.FORM_PIN;
        UiRect save = new UiRect(left, infoTop + layout.captionHeight + layout.u(12), width, layout.buttonHeight);
        UiRect delete = existing ? new UiRect(left, save.bottom() + layout.gap, width, layout.buttonHeight) : null;
        return new FormLayout(labelTop, field, kindRow, previous, next, infoTop, save, delete);
    }

    private static Component formTitle(PhoneMapApp app) {
        boolean existing = !app.formId.isEmpty();
        return Component.translatable(switch (app.formType) {
            case PhoneMapApp.FORM_PIN -> "map.bphone.form.pin";
            case PhoneMapApp.FORM_ROAD -> existing ? "map.bphone.form.road_edit" : "map.bphone.form.road_new";
            case PhoneMapApp.FORM_AREA -> existing ? "map.bphone.form.area_edit" : "map.bphone.form.area_new";
            default -> existing ? "map.bphone.form.marker_edit" : "map.bphone.form.marker_new";
        });
    }

    private static Component formKindName(PhoneMapApp app) {
        return switch (app.formType) {
            case PhoneMapApp.FORM_ROAD -> Component.translatable("map.bphone.road." + MapData.ROAD_KIND_NAMES[app.formKind]);
            case PhoneMapApp.FORM_AREA -> Component.translatable("map.bphone.area." + MapData.AREA_KIND_NAMES[app.formKind]);
            default -> kindName(app.formKind);
        };
    }

    private static void renderForm(PhoneScreen screen, PhoneMapApp app, GuiGraphics graphics, PhoneChatLayout layout) {
        renderHeader(screen, graphics, layout, layout.header(), formTitle(app), true, false);
        FormLayout form = formLayout(app, layout);
        int left = layout.surface.left + layout.pad;
        int width = layout.surface.width - 2 * layout.pad;
        drawText(graphics, layout, Component.translatable("map.bphone.form.name"), left, form.labelTop(), TEXT_SECONDARY,
                layout.captionScale);
        renderField(graphics, layout, form.field(), app.formName, Component.translatable("map.bphone.form.name_placeholder"),
                ACCENT, SURFACE);
        if (form.kindRow() != null) {
            UiRect row = form.kindRow();
            fillRounded(graphics, row.left, row.top, row.width, row.height, layout.radius, SURFACE);
            renderButton(screen, graphics, layout, form.previous(), Component.literal("<"), Tone.SECONDARY);
            renderButton(screen, graphics, layout, form.next(), Component.literal(">"), Tone.SECONDARY);
            Component kind = Component.translatable("map.bphone.form.kind", formKindName(app));
            drawCentered(graphics, layout, fit(layout, kind.getString(), PLAIN,
                            row.width - 2 * row.height - 2 * layout.gap, layout.bodyScale),
                    row.left + row.width / 2, row.top + (row.height - layout.bodyHeight) / 2, TEXT, layout.bodyScale);
            if (app.formType == PhoneMapApp.FORM_MARKER && app.formKind != MapData.MARKER_LABEL) {
                MapDraw draw = new MapDraw(graphics);
                kindIcon(graphics, draw, layout, new UiRect(form.previous().right() + layout.gap,
                        row.top + (row.height - layout.avatarSmall) / 2, layout.avatarSmall, layout.avatarSmall), app.formKind);
                draw.flush();
            }
        }
        String info = switch (app.formType) {
            case PhoneMapApp.FORM_ROAD, PhoneMapApp.FORM_AREA ->
                    Component.translatable("map.bphone.form.points", app.formPoints.size()).getString();
            default -> Component.translatable("map.bphone.form.coords", app.formX, app.formY, app.formZ).getString();
        };
        drawText(graphics, layout, fit(layout, info, PLAIN, width, layout.captionScale), left, form.infoTop(),
                TEXT_TERTIARY, layout.captionScale);
        renderButton(screen, graphics, layout, form.save(), Component.translatable("map.bphone.form.save"),
                app.canSubmitForm() ? Tone.PRIMARY : Tone.DISABLED);
        if (form.delete() != null) {
            renderButton(screen, graphics, layout, form.delete(), Component.translatable(app.deleteConfirm > 0
                    ? "map.bphone.confirm" : "map.bphone.form.delete"), Tone.DANGER);
        }
    }

    private static void clickForm(PhoneMapApp app, PhoneChatLayout layout, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            app.back();
            return;
        }
        FormLayout form = formLayout(app, layout);
        if (form.previous() != null && form.previous().contains(mouseX, mouseY)) {
            app.cycleFormKind(-1);
        } else if (form.next() != null && form.next().contains(mouseX, mouseY)) {
            app.cycleFormKind(1);
        } else if (form.save().contains(mouseX, mouseY) && app.canSubmitForm()) {
            app.submitForm();
        } else if (form.delete() != null && form.delete().contains(mouseX, mouseY)) {
            app.deleteForm();
        }
    }

    private static UiRect lineNameField(PhoneChatLayout layout) {
        return new UiRect(layout.surface.left + layout.pad, layout.header().bottom() + layout.u(6),
                layout.surface.width - 2 * layout.pad, layout.buttonHeight);
    }

    private static UiRect swatch(PhoneChatLayout layout, int index) {
        UiRect field = lineNameField(layout);
        int columns = MapData.LINE_COLORS.length / 2;
        int size = Math.min(layout.u(14), (field.width - (columns - 1) * layout.gap) / columns);
        int row = index / columns;
        int column = index % columns;
        int total = columns * size + (columns - 1) * layout.gap;
        int left = field.left + (field.width - total) / 2 + column * (size + layout.gap);
        return new UiRect(left, field.bottom() + layout.u(6) + row * (size + layout.gap), size, size);
    }

    private static UiRect lineStationsViewport(PhoneChatLayout layout) {
        UiRect last = swatch(layout, MapData.LINE_COLORS.length - 1);
        int top = last.bottom() + layout.u(6) + layout.sectionHeight;
        return layout.viewportBetween(top, layout.bottomBar().top);
    }

    private static void renderLineForm(PhoneScreen screen, PhoneMapApp app, GuiGraphics graphics, PhoneChatLayout layout) {
        renderHeader(screen, graphics, layout, layout.header(), Component.translatable(app.lineFormId.isEmpty()
                ? "map.bphone.line.new" : "map.bphone.line.edit"), true, false);
        renderField(graphics, layout, lineNameField(layout), app.lineFormName,
                Component.translatable("map.bphone.line.name_placeholder"), ACCENT, SURFACE);
        for (int index = 0; index < MapData.LINE_COLORS.length; index++) {
            UiRect swatch = swatch(layout, index);
            boolean selected = MapData.LINE_COLORS[index] == app.lineFormColor;
            if (selected) {
                fillRounded(graphics, swatch.left - 2, swatch.top - 2, swatch.width + 4, swatch.height + 4,
                        (swatch.width + 4) / 2, TEXT);
            }
            fillRounded(graphics, swatch.left, swatch.top, swatch.width, swatch.height, swatch.width / 2,
                    0xFF000000 | MapData.LINE_COLORS[index]);
        }
        UiRect viewport = lineStationsViewport(layout);
        renderSection(graphics, layout, new UiRect(viewport.left, viewport.top - layout.sectionHeight, viewport.width,
                layout.sectionHeight), Component.translatable("map.bphone.line.pick", app.lineFormStations.size()).getString());
        List<MapData.Marker> candidates = app.stationCandidates();
        if (candidates.isEmpty()) {
            renderEmptyState(graphics, layout, viewport, Component.translatable("map.bphone.line.no_stations"),
                    Component.translatable("map.bphone.line.no_stations_hint"));
        } else {
            app.lineFormScroll = Mth.clamp(app.lineFormScroll, 0,
                    PhoneChatLayout.maxScroll(viewport, layout.listContentHeight(candidates.size(), false)));
            boolean pointerInside = hovered(screen, viewport);
            graphics.enableScissor(viewport.left, viewport.top, viewport.right(), viewport.bottom());
            for (int index = 0; index < candidates.size(); index++) {
                UiRect row = layout.listRow(viewport, index, app.lineFormScroll);
                if (row.bottom() <= viewport.top || row.top >= viewport.bottom()) {
                    continue;
                }
                MapData.Marker marker = candidates.get(index);
                graphics.fill(row.left, row.top, row.right(), row.bottom(), pointerInside && hovered(screen, row) ? HOVER : SURFACE);
                int order = app.lineFormStations.indexOf(marker.id());
                UiRect badge = layout.rowAvatar(row, layout.avatarSmall);
                if (order >= 0) {
                    fillRounded(graphics, badge.left, badge.top, badge.width, badge.height, badge.width / 2,
                            0xFF000000 | app.lineFormColor);
                    Component number = Component.literal(String.valueOf(order + 1)).withStyle(BOLD);
                    drawCentered(graphics, layout, number, badge.left + badge.width / 2,
                            badge.top + (badge.height - layout.captionHeight) / 2, WHITE, layout.captionScale);
                } else {
                    renderCheck(graphics, layout, badge, false, true);
                }
                int textLeft = layout.rowTextLeft(row, layout.avatarSmall);
                renderTwoLineText(graphics, layout, row, textLeft, row.right() - layout.pad, marker.name(), TEXT,
                        kindName(marker.kind()).getString(), TEXT_TERTIARY);
            }
            graphics.disableScissor();
        }
        UiRect bar = layout.bottomBar();
        graphics.fill(bar.left, bar.top, bar.right(), bar.bottom(), SURFACE);
        graphics.fill(bar.left, bar.top, bar.right(), bar.top + 1, HAIRLINE);
        UiRect[] buttons = lineFormButtons(app, layout);
        renderButton(screen, graphics, layout, buttons[0], Component.translatable("map.bphone.form.save"),
                app.canSubmitLine() ? Tone.PRIMARY : Tone.DISABLED);
        if (buttons.length > 1) {
            renderButton(screen, graphics, layout, buttons[1], Component.translatable(app.deleteConfirm > 0
                    ? "map.bphone.confirm" : "map.bphone.form.delete"), Tone.DANGER);
        }
    }

    private static UiRect[] lineFormButtons(PhoneMapApp app, PhoneChatLayout layout) {
        UiRect full = layout.bottomBarButton();
        if (app.lineFormId.isEmpty()) {
            return new UiRect[] {full};
        }
        int width = (full.width - layout.gap) / 2;
        return new UiRect[] {
                new UiRect(full.left, full.top, width, full.height),
                new UiRect(full.left + width + layout.gap, full.top, full.width - width - layout.gap, full.height)
        };
    }

    private static void clickLineForm(PhoneMapApp app, PhoneChatLayout layout, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (layout.backButton(layout.header()).contains(mouseX, mouseY)) {
            app.back();
            return;
        }
        for (int index = 0; index < MapData.LINE_COLORS.length; index++) {
            if (swatch(layout, index).contains(mouseX, mouseY)) {
                app.lineFormColor = MapData.LINE_COLORS[index];
                return;
            }
        }
        UiRect[] buttons = lineFormButtons(app, layout);
        if (buttons[0].contains(mouseX, mouseY)) {
            app.submitLine();
            return;
        }
        if (buttons.length > 1 && buttons[1].contains(mouseX, mouseY)) {
            app.deleteLine();
            return;
        }
        List<MapData.Marker> candidates = app.stationCandidates();
        int index = layout.listRowAt(lineStationsViewport(layout), candidates.size(), app.lineFormScroll, mouseX, mouseY);
        if (index >= 0) {
            app.toggleLineStation(candidates.get(index).id());
        }
    }

    static Component kindName(int kind) {
        return Component.translatable("map.bphone.kind." + MapData.MARKER_KIND_NAMES[Mth.clamp(kind, 0,
                MapData.MARKER_KIND_COUNT - 1)]);
    }

    private static void kindIcon(GuiGraphics graphics, MapDraw draw, PhoneChatLayout layout, UiRect icon, int kind) {
        float radius = icon.width / 2.0F;
        draw.disc(icon.left + radius, icon.top + radius, radius, MARKER_COLORS[Mth.clamp(kind, 0, MARKER_COLORS.length - 1)]);
        draw.flush();
        Component glyph = Component.translatable("map.bphone.glyph." + MapData.MARKER_KIND_NAMES[kind]);
        float scale = layout.captionScale;
        int width = Math.round(layout.font.width(glyph) * scale);
        drawText(graphics, layout, glyph, icon.left + (icon.width - width) / 2,
                icon.top + (icon.height - layout.captionHeight) / 2, WHITE, scale);
    }
}
