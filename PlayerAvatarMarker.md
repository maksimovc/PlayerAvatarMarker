# PlayerAvatarMarker

Shows each online player's avatar portrait on the world map and optionally on FastMiniMap or BetterMap when those mods are installed. Avatars are fetched from [hyvatar.io](https://hyvatar.io/) and cached under the plugin data directory.

Release `1.5.0` also improves vanish compatibility for HyEssentialsX and reduces dynamic world-map asset races for generated marker images.

## Features

- Circular avatar portrait per player on the world map
- Avatar fetch and reuse through in-memory plus disk-backed caching
- Low-latency position updates via live movement tracking
- Nickname shown below the avatar without hover when enabled
- Suppresses the vanilla duplicate player marker with generated override assets
- Built-in `/playeravatar` UI for map, minimap, and compass visibility control
- Online players panel with avatar preview and nickname list
- FastMiniMap player-overlay support
- BetterMap compatibility that respects viewer settings
- Vanish-aware visibility handling for EliteEssentials, EssentialsPlus, and HyEssentialsX
- Earlier publishing of generated marker assets to reduce temporary missing-image warnings

## Data layout

- Global config: `UserData/Saves/<World>/plugins/PlayerAvatarMarker/playeravatarmarker-config.json`
- Per-player visibility profiles: `UserData/Saves/<World>/plugins/PlayerAvatarMarker/player-settings/<player-uuid>.json`
- Avatar cache: `UserData/Saves/<World>/plugins/PlayerAvatarMarker/avatar-cache/`
- Generated static asset pack: `UserData/Saves/<World>/mods/PlayerAvatarMarkerAssets/`

Per-player visibility profiles currently use `schemaVersion: 2`.

## Configuration

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

- Standalone world-map avatar markers
- Gains a FastMiniMap player overlay when FastMiniMap is installed
- Gains BetterMap radar/compass support when BetterMap is installed

## Recommended mods

[![FastMiniMap](https://media.forgecdn.net/avatars/thumbnails/1753/613/256/256/639116048377503958.png)](https://www.curseforge.com/hytale/mods/fast-mini-map)
[![MobMapMarkers](https://media.forgecdn.net/avatars/thumbnails/1739/745/256/256/639107553552050790.png)](https://www.curseforge.com/hytale/mods/mob-map-markers)
