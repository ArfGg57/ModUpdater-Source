# Technical Implementation Details - Crash Fix

## Problem Identification

### Issue
When `modupdater.restartRequired` system property is set to `true`, the game should crash when the main menu is reached. However, the crash was not occurring.

### Root Cause
The exception (`ReportedException`) was being thrown from within a Forge event handler (`onClientTick`). Forge's event bus catches exceptions thrown from event handlers to prevent individual mods from crashing the entire game.

## Technical Solution

### Approach
Use `Minecraft.addScheduledTask()` to schedule the crash execution outside the event handler context.

### Why This Works

#### Forge Event Bus Exception Handling
```java
// Simplified Forge event bus logic
try {
    eventHandler.handleEvent(event);  // Your @SubscribeEvent method
} catch (Throwable t) {
    // Log the exception but continue
    FMLLog.log(Level.ERROR, t, "Exception caught during event handling");
    // Game continues normally - NO CRASH
}
```

#### Scheduled Task Execution
```java
// Minecraft.runTick() method (simplified)
public void runTick() {
    // Process events (with exception catching)
    eventBus.post(new TickEvent.ClientTickEvent());
    
    // Execute scheduled tasks (NO exception catching)
    while (!scheduledTasks.isEmpty()) {
        Runnable task = scheduledTasks.poll();
        task.run();  // If this throws, game crashes!
    }
    
    // Render frame
    render();
}
```

### Implementation

#### Before Fix
```java
@SubscribeEvent
public void onClientTick(TickEvent.ClientTickEvent event) {
    if (shouldCrash) {
        executeCrash(currentScreen);  // Calls method that throws exception
    }
}

private void executeCrash(GuiScreen currentScreen) {
    // ... create crash report ...
    throw new ReportedException(report);  // ❌ Caught by event bus
}
```

**Execution Flow:**
```
Minecraft.runTick()
└─> eventBus.post(ClientTickEvent)
    └─> [try-catch block]
        └─> onClientTick()
            └─> executeCrash()
                └─> throw ReportedException  ❌ Caught here
                └─> [Exception logged, game continues]
```

#### After Fix
```java
@SubscribeEvent
public void onClientTick(TickEvent.ClientTickEvent event) {
    if (shouldCrash) {
        executeCrash(currentScreen);  // Schedules the crash
    }
}

private void executeCrash(final GuiScreen currentScreen) {
    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
        @Override
        public void run() {
            // ... create crash report ...
            throw new ReportedException(report);  // ✅ NOT caught by event bus
        }
    });
}
```

**Execution Flow:**
```
Minecraft.runTick()
├─> eventBus.post(ClientTickEvent)
│   └─> [try-catch block]
│       └─> onClientTick()
│           └─> executeCrash()
│               └─> addScheduledTask(Runnable)  ✅ Returns normally
│               └─> [Event handler completes successfully]
│
└─> scheduledTasks.poll().run()
    └─> [NO try-catch block]
        └─> throw ReportedException  ✅ Crashes the game!
```

## Code Changes Detail

### File Modified
`modupdater-standalone/src/main/java/com/ArfGg57/modupdater/ModUpdaterDeferredCrash.java`

### Change 1: Method Signature
```diff
- private void executeCrash(GuiScreen currentScreen) {
+ private void executeCrash(final GuiScreen currentScreen) {
```
**Reason:** Parameter must be `final` to be captured by anonymous inner class.

### Change 2: Flag Comment
```diff
+ // Mark as executed to prevent re-entry (flag is set before actual execution
+ // to prevent scheduling the crash multiple times from subsequent ticks)
  crashExecuted = true;
```
**Reason:** Clarify why flag is set before actual crash execution.

### Change 3: Wrap in Scheduled Task
```diff
- RuntimeException cause = new RuntimeException(crashMessage);
- CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
- // ... crash report setup ...
- throw new ReportedException(report);
+ // Execute crash outside event handler to avoid suppression by event bus
+ Minecraft.getMinecraft().addScheduledTask(new Runnable() {
+     @Override
+     public void run() {
+         RuntimeException cause = new RuntimeException(crashMessage);
+         CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
+         // ... crash report setup ...
+         throw new ReportedException(report);
+     }
+ });
```
**Reason:** Move crash execution outside event handler context.

### Change 4: Updated Logging
```diff
- System.out.println("[ModUpdaterDeferredCrash] EXECUTING DEFERRED CRASH");
+ System.out.println("[ModUpdaterDeferredCrash] SCHEDULING DEFERRED CRASH");
  // ... schedule task ...
+ System.out.println("[ModUpdaterDeferredCrash] Crash scheduled successfully - will execute after event handler completes");
```
**Reason:** Accurately reflect the two-stage process.

### Change 5: Log in Scheduled Task
```diff
+ Minecraft.getMinecraft().addScheduledTask(new Runnable() {
+     @Override
+     public void run() {
+         System.out.println("[ModUpdaterDeferredCrash] EXECUTING CRASH (outside event handler)");
          // ... crash logic ...
+     }
+ });
```
**Reason:** Confirm when crash actually executes.

## Testing the Fix

### Log Sequence to Expect
```
[ModUpdater] DEBUG: System property 'modupdater.restartRequired' set to: true
[ModUpdaterDeferredCrash] Restart required detected at init time
[ModUpdaterDeferredCrash] Registering tick event listener
[ModUpdaterDeferredCrash] Main menu detected: net.minecraft.client.gui.GuiMainMenu
[ModUpdaterDeferredCrash] Scheduling crash with 3 tick delay
[ModUpdaterDeferredCrash] Delay complete - executing crash now
[ModUpdaterDeferredCrash] SCHEDULING DEFERRED CRASH
[ModUpdaterDeferredCrash] Event listener unregistered
[ModUpdaterDeferredCrash] Crash scheduled successfully
[ModUpdaterDeferredCrash] EXECUTING CRASH (outside event handler)
[ModUpdaterDeferredCrash] About to throw ReportedException
```

### What Happens
1. **Tick 1-3**: Delay countdown
2. **Tick 4**: `executeCrash()` called
   - Schedules crash task
   - Unregisters event listener
   - Returns normally
3. **Still Tick 4**: After event processing
   - Scheduled task executes
   - Exception thrown
   - **Game crashes** ✅

## Alternative Solutions Considered

### 1. Throw from Different Event
**Status:** ❌ Rejected
- All event handlers have exception catching
- Would have the same problem

### 2. Use Separate Thread
**Status:** ❌ Rejected
- Violates Minecraft's threading model
- Could cause thread safety issues
- More complex than needed

### 3. Use Timer/Delayed Executor
**Status:** ❌ Rejected
- Adds external dependency
- More complex than needed
- Timing issues possible

### 4. Modify Event Bus
**Status:** ❌ Rejected
- Requires Forge modification
- Not feasible for a mod
- Would affect all mods

### 5. Use Scheduled Task ✅
**Status:** ✅ **Selected**
- Clean and simple
- Uses existing Minecraft API
- Thread-safe
- Well-understood pattern
- Minimal code changes

## Performance Analysis

### Overhead Added
- **One-time**: One scheduled task creation when crash is needed
- **Ongoing**: None (task executes once and game crashes)
- **Memory**: One Runnable instance (~100 bytes)
- **CPU**: Negligible (task scheduling is O(1))

### Performance Impact
**Rating: Negligible**

The fix adds virtually no performance overhead:
1. Only activates when crash is needed (rare scenario)
2. Task executes once and terminates
3. No loops, polling, or ongoing monitoring added
4. Uses standard Minecraft patterns

## Compatibility Analysis

### Minecraft Versions
✅ Compatible with Minecraft 1.7.10 (and likely later versions)

### Forge Versions
✅ Compatible with Forge for 1.7.10 (and likely later versions)

### Other Mods
✅ No mod conflicts expected
- Uses standard APIs
- No global state modification
- Isolated to ModUpdater mod

### Java Versions
✅ Compatible with Java 8+ (project target is Java 8)

## Maintenance Considerations

### Code Clarity
✅ **High**
- Well-documented with comments
- Clear naming (`addScheduledTask`, `executeCrash`)
- Obvious intent and flow

### Debuggability
✅ **High**
- Comprehensive logging at each step
- Clear log messages
- Easy to trace execution in logs

### Testability
⚠️ **Moderate**
- Requires full Minecraft environment to test
- Cannot unit test easily (depends on Minecraft runtime)
- Can be tested manually in development environment

### Maintainability
✅ **High**
- Minimal code change
- Uses standard patterns
- Self-contained fix
- No complex dependencies

## Conclusion

This fix properly resolves the crash issue by ensuring the exception is thrown outside the event handler context where it won't be suppressed. The implementation:

- ✅ Is minimal and focused
- ✅ Uses standard Minecraft/Forge APIs
- ✅ Has negligible performance impact
- ✅ Is well-documented and maintainable
- ✅ Is compatible and safe
- ✅ Solves the problem completely

The scheduled task approach is the correct solution for this type of problem in Minecraft/Forge modding.
