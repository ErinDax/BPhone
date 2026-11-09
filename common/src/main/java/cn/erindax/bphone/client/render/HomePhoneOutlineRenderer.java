package cn.erindax.bphone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public final class HomePhoneOutlineRenderer {
    private static final float COLOR = 0.0F;
    private static final float ALPHA = 0.4F;
    private static final FreeElementRotation TOP_ROTATION =
            FreeElementRotation.of(-150.0F, 0.0F, 180.0F, 7.99822F, 3.5841F, 8.6145F);
    private static final FreeElementRotation BODY_ROTATION =
            FreeElementRotation.of(180.0F, 0.0F, 180.0F, 7.99822F, 3.5841F, 8.6145F);
    private static final Cuboid[] BASE_CUBOIDS = new Cuboid[]{
            new Cuboid(2.0F, 1.96227F, 3.98819F, 14.0F, 3.96227F, 12.98819F, TOP_ROTATION),
            new Cuboid(3.0F, -0.00729F, 9.5F, 13.0F, 1.5F, 11.59131F, BODY_ROTATION),
            new Cuboid(3.0F, -0.00729F, 7.5F, 13.0F, 2.4F, 9.59131F, BODY_ROTATION),
            new Cuboid(3.0F, 0.0F, 4.6F, 13.0F, 4.2F, 7.6F, BODY_ROTATION)
    };
    private static final Cuboid[] HANDSET_CUBOIDS = new Cuboid[]{
            new Cuboid(3.6F, 3.96227F, 4.38819F, 6.0F, 5.16227F, 12.68819F, TOP_ROTATION),
            new Cuboid(3.6F, 2.76227F, 11.38819F, 6.0F, 3.96227F, 13.68819F, TOP_ROTATION),
            new Cuboid(3.6F, 2.76227F, 3.38819F, 6.0F, 3.96227F, 5.68819F, TOP_ROTATION)
    };
    private static final int[][] EDGES = new int[][]{
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5},
            {2, 3}, {2, 6},
            {3, 7},
            {4, 5}, {4, 6},
            {5, 7},
            {6, 7}
    };

    private HomePhoneOutlineRenderer() {
    }

    public static void render(PoseStack poseStack, VertexConsumer consumer, BlockPos pos, Direction facing,
                              boolean hasHandset, double cameraX, double cameraY, double cameraZ) {
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraX, pos.getY() - cameraY, pos.getZ() - cameraZ);

        PoseStack.Pose pose = poseStack.last();
        for (Cuboid cuboid : BASE_CUBOIDS) {
            renderCuboid(pose, consumer, cuboid, facing);
        }
        if (hasHandset) {
            for (Cuboid cuboid : HANDSET_CUBOIDS) {
                renderCuboid(pose, consumer, cuboid, facing);
            }
        }

        poseStack.popPose();
    }

    private static void renderCuboid(PoseStack.Pose pose, VertexConsumer consumer, Cuboid cuboid, Direction facing) {
        Vector3f[] corners = cuboid.corners();
        for (Vector3f corner : corners) {
            corner.div(16.0F);
            cuboid.rotation().apply(corner);
            rotateForFacing(corner, facing);
        }

        for (int[] edge : EDGES) {
            drawLine(pose, consumer, corners[edge[0]], corners[edge[1]]);
        }
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer consumer, Vector3f start, Vector3f end) {
        Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() <= 1.0E-6F) {
            return;
        }
        direction.normalize();

        consumer.addVertex(pose, start.x(), start.y(), start.z())
                .setColor(COLOR, COLOR, COLOR, ALPHA)
                .setNormal(pose, direction.x(), direction.y(), direction.z());
        consumer.addVertex(pose, end.x(), end.y(), end.z())
                .setColor(COLOR, COLOR, COLOR, ALPHA)
                .setNormal(pose, direction.x(), direction.y(), direction.z());
    }

    private static void rotateForFacing(Vector3f point, Direction facing) {
        float x = point.x;
        float z = point.z;
        switch (facing) {
            case EAST -> point.set(1.0F - z, point.y, x);
            case SOUTH -> point.set(1.0F - x, point.y, 1.0F - z);
            case WEST -> point.set(z, point.y, 1.0F - x);
            default -> {
            }
        }
    }

    private record Cuboid(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                          FreeElementRotation rotation) {
        private Vector3f[] corners() {
            return new Vector3f[]{
                    new Vector3f(minX, minY, minZ),
                    new Vector3f(maxX, minY, minZ),
                    new Vector3f(minX, maxY, minZ),
                    new Vector3f(maxX, maxY, minZ),
                    new Vector3f(minX, minY, maxZ),
                    new Vector3f(maxX, minY, maxZ),
                    new Vector3f(minX, maxY, maxZ),
                    new Vector3f(maxX, maxY, maxZ)
            };
        }
    }
}
