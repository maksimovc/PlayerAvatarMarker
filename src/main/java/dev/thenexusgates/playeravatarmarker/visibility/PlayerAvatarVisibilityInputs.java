package dev.thenexusgates.playeravatarmarker;

record PlayerAvatarVisibilityInputs(boolean self,
                                    boolean viewerVanished,
                                    boolean targetVanished,
                                    boolean hiddenByViewerManager,
                                    boolean hiddenByCollector,
                                    boolean hiddenByVanishCollector) {

    static PlayerAvatarVisibilityInputs hiddenTarget() {
        return new PlayerAvatarVisibilityInputs(false, false, false, false, true, false);
    }
}

