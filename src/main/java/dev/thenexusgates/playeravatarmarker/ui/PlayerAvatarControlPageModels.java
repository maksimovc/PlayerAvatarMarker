package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PlayerAvatarControlPageModels {

    enum SurfaceBulkState {
        ALL_ENABLED,
        ALL_DISABLED,
        MIXED,
        EMPTY
    }

    record RowModel(UUID uuid,
                    String playerName,
                    String metaText,
                    String visibilityBadge,
                    String assetPath,
                    boolean self,
                    boolean mapEnabled,
                    boolean minimapEnabled,
                    boolean compassEnabled,
                    PlayerAvatarVisibilityState visibilityState) {
    }

    record PageSlice(List<RowModel> rows, int pageIndex, int pageCount) {
    }

    private PlayerAvatarControlPageModels() {
    }

    static List<RowModel> buildRows(PlayerRef viewerRef,
                                    PlayerAvatarMarkerPlugin plugin,
                                    Runnable onAvatarReady,
                                    Runnable onPendingAssets) {
        List<PlayerRef> players = new ArrayList<>(plugin.getActivePlayers());
        if (plugin.getAvatarService() != null) {
            plugin.getAvatarService().advanceViewerDeliveryPhase(viewerRef);
        }

        UUID viewerUuid = viewerRef != null ? viewerRef.getUuid() : null;
        players.sort(Comparator
                .comparing((PlayerRef ref) -> !isSelf(ref, viewerUuid))
                .thenComparing(ref -> PlayerAvatarPlayerNames.resolveOrLocalizedUnknown(ref, viewerRef).toLowerCase(Locale.ROOT)));

        PlayerAvatarPlayerSettings settings = plugin.resolvePlayerSettings(viewerRef);
        List<RowModel> rows = new ArrayList<>(players.size());
        for (PlayerRef ref : players) {
            UUID playerUuid = ref.getUuid();
            if (playerUuid == null) {
                continue;
            }

            String playerName = PlayerAvatarPlayerNames.resolveOrLocalizedUnknown(ref, viewerRef);
            boolean self = isSelf(ref, viewerUuid);
            PlayerAvatarVisibilityDecision visibility = PlayerAvatarVisibilityService.resolve(viewerRef, viewerUuid, playerUuid);
            PlayerAvatarVisibilityState visibilityState = visibility.state();
            if (!visibility.isVisible()) {
                continue;
            }

            PlayerAvatarMarkerVisuals.AvatarVisual visual =
                    PlayerAvatarMarkerVisuals.resolveAvatarVisual(viewerRef, playerUuid, playerName, visibilityState, onAvatarReady);
            String assetPath = PlayerAvatarMarkerVisuals.toUiAssetPath(visual.markerImage());
            boolean customized = settings.hasAnyOverride(playerUuid);
            boolean mapEnabled = settings.isEnabledFor(PlayerAvatarSurface.MAP, viewerUuid, playerUuid);
            boolean minimapEnabled = settings.isEnabledFor(PlayerAvatarSurface.MINIMAP, viewerUuid, playerUuid);
            boolean compassEnabled = settings.isEnabledFor(PlayerAvatarSurface.COMPASS, viewerUuid, playerUuid);
            rows.add(new RowModel(
                    playerUuid,
                    playerName,
                    describeRowMeta(viewerRef, self, customized, visibilityState),
                    visibilityState.isGhosted() ? PlayerAvatarUiText.choose(viewerRef, "VANISH", "СКРИТІСТЬ") : "",
                    assetPath,
                    self,
                    mapEnabled,
                    minimapEnabled,
                    compassEnabled,
                    visibilityState));
        }

        if (plugin.getAvatarService() != null && plugin.getAvatarService().hasPendingAssets(viewerUuid) && onPendingAssets != null) {
            onPendingAssets.run();
        }
        return rows;
    }

    static PageSlice pageSlice(List<RowModel> rows, int requestedPage, int pageSize) {
        int total = rows.size();
        int pageCount = total == 0 ? 1 : (int) Math.ceil(total / (double) pageSize);
        int pageIndex = total == 0 ? 0 : Math.max(0, Math.min(requestedPage, pageCount - 1));
        if (total == 0) {
            return new PageSlice(List.of(), pageIndex, pageCount);
        }

        int fromIndex = pageIndex * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        return new PageSlice(rows.subList(fromIndex, toIndex), pageIndex, pageCount);
    }

    static int countEnabled(List<RowModel> rows, PlayerAvatarSurface surface, UUID viewerUuid) {
        int enabledCount = 0;
        for (RowModel row : rows) {
            if (isEnabled(row, surface, viewerUuid)) {
                enabledCount++;
            }
        }
        return enabledCount;
    }

    static SurfaceBulkState bulkState(List<RowModel> rows, PlayerAvatarSurface surface, UUID viewerUuid) {
        if (rows.isEmpty()) {
            return SurfaceBulkState.EMPTY;
        }

        int enabledCount = countEnabled(rows, surface, viewerUuid);
        int eligibleCount = countEligible(rows, surface, viewerUuid);
        if (eligibleCount == 0) {
            return SurfaceBulkState.EMPTY;
        }
        if (enabledCount == 0) {
            return SurfaceBulkState.ALL_DISABLED;
        }
        if (enabledCount == eligibleCount) {
            return SurfaceBulkState.ALL_ENABLED;
        }
        return SurfaceBulkState.MIXED;
    }

    static int countEligible(List<RowModel> rows, PlayerAvatarSurface surface, UUID viewerUuid) {
        int eligibleCount = 0;
        for (RowModel row : rows) {
            if (isEligible(row, surface, viewerUuid)) {
                eligibleCount++;
            }
        }
        return eligibleCount;
    }

    static boolean isEnabled(RowModel row, PlayerAvatarSurface surface, UUID viewerUuid) {
        if (!isEligible(row, surface, viewerUuid)) {
            return false;
        }
        return switch (surface) {
            case MAP -> row.mapEnabled();
            case MINIMAP -> row.minimapEnabled();
            case COMPASS -> row.compassEnabled();
        };
    }

    static boolean isEligible(RowModel row, PlayerAvatarSurface surface, UUID viewerUuid) {
        return row != null && !PlayerAvatarPlayerSettings.isSelfForcedHidden(surface, viewerUuid, row.uuid());
    }

    private static boolean isSelf(PlayerRef ref, UUID viewerUuid) {
        return ref != null && viewerUuid != null && viewerUuid.equals(ref.getUuid());
    }

    private static String describeRowMeta(PlayerRef viewerRef,
                                          boolean self,
                                          boolean customized,
                                          PlayerAvatarVisibilityState visibilityState) {
        if (visibilityState != null && visibilityState.isGhosted()) {
            if (self) {
                return PlayerAvatarUiText.choose(viewerRef, "You are in hidden mode", "Ви у режимі скритності");
            }
            return PlayerAvatarUiText.choose(viewerRef, "Visible to you as hidden", "Показується вам як прихований");
        }

        if (self) {
            return customized
                    ? PlayerAvatarUiText.choose(viewerRef, "You · Map only", "Ви · лише мапа")
                    : PlayerAvatarUiText.choose(viewerRef, "You · Hidden on minimap and compass", "Ви · приховано на мінімапі та компасі");
        }

        return customized
                ? PlayerAvatarUiText.choose(viewerRef, "Custom visibility", "Індивідуальна видимість")
                : PlayerAvatarUiText.choose(viewerRef, "Uses bulk defaults", "Використовує загальні налаштування");
    }
}


