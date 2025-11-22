# ModUpdater

A Forge 1.7.10 mod that automatically updates mods, configs, and files from a remote server.

## Features

- 🔄 Automatic mod updates from CurseForge, Modrinth, or direct URLs
- 📦 Config file synchronization with intelligent tracking
- 🔍 Smart rename detection using SHA-256 hashes
- 🔒 Robust file lock handling for Windows compatibility
- 💥 **Automatic restart enforcement via Forge crash when locked files prevent update**
- ✨ Clean, modern UI for update confirmations
- 📊 Comprehensive logging and error handling
- 🎯 **NEW:** Intelligent filename extension inference
- ⚡ **NEW:** Early-load coremod for locked file handling
- 🛡️ **NEW:** Enhanced pending operations system (MOVE/DELETE/REPLACE)
- 📋 **NEW:** Unified manifest tracking for mods AND auxiliary files
- 🗑️ **NEW:** Version-specific deletion system with safety mode
- 🔁 **NEW:** Self-update system for ModUpdater itself
- ✅ **FIXED:** Idempotent updates - no repeated downloads of unchanged files
- 🗑️ **FIXED:** Delete tracking - operations only execute once
- 🔄 **FIXED:** Smart overwrite - only replaces on actual content change
- 🔧 **FIXED:** Rename failures no longer trigger unnecessary re-downloads

## Quick Start

See [docs/QUICK_START.md](docs/QUICK_START.md) for setup instructions.

## Building from Source

See [BUILD_GUIDE.md](BUILD_GUIDE.md) for detailed build instructions, including:
- Required Java version and tools
- Gradle build commands
- Which JAR to distribute (launchwrapper module)
- Testing the crash/restart feature
- Troubleshooting common build issues

## Restart Enforcement

When updates cannot complete due to locked files (common on Windows), ModUpdater automatically enforces a restart:

1. **Detection**: UpdaterCore sets `modupdater.restartRequired=true` when files are locked
2. **Deferred Crash**: After the main menu appears, ModUpdater triggers a Forge crash after a 3-tick delay
3. **Crash Report**: Includes detailed information about locked files and restart reason
4. **User Experience**: Clean Forge crash report explaining restart is needed

This ensures users can't continue playing with outdated mods when updates fail. The crash uses only Forge 1.7.10 compatible APIs (tick events, CrashReport) - no reliance on newer Minecraft scheduling methods.

See [BUILD_GUIDE.md](BUILD_GUIDE.md) for testing instructions and configuration options.

## New Features

### Filename Extension Resolution

The updater now automatically infers file extensions when missing from configuration:

```json
{
  "file_name": "betterleaves",
  "source": { "url": "https://example.com/betterleaves.jar" }
}
```

Saves as `betterleaves.jar` automatically! See [docs/FILENAME_RESOLUTION.md](docs/FILENAME_RESOLUTION.md) for details.

### Coremod for Early Operations

Optional coremod support for processing pending file operations before mods load:
- Handles locked files more reliably on Windows
- Processes deferred operations from previous runs (MOVE/DELETE/REPLACE)
- Runs before FML scans mods directory
- Automatically configured via manifest attributes

See [docs/COREMOD_SETUP.md](docs/COREMOD_SETUP.md) for setup instructions.

## Early Loading System

ModUpdater includes a sophisticated early-loading system that ensures reliable file operations **before mods are loaded**:

### How It Works

1. **Coremod Phase** (Earliest - Before mod scanning)
   - `ModUpdaterCoremod` loads as an FML coremod plugin (SortingIndex=1)
   - Processes any pending file operations from `config/ModUpdater/pending-ops.json`
   - **NEW:** Fetches remote config headlessly (no GUI)
   - **NEW:** Scans for outdated mods and deletes them immediately
   - **NEW:** All cleanup happens BEFORE Forge scans/loads mods
   - Marks early phase as completed

2. **Tweaker Phase** (Early - Before Minecraft launches)
   - `UpdaterTweaker` runs as a Launchwrapper tweaker
   - Shows confirmation dialog for updates
   - Checks if coremod already performed cleanup
   - Performs main update logic (downloads new mods) if user agrees

3. **PreInit Phase** (Normal - After mods load)
   - `ModUpdater` @Mod preInit handler
   - Checks if early phases already completed
   - Only runs if coremod/tweaker didn't execute (backward compatibility)

### Benefits

- **Prevents Loading Old Mods**: Outdated mods are deleted BEFORE Forge can scan and initialize them
- **No Conflicts**: Duplicate mod versions never loaded simultaneously
- **First-Run Deletion**: Files deleted/renamed on first launch, no restart needed
- **Prevents File Locks**: Operations execute before Forge locks JAR files
- **Immediate Deletion**: Outdated mods can be deleted immediately instead of requiring restart
- **Atomic Replacements**: New versions replace old ones cleanly without conflicts
- **Windows Compatibility**: Significantly improves reliability on Windows systems
- **Graceful Fallback**: If immediate operations fail, they're deferred to next startup

### Early Cleanup Process

The coremod performs these steps before any mods load:

1. **Fetch Remote Config**: Downloads latest mods.json (headless operation)
2. **Build Valid Mod List**: Determines which mods should be present
3. **Load Metadata**: Reads tracking info for installed mods
4. **Scan Mod Folders**: Checks each installed mod against valid list
5. **Identify Outdated**: Finds mods that are no longer in config
6. **Delete Immediately**: Removes outdated mods with 5 retry attempts
7. **Update Metadata**: Removes deleted mods from tracking database

### Pending Operations

When a file cannot be deleted/moved immediately (e.g., still locked), the operation is:
1. Scheduled using `File.deleteOnExit()` for JVM exit cleanup
2. Written to `config/ModUpdater/pending-ops.json` for next startup
3. Processed by the coremod on next launch before files are locked again

Each pending operation includes:
- **Type**: DELETE, MOVE, or REPLACE
- **Paths**: Source, target, and staged paths as needed
- **Reason**: Human-readable explanation for debugging
- **Timestamps**: When scheduled and when executed
- **Idempotency**: Operations check if already completed before attempting

### Unified Manifest for Files and Mods

The updater now tracks both mods and auxiliary files (configs, resources) in a unified manifest:
- Prevents repeated downloads of already-present files
- Tracks checksums for integrity verification
- Supports migration from previous metadata format
- No configuration changes needed - works automatically!

### Version-Specific Deletion System

The new deletion system provides precise control over file and folder cleanup:
- **Version range logic**: Deletions only apply when transitioning through specific versions
- **Safety mode**: Optional restriction to only delete from config/ directory
- **File vs folder distinction**: Properly handles both types with recursive folder deletion
- **Legacy format detection**: Automatically detects old format and provides migration guidance

See [docs/CONFIG.md](docs/CONFIG.md) for complete documentation and examples.

### Recent Comprehensive Fixes

Recent updates have fixed several critical issues:
- **Idempotent Updates**: Files with unchanged checksums are no longer re-downloaded on every run
- **Smart Overwrite Logic**: `overwrite=true` now only replaces files when content actually changes
- **Delete Tracking**: Delete operations are marked as completed and never re-proposed
- **Version Tracking**: Auxiliary files now support optional version tracking in manifest
- **Rename Handling**: Failed renames no longer trigger unnecessary re-downloads

See [docs/FIXES_COMPREHENSIVE_v3.md](docs/FIXES_COMPREHENSIVE_v3.md) for complete details.

## Self-Update System

ModUpdater can now automatically update itself! The self-update system:

- **Automatic Detection**: Checks GitHub Releases for new versions
- **Secure Downloads**: SHA-256 hash verification
- **Safe Installation**: Atomic JAR replacement using bootstrap launcher
- **Rollback Support**: Automatic backup and recovery on failure
- **User Control**: Configurable update intervals and prompts

### How It Works

1. ModUpdater checks for updates at startup (default: every 24 hours)
2. If a newer version is found, it downloads and verifies the JAR
3. Update is staged for installation on next Minecraft launch
4. Bootstrap script replaces the old JAR with the new one
5. Minecraft continues launching with the updated ModUpdater

### Configuration

Self-update is **enabled by default**. To customize:

```json
// config/ModUpdater/self_update.json
{
  "enabled": true,
  "source_type": "github_releases",
  "github_repo": "ArfGg57/ModUpdater-Source",
  "check_interval_hours": 24,
  "auto_install": false
}
```

See [SELF_UPDATE.md](SELF_UPDATE.md) for complete documentation including:
- Setup instructions
- Release process
- Signing procedure
- Troubleshooting guide
- Security considerations

## Documentation

- **User Guides**
  - [Quick Start Guide](docs/QUICK_START.md) - Get started quickly
  - [Self-Update Guide](SELF_UPDATE.md) - Automatic ModUpdater updates
  - [Mods JSON Schema](docs/MODS_JSON_SCHEMA.md) - Configuration format reference
  - [Deletion Configuration](docs/CONFIG.md) - Version-specific deletion system
  - [Filename Resolution](docs/FILENAME_RESOLUTION.md) - Extension inference guide
  - [Coremod Setup](docs/COREMOD_SETUP.md) - Early-load configuration

- **Testing & Validation**
  - [Testing Guide](docs/TESTING_GUIDE.md) - General testing procedures
  - [Manual Testing](docs/TESTING_MANUAL.md) - Step-by-step manual tests
  - [Refactoring Tests](docs/TESTING_REFACTORING.md) - Tests for recent refactoring
  - [Validation Guide](docs/VALIDATION.md) - Validation procedures

- **Technical Documentation**
  - [Refactoring Guide](docs/REFACTORING_GUIDE.md) - Architecture and design
  - [Security Summary](docs/SECURITY_SUMMARY.md) - Security analysis
  - [Implementation Summary](docs/IMPLEMENTATION_SUMMARY.md) - Implementation details
  - [Comprehensive Fixes v3](docs/FIXES_COMPREHENSIVE_v3.md) - Recent bug fixes and enhancements
  - [PR Summaries](docs/PR_SUMMARY_REFACTORING.md) - Pull request details
  - [Fix Summaries](docs/FIX_SUMMARY_v2.md) - Bug fix details

## Project Structure

```
ModUpdater-Source/
├── modupdater-core/          # Core update logic
│   └── src/main/java/com/ArfGg57/modupdater/
│       ├── hash/             # Hash utilities and rename detection
│       ├── metadata/         # Metadata management
│       └── util/             # General utilities
├── modupdater-launchwrapper/ # LaunchWrapper integration
├── modupdater-standalone/    # Standalone launcher
└── docs/                     # Documentation
```

## Building

```bash
./gradlew build
```

Requires Java 8 for compatibility with Forge 1.7.10.

## Disclaimer

This mod is largely AI-generated. The entire plan and layout was created by the repository owner, with significant effort put into refining the code and ensuring quality.

