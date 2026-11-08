package cn.erindax.bphone.neoforge.client;

import net.minecraft.world.item.Item;

import dev.architectury.registry.registries.RegistrySupplier;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.ModParticles;
import cn.erindax.bphone.client.BEATClient;
import cn.erindax.bphone.client.particle.MegaphoneWaveParticle;
import cn.erindax.bphone.item.MegaphoneItem;
import cn.erindax.bphone.item.PhoneItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class BEATNeoForgeClient {
    private BEATNeoForgeClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BEATNeoForgeClient::onClientSetup);
        modEventBus.addListener(BEATNeoForgeClient::onRegisterItemColors);
        modEventBus.addListener(BEATNeoForgeClient::onRegisterParticleProviders);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(BEATClient::init);
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) ->
                        tintIndex == 0 && stack.getItem() instanceof MegaphoneItem megaphoneItem
                                ? megaphoneItem.getColor(stack)
                                : -1,
                ModItems.MEGAPHONE.get());
        for (RegistrySupplier<Item> phoneSupplier : ModItems.PHONES) {
            event.register((stack, tintIndex) ->
                            tintIndex == 0 && stack.getItem() instanceof PhoneItem phoneItem
                                    ? phoneItem.getColor(stack)
                                    : -1,
                    phoneSupplier.get());
        }
    }

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.MEGAPHONE_WAVE.get(), MegaphoneWaveParticle.Provider::new);
    }
}
