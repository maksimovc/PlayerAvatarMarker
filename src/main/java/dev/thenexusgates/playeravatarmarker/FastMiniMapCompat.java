package dev.thenexusgates.playeravatarmarker;

final class FastMiniMapCompat {

    private static final String API_CLASS = "dev.thenexusgates.fastminimap.FastMiniMapPlayerLayerApi";
    private static final boolean AVAILABLE = detect();

    private FastMiniMapCompat() {}

    static boolean isAvailable() {
        return AVAILABLE;
    }

    private static boolean detect() {
        try {
            Class.forName(API_CLASS, false, FastMiniMapCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
