package cn.erindax.bphone.shop;

import net.minecraft.world.item.ItemStack;

public record ShopEntry(String id, String name, ItemStack stack, long price, String description) {
}
