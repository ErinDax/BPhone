package cn.erindax.bphone.homephone;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public final class HomePhoneRingState {
    private static final Set<HomePhoneAddress> RINGING_PHONES = new HashSet<>();

    private HomePhoneRingState() {
    }

    public static void update(ResourceKey<Level> dimension, BlockPos blockPos, boolean ringing) {
        if (dimension == null || blockPos == null) {
            return;
        }

        HomePhoneAddress address = new HomePhoneAddress(dimension, blockPos.immutable());
        if (ringing) {
            RINGING_PHONES.add(address);
        } else {
            RINGING_PHONES.remove(address);
        }
    }

    public static boolean isRinging(ResourceKey<Level> dimension, BlockPos blockPos) {
        if (dimension == null || blockPos == null) {
            return false;
        }

        return RINGING_PHONES.contains(new HomePhoneAddress(dimension, blockPos));
    }

    public static Set<BlockPos> snapshot(ResourceKey<Level> dimension) {
        Set<BlockPos> positions = new HashSet<>();
        if (dimension == null) {
            return positions;
        }

        for (HomePhoneAddress address : RINGING_PHONES) {
            if (address.dimension.equals(dimension)) {
                positions.add(address.blockPos);
            }
        }
        return positions;
    }

    public static void clear() {
        RINGING_PHONES.clear();
    }

    private record HomePhoneAddress(ResourceKey<Level> dimension, BlockPos blockPos) {
    }
}
