package cn.erindax.bphone.walkie;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.item.WalkieRadioItem;
import cn.erindax.bphone.voice.VoiceProvider;
import cn.erindax.bphone.voice.VoiceProviderDetector;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class WalkieNetworking {
    public static final ResourceLocation TUNE = id("walkie_tune");
    public static final ResourceLocation TRANSMIT = id("walkie_transmit");
    public static final ResourceLocation TALK_STATE = id("walkie_talk_state");
    private static final double TRANSMIT_RANGE = Double.MAX_VALUE;
    private static boolean initialized;

    private WalkieNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        NetworkManager.registerReceiver(NetworkManager.c2s(), TUNE, (buf, context) -> {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            WalkieBand band = buf.readEnum(WalkieBand.class);
            int frequency = buf.readVarInt();
            context.queue(() -> tuneHeldWalkie((ServerPlayer) context.getPlayer(), hand, band, frequency));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), TRANSMIT, (buf, context) -> {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            context.queue(() -> transmit((ServerPlayer) context.getPlayer(), hand));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), TALK_STATE, (buf, context) -> {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            boolean talking = buf.readBoolean();
            context.queue(() -> setTalkState((ServerPlayer) context.getPlayer(), hand, talking));
        });
    }

    private static void tuneHeldWalkie(ServerPlayer player, InteractionHand hand, WalkieBand band, int frequency) {
        if (player == null || band == null) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof WalkieRadioItem)) {
            return;
        }

        WalkieRadioItem.setTuning(stack, band, frequency);
        player.displayClientMessage(Component.translatable("message.bphone.walkie.tuned",
                band.display(WalkieRadioItem.getFrequency(stack))), true);
    }

    private static void transmit(ServerPlayer player, InteractionHand hand) {
        if (player == null) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof WalkieRadioItem)) {
            return;
        }

        Component frequency = WalkieRadioItem.getBand(stack).display(WalkieRadioItem.getFrequency(stack));
        int receivers = 0;
        for (ServerPlayer target : player.server.getPlayerList().getPlayers()) {
            if (target == player || target.level() != player.level() || !target.position().closerThan(player.position(), TRANSMIT_RANGE)) {
                continue;
            }

            if (!hasMatchingWalkie(target, stack)) {
                continue;
            }

            receivers++;
            target.displayClientMessage(Component.translatable("message.bphone.walkie.received",
                    player.getDisplayName(), frequency), false);
        }

        player.displayClientMessage(Component.translatable("message.bphone.walkie.transmitted", frequency, receivers), true);
    }

    private static void setTalkState(ServerPlayer player, InteractionHand hand, boolean talking) {
        if (player == null || hand == null) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (talking && !(stack.getItem() instanceof WalkieRadioItem)) {
            return;
        }

        VoiceProvider provider = VoiceProviderDetector.detect();
        String hookClassName = switch (provider) {
            case PLASMO_VOICE -> "cn.erindax.bphone.walkie.voice.WalkieVoiceHook";
            case SIMPLE_VOICE_CHAT -> "cn.erindax.bphone.voice.svc.SvcWalkieVoiceHook";
            case NONE -> null;
        };

        if (hookClassName == null) {
            return;
        }

        try {
            Class<?> hookClass = Class.forName(hookClassName);
            hookClass.getMethod("setTalking", ServerPlayer.class, InteractionHand.class, boolean.class)
                    .invoke(null, player, hand, talking);
        } catch (ReflectiveOperationException exception) {
            BPhone.LOGGER.debug("Failed to update walkie voice talk state", exception);
        }
    }

    private static boolean hasMatchingWalkie(ServerPlayer player, ItemStack sourceStack) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof WalkieRadioItem && WalkieRadioItem.hasSameTuning(sourceStack, stack)) {
                return true;
            }
        }

        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof WalkieRadioItem && WalkieRadioItem.hasSameTuning(sourceStack, stack)) {
                return true;
            }
        }

        return false;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, path);
    }
}
