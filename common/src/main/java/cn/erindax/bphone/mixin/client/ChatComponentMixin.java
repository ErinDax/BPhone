package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.phone.PhoneClientHooks;
import cn.erindax.bphone.client.phone.PhonePublicChat;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void beat$recordPublicChat(Component chatComponent, MessageSignature headerSignature, GuiMessageTag tag,
                                         CallbackInfo ci) {
        PhonePublicChat.accept(chatComponent);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void beat$hideVanillaChat(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused,
                                        CallbackInfo ci) {
        if (PhoneClientHooks.shouldHideVanillaChat(focused)) {
            ci.cancel();
        }
    }
}
