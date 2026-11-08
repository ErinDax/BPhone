package cn.erindax.bphone.shop;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ShopCategory(String id, String name, ItemStack icon, List<ShopEntry> entries) {
}
