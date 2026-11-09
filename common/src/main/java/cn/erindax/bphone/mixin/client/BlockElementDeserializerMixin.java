package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.render.FreeElementRotation;
import cn.erindax.bphone.client.render.FreeRotationAccess;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public abstract class BlockElementDeserializerMixin {
    @Inject(method = "getRotation(Lcom/google/gson/JsonObject;)Lnet/minecraft/client/renderer/block/model/BlockElementRotation;",
            at = @At("HEAD"), cancellable = true)
    private void beat$skipFreeRotation(JsonObject json, CallbackInfoReturnable<BlockElementRotation> cir) {
        if (FreeElementRotation.isFree(json)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockElement;",
            at = @At("RETURN"))
    private void beat$attachFreeRotation(JsonElement json, Type type, JsonDeserializationContext context,
                                         CallbackInfoReturnable<BlockElement> cir) {
        FreeElementRotation rotation = FreeElementRotation.fromJson(json.getAsJsonObject());
        if (rotation != null) {
            FreeRotationAccess.setFreeRotation(cir.getReturnValue(), rotation);
        }
    }
}
