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
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class PhonePanelBuffer {
    private static final int RIM_COLOR = 0x50000000;

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
        guiGraphics.flush();
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        if (target == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        PhoneUi.fillRounded(guiGraphics, bounds.left - 1, bounds.top - 1, bounds.width + 2, bounds.height + 2,
                radius + 1, RIM_COLOR);
        guiGraphics.flush();

        int density = PhoneUi.pixelDensity();
        int left = bounds.left * density;
        int top = bounds.top * density;
        int right = (bounds.left + bounds.width) * density;
        int bottom = (bounds.top + bounds.height) * density;
        int corner = Math.max(0, Math.min(radius, Math.min(bounds.width, bounds.height) / 2)) * density;
        float texWidth = target.width;
        float texHeight = target.height;
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1.0F / density, 1.0F / density, 1.0F);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        addStrip(builder, matrix, left, top + corner, right, bottom - corner, texWidth, texHeight);
        for (int row = 0; row < corner; row++) {
            int inset = PhoneUi.roundInset(corner, row);
            addStrip(builder, matrix, left + inset, top + row, right - inset, top + row + 1, texWidth, texHeight);
            addStrip(builder, matrix, left + inset, bottom - 1 - row, right - inset, bottom - row, texWidth, texHeight);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        if (blendEnabled) {
            RenderSystem.enableBlend();
        }
        RenderSystem.enableDepthTest();
        guiGraphics.pose().popPose();
    }

    void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    private static void addStrip(BufferBuilder builder, Matrix4f matrix, int left, int top, int right, int bottom,
                                 float texWidth, float texHeight) {
        float minU = left / texWidth;
        float maxU = right / texWidth;
        float minV = 1.0F - top / texHeight;
        float maxV = 1.0F - bottom / texHeight;
        builder.addVertex(matrix, left, top, 0.0F).setUv(minU, minV);
        builder.addVertex(matrix, left, bottom, 0.0F).setUv(minU, maxV);
        builder.addVertex(matrix, right, bottom, 0.0F).setUv(maxU, maxV);
        builder.addVertex(matrix, right, top, 0.0F).setUv(maxU, minV);
    }
}
