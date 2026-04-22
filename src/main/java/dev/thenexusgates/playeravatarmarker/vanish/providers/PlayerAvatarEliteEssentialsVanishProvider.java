package dev.thenexusgates.playeravatarmarker;

import java.util.UUID;

final class PlayerAvatarEliteEssentialsVanishProvider implements PlayerAvatarVanishProvider {

    private static final String PLUGIN_CLASS = "com.eliteessentials.EliteEssentials";
    private static final String PLUGIN_IDENTIFIER = "com.eliteessentials:EliteEssentials";
    private static final String PLUGIN_GETTER = "getInstance";

    @Override
    public String name() {
        return "EliteEssentials";
    }

    @Override
    public boolean isAvailable() {
        try {
            return PlayerAvatarVanishReflection.resolveEnabledPluginInstance(PLUGIN_CLASS, PLUGIN_IDENTIFIER, PLUGIN_GETTER) != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean isVanished(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }

        try {
            Object plugin = PlayerAvatarVanishReflection.resolveEnabledPluginInstance(PLUGIN_CLASS, PLUGIN_IDENTIFIER, PLUGIN_GETTER);
            if (plugin == null) {
                return false;
            }

            Object vanishService = PlayerAvatarVanishReflection.invokeNoArgs(plugin.getClass(), plugin, "getVanishService");
            return vanishService != null
                    && PlayerAvatarVanishReflection.invokeBoolean(vanishService.getClass(), vanishService, "isVanished", UUID.class, playerUuid);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}

