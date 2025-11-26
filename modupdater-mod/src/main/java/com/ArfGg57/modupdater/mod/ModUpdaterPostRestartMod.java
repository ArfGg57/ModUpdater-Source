package com.ArfGg57.modupdater.mod;

import com.ArfGg57.modupdater.hash.HashUtils;
import com.ArfGg57.modupdater.pending.PendingUpdateOperation;
import com.ArfGg57.modupdater.pending.PendingUpdateOpsManager;
import com.ArfGg57.modupdater.util.FileUtils;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModUpdater Mod Component - handles deferred update operations after restart.
 * 
 * This mod runs separately from the tweaker. When the game starts, it checks for
 * any pending update operations that couldn't be completed in the previous session
 * (due to locked files). When the user reaches the title screen, it:
 * 1. Deletes the old files
 * 2. Downloads and installs new files (for updates)
 * 3. Shows a dialog explaining the restart was needed
 * 4. Exits the game so the new mods can be loaded
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
    
    // Flag to track if we have pending operations
    private volatile boolean hasPendingOps = false;
    
    // Flag to track if we already processed operations
    private final AtomicBoolean operationsProcessed = new AtomicBoolean(false);
    
    // The pending operations manager
    private PendingUpdateOpsManager pendingOps;
    
    // Results from processing
    private final List<String> processedFiles = new ArrayList<>();
    private final List<String> failedFiles = new ArrayList<>();
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("[ModUpdater-Mod] Initializing post-restart handler...");
        
        // Check if there are pending operations
        if (PendingUpdateOpsManager.hasPendingOperations()) {
            pendingOps = PendingUpdateOpsManager.load();
            if (pendingOps != null && pendingOps.hasOperations()) {
                hasPendingOps = true;
                System.out.println("[ModUpdater-Mod] Found " + pendingOps.getOperations().size() + " pending operation(s)");
                System.out.println("[ModUpdater-Mod] Will process when main menu is reached");
                
                // Register tick handler to detect main menu
                MinecraftForge.EVENT_BUS.register(this);
            } else {
                System.out.println("[ModUpdater-Mod] No pending operations found");
            }
        } else {
            System.out.println("[ModUpdater-Mod] No pending operations file exists");
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // Only process once
        if (!hasPendingOps || operationsProcessed.get()) return;
        
        // Check if we're at the main menu
        GuiScreen currentScreen = null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) currentScreen = mc.currentScreen;
        } catch (Exception e) {
            return;
        }
        
        if (!isMainMenuScreen(currentScreen)) return;
        
        // We're at the main menu - process pending operations
        if (operationsProcessed.compareAndSet(false, true)) {
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
     * Detect if the given screen is a main menu.
     */
    private boolean isMainMenuScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        
        if (screen instanceof GuiMainMenu) {
            return true;
        }
        
        String className = screen.getClass().getName().toLowerCase();
        return className.contains("main") && className.contains("menu");
    }
}
