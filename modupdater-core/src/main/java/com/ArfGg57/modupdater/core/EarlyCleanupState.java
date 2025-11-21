package com.ArfGg57.modupdater.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks files that were moved during early cleanup phase.
 * These files can be restored if user cancels the confirmation dialog.
 */
public class EarlyCleanupState {
    
    private static EarlyCleanupState instance;
    
    /**
     * Maps original file path to temporary backup location
     */
    private final Map<String, String> movedFiles = new HashMap<>();
    
    /**
     * List of mod file paths (full path and name) that were removed during early cleanup
     */
    private final List<String> removedModNames = new ArrayList<>();
    
    private EarlyCleanupState() {}
    
    /**
     * Get or create the singleton instance
     */
    public static synchronized EarlyCleanupState getInstance() {
        if (instance == null) {
            instance = new EarlyCleanupState();
        }
        return instance;
    }
    
    /**
     * Record that a file was moved to temporary location
     * 
     * @param originalPath Original file path
     * @param tempPath Temporary backup path
     */
    public void recordMovedFile(String originalPath, String tempPath) {
        movedFiles.put(originalPath, tempPath);
    }
    
    /**
     * Record a mod that was removed (for showing in confirmation dialog)
     * 
     * @param modPathAndName Full path and name of the removed mod file
     */
    public void recordRemovedMod(String modPathAndName) {
        removedModNames.add(modPathAndName);
    }
    
    /**
     * Get all files that were moved during early cleanup
     * 
     * @return Map of original path to temporary path
     */
    public Map<String, String> getMovedFiles() {
        return new HashMap<>(movedFiles);
    }
    
    /**
     * Get list of mods that were removed during early cleanup
     * 
     * @return List of mod file paths (full path and name)
     */
    public List<String> getRemovedModNames() {
        return new ArrayList<>(removedModNames);
    }
    
    /**
     * Check if any files were moved during early cleanup
     * 
     * @return true if files were moved
     */
    public boolean hasMovedFiles() {
        return !movedFiles.isEmpty();
    }
    
    /**
     * Restore all moved files to their original locations.
     * Called if user cancels the confirmation dialog.
     * 
     * @return true if all files restored successfully
     */
    public boolean restoreMovedFiles() {
        boolean allSuccess = true;
        
        for (Map.Entry<String, String> entry : movedFiles.entrySet()) {
            File tempFile = new File(entry.getValue());
            File originalFile = new File(entry.getKey());
            
            if (tempFile.exists()) {
                // Restore the file
                if (!tempFile.renameTo(originalFile)) {
                    System.err.println("[EarlyCleanupState] Failed to restore: " + entry.getKey());
                    allSuccess = false;
                } else {
                    System.out.println("[EarlyCleanupState] Restored: " + entry.getKey());
                }
            }
        }
        
        if (allSuccess) {
            movedFiles.clear();
            removedModNames.clear();
        }
        
        return allSuccess;
    }
    
    /**
     * Permanently delete all moved files.
     * Called if user accepts the confirmation dialog.
     */
    public void deleteMovedFiles() {
        for (Map.Entry<String, String> entry : movedFiles.entrySet()) {
            File tempFile = new File(entry.getValue());
            
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    System.out.println("[EarlyCleanupState] Permanently deleted: " + tempFile.getName());
                } else {
                    System.err.println("[EarlyCleanupState] Failed to delete temp file: " + tempFile.getPath());
                }
            }
        }
        
        movedFiles.clear();
        removedModNames.clear();
    }
    
    /**
     * Clear all state (for testing or cleanup)
     */
    public void clear() {
        movedFiles.clear();
        removedModNames.clear();
    }
}
