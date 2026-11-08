package cn.erindax.bphone.neoforge;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.neoforge.client.BEATNeoForgeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(BPhone.MOD_ID)
public final class BEATNeoForge {
    public BEATNeoForge(IEventBus modEventBus, Dist dist) {
        BPhone.init();
        if (dist.isClient()) {
            BEATNeoForgeClient.register(modEventBus);
        }
    }
}
