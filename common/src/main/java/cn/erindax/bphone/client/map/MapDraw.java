package cn.erindax.bphone.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class MapDraw {
    private final GuiGraphics graphics;
    private final Matrix4f pose;
    private VertexConsumer consumer;

    public MapDraw(GuiGraphics graphics) {
        this.graphics = graphics;
        this.pose = graphics.pose().last().pose();
    }

    private VertexConsumer consumer() {
        if (consumer == null) {
            consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        }
        return consumer;
    }

    public void flush() {
        if (consumer != null) {
            graphics.flush();
            consumer = null;
        }
    }

    public void clearDepth() {
        flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        VertexConsumer buffer = consumer();
        float area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (area > 0.0F) {
            buffer.addVertex(pose, x3, y3, 0.0F).setColor(argb);
            buffer.addVertex(pose, x2, y2, 0.0F).setColor(argb);
            buffer.addVertex(pose, x1, y1, 0.0F).setColor(argb);
            buffer.addVertex(pose, x0, y0, 0.0F).setColor(argb);
            return;
        }
        buffer.addVertex(pose, x0, y0, 0.0F).setColor(argb);
        buffer.addVertex(pose, x1, y1, 0.0F).setColor(argb);
        buffer.addVertex(pose, x2, y2, 0.0F).setColor(argb);
        buffer.addVertex(pose, x3, y3, 0.0F).setColor(argb);
    }

    public void triangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        quad(x0, y0, x1, y1, x2, y2, x2, y2, argb);
    }

    public void rect(float left, float top, float right, float bottom, int argb) {
        quad(left, top, left, bottom, right, bottom, right, top, argb);
    }

    public void segment(float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) {
            return;
        }
        float nx = -dy / length * width * 0.5F;
        float ny = dx / length * width * 0.5F;
        quad(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, argb);
    }

    public void dashed(float x0, float y0, float x1, float y1, float width, float dash, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F || dash <= 0.0F) {
            return;
        }
        for (float start = 0.0F; start < length; start += dash * 2.0F) {
            float end = Math.min(length, start + dash);
            segment(x0 + dx * start / length, y0 + dy * start / length, x0 + dx * end / length, y0 + dy * end / length,
                    width, argb);
        }
    }

    public void disc(float centerX, float centerY, float radius, int argb) {
        if (radius <= 0.0F) {
            return;
        }
        int steps = Math.max(8, Math.min(28, Math.round(radius * 3.0F)));
        float previousX = centerX + radius;
        float previousY = centerY;
        for (int step = 1; step <= steps; step++) {
            double angle = Math.PI * 2.0D * step / steps;
            float x = centerX + (float) Math.cos(angle) * radius;
            float y = centerY + (float) Math.sin(angle) * radius;
            triangle(centerX, centerY, x, y, previousX, previousY, argb);
            previousX = x;
            previousY = y;
        }
    }

    public void polyline(float[] xs, float[] ys, int count, float width, int argb) {
        for (int index = 0; index + 1 < count; index++) {
            segment(xs[index], ys[index], xs[index + 1], ys[index + 1], width, argb);
        }
        if (width >= 2.5F) {
            for (int index = 1; index + 1 < count; index++) {
                disc(xs[index], ys[index], width * 0.5F, argb);
            }
        }
    }

    public void fillTriangles(float[] xs, float[] ys, int[] triangles, int argb) {
        for (int index = 0; index + 2 < triangles.length; index += 3) {
            int a = triangles[index];
            int b = triangles[index + 1];
            int c = triangles[index + 2];
            triangle(xs[a], ys[a], xs[b], ys[b], xs[c], ys[c], argb);
        }
    }

    public void outline(float[] xs, float[] ys, int count, float width, int argb, boolean dashedLine) {
        for (int index = 0; index < count; index++) {
            int next = (index + 1) % count;
            if (dashedLine) {
                dashed(xs[index], ys[index], xs[next], ys[next], width, 4.0F, argb);
            } else {
                segment(xs[index], ys[index], xs[next], ys[next], width, argb);
            }
        }
    }

    public void texture(ResourceLocation texture, float left, float top, float right, float bottom,
                        float u0, float v0, float u1, float v1) {
        flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(pose, left, top, 0.0F).setUv(u0, v0);
        builder.addVertex(pose, left, bottom, 0.0F).setUv(u0, v1);
        builder.addVertex(pose, right, bottom, 0.0F).setUv(u1, v1);
        builder.addVertex(pose, right, top, 0.0F).setUv(u1, v0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    public void outlinedText(Font font, FormattedCharSequence text, float x, float y, float textScale, float angle,
                             int color, int outline) {
        flush();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        if (angle != 0.0F) {
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        }
        graphics.pose().scale(textScale, textScale, 1.0F);
        font.drawInBatch8xOutline(text, 0.0F, 0.0F, color, outline, graphics.pose().last().pose(),
                graphics.bufferSource(), LightTexture.FULL_BRIGHT);
        graphics.pose().popPose();
        graphics.flush();
    }

    public static int[] triangulate(double[] xs, double[] zs) {
        int count = xs.length;
        if (count < 3) {
            return new int[0];
        }
        double area = 0.0D;
        for (int index = 0; index < count; index++) {
            int next = (index + 1) % count;
            area += xs[index] * zs[next] - xs[next] * zs[index];
        }
        int[] order = new int[count];
        for (int index = 0; index < count; index++) {
            order[index] = area > 0.0D ? index : count - 1 - index;
        }
        int[] result = new int[(count - 2) * 3];
        int written = 0;
        int remaining = count;
        int guard = count * count;
        int cursor = 0;
        while (remaining > 3 && guard-- > 0) {
            int previous = order[(cursor + remaining - 1) % remaining];
            int current = order[cursor % remaining];
            int next = order[(cursor + 1) % remaining];
            if (isEar(xs, zs, order, remaining, previous, current, next)) {
                result[written++] = previous;
                result[written++] = current;
                result[written++] = next;
                int removeAt = cursor % remaining;
                System.arraycopy(order, removeAt + 1, order, removeAt, remaining - removeAt - 1);
                remaining--;
                cursor = Math.max(0, removeAt - 1);
            } else {
                cursor = (cursor + 1) % remaining;
            }
        }
        if (remaining > 3) {
            for (int index = 1; index + 1 < remaining && written + 3 <= result.length; index++) {
                result[written++] = order[0];
                result[written++] = order[index];
                result[written++] = order[index + 1];
            }
        } else if (remaining == 3 && written + 3 <= result.length) {
            result[written++] = order[0];
            result[written++] = order[1];
            result[written++] = order[2];
        }
        return written == result.length ? result : java.util.Arrays.copyOf(result, written);
    }

    private static boolean isEar(double[] xs, double[] zs, int[] order, int remaining, int a, int b, int c) {
        double cross = (xs[b] - xs[a]) * (zs[c] - zs[a]) - (zs[b] - zs[a]) * (xs[c] - xs[a]);
        if (cross <= 1.0E-9D) {
            return false;
        }
        for (int index = 0; index < remaining; index++) {
            int point = order[index];
            if (point == a || point == b || point == c) {
                continue;
            }
            if (inside(xs[point], zs[point], xs[a], zs[a], xs[b], zs[b], xs[c], zs[c])) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(double px, double pz, double ax, double az, double bx, double bz, double cx, double cz) {
        double d1 = (px - bx) * (az - bz) - (ax - bx) * (pz - bz);
        double d2 = (px - cx) * (bz - cz) - (bx - cx) * (pz - cz);
        double d3 = (px - ax) * (cz - az) - (cx - ax) * (pz - az);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }
}
