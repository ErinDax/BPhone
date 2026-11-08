package cn.erindax.bphone.phone;

import net.minecraft.world.entity.player.Player;

public interface PhoneCallPoseAccess {
    boolean beat$isPhoneCallPoseActive();

    void beat$setPhoneCallPoseActive(boolean active);

    boolean beat$isPhoneCameraPoseActive();

    void beat$setPhoneCameraPoseActive(boolean active);

    boolean beat$isPhoneCameraSelfieActive();

    void beat$setPhoneCameraSelfieActive(boolean active);

    boolean beat$isPhoneBankQrPoseActive();

    void beat$setPhoneBankQrPoseActive(boolean active);

    boolean beat$isPhoneChatQrPoseActive();

    void beat$setPhoneChatQrPoseActive(boolean active);

    boolean beat$isPhoneScreenOnActive();

    void beat$setPhoneScreenOnActive(boolean active);

    static boolean isPhoneCallPoseActive(Player player) {
        return player instanceof PhoneCallPoseAccess access && access.beat$isPhoneCallPoseActive();
    }

    static void setPhoneCallPoseActive(Player player, boolean active) {
        if (player instanceof PhoneCallPoseAccess access) {
            access.beat$setPhoneCallPoseActive(active);
        }
    }

    static boolean isPhoneCameraPoseActive(Player player) {
        return player instanceof PhoneCallPoseAccess access && access.beat$isPhoneCameraPoseActive();
    }

    static void setPhoneCameraPoseActive(Player player, boolean active) {
        if (player instanceof PhoneCallPoseAccess access) {
            access.beat$setPhoneCameraPoseActive(active);
            if (!active) {
                access.beat$setPhoneCameraSelfieActive(false);
            }
        }
    }

    static boolean isPhoneCameraSelfieActive(Player player) {
        return player instanceof PhoneCallPoseAccess access
                && access.beat$isPhoneCameraPoseActive()
                && access.beat$isPhoneCameraSelfieActive();
    }

    static void setPhoneCameraSelfieActive(Player player, boolean active) {
        if (player instanceof PhoneCallPoseAccess access) {
            access.beat$setPhoneCameraSelfieActive(active);
        }
    }

    static boolean isPhoneBankQrPoseActive(Player player) {
        return player instanceof PhoneCallPoseAccess access && access.beat$isPhoneBankQrPoseActive();
    }

    static void setPhoneBankQrPoseActive(Player player, boolean active) {
        if (player instanceof PhoneCallPoseAccess access) {
            access.beat$setPhoneBankQrPoseActive(active);
        }
    }

    static boolean isPhoneChatQrPoseActive(Player player) {
        return player instanceof PhoneCallPoseAccess access && access.beat$isPhoneChatQrPoseActive();
    }

    static void setPhoneChatQrPoseActive(Player player, boolean active) {
        if (player instanceof PhoneCallPoseAccess access) {
            access.beat$setPhoneChatQrPoseActive(active);
        }
    }

    static boolean isPhoneScreenOnActive(Player player) {
        return player instanceof PhoneCallPoseAccess access && access.beat$isPhoneScreenOnActive();
    }

    static void setPhoneScreenOnActive(Player player, boolean active) {
        if (player instanceof PhoneCallPoseAccess access) {
            access.beat$setPhoneScreenOnActive(active);
        }
    }
}
