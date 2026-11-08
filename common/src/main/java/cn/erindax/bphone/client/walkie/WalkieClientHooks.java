package cn.erindax.bphone.client.walkie;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class WalkieClientHooks {
    private WalkieClientHooks() {
    }

    public static void openScreen(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new WalkieScreen(hand));
    }
}
