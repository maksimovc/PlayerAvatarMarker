# PlayerAvatarMarker

A Hytale server mod that shows a unique avatar portrait for each player on the large world map and, when available, on the minimap through FastMiniMap integration.

Each player gets their own generated avatar PNG file (`pam-<uuid>.png`) so the mod supports unlimited players.

## Features

- Circular avatar portrait for each online player on the large world map
- Avatars fetched from hyvatar.io and cached in memory
- Unlimited players supported
- Player nickname shown under the avatar marker
- Lower-latency position updates through live movement tracking
- Suppresses the duplicate vanilla player marker
- BetterMap compatibility through a dedicated radar provider
- FastMiniMap overlay support when FastMiniMap is installed
- Configurable: nickname visibility, avatar size, rotation, background

## Installation

1. Copy `PlayerAvatarMarker-1.3.5.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Start the server

This is a regular Hytale mod, so it belongs in the `mods/` folder.

## Configuration

Config file:
`UserData/Saves/<world>/mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`

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

- Works standalone
- Works with BetterMap by Paralaxe
- Adds a minimap overlay when FastMiniMap is installed
- Safe with mods that do not replace the same world-map player provider

## Building from source

```bat
cd PlayerAvatarMarker
gradlew.bat build
```

Output: `build/libs/PlayerAvatarMarker-1.3.5.jar`

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## Short Changelog: 1.3.0 -> 1.3.5

- Player markers on the large map now follow movement much more accurately
- Fixed cases where avatar markers disappeared or refreshed incorrectly
- Restored nickname display under avatar markers
- Improved BetterMap and FastMiniMap compatibility

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
