package cn.erindax.bphone;

import cn.erindax.bphone.block.HomePhoneBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BPhone.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> HOME_PHONE = BLOCKS.register("home_phone",
            () -> new HomePhoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    private ModBlocks() {
    }

    public static void init() {
        BLOCKS.register();
    }
}
