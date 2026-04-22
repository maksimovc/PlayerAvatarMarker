package dev.thenexusgates.playeravatarmarker;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarVanishProviders {

    private static final long VANISH_CACHE_TTL_MS = 100L;
    private static final long PROVIDER_CACHE_TTL_MS = 250L;
    private static final List<PlayerAvatarVanishProvider> PROVIDERS = List.of(
            new PlayerAvatarEliteEssentialsVanishProvider(),
            new PlayerAvatarEssentialsPlusVanishProvider(),
            new PlayerAvatarHyEssentialsXVanishProvider());

    private static final ConcurrentHashMap<UUID, CacheEntry> vanishCache = new ConcurrentHashMap<>();
    private static volatile ProviderState providerState = new ProviderState(false, 0L);

    private PlayerAvatarVanishProviders() {}

    static boolean hasActiveProvider() {
        long now = System.currentTimeMillis();
        ProviderState cached = providerState;
        if (cached.expiresAtMs() >= now) {
            return cached.active();
        }

        boolean active = false;
        for (PlayerAvatarVanishProvider provider : PROVIDERS) {
            if (provider == null) {
                continue;
            }
            try {
                if (provider.isAvailable()) {
                    active = true;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        providerState = new ProviderState(active, now + PROVIDER_CACHE_TTL_MS);
        return active;
    }

    static boolean isVanished(UUID playerUuid) {
        if (playerUuid == null || !hasActiveProvider()) {
            return false;
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = vanishCache.get(playerUuid);
        if (cached != null && cached.expiresAtMs() >= now) {
            return cached.vanished();
        }

        boolean vanished = false;
        for (PlayerAvatarVanishProvider provider : PROVIDERS) {
            if (provider == null) {
                continue;
            }
            try {
                if (!provider.isAvailable()) {
                    continue;
                }
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

    private record ProviderState(boolean active, long expiresAtMs) {}
}

