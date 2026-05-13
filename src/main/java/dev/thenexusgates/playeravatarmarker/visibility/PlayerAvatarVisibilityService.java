package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class PlayerAvatarVisibilityService {

    private static final String HYESSENTIALSX_VANISH_FILTER_CLASS =
            "xyz.thelegacyvoyage.hyessentialsx.util.MapVisibilityUtil$VanishMapFilter";
    private static final long HIDDEN_BY_OTHERS_CACHE_TTL_MS = 100L;
    private static final ConcurrentHashMap<UUID, CacheEntry> hiddenByOthersCache = new ConcurrentHashMap<>();

    private PlayerAvatarVisibilityService() {
    }

    static void invalidateCachedState() {
        hiddenByOthersCache.clear();
        PlayerAvatarVanishProviders.invalidateCachedState();
    }

    static VisibilityLookup createLookup(PlayerRef viewerRef, UUID viewerUuid, MarkersCollector collector) {
        Predicate<PlayerRef> playerMapFilter = collector != null ? collector.getPlayerMapFilter() : null;
        return new VisibilityLookup(
                viewerUuid,
                viewerRef != null ? viewerRef.getHiddenPlayersManager() : null,
                playerMapFilter,
                isHyEssentialsXVanishFilter(playerMapFilter),
                isVanished(viewerUuid));
    }

    static PlayerAvatarVisibilityDecision resolve(PlayerRef viewerRef, UUID viewerUuid, UUID targetUuid) {
        return createLookup(viewerRef, viewerUuid, null).resolve(targetUuid);
    }

    static PlayerAvatarVisibilityDecision resolve(PlayerRef viewerRef,
                                                  UUID viewerUuid,
                                                  UUID targetUuid,
                                                  boolean hiddenByCollector,
                                                  boolean hiddenByVanishCollector) {
        HiddenPlayersManager viewerHiddenPlayersManager = viewerRef != null ? viewerRef.getHiddenPlayersManager() : null;
        return resolveInternal(
                viewerUuid,
                isVanished(viewerUuid),
                viewerHiddenPlayersManager,
                targetUuid,
                hiddenByCollector,
                hiddenByVanishCollector);
    }

    private static PlayerAvatarVisibilityDecision resolveInternal(UUID viewerUuid,
                                                                  boolean viewerVanished,
                                                                  HiddenPlayersManager viewerHiddenPlayersManager,
                                                                  UUID targetUuid,
                                                                  boolean hiddenByCollector,
                                                                  boolean hiddenByVanishCollector) {
        if (targetUuid == null) {
            return new PlayerAvatarVisibilityDecision(
                    PlayerAvatarVisibilityState.HIDDEN,
                    false,
                    false,
                    false,
                    false,
                    hiddenByCollector,
                    hiddenByVanishCollector);
        }

        boolean self = viewerUuid != null && viewerUuid.equals(targetUuid);
        boolean targetVanished = isVanished(targetUuid);
                boolean hiddenByViewerManager = isHiddenByViewer(viewerHiddenPlayersManager, targetUuid);

                return PlayerAvatarVisibilityResolver.resolve(new PlayerAvatarVisibilityInputs(
                self,
                viewerVanished,
                targetVanished,
                hiddenByViewerManager,
                hiddenByCollector,
                hiddenByVanishCollector));
    }


    static boolean isHiddenByCollectorFilter(MarkersCollector collector, PlayerRef targetRef) {
        if (collector == null || targetRef == null) {
            return false;
        }

        Predicate<PlayerRef> playerMapFilter = collector.getPlayerMapFilter();
        return playerMapFilter != null && playerMapFilter.test(targetRef);
    }

    static boolean isHiddenByHyEssentialsXVanishCollector(MarkersCollector collector, PlayerRef targetRef) {
        if (collector == null || targetRef == null) {
            return false;
        }

        Predicate<PlayerRef> playerMapFilter = collector.getPlayerMapFilter();
        return playerMapFilter != null
                && isHyEssentialsXVanishFilter(playerMapFilter)
                && playerMapFilter.test(targetRef);
    }

    private static boolean isHiddenByViewer(HiddenPlayersManager hiddenPlayersManager, UUID targetUuid) {
        if (hiddenPlayersManager == null || targetUuid == null) {
            return false;
        }

        return hiddenPlayersManager.isPlayerHidden(targetUuid);
    }

    private static boolean isVanished(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        if (PlayerAvatarVanishProviders.isVanished(playerUuid)) {
            return true;
        }

        return isHiddenByOtherPlayers(playerUuid);
    }

    private static boolean isHiddenByOtherPlayers(UUID targetUuid) {
        if (targetUuid == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = hiddenByOthersCache.get(targetUuid);
        if (cached != null && cached.expiresAtMs() >= now) {
            return cached.hidden();
        }

        boolean hidden = computeHiddenByOtherPlayers(targetUuid);
        hiddenByOthersCache.put(targetUuid, new CacheEntry(hidden, now + HIDDEN_BY_OTHERS_CACHE_TTL_MS));
        return hidden;
    }

    private static boolean computeHiddenByOtherPlayers(UUID targetUuid) {
        if (targetUuid == null) {
            return false;
        }

        PlayerAvatarMarkerPlugin plugin = PlayerAvatarMarkerPlugin.getInstance();
        Collection<PlayerRef> players = plugin != null ? plugin.getActivePlayers() : null;
        if (players == null || players.isEmpty()) {
            return false;
        }

        for (PlayerRef observerRef : players) {
            if (observerRef == null || targetUuid.equals(observerRef.getUuid())) {
                continue;
            }

            HiddenPlayersManager hiddenPlayersManager = observerRef.getHiddenPlayersManager();
            if (hiddenPlayersManager != null && hiddenPlayersManager.isPlayerHidden(targetUuid)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isHyEssentialsXVanishFilter(Predicate<PlayerRef> playerMapFilter) {
        if (playerMapFilter == null) {
            return false;
        }

        for (Class<?> type = playerMapFilter.getClass(); type != null; type = type.getSuperclass()) {
            if (HYESSENTIALSX_VANISH_FILTER_CLASS.equals(type.getName())) {
                return true;
            }
        }

        String className = playerMapFilter.getClass().getName();
        return className != null
                && className.startsWith(HYESSENTIALSX_VANISH_FILTER_CLASS + "$");
    }

    static final class VisibilityLookup {

        private final UUID viewerUuid;
        private final HiddenPlayersManager viewerHiddenPlayersManager;
        private final Predicate<PlayerRef> playerMapFilter;
        private final boolean hyEssentialsXVanishFilter;
        private final boolean viewerVanished;

        private VisibilityLookup(UUID viewerUuid,
                                 HiddenPlayersManager viewerHiddenPlayersManager,
                                 Predicate<PlayerRef> playerMapFilter,
                                 boolean hyEssentialsXVanishFilter,
                                 boolean viewerVanished) {
            this.viewerUuid = viewerUuid;
            this.viewerHiddenPlayersManager = viewerHiddenPlayersManager;
            this.playerMapFilter = playerMapFilter;
            this.hyEssentialsXVanishFilter = hyEssentialsXVanishFilter;
            this.viewerVanished = viewerVanished;
        }

        PlayerAvatarVisibilityDecision resolve(PlayerRef targetRef) {
            UUID targetUuid = targetRef != null ? targetRef.getUuid() : null;
            boolean hiddenByCollector = playerMapFilter != null && targetRef != null && playerMapFilter.test(targetRef);
            return resolveInternal(
                    viewerUuid,
                    viewerVanished,
                    viewerHiddenPlayersManager,
                    targetUuid,
                    hiddenByCollector,
                    hiddenByCollector && hyEssentialsXVanishFilter);
        }

        PlayerAvatarVisibilityDecision resolve(UUID targetUuid) {
            return resolveInternal(
                    viewerUuid,
                    viewerVanished,
                    viewerHiddenPlayersManager,
                    targetUuid,
                    false,
                    false);
        }
    }

    private record CacheEntry(boolean hidden, long expiresAtMs) {
    }
}

