package dev.thenexusgates.playeravatarmarker;

final class PlayerAvatarVisibilityResolver {

    private PlayerAvatarVisibilityResolver() {}

    static PlayerAvatarVisibilityDecision resolve(PlayerAvatarVisibilityInputs inputs) {
        if (inputs == null) {
            return new PlayerAvatarVisibilityDecision(
                    PlayerAvatarVisibilityState.HIDDEN,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false);
        }

        boolean viewerVanished = inputs.viewerVanished();
        boolean targetVanished = inputs.targetVanished();
        boolean hiddenByViewerManager = inputs.hiddenByViewerManager();
        boolean hiddenByCollector = inputs.hiddenByCollector();
        boolean hiddenByVanishCollector = inputs.hiddenByVanishCollector();
        boolean effectiveTargetVanished = targetVanished
                || (viewerVanished && (hiddenByViewerManager || hiddenByVanishCollector));
        boolean selfGhosted = inputs.self()
                && (viewerVanished || targetVanished || hiddenByViewerManager || hiddenByCollector || hiddenByVanishCollector);

        PlayerAvatarVisibilityState state;
        if (inputs.self()) {
            state = selfGhosted ? PlayerAvatarVisibilityState.GHOSTED : PlayerAvatarVisibilityState.VISIBLE;
        } else if (hiddenByCollector || hiddenByViewerManager) {
            state = viewerVanished && effectiveTargetVanished
                    ? PlayerAvatarVisibilityState.GHOSTED
                    : PlayerAvatarVisibilityState.HIDDEN;
        } else if (effectiveTargetVanished) {
            state = viewerVanished ? PlayerAvatarVisibilityState.GHOSTED : PlayerAvatarVisibilityState.HIDDEN;
        } else {
            state = PlayerAvatarVisibilityState.VISIBLE;
        }

        return new PlayerAvatarVisibilityDecision(
                state,
                viewerVanished,
                targetVanished,
                effectiveTargetVanished,
                hiddenByViewerManager,
                hiddenByCollector,
                hiddenByVanishCollector);
    }
}

