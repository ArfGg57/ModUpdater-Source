# Crash Feature Fix - Duplicate ModID Resolution

## Problem
The crash feature wasn't working properly because both the tweaker component (`modupdater-launchwrapper`) and the standalone mod component (`modupdater-standalone`) had `mcmod.info` files with the same `modid="modupdater"`. This created a conflict where Forge would only load one of the mods, preventing the crash feature from functioning correctly.

## Root Cause
When both JAR components are packaged together (as in `!!!!!modupdater.jar`), Forge discovers both `mcmod.info` files during mod discovery. Since they had the same modid, only one would be registered as a mod, causing the restart/crash handling logic to fail.

## Solution
Changed the modid in `modupdater-launchwrapper` to `modupdater-tweaker` while keeping `modupdater-standalone` as `modupdater`. This allows both components to coexist and function properly.

### Files Modified
1. **modupdater-launchwrapper/src/main/resources/mcmod.info**
   - Changed `modid` from `"modupdater"` to `"modupdater-tweaker"`
   - Updated description to clarify it's the tweaker component

2. **modupdater-launchwrapper/src/main/java/com/ArfGg57/modupdater/ModUpdaterMod.java**
   - Updated `MODID` constant to `"modupdater-tweaker"`
   - Updated `NAME` constant to `"ModUpdater Tweaker"`

3. **modupdater-standalone/src/main/resources/mcmod.info**
   - Kept `modid` as `"modupdater"` (this is the main mod)
   - Updated description to mention restart handling
   - Corrected author attribution

4. **modupdater-standalone/src/main/java/com/ArfGg57/modupdater/ModUpdater.java**
   - Updated version to `"2.20"` for consistency
   - Standardized name to `"ModUpdater"`

## How It Works Now

### Component Roles

**modupdater-tweaker** (Launchwrapper Component)
- Loads early via `TweakClass` manifest attribute
- Shows update confirmation dialog before game starts
- Runs `UpdaterCore.runUpdate()` if user agrees
- Handles cleanup of locked files on **subsequent launches** via `RestartEnforcer`

**modupdater** (Standalone Mod Component)
- Loads as a regular Forge mod
- Runs `UpdaterCore.runUpdate()` in preInit (fallback if tweaker didn't run)
- Monitors for restart requirements via system property `modupdater.restartRequired`
- Triggers crash on main menu when restart is needed
- Shows detailed crash report with list of locked files

### Complete Flow

#### First Launch (with locked files)
1. **Tweaker Phase**: Tweaker runs, shows dialog, updates if user agrees
2. **Update Detection**: `UpdaterCore` detects files that can't be deleted (locked by OS)
3. **Persistent Storage**: Writes locked file list to `config/ModUpdater/locked_files.lst`
4. **Flag Setting**: Creates `config/ModUpdater/restart_required.flag`
5. **System Property**: Sets `modupdater.restartRequired=true`
6. **Game Loading**: Game continues loading normally
7. **Mod Init**: Standalone mod's `init()` detects restart flag
8. **Tick Monitor**: Registers tick event handler to monitor for main menu
9. **Main Menu**: When main menu appears, triggers delayed crash
10. **Crash Report**: Shows detailed Forge crash report with locked file list

#### Second Launch (after crash)
1. **Tweaker Phase**: Tweaker runs (no updates needed)
2. **Cleanup Init**: Tweaker mod's `init()` detects restart flag file
3. **File Deletion**: `RestartEnforcer.tryProcessRestartFlag()` attempts to delete locked files
4. **Success Case**: Files are deletable now (previous JVM released them)
5. **Flag Clearing**: Removes restart flag and locked file list
6. **Normal Startup**: Game continues normally without crash

#### Persistent Files
The system uses three persistent files under `config/ModUpdater/`:
- `restart_required.flag` - Empty file indicating restart is needed
- `restart_message.txt` - Human-readable message for the restart
- `locked_files.lst` - List of absolute file paths to delete (one per line)

These files survive JVM restarts and are cleaned up after successful file deletion.

### System Properties Used
- `modupdater.restartRequired` - Set to "true" when restart needed
- `modupdater.lockedFilesListFile` - Path to temp file with locked files (legacy)
- `modupdater.restartMessage` - Message to display (legacy)
- `modupdater.deferCrash` - Set when user declines update in dialog

## Testing

### Manual Testing Steps
1. **Setup**: Create a scenario where mod files are locked during update
   - Have an old version of a mod in `mods/` directory
   - Configure update to newer version
   - Simulate file lock (difficult to test manually)

2. **Alternative Test**: Verify no duplicate mod errors
   - Build the project: `./gradlew build`
   - Check build output JARs contain both mcmod.info files
   - Launch game with the JAR
   - Check logs for "Found duplicate" or "ModID conflict" errors
   - Should see both mods load: `modupdater-tweaker` and `modupdater`

3. **Verify ModIDs in Logs**: Should see entries like:
   ```
   [FML]: Found mod modupdater-tweaker
   [FML]: Found mod modupdater
   ```

### Unit Tests
Existing tests in `modupdater-core/src/test/java/com/ArfGg57/modupdater/restart/CrashUtilsTest.java` verify:
- ✅ Restart flag write/read functionality
- ✅ Locked file list persistence
- ✅ Artifact cleanup
- ✅ Default message handling

## Benefits
1. **No Conflicts**: Both components can coexist without modid conflicts
2. **Clear Separation**: Distinct modids make it clear which component does what
3. **Crash Feature Works**: Standalone mod properly detects restart requirements
4. **Cleanup Works**: Tweaker mod can delete locked files on next launch
5. **Backward Compatible**: Existing persistent files still work

## Related Files
- `modupdater-core/src/main/java/com/ArfGg57/modupdater/restart/CrashUtils.java` - Persistent file management
- `modupdater-launchwrapper/src/main/java/com/ArfGg57/modupdater/RestartEnforcer.java` - File deletion logic
- `modupdater-standalone/src/main/java/com/ArfGg57/modupdater/ModUpdater.java` - Crash trigger logic
- `modupdater-core/src/main/java/com/ArfGg57/modupdater/core/UpdaterCore.java` - Main update logic

## Migration Notes
Users upgrading from previous versions will see both mods in their mod list:
- `ModUpdater Tweaker` (modupdater-tweaker)
- `ModUpdater` (modupdater)

Both are part of the same JAR and work together - this is intentional and correct.
