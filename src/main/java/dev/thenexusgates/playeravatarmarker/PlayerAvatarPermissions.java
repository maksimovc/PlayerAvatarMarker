package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

final class PlayerAvatarPermissions {

    static final String ADMIN = "playeravatarmarker.admin";
    static final String USE = "playeravatarmarker.use";
    static final String SETTINGS_MAP = "playeravatarmarker.settings.map";
    static final String SETTINGS_MINIMAP = "playeravatarmarker.settings.minimap";
    static final String SETTINGS_COMPASS = "playeravatarmarker.settings.compass";

    private PlayerAvatarPermissions() {
    }

    static boolean canOpenUi(PlayerRef playerRef) {
        return has(playerRef, USE);
    }

    static boolean canEditSurface(PlayerRef playerRef, PlayerAvatarSurface surface) {
        return has(playerRef, switch (surface) {
            case MAP -> SETTINGS_MAP;
            case MINIMAP -> SETTINGS_MINIMAP;
            case COMPASS -> SETTINGS_COMPASS;
        });
    }

    static void sendUseDenied(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        playerRef.sendMessage(Message.raw(PlayerAvatarUiText.choose(
                playerRef,
                "You do not have permission to use /playeravatar.",
                "У вас немає дозволу на використання /playeravatar.")));
    }

    static void sendSurfaceDenied(PlayerRef playerRef, PlayerAvatarSurface surface) {
        if (playerRef == null || surface == null) {
            return;
        }

        String surfaceLabel = switch (surface) {
            case MAP -> PlayerAvatarUiText.choose(playerRef, "map", "мапи");
            case MINIMAP -> PlayerAvatarUiText.choose(playerRef, "minimap", "мінімапи");
            case COMPASS -> PlayerAvatarUiText.choose(playerRef, "compass", "компаса");
        };
        playerRef.sendMessage(Message.raw(PlayerAvatarUiText.format(
                playerRef,
                "You do not have permission to change %s avatar visibility.",
                "У вас немає дозволу змінювати видимість аватарок для %s.",
                surfaceLabel)));
    }

    private static boolean has(PlayerRef playerRef, String permission) {
        if (playerRef == null || permission == null || permission.isBlank()) {
            return false;
        }

        PermissionsModule permissionsModule = PermissionsModule.get();
        if (permissionsModule == null) {
            return false;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return false;
        }

        return permissionsModule.hasPermission(playerUuid, ADMIN)
                || permissionsModule.hasPermission(playerUuid, permission);
    }
}