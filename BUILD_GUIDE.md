# ModUpdater Build Guide

This guide explains how to build ModUpdater from source and create the distributable JAR file for Forge 1.7.10.

## Prerequisites

### Required Software

- **Java Development Kit (JDK) 8**: Required for compatibility with Forge 1.7.10
  - Download from [AdoptOpenJDK](https://adoptopenjdk.net/) or [Oracle](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
  - Verify installation: `java -version` should show version 1.8.x

- **Git**: For cloning the repository
  - Download from [git-scm.com](https://git-scm.com/)

### Verify Java Version

```bash
# Check Java version (must be 8 / 1.8.x)
java -version
javac -version
```

Output should show something like:
```
java version "1.8.0_xxx"
Java(TM) SE Runtime Environment (build 1.8.0_xxx)
```

## Building the Project

### Step 1: Clone the Repository

```bash
git clone https://github.com/ArfGg57/ModUpdater-Source.git
cd ModUpdater-Source
```

### Step 2: Build Using Gradle Wrapper

The project includes a Gradle wrapper that automatically downloads the correct Gradle version.

#### On Linux/macOS:

```bash
./gradlew clean build
```

#### On Windows:

```cmd
gradlew.bat clean build
```

Or simply:
```cmd
gradlew clean build
```

### Build Output

The build process compiles three modules:
1. `modupdater-core` - Core update logic
2. `modupdater-standalone` - Standalone mod module
3. `modupdater-launchwrapper` - Tweaker module (includes all components)

**The distributable JAR is created in:**
```
modupdater-launchwrapper/build/libs/!!!!!modupdater-2.20.jar
```

Note: The "!!!!!" prefix ensures this mod loads early in the Forge mod loading sequence.

### Understanding the Build Structure

- **modupdater-core**: Contains shared logic (update processing, file operations, UI dialogs)
- **modupdater-standalone**: Contains the `@Mod(modid="modupdater")` class with crash logic
- **modupdater-launchwrapper**: Contains the `@Mod(modid="modupdater-tweaker")` class and LaunchWrapper tweaker
  - Creates a "fat JAR" that bundles all three modules
  - This is the JAR you should distribute

## Installation

### For Forge 1.7.10

1. Build the project following the steps above
2. Locate the JAR file: `modupdater-launchwrapper/build/libs/!!!!!modupdater-2.20.jar`
3. Copy this JAR to your `.minecraft/mods/` folder
4. Launch Minecraft with Forge 1.7.10

**Important**: Only install the launchwrapper JAR (`!!!!!modupdater-2.20.jar`). Do not install the standalone or core JARs.

## Verifying the Build

After installation, verify ModUpdater is working:

1. Launch Minecraft with Forge 1.7.10
2. Check the logs for ModUpdater initialization:
   ```
   [ModUpdater-Tweaker] Init event handler called
   [ModUpdater-Tweaker] Registering tick event listener for continuous monitoring
   ```
3. Check the Forge mods list - you should see:
   - **ModUpdater Tweaker** (modid: `modupdater-tweaker`)
   - **ModUpdater** (modid: `modupdater`) - if standalone is also loaded

## Testing the Crash Feature

To test the deferred crash functionality when locked files prevent updates:

### Manual Test Setup

1. **Simulate a locked file scenario**:
   - Before launching Minecraft, set the system property manually (for testing only):
     ```
     -Dmodupdater.restartRequired=true
     ```
   - Or let UpdaterCore set it automatically when it encounters locked files

2. **Expected behavior**:
   - Game launches normally
   - When the main menu appears, you should see in logs:
     ```
     [ModUpdater-Tweaker] Restart required detected at init time
     [ModUpdater-Tweaker] Main menu detected: net.minecraft.client.gui.GuiMainMenu
     [ModUpdater-Tweaker] Scheduling crash with 3 tick delay for GUI stability
     [ModUpdater-Tweaker] EXECUTING DEFERRED CRASH (direct)
     ```
   - Forge crash report is generated with custom sections:
     - RestartRequiredProperty
     - MenuClass
     - DelayTicksUsed
     - CrashTimestamp
     - LockedFilesPresent

3. **Verify crash report**:
   - Look in `crash-reports/` folder
   - Open the crash report and verify it contains ModUpdater sections

### Test Decline Scenario

To test immediate crash when user declines update:

1. The tweaker shows a confirmation dialog before Minecraft loads
2. Click "Decline" or close the dialog
3. Expected behavior:
   - Immediate crash during FML initialization
   - Crash report mentions: "User declined update"

## Troubleshooting

### Build Fails - Missing Dependencies

**Problem**: Build fails with "Could not resolve" errors for ForgeGradle dependencies.

**Solution**: This is usually a temporary network issue with maven.minecraftforge.net. Try:
1. Wait a few minutes and try again
2. Check your internet connection
3. Try using a VPN if the repository is blocked in your region

### Duplicate Modid Warning

**Problem**: Forge warns about duplicate modid "modupdater".

**Cause**: Both `modupdater-standalone` and `modupdater-launchwrapper` JARs are in the mods folder.

**Solution**: Only use the launchwrapper JAR (`!!!!!modupdater-2.20.jar`). Remove any other ModUpdater JARs.

### mcmod.info Not Included

**Problem**: Mod metadata (name, version, description) not showing in Forge.

**Cause**: mcmod.info file not included in JAR.

**Solution**: 
1. Verify `src/main/resources/mcmod.info` exists in both modules
2. Rebuild with `./gradlew clean build`
3. Check the JAR contents: `jar tf modupdater-launchwrapper/build/libs/!!!!!modupdater-2.20.jar | grep mcmod.info`

### Property Not Set / Crash Never Occurs

**Problem**: The crash never happens even though locked files exist.

**Debugging**:
1. Check logs for: `[ModUpdater] System property 'modupdater.restartRequired' set to: true`
2. If property is set but crash doesn't happen, check for:
   - `[ModUpdater-Tweaker] Restart required detected at init time`
   - `[ModUpdater-Tweaker] Main menu detected`
3. Verify you're reaching the main menu (not stuck on loading screen)
4. Check that the launchwrapper JAR is actually loaded (look for init messages)

### Crash Happens Too Soon

**Problem**: Crash happens before menu is fully visible.

**Solution**: The 3-tick delay (CRASH_DELAY_TICKS) should be sufficient. If not:
1. Edit `ModUpdaterMod.java`
2. Change `CRASH_DELAY_TICKS = 3` to a higher value (e.g., 10)
3. Rebuild

### Java Version Mismatch

**Problem**: Build or runtime errors related to Java version.

**Solution**: 
- Ensure you're using Java 8 (1.8.x)
- Set JAVA_HOME environment variable to JDK 8 location
- Forge 1.7.10 requires Java 8 and will not work with newer versions

## Advanced Configuration

### Changing the Crash Delay

Edit `modupdater-launchwrapper/src/main/java/com/ArfGg57/modupdater/ModUpdaterMod.java`:

```java
// Change this value (in ticks, 20 ticks = 1 second)
private static final int CRASH_DELAY_TICKS = 3;
```

### Disabling Heuristic Main Menu Detection

If the heuristic detection (class name contains "main" and "menu") causes false positives:

Edit `ModUpdaterMod.java`, comment out the heuristic section in `isMainMenuScreen()`:

```java
// Strategy 2: Heuristic - check class name for "main" and "menu"
// String className = screen.getClass().getName().toLowerCase();
// boolean hasMain = className.contains("main");
// boolean hasMenu = className.contains("menu");
// if (hasMain && hasMenu) {
//     return true;
// }
```

## Development Mode

For development and testing:

### Run in IDE (IntelliJ IDEA / Eclipse)

1. Import the project as a Gradle project
2. Let the IDE download dependencies
3. For the standalone module:
   - Run the `setupDecompWorkspace` task (if using ForgeGradle)
   - Use the generated run configurations
4. For the launchwrapper module:
   - Add `-Dmodupdater.restartRequired=true` to JVM arguments for testing

### Run Client Directly with Gradle

```bash
./gradlew :modupdater-standalone:runClient
```

Note: The launchwrapper module doesn't have a runClient task since it's meant to be used as a tweaker in actual Forge environments.

## Additional Resources

- [Forge 1.7.10 Documentation](https://mcforge.readthedocs.io/en/1.7.10/)
- [ForgeGradle Documentation](https://github.com/MinecraftForge/ForgeGradle)
- [Project README](README.md)
- [Quick Start Guide](docs/QUICK_START.md)

## Version Information

- **ModUpdater Version**: 2.20
- **Minecraft Version**: 1.7.10
- **Forge Version**: 10.13.4.1614-1.7.10 (or compatible)
- **Java Version**: 8 (1.8.x)
- **Gradle Version**: 4.10.3 (via wrapper)

## Support

If you encounter issues not covered in this guide:

1. Check existing GitHub issues
2. Review the logs in `.minecraft/logs/fml-client-latest.log`
3. Create a new issue with:
   - Full build output or error message
   - Java version (`java -version`)
   - Operating system
   - Steps to reproduce

## License

This mod is largely AI-generated. See the main [README.md](README.md) for more information.
