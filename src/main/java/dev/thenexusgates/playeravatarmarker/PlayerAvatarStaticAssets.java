package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.universe.Universe;

final class PlayerAvatarStaticAssets {

    private static final String WORLDMAP_PLAYER_ASSET = "UI/WorldMap/Player.png";
    private static final String WORLDMAP_MARKER_PLAYER_ASSET = "UI/WorldMap/MapMarkers/Player.png";
    private static final String FALLBACK_MARKER_ASSET = "UI/WorldMap/MapMarkers/pam-placeholder.png";

    private static final byte[] TRANSPARENT_PLAYER_PNG = PlayerAvatarImageProcessor.createTransparentPng();
    private static final byte[] FALLBACK_MARKER_PNG = PlayerAvatarImageProcessor.createFallbackMarkerPng(64);

    private static volatile boolean registered;

    private PlayerAvatarStaticAssets() {}

    static void register() {
        if (registered) {
            return;
        }

        synchronized (PlayerAvatarStaticAssets.class) {
            if (registered) {
                return;
            }

            CommonAssetModule commonAssetModule = CommonAssetModule.get();
            if (commonAssetModule == null) {
                return;
            }

            registerAsset(
                    commonAssetModule,
                    WORLDMAP_PLAYER_ASSET,
                    TRANSPARENT_PLAYER_PNG);
            registerAsset(
                    commonAssetModule,
                    WORLDMAP_MARKER_PLAYER_ASSET,
                    TRANSPARENT_PLAYER_PNG);
            registerAsset(
                    commonAssetModule,
                    FALLBACK_MARKER_ASSET,
                    FALLBACK_MARKER_PNG);

            Universe universe = Universe.get();
            if (universe != null && universe.getPlayerCount() > 0) {
                universe.broadcastPacketNoCache(new RequestCommonAssetsRebuild());
            }

            registered = true;
        }
    }

    private static void registerAsset(CommonAssetModule commonAssetModule, String assetPath, byte[] pngBytes) {
        if (commonAssetModule == null || assetPath == null || assetPath.isBlank() || pngBytes == null || pngBytes.length == 0) {
            return;
        }

        commonAssetModule.addCommonAsset(
                assetPath,
                new PlayerAvatarRuntimePngAsset(assetPath, pngBytes),
                true);
    }
}