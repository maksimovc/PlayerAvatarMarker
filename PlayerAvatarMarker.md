# PlayerAvatarMarker

Shows each online player's avatar portrait on the world map (M key) and optionally on the minimap when [FastMiniMap](https://www.curseforge.com/hytale/mods/fast-mini-map) is installed. Avatars are fetched from [hyvatar.io](https://hyvatar.io/).

## Features

- Circular avatar portrait per player on the world map
- Avatars fetched from hyvatar.io on first join and cached in memory
- No player limit
- Nickname shown below the avatar without hover
- Low-latency position updates via live movement packet tracking
- Suppresses the vanilla duplicate player marker
- Configurable: background circle, background color, nickname visibility, avatar size
- **Minimap overlay** — avatars + nicknames appear on the minimap when FastMiniMap is installed
- Compatible with BetterMap by Paralaxe (respects radar range and visibility settings)

## Installation

1. Copy `PlayerAvatarMarker-1.3.0.jar` to `UserData/Saves/<World>/mods/`
2. Start the server — config is auto-generated on first run

## Configuration

Config path: `UserData/Saves/<World>/mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`

| Key | Default | Description |
|-----|---------|-------------|
| `enableBackground` | `true` | Draw filled circle behind the avatar |
| `backgroundColor` | `"#2D2D2D"` | Background color (hex) |
| `enableRotation` | `false` | Rotate icon to face player direction |
| `showNickname` | `true` | Show player name below avatar |
| `avatarSize` | `64` | Output size for generated avatar images (px) |

## Commands & Permissions

PlayerAvatarMarker has **no commands or permissions**. Avatars are loaded automatically for all online players. All settings are managed via the config file.

## Compatibility

- Standalone — works without any other mods
- Gains **minimap player overlay** with FastMiniMap installed
- Fully compatible with **BetterMap by Paralaxe**
- Compatible with **MapTrail by jadedbay**

## Recommended mods

[![FastMiniMap](https://media.forgecdn.net/avatars/thumbnails/1753/613/256/256/639116048377503958.png)](https://www.curseforge.com/hytale/mods/fast-mini-map)
[![MobMapMarkers](https://media.forgecdn.net/avatars/thumbnails/1739/745/256/256/639107553552050790.png)](https://www.curseforge.com/hytale/mods/mob-map-markers)
