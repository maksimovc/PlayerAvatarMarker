package dev.thenexusgates.playeravatarmarker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

final class PlayerAvatarPlayerSettingsStore {

    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarPlayerSettingsStore.class.getName());

    private final Path playersRoot;
    private final ConcurrentMap<UUID, PlayerAvatarPlayerSettings> cache = new ConcurrentHashMap<>();

    PlayerAvatarPlayerSettingsStore(Path dataRoot) {
        this.playersRoot = dataRoot.resolve("player-settings");
    }

    void preload(UUID playerUuid) {
        if (playerUuid != null) {
            resolve(playerUuid);
        }
    }

    void unload(UUID playerUuid) {
        if (playerUuid != null) {
            cache.remove(playerUuid);
        }
    }

    PlayerAvatarPlayerSettings resolve(UUID playerUuid) {
        if (playerUuid == null) {
            return new PlayerAvatarPlayerSettings();
        }
        return cache.computeIfAbsent(playerUuid, this::load).copy();
    }

    void save(UUID playerUuid, PlayerAvatarPlayerSettings settings) {
        if (playerUuid == null || settings == null) {
            return;
        }

        PlayerAvatarPlayerSettings normalized = settings.copy().normalize();
        cache.put(playerUuid, normalized);
        Path path = profilePath(playerUuid);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, encode(normalized), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warning("[PlayerAvatarMarker] Failed to save player settings for " + playerUuid + ": " + e.getMessage());
        }
    }

    private PlayerAvatarPlayerSettings load(UUID playerUuid) {
        PlayerAvatarPlayerSettings settings = new PlayerAvatarPlayerSettings();
        Path path = profilePath(playerUuid);
        if (!Files.exists(path)) {
            return settings;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            PlayerAvatarJson parsed = PlayerAvatarJson.parseObject(json);
            settings.schemaVersion = parsed.getInt("schemaVersion", settings.schemaVersion);
            settings.mapEnabled = parsed.getBoolean("mapEnabled", settings.mapEnabled);
            settings.minimapEnabled = parsed.getBoolean("minimapEnabled", settings.minimapEnabled);
            settings.compassEnabled = parsed.getBoolean("compassEnabled", settings.compassEnabled);
            PlayerAvatarPlayerSettings normalized = settings.normalize();
            String normalizedJson = encode(normalized);
            if (!normalizedJson.equals(json)) {
                Files.writeString(path, normalizedJson, StandardCharsets.UTF_8);
            }
            return normalized;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warning("[PlayerAvatarMarker] Failed to read player settings for " + playerUuid + ": " + e.getMessage());
            return settings.normalize();
        }
    }

    private Path profilePath(UUID playerUuid) {
        return playersRoot.resolve(playerUuid + ".json");
    }

    private static String encode(PlayerAvatarPlayerSettings settings) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("schemaVersion", settings.schemaVersion);
        values.put("mapEnabled", settings.mapEnabled);
        values.put("minimapEnabled", settings.minimapEnabled);
        values.put("compassEnabled", settings.compassEnabled);
        return PlayerAvatarJson.writeObject(values);
    }
}