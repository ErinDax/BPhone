package cn.erindax.bphone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

public final class AirstrikeTargetRenderer {
    private static final float MARKER_SIZE = 0.42F;
    private static final float RED = 1.0F;
    private static final float GREEN = 0.12F;
    private static final float BLUE = 0.12F;
    private static final float ALPHA = 0.95F;

    private AirstrikeTargetRenderer() {
    }

    public static void render(PoseStack poseStack, Camera camera, double x, double y, double z) {
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(x - camera.getPosition().x, y - camera.getPosition().y, z - camera.getPosition().z);
        poseStack.mulPose(camera.rotation());

        PoseStack.Pose pose = poseStack.last();
        drawLine(pose, consumer, new Vector3f(-MARKER_SIZE, -MARKER_SIZE, 0.0F), new Vector3f(MARKER_SIZE, MARKER_SIZE, 0.0F));
        drawLine(pose, consumer, new Vector3f(-MARKER_SIZE, MARKER_SIZE, 0.0F), new Vector3f(MARKER_SIZE, -MARKER_SIZE, 0.0F));

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer consumer, Vector3f start, Vector3f end) {
        Vector3f normal = new Vector3f(end).sub(start).normalize();
        consumer.addVertex(pose, start.x(), start.y(), start.z())
                .setColor(RED, GREEN, BLUE, ALPHA)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
        consumer.addVertex(pose, end.x(), end.y(), end.z())
                .setColor(RED, GREEN, BLUE, ALPHA)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
    }
}
