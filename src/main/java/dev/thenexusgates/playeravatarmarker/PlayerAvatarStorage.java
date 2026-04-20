package dev.thenexusgates.playeravatarmarker;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

final class PlayerAvatarStorage {

    private static final List<String> MIGRATED_DATA_ENTRIES = List.of(
            "playeravatarmarker-config.json",
            "player-settings",
            "avatar-cache");

    private static volatile boolean initialized;
    private static Path dataRoot;

    private PlayerAvatarStorage() {
    }

    static void init() {
        ensureInitialized();
    }

    static Path getDataRoot() {
        ensureInitialized();
        return dataRoot;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (PlayerAvatarStorage.class) {
            if (initialized) {
                return;
            }

            try {
                Path pluginLocation = Paths.get(PlayerAvatarMarkerPlugin.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
                Path modsDirectory = Files.isDirectory(pluginLocation)
                        ? pluginLocation
                        : pluginLocation.getParent();
                Path worldRoot = modsDirectory != null ? modsDirectory.getParent() : null;
                Path legacyPackRoot = modsDirectory != null ? modsDirectory.resolve("PlayerAvatarMarkerAssets") : null;

                dataRoot = worldRoot == null
                        ? Paths.get("PlayerAvatarMarker")
                        : worldRoot.resolve("plugins").resolve("PlayerAvatarMarker");

                Files.createDirectories(dataRoot);
                migrateLegacyData(legacyPackRoot);
                cleanupLegacyAssetPack(legacyPackRoot);
                initialized = true;
            } catch (IOException | URISyntaxException exception) {
                throw new IllegalStateException("Failed to initialize PlayerAvatarMarker storage", exception);
            }
        }
    }

    private static void migrateLegacyData(Path legacyPackRoot) throws IOException {
        if (legacyPackRoot == null || dataRoot == null || !Files.exists(legacyPackRoot)) {
            return;
        }

        for (String entryName : MIGRATED_DATA_ENTRIES) {
            Path legacyPath = legacyPackRoot.resolve(entryName);
            if (!Files.exists(legacyPath)) {
                continue;
            }

            Path targetPath = dataRoot.resolve(entryName);
            moveRecursively(legacyPath, targetPath);
        }
    }

    private static void cleanupLegacyAssetPack(Path legacyPackRoot) {
        if (legacyPackRoot == null || dataRoot == null || dataRoot.equals(legacyPackRoot) || !Files.exists(legacyPackRoot)) {
            return;
        }

        try {
            deleteRecursively(legacyPackRoot);
        } catch (IOException exception) {
        }
    }

    private static void moveRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (var stream = Files.list(source)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    moveRecursively(child, target.resolve(child.getFileName().toString()));
                }
            }
            Files.deleteIfExists(source);
            return;
        }

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (var stream = Files.list(target)) {
                for (Path child : (Iterable<Path>) stream::iterator) {
                    deleteRecursively(child);
                }
            }
        }

        Files.deleteIfExists(target);
    }
}