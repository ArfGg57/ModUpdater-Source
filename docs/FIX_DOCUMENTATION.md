# ModUpdater Crash Detection Fix

## Problem Statement

After setting `modupdater.restartRequired` to `true` when locked files were detected, the game was not crashing when the main menu loaded. The user reported:

```
[00:15:14] [main/INFO]: Some files could not be deleted and require a restart. Deferring Forge crash until mod init.
[00:15:14] [main/INFO]: Launching cleanup helper process to show dialog after crash...
[00:15:14] [main/INFO]: [ModUpdater] DEBUG: System property 'modupdater.restartRequired' set to: true
[00:15:14] [main/INFO]: [ModUpdater] DEBUG: Locked files count: 1
```

**Critical Missing Evidence**: No log output from `ModUpdaterDeferredCrash.init()` which should have printed `"[ModUpdaterDeferredCrash] Init event handler called"`

## Root Cause Analysis

### Issue Identified
The `ModUpdaterDeferredCrash` class was defined as a separate `@Mod` in the same JAR as `ModUpdater`, but Forge 1.7.10 was not loading it as a separate mod container.

### Why It Failed
1. **Two @Mod classes in one JAR**: `ModUpdater` and `ModUpdaterDeferredCrash` were both annotated with `@Mod`
2. **Forge mod discovery**: In Minecraft 1.7.10, Forge uses ASM scanning to discover `@Mod` classes
3. **Discovery limitation**: The second mod class was not being discovered/registered by Forge
4. **Result**: The crash detection code in `ModUpdaterDeferredCrash.init()` never executed

### Evidence
- The log showed no output from `ModUpdaterDeferredCrash` at all
- The `init()` method should always log "[ModUpdaterDeferredCrash] Init event handler called"
- The absence of this log proves the mod was never initialized

## Solution

### Approach
Consolidate all crash detection logic into the main `ModUpdater` class instead of using a separate mod class.

### Implementation

#### 1. Unified Mod Structure
- Moved all crash detection code from `ModUpdaterDeferredCrash.java` into `ModUpdater.java`
- Added `init()` event handler to `ModUpdater` (runs after `preInit()`)
- Removed `ModUpdaterDeferredCrash.java` entirely

#### 2. Event Handler Sequence
```
ModUpdater.preInit() → UpdaterCore.runUpdate() → Sets system property if files are locked
                                                ↓
ModUpdater.init()    → Detects system property → Registers tick listener
                                                ↓
onClientTick()       → Monitors for main menu  → Schedules crash
                                                ↓
Scheduled Task       → Executes crash outside event handler
```

#### 3. Key Components

**State Variables** (in ModUpdater class):
```java
private volatile boolean restartRequiredFlag = false;  // Set when property detected
private volatile boolean crashScheduled = false;        // Set when menu detected
private volatile boolean crashExecuted = false;         // Prevents multiple crashes
private volatile int crashDelayTicks = 0;              // Countdown timer
```

**Init Handler** (new in ModUpdater):
```java
@Mod.EventHandler
public void init(FMLInitializationEvent evt) {
    // Check system properties
    String restartRequired = System.getProperty("modupdater.restartRequired");
    
    // If restart required, set flag and register tick listener
    if ("true".equals(restartRequired)) {
        restartRequiredFlag = true;
        MinecraftForge.EVENT_BUS.register(this);
    }
}
```

**Tick Listener**:
```java
@SubscribeEvent
public void onClientTick(TickEvent.ClientTickEvent event) {
    // Monitor for main menu
    // When detected, schedule crash with delay
    // Execute crash via addScheduledTask()
}
```

**Crash Execution**:
```java
private void executeCrash(final GuiScreen currentScreen) {
    // Use addScheduledTask to execute crash outside event handler
    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
        public void run() {
            throw new ReportedException(report);  // Crashes the game
        }
    });
}
```

## Why This Fix Works

### 1. Single Mod Container
- Only one `@Mod` class (`ModUpdater`) exists now
- Forge reliably loads and initializes it
- No dependency on multi-mod-per-JAR discovery

### 2. Proper Event Sequencing
- `preInit()`: Updates run, property set if needed
- `init()`: Property detected, listener registered
- `tick()`: Menu detected, crash scheduled
- This matches Forge's natural event flow

### 3. Scheduled Task Pattern
- `addScheduledTask()` executes code outside event handler context
- Exceptions thrown from scheduled tasks crash the game
- Exceptions thrown from event handlers are suppressed by Forge
- This is the correct pattern for intentional crashes

### 4. Robust Monitoring
- ALWAYS registers tick listener when restart is required
- Continuously checks for main menu (not just GuiOpenEvent)
- Handles late property setting (even after init)
- Supports custom main menu mods via heuristics

## Expected Log Output

### Normal Operation (No Locked Files)
```
[main/INFO]: [ModUpdater] Running update in preInit
[main/INFO]: [ModUpdater] Init event handler called
[main/INFO]: [ModUpdater] modupdater.deferCrash = null
[main/INFO]: [ModUpdater] modupdater.restartRequired = null
[main/INFO]: [ModUpdater] Registering tick event listener for continuous monitoring
[main/INFO]: [ModUpdater] Tick loop active - will monitor for main menu and property changes
```
(Game continues normally, no crash)

### Crash Scenario (Locked Files Detected)
```
[main/INFO]: [ModUpdater] Running update in preInit
[main/INFO]: Some files could not be deleted and require a restart. Deferring Forge crash until mod init.
[main/INFO]: [ModUpdater] DEBUG: System property 'modupdater.restartRequired' set to: true
[main/INFO]: [ModUpdater] Init event handler called
[main/INFO]: [ModUpdater] modupdater.deferCrash = null
[main/INFO]: [ModUpdater] modupdater.restartRequired = true
[main/INFO]: [ModUpdater] Restart required detected at init time
[main/INFO]: [ModUpdater] Registering tick event listener for continuous monitoring
[main/INFO]: [ModUpdater] Tick loop active - will monitor for main menu and property changes
... (game continues loading) ...
[main/INFO]: [ModUpdater] Main menu detected: net.minecraft.client.gui.GuiMainMenu
[main/INFO]: [ModUpdater] Scheduling crash with 3 tick delay for GUI stability
[main/INFO]: [ModUpdater] Crash scheduled, waiting 3 tick(s) for GUI stability
[main/INFO]: [ModUpdater] Delay complete - executing crash now
[main/INFO]: [ModUpdater] ========================================
[main/INFO]: [ModUpdater] SCHEDULING DEFERRED CRASH
[main/INFO]: [ModUpdater] ========================================
[main/INFO]: [ModUpdater] Event listener unregistered
[main/INFO]: [ModUpdater] Crash scheduled successfully - will execute after event handler completes
[main/INFO]: [ModUpdater] EXECUTING CRASH (outside event handler)
[main/INFO]: [ModUpdater] About to throw ReportedException for restart required
```
(Game crashes with crash report)

## Changes Made

### Files Modified

1. **modupdater-standalone/src/main/java/com/ArfGg57/modupdater/ModUpdater.java**
   - Added imports for crash handling and event system
   - Added state variables for crash detection
   - Added `init()` event handler
   - Added `isMainMenuScreen()` helper method
   - Added `onClientTick()` event handler
   - Added `executeCrash()` method with scheduled task
   - **Net change**: +231 lines

2. **modupdater-standalone/src/main/java/com/ArfGg57/modupdater/ModUpdaterDeferredCrash.java**
   - **DELETED** (functionality moved to ModUpdater.java)
   - **Net change**: -270 lines

3. **modupdater-standalone/src/main/resources/mcmod.info**
   - Removed second mod entry (modupdaterdeferredcrash)
   - Updated description to mention crash handling
   - **Net change**: -13 lines

4. **.gitignore**
   - Added pattern to exclude `*.deprecated` files
   - **Net change**: +3 lines

### Total Impact
- **Lines added**: 234
- **Lines removed**: 283
- **Net change**: -49 lines
- **Files changed**: 4

## Testing Recommendations

### Manual Test Scenario

**Setup**:
1. Configure ModUpdater to update a mod that's currently loaded (will be locked)
2. Start the game

**Expected Behavior**:
1. Game starts loading
2. ModUpdater attempts to update/delete locked file
3. File deletion fails (locked by current game process)
4. Property `modupdater.restartRequired` set to `true`
5. ModUpdater.init() detects property and registers tick listener
6. Game continues loading to main menu
7. Main menu appears
8. Tick listener detects main menu
9. After 3 tick delay (~150ms), crash is scheduled
10. Crash executes outside event handler
11. **Game crashes to launcher with crash report**
12. Cleanup helper dialog appears
13. User restarts game
14. Locked files are deleted on restart
15. Game starts successfully with updated mods

### Validation Points

✅ **Log shows**: `"[ModUpdater] Init event handler called"`  
✅ **Log shows**: `"[ModUpdater] Restart required detected at init time"`  
✅ **Log shows**: `"[ModUpdater] Main menu detected"`  
✅ **Log shows**: `"[ModUpdater] EXECUTING CRASH (outside event handler)"`  
✅ **Game crashes** when main menu loads  
✅ **Crash report** contains ModUpdater details  
✅ **Cleanup dialog** appears after crash  
✅ **Locked files** are removed on restart  

## Security Analysis

**CodeQL Scan Result**: ✅ **0 alerts**

- No new security vulnerabilities introduced
- Input sanitization maintained for decline reasons
- No new external dependencies
- No unsafe operations
- Proper exception handling

## Backwards Compatibility

✅ **Fully Compatible**:
- No API changes
- No breaking changes to existing functionality
- Only affects crash detection path
- Normal operation unchanged
- All existing features work as before

## Performance Impact

**Minimal**:
- One additional event handler (init)
- Tick listener only active when crash needed
- Simple boolean checks every tick (negligible overhead)
- Cleanup and unregister after single use

## Conclusion

This fix resolves the crash detection issue by:
1. Eliminating the unreliable two-mod-per-JAR pattern
2. Using Forge's natural event flow (preInit → init → tick)
3. Employing the correct crash pattern (scheduled task)
4. Providing robust monitoring and logging

The implementation is clean, safe, well-documented, and follows Minecraft/Forge best practices.
