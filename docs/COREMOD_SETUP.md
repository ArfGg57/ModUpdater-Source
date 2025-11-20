# ModUpdater Coremod Setup

## Overview

The ModUpdater coremod provides early-phase file operations processing, enabling the updater to handle locked files more reliably on Windows systems. It runs before Forge loads mod JARs, allowing pending operations (like file deletions or renames) to complete before files become locked.

## Features

- **Early Loading**: Runs before FML scans the mods directory
- **Pending Operations**: Processes file operations that were deferred from previous runs
- **File Lock Handling**: Attempts to complete operations that failed due to locked files
- **Transparent Integration**: Works alongside the existing ModUpdater without configuration changes

## Setup Instructions

To enable the coremod functionality, you need to create a MANIFEST.MF file with the coremod specification.

### Step 1: Create META-INF/MANIFEST.MF

In your mod JAR's `META-INF/MANIFEST.MF` file, add these attributes:

```
Manifest-Version: 1.0
FMLCorePlugin: com.ArfGg57.modupdater.coremod.ModUpdaterCoremod
FMLCorePluginContainsFMLMod: true
```

### Step 2: Package the JAR

Ensure that the coremod class (`ModUpdaterCoremod.class`) and all its dependencies (`PendingOperations`, `FileUtils`, etc.) are included in the final JAR.

### Step 3: Test

When Forge starts, you should see log messages like:
```
[ModUpdaterCoremod] Initializing early-load phase...
[ModUpdaterCoremod] Processing N pending operation(s) from previous run...
[ModUpdaterCoremod] Early-load phase complete
```

## How It Works

1. When a file operation fails due to a file lock (e.g., a mod JAR is already loaded), the ModUpdater schedules it as a "pending operation" in `config/ModUpdater/pending-ops.json`.

2. On the next game launch, the coremod loads early (before mods are loaded).

3. The coremod reads `pending-ops.json` and attempts to process each operation:
   - DELETE: Try to delete the file
   - MOVE: Try to move the file

4. Successfully completed operations are removed from `pending-ops.json`.

5. Failed operations remain in the file for the next run.

## Technical Notes

- The coremod class implements `IFMLLoadingPlugin` interface methods but does not import the interface at compile time to avoid build dependencies.
- At runtime, Forge will recognize the class as a valid coremod plugin through duck typing.
- The implementation is compatible with Forge 1.7.10 and Java 8.

## Troubleshooting

### Coremod Not Loading

If the coremod doesn't appear to load:
1. Check that `FMLCorePlugin` is correctly specified in MANIFEST.MF
2. Verify the class path is correct: `com.ArfGg57.modupdater.coremod.ModUpdaterCoremod`
3. Check the Forge/FML logs for loading errors

### Operations Still Failing

If pending operations continue to fail:
1. Check file permissions
2. Verify no other process has the files locked
3. Review the ModUpdater logs for specific error messages

## Alternative: Without Coremod

If you choose not to use the coremod, the ModUpdater will still function but will rely on:
- Java's `File.deleteOnExit()` for deferred deletions
- Pending operations processing at the start of the next update (after mods are loaded)

This may be less reliable on Windows where JARs remain locked while in use.
