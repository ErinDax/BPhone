package cn.erindax.bphone.map;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.network.NetworkBufferUtils;
import cn.erindax.bphone.phone.PhoneData;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MapNetworking {
    public static final int EDIT_PERMISSION = 2;
    public static final ResourceLocation SYNC = BPhone.id("map_sync");
    public static final ResourceLocation EDIT = BPhone.id("map_edit");
    public static final ResourceLocation PIN = BPhone.id("map_pin");

    public static final int SECTION_MARKERS = 0;
    public static final int SECTION_ROADS = 1;
    public static final int SECTION_AREAS = 2;
    public static final int SECTION_LINES = 3;

    public static final int OP_UPSERT_MARKER = 0;
    public static final int OP_DELETE_MARKER = 1;
    public static final int OP_UPSERT_ROAD = 2;
    public static final int OP_DELETE_ROAD = 3;
    public static final int OP_UPSERT_AREA = 4;
    public static final int OP_DELETE_AREA = 5;
    public static final int OP_UPSERT_LINE = 6;
    public static final int OP_DELETE_LINE = 7;

    public static final int PIN_ADD = 0;
    public static final int PIN_REMOVE = 1;

    private static boolean initialized;

    private MapNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(SYNC);

        NetworkManager.registerReceiver(NetworkManager.c2s(), EDIT, (buf, context) -> {
            int op = buf.readByte();
            Object payload = switch (op) {
                case OP_UPSERT_MARKER -> MapData.readMarker(buf);
                case OP_UPSERT_ROAD -> MapData.readRoad(buf);
                case OP_UPSERT_AREA -> MapData.readArea(buf);
                case OP_UPSERT_LINE -> MapData.readLine(buf);
                default -> buf.readUtf(MapData.MAX_ID_LENGTH);
            };
            context.queue(() -> applyEdit((ServerPlayer) context.getPlayer(), op, payload));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), PIN, (buf, context) -> {
            int op = buf.readByte();
            InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            PhoneMapPins.Pin pin = op == PIN_ADD
                    ? new PhoneMapPins.Pin(buf.readUtf(MapData.MAX_ID_LENGTH), buf.readUtf(MapData.MAX_NAME_LENGTH),
                    buf.readUtf(MapData.MAX_DIMENSION_LENGTH), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
                    : new PhoneMapPins.Pin(buf.readUtf(MapData.MAX_ID_LENGTH), "", "", 0, 0, 0);
            context.queue(() -> applyPin((ServerPlayer) context.getPlayer(), op, hand, pin));
        });

        PlayerEvent.PLAYER_JOIN.register(MapNetworking::syncAll);
    }

    public static boolean canEdit(ServerPlayer player) {
        return player != null && player.hasPermissions(EDIT_PERMISSION);
    }

    public static void syncAll(ServerPlayer player) {
        if (player == null || !MapStore.isOpen()) {
            return;
        }
        for (int section = SECTION_MARKERS; section <= SECTION_LINES; section++) {
            sendSection(player, section);
        }
    }

    public static void broadcast(MinecraftServer server, int section) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            sendSection(player, section);
        }
    }

    private static void sendSection(ServerPlayer player, int section) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeByte(section);
        buf.writeVarLong(MapStore.revision());
        switch (section) {
            case SECTION_MARKERS -> {
                List<MapData.Marker> markers = MapStore.markers();
                buf.writeVarInt(markers.size());
                markers.forEach(marker -> MapData.writeMarker(buf, marker));
            }
            case SECTION_ROADS -> {
                List<MapData.Road> roads = MapStore.roads();
                buf.writeVarInt(roads.size());
                roads.forEach(road -> MapData.writeRoad(buf, road));
            }
            case SECTION_AREAS -> {
                List<MapData.Area> areas = MapStore.areas();
                buf.writeVarInt(areas.size());
                areas.forEach(area -> MapData.writeArea(buf, area));
            }
            default -> {
                List<MapData.Line> lines = MapStore.lines();
                buf.writeVarInt(lines.size());
                lines.forEach(line -> MapData.writeLine(buf, line));
            }
        }
        NetworkManager.sendToPlayer(player, SYNC, buf);
    }

    private static void applyEdit(ServerPlayer player, int op, Object payload) {
        if (player == null || player.getServer() == null || !MapStore.isOpen()) {
            return;
        }
        if (!canEdit(player)) {
            player.displayClientMessage(Component.translatable("map.bphone.error.permission"), true);
            return;
        }
        String dimension = player.level().dimension().location().toString();
        boolean success;
        int section;
        boolean delete = false;
        switch (op) {
            case OP_UPSERT_MARKER -> {
                MapData.Marker marker = (MapData.Marker) payload;
                success = MapStore.upsertMarker(new MapData.Marker(idOrNew(marker.id(), "m"),
                        marker.dimension().isEmpty() ? dimension : marker.dimension(),
                        MapData.clip(marker.name(), MapData.MAX_NAME_LENGTH), marker.kind(),
                        marker.x(), marker.y(), marker.z(), MapData.clip(marker.note(), MapData.MAX_NOTE_LENGTH)));
                section = SECTION_MARKERS;
            }
            case OP_UPSERT_ROAD -> {
                MapData.Road road = (MapData.Road) payload;
                success = MapStore.upsertRoad(new MapData.Road(idOrNew(road.id(), "r"),
                        road.dimension().isEmpty() ? dimension : road.dimension(),
                        MapData.clip(road.name(), MapData.MAX_NAME_LENGTH), road.kind(), road.points()));
                section = SECTION_ROADS;
            }
            case OP_UPSERT_AREA -> {
                MapData.Area area = (MapData.Area) payload;
                success = MapStore.upsertArea(new MapData.Area(idOrNew(area.id(), "a"),
                        area.dimension().isEmpty() ? dimension : area.dimension(),
                        MapData.clip(area.name(), MapData.MAX_NAME_LENGTH), area.kind(), area.points()));
                section = SECTION_AREAS;
            }
            case OP_UPSERT_LINE -> {
                MapData.Line line = (MapData.Line) payload;
                List<String> stations = new ArrayList<>();
                for (String station : line.stations()) {
                    if (MapStore.findMarker(station) != null && !stations.contains(station)) {
                        stations.add(station);
                    }
                }
                success = MapStore.upsertLine(new MapData.Line(idOrNew(line.id(), "l"),
                        MapData.clip(line.name(), MapData.MAX_NAME_LENGTH), line.color() & 0xFFFFFF, List.copyOf(stations)));
                section = SECTION_LINES;
            }
            case OP_DELETE_MARKER -> {
                success = MapStore.deleteMarker((String) payload);
                section = SECTION_MARKERS;
                delete = true;
            }
            case OP_DELETE_ROAD -> {
                success = MapStore.deleteRoad((String) payload);
                section = SECTION_ROADS;
                delete = true;
            }
            case OP_DELETE_AREA -> {
                success = MapStore.deleteArea((String) payload);
                section = SECTION_AREAS;
                delete = true;
            }
            case OP_DELETE_LINE -> {
                success = MapStore.deleteLine((String) payload);
                section = SECTION_LINES;
                delete = true;
            }
            default -> {
                return;
            }
        }
        if (!success) {
            player.displayClientMessage(Component.translatable(delete ? "map.bphone.error.missing" : "map.bphone.error.full"), true);
            syncAll(player);
            return;
        }
        broadcast(player.getServer(), section);
        if (op == OP_DELETE_MARKER) {
            broadcast(player.getServer(), SECTION_LINES);
        }
        player.displayClientMessage(Component.translatable(delete ? "map.bphone.deleted" : "map.bphone.saved"), true);
    }

    private static void applyPin(ServerPlayer player, int op, InteractionHand hand, PhoneMapPins.Pin pin) {
        if (player == null) {
            return;
        }
        ItemStack phone = PhoneData.findPhoneStack(player, hand);
        if (phone.isEmpty()) {
            player.displayClientMessage(Component.translatable("screen.bphone.phone.call.error.no_phone"), true);
            return;
        }
        boolean changed;
        if (op == PIN_ADD) {
            String name = MapData.clip(pin.name(), MapData.MAX_NAME_LENGTH);
            String dimension = pin.dimension().isEmpty() ? player.level().dimension().location().toString() : pin.dimension();
            changed = PhoneMapPins.add(phone, new PhoneMapPins.Pin(idOrNew(pin.id(), "p"), name, dimension,
                    pin.x(), pin.y(), pin.z()));
            if (!changed) {
                player.displayClientMessage(Component.translatable("map.bphone.pin.full", PhoneMapPins.MAX_PINS), true);
                return;
            }
        } else {
            changed = PhoneMapPins.remove(phone, pin.id());
        }
        if (changed) {
            PhoneData.markDirty(player);
        }
    }

    private static String idOrNew(String id, String prefix) {
        String clean = MapData.clip(id, MapData.MAX_ID_LENGTH);
        return clean.isEmpty() ? MapStore.newId(prefix) : clean;
    }
}
