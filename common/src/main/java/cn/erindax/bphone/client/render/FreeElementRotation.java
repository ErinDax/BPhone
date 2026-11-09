package cn.erindax.bphone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Locale;

public record FreeElementRotation(Matrix3f matrix, Vector3f origin) {
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final int NORMAL_INDEX = 7;

    public static FreeElementRotation of(float x, float y, float z, float originX, float originY, float originZ) {
        Matrix3f matrix = new Matrix3f()
                .rotationZ((float) Math.toRadians(z))
                .rotateY((float) Math.toRadians(y))
                .rotateX((float) Math.toRadians(x));
        return new FreeElementRotation(matrix, new Vector3f(originX, originY, originZ).div(16.0F));
    }

    public static boolean isFree(JsonObject element) {
        if (!element.has("rotation") || !element.get("rotation").isJsonObject()) {
            return false;
        }
        JsonObject rotation = element.getAsJsonObject("rotation");
        if (!rotation.has("axis")) {
            return true;
        }
        if (!rotation.has("angle")) {
            return false;
        }
        float angle = rotation.get("angle").getAsFloat();
        return angle != 0.0F && Math.abs(angle) != 22.5F && Math.abs(angle) != 45.0F;
    }

    public static FreeElementRotation fromJson(JsonObject element) {
        if (!isFree(element)) {
            return null;
        }
        JsonObject rotation = element.getAsJsonObject("rotation");
        float x = angle(rotation, "x");
        float y = angle(rotation, "y");
        float z = angle(rotation, "z");
        if (rotation.has("axis")) {
            String axis = rotation.get("axis").getAsString().toLowerCase(Locale.ROOT);
            float angle = rotation.get("angle").getAsFloat();
            x = axis.equals("x") ? angle : 0.0F;
            y = axis.equals("y") ? angle : 0.0F;
            z = axis.equals("z") ? angle : 0.0F;
        }
        float[] origin = {8.0F, 8.0F, 8.0F};
        if (rotation.has("origin")) {
            JsonArray array = rotation.getAsJsonArray("origin");
            for (int index = 0; index < origin.length; index++) {
                origin[index] = array.get(index).getAsFloat();
            }
        }
        return of(x, y, z, origin[0], origin[1], origin[2]);
    }

    private static float angle(JsonObject rotation, String axis) {
        return rotation.has(axis) ? rotation.get(axis).getAsFloat() : 0.0F;
    }

    public void apply(Vector3f position) {
        position.sub(origin).mul(matrix).add(origin);
    }

    public BakedQuad bake(BlockElement element, BlockElementFace face, TextureAtlasSprite sprite, Direction direction,
                          ModelState state) {
        int[] vertices = FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, direction,
                BlockModelRotation.X0_Y0, null, element.shade).getVertices();
        Transformation transformation = state.getRotation();
        Vector3f position = new Vector3f();
        for (int vertex = 0; vertex < FaceBakery.VERTEX_COUNT; vertex++) {
            int offset = vertex * FaceBakery.VERTEX_INT_SIZE;
            position.set(Float.intBitsToFloat(vertices[offset]), Float.intBitsToFloat(vertices[offset + 1]),
                    Float.intBitsToFloat(vertices[offset + 2]));
            apply(position);
            FACE_BAKERY.applyModelRotation(position, transformation);
            vertices[offset] = Float.floatToRawIntBits(position.x());
            vertices[offset + 1] = Float.floatToRawIntBits(position.y());
            vertices[offset + 2] = Float.floatToRawIntBits(position.z());
        }
        if (vertices[NORMAL_INDEX] != 0) {
            syncBakedNormals(vertices);
        }
        return new BakedQuad(vertices, face.tintIndex(), FaceBakery.calculateFacing(vertices), sprite, element.shade);
    }

    private static void syncBakedNormals(int[] vertices) {
        Vector3f edge = position(vertices, 3).sub(position(vertices, 1));
        Vector3f normal = position(vertices, 2).sub(position(vertices, 0)).cross(edge).normalize();
        int packed = (Math.round(normal.x() * 127.0F) & 0xFF)
                | ((Math.round(normal.y() * 127.0F) & 0xFF) << 8)
                | ((Math.round(normal.z() * 127.0F) & 0xFF) << 16);
        for (int vertex = 0; vertex < FaceBakery.VERTEX_COUNT; vertex++) {
            vertices[vertex * FaceBakery.VERTEX_INT_SIZE + NORMAL_INDEX] = packed;
        }
    }

    private static Vector3f position(int[] vertices, int vertex) {
        int offset = vertex * FaceBakery.VERTEX_INT_SIZE;
        return new Vector3f(Float.intBitsToFloat(vertices[offset]), Float.intBitsToFloat(vertices[offset + 1]),
                Float.intBitsToFloat(vertices[offset + 2]));
    }
}
