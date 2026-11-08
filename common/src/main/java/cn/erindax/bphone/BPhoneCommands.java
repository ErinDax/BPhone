package cn.erindax.bphone;

import cn.erindax.bphone.announcement.Announcement;
import cn.erindax.bphone.announcement.AnnouncementNetworking;
import cn.erindax.bphone.api.economy.EconomyResult;
import cn.erindax.bphone.economy.BuiltinEconomyProvider;
import cn.erindax.bphone.map.MapData;
import cn.erindax.bphone.map.MapNetworking;
import cn.erindax.bphone.map.MapStore;
import cn.erindax.bphone.shop.ShopCatalog;
import cn.erindax.bphone.shop.ShopNetworking;
import cn.erindax.bphone.shop.StallStore;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class BPhoneCommands {
    private static final int PERMISSION = 2;

    private enum MoneyAction {
        BALANCE,
        GIVE,
        TAKE,
        SET
    }

    private BPhoneCommands() {
    }

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bphone")
                .requires(source -> source.hasPermission(PERMISSION))
                .then(moneyCommand())
                .then(Commands.literal("shop")
                        .then(Commands.literal("reload").executes(BPhoneCommands::shopReload))
                        .then(Commands.literal("stalls")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> stalls(context, BoolArgumentType.getBool(context, "enabled"))))))
                .then(Commands.literal("announce")
                        .then(Commands.literal("star").then(announceArguments(true)))
                        .then(announceArguments(false)))
                .then(Commands.literal("map")
                        .then(Commands.literal("reload").executes(BPhoneCommands::mapReload))
                        .then(Commands.literal("marker")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(MapData.MARKER_KIND_NAMES, builder))
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(BPhoneCommands::markerAdd))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(BPhoneCommands::markerRemove))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moneyCommand() {
        return Commands.literal("money")
                .executes(BPhoneCommands::moneyInfo)
                .then(Commands.literal("balance")
                        .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .executes(context -> money(context, MoneyAction.BALANCE, 0L))))
                .then(Commands.literal("give")
                        .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                        .executes(context -> money(context, MoneyAction.GIVE,
                                                LongArgumentType.getLong(context, "amount"))))))
                .then(Commands.literal("take")
                        .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                        .executes(context -> money(context, MoneyAction.TAKE,
                                                LongArgumentType.getLong(context, "amount"))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                        .executes(context -> money(context, MoneyAction.SET,
                                                LongArgumentType.getLong(context, "amount"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> announceArguments(boolean starred) {
        return Commands.argument("title", StringArgumentType.string())
                .executes(context -> announce(context, starred, ""))
                .then(Commands.argument("body", StringArgumentType.greedyString())
                        .executes(context -> announce(context, starred, StringArgumentType.getString(context, "body"))));
    }

    private static int moneyInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!EconomyBridge.available()) {
            source.sendFailure(Component.translatable("commands.bphone.money.unavailable"));
            return 0;
        }
        Component message = Component.translatable("commands.bphone.money.info", EconomyBridge.providerId());
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int money(CommandContext<CommandSourceStack> context, MoneyAction action, long amount)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        if (!EconomyBridge.available()) {
            source.sendFailure(Component.translatable("commands.bphone.money.unavailable"));
            return 0;
        }
        BuiltinEconomyProvider builtin = EconomyBridge.activeBuiltin();
        if (action == MoneyAction.SET && builtin == null) {
            source.sendFailure(Component.translatable("commands.bphone.money.set_unsupported", EconomyBridge.providerId()));
            return 0;
        }

        Component reason = Component.translatable("economy.bphone.reason.admin");
        int done = 0;
        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "players")) {
            UUID account = profile.getId();
            String name = profile.getName();
            EconomyResult result = switch (action) {
                case GIVE -> EconomyBridge.depositTo(server, account, amount, reason);
                case TAKE -> EconomyBridge.withdrawFrom(server, account, amount, reason);
                case SET -> {
                    builtin.set(account, amount);
                    yield EconomyResult.SUCCESS;
                }
                default -> EconomyResult.SUCCESS;
            };
            if (!result.isSuccess()) {
                source.sendFailure(Component.translatable(result == EconomyResult.INSUFFICIENT_FUNDS
                        ? "commands.bphone.money.insufficient" : "commands.bphone.money.failed", name));
                continue;
            }
            long balance = EconomyBridge.balanceOf(server, account);
            Component message = switch (action) {
                case GIVE -> Component.translatable("commands.bphone.money.given", name, amount, balance);
                case TAKE -> Component.translatable("commands.bphone.money.taken", name, amount, balance);
                case SET -> Component.translatable("commands.bphone.money.set", name, balance);
                default -> Component.translatable("commands.bphone.money.balance", name, balance);
            };
            source.sendSuccess(() -> message, action != MoneyAction.BALANCE);
            done++;
        }
        return done;
    }

    private static int shopReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ShopCatalog.load(source.getServer());
        ShopNetworking.syncAll(source.getServer());
        Component message = Component.translatable("commands.bphone.shop.reloaded", ShopCatalog.categories().size());
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int stalls(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        if (!ShopCatalog.setStallsEnabled(source.getServer(), enabled)) {
            source.sendFailure(Component.translatable("commands.bphone.shop.failed"));
            return 0;
        }
        ShopNetworking.syncAll(source.getServer());
        Component message = enabled
                ? Component.translatable("commands.bphone.shop.stalls_on")
                : Component.translatable("commands.bphone.shop.stalls_off", StallStore.count());
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int announce(CommandContext<CommandSourceStack> context, boolean starred, String body) {
        CommandSourceStack source = context.getSource();
        String title = StringArgumentType.getString(context, "title");
        ServerPlayer player = source.getPlayer();
        Announcement saved = AnnouncementNetworking.publishAs(source.getServer(), source.getTextName(),
                player == null ? null : player.getUUID(), title, body.replace("\\n", "\n"), starred);
        if (saved == null) {
            source.sendFailure(Component.translatable("commands.bphone.announce.failed"));
            return 0;
        }
        Component message = Component.translatable("commands.bphone.announce.done", saved.title());
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int mapReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!MapStore.isOpen()) {
            source.sendFailure(Component.translatable("commands.bphone.unavailable"));
            return 0;
        }
        boolean loaded = MapStore.reload();
        for (int section = MapNetworking.SECTION_MARKERS; section <= MapNetworking.SECTION_LINES; section++) {
            MapNetworking.broadcast(source.getServer(), section);
        }
        if (!loaded) {
            source.sendFailure(Component.translatable("commands.bphone.map.reload_failed"));
            return 0;
        }
        Component message = Component.translatable("commands.bphone.map.reloaded", MapStore.markers().size(),
                MapStore.roads().size(), MapStore.areas().size(), MapStore.lines().size());
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int markerAdd(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!MapStore.isOpen()) {
            source.sendFailure(Component.translatable("commands.bphone.unavailable"));
            return 0;
        }
        String kindName = StringArgumentType.getString(context, "kind");
        int kind = MapData.kindByName(MapData.MARKER_KIND_NAMES, kindName);
        if (!MapData.MARKER_KIND_NAMES[kind].equalsIgnoreCase(kindName)) {
            source.sendFailure(Component.translatable("commands.bphone.map.unknown_kind", kindName));
            return 0;
        }
        String name = MapData.clip(StringArgumentType.getString(context, "name"), MapData.MAX_NAME_LENGTH);
        Vec3 position = source.getPosition();
        int x = Mth.floor(position.x);
        int y = Mth.floor(position.y);
        int z = Mth.floor(position.z);
        MapData.Marker marker = new MapData.Marker(MapStore.newId("m"),
                source.getLevel().dimension().location().toString(), name, kind, x, y, z, "");
        if (name.isEmpty() || !MapStore.upsertMarker(marker)) {
            source.sendFailure(Component.translatable("commands.bphone.map.full"));
            return 0;
        }
        MapNetworking.broadcast(source.getServer(), MapNetworking.SECTION_MARKERS);
        Component message = Component.translatable("commands.bphone.map.marker_added", name, x, y, z);
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int markerRemove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name").trim();
        List<MapData.Marker> found = MapStore.isOpen() ? MapStore.findMarkersByName(name) : List.of();
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.bphone.map.marker_missing", name));
            return 0;
        }
        for (MapData.Marker marker : found) {
            MapStore.deleteMarker(marker.id());
        }
        MapNetworking.broadcast(source.getServer(), MapNetworking.SECTION_MARKERS);
        MapNetworking.broadcast(source.getServer(), MapNetworking.SECTION_LINES);
        Component message = Component.translatable("commands.bphone.map.marker_removed", found.size());
        source.sendSuccess(() -> message, true);
        return found.size();
    }
}
