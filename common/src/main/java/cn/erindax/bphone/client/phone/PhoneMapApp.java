package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.client.map.MapClientState;
import cn.erindax.bphone.client.map.MapNavigation;
import cn.erindax.bphone.client.map.MapNetworkingClient;
import cn.erindax.bphone.map.MapData;
import cn.erindax.bphone.map.MapNetworking;
import cn.erindax.bphone.map.PhoneMapPins;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

final class PhoneMapApp {
    static final int PAGE_MAP = 0;
    static final int PAGE_SEARCH = 1;
    static final int PAGE_LINE = 2;
    static final int PAGE_FORM = 3;
    static final int PAGE_LINE_FORM = 4;

    static final int SEARCH_PLACES = 0;
    static final int SEARCH_LINES = 1;
    static final int SEARCH_PINS = 2;

    static final int STYLE_TERRAIN = 0;
    static final int STYLE_PLAIN = 1;
    static final int STYLE_CITY = 2;
    private static final String[] STYLE_KEYS = {
            "map.bphone.style.terrain", "map.bphone.style.plain", "map.bphone.style.city"
    };

    static final int TOOL_NONE = 0;
    static final int TOOL_MARKER = 1;
    static final int TOOL_ROAD = 2;
    static final int TOOL_AREA = 3;

    static final int SELECT_NONE = 0;
    static final int SELECT_MARKER = 1;
    static final int SELECT_PIN = 2;
    static final int SELECT_POINT = 3;

    static final int FORM_MARKER = 0;
    static final int FORM_ROAD = 1;
    static final int FORM_AREA = 2;
    static final int FORM_PIN = 3;

    static final float MIN_ZOOM = 0.125F;
    static final float MAX_ZOOM = 6.0F;
    private static final int CONFIRM_TICKS = 60;
    private static final int STATUS_TICKS = 50;

    private static float savedZoom = 1.0F;
    private static int savedStyle = STYLE_TERRAIN;

    private final PhoneScreen screen;
    private final Deque<Integer> history = new ArrayDeque<>();
    int page = PAGE_MAP;
    double centerX;
    double centerZ;
    float zoom = 1.0F;
    boolean follow = true;
    int style = STYLE_TERRAIN;

    int selectType = SELECT_NONE;
    String selectId = "";
    String selectName = "";
    String selectDimension = "";
    int selectX;
    int selectY;
    int selectZ;

    boolean pressing;
    int pressButton = -1;
    double pressX;
    double pressY;
    boolean dragged;

    String query = "";
    int searchTab = SEARCH_PLACES;
    int searchScroll;
    String lineId = "";
    int lineScroll;

    boolean editing;
    int tool = TOOL_NONE;
    final List<MapData.Point> draft = new ArrayList<>();

    int formType = FORM_MARKER;
    String formId = "";
    String formName = "";
    String formDimension = "";
    int formKind;
    int formX;
    int formY;
    int formZ;
    List<MapData.Point> formPoints = List.of();
    int deleteConfirm;

    String lineFormId = "";
    String lineFormName = "";
    int lineFormColor = MapData.LINE_COLORS[0];
    final List<String> lineFormStations = new ArrayList<>();
    int lineFormScroll;

    Component status = Component.empty();
    int statusTicks;

    PhoneMapApp(PhoneScreen screen) {
        this.screen = screen;
    }

    void open() {
        history.clear();
        page = PAGE_MAP;
        zoom = savedZoom;
        style = savedStyle;
        editing = false;
        tool = TOOL_NONE;
        draft.clear();
        clearSelection();
        query = "";
        searchScroll = 0;
        pressing = false;
        recenter();
    }

    void close() {
        savedZoom = zoom;
        savedStyle = style;
        pressing = false;
        editing = false;
        tool = TOOL_NONE;
        draft.clear();
        history.clear();
        page = PAGE_MAP;
    }

    void tick() {
        if (follow) {
            LocalPlayer player = player();
            if (player != null) {
                centerX = player.getX();
                centerZ = player.getZ();
            }
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
        if (deleteConfirm > 0) {
            deleteConfirm--;
        }
    }

    boolean escape() {
        if (page != PAGE_MAP) {
            back();
            return true;
        }
        if (tool != TOOL_NONE) {
            cancelTool();
            return true;
        }
        if (selectType != SELECT_NONE) {
            clearSelection();
            return true;
        }
        if (editing) {
            editing = false;
            return true;
        }
        return false;
    }

    void go(int next) {
        history.push(page);
        page = next;
        deleteConfirm = 0;
    }

    void back() {
        page = history.isEmpty() ? PAGE_MAP : history.pop();
        deleteConfirm = 0;
    }

    void status(Component message) {
        status = message;
        statusTicks = STATUS_TICKS;
    }

    static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    String dimension() {
        return MapClientState.currentDimension();
    }

    void recenter() {
        follow = true;
        LocalPlayer player = player();
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    void focus(double x, double z, float minZoom) {
        follow = false;
        centerX = x;
        centerZ = z;
        zoom = Math.max(zoom, minZoom);
    }

    void pan(double screenDx, double screenDy) {
        follow = false;
        centerX -= screenDx / zoom;
        centerZ -= screenDy / zoom;
    }

    void zoomAround(float factor, double anchorX, double anchorY, UiRect viewport) {
        float next = Mth.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        if (next == zoom) {
            return;
        }
        double midX = viewport.left + viewport.width / 2.0D;
        double midY = viewport.top + viewport.height / 2.0D;
        double worldX = centerX + (anchorX - midX) / zoom;
        double worldZ = centerZ + (anchorY - midY) / zoom;
        zoom = next;
        if (!follow) {
            centerX = worldX - (anchorX - midX) / zoom;
            centerZ = worldZ - (anchorY - midY) / zoom;
        }
    }

    void toggleStyle() {
        style = (style + 1) % STYLE_KEYS.length;
        savedStyle = style;
        status(Component.translatable(STYLE_KEYS[style]));
    }

    void clearSelection() {
        selectType = SELECT_NONE;
        selectId = "";
        selectName = "";
        selectDimension = "";
    }

    void selectMarker(MapData.Marker marker) {
        selectType = SELECT_MARKER;
        selectId = marker.id();
        selectName = marker.name();
        selectDimension = marker.dimension();
        selectX = marker.x();
        selectY = marker.y();
        selectZ = marker.z();
    }

    void selectPin(PhoneMapPins.Pin pin) {
        selectType = SELECT_PIN;
        selectId = pin.id();
        selectName = pin.name();
        selectDimension = pin.dimension();
        selectX = pin.x();
        selectY = pin.y();
        selectZ = pin.z();
    }

    void selectPoint(int x, int z) {
        selectType = SELECT_POINT;
        selectId = "";
        selectName = Component.translatable("map.bphone.dropped").getString();
        selectDimension = dimension();
        selectX = x;
        selectY = surfaceY(x, z);
        selectZ = z;
    }

    MapData.Marker selectedMarker() {
        return selectType == SELECT_MARKER ? MapClientState.marker(selectId) : null;
    }

    boolean isNavigatingToSelection() {
        MapNavigation.Target target = MapNavigation.target();
        return target != null && selectType != SELECT_NONE && target.dimension().equals(selectDimension)
                && Math.abs(target.x() - (selectX + 0.5D)) < 0.01D && Math.abs(target.z() - (selectZ + 0.5D)) < 0.01D;
    }

    void navigateToSelection() {
        if (selectType == SELECT_NONE) {
            return;
        }
        MapNavigation.start(new MapNavigation.Target(selectName, selectDimension, selectX + 0.5D, selectY, selectZ + 0.5D));
        if (MapNavigation.isOtherDimension()) {
            status(Component.translatable("map.bphone.nav.other_dimension"));
        } else {
            status(Component.translatable("map.bphone.nav.started", selectName));
            recenter();
        }
    }

    void stopNavigation() {
        MapNavigation.stop();
        status(Component.translatable("map.bphone.nav.stopped"));
    }

    List<PhoneMapPins.Pin> pins() {
        return PhoneMapPins.get(screen.getSettingsPhoneStack());
    }

    List<PhoneMapPins.Pin> pinsHere() {
        List<PhoneMapPins.Pin> result = new ArrayList<>();
        String dimension = dimension();
        for (PhoneMapPins.Pin pin : pins()) {
            if (pin.dimension().equals(dimension)) {
                result.add(pin);
            }
        }
        return result;
    }

    void startPinForm() {
        if (selectType == SELECT_NONE) {
            return;
        }
        formType = FORM_PIN;
        formId = "";
        formName = selectType == SELECT_POINT ? "" : selectName;
        formDimension = selectDimension;
        formX = selectX;
        formY = selectY;
        formZ = selectZ;
        go(PAGE_FORM);
    }

    void deleteSelectedPin() {
        if (selectType != SELECT_PIN) {
            return;
        }
        if (deleteConfirm <= 0) {
            deleteConfirm = CONFIRM_TICKS;
            return;
        }
        deleteConfirm = 0;
        MapNetworkingClient.removePin(screen.getOpenHand(), selectId);
        clearSelection();
        status(Component.translatable("map.bphone.pin.removed"));
    }

    boolean canEdit() {
        return !screen.isHomePhoneMode() && MapNetworkingClient.canEdit();
    }

    void toggleEditing() {
        if (!canEdit()) {
            return;
        }
        editing = !editing;
        cancelTool();
        clearSelection();
    }

    void startTool(int next) {
        tool = next;
        draft.clear();
        clearSelection();
    }

    void cancelTool() {
        tool = TOOL_NONE;
        draft.clear();
    }

    void addDraftPoint(int x, int z) {
        if (draft.size() >= MapData.MAX_POINTS) {
            return;
        }
        if (!draft.isEmpty()) {
            MapData.Point last = draft.get(draft.size() - 1);
            if (last.x() == x && last.z() == z) {
                return;
            }
        }
        draft.add(new MapData.Point(x, z));
    }

    void undoDraft() {
        if (!draft.isEmpty()) {
            draft.remove(draft.size() - 1);
        }
    }

    boolean canFinishDraft() {
        return tool == TOOL_ROAD ? draft.size() >= 2 : tool == TOOL_AREA && draft.size() >= 3;
    }

    void finishDraft() {
        if (!canFinishDraft()) {
            return;
        }
        formType = tool == TOOL_ROAD ? FORM_ROAD : FORM_AREA;
        formId = "";
        formName = "";
        formKind = 0;
        formDimension = dimension();
        formPoints = List.copyOf(draft);
        cancelTool();
        go(PAGE_FORM);
    }

    void placeMarker(int x, int z) {
        formType = FORM_MARKER;
        formId = "";
        formName = "";
        formKind = MapData.MARKER_GENERIC;
        formDimension = dimension();
        formX = x;
        formY = surfaceY(x, z);
        formZ = z;
        cancelTool();
        go(PAGE_FORM);
    }

    void editMarker(MapData.Marker marker) {
        formType = FORM_MARKER;
        formId = marker.id();
        formName = marker.name();
        formKind = marker.kind();
        formDimension = marker.dimension();
        formX = marker.x();
        formY = marker.y();
        formZ = marker.z();
        go(PAGE_FORM);
    }

    void editRoad(MapData.Road road) {
        formType = FORM_ROAD;
        formId = road.id();
        formName = road.name();
        formKind = road.kind();
        formDimension = road.dimension();
        formPoints = road.points();
        go(PAGE_FORM);
    }

    void editArea(MapData.Area area) {
        formType = FORM_AREA;
        formId = area.id();
        formName = area.name();
        formKind = area.kind();
        formDimension = area.dimension();
        formPoints = area.points();
        go(PAGE_FORM);
    }

    int formKindCount() {
        return switch (formType) {
            case FORM_ROAD -> MapData.ROAD_KIND_COUNT;
            case FORM_AREA -> MapData.AREA_KIND_COUNT;
            case FORM_MARKER -> MapData.MARKER_KIND_COUNT;
            default -> 0;
        };
    }

    void cycleFormKind(int delta) {
        int count = formKindCount();
        if (count > 0) {
            formKind = Math.floorMod(formKind + delta, count);
        }
    }

    boolean canSubmitForm() {
        return !MapData.clip(formName, MapData.MAX_NAME_LENGTH).isEmpty() || formType == FORM_PIN;
    }

    void submitForm() {
        String name = MapData.clip(formName, MapData.MAX_NAME_LENGTH);
        switch (formType) {
            case FORM_PIN -> {
                if (name.isEmpty()) {
                    name = String.format(Locale.ROOT, "%d, %d", formX, formZ);
                }
                MapNetworkingClient.addPin(screen.getOpenHand(), name, formDimension, formX, formY, formZ);
                status(Component.translatable("map.bphone.pin.saved", name));
                clearSelection();
            }
            case FORM_MARKER -> {
                if (name.isEmpty()) {
                    return;
                }
                MapNetworkingClient.saveMarker(new MapData.Marker(formId, formDimension, name, formKind, formX, formY, formZ, ""));
            }
            case FORM_ROAD -> {
                if (name.isEmpty()) {
                    return;
                }
                MapNetworkingClient.saveRoad(new MapData.Road(formId, formDimension, name, formKind, formPoints));
            }
            default -> {
                if (name.isEmpty()) {
                    return;
                }
                MapNetworkingClient.saveArea(new MapData.Area(formId, formDimension, name, formKind, formPoints));
            }
        }
        history.clear();
        page = PAGE_MAP;
    }

    void deleteForm() {
        if (formId.isEmpty() || formType == FORM_PIN) {
            return;
        }
        if (deleteConfirm <= 0) {
            deleteConfirm = CONFIRM_TICKS;
            return;
        }
        deleteConfirm = 0;
        int op = switch (formType) {
            case FORM_ROAD -> MapNetworking.OP_DELETE_ROAD;
            case FORM_AREA -> MapNetworking.OP_DELETE_AREA;
            default -> MapNetworking.OP_DELETE_MARKER;
        };
        MapNetworkingClient.delete(op, formId);
        if (selectType == SELECT_MARKER && selectId.equals(formId)) {
            clearSelection();
        }
        history.clear();
        page = PAGE_MAP;
    }

    void openLineForm(MapData.Line line) {
        lineFormId = line == null ? "" : line.id();
        lineFormName = line == null ? "" : line.name();
        lineFormColor = line == null ? MapData.LINE_COLORS[MapClientState.lines().size() % MapData.LINE_COLORS.length]
                : line.color();
        lineFormStations.clear();
        if (line != null) {
            lineFormStations.addAll(line.stations());
        }
        lineFormScroll = 0;
        go(PAGE_LINE_FORM);
    }

    void toggleLineStation(String markerId) {
        if (!lineFormStations.remove(markerId) && lineFormStations.size() < MapData.MAX_LINE_STATIONS) {
            lineFormStations.add(markerId);
        }
    }

    boolean canSubmitLine() {
        return !MapData.clip(lineFormName, MapData.MAX_NAME_LENGTH).isEmpty() && lineFormStations.size() >= 2;
    }

    void submitLine() {
        if (!canSubmitLine()) {
            return;
        }
        MapNetworkingClient.saveLine(new MapData.Line(lineFormId, MapData.clip(lineFormName, MapData.MAX_NAME_LENGTH),
                lineFormColor, List.copyOf(lineFormStations)));
        back();
    }

    void deleteLine() {
        if (lineFormId.isEmpty()) {
            return;
        }
        if (deleteConfirm <= 0) {
            deleteConfirm = CONFIRM_TICKS;
            return;
        }
        deleteConfirm = 0;
        MapNetworkingClient.delete(MapNetworking.OP_DELETE_LINE, lineFormId);
        back();
    }

    List<MapData.Marker> stationCandidates() {
        List<MapData.Marker> stations = new ArrayList<>();
        List<MapData.Marker> all = new ArrayList<>();
        for (MapData.Marker marker : MapClientState.allMarkers()) {
            if (marker.kind() == MapData.MARKER_LABEL) {
                continue;
            }
            all.add(marker);
            if (marker.kind() == MapData.MARKER_STATION) {
                stations.add(marker);
            }
        }
        List<MapData.Marker> result = stations.isEmpty() ? all : stations;
        result.sort(Comparator.comparing(MapData.Marker::name));
        return result;
    }

    void openSearch() {
        query = "";
        searchScroll = 0;
        go(PAGE_SEARCH);
    }

    private boolean matches(String name) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle);
    }

    double distanceTo(double x, double z) {
        LocalPlayer player = player();
        return player == null ? 0.0D : Math.hypot(x + 0.5D - player.getX(), z + 0.5D - player.getZ());
    }

    List<MapData.Marker> searchPlaces() {
        List<MapData.Marker> result = new ArrayList<>();
        for (MapData.Marker marker : MapClientState.markers(dimension())) {
            if (matches(marker.name())) {
                result.add(marker);
            }
        }
        result.sort(Comparator.comparingDouble(marker -> distanceTo(marker.x(), marker.z())));
        return result;
    }

    List<MapData.Line> searchLines() {
        List<MapData.Line> result = new ArrayList<>();
        for (MapData.Line line : MapClientState.lines()) {
            if (matches(line.name())) {
                result.add(line);
            }
        }
        return result;
    }

    List<PhoneMapPins.Pin> searchPins() {
        List<PhoneMapPins.Pin> result = new ArrayList<>();
        for (PhoneMapPins.Pin pin : pins()) {
            if (matches(pin.name())) {
                result.add(pin);
            }
        }
        return result;
    }

    void showMarker(MapData.Marker marker) {
        history.clear();
        page = PAGE_MAP;
        selectMarker(marker);
        if (marker.dimension().equals(dimension())) {
            focus(marker.x() + 0.5D, marker.z() + 0.5D, 1.0F);
        } else {
            status(Component.translatable("map.bphone.nav.other_dimension"));
        }
    }

    void showPin(PhoneMapPins.Pin pin) {
        history.clear();
        page = PAGE_MAP;
        selectPin(pin);
        if (pin.dimension().equals(dimension())) {
            focus(pin.x() + 0.5D, pin.z() + 0.5D, 1.0F);
        } else {
            status(Component.translatable("map.bphone.nav.other_dimension"));
        }
    }

    void openLine(MapData.Line line) {
        lineId = line.id();
        lineScroll = 0;
        go(PAGE_LINE);
    }

    private boolean hasField() {
        return page == PAGE_SEARCH || page == PAGE_FORM || page == PAGE_LINE_FORM;
    }

    private String field() {
        return switch (page) {
            case PAGE_SEARCH -> query;
            case PAGE_FORM -> formName;
            case PAGE_LINE_FORM -> lineFormName;
            default -> "";
        };
    }

    private void setField(String value) {
        switch (page) {
            case PAGE_SEARCH -> {
                query = value;
                searchScroll = 0;
            }
            case PAGE_FORM -> formName = value;
            case PAGE_LINE_FORM -> lineFormName = value;
            default -> {
            }
        }
    }

    boolean keyPressed(int keyCode, boolean control) {
        if (!hasField()) {
            return false;
        }
        String value = field();
        if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (!value.isEmpty()) {
                setField(value.substring(0, value.length() - 1));
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (page == PAGE_FORM && canSubmitForm()) {
                submitForm();
            } else if (page == PAGE_LINE_FORM) {
                submitLine();
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_V && control) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null) {
                for (int index = 0; index < clipboard.length(); index++) {
                    charTyped(clipboard.charAt(index));
                }
            }
            return true;
        }
        return false;
    }

    boolean charTyped(char character) {
        if (!hasField()) {
            return false;
        }
        if (Character.isISOControl(character)) {
            return true;
        }
        String value = field();
        if (value.length() < MapData.MAX_NAME_LENGTH) {
            setField(value + character);
        }
        return true;
    }

    static int surfaceY(int x, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            int height = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (height > minecraft.level.getMinBuildHeight()) {
                return height;
            }
        }
        LocalPlayer player = player();
        return player == null ? 64 : Mth.floor(player.getY());
    }
}
