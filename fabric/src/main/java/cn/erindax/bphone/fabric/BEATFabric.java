package cn.erindax.bphone.fabric;

import cn.erindax.bphone.BPhone;
import net.fabricmc.api.ModInitializer;

public final class BEATFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BPhone.init();
    }
}
