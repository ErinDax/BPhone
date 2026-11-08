package cn.erindax.bphone.phone;

import cn.erindax.bphone.item.ItemNbt;

import cn.erindax.bphone.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PhoneData {
    public static final String CONTACTS_TAG = "contacts";
    public static final String CONTACT_NAME_TAG = "name";
    public static final String CONTACT_NUMBER_TAG = "number";
    public static final int PHONE_NUMBER_LENGTH = 5;
    public static final int HOME_PHONE_MAX_NUMBER = 9999;
    public static final int MOBILE_PHONE_MIN_NUMBER = 10000;
    private static final int HOME_PHONE_NUMBER_SPACE = HOME_PHONE_MAX_NUMBER + 1;
    private static final int MOBILE_PHONE_NUMBER_SPACE = 100000 - MOBILE_PHONE_MIN_NUMBER;
    public static final int MAX_CONTACT_NAME_LENGTH = 24;

    private PhoneData() {
    }

    public static String getPhoneNumber(Player player) {
        if (player == null) {
            return "00000";
        }

        int hash = player.getUUID().hashCode();
        int number = MOBILE_PHONE_MIN_NUMBER + Math.floorMod(hash, MOBILE_PHONE_NUMBER_SPACE);
        return String.format(Locale.ROOT, "%05d", number);
    }

    public static String getHomePhoneNumber(ResourceKey<Level> dimension, BlockPos blockPos) {
        if (dimension == null || blockPos == null) {
            return "00000";
        }

        int hash = Objects.hash(
                dimension.location().toString(),
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ());
        int number = Math.floorMod(hash, HOME_PHONE_NUMBER_SPACE);
        return String.format(Locale.ROOT, "%05d", number);
    }

    public static String normalizePhoneNumber(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        StringBuilder digits = new StringBuilder(PHONE_NUMBER_LENGTH);
        for (int i = 0; i < raw.length() && digits.length() < PHONE_NUMBER_LENGTH; i++) {
            char ch = raw.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            }
        }
        return digits.toString();
    }

    public static boolean isValidPhoneNumber(String raw) {
        return normalizePhoneNumber(raw).length() == PHONE_NUMBER_LENGTH;
    }

    public static boolean isMobilePhoneNumber(String raw) {
        String normalized = normalizePhoneNumber(raw);
        if (!isValidPhoneNumber(normalized)) {
            return false;
        }

        return Integer.parseInt(normalized) >= MOBILE_PHONE_MIN_NUMBER;
    }

    public static boolean isHomePhoneNumber(String raw) {
        String normalized = normalizePhoneNumber(raw);
        if (!isValidPhoneNumber(normalized)) {
            return false;
        }

        return Integer.parseInt(normalized) <= HOME_PHONE_MAX_NUMBER;
    }

    public static List<PhoneContact> getContacts(ItemStack phoneStack) {
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack)) {
            return List.of();
        }

        CompoundTag tag = ItemNbt.getTag(phoneStack);
        return tag == null ? List.of() : getContacts(tag);
    }

    public static List<PhoneContact> getContacts(CompoundTag tag) {
        if (tag == null || !tag.contains(CONTACTS_TAG, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag contactsTag = tag.getList(CONTACTS_TAG, Tag.TAG_COMPOUND);
        if (contactsTag.isEmpty()) {
            return List.of();
        }

        List<PhoneContact> contacts = new ArrayList<>(contactsTag.size());
        for (int i = 0; i < contactsTag.size(); i++) {
            CompoundTag entry = contactsTag.getCompound(i);
            String number = normalizePhoneNumber(entry.getString(CONTACT_NUMBER_TAG));
            if (!isValidPhoneNumber(number)) {
                continue;
            }

            String name = sanitizeContactName(entry.getString(CONTACT_NAME_TAG), number);
            contacts.add(new PhoneContact(name, number));
        }
        return contacts;
    }

    public static boolean hasContact(ItemStack phoneStack, String number) {
        String normalized = normalizePhoneNumber(number);
        if (!isValidPhoneNumber(normalized)) {
            return false;
        }

        for (PhoneContact contact : getContacts(phoneStack)) {
            if (contact.number().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasContact(CompoundTag tag, String number) {
        String normalized = normalizePhoneNumber(number);
        if (!isValidPhoneNumber(normalized)) {
            return false;
        }

        for (PhoneContact contact : getContacts(tag)) {
            if (contact.number().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean saveContact(ItemStack phoneStack, String desiredName, String number) {
        String normalized = normalizePhoneNumber(number);
        if (phoneStack.isEmpty() || !isValidPhoneNumber(normalized)) {
            return false;
        }

        CompoundTag tag = ItemNbt.copyTag(phoneStack);
        if (!saveContact(tag, desiredName, normalized)) {
            return false;
        }
        ItemNbt.setTag(phoneStack, tag);
        return true;
    }

    public static boolean saveContact(CompoundTag tag, String desiredName, String number) {
        String normalized = normalizePhoneNumber(number);
        if (tag == null || !isValidPhoneNumber(normalized)) {
            return false;
        }

        ListTag contactsTag = tag.contains(CONTACTS_TAG, Tag.TAG_LIST)
                ? tag.getList(CONTACTS_TAG, Tag.TAG_COMPOUND)
                : new ListTag();

        for (int i = contactsTag.size() - 1; i >= 0; i--) {
            CompoundTag existing = contactsTag.getCompound(i);
            if (normalized.equals(normalizePhoneNumber(existing.getString(CONTACT_NUMBER_TAG)))) {
                contactsTag.remove(i);
            }
        }

        CompoundTag contactTag = new CompoundTag();
        contactTag.putString(CONTACT_NAME_TAG, sanitizeContactName(desiredName, normalized));
        contactTag.putString(CONTACT_NUMBER_TAG, normalized);
        contactsTag.add(contactTag);

        tag.put(CONTACTS_TAG, contactsTag);
        return true;
    }

    public static boolean removeContact(ItemStack phoneStack, String number) {
        String normalized = normalizePhoneNumber(number);
        if (phoneStack.isEmpty() || !ItemNbt.hasTag(phoneStack) || !isValidPhoneNumber(normalized)) {
            return false;
        }

        CompoundTag tag = ItemNbt.copyTag(phoneStack);
        if (!removeContact(tag, normalized)) {
            return false;
        }
        ItemNbt.setTag(phoneStack, tag);
        return true;
    }

    public static boolean removeContact(CompoundTag tag, String number) {
        String normalized = normalizePhoneNumber(number);
        if (tag == null || !isValidPhoneNumber(normalized)) {
            return false;
        }

        if (tag == null || !tag.contains(CONTACTS_TAG, Tag.TAG_LIST)) {
            return false;
        }

        ListTag contactsTag = tag.getList(CONTACTS_TAG, Tag.TAG_COMPOUND);
        for (int i = contactsTag.size() - 1; i >= 0; i--) {
            CompoundTag existing = contactsTag.getCompound(i);
            if (normalized.equals(normalizePhoneNumber(existing.getString(CONTACT_NUMBER_TAG)))) {
                contactsTag.remove(i);
                if (contactsTag.isEmpty()) {
                    tag.remove(CONTACTS_TAG);
                } else {
                    tag.put(CONTACTS_TAG, contactsTag);
                }
                return true;
            }
        }

        return false;
    }

    public static ItemStack findPhoneStack(Player player) {
        return findPhoneStack(player, null);
    }

    public static ItemStack findPhoneStack(Player player, InteractionHand preferredHand) {
        if (player == null) {
            return ItemStack.EMPTY;
        }

        if (preferredHand != null) {
            ItemStack preferred = player.getItemInHand(preferredHand);
            if (isPhone(preferred)) {
                return preferred;
            }
        }

        ItemStack mainHand = player.getMainHandItem();
        if (isPhone(mainHand)) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        if (isPhone(offHand)) {
            return offHand;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isPhone(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    public static boolean hasPhone(Player player) {
        return !findPhoneStack(player).isEmpty();
    }

    public static void markDirty(Player player) {
        player.getInventory().setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    private static boolean isPhone(ItemStack stack) {
        return ModItems.isPhone(stack);
    }

    private static String sanitizeContactName(String desiredName, String fallbackNumber) {
        String base = desiredName == null ? "" : desiredName.trim();
        if (base.isEmpty()) {
            base = fallbackNumber;
        }

        int end = Math.min(base.length(), MAX_CONTACT_NAME_LENGTH);
        return base.substring(0, end);
    }
}
