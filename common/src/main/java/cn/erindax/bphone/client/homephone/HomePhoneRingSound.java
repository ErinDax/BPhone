package cn.erindax.bphone.client.homephone;

import cn.erindax.bphone.ModSounds;
import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.homephone.HomePhoneRingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class HomePhoneRingSound extends AbstractTickableSoundInstance {
    private final BlockPos homePhonePos;
    private boolean shouldStop = false;

    public HomePhoneRingSound(BlockPos pos) {
        super(ModSounds.HOME_PHONE_RING.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.homePhonePos = pos.immutable();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.38F;
        this.pitch = 1.0F;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean missingLevel = minecraft.level == null;
        boolean stoppedInState = missingLevel
                || !HomePhoneRingState.isRinging(minecraft.level.dimension(), homePhonePos)
                || !minecraft.level.hasChunkAt(homePhonePos)
                || !(minecraft.level.getBlockEntity(homePhonePos) instanceof HomePhoneBlockEntity homePhone)
                || !homePhone.isRinging()
                || !refreshOutputPosition(minecraft, homePhone);
        if (this.shouldStop || stoppedInState) {
            stopRinging();
            this.stop();
        }
    }

    public void stopRinging() {
        this.shouldStop = true;
    }

    private boolean refreshOutputPosition(Minecraft minecraft, HomePhoneBlockEntity homePhone) {
        UUID handsetHolderId = homePhone.getHandsetHolderId();
        if (handsetHolderId != null) {
            Player holder = minecraft.level.getPlayerByUUID(handsetHolderId);
            if (holder != null) {
                this.x = holder.getX();
                this.y = holder.getEyeY();
                this.z = holder.getZ();
                return true;
            }
        }

        this.x = homePhonePos.getX() + 0.5D;
        this.y = homePhonePos.getY() + 0.5D;
        this.z = homePhonePos.getZ() + 0.5D;
        return true;
    }
}
