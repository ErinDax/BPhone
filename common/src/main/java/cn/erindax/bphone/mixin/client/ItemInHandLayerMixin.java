package cn.erindax.bphone.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void beat$hideLocalSelfiePhone(LivingEntity livingEntity, ItemStack itemStack,
                                                 ItemDisplayContext itemDisplayContext, HumanoidArm humanoidArm,
                                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                 CallbackInfo ci) {
        if (livingEntity instanceof LocalPlayer localPlayer
                && ModItems.isPhone(itemStack)
                && PhoneClientHooks.shouldUseSelfieCamera(localPlayer)) {
            ci.cancel();
        }
    }
}
