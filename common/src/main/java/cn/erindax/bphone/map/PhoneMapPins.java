package cn.erindax.bphone.map;

import cn.erindax.bphone.item.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class PhoneMapPins {
    public static final String TAG = "map_pins";
    public static final int MAX_PINS = 24;

    public record Pin(String id, String name, String dimension, int x, int y, int z) {
    }

    private PhoneMapPins() {
    }

    public static List<Pin> get(ItemStack phone) {
        CompoundTag tag = phone == null || phone.isEmpty() ? null : ItemNbt.getTag(phone);
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(TAG, Tag.TAG_COMPOUND);
        List<Pin> pins = new ArrayList<>(list.size());
        for (int index = 0; index < list.size() && pins.size() < MAX_PINS; index++) {
            CompoundTag entry = list.getCompound(index);
            String id = entry.getString("id");
            if (!id.isEmpty()) {
                pins.add(new Pin(id, entry.getString("name"), entry.getString("dim"),
                        entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
            }
        }
        return pins;
    }

    public static boolean add(ItemStack phone, Pin pin) {
        if (phone == null || phone.isEmpty() || pin == null || pin.id().isEmpty()) {
            return false;
        }
        List<Pin> pins = new ArrayList<>(get(phone));
        pins.removeIf(existing -> existing.id().equals(pin.id()));
        if (pins.size() >= MAX_PINS) {
            return false;
        }
        pins.add(0, pin);
        write(phone, pins);
        return true;
    }

    public static boolean remove(ItemStack phone, String id) {
        List<Pin> pins = new ArrayList<>(get(phone));
        if (!pins.removeIf(existing -> existing.id().equals(id))) {
            return false;
        }
        write(phone, pins);
        return true;
    }

    private static void write(ItemStack phone, List<Pin> pins) {
        CompoundTag tag = ItemNbt.copyTag(phone);
        if (pins.isEmpty()) {
            tag.remove(TAG);
        } else {
            ListTag list = new ListTag();
            for (Pin pin : pins) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", pin.id());
                entry.putString("name", MapData.clip(pin.name(), MapData.MAX_NAME_LENGTH));
                entry.putString("dim", MapData.clip(pin.dimension(), MapData.MAX_DIMENSION_LENGTH));
                entry.putInt("x", pin.x());
                entry.putInt("y", pin.y());
                entry.putInt("z", pin.z());
                list.add(entry);
            }
            tag.put(TAG, list);
        }
        ItemNbt.setTag(phone, tag);
    }
}
