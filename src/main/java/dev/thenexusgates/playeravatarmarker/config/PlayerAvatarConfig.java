package dev.thenexusgates.playeravatarmarker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlayerAvatarConfig {

    boolean enableRotation = false;
    boolean enableBackground = true;
    String backgroundColor = "#2D2D2D";
    boolean showNickname = true;
    int avatarSize = 64;

    private PlayerAvatarConfig() {}

    static PlayerAvatarConfig load(Path configPath) {
        PlayerAvatarConfig cfg = new PlayerAvatarConfig();
        if (!Files.exists(configPath)) {
            save(cfg, configPath);
            return cfg;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            cfg.enableRotation = readBool(json, "enableRotation", cfg.enableRotation);
            cfg.enableBackground = readBool(json, "enableBackground", cfg.enableBackground);
            cfg.backgroundColor = readString(json, "backgroundColor", cfg.backgroundColor);
            cfg.showNickname = readBool(json, "showNickname", cfg.showNickname);
            cfg.avatarSize = readInt(json, "avatarSize", cfg.avatarSize);
        } catch (IOException e) {
        }
        return cfg;
    }

    private static void save(PlayerAvatarConfig cfg, Path configPath) {
        String json = """
                {
                  "enableRotation": %s,
                  "enableBackground": %s,
                  "backgroundColor": "%s",
                  "showNickname": %s,
                  "avatarSize": %d
                }
                """.formatted(cfg.enableRotation, cfg.enableBackground,
                cfg.backgroundColor, cfg.showNickname, cfg.avatarSize);
        try {
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
    }

    private static boolean readBool(String json, String key, boolean fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : fallback;
    }

    private static String readString(String json, String key, String fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : fallback;
    }

    private static int readInt(String json, String key, int fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    int backgroundColorRGB() {
        try {
            String hex = backgroundColor.startsWith("#") ? backgroundColor.substring(1) : backgroundColor;
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0x2D2D2D;
        }
    }
}
