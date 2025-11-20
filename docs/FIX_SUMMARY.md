# Fix Summary: Mod Renaming and Updating Issues

## Problem Statement

Two critical bugs were identified in the mod management system:

1. **Manual File Rename Issue**: When users manually renamed a mod file in the `/mods` folder (e.g., `journeymap.jar` → `journeymap-custom.jar`), ModUpdater would re-download the mod instead of recognizing the renamed file.

2. **Update Cleanup Issue**: When updating a mod to a new version (changing `fileId` or `versionId` in mods.json), ModUpdater would download the new version but fail to delete the old version, leaving duplicate files.

## Root Causes

### Issue 1: Metadata Lookup Without Fallback
In `UpdaterCore.java` (line 428), when a file from metadata didn't exist at its expected location:
```java
// OLD CODE
if (existingFile.exists()) {
    // verify hash...
} else {
    gui.show("Mod in metadata but file missing; will redownload: " + installedFileName);
    needDownload = true;  // Immediately gives up!
}
```

The code immediately decided to re-download without considering the file might have been renamed by the user.

### Issue 2: Prefix-Based File Finding
In `UpdaterCore.java` (line 542), old version deletion used:
```java
// OLD CODE
List<File> existingFiles = FileUtils.findFilesForNumberId(targetDir, numberId);
```

The `findFilesForNumberId()` method only found files with the `numberId-` prefix pattern (e.g., `1-journeymap.jar`). However, newer mods don't use this prefix anymore—they're tracked via metadata only. So old versions were never found and never deleted.

## Solution

### Fix 1: Hash-Based Rename Detection
Added a hash-scanning fallback when a file is missing from its expected location:

```java
// NEW CODE (lines 428-463)
if (existingFile.exists()) {
    // verify hash...
} else {
    // IMPROVED: Try to find it by hash before downloading
    if (!expectedHash.isEmpty()) {
        gui.show("Mod in metadata but file missing: " + installedFileName + "; scanning for renamed file...");
        File[] allFiles = targetDir.listFiles();
        boolean found = false;
        if (allFiles != null) {
            for (File candidate : allFiles) {
                if (!candidate.isFile()) continue;
                if (candidate.getName().endsWith(".tmp")) continue;
                String candidateHash = HashUtils.sha256Hex(candidate);
                if (FileUtils.hashEquals(expectedHash, candidateHash)) {
                    gui.show("Found renamed mod by hash: " + candidate.getName());
                    existingFile = candidate;
                    // Update metadata and optionally rename back
                    modMetadata.recordMod(numberId, candidate.getName(), expectedHash, source);
                    if (!candidate.getName().equals(finalName)) {
                        FileUtils.backupPathTo(candidate, backupRoot);
                        FileUtils.atomicMoveWithRetries(candidate, target, 5, 200);
                        gui.show("Renamed mod to: " + target.getPath());
                        modMetadata.recordMod(numberId, finalName, expectedHash, source);
                    }
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            gui.show("Could not find renamed file; will redownload: " + installedFileName);
            needDownload = true;
        }
    } else {
        gui.show("Mod in metadata but file missing; will redownload: " + installedFileName);
        needDownload = true;
    }
}
```

**How it works:**
1. Scans all files in the mods directory
2. Calculates SHA-256 hash for each candidate
3. Compares with expected hash
4. If match found, updates metadata and optionally renames back to standard name
5. Only downloads if no matching hash is found

### Fix 2: Metadata-Based File Finding
Created new helper method `findFilesForNumberIdViaMetadata()` that uses metadata to find files:

```java
// NEW CODE (lines 682-717)
private List<File> findFilesForNumberIdViaMetadata(File dir, String numberId, ModMetadata metadata) {
    List<File> out = new ArrayList<>();
    if (dir == null || !dir.isDirectory() || numberId == null || numberId.isEmpty()) {
        return out;
    }
    
    // First, check metadata for files tracked under this numberId
    ModMetadata.ModEntry entry = metadata.getMod(numberId);
    if (entry != null && entry.fileName != null && !entry.fileName.isEmpty()) {
        File f = new File(dir, entry.fileName);
        if (f.exists() && f.isFile()) {
            out.add(f);
        }
    }
    
    // Also check for legacy files with numberId- prefix (backwards compatibility)
    File[] children = dir.listFiles();
    if (children != null) {
        for (File f : children) {
            if (!f.isFile()) continue;
            if (f.getName().endsWith(".tmp")) continue;
            if (f.getName().startsWith(numberId + "-")) {
                // Avoid duplicates
                if (!out.contains(f)) {
                    out.add(f);
                }
            }
        }
    }
    
    return out;
}
```

Updated deletion logic to use the new method:
```java
// NEW CODE (line 576)
List<File> existingFiles = findFilesForNumberIdViaMetadata(targetDir, numberId, modMetadata);
```

**How it works:**
1. Checks metadata to find the actual filename
2. Falls back to legacy prefix pattern for backwards compatibility
3. Returns all files belonging to the numberId
4. Ensures old versions are found and deleted during updates

## Benefits

1. **No More Duplicate Downloads**: User-renamed files are detected by hash and kept
2. **Clean Updates**: Old versions are properly deleted when updating mods
3. **Better Logging**: Clear messages about rename detection and old version removal
4. **Backwards Compatible**: Still handles legacy `numberId-` prefix files
5. **Safe Operations**: All deletions backed up before removal

## Testing

See `TESTING_GUIDE.md` for detailed test scenarios to verify the fixes.

## Security

✅ CodeQL analysis: 0 vulnerabilities found

All hash comparisons use constant-time comparison (`FileUtils.hashEquals()`) to prevent timing attacks.

## Requirements Met

✅ All mods.json entries must include SHA-256 hashes (confirmed by user)
✅ Handles manual file renames via hash detection
✅ Properly deletes old versions during updates
✅ Maintains accurate metadata
✅ Backwards compatible with legacy naming schemes
