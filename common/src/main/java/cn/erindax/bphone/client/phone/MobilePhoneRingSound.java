package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.ModItems;
import cn.erindax.bphone.phone.PhoneCallState;
import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.UUID;

public final class MobilePhoneRingSound extends AbstractTickableSoundInstance {
    private static final double SEARCH_RADIUS = 32.0D;
    private boolean shouldStop;

    public MobilePhoneRingSound(String ringtone) {
        super(PhoneRingtones.sound(ringtone), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = PhoneRingtones.loopDelay(ringtone);
        this.volume = 0.55F;
        this.pitch = 1.0F;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean shouldStopInState = shouldStop
                || minecraft.level == null
                || minecraft.player == null
                || PhoneClientCallState.getState() != PhoneCallState.INCOMING_RINGING
                || !refreshOutputPosition(minecraft);
        if (shouldStopInState) {
            stopRinging();
            this.stop();
        }
    }

    public void stopRinging() {
        this.shouldStop = true;
    }

    private boolean refreshOutputPosition(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }

        ItemEntity droppedPhone = findBestDroppedPhone(minecraft, player);
        if (droppedPhone != null) {
            this.x = droppedPhone.getX();
            this.y = droppedPhone.getY() + 0.1D;
            this.z = droppedPhone.getZ();
            return true;
        }

        if (PhoneData.hasPhone(player)) {
            this.x = player.getX();
            this.y = player.getEyeY() - 0.2D;
            this.z = player.getZ();
            return true;
        }

        return false;
    }

    private ItemEntity findBestDroppedPhone(Minecraft minecraft, LocalPlayer player) {
        UUID playerId = player.getUUID();
        ItemEntity bestOwned = null;
        ItemEntity bestAny = null;
        double bestOwnedDistance = Double.MAX_VALUE;
        double bestAnyDistance = Double.MAX_VALUE;

        for (ItemEntity itemEntity : minecraft.level.getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(SEARCH_RADIUS),
                entity -> !entity.isRemoved() && ModItems.isPhone(entity.getItem()))) {
            double distance = player.distanceToSqr(itemEntity);
            if (distance < bestAnyDistance) {
                bestAnyDistance = distance;
                bestAny = itemEntity;
            }

            if (playerId.equals(itemEntity.getOwner()) && distance < bestOwnedDistance) {
                bestOwnedDistance = distance;
                bestOwned = itemEntity;
            }
        }

        return bestOwned != null ? bestOwned : bestAny;
    }
}
