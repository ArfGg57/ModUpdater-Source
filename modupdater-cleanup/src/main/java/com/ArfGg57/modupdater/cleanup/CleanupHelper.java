package com.ArfGg57.modupdater.cleanup;

import com.ArfGg57.modupdater.pending.PendingUpdateOperation;
import com.ArfGg57.modupdater.pending.PendingUpdateOpsManager;
import com.ArfGg57.modupdater.ui.PostRestartUpdateGui;
import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.hash.HashUtils;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Standalone cleanup helper that runs in a separate JVM.
 * 
 * This helper is launched just before the game crashes due to locked files.
 * It waits for the game process to exit, then processes pending operations
 * (deletes and downloads) before the next game launch.
 * 
 * Usage: java -jar modupdater-cleanup.jar [GAME_PID] [PENDING_OPS_PATH] [GAME_DIR]
 * 
 * If no arguments are provided, it uses defaults:
 * - GAME_PID: Optional, if provided waits for process to exit
 * - PENDING_OPS_PATH: config/ModUpdater/pending-update-ops.json
 * - GAME_DIR: current directory (.)
 */
public class CleanupHelper {
    
    // Configuration
    private static final int PROCESS_WAIT_INTERVAL_MS = 500;
    private static final int MAX_PROCESS_WAIT_MS = 60000; // 60 seconds max wait
    private static final int DELETE_RETRY_COUNT = 10;
    private static final int DELETE_RETRY_DELAY_MS = 500;
    
    private static PostRestartUpdateGui gui;
    private static String gameDir = ".";
    
    public static void main(String[] args) {
        System.out.println("[CleanupHelper] Starting ModUpdater Cleanup Helper");
        System.out.println("[CleanupHelper] Arguments: " + java.util.Arrays.toString(args));
        
        // Parse arguments
        String gamePid = null;
        String pendingOpsPath = null;
        
        if (args.length >= 1 && args[0] != null && !args[0].isEmpty()) {
            gamePid = args[0];
        }
        if (args.length >= 2 && args[1] != null && !args[1].isEmpty()) {
            pendingOpsPath = args[1];
        }
        if (args.length >= 3 && args[2] != null && !args[2].isEmpty()) {
            gameDir = args[2];
        }
        
        try {
            // Initialize GUI on EDT
            SwingUtilities.invokeAndWait(() -> {
                gui = new PostRestartUpdateGui();
                gui.setOnCloseCallback(() -> System.exit(0));
                gui.show();
            });
            
            // Wait for game process to exit if PID provided
            if (gamePid != null) {
                waitForProcessExit(gamePid);
            } else {
                gui.addLog("No game PID provided, proceeding immediately");
                // Small delay to ensure files are released
                Thread.sleep(2000);
            }
            
            // Set working directory for relative paths
            System.setProperty("user.dir", gameDir);
            
            // Load pending operations
            if (pendingOpsPath != null) {
                // Use custom path
                System.out.println("[CleanupHelper] Loading pending ops from: " + pendingOpsPath);
            }
            
            PendingUpdateOpsManager pendingOps = PendingUpdateOpsManager.load();
            
            if (pendingOps == null || !pendingOps.hasOperations()) {
                gui.addLog("No pending operations found");
                gui.setProgress(100);
                gui.showComplete();
                return;
            }
            
            List<PendingUpdateOperation> operations = pendingOps.getOperations();
            gui.addLog("Found " + operations.size() + " pending operation(s)");
            
            int totalOps = operations.size();
            int completedOps = 0;
            int failedOps = 0;
            
            // Process each operation
            for (PendingUpdateOperation op : operations) {
                try {
                    processOperation(op);
                    completedOps++;
                } catch (Exception e) {
                    System.err.println("[CleanupHelper] Operation failed: " + e.getMessage());
                    e.printStackTrace();
                    gui.addLog("FAILED: " + e.getMessage());
                    failedOps++;
                }
                
                // Update progress
                int progress = (int) (((completedOps + failedOps) * 100.0) / totalOps);
                gui.setProgress(progress);
            }
            
            // Clear pending operations file
            if (PendingUpdateOpsManager.clearPendingOperations()) {
                gui.addLog("Cleared pending operations file");
            }
            
            // Show completion
            if (failedOps > 0) {
                gui.addLog("Completed with " + failedOps + " error(s)");
            } else {
                gui.addLog("All operations completed successfully");
            }
            gui.showComplete();
            
        } catch (Exception e) {
            System.err.println("[CleanupHelper] Fatal error: " + e.getMessage());
            e.printStackTrace();
            if (gui != null) {
                gui.addLog("Error: " + e.getMessage());
                gui.showComplete();
            } else {
                // If GUI failed to initialize, just exit
                System.exit(1);
            }
        }
    }
    
    /**
     * Wait for the game process to exit.
     */
    private static void waitForProcessExit(String pid) {
        gui.addLog("Waiting for game process (PID: " + pid + ") to exit...");
        System.out.println("[CleanupHelper] Waiting for process " + pid + " to exit");
        
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < MAX_PROCESS_WAIT_MS) {
            if (!isProcessRunning(pid)) {
                gui.addLog("Game process has exited");
                System.out.println("[CleanupHelper] Process " + pid + " has exited");
                
                // Extra delay to ensure file handles are released
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            
            try {
                Thread.sleep(PROCESS_WAIT_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        gui.addLog("Timeout waiting for game process, proceeding anyway");
        System.out.println("[CleanupHelper] Timeout waiting for process, proceeding");
    }
    
    /**
     * Check if a process is still running.
     */
    private static boolean isProcessRunning(String pid) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                // Windows: use tasklist to check if process exists
                pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/NH");
            } else {
                // Unix: use kill -0 to check if process exists (doesn't actually kill)
                pb = new ProcessBuilder("/bin/sh", "-c", "kill -0 " + pid + " 2>/dev/null");
            }
            
            Process p = pb.start();
            int exitCode = p.waitFor();
            
            if (os.contains("win")) {
                // On Windows, tasklist returns output if process found
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(pid)) {
                        return true;
                    }
                }
                return false;
            } else {
                // On Unix, kill -0 returns 0 if process exists
                return exitCode == 0;
            }
        } catch (Exception e) {
            System.err.println("[CleanupHelper] Error checking process: " + e.getMessage());
            return false; // Assume process is not running if we can't check
        }
    }
    
    /**
     * Process a single pending operation.
     */
    private static void processOperation(PendingUpdateOperation op) throws Exception {
        System.out.println("[CleanupHelper] Processing: " + op.getType() + " - " + op.getOldFilePath());
        
        switch (op.getType()) {
            case DELETE:
                processDeleteOperation(op);
                break;
            case UPDATE:
                processUpdateOperation(op);
                break;
            default:
                throw new Exception("Unknown operation type: " + op.getType());
        }
    }
    
    /**
     * Process a DELETE operation.
     */
    private static void processDeleteOperation(PendingUpdateOperation op) throws Exception {
        File file = new File(op.getOldFilePath());
        
        if (!file.exists()) {
            gui.addLog("Already removed: " + file.getName());
            System.out.println("[CleanupHelper] File already removed: " + file);
            return;
        }
        
        gui.addLog("Deleting: " + file.getName());
        
        boolean deleted = false;
        for (int attempt = 0; attempt < DELETE_RETRY_COUNT && !deleted; attempt++) {
            if (attempt > 0) {
                System.gc(); // Suggest GC to release file handles
                Thread.sleep(DELETE_RETRY_DELAY_MS);
            }
            
            deleted = file.delete();
            
            if (!deleted && file.exists()) {
                System.out.println("[CleanupHelper] Delete attempt " + (attempt + 1) + " failed for: " + file);
            }
        }
        
        if (!deleted && file.exists()) {
            throw new Exception("Failed to delete: " + file.getAbsolutePath());
        }
        
        gui.addLog("Deleted: " + file.getName());
        System.out.println("[CleanupHelper] Successfully deleted: " + file);
    }
    
    /**
     * Process an UPDATE operation (delete old file, download new file).
     */
    private static void processUpdateOperation(PendingUpdateOperation op) throws Exception {
        // Step 1: Delete old file
        File oldFile = new File(op.getOldFilePath());
        
        if (oldFile.exists()) {
            gui.addLog("Removing old: " + oldFile.getName());
            
            boolean deleted = false;
            for (int attempt = 0; attempt < DELETE_RETRY_COUNT && !deleted; attempt++) {
                if (attempt > 0) {
                    System.gc();
                    Thread.sleep(DELETE_RETRY_DELAY_MS);
                }
                deleted = oldFile.delete();
            }
            
            if (!deleted && oldFile.exists()) {
                throw new Exception("Failed to delete old file: " + oldFile.getAbsolutePath());
            }
            
            System.out.println("[CleanupHelper] Deleted old file: " + oldFile);
        }
        
        // Step 2: Download new file
        String downloadUrl = op.getNewFileUrl();
        String newFileName = op.getNewFileName();
        String installLocation = op.getInstallLocation();
        String expectedHash = op.getExpectedHash();
        
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            gui.addLog("No download URL, delete-only operation");
            return;
        }
        
        if (newFileName == null || newFileName.isEmpty()) {
            newFileName = FileUtils.extractFileNameFromUrl(downloadUrl);
        }
        
        if (installLocation == null || installLocation.isEmpty()) {
            installLocation = "mods";
        }
        
        File targetDir = new File(gameDir, installLocation);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        File targetFile = new File(targetDir, newFileName);
        File tmpFile = new File(targetDir, newFileName + ".tmp");
        
        gui.addLog("Downloading: " + newFileName);
        System.out.println("[CleanupHelper] Downloading " + downloadUrl + " to " + targetFile);
        
        try {
            // Download to temp file
            FileUtils.downloadFile(downloadUrl, tmpFile, 3);
            
            // Verify hash if provided
            if (expectedHash != null && !expectedHash.isEmpty()) {
                String actualHash = HashUtils.sha256Hex(tmpFile);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    tmpFile.delete();
                    throw new Exception("Hash mismatch for " + newFileName + 
                        ": expected " + expectedHash + ", got " + actualHash);
                }
                System.out.println("[CleanupHelper] Hash verified: " + actualHash);
            }
            
            // Move temp file to final location
            if (targetFile.exists()) {
                targetFile.delete();
            }
            
            if (!tmpFile.renameTo(targetFile)) {
                // Fallback: copy and delete
                FileUtils.atomicMoveWithRetries(tmpFile, targetFile, 5, 200);
            }
            
            gui.addLog("Installed: " + newFileName);
            System.out.println("[CleanupHelper] Successfully installed: " + targetFile);
            
        } finally {
            // Clean up temp file if it still exists
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
    }
}
