package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.render.FreeElementRotation;
import cn.erindax.bphone.client.render.FreeRotationAccess;
import net.minecraft.client.renderer.block.model.BlockElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockElement.class)
public abstract class BlockElementMixin implements FreeRotationAccess {
    @Unique
    private FreeElementRotation beat$freeRotation;

    @Override
    public FreeElementRotation beat$getFreeRotation() {
        return beat$freeRotation;
    }

    @Override
    public void beat$setFreeRotation(FreeElementRotation rotation) {
        beat$freeRotation = rotation;
    }
}
