package dev.thenexusgates.playeravatarmarker;

enum PlayerAvatarVisibilityState {
    VISIBLE,
    GHOSTED,
    HIDDEN;

    boolean isVisible() {
        return this != HIDDEN;
    }

    boolean isGhosted() {
        return this == GHOSTED;
    }
}

