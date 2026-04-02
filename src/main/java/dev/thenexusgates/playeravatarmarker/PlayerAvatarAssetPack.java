package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.common.plugin.AuthorInfo;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class PlayerAvatarAssetPack {

    private static final Logger LOGGER = Logger.getLogger(PlayerAvatarAssetPack.class.getName());
    private static final String PACK_ID = "thenexusgates:PlayerAvatarMarkerAssets";
    private static final String PACK_GROUP = "thenexusgates";
    private static final String PACK_NAME = "PlayerAvatarMarkerAssets";
    private static final String PACK_VERSION = "1.0.0";
    private static final String TARGET_SERVER_VERSION = "2026.03.26-89796e57b";

    private static final String MANIFEST_JSON = """
            {
              "Group": "thenexusgates",
              "Name": "PlayerAvatarMarkerAssets",
              "Version": "1.0.0",
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

    private static volatile boolean initialized;
    private static volatile boolean registered;
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

    static String getImagePath(UUID playerUuid) {
        return "pam-" + playerUuid.toString().replace("-", "") + ".png";
    }

    static void suppressDefaultPlayerMarker() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            writeAvatar("Player.png", baos.toByteArray());
        } catch (IOException e) {
            LOGGER.warning("[PlayerAvatarMarker] Failed to suppress default player marker: " + e.getMessage());
        }
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
            Files.createDirectories(output.getParent());
            Files.write(output, pngBytes);
            pushAssetToClients(slotImage, pngBytes, output);
        } catch (IOException e) {
            LOGGER.warning("[PlayerAvatarMarker] Failed to write avatar asset pack file for "
                    + slotImage + ": " + e.getMessage());
        }
    }

    private static void pushAssetToClients(String fileName, byte[] pngBytes, Path filePath) {
        try {
            CommonAssetModule cam = CommonAssetModule.get();
            if (cam == null) {
                LOGGER.warning("[PlayerAvatarMarker] CommonAssetModule not available, cannot push asset");
                return;
            }

            String assetName = "UI/WorldMap/MapMarkers/" + fileName;
            FileCommonAsset asset = new FileCommonAsset(filePath, assetName, pngBytes);
            cam.addCommonAsset(assetName, asset, true);
            cam.sendAsset(asset, true);
            pushedAssets.put(assetName, asset);
            LOGGER.info("[PlayerAvatarMarker] Pushed avatar asset to all clients: " + assetName);
        } catch (Exception e) {
            LOGGER.warning("[PlayerAvatarMarker] Failed to push avatar asset to clients: " + e.getMessage());
        }
    }

    static void sendAllAvatars() {
        CommonAssetModule cam = CommonAssetModule.get();
        if (cam == null) return;
        for (FileCommonAsset asset : pushedAssets.values()) {
            try {
                cam.sendAsset(asset, true);
            } catch (Exception e) {
                LOGGER.warning("[PlayerAvatarMarker] Failed to resend avatar: " + e.getMessage());
            }
        }
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

                packRoot = modsDirectory.resolve("PlayerAvatarMarkerAssets");

                Files.createDirectories(packRoot);
                Path manifestPath = packRoot.resolve("manifest.json");
                Files.writeString(manifestPath, MANIFEST_JSON, StandardCharsets.UTF_8);
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
        LOGGER.info("[PlayerAvatarMarker] Registered runtime asset pack: " + PACK_ID);
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
            LOGGER.warning("[PlayerAvatarMarker] Failed to auto-enable asset pack in config.json: "
                    + e.getMessage());
        }
    }
}