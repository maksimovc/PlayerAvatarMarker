package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import java.util.Collection;
import java.util.UUID;

final class BetterMapCompatProvider implements WorldMapManager.MarkerProvider {

    static final String PROVIDER_KEY = "BetterMapPlayerRadar";

    static boolean isAvailable() {
        return BetterMapBridge.isAvailable();
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        BetterMapBridge.ViewerSettings viewerSettings = BetterMapBridge.resolveViewerSettings(viewer);
        if (!viewerSettings.enabled()) {
            return;
        }

        UUID viewerUuid = ((CommandSender) viewer).getUuid();
        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        PlayerRef viewerRef = plugin != null ? plugin.getActivePlayerRef(viewerUuid) : null;
        if (plugin != null && plugin.getAvatarService() != null && viewerRef != null) {
            plugin.getAvatarService().advanceViewerDeliveryPhase(viewerRef);
        }
        PlayerAvatarPlayerSettings viewerSettingsState = plugin != null ? plugin.resolvePlayerSettings(viewerUuid) : new PlayerAvatarPlayerSettings();
        if (!viewerSettingsState.compassEnabled) {
            return;
        }

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null || playerRefs.isEmpty()) {
            return;
        }

        Vector3d viewerPosition = findViewerPosition(playerRefs, viewerUuid);
        long maxDistanceSquared = maxDistanceSquared(viewerSettings.radarRange());

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        for (PlayerRef ref : playerRefs) {
            try {
                UUID playerUuid = ref.getUuid();
                if (playerUuid == null) {
                    continue;
                }
                if (!viewerSettingsState.isEnabledFor(PlayerAvatarSurface.COMPASS, viewerUuid, playerUuid)) {
                    continue;
                }

                boolean isViewer = viewerUuid != null && viewerUuid.equals(playerUuid);
                if (!isViewer && isViewerHiddenForTarget(ref, viewerUuid)) {
                    continue;
                }

                Transform playerTransform = PlayerAvatarLiveTracker.resolveTransform(ref);
                if (playerTransform == null || playerTransform.getPosition() == null) {
                    continue;
                }

                if (!isViewer && viewerPosition != null && maxDistanceSquared != Long.MAX_VALUE) {
                    if (squaredDistance(viewerPosition, playerTransform.getPosition()) > maxDistanceSquared) {
                        continue;
                    }
                }

                String playerName = ref.getUsername();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = playerUuid.toString().substring(0, 8);
                }
                int distance = 0;
                if (!isViewer && viewerPosition != null) {
                    distance = (int) Math.sqrt(squaredDistance(viewerPosition, playerTransform.getPosition()));
                }

                String markerLabel = null;
                if (config == null || config.showNickname) {
                    markerLabel = playerName + " (" + distance + "m)";
                }

                PlayerAvatarMarkerSupport.AvatarVisual avatarVisual =
                        PlayerAvatarMarkerSupport.resolveAvatarVisual(viewerRef, playerUuid, playerName, null);
                Vector3f markerRotation = PlayerAvatarMarkerSupport.resolveMarkerRotation(
                        config,
                        PlayerAvatarLiveTracker.resolveRotation(ref));
                Transform markerTransform = new Transform(playerTransform.getPosition(), markerRotation);

                MapMarker marker = PlayerAvatarMarkerSupport.createNamedImageMarker(
                    PlayerAvatarMarkerSupport.buildDynamicMarkerId(
                    PlayerAvatarMarkerSupport.BETTER_MAP_MARKER_PREFIX,
                        playerUuid,
                        avatarVisual.markerVariant(),
                        markerTransform),
                        markerLabel,
                        avatarVisual.markerImage(),
                        markerTransform);

                BetterMapBridge.injectTeleportContextMenu(marker, viewer);
                collector.add(marker);
            } catch (Exception e) {
            }
        }
    }

    private static boolean isViewerHiddenForTarget(PlayerRef ref, UUID viewerUuid) {
        if (ref == null || viewerUuid == null) {
            return false;
        }

        return Boolean.TRUE.equals(PlayerAvatarWorldThreadBridge.call(
                ref,
                () -> {
                    HiddenPlayersManager hiddenPlayersManager = ref.getHiddenPlayersManager();
                    return hiddenPlayersManager != null && hiddenPlayersManager.isPlayerHidden(viewerUuid);
                },
                Boolean.FALSE,
                "resolve hidden player state"));
    }

    private static Vector3d findViewerPosition(Collection<PlayerRef> playerRefs, UUID viewerUuid) {
        if (playerRefs == null || viewerUuid == null) {
            return null;
        }

        for (PlayerRef ref : playerRefs) {
            if (ref == null || !viewerUuid.equals(ref.getUuid())) {
                continue;
            }

            Vector3d position = PlayerAvatarLiveTracker.resolvePosition(ref);
            if (position != null) {
                return position;
            }
            break;
        }

        return null;
    }

    private static long maxDistanceSquared(int radarRange) {
        if (radarRange < 0) {
            return Long.MAX_VALUE;
        }

        long range = radarRange;
        return range * range;
    }

    private static double squaredDistance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}