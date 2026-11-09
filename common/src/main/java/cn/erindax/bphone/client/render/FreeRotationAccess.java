package cn.erindax.bphone.client.render;

import net.minecraft.client.renderer.block.model.BlockElement;

public interface FreeRotationAccess {
    FreeElementRotation beat$getFreeRotation();

    void beat$setFreeRotation(FreeElementRotation rotation);

    static FreeElementRotation getFreeRotation(BlockElement element) {
        return element instanceof FreeRotationAccess access ? access.beat$getFreeRotation() : null;
    }

    static void setFreeRotation(BlockElement element, FreeElementRotation rotation) {
        if (element instanceof FreeRotationAccess access) {
            access.beat$setFreeRotation(rotation);
        }
    }
}
