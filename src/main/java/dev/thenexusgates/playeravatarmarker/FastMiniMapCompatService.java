package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.thenexusgates.fastminimap.FastMiniMapPlayerLayerApi;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class FastMiniMapCompatService {

    FastMiniMapCompatService() {}

    void register() {
        FastMiniMapPlayerLayerApi.setProvider(this::getDots);
    }

    void unregister() {
        FastMiniMapPlayerLayerApi.setProvider(null);
    }

    // -------------------------------------------------------------------------
    // PlayerDotProvider impl
    // -------------------------------------------------------------------------

    private List<FastMiniMapPlayerLayerApi.PlayerDot> getDots(
            String worldName, UUID viewerUuid,
            double viewerX, double viewerZ, int radiusBlocks) {

        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        if (plugin != null && !plugin.resolvePlayerSettings(viewerUuid).minimapEnabled) {
            return List.of();
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return List.of();
        }

        World world = null;
        for (World w : universe.getWorlds().values()) {
            if (w != null && w.isAlive() && worldName.equals(w.getName())) {
                world = w;
                break;
            }
        }
        if (world == null) {
            return List.of();
        }

        PlayerAvatarPlayerSettings viewerSettings = plugin != null ? plugin.resolvePlayerSettings(viewerUuid) : new PlayerAvatarPlayerSettings();
        if (!viewerSettings.minimapEnabled) {
            return List.of();
        }

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null || playerRefs.isEmpty()) {
            return List.of();
        }

        double radiusSq = radiusBlocks <= 0
                ? Double.POSITIVE_INFINITY
                : (double) radiusBlocks * radiusBlocks;

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        boolean showNickname = config == null || config.showNickname;

        // vanished admins can still see everyone; only hide vanished players from regular viewers
        boolean viewerIsVanished = VanishBridge.isVanished(viewerUuid);
        List<FastMiniMapPlayerLayerApi.PlayerDot> dots = new ArrayList<>();
        for (PlayerRef ref : playerRefs) {
            UUID uuid = ref.getUuid();
            if (uuid == null || uuid.equals(viewerUuid)) {
                continue; // skip self
            }

            if (!viewerIsVanished && VanishBridge.isVanished(uuid)) {
                continue;
            }

            if (!viewerSettings.isEnabledFor(PlayerAvatarSurface.MINIMAP, viewerUuid, uuid)) {
                continue;
            }

            Vector3d pos = PlayerAvatarLiveTracker.resolvePosition(ref);
            if (pos == null) {
                continue;
            }

            double dx = pos.x - viewerX;
            double dz = pos.z - viewerZ;
            if (dx * dx + dz * dz > radiusSq) {
                continue;
            }

            String username = ref.getUsername();
            if (username == null || username.isEmpty()) {
                username = uuid.toString().substring(0, 8);
            }

            BufferedImage icon = resolveIcon(uuid, username);
            String label = showNickname ? username : null;
            dots.add(new FastMiniMapPlayerLayerApi.PlayerDot(pos.x, pos.z, icon, label));
        }
        return dots;
    }

    private BufferedImage resolveIcon(UUID uuid, String username) {
        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        if (plugin == null || plugin.getAvatarService() == null) {
            return null;
        }
        return plugin.getAvatarService().resolveMinimapIcon(uuid, username);
    }
}
