# Unified Manifest System

## Overview

ModUpdater now uses a unified manifest system that tracks both mods and auxiliary files (configs, resources, etc.) in a single metadata file. This prevents repeated downloads of files that are already present and provides better tracking across updates.

## Location

The unified manifest is stored at: `config/ModUpdater/mod_metadata.json`

## Format

The manifest contains two arrays:
- `mods`: Tracks installed mod JARs with full source information
- `files`: Tracks auxiliary files (configs, resources) with checksums

### Example Structure

```json
{
  "mods": [
    {
      "numberId": "12345",
      "fileName": "examplemod-1.0.0.jar",
      "hash": "a1b2c3...",
      "sourceType": "curseforge",
      "curseforgeProjectId": 12345,
      "curseforgeFileId": 67890
    }
  ],
  "files": [
    {
      "fileName": "ok_johnson.txt",
      "kind": "FILE",
      "version": null,
      "checksum": "d4e5f6...",
      "url": "https://example.com/config/ok_johnson.txt",
      "installLocation": "config/"
    }
  ]
}
```

## How It Works

### For Mods (Existing Behavior)

Mods continue to be tracked by their `numberId` with full source information (CurseForge project/file IDs, Modrinth version IDs, or direct URLs). This allows:
- Smart rename detection
- Version tracking
- Update verification
- Cleanup of outdated versions

### For Auxiliary Files (New Feature)

Auxiliary files are now tracked by filename with:
- **Checksum**: SHA-256 hash for integrity verification
- **URL**: Last download location for reference
- **Install Location**: Target directory (e.g., `config/`)

When the updater processes a file from `files.json`:

1. **Check Manifest First**: Look up the file by name in the manifest
2. **Verify Checksum**: If found, compare checksums
3. **Skip if Match**: File with matching checksum = NO_ACTION
4. **Download if Needed**: Only download if:
   - File not in manifest
   - File missing from disk
   - Checksum mismatch (indicating an update)

### Migration from Old Format

The system is **backward compatible**:
- Existing `mod_metadata.json` files with only `mods` array continue to work
- On first run with new code, auxiliary files are automatically added to manifest
- Files present on disk are scanned, checksums computed, and entries added
- No manual migration needed!

## Benefits

### 1. No Repeated Downloads

**Before**: `config/ok_johnson.txt` flagged as "missing" every run, even if present
```
File missing; will download: config/ok_johnson.txt
```

**After**: File tracked in manifest, no unnecessary download
```
[ModUpdater] File OK (manifest): config/ok_johnson.txt
```

### 2. Integrity Verification

Files are verified by checksum, ensuring:
- Corruption detection
- Update detection when server file changes
- Confidence that files match expected versions

### 3. Smarter Overwrite Logic

The `overwrite` flag now works with checksum tracking:
- `overwrite=false` + file present = skip download, add to manifest
- `overwrite=true` + checksum match = skip download
- `overwrite=true` + checksum mismatch = download new version

### 4. Cleaner Logs

Consistent logging prefixes help identify manifest-tracked files:
```
[ModUpdater] File OK (manifest): config/ok_johnson.txt
[ModUpdater] File hash mismatch; will repair: config/settings.cfg
```

## Configuration Notes

### Important: `file_name` vs `display_name`

In your `files.json` configuration:
- **`file_name`**: The actual filename to save on disk (use this!)
- **`display_name`**: Human-readable name for UI/logs (optional)

The system uses `file_name` for the saved filename. If `file_name` is blank, it extracts the filename from the download URL.

**Never uses `display_name` for the saved filename!**

Example:
```json
{
  "file_name": "ok_johnson.txt",      // ✓ Used for saved filename
  "display_name": "OK Johnson Config", // ✓ Used for display only
  "url": "https://example.com/config/ok_johnson.txt"
}
```

### Hash Field (Optional)

You can provide expected checksums in `files.json`:
```json
{
  "file_name": "settings.cfg",
  "url": "https://example.com/settings.cfg",
  "hash": "a1b2c3d4e5..."  // Optional: SHA-256 hash
}
```

If provided, the updater will:
- Verify the downloaded file matches the hash
- Detect local file corruption
- Only download if hash mismatch detected

If omitted, the updater will:
- Still compute and store hash in manifest
- Use file presence as the main check

## Troubleshooting

### File Shows as "Missing" Despite Being Present

**Possible Causes**:
1. File not yet in manifest (will be added after verification)
2. Checksum mismatch (indicates corruption or update needed)
3. Wrong `file_name` in configuration

**Solution**: Check logs for specific reason. If file exists and hash matches, it will be added to manifest on current run.

### Want to Force Re-download

**Option 1**: Delete the file from disk
**Option 2**: Delete the entry from `mod_metadata.json` under `files` array
**Option 3**: Change the `hash` value in your remote `files.json`

### Migrate Existing Setup

No action needed! On first run with new code:
1. Updater loads existing `mods` array
2. Creates empty `files` array
3. Scans configured files from `files.json`
4. Adds existing files to manifest with computed checksums
5. Subsequent runs use manifest to avoid re-downloads

## Technical Details

### Storage Location
- **File**: `config/ModUpdater/mod_metadata.json`
- **Format**: JSON with pretty-printing (2-space indent)
- **Encoding**: UTF-8

### Checksum Algorithm
- **Algorithm**: SHA-256
- **Encoding**: Hexadecimal lowercase
- **Length**: 64 characters

### File Tracking Logic

```java
// Check manifest first
if (modMetadata.isFileInstalledAndMatches(fileName, expectedHash)) {
    // File present in manifest with matching hash
    log("File OK (manifest): " + fileName);
    return NO_ACTION;
}

// File not in manifest or hash mismatch
if (fileExistsOnDisk) {
    String actualHash = computeHash(file);
    if (hashEquals(expectedHash, actualHash)) {
        // Add to manifest for future runs
        modMetadata.recordFile(fileName, actualHash, url, location);
        return NO_ACTION;
    } else {
        // Hash mismatch - need to repair
        return DOWNLOAD;
    }
} else {
    // File missing
    return DOWNLOAD;
}
```

## See Also

- [Coremod Setup](COREMOD_SETUP.md) - Early-load operations
- [Mods JSON Schema](MODS_JSON_SCHEMA.md) - Configuration format
- [Quick Start](QUICK_START.md) - Initial setup guide
