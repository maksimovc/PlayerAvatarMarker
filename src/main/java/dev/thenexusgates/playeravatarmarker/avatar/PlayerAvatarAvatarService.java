package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarAvatarService {

    private static final String MAP_MARKER_ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final String FALLBACK_MARKER_IMAGE = "pam-placeholder.png";
    private static final int MINIMAP_AVATAR_SIZE = 22;
    private static final int MIN_MARKER_AVATAR_SIZE = 16;
    private static final float GHOSTED_ALPHA = 0.5f;

    private final ConcurrentHashMap<UUID, AvatarBundle> avatarBundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<AvatarBundle>> pendingAvatarBundles = new ConcurrentHashMap<>();
    private final PlayerAvatarViewerAssetDelivery viewerAssetDelivery = new PlayerAvatarViewerAssetDelivery();
    private final BufferedImage fallbackIcon;
    private final BufferedImage fallbackGhostedIcon;

    PlayerAvatarAvatarService(Path dataRoot) {
        PlayerAvatarCache.configure(dataRoot);
        this.fallbackIcon = decodeImage(PlayerAvatarImageProcessor.createFallbackMarkerPng(MINIMAP_AVATAR_SIZE));
        this.fallbackGhostedIcon = decodeImage(PlayerAvatarImageProcessor.applyOpacity(
                PlayerAvatarImageProcessor.createFallbackMarkerPng(MINIMAP_AVATAR_SIZE),
                GHOSTED_ALPHA,
                true));
    }

    void clearViewer(UUID viewerUuid) {
        viewerAssetDelivery.clearViewer(viewerUuid);
    }

    boolean hasPendingAssets(UUID viewerUuid) {
        return viewerAssetDelivery.hasPendingAssets(viewerUuid);
    }

    boolean advanceViewerDeliveryPhase(PlayerRef viewer) {
        return viewerAssetDelivery.advanceViewerDeliveryPhase(viewer);
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
                                 boolean ghosted,
                                 Runnable onReady) {
        if (subjectId == null || username == null || username.isBlank()) {
            return FALLBACK_MARKER_IMAGE;
        }

        CompletableFuture<AvatarBundle> future = resolveAvatarBundle(subjectId, username);
        AvatarBundle bundle = resolveCompletedBundle(future, onReady);
        if (bundle == null) {
            return FALLBACK_MARKER_IMAGE;
        }

        String markerImagePath = ghosted ? bundle.ghostedMarkerImagePath() : bundle.markerImagePath();
        byte[] markerPng = ghosted ? bundle.ghostedMarkerPng() : bundle.markerPng();
        String assetPath = toUiAssetPath(markerImagePath);
        if (viewer != null && assetPath != null) {
            viewerAssetDelivery.deliver(viewer, assetPath, markerPng);
        }
        PlayerAvatarAssetPack.writeAvatar(markerImagePath, markerPng);
        return markerImagePath;
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

    BufferedImage resolveMinimapIcon(UUID subjectId, String username, boolean ghosted) {
        if (subjectId == null || username == null || username.isBlank()) {
            return ghosted ? fallbackGhostedIcon : fallbackIcon;
        }

        AvatarBundle cached = avatarBundles.get(subjectId);
        if (cached != null) {
            BufferedImage cachedIcon = ghosted ? cached.ghostedIcon() : cached.icon();
            if (cachedIcon != null) {
                return cachedIcon;
            }
        }

        CompletableFuture<AvatarBundle> future = resolveAvatarBundle(subjectId, username);
        if (future.isDone() && !future.isCompletedExceptionally()) {
            AvatarBundle bundle = future.getNow(null);
            if (bundle != null) {
                BufferedImage readyIcon = ghosted ? bundle.ghostedIcon() : bundle.icon();
                if (readyIcon != null) {
                    return readyIcon;
                }
            }
        }
        return ghosted ? fallbackGhostedIcon : fallbackIcon;
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
                        PlayerAvatarAssetPack.writeAvatar(bundle.ghostedMarkerImagePath(), bundle.ghostedMarkerPng());
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
        int markerSize = Math.max(MIN_MARKER_AVATAR_SIZE, config != null ? config.avatarSize : 64);
        byte[] markerBytes = PlayerAvatarImageProcessor.process(
                rawBytes,
                markerSize,
                config != null ? config.backgroundColorRGB() : 0x2D2D2D,
                config == null || config.enableBackground);
        if (markerBytes == null || markerBytes.length == 0) {
            return null;
        }

        byte[] ghostedMarkerBytes = PlayerAvatarImageProcessor.applyOpacity(markerBytes, GHOSTED_ALPHA, true);
        if (ghostedMarkerBytes == null || ghostedMarkerBytes.length == 0) {
            ghostedMarkerBytes = markerBytes;
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

        byte[] ghostedMinimapBytes = PlayerAvatarImageProcessor.applyOpacity(minimapBytes, GHOSTED_ALPHA, true);
        BufferedImage ghostedMinimapIcon = decodeImage(ghostedMinimapBytes);
        if (ghostedMinimapIcon == null) {
            ghostedMinimapIcon = fallbackGhostedIcon != null ? fallbackGhostedIcon : minimapIcon;
        }

        String markerImagePath = buildMarkerImagePath(subjectId, markerBytes, "normal");
        String ghostedMarkerImagePath = buildMarkerImagePath(subjectId, ghostedMarkerBytes, "ghosted");
        if (markerImagePath == null || markerImagePath.isBlank() || ghostedMarkerImagePath == null || ghostedMarkerImagePath.isBlank()) {
            return null;
        }

        return new AvatarBundle(markerImagePath, markerBytes, minimapIcon, ghostedMarkerImagePath, ghostedMarkerBytes, ghostedMinimapIcon);
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

    private static String buildMarkerImagePath(UUID subjectId, byte[] markerBytes, String variantToken) {
        if (subjectId == null || markerBytes == null || markerBytes.length == 0) {
            return null;
        }

        String uuidToken = subjectId.toString().replace("-", "");
        String contentKey = computeContentKey(markerBytes);
        String variant = variantToken == null || variantToken.isBlank() ? "default" : variantToken;
        return "pam-" + uuidToken + "-" + variant + "-" + contentKey.substring(0, Math.min(16, contentKey.length())) + ".png";
    }

    private static String computeContentKey(byte[] pngBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(pngBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }


    private record AvatarBundle(String markerImagePath,
                                byte[] markerPng,
                                BufferedImage icon,
                                String ghostedMarkerImagePath,
                                byte[] ghostedMarkerPng,
                                BufferedImage ghostedIcon) {
    }
}