package cn.erindax.bphone.fabric.client;

import net.minecraft.world.item.Item;

import dev.architectury.registry.registries.RegistrySupplier;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.ModParticles;
import cn.erindax.bphone.client.BEATClient;
import cn.erindax.bphone.client.particle.MegaphoneWaveParticle;
import cn.erindax.bphone.item.MegaphoneItem;
import cn.erindax.bphone.item.PhoneItem;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;

public final class BEATFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BEATClient.init();
        ColorProviderRegistry.ITEM.register((stack, tintIndex) ->
                        tintIndex == 0 && stack.getItem() instanceof MegaphoneItem megaphoneItem
                                ? megaphoneItem.getColor(stack)
                                : -1,
                ModItems.MEGAPHONE.get());
        for (RegistrySupplier<Item> phoneSupplier : ModItems.PHONES) {
        ColorProviderRegistry.ITEM.register((stack, tintIndex) ->
                        tintIndex == 0 && stack.getItem() instanceof PhoneItem phoneItem
                                ? phoneItem.getColor(stack)
                                : -1,
                phoneSupplier.get());
        }
        ParticleFactoryRegistry.getInstance().register(ModParticles.MEGAPHONE_WAVE.get(), MegaphoneWaveParticle.Provider::new);
    }
}
