# Refactoring and File Lock Handling Guide

## Overview

This document describes the recent refactoring improvements to ModUpdater, including centralized hash detection and robust file lock handling.

## Architecture Changes

### Package Structure

The codebase has been reorganized into clearer packages:

- **`com.ArfGg57.modupdater.hash`**: Hash-related utilities and rename detection
  - `HashUtils`: SHA-256 hashing utilities
  - `RenamedFileResolver`: Centralized hash-based file resolution

- **`com.ArfGg57.modupdater.metadata`**: Metadata management
  - `ModMetadata`: Tracks installed mods by their source identifiers

- **`com.ArfGg57.modupdater.util`**: General utilities
  - `PendingOperations`: Handles deferred file operations for locked files

### Centralized Hash Detection

Previously, hash-based rename detection logic was duplicated in multiple places:
1. Cleanup phase (scanning for outdated mods)
2. Mod installation phase (finding renamed files)

This has been consolidated into `RenamedFileResolver`, which:
- Builds a hash → ModEntry index once per run (O(1) lookups)
- Provides safe hashing with consistent error handling
- Supports early exit on first match to avoid multiple matches
- Uses callback interface for flexible logging

#### Key Methods

```java
// Safely compute SHA-256 hash with error handling
String safeSha256Hex(File file)

// Resolve file to find its ModEntry from metadata
ModMetadata.ModEntry resolveByHash(File file)

// Verify a file's hash matches expected value
boolean verifyHash(File file, String expectedHash)

// Find file in directory by matching hash
File findFileByHash(File directory, String expectedHash, String... skipFileNames)

// Get the numberId of the mod that owns a file
String getOwnerNumberId(File file)

// Find all files belonging to a mod
File[] findAllFilesForMod(File directory, String numberId)
```

#### Benefits

1. **No duplicate code**: Single source of truth for hash-based detection
2. **Better performance**: One-time index build, O(1) hash lookups
3. **Consistent behavior**: Same logic everywhere reduces bugs
4. **Better logging**: Centralized logging via callback interface
5. **Easier testing**: Single class to test instead of scattered logic

## File Lock Handling

### Problem

When Forge loads mods early in the startup process, JAR files may be locked by the JVM's ClassLoader. This prevents ModUpdater from deleting or moving files during updates, causing:
- Failed deletions leaving old mod versions
- Update errors requiring manual intervention
- Inconsistent state between runs

### Solution: PendingOperations

The `PendingOperations` class provides robust, cross-platform handling for locked files:

1. **Immediate attempt**: First tries to delete/move the file normally
2. **Lock detection**: Detects if file is locked using platform-agnostic checks
3. **Graceful fallback**: Schedules locked files using `deleteOnExit()`
4. **Persistent tracking**: Records operations in `pending-ops.json`
5. **Startup processing**: Processes pending operations at next startup (before main scan)

#### Key Methods

```java
// Attempt to delete with fallback to pending operations
boolean deleteWithFallback(File file)

// Check if a file is currently locked
static boolean isFileLocked(File file)

// Process pending operations from previous run
int processPendingOperations()
```

#### How It Works

1. **During Update**: When ModUpdater tries to delete a locked file:
   ```
   [Update Run 1]
   - Try to delete old-mod.jar
   - File is locked (loaded by Forge)
   - Schedule deleteOnExit()
   - Record in pending-ops.json
   - Continue with update
   ```

2. **Next Startup**: Before the main update scan:
   ```
   [Update Run 2]
   - Load pending-ops.json
   - Process pending operations
   - old-mod.jar is now gone (deleteOnExit worked)
   - Or retry deletion if still exists
   - Continue with normal update
   ```

#### Configuration

The pending operations file is stored at:
```
config/ModUpdater/pending-ops.json
```

Format:
```json
{
  "operations": [
    {
      "type": "DELETE",
      "sourcePath": "mods/old-mod.jar",
      "timestamp": 1700000000000
    }
  ]
}
```

### Early Startup Integration

ModUpdater processes pending operations early in the startup flow:

```java
// In UpdaterCore.runUpdate()
PendingOperations pendingOps = new PendingOperations(pendingOpsPath, logger);
pendingOps.processPendingOperations(); // Before main scan
```

This ensures that:
1. Locked files from previous run are cleaned up first
2. Fresh start for current update cycle
3. Minimal chance of duplicate files

## Migration Guide

### For Users

No changes required! The refactoring is behavior-preserving:
- Rename detection works exactly the same
- Overwrite behavior unchanged  
- Better resilience for locked files

### For Developers

If you've been modifying ModUpdater code:

1. **Update imports**:
   ```java
   // Old
   import com.ArfGg57.modupdater.HashUtils;
   import com.ArfGg57.modupdater.ModMetadata;
   
   // New
   import com.ArfGg57.modupdater.hash.HashUtils;
   import com.ArfGg57.modupdater.metadata.ModMetadata;
   ```

2. **Use RenamedFileResolver instead of manual hash scanning**:
   ```java
   // Old (manual scanning)
   for (File candidate : targetDir.listFiles()) {
       String hash = HashUtils.sha256Hex(candidate);
       if (FileUtils.hashEquals(expectedHash, hash)) {
           // Found it
       }
   }
   
   // New (centralized)
   RenamedFileResolver resolver = new RenamedFileResolver(metadata, logger);
   File found = resolver.findFileByHash(targetDir, expectedHash);
   ```

3. **Use PendingOperations for file deletion**:
   ```java
   // Old
   file.delete(); // May fail silently
   
   // New
   PendingOperations pendingOps = new PendingOperations(path, logger);
   if (!pendingOps.deleteWithFallback(file)) {
       // Will retry on next startup
   }
   ```

## Performance

The refactoring maintains or improves performance:

- **Hash index**: Built once per run, O(1) lookups vs O(N) scans
- **Early exit**: Stops on first match, avoiding redundant hashing
- **No regression**: Typical modpacks (<100 JARs) show same or better startup time
- **Locked file handling**: No blocking waits, graceful fallback

## Testing

Key test scenarios (all must pass):

1. **Rename detection**: User renames mod → detected by hash, no re-download
2. **Content change**: Rename + modify → hash mismatch, re-download
3. **Version upgrade**: Old version deleted, new version installed once
4. **Locked file**: Deletion fails → scheduled for next run
5. **Pending operations**: Next run processes pending ops successfully
6. **Unmanaged files**: User-added files not deleted

## Compatibility

- **Java Version**: Java 8+ (unchanged)
- **Forge Version**: 1.7.10 (unchanged)
- **Schema**: No changes to remote configs or metadata format
- **Hash Algorithm**: SHA-256 (unchanged)

## Future Enhancements

Potential improvements for future PRs:

1. **Earlier Tweaker Execution**: Run UpdaterTweaker even earlier in Forge lifecycle
2. **Config Toggle**: Add option to disable pending operations feature
3. **Move Operations**: Support for deferred move operations (not just delete)
4. **Age-based Cleanup**: Automatically remove very old pending operations

## References

- PR #7: Initial rename detection
- PR #8: Overwrite behavior fixes
- PR #9: Cleanup phase improvements
- This PR: Refactoring and file lock handling
