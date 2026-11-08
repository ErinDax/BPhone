package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double SELFIE_FORWARD_OFFSET = 1.45D;
    private static final double SELFIE_SIDE_OFFSET = 0.10D;
    private static final double SELFIE_VERTICAL_OFFSET = -0.08D;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void beat$attachSelfieCameraToPhone(BlockGetter level, Entity entity,
                                                      boolean detached, boolean mirrored,
                                                      float partialTick, CallbackInfo ci) {
        if (!(entity instanceof LocalPlayer localPlayer) || !PhoneClientHooks.shouldUseSelfieCamera(localPlayer)) {
            return;
        }

        HumanoidArm phoneArm = PhoneClientHooks.getRaisedPhoneArm(localPlayer);
        double side = phoneArm == HumanoidArm.RIGHT ? 1.0D : -1.0D;

        Vec3 look = localPlayer.getViewVector(partialTick).normalize();
        Vec3 right = look.cross(WORLD_UP);
        if (right.lengthSqr() < 1.0E-4D) {
            right = Vec3.directionFromRotation(0.0F, localPlayer.getYRot() - 90.0F);
        } else {
            right = right.normalize();
        }

        Vec3 phoneCameraPosition = localPlayer.getEyePosition(partialTick)
                .add(look.scale(SELFIE_FORWARD_OFFSET))
                .add(right.scale(SELFIE_SIDE_OFFSET * side))
                .add(0.0D, SELFIE_VERTICAL_OFFSET, 0.0D);
        this.setPosition(phoneCameraPosition);
    }
}
