# PlayerAvatarMarker

PlayerAvatarMarker is a Hytale server mod that replaces the generic player dot with each player's avatar portrait on the large world map and, when companion mods are present, on FastMiniMap and BetterMap surfaces as well.

The current 1.4.0 release includes the built-in control UI, per-player visibility profiles, generated static marker overrides, and the newer plugin data layout under `plugins/PlayerAvatarMarker`.

## Features

- Circular avatar portrait for each online player on the large world map
- Avatars fetched from hyvatar.io and cached for reuse in memory and on disk
- Lower-latency position updates through live movement tracking
- Player nickname shown directly below the avatar marker when enabled
- Suppresses the duplicate vanilla player marker with generated static override assets
- BetterMap compatibility through a dedicated radar-style provider
- FastMiniMap player-overlay support when FastMiniMap is installed
- Built-in `/playeravatar` control UI with per-surface global toggles and per-player overrides
- Online player list with avatar preview and nickname inside the control page
- Configurable nickname visibility, avatar size, facing rotation, and background styling

## Installation

1. Copy `PlayerAvatarMarker-1.4.0.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Start the server

PlayerAvatarMarker is a normal Hytale mod jar and belongs in the world's `mods/` folder. On first start it also creates the companion asset-pack directory `PlayerAvatarMarkerAssets` automatically for static world-map marker overrides.

## Data layout

- Global config: `UserData/Saves/<World>/plugins/PlayerAvatarMarker/playeravatarmarker-config.json`
- Per-player visibility profiles: `UserData/Saves/<World>/plugins/PlayerAvatarMarker/player-settings/<player-uuid>.json`
- Avatar disk cache: `UserData/Saves/<World>/plugins/PlayerAvatarMarker/avatar-cache/`
- Generated static asset pack: `UserData/Saves/<World>/mods/PlayerAvatarMarkerAssets/`

Per-player visibility profiles currently use `schemaVersion: 2` and store the three surface toggles plus per-target override masks. Existing legacy data is migrated into the plugin data directory automatically.

## Configuration

```json
{
  "enableRotation": false,
  "enableBackground": true,
  "backgroundColor": "#2D2D2D",
  "showNickname": true,
  "avatarSize": 64
}
```

| Field | Default | Description |
|---|---|---|
| `enableRotation` | `false` | Rotate the avatar marker to face player direction |
| `enableBackground` | `true` | Draw a filled circle behind the avatar |
| `backgroundColor` | `"#2D2D2D"` | Background circle color |
| `showNickname` | `true` | Show player name under the avatar |
| `avatarSize` | `64` | Output size for generated avatar marker images |

## Compatibility

- Works standalone on the main world map
- Adds a player-avatar overlay to FastMiniMap when FastMiniMap is installed
- Adds a BetterMap-compatible player radar provider when BetterMap is installed
- Safe with mods that do not replace the same world-map player marker provider

## Building from source

```bat
cd PlayerAvatarMarker
gradlew.bat build
```

Output: `build/libs/PlayerAvatarMarker-1.4.0.jar`

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## Commands & Permissions

- Command: `/playeravatar`
- Aliases: `/pam`, `/avatarmarker`
- Open UI permission: `playeravatarmarker.use`
- Map toggle permission: `playeravatarmarker.settings.map`
- Minimap toggle permission: `playeravatarmarker.settings.minimap`
- Compass toggle permission: `playeravatarmarker.settings.compass`
- Admin bypass: `playeravatarmarker.admin`

## 1.4.0 Highlights

- Ships with the built-in PlayerAvatarMarker control UI
- Persists per-player visibility profiles under `plugins/PlayerAvatarMarker/player-settings`
- Uses disk-backed avatar caching under `plugins/PlayerAvatarMarker/avatar-cache`
- Generates and maintains static world-map marker override assets in `mods/PlayerAvatarMarkerAssets`

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
