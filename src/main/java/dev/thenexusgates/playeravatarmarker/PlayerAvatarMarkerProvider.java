package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerAvatarMarkerProvider implements WorldMapManager.MarkerProvider {

    private static final String MARKER_PREFIX = "pam:player:";
    private static final java.util.concurrent.atomic.AtomicBoolean firstRunLogged =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarMarkerProvider.class.getName());
    private static final Set<UUID> persistedAvatars =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        if (firstRunLogged.compareAndSet(false, true)) {
            LOGGER.info("[PlayerAvatarMarker] update() called for " + viewer.getUuid());
        }
        UUID viewerUuid = viewer.getUuid();

        for (Player player : world.getPlayers()) {
            try {
                UUID playerUuid = player.getUuid();
                if (playerUuid == null) continue;
                Transform t = player.getTransformComponent() != null
                        ? player.getTransformComponent().getTransform() : null;
                if (t == null) continue;
                Vector3d position = t.getPosition();
                Vector3f rotation = t.getRotation();
                if (position == null) continue;
                PlayerRef ref = player.getPlayerRef();
                String playerName = (ref != null && ref.getUsername() != null && !ref.getUsername().isEmpty())
                        ? ref.getUsername()
                        : playerUuid.toString().substring(0, 8);
                String slotImage = PlayerAvatarAssetPack.getImagePath(playerUuid);
                CompletableFuture<byte[]> avatarFuture =
                        PlayerAvatarCache.getOrFetch(playerUuid, playerName);
                boolean avatarReady = false;
                if (avatarFuture.isDone() && !avatarFuture.isCompletedExceptionally()) {
                    byte[] bytes = avatarFuture.join();
                    if (bytes != null && bytes.length > 0) {
                        avatarReady = true;
                        if (persistedAvatars.add(playerUuid)) {
                            PlayerAvatarConfig cfg = PlayerAvatarMarkerPlugin.getConfig();
                            byte[] processed = PlayerAvatarImageProcessor.process(
                                    bytes, 64, cfg != null ? cfg.backgroundColorRGB() : 0x2D2D2D,
                                    cfg != null && cfg.enableBackground);
                            LOGGER.info("[PlayerAvatarMarker] Writing avatar for " + playerName + " -> " + slotImage);
                            PlayerAvatarAssetPack.writeAvatar(slotImage, processed);
                        }
                    }
                }
                // Use custom avatar if ready, otherwise fall back to default Player.png
                String markerImage = avatarReady ? slotImage : "Player.png";
                PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
                Vector3f markerRotation = (config != null && config.enableRotation && rotation != null)
                        ? rotation : Vector3f.ZERO;
                Transform transform = new Transform(position, markerRotation);
                MapMarkerBuilder builder = new MapMarkerBuilder(MARKER_PREFIX + playerUuid, markerImage, transform);
                builder.withComponent(new PlayerMarkerComponent(playerUuid));
                if (config == null || config.showNickname) {
                    builder.withCustomName(playerName);
                }
                collector.addIgnoreViewDistance(builder.build());
            } catch (Exception e) {
                LOGGER.warning("[PlayerAvatarMarker] Error creating marker: " + e.getMessage());
            }
        }
    }
}
