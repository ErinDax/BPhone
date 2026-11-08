package cn.erindax.bphone.item;

import cn.erindax.bphone.BPhone;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

public final class PhoneItem extends Item {
    public static final int DEFAULT_COLOR = 0xFF222323;

    public static final String NAME_TRANSLATION_KEY = "item.bphone.phone";

    private final int defaultDyeColor;
    private final int nameColor;

    public PhoneItem(int defaultDyeColor, int nameColor, Properties properties) {
        super(properties);
        this.defaultDyeColor = defaultDyeColor;
        this.nameColor = nameColor;
    }

    public int getColor(ItemStack stack) {
        return stack == null ? defaultDyeColor : DyedItemColor.getOrDefault(stack, defaultDyeColor);
    }

    public int defaultDyeColor() {
        return defaultDyeColor;
    }

    public int nameColor() {
        return nameColor;
    }

    @Override
    public String getDescriptionId() {
        return NAME_TRANSLATION_KEY;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(style -> style.withColor(this.nameColor));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openPhoneScreenClient(hand);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }

    private static void openPhoneScreenClient(InteractionHand hand) {
        try {
            Class<?> hookClass = Class.forName("cn.erindax.bphone.client.phone.PhoneClientHooks");
            Method openMethod = hookClass.getDeclaredMethod("openScreen", InteractionHand.class);
            openMethod.invoke(null, hand);
        } catch (ReflectiveOperationException exception) {
            BPhone.LOGGER.debug("Failed to open phone screen", exception);
        }
    }
}
