package cn.erindax.bphone.client.homephone;

import cn.erindax.bphone.ModSounds;
import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.client.phone.PhoneClientCallState;
import cn.erindax.bphone.phone.PhoneCallState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class HomePhoneDialingSound extends AbstractTickableSoundInstance {
    private final BlockPos homePhonePos;
    private boolean shouldStop;

    public HomePhoneDialingSound(BlockPos homePhonePos) {
        super(ModSounds.PHONE_DIALING.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.homePhonePos = homePhonePos.immutable();
        this.x = homePhonePos.getX() + 0.5D;
        this.y = homePhonePos.getY() + 0.5D;
        this.z = homePhonePos.getZ() + 0.5D;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.30F;
        this.pitch = 1.0F;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean shouldStopInState = minecraft.level == null
                || PhoneClientCallState.getState(homePhonePos) != PhoneCallState.OUTGOING_RINGING
                || !minecraft.level.hasChunkAt(homePhonePos)
                || !(minecraft.level.getBlockEntity(homePhonePos) instanceof HomePhoneBlockEntity homePhone)
                || !refreshOutputPosition(minecraft, homePhone);
        if (shouldStop || shouldStopInState) {
            stopDialing();
            this.stop();
        }
    }

    public void stopDialing() {
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

        if (!homePhone.isSpeakerEnabled()) {
            return false;
        }

        this.x = homePhonePos.getX() + 0.5D;
        this.y = homePhonePos.getY() + 0.5D;
        this.z = homePhonePos.getZ() + 0.5D;
        return true;
    }
}
