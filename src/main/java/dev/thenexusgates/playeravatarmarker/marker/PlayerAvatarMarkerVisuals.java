package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

final class PlayerAvatarMarkerVisuals {

    private static final String GHOSTED_LABEL_COLOR = "#FFFFFF80";

    private PlayerAvatarMarkerVisuals() {
    }

    static String decorateLabel(String baseLabel, PlayerAvatarVisibilityState visibilityState, PlayerRef viewerRef) {
        if (baseLabel == null || baseLabel.isBlank() || visibilityState == null || !visibilityState.isGhosted()) {
            return baseLabel;
        }

        return baseLabel + " · " + PlayerAvatarUiText.choose(viewerRef, "hidden", "прихований");
    }

    static String labelColor(PlayerAvatarVisibilityState visibilityState) {
        return visibilityState != null && visibilityState.isGhosted() ? GHOSTED_LABEL_COLOR : null;
    }

    static AvatarVisual resolveAvatarVisual(PlayerRef viewerRef,
                                            UUID playerUuid,
                                            String playerName,
                                            PlayerAvatarVisibilityState visibilityState,
                                            Runnable onReady) {
        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        PlayerAvatarAvatarService avatarService = plugin != null ? plugin.getAvatarService() : null;
        if (avatarService == null) {
            return new AvatarVisual(":fallback", null);
        }

        boolean ghosted = visibilityState != null && visibilityState.isGhosted();
        String markerImage = avatarService.ensureAvatarForViewer(viewerRef, playerUuid, playerName, ghosted, onReady);
        String variantPrefix = ghosted ? ":ghosted:" : ":avatar:";
        String markerVariant = PlayerAvatarAvatarService.isFallbackMarkerImage(markerImage)
                ? ":fallback"
                : variantPrefix + Integer.toUnsignedString(markerImage.hashCode(), 16);
        return new AvatarVisual(markerVariant, markerImage);
    }

    static String toUiAssetPath(String markerImage) {
        return PlayerAvatarAvatarService.toUiAssetPath(markerImage);
    }

    record AvatarVisual(String markerVariant, String markerImage) {
    }
}

