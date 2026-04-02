package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import java.util.Map;
import java.util.logging.Level;

public final class PlayerAvatarMarkerPlugin extends JavaPlugin {

    static final String PROVIDER_KEY = "playerIcons";

    private static PlayerAvatarMarkerPlugin instance;
    private static PlayerAvatarConfig config;
    public static PlayerAvatarMarkerPlugin getInstance() {
        return instance;
    }

    public static PlayerAvatarConfig getConfig() {
        return config;
    }

    public PlayerAvatarMarkerPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Starting v1.0.0");

        PlayerAvatarAssetPack.init();
        PlayerAvatarAssetPack.suppressDefaultPlayerMarker();
        config = PlayerAvatarConfig.load(
                PlayerAvatarAssetPack.getPackRoot().resolve("playeravatarmarker-config.json"));

        Universe universe = Universe.get();
        if (universe != null) {
            for (World world : universe.getWorlds().values()) {
                registerProvider(world);
            }
        }

        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            World world = event.getWorld();
            if (world != null) {
                registerProvider(world);
            }
        });

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            World world = event.getPlayer().getWorld();
            if (world != null) {
                registerProvider(world);
            }
            PlayerAvatarAssetPack.sendAllAvatars();
            warmAvatarForPlayer(event.getPlayer());
        });

        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Ready.");
    }

    private void registerProvider(World world) {
        WorldMapManager worldMapManager = world.getWorldMapManager();
        if (worldMapManager == null) return;
        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers != null && providers.get(PROVIDER_KEY) instanceof PlayerAvatarMarkerProvider) return;
        worldMapManager.addMarkerProvider(PROVIDER_KEY, new PlayerAvatarMarkerProvider());
        worldMapManager.setTps(20);
        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Provider registered: " + world.getName() + " (TPS set to 20)");
    }

    private void warmAvatarForPlayer(com.hypixel.hytale.server.core.entity.entities.Player player) {
        java.util.UUID uuid = player.getUuid();
        String username = player.getPlayerRef() != null
                ? player.getPlayerRef().getUsername()
                : null;
        if (uuid == null || username == null || username.isEmpty()) {
            return;
        }

        PlayerAvatarCache.getOrFetch(uuid, username).thenAccept(bytes -> {
            if (bytes != null && bytes.length > 0) {
                byte[] processed = PlayerAvatarImageProcessor.process(
                        bytes, 64, config != null ? config.backgroundColorRGB() : 0x2D2D2D,
                        config != null && config.enableBackground);
                PlayerAvatarAssetPack.writeAvatar(uuid, processed);
            }
        });
    }
}
