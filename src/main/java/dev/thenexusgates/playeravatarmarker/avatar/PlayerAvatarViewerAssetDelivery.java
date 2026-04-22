package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarViewerAssetDelivery {

    private final ConcurrentHashMap<UUID, ViewerDeliveryState> viewerStates = new ConcurrentHashMap<>();

    void clearViewer(UUID viewerUuid) {
        if (viewerUuid != null) {
            viewerStates.remove(viewerUuid);
        }
    }

    boolean hasPendingAssets(UUID viewerUuid) {
        return false;
    }

    boolean advanceViewerDeliveryPhase(PlayerRef viewer) {
        return false;
    }

    boolean deliver(PlayerRef viewer, String assetPath, byte[] pngBytes) {
        if (viewer == null || pngBytes == null || pngBytes.length == 0 || assetPath == null || assetPath.isBlank()) {
            return false;
        }

        UUID viewerUuid = viewer.getUuid();
        if (viewerUuid == null) {
            return false;
        }

        ViewerDeliveryState deliveryState = viewerStates.computeIfAbsent(viewerUuid, ignored -> new ViewerDeliveryState());
        if (deliveryState.isDelivered(assetPath)) {
            return true;
        }

        if (!deliveryState.reserve(assetPath)) {
            return true;
        }

        boolean delivered = PlayerAvatarAssetPublisher.deliver(viewer, assetPath, pngBytes, true);
        if (!delivered) {
            deliveryState.unreserve(assetPath);
            return false;
        }
        deliveryState.markDelivered(assetPath);
        return true;
    }

    private static final class ViewerDeliveryState {

        private final Set<String> deliveredAssets = ConcurrentHashMap.newKeySet();
        private final Set<String> reservedAssets = ConcurrentHashMap.newKeySet();

        private boolean reserve(String assetPath) {
            return assetPath != null
                    && !deliveredAssets.contains(assetPath)
                    && reservedAssets.add(assetPath);
        }

        private boolean isDelivered(String assetPath) {
            return deliveredAssets.contains(assetPath);
        }

        private void unreserve(String assetPath) {
            if (assetPath != null) {
                reservedAssets.remove(assetPath);
            }
        }

        private void markDelivered(String assetPath) {
            if (assetPath == null) {
                return;
            }
            reservedAssets.remove(assetPath);
            deliveredAssets.add(assetPath);
        }
    }
}

