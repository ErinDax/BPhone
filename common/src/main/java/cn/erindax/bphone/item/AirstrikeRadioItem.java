package cn.erindax.bphone.item;

import cn.erindax.bphone.item.ItemNbt;

import cn.erindax.bphone.airstrike.AirstrikeManager;
import cn.erindax.bphone.airstrike.AirstrikeMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AirstrikeRadioItem extends Item {
    private static final String MODE_TAG = "AirstrikeMode";

    public AirstrikeRadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                AirstrikeManager.cancelSelection(serverPlayer);
            } else {
                AirstrikeManager.useRadio(serverPlayer, stack);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static AirstrikeMode getMode(ItemStack stack) {
        if (stack == null || !ItemNbt.hasTag(stack)) {
            return AirstrikeMode.BOMBARDMENT;
        }

        CompoundTag tag = ItemNbt.getTag(stack);
        return tag == null
                ? AirstrikeMode.BOMBARDMENT
                : AirstrikeMode.byOrdinal(tag.getInt(MODE_TAG));
    }

    public static AirstrikeMode cycleMode(ItemStack stack) {
        AirstrikeMode nextMode = getMode(stack).next();
        ItemNbt.getOrCreateTag(stack).putInt(MODE_TAG, nextMode.ordinal());
        return nextMode;
    }
}
