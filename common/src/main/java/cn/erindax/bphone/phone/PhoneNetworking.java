package cn.erindax.bphone.phone;

import cn.erindax.bphone.network.NetworkBufferUtils;

import cn.erindax.bphone.BPhone;
import cn.erindax.bphone.EconomyBridge;
import cn.erindax.bphone.api.economy.EconomyResult;
import cn.erindax.bphone.block.entity.HomePhoneBlockEntity;
import cn.erindax.bphone.block.entity.HomePhoneRegistry;
import cn.erindax.bphone.phone.chat.ChatNetworking;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhoneNetworking {
    public static final ResourceLocation CALL_STATE_SYNC = id("phone_call_state_sync");
    public static final ResourceLocation PHONE_TOAST = id("phone_toast");
    public static final ResourceLocation HOME_PHONE_SCREEN_OPEN = id("home_phone_screen_open");
    public static final ResourceLocation CALL_REQUEST = id("phone_call_request");
    public static final ResourceLocation CALL_ANSWER = id("phone_call_answer");
    public static final ResourceLocation CALL_END = id("phone_call_end");
    public static final ResourceLocation CALL_SPEAKER_TOGGLE = id("phone_call_speaker_toggle");
    public static final ResourceLocation CALL_MUTE_TOGGLE = id("phone_call_mute_toggle");
    public static final ResourceLocation CALL_MUTE_SPEAKER_SYNC = id("phone_call_mute_speaker_sync");
    public static final ResourceLocation CALL_SYNC_REQUEST = id("phone_call_sync_request");
    public static final ResourceLocation HOME_PHONE_HANDSET_PUT_DOWN = id("home_phone_handset_put_down");

    public static final ResourceLocation CONTACT_SAVE = id("phone_contact_save");
    public static final ResourceLocation CONTACT_DELETE = id("phone_contact_delete");
    public static final ResourceLocation PHOTO_APPEND = id("phone_photo_append");
    public static final ResourceLocation PHOTO_DELETE = id("phone_photo_delete");
    public static final ResourceLocation CAMERA_POSE_UPDATE = id("phone_camera_pose_update");
    public static final ResourceLocation SCREEN_ON_UPDATE = id("phone_screen_on_update");
    public static final ResourceLocation BANK_SYNC_REQUEST = id("phone_bank_sync_request");
    public static final ResourceLocation BANK_STATE_SYNC = id("phone_bank_state_sync");
    public static final ResourceLocation BANK_TRANSFER = id("phone_bank_transfer");
    public static final ResourceLocation BANK_RECEIVE_STATE = id("phone_bank_receive_state");
    public static final ResourceLocation BANK_SCAN_TARGET = id("phone_bank_scan_target");
    public static final ResourceLocation BANK_SCAN_RESULT = id("phone_bank_scan_result");
    public static final ResourceLocation BANK_TRANSFER_RECEIPT = id("phone_bank_transfer_receipt");
    public static final ResourceLocation CALL_LOG_SYNC_REQUEST = id("phone_call_log_sync_request");
    public static final ResourceLocation CALL_LOG_SYNC = id("phone_call_log_sync");
    public static final ResourceLocation SETTINGS_UPDATE = id("phone_settings_update");
    private static final double BANK_SCAN_MAX_DISTANCE_SQR = 12.0D * 12.0D;
    private static final Set<UUID> ACTIVE_BANK_RECEIVERS = ConcurrentHashMap.newKeySet();
    private static boolean initialized;

    private PhoneNetworking() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        NetworkBufferUtils.registerClientboundTypes(CALL_STATE_SYNC, PHONE_TOAST, HOME_PHONE_SCREEN_OPEN,
                CALL_MUTE_SPEAKER_SYNC, BANK_STATE_SYNC, BANK_SCAN_RESULT, BANK_TRANSFER_RECEIPT, CALL_LOG_SYNC);
        PhoneCallManager.init();
        ChatNetworking.init();

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_REQUEST, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.startHomePhoneCall(player, homePhonePos, number);
                } else {
                    PhoneCallManager.startCall(player, number);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_ANSWER, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.answerHomePhoneCall(player, homePhonePos);
                } else {
                    PhoneCallManager.answerCall(player);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_END, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.endHomePhoneCall(player, homePhonePos);
                } else {
                    PhoneCallManager.endCall(player);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_SPEAKER_TOGGLE, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.toggleHomePhoneSpeaker(player, homePhonePos);
                } else {
                    PhoneCallManager.toggleSpeaker(player);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_MUTE_TOGGLE, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.toggleHomePhoneMute(player, homePhonePos);
                } else {
                    PhoneCallManager.toggleMute(player);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), HOME_PHONE_HANDSET_PUT_DOWN, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.putDownHomePhoneHandset(player, player.serverLevel().dimension(), homePhonePos);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_SYNC_REQUEST, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                if (homePhonePos != null) {
                    PhoneCallManager.syncHomePhone(player, homePhonePos);
                } else {
                    PhoneCallManager.syncPlayer(player);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CONTACT_SAVE, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            String requestedName = buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH);
            context.queue(() -> saveContact((ServerPlayer) context.getPlayer(), homePhonePos, number, requestedName));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CONTACT_DELETE, (buf, context) -> {
            BlockPos homePhonePos = readHomePhoneContext(buf);
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            String unusedSuggestedName = buf.readUtf(PhoneData.MAX_CONTACT_NAME_LENGTH);
            context.queue(() -> deleteContact((ServerPlayer) context.getPlayer(), homePhonePos, number));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), PHOTO_APPEND, (buf, context) -> {
            InteractionHand preferredHand = buf.readEnum(InteractionHand.class);
            String fileName = buf.readUtf(PhonePhotoData.MAX_PHOTO_FILE_NAME_LENGTH);
            ItemStack captureStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            context.queue(() -> appendPhonePhoto((ServerPlayer) context.getPlayer(), preferredHand, fileName, captureStack));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), PHOTO_DELETE, (buf, context) -> {
            InteractionHand preferredHand = buf.readEnum(InteractionHand.class);
            String fileName = buf.readUtf(PhonePhotoData.MAX_PHOTO_FILE_NAME_LENGTH);
            context.queue(() -> deletePhonePhoto((ServerPlayer) context.getPlayer(), preferredHand, fileName));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CAMERA_POSE_UPDATE, (buf, context) -> {
            boolean active = buf.readBoolean();
            boolean selfie = buf.readBoolean();
            context.queue(() -> updateCameraPose((ServerPlayer) context.getPlayer(), active, selfie));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), SCREEN_ON_UPDATE, (buf, context) -> {
            boolean active = buf.readBoolean();
            context.queue(() -> updateScreenOnState((ServerPlayer) context.getPlayer(), active));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), BANK_SYNC_REQUEST, (buf, context) ->
                context.queue(() -> sendBankState((ServerPlayer) context.getPlayer())));

        NetworkManager.registerReceiver(NetworkManager.c2s(), BANK_TRANSFER, (buf, context) -> {
            String number = buf.readUtf(PhoneData.PHONE_NUMBER_LENGTH);
            long amount = buf.readVarLong();
            context.queue(() -> transferBankMoney((ServerPlayer) context.getPlayer(), number, amount));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), BANK_RECEIVE_STATE, (buf, context) -> {
            boolean active = buf.readBoolean();
            boolean chat = buf.readableBytes() > 0 && buf.readBoolean();
            context.queue(() -> setBankReceiveState((ServerPlayer) context.getPlayer(), active, chat));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), BANK_SCAN_TARGET, (buf, context) -> {
            UUID targetId = buf.readUUID();
            context.queue(() -> scanBankPaymentTarget((ServerPlayer) context.getPlayer(), targetId));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), SETTINGS_UPDATE, (buf, context) -> {
            InteractionHand preferredHand = buf.readEnum(InteractionHand.class);
            String key = buf.readUtf(PhoneSettingsData.MAX_KEY_LENGTH);
            String value = buf.readUtf(PhoneSettingsData.MAX_VALUE_LENGTH);
            context.queue(() -> updatePhoneSetting((ServerPlayer) context.getPlayer(), preferredHand, key, value));
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), CALL_LOG_SYNC_REQUEST, (buf, context) -> {
            context.queue(() -> {
                ServerPlayer player = (ServerPlayer) context.getPlayer();
                List<CallLogEntry> entries = CallLogStorageManager.getEntries(player.getUUID(), 50);
                RegistryFriendlyByteBuf responseBuf = NetworkBufferUtils.create();
                writeHomePhoneContext(responseBuf, null);
                responseBuf.writeInt(entries.size());
                for (CallLogEntry entry : entries) {
                    responseBuf.writeUtf(entry.otherNumber(), PhoneData.PHONE_NUMBER_LENGTH);
                    responseBuf.writeUtf(entry.otherName() == null ? "" : entry.otherName(), PhoneData.MAX_CONTACT_NAME_LENGTH);
                    responseBuf.writeUtf(entry.callType(), 16);
                    responseBuf.writeLong(entry.timestamp());
                    responseBuf.writeInt(entry.durationTicks());
                }
                NetworkManager.sendToPlayer(player, CALL_LOG_SYNC, responseBuf);
            });
        });

        PlayerEvent.PLAYER_JOIN.register(player -> {
            try {
                PhoneCallManager.syncPlayer(player);
            } catch (Throwable throwable) {
                BPhone.LOGGER.error("Failed to sync phone call state for {}", player.getGameProfile().getName(), throwable);
            }
        });
        TickEvent.SERVER_PRE.register(server -> {
            pruneBankReceivers(server);
        });
    }

    public static void sendCallState(ServerPlayer player, PhoneCallState state, String otherNumber, String otherName) {
        sendCallState(player, state, otherNumber, otherName, null);
    }

    public static void sendCallState(ServerPlayer player, PhoneCallState state, String otherNumber, String otherName,
                                     UUID otherProfileId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        writeHomePhoneContext(buf, null);
        buf.writeEnum(state);
        buf.writeUtf(PhoneData.normalizePhoneNumber(otherNumber), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeUtf(otherName == null ? "" : otherName, PhoneData.MAX_CONTACT_NAME_LENGTH);
        writeOptionalUuid(buf, otherProfileId);
        NetworkManager.sendToPlayer(player, CALL_STATE_SYNC, buf);
    }

    public static void sendHomePhoneCallState(ServerPlayer player, BlockPos homePhonePos,
                                              PhoneCallState state, String otherNumber, String otherName) {
        sendHomePhoneCallState(player, homePhonePos, state, otherNumber, otherName, null);
    }

    public static void sendHomePhoneCallState(ServerPlayer player, BlockPos homePhonePos,
                                              PhoneCallState state, String otherNumber, String otherName,
                                              UUID otherProfileId) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        writeHomePhoneContext(buf, homePhonePos);
        buf.writeEnum(state);
        buf.writeUtf(PhoneData.normalizePhoneNumber(otherNumber), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeUtf(otherName == null ? "" : otherName, PhoneData.MAX_CONTACT_NAME_LENGTH);
        writeOptionalUuid(buf, otherProfileId);
        NetworkManager.sendToPlayer(player, CALL_STATE_SYNC, buf);
    }

    public static void sendMuteSpeakerSync(ServerPlayer player, BlockPos homePhonePos, boolean muted, boolean speakerEnabled) {
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        writeHomePhoneContext(buf, homePhonePos);
        buf.writeBoolean(muted);
        buf.writeBoolean(speakerEnabled);
        NetworkManager.sendToPlayer(player, CALL_MUTE_SPEAKER_SYNC, buf);
    }

    public static void openHomePhoneScreen(ServerPlayer player, BlockPos homePhonePos) {
        if (player == null || homePhonePos == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeBlockPos(homePhonePos);
        NetworkManager.sendToPlayer(player, HOME_PHONE_SCREEN_OPEN, buf);
    }

    public static void sendPhoneToast(ServerPlayer player, Component title, Component message) {
        if (player == null || title == null || message == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        ComponentSerialization.STREAM_CODEC.encode(buf, title);
        ComponentSerialization.STREAM_CODEC.encode(buf, message);
        NetworkManager.sendToPlayer(player, PHONE_TOAST, buf);
    }

    public static void sendBankState(ServerPlayer player) {
        sendBankState(player, null);
    }

    private static void sendBankState(ServerPlayer player, Component status) {
        if (player == null || player.getServer() == null) {
            return;
        }

        Component effectiveStatus = status == null && !EconomyBridge.available()
                ? Component.translatable("screen.bphone.phone.bank.status.no_economy")
                : status;
        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create(player);
        buf.writeLong(EconomyBridge.balanceOf(player));
        buf.writeBoolean(effectiveStatus != null);
        if (effectiveStatus != null) {
            ComponentSerialization.STREAM_CODEC.encode(buf, effectiveStatus);
        }
        NetworkManager.sendToPlayer(player, BANK_STATE_SYNC, buf);
    }

    private static void sendBankStatus(ServerPlayer player, Component status) {
        if (player == null || status == null) {
            return;
        }

        player.displayClientMessage(status, true);
        sendBankState(player, status);
    }

    private static void sendBankScanResult(ServerPlayer scanner, ServerPlayer target) {
        if (scanner == null || target == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(PhoneData.getPhoneNumber(target), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeUtf(target.getGameProfile().getName(), PhoneData.MAX_CONTACT_NAME_LENGTH);
        NetworkManager.sendToPlayer(scanner, BANK_SCAN_RESULT, buf);
    }

    private static void sendBankTransferReceipt(ServerPlayer sender, ServerPlayer recipient, long amount, long senderBalance) {
        if (sender == null || recipient == null) {
            return;
        }

        RegistryFriendlyByteBuf buf = NetworkBufferUtils.create();
        buf.writeUtf(PhoneData.getPhoneNumber(recipient), PhoneData.PHONE_NUMBER_LENGTH);
        buf.writeUtf(recipient.getGameProfile().getName(), PhoneData.MAX_CONTACT_NAME_LENGTH);
        buf.writeVarLong(Math.max(0L, amount));
        buf.writeVarLong(Math.max(0L, senderBalance));
        NetworkManager.sendToPlayer(sender, BANK_TRANSFER_RECEIPT, buf);
    }

    private static void setBankReceiveState(ServerPlayer player, boolean active, boolean chat) {
        if (player == null) {
            return;
        }

        if (active && canUseMobileBank(player)) {
            ACTIVE_BANK_RECEIVERS.add(player.getUUID());
            PhoneCallPoseAccess.setPhoneBankQrPoseActive(player, !chat);
            PhoneCallPoseAccess.setPhoneChatQrPoseActive(player, chat);
        } else {
            ACTIVE_BANK_RECEIVERS.remove(player.getUUID());
            PhoneCallPoseAccess.setPhoneBankQrPoseActive(player, false);
            PhoneCallPoseAccess.setPhoneChatQrPoseActive(player, false);
        }
    }

    private static void scanBankPaymentTarget(ServerPlayer scanner, UUID targetId) {
        if (!canUseMobileBank(scanner) || scanner.getServer() == null || targetId == null) {
            return;
        }

        if (scanner.getUUID().equals(targetId)) {
            sendBankStatus(scanner, Component.translatable("screen.bphone.phone.bank.status.self"));
            return;
        }

        ServerPlayer target = scanner.getServer().getPlayerList().getPlayer(targetId);
        if (target == null || !ACTIVE_BANK_RECEIVERS.contains(target.getUUID()) || !PhoneData.hasPhone(target)
                || !PhoneCallPoseAccess.isPhoneBankQrPoseActive(target)
                || scanner.distanceToSqr(target) > BANK_SCAN_MAX_DISTANCE_SQR) {
            sendBankStatus(scanner, Component.translatable("screen.bphone.phone.bank.status.scan_failed"));
            return;
        }

        sendBankScanResult(scanner, target);
        sendBankStatus(scanner, Component.translatable(
                "screen.bphone.phone.bank.status.scan_found",
                formatBankPlayerLabel(target, PhoneData.getPhoneNumber(target))));
    }

    private static void pruneBankReceivers(MinecraftServer server) {
        if (server == null || ACTIVE_BANK_RECEIVERS.isEmpty()) {
            return;
        }

        ACTIVE_BANK_RECEIVERS.removeIf(uuid -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return true;
            }

            if (!canUseMobileBank(player)) {
                PhoneCallPoseAccess.setPhoneBankQrPoseActive(player, false);
                PhoneCallPoseAccess.setPhoneChatQrPoseActive(player, false);
                return true;
            }

            return false;
        });
    }

    private static void transferBankMoney(ServerPlayer sender, String rawNumber, long amount) {
        if (!canUseMobileBank(sender)) {
            return;
        }

        if (!EconomyBridge.available()) {
            sendBankStatus(sender, Component.translatable("screen.bphone.phone.bank.status.no_economy"));
            return;
        }

        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (amount <= 0L) {
            sendBankStatus(sender, Component.translatable("screen.bphone.phone.bank.status.invalid_amount"));
            return;
        }

        if (!PhoneData.isValidPhoneNumber(number) || !PhoneData.isMobilePhoneNumber(number)) {
            sendBankStatus(sender, Component.translatable("screen.bphone.phone.bank.status.unavailable", number));
            return;
        }

        if (PhoneData.getPhoneNumber(sender).equals(number)) {
            sendBankStatus(sender, Component.translatable("screen.bphone.phone.bank.status.self"));
            return;
        }

        ServerPlayer recipient = PhoneCallManager.findOnlineByNumber(sender.getServer(), number);
        if (recipient == null || !PhoneData.hasPhone(recipient)) {
            sendBankStatus(sender, Component.translatable("screen.bphone.phone.bank.status.unavailable", number));
            return;
        }

        long balance = EconomyBridge.balanceOf(sender);
        if (balance < amount) {
            sendBankStatus(sender, Component.translatable("screen.bphone.phone.bank.status.insufficient"));
            return;
        }

        String recipientLabel = formatBankPlayerLabel(recipient, number);
        String senderLabel = formatBankPlayerLabel(sender, PhoneData.getPhoneNumber(sender));
        EconomyResult result = EconomyBridge.transfer(sender, recipient, amount,
                Component.translatableWithFallback("economy.bphone.reason.transfer", "Phone transfer %s -> %s",
                        senderLabel, recipientLabel));
        if (!result.isSuccess()) {
            sendBankStatus(sender, bankFailureStatus(result, number));
            return;
        }

        long recipientBalance = EconomyBridge.balanceOf(recipient);
        long senderBalance = EconomyBridge.balanceOf(sender);
        sendBankStatus(sender, Component.translatable(
                "screen.bphone.phone.bank.status.transfer_sent", amount, recipientLabel, senderBalance));
        sendBankTransferReceipt(sender, recipient, amount, senderBalance);
        sendPhoneToast(sender,
                Component.translatable("toast.bphone.phone.bank.sent.title"),
                Component.translatable("toast.bphone.phone.bank.sent.body", amount, recipientLabel));
        sendBankStatus(recipient, Component.translatable(
                "screen.bphone.phone.bank.status.transfer_received", amount, senderLabel, recipientBalance));
        sendPhoneToast(recipient,
                Component.translatable("toast.bphone.phone.bank.received.title"),
                Component.translatable("toast.bphone.phone.bank.received.body", amount, senderLabel));
    }

    private static Component bankFailureStatus(EconomyResult result, String number) {
        return switch (result) {
            case INSUFFICIENT_FUNDS -> Component.translatable("screen.bphone.phone.bank.status.insufficient");
            case INVALID_AMOUNT -> Component.translatable("screen.bphone.phone.bank.status.invalid_amount");
            case ACCOUNT_UNAVAILABLE -> Component.translatable("screen.bphone.phone.bank.status.unavailable", number);
            case UNAVAILABLE -> Component.translatable("screen.bphone.phone.bank.status.no_economy");
            default -> Component.translatable("screen.bphone.phone.bank.status.transfer_failed");
        };
    }

    private static boolean canUseMobileBank(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }

        if (PhoneData.hasPhone(player)) {
            return true;
        }

        sendBankStatus(player, Component.translatable("screen.bphone.phone.call.error.no_phone"));
        return false;
    }

    private static String formatBankPlayerLabel(ServerPlayer player, String number) {
        String name = player == null ? "" : player.getGameProfile().getName();
        if (name == null || name.isBlank()) {
            return number;
        }
        return name + " (" + number + ")";
    }

    private static void saveContact(ServerPlayer player, BlockPos homePhonePos, String rawNumber, String requestedName) {
        var server = player.getServer();
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number)) {
            player.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.invalid"));
            return;
        }

        String contactName;
        if (requestedName != null && !requestedName.isBlank() && !requestedName.equals(number)) {
            contactName = requestedName;
        } else {
            String resolvedName = PhoneCallManager.resolveContactName(server, number, requestedName);
            contactName = resolvedName == null || resolvedName.isBlank() ? number : resolvedName;
        }
        if (homePhonePos != null) {
            HomePhoneBlockEntity homePhone = getAccessibleHomePhone(player, homePhonePos);
            if (homePhone == null) {
                return;
            }

            if (homePhone.saveContact(contactName, number)) {
                player.sendSystemMessage(Component.translatable(
                        "screen.bphone.phone.call.contacts.saved", contactName));
            }
            return;
        }

        if (!PhoneData.hasPhone(player)) {
            player.sendSystemMessage(Component.translatable("screen.bphone.phone.call.error.no_phone"));
            return;
        }

        ItemStack phoneStack = PhoneData.findPhoneStack(player);
        if (PhoneData.saveContact(phoneStack, contactName, number)) {
            PhoneData.markDirty(player);
            player.sendSystemMessage(Component.translatable(
                    "screen.bphone.phone.call.contacts.saved", contactName));
        }
    }

    private static void deleteContact(ServerPlayer player, BlockPos homePhonePos, String rawNumber) {
        String number = PhoneData.normalizePhoneNumber(rawNumber);
        if (!PhoneData.isValidPhoneNumber(number)) {
            return;
        }

        if (homePhonePos != null) {
            HomePhoneBlockEntity homePhone = getAccessibleHomePhone(player, homePhonePos);
            if (homePhone != null && homePhone.removeContact(number)) {
                player.sendSystemMessage(Component.translatable(
                        "screen.bphone.phone.call.contacts.deleted", number));
            }
            return;
        }

        ItemStack phoneStack = PhoneData.findPhoneStack(player);
        if (phoneStack.isEmpty()) {
            return;
        }

        if (PhoneData.removeContact(phoneStack, number)) {
            PhoneData.markDirty(player);
            player.sendSystemMessage(Component.translatable(
                    "screen.bphone.phone.call.contacts.deleted", number));
        }
    }

    private static void appendPhonePhoto(ServerPlayer player, InteractionHand preferredHand,
                                         String photoFileName, ItemStack captureStack) {
        if (player == null || photoFileName == null || photoFileName.isBlank()) {
            return;
        }

        ItemStack phoneStack = PhoneData.findPhoneStack(player, preferredHand);
        if (phoneStack.isEmpty()) {
            return;
        }

        if (PhonePhotoData.appendPhoto(phoneStack, photoFileName, captureStack)) {
            PhoneData.markDirty(player);
        }
    }

    private static void deletePhonePhoto(ServerPlayer player, InteractionHand preferredHand, String photoFileName) {
        if (player == null || photoFileName == null || photoFileName.isBlank()) {
            return;
        }

        ItemStack phoneStack = PhoneData.findPhoneStack(player, preferredHand);
        if (phoneStack.isEmpty()) {
            return;
        }

        if (PhonePhotoData.removePhotoByFileName(phoneStack, photoFileName)) {
            PhoneData.markDirty(player);
        }
    }

    private static void updatePhoneSetting(ServerPlayer player, InteractionHand preferredHand, String key, String value) {
        if (player == null) {
            return;
        }

        ItemStack phoneStack = PhoneData.findPhoneStack(player, preferredHand);
        if (phoneStack.isEmpty()) {
            return;
        }

        if (PhoneSettingsData.apply(phoneStack, key, value)) {
            PhoneData.markDirty(player);
        }
    }

    private static void updateCameraPose(ServerPlayer player, boolean active, boolean selfie) {
        if (player == null) {
            return;
        }

        PhoneCallPoseAccess.setPhoneCameraPoseActive(player, active);
        PhoneCallPoseAccess.setPhoneCameraSelfieActive(player, active && selfie);
    }

    private static void updateScreenOnState(ServerPlayer player, boolean active) {
        if (player == null) {
            return;
        }

        PhoneCallPoseAccess.setPhoneScreenOnActive(player, active && PhoneData.hasPhone(player));
    }




    private static HomePhoneBlockEntity getAccessibleHomePhone(ServerPlayer player, BlockPos homePhonePos) {
        if (player == null || homePhonePos == null) {
            return null;
        }

        ServerLevel level = player.serverLevel();
        HomePhoneBlockEntity homePhone = HomePhoneRegistry.get(level, homePhonePos);
        if (homePhone == null) {
            return null;
        }

        return homePhone;
    }

    private static BlockPos readHomePhoneContext(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }

    private static void writeHomePhoneContext(RegistryFriendlyByteBuf buf, BlockPos homePhonePos) {
        boolean homePhone = homePhonePos != null;
        buf.writeBoolean(homePhone);
        if (homePhone) {
            buf.writeBlockPos(homePhonePos);
        }
    }

    private static void writeOptionalUuid(RegistryFriendlyByteBuf buf, UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUUID(uuid);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, path);
    }
}
