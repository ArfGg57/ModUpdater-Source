package com.ArfGg57.modupdater.deletion;

import com.ArfGg57.modupdater.metadata.ModMetadata;
import com.ArfGg57.modupdater.util.FileUtils;
import com.ArfGg57.modupdater.util.PendingOperations;
import com.ArfGg57.modupdater.version.VersionComparator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DeletionProcessor: Handles processing of deletion operations.
 * 
 * - Applies version range logic
 * - Implements safetyMode filtering
 * - Handles file vs folder deletions
 * - Provides robust error handling and logging
 * - Supports legacy format migration
 * 
 * Compatible with Java 8 / Forge 1.7.10
 */
public class DeletionProcessor {
    
    public interface Logger {
        void log(String message);
    }
    
    private final Logger logger;
    private final ModMetadata metadata;
    private final PendingOperations pendingOps;
    private final File backupRoot;
    
    public DeletionProcessor(Logger logger, ModMetadata metadata, PendingOperations pendingOps, File backupRoot) {
        this.logger = logger;
        this.metadata = metadata;
        this.pendingOps = pendingOps;
        this.backupRoot = backupRoot;
    }
    
    /**
     * Process deletions from deletes.json configuration.
     * 
     * @param deletesRoot Root JSONObject from deletes.json
     * @param currentVersion User's current version (exclusive lower bound)
     * @param targetVersion Target version being updated to (inclusive upper bound)
     * @return Number of deletions performed
     */
    public int processDeletions(JSONObject deletesRoot, String currentVersion, String targetVersion) {
        if (deletesRoot == null) {
            logger.log("No deletions configuration found (deletes.json missing or invalid)");
            return 0;
        }
        
        // Check if this is legacy format (has "deletes" array with "since" field)
        if (isLegacyFormat(deletesRoot)) {
            logger.log("WARNING: Legacy deletes.json format detected (using 'since' field)");
            logger.log("Please migrate to new format. See CONFIG.md for details.");
            logger.log("Skipping deletions due to legacy format.");
            return 0;
        }
        
        // Parse new format
        DeletionConfig config = DeletionConfig.fromJson(deletesRoot);
        logger.log("Deletion config loaded: safetyMode=" + config.isSafetyMode() + 
                   ", entries=" + config.getDeletions().size());
        
        int deletionCount = 0;
        
        for (DeletionConfig.DeletionEntry entry : config.getDeletions()) {
            String deleteVersion = entry.getVersion();
            
            // Check if this deletion applies based on version range
            if (!shouldApplyDeletion(deleteVersion, currentVersion, targetVersion)) {
                logger.log("Skipping deletion entry for version " + deleteVersion + 
                          " (outside range " + currentVersion + " to " + targetVersion + ")");
                continue;
            }
            
            logger.log("Applying deletion entry for version " + deleteVersion);
            
            for (DeletionConfig.PathEntry pathEntry : entry.getPaths()) {
                boolean deleted = processPathEntry(pathEntry, config.isSafetyMode());
                if (deleted) {
                    deletionCount++;
                }
            }
        }
        
        // Save metadata after deletions
        logger.log("Saving metadata after deletions...");
        metadata.save();
        
        return deletionCount;
    }
    
    /**
     * Check if a deletion should apply based on version range.
     * 
     * Logic: D > C AND D <= T
     * where D = deleteVersion, C = currentVersion, T = targetVersion
     */
    private boolean shouldApplyDeletion(String deleteVersion, String currentVersion, String targetVersion) {
        // Parse versions
        VersionComparator.Version dVer = VersionComparator.parse(deleteVersion);
        VersionComparator.Version cVer = VersionComparator.parse(currentVersion);
        VersionComparator.Version tVer = VersionComparator.parse(targetVersion);
        
        if (dVer == null) {
            logger.log("WARNING: Malformed delete version '" + deleteVersion + "'; skipping entry");
            return false;
        }
        
        if (cVer == null) {
            logger.log("WARNING: Malformed current version '" + currentVersion + "'; using 0.0.0");
            cVer = new VersionComparator.Version(0, 0, 0);
        }
        
        if (tVer == null) {
            logger.log("WARNING: Malformed target version '" + targetVersion + "'; skipping entry");
            return false;
        }
        
        // Check: D > C AND D <= T
        boolean result = (dVer.compareTo(cVer) > 0) && (dVer.compareTo(tVer) <= 0);
        
        if (!result) {
            logger.log("Version " + deleteVersion + " not in range (" + 
                      currentVersion + " < version <= " + targetVersion + ")");
        }
        
        return result;
    }
    
    /**
     * Process a single path entry (file or folder deletion).
     */
    private boolean processPathEntry(DeletionConfig.PathEntry pathEntry, boolean safetyMode) {
        String path = pathEntry.getPath();
        DeletionConfig.PathType type = pathEntry.getType();
        
        // Apply safety mode filter
        if (safetyMode && !isInSafeDirectory(path)) {
            logger.log("WARNING: Safety mode enabled - skipping deletion outside config/ directory: " + path);
            return false;
        }
        
        File file = new File(path);
        
        if (!file.exists()) {
            logger.log("Delete skip (not present): " + path);
            return false;
        }
        
        // Verify type matches
        if (type == DeletionConfig.PathType.FILE && !file.isFile()) {
            logger.log("WARNING: Path marked as FILE but is not a file: " + path + "; skipping");
            return false;
        }
        
        if (type == DeletionConfig.PathType.FOLDER && !file.isDirectory()) {
            logger.log("WARNING: Path marked as FOLDER but is not a directory: " + path + "; skipping");
            return false;
        }
        
        // Backup before deletion
        logger.log("Backing up then deleting " + type + ": " + path);
        try {
            backupPath(file);
        } catch (Exception e) {
            logger.log("WARNING: Failed to backup " + path + ": " + e.getMessage());
            // Continue with deletion anyway
        }
        
        // Attempt deletion
        boolean deleted = false;
        if (type == DeletionConfig.PathType.FOLDER) {
            // For folders, we need to recursively delete
            // Try using FileUtils.deleteSilently which handles recursion
            deleted = deleteRecursivelyWithFallback(file);
        } else {
            // For files, use pendingOps.deleteWithFallback
            deleted = pendingOps.deleteWithFallback(file);
        }
        
        if (deleted) {
            logger.log("Successfully deleted: " + path);
            return true;
        } else {
            logger.log("Delete scheduled for next startup (file/folder locked): " + path);
            return false;
        }
    }
    
    /**
     * Delete a directory recursively, with fallback to pending operations if locked.
     */
    private boolean deleteRecursivelyWithFallback(File dir) {
        if (!dir.exists()) {
            return true;
        }
        
        if (!dir.isDirectory()) {
            // Not a directory, use standard delete
            return pendingOps.deleteWithFallback(dir);
        }
        
        // Recursively delete contents
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteRecursivelyWithFallback(child);
                } else {
                    pendingOps.deleteWithFallback(child);
                }
            }
        }
        
        // Try to delete the directory itself
        if (dir.delete()) {
            return true;
        } else {
            // Schedule for deletion on exit
            dir.deleteOnExit();
            PendingOperations.PendingOp op = new PendingOperations.PendingOp(
                PendingOperations.OpType.DELETE, dir.getAbsolutePath(), null);
            // Note: We can't directly add to pendingOps from here, so we'll just use deleteOnExit
            return false;
        }
    }
    
    /**
     * Check if a path is within the safe directory (config/).
     */
    private boolean isInSafeDirectory(String path) {
        // Normalize path separators
        String normalized = path.replace('\\', '/');
        
        // Check if path starts with "config/"
        return normalized.startsWith("config/");
    }
    
    /**
     * Backup a file or directory to the backup root.
     */
    private void backupPath(File path) throws Exception {
        if (backupRoot == null) {
            return; // No backup directory configured
        }
        
        FileUtils.backupPathTo(path, backupRoot);
    }
    
    /**
     * Check if deletes.json uses legacy format.
     * 
     * Legacy format has "deletes" array with objects containing "since" field.
     */
    private boolean isLegacyFormat(JSONObject deletesRoot) {
        // Check for "deletes" array
        JSONArray deletes = deletesRoot.optJSONArray("deletes");
        if (deletes == null || deletes.length() == 0) {
            return false;
        }
        
        // Check if first entry has "since" field
        JSONObject firstEntry = deletes.optJSONObject(0);
        if (firstEntry != null && firstEntry.has("since")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Build a list of deletion descriptions for UI display.
     * 
     * @param deletesRoot Root JSONObject from deletes.json
     * @param currentVersion User's current version
     * @param targetVersion Target version being updated to
     * @return List of human-readable deletion descriptions
     */
    public List<String> buildDeletionsList(JSONObject deletesRoot, String currentVersion, String targetVersion) {
        List<String> deletions = new ArrayList<>();
        
        if (deletesRoot == null) {
            return deletions;
        }
        
        // Check for legacy format
        if (isLegacyFormat(deletesRoot)) {
            deletions.add("Legacy format detected - deletions disabled");
            return deletions;
        }
        
        DeletionConfig config = DeletionConfig.fromJson(deletesRoot);
        
        for (DeletionConfig.DeletionEntry entry : config.getDeletions()) {
            String deleteVersion = entry.getVersion();
            
            if (!shouldApplyDeletion(deleteVersion, currentVersion, targetVersion)) {
                continue;
            }
            
            for (DeletionConfig.PathEntry pathEntry : entry.getPaths()) {
                String path = pathEntry.getPath();
                
                // Apply safety mode filter
                if (config.isSafetyMode() && !isInSafeDirectory(path)) {
                    continue;
                }
                
                String typeStr = pathEntry.getType() == DeletionConfig.PathType.FILE ? "FILE" : "FOLDER";
                deletions.add(typeStr + ": " + path);
            }
        }
        
        return deletions;
    }
}
