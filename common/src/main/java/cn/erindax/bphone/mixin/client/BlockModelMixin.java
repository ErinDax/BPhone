package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.render.FreeElementRotation;
import cn.erindax.bphone.client.render.FreeRotationAccess;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModel.class)
public abstract class BlockModelMixin {
    @Inject(method = "bakeFace(Lnet/minecraft/client/renderer/block/model/BlockElement;Lnet/minecraft/client/renderer/block/model/BlockElementFace;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/core/Direction;Lnet/minecraft/client/resources/model/ModelState;)Lnet/minecraft/client/renderer/block/model/BakedQuad;",
            at = @At("HEAD"), cancellable = true)
    private static void beat$bakeFreeRotatedFace(BlockElement element, BlockElementFace face, TextureAtlasSprite sprite,
                                                 Direction direction, ModelState state,
                                                 CallbackInfoReturnable<BakedQuad> cir) {
        FreeElementRotation rotation = FreeRotationAccess.getFreeRotation(element);
        if (rotation != null) {
            cir.setReturnValue(rotation.bake(element, face, sprite, direction, state));
        }
    }
}
