# Deferred Crash Fix

## Problem
When files failed to delete during mod updates (because they were locked by the game), the system would log that it would crash via Forge, but the crash never happened. This left the game running with outdated/conflicting mod files.

## Root Cause
The `ModUpdaterDeferredCrash` mod class was not being properly loaded by Forge because:

1. It wasn't declared in `mcmod.info`
2. The `mcmod.info` file was in the wrong location (`src/main/java/resources/` instead of `src/main/resources/`)

## Solution
1. **Added ModUpdaterDeferredCrash to mcmod.info** - Declared the second mod with proper dependency on the first mod
2. **Moved mcmod.info to correct location** - Moved from `src/main/java/resources/` to `src/main/resources/` so Gradle can properly include it in the JAR
3. **Added dependency specification** - Added `dependencies = "after:modupdater"` to the `@Mod` annotation to ensure proper load order
4. **Added debug logging** - Added logging to help diagnose any future issues

## How It Works

### Normal Flow (No Locked Files)
1. `ModUpdater.preInit()` runs and performs update
2. All files are successfully processed
3. No crash occurs, game continues normally

### Crash Flow (Locked Files Present)
1. `ModUpdater.preInit()` runs and performs update
2. Some files fail to delete (locked by game)
3. System property `modupdater.restartRequired` is set to `"true"`
4. Cleanup helper process is launched
5. `ModUpdater.preInit()` completes normally
6. Later, `ModUpdaterDeferredCrash.init()` is called
7. It checks the system property
8. Finding it set to `"true"`, it throws a `ReportedException`
9. This causes Forge to crash the game back to the launcher
10. The cleanup helper process shows a dialog and deletes the locked files

## Event Lifecycle
In Forge 1.7.10, mods go through these phases in order:
1. Construction - All `@Mod` classes are instantiated
2. **PreInitialization** - All mods' `preInit` methods are called (including `ModUpdater.preInit`)
3. **Initialization** - All mods' `init` methods are called (including `ModUpdaterDeferredCrash.init`)
4. PostInitialization - All mods' `postInit` methods are called
5. Complete - Loading finishes

Since ALL preInit events happen before ANY init events, the timing is correct:
- `ModUpdater.preInit()` sets the property
- `ModUpdaterDeferredCrash.init()` checks it and crashes if needed

## Testing
To test this fix:
1. Build the mod JAR with the changes
2. Install it in a Minecraft 1.7.10 instance with Forge
3. Configure the mod to update with files that will be locked (e.g., currently loaded mods)
4. Start the game
5. Verify in the logs:
   - `[ModUpdaterDeferredCrash] Init event handler called` appears
   - If locked files are detected: `[ModUpdaterDeferredCrash] Crash condition met - triggering Forge crash` appears
   - The game crashes back to the launcher
   - The cleanup dialog appears and removes locked files

## Debug Logging
The fix includes debug logging to help diagnose issues:

**In UpdaterCore:**
- `DEBUG: System property 'modupdater.restartRequired' set to: true` - Confirms property is set
- `DEBUG: Locked files count: N` - Shows how many files are locked

**In ModUpdaterDeferredCrash:**
- `[ModUpdaterDeferredCrash] Init event handler called` - Confirms the event is firing
- `[ModUpdaterDeferredCrash] modupdater.deferCrash = null` - Shows property values
- `[ModUpdaterDeferredCrash] modupdater.restartRequired = true` - Shows property values
- `[ModUpdaterDeferredCrash] Crash condition met - triggering Forge crash` - Confirms crash will happen
- `[ModUpdaterDeferredCrash] About to throw ReportedException` - Last message before crash
- `[ModUpdaterDeferredCrash] No crash needed - continuing normal initialization` - No crash needed

If the init handler is never called, the issue is with mod loading/discovery.
If the property is not set correctly, the issue is in UpdaterCore.
If the crash doesn't happen after "About to throw ReportedException", something is catching the exception.
