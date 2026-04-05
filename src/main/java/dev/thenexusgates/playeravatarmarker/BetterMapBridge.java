package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

final class BetterMapBridge {

    private static final Logger LOGGER = Logger.getLogger(BetterMapBridge.class.getName());

    private static volatile BridgeState bridgeState;
    private static volatile boolean unavailableLogged;

    private BetterMapBridge() {}

    static boolean isAvailable() {
        return resolveBridgeState().available;
    }

    static ViewerSettings resolveViewerSettings(Player viewer) {
        BridgeState state = resolveBridgeState();
        if (!state.available || viewer == null) {
            return ViewerSettings.disabled();
        }

        try {
            Object modConfig = state.modConfigGetInstance.invoke(null);
            if (modConfig == null) {
                return ViewerSettings.disabled();
            }

            boolean radarEnabled = (boolean) state.modConfigIsRadarEnabled.invoke(modConfig);
            if (!radarEnabled) {
                return ViewerSettings.disabled();
            }

            UUID viewerUuid = ((CommandSender) viewer).getUuid();
            Object playerConfig = null;
            if (viewerUuid != null) {
                Object playerConfigManager = state.playerConfigManagerGetInstance.invoke(null);
                if (playerConfigManager != null) {
                    playerConfig = state.playerConfigManagerGetPlayerConfig.invoke(playerConfigManager, viewerUuid);
                }
            }

            boolean hidePlayersGlobally = (boolean) state.modConfigIsHidePlayersOnMap.invoke(modConfig);
            if (hidePlayersGlobally) {
                boolean viewerOverridesHide = playerConfig != null
                        && (boolean) state.playerConfigIsOverrideGlobalPlayersHide.invoke(playerConfig)
                        && (boolean) state.permissionsUtilCanOverridePlayers.invoke(null, viewer);
                if (!viewerOverridesHide) {
                    return ViewerSettings.disabled();
                }
            }

            if (playerConfig != null && (boolean) state.playerConfigIsHidePlayersOnMap.invoke(playerConfig)) {
                return ViewerSettings.disabled();
            }

            int radarRange = (int) state.modConfigGetRadarRange.invoke(modConfig);
            return new ViewerSettings(true, radarRange);
        } catch (Exception e) {
            LOGGER.warning("[PlayerAvatarMarker] BetterMap settings bridge failed: " + e.getMessage());
            return ViewerSettings.disabled();
        }
    }

    static void injectTeleportContextMenu(MapMarker marker, Player viewer) {
        BridgeState state = resolveBridgeState();
        if (!state.available || marker == null || viewer == null) {
            return;
        }

        try {
            state.markerTeleportUtilInject.invoke(null, marker, viewer, state.markerTypePlayer);
        } catch (Exception e) {
            LOGGER.warning("[PlayerAvatarMarker] BetterMap teleport bridge failed: " + e.getMessage());
        }
    }

    private static BridgeState resolveBridgeState() {
        BridgeState current = bridgeState;
        if (current != null) {
            return current;
        }

        synchronized (BetterMapBridge.class) {
            current = bridgeState;
            if (current != null) {
                return current;
            }

            bridgeState = current = loadBridgeState();
            return current;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BridgeState loadBridgeState() {
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            Class<?> modConfigClass = loadClass("dev.ninesliced.configs.ModConfig", contextLoader);
            Class<?> playerConfigManagerClass = loadClass("dev.ninesliced.managers.PlayerConfigManager", contextLoader);
            Class<?> playerConfigClass = loadClass("dev.ninesliced.configs.PlayerConfig", contextLoader);
            Class<?> permissionsUtilClass = loadClass("dev.ninesliced.utils.PermissionsUtil", contextLoader);
            Class<?> markerTeleportUtilClass = loadClass("dev.ninesliced.utils.MarkerTeleportUtil", contextLoader);
            Class<? extends Enum> markerTypeClass = (Class<? extends Enum>) loadClass(
                    "dev.ninesliced.utils.PermissionsUtil$MarkerType", contextLoader);

            Object markerTypePlayer = Enum.valueOf(markerTypeClass, "PLAYER");

            return new BridgeState(
                    true,
                    modConfigClass.getMethod("getInstance"),
                    modConfigClass.getMethod("isRadarEnabled"),
                    modConfigClass.getMethod("isHidePlayersOnMap"),
                    modConfigClass.getMethod("getRadarRange"),
                    playerConfigManagerClass.getMethod("getInstance"),
                    playerConfigManagerClass.getMethod("getPlayerConfig", UUID.class),
                    playerConfigClass.getMethod("isOverrideGlobalPlayersHide"),
                    playerConfigClass.getMethod("isHidePlayersOnMap"),
                    permissionsUtilClass.getMethod("canOverridePlayers", Player.class),
                    markerTeleportUtilClass.getMethod("injectTeleportContextMenu", MapMarker.class, Player.class, markerTypeClass),
                    markerTypePlayer);
        } catch (Exception e) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                LOGGER.info("[PlayerAvatarMarker] BetterMap 1.3.5 compatibility bridge unavailable: " + e.getMessage());
            }
            return BridgeState.unavailable();
        }
    }

    private static Class<?> loadClass(String className, ClassLoader contextLoader) throws ClassNotFoundException {
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        try {
            return Class.forName(className, false, BetterMapBridge.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
        }

        return Class.forName(className);
    }

    record ViewerSettings(boolean enabled, int radarRange) {
        static ViewerSettings disabled() {
            return new ViewerSettings(false, 0);
        }
    }

    private record BridgeState(
            boolean available,
            Method modConfigGetInstance,
            Method modConfigIsRadarEnabled,
            Method modConfigIsHidePlayersOnMap,
            Method modConfigGetRadarRange,
            Method playerConfigManagerGetInstance,
            Method playerConfigManagerGetPlayerConfig,
            Method playerConfigIsOverrideGlobalPlayersHide,
            Method playerConfigIsHidePlayersOnMap,
            Method permissionsUtilCanOverridePlayers,
            Method markerTeleportUtilInject,
            Object markerTypePlayer) {

        static BridgeState unavailable() {
            return new BridgeState(false, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}