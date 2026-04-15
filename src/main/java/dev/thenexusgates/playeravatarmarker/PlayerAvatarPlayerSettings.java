package dev.thenexusgates.playeravatarmarker;

final class PlayerAvatarPlayerSettings {

    static final int CURRENT_SCHEMA_VERSION = 1;

    int schemaVersion = CURRENT_SCHEMA_VERSION;
    boolean mapEnabled = true;
    boolean minimapEnabled = true;
    boolean compassEnabled = true;

    PlayerAvatarPlayerSettings copy() {
        PlayerAvatarPlayerSettings copy = new PlayerAvatarPlayerSettings();
        copy.schemaVersion = schemaVersion;
        copy.mapEnabled = mapEnabled;
        copy.minimapEnabled = minimapEnabled;
        copy.compassEnabled = compassEnabled;
        return copy;
    }

    PlayerAvatarPlayerSettings normalize() {
        schemaVersion = CURRENT_SCHEMA_VERSION;
        return this;
    }

    boolean isEnabled(PlayerAvatarSurface surface) {
        return switch (surface) {
            case MAP -> mapEnabled;
            case MINIMAP -> minimapEnabled;
            case COMPASS -> compassEnabled;
        };
    }

    void setEnabled(PlayerAvatarSurface surface, boolean enabled) {
        switch (surface) {
            case MAP -> mapEnabled = enabled;
            case MINIMAP -> minimapEnabled = enabled;
            case COMPASS -> compassEnabled = enabled;
        }
    }
}