package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;

import java.lang.reflect.Field;

final class PlayerAvatarWorldMapState {

    private static final Field CLIENT_HAS_WORLD_MAP_VISIBLE_FIELD = resolveClientHasWorldMapVisibleField();

    private PlayerAvatarWorldMapState() {
    }

    static boolean isWorldMapVisible(Player viewer) {
        return readWorldMapVisibility(viewer, false);
    }

    static boolean shouldShowSelfMarker(Player viewer) {
        return readWorldMapVisibility(viewer, true);
    }

    private static boolean readWorldMapVisibility(Player viewer, boolean fallback) {
        if (viewer == null) {
            return fallback;
        }

        Field field = CLIENT_HAS_WORLD_MAP_VISIBLE_FIELD;
        if (field == null) {
            return fallback;
        }

        try {
            WorldMapTracker worldMapTracker = viewer.getWorldMapTracker();
            if (worldMapTracker == null) {
                return fallback;
            }
            return field.getBoolean(worldMapTracker);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static Field resolveClientHasWorldMapVisibleField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

