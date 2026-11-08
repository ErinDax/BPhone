package cn.erindax.bphone.client.map;

import cn.erindax.bphone.client.phone.PhoneScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

public final class MapNavigation {
    private static final double ARRIVE_DISTANCE = 4.0D;
    private static final double OFF_ROUTE_DISTANCE = 24.0D;
    private static final double WALK_SPEED = 4.317D;
    private static final long REROUTE_MILLIS = 3000L;

    public record Target(String name, String dimension, double x, double y, double z) {
    }

    private static Target target;
    private static List<double[]> route = List.of();
    private static int next;
    private static double remaining = -1.0D;
    private static long lastRoute;

    private MapNavigation() {
    }

    public static void start(Target destination) {
        target = destination;
        next = 1;
        remaining = -1.0D;
        reroute();
    }

    public static void stop() {
        target = null;
        route = List.of();
        next = 1;
        remaining = -1.0D;
    }

    public static boolean isActive() {
        return target != null;
    }

    public static Target target() {
        return target;
    }

    public static List<double[]> route() {
        return route;
    }

    public static int nextIndex() {
        return next;
    }

    public static double remaining() {
        return remaining;
    }

    public static boolean isOtherDimension() {
        return target != null && !target.dimension().equals(MapClientState.currentDimension());
    }

    public static String formatDistance(double blocks) {
        if (blocks < 0.0D) {
            return "--";
        }
        if (blocks >= 1000.0D) {
            return Component.translatable("map.bphone.distance.km",
                    String.format(Locale.ROOT, "%.1f", blocks / 1000.0D)).getString();
        }
        return Component.translatable("map.bphone.distance.m", Math.round(blocks)).getString();
    }

    public static String formatDuration(double blocks) {
        long seconds = Math.max(1L, Math.round(blocks / WALK_SPEED));
        if (seconds < 60L) {
            return Component.translatable("map.bphone.duration.seconds", seconds).getString();
        }
        return Component.translatable("map.bphone.duration.minutes", Math.round(seconds / 60.0D)).getString();
    }

    private static void reroute() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        lastRoute = System.currentTimeMillis();
        if (target == null || player == null || isOtherDimension()) {
            route = List.of();
            return;
        }
        route = MapClientState.graph(target.dimension()).route(player.getX(), player.getZ(), target.x(), target.z());
        next = Math.min(1, route.size() - 1);
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (target == null || player == null) {
            return;
        }
        if (isOtherDimension()) {
            route = List.of();
            remaining = -1.0D;
            return;
        }
        if (route.isEmpty()) {
            reroute();
        }
        double x = player.getX();
        double z = player.getZ();
        if (Math.hypot(target.x() - x, target.z() - z) <= ARRIVE_DISTANCE) {
            arrive(minecraft);
            return;
        }
        advance(x, z);
        if (distanceToRoute(x, z) > OFF_ROUTE_DISTANCE && System.currentTimeMillis() - lastRoute > REROUTE_MILLIS) {
            reroute();
            advance(x, z);
        }
        double[] point = route.get(next);
        remaining = Math.hypot(point[0] - x, point[1] - z) + RoadGraph.length(route, next);
    }

    private static void advance(double x, double z) {
        if (route.size() < 2) {
            return;
        }
        int best = next;
        double bestDistance = Double.MAX_VALUE;
        int last = Math.min(route.size() - 1, next + 6);
        for (int index = Math.max(1, next - 1); index <= last; index++) {
            double distance = distanceToSegment(route.get(index - 1), route.get(index), x, z);
            if (distance < bestDistance - 0.01D) {
                bestDistance = distance;
                best = index;
            }
        }
        next = Math.max(1, best);
        while (next < route.size() - 1 && Math.hypot(route.get(next)[0] - x, route.get(next)[1] - z) < 3.0D) {
            next++;
        }
    }

    private static double distanceToRoute(double x, double z) {
        double best = Double.MAX_VALUE;
        for (int index = 1; index < route.size(); index++) {
            best = Math.min(best, distanceToSegment(route.get(index - 1), route.get(index), x, z));
        }
        return best;
    }

    private static double distanceToSegment(double[] a, double[] b, double x, double z) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared < 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((x - a[0]) * dx + (z - a[1]) * dz) / lengthSquared));
        return Math.hypot(a[0] + dx * t - x, a[1] + dz * t - z);
    }

    private static void arrive(Minecraft minecraft) {
        Component name = Component.literal(target.name());
        stop();
        SystemToast.add(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("map.bphone.nav.arrived"), name);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.4F));
    }

    public static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (target == null || player == null || minecraft.options.hideGui || minecraft.screen instanceof PhoneScreen) {
            return;
        }
        String distance = isOtherDimension()
                ? Component.translatable("map.bphone.nav.other_dimension").getString()
                : formatDistance(remaining);
        Component label = Component.literal(target.name() + "  " + distance);
        FormattedCharSequence text = label.getVisualOrderText();
        int textWidth = minecraft.font.width(text);
        int arrowSize = 9;
        int width = textWidth + arrowSize + 16;
        int height = 15;
        int left = (graphics.guiWidth() - width) / 2;
        int top = 4;
        graphics.fill(left + 1, top, left + width - 1, top + height, 0xC0181A1F);
        graphics.fill(left, top + 1, left + width, top + height - 1, 0xC0181A1F);

        MapDraw draw = new MapDraw(graphics);
        float centerX = left + 6 + arrowSize / 2.0F;
        float centerY = top + height / 2.0F;
        if (!isOtherDimension() && route.size() > next) {
            double[] point = route.get(next);
            double dx = point[0] - player.getX();
            double dz = point[1] - player.getZ();
            float yaw = (float) Math.toRadians(player.getYRot());
            double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
            double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
            arrow(draw, centerX, centerY, (float) Math.atan2(-forward, right), arrowSize / 2.0F, 0xFF4FC3F7);
        } else {
            draw.disc(centerX, centerY, 2.5F, 0xFFB0B4BA);
        }
        draw.flush();
        graphics.drawString(minecraft.font, text, left + arrowSize + 10, top + 4, 0xFFFFFFFF, false);
    }

    public static void arrow(MapDraw draw, float x, float y, float angle, float size, int argb) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float tipX = x + cos * size;
        float tipY = y + sin * size;
        float backX = x - cos * size * 0.55F;
        float backY = y - sin * size * 0.55F;
        float leftX = x - cos * size * 0.9F - sin * size * 0.8F;
        float leftY = y - sin * size * 0.9F + cos * size * 0.8F;
        float rightX = x - cos * size * 0.9F + sin * size * 0.8F;
        float rightY = y - sin * size * 0.9F - cos * size * 0.8F;
        draw.triangle(tipX, tipY, leftX, leftY, backX, backY, argb);
        draw.triangle(tipX, tipY, backX, backY, rightX, rightY, argb);
    }
}
