package cn.erindax.bphone.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import cn.erindax.bphone.item.MegaphoneItem;
import cn.erindax.bphone.item.WalkieRadioItem;
import cn.erindax.bphone.client.phone.PhoneScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Shadow
    @Final
    private Options options;

    @Inject(method = "tick", at = @At("RETURN"))
    private void beat$applyPhoneCameraMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        if (slowDown && beat$shouldIgnoreUseSlowdown(minecraft)) {
            this.leftImpulse = beat$restoreImpulse(this.leftImpulse, slowDownFactor);
            this.forwardImpulse = beat$restoreImpulse(this.forwardImpulse, slowDownFactor);
        }

        if (!(minecraft.screen instanceof PhoneScreen phoneScreen) || !phoneScreen.isCameraMoveModeActive()) {
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        boolean up = isKeyDown(windowHandle, this.options.keyUp);
        boolean down = isKeyDown(windowHandle, this.options.keyDown);
        boolean left = isKeyDown(windowHandle, this.options.keyLeft);
        boolean right = isKeyDown(windowHandle, this.options.keyRight);

        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.jumping = isKeyDown(windowHandle, this.options.keyJump);
        this.shiftKeyDown = isKeyDown(windowHandle, this.options.keyShift);
        this.forwardImpulse = calculateImpulse(up, down);
        this.leftImpulse = calculateImpulse(left, right);

        if (slowDown) {
            this.leftImpulse *= slowDownFactor;
            this.forwardImpulse *= slowDownFactor;
        }
    }

    private static float calculateImpulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }

        return positive ? 1.0F : -1.0F;
    }

    private static boolean beat$shouldIgnoreUseSlowdown(Minecraft minecraft) {
        return minecraft.player != null
                && (MegaphoneItem.isUsingMegaphone(minecraft.player)
                || WalkieRadioItem.isUsingWalkie(minecraft.player));
    }

    private static float beat$restoreImpulse(float impulse, float slowDownFactor) {
        if (impulse == 0.0F || slowDownFactor == 0.0F) {
            return impulse;
        }

        return impulse / slowDownFactor;
    }

    private static boolean isKeyDown(long windowHandle, KeyMapping keyMapping) {
        if (keyMapping == null) {
            return false;
        }

        InputConstants.Key inputKey = InputConstants.getKey(keyMapping.saveString());
        return inputKey.getType() == InputConstants.Type.KEYSYM
                && inputKey.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(windowHandle, inputKey.getValue());
    }
}
