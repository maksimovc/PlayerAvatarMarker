package dev.thenexusgates.playeravatarmarker;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarCache {

    private static final String HYVATAR_BASE_URL = "https://hyvatar.io/render/";
    private static final long DISK_CACHE_TTL_MS = Duration.ofHours(12).toMillis();
    private static final long FETCH_FAILURE_COOLDOWN_MS = Duration.ofSeconds(30).toMillis();
    private static final int MIN_FETCH_SIZE = 128;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ConcurrentHashMap<UUID, CompletableFuture<byte[]>> cache =
            new ConcurrentHashMap<>();
        private static final ConcurrentHashMap<UUID, FailureState> failures = new ConcurrentHashMap<>();

    private static volatile Path cacheRoot;

    private PlayerAvatarCache() {}

    static void configure(Path dataRoot) {
        if (dataRoot == null) {
            return;
        }
        cacheRoot = dataRoot.resolve("avatar-cache");
        try {
            Files.createDirectories(cacheRoot);
        } catch (Exception exception) {
        }
    }

    static CompletableFuture<byte[]> getOrFetch(UUID uuid, String username) {
        if (uuid == null || username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        FailureState failure = failures.get(uuid);
        if (failure != null && failure.retryAfterMs() > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(null);
        }

        return cache.computeIfAbsent(uuid, ignored -> loadOrFetchAsync(uuid, username)
                .whenComplete((bytes, error) -> {
                    if (error != null || bytes == null || bytes.length == 0) {
                        cache.remove(uuid);
                        return;
                    }
                    failures.remove(uuid);
                }));
    }

    static void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    static String buildRenderUrl(String username, int size) {
        if (username == null || username.isBlank()) {
            return null;
        }

        int requestedSize = Math.max(1, size);
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return HYVATAR_BASE_URL + encoded + "?size=" + requestedSize;
    }

    private static CompletableFuture<byte[]> loadOrFetchAsync(UUID uuid, String username) {
        return CompletableFuture.supplyAsync(() -> readDiskCache(uuid))
                .thenCompose(cachedBytes -> cachedBytes != null && cachedBytes.length > 0
                        ? CompletableFuture.completedFuture(cachedBytes)
                        : fetchAsync(uuid, username).thenApply(bytes -> {
                            writeDiskCache(uuid, bytes);
                            return bytes;
                        }));
    }

    private static CompletableFuture<byte[]> fetchAsync(UUID uuid, String username) {
        PlayerAvatarConfig cfg = PlayerAvatarMarkerPlugin.getConfig();
        int size = Math.max(MIN_FETCH_SIZE, (cfg != null) ? cfg.avatarSize : 64);
        String url = buildRenderUrl(username, size);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    }
                    registerFailure(uuid);
                    return null;
                })
                .exceptionally(e -> {
                    registerFailure(uuid);
                    return null;
                });
    }

    private static void registerFailure(UUID uuid) {
        if (uuid == null) {
            return;
        }

        failures.put(uuid, new FailureState(System.currentTimeMillis() + FETCH_FAILURE_COOLDOWN_MS));
    }

    private static byte[] readDiskCache(UUID uuid) {
        Path root = cacheRoot;
        if (root == null || uuid == null) {
            return null;
        }

        try {
            Path file = root.resolve(uuid.toString().replace("-", "") + ".png");
            if (!Files.exists(file)) {
                return null;
            }
            long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
            if (ageMs > DISK_CACHE_TTL_MS) {
                return null;
            }
            return Files.readAllBytes(file);
        } catch (Exception exception) {
            return null;
        }
    }

    private static void writeDiskCache(UUID uuid, byte[] bytes) {
        Path root = cacheRoot;
        if (root == null || uuid == null || bytes == null || bytes.length == 0) {
            return;
        }

        try {
            Files.createDirectories(root);
            Files.write(root.resolve(uuid.toString().replace("-", "") + ".png"), bytes);
        } catch (Exception exception) {
        }
    }

    private record FailureState(long retryAfterMs) {
    }
}
