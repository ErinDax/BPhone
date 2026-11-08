package cn.erindax.bphone.mixin.client;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.client.phone.PhoneClientHooks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    private static final float SELFIE_ARM_PITCH = -(100.0F * ((float) Math.PI / 180.0F));
    private static final float SELFIE_ARM_YAW = 0.12F;
    private static final float SELFIE_ARM_ROLL = 0.12F;
    private static final float SELFIE_ARM_HEAD_PITCH_FOLLOW = 0.80F;
    private static final float SELFIE_ARM_HEAD_YAW_FOLLOW = 0.92F;
    private static final float CAMERA_ARM_PITCH = -(100.0F * ((float) Math.PI / 180.0F));
    private static final float CAMERA_ARM_YAW = 0.22F;
    private static final float CAMERA_ARM_ROLL = 0.08F;
    private static final float QR_ARM_X = 4.25F;
    private static final float QR_ARM_FORWARD_Z = -1.0F;
    private static final float QR_ARM_PITCH = -0.95F;
    private static final float QR_ARM_YAW = 0.22F;
    private static final float QR_ARM_ROLL = 0.08F;
    private static final float MEGAPHONE_ARM_SIDE_OFFSET = 2.15F;
    private static final float MEGAPHONE_ARM_FORWARD_OFFSET = 1.75F;
    private static final float MEGAPHONE_ARM_PITCH = -1.48F;
    private static final float MEGAPHONE_ARM_ROLL = 0.04F;
    private static final float MEGAPHONE_ARM_HEAD_PITCH_FOLLOW = 1.0F;
    private static final float MEGAPHONE_ARM_HEAD_YAW_FOLLOW = 1.0F;
    private static final float WALKIE_ARM_SIDE_OFFSET = 1.8F;
    private static final float WALKIE_ARM_PITCH = -1.48F;
    private static final float WALKIE_ARM_YAW_OFFSET = 0.0F;
    private static final float WALKIE_ARM_ROLL = 0.0F;
    private static final float WALKIE_ARM_HEAD_PITCH_FOLLOW = 1.0F;
    private static final float WALKIE_ARM_HEAD_YAW_FOLLOW = 1.0F;
    private static final float HANDSET_ARM_PITCH = -(134.0F * ((float) Math.PI / 180.0F));
    private static final float HANDSET_ARM_YAW = 0.08F;
    private static final float HANDSET_ARM_ROLL = 0.04F;

    @Shadow
    public ModelPart head;

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void beat$raisePhoneArm(T entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        HumanoidArm megaphoneArm = beat$getMegaphoneArm(entity);
        if (megaphoneArm != null) {
            ModelPart raisedArm = megaphoneArm == HumanoidArm.RIGHT ? rightArm : leftArm;
            float side = megaphoneArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            beat$applyMegaphonePose(raisedArm, side);
            return;
        }

        HumanoidArm walkieArm = beat$getWalkieArm(entity);
        if (walkieArm != null) {
            ModelPart raisedArm = walkieArm == HumanoidArm.RIGHT ? rightArm : leftArm;
            float side = walkieArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            beat$applyWalkiePose(raisedArm, side);
            return;
        }

        if (!PhoneClientHooks.shouldRaisePhoneArm(entity)) {
            HumanoidArm handsetArm = beat$getHandsetArm(entity);
            if (handsetArm == null) {
                return;
            }

            ModelPart raisedArm = handsetArm == HumanoidArm.RIGHT ? rightArm : leftArm;
            float side = handsetArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            beat$applyHandsetPose(raisedArm, side);
            return;
        }

        HumanoidArm phoneArm = PhoneClientHooks.getRaisedPhoneArm(entity);
        ModelPart raisedArm = phoneArm == HumanoidArm.RIGHT ? rightArm : leftArm;
        float side = phoneArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        if (PhoneClientHooks.shouldUsePhoneCallPose(entity)) {
            beat$applyHandsetPose(raisedArm, side);
            return;
        }

        if (PhoneClientHooks.shouldUsePhoneQrModel(entity) || PhoneClientHooks.shouldUseChatQrModel(entity)) {
            raisedArm.x = -QR_ARM_X * side;
            raisedArm.z = QR_ARM_FORWARD_Z;
            raisedArm.xRot = Mth.clamp(head.xRot + QR_ARM_PITCH, -1.80F, 0.35F);
            raisedArm.yRot = head.yRot - (QR_ARM_YAW * side);
            raisedArm.zRot = QR_ARM_ROLL * side;
            return;
        }

        boolean selfieMode = PhoneClientHooks.shouldUseSelfieCamera(entity);

        if (selfieMode) {
            beat$applyHeadFollowingPhonePose(
                    raisedArm,
                    side,
                    SELFIE_ARM_PITCH,
                    SELFIE_ARM_YAW,
                    SELFIE_ARM_ROLL,
                    SELFIE_ARM_HEAD_PITCH_FOLLOW,
                    SELFIE_ARM_HEAD_YAW_FOLLOW,
                    -1.90F);
            return;
        }

        beat$applyHeadFollowingPhonePose(
                raisedArm,
                side,
                CAMERA_ARM_PITCH,
                CAMERA_ARM_YAW,
                CAMERA_ARM_ROLL,
                1.0F,
                1.0F,
                -1.80F);
    }

    @Unique
    private static HumanoidArm beat$getMegaphoneArm(LivingEntity entity) {
        if (!entity.isUsingItem() || !entity.getUseItem().is(ModItems.MEGAPHONE.get())) {
            return null;
        }

        return entity.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? entity.getMainArm()
                : entity.getMainArm().getOpposite();
    }

    @Unique
    private static HumanoidArm beat$getWalkieArm(LivingEntity entity) {
        if (!entity.isUsingItem() || !entity.getUseItem().is(ModItems.WALKIE.get())) {
            return null;
        }

        return entity.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? entity.getMainArm()
                : entity.getMainArm().getOpposite();
    }

    @Unique
    private static HumanoidArm beat$getHandsetArm(LivingEntity entity) {
        if (entity.getMainHandItem().is(ModItems.HOME_PHONE_HANDSET.get())) {
            return entity.getMainArm();
        }

        if (entity.getOffhandItem().is(ModItems.HOME_PHONE_HANDSET.get())) {
            return entity.getMainArm().getOpposite();
        }

        return null;
    }

    @Unique
    private void beat$applyMegaphonePose(ModelPart raisedArm, float side) {
        float headYaw = head.yRot;
        float sideOffset = MEGAPHONE_ARM_SIDE_OFFSET * side;
        float forwardOffset = MEGAPHONE_ARM_FORWARD_OFFSET;
        raisedArm.x = (-sideOffset * Mth.cos(headYaw)) - (forwardOffset * Mth.sin(headYaw));
        raisedArm.z = (sideOffset * Mth.sin(headYaw)) - (forwardOffset * Mth.cos(headYaw));
        raisedArm.xRot = Mth.clamp((head.xRot * MEGAPHONE_ARM_HEAD_PITCH_FOLLOW) + MEGAPHONE_ARM_PITCH, -2.05F, 0.25F);
        raisedArm.yRot = head.yRot * MEGAPHONE_ARM_HEAD_YAW_FOLLOW;
        raisedArm.zRot = MEGAPHONE_ARM_ROLL * side;
    }

    @Unique
    private void beat$applyWalkiePose(ModelPart raisedArm, float side) {
        raisedArm.x = -5.0F * side;
        raisedArm.z = 0.0F;
        raisedArm.xRot = Mth.clamp((head.xRot * 0.5F) + WALKIE_ARM_PITCH, -2.15F, 0.10F);
        raisedArm.yRot = (head.yRot * 0.5F) - (0.26F * side);
        raisedArm.zRot = 0.0F;
    }

    @Unique
    private void beat$applyHeadFollowingPhonePose(ModelPart raisedArm, float side, float pitch,
                                                        float yawOffset, float roll, float pitchFollow,
                                                        float yawFollow, float minPitch) {
        raisedArm.xRot = Mth.clamp((head.xRot * pitchFollow) + pitch, minPitch, 0.35F);
        raisedArm.yRot = (head.yRot * yawFollow) - (yawOffset * side);
        raisedArm.zRot = (roll * side) - (head.yRot * 0.08F * side);
    }

    @Unique
    private static void beat$applyHandsetPose(ModelPart raisedArm, float side) {
        raisedArm.xRot = HANDSET_ARM_PITCH;
        raisedArm.yRot = HANDSET_ARM_YAW * side;
        raisedArm.zRot = HANDSET_ARM_ROLL * side;
    }
}
