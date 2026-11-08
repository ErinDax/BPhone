package cn.erindax.bphone.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import cn.erindax.bphone.client.phone.PhoneScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;isDown()Z"
            )
    )
    private boolean beat$allowSprintWhilePhoneCameraMoves(KeyMapping keyMapping) {
        if (keyMapping != this.minecraft.options.keySprint || !isPhoneCameraMoveModeActive()) {
            return keyMapping.isDown();
        }

        return isPhysicalKeyDown(keyMapping);
    }

    private boolean isPhoneCameraMoveModeActive() {
        return this.minecraft.screen instanceof PhoneScreen phoneScreen
                && phoneScreen.isCameraMoveModeActive();
    }

    private boolean isPhysicalKeyDown(KeyMapping keyMapping) {
        long windowHandle = this.minecraft.getWindow().getWindow();
        InputConstants.Key inputKey = InputConstants.getKey(keyMapping.saveString());

        if (inputKey.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }

        return switch (inputKey.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(windowHandle, inputKey.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(windowHandle, inputKey.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
