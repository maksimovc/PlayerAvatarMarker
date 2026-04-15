# Release Guide

Instructions for publishing **PlayerAvatarMarker** on GitHub and CurseForge.

Current release target: `v2.0.0`

---

## 1. Build the release jar

```bash
cd PlayerAvatarMarker
./gradlew clean build
```

The output jar is at:
```
build/libs/PlayerAvatarMarker-2.0.0.jar
```

Verify it works by copying to your server's `mods/` folder and starting the server.
The console should show:
```
[PlayerAvatarMarker] Starting v2.0.0
[PlayerAvatarMarker] Provider registered: <world>
[PlayerAvatarMarker] Ready.
```

When a player joins, you should also see:
```
[PlayerAvatarMarker] Writing avatar for <username> -> pam/pam-<uuid>.png
```

---

## 2. GitHub Release

### Repository setup

1. Create a GitHub repository: `PlayerAvatarMarker`
2. Upload all source files (the entire `PlayerAvatarMarker/` folder)
3. Add a `.gitignore`:
   ```
   .gradle/
   build/
   *.class
   .idea/
   *.iml
   ```

### Create a release

1. Go to **Releases** → **Draft a new release**
2. **Tag:** `v2.0.0`
3. **Release title:** `PlayerAvatarMarker v2.0.0`
4. **Description:**

```markdown
## PlayerAvatarMarker v2.0.0

Shows unique player avatar portraits on the Hytale world map, FastMiniMap, and BetterMap compass, fetched from [hyvatar.io](https://hyvatar.io).

### Features
- Circular avatar portrait for each online player on the world map
- Avatars fetched from hyvatar.io, cached in memory — no repeated downloads
- Unlimited players supported
- Player nickname shown without hover
- Improved multiplayer responsiveness using live movement packet tracking
- BetterMap by Paralaxe compatibility with dedicated player radar integration
- FastMiniMap overlay support when installed
- Built-in `/playeravatar` control UI with per-player surface toggles
- Online player list with avatar preview and nickname
- Configurable: background, background color, rotation, nickname visibility, avatar size

### Commands and permissions
- `/playeravatar` opens the control UI
- `/pam` and `/avatarmarker` are aliases
- `playeravatarmarker.use` opens the page
- `playeravatarmarker.settings.map` changes large map visibility
- `playeravatarmarker.settings.minimap` changes FastMiniMap visibility
- `playeravatarmarker.settings.compass` changes BetterMap compass visibility
- `playeravatarmarker.admin` bypasses the checks above

### Installation
1. Copy `PlayerAvatarMarker-2.0.0.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Start the server — no extra arguments required

### Configuration
Config is auto-generated at `mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`:
```json
{
  "enableRotation": false,
  "enableBackground": true,
  "backgroundColor": "#2D2D2D",
  "showNickname": true,
  "avatarSize": 64
}
```

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

5. **Attach:** `PlayerAvatarMarker-2.0.0.jar`
6. Click **Publish release**

---

## 3. CurseForge Publication

### Project creation

1. Go to [CurseForge](https://www.curseforge.com/) → **Create Project**
2. **Game:** Hytale
3. **Project type:** Server Mod
4. Fill in the project details:

| Field | Value |
|---|---|
| **Project name** | PlayerAvatarMarker |
| **Summary** | Shows unique hyvatar.io avatar portraits for each player on the Hytale world map, minimap, and compass. |
| **Description** | *(see below)* |
| **Categories** | Server Utility, World Map |
| **License** | AGPL-3.0 |
| **Source URL** | `https://github.com/maksimovc/PlayerAvatarMarker` |
| **Issues URL** | `https://github.com/maksimovc/PlayerAvatarMarker/issues` |

### CurseForge project description

```markdown
## PlayerAvatarMarker

A Hytale server mod that shows **unique avatar portraits** for each player on the world map
(opened with the **M** key), and optionally on the minimap or compass, fetched from [hyvatar.io](https://hyvatar.io).

### Features
- Circular avatar portrait for each online player on the world map
- Avatars fetched from hyvatar.io on first join, cached in memory
- Unlimited players — no slot limit
- Player nickname shown without hover
- Lower-latency multiplayer marker updates via live movement packet tracking
- Fixed large-map marker refresh and nickname regressions from the early 1.3.x builds
- Configurable: background circle, background color, rotation, nickname visibility, avatar size
- Fully compatible with BetterMap by Paralaxe and other map mods
- Adds a minimap overlay when FastMiniMap is installed
- Adds a built-in `/playeravatar` UI with per-player map, minimap, and compass toggles
- Shows all online players with avatar preview and nickname in one page

### Installation
1. Download the jar from the Files tab
2. Copy `PlayerAvatarMarker-2.0.0.jar` to `UserData/Saves/<YourWorld>/mods/`
3. Start the server — no extra flags required

### Commands & Permissions
- `/playeravatar` opens the control UI
- `/pam` and `/avatarmarker` are aliases
- `playeravatarmarker.use` opens the UI
- `playeravatarmarker.settings.map` changes large map visibility
- `playeravatarmarker.settings.minimap` changes FastMiniMap visibility
- `playeravatarmarker.settings.compass` changes BetterMap compass visibility
- `playeravatarmarker.admin` bypasses all checks

### Configuration
Config is auto-generated at:
`UserData/Saves/<YourWorld>/mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`

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
| `enableRotation` | `false` | Rotate icon to face player direction |
| `enableBackground` | `true` | Draw filled circle behind the avatar |
| `backgroundColor` | `"#2D2D2D"` | Background color (hex) |
| `showNickname` | `true` | Show player name below avatar |
| `avatarSize` | `64` | Output size for generated avatar marker images |

### Compatibility
- Works alongside **BetterMap by Paralaxe**
- Works alongside **BetterPlayerMarkers**
- Safe with any mod not conflicting on provider key `playerIcons`

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

### Upload the file

1. Go to your project → **Files** → **Upload File**
2. **File:** `PlayerAvatarMarker-2.0.0.jar`
3. **Display name:** `PlayerAvatarMarker-2.0.0`
4. **Game version:** Hytale (latest)
5. **Release type:** Release
6. **Changelog:**
   ```
  - Added a built-in PlayerAvatarMarker control UI.
  - Added per-player toggles for the large map, FastMiniMap, and BetterMap compass.
  - Added an online players panel with avatar preview and nickname list.
  - Packaged the new UI assets directly with the mod.
   ```
7. Click **Upload**

---

## Version bumping

When releasing a new version:

1. Update `version` in `build.gradle.kts`
2. Build: `./gradlew clean build`
3. Create a new GitHub release with the new tag
4. Upload the new jar to CurseForge

---

## Short CurseForge Changelog

```text
- Маркери гравців на великій мапі тепер рухаються значно точніше.
- Виправлені випадки, коли аватарки зникали або оновлювались неправильно.
- Повернено показ ніків під аватарками.
- Покращена сумісність із BetterMap і FastMiniMap.
```

---

## .gitignore (for the repository)

```
.gradle/
build/
*.class
.idea/
*.iml
```
