package com.ArfGg57.modupdater.util;

import com.ArfGg57.modupdater.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PendingOperations: Handles deferred file operations for locked files.
 * 
 * When a file cannot be deleted or moved due to being locked (e.g., loaded by Forge),
 * this class schedules the operation for the next startup using deleteOnExit and
 * tracks it in pending-ops.json for robustness.
 * 
 * Features:
 * - Cross-platform file lock detection
 * - Graceful fallback using deleteOnExit
 * - Persistent tracking via pending-ops.json
 * - Startup processing before main update logic
 */
public class PendingOperations {
    
    /**
     * Logger callback interface
     */
    public interface Logger {
        void log(String message);
    }
    
    private final String pendingOpsPath;
    private final Logger logger;
    private final List<PendingOp> operations;
    
    /**
     * Operation types supported
     */
    public enum OpType {
        DELETE,
        MOVE
    }
    
    /**
     * Represents a single pending operation
     */
    public static class PendingOp {
        public OpType type;
        public String sourcePath;
        public String targetPath; // only for MOVE operations
        public long timestamp;
        
        public PendingOp() {}
        
        public PendingOp(OpType type, String sourcePath, String targetPath) {
            this.type = type;
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.timestamp = System.currentTimeMillis();
        }
        
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("type", type.toString());
            obj.put("sourcePath", sourcePath);
            if (targetPath != null) obj.put("targetPath", targetPath);
            obj.put("timestamp", timestamp);
            return obj;
        }
        
        public static PendingOp fromJson(JSONObject obj) {
            PendingOp op = new PendingOp();
            op.type = OpType.valueOf(obj.optString("type", "DELETE"));
            op.sourcePath = obj.optString("sourcePath", "");
            op.targetPath = obj.optString("targetPath", null);
            op.timestamp = obj.optLong("timestamp", 0);
            return op;
        }
    }
    
    /**
     * Create a new PendingOperations instance.
     * 
     * @param pendingOpsPath Path to the pending-ops.json file
     * @param logger Logger for status messages
     */
    public PendingOperations(String pendingOpsPath, Logger logger) {
        this.pendingOpsPath = pendingOpsPath;
        this.logger = logger;
        this.operations = new ArrayList<>();
        load();
    }
    
    /**
     * Load pending operations from disk
     */
    private void load() {
        try {
            File file = new File(pendingOpsPath);
            if (!file.exists()) {
                return;
            }
            
            JSONObject root = FileUtils.readJson(pendingOpsPath);
            JSONArray opsArray = root.optJSONArray("operations");
            if (opsArray == null) {
                return;
            }
            
            operations.clear();
            for (int i = 0; i < opsArray.length(); i++) {
                try {
                    PendingOp op = PendingOp.fromJson(opsArray.getJSONObject(i));
                    operations.add(op);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.log("Warning: Failed to parse pending operation: " + e.getMessage());
                    }
                }
            }
            
            if (logger != null && !operations.isEmpty()) {
                logger.log("Loaded " + operations.size() + " pending operation(s) from previous run");
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.log("Warning: Could not load pending operations: " + e.getMessage());
            }
            operations.clear();
        }
    }
    
    /**
     * Save pending operations to disk
     */
    private void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray opsArray = new JSONArray();
            
            for (PendingOp op : operations) {
                opsArray.put(op.toJson());
            }
            
            root.put("operations", opsArray);
            
            File file = new File(pendingOpsPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.log("Warning: Could not save pending operations: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if a file is currently locked (in use by another process).
     * This is a heuristic check that works across platforms.
     * 
     * @param file The file to check
     * @return true if the file appears to be locked
     */
    public static boolean isFileLocked(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        
        // Try to rename the file to itself as a quick lock check
        // This doesn't actually rename but tests if we have exclusive access
        if (!file.canWrite()) {
            return true;
        }
        
        // Try to delete and immediately recreate (test write lock)
        // Actually, safer approach: try to open for exclusive write
        try {
            // Try to open the file in append mode (should fail if locked)
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw");
            java.nio.channels.FileLock lock = raf.getChannel().tryLock();
            if (lock != null) {
                lock.release();
                raf.close();
                return false;
            } else {
                raf.close();
                return true;
            }
        } catch (Exception e) {
            // If we can't acquire lock, file is likely locked
            return true;
        }
    }
    
    /**
     * Attempt to delete a file, with fallback to scheduling for next startup.
     * 
     * @param file The file to delete
     * @return true if deleted immediately, false if scheduled for later
     */
    public boolean deleteWithFallback(File file) {
        if (file == null || !file.exists()) {
            return true; // Nothing to do
        }
        
        // Try immediate deletion
        if (file.delete()) {
            if (logger != null) {
                logger.log("Deleted file: " + file.getPath());
            }
            return true;
        }
        
        // Deletion failed - check if locked
        if (isFileLocked(file)) {
            if (logger != null) {
                logger.log("File is locked, scheduling for deletion on next startup: " + file.getPath());
            }
            
            // Schedule for JVM exit
            file.deleteOnExit();
            
            // Record in pending operations
            PendingOp op = new PendingOp(OpType.DELETE, file.getAbsolutePath(), null);
            operations.add(op);
            save();
            
            return false;
        } else {
            // Not locked but still failed - log warning
            if (logger != null) {
                logger.log("Warning: Failed to delete file (not locked): " + file.getPath());
            }
            return false;
        }
    }
    
    /**
     * Process all pending operations from previous runs.
     * This should be called early at startup before the main update logic.
     * 
     * @return The number of operations successfully completed
     */
    public int processPendingOperations() {
        if (operations.isEmpty()) {
            return 0;
        }
        
        if (logger != null) {
            logger.log("Processing " + operations.size() + " pending operation(s) from previous run...");
        }
        
        int completed = 0;
        List<PendingOp> remaining = new ArrayList<>();
        
        for (PendingOp op : operations) {
            try {
                boolean success = false;
                
                if (op.type == OpType.DELETE) {
                    File file = new File(op.sourcePath);
                    if (!file.exists()) {
                        // Already deleted (possibly by deleteOnExit)
                        if (logger != null) {
                            logger.log("Pending delete completed (file gone): " + op.sourcePath);
                        }
                        success = true;
                    } else if (file.delete()) {
                        if (logger != null) {
                            logger.log("Completed pending delete: " + op.sourcePath);
                        }
                        success = true;
                    } else {
                        if (logger != null) {
                            logger.log("Warning: Still cannot delete file: " + op.sourcePath);
                        }
                    }
                } else if (op.type == OpType.MOVE) {
                    File src = new File(op.sourcePath);
                    File dst = new File(op.targetPath);
                    
                    if (!src.exists()) {
                        // Source gone - consider it moved or deleted
                        success = true;
                    } else {
                        try {
                            FileUtils.atomicMoveWithRetries(src, dst, 3, 100);
                            if (logger != null) {
                                logger.log("Completed pending move: " + op.sourcePath + " -> " + op.targetPath);
                            }
                            success = true;
                        } catch (Exception e) {
                            if (logger != null) {
                                logger.log("Warning: Still cannot move file: " + op.sourcePath + ": " + e.getMessage());
                            }
                        }
                    }
                }
                
                if (success) {
                    completed++;
                } else {
                    // Keep for next run
                    remaining.add(op);
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.log("Error processing pending operation: " + e.getMessage());
                }
                remaining.add(op);
            }
        }
        
        operations.clear();
        operations.addAll(remaining);
        save();
        
        if (logger != null) {
            logger.log("Completed " + completed + " pending operation(s), " + remaining.size() + " still pending");
        }
        
        return completed;
    }
    
    /**
     * Clear all pending operations
     */
    public void clear() {
        operations.clear();
        save();
    }
    
    /**
     * Get the number of pending operations
     */
    public int size() {
        return operations.size();
    }
}
