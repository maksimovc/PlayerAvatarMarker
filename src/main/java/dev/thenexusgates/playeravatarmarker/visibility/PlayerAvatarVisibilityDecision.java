package dev.thenexusgates.playeravatarmarker;

record PlayerAvatarVisibilityDecision(PlayerAvatarVisibilityState state,
                                      boolean viewerVanished,
                                      boolean targetVanished,
                                      boolean effectiveTargetVanished,
                                      boolean hiddenByViewerManager,
                                      boolean hiddenByCollector,
                                      boolean hiddenByVanishCollector) {

    boolean isVisible() {
        return state != null && state.isVisible();
    }

    boolean isGhosted() {
        return state != null && state.isGhosted();
    }
}

