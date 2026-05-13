package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
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

        PlayerAvatarProviderContext context = PlayerAvatarProviderContext.resolve(viewer);
        UUID viewerUuid = ((CommandSender) viewer).getUuid();
        if (!context.isSurfaceEnabled()) {
            return;
        }

        PlayerAvatarSurface surface = context.surface();
        PlayerAvatarConfig config = context.config();
        boolean showNickname = config == null || config.showNickname;
        boolean showSelfMarker = surface != PlayerAvatarSurface.MAP || PlayerAvatarWorldMapState.shouldShowSelfMarker(viewer);
        PlayerAvatarVisibilityService.VisibilityLookup visibilityLookup =
                PlayerAvatarVisibilityService.createLookup(context.viewerRef(), context.viewerUuid(), collector);

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null || playerRefs.isEmpty()) {
            return;
        }

        Vector3d viewerPosition = PlayerAvatarLiveTracker.resolvePosition(context.viewerRef());
        if (viewerPosition == null) {
            viewerPosition = findViewerPosition(playerRefs, viewerUuid);
        }
        long maxDistanceSquared = surface == PlayerAvatarSurface.MAP
                ? Long.MAX_VALUE
                : maxDistanceSquared(viewerSettings.radarRange());

        for (PlayerRef ref : playerRefs) {
            try {
                UUID playerUuid = ref.getUuid();
                if (playerUuid == null) {
                    continue;
                }
                boolean isViewer = context.isViewer(playerUuid);
                PlayerAvatarVisibilityDecision visibility = visibilityLookup.resolve(ref);
                PlayerAvatarVisibilityState visibilityState = visibility.state();
                boolean surfaceEnabled = context.isTargetEnabled(playerUuid, visibility);
                if (!surfaceEnabled) {
                    continue;
                }
                if (!visibility.isVisible()) {
                    continue;
                }

                if (surface == PlayerAvatarSurface.MAP && isViewer && !showSelfMarker) {
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

                String playerName = PlayerAvatarPlayerNames.resolve(ref);
                int distance = 0;
                if (!isViewer && viewerPosition != null) {
                    distance = (int) Math.sqrt(squaredDistance(viewerPosition, playerTransform.getPosition()));
                }

                String markerLabel = null;
                if (showNickname) {
                    markerLabel = PlayerAvatarMarkerVisuals.decorateLabel(playerName + " (" + distance + "m)", visibilityState, context.viewerRef());
                }

                PlayerAvatarMarkerVisuals.AvatarVisual avatarVisual =
                        PlayerAvatarMarkerVisuals.resolveAvatarVisual(context.viewerRef(), playerUuid, playerName, visibilityState, null);
                Vector3f markerRotation = PlayerAvatarMarkerFactory.resolveMarkerRotation(config, playerTransform.getRotation());
                Transform markerTransform = markerRotation == playerTransform.getRotation()
                        ? playerTransform
                        : new Transform(playerTransform.getPosition(), markerRotation);

                MapMarker marker = PlayerAvatarMarkerFactory.createPlainPlayerMarker(
                    PlayerAvatarMarkerFactory.buildDynamicMarkerId(
                    PlayerAvatarMarkerFactory.BETTER_MAP_MARKER_PREFIX,
                        playerUuid,
                        avatarVisual.markerVariant(),
                        markerTransform),
                    playerUuid,
                        markerLabel,
                        PlayerAvatarMarkerVisuals.labelColor(visibilityState),
                        avatarVisual.markerImage(),
                        markerTransform);

                BetterMapBridge.injectTeleportContextMenu(marker, viewer);
                if (surface == PlayerAvatarSurface.MAP) {
                    collector.addIgnoreViewDistance(marker);
                } else {
                    collector.add(marker);
                }
            } catch (Exception ignored) {
            }
        }
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