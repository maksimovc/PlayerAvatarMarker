package dev.thenexusgates.playeravatarmarker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class PlayerAvatarPlayerSettings {

    private static final int OVERRIDE_INHERIT = 0;
    private static final int OVERRIDE_DISABLED = 1;
    private static final int OVERRIDE_ENABLED = 2;
    private static final int OVERRIDE_MASK = 0b11;

    static final int CURRENT_SCHEMA_VERSION = 2;

    int schemaVersion = CURRENT_SCHEMA_VERSION;
    boolean mapEnabled = true;
    boolean minimapEnabled = true;
    boolean compassEnabled = true;
    final Map<UUID, Integer> playerOverrides = new LinkedHashMap<>();

    PlayerAvatarPlayerSettings copy() {
        PlayerAvatarPlayerSettings copy = new PlayerAvatarPlayerSettings();
        copy.schemaVersion = schemaVersion;
        copy.mapEnabled = mapEnabled;
        copy.minimapEnabled = minimapEnabled;
        copy.compassEnabled = compassEnabled;
        copy.playerOverrides.putAll(playerOverrides);
        return copy;
    }

    PlayerAvatarPlayerSettings normalize() {
        schemaVersion = CURRENT_SCHEMA_VERSION;
        playerOverrides.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() == 0);
        return this;
    }

    PlayerAvatarPlayerSettings normalizeForOwner(UUID ownerUuid) {
        removeForcedSelfOverrides(ownerUuid);
        return normalize();
    }

    boolean isEnabled(PlayerAvatarSurface surface) {
        return switch (surface) {
            case MAP -> mapEnabled;
            case MINIMAP -> minimapEnabled;
            case COMPASS -> compassEnabled;
        };
    }

    boolean isEnabledFor(PlayerAvatarSurface surface, UUID targetUuid) {
        if (targetUuid == null) {
            return isEnabled(surface);
        }

        int overrideState = overrideState(surface, targetUuid);
        return switch (overrideState) {
            case OVERRIDE_DISABLED -> false;
            case OVERRIDE_ENABLED -> true;
            default -> isEnabled(surface);
        };
    }

    boolean isEnabledFor(PlayerAvatarSurface surface, UUID ownerUuid, UUID targetUuid) {
        if (isSelfForcedHidden(surface, ownerUuid, targetUuid)) {
            return false;
        }
        return isEnabledFor(surface, targetUuid);
    }

    void setEnabled(PlayerAvatarSurface surface, boolean enabled) {
        switch (surface) {
            case MAP -> mapEnabled = enabled;
            case MINIMAP -> minimapEnabled = enabled;
            case COMPASS -> compassEnabled = enabled;
        }
    }

    void setAll(PlayerAvatarSurface surface, boolean enabled) {
        setEnabled(surface, enabled);
        clearOverrides(surface);
    }

    void toggleTarget(PlayerAvatarSurface surface, UUID targetUuid) {
        if (targetUuid == null) {
            return;
        }
        setTargetEnabled(surface, targetUuid, !isEnabledFor(surface, targetUuid));
    }

    void toggleTarget(PlayerAvatarSurface surface, UUID ownerUuid, UUID targetUuid) {
        if (isSelfForcedHidden(surface, ownerUuid, targetUuid)) {
            return;
        }
        toggleTarget(surface, targetUuid);
    }

    void setTargetEnabled(PlayerAvatarSurface surface, UUID targetUuid, boolean enabled) {
        if (targetUuid == null) {
            return;
        }

        int nextState = enabled == isEnabled(surface) ? OVERRIDE_INHERIT : (enabled ? OVERRIDE_ENABLED : OVERRIDE_DISABLED);
        putOverrideState(surface, targetUuid, nextState);
    }

    void setTargetEnabled(PlayerAvatarSurface surface, UUID ownerUuid, UUID targetUuid, boolean enabled) {
        if (isSelfForcedHidden(surface, ownerUuid, targetUuid)) {
            return;
        }
        setTargetEnabled(surface, targetUuid, enabled);
    }

    boolean hasOverride(PlayerAvatarSurface surface, UUID targetUuid) {
        return overrideState(surface, targetUuid) != OVERRIDE_INHERIT;
    }

    boolean hasAnyOverride(UUID targetUuid) {
        Integer value = targetUuid != null ? playerOverrides.get(targetUuid) : null;
        return value != null && value != 0;
    }

    static boolean isSelfForcedHidden(PlayerAvatarSurface surface, UUID ownerUuid, UUID targetUuid) {
        return ownerUuid != null
                && ownerUuid.equals(targetUuid)
                && (surface == PlayerAvatarSurface.MINIMAP || surface == PlayerAvatarSurface.COMPASS);
    }

    private void removeForcedSelfOverrides(UUID ownerUuid) {
        if (ownerUuid == null) {
            return;
        }

        clearOverrideState(PlayerAvatarSurface.MINIMAP, ownerUuid);
        clearOverrideState(PlayerAvatarSurface.COMPASS, ownerUuid);
    }

    private void clearOverrides(PlayerAvatarSurface surface) {
        if (playerOverrides.isEmpty()) {
            return;
        }

        playerOverrides.replaceAll((uuid, value) -> clearOverrideBits(surface, value));
        playerOverrides.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() == 0);
    }

    private int overrideState(PlayerAvatarSurface surface, UUID targetUuid) {
        Integer value = targetUuid != null ? playerOverrides.get(targetUuid) : null;
        if (value == null) {
            return OVERRIDE_INHERIT;
        }
        return (value >> shiftOf(surface)) & OVERRIDE_MASK;
    }

    private void putOverrideState(PlayerAvatarSurface surface, UUID targetUuid, int state) {
        int value = playerOverrides.getOrDefault(targetUuid, 0);
        int shift = shiftOf(surface);
        int cleared = value & ~(OVERRIDE_MASK << shift);
        int updated = cleared | ((state & OVERRIDE_MASK) << shift);
        if (updated == 0) {
            playerOverrides.remove(targetUuid);
        } else {
            playerOverrides.put(targetUuid, updated);
        }
    }

    private void clearOverrideState(PlayerAvatarSurface surface, UUID targetUuid) {
        if (targetUuid == null) {
            return;
        }

        int value = playerOverrides.getOrDefault(targetUuid, 0);
        int updated = clearOverrideBits(surface, value);
        if (updated == 0) {
            playerOverrides.remove(targetUuid);
        } else {
            playerOverrides.put(targetUuid, updated);
        }
    }

    private int clearOverrideBits(PlayerAvatarSurface surface, int value) {
        return value & ~(OVERRIDE_MASK << shiftOf(surface));
    }

    private static int shiftOf(PlayerAvatarSurface surface) {
        return switch (surface) {
            case MAP -> 0;
            case MINIMAP -> 2;
            case COMPASS -> 4;
        };
    }
}