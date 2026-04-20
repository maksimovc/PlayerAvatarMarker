package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarAvatarService {

    private static final String MAP_MARKER_ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final String FALLBACK_MARKER_IMAGE = "pam-placeholder.png";
    private static final int MINIMAP_AVATAR_SIZE = 22;
    private static final int MIN_MARKER_AVATAR_SIZE = 16;

    private final ConcurrentHashMap<UUID, AvatarBundle> avatarBundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<AvatarBundle>> pendingAvatarBundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ViewerDeliveryState> viewerStates = new ConcurrentHashMap<>();
    private final BufferedImage fallbackIcon;

    PlayerAvatarAvatarService(Path dataRoot) {
        PlayerAvatarCache.configure(dataRoot);
        this.fallbackIcon = decodeImage(PlayerAvatarImageProcessor.createFallbackMarkerPng(MINIMAP_AVATAR_SIZE));
    }

    void clearViewer(UUID viewerUuid) {
        if (viewerUuid != null) {
            viewerStates.remove(viewerUuid);
        }
    }

    boolean hasPendingAssets(UUID viewerUuid) {
        if (viewerUuid == null) {
            return false;
        }

        ViewerDeliveryState state = viewerStates.get(viewerUuid);
        return state != null && state.hasPendingAssets();
    }

    boolean advanceViewerDeliveryPhase(PlayerRef viewer) {
        if (viewer == null) {
            return false;
        }

        UUID viewerUuid = viewer.getUuid();
        if (viewerUuid == null) {
            return false;
        }

        ViewerDeliveryState state = viewerStates.get(viewerUuid);
        if (state == null) {
            return false;
        }

        boolean nextBatchReady = state.advancePhase();
        if (!nextBatchReady) {
            return false;
        }

        try {
            var packetHandler = viewer.getPacketHandler();
            if (packetHandler == null) {
                state.discardPendingBatches();
                return false;
            }
            packetHandler.writeNoCache(new RequestCommonAssetsRebuild());
            return true;
        } catch (RuntimeException exception) {
            state.discardPendingBatches();
            return false;
        }
    }

    void prefetch(UUID subjectId, String username) {
        if (subjectId == null || username == null || username.isBlank()) {
            return;
        }
        resolveAvatarBundle(subjectId, username);
    }

    String ensureAvatarForViewer(PlayerRef viewer,
                                 UUID subjectId,
                                 String username,
                                 Runnable onReady) {
        if (subjectId == null || username == null || username.isBlank()) {
            return FALLBACK_MARKER_IMAGE;
        }

        CompletableFuture<AvatarBundle> future = resolveAvatarBundle(subjectId, username);
        AvatarBundle bundle = resolveCompletedBundle(future, onReady);
        if (bundle == null) {
            return FALLBACK_MARKER_IMAGE;
        }

        PlayerAvatarAssetPack.writeAvatar(bundle.markerImagePath(), bundle.markerPng());
        if (viewer == null) {
            return bundle.markerImagePath();
        }

        UUID viewerUuid = viewer.getUuid();
        String assetPath = toUiAssetPath(bundle.markerImagePath());
        if (viewerUuid == null || assetPath == null || assetPath.isBlank()) {
            return bundle.markerImagePath();
        }

        ViewerDeliveryState deliveryState = viewerStates.computeIfAbsent(viewerUuid, ignored -> new ViewerDeliveryState());
        if (deliveryState.isDelivered(assetPath)) {
            return bundle.markerImagePath();
        }

        DeliveryReservation reservation = deliveryState.reserve(Set.of(assetPath));
        if (reservation.assetPaths().isEmpty()) {
            return bundle.markerImagePath();
        }

        boolean delivered = PlayerAvatarAssetPublisher.deliver(
                viewer,
                assetPath,
                bundle.markerPng(),
                reservation.rebuildRequired());
        if (!delivered) {
            deliveryState.discardPendingBatches();
            return bundle.markerImagePath();
        }

        return bundle.markerImagePath();
    }

    static String toUiAssetPath(String markerImage) {
        if (markerImage == null || markerImage.isBlank()) {
            return null;
        }

        if (markerImage.contains("/")) {
            return markerImage;
        }

        return MAP_MARKER_ASSET_PREFIX + markerImage;
    }

    static boolean isFallbackMarkerImage(String markerImage) {
        return markerImage == null || markerImage.isBlank() || FALLBACK_MARKER_IMAGE.equals(markerImage);
    }

    BufferedImage resolveMinimapIcon(UUID subjectId, String username) {
        if (subjectId == null || username == null || username.isBlank()) {
            return fallbackIcon;
        }

        AvatarBundle cached = avatarBundles.get(subjectId);
        if (cached != null && cached.icon() != null) {
            return cached.icon();
        }

        CompletableFuture<AvatarBundle> future = resolveAvatarBundle(subjectId, username);
        if (future.isDone() && !future.isCompletedExceptionally()) {
            AvatarBundle bundle = future.getNow(null);
            if (bundle != null && bundle.icon() != null) {
                return bundle.icon();
            }
        }
        return fallbackIcon;
    }

    private CompletableFuture<AvatarBundle> resolveAvatarBundle(UUID subjectId, String username) {
        AvatarBundle cached = avatarBundles.get(subjectId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return pendingAvatarBundles.computeIfAbsent(subjectId, ignored -> PlayerAvatarCache.getOrFetch(subjectId, username)
                .thenApply(bytes -> buildAvatarBundle(subjectId, bytes))
                .thenApply(bundle -> {
                    if (bundle != null) {
                        avatarBundles.put(subjectId, bundle);
                        PlayerAvatarAssetPack.writeAvatar(bundle.markerImagePath(), bundle.markerPng());
                    }
                    return bundle;
                })
                .whenComplete((bundle, error) -> pendingAvatarBundles.remove(subjectId)));
    }

    private AvatarBundle buildAvatarBundle(UUID subjectId, byte[] rawBytes) {
        if (subjectId == null || rawBytes == null || rawBytes.length == 0) {
            return null;
        }

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        int markerSize = Math.max(MIN_MARKER_AVATAR_SIZE, PlayerAvatarMarkerSupport.getAvatarSize(config));
        byte[] markerBytes = PlayerAvatarImageProcessor.process(
                rawBytes,
                markerSize,
                config != null ? config.backgroundColorRGB() : 0x2D2D2D,
                config == null || config.enableBackground);
        if (markerBytes == null || markerBytes.length == 0) {
            return null;
        }

        byte[] minimapBytes = PlayerAvatarImageProcessor.process(
                rawBytes,
                MINIMAP_AVATAR_SIZE,
                config != null ? config.backgroundColorRGB() : 0x2D2D2D,
                config == null || config.enableBackground);
        BufferedImage minimapIcon = decodeImage(minimapBytes);
        if (minimapIcon == null) {
            minimapIcon = fallbackIcon;
        }

        String markerImagePath = buildMarkerImagePath(subjectId, markerBytes);
        if (markerImagePath == null || markerImagePath.isBlank()) {
            return null;
        }

        return new AvatarBundle(markerImagePath, markerBytes, minimapIcon);
    }

    private static BufferedImage decodeImage(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) {
            return null;
        }

        try {
            return ImageIO.read(new ByteArrayInputStream(pngBytes));
        } catch (IOException exception) {
            return null;
        }
    }

    private AvatarBundle resolveCompletedBundle(CompletableFuture<AvatarBundle> future, Runnable onReady) {
        if (future == null) {
            return null;
        }

        if (future.isDone() && !future.isCompletedExceptionally()) {
            return future.getNow(null);
        }

        if (onReady != null) {
            future.whenComplete((bundle, error) -> {
                if (error == null && bundle != null) {
                    onReady.run();
                }
            });
        }
        return null;
    }

    private static String buildMarkerImagePath(UUID subjectId, byte[] markerBytes) {
        if (subjectId == null || markerBytes == null || markerBytes.length == 0) {
            return null;
        }

        String uuidToken = subjectId.toString().replace("-", "");
        String contentKey = computeContentKey(markerBytes);
        return "pam-" + uuidToken + "-" + contentKey.substring(0, Math.min(16, contentKey.length())) + ".png";
    }

    private static String computeContentKey(byte[] pngBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(pngBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record DeliveryReservation(Set<String> assetPaths, boolean rebuildRequired) {
    }

    private static final class ViewerDeliveryState {

        private final Set<String> deliveredAssets = ConcurrentHashMap.newKeySet();
        private final LinkedHashMap<String, Boolean> activationBatch = new LinkedHashMap<>();
        private final LinkedHashMap<String, Boolean> currentBatch = new LinkedHashMap<>();
        private final LinkedHashMap<String, Boolean> queuedBatch = new LinkedHashMap<>();

        private synchronized DeliveryReservation reserve(Collection<String> assetPaths) {
            LinkedHashMap<String, Boolean> targetBatch = currentBatch.isEmpty() ? currentBatch : queuedBatch;
            boolean rebuildRequired = targetBatch == currentBatch;
            LinkedHashMap<String, Boolean> reserved = new LinkedHashMap<>();
            for (String assetPath : assetPaths) {
                if (assetPath == null
                        || deliveredAssets.contains(assetPath)
                        || activationBatch.containsKey(assetPath)
                        || currentBatch.containsKey(assetPath)
                        || queuedBatch.containsKey(assetPath)) {
                    continue;
                }

                targetBatch.put(assetPath, Boolean.TRUE);
                reserved.put(assetPath, Boolean.TRUE);
            }

            return new DeliveryReservation(Set.copyOf(reserved.keySet()), rebuildRequired && !reserved.isEmpty());
        }

        private synchronized boolean isDelivered(String assetPath) {
            return deliveredAssets.contains(assetPath);
        }

        private synchronized boolean hasPendingAssets() {
            return !activationBatch.isEmpty() || !currentBatch.isEmpty() || !queuedBatch.isEmpty();
        }

        private synchronized boolean advancePhase() {
            if (!activationBatch.isEmpty()) {
                deliveredAssets.addAll(activationBatch.keySet());
                activationBatch.clear();
            }

            if (currentBatch.isEmpty()) {
                return false;
            }

            activationBatch.putAll(currentBatch);
            currentBatch.clear();
            if (queuedBatch.isEmpty()) {
                return false;
            }

            currentBatch.putAll(queuedBatch);
            queuedBatch.clear();
            return true;
        }

        private synchronized void discardPendingBatches() {
            currentBatch.clear();
            queuedBatch.clear();
        }
    }

    private record AvatarBundle(String markerImagePath, byte[] markerPng, BufferedImage icon) {
    }
}