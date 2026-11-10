package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void beat$hideCrosshairInPhoneCamera(GuiGraphics guiGraphics, DeltaTracker deltaTracker,
                                                 CallbackInfo ci) {
        if (PhoneClientHooks.shouldHideCrosshair()) {
            ci.cancel();
        }
    }
}
