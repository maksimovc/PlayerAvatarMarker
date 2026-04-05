package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.util.PositionUtil;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class PlayerAvatarMarkerSupport {

    static final String MARKER_PREFIX = "PlayerAvatar-";

    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarMarkerSupport.class.getName());
    private static final Set<UUID> persistedAvatars =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private PlayerAvatarMarkerSupport() {}

    static void removePersistedAvatar(UUID uuid) {
        persistedAvatars.remove(uuid);
    }

    static int getAvatarSize(PlayerAvatarConfig config) {
        return (config != null) ? config.avatarSize : 64;
    }

    static Vector3f resolveMarkerRotation(PlayerAvatarConfig config, Vector3f headRotation) {
        return (config != null && config.enableRotation && headRotation != null)
                ? headRotation
                : Vector3f.ZERO;
    }

    static AvatarVisual resolveAvatarVisual(UUID playerUuid, String playerName, int avatarSize) {
        String slotImage = PlayerAvatarAssetPack.getImagePath(playerUuid);
        CompletableFuture<byte[]> avatarFuture = PlayerAvatarCache.getOrFetch(playerUuid, playerName);
        boolean avatarAssetRegistered = persistedAvatars.contains(playerUuid);

        if (avatarFuture.isDone() && !avatarFuture.isCompletedExceptionally()) {
            byte[] bytes = avatarFuture.join();
            if (bytes != null && bytes.length > 0) {
                if (!avatarAssetRegistered && persistedAvatars.add(playerUuid)) {
                    PlayerAvatarConfig cfg = PlayerAvatarMarkerPlugin.getConfig();
                    byte[] processed = PlayerAvatarImageProcessor.process(
                            bytes, avatarSize,
                            cfg != null ? cfg.backgroundColorRGB() : 0x2D2D2D,
                            cfg != null && cfg.enableBackground);
                    LOGGER.info("[PlayerAvatarMarker] Writing avatar for " + playerName + " -> " + slotImage);
                    PlayerAvatarAssetPack.writeAvatar(slotImage, processed);
                }
            }
        }

        String markerVariant = avatarAssetRegistered ? ":avatar" : ":fallback";
        String markerImage = avatarAssetRegistered ? slotImage : PlayerAvatarAssetPack.getFallbackImagePath();
        return new AvatarVisual(markerVariant, markerImage);
    }

    static String buildMarkerId(UUID playerUuid, String markerVariant) {
        return MARKER_PREFIX + playerUuid + markerVariant;
    }

    static MapMarker createPlainMarker(String markerId, String markerLabel, String markerImage, Transform transform) {
        com.hypixel.hytale.protocol.Transform transformPacket = PositionUtil.toTransformPacket(transform);
        FormattedMessage formattedMessage = null;
        if (markerLabel != null && !markerLabel.isBlank()) {
            formattedMessage = new FormattedMessage();
            formattedMessage.rawText = markerLabel;
        }

        return new MapMarker(markerId, formattedMessage, markerImage, transformPacket, null, null);
    }

    record AvatarVisual(String markerVariant, String markerImage) {}
}