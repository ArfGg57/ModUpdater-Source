package com.ArfGg57.modupdater.core;

import com.ArfGg57.modupdater.util.PendingOperations;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ModUpdaterLifecycle: Central utility for managing the updater lifecycle and operation scheduling.
 * 
 * Provides static methods to:
 * - Query whether we're in early coremod phase
 * - Schedule pending operations that can't be completed immediately
 * - Flush scheduled operations to persistent storage
 * 
 * This utility bridges the gap between early coremod execution and later update phases.
 */
public class ModUpdaterLifecycle {
    
    private static final String PENDING_OPS_PATH = "config/ModUpdater/pending-ops.json";
    private static final List<PendingOperations.PendingOp> newPendingOps = new ArrayList<>();
    private static PendingOperations pendingOpsInstance;
    private static boolean earlyPhaseCompleted = false;
    
    // Logger interface for pending operations
    private static final PendingOperations.Logger logger = new PendingOperations.Logger() {
        public void log(String message) {
            System.out.println("[ModUpdaterLifecycle] " + message);
        }
    };
    
    /**
     * Check if we are in the early coremod loading phase.
     * 
     * @return true if early phase is active
     */
    public static boolean isEarlyPhase() {
        return EarlyPhaseContext.isEarlyPhase();
    }
    
    /**
     * Mark that the early phase has been completed.
     * This should be called after ModUpdaterCoremod finishes processing.
     */
    public static void markEarlyPhaseCompleted() {
        earlyPhaseCompleted = true;
    }
    
    /**
     * Check if the early phase was completed (coremod ran).
     * 
     * @return true if early phase ran and completed
     */
    public static boolean wasEarlyPhaseCompleted() {
        return earlyPhaseCompleted;
    }
    
    /**
     * Schedule a file for deletion.
     * This will be written to pending-ops.json and processed on next startup.
     * 
     * @param file The file to delete
     * @param reason Human-readable reason for debugging
     */
    public static void schedulePendingDelete(Path file, String reason) {
        if (file == null) return;
        
        logger.log("Scheduling pending delete: " + file + " (reason: " + reason + ")");
        PendingOperations.PendingOp op = new PendingOperations.PendingOp(
            PendingOperations.OpType.DELETE,
            file.toAbsolutePath().toString(),
            null
        );
        newPendingOps.add(op);
    }
    
    /**
     * Schedule a file to be moved/renamed.
     * This will be written to pending-ops.json and processed on next startup.
     * 
     * @param from The source file path
     * @param to The target file path
     * @param reason Human-readable reason for debugging
     */
    public static void schedulePendingMove(Path from, Path to, String reason) {
        if (from == null || to == null) return;
        
        logger.log("Scheduling pending move: " + from + " -> " + to + " (reason: " + reason + ")");
        PendingOperations.PendingOp op = new PendingOperations.PendingOp(
            PendingOperations.OpType.MOVE,
            from.toAbsolutePath().toString(),
            to.toAbsolutePath().toString()
        );
        newPendingOps.add(op);
    }
    
    /**
     * Schedule a file replacement operation.
     * 
     * @param oldFile The old file to be replaced
     * @param newFile The target location for the new file
     * @param stagedFile The staged new file (already downloaded)
     * @param checksum The expected checksum of the staged file
     * @param reason Human-readable reason for debugging
     */
    public static void schedulePendingReplace(Path oldFile, Path newFile, Path stagedFile, String checksum, String reason) {
        if (oldFile == null || newFile == null || stagedFile == null) return;
        
        logger.log("Scheduling pending replace: " + oldFile + " -> " + newFile + " (reason: " + reason + ")");
        PendingOperations.PendingOp op = new PendingOperations.PendingOp(
            PendingOperations.OpType.REPLACE,
            oldFile.toAbsolutePath().toString(),
            newFile.toAbsolutePath().toString(),
            stagedFile.toAbsolutePath().toString(),
            checksum
        );
        newPendingOps.add(op);
    }
    
    /**
     * Flush all newly scheduled operations to pending-ops.json.
     * This should be called at the end of an update cycle or via shutdown hook.
     */
    public static synchronized void flushNewPendingOperations() {
        if (newPendingOps.isEmpty()) {
            return;
        }
        
        logger.log("Flushing " + newPendingOps.size() + " new pending operation(s) to disk");
        
        // Get or create pending operations instance
        if (pendingOpsInstance == null) {
            pendingOpsInstance = new PendingOperations(PENDING_OPS_PATH, logger);
        }
        
        // Add new operations to existing ones
        for (PendingOperations.PendingOp op : newPendingOps) {
            pendingOpsInstance.addOperation(op);
        }
        
        newPendingOps.clear();
    }
    
    /**
     * Install a JVM shutdown hook to ensure pending operations are persisted.
     */
    public static void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                flushNewPendingOperations();
            }
        }, "ModUpdaterLifecycle-ShutdownHook"));
    }
}
