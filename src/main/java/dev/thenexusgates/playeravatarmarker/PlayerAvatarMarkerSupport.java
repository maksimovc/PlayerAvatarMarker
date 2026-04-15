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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class PlayerAvatarMarkerSupport {

    static final String MARKER_PREFIX = "PlayerAvatar-";
    static final String BETTER_MAP_MARKER_PREFIX = "PlayerRadar-";

    private static final double POSITION_ID_BUCKET = 4.0d;
    private static final float ROTATION_ID_BUCKET_DEGREES = 10.0f;

    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarMarkerSupport.class.getName());
    private static final Set<UUID> persistedAvatars =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
        private static final Set<UUID> ensuredPlayerModels =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Field CLIENT_HAS_WORLDMAP_VISIBLE_FIELD = resolveClientHasWorldMapVisibleField();

    private PlayerAvatarMarkerSupport() {}

    static void removePersistedAvatar(UUID uuid) {
        persistedAvatars.remove(uuid);
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
            LOGGER.warning("[PlayerAvatarMarker] Failed to ensure player model: " + e.getMessage());
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

    static String buildDynamicMarkerId(String markerPrefix, UUID playerUuid, String markerVariant, Transform transform) {
        StringBuilder builder = new StringBuilder(markerPrefix)
                .append(playerUuid)
                .append(markerVariant);

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

    static boolean shouldShowSelfMarker(Player viewer) {
        if (viewer == null) {
            return true;
        }

        Field field = CLIENT_HAS_WORLDMAP_VISIBLE_FIELD;
        if (field == null) {
            return true;
        }

        try {
            WorldMapTracker worldMapTracker = viewer.getWorldMapTracker();
            if (worldMapTracker == null) {
                return true;
            }

            return field.getBoolean(worldMapTracker);
        } catch (ReflectiveOperationException e) {
            LOGGER.warning("[PlayerAvatarMarker] Failed to read world map visibility: " + e.getMessage());
            return true;
        }
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
        builder.withComponent(new PlayerMarkerComponent(playerUuid));
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
            LOGGER.warning("[PlayerAvatarMarker] WorldMapTracker.clientHasWorldMapVisible is unavailable: " + e.getMessage());
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