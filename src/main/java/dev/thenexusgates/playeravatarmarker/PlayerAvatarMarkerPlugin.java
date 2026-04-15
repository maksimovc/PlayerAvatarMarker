package dev.thenexusgates.playeravatarmarker;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public final class PlayerAvatarMarkerPlugin extends JavaPlugin {

    static final String PROVIDER_KEY = "playerIcons";
    private static final String VERSION = "2.0.0";

    private static PlayerAvatarMarkerPlugin instance;
    private static PlayerAvatarConfig config;
    private final ConcurrentMap<UUID, PlayerRef> activePlayers = new ConcurrentHashMap<>();
    private FastMiniMapCompatService fastMiniMapCompatService;
    private PlayerAvatarPlayerSettingsStore playerSettingsStore;
    private PlayerAvatarUiSounds uiSounds;
    private Path dataDirectory;

    public static PlayerAvatarMarkerPlugin getInstance() {
        return instance;
    }

    public static PlayerAvatarConfig getConfig() {
        return config;
    }

    public Collection<PlayerRef> getActivePlayers() {
        return activePlayers.values();
    }

    public PlayerAvatarUiSounds getUiSounds() {
        return uiSounds;
    }

    public PlayerAvatarMarkerPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Starting v" + VERSION);

        PlayerAvatarAssetPack.init();
        PlayerAvatarLiveTracker.register();
        dataDirectory = PlayerAvatarAssetPack.getPackRoot();
        config = PlayerAvatarConfig.load(
                dataDirectory.resolve("playeravatarmarker-config.json"));
        playerSettingsStore = new PlayerAvatarPlayerSettingsStore(dataDirectory);
        uiSounds = new PlayerAvatarUiSounds();
        getCommandRegistry().registerCommand(new PlayerAvatarControlCommand(this));

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
            registerActivePlayer(ref);
            // Do NOT call sendAvatarsToPlayer here: the player is already in the Playing
            // stage, and sending assets via CommonAssetModule at that point triggers a
            // WorldLoadProgress packet which the client rejects, causing a disconnect.
            // Assets registered via addCommonAsset are automatically delivered during
            // this player's SettingUp phase before PlayerReadyEvent fires.
            PlayerAvatarMarkerSupport.ensureRenderablePlayerModel(ref);
            warmAvatarForPlayer(ref);
        });

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef ref = event.getPlayerRef();
            if (ref == null) return;
            java.util.UUID uuid = ref.getUuid();
            if (uuid == null) return;
            activePlayers.remove(uuid);
            if (playerSettingsStore != null) {
                playerSettingsStore.unload(uuid);
            }
            PlayerAvatarLiveTracker.remove(uuid);
            PlayerAvatarCache.invalidate(uuid);
            PlayerAvatarAssetPack.cleanupAvatar(uuid);
            PlayerAvatarMarkerProvider.removePersistedAvatar(uuid);
            if (fastMiniMapCompatService != null) {
                fastMiniMapCompatService.invalidatePlayer(uuid);
            }
        });

        getLogger().at(Level.INFO).log("[PlayerAvatarMarker] Ready.");

        if (FastMiniMapCompat.isAvailable()) {
            fastMiniMapCompatService = new FastMiniMapCompatService();
            fastMiniMapCompatService.register();
        }
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

    @Override
    protected void shutdown() {
        if (fastMiniMapCompatService != null) {
            fastMiniMapCompatService.unregister();
        }
        activePlayers.clear();
    }

    public PlayerAvatarPlayerSettings resolvePlayerSettings(PlayerRef playerRef) {
        return resolvePlayerSettings(playerRef != null ? playerRef.getUuid() : null);
    }

    public PlayerAvatarPlayerSettings resolvePlayerSettings(UUID playerUuid) {
        if (playerSettingsStore == null) {
            return new PlayerAvatarPlayerSettings();
        }
        return playerSettingsStore.resolve(playerUuid);
    }

    public void applyPlayerSettings(PlayerRef playerRef, PlayerAvatarPlayerSettings settings) {
        if (playerRef == null || settings == null || playerSettingsStore == null) {
            return;
        }
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        playerSettingsStore.save(playerUuid, settings);
    }

    public void openControlPage(Store<EntityStore> store, Ref<EntityStore> entityRef, PlayerRef playerRef) {
        if (store == null || entityRef == null || playerRef == null) {
            return;
        }

        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        player.getPageManager().openCustomPage(entityRef, store, new PlayerAvatarControlPage(playerRef, this));
        if (uiSounds != null) {
            uiSounds.play(playerRef, PlayerAvatarUiSounds.Cue.NAVIGATE);
        }
    }

    private void registerActivePlayer(PlayerRef ref) {
        if (ref == null) {
            return;
        }
        UUID uuid = ref.getUuid();
        if (uuid == null) {
            return;
        }
        activePlayers.put(uuid, ref);
        if (playerSettingsStore != null) {
            playerSettingsStore.preload(uuid);
        }
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
