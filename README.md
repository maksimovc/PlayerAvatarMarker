# PlayerAvatarMarker

A Hytale server mod that shows unique avatar portraits for each player on the world map, with BetterMap compatibility and lower-latency multiplayer tracking.

Each player gets their own dedicated avatar PNG file (`pam-<uuid>.png`) — unlimited players supported.

---

## Features

- Shows circular avatar portrait for each online player on the world map
- Avatars fetched from hyvatar.io on first appearance, cached in memory
- Unlimited players — no slot limit
- Shows player usernames without needing hover
- Fixes player marker rendering for multiplayer map usage and reduces visible position lag by tracking live `ClientMovement` updates
- Suppresses duplicate default player overlays and keeps the local marker off the compass unless the full map is open
- Compatible with BetterMap by Paralaxe through a dedicated `BetterMapPlayerRadar` provider and player-style markers
- Configurable: nickname visibility, avatar size, rotation, background

## Configuration

Config file is generated automatically at:
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
| `enableRotation` | `false` | Rotate avatar icon to face player direction |
| `enableBackground` | `true` | Draw filled circle behind the avatar |
| `backgroundColor` | `"#2D2D2D"` | Background circle color (hex) |
| `showNickname` | `true` | Show player name below avatar |
| `avatarSize` | `64` | Output size for generated avatar marker images |

## BetterMap

- BetterMap compatibility is enabled automatically when BetterMap by Paralaxe is installed.
- The mod mirrors BetterMap radar distance/privacy handling and uses a dedicated provider key so it does not overwrite unrelated map providers.
- Player labels remain visible and avatar markers use fresher multiplayer movement data than the old `PlayerRef.getTransform()` path.

## Installation

1. Copy `PlayerAvatarMarker-1.2.0.jar` to  
   `UserData/Saves/<YourWorld>/mods/`
2. Start the server — no extra arguments required.

> **Note:** This is a regular Hytale **mod** (not an early plugin). It goes in the `mods/` folder.

## Building from source

```bash
cd PlayerAvatarMarker
./gradlew clean build
```

Output: `build/libs/PlayerAvatarMarker-1.2.0.jar`

## Requirements

- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25

## Compatibility

- Works alongside **BetterMap by Paralaxe**
- Works alongside **BetterPlayerMarkers**
- Safe with any mod that does not conflict on key `playerIcons`

## License

GNU Affero General Public License v3.0 — see [LICENSE](LICENSE)
