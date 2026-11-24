package cn.erindax.bphone.client.phone;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class PhonePanelBuffer {
    private TextureTarget target;

    void begin(GuiGraphics guiGraphics) {
        guiGraphics.flush();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }

    void end(GuiGraphics guiGraphics, UiRect bounds, int radius) {
        end(guiGraphics, bounds, radius, PhoneClientPrefs.panelOpacity());
    }

    void end(GuiGraphics guiGraphics, UiRect bounds, int radius, float alphaFactor) {
        guiGraphics.flush();
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        if (target == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }

        PhoneUi.fillRounded(guiGraphics, bounds.left - 3, bounds.top - 2, bounds.width + 6, bounds.height + 6,
                radius + 3, 0x16000000);
        PhoneUi.fillRounded(guiGraphics, bounds.left - 1, bounds.top - 1, bounds.width + 2, bounds.height + 3,
                radius + 1, 0x26000000);
        guiGraphics.flush();

        int density = PhoneUi.pixelDensity();
        int left = bounds.left * density;
        int top = bounds.top * density;
        int right = (bounds.left + bounds.width) * density;
        int bottom = (bounds.top + bounds.height) * density;
        int corner = Math.max(0, Math.min(radius, Math.min(bounds.width, bounds.height) / 2)) * density;
        float texWidth = target.width;
        float texHeight = target.height;
        int baseAlpha = Mth.clamp(Math.round(alphaFactor * 255.0F), 40, 255);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (bottom - corner > top + corner) {
            addQuad(builder, matrix, left, top + corner, right, bottom - corner, texWidth, texHeight, baseAlpha);
        }
        for (int row = 0; row < corner; row++) {
            double dy = corner - 0.5D - row;
            int innerInset = corner - (int) Math.floor(Math.sqrt(Math.max(0.0D, (corner - 0.65D) * (corner - 0.65D) - dy * dy)));
            int outerInset = Math.max(0, corner - (int) Math.ceil(Math.sqrt(Math.max(0.0D, (corner + 0.65D) * (corner + 0.65D) - dy * dy))) - 1);
            if (right - innerInset > left + innerInset) {
                addQuad(builder, matrix, left + innerInset, top + row, right - innerInset, top + row + 1, texWidth, texHeight, baseAlpha);
                addQuad(builder, matrix, left + innerInset, bottom - 1 - row, right - innerInset, bottom - row, texWidth, texHeight, baseAlpha);
            }
            int limit = Math.min(corner, innerInset);
            for (int col = outerInset; col < limit; col++) {
                double dx = corner - 0.5D - col;
                float cov = Mth.clamp((float) (corner + 0.5D - Math.hypot(dx, dy)), 0.0F, 1.0F);
                int pixelAlpha = Math.round(baseAlpha * cov);
                if (pixelAlpha <= 0) {
                    continue;
                }
                addQuad(builder, matrix, left + col, top + row, left + col + 1, top + row + 1, texWidth, texHeight, pixelAlpha);
                if (right - 1 - col != left + col) {
                    addQuad(builder, matrix, right - 1 - col, top + row, right - col, top + row + 1, texWidth, texHeight, pixelAlpha);
                }
                addQuad(builder, matrix, left + col, bottom - 1 - row, left + col + 1, bottom - row, texWidth, texHeight, pixelAlpha);
                if (right - 1 - col != left + col) {
                    addQuad(builder, matrix, right - 1 - col, bottom - 1 - row, right - col, bottom - row, texWidth, texHeight, pixelAlpha);
                }
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        if (!blendEnabled) {
            RenderSystem.disableBlend();
        }
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();

        int rimColor = PhoneUi.isDark() ? 0x3AFFFFFF : 0x26000000;
        PhoneUi.strokeRounded(guiGraphics, bounds.left, bounds.top, bounds.width, bounds.height, radius, 1, rimColor);
        guiGraphics.flush();
    }

    void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    private static void addQuad(BufferBuilder builder, Matrix4f matrix, int left, int top, int right, int bottom,
                                float texWidth, float texHeight, int alpha) {
        float minU = left / texWidth;
        float maxU = right / texWidth;
        float minV = 1.0F - top / texHeight;
        float maxV = 1.0F - bottom / texHeight;
        builder.addVertex(matrix, left, top, 0.0F).setUv(minU, minV).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, left, bottom, 0.0F).setUv(minU, maxV).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, right, bottom, 0.0F).setUv(maxU, maxV).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, right, top, 0.0F).setUv(maxU, minV).setColor(255, 255, 255, alpha);
    }
}
