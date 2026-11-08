package cn.erindax.bphone.client.airstrike;

import cn.erindax.bphone.airstrike.AirstrikeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AirstrikeClientState {
    private static ResourceLocation targetDimensionId;
    private static AirstrikeMode mode = AirstrikeMode.BOMBARDMENT;
    private static final List<BlockPos> targetPoints = new ArrayList<>();
    private static long countdownEndTick = -1L;
    private static int lastCountdownSecond = Integer.MIN_VALUE;

    private AirstrikeClientState() {
    }

    public static void setSelectedTarget(ResourceLocation dimensionId, AirstrikeMode nextMode, List<BlockPos> points) {
        targetDimensionId = dimensionId;
        mode = nextMode == null ? AirstrikeMode.BOMBARDMENT : nextMode;
        targetPoints.clear();
        if (points != null) {
            targetPoints.addAll(points);
        }
        countdownEndTick = -1L;
        lastCountdownSecond = Integer.MIN_VALUE;
    }

    public static void startLockedTarget(Minecraft minecraft, ResourceLocation dimensionId, AirstrikeMode nextMode,
                                         List<BlockPos> points, int countdownTicks) {
        setSelectedTarget(dimensionId, nextMode, points);
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        countdownEndTick = minecraft.level.getGameTime() + countdownTicks;
        lastCountdownSecond = Integer.MIN_VALUE;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                AirstrikeClientConfig.getTargetLockedSound(minecraft),
                1.0F,
                1.0F
        ));
    }

    public static void clear() {
        targetDimensionId = null;
        mode = AirstrikeMode.BOMBARDMENT;
        targetPoints.clear();
        countdownEndTick = -1L;
        lastCountdownSecond = Integer.MIN_VALUE;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (targetPoints.isEmpty() || targetDimensionId == null) {
            return;
        }

        if (!targetDimensionId.equals(minecraft.level.dimension().location())) {
            return;
        }

        if (countdownEndTick < 0L) {
            return;
        }

        long remainingTicks = countdownEndTick - minecraft.level.getGameTime();
        if (remainingTicks <= 0L) {
            return;
        }

        int remainingSeconds = (int) Math.ceil(remainingTicks / 20.0D);
        if (remainingSeconds == lastCountdownSecond) {
            return;
        }

        lastCountdownSecond = remainingSeconds;
        minecraft.player.displayClientMessage(
                Component.translatable("message.bphone.airstrike.countdown", remainingSeconds),
                true
        );
    }

    public static List<Vec3> getRenderPositions(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || targetPoints.isEmpty() || targetDimensionId == null) {
            return List.of();
        }

        if (!targetDimensionId.equals(minecraft.level.dimension().location())) {
            return List.of();
        }

        List<Vec3> positions = new ArrayList<>(targetPoints.size());
        for (BlockPos targetPoint : targetPoints) {
            positions.add(Vec3.atCenterOf(targetPoint).add(0.0D, 1.15D, 0.0D));
        }
        return positions;
    }

    public static AirstrikeMode getMode() {
        return mode;
    }
}
