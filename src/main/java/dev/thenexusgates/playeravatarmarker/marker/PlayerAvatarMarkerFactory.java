package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.util.PositionUtil;

import java.util.UUID;

final class PlayerAvatarMarkerFactory {

    static final String PLAYER_MARKER_PREFIX = "PlayerAvatar-";
    static final String BETTER_MAP_MARKER_PREFIX = "PlayerRadar-";
    private static final double POSITION_ID_SCALE = 32.0;
    private static final int ROTATION_ID_BUCKETS = 256;

    private PlayerAvatarMarkerFactory() {
    }

    static Vector3f resolveMarkerRotation(PlayerAvatarConfig config, Vector3f headRotation) {
        return config != null && config.enableRotation && headRotation != null
                ? headRotation
                : Vector3f.ZERO;
    }

    static String buildDynamicMarkerId(String markerPrefix, UUID playerUuid, String markerVariant, Transform transform) {
        StringBuilder builder = new StringBuilder(markerPrefix)
                .append(playerUuid)
                .append(markerVariant != null ? markerVariant : "");

        if (transform == null) {
            return builder.toString();
        }

        if (transform.getPosition() != null) {
            builder.append(":p")
                    .append(quantizePosition(transform.getPosition().x))
                    .append('_')
                    .append(quantizePosition(transform.getPosition().z));
        }

        if (transform.getRotation() != null) {
            builder.append(":r")
                    .append(quantizeYaw(transform.getRotation().getYaw()));
        }

        return builder.toString();
    }

    private static long quantizePosition(double coordinate) {
        if (!Double.isFinite(coordinate)) {
            return 0L;
        }
        return Math.round(coordinate * POSITION_ID_SCALE);
    }

    private static int quantizeYaw(double yawRadians) {
        if (!Double.isFinite(yawRadians)) {
            return 0;
        }

        double fullTurn = Math.PI * 2.0;
        double normalized = yawRadians % fullTurn;
        if (normalized < 0.0) {
            normalized += fullTurn;
        }

        int bucket = (int) Math.round((normalized / fullTurn) * ROTATION_ID_BUCKETS) % ROTATION_ID_BUCKETS;
        return bucket < 0 ? bucket + ROTATION_ID_BUCKETS : bucket;
    }

    static MapMarker createPlainPlayerMarker(String markerId,
                                             UUID playerUuid,
                                             String markerLabel,
                                             String labelColor,
                                             String markerImage,
                                             Transform transform) {
        com.hypixel.hytale.protocol.Transform transformPacket = PositionUtil.toTransformPacket(transform);
        FormattedMessage formattedMessage = createFormattedMessage(markerLabel, labelColor);
        PlayerMarkerComponent[] components = playerUuid != null
                ? new PlayerMarkerComponent[] {new PlayerMarkerComponent(playerUuid)}
                : null;
        return new MapMarker(markerId, formattedMessage, markerImage, transformPacket, null, components);
    }

    static MapMarker createNamedPlayerMarker(String markerId,
                                             UUID playerUuid,
                                             String markerLabel,
                                             String labelColor,
                                             String markerImage,
                                             Transform transform) {
        MapMarkerBuilder builder = new MapMarkerBuilder(markerId, markerImage, transform);
        if (playerUuid != null) {
            builder.withComponent(new PlayerMarkerComponent(playerUuid));
        }
        applyMarkerLabel(builder, markerLabel, labelColor);
        return builder.build();
    }

    private static void applyMarkerLabel(MapMarkerBuilder builder, String markerLabel, String labelColor) {
        if (builder == null || markerLabel == null || markerLabel.isBlank()) {
            return;
        }

        if (labelColor == null || labelColor.isBlank()) {
            builder.withCustomName(markerLabel);
            return;
        }

        builder.withName(Message.raw(markerLabel).color(labelColor));
    }

    private static FormattedMessage createFormattedMessage(String markerLabel, String labelColor) {
        if (markerLabel == null || markerLabel.isBlank()) {
            return null;
        }

        FormattedMessage formattedMessage = new FormattedMessage();
        formattedMessage.rawText = markerLabel;
        if (labelColor != null && !labelColor.isBlank()) {
            formattedMessage.color = labelColor;
        }
        return formattedMessage;
    }

}

