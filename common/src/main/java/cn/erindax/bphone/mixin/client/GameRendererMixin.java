package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void beat$applyPhoneCameraZoom(Camera camera, float partialTick, boolean useFovSetting,
                                                 CallbackInfoReturnable<Double> cir) {
        float zoomFactor = PhoneClientHooks.getActivePhoneCameraZoomFactor();
        if (zoomFactor <= 0.0F || Math.abs(zoomFactor - 1.0F) < 0.001F) {
            return;
        }

        cir.setReturnValue(cir.getReturnValue() / Math.max(0.01F, zoomFactor));
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void beat$hideBlockOutlineInPhoneCamera(CallbackInfoReturnable<Boolean> cir) {
        if (PhoneClientHooks.shouldHideBlockOutline()) {
            cir.setReturnValue(false);
        }
    }
}
