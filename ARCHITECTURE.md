# PlayerAvatarMarker Architecture

This document explains the new folder-oriented structure of the `PlayerAvatarMarker` source tree.

## Why the source tree was reorganized

The mod had grown into one large flat directory, which made it hard to quickly understand:

- where world-map code lives,
- where UI logic lives,
- where avatar generation lives,
- where vanish handling lives,
- and which files are safe to touch for a given feature.

To make maintenance easier without destabilizing the mod, the code is now grouped by responsibility.

> Note: the Java package remains `dev.thenexusgates.playeravatarmarker` on purpose.
> This keeps existing package-private collaboration intact while still giving the project a clear physical architecture in the source tree.

## Source layout

### `bootstrap/`
Entry point and top-level plugin wiring.

- `PlayerAvatarMarkerPlugin.java` — plugin startup/shutdown, provider registration, player lifecycle wiring, command registration.

### `command/`
User-facing command entrypoints.

- `PlayerAvatarControlCommand.java` — `/playeravatar`, `/pam`, `/avatarmarker`.

### `config/`
Static configuration loading and defaults.

- `PlayerAvatarConfig.java` — reads and writes the global config file.

### `settings/`
Per-player state and persistence.

- `PlayerAvatarPlayerSettings.java` — per-surface defaults and per-target overrides.
- `PlayerAvatarPlayerSettingsStore.java` — disk-backed loading/saving of player settings.

### `storage/`
Filesystem and lightweight JSON helpers.

- `PlayerAvatarStorage.java` — plugin data root initialization and legacy migration.
- `PlayerAvatarJson.java` — minimal JSON parser/writer used by settings storage.
- `PlayerAvatarPaths.java` — shared path resolution for plugin data and generated asset-pack folders.

### `avatar/`
Avatar fetching, caching, conversion, and viewer delivery state.

- `PlayerAvatarAvatarService.java` — main avatar pipeline.
- `PlayerAvatarCache.java` — HTTP fetch + disk cache.
- `PlayerAvatarImageProcessor.java` — PNG processing, ghosting, fallback image generation.

### `assets/`
Runtime-generated Hytale common assets.

- `PlayerAvatarAssetPack.java` — generated asset-pack registration and asset writes.
- `PlayerAvatarAssetPublisher.java` — direct per-viewer runtime asset delivery.
- `PlayerAvatarRuntimePngAsset.java` — in-memory PNG asset wrapper.

### `marker/`
Map/compass marker construction and related model types.

- `PlayerAvatarMarkerProvider.java` — main world map / compass provider.
- `PlayerAvatarMarkerFactory.java` — marker IDs, label formatting into packets, and marker creation.
- `PlayerAvatarMarkerVisuals.java` — avatar visual resolution, UI asset paths, and ghosted label styling.
- `PlayerAvatarPlayerModelSupport.java` — ensures player entities have renderable models when needed.
- `PlayerAvatarWorldMapState.java` — world-map visibility state reflection helpers.
- `PlayerAvatarSurface.java` — surface enum (`MAP`, `MINIMAP`, `COMPASS`).

### `tracking/`
Live player transform tracking.

- `PlayerAvatarLiveTracker.java` — rotation snapshots from movement packets plus transform resolution.

### `compat/`
Integrations with optional companion mods.

#### `compat/fastminimap/`
- `FastMiniMapCompat.java` — presence detection.
- `FastMiniMapCompatService.java` — FastMiniMap player-dot provider.

#### `compat/bettermap/`
- `BetterMapBridge.java` — reflection bridge into BetterMap.
- `BetterMapCompatProvider.java` — BetterMap radar provider.

### `ui/`
Custom UI page logic and player-facing text/sound helpers.

- `PlayerAvatarControlPage.java` — main control page rendering and interactions.
- `PlayerAvatarUiText.java` — EN/UK text helpers.
- `PlayerAvatarUiSounds.java` — UI feedback sound cues.

### `permissions/`
Permission checks and permission-denied feedback.

- `PlayerAvatarPermissions.java`

### `visibility/`
Shared visibility policy used by map, minimap, compass, and UI.

- `PlayerAvatarVisibilityState.java`
- `PlayerAvatarVisibilityInputs.java`
- `PlayerAvatarVisibilityDecision.java`
- `PlayerAvatarVisibilityResolver.java`
- `PlayerAvatarVisibilityService.java`

### `vanish/`
Vanish-provider abstraction and provider selection.

- `PlayerAvatarVanishProvider.java`
- `PlayerAvatarVanishProviders.java`
- `PlayerAvatarVanishReflection.java`

#### `vanish/providers/`
Concrete vanish integrations.

- `PlayerAvatarEliteEssentialsVanishProvider.java`
- `PlayerAvatarEssentialsPlusVanishProvider.java`
- `PlayerAvatarHyEssentialsXVanishProvider.java`

### `world/`
Helpers for world-thread-safe reads.

- `PlayerAvatarWorldThreadBridge.java`

### `src/test/java/.../visibility/`
Tests for the pure visibility matrix.

- `PlayerAvatarVisibilityResolverTest.java`

## Main runtime flows

### 1. Plugin startup
1. `bootstrap/PlayerAvatarMarkerPlugin` initializes storage and generated assets.
2. It loads global config and per-player settings services.
3. It registers commands, world-map providers, and optional compat providers.
4. It tracks active players and warms avatar data when players become ready.

### 2. Avatar delivery flow
1. `avatar/PlayerAvatarAvatarService` asks `avatar/PlayerAvatarCache` for raw avatar bytes.
2. `avatar/PlayerAvatarImageProcessor` generates normal and ghosted marker variants.
3. `assets/PlayerAvatarAssetPack` persists generated assets.
4. `assets/PlayerAvatarAssetPublisher` can push runtime assets directly to a viewer when needed.

### 3. Visibility / vanish flow
1. A surface asks `visibility/PlayerAvatarVisibilityService` for a decision.
2. That service gathers raw inputs:
   - self-view,
   - viewer vanish state,
   - target vanish state,
   - hidden-player-manager state,
   - collector filter state.
3. `visibility/PlayerAvatarVisibilityResolver` turns those inputs into a final state:
   - `VISIBLE`
   - `GHOSTED`
   - `HIDDEN`
4. Surface-specific code only consumes the final decision.

### 4. UI flow
1. `/playeravatar` goes through `command/PlayerAvatarControlCommand`.
2. `bootstrap/PlayerAvatarMarkerPlugin` opens `ui/PlayerAvatarControlPage`.
3. The page reads active players, settings, avatar previews, and shared visibility decisions.

## Intentional cleanup during the reorganization

The following dead compatibility leftovers were removed because they were no longer referenced anywhere:

- `PlayerAvatarStaticAssets.java`
- `PlayerAvatarVanishSupport.java`

This keeps the codebase smaller and reduces confusion for future work.

## Maintenance guidelines

When adding new code, place it by responsibility:

- new map marker logic -> `marker/`
- new optional mod bridge -> `compat/...`
- new player setting persistence -> `settings/`
- new visibility rules -> `visibility/`
- new vanish mod integration -> `vanish/providers/`
- new file/path helpers -> `storage/` or `world/`

If a feature touches multiple folders, keep each folder focused on its own responsibility instead of centralizing everything into one large helper class.


