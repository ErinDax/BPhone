package cn.erindax.bphone.block.entity;

import cn.erindax.bphone.ModBlockEntities;
import cn.erindax.bphone.block.HomePhoneBlock;
import cn.erindax.bphone.homephone.HomePhoneRingState;
import cn.erindax.bphone.phone.PhoneContact;
import cn.erindax.bphone.phone.PhoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public final class HomePhoneBlockEntity extends BlockEntity {
    private static final String PHONE_DATA_TAG = "PhoneData";
    private static final String RINGING_TAG = "Ringing";
    private static final String SPEAKER_ENABLED_TAG = "SpeakerEnabled";
    private static final String HANDSET_HOLDER_TAG = "HandsetHolder";
    private final CompoundTag phoneData = new CompoundTag();
    private boolean ringing;
    private boolean speakerEnabled;
    private UUID handsetHolderId;

    public HomePhoneBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.HOME_PHONE.get(), blockPos, blockState);
    }

    public String getPhoneNumber() {
        Level currentLevel = getLevel();
        if (currentLevel == null) {
            return "00000";
        }
        return PhoneData.getHomePhoneNumber(currentLevel.dimension(), getBlockPos());
    }

    public List<PhoneContact> getContacts() {
        return PhoneData.getContacts(phoneData);
    }

    public boolean hasContact(String number) {
        return PhoneData.hasContact(phoneData, number);
    }

    public boolean saveContact(String desiredName, String number) {
        boolean changed = PhoneData.saveContact(phoneData, desiredName, number);
        if (changed) {
            markUpdated();
        }
        return changed;
    }

    public boolean removeContact(String number) {
        boolean changed = PhoneData.removeContact(phoneData, number);
        if (changed) {
            markUpdated();
        }
        return changed;
    }

    public boolean isRinging() {
        return ringing;
    }

    public void setRinging(boolean ringing) {
        if (this.ringing == ringing) {
            return;
        }

        this.ringing = ringing;
        markUpdated();
        syncClientRingState();
    }

    public boolean isSpeakerEnabled() {
        return speakerEnabled;
    }

    public void setSpeakerEnabled(boolean speakerEnabled) {
        if (this.speakerEnabled == speakerEnabled) {
            return;
        }

        this.speakerEnabled = speakerEnabled;
        markUpdated();
    }

    public UUID getHandsetHolderId() {
        return handsetHolderId;
    }

    public void setHandsetHolderId(UUID handsetHolderId) {
        if (java.util.Objects.equals(this.handsetHolderId, handsetHolderId)) {
            return;
        }

        this.handsetHolderId = handsetHolderId;
        syncHandsetVisualState();
        markUpdated();
    }

    @Override
    public void setRemoved() {
        HomePhoneRegistry.unregister(this);
        clearClientRingState();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        HomePhoneRegistry.register(this);
        syncHandsetVisualState();
        syncClientRingState();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(RINGING_TAG, ringing);
        tag.putBoolean(SPEAKER_ENABLED_TAG, speakerEnabled);
        if (handsetHolderId != null) {
            tag.putUUID(HANDSET_HOLDER_TAG, handsetHolderId);
        }
        if (!phoneData.isEmpty()) {
            tag.put(PHONE_DATA_TAG, phoneData.copy());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ringing = tag.getBoolean(RINGING_TAG);
        speakerEnabled = tag.getBoolean(SPEAKER_ENABLED_TAG);
        handsetHolderId = tag.hasUUID(HANDSET_HOLDER_TAG) ? tag.getUUID(HANDSET_HOLDER_TAG) : null;
        phoneData.remove(PhoneData.CONTACTS_TAG);
        if (tag.contains(PHONE_DATA_TAG, Tag.TAG_COMPOUND)) {
            phoneData.merge(tag.getCompound(PHONE_DATA_TAG));
        }
        syncClientRingState();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean(RINGING_TAG, ringing);
        tag.putBoolean(SPEAKER_ENABLED_TAG, speakerEnabled);
        if (handsetHolderId != null) {
            tag.putUUID(HANDSET_HOLDER_TAG, handsetHolderId);
        }
        if (!phoneData.isEmpty()) {
            tag.put(PHONE_DATA_TAG, phoneData.copy());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    private void syncClientRingState() {
        if (level != null && level.isClientSide) {
            HomePhoneRingState.update(level.dimension(), worldPosition, ringing);
        }
    }

    private void clearClientRingState() {
        if (level != null && level.isClientSide) {
            HomePhoneRingState.update(level.dimension(), worldPosition, false);
        }
    }

    private void syncHandsetVisualState() {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        if (state.getBlock() instanceof HomePhoneBlock && state.hasProperty(HomePhoneBlock.HAS_HANDSET)) {
            boolean hasHandset = handsetHolderId == null;
            if (state.getValue(HomePhoneBlock.HAS_HANDSET) != hasHandset) {
                level.setBlock(worldPosition, state.setValue(HomePhoneBlock.HAS_HANDSET, hasHandset), 3);
            }
        }
    }
}
