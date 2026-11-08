package cn.erindax.bphone.item;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.ModParticles;
import cn.erindax.bphone.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MegaphoneItem extends Item {
    public static final int DEFAULT_COLOR = 0xFF8A0000;
    private static final int USE_DURATION = 72000;
    private static final int SHRIEK_PARTICLE_INTERVAL = 2;
    private static final double SHRIEK_FORWARD_OFFSET = 1.00D;
    private static final double SHRIEK_SIDE_OFFSET = 0.12D;
    private static final double SHRIEK_VERTICAL_OFFSET = -0.18D;
    private static final double SHRIEK_STEP_DISTANCE = 0.12D;
    private static final int SHRIEK_PARTICLE_COUNT = 2;
    private static final double SHRIEK_PARTICLE_SPEED = 0.12D;

    public MegaphoneItem(Properties properties) {
        super(properties);
    }

    public int getColor(ItemStack stack) {
        return stack == null ? DEFAULT_COLOR : DyedItemColor.getOrDefault(stack, DEFAULT_COLOR);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(livingEntity instanceof ServerPlayer serverPlayer)
                || !BPhone.isMegaphoneSpeaking(serverPlayer)
                || remainingUseDuration % SHRIEK_PARTICLE_INTERVAL != 0) {
            return;
        }

        Vec3 forward = livingEntity.getViewVector(1.0F).normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        HumanoidArm arm = resolveUsedArm(livingEntity);
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        Vec3 muzzle = livingEntity.getEyePosition()
                .add(up.scale(SHRIEK_VERTICAL_OFFSET))
                .add(right.scale(SHRIEK_SIDE_OFFSET * side))
                .add(forward.scale(SHRIEK_FORWARD_OFFSET));

        for (int index = 0; index < SHRIEK_PARTICLE_COUNT; index++) {
            Vec3 particlePos = muzzle.add(forward.scale(index * SHRIEK_STEP_DISTANCE));
            serverLevel.sendParticles(
                    ModParticles.MEGAPHONE_WAVE.get(),
                    particlePos.x,
                    particlePos.y,
                    particlePos.z,
                    0,
                    forward.x * SHRIEK_PARTICLE_SPEED,
                    forward.y * SHRIEK_PARTICLE_SPEED,
                    forward.z * SHRIEK_PARTICLE_SPEED,
                    1.0D);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public static boolean isUsingMegaphone(LivingEntity livingEntity) {
        return livingEntity != null
                && livingEntity.isUsingItem()
                && livingEntity.getUseItem().is(ModItems.MEGAPHONE.get());
    }

    private static HumanoidArm resolveUsedArm(LivingEntity livingEntity) {
        return livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? livingEntity.getMainArm()
                : livingEntity.getMainArm().getOpposite();
    }
}
