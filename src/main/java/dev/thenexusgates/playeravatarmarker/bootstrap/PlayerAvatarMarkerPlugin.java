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

public final class PlayerAvatarMarkerPlugin extends JavaPlugin {

    static final String PROVIDER_KEY = "playerIcons";

    private static PlayerAvatarMarkerPlugin instance;
    private static PlayerAvatarConfig config;
    private final ConcurrentMap<UUID, PlayerRef> activePlayers = new ConcurrentHashMap<>();
    private FastMiniMapCompatService fastMiniMapCompatService;
    private PlayerAvatarPlayerSettingsStore playerSettingsStore;
    private PlayerAvatarAvatarService avatarService;
    private PlayerAvatarUiSounds uiSounds;
    private Path dataDirectory;

    public static PlayerAvatarMarkerPlugin getInstance() {
        return instance;
    }

    public static PlayerAvatarConfig getConfig() {
        return config;
    }

    public Collection<PlayerRef> getActivePlayers() {
        Collection<PlayerRef> livePlayers = resolveLivePlayers();
        return livePlayers.isEmpty() ? activePlayers.values() : livePlayers;
    }

    public PlayerRef getActivePlayerRef(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        PlayerRef liveRef = resolveLivePlayerRef(playerUuid);
        return liveRef != null ? liveRef : activePlayers.get(playerUuid);
    }

    public PlayerAvatarUiSounds getUiSounds() {
        return uiSounds;
    }

    public PlayerAvatarAvatarService getAvatarService() {
        return avatarService;
    }

    public PlayerAvatarMarkerPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        PlayerAvatarStorage.init();
        PlayerAvatarAssetPack.init();
        PlayerAvatarLiveTracker.register();
        dataDirectory = PlayerAvatarStorage.getDataRoot();
        config = PlayerAvatarConfig.load(
                dataDirectory.resolve("playeravatarmarker-config.json"));
        avatarService = new PlayerAvatarAvatarService(dataDirectory);
        playerSettingsStore = new PlayerAvatarPlayerSettingsStore(dataDirectory);
        uiSounds = new PlayerAvatarUiSounds();
        getCommandRegistry().registerCommand(new PlayerAvatarControlCommand(this));

        registerProvidersForLoadedWorlds();
        registerEventHandlers();

        if (FastMiniMapCompat.isAvailable()) {
            fastMiniMapCompatService = new FastMiniMapCompatService();
            fastMiniMapCompatService.register();
        }
    }

    private void registerProvidersForLoadedWorlds() {
        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        for (World world : universe.getWorlds().values()) {
            registerProvider(world);
        }
    }

    private void registerEventHandlers() {
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            World world = event.getWorld();
            if (world != null) {
                registerProvider(world);
            }
        });

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Ref<EntityStore> entityRef = event.getPlayerRef();
            if (entityRef == null || !entityRef.isValid()) {
                return;
            }
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) {
                return;
            }

            PlayerRef ref = store.getComponent(entityRef, PlayerRef.getComponentType());
            if (ref == null) {
                return;
            }

            com.hypixel.hytale.server.core.entity.entities.Player player = event.getPlayer();
            World playerWorld = player == null ? null : player.getWorld();
            if (playerWorld != null) {
                registerProvider(playerWorld);
            }
            registerActivePlayer(ref);
            if (avatarService != null && ref != null) {
                avatarService.clearViewer(ref.getUuid());
            }
            PlayerAvatarPlayerModelSupport.ensureRenderable(ref);
            warmAvatarForPlayer(ref);
            warmKnownAvatarsForViewer(ref);
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
            if (avatarService != null) {
                avatarService.clearViewer(uuid);
            }
            PlayerAvatarMarkerProvider.removePersistedAvatar(uuid);
        });
    }

    private void registerProvider(World world) {
        WorldMapManager worldMapManager = world.getWorldMapManager();
        if (worldMapManager == null) return;
        suppressDefaultPlayerMarkers(worldMapManager, world);
        Map<String, WorldMapManager.MarkerProvider> providers = worldMapManager.getMarkerProviders();
        if (providers == null || !(providers.get(PROVIDER_KEY) instanceof PlayerAvatarMarkerProvider)) {
            installProvider(worldMapManager, PROVIDER_KEY, new PlayerAvatarMarkerProvider());
        }

        if (BetterMapCompatProvider.isAvailable()) {
            if (providers == null || !(providers.get(BetterMapCompatProvider.PROVIDER_KEY) instanceof BetterMapCompatProvider)) {
                installProvider(worldMapManager, BetterMapCompatProvider.PROVIDER_KEY, new BetterMapCompatProvider());
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

        providers.entrySet().removeIf(entry -> isVanillaOtherPlayersProvider(entry.getValue()));
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
        PlayerAvatarLiveTracker.shutdown();
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

    private Collection<PlayerRef> resolveLivePlayers() {
        Universe universe = Universe.get();
        if (universe == null) {
            return java.util.List.of();
        }

        java.util.List<PlayerRef> players = universe.getPlayers();
        if (players == null || players.isEmpty()) {
            return java.util.List.of();
        }

        java.util.ArrayList<PlayerRef> livePlayers = new java.util.ArrayList<>(players.size());
        for (PlayerRef ref : players) {
            if (ref == null) {
                continue;
            }

            UUID uuid = ref.getUuid();
            if (uuid != null) {
                activePlayers.put(uuid, ref);
            }
            livePlayers.add(ref);
        }
        return livePlayers;
    }

    private PlayerRef resolveLivePlayerRef(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }

        java.util.List<PlayerRef> players = universe.getPlayers();
        if (players == null || players.isEmpty()) {
            return null;
        }

        for (PlayerRef ref : players) {
            if (ref == null || !playerUuid.equals(ref.getUuid())) {
                continue;
            }

            activePlayers.put(playerUuid, ref);
            return ref;
        }

        return null;
    }

    private void warmAvatarForPlayer(PlayerRef ref) {
        if (ref == null) return;
        java.util.UUID uuid = ref.getUuid();
        String username = ref.getUsername();
        if (uuid == null || username == null || username.isEmpty()) {
            return;
        }

        if (avatarService != null) {
            avatarService.prefetch(uuid, username);
        } else {
            PlayerAvatarCache.getOrFetch(uuid, username);
        }
    }

    private void warmKnownAvatarsForViewer(PlayerRef viewerRef) {
        if (viewerRef == null || avatarService == null) {
            return;
        }

        UUID viewerUuid = viewerRef.getUuid();
        for (PlayerRef ref : activePlayers.values()) {
            if (ref == null) {
                continue;
            }

            UUID uuid = ref.getUuid();
            String username = ref.getUsername();
            if (uuid == null || username == null || username.isBlank()) {
                continue;
            }

            if (viewerUuid != null && viewerUuid.equals(uuid)) {
                continue;
            }

            avatarService.prefetch(uuid, username);
        }
    }
}
