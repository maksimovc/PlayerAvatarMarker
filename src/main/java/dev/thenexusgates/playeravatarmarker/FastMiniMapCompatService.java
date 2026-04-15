package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.thenexusgates.fastminimap.FastMiniMapPlayerLayerApi;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class FastMiniMapCompatService {

    private static final Logger LOGGER = Logger.getLogger(FastMiniMapCompatService.class.getName());

    /** Rendered size of player avatar icons on the minimap in pixels. */
    private static final int MINIMAP_AVATAR_SIZE = 22;

    /**
     * Per-UUID cache of processed {@link BufferedImage} icons.
     * Entries are added when the async avatar download completes and are removed
     * via {@link #invalidatePlayer(UUID)} when the player disconnects.
     * No permanent NO_ICON sentinel — each tick retries until the download succeeds.
     */
    private final ConcurrentHashMap<UUID, BufferedImage> avatarCache = new ConcurrentHashMap<>();

    /** Lazily initialized fallback icon (grey silhouette circle). */
    private static volatile BufferedImage fallbackIcon;

    FastMiniMapCompatService() {}

    void register() {
        FastMiniMapPlayerLayerApi.setProvider(this::getDots);
        LOGGER.info("[PlayerAvatarMarker] FastMiniMap player overlay registered.");
    }

    void unregister() {
        FastMiniMapPlayerLayerApi.setProvider(null);
        LOGGER.info("[PlayerAvatarMarker] FastMiniMap player overlay unregistered.");
    }

    /** Called on player disconnect to free the cached icon for that UUID. */
    void invalidatePlayer(UUID uuid) {
        if (uuid != null) {
            avatarCache.remove(uuid);
        }
    }

    // -------------------------------------------------------------------------
    // PlayerDotProvider impl
    // -------------------------------------------------------------------------

    private List<FastMiniMapPlayerLayerApi.PlayerDot> getDots(
            String worldName, UUID viewerUuid,
            double viewerX, double viewerZ, int radiusBlocks) {

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

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs == null || playerRefs.isEmpty()) {
            return List.of();
        }

        double radiusSq = radiusBlocks <= 0
                ? Double.POSITIVE_INFINITY
                : (double) radiusBlocks * radiusBlocks;

        PlayerAvatarConfig config = PlayerAvatarMarkerPlugin.getConfig();
        boolean showNickname = config == null || config.showNickname;

        List<FastMiniMapPlayerLayerApi.PlayerDot> dots = new ArrayList<>();
        for (PlayerRef ref : playerRefs) {
            UUID uuid = ref.getUuid();
            if (uuid == null || uuid.equals(viewerUuid)) {
                continue; // skip self
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

            BufferedImage icon = resolveIcon(uuid, username, config);
            String label = showNickname ? username : null;
            dots.add(new FastMiniMapPlayerLayerApi.PlayerDot(pos.x, pos.z, icon, label));
        }
        return dots;
    }

    // -------------------------------------------------------------------------
    // Icon resolution
    // -------------------------------------------------------------------------

    private BufferedImage resolveIcon(UUID uuid, String username, PlayerAvatarConfig config) {
        BufferedImage cached = avatarCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<byte[]> future = PlayerAvatarCache.getOrFetch(uuid, username);
        if (future.isDone() && !future.isCompletedExceptionally()) {
            byte[] rawBytes = future.getNow(null);
            if (rawBytes != null && rawBytes.length > 0) {
                int bgColorRGB = config != null ? config.backgroundColorRGB() : 0x2D2D2D;
                boolean enableBg = config == null || config.enableBackground;
                byte[] processedBytes = PlayerAvatarImageProcessor.process(
                        rawBytes, MINIMAP_AVATAR_SIZE, bgColorRGB, enableBg);
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(processedBytes));
                    if (img != null) {
                        avatarCache.put(uuid, img);
                        return img;
                    }
                } catch (IOException ignored) {
                    // fall through to fallback
                }
            }
        }

        return getFallbackIcon();
    }

    private static BufferedImage getFallbackIcon() {
        BufferedImage icon = fallbackIcon;
        if (icon == null) {
            byte[] bytes = PlayerAvatarImageProcessor.createFallbackMarkerPng(MINIMAP_AVATAR_SIZE);
            try {
                icon = ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (IOException ignored) {
                // Return null only if ImageIO fails; caller handles null gracefully
            }
            fallbackIcon = icon;
        }
        return icon;
    }
}
