# Fix Summary: Mod Renaming and Updating Issues (v2)

## Problem Statement

Despite previous fixes, two critical bugs remained in the mod management system when users manually renamed mod files:

1. **Renamed Mod Re-download Issue**: When users manually renamed a mod file (e.g., `journeymap.jar` → `journeymap-custom.jar`), ModUpdater would still re-download the mod and leave both the renamed file and the new download in the folder.

2. **Old Renamed Version Not Deleted**: When updating a mod to a new version (changing `fileId` or `versionId` in mods.json), if the old version had been renamed by the user, ModUpdater would download the new version but fail to delete the old renamed version, leaving duplicate files.

## Root Causes

### Bug 1: Hash Scanning Skip Logic (Lines 520-527)

The hash scanning logic in the else branch had an overly aggressive skip condition:

```java
// OLD CODE
// Skip files that are already tracked by other mods in metadata
boolean alreadyTracked = false;
for (ModMetadata.ModEntry entry : modMetadata.getAllMods()) {
    if (entry.fileName != null && entry.fileName.equals(candidate.getName())) {
        alreadyTracked = true;  // BUG: Skips even if it's the SAME mod!
        break;
    }
}
if (alreadyTracked) continue;
```

**Problem**: When a file was tracked in metadata under the SAME numberId (e.g., from a previous partial run where metadata was updated but the file wasn't renamed), the scanner would skip it entirely. This caused:

1. User renames `journeymap.jar` → `journeymap-custom.jar`
2. Metadata gets updated to track `journeymap-custom.jar` (perhaps from a failed rename attempt)
3. On next run, `isModInstalledAndMatches` returns FALSE (e.g., hash mismatch)
4. Hash scanner in else branch finds `journeymap-custom.jar`
5. **BUG**: Skips it because it's "already tracked" in metadata
6. System downloads mod again
7. Result: Both `journeymap-custom.jar` and `journeymap.jar` exist

### Bug 2: Old Version Deletion Without Hash Scanning (Lines 690-719)

The `findFilesForNumberIdViaMetadata` method only looked for files by their exact filename from metadata:

```java
// OLD CODE
ModMetadata.ModEntry entry = metadata.getMod(numberId);
if (entry != null && entry.fileName != null && !entry.fileName.isEmpty()) {
    File f = new File(dir, entry.fileName);
    if (f.exists() && f.isFile()) {
        out.add(f);
    }
    // BUG: If file doesn't exist, gives up immediately!
}
```

**Problem**: When updating a mod whose old version had been renamed:

1. Metadata: `{numberId: "1", fileName: "old-version.jar", hash: "abc123"}`
2. File renamed to: `my-custom-name.jar`
3. mods.json updated with new version (hash: "def456")
4. System downloads new version
5. Tries to delete old version using `findFilesForNumberIdViaMetadata`
6. **BUG**: Looks for `old-version.jar`, doesn't find it, returns empty list
7. Old renamed file `my-custom-name.jar` is not deleted
8. Result: Both old and new versions exist

## Solution

### Fix 1: Only Skip Files Tracked by DIFFERENT Mods

Changed the skip logic to only skip files that belong to a DIFFERENT mod (different numberId):

```java
// NEW CODE (lines 519-527)
// Skip files that are already tracked by OTHER mods in metadata (different numberId)
boolean alreadyTrackedByOtherMod = false;
for (ModMetadata.ModEntry entry : modMetadata.getAllMods()) {
    if (entry.fileName != null && entry.fileName.equals(candidate.getName())) {
        // Only skip if tracked by a DIFFERENT mod (different numberId)
        if (!entry.numberId.equals(numberId)) {
            alreadyTrackedByOtherMod = true;
            break;
        }
    }
}
if (alreadyTrackedByOtherMod) continue;
```

**How it works:**
- Files tracked by the SAME mod (same numberId) are NOT skipped
- Only files belonging to OTHER mods are skipped
- This allows the hash scanner to properly detect and handle renamed files

### Fix 2: Hash-Based Scanning in findFilesForNumberIdViaMetadata

Added hash-based scanning as a fallback when the tracked file is missing:

```java
// NEW CODE (lines 703-725)
if (f.exists() && f.isFile()) {
    out.add(f);
} else if (entry.hash != null && !entry.hash.isEmpty()) {
    // File in metadata doesn't exist - maybe it was renamed
    // Try to find it by hash
    File[] allFiles = dir.listFiles();
    if (allFiles != null) {
        for (File candidate : allFiles) {
            if (!candidate.isFile()) continue;
            if (candidate.getName().endsWith(".tmp")) continue;
            try {
                String candidateHash = HashUtils.sha256Hex(candidate);
                if (FileUtils.hashEquals(entry.hash, candidateHash)) {
                    // Found the renamed file
                    if (!out.contains(candidate)) {
                        out.add(candidate);
                    }
                    break;
                }
            } catch (Exception ex) {
                // Skip files that can't be hashed
            }
        }
    }
}
```

**How it works:**
1. First tries to find file by exact filename from metadata
2. If file doesn't exist but hash is available, scans directory by hash
3. Calculates SHA-256 hash for each candidate file
4. Compares with expected hash from metadata
5. If match found, returns the renamed file for deletion

## Test Cases

### Test Case 1: User Renames Mod, Mod Still in mods.json

**Setup:**
- File: `journeymap.jar` (hash `abc123`)
- Metadata: `{numberId: "1", fileName: "journeymap.jar", hash: "abc123"}`
- mods.json: `{numberId: "1", hash: "abc123"}`
- User renames: `journeymap.jar` → `journeymap-custom.jar`

**Expected Result:**
- Hash scanner finds `journeymap-custom.jar` by hash ✓
- Renames back to `journeymap.jar` ✓
- Updates metadata ✓
- NO re-download ✓

### Test Case 2: Mod Updated, Old Version Renamed

**Setup:**
- File: `journeymap-v1.jar` (hash `abc123`)
- Metadata: `{numberId: "1", fileName: "journeymap-v1.jar", hash: "abc123"}`
- User renames: `journeymap-v1.jar` → `my-custom-map.jar`
- mods.json updated: `{numberId: "1", hash: "def456"}` (new version)

**Expected Result:**
- System detects version mismatch ✓
- Downloads new version with hash `def456` ✓
- `findFilesForNumberIdViaMetadata` scans by hash ✓
- Finds and deletes `my-custom-map.jar` (old version) ✓
- Installs new version ✓
- NO duplicate files ✓

### Test Case 3: Mod Removed, File Renamed

**Setup:**
- File: `journeymap.jar` (hash `abc123`)
- Metadata: `{numberId: "1", fileName: "journeymap.jar", hash: "abc123"}`
- User renames: `journeymap.jar` → `keep-this.jar`
- Admin removes mod from mods.json

**Expected Result:**
- Cleanup phase doesn't find file by exact name ✓
- File treated as unmanaged ✓
- Renamed file preserved (user choice respected) ✓

## Benefits

1. **No More Duplicate Downloads**: User-renamed files are properly detected by hash
2. **Clean Updates**: Old renamed versions are found and deleted during updates
3. **User Choice Respected**: Files not in metadata are left alone
4. **Robust Detection**: Works even if metadata is in inconsistent state
5. **Backwards Compatible**: Still handles legacy `numberId-` prefix files

## Security

✅ CodeQL analysis: 0 vulnerabilities found

All hash comparisons use constant-time comparison (`FileUtils.hashEquals()`) to prevent timing attacks.

## Changes Made

**File: `modupdater-core/src/main/java/com/ArfGg57/modupdater/UpdaterCore.java`**

1. Lines 519-527: Fixed hash scanning skip logic to only skip files tracked by different numberId
2. Lines 703-725: Added hash-based scanning fallback in `findFilesForNumberIdViaMetadata`

Total changes: 32 lines added, 6 lines removed

## Verification

To verify the fix works:

1. Install a mod through ModUpdater
2. Manually rename the mod file
3. Run ModUpdater again
4. Verify:
   - Mod is detected by hash (check logs)
   - No re-download occurs
   - File is renamed back to standard name
   - Metadata is updated correctly

For update verification:

1. Install a mod with version A
2. Rename the mod file
3. Update mods.json to version B
4. Run ModUpdater
5. Verify:
   - New version is downloaded
   - Old renamed version is deleted
   - Only new version remains in folder
