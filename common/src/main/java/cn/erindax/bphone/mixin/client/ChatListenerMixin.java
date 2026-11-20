package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.phone.PhonePublicChat;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Inject(method = "showMessageToPlayer", at = @At("HEAD"))
    private void beat$beginPlayerMessage(ChatType.Bound boundChatType, PlayerChatMessage chatMessage,
                                           Component decoratedServerContent, GameProfile gameProfile,
                                           boolean onlyShowSecureChat, Instant timestamp,
                                           CallbackInfoReturnable<Boolean> cir) {
        PhonePublicChat.beginPlayerMessage(boundChatType, chatMessage, gameProfile);
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
    private void beat$endPlayerMessage(ChatType.Bound boundChatType, PlayerChatMessage chatMessage,
                                         Component decoratedServerContent, GameProfile gameProfile,
                                         boolean onlyShowSecureChat, Instant timestamp,
                                         CallbackInfoReturnable<Boolean> cir) {
        PhonePublicChat.endPlayerMessage();
    }
}
