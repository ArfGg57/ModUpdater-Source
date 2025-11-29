# ModUpdater

**A Forge 1.7.10 mod that automatically updates mods, configs, and files from a remote server.**

ModUpdater is designed for modpack creators who want to provide automatic updates to their users. When players launch the game, ModUpdater checks for updates, shows what will change, and handles all the downloading and file management automatically. It also keeps itself up to date with automatic self-updates.

---

## Table of Contents

- [Features](#features)
- [Quick Start for Modpack Creators](#quick-start-for-modpack-creators)
  - [1. Set Up Your Remote Config](#1-set-up-your-remote-config)
  - [2. Configure mods.json](#2-configure-modsjson)
  - [3. Configure files.json](#3-configure-filesjson)
  - [4. Configure deletes.json](#4-configure-deletesjson)
  - [5. Distribute ModUpdater](#5-distribute-modupdater)
- [Configuration Reference](#configuration-reference)
  - [Remote Config (config.json)](#remote-config-configjson)
  - [Mods Configuration (mods.json)](#mods-configuration-modsjson)
  - [Files Configuration (files.json)](#files-configuration-filesjson)
  - [Deletes Configuration (deletes.json)](#deletes-configuration-deletesjson)
- [Self-Update System](#self-update-system)
- [How Updates Work](#how-updates-work)
- [Building from Source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)

---

## Features

- 🔄 **Automatic mod updates** from CurseForge, Modrinth, or direct URLs
- 📦 **Config file synchronization** with intelligent tracking
- 🔍 **Smart rename detection** using SHA-256 hashes
- 🔒 **Robust file lock handling** for Windows compatibility
- 💥 **Automatic restart enforcement** when locked files prevent updates
- ✨ **Clean, modern UI** for update confirmations
- 🔁 **Automatic self-updates** - ModUpdater keeps itself up to date
- 📊 **Comprehensive logging** and error handling
- ⚡ **Early-load coremod** for handling locked files before mods load
- 🗑️ **Version-specific deletion system** for precise cleanup control

---

## Quick Start for Modpack Creators

### 1. Set Up Your Remote Config

Host your configuration files on a web server (GitHub raw files work great). You'll need:

- A main config file that points to your mods/files/deletes configs
- A `mods.json` file listing all mods to install
- A `files.json` file for auxiliary files (configs, resources, etc.)
- A `deletes.json` file for files that should be removed in specific versions

**Example folder structure on GitHub:**
```
your-modpack-config/
├── config.json        # Main config with modpack version
├── mods.json          # List of mods
├── files.json         # Additional files
└── deletes.json       # Files to delete
```

**config.json (hosted on your server):**
```json
{
  "modpackVersion": "1.0.0",
  "configsBaseUrl": "https://raw.githubusercontent.com/YourUser/your-modpack-config/main/",
  "modsJson": "mods.json",
  "filesJson": "files.json",
  "deletesJson": "deletes.json",
  "checkCurrentVersion": true,
  "maxRetries": 3,
  "backupKeep": 5
}
```

### 2. Configure mods.json

Create a JSON array listing all mods in your modpack:

```json
[
  {
    "since": "1.0.0",
    "numberId": "1",
    "installLocation": "mods",
    "display_name": "JourneyMap",
    "file_name": "",
    "hash": "abc123def456...",
    "source": {
      "type": "curseforge",
      "projectId": 32274,
      "fileId": 3867367
    }
  },
  {
    "since": "1.0.0",
    "numberId": "2",
    "installLocation": "mods",
    "display_name": "OptiFine",
    "source": {
      "type": "url",
      "url": "https://example.com/optifine.jar"
    }
  },
  {
    "since": "1.0.0",
    "numberId": "3",
    "installLocation": "mods",
    "display_name": "Sodium",
    "source": {
      "type": "modrinth",
      "versionId": "rAfhHfow"
    }
  }
]
```

**Field Reference:**

| Field | Required | Description |
|-------|----------|-------------|
| `since` | Yes | Version when this mod was added to the modpack |
| `numberId` | Yes | Unique identifier for this mod (must be unique across all mods) |
| `installLocation` | Yes | Directory to install the mod (usually "mods") |
| `display_name` | No | Human-readable name for the UI |
| `file_name` | No | Custom filename (if empty, uses source filename) |
| `hash` | No | SHA-256 hash for verification (recommended) |
| `source` | Yes | Download source (see source types below) |

**Source Types:**

1. **CurseForge:**
```json
"source": {
  "type": "curseforge",
  "projectId": 32274,
  "fileId": 3867367
}
```

2. **Modrinth:**
```json
"source": {
  "type": "modrinth",
  "versionId": "rAfhHfow"
}
```

3. **Direct URL:**
```json
"source": {
  "type": "url",
  "url": "https://example.com/mod.jar"
}
```

### 3. Configure files.json

For auxiliary files (configs, resources, etc.):

```json
{
  "files": [
    {
      "since": "1.0.0",
      "url": "https://example.com/config/myconfig.cfg",
      "downloadPath": "config/",
      "file_name": "myconfig.cfg",
      "display_name": "My Config File",
      "overwrite": true,
      "hash": "abc123..."
    }
  ]
}
```

**Field Reference:**

| Field | Required | Description |
|-------|----------|-------------|
| `since` | Yes | Version when this file was added |
| `url` | Yes | Download URL |
| `downloadPath` | Yes | Directory to install to |
| `file_name` | No | Custom filename |
| `display_name` | No | Human-readable name |
| `overwrite` | No | Whether to replace existing files (default: true) |
| `hash` | No | SHA-256 hash for verification |

### 4. Configure deletes.json

For files that should be removed in specific versions:

```json
{
  "deletes": [
    {
      "since": "1.1.0",
      "type": "file",
      "path": "config/oldconfig.cfg",
      "reason": "Replaced with new config system"
    },
    {
      "since": "1.2.0",
      "type": "folder",
      "path": "mods/deprecated/",
      "reason": "Removed deprecated mods folder"
    }
  ]
}
```

**Field Reference:**

| Field | Required | Description |
|-------|----------|-------------|
| `since` | Yes | Version when this deletion applies |
| `type` | Yes | "file" or "folder" |
| `path` | Yes | Path to delete |
| `reason` | No | Human-readable explanation |

### 5. Distribute ModUpdater

1. Build ModUpdater (see [Building from Source](#building-from-source))
2. Include these files in your modpack:
   - `mods/!!!!!modupdater-X.XX.jar` - Main updater
   - `mods/!!!!!modupdater-mod-X.XX.jar` - Post-restart handler
   - `config/ModUpdater/config.json` - Local config pointing to your remote

**Local config.json (`config/ModUpdater/config.json`):**
```json
{
  "remote_config_url": "https://raw.githubusercontent.com/YourUser/your-modpack-config/main/config.json"
}
```

---

## Configuration Reference

### Remote Config (config.json)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `modpackVersion` | string | Yes | Current modpack version (semver format) |
| `configsBaseUrl` | string | Yes | Base URL for mods.json, files.json, deletes.json |
| `modsJson` | string | No | Filename of mods list (default: "mods.json") |
| `filesJson` | string | No | Filename of files list (default: "files.json") |
| `deletesJson` | string | No | Filename of deletes list (default: "deletes.json") |
| `checkCurrentVersion` | boolean | No | Verify files even when version matches (default: true) |
| `maxRetries` | number | No | Download retry count (default: 3) |
| `backupKeep` | number | No | Number of backups to keep (default: 5) |
| `debugMode` | boolean | No | Enable verbose logging (default: false) |

### Mods Configuration (mods.json)

See [Configure mods.json](#2-configure-modsjson) above.

### Files Configuration (files.json)

See [Configure files.json](#3-configure-filesjson) above.

### Deletes Configuration (deletes.json)

See [Configure deletes.json](#4-configure-deletesjson) above.

---

## Self-Update System

ModUpdater automatically keeps itself up to date. The system handles updating all three ModUpdater components:

1. **Launchwrapper JAR** (`!!!!!modupdater-X.XX.jar`) - The main tweaker that runs before Forge loads
2. **Mod JAR** (`!!!!!modupdater-mod-X.XX.jar`) - The post-restart handler that completes pending operations
3. **Cleanup JAR** (`modupdater-cleanup-X.XX.jar`) - Helper for cleanup operations

### How Self-Update Works

1. **Configuration Check**: On launch, ModUpdater fetches `current_release.json` from the source repository
2. **GitHub API Check**: It queries the GitHub releases API for the latest release assets
3. **Version Comparison**: Compares installed files against the expected files in the configuration
4. **Filename Prefix Handling**: GitHub doesn't allow `!` characters in filenames, so the config maps GitHub names to local names:
   - GitHub: `modupdater-2.21.jar` → Local: `!!!!!modupdater-2.21.jar`
5. **User Confirmation**: Updates appear in the confirmation dialog alongside mod updates
6. **Installation**: New versions are downloaded and old versions are scheduled for deletion
7. **Restart Handling**: If files are locked (common on Windows), uses the pending operations system

### Configuration File Format (current_release.json)

The `current_release.json` file in the repository root configures the self-update system:

```json
{
  "version": "2.21",
  "filename_prefix": "!!!!!",
  "files": {
    "launchwrapper": {
      "github_name": "modupdater-2.21.jar",
      "local_name": "!!!!!modupdater-2.21.jar",
      "hash": "abc123..."
    },
    "mod": {
      "github_name": "modupdater-mod-2.21.jar",
      "local_name": "!!!!!modupdater-mod-2.21.jar",
      "hash": "def456..."
    },
    "cleanup": {
      "github_name": "modupdater-cleanup-2.21.jar",
      "local_name": "modupdater-cleanup-2.21.jar",
      "hash": "789xyz..."
    }
  }
}
```

**Field Reference:**

| Field | Description |
|-------|-------------|
| `version` | Current release version (e.g., "2.21") |
| `filename_prefix` | Prefix added to filenames locally (e.g., "!!!!!") to ensure early loading |
| `files` | Object containing configuration for each JAR file |
| `files.*.github_name` | Filename as uploaded to GitHub releases (without special characters) |
| `files.*.local_name` | Filename when installed in the mods folder (with prefix if needed) |
| `files.*.hash` | Optional SHA-256 hash for integrity verification |

### Updating current_release.json (For Contributors)

When releasing a new version:

1. Update the `version` field to the new version number
2. Update `github_name` and `local_name` for each file with the new version
3. Optionally update the `hash` fields with SHA-256 hashes of the new JARs
4. Commit and push to the main branch before creating the GitHub release

**Example update from 2.21 to 2.22:**
```json
{
  "version": "2.22",
  "filename_prefix": "!!!!!",
  "files": {
    "launchwrapper": {
      "github_name": "modupdater-2.22.jar",
      "local_name": "!!!!!modupdater-2.22.jar",
      "hash": ""
    },
    "mod": {
      "github_name": "modupdater-mod-2.22.jar",
      "local_name": "!!!!!modupdater-mod-2.22.jar",
      "hash": ""
    },
    "cleanup": {
      "github_name": "modupdater-cleanup-2.22.jar",
      "local_name": "modupdater-cleanup-2.22.jar",
      "hash": ""
    }
  }
}
```

### Partial Updates

The self-update system handles partial updates gracefully:
- If only 1 or 2 files need updating, only those files are downloaded
- If a file is already up to date, it's skipped
- If a file entry is missing from the config, ModUpdater falls back to pattern matching

### Legacy Configuration Support

For backward compatibility, the old configuration format is still supported:

```json
{
  "current_file_name": "!!!!!modupdater-2.21.jar",
  "previous_release_hash": "abc123..."
}
```

This format is automatically converted to the new format internally.

**The self-update URL is hard-coded:**
- API: `https://api.github.com/repos/ArfGg57/ModUpdater-Source/releases/latest`
- Config: `https://raw.githubusercontent.com/ArfGg57/ModUpdater-Source/main/current_release.json`
- Source: `https://github.com/ArfGg57/ModUpdater-Source`

**For modpack creators**: Self-update happens automatically. Your users will always have the latest ModUpdater version.

---

## How Updates Work

### Update Flow

1. **Launch**: User launches Minecraft with ModUpdater installed
2. **Check**: ModUpdater checks for self-updates and mod updates
3. **Dialog**: If updates are available, shows confirmation dialog with:
   - Files to Add (new mods/files and new ModUpdater version)
   - Files to Delete (old versions, outdated mods)
4. **User Choice**: User clicks "Agree" or "Quit"
5. **Download**: If agreed, downloads all new files
6. **Install**: Installs new files, removes old ones
7. **Locked Files**: If any files are locked (common on Windows):
   - Schedules them for deletion after restart
   - Triggers a game crash to force restart
8. **Restart**: On next launch, pending operations complete

### File Locking

On Windows, JAR files loaded by Forge cannot be deleted until the JVM exits. ModUpdater handles this with:

1. **Pending Operations**: Locked deletions are saved to `pending-update-ops.json`
2. **Crash Enforcement**: Game crashes with clear explanation
3. **Post-Restart Handler**: On next launch, completes pending operations

### Backup System

Before any file is deleted or replaced:
1. Backed up to `modupdater/backup/[timestamp]/`
2. Old backups are pruned (keeps 5 by default)

---

## Building from Source

### Prerequisites

- **Java 8** (JDK 1.8.x) - Required for Forge 1.7.10 compatibility
- **Git** for cloning the repository

### Build Steps

```bash
# Clone the repository
git clone https://github.com/ArfGg57/ModUpdater-Source.git
cd ModUpdater-Source

# Build using Gradle wrapper
./gradlew clean build     # Linux/macOS
gradlew.bat clean build   # Windows
```

### Output Files

After building, find the JARs in:
- `modupdater-launchwrapper/build/libs/!!!!!modupdater-X.XX.jar` - Main tweaker
- `modupdater-mod/build/libs/!!!!!modupdater-mod-X.XX.jar` - Post-restart handler

**Note**: The "!!!!!" prefix ensures early loading in Forge's mod sorting.

### Installation

Copy both JAR files to your `.minecraft/mods/` folder.

---

## Troubleshooting

### Mod keeps re-downloading

**Cause**: Hash mismatch or incorrect source IDs

**Solution**:
1. Verify the `hash` field matches the actual file
2. Check `projectId`/`fileId` or `versionId` are correct
3. Delete `config/ModUpdater/mod_metadata.json` to rebuild cache

### Old mod version not removed

**Cause**: Mod not tracked in metadata

**Solution**:
1. Ensure the mod has a unique `numberId`
2. Delete it manually - ModUpdater won't reinstall if it's not in mods.json

### Update dialog never appears

**Cause**: `remote_config_url` is empty or unreachable

**Solution**:
1. Check `config/ModUpdater/config.json` exists and has correct URL
2. Verify the URL is accessible in a browser
3. Check firewall/proxy settings

### Game crashes on update

**Cause**: Normal behavior when files are locked

**Solution**: This is expected! Restart the game and updates will complete.

### Files not being deleted

**Cause**: Delete entries may have wrong `since` version

**Solution**: Ensure `since` version is greater than user's current version

---

## Project Structure

```
ModUpdater-Source/
├── modupdater-core/           # Core update logic
│   └── src/main/java/com/ArfGg57/modupdater/
│       ├── core/              # Main UpdaterCore
│       ├── deletion/          # Deletion processing
│       ├── hash/              # Hash utilities
│       ├── metadata/          # Mod tracking
│       ├── pending/           # Pending operations
│       ├── selfupdate/        # Self-update system
│       └── ui/                # User interface dialogs
├── modupdater-launchwrapper/  # Tweaker for early loading
├── modupdater-mod/            # Post-restart handler
├── modupdater-cleanup/        # Cleanup helper JAR
├── modupdater-standalone/     # Standalone launcher
├── current_release.json       # Current release info for self-update
└── README.md                  # This file
```

---

## Updating Your Modpack

### Adding a Mod

1. Add entry to `mods.json` with new `numberId`
2. Set `since` to current modpack version
3. Increment `modpackVersion` in config.json

### Removing a Mod

1. Delete the mod entry from `mods.json`
2. The file will be removed from user's mods folder on next update

### Updating a Mod

1. Change the `fileId` (CurseForge), `versionId` (Modrinth), or `url`
2. Update the `hash` if you have one
3. Increment `modpackVersion`

### Updating a Config File

1. Update the file at the hosted URL
2. Update the `hash` in `files.json`
3. Set `overwrite: true` if you want to replace user changes

---

## Best Practices

1. **Always include hashes** for security and integrity
2. **Use unique `numberId`** values across all mods
3. **Test changes** in a development environment first
4. **Increment `modpackVersion`** with every update
5. **Keep backups** of your config files
6. **Document changes** in version notes for users

---

## Disclaimer

This mod is largely AI-generated. The entire plan and layout was created by the repository owner, with significant effort put into refining the code and ensuring quality.

---

## Support

- **Issues**: [GitHub Issues](https://github.com/ArfGg57/ModUpdater-Source/issues)
- **Source**: [GitHub Repository](https://github.com/ArfGg57/ModUpdater-Source)