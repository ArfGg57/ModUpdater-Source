package com.ArfGg57.modupdater.util;

import com.ArfGg57.modupdater.util.FileUtils;
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
        MOVE,
        REPLACE  // Replace old file with staged new file
    }
    
    /**
     * Represents a single pending operation
     */
    public static class PendingOp {
        public OpType type;
        public String sourcePath;
        public String targetPath; // for MOVE and REPLACE operations
        public String stagedPath;  // for REPLACE: path to the new file to install
        public String checksum;    // for REPLACE: expected checksum of staged file
        public long timestamp;
        public String reason;      // Human-readable reason for debugging
        public long executedTimestamp; // When operation was executed (0 if not yet)
        
        public PendingOp() {}
        
        public PendingOp(OpType type, String sourcePath, String targetPath) {
            this.type = type;
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.timestamp = System.currentTimeMillis();
            this.executedTimestamp = 0;
        }
        
        public PendingOp(OpType type, String sourcePath, String targetPath, String stagedPath, String checksum) {
            this.type = type;
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.stagedPath = stagedPath;
            this.checksum = checksum;
            this.timestamp = System.currentTimeMillis();
            this.executedTimestamp = 0;
        }
        
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("type", type.toString());
            obj.put("sourcePath", sourcePath);
            if (targetPath != null) obj.put("targetPath", targetPath);
            if (stagedPath != null) obj.put("stagedPath", stagedPath);
            if (checksum != null) obj.put("checksum", checksum);
            obj.put("timestamp", timestamp);
            if (reason != null) obj.put("reason", reason);
            if (executedTimestamp > 0) obj.put("executedTimestamp", executedTimestamp);
            return obj;
        }
        
        public static PendingOp fromJson(JSONObject obj) {
            PendingOp op = new PendingOp();
            op.type = OpType.valueOf(obj.optString("type", "DELETE"));
            op.sourcePath = obj.optString("sourcePath", "");
            op.targetPath = obj.optString("targetPath", null);
            op.stagedPath = obj.optString("stagedPath", null);
            op.checksum = obj.optString("checksum", null);
            op.timestamp = obj.optLong("timestamp", 0);
            op.reason = obj.optString("reason", null);
            op.executedTimestamp = obj.optLong("executedTimestamp", 0);
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
     * During early coremod phase, tries harder with more retries since files shouldn't be locked yet.
     * 
     * @param file The file to delete
     * @return true if deleted immediately, false if scheduled for later
     */
    public boolean deleteWithFallback(File file) {
        if (file == null || !file.exists()) {
            return true; // Nothing to do
        }
        
        // During early phase, try harder with retries since files shouldn't be locked
        boolean isEarlyPhase = false;
        try {
            // Check if we're in early phase (avoid hard dependency on core package)
            Class<?> lifecycleClass = Class.forName("com.ArfGg57.modupdater.core.ModUpdaterLifecycle");
            java.lang.reflect.Method wasEarlyMethod = lifecycleClass.getMethod("wasEarlyPhaseCompleted");
            Boolean wasEarly = (Boolean) wasEarlyMethod.invoke(null);
            isEarlyPhase = wasEarly != null && wasEarly;
        } catch (Exception e) {
            // Early phase check not available, proceed normally
        }
        
        int maxAttempts = isEarlyPhase ? 5 : 1; // More attempts during early phase
        long sleepMs = 200;
        
        // Try immediate deletion with retries
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (file.delete()) {
                if (logger != null) {
                    logger.log("Deleted file" + (attempt > 1 ? " (attempt " + attempt + ")" : "") + ": " + file.getPath());
                }
                return true;
            }
            
            // Not last attempt - sleep and retry
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // Deletion failed after retries - check if locked
        if (isFileLocked(file)) {
            if (logger != null) {
                logger.log("File is locked after " + maxAttempts + " attempt(s), scheduling for deletion on next startup: " + file.getPath());
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
                logger.log("Warning: Failed to delete file after " + maxAttempts + " attempt(s) (not locked): " + file.getPath());
            }
            return false;
        }
    }
    
    /**
     * Attempt to move/rename a file, with fallback to scheduling for next startup.
     * During early coremod phase, tries harder with more retries since files shouldn't be locked yet.
     * 
     * @param source The source file to move
     * @param target The target location
     * @return true if moved immediately, false if scheduled for later
     */
    public boolean moveWithFallback(File source, File target) {
        if (source == null || !source.exists()) {
            return true; // Nothing to do
        }
        
        // During early phase, try harder with retries since files shouldn't be locked
        boolean isEarlyPhase = false;
        try {
            Class<?> lifecycleClass = Class.forName("com.ArfGg57.modupdater.core.ModUpdaterLifecycle");
            java.lang.reflect.Method wasEarlyMethod = lifecycleClass.getMethod("wasEarlyPhaseCompleted");
            Boolean wasEarly = (Boolean) wasEarlyMethod.invoke(null);
            isEarlyPhase = wasEarly != null && wasEarly;
        } catch (Exception e) {
            // Early phase check not available, proceed normally
        }
        
        int maxAttempts = isEarlyPhase ? 5 : 3; // More attempts during early phase
        
        // Try immediate move with retries
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                FileUtils.atomicMoveWithRetries(source, target, 1, 100);
                if (logger != null) {
                    logger.log("Moved file" + (attempt > 1 ? " (attempt " + attempt + ")" : "") + ": " + source.getPath() + " -> " + target.getPath());
                }
                return true;
            } catch (Exception e) {
                // Move failed on this attempt
                if (attempt == maxAttempts) {
                    // Last attempt failed
                    if (isFileLocked(source)) {
                        if (logger != null) {
                            logger.log("File is locked after " + maxAttempts + " attempt(s), scheduling move for next startup: " + source.getPath() + " -> " + target.getPath());
                        }
                        
                        // Record in pending operations
                        PendingOp op = new PendingOp(OpType.MOVE, source.getAbsolutePath(), target.getAbsolutePath());
                        operations.add(op);
                        save();
                        
                        return false;
                    } else {
                        // Not locked but still failed - log warning
                        if (logger != null) {
                            logger.log("Warning: Failed to move file after " + maxAttempts + " attempt(s) (not locked): " + source.getPath() + ": " + e.getMessage());
                        }
                        return false;
                    }
                } else {
                    // Not last attempt - sleep and retry
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Schedule a file replacement operation for next startup.
     * This downloads the new file to a staging area, then schedules the replacement.
     * 
     * @param oldFile The old file to replace
     * @param newFile The target location for the new file
     * @param stagedFile The staged new file (already downloaded)
     * @param checksum The expected checksum of the staged file
     */
    public void scheduleReplace(File oldFile, File newFile, File stagedFile, String checksum) {
        if (logger != null) {
            logger.log("Scheduling file replacement for next startup: " + oldFile.getPath() + " -> " + newFile.getPath());
        }
        
        // Record in pending operations
        PendingOp op = new PendingOp(
            OpType.REPLACE, 
            oldFile.getAbsolutePath(), 
            newFile.getAbsolutePath(),
            stagedFile.getAbsolutePath(),
            checksum
        );
        operations.add(op);
        save();
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
                        // Already deleted (possibly by deleteOnExit) - idempotent success
                        if (logger != null) {
                            String reasonMsg = op.reason != null ? " (reason: " + op.reason + ")" : "";
                            logger.log("Pending delete completed (file gone): " + op.sourcePath + reasonMsg);
                        }
                        success = true;
                    } else if (file.delete()) {
                        if (logger != null) {
                            String reasonMsg = op.reason != null ? " (reason: " + op.reason + ")" : "";
                            logger.log("Completed pending delete: " + op.sourcePath + reasonMsg);
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
                    
                    // Idempotency checks
                    if (!src.exists() && !dst.exists()) {
                        // Both gone - something else handled it, consider success
                        success = true;
                    } else if (!src.exists() && dst.exists()) {
                        // Source gone, target exists - likely already moved
                        if (logger != null) {
                            logger.log("Pending move appears completed (target exists): " + op.targetPath);
                        }
                        success = true;
                    } else if (src.exists() && dst.exists()) {
                        // Both exist - target already there, delete source
                        if (src.delete()) {
                            success = true;
                        }
                    } else {
                        // src exists, dst doesn't - normal move
                        try {
                            FileUtils.atomicMoveWithRetries(src, dst, 3, 100);
                            if (logger != null) {
                                String reasonMsg = op.reason != null ? " (reason: " + op.reason + ")" : "";
                                logger.log("Completed pending move: " + op.sourcePath + " -> " + op.targetPath + reasonMsg);
                            }
                            success = true;
                        } catch (Exception e) {
                            if (logger != null) {
                                logger.log("Warning: Still cannot move file: " + op.sourcePath + ": " + e.getMessage());
                            }
                        }
                    }
                } else if (op.type == OpType.REPLACE) {
                    File old = new File(op.sourcePath);
                    File staged = new File(op.stagedPath);
                    File target = new File(op.targetPath);
                    
                    // Idempotency check: if target already exists with correct content and old/staged are gone
                    if (target.exists() && !old.exists() && !staged.exists()) {
                        if (logger != null) {
                            logger.log("Pending replace appears completed (target exists, old/staged gone): " + op.targetPath);
                        }
                        success = true;
                    } else if (!staged.exists()) {
                        // Staged file missing - can't complete operation
                        if (logger != null) {
                            logger.log("Warning: Staged file missing for REPLACE: " + op.stagedPath);
                        }
                        // Keep operation for potential retry
                        remaining.add(op);
                        continue;
                    } else {
                        // Normal replace flow
                        // Delete old file if it exists
                        if (old.exists()) {
                            if (!old.delete()) {
                                if (logger != null) {
                                    logger.log("Warning: Still cannot delete old file: " + op.sourcePath);
                                }
                                // Can't complete - keep for next run
                                remaining.add(op);
                                continue;
                            }
                        }
                        
                        // Move staged file to target
                        try {
                            FileUtils.atomicMoveWithRetries(staged, target, 3, 100);
                            if (logger != null) {
                                String reasonMsg = op.reason != null ? " (reason: " + op.reason + ")" : "";
                                logger.log("Completed pending replace: " + op.sourcePath + " -> " + op.targetPath + reasonMsg);
                            }
                            success = true;
                        } catch (Exception e) {
                            if (logger != null) {
                                logger.log("Warning: Cannot move staged file to target: " + e.getMessage());
                            }
                        }
                    }
                }
                
                if (success) {
                    // Mark operation as executed
                    op.executedTimestamp = System.currentTimeMillis();
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
    
    /**
     * Add a new pending operation and save to disk.
     * 
     * @param op The operation to add
     */
    public void addOperation(PendingOp op) {
        if (op != null) {
            operations.add(op);
            save();
        }
    }
}
