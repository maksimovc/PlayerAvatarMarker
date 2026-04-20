package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.common.plugin.AuthorInfo;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.Universe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAvatarAssetPack {

    private static final String PACK_ID = "thenexusgates:PlayerAvatarMarkerAssets";
    private static final String PACK_GROUP = "thenexusgates";
    private static final String PACK_NAME = "PlayerAvatarMarkerAssets";
    private static final String PACK_VERSION = "1.4.0";
    private static final String TARGET_SERVER_VERSION = "2026.03.26-89796e57b";
    private static final String FALLBACK_MARKER_IMAGE = "pam-placeholder.png";
    private static final String MARKER_ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final String WORLDMAP_ASSET_PREFIX = "UI/WorldMap/";
    private static final String PACK_ICON_RESOURCE = "/asset-pack-icon-256.png";
    private static final String PACK_ICON_FILE = "icon-256.png";

    private static final String MANIFEST_JSON = """
            {
              "Group": "thenexusgates",
              "Name": "PlayerAvatarMarkerAssets",
                                                                                                                "Version": "1.4.0",
              "Description": "Generated avatar overrides for world map marker icons",
              "Authors": [
                {
                  "Name": "maksimovc"
                }
              ],
              "ServerVersion": "2026.03.26-89796e57b",
              "Dependencies": {},
              "OptionalDependencies": {},
              "DisabledByDefault": false,
              "IncludesAssetPack": true
            }
            """;

    private static final ConcurrentHashMap<String, FileCommonAsset> pushedAssets = new ConcurrentHashMap<>();
    private static final List<String> MIGRATED_DATA_ENTRIES = List.of(
            "playeravatarmarker-config.json",
            "player-settings"
        );

    private static volatile boolean initialized;
    private static volatile boolean registered;
    private static Path dataRoot;
    private static Path packRoot;

    private PlayerAvatarAssetPack() {}

    static void init() {
        ensureInitialized();
        registerPackIfNeeded();
    }

    static Path getPackRoot() {
        ensureInitialized();
        return packRoot;
    }

    static Path getDataRoot() {
        ensureInitialized();
        return dataRoot;
    }

    static String getImagePath(UUID playerUuid) {
        return "pam-" + playerUuid.toString().replace("-", "") + ".png";
    }

    static String getFallbackImagePath() {
        return FALLBACK_MARKER_IMAGE;
    }

    static String toUiAssetPath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        return MARKER_ASSET_PREFIX + imagePath;
    }

    static void writeAvatar(UUID playerUuid, byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) return;
        writeAvatar(getImagePath(playerUuid), pngBytes);
    }

    static void writeAvatar(String slotImage, byte[] pngBytes) {
        ensureInitialized();
        registerPackIfNeeded();
        if (slotImage == null || pngBytes == null || pngBytes.length == 0) {
            return;
        }

        try {
            Path output = packRoot.resolve("Common/UI/WorldMap/MapMarkers").resolve(slotImage);
            if (!writeBytesIfChanged(output, pngBytes)) {
                return;
            }
            pushAssetToClients(MARKER_ASSET_PREFIX + slotImage, pngBytes, output);
        } catch (IOException e) {
        }
    }

    private static void pushAssetToClients(String assetName, byte[] pngBytes, Path filePath) {
        try {
            CommonAssetModule cam = CommonAssetModule.get();
            if (cam == null) {
                return;
            }

            FileCommonAsset asset = new FileCommonAsset(filePath, assetName, pngBytes);
            cam.addCommonAsset(assetName, asset, true);
            pushedAssets.put(assetName, asset);
            Universe universe = Universe.get();
            if (universe != null && universe.getPlayerCount() > 0) {
                universe.broadcastPacketNoCache(new RequestCommonAssetsRebuild());
            }
        } catch (Exception e) {
        }
    }

    static void sendAvatarsToPlayer(PacketHandler ph) {
        if (ph == null) return;
        CommonAssetModule cam = CommonAssetModule.get();
        if (cam == null) return;
        List<CommonAsset> assets = new ArrayList<>(pushedAssets.values());
        if (!assets.isEmpty()) {
            cam.sendAssetsToPlayer(ph, assets, true);
        }
    }

    static void cleanupAvatar(UUID uuid) {
        String imagePath = getImagePath(uuid);
        String assetName = "UI/WorldMap/MapMarkers/" + imagePath;
        pushedAssets.remove(assetName);
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (PlayerAvatarAssetPack.class) {
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
                Path worldRoot = modsDirectory.getParent();

                dataRoot = worldRoot == null
                    ? modsDirectory.resolve("PlayerAvatarMarker")
                    : worldRoot.resolve("plugins").resolve("PlayerAvatarMarker");
                packRoot = modsDirectory.resolve("PlayerAvatarMarkerAssets");

                Files.createDirectories(dataRoot);
                Files.createDirectories(packRoot);
                migrateLegacyData();
                Path manifestPath = packRoot.resolve("manifest.json");
                Files.writeString(manifestPath, MANIFEST_JSON, StandardCharsets.UTF_8);
                ensurePackIcon();
                ensureStaticWorldMapAssets();
                ensurePackEnabled(modsDirectory.getParent().resolve("config.json"));

                registerPackIfNeeded();
                initialized = true;
            } catch (IOException | URISyntaxException e) {
                throw new IllegalStateException("Failed to initialize avatar asset pack", e);
            }
        }
    }

    private static void registerPackIfNeeded() {
        if (registered) {
            return;
        }

        AssetModule assetModule = AssetModule.get();
        if (assetModule == null) {
            return;
        }

        if (assetModule.getAssetPack(PACK_ID) != null) {
            registered = true;
            return;
        }

        assetModule.registerPack(PACK_ID, packRoot, buildRuntimeManifest(), true);
        registered = true;
    }

    private static void ensurePackIcon() {
        if (packRoot == null) {
            return;
        }

        try (var stream = PlayerAvatarAssetPack.class.getResourceAsStream(PACK_ICON_RESOURCE)) {
            if (stream == null) {
                return;
            }

            byte[] iconBytes = stream.readAllBytes();
            if (iconBytes.length == 0) {
                return;
            }

            writeBytesIfChanged(packRoot.resolve(PACK_ICON_FILE), iconBytes);
        } catch (IOException e) {
        }
    }

    private static void ensureStaticWorldMapAssets() {
        writeStaticCommonAsset(
                WORLDMAP_ASSET_PREFIX + "Player.png",
                packRoot.resolve("Common/UI/WorldMap/Player.png"),
                PlayerAvatarImageProcessor.createTransparentPng());
        writeStaticCommonAsset(
                MARKER_ASSET_PREFIX + "Player.png",
                packRoot.resolve("Common/UI/WorldMap/MapMarkers/Player.png"),
                PlayerAvatarImageProcessor.createTransparentPng());
        writeStaticCommonAsset(
                MARKER_ASSET_PREFIX + FALLBACK_MARKER_IMAGE,
                packRoot.resolve("Common/UI/WorldMap/MapMarkers").resolve(FALLBACK_MARKER_IMAGE),
                PlayerAvatarImageProcessor.createFallbackMarkerPng(64));
    }

    private static void writeStaticCommonAsset(String assetName, Path output, byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) {
            return;
        }

        try {
            if (!writeBytesIfChanged(output, pngBytes)) {
                return;
            }
            pushAssetToClients(assetName, pngBytes, output);
        } catch (IOException e) {
        }
    }

    private static void migrateLegacyData() throws IOException {
        if (dataRoot == null || packRoot == null || dataRoot.equals(packRoot)) {
            return;
        }

        for (String entryName : MIGRATED_DATA_ENTRIES) {
            Path legacyPath = packRoot.resolve(entryName);
            if (!Files.exists(legacyPath)) {
                continue;
            }

            Path targetPath = dataRoot.resolve(entryName);
            moveRecursively(legacyPath, targetPath);
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

    private static PluginManifest buildRuntimeManifest() {
        PluginManifest manifest = new PluginManifest();
        manifest.setGroup(PACK_GROUP);
        manifest.setName(PACK_NAME);
        manifest.setVersion(Semver.fromString(PACK_VERSION));
        manifest.setDescription("Generated avatar overrides for world map marker icons");
        manifest.setWebsite("https://github.com/thenexusgates");
        manifest.setServerVersion(TARGET_SERVER_VERSION);

        AuthorInfo author = new AuthorInfo();
        author.setName("maksimovc");
        manifest.setAuthors(List.of(author));
        return manifest;
    }

    private static void ensurePackEnabled(Path configPath) {
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            String updated = json;

            if (json.contains('"' + PACK_ID + '"')) {
                updated = json.replaceAll(
                        "(\\\"" + java.util.regex.Pattern.quote(PACK_ID) + "\\\"\\s*:\\s*\\{\\s*\\\"Enabled\\\"\\s*:\\s*)false",
                        "$1true");
            } else if (json.contains("\"Mods\": {")) {
                updated = json.replace(
                        "\"Mods\": {",
                        "\"Mods\": {\n    \"" + PACK_ID + "\": {\n      \"Enabled\": true\n    },");
            }

            if (!updated.equals(json)) {
                Files.writeString(configPath, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
        }
    }

    private static boolean writeBytesIfChanged(Path output, byte[] pngBytes) throws IOException {
        Files.createDirectories(output.getParent());
        if (Files.exists(output) && java.util.Arrays.equals(Files.readAllBytes(output), pngBytes)) {
            return false;
        }
        Files.write(output, pngBytes);
        return true;
    }
}