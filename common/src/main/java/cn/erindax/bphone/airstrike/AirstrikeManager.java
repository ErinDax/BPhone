package cn.erindax.bphone.airstrike;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AirstrikeManager {
    private static final int TARGET_SELECTION_TIMEOUT_TICKS = 100;
    private static final int STRIKE_COUNTDOWN_TICKS = 60;
    private static final int BOMBARDMENT_DURATION_TICKS = 100;
    private static final int STRAFING_DURATION_TICKS = 24;
    private static final double TARGET_PICK_DISTANCE = 128.0D;
    private static final double BOMBARDMENT_RADIUS = 10.0D;
    private static final double STRAFING_RADIUS = 10.0D;
    private static final double PARTICLE_WIDTH = 10.0D;
    private static final double PARTICLE_HALF_WIDTH = PARTICLE_WIDTH * 0.5D;
    private static final float BOMBARDMENT_DAMAGE = 9.0F;
    private static final float STRAFING_DAMAGE = 7.0F;
    private static boolean initialized;

    private static final Map<UUID, PendingTarget> PENDING_TARGETS = new HashMap<>();
    private static final Map<UUID, ActiveStrike> ACTIVE_STRIKES = new HashMap<>();

    private AirstrikeManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        AirstrikeNetworking.init();
        TickEvent.SERVER_PRE.register(AirstrikeManager::onServerTick);
        PlayerEvent.PLAYER_QUIT.register(player -> {
            PENDING_TARGETS.remove(player.getUUID());
            ACTIVE_STRIKES.remove(player.getUUID());
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            PENDING_TARGETS.clear();
            ACTIVE_STRIKES.clear();
        });
    }

    public static void useRadio(ServerPlayer player, ItemStack stack) {
        if (player == null || player.getServer() == null || stack == null) {
            return;
        }

        UUID playerId = player.getUUID();
        if (ACTIVE_STRIKES.containsKey(playerId)) {
            showActionbar(player, Component.translatable("message.bphone.airstrike.already_inbound"));
            return;
        }

        AirstrikeMode mode = cn.erindax.bphone.item.AirstrikeRadioItem.getMode(stack);
        PendingTarget pendingTarget = PENDING_TARGETS.get(playerId);
        long currentTick = player.getServer().getTickCount();
        if (pendingTarget != null && pendingTarget.isExpired(currentTick)) {
            clearPendingTarget(player, Component.translatable("message.bphone.airstrike.target_cancelled"));
            pendingTarget = null;
        }

        if (pendingTarget != null && pendingTarget.mode() == mode) {
            if (!pendingTarget.mode().requiresTwoPoints() || pendingTarget.secondPoint() != null) {
                confirmStrike(player, pendingTarget, currentTick);
                return;
            }
        }

        HitResult hitResult = player.pick(TARGET_PICK_DISTANCE, 1.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            showActionbar(player, Component.translatable("message.bphone.airstrike.invalid_target"));
            return;
        }

        BlockPos blockPos = blockHitResult.getBlockPos();
        if (pendingTarget == null) {
            PendingTarget created = new PendingTarget(mode, player.serverLevel().dimension(), blockPos, null, currentTick + TARGET_SELECTION_TIMEOUT_TICKS);
            PENDING_TARGETS.put(playerId, created);
            AirstrikeNetworking.sendTargetState(player, created.dimension().location(), created.mode(), created.firstPoint());
            showActionbar(player, Component.translatable(
                    mode.requiresTwoPoints()
                            ? "message.bphone.airstrike.point_one_selected"
                            : "message.bphone.airstrike.target_selected",
                    blockPos.getX(), blockPos.getY(), blockPos.getZ()
            ));
            return;
        }

        if (pendingTarget.mode() != mode) {
            clearPendingTarget(player, Component.translatable("message.bphone.airstrike.target_cancelled"));
            PendingTarget created = new PendingTarget(mode, player.serverLevel().dimension(), blockPos, null, currentTick + TARGET_SELECTION_TIMEOUT_TICKS);
            PENDING_TARGETS.put(playerId, created);
            AirstrikeNetworking.sendTargetState(player, created.dimension().location(), created.mode(), created.firstPoint());
            showActionbar(player, Component.translatable(
                    mode.requiresTwoPoints()
                            ? "message.bphone.airstrike.point_one_selected"
                            : "message.bphone.airstrike.target_selected",
                    blockPos.getX(), blockPos.getY(), blockPos.getZ()
            ));
            return;
        }

        if (pendingTarget.secondPoint() == null) {
            PendingTarget updated = pendingTarget.withSecondPoint(blockPos, currentTick + TARGET_SELECTION_TIMEOUT_TICKS);
            PENDING_TARGETS.put(playerId, updated);
            AirstrikeNetworking.sendTargetState(player, updated.dimension().location(), updated.mode(), updated.firstPoint(), updated.secondPoint());
            showActionbar(player, Component.translatable(
                    "message.bphone.airstrike.point_two_selected",
                    blockPos.getX(), blockPos.getY(), blockPos.getZ()
            ));
            return;
        }

    }

    public static void cancelSelection(ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (PENDING_TARGETS.containsKey(player.getUUID())) {
            clearPendingTarget(player, Component.translatable("message.bphone.airstrike.target_cancelled"));
        }
    }

    public static void handleModeCycle(ServerPlayer player, AirstrikeMode nextMode) {
        if (player == null || nextMode == null) {
            return;
        }

        if (PENDING_TARGETS.containsKey(player.getUUID())) {
            clearPendingTarget(player, null);
        }

        showActionbar(player, Component.translatable("message.bphone.airstrike.mode_changed",
                Component.translatable("message.bphone.airstrike.mode." + nextMode.serializedName())));
    }

    private static void confirmStrike(ServerPlayer player, PendingTarget pendingTarget, long currentTick) {
        PENDING_TARGETS.remove(player.getUUID());

        int durationTicks = switch (pendingTarget.mode()) {
            case BOMBARDMENT -> BOMBARDMENT_DURATION_TICKS;
            case STRAFING_RUN -> STRAFING_DURATION_TICKS;
        };

        ActiveStrike activeStrike = new ActiveStrike(
                pendingTarget.mode(),
                pendingTarget.dimension(),
                pendingTarget.firstPoint(),
                pendingTarget.secondPoint(),
                currentTick + STRIKE_COUNTDOWN_TICKS,
                currentTick + STRIKE_COUNTDOWN_TICKS + durationTicks
        );
        ACTIVE_STRIKES.put(player.getUUID(), activeStrike);

        if (pendingTarget.secondPoint() != null) {
            AirstrikeNetworking.sendStrikeLocked(player, pendingTarget.dimension().location(), pendingTarget.mode(),
                    STRIKE_COUNTDOWN_TICKS, pendingTarget.firstPoint(), pendingTarget.secondPoint());
        } else {
            AirstrikeNetworking.sendStrikeLocked(player, pendingTarget.dimension().location(), pendingTarget.mode(),
                    STRIKE_COUNTDOWN_TICKS, pendingTarget.firstPoint());
        }
        showActionbar(player, Component.translatable("message.bphone.airstrike.locked"));
    }

    private static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long currentTick = server.getTickCount();
        expirePendingTargets(server, currentTick);
        tickActiveStrikes(server, currentTick);
    }

    private static void expirePendingTargets(MinecraftServer server, long currentTick) {
        Iterator<Map.Entry<UUID, PendingTarget>> iterator = PENDING_TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTarget> entry = iterator.next();
            if (!entry.getValue().isExpired(currentTick)) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                AirstrikeNetworking.sendTargetCleared(player);
                showActionbar(player, Component.translatable("message.bphone.airstrike.target_cancelled"));
            }

            iterator.remove();
        }
    }

    private static void tickActiveStrikes(MinecraftServer server, long currentTick) {
        Iterator<Map.Entry<UUID, ActiveStrike>> iterator = ACTIVE_STRIKES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveStrike> entry = iterator.next();
            ActiveStrike strike = entry.getValue();
            ServerLevel level = server.getLevel(strike.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            if (currentTick < strike.startTick()) {
                continue;
            }

            if (currentTick >= strike.endTick()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    AirstrikeNetworking.sendTargetCleared(player);
                }
                iterator.remove();
                continue;
            }

            switch (strike.mode()) {
                case BOMBARDMENT -> tickBombardment(level, strike, currentTick);
                case STRAFING_RUN -> tickStrafingRun(level, strike, currentTick);
            }
        }
    }

    private static void tickBombardment(ServerLevel level, ActiveStrike strike, long currentTick) {
        if ((currentTick - strike.startTick()) % 5L != 0L) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(strike.firstPoint()).add(0.0D, 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 8, PARTICLE_HALF_WIDTH, 0.2D, PARTICLE_HALF_WIDTH, 0.02D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TNT.defaultBlockState()),
                center.x, center.y, center.z,
                60,
                PARTICLE_HALF_WIDTH, 0.3D, PARTICLE_HALF_WIDTH,
                0.02D);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 42, PARTICLE_HALF_WIDTH, 0.35D, PARTICLE_HALF_WIDTH, 0.05D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.15F, 1.05F);
        damageNearby(level, center, BOMBARDMENT_RADIUS, BOMBARDMENT_DAMAGE, true);
    }

    private static void tickStrafingRun(ServerLevel level, ActiveStrike strike, long currentTick) {
        if (strike.secondPoint() == null) {
            return;
        }

        double progress = (double) (currentTick - strike.startTick()) / (double) Math.max(1L, strike.endTick() - strike.startTick());
        Vec3 start = Vec3.atCenterOf(strike.firstPoint()).add(0.0D, 0.35D, 0.0D);
        Vec3 end = Vec3.atCenterOf(strike.secondPoint()).add(0.0D, 0.35D, 0.0D);
        Vec3 current = start.lerp(end, progress);

        level.sendParticles(ParticleTypes.EXPLOSION, current.x, current.y, current.z, 4, PARTICLE_HALF_WIDTH, 0.15D, PARTICLE_HALF_WIDTH, 0.015D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TNT.defaultBlockState()),
                current.x, current.y, current.z,
                32,
                PARTICLE_HALF_WIDTH, 0.2D, PARTICLE_HALF_WIDTH,
                0.02D);
        level.sendParticles(ParticleTypes.SMOKE, current.x, current.y, current.z, 20, PARTICLE_HALF_WIDTH, 0.2D, PARTICLE_HALF_WIDTH, 0.04D);
        level.playSound(null, current.x, current.y, current.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.85F, 1.3F);

        damageNearby(level, current, STRAFING_RADIUS, STRAFING_DAMAGE, false);
    }

    private static void damageNearby(ServerLevel level, Vec3 center, double radius, float maxDamage, boolean strongerFalloff) {
        AABB damageBox = new AABB(center, center).inflate(radius);
        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, damageBox, LivingEntity::isAlive)) {
            double distance = livingEntity.position().distanceTo(center);
            if (distance > radius) {
                continue;
            }

            double ratio = 1.0D - (distance / radius);
            if (strongerFalloff) {
                ratio *= ratio;
            }

            float damage = (float) (ratio * maxDamage);
            if (damage <= 0.0F) {
                continue;
            }

            livingEntity.hurt(level.damageSources().explosion(null, null), damage);
        }
    }

    private static void clearPendingTarget(ServerPlayer player, Component message) {
        PENDING_TARGETS.remove(player.getUUID());
        AirstrikeNetworking.sendTargetCleared(player);
        if (message != null) {
            showActionbar(player, message);
        }
    }

    private static void showActionbar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private record PendingTarget(
            AirstrikeMode mode,
            ResourceKey<Level> dimension,
            BlockPos firstPoint,
            BlockPos secondPoint,
            long expireTick
    ) {
        private boolean isExpired(long currentTick) {
            return currentTick >= expireTick;
        }

        private PendingTarget withSecondPoint(BlockPos blockPos, long newExpireTick) {
            return new PendingTarget(mode, dimension, firstPoint, blockPos, newExpireTick);
        }
    }

    private record ActiveStrike(
            AirstrikeMode mode,
            ResourceKey<Level> dimension,
            BlockPos firstPoint,
            BlockPos secondPoint,
            long startTick,
            long endTick
    ) {
    }
}
