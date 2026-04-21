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
import com.hypixel.hytale.server.core.command.system.CommandSender;

public class PlayerAvatarMarkerProvider implements WorldMapManager.MarkerProvider {

    static void removePersistedAvatar(UUID uuid) {
        PlayerAvatarMarkerSupport.removePersistedAvatar(uuid);
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null) return;

        WorldMapConfig worldMapConfig = world.getGameplayConfig().getWorldMapConfig();
        if (worldMapConfig != null && !worldMapConfig.isDisplayPlayers()) {
            return;
        }

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        UUID viewerUuid = viewer != null ? ((CommandSender) viewer).getUuid() : null;
        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        PlayerRef viewerRef = plugin != null ? plugin.getActivePlayerRef(viewerUuid) : null;
        if (plugin != null && plugin.getAvatarService() != null && viewerRef != null) {
            plugin.getAvatarService().advanceViewerDeliveryPhase(viewerRef);
        }
        boolean worldMapVisible = PlayerAvatarMarkerSupport.isWorldMapVisible(viewer);
        PlayerAvatarSurface surface = worldMapVisible ? PlayerAvatarSurface.MAP : PlayerAvatarSurface.COMPASS;
        PlayerAvatarPlayerSettings viewerSettings = plugin != null ? plugin.resolvePlayerSettings(viewerUuid) : new PlayerAvatarPlayerSettings();
        if (!viewerSettings.isEnabled(surface)) {
            return;
        }

        boolean viewerIsVanished = VanishBridge.isVanished(viewerUuid);
        for (PlayerRef ref : playerRefs) {
            try {
                UUID playerUuid = ref.getUuid();
                if (playerUuid == null) continue;
                if (!viewerSettings.isEnabledFor(surface, viewerUuid, playerUuid)) {
                    continue;
                }
                boolean isViewer = viewerUuid != null && playerUuid.equals(viewerUuid);
                // hide vanished players only from non-vanished viewers;
                // vanished admins can still see everyone
                if (!isViewer && !viewerIsVanished && VanishBridge.isVanished(playerUuid)) {
                    continue;
                }
                Transform t = PlayerAvatarLiveTracker.resolveTransform(ref);
                if (t == null) continue;
                Vector3d position = t.getPosition();
                if (position == null) continue;
                String playerName = ref.getUsername();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = playerUuid.toString().substring(0, 8);
                }
                PlayerAvatarMarkerSupport.AvatarVisual avatarVisual =
                        PlayerAvatarMarkerSupport.resolveAvatarVisual(viewerRef, playerUuid, playerName, null);
                Vector3f headRotation = PlayerAvatarLiveTracker.resolveRotation(ref);
                Vector3f markerRotation = PlayerAvatarMarkerSupport.resolveMarkerRotation(config, headRotation);
                Transform transform = new Transform(position, markerRotation);
                if (surface == PlayerAvatarSurface.MAP && isViewer && !PlayerAvatarMarkerSupport.shouldShowSelfMarker(viewer)) {
                    continue;
                }

                MapMarker marker = PlayerAvatarMarkerSupport.createNamedPlayerMarker(
                    PlayerAvatarMarkerSupport.buildDynamicMarkerId(
                        PlayerAvatarMarkerSupport.MARKER_PREFIX,
                        playerUuid,
                        avatarVisual.markerVariant(),
                        transform),
                        playerUuid,
                        config == null || config.showNickname ? playerName : null,
                        avatarVisual.markerImage(),
                        transform);
                if (surface == PlayerAvatarSurface.MAP) {
                    collector.addIgnoreViewDistance(marker);
                } else {
                    collector.add(marker);
                }
            } catch (Exception e) {
            }
        }
    }
}
