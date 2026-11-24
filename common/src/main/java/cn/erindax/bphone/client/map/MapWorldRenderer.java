package cn.erindax.bphone.client.map;

import cn.erindax.bphone.client.phone.PhoneClientPrefs;
import cn.erindax.bphone.client.phone.PhoneFont;
import cn.erindax.bphone.client.phone.PhoneScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public final class MapWorldRenderer {
    private static final double SAMPLE_STEP = 1.2D;
    private static final double MAX_PATH_LENGTH = 84.0D;
    private static final double CHEVRON_SPACING = 2.4D;
    private static final double CHEVRON_SPEED = 3.2D;
    private static final float RIBBON_OUTER_HALF = 0.38F;
    private static final float RIBBON_INNER_HALF = 0.16F;
    private static final int[] SURFACE_OFFSETS = {0, 1, -1, 2, -2, -3, 3, -4, 4, -5, 5, -6};

    private MapWorldRenderer() {
    }

    public static void renderWorld(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.options.hideGui
                || !MapNavigation.isActive() || MapNavigation.isOtherDimension()
                || !PhoneClientPrefs.worldNavGuide()) {
            return;
        }
        if (minecraft.screen instanceof PhoneScreen phoneScreen && phoneScreen.isPhotoCameraMode()) {
            return;
        }

        MapNavigation.Target target = MapNavigation.target();
        if (target == null) {
            return;
        }

        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());

        List<double[]> waypoints = collectWaypoints(px, pz, target);
        List<double[]> sampled = sampleCurve(level, waypoints, py);

        Vec3 cam = camera.getPosition();
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f identity = poseStack.last().pose();
        if (sampled.size() >= 2) {
            RenderSystem.disableDepthTest();
            renderRibbonAndChevrons(identity, cam, sampled, 0.32F);
            RenderSystem.enableDepthTest();
            renderRibbonAndChevrons(identity, cam, sampled, 1.0F);
        }

        double horizToDest = Math.hypot(target.x() - px, target.z() - pz);
        double destSurfaceY = sampleSurfaceY(level, target.x(), target.y(), target.z()) + 0.06D;
        if (horizToDest <= 160.0D) {
            renderDestinationBeacon(identity, cam, target.x(), destSurfaceY, target.z(), horizToDest);
            renderDestinationBillboard(minecraft, poseStack, camera, cam, target, destSurfaceY, horizToDest);
        }

        RenderSystem.depthMask(true);
        if (cullWasEnabled) {
            RenderSystem.enableCull();
        }
        RenderSystem.disableBlend();
    }

    private static List<double[]> collectWaypoints(double px, double pz, MapNavigation.Target target) {
        List<double[]> route = MapNavigation.route();
        int next = MapNavigation.nextIndex();
        List<double[]> points = new ArrayList<>();
        points.add(new double[] {px, pz});
        if (route.size() >= 2 && next < route.size()) {
            int prevIdx = Math.max(0, next - 1);
            double[] a = route.get(prevIdx);
            double[] b = route.get(next);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double lenSq = dx * dx + dz * dz;
            if (lenSq > 1.0E-6D) {
                double t = Mth.clamp(((px - a[0]) * dx + (pz - a[1]) * dz) / lenSq, 0.0D, 1.0D);
                double projX = a[0] + dx * t;
                double projZ = a[1] + dz * t;
                double off = Math.hypot(projX - px, projZ - pz);
                if (off > 0.6D && off <= 18.0D && Math.hypot(b[0] - projX, b[1] - projZ) > 0.6D) {
                    points.add(new double[] {projX, projZ});
                }
            }
            for (int i = next; i < route.size(); i++) {
                points.add(route.get(i));
            }
        } else {
            points.add(new double[] {target.x(), target.z()});
        }
        return points;
    }

    private static List<double[]> sampleCurve(ClientLevel level, List<double[]> waypoints, double startY) {
        List<double[]> raw = new ArrayList<>();
        if (waypoints.size() < 2) {
            return raw;
        }

        double refY = startY;
        double accDist = 0.0D;
        double[] first = waypoints.get(0);
        refY = sampleSurfaceY(level, first[0], refY, first[1]);
        raw.add(new double[] {first[0], refY, first[1], 0.0D});

        for (int i = 0; i + 1 < waypoints.size() && accDist < MAX_PATH_LENGTH; i++) {
            double[] a = waypoints.get(i);
            double[] b = waypoints.get(i + 1);
            double segLen = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (segLen < 0.05D) {
                continue;
            }
            int steps = Math.max(1, (int) Math.ceil(segLen / SAMPLE_STEP));
            for (int step = 1; step <= steps && accDist < MAX_PATH_LENGTH; step++) {
                double t = (double) step / steps;
                double sx = Mth.lerp(t, a[0], b[0]);
                double sz = Mth.lerp(t, a[1], b[1]);
                double stepLen = segLen / steps;
                accDist += stepLen;
                refY = sampleSurfaceY(level, sx, refY, sz);
                raw.add(new double[] {sx, refY, sz, accDist});
            }
        }

        int n = raw.size();
        if (n >= 3) {
            for (int pass = 0; pass < 2; pass++) {
                double[] smoothedY = new double[n];
                smoothedY[0] = raw.get(0)[1];
                smoothedY[n - 1] = raw.get(n - 1)[1];
                for (int i = 1; i + 1 < n; i++) {
                    double prevY = raw.get(i - 1)[1];
                    double currY = raw.get(i)[1];
                    double nextY = raw.get(i + 1)[1];
                    if (Math.abs(nextY - prevY) <= 3.0D) {
                        smoothedY[i] = 0.25D * prevY + 0.50D * currY + 0.25D * nextY;
                    } else {
                        smoothedY[i] = currY;
                    }
                }
                for (int i = 0; i < n; i++) {
                    raw.get(i)[1] = smoothedY[i];
                }
            }
        }

        for (double[] p : raw) {
            p[1] += 0.18D;
        }
        return raw;
    }

    private static double sampleSurfaceY(ClientLevel level, double x, double refY, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
            return refY;
        }
        int baseY = Mth.floor(refY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        double bestY = Double.NaN;
        double bestDiff = Double.MAX_VALUE;
        for (int dy : SURFACE_OFFSETS) {
            int y = baseY + dy;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 1) {
                continue;
            }
            pos.set(bx, y, bz);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            above.set(bx, y + 1, bz);
            VoxelShape aboveShape = level.getBlockState(above).getCollisionShape(level, above);
            if (!aboveShape.isEmpty() && aboveShape.max(Direction.Axis.Y) > 0.25D) {
                continue;
            }
            double candidateY = y + shape.max(Direction.Axis.Y);
            double diff = Math.abs(candidateY - refY);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestY = candidateY;
            }
        }
        if (!Double.isNaN(bestY)) {
            return bestY;
        }
        int hm = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return hm > level.getMinBuildHeight() ? hm : refY;
    }

    private static float pathFade(double s, double totalLen) {
        float nearFade = Mth.clamp((float) ((s - 0.65D) / 1.5D), 0.0F, 1.0F);
        float farFade = Mth.clamp((float) ((MAX_PATH_LENGTH - s) / 22.0D), 0.0F, 1.0F);
        float endFade = totalLen < 3.0D ? 1.0F : Mth.clamp((float) ((totalLen - s) / 0.8D), 0.15F, 1.0F);
        return nearFade * farFade * endFade;
    }

    private static void renderRibbonAndChevrons(Matrix4f matrix, Vec3 cam, List<double[]> pts, float passScale) {
        int n = pts.size();
        double totalLen = pts.get(n - 1)[3];
        if (totalLen < 0.8D) {
            return;
        }

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (int i = 0; i + 1 < n; i++) {
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            float f0 = pathFade(a[3], totalLen) * passScale;
            float f1 = pathFade(b[3], totalLen) * passScale;
            if (f0 <= 0.01F && f1 <= 0.01F) {
                continue;
            }
            double sdx = b[0] - a[0];
            double sdz = b[2] - a[2];
            double slen = Math.hypot(sdx, sdz);
            if (slen < 1.0E-5D) {
                continue;
            }
            float snx = (float) (-sdz / slen);
            float snz = (float) (sdx / slen);
            any = true;
            emitRibbonQuad(builder, matrix, cam, a, b, snx, snz, snx, snz,
                    RIBBON_OUTER_HALF, 0x1E, 0x9E, 0xF5, Math.round(105 * f0), Math.round(105 * f1), 0.0F);
            emitRibbonQuad(builder, matrix, cam, a, b, snx, snz, snx, snz,
                    RIBBON_INNER_HALF, 0x70, 0xEA, 0xFF, Math.round(205 * f0), Math.round(205 * f1), 0.008F);
        }

        double anim = ((System.currentTimeMillis() % 120000L) / 1000.0D) * CHEVRON_SPEED;
        double firstS = anim % CHEVRON_SPACING;
        int segIdx = 0;
        for (double s = firstS; s < totalLen - 0.5D; s += CHEVRON_SPACING) {
            if (s < 1.1D) {
                continue;
            }
            while (segIdx + 1 < n && pts.get(segIdx + 1)[3] < s) {
                segIdx++;
            }
            if (segIdx + 1 >= n) {
                break;
            }
            double[] a = pts.get(segIdx);
            double[] b = pts.get(segIdx + 1);
            double span = Math.max(1.0E-4D, b[3] - a[3]);
            double t = Mth.clamp((s - a[3]) / span, 0.0D, 1.0D);
            double cx = Mth.lerp(t, a[0], b[0]);
            double cy = Mth.lerp(t, a[1], b[1]) + 0.018D;
            double cz = Mth.lerp(t, a[2], b[2]);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double dz = b[2] - a[2];
            double horiz = Math.hypot(dx, dz);
            if (horiz < 1.0E-4D) {
                continue;
            }
            float fade = pathFade(s, totalLen) * passScale;
            int alpha = Math.round(248 * fade);
            if (alpha <= 4) {
                continue;
            }
            any = true;
            emitChevron(builder, matrix, cam, cx, cy, cz,
                    (float) (dx / horiz), (float) (dy / horiz), (float) (dz / horiz),
                    (float) (-dz / horiz), (float) (dx / horiz), alpha);
        }

        if (any) {
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }
    }

    private static void emitRibbonQuad(BufferBuilder builder, Matrix4f matrix, Vec3 cam,
                                       double[] a, double[] b, float nx0, float nz0, float nx1, float nz1,
                                       float halfWidth, int r, int g, int bl, int a0, int a1, float yLift) {
        float ax = (float) (a[0] - cam.x);
        float ay = (float) (a[1] - cam.y) + yLift;
        float az = (float) (a[2] - cam.z);
        float bx = (float) (b[0] - cam.x);
        float by = (float) (b[1] - cam.y) + yLift;
        float bz = (float) (b[2] - cam.z);

        builder.addVertex(matrix, ax - nx0 * halfWidth, ay, az - nz0 * halfWidth).setColor(r, g, bl, a0);
        builder.addVertex(matrix, bx - nx1 * halfWidth, by, bz - nz1 * halfWidth).setColor(r, g, bl, a1);
        builder.addVertex(matrix, bx + nx1 * halfWidth, by, bz + nz1 * halfWidth).setColor(r, g, bl, a1);
        builder.addVertex(matrix, ax + nx0 * halfWidth, ay, az + nz0 * halfWidth).setColor(r, g, bl, a0);
    }

    private static void emitChevron(BufferBuilder builder, Matrix4f matrix, Vec3 cam,
                                    double cx, double cy, double cz,
                                    float fx, float fy, float fz, float rx, float rz, int alpha) {
        float ox = (float) (cx - cam.x);
        float oy = (float) (cy - cam.y);
        float oz = (float) (cz - cam.z);

        float tipLen = 0.40F;
        float innerTip = 0.16F;
        float tailBack = -0.22F;
        float tailInner = -0.38F;
        float wingSpan = 0.31F;

        float tipX = ox + fx * tipLen;
        float tipY = oy + fy * tipLen;
        float tipZ = oz + fz * tipLen;

        float inX = ox + fx * innerTip;
        float inY = oy + fy * innerTip;
        float inZ = oz + fz * innerTip;

        float lOutX = ox + fx * tailBack - rx * wingSpan;
        float lOutY = oy + fy * tailBack;
        float lOutZ = oz + fz * tailBack - rz * wingSpan;

        float lInX = ox + fx * tailInner - rx * wingSpan;
        float lInY = oy + fy * tailInner;
        float lInZ = oz + fz * tailInner - rz * wingSpan;

        float rOutX = ox + fx * tailBack + rx * wingSpan;
        float rOutY = oy + fy * tailBack;
        float rOutZ = oz + fz * tailBack + rz * wingSpan;

        float rInX = ox + fx * tailInner + rx * wingSpan;
        float rInY = oy + fy * tailInner;
        float rInZ = oz + fz * tailInner + rz * wingSpan;

        builder.addVertex(matrix, tipX, tipY, tipZ).setColor(240, 252, 255, alpha);
        builder.addVertex(matrix, lOutX, lOutY, lOutZ).setColor(160, 236, 255, alpha);
        builder.addVertex(matrix, lInX, lInY, lInZ).setColor(120, 220, 255, alpha * 3 / 4);
        builder.addVertex(matrix, inX, inY, inZ).setColor(240, 252, 255, alpha);

        builder.addVertex(matrix, tipX, tipY, tipZ).setColor(240, 252, 255, alpha);
        builder.addVertex(matrix, inX, inY, inZ).setColor(240, 252, 255, alpha);
        builder.addVertex(matrix, rInX, rInY, rInZ).setColor(120, 220, 255, alpha * 3 / 4);
        builder.addVertex(matrix, rOutX, rOutY, rOutZ).setColor(160, 236, 255, alpha);
    }

    private static void renderDestinationBeacon(Matrix4f matrix, Vec3 cam, double dx, double dy, double dz, double horizDist) {
        float fade = Mth.clamp((float) ((160.0D - horizDist) / 30.0D), 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float ox = (float) (dx - cam.x);
        float oy = (float) (dy - cam.y);
        float oz = (float) (dz - cam.z);

        int segments = 24;
        emitFlatRing(builder, matrix, ox, oy + 0.01F, oz, 0.58F, 0.74F, segments, 92, 225, 255, Math.round(210 * fade));
        float pulse = (System.currentTimeMillis() % 1800L) / 1800.0F;
        float pulseR = 0.68F + pulse * 0.85F;
        emitFlatRing(builder, matrix, ox, oy + 0.005F, oz, pulseR, pulseR + 0.11F, segments,
                92, 225, 255, Math.round(135 * (1.0F - pulse) * fade));

        float pillarRadius = 0.13F;
        float pillarHeight = 3.4F;
        int bottomAlpha = Math.round(115 * fade);
        for (int i = 0; i < 12; i++) {
            double a0 = Math.PI * 2.0D * i / 12;
            double a1 = Math.PI * 2.0D * (i + 1) / 12;
            float x0 = ox + (float) Math.cos(a0) * pillarRadius;
            float z0 = oz + (float) Math.sin(a0) * pillarRadius;
            float x1 = ox + (float) Math.cos(a1) * pillarRadius;
            float z1 = oz + (float) Math.sin(a1) * pillarRadius;
            builder.addVertex(matrix, x0, oy, z0).setColor(79, 195, 247, bottomAlpha);
            builder.addVertex(matrix, x1, oy, z1).setColor(79, 195, 247, bottomAlpha);
            builder.addVertex(matrix, x1, oy + pillarHeight, z1).setColor(79, 195, 247, 0);
            builder.addVertex(matrix, x0, oy + pillarHeight, z0).setColor(79, 195, 247, 0);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void emitFlatRing(BufferBuilder builder, Matrix4f matrix, float cx, float cy, float cz,
                                     float innerR, float outerR, int segments, int r, int g, int b, int a) {
        if (a <= 2) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            builder.addVertex(matrix, cx + cos0 * innerR, cy, cz + sin0 * innerR).setColor(r, g, b, a);
            builder.addVertex(matrix, cx + cos0 * outerR, cy, cz + sin0 * outerR).setColor(r, g, b, a);
            builder.addVertex(matrix, cx + cos1 * outerR, cy, cz + sin1 * outerR).setColor(r, g, b, a);
            builder.addVertex(matrix, cx + cos1 * innerR, cy, cz + sin1 * innerR).setColor(r, g, b, a);
        }
    }

    private static void renderDestinationBillboard(Minecraft minecraft, PoseStack poseStack, Camera camera, Vec3 cam,
                                                   MapNavigation.Target target, double surfaceY, double horizDist) {
        Font font = PhoneFont.get();
        double rem = MapNavigation.remaining() >= 0.0D ? MapNavigation.remaining() : horizDist;
        String labelStr = target.name() + " · " + MapNavigation.formatDistance(rem);
        FormattedCharSequence text = Component.literal(labelStr).getVisualOrderText();
        int textWidth = font.width(text);

        double signX = target.x() - cam.x;
        double signY = surfaceY + 2.35D - cam.y;
        double signZ = target.z() - cam.z;
        double camDist = Math.sqrt(signX * signX + signY * signY + signZ * signZ);
        float scale = 0.022F * Mth.clamp((float) (camDist / 13.0D), 0.85F, 5.2F);

        poseStack.pushPose();
        poseStack.translate(signX, signY, signZ);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(scale, -scale, scale);

        Matrix4f mat = poseStack.last().pose();
        float padX = 6.0F;
        float padY = 3.5F;
        float dotSize = 4.5F;
        float dotGap = 4.0F;
        float totalW = padX * 2.0F + dotSize + dotGap + textWidth;
        float totalH = font.lineHeight + padY * 2.0F - 1.0F;
        float left = -totalW * 0.5F;
        float top = -totalH * 0.5F;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bg = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        emitBillboardPill(bg, mat, left - 0.8F, top - 0.8F, totalW + 1.6F, totalH + 1.6F, 28, 168, 240, 140);
        emitBillboardPill(bg, mat, left, top, totalW, totalH, 20, 23, 30, 212);
        float dotX = left + padX;
        float dotY = top + (totalH - dotSize) * 0.5F;
        emitBillboardPill(bg, mat, dotX, dotY, dotSize, dotSize, 79, 195, 247, 245);
        BufferUploader.drawWithShader(bg.buildOrThrow());

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        font.drawInBatch(text, dotX + dotSize + dotGap, top + padY, 0xFFFFFFFF, false, mat,
                buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void emitBillboardPill(BufferBuilder builder, Matrix4f mat, float x, float y, float w, float h,
                                          int r, int g, int b, int a) {
        float c = Math.min(2.5F, Math.min(w, h) * 0.35F);
        builder.addVertex(mat, x + c, y, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + c, y + h, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + w - c, y + h, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + w - c, y, 0.0F).setColor(r, g, b, a);

        builder.addVertex(mat, x, y + c, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x, y + h - c, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + c, y + h, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + c, y, 0.0F).setColor(r, g, b, a);

        builder.addVertex(mat, x + w - c, y, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + w - c, y + h, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + w, y + h - c, 0.0F).setColor(r, g, b, a);
        builder.addVertex(mat, x + w, y + c, 0.0F).setColor(r, g, b, a);
    }
}
