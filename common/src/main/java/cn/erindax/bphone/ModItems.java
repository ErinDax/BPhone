package cn.erindax.bphone;

import cn.erindax.bphone.item.AirstrikeRadioItem;
import cn.erindax.bphone.item.HomePhoneHandsetItem;
import cn.erindax.bphone.item.MegaphoneItem;
import cn.erindax.bphone.item.PhoneItem;
import cn.erindax.bphone.item.WalkieRadioItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BPhone.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> PHONE = registerPhone("phone", 0xFFFFFFFF, 0xFFF2F2F2);
    public static final RegistrySupplier<Item> PHONE_BLACK = registerPhone("phone_black", 0xFF383838, 0xFF9A9A9A);
    public static final RegistrySupplier<Item> PHONE_BLUE = registerPhone("phone_blue", 0xFF9AECF1, 0xFF55AAFF);
    public static final RegistrySupplier<Item> PHONE_RED = registerPhone("phone_red", 0xFFF28585, 0xFFFF5555);
    public static final RegistrySupplier<Item> PHONE_YELLOW = registerPhone("phone_yellow", 0xFFF6D75D, 0xFFFFFF55);
    public static final RegistrySupplier<Item> PHONE_AQUA = registerPhone("phone_aqua", 0xFF51AC83, 0xFF55FFD4);
    public static final RegistrySupplier<Item> PHONE_LIGHTGREEN = registerPhone("phone_lightgreen", 0xFFEBF5EB, 0xFFA8E6A8);

    public static final List<RegistrySupplier<Item>> PHONES = List.of(
            PHONE,
            PHONE_BLACK,
            PHONE_BLUE,
            PHONE_RED,
            PHONE_YELLOW,
            PHONE_AQUA,
            PHONE_LIGHTGREEN);

    public static final RegistrySupplier<Item> HOME_PHONE = ITEMS.register("home_phone",
            () -> new BlockItem(ModBlocks.HOME_PHONE.get(), new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> HOME_PHONE_HANDSET = ITEMS.register("home_phone_handset",
            () -> new HomePhoneHandsetItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> MEGAPHONE = ITEMS.register("megaphone",
            () -> new MegaphoneItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> AIRSTRIKE_RADIO = ITEMS.register("airstrike_radio",
            () -> new AirstrikeRadioItem(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> WALKIE = ITEMS.register("walkie",
            () -> new WalkieRadioItem(new Item.Properties().stacksTo(1)));

    public static boolean isPhone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (RegistrySupplier<Item> supplier : PHONES) {
            if (stack.is(supplier.get())) {
                return true;
            }
        }
        return false;
    }

    private static RegistrySupplier<Item> registerPhone(String id, int dyeColor, int nameColor) {
        return ITEMS.register(id, () -> new PhoneItem(dyeColor, nameColor, new Item.Properties().stacksTo(1)));
    }

    private ModItems() {
    }

    public static void init() {
        ITEMS.register();
    }
}
