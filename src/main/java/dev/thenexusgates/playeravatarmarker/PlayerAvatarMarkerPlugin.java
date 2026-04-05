package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Starting v1.2.0");

        PlayerAvatarAssetPack.init();
        PlayerAvatarLiveTracker.register();
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
            // getPlayer() and getPlayerRef() on Player are deprecated; required here since
            // PlayerReadyEvent exposes no direct PlayerRef path.
            @SuppressWarnings("deprecation")
            com.hypixel.hytale.server.core.entity.entities.Player player = event.getPlayer();
            if (player == null) return;
            World playerWorld = player.getWorld();
            if (playerWorld != null) {
                registerProvider(playerWorld);
            }
            @SuppressWarnings("deprecation")
            PlayerRef ref = player.getPlayerRef();
            // Do NOT call sendAvatarsToPlayer here: the player is already in the Playing
            // stage, and sending assets via CommonAssetModule at that point triggers a
            // WorldLoadProgress packet which the client rejects, causing a disconnect.
            // Assets registered via addCommonAsset are automatically delivered during
            // this player's SettingUp phase before PlayerReadyEvent fires.
            warmAvatarForPlayer(ref);
        });

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef ref = event.getPlayerRef();
            if (ref == null) return;
            java.util.UUID uuid = ref.getUuid();
            if (uuid == null) return;
            PlayerAvatarLiveTracker.remove(uuid);
            PlayerAvatarCache.invalidate(uuid);
            PlayerAvatarAssetPack.cleanupAvatar(uuid);
            PlayerAvatarMarkerProvider.removePersistedAvatar(uuid);
        });

        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Ready.");
    }

    private void registerProvider(World world) {
        WorldMapManager worldMapManager = world.getWorldMapManager();
        if (worldMapManager == null) return;
        suppressDefaultPlayerMarkers(worldMapManager, world);
        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers == null || !(providers.get(PROVIDER_KEY) instanceof PlayerAvatarMarkerProvider)) {
            installProvider(worldMapManager, PROVIDER_KEY, new PlayerAvatarMarkerProvider());
            getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Provider registered: " + world.getName());
        }

        if (BetterMapCompatProvider.isAvailable()) {
            if (providers == null || !(providers.get(BetterMapCompatProvider.PROVIDER_KEY) instanceof BetterMapCompatProvider)) {
                installProvider(worldMapManager, BetterMapCompatProvider.PROVIDER_KEY, new BetterMapCompatProvider());
                getLogger().at(Level.INFO).log("[PlayerAvatarMarker] BetterMap compatibility provider active: " + world.getName());
            }
        }
    }

    private void installProvider(WorldMapManager worldMapManager, String key, WorldMapManager.MarkerProvider provider) {
        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers != null) {
            try {
                providers.put(key, provider);
                return;
            } catch (UnsupportedOperationException ignored) {
            }
        }

        worldMapManager.addMarkerProvider(key, provider);
    }

    private void suppressDefaultPlayerMarkers(WorldMapManager worldMapManager, World world) {
        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers == null || providers.isEmpty()) {
            return;
        }

        boolean removed = providers.entrySet().removeIf(entry -> isVanillaOtherPlayersProvider(entry.getValue()));
        if (removed) {
            getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Removed vanilla player marker provider: " + world.getName());
        }
    }

    private boolean isVanillaOtherPlayersProvider(WorldMapManager.MarkerProvider provider) {
        return provider != null
                && "com.hypixel.hytale.server.core.universe.world.worldmap.markers.providers.OtherPlayersMarkerProvider"
                .equals(provider.getClass().getName());
    }

    private void warmAvatarForPlayer(PlayerRef ref) {
        if (ref == null) return;
        java.util.UUID uuid = ref.getUuid();
        String username = ref.getUsername();
        if (uuid == null || username == null || username.isEmpty()) {
            return;
        }

        PlayerAvatarCache.getOrFetch(uuid, username);
    }
}
