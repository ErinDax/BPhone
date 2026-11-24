package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.EconomyBridge;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.announcement.AnnouncementNetworking;
import cn.erindax.bphone.client.announcement.AnnouncementClientState;
import cn.erindax.bphone.client.map.MapClientState;
import cn.erindax.bphone.client.map.MapNavigation;
import cn.erindax.bphone.map.MapData;
import cn.erindax.bphone.map.MapNetworking;
import cn.erindax.bphone.map.MapStore;
import cn.erindax.bphone.map.PhoneMapPins;
import cn.erindax.bphone.notification.NotificationApi;
import cn.erindax.bphone.phone.PhoneData;
import cn.erindax.bphone.phone.PhoneSettingsData;
import cn.erindax.bphone.phone.chat.ChatPayloads;
import cn.erindax.bphone.phone.chat.ChatService;
import cn.erindax.bphone.phone.chat.ChatStore;
import cn.erindax.bphone.shop.ShopCatalog;
import cn.erindax.bphone.shop.StallStore;
import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class DevUiSnapshots {
    private static final String TRIGGER = "bphone_ui_snapshots.txt";
    private static final String WORLD = "bphone_ui";
    private static final UUID SAKURA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID JIE = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID MIKA = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID WANG = UUID.fromString("00000000-0000-4000-8000-000000000004");

    private record Scene(String name, Consumer<Minecraft> setup, boolean phoneCrop) {
    }

    private static Path output;
    private static Set<String> filter = Set.of();
    private static int stage;
    private static int wait;
    private static final List<Scene> SCENES = new ArrayList<>();
    private static int sceneIndex;
    private static boolean sceneOpened;
    private static int spawnX;
    private static int spawnZ;

    private DevUiSnapshots() {
    }

    public static void init() {
        Path trigger = dev.architectury.platform.Platform.getGameFolder().resolve(TRIGGER);
        if (!Files.isRegularFile(trigger)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(trigger, StandardCharsets.UTF_8);
            output = Path.of(lines.get(0).trim());
            if (lines.size() > 1 && !lines.get(1).isBlank()) {
                filter = Set.of(lines.get(1).trim().split("\\s*,\\s*"));
            }
            Files.createDirectories(output);
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone dev] bad trigger file", exception);
            return;
        }
        ClientTickEvent.CLIENT_POST.register(DevUiSnapshots::tick);
    }

    private static void tick(Minecraft minecraft) {
        try {
            switch (stage) {
                case 0 -> {
                    if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                        createWorld(minecraft);
                        stage = 1;
                    }
                }
                case 1 -> {
                    if (minecraft.player != null && minecraft.level != null && minecraft.getSingleplayerServer() != null
                            && minecraft.screen == null) {
                        setupServer(minecraft);
                        wait = 220;
                        stage = 2;
                    }
                }
                case 2 -> {
                    if (--wait <= 0) {
                        injectChat(minecraft);
                        buildScenes();
                        stage = 3;
                    }
                }
                case 3 -> runScenes(minecraft);
                default -> {
                    if (--wait <= 0) {
                        minecraft.stop();
                    }
                }
            }
        } catch (Throwable throwable) {
            BPhone.LOGGER.error("[BPhone dev] snapshot harness failed", throwable);
            stage = 9;
            wait = 5;
        }
    }

    private static void createWorld(Minecraft minecraft) throws Exception {
        Path saves = minecraft.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(saves)) {
            try (Stream<Path> walk = Files.walk(saves)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261106L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL)
                        .value().createWorldDimensions(), new TitleScreen());
    }

    private static void setupServer(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        UUID self = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(self);
            if (player == null) {
                return;
            }
            server.overworld().setDayTime(6000L);
            server.overworld().setWeatherParameters(12000, 0, false, false);
            player.getInventory().setItem(0, new ItemStack(ModItems.PHONE_AQUA.get()));
            player.getInventory().selected = 0;
            BlockPos pos = player.blockPosition();
            int x = pos.getX();
            int z = pos.getZ();
            spawnX = x;
            spawnZ = z;
            buildMap(player, x, pos.getY(), z);
            ItemStack phone = player.getInventory().getItem(0);
            PhoneMapPins.add(phone, new PhoneMapPins.Pin("p_home", "我的家", "minecraft:overworld", x + 30, pos.getY(), z + 52));
            PhoneData.markDirty(player);
            EconomyBridge.depositTo(server, self, 1280L, Component.literal("dev"));
            ShopCatalog.setStallsEnabled(server, true);
            StallStore.add(new StallStore.Listing(UUID.randomUUID(), JIE, "阿杰", new ItemStack(Items.DIAMOND, 3), 300L,
                    System.currentTimeMillis()));
            StallStore.add(new StallStore.Listing(UUID.randomUUID(), MIKA, "Mika", new ItemStack(Items.COOKED_SALMON, 16),
                    48L, System.currentTimeMillis()));
            AnnouncementNetworking.publishAs(server, "市政厅", null, "新版手机地图上线",
                    "打开手机里的「地图」就能查看站点、线路和街道，还可以一键导航。", false);
            AnnouncementNetworking.publishAs(server, "市政厅", null, "本周六晚 8 点城市庆典",
                    "中央公园将举办烟花表演，当晚 19:00 起长安大道临时封路，请绕行梧桐路。", true);
            NotificationApi.push(player, "银行", "到账提醒", "工资 1280 已到账");
            NotificationApi.push(player, "BRP", "天气预警", "明天有雷阵雨，出门记得带伞");
        });
    }

    private static List<MapData.Point> points(int baseX, int baseZ, int... offsets) {
        List<MapData.Point> result = new ArrayList<>();
        for (int index = 0; index + 1 < offsets.length; index += 2) {
            result.add(new MapData.Point(baseX + offsets[index], baseZ + offsets[index + 1]));
        }
        return List.copyOf(result);
    }

    private static void buildMap(ServerPlayer player, int x, int y, int z) {
        String dim = "minecraft:overworld";
        MapStore.upsertRoad(new MapData.Road("r_avenue", dim, "长安大道", MapData.ROAD_AVENUE, points(x, z, -180, 0, 180, 0)));
        MapStore.upsertRoad(new MapData.Road("r_wutong", dim, "梧桐路", MapData.ROAD_STREET, points(x, z, 40, -130, 40, 130)));
        MapStore.upsertRoad(new MapData.Road("r_sakura", dim, "樱花街", MapData.ROAD_STREET,
                points(x, z, -120, -60, -20, -60, 40, -30)));
        MapStore.upsertRoad(new MapData.Road("r_river", dim, "滨河步道", MapData.ROAD_PATH,
                points(x, z, -120, 50, -60, 72, 0, 62, 60, 84, 120, 80)));
        MapStore.upsertRoad(new MapData.Road("r_rail", dim, "城际铁路", MapData.ROAD_RAIL, points(x, z, -190, -100, 190, -88)));
        MapStore.upsertRoad(new MapData.Road("r_north", dim, "北环路", MapData.ROAD_STREET, points(x, z, -60, -60, -60, 0)));
        MapStore.upsertArea(new MapData.Area("a_old", dim, "老城区", MapData.AREA_DISTRICT,
                points(x, z, -170, -140, 10, -140, 10, -8, -170, -8)));
        MapStore.upsertArea(new MapData.Area("a_park", dim, "中央公园", MapData.AREA_PARK,
                points(x, z, -95, 10, -25, 10, -20, 44, -90, 46)));
        MapStore.upsertArea(new MapData.Area("a_lake", dim, "镜湖", MapData.AREA_WATER,
                points(x, z, -80, 18, -55, 15, -45, 30, -62, 40, -82, 33)));
        MapStore.upsertArea(new MapData.Area("a_hall", dim, "市政大楼", MapData.AREA_BUILDING,
                points(x, z, 50, 8, 75, 8, 75, 28, 50, 28)));
        MapStore.upsertArea(new MapData.Area("a_plaza", dim, "站前广场", MapData.AREA_PLAZA,
                points(x, z, 46, -24, 72, -24, 72, -6, 46, -6)));
        MapStore.upsertMarker(new MapData.Marker("m_central", dim, "中央车站", MapData.MARKER_STATION, x + 40, y, z - 4, ""));
        MapStore.upsertMarker(new MapData.Marker("m_sakura", dim, "樱花站", MapData.MARKER_STATION, x - 60, y, z - 64, ""));
        MapStore.upsertMarker(new MapData.Marker("m_river", dim, "滨河站", MapData.MARKER_STATION, x + 100, y, z + 82, ""));
        MapStore.upsertMarker(new MapData.Marker("m_shop", dim, "便利店", MapData.MARKER_SHOP, x + 20, y, z + 14, ""));
        MapStore.upsertMarker(new MapData.Marker("m_food", dim, "拉面馆", MapData.MARKER_FOOD, x - 12, y, z - 16, ""));
        MapStore.upsertMarker(new MapData.Marker("m_hospital", dim, "市立医院", MapData.MARKER_HOSPITAL, x + 72, y, z - 48, ""));
        MapStore.upsertMarker(new MapData.Marker("m_police", dim, "警察局", MapData.MARKER_POLICE, x + 18, y, z + 36, ""));
        MapStore.upsertMarker(new MapData.Marker("m_gov", dim, "市政府", MapData.MARKER_GOVERNMENT, x + 62, y, z + 18, ""));
        MapStore.upsertMarker(new MapData.Marker("m_bank", dim, "城市银行", MapData.MARKER_BANK, x + 24, y, z - 14, ""));
        MapStore.upsertMarker(new MapData.Marker("m_clock", dim, "钟楼", MapData.MARKER_LANDMARK, x - 30, y, z - 36, ""));
        MapStore.upsertMarker(new MapData.Marker("m_label", dim, "老城区", MapData.MARKER_LABEL, x - 100, y, z - 100, ""));
        MapStore.upsertLine(new MapData.Line("l_1", "1号线", 0xE53935, List.of("m_sakura", "m_central", "m_river")));
        for (int section = MapNetworking.SECTION_MARKERS; section <= MapNetworking.SECTION_LINES; section++) {
            MapNetworking.broadcast(player.getServer(), section);
        }
    }

    private static void injectChat(Minecraft minecraft) {
        UUID self = minecraft.player.getUUID();
        String own = PhoneData.getPhoneNumber(minecraft.player);
        long now = System.currentTimeMillis();
        List<ChatPayloads.FriendData> friends = List.of(
                new ChatPayloads.FriendData("小樱", "10234", SAKURA),
                new ChatPayloads.FriendData("阿杰", "20345", JIE),
                new ChatPayloads.FriendData("Mika", "30456", MIKA),
                new ChatPayloads.FriendData("老王", "40567", WANG));
        List<ChatPayloads.MemberData> directMembers = List.of(
                new ChatPayloads.MemberData(self, own, "Dev", 4L),
                new ChatPayloads.MemberData(SAKURA, "10234", "小樱", 3L));
        List<ChatPayloads.EntryData> directEntries = List.of(
                new ChatPayloads.EntryData(1L, SAKURA, "10234", "小樱", ChatStore.ENTRY_TEXT, "在吗？晚上一起去集市逛逛", "", now - 3_600_000L),
                new ChatPayloads.EntryData(2L, self, own, "Dev", ChatStore.ENTRY_TEXT, "好呀，几点出发？", "", now - 3_500_000L),
                new ChatPayloads.EntryData(3L, SAKURA, "10234", "小樱", ChatStore.ENTRY_TEXT, "七点，中央车站门口见，记得带上手机，我们用地图导航过去", "", now - 600_000L),
                new ChatPayloads.EntryData(4L, self, own, "Dev", ChatStore.ENTRY_TEXT, "收到", "", now - 500_000L),
                new ChatPayloads.EntryData(5L, SAKURA, "10234", "小樱", ChatStore.ENTRY_TEXT, "顺便帮我带一瓶矿泉水", "", now - 60_000L));
        String sep = ChatService.SYSTEM_SEPARATOR;
        List<ChatPayloads.MemberData> groupMembers = List.of(
                new ChatPayloads.MemberData(self, own, "Dev", 14L),
                new ChatPayloads.MemberData(JIE, "20345", "阿杰", 14L),
                new ChatPayloads.MemberData(MIKA, "30456", "Mika", 13L),
                new ChatPayloads.MemberData(SAKURA, "10234", "小樱", 12L));
        List<ChatPayloads.EntryData> groupEntries = List.of(
                new ChatPayloads.EntryData(10L, null, "00000", "", ChatStore.ENTRY_SYSTEM,
                        "phone.chat.system.group_created" + sep + "Dev" + sep + "周末钓鱼小队", "", now - 7_200_000L),
                new ChatPayloads.EntryData(11L, JIE, "20345", "阿杰", ChatStore.ENTRY_TEXT, "我带鱼竿和折叠椅", "", now - 7_000_000L),
                new ChatPayloads.EntryData(12L, MIKA, "30456", "Mika", ChatStore.ENTRY_TEXT, "I'll bring charcoal for grilling!", "", now - 6_900_000L),
                new ChatPayloads.EntryData(13L, self, own, "Dev", ChatStore.ENTRY_TEXT, "我负责开车，周六早上八点出发", "", now - 6_800_000L),
                new ChatPayloads.EntryData(14L, JIE, "20345", "阿杰", ChatStore.ENTRY_TEXT, "没问题", "", now - 120_000L));
        List<ChatPayloads.ConversationData> conversations = List.of(
                new ChatPayloads.ConversationData(1L, ChatStore.KIND_DIRECT, "小樱", "10234", null, now - 60_000L, 4L, 1, false,
                        directMembers, directEntries),
                new ChatPayloads.ConversationData(2L, ChatStore.KIND_GROUP, "周末钓鱼小队", "", self, now - 120_000L, 13L, 1, false,
                        groupMembers, groupEntries),
                new ChatPayloads.ConversationData(3L, ChatStore.KIND_DIRECT, "老王", "40567", null, now - 86_400_000L, 21L, 0, false,
                        List.of(new ChatPayloads.MemberData(self, own, "Dev", 21L),
                                new ChatPayloads.MemberData(WANG, "40567", "老王", 21L)),
                        List.of(new ChatPayloads.EntryData(21L, WANG, "40567", "老王", ChatStore.ENTRY_TEXT,
                                "房租这周记得转我", "", now - 86_400_000L))));
        PhoneClientChatState.applyState(new ChatPayloads.StateData("Dev", friends,
                List.of(new ChatPayloads.FriendData("骚扰电话", "66666", null)), conversations));
        PhoneClientChatState.applyRequests(List.of(new PhoneClientChatState.FriendRequest(1L, "50678", "路人甲", now)));
        injectPublic(minecraft);
    }

    private static void injectPublic(Minecraft minecraft) {
        PhonePublicChat.clear();
        String selfName = minecraft.player.getGameProfile().getName();
        minecraft.gui.getChat().addMessage(Component.translatable("multiplayer.player.joined", "Mika"));
        PhonePublicChat.addPlayer(MIKA, "Mika", "hi everyone, anyone near the central station?");
        PhonePublicChat.addPlayer(JIE, "阿杰", "我在，刚从便利店出来");
        PhonePublicChat.addPlayer(JIE, "阿杰", "「Mika: anyone near the central station?」 周六的钓鱼还去吗 @" + selfName);
        minecraft.gui.getChat().addMessage(Component.translatable("chat.type.text", "小樱",
                "去呀，我已经把鱼饵买好了，到时候中央车站门口集合"));
        minecraft.gui.getChat().addMessage(Component.literal("<路人甲> 这条不是在线玩家发的"));
        minecraft.player.connection.sendChat("我也去，开车带你们");
        minecraft.player.connection.sendCommand("me 挥了挥手");
    }

    private static PhoneScreen phone(Minecraft minecraft) {
        return minecraft.screen instanceof PhoneScreen screen ? screen : null;
    }

    private static void call(Object target, String name, Object... args) {
        try {
            for (Method method : target.getClass().getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    method.setAccessible(true);
                    method.invoke(target, args);
                    return;
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException(name, exception);
        }
        throw new IllegalStateException("no method " + name);
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception exception) {
            throw new IllegalStateException(name, exception);
        }
    }

    private static void home(PhoneScreen screen) {
        set(screen, "unlocked", true);
        call(screen, "rebuildWidgets");
    }

    private static void scene(String name, Consumer<PhoneScreen> setup) {
        SCENES.add(new Scene(name, minecraft -> {
            PhoneScreen screen = new PhoneScreen(InteractionHand.MAIN_HAND);
            minecraft.setScreen(screen);
            setup.accept(screen);
        }, true));
    }

    private static void world(String name, Consumer<Minecraft> setup) {
        SCENES.add(new Scene(name, setup, false));
    }

    private static MapData.Marker marker(String id) {
        return MapClientState.marker(id);
    }

    private static void buildScenes() {
        scene("00_lock", screen -> {
        });
        scene("01_home", DevUiSnapshots::home);
        scene("10_chat_list", screen -> call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS));
        scene("11_chat_contacts", screen -> call(screen, "openChatApp", PhoneChatLayout.TAB_CONTACTS));
        scene("12_chat_profile", screen -> call(screen, "openChatApp", PhoneChatLayout.TAB_PROFILE));
        scene("13_chat_thread", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openConversation(PhoneClientChatState.getConversation(1L));
            screen.chat.draft = "我已经到车站了";
        });
        scene("14_chat_group", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openConversation(PhoneClientChatState.getConversation(2L));
        });
        scene("15_chat_group_info", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openConversation(PhoneClientChatState.getConversation(2L));
            screen.chat.go(PhoneChatApp.PAGE_INFO);
        });
        scene("16_chat_add", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.go(PhoneChatApp.PAGE_ADD);
        });
        scene("17_chat_search", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.go(PhoneChatApp.PAGE_SEARCH);
            screen.chat.searchQuery = "车站";
            screen.chat.searchedQuery = "车站";
            screen.chat.searchHits = List.of(new ChatPayloads.SearchHitData(1L, "小樱",
                    PhoneClientChatState.getConversation(1L).entries.get(2)));
        });
        scene("17b_public", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openPublic(false);
            screen.chat.publicDraft = "大家晚上好";
        });
        scene("17c_public_reply_emoji", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openPublic(false);
            screen.chat.setPublicReply("小樱", "去呀，我已经把鱼饵买好了");
            screen.chat.publicDraft = "太棒了 ";
            screen.chat.publicEmojiOpen = true;
        });
        scene("18_chat_dark", screen -> {
            screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE, "1");
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
        });
        scene("19_chat_thread_dark", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openConversation(PhoneClientChatState.getConversation(2L));
        });
        scene("19b_public_dark", screen -> {
            call(screen, "openChatApp", PhoneChatLayout.TAB_CHATS);
            screen.chat.openPublic(false);
        });
        scene("20_map", screen -> {
            screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE, "0");
            screen.openMapApp();
        });
        scene("21_map_selected", screen -> {
            screen.openMapApp();
            screen.map.showMarker(marker("m_central"));
        });
        scene("22_map_nav", screen -> {
            screen.openMapApp();
            screen.map.selectMarker(marker("m_river"));
            screen.map.navigateToSelection();
        });
        scene("23_map_plain", screen -> {
            screen.openMapApp();
            screen.map.toggleStyle();
        });
        scene("24_map_search", screen -> {
            screen.map.style = PhoneMapApp.STYLE_TERRAIN;
            screen.openMapApp();
            screen.map.openSearch();
        });
        scene("25_map_line", screen -> {
            screen.openMapApp();
            screen.map.openLine(MapClientState.line("l_1"));
        });
        scene("26_map_edit", screen -> {
            screen.openMapApp();
            screen.map.toggleEditing();
            screen.map.startTool(PhoneMapApp.TOOL_ROAD);
            screen.map.addDraftPoint(spawnX - 30, spawnZ + 20);
            screen.map.addDraftPoint(spawnX + 10, spawnZ + 30);
            screen.map.addDraftPoint(spawnX + 20, spawnZ + 60);
        });
        scene("27_map_form", screen -> {
            screen.openMapApp();
            screen.map.toggleEditing();
            screen.map.editMarker(marker("m_hospital"));
        });
        scene("28_map_zoomout", screen -> {
            screen.openMapApp();
            screen.map.zoom = 0.3F;
        });
        scene("29_map_line_form", screen -> {
            screen.openMapApp();
            screen.map.openLineForm(MapClientState.line("l_1"));
        });
        world("29b_hud_nav", minecraft -> {
            minecraft.setScreen(null);
            MapData.Marker shop = marker("m_shop");
            double tx = shop != null ? shop.x() + 0.5D : spawnX + 20.5D;
            double ty = shop != null ? shop.y() : minecraft.player.getY();
            double tz = shop != null ? shop.z() + 0.5D : spawnZ + 14.5D;
            MapNavigation.start(new MapNavigation.Target("便利店", "minecraft:overworld", tx, ty, tz));
            minecraft.player.setYRot(-72.0F);
            minecraft.player.setXRot(22.0F);
        });
        world("29c_nav_phone_open", minecraft -> {
            PhoneScreen screen = new PhoneScreen(InteractionHand.MAIN_HAND);
            minecraft.setScreen(screen);
            screen.openMapApp();
            MapData.Marker shop = marker("m_shop");
            if (shop != null) {
                screen.map.selectMarker(shop);
                screen.map.navigateToSelection();
            }
        });
        scene("30_shop", screen -> {
            MapNavigation.stop();
            screen.openShopApp();
        });
        scene("31_shop_items", screen -> {
            screen.openShopApp();
            screen.openShopCategory(0);
        });
        scene("32_shop_detail", screen -> {
            screen.openShopApp();
            screen.openShopCategory(0);
            screen.openShopEntry(0);
        });
        scene("33_shop_stalls", screen -> {
            screen.openShopApp();
            screen.shopPage = PhoneShopSurfaceRenderer.PAGE_STALLS;
            cn.erindax.bphone.client.phone.ShopNetworkingClient.requestStalls();
        });
        scene("40_settings", ScreenActions::settings);
        scene("41_settings_ringtone", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_RINGTONE);
        });
        scene("42_settings_about", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_ABOUT);
        });
        scene("43_settings_wallpaper", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_HOME_WALLPAPER);
        });
        scene("44_settings_display", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_DISPLAY);
        });
        scene("45_settings_sound", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_SOUND);
        });
        scene("46_settings_chat", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_CHAT);
        });
        scene("46b_settings_chat_end", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_CHAT);
            for (int step = 0; step < 40; step++) {
                PhoneSettingsSurfaceRenderer.scroll(screen, -1.0D);
            }
        });
        scene("47_settings_dark", screen -> {
            screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE, "1");
            screen.openSettingsApp();
        });
        scene("48_settings_chat_dark", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_CHAT);
        });
        scene("49_settings_sound_dark", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_SOUND);
        });
        scene("50_bank", screen -> {
            screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE, "0");
            call(screen, "openBankApp");
        });
        scene("51_bank_scan", screen -> {
            call(screen, "openBankApp");
            call(screen, "openBankScanCamera");
        });
        scene("55_bank_transfer", screen -> {
            call(screen, "openBankApp");
            call(screen, "openBankTransferPage");
            screen.bankTransferNumber = "12345";
            screen.bankTransferAmount = "2500";
            screen.bankAmountFocused = true;
        });
        scene("56_bank_receive", screen -> {
            call(screen, "openBankApp");
            call(screen, "openBankReceivePage");
        });
        scene("57_bank_slip", screen -> {
            call(screen, "openBankApp");
            screen.handleBankTransferReceipt("12345", "Alex", 2500L, 97500L);
        });
        scene("58_bank_pay", screen -> {
            call(screen, "openBankApp");
            call(screen, "openBankPaymentPage", "12345", "Alex");
        });
        scene("59_bank_dark", screen -> {
            screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE, "1");
            call(screen, "openBankApp");
            call(screen, "openBankReceivePage");
        });
        world("52_qr_raise_fp", minecraft -> {
            PhoneScreen screen = new PhoneScreen(InteractionHand.MAIN_HAND);
            minecraft.setScreen(screen);
            screen.applyPhoneSetting(PhoneSettingsData.KEY_DARK_MODE, "0");
            screen.showChatQrToWorld();
        });
        world("53_qr_raise_tp", minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        world("54_qr_raise_side", minecraft -> {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.player.setYRot(minecraft.player.getYRot() + 90.0F);
        });
        scene("60_camera", screen -> {
            minecraft().options.setCameraType(CameraType.FIRST_PERSON);
            PhoneClientHooks.clearBankReceiveWorldState(true);
            call(screen, "openCamera");
        });
        world("62_camera_pose_tp", minecraft -> {
            PhoneScreen screen = new PhoneScreen(InteractionHand.MAIN_HAND);
            minecraft.setScreen(screen);
            call(screen, "openCamera");
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        scene("63_camera_shot", screen -> {
            minecraft().options.setCameraType(CameraType.FIRST_PERSON);
            call(screen, "openCamera");
            call(screen, "requestPhotoCapture");
        });
        scene("64_gallery", screen -> call(screen, "openGallery"));
        scene("65_photo_viewer", screen -> call(screen, "openPhotoViewer", 0));
        scene("66_call_dial", screen -> call(screen, "openCallApp"));
        scene("67_call_contacts", screen -> {
            call(screen, "openCallApp");
            call(screen, "openCallContactsPage");
        });
        scene("68_call_add_contact", screen -> {
            call(screen, "openCallApp");
            call(screen, "openCallContactsPage");
            call(screen, "openAddContactPage");
        });
        scene("70_announcements", screen -> {
            minecraft().options.setCameraType(CameraType.FIRST_PERSON);
            screen.openAnnouncementsApp(null);
        });
        scene("71_announcement_detail", screen -> {
            List<cn.erindax.bphone.announcement.Announcement> list = AnnouncementClientState.getAnnouncements();
            screen.openAnnouncementsApp(list.isEmpty() ? null : list.get(0).id());
        });
        scene("80_notifications", PhoneScreen::openNotificationsApp);
        scene("81_home_after", DevUiSnapshots::home);
        world("90_public_key", minecraft -> {
            minecraft.setScreen(null);
            minecraft.gui.getChat().addMessage(Component.literal(
                    "this vanilla chat line is long enough to reach past the sidebar and must stay hidden behind it"));
            call(minecraft, "openChatScreen", "");
        });
        world("91_public_standalone", minecraft -> {
            minecraft.setScreen(null);
            minecraft.player.getInventory().selected = 1;
            call(minecraft, "openChatScreen", "");
            if (minecraft.screen instanceof PhoneScreen screen) {
                screen.chat.publicDraft = "/time set day";
            }
        });
        world("92_modern_hud", minecraft -> {
            minecraft.setScreen(null);
            injectPublic(minecraft);
            PhonePublicChat.addPlayer(SAKURA, "小樱", "「阿杰: 周六的钓鱼还去吗」 我带水果和饮料 (^_^)b");
        });
        world("93_command_key", minecraft -> {
            minecraft.setScreen(null);
            call(minecraft, "openChatScreen", "/");
        });
        world("94_hide_vanilla", minecraft -> {
            minecraft.setScreen(null);
            minecraft.player.getInventory().selected = 0;
            PhoneClientPrefs.setHideVanillaChat(true);
            minecraft.gui.getChat().addMessage(Component.literal("this line must not show while vanilla chat is hidden"));
        });
        scene("95_settings_toggle", screen -> {
            screen.openSettingsApp();
            screen.openSettingsPage(PhoneSettingsSurfaceRenderer.PAGE_CHAT);
            PhoneChatLayout layout = new PhoneChatLayout(screen);
            int firstRowTop = layout.header().bottom() + 2 * layout.gap + layout.bodyHeight + 2 * layout.u(4)
                    + layout.sectionHeight + layout.u(3);
            PhoneSettingsSurfaceRenderer.handleClick(screen, layout.surface.left + layout.surface.width / 2.0D,
                    firstRowTop + layout.u(8));
        });
        world("96_key_off", minecraft -> {
            minecraft.setScreen(null);
            call(minecraft, "openChatScreen", "");
        });
        world("97_close_after_send", minecraft -> {
            minecraft.setScreen(null);
            PhoneClientPrefs.setPublicChatKey(true);
            PhoneClientPrefs.setHideVanillaChat(false);
            PhoneClientPrefs.setCloseAfterSend(true);
            call(minecraft, "openChatScreen", "");
            if (minecraft.screen instanceof PhoneScreen screen) {
                screen.chat.publicDraft = "sent with close after send";
                screen.chat.sendPublic();
            }
        });
    }

    private static void restorePrefs() {
        if (!PhoneClientPrefs.publicChatKey()) {
            PhoneClientPrefs.setPublicChatKey(true);
        }
        if (PhoneClientPrefs.closeAfterSend()) {
            PhoneClientPrefs.setCloseAfterSend(false);
        }
        if (PhoneClientPrefs.hideVanillaChat()) {
            PhoneClientPrefs.setHideVanillaChat(false);
        }
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static final class ScreenActions {
        private static void settings(PhoneScreen screen) {
            screen.openSettingsApp();
        }
    }

    private static void runScenes(Minecraft minecraft) throws Exception {
        while (sceneIndex < SCENES.size() && !filter.isEmpty() && !matches(SCENES.get(sceneIndex).name())) {
            sceneIndex++;
        }
        if (sceneIndex >= SCENES.size()) {
            minecraft.setScreen(null);
            restorePrefs();
            stage = 9;
            wait = 10;
            return;
        }
        Scene scene = SCENES.get(sceneIndex);
        if (!sceneOpened) {
            GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), 4.0D, 4.0D);
            scene.setup().accept(minecraft);
            sceneOpened = true;
            wait = 12;
            return;
        }
        if (--wait > 0) {
            return;
        }
        NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
        Path file = output.resolve(scene.name() + ".png");
        PhoneScreen screen = phone(minecraft);
        if (scene.phoneCrop() && screen != null && !screen.cameraMode) {
            double guiScale = minecraft.getWindow().getGuiScale();
            int margin = 6;
            int left = Math.max(0, (int) Math.floor((screen.displayX - margin) * guiScale));
            int top = Math.max(0, (int) Math.floor((screen.displayY - margin) * guiScale));
            int width = Math.min(image.getWidth() - left, (int) Math.ceil((screen.displayWidth + 2 * margin) * guiScale));
            int height = Math.min(image.getHeight() - top, (int) Math.ceil((screen.displayHeight + 2 * margin) * guiScale));
            NativeImage cropped = new NativeImage(width, height, false);
            image.copyRect(cropped, left, top, 0, 0, width, height, false, false);
            cropped.writeToFile(file);
            cropped.close();
        } else {
            image.writeToFile(file);
        }
        image.close();
        BPhone.LOGGER.info("[BPhone dev] saved {}", file);
        sceneIndex++;
        sceneOpened = false;
    }

    private static boolean matches(String name) {
        for (String prefix : filter) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
