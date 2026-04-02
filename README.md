# PlayerAvatarMarker

A Hytale server mod that shows **unique avatar portraits** for each player on the world map (opened with the **M** key), fetched from [hyvatar.io](https://hyvatar.io).

Each player gets their own dedicated avatar PNG file (`pam-<uuid>.png`) — unlimited players supported.

---

## Features

- Shows circular avatar portrait for each online player on the world map
- Avatars fetched from hyvatar.io on first appearance, cached in memory
- Unlimited players — no slot limit
- Shows player username below their avatar
- Suppresses the default blue "Ви" player indicator — replaces it with the custom avatar
- Background circle with configurable color (disabled by default)
- Real-time position sync — map updates at 20 TPS
- Configurable: nickname visibility, rotation, background
- Fully compatible with BetterMap and other map mods (unique provider key `playerIcons`)

## Configuration

Config file is generated automatically at:
`UserData/Saves/<world>/mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`

```json
{
  "enableRotation": false,
  "enableBackground": false,
  "backgroundColor": "#2D2D2D",
  "showNickname": true
}
```

| Field | Default | Description |
|---|---|---|
| `enableRotation` | `false` | Rotate avatar icon to face player direction |
| `enableBackground` | `false` | Draw filled circle behind the avatar |
| `backgroundColor` | `"#2D2D2D"` | Background circle color (hex) |
| `showNickname` | `true` | Show player name below avatar |

## Installation

1. Copy `PlayerAvatarMarker-1.0.0.jar` to  
   `UserData/Saves/<YourWorld>/mods/`
2. Start the server — no extra arguments required.

> **Note:** This is a regular Hytale **mod** (not an early plugin). It goes in the `mods/` folder.

## Building from source

```bash
cd PlayerAvatarMarker
./gradlew clean build
```

Output: `build/libs/PlayerAvatarMarker-1.0.0.jar`

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## Compatibility

- Works alongside **BetterMap** — registers under different provider key
- Works alongside **BetterPlayerMarkers**
- Safe with any mod that does not conflict on key `playerIcons`

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
