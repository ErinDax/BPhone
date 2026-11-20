package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void beat$openPublicChat(String defaultText, CallbackInfo ci) {
        if (PhoneClientHooks.openPublicChat(defaultText)) {
            ci.cancel();
        }
    }
}
