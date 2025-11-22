package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.restart.CrashUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Utility class to handle restart enforcement logic.
 * Processes restart flags and attempts to clean up locked files.
 */
public final class RestartEnforcer {
    private RestartEnforcer() {}
    
    /**
     * Attempts to process restart flag if present.
     * 
     * @param allowUI If true, will initiate a delayed exit after cleanup.
     *                If false, will only attempt cleanup without exiting.
     */
    public static void tryProcessRestartFlag(boolean allowUI) {
        // Check if restart is required
        if (!CrashUtils.isRestartRequired()) {
            return;
        }
        
        System.out.println("[ModUpdater] Restart flag detected. Processing locked files...");
        
        // Read the list of locked files
        List<File> lockedFiles = CrashUtils.readPendingLockedFiles();
        
        if (lockedFiles.isEmpty()) {
            System.out.println("[ModUpdater] No locked files found in list.");
        } else {
            System.out.println("[ModUpdater] Attempting to delete " + lockedFiles.size() + " locked file(s)...");
            
            int successCount = 0;
            int failCount = 0;
            
            for (File file : lockedFiles) {
                try {
                    if (Files.deleteIfExists(file.toPath())) {
                        System.out.println("[ModUpdater] Successfully deleted: " + file.getAbsolutePath());
                        successCount++;
                    } else {
                        System.out.println("[ModUpdater] File not found (already deleted?): " + file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    System.err.println("[ModUpdater] Failed to delete " + file.getAbsolutePath() + ": " + e.getMessage());
                    failCount++;
                }
            }
            
            System.out.println("[ModUpdater] Deletion complete: " + successCount + " succeeded, " + failCount + " failed");
        }
        
        // If UI is allowed (post-init phase), schedule a delayed exit
        if (allowUI) {
            String message = CrashUtils.readRestartMessage();
            System.out.println("[ModUpdater] " + message);
            System.out.println("[ModUpdater] Scheduling exit in 3 seconds...");
            
            // Start a thread to delay the exit
            Thread exitThread = new Thread(() -> {
                try {
                    Thread.sleep(3000); // 3 second delay
                    
                    // Clear restart artifacts before exiting
                    CrashUtils.clearRestartArtifacts();
                    System.out.println("[ModUpdater] Exiting to complete restart...");
                    
                    System.exit(0);
                } catch (InterruptedException e) {
                    System.err.println("[ModUpdater] Exit thread interrupted: " + e.getMessage());
                }
            }, "ModUpdater-RestartEnforcer");
            
            exitThread.setDaemon(false); // Ensure it doesn't prevent JVM exit
            exitThread.start();
        } else {
            System.out.println("[ModUpdater] Cleanup attempted during init phase. Exit will be deferred to post-init.");
        }
    }
}
