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
import java.util.logging.Logger;

public class PlayerAvatarMarkerProvider implements WorldMapManager.MarkerProvider {

    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarMarkerProvider.class.getName());

    static void removePersistedAvatar(UUID uuid) {
        PlayerAvatarMarkerSupport.removePersistedAvatar(uuid);
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null) return;
        boolean singlePlayerViewerFallback = playerRefs.size() == 1;

        WorldMapConfig worldMapConfig = world.getGameplayConfig().getWorldMapConfig();
        if (worldMapConfig != null && !worldMapConfig.isDisplayPlayers()) {
            return;
        }

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
    int avatarSize = PlayerAvatarMarkerSupport.getAvatarSize(config);
    UUID viewerUuid = viewer != null ? ((CommandSender) viewer).getUuid() : null;

        for (PlayerRef ref : playerRefs) {
            try {
                UUID playerUuid = ref.getUuid();
                if (playerUuid == null) continue;
        boolean isViewer = viewerUuid != null && playerUuid.equals(viewerUuid);
                Transform t = ref.getTransform();
                if (t == null) continue;
                Vector3d position = t.getPosition();
                if (position == null) continue;
                String playerName = ref.getUsername();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = playerUuid.toString().substring(0, 8);
                }
                Vector3f headRotation = ref.getHeadRotation();
                PlayerAvatarMarkerSupport.AvatarVisual avatarVisual =
                        PlayerAvatarMarkerSupport.resolveAvatarVisual(playerUuid, playerName, avatarSize);
                Vector3f markerRotation = PlayerAvatarMarkerSupport.resolveMarkerRotation(config, headRotation);
                Transform transform = new Transform(position, markerRotation);
                if (isViewer && !singlePlayerViewerFallback) {
                    continue;
                }

                MapMarker marker = PlayerAvatarMarkerSupport.createPlainMarker(
                        PlayerAvatarMarkerSupport.buildMarkerId(playerUuid, avatarVisual.markerVariant()),
                        config == null || config.showNickname ? playerName : null,
                        avatarVisual.markerImage(),
                        transform);
                collector.add(marker);
            } catch (Exception e) {
                LOGGER.warning("[PlayerAvatarMarker] Error creating marker: " + e.getMessage());
            }
        }
    }
}
