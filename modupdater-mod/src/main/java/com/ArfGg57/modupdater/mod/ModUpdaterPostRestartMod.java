package com.ArfGg57.modupdater.mod;

import com.ArfGg57.modupdater.hash.HashUtils;
import com.ArfGg57.modupdater.pending.PendingUpdateOperation;
import com.ArfGg57.modupdater.pending.PendingUpdateOpsManager;
import com.ArfGg57.modupdater.restart.CrashCoordinator;
import com.ArfGg57.modupdater.restart.CleanupHelperLauncher;
import com.ArfGg57.modupdater.util.FileUtils;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
/**
 * ModUpdater Mod Component - handles two scenarios:
 * 
 * 1. CRASH ENFORCEMENT: When update just ran and files are locked (restartRequired=true),
 *    this mod detects when main menu is reached and crashes the game with a Forge crash report.
 *    This forces a restart so locked files can be released.
 * 
 * 2. POST-RESTART PROCESSING: After the user restarts the game, this mod checks for pending
 *    update operations that couldn't be completed in the previous session. When main menu
 *    is reached, it processes those operations and exits.
 */
@Mod(modid = ModUpdaterPostRestartMod.MODID, name = ModUpdaterPostRestartMod.NAME, version = ModUpdaterPostRestartMod.VERSION, acceptedMinecraftVersions = "*")
public class ModUpdaterPostRestartMod {
    
    public static final String MODID = "modupdater-mod";
    public static final String NAME = "ModUpdater Post-Restart Handler";
    public static final String VERSION = "2.20";
    
    // Configuration constants
    private static final int INITIAL_DELAY_MS = 500;        // Delay before starting operations
    private static final int DELETE_RETRY_COUNT = 5;         // Number of retries for file deletion
    private static final int DELETE_RETRY_DELAY_MS = 200;    // Delay between deletion retries
    
    // Crash enforcement constants
    private static final int CRASH_DELAY_TICKS = 3;         // Delay before crash for GUI stability
    private static final int TIMEOUT_TICKS = 600;           // 30 seconds at 20 TPS - fallback timeout
    private static final int CLEANUP_HELPER_START_DELAY_MS = 2000;  // Delay before killing JVM to let cleanup helper start and show GUI (increased from 500ms)
    
    // Flag to track if restart is required (crash enforcement mode)
    private volatile boolean restartRequiredFlag = false;
    
    // Flag to track if we have pending operations (post-restart mode)
    private volatile boolean hasPendingOps = false;
    
    // Flag to track if mod post-initialization is complete (similar to ding mod pattern)
    // Using volatile for thread-safe access from event handlers
    public static volatile boolean postInit = false;
    
    // Flag to track if we already processed operations or crashed
    private final AtomicBoolean actionTaken = new AtomicBoolean(false);
    
    // Crash scheduling state
    private volatile boolean crashScheduled = false;
    private volatile int crashDelayTicks = 0;
    private volatile int ticksSinceInit = 0;
    private volatile String crashMessage = "";
    
    // The pending operations manager
    private PendingUpdateOpsManager pendingOps;
    
    // Results from processing
    private final List<String> processedFiles = new ArrayList<>();
    private final List<String> failedFiles = new ArrayList<>();
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println("[ModUpdater-Mod] Pre-initializing post-restart handler...");
        
        // Check if restart is required (crash enforcement mode)
        String restartRequired = System.getProperty("modupdater.restartRequired");
        System.out.println("[ModUpdater-Mod] modupdater.restartRequired = " + restartRequired);
        
        if ("true".equals(restartRequired)) {
            System.out.println("[ModUpdater-Mod] Restart required detected - will crash when main menu is reached");
            restartRequiredFlag = true;
            crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
            
            // Register event handler for crash enforcement
            MinecraftForge.EVENT_BUS.register(this);
            System.out.println("[ModUpdater-Mod] Event handler registered for crash enforcement");
            return; // Don't check for pending ops, we're going to crash anyway
        }
        
        // Check if there are pending operations (post-restart mode)
        if (PendingUpdateOpsManager.hasPendingOperations()) {
            pendingOps = PendingUpdateOpsManager.load();
            if (pendingOps != null && pendingOps.hasOperations()) {
                hasPendingOps = true;
                System.out.println("[ModUpdater-Mod] Found " + pendingOps.getOperations().size() + " pending operation(s)");
                System.out.println("[ModUpdater-Mod] Will process when main menu is reached");
                
                // Register event handler to detect main menu
                MinecraftForge.EVENT_BUS.register(this);
            } else {
                System.out.println("[ModUpdater-Mod] No pending operations found");
            }
        } else {
            System.out.println("[ModUpdater-Mod] No pending operations file exists");
        }
    }
    
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        System.out.println("[ModUpdater-Mod] Post-initialization complete");
        postInit = true;
    }
    
    /**
     * GuiOpenEvent handler - more reliable way to detect main menu opening.
     * Uses LOWEST priority to ensure all other handlers run first (similar to ding mod pattern).
     * 
     * IMPORTANT: This handler only performs crash enforcement (launching cleanup helper and killing JVM)
     * when restartRequiredFlag is true. In pending ops mode (second restart), this handler does nothing
     * and lets onClientTick handle the pending operations processing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        // Only process after post-init is complete
        if (!postInit) return;

        // Only process once
        if (actionTaken.get()) return;

        // If crash already executed by any mod instance, stop processing
        if (CrashCoordinator.isCrashExecuted()) {
            actionTaken.set(true);
            return;
        }

        // Check if the GUI being opened is a main menu
        if (!isMainMenuScreen(event.gui)) return;

        System.out.println("[ModUpdater-Mod] Main menu opened (via GuiOpenEvent): " +
                (event.gui != null ? event.gui.getClass().getName() : "null"));

        // ONLY perform crash enforcement if restartRequiredFlag is true
        // In pending ops mode (second restart after crash), do NOT launch cleanup helper or kill JVM
        // Let onClientTick handle pending operations processing instead
        if (!restartRequiredFlag) {
            System.out.println("[ModUpdater-Mod] Not in crash enforcement mode, skipping JVM kill (pending ops will be handled by tick handler)");
            return;
        }

        // Try to claim crash execution (thread-safe, prevents duplicate crashes from tick handler)
        if (!CrashCoordinator.tryClaim()) {
            System.out.println("[ModUpdater-Mod] Another handler already claimed crash execution, skipping");
            actionTaken.set(true);
            return;
        }

        // Mark we've taken action so we don't double-run
        actionTaken.set(true);
        System.out.println("[ModUpdater-Mod] Crash enforcement mode active - preparing to launch cleanup helper and terminate JVM");

        // Launch cleanup helper and kill JVM using the shared helper method
        launchCleanupHelperAndKillJvm("ModUpdater-ForceKill");
    }



    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // Only process after post-init is complete (similar to ding mod pattern)
        if (!postInit) return;
        
        // Increment tick counter
        ticksSinceInit++;
        
        // Only process once
        if (actionTaken.get()) return;
        
        // If crash already executed by any mod instance, stop processing
        if (CrashCoordinator.isCrashExecuted()) {
            actionTaken.set(true);
            return;
        }
        
        // Poll for restart required property (handles late setting by UpdaterCore)
        if (!restartRequiredFlag) {
            String restartRequired = System.getProperty("modupdater.restartRequired");
            if ("true".equals(restartRequired)) {
                System.out.println("[ModUpdater-Mod] Restart required property detected late (after init)");
                restartRequiredFlag = true;
                crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
                ticksSinceInit = 0; // Reset counter
            }
        }
        
        // Safely access Minecraft and current screen
        GuiScreen currentScreen = null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) currentScreen = mc.currentScreen;
        } catch (Exception e) {
            return;
        }
        
        // Handle crash enforcement mode
        if (restartRequiredFlag) {
            handleCrashEnforcement(currentScreen);
            return;
        }
        
        // Handle post-restart mode (pending operations)
        if (hasPendingOps) {
            handlePendingOperations(currentScreen);
        }
    }
    
    /**
     * Handle crash enforcement when restart is required.
     */
    private void handleCrashEnforcement(GuiScreen currentScreen) {
        // Handle crash countdown if already scheduled
        if (crashScheduled) {
            if (crashDelayTicks > 0) {
                if (crashDelayTicks == CRASH_DELAY_TICKS) {
                    System.out.println("[ModUpdater-Mod] Crash scheduled, waiting " + crashDelayTicks + " tick(s) for GUI stability");
                }
                crashDelayTicks--;
                if (crashDelayTicks == 0) {
                    System.out.println("[ModUpdater-Mod] Delay complete - executing crash now");
                    performCrash(currentScreen);
                }
            }
            return;
        }
        
        // Check if we're at main menu and schedule crash
        if (isMainMenuScreen(currentScreen)) {
            System.out.println("[ModUpdater-Mod] Main menu detected: " + (currentScreen != null ? currentScreen.getClass().getName() : "null"));
            System.out.println("[ModUpdater-Mod] Scheduling crash with " + CRASH_DELAY_TICKS + " tick delay for GUI stability");
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
            return;
        }
        
        // Timeout fallback: If we've been waiting too long without detecting main menu,
        // crash anyway. This handles edge cases where main menu detection completely fails.
        if (ticksSinceInit >= TIMEOUT_TICKS && !crashScheduled) {
            System.out.println("[ModUpdater-Mod] WARNING: Main menu not detected after " + TIMEOUT_TICKS + " ticks");
            System.out.println("[ModUpdater-Mod] Current screen: " + (currentScreen != null ? currentScreen.getClass().getName() : "null"));
            System.out.println("[ModUpdater-Mod] Triggering timeout fallback crash - restart is required");
            crashMessage = "ModUpdater deferred crash trigger (timeout fallback). Restart required due to locked files.";
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
        }
    }
    
    /**
     * Perform the crash by launching the cleanup helper and externally killing the JVM.
     * This is a fallback path if onGuiOpen didn't handle the crash (e.g., main menu detection timing issues).
     */
    private void performCrash(GuiScreen currentScreen) {
        // Try to claim the crash execution (thread-safe, prevents duplicate crashes)
        if (!CrashCoordinator.tryClaim()) {
            System.out.println("[ModUpdater-Mod] Another mod instance already claimed crash execution, skipping");
            actionTaken.set(true);
            return;
        }
        
        actionTaken.set(true);
        
        // Unregister event handler to prevent further ticks
        try { 
            MinecraftForge.EVENT_BUS.unregister(this); 
        } catch (Exception ignored) {}
        
        System.out.println("[ModUpdater-Mod] ========================================");
        System.out.println("[ModUpdater-Mod] EXECUTING DEFERRED CRASH (via tick handler fallback)");
        System.out.println("[ModUpdater-Mod] ========================================");
        
        // Launch cleanup helper and kill JVM using the shared helper method
        launchCleanupHelperAndKillJvm("ModUpdater-ForceKill-Fallback");
    }
    
    /**
     * Handle pending operations processing.
     */
    private void handlePendingOperations(GuiScreen currentScreen) {
        if (!isMainMenuScreen(currentScreen)) return;
        
        // We're at the main menu - process pending operations
        if (actionTaken.compareAndSet(false, true)) {
            System.out.println("[ModUpdater-Mod] Main menu detected - processing pending operations...");
            
            // Unregister tick handler
            try {
                MinecraftForge.EVENT_BUS.unregister(this);
            } catch (Exception ignored) {}
            
            // Process operations in a separate thread to avoid blocking the game
            new Thread(this::processOperationsAndExit, "ModUpdater-PostRestart").start();
        }
    }
    
    /**
     * Process all pending operations and exit the game.
     */
    private void processOperationsAndExit() {
        try {
            Thread.sleep(INITIAL_DELAY_MS); // Give the game a moment to stabilize
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            System.out.println("[ModUpdater-Mod] Interrupted during initial delay, continuing...");
        }
        
        System.out.println("[ModUpdater-Mod] ========================================");
        System.out.println("[ModUpdater-Mod] Processing pending update operations...");
        System.out.println("[ModUpdater-Mod] ========================================");
        
        for (PendingUpdateOperation op : pendingOps.getOperations()) {
            try {
                processOperation(op);
            } catch (Exception e) {
                System.err.println("[ModUpdater-Mod] Failed to process operation: " + e.getMessage());
                e.printStackTrace();
                failedFiles.add(op.getOldFilePath() + " - Error: " + e.getMessage());
            }
        }
        
        // Clear the pending operations file
        PendingUpdateOpsManager.clearPendingOperations();
        
        System.out.println("[ModUpdater-Mod] ========================================");
        System.out.println("[ModUpdater-Mod] Processing complete!");
        System.out.println("[ModUpdater-Mod] Processed: " + processedFiles.size());
        System.out.println("[ModUpdater-Mod] Failed: " + failedFiles.size());
        System.out.println("[ModUpdater-Mod] ========================================");
        
        // Show dialog and exit
        showCompletionDialogAndExit();
    }
    
    /**
     * Process a single operation.
     */
    private void processOperation(PendingUpdateOperation op) throws Exception {
        System.out.println("[ModUpdater-Mod] Processing: " + op.getType() + " - " + op.getOldFilePath());
        
        File oldFile = new File(op.getOldFilePath());
        
        // Step 1: Delete the old file
        if (oldFile.exists()) {
            System.out.println("[ModUpdater-Mod] Deleting: " + oldFile.getAbsolutePath());
            boolean deleted = oldFile.delete();
            if (!deleted) {
                // Try with retries
                for (int i = 0; i < DELETE_RETRY_COUNT && oldFile.exists(); i++) {
                    try {
                        Thread.sleep(DELETE_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Interrupted while retrying deletion");
                    }
                    System.gc(); // Suggest GC to release file handles
                    deleted = oldFile.delete();
                    if (deleted) break;
                }
            }
            
            if (!deleted && oldFile.exists()) {
                throw new Exception("Failed to delete file after retries: " + oldFile.getAbsolutePath());
            }
            System.out.println("[ModUpdater-Mod] Successfully deleted: " + oldFile.getName());
        } else {
            System.out.println("[ModUpdater-Mod] Old file already gone: " + oldFile.getAbsolutePath());
        }
        
        // Step 2: If this is an UPDATE operation, download the new file
        if (op.getType() == PendingUpdateOperation.OperationType.UPDATE) {
            String newFileUrl = op.getNewFileUrl();
            String newFileName = op.getNewFileName();
            String installLocation = op.getInstallLocation();
            String expectedHash = op.getExpectedHash();
            
            if (newFileUrl == null || newFileUrl.isEmpty()) {
                throw new Exception("UPDATE operation missing newFileUrl");
            }
            
            if (newFileName == null || newFileName.isEmpty()) {
                newFileName = FileUtils.extractFileNameFromUrl(newFileUrl);
            }
            
            if (installLocation == null || installLocation.isEmpty()) {
                installLocation = "mods";
            }
            
            File targetDir = new File(installLocation);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            File targetFile = new File(targetDir, newFileName);
            
            System.out.println("[ModUpdater-Mod] Downloading: " + newFileUrl);
            System.out.println("[ModUpdater-Mod] Target: " + targetFile.getAbsolutePath());
            
            // Download to temp file first
            File tmpFile = new File(targetDir, newFileName + ".tmp");
            
            try {
                FileUtils.downloadFile(newFileUrl, tmpFile);
                
                // Verify hash if provided
                if (expectedHash != null && !expectedHash.isEmpty()) {
                    String actualHash = HashUtils.sha256Hex(tmpFile);
                    if (!FileUtils.hashEquals(expectedHash, actualHash)) {
                        throw new Exception("Hash mismatch: expected " + expectedHash + ", got " + actualHash);
                    }
                    System.out.println("[ModUpdater-Mod] Hash verified successfully");
                }
                
                // Move to final location
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                if (!tmpFile.renameTo(targetFile)) {
                    throw new Exception("Failed to rename temp file to target");
                }
                
                System.out.println("[ModUpdater-Mod] Successfully installed: " + newFileName);
                processedFiles.add(newFileName + " (updated)");
                
            } finally {
                // Clean up temp file if it still exists
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
            }
            
        } else {
            // DELETE operation
            processedFiles.add(oldFile.getName() + " (deleted)");
        }
    }
    
    /**
     * Show a completion dialog and exit the game.
     */
    private void showCompletionDialogAndExit() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder message = new StringBuilder();
            message.append("<html><body style='width: 350px;'>");
            message.append("<h2>ModUpdater - Restart Complete</h2>");
            message.append("<p>The following operations were completed:</p>");
            message.append("<ul>");
            
            for (String file : processedFiles) {
                message.append("<li>").append(file).append("</li>");
            }
            
            if (!failedFiles.isEmpty()) {
                message.append("</ul><p style='color: red;'>The following operations failed:</p><ul>");
                for (String file : failedFiles) {
                    message.append("<li>").append(file).append("</li>");
                }
            }
            
            message.append("</ul>");
            message.append("<p><b>Please restart the game to load the updated mods.</b></p>");
            message.append("</body></html>");
            
            JOptionPane.showMessageDialog(null, message.toString(), 
                "ModUpdater - Restart Required", JOptionPane.INFORMATION_MESSAGE);
            
            System.out.println("[ModUpdater-Mod] Exiting game for restart...");
            System.exit(0);
        });
    }
    
    /**
     * Launch the cleanup helper and spawn external JVM kill thread.
     * This is used by both onGuiOpen and performCrash to ensure consistent behavior.
     * 
     * @param threadName The name for the kill thread (for logging purposes)
     */
    private void launchCleanupHelperAndKillJvm(String threadName) {
        // --- Launch the cleanup helper before the game is killed ---
        File mcDir = Minecraft.getMinecraft().mcDataDir;
        System.out.println("[ModUpdater-Mod] Game directory: " + mcDir.getAbsolutePath());
        
        boolean helperLaunched = launchCleanupHelperWithFallback(mcDir);
        
        if (!helperLaunched) {
            System.out.println("[ModUpdater-Mod] WARNING: Cleanup helper not available; pending operations will be processed on next game launch.");
        } else {
            System.out.println("[ModUpdater-Mod] Cleanup helper launched successfully, will process operations after JVM exit");
        }

        // Flush output streams to ensure logs are written before JVM is killed
        System.out.flush();
        System.err.flush();

        // --- Spawn external process to forcibly kill the JVM (works around FML security manager) ---
        new Thread(() -> {
            try { Thread.sleep(CLEANUP_HELPER_START_DELAY_MS); } catch (InterruptedException ignored) {}

            System.out.println("[ModUpdater-Mod] Preparing external kill of JVM to release locked files");
            killCurrentJvm();
        }, threadName).start();
    }
    
    /**
     * Launch the cleanup helper using the primary method (CleanupHelperLauncher) 
     * with fallback to legacy modupdater.cleanupHelperCmd property.
     * 
     * @param mcDir The Minecraft data directory
     * @return true if the helper was launched successfully, false otherwise
     */
    private boolean launchCleanupHelperWithFallback(File mcDir) {
        boolean helperLaunched = CleanupHelperLauncher.launchCleanupHelper(mcDir);
        
        // Fallback: try legacy modupdater.cleanupHelperCmd property
        if (!helperLaunched) {
            String helperCmd = System.getProperty("modupdater.cleanupHelperCmd", "").trim();
            if (!helperCmd.isEmpty()) {
                try {
                    String[] cmd = helperCmd.split(" ");
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(new File(mcDir, "config/ModUpdater"));
                    pb.start();
                    System.out.println("[ModUpdater-Mod] Launched cleanup helper via modupdater.cleanupHelperCmd");
                    helperLaunched = true;
                } catch (IOException ioe) {
                    System.err.println("[ModUpdater-Mod] Failed to launch cleanup helper: " + ioe.getMessage());
                } catch (SecurityException se) {
                    System.err.println("[ModUpdater-Mod] SecurityException launching cleanup helper: " + se.getMessage());
                }
            }
        }
        
        return helperLaunched;
    }
    
    /**
     * Kill the current JVM using an external process.
     * Uses OS-specific commands: taskkill on Windows, kill -9 on Unix.
     */
    private void killCurrentJvm() {
        // Get current JVM PID (RuntimeMXBean name format "pid@host")
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = jvmName;
        if (jvmName != null && jvmName.contains("@")) {
            pid = jvmName.split("@")[0];
        }
        System.out.println("[ModUpdater-Mod] Current JVM PID: " + pid);

        // Build OS-specific kill command
        String os = System.getProperty("os.name").toLowerCase();
        String[] killCmd;
        if (os.contains("win")) {
            // Use taskkill to force-terminate the JVM process
            killCmd = new String[] { "cmd", "/c", "taskkill", "/PID", pid, "/F" };
        } else {
            // Unix-like: use kill -9
            killCmd = new String[] { "/bin/sh", "-c", "kill -9 " + pid };
        }

        System.out.println("[ModUpdater-Mod] Executing external kill command: " + Arrays.toString(killCmd));
        System.out.flush();
        
        try {
            ProcessBuilder pb = new ProcessBuilder(killCmd);
            pb.inheritIO();
            pb.start();
            System.out.println("[ModUpdater-Mod] External kill command started successfully");
        } catch (IOException ioe) {
            System.err.println("[ModUpdater-Mod] Failed to start external kill command: " + ioe.getMessage());
        } catch (SecurityException se) {
            System.err.println("[ModUpdater-Mod] SecurityException starting kill process: " + se.getMessage());
        }
    }

    /**
     * Detect if the given screen is a main menu.
     * Uses multiple patterns to detect various custom main menu implementations.
     */
    private boolean isMainMenuScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        
        if (screen instanceof GuiMainMenu) {
            return true;
        }
        
        String className = screen.getClass().getName().toLowerCase();
        
        // Pattern 1: Contains both "main" and "menu" (e.g., MainMenuScreen, GuiMainMenu)
        if (className.contains("main") && className.contains("menu")) {
            return true;
        }
        
        // Pattern 2: Contains "custommenu" or "guicustom" (CustomMainMenu mod uses GuiCustomMenu)
        if (className.contains("custommenu") || className.contains("guicustom")) {
            return true;
        }
        
        // Pattern 3: Contains "title" and "screen" or "menu" (e.g., TitleScreen, TitleMenu)
        if (className.contains("title") && (className.contains("screen") || className.contains("menu"))) {
            return true;
        }
        
        // Pattern 4: Ends with "mainmenu" or "titlemenu" or "titlescreen"
        if (className.endsWith("mainmenu") || className.endsWith("titlemenu") || className.endsWith("titlescreen")) {
            return true;
        }
        
        // Pattern 5: lumien's CustomMainMenu mod specifically uses these patterns
        if (className.contains("lumien") && className.contains("menu")) {
            return true;
        }
        
        return false;
    }
}
