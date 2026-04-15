# PlayerAvatarMarker

A Hytale server mod that shows a unique avatar portrait for each player on the large world map and, when available, on the minimap and compass through FastMiniMap and BetterMap integration.

Version 2.0.0 adds a built-in control UI so each player can choose where avatar markers appear.

## Features

- Circular avatar portrait for each online player on the large world map
- Avatars fetched from hyvatar.io and cached in memory
- Unlimited players supported
- Player nickname shown under the avatar marker
- Lower-latency position updates through live movement tracking
- Suppresses the duplicate vanilla player marker
- BetterMap compatibility through a dedicated radar provider
- FastMiniMap overlay support when FastMiniMap is installed
- Built-in `/playeravatar` UI with per-player map, minimap, and compass toggles
- Online player list with avatar preview and nickname inside the UI
- Configurable: nickname visibility, avatar size, rotation, background

## Installation

1. Copy `PlayerAvatarMarker-2.0.0.jar` to `UserData/Saves/<YourWorld>/mods/`
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

Output: `build/libs/PlayerAvatarMarker-2.0.0.jar`

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

## 2.0.0 Highlights

- Added a custom in-game control UI for PlayerAvatarMarker
- Added per-player visibility toggles for the large map, FastMiniMap, and BetterMap compass
- Added an online players panel with avatar preview and nickname list
- Enabled packaged UI assets directly from the mod asset pack

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
