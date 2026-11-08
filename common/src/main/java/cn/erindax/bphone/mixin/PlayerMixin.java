package cn.erindax.bphone.mixin;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.phone.PhoneCallPoseAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin implements PhoneCallPoseAccess {
    @Unique
    private static final EntityDataAccessor<Boolean> MINDEVICE_PHONE_CALL_POSE = SynchedEntityData.defineId(
            Player.class,
            EntityDataSerializers.BOOLEAN
    );
    @Unique
    private static final EntityDataAccessor<Boolean> MINDEVICE_PHONE_CAMERA_POSE = SynchedEntityData.defineId(
            Player.class,
            EntityDataSerializers.BOOLEAN
    );
    @Unique
    private static final EntityDataAccessor<Boolean> MINDEVICE_PHONE_CAMERA_SELFIE = SynchedEntityData.defineId(
            Player.class,
            EntityDataSerializers.BOOLEAN
    );
    @Unique
    private static final EntityDataAccessor<Boolean> MINDEVICE_PHONE_BANK_QR_POSE = SynchedEntityData.defineId(
            Player.class,
            EntityDataSerializers.BOOLEAN
    );
    @Unique
    private static final EntityDataAccessor<Boolean> MINDEVICE_PHONE_CHAT_QR_POSE = SynchedEntityData.defineId(
            Player.class,
            EntityDataSerializers.BOOLEAN
    );
    @Unique
    private static final EntityDataAccessor<Boolean> MINDEVICE_PHONE_SCREEN_ON = SynchedEntityData.defineId(
            Player.class,
            EntityDataSerializers.BOOLEAN
    );

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void beat$definePhoneCallPoseData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(MINDEVICE_PHONE_CALL_POSE, false);
        builder.define(MINDEVICE_PHONE_CAMERA_POSE, false);
        builder.define(MINDEVICE_PHONE_CAMERA_SELFIE, false);
        builder.define(MINDEVICE_PHONE_BANK_QR_POSE, false);
        builder.define(MINDEVICE_PHONE_CHAT_QR_POSE, false);
        builder.define(MINDEVICE_PHONE_SCREEN_ON, false);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void beat$preventDroppingHomePhoneHandset(ItemStack stack, boolean dropAround,
                                                            boolean includeThrowerName,
                                                            CallbackInfoReturnable<ItemEntity> cir) {
        if (stack.is(ModItems.HOME_PHONE_HANDSET.get())) {
            cir.setReturnValue(null);
        }
    }

    @Override
    public boolean beat$isPhoneCallPoseActive() {
        return ((Player) (Object) this).getEntityData().get(MINDEVICE_PHONE_CALL_POSE);
    }

    @Override
    public void beat$setPhoneCallPoseActive(boolean active) {
        ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_CALL_POSE, active);
    }

    @Override
    public boolean beat$isPhoneCameraPoseActive() {
        return ((Player) (Object) this).getEntityData().get(MINDEVICE_PHONE_CAMERA_POSE);
    }

    @Override
    public void beat$setPhoneCameraPoseActive(boolean active) {
        ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_CAMERA_POSE, active);
        if (!active) {
            ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_CAMERA_SELFIE, false);
        }
    }

    @Override
    public boolean beat$isPhoneCameraSelfieActive() {
        return ((Player) (Object) this).getEntityData().get(MINDEVICE_PHONE_CAMERA_SELFIE);
    }

    @Override
    public void beat$setPhoneCameraSelfieActive(boolean active) {
        ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_CAMERA_SELFIE, active);
    }

    @Override
    public boolean beat$isPhoneBankQrPoseActive() {
        return ((Player) (Object) this).getEntityData().get(MINDEVICE_PHONE_BANK_QR_POSE);
    }

    @Override
    public void beat$setPhoneBankQrPoseActive(boolean active) {
        ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_BANK_QR_POSE, active);
    }

    @Override
    public boolean beat$isPhoneChatQrPoseActive() {
        return ((Player) (Object) this).getEntityData().get(MINDEVICE_PHONE_CHAT_QR_POSE);
    }

    @Override
    public void beat$setPhoneChatQrPoseActive(boolean active) {
        ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_CHAT_QR_POSE, active);
    }

    @Override
    public boolean beat$isPhoneScreenOnActive() {
        return ((Player) (Object) this).getEntityData().get(MINDEVICE_PHONE_SCREEN_ON);
    }

    @Override
    public void beat$setPhoneScreenOnActive(boolean active) {
        ((Player) (Object) this).getEntityData().set(MINDEVICE_PHONE_SCREEN_ON, active);
    }
}
