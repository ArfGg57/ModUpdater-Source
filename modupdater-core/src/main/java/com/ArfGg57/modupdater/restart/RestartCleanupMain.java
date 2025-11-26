package com.ArfGg57.modupdater.restart;

import com.ArfGg57.modupdater.pending.PendingUpdateOperation;
import com.ArfGg57.modupdater.pending.PendingUpdateOpsManager;
import com.ArfGg57.modupdater.ui.PostRestartUpdateGui;
import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.hash.HashUtils;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Standalone helper that processes pending update operations after a restart.
 * 
 * Shows a styled GUI that:
 * 1. Displays "A restart was required for this modpack update. Finishing Update..."
 * 2. Shows progress as it deletes and downloads files
 * 3. Displays "Update finished! Please relaunch your game." when complete
 */
public class RestartCleanupMain {
    
    private static PostRestartUpdateGui gui;
    
    public static void main(String[] args) throws Exception {
        System.out.println("[RestartCleanup] Starting post-restart cleanup process");
        
        // Initialize the GUI on the EDT
        SwingUtilities.invokeAndWait(() -> {
            gui = new PostRestartUpdateGui();
            gui.setOnCloseCallback(() -> System.exit(0));  // Exit when user clicks Close
            gui.show();
        });
        
        try {
            // Load pending operations if available
            PendingUpdateOpsManager pendingOps = PendingUpdateOpsManager.load();
            
            // Also read the legacy locked files list from args if provided
            List<File> legacyFilesToDelete = new ArrayList<>();
            if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
                legacyFilesToDelete = readList(args[0]);
            }
            
            // Calculate total operations for progress
            int totalOps = legacyFilesToDelete.size();
            if (pendingOps != null) {
                totalOps += pendingOps.getOperations().size();
            }
            
            if (totalOps == 0) {
                gui.addLog("No pending operations found");
                gui.setProgress(100);
                gui.showComplete();
                return;
            }
            
            gui.addLog("Found " + totalOps + " operation(s) to process");
            int completedOps = 0;
            
            // First, process legacy delete list (for backwards compatibility)
            if (!legacyFilesToDelete.isEmpty()) {
                gui.addLog("Processing " + legacyFilesToDelete.size() + " legacy delete(s)...");
                for (File f : legacyFilesToDelete) {
                    if (f.exists()) {
                        try {
                            Files.deleteIfExists(f.toPath());
                            gui.addLog("Deleted: " + f.getName());
                            System.out.println("[RestartCleanup] Deleted: " + f);
                        } catch (Exception ex) {
                            gui.addLog("Failed to delete: " + f.getName());
                            System.err.println("[RestartCleanup] Failed to delete " + f + ": " + ex);
                        }
                    } else {
                        gui.addLog("Already removed: " + f.getName());
                    }
                    completedOps++;
                    updateProgress(completedOps, totalOps);
                }
            }
            
            // Then, process pending update operations (new system)
            if (pendingOps != null && pendingOps.hasOperations()) {
                gui.addLog("Processing " + pendingOps.getOperations().size() + " pending operation(s)...");
                
                for (PendingUpdateOperation op : pendingOps.getOperations()) {
                    try {
                        processOperation(op);
                    } catch (Exception ex) {
                        gui.addLog("Error: " + ex.getMessage());
                        System.err.println("[RestartCleanup] Error processing operation: " + ex);
                        ex.printStackTrace();
                    }
                    completedOps++;
                    updateProgress(completedOps, totalOps);
                }
                
                // Clear pending operations file after successful processing
                PendingUpdateOpsManager.clearPendingOperations();
                gui.addLog("Cleared pending operations file");
            }
            
            // Clear restart artifacts
            CrashUtils.clearRestartArtifacts();
            
            // Show completion
            gui.addLog("All operations completed");
            gui.showComplete();
            
        } catch (Exception e) {
            System.err.println("[RestartCleanup] Error during cleanup: " + e);
            e.printStackTrace();
            gui.addLog("Error: " + e.getMessage());
            gui.showComplete();  // Still show complete so user can close
        }
    }
    
    /**
     * Process a single pending update operation
     */
    private static void processOperation(PendingUpdateOperation op) throws Exception {
        String reason = op.getReason() != null ? op.getReason() : "";
        
        switch (op.getType()) {
            case DELETE:
                processDeleteOperation(op);
                break;
            case UPDATE:
                processUpdateOperation(op);
                break;
            default:
                gui.addLog("Unknown operation type: " + op.getType());
        }
    }
    
    /**
     * Process a DELETE operation - just delete the old file
     */
    private static void processDeleteOperation(PendingUpdateOperation op) throws Exception {
        File oldFile = new File(op.getOldFilePath());
        
        if (oldFile.exists()) {
            gui.addLog("Deleting: " + oldFile.getName());
            try {
                Files.deleteIfExists(oldFile.toPath());
                System.out.println("[RestartCleanup] Deleted: " + oldFile);
            } catch (Exception ex) {
                gui.addLog("Failed to delete: " + oldFile.getName());
                throw new Exception("Failed to delete " + oldFile + ": " + ex.getMessage());
            }
        } else {
            gui.addLog("Already removed: " + oldFile.getName());
        }
    }
    
    /**
     * Process an UPDATE operation - delete old file and download new file
     */
    private static void processUpdateOperation(PendingUpdateOperation op) throws Exception {
        File oldFile = new File(op.getOldFilePath());
        String downloadUrl = op.getNewFileUrl();
        String newFileName = op.getNewFileName();
        String installLocation = op.getInstallLocation();
        String expectedHash = op.getExpectedHash();
        
        // Step 1: Delete the old file
        if (oldFile.exists()) {
            gui.addLog("Deleting old: " + oldFile.getName());
            try {
                Files.deleteIfExists(oldFile.toPath());
                System.out.println("[RestartCleanup] Deleted old file: " + oldFile);
            } catch (Exception ex) {
                gui.addLog("Failed to delete: " + oldFile.getName());
                throw new Exception("Failed to delete old file " + oldFile + ": " + ex.getMessage());
            }
        }
        
        // Step 2: Download the new file
        if (downloadUrl != null && !downloadUrl.isEmpty() && newFileName != null && !newFileName.isEmpty()) {
            gui.addLog("Downloading: " + newFileName);
            
            File targetDir = new File(installLocation != null ? installLocation : "mods");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            // Download to temp file first
            File tmpFile = new File(targetDir, newFileName + "-" + UUID.randomUUID().toString() + ".tmp");
            File targetFile = new File(targetDir, newFileName);
            
            try {
                // Download the file
                FileUtils.downloadFile(downloadUrl, tmpFile, 3);
                System.out.println("[RestartCleanup] Downloaded to temp: " + tmpFile);
                
                // Verify hash if provided
                if (expectedHash != null && !expectedHash.trim().isEmpty()) {
                    String actualHash = HashUtils.sha256Hex(tmpFile);
                    if (!expectedHash.equalsIgnoreCase(actualHash)) {
                        gui.addLog("Hash mismatch - retrying download...");
                        System.err.println("[RestartCleanup] Hash mismatch. Expected: " + expectedHash + ", Got: " + actualHash);
                        tmpFile.delete();
                        
                        // Retry once more
                        FileUtils.downloadFile(downloadUrl, tmpFile, 3);
                        actualHash = HashUtils.sha256Hex(tmpFile);
                        if (!expectedHash.equalsIgnoreCase(actualHash)) {
                            tmpFile.delete();
                            throw new Exception("Hash verification failed after retry");
                        }
                    }
                }
                
                // Move temp file to final location
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                FileUtils.atomicMoveWithRetries(tmpFile, targetFile, 5, 200);
                
                gui.addLog("Installed: " + newFileName);
                System.out.println("[RestartCleanup] Installed: " + targetFile);
                
            } catch (Exception ex) {
                // Clean up temp file on failure
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
                throw new Exception("Download failed for " + newFileName + ": " + ex.getMessage());
            }
        }
    }
    
    /**
     * Update progress in the GUI
     */
    private static void updateProgress(int completed, int total) {
        if (total <= 0) {
            gui.setProgress(100);
            return;
        }
        int percent = (int) ((completed * 100L) / total);
        gui.setProgress(Math.min(Math.max(percent, 0), 100));
    }
    
    /**
     * Read a list of file paths from a text file
     */
    private static List<File> readList(String listPath) throws Exception {
        List<File> files = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(listPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    files.add(new File(line.trim()));
                }
            }
        }
        return files;
    }
}
