package dev.thenexusgates.playeravatarmarker;

import java.util.UUID;

interface PlayerAvatarVanishProvider {

    String name();

    boolean isAvailable();

    boolean isVanished(UUID playerUuid);
}

