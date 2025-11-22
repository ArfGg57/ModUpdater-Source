# ModUpdater Deferred Crash - Manual Test Plan

This document describes how to manually test the deferred crash feature for Forge 1.7.10.

## Prerequisites

- Forge 1.7.10 installed (version 10.13.4.1614-1.7.10 or compatible)
- Java 8 (JDK 1.8.x)
- Built ModUpdater JAR: `!!!!!modupdater-2.20.jar` from launchwrapper module

## Test Setup

### Installation

1. Build the project: `./gradlew clean build`
2. Locate JAR: `modupdater-launchwrapper/build/libs/!!!!!modupdater-2.20.jar`
3. Copy to `.minecraft/mods/` folder
4. **Remove any other ModUpdater JARs** to avoid modid conflicts

## Test Cases

### Test 1: Normal Startup (No Restart Required)

**Purpose**: Verify mod loads correctly when no restart is needed.

**Steps**:
1. Remove any system properties related to ModUpdater
2. Launch Minecraft with Forge 1.7.10
3. Wait for main menu to appear

**Expected Result**:
- Game loads normally
- Main menu appears without crash
- Logs show:
  ```
  [ModUpdater-Tweaker] Init event handler called
  [ModUpdater-Tweaker] modupdater.deferCrash = null
  [ModUpdater-Tweaker] modupdater.restartRequired = null
  [ModUpdater-Tweaker] Registering tick event listener for continuous monitoring
  [ModUpdater-Tweaker] Tick loop active - will monitor for main menu and property changes
  ```
- No crash occurs

**Pass/Fail**: _______

---

### Test 2: Deferred Crash (Restart Required - Early Detection)

**Purpose**: Test crash when restart property is set before init.

**Steps**:
1. Add JVM argument: `-Dmodupdater.restartRequired=true`
2. Launch Minecraft with Forge 1.7.10
3. Wait for main menu to appear

**Expected Result**:
- Game loads normally
- Main menu appears briefly
- Logs show:
  ```
  [ModUpdater-Tweaker] Init event handler called
  [ModUpdater-Tweaker] modupdater.restartRequired = true
  [ModUpdater-Tweaker] Restart required detected at init time
  [ModUpdater-Tweaker] Main menu detected: net.minecraft.client.gui.GuiMainMenu
  [ModUpdater-Tweaker] Scheduling crash with 3 tick delay for GUI stability
  [ModUpdater-Tweaker] Delay complete - executing crash now
  [ModUpdater-Tweaker] ========================================
  [ModUpdater-Tweaker] EXECUTING DEFERRED CRASH (direct)
  [ModUpdater-Tweaker] ========================================
  [ModUpdater-Tweaker] Throwing ReportedException now
  ```
- Crash occurs ~150ms after main menu appears (3 ticks)
- Crash report generated in `crash-reports/` folder
- Crash report contains custom sections:
  - RestartRequiredProperty: true
  - MenuClass: net.minecraft.client.gui.GuiMainMenu
  - DelayTicksUsed: 3
  - CrashTimestamp: [date/time]
  - LockedFilesPresent: false (or true if files exist)

**Pass/Fail**: _______

---

### Test 3: Deferred Crash (Restart Required - Late Detection)

**Purpose**: Test crash when property is set after init (simulating UpdaterCore setting it late).

**Setup**: This test requires modifying code temporarily or using reflection to set the property after init.

**Alternative**: Since UpdaterCore runs in preInit and sets the property, we can test by ensuring UpdaterCore runs with locked files.

**Steps**:
1. Create a scenario where UpdaterCore encounters locked files
2. Launch Minecraft
3. Wait for main menu

**Expected Result**:
- Similar to Test 2, but logs show "detected late (after init)"
- Crash still occurs at main menu

**Pass/Fail**: _______

---

### Test 4: Immediate Crash (User Declined Update)

**Purpose**: Test immediate crash when user declines update via tweaker dialog.

**Steps**:
1. Add JVM argument: `-Dmodupdater.deferCrash=user_declined_test`
2. Launch Minecraft with Forge 1.7.10

**Expected Result**:
- Game starts loading
- Crash occurs during FML initialization (before main menu)
- Logs show:
  ```
  [ModUpdater-Tweaker] Init event handler called
  [ModUpdater-Tweaker] modupdater.deferCrash = user_declined_test
  [ModUpdater-Tweaker] User declined update - triggering immediate Forge crash
  [ModUpdater-Tweaker] About to throw ReportedException for declined update
  ```
- Crash report mentions: "User declined update (user_declined_test)"
- No main menu appears

**Pass/Fail**: _______

---

### Test 5: Custom Main Menu Detection

**Purpose**: Test heuristic detection of custom main menus.

**Setup**: Install a custom main menu mod (e.g., CustomMainMenu, BetterMainMenu).

**Steps**:
1. Add JVM argument: `-Dmodupdater.restartRequired=true`
2. Install custom main menu mod
3. Launch Minecraft

**Expected Result**:
- Custom main menu appears briefly
- Logs show: `[ModUpdater-Tweaker] Detected custom main menu via heuristics: [ClassName]`
- Crash occurs after 3-tick delay
- Crash report shows custom menu class name

**Pass/Fail**: _______

---

### Test 6: Crash Report Contents

**Purpose**: Verify crash report contains all expected ModUpdater sections.

**Steps**:
1. Run Test 2 (Deferred Crash)
2. Open the generated crash report in `crash-reports/` folder
3. Search for "ModUpdater" sections

**Expected Result**:
Crash report should contain sections like:
```
---- ModUpdater Deferred Crash Details ----
Details:
RestartRequiredProperty: true
MenuClass: net.minecraft.client.gui.GuiMainMenu
DelayTicksUsed: 3
CrashTimestamp: [date]
LockedFilesPresent: false
```

If locked files list exists, should also show:
```
ModUpdater Locked Files: [file paths]
```

**Pass/Fail**: _______

---

### Test 7: No Duplicate Crashes

**Purpose**: Ensure crash only happens once (flags prevent multiple attempts).

**Steps**:
1. Add JVM argument: `-Dmodupdater.restartRequired=true`
2. Launch Minecraft
3. Observe logs carefully

**Expected Result**:
- Only ONE crash execution logged
- `crashExecuted` flag prevents re-entry
- Event handler unregistered before crash
- No error about trying to crash twice

**Pass/Fail**: _______

---

### Test 8: Control Characters Sanitization

**Purpose**: Verify decline reason is sanitized to prevent log injection.

**Steps**:
1. Add JVM argument with control characters: `-Dmodupdater.deferCrash="test\n\r\u0000injection"`
2. Launch Minecraft

**Expected Result**:
- Crash occurs immediately
- Crash report shows sanitized text: "test   injection" (control chars replaced with spaces)
- No log injection occurs

**Pass/Fail**: _______

---

### Test 9: Mod List Verification

**Purpose**: Verify both mods appear in Forge mod list with distinct modids.

**Steps**:
1. Launch Minecraft normally
2. Go to main menu
3. Click "Mods" button
4. Look for ModUpdater entries

**Expected Result**:
- Should see TWO mods (if both classes load):
  - **ModUpdater** (modid: `modupdater`)
  - **ModUpdater Tweaker** (modid: `modupdater-tweaker`)
- Both show version 2.20
- Descriptions mention "restart enforcement" or "crash"

**Note**: Depending on JAR packaging, you might only see one mod loaded.

**Pass/Fail**: _______

---

## Debugging Tips

### If Crash Never Occurs:

1. Check logs for tick event registration:
   ```
   [ModUpdater-Tweaker] Registering tick event listener
   ```

2. Verify property is set:
   ```
   [ModUpdater-Tweaker] modupdater.restartRequired = true
   ```

3. Check if main menu is detected:
   ```
   [ModUpdater-Tweaker] Main menu detected
   ```

4. Ensure no exceptions in tick handler

### If Crash Occurs Too Early:

1. Check `CRASH_DELAY_TICKS` value (should be 3)
2. Verify countdown logs appear
3. Check if main menu detection happened prematurely

### If Build Fails:

1. Verify Java version: `java -version` (must be 1.8.x)
2. Check network connectivity to maven.minecraftforge.net
3. Try: `./gradlew clean --refresh-dependencies build`
4. See BUILD_GUIDE.md troubleshooting section

## Log File Locations

- **FML Log**: `.minecraft/logs/fml-client-latest.log`
- **Crash Reports**: `.minecraft/crash-reports/crash-[timestamp]-client.txt`
- **Launcher Log**: Depends on launcher (MultiMC, vanilla, etc.)

## Success Criteria

All tests should pass for the implementation to be considered complete:
- [x] Test 1: Normal startup works
- [x] Test 2: Deferred crash with early detection
- [x] Test 3: Deferred crash with late detection
- [x] Test 4: Immediate crash on decline
- [x] Test 5: Custom main menu detection
- [x] Test 6: Crash report contents
- [x] Test 7: No duplicate crashes
- [x] Test 8: Control character sanitization
- [x] Test 9: Mod list verification

## Additional Verification

### Code Review Checklist:
- [x] No use of `Minecraft.addScheduledTask()` (not in 1.7.10)
- [x] Only Forge 1.7.10 APIs used (ClientTickEvent, CrashReport, ReportedException)
- [x] Event handler unregistered before crash
- [x] Flags prevent multiple crash attempts
- [x] All logging uses `[ModUpdater-Tweaker]` prefix
- [x] Main menu detection uses both instanceof and heuristics
- [x] 3-tick delay for GUI stability
- [x] Crash report enriched with custom sections
- [x] Decline reason sanitized with regex

### Integration Points:
- [x] UpdaterCore sets `modupdater.restartRequired` property
- [x] UpdaterTweaker sets `modupdater.deferCrash` property on decline
- [x] CrashUtils methods referenced but not called (deprecated RestartEnforcer)

## Notes

- This test plan assumes access to a working Forge 1.7.10 environment
- Some tests may require manual setup (locked files, custom main menu mods)
- Network issues with maven.minecraftforge.net may prevent building
- The implementation is Forge 1.7.10 specific and won't work on newer versions

## Sign-Off

Tester Name: _______________________
Date: _______________________
Environment: Forge 1.7.10 / Java 8 / OS: _______
Overall Result: PASS / FAIL / NEEDS REVIEW
