# Release Guide

Instructions for publishing **PlayerAvatarMarker** on GitHub and CurseForge.

---

## 1. Build the release jar

```bash
cd PlayerAvatarMarker
./gradlew clean build
```

The output jar is at:
```
build/libs/PlayerAvatarMarker-1.1.0.jar
```

Verify it works by copying to your server's `mods/` folder and starting the server.
The console should show:
```
[PlayerAvatarMarker] Starting v1.1.0
[PlayerAvatarMarker] Provider registered: <world> (TPS set to 20)
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
2. **Tag:** `v1.0.0`
3. **Release title:** `PlayerAvatarMarker v1.0.0`
4. **Description:**

```markdown
## PlayerAvatarMarker v1.0.0

Shows unique player avatar portraits on the Hytale world map, fetched from [hyvatar.io](https://hyvatar.io).

### Features
- Circular avatar portrait for each online player on the world map
- Avatars fetched from hyvatar.io, cached in memory — no repeated downloads
- Unlimited players supported
- Player nickname shown below their avatar
- Suppresses the default blue player indicator — replaced by the custom avatar
- Real-time position sync (20 TPS)
- Configurable: background, background color, rotation, nickname visibility

### Installation
1. Copy `PlayerAvatarMarker-1.0.0.jar` to `UserData/Saves/<YourWorld>/mods/`
2. Start the server — no extra arguments required

### Configuration
Config is auto-generated at `mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`:
```json
{
  "enableRotation": false,
  "enableBackground": false,
  "backgroundColor": "#2D2D2D",
  "showNickname": true
}
```

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

5. **Attach:** `PlayerAvatarMarker-1.0.0.jar`
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
| **Summary** | Shows unique hyvatar.io avatar portraits for each player on the Hytale world map. |
| **Description** | *(see below)* |
| **Categories** | Server Utility, World Map |
| **License** | AGPL-3.0 |
| **Source URL** | `https://github.com/maksimovc/PlayerAvatarMarker` |
| **Issues URL** | `https://github.com/maksimovc/PlayerAvatarMarker/issues` |

### CurseForge project description

```markdown
## PlayerAvatarMarker

A Hytale server mod that shows **unique avatar portraits** for each player on the world map
(opened with the **M** key), fetched from [hyvatar.io](https://hyvatar.io).

### Features
- Circular avatar portrait for each online player on the world map
- Avatars fetched from hyvatar.io on first join, cached in memory
- Unlimited players — no slot limit
- Player nickname shown below their avatar
- Suppresses the default blue "Ви" player indicator — replaced with the custom avatar
- Real-time marker position sync at 20 TPS
- Configurable: background circle, background color, rotation, nickname visibility
- Fully compatible with BetterMap and other map mods (provider key `playerIcons`)

### Installation
1. Download the jar from the Files tab
2. Copy `PlayerAvatarMarker-1.0.0.jar` to `UserData/Saves/<YourWorld>/mods/`
3. Start the server — no extra flags required

### Configuration
Config is auto-generated at:
`UserData/Saves/<YourWorld>/mods/PlayerAvatarMarkerAssets/playeravatarmarker-config.json`

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
| `enableRotation` | `false` | Rotate icon to face player direction |
| `enableBackground` | `false` | Draw filled circle behind the avatar |
| `backgroundColor` | `"#2D2D2D"` | Background color (hex) |
| `showNickname` | `true` | Show player name below avatar |

### Compatibility
- Works alongside **BetterMap**
- Works alongside **BetterPlayerMarkers**
- Safe with any mod not conflicting on provider key `playerIcons`

### Requirements
- Hytale Server `2026.03.26-89796e57b` or newer
- Java 25
```

### Upload the file

1. Go to your project → **Files** → **Upload File**
2. **File:** `PlayerAvatarMarker-1.0.0.jar`
3. **Display name:** `PlayerAvatarMarker-1.0.0`
4. **Game version:** Hytale (latest)
5. **Release type:** Release
6. **Changelog:**
   ```
   Initial release.
   - Avatar portraits fetched from hyvatar.io and shown on the world map
   - Unlimited players, per-player PNG assets
   - Suppresses default blue player indicator
   - Real-time 20 TPS position sync
   - Configurable background, rotation, nickname
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

## .gitignore (for the repository)

```
.gradle/
build/
*.class
.idea/
*.iml
```
