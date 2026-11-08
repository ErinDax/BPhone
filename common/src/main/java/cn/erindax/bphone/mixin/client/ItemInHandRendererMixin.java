package cn.erindax.bphone.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(
            method = "renderHandsWithItems",
            at = @At("HEAD"),
            cancellable = true
    )
    private void beat$hideHandsWhilePhoneCameraOpen(float partialTick, PoseStack poseStack,
                                                     MultiBufferSource.BufferSource bufferSource,
                                                     LocalPlayer player, int packedLight, CallbackInfo ci) {
        if (PhoneClientHooks.isLocalPhoneCameraPoseActive()) {
            ci.cancel();
        }
    }
}
