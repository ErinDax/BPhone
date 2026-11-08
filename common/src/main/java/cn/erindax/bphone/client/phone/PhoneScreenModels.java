package cn.erindax.bphone.client.phone;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class PhotoEntry {
    final String fileName;
    final ItemStack itemStack;

    PhotoEntry(String fileName, ItemStack itemStack) {
        this.fileName = fileName == null ? "" : fileName;
        this.itemStack = itemStack;
    }

    boolean hasFile() {
        return !fileName.isEmpty();
    }
}

final class PhotoTexture {
    final ResourceLocation textureId;
    final int width;
    final int height;

    PhotoTexture(ResourceLocation textureId, int width, int height) {
        this.textureId = textureId;
        this.width = width;
        this.height = height;
    }
}

final class GalleryLayout {
    final int startX;
    final int startY;
    final int slotSize;
    final int columnGap;
    final int rowGap;

    GalleryLayout(int startX, int startY, int slotSize, int columnGap, int rowGap) {
        this.startX = startX;
        this.startY = startY;
        this.slotSize = slotSize;
        this.columnGap = columnGap;
        this.rowGap = rowGap;
    }
}

final class ViewerLayout {
    final int areaX;
    final int areaY;
    final int areaWidth;
    final int areaHeight;

    ViewerLayout(int areaX, int areaY, int areaWidth, int areaHeight) {
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;
    }
}

final class UiRect {
    final int left;
    final int top;
    final int width;
    final int height;

    UiRect(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    int right() {
        return left + width;
    }

    int bottom() {
        return top + height;
    }

    boolean contains(double x, double y) {
        return x >= left && x <= right() && y >= top && y <= bottom();
    }
}
