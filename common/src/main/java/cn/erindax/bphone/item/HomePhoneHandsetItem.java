package cn.erindax.bphone.item;

import cn.erindax.bphone.item.ItemNbt;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.block.entity.HomePhoneRegistry.HomePhoneAddress;
import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

public final class HomePhoneHandsetItem extends Item {
    private static final String PHONE_DIMENSION_TAG = "HomePhoneDimension";
    private static final String PHONE_POS_TAG = "HomePhonePos";
    private static final String PHONE_NUMBER_TAG = "HomePhoneNumber";
    private static final String LOCKED_HOTBAR_SLOT_TAG = "LockedHotbarSlot";

    public HomePhoneHandsetItem(Properties properties) {
        super(properties);
    }

    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos blockPos, Player player) {
        return true;
    }

    public static ItemStack createBoundHandset(HomePhoneAddress address, String phoneNumber) {
        ItemStack stack = new ItemStack(ModItems.HOME_PHONE_HANDSET.get());
        bindTo(stack, address, phoneNumber);
        return stack;
    }

    public static boolean ensurePlayerHasHandset(ServerPlayer player, InteractionHand hand,
                                                 HomePhoneAddress address, String phoneNumber) {
        if (hand == InteractionHand.MAIN_HAND) {
            return enforceMainHandLock(player, address, phoneNumber);
        }

        if (player == null || address == null) {
            return false;
        }

        if (isBoundTo(player.getItemInHand(hand), address)) {
            lockToHotbarSlot(player.getItemInHand(hand), player.getInventory().selected);
            PhoneData.markDirty(player);
            return true;
        }

        ItemStack handset = extractFirstBoundHandset(player, address);
        if (handset.isEmpty()) {
            handset = createBoundHandset(address, phoneNumber);
        }

        removeBoundHandsets(player, address);
        lockToHotbarSlot(handset, player.getInventory().selected);
        player.setItemInHand(hand, handset);
        PhoneData.markDirty(player);
        return true;
    }

    public static boolean enforceMainHandLock(ServerPlayer player, HomePhoneAddress address, String phoneNumber) {
        if (player == null || address == null) {
            return false;
        }

        ItemStack existing = findFirstBoundHandset(player, address);
        int lockedSlot = resolveLockedHotbarSlot(existing, player.getInventory().selected);
        ItemStack handset = extractFirstBoundHandset(player, address);
        if (handset.isEmpty()) {
            handset = createBoundHandset(address, phoneNumber);
        }

        removeBoundHandsets(player, address);
        stashHotbarItem(player, lockedSlot, address);
        lockToHotbarSlot(handset, lockedSlot);
        player.getInventory().items.set(lockedSlot, handset);
        player.getInventory().selected = lockedSlot;
        PhoneData.markDirty(player);
        if (player.connection != null) {
            player.connection.send(new ClientboundSetCarriedItemPacket(lockedSlot));
        }
        return true;
    }

    public static boolean isHoldingBoundHandset(ServerPlayer player, HomePhoneAddress address) {
        return player != null
                && address != null
                && (isBoundTo(player.getMainHandItem(), address) || isBoundTo(player.getOffhandItem(), address));
    }

    public static InteractionHand getHeldBoundHand(ServerPlayer player, HomePhoneAddress address) {
        if (player == null || address == null) {
            return null;
        }

        if (isBoundTo(player.getMainHandItem(), address)) {
            return InteractionHand.MAIN_HAND;
        }

        if (isBoundTo(player.getOffhandItem(), address)) {
            return InteractionHand.OFF_HAND;
        }

        return null;
    }

    public static boolean removeBoundHandsets(ServerPlayer player, HomePhoneAddress address) {
        if (player == null || address == null) {
            return false;
        }

        boolean removed = false;
        if (isBoundTo(player.getMainHandItem(), address)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            removed = true;
        }

        if (isBoundTo(player.getOffhandItem(), address)) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            removed = true;
        }

        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (isBoundTo(stack, address)) {
                player.getInventory().items.set(slot, ItemStack.EMPTY);
                removed = true;
            }
        }

        if (removed) {
            PhoneData.markDirty(player);
        }
        return removed;
    }

    public static boolean isBoundTo(ItemStack stack, HomePhoneAddress address) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof HomePhoneHandsetItem) || address == null) {
            return false;
        }

        HomePhoneAddress boundAddress = getBoundAddress(stack);
        return boundAddress != null
                && boundAddress.dimension().equals(address.dimension())
                && boundAddress.blockPos().equals(address.blockPos());
    }

    public static HomePhoneAddress getBoundAddress(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof HomePhoneHandsetItem) || !ItemNbt.hasTag(stack)) {
            return null;
        }

        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null || !tag.contains(PHONE_DIMENSION_TAG, Tag.TAG_STRING) || !tag.contains(PHONE_POS_TAG, Tag.TAG_LONG)) {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(PHONE_DIMENSION_TAG));
        if (dimensionId == null) {
            return null;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockPos blockPos = BlockPos.of(tag.getLong(PHONE_POS_TAG));
        return new HomePhoneAddress(dimension, blockPos);
    }

    private static void bindTo(ItemStack stack, HomePhoneAddress address, String phoneNumber) {
        CompoundTag tag = ItemNbt.getOrCreateTag(stack);
        tag.putString(PHONE_DIMENSION_TAG, address.dimension().location().toString());
        tag.putLong(PHONE_POS_TAG, address.blockPos().asLong());
        tag.putString(PHONE_NUMBER_TAG, phoneNumber == null ? "" : phoneNumber);
    }

    private static void lockToHotbarSlot(ItemStack stack, int slot) {
        ItemNbt.getOrCreateTag(stack).putInt(LOCKED_HOTBAR_SLOT_TAG, Mth.clamp(slot, 0, 8));
    }

    private static int resolveLockedHotbarSlot(ItemStack stack, int fallbackSlot) {
        if (stack != null && !stack.isEmpty() && ItemNbt.hasTag(stack)) {
            CompoundTag tag = ItemNbt.getTag(stack);
            if (tag != null && tag.contains(LOCKED_HOTBAR_SLOT_TAG, Tag.TAG_INT)) {
                return Mth.clamp(tag.getInt(LOCKED_HOTBAR_SLOT_TAG), 0, 8);
            }
        }
        return Mth.clamp(fallbackSlot, 0, 8);
    }

    private static ItemStack findFirstBoundHandset(ServerPlayer player, HomePhoneAddress address) {
        if (player == null || address == null) {
            return ItemStack.EMPTY;
        }

        if (isBoundTo(player.getMainHandItem(), address)) {
            return player.getMainHandItem();
        }

        if (isBoundTo(player.getOffhandItem(), address)) {
            return player.getOffhandItem();
        }

        for (ItemStack stack : player.getInventory().items) {
            if (isBoundTo(stack, address)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack extractFirstBoundHandset(ServerPlayer player, HomePhoneAddress address) {
        if (player == null || address == null) {
            return ItemStack.EMPTY;
        }

        if (isBoundTo(player.getMainHandItem(), address)) {
            ItemStack stack = player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return stack;
        }

        if (isBoundTo(player.getOffhandItem(), address)) {
            ItemStack stack = player.getOffhandItem();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return stack;
        }

        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (isBoundTo(stack, address)) {
                player.getInventory().items.set(slot, ItemStack.EMPTY);
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static void stashHotbarItem(ServerPlayer player, int hotbarSlot, HomePhoneAddress address) {
        ItemStack current = player.getInventory().items.get(hotbarSlot);
        if (current.isEmpty() || isBoundTo(current, address)) {
            return;
        }

        player.getInventory().items.set(hotbarSlot, ItemStack.EMPTY);
        if (!stashStack(player, current, hotbarSlot)) {
            player.drop(current, false);
        }
    }

    private static boolean stashStack(ServerPlayer player, ItemStack stack, int excludedHotbarSlot) {
        if (stack.isEmpty()) {
            return true;
        }

        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (slot == excludedHotbarSlot) {
                continue;
            }

            ItemStack candidate = player.getInventory().items.get(slot);
            if (candidate.isEmpty()) {
                player.getInventory().items.set(slot, stack);
                return true;
            }
        }

        for (int slot = 0; slot < player.getInventory().offhand.size(); slot++) {
            ItemStack candidate = player.getInventory().offhand.get(slot);
            if (candidate.isEmpty()) {
                player.getInventory().offhand.set(slot, stack);
                return true;
            }
        }

        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (slot == excludedHotbarSlot) {
                continue;
            }

            ItemStack candidate = player.getInventory().items.get(slot);
            if (canMerge(candidate, stack)) {
                mergeStacks(candidate, stack);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }

        for (int slot = 0; slot < player.getInventory().offhand.size(); slot++) {
            ItemStack candidate = player.getInventory().offhand.get(slot);
            if (canMerge(candidate, stack)) {
                mergeStacks(candidate, stack);
                if (stack.isEmpty()) {
                    return true;
                }
            }
        }

        return stack.isEmpty();
    }

    private static boolean canMerge(ItemStack target, ItemStack source) {
        return !target.isEmpty()
                && ItemStack.isSameItemSameComponents(target, source)
                && target.getCount() < target.getMaxStackSize();
    }

    private static void mergeStacks(ItemStack target, ItemStack source) {
        int moved = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
        if (moved <= 0) {
            return;
        }

        target.grow(moved);
        source.shrink(moved);
    }
}
