package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldMapConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import java.util.Collection;
import java.util.UUID;

public class PlayerAvatarMarkerProvider implements WorldMapManager.MarkerProvider {

    static void removePersistedAvatar(UUID uuid) {
        PlayerAvatarPlayerModelSupport.forget(uuid);
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null) return;

        WorldMapConfig worldMapConfig = world.getGameplayConfig().getWorldMapConfig();
        if (worldMapConfig != null && !worldMapConfig.isDisplayPlayers()) {
            return;
        }

        PlayerAvatarProviderContext context = PlayerAvatarProviderContext.resolve(viewer);
        if (!context.isSurfaceEnabled()) {
            return;
        }

        PlayerAvatarSurface surface = context.surface();
        PlayerAvatarConfig config = context.config();
        boolean showNickname = config == null || config.showNickname;
        boolean showSelfMarker = surface != PlayerAvatarSurface.MAP || PlayerAvatarWorldMapState.shouldShowSelfMarker(viewer);
        PlayerAvatarVisibilityService.VisibilityLookup visibilityLookup =
                PlayerAvatarVisibilityService.createLookup(context.viewerRef(), context.viewerUuid(), collector);

        for (PlayerRef ref : playerRefs) {
            try {
                UUID playerUuid = ref.getUuid();
                if (playerUuid == null) continue;
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
                Transform liveTransform = PlayerAvatarLiveTracker.resolveTransform(ref);
                if (liveTransform == null) continue;
                Vector3d position = liveTransform.getPosition();
                if (position == null) continue;
                String playerName = PlayerAvatarPlayerNames.resolve(ref);
                PlayerAvatarMarkerVisuals.AvatarVisual avatarVisual =
                        PlayerAvatarMarkerVisuals.resolveAvatarVisual(context.viewerRef(), playerUuid, playerName, visibilityState, null);
                Vector3f markerRotation = PlayerAvatarMarkerFactory.resolveMarkerRotation(config, liveTransform.getRotation());
                Transform transform = markerRotation == liveTransform.getRotation()
                        ? liveTransform
                        : new Transform(position, markerRotation);
                if (surface == PlayerAvatarSurface.MAP && isViewer && !showSelfMarker) {
                    continue;
                }

                String markerLabel = showNickname
                        ? PlayerAvatarMarkerVisuals.decorateLabel(playerName, visibilityState, context.viewerRef())
                        : null;

                MapMarker marker = PlayerAvatarMarkerFactory.createNamedPlayerMarker(
                    PlayerAvatarMarkerFactory.buildDynamicMarkerId(
                        PlayerAvatarMarkerFactory.PLAYER_MARKER_PREFIX,
                        playerUuid,
                        avatarVisual.markerVariant(),
                        transform),
                        playerUuid,
                        markerLabel,
                        PlayerAvatarMarkerVisuals.labelColor(visibilityState),
                        avatarVisual.markerImage(),
                        transform);
                if (surface == PlayerAvatarSurface.MAP) {
                    collector.addIgnoreViewDistance(marker);
                } else {
                    collector.add(marker);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
