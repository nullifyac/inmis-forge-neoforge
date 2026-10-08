/*
 * Backpacked-derived portions by MrCrayfish and contributors (GNU LGPL v2.1).
 * Source: https://github.com/MrCrayfish/Backpacked/blob/73db4d7cd729ebcd05b4cab8d7c0f7be9eeaa19a/common/src/main/java/com/mrcrayfish/backpacked/common/PlaceSoundControls.java
 * Adapted for Inmis packages and Minecraft/loader APIs.
 * Attribution notice added 2026-10-08; see THIRD-PARTY-NOTICES.txt and
 * LICENSES/Backpacked-LGPL-2.1.txt in the main resources.
 */
package draylar.inmis.augment;

import java.util.function.Supplier;

public final class PlaceSoundControls {
    private static final PlaceSoundControls INSTANCE = new PlaceSoundControls();

    private boolean enabled;
    private boolean aboutToPlay;
    private boolean preventNextPlay;
    private boolean sendToAllPlayers;

    private PlaceSoundControls() {
    }

    public static <T> T runWithOptions(boolean preventNextPlay, boolean sendToAllPlayers, Supplier<T> action) {
        try {
            INSTANCE.enabled = true;
            INSTANCE.aboutToPlay = false;
            INSTANCE.preventNextPlay = preventNextPlay;
            INSTANCE.sendToAllPlayers = sendToAllPlayers;
            return action.get();
        } finally {
            INSTANCE.enabled = false;
            INSTANCE.preventNextPlay = false;
            INSTANCE.sendToAllPlayers = false;
            INSTANCE.aboutToPlay = false;
        }
    }

    public static boolean isAboutToPlay() {
        return INSTANCE.enabled && INSTANCE.aboutToPlay;
    }

    public static boolean shouldPreventNextPlay() {
        return INSTANCE.enabled && INSTANCE.preventNextPlay;
    }

    public static boolean shouldSendToAllPlayers() {
        return INSTANCE.enabled && INSTANCE.sendToAllPlayers;
    }

    public static void markAboutToPlay() {
        if (INSTANCE.enabled) {
            INSTANCE.aboutToPlay = true;
        }
    }
}
