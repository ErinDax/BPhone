package cn.erindax.bphone.client.map;

import cn.erindax.bphone.map.MapData;
import cn.erindax.bphone.map.MapNetworking;
import cn.erindax.bphone.network.NetworkBufferUtils;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

public final class MapNetworkingClient {
    private static boolean initialized;

    private MapNetworkingClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkManager.registerReceiver(NetworkManager.s2c(), MapNetworking.SYNC, (buf, context) -> {
            int section = buf.readByte();
            buf.readVarLong();
            int count = buf.readVarInt();
            switch (section) {
                case MapNetworking.SECTION_MARKERS -> {
                    List<MapData.Marker> markers = new ArrayList<>(Math.min(count, MapData.MAX_MARKERS));
                    for (int index = 0; index < count; index++) {
                        MapData.Marker marker = MapData.readMarker(buf);
                        if (index < MapData.MAX_MARKERS) {
                            markers.add(marker);
                        }
                    }
                    context.queue(() -> MapClientState.applyMarkers(markers));
                }
                case MapNetworking.SECTION_ROADS -> {
                    List<MapData.Road> roads = new ArrayList<>(Math.min(count, MapData.MAX_ROADS));
                    for (int index = 0; index < count; index++) {
                        MapData.Road road = MapData.readRoad(buf);
                        if (index < MapData.MAX_ROADS) {
                            roads.add(road);
                        }
                    }
                    context.queue(() -> MapClientState.applyRoads(roads));
                }
                case MapNetworking.SECTION_AREAS -> {
                    List<MapData.Area> areas = new ArrayList<>(Math.min(count, MapData.MAX_AREAS));
                    for (int index = 0; index < count; index++) {
                        MapData.Area area = MapData.readArea(buf);
                        if (index < MapData.MAX_AREAS) {
                            areas.add(area);
                        }
                    }
                    context.queue(() -> MapClientState.applyAreas(areas));
                }
                default -> {
                    List<MapData.Line> lines = new ArrayList<>(Math.min(count, MapData.MAX_LINES));
                    for (int index = 0; index < count; index++) {
                        MapData.Line line = MapData.readLine(buf);
                        if (index < MapData.MAX_LINES) {
                            lines.add(line);
                        }
                    }
                    context.queue(() -> MapClientState.applyLines(lines));
                }
            }
        });
    }

    public static boolean canEdit() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasPermissions(MapNetworking.EDIT_PERMISSION);
    }

    public static void saveMarker(MapData.Marker marker) {
        RegistryFriendlyByteBuf buf = edit(MapNetworking.OP_UPSERT_MARKER);
        MapData.writeMarker(buf, marker);
        NetworkManager.sendToServer(MapNetworking.EDIT, buf);
    }

    public static void saveRoad(MapData.Road road) {
        RegistryFriendlyByteBuf buf = edit(MapNetworking.OP_UPSERT_ROAD);
        MapData.writeRoad(buf, road);
        NetworkManager.sendToServer(MapNetworking.EDIT, buf);
    }

    public static void saveArea(MapData.Area area) {
        RegistryFriendlyByteBuf buf = edit(MapNetworking.OP_UPSERT_AREA);
        MapData.writeArea(buf, area);
        NetworkManager.sendToServer(MapNetworking.EDIT, buf);
    }

    public static void saveLine(MapData.Line line) {
        RegistryFriendlyByteBuf buf = edit(MapNetworking.OP_UPSERT_LINE);
        MapData.writeLine(buf, line);
        NetworkManager.sendToServer(MapNetworking.EDIT, buf);
    }

    public static void delete(int op, String id) {
        RegistryFriendlyByteBuf buf = edit(op);
        buf.writeUtf(id, MapData.MAX_ID_LENGTH);
        NetworkManager.sendToServer(MapNetworking.EDIT, buf);
    }

    public static void addPin(InteractionHand hand, String name, String dimension, int x, int y, int z) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeByte(MapNetworking.PIN_ADD);
        buf.writeBoolean(hand == InteractionHand.OFF_HAND);
        buf.writeUtf("", MapData.MAX_ID_LENGTH);
        buf.writeUtf(MapData.clip(name, MapData.MAX_NAME_LENGTH), MapData.MAX_NAME_LENGTH);
        buf.writeUtf(dimension, MapData.MAX_DIMENSION_LENGTH);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        NetworkManager.sendToServer(MapNetworking.PIN, buf);
    }

    public static void removePin(InteractionHand hand, String id) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeByte(MapNetworking.PIN_REMOVE);
        buf.writeBoolean(hand == InteractionHand.OFF_HAND);
        buf.writeUtf(id, MapData.MAX_ID_LENGTH);
        NetworkManager.sendToServer(MapNetworking.PIN, buf);
    }

    private static RegistryFriendlyByteBuf edit(int op) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeByte(op);
        return buf;
    }
}
