# PlayerAvatarMarker

Shows each online player's avatar portrait on the world map (M key) and optionally on the minimap or compass when [FastMiniMap](https://www.curseforge.com/hytale/mods/fast-mini-map) or BetterMap are installed. Avatars are fetched from [hyvatar.io](https://hyvatar.io/).

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
- **Built-in control UI** — open `/playeravatar` to manage map, minimap, and compass visibility per player
- **Online players panel** — see all online players with avatar preview and nickname in one page

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

- Command: `/playeravatar`
- Aliases: `/pam`, `/avatarmarker`
- Open UI permission: `playeravatarmarker.use`
- Map toggle permission: `playeravatarmarker.settings.map`
- Minimap toggle permission: `playeravatarmarker.settings.minimap`
- Compass toggle permission: `playeravatarmarker.settings.compass`
- Admin bypass: `playeravatarmarker.admin`

## Compatibility

- Standalone — works without any other mods
- Gains **minimap player overlay** with FastMiniMap installed
- Fully compatible with **BetterMap by Paralaxe**
- Compatible with **MapTrail by jadedbay**

## Recommended mods

[![FastMiniMap](https://media.forgecdn.net/avatars/thumbnails/1753/613/256/256/639116048377503958.png)](https://www.curseforge.com/hytale/mods/fast-mini-map)
[![MobMapMarkers](https://media.forgecdn.net/avatars/thumbnails/1739/745/256/256/639107553552050790.png)](https://www.curseforge.com/hytale/mods/mob-map-markers)
