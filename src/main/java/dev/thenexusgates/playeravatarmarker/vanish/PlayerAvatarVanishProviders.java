package dev.thenexusgates.playeravatarmarker;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarVanishProviders {

    private static final long VANISH_CACHE_TTL_MS = 100L;
    private static final long ACTIVE_PROVIDER_CACHE_TTL_MS = 5000L;
    private static final long INACTIVE_PROVIDER_CACHE_TTL_MS = 15000L;
    private static final List<PlayerAvatarVanishProvider> PROVIDERS = List.of(
            new PlayerAvatarEliteEssentialsVanishProvider(),
            new PlayerAvatarEssentialsPlusVanishProvider(),
            new PlayerAvatarHyEssentialsXVanishProvider());

    private static final ConcurrentHashMap<UUID, CacheEntry> vanishCache = new ConcurrentHashMap<>();
    private static volatile ProviderState providerState = new ProviderState(List.of(), 0L);

    private PlayerAvatarVanishProviders() {}

    static void invalidateCachedState() {
        vanishCache.clear();
        PlayerAvatarVanishReflection.clearCaches();
        providerState = new ProviderState(List.of(), 0L);
    }

    static boolean hasActiveProvider() {
        long now = System.currentTimeMillis();
        return !resolveProviderState(now).activeProviders().isEmpty();
    }

    private static ProviderState resolveProviderState(long now) {
        ProviderState cached = providerState;
        if (cached.expiresAtMs() >= now) {
            return cached;
        }

        List<PlayerAvatarVanishProvider> activeProviders = new java.util.ArrayList<>(PROVIDERS.size());
        for (PlayerAvatarVanishProvider provider : PROVIDERS) {
            if (provider == null) {
                continue;
            }
            try {
                if (provider.isAvailable()) {
                    activeProviders.add(provider);
                }
            } catch (Throwable ignored) {
            }
        }

        long ttlMs = activeProviders.isEmpty()
                ? INACTIVE_PROVIDER_CACHE_TTL_MS
                : ACTIVE_PROVIDER_CACHE_TTL_MS;
        ProviderState resolved = new ProviderState(List.copyOf(activeProviders), now + ttlMs);
        providerState = resolved;
        return resolved;
    }

    static boolean isVanished(UUID playerUuid) {
        long now = System.currentTimeMillis();
        ProviderState providers = resolveProviderState(now);
        if (playerUuid == null || providers.activeProviders().isEmpty()) {
            return false;
        }

        CacheEntry cached = vanishCache.get(playerUuid);
        if (cached != null && cached.expiresAtMs() >= now) {
            return cached.vanished();
        }

        boolean vanished = false;
        for (PlayerAvatarVanishProvider provider : providers.activeProviders()) {
            try {
                if (provider.isVanished(playerUuid)) {
                    vanished = true;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        vanishCache.put(playerUuid, new CacheEntry(vanished, now + VANISH_CACHE_TTL_MS));
        return vanished;
    }

    private record CacheEntry(boolean vanished, long expiresAtMs) {}

    private record ProviderState(List<PlayerAvatarVanishProvider> activeProviders, long expiresAtMs) {}
}

