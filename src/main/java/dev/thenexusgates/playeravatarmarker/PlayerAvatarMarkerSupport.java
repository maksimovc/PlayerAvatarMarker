package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.util.PositionUtil;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarMarkerSupport {

    static final String MARKER_PREFIX = "PlayerAvatar-";
    static final String BETTER_MAP_MARKER_PREFIX = "PlayerRadar-";
    static final String PLAYER_MARKER_IMAGE = "Player.png";

    private static final double POSITION_ID_BUCKET = 4.0d;
    private static final float ROTATION_ID_BUCKET_DEGREES = 10.0f;

    private static final Set<UUID> ensuredPlayerModels =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Field CLIENT_HAS_WORLDMAP_VISIBLE_FIELD = resolveClientHasWorldMapVisibleField();

    private PlayerAvatarMarkerSupport() {}

    static void removePersistedAvatar(UUID uuid) {
        ensuredPlayerModels.remove(uuid);
    }

    static void ensureRenderablePlayerModel(PlayerRef playerRef) {
        try {
            if (playerRef == null) {
                return;
            }

            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null || ensuredPlayerModels.contains(playerUuid)) {
                return;
            }

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return;
            }

            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            PlayerSkinComponent skinComponent = store.getComponent(entityRef, PlayerSkinComponent.getComponentType());
            if (skinComponent == null || skinComponent.getPlayerSkin() == null) {
                return;
            }

            if (store.getComponent(entityRef, ModelComponent.getComponentType()) != null) {
                ensuredPlayerModels.add(playerUuid);
                return;
            }

            CosmeticsModule cosmeticsModule = CosmeticsModule.get();
            if (cosmeticsModule == null) {
                return;
            }

            var model = cosmeticsModule.createModel(skinComponent.getPlayerSkin());
            if (model == null) {
                return;
            }

            store.putComponent(entityRef, ModelComponent.getComponentType(), new ModelComponent(model));
            skinComponent.setNetworkOutdated();
            ensuredPlayerModels.add(playerUuid);
        } catch (Exception e) {
        }
    }

    static int getAvatarSize(PlayerAvatarConfig config) {
        return (config != null) ? config.avatarSize : 64;
    }

    static Vector3f resolveMarkerRotation(PlayerAvatarConfig config, Vector3f headRotation) {
        return (config != null && config.enableRotation && headRotation != null)
                ? headRotation
                : Vector3f.ZERO;
    }

    static AvatarVisual resolveAvatarVisual(PlayerRef viewerRef, UUID playerUuid, String playerName, Runnable onReady) {
        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        PlayerAvatarAvatarService avatarService = plugin != null ? plugin.getAvatarService() : null;
        if (avatarService == null) {
            return new AvatarVisual(":fallback", null);
        }

        String markerImage = avatarService.ensureAvatarForViewer(viewerRef, playerUuid, playerName, onReady);
        String markerVariant = PlayerAvatarAvatarService.isFallbackMarkerImage(markerImage)
            ? ":fallback"
            : ":avatar:" + Integer.toUnsignedString(markerImage.hashCode(), 16);
        return new AvatarVisual(markerVariant, markerImage);
    }

    static String toUiAssetPath(String markerImage) {
        return PlayerAvatarAvatarService.toUiAssetPath(markerImage);
    }

    static String buildMarkerId(String markerPrefix, UUID playerUuid, String markerVariant) {
        return markerPrefix + playerUuid + (markerVariant != null ? markerVariant : "");
    }

    static String buildDynamicMarkerId(String markerPrefix, UUID playerUuid, String markerVariant, Transform transform) {
        StringBuilder builder = new StringBuilder(markerPrefix)
                .append(playerUuid)
                .append(markerVariant != null ? markerVariant : "");

        if (transform != null && transform.getPosition() != null) {
            builder.append(':').append(quantize(transform.getPosition().x));
            builder.append(':').append(quantize(transform.getPosition().y));
            builder.append(':').append(quantize(transform.getPosition().z));
        }

        if (transform != null && transform.getRotation() != null) {
            builder.append(':').append(quantizeRotation(transform.getRotation().getYaw()));
        }

        return builder.toString();
    }

    static boolean isWorldMapVisible(Player viewer) {
        if (viewer == null) {
            return false;
        }

        Field field = CLIENT_HAS_WORLDMAP_VISIBLE_FIELD;
        if (field == null) {
            return false;
        }

        try {
            WorldMapTracker worldMapTracker = viewer.getWorldMapTracker();
            if (worldMapTracker == null) {
                return false;
            }

            return field.getBoolean(worldMapTracker);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    static boolean shouldShowSelfMarker(Player viewer) {
        return isWorldMapVisible(viewer);
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

    static MapMarker createNamedImageMarker(String markerId, String markerLabel, String markerImage, Transform transform) {
        MapMarkerBuilder builder = new MapMarkerBuilder(markerId, markerImage, transform);
        if (markerLabel != null && !markerLabel.isBlank()) {
            builder.withCustomName(markerLabel);
        }
        return builder.build();
    }

    static MapMarker createPlainPlayerMarker(String markerId, UUID playerUuid, String markerLabel, String markerImage, Transform transform) {
        com.hypixel.hytale.protocol.Transform transformPacket = PositionUtil.toTransformPacket(transform);
        FormattedMessage formattedMessage = null;
        if (markerLabel != null && !markerLabel.isBlank()) {
            formattedMessage = new FormattedMessage();
            formattedMessage.rawText = markerLabel;
        }

        PlayerMarkerComponent[] components = playerUuid != null
                ? new PlayerMarkerComponent[] {new PlayerMarkerComponent(playerUuid)}
                : null;
        return new MapMarker(markerId, formattedMessage, markerImage, transformPacket, null, components);
    }

    static MapMarker createNamedPlayerMarker(String markerId, UUID playerUuid, String markerLabel, String markerImage, Transform transform) {
        MapMarkerBuilder builder = new MapMarkerBuilder(markerId, markerImage, transform);
        if (playerUuid != null) {
            builder.withComponent(new PlayerMarkerComponent(playerUuid));
        }
        if (markerLabel != null && !markerLabel.isBlank()) {
            builder.withCustomName(markerLabel);
        }
        return builder.build();
    }

    private static Field resolveClientHasWorldMapVisibleField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static long quantize(double value) {
        return Math.round(value * POSITION_ID_BUCKET);
    }

    private static long quantizeRotation(float value) {
        return Math.round(value / ROTATION_ID_BUCKET_DEGREES);
    }

    record AvatarVisual(String markerVariant, String markerImage) {}
}