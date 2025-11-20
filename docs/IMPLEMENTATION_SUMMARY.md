# Mod Synchronization Implementation Summary

## Overview

This implementation provides automatic synchronization of the client's mod folder with a remote `mods.json` configuration. The system uses metadata-based identification to track mods by their source identifiers (CurseForge projectId/fileId, Modrinth versionId) rather than filenames, enabling robust handling of updates, renames, and deletions.

## Problem Solved

Previously, the mod updater couldn't reliably track mods when:
- File names changed between versions (testv1.jar → testv2.jar → oopsv19.jar)
- Mods were renamed on disk
- Old mod versions needed to be removed
- The system needed to verify installation regardless of version numbers

## Solution

### 1. Metadata-Based Tracking (`ModMetadata.java`)

A new class that maintains a persistent cache of installed mods with their:
- `numberId`: Unique identifier for the mod entry
- `fileName`: Current filename on disk
- `hash`: SHA-256 hash for verification
- Source identifiers:
  - CurseForge: `projectId` + `fileId`
  - Modrinth: `projectSlug` + `versionId`
  - Direct URL: `url`

This metadata is stored in `config/ModUpdater/mod_metadata.json` and persists across runs.

### 2. Enhanced Synchronization Logic (`UpdaterCore.java`)

The mod installation phase now follows this process:

#### Step 1: Load Metadata
- Load the metadata cache from disk
- Build a map of expected mods from `mods.json`

#### Step 2: Cleanup Phase
- Scan the mods folder
- For each file:
  1. Check if it's tracked in metadata
  2. Check if the tracked mod is in the current `mods.json`
  3. Check if it has the correct version (via source identifiers)
  4. If not valid: backup and delete the file

This ensures only current, valid mods remain in the folder.

#### Step 3: Install/Update Phase
For each mod in `mods.json`:
1. Check metadata to see if correct version is installed
2. Verify file exists and hash matches (if hash provided)
3. If installed and correct:
   - Rename if filename doesn't match desired name
   - Update metadata if needed
4. If not installed or wrong version:
   - Download new version
   - Delete old version(s)
   - Install new version
   - Update metadata

#### Step 4: Save Metadata
- Persist the updated metadata cache to disk

### 3. New mods.json Schema

Enhanced schema with additional fields:

```json
{
  "since": "1.1.0",
  "numberId": "1",
  "installLocation": "mods",
  "file_name": "",           // NEW: Override filename
  "display_name": "Woof1",   // NEW: Human-readable name
  "hash": "",                // Optional: SHA-256 hash
  "source": {
    "type": "curseforge",    // or "modrinth" or "url"
    "projectId": 255308,
    "fileId": 5789438
  }
}
```

## How It Solves the Requirements

### ✅ Detect Outdated Mods
- Uses source identifiers (projectId/fileId, versionId) to identify mods
- If a file in the folder doesn't match the expected source identifiers in `mods.json`, it's considered outdated
- Example: testv1.jar with fileId=1000 is outdated if mods.json specifies fileId=2000

### ✅ Handle Renamed Mods
- Metadata tracks the mod by numberId and source identifiers, not filename
- If testv1.jar is renamed to oopsv19.jar manually, the system:
  1. Finds it via metadata (numberId + source match)
  2. Recognizes it's the correct mod
  3. Optionally renames it back to the standard name

### ✅ Handle Deleted Mods
- If a mod file is deleted but still in `mods.json`, the system:
  1. Checks metadata and finds it missing
  2. Downloads and installs it again

### ✅ Remove Old Versions
- When a mod updates in `mods.json` (new fileId/versionId):
  1. Cleanup phase identifies old files via metadata
  2. Old version is backed up and deleted
  3. New version is downloaded and installed
  4. Metadata is updated with new source identifiers

### ✅ Version-Independent Verification
- Verification happens every run, regardless of modpack version numbers
- The `since` field is used only for filtering, not for skip logic
- Even if local and remote versions match, all mods are verified

## Backwards Compatibility

The system maintains backwards compatibility with the old naming scheme:
- Old mods with `numberId-filename.jar` pattern are detected
- They're added to metadata on first run
- Continue to work normally

## File Structure

```
config/ModUpdater/
├── config.json              # Contains remote_config_url
├── modpack_version.json     # Current applied version
└── mod_metadata.json        # NEW: Mod metadata cache

mods/
├── 1-JourneyMap-1.7.10.jar
├── 2-OptiFine.jar
└── 3-AppliedEnergistics2.jar
```

## Benefits

1. **Reliable Tracking**: Mods identified by source, not filename
2. **Automatic Cleanup**: Old versions removed automatically
3. **Rename Tolerance**: Manual renames don't break anything
4. **Self-Healing**: Deleted mods are reinstalled
5. **Always Current**: Every run ensures mods match configuration
6. **Safe Operations**: All deletions are backed up first

## Testing Scenarios

### Scenario 1: Update from v1.0.0 to v1.1.0
- Client has: testv1.jar (fileId=1000)
- Server has: testv2.jar (fileId=2000)
- Result: testv1.jar removed, testv2.jar installed

### Scenario 2: Manual Rename
- Client has: 1-JourneyMap.jar renamed to MyMap.jar
- Metadata tracks: numberId=1, fileName=MyMap.jar
- Result: Recognized as valid, optionally renamed to standard name

### Scenario 3: Manual Deletion
- Client deletes: 1-JourneyMap.jar
- Metadata has: numberId=1, projectId=32274, fileId=3867367
- Result: File re-downloaded and installed

### Scenario 4: Modpack Change
- Server removes mod numberId=2 from mods.json
- Client has: 2-OptiFine.jar
- Result: File backed up and removed (no longer in mods.json)

## Security

- SHA-256 hash verification for all downloads
- Backup before deletion (in `modupdater/backup/`)
- Atomic file operations to prevent corruption
- No new security vulnerabilities introduced (verified by CodeQL)

## Documentation

- `MODS_JSON_SCHEMA.md`: Complete schema documentation
- `example_mods.json`: Working example with all source types
- Inline code comments explain the logic

## Performance

- Metadata loaded once at start
- Efficient map-based lookups
- Minimal API calls (only for mods needing download)
- Backwards compatible, no breaking changes

## Future Enhancements

Possible improvements for future versions:
- Parallel mod downloads
- Delta updates for large mods
- Mod dependency resolution
- Conflict detection between mods
