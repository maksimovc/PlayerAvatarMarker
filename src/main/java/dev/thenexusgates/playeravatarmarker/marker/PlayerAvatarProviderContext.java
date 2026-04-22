package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;

record PlayerAvatarProviderContext(PlayerAvatarMarkerPlugin plugin,
                                   PlayerAvatarConfig config,
                                   PlayerRef viewerRef,
                                   UUID viewerUuid,
                                   PlayerAvatarPlayerSettings viewerSettings,
                                   PlayerAvatarSurface surface) {

    static PlayerAvatarProviderContext resolve(Player viewer) {
        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        UUID viewerUuid = viewer != null ? ((CommandSender) viewer).getUuid() : null;
        PlayerRef viewerRef = plugin != null ? plugin.getActivePlayerRef(viewerUuid) : null;
        if (plugin != null && plugin.getAvatarService() != null && viewerRef != null) {
            plugin.getAvatarService().advanceViewerDeliveryPhase(viewerRef);
        }

        boolean worldMapVisible = PlayerAvatarWorldMapState.isWorldMapVisible(viewer);
        PlayerAvatarSurface surface = worldMapVisible ? PlayerAvatarSurface.MAP : PlayerAvatarSurface.COMPASS;
        PlayerAvatarPlayerSettings viewerSettings = plugin != null
                ? plugin.resolvePlayerSettings(viewerUuid)
                : new PlayerAvatarPlayerSettings();
        return new PlayerAvatarProviderContext(plugin, config, viewerRef, viewerUuid, viewerSettings, surface);
    }

    boolean isSurfaceEnabled() {
        return viewerSettings.isEnabled(surface);
    }

    boolean isViewer(UUID targetUuid) {
        return viewerUuid != null && viewerUuid.equals(targetUuid);
    }

    boolean isTargetEnabled(UUID targetUuid, PlayerAvatarVisibilityDecision visibility) {
        boolean isViewer = isViewer(targetUuid);
        return surface == PlayerAvatarSurface.MAP && isViewer && visibility != null && visibility.isGhosted()
                ? viewerSettings.isEnabled(surface)
                : viewerSettings.isEnabledFor(surface, viewerUuid, targetUuid);
    }
}

