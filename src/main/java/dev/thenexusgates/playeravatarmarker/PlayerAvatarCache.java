package dev.thenexusgates.playeravatarmarker;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class PlayerAvatarCache {

    private static final String HYVATAR_URL = "https://hyvatar.io/render/%s?size=64";

    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarCache.class.getName());

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ConcurrentHashMap<UUID, CompletableFuture<byte[]>> cache =
            new ConcurrentHashMap<>();

    private PlayerAvatarCache() {}

    static CompletableFuture<byte[]> getOrFetch(UUID uuid, String username) {
        return cache.computeIfAbsent(uuid, k -> fetchAsync(username));
    }

    static void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    private static CompletableFuture<byte[]> fetchAsync(String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String url = String.format(HYVATAR_URL, encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    }
                    LOGGER.warning("[PlayerAvatarMarker] hyvatar.io returned HTTP "
                            + response.statusCode() + " for user: " + username);
                    return null;
                })
                .exceptionally(e -> {
                    LOGGER.warning("[PlayerAvatarMarker] Failed to fetch avatar for "
                            + username + ": " + e.getMessage());
                    return null;
                });
    }
}
