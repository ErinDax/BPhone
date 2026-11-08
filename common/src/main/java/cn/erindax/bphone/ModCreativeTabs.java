package cn.erindax.bphone;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(BPhone.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeTabRegistry.create(Component.translatable("itemGroup.bphone.main"),
                    () -> new ItemStack(ModItems.PHONE.get())));

    private ModCreativeTabs() {
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        CREATIVE_MODE_TABS.register();
        CreativeTabRegistry.append(MAIN, ModItems.HOME_PHONE);
        for (RegistrySupplier<Item> phoneSupplier : ModItems.PHONES) {
            CreativeTabRegistry.append(MAIN, phoneSupplier);
        }
        CreativeTabRegistry.append(MAIN, ModItems.MEGAPHONE);
        for (DyeColor color : DyeColor.values()) {
            CreativeTabRegistry.appendStack(CreativeModeTabs.SEARCH, () -> dyedMegaphone(color));
        }
        CreativeTabRegistry.append(MAIN, ModItems.AIRSTRIKE_RADIO);
        CreativeTabRegistry.append(MAIN, ModItems.WALKIE);
    }

    private static ItemStack dyedMegaphone(DyeColor color) {
        ItemStack stack = new ItemStack(ModItems.MEGAPHONE.get());
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColorToRgb(color), false));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.bphone.megaphone.dyed",
                Component.translatable("color.minecraft." + color.getName())));
        return stack;
    }

    private static int dyeColorToRgb(DyeColor color) {
        return color.getTextureDiffuseColor();
    }
}
