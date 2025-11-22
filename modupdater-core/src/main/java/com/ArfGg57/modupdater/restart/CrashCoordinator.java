package com.ArfGg57.modupdater.restart;

/**
 * Coordinates crash enforcement across multiple mod instances to prevent duplicate crashes.
 * Uses static flags to ensure only one mod instance executes the crash, even when
 * multiple mod classes are loaded (e.g., both modupdater-tweaker and modupdater).
 */
public class CrashCoordinator {
    
    // Static flags shared across all mod instances
    private static volatile boolean crashExecuted = false;
    private static final Object lock = new Object();
    
    /**
     * Check if a crash has already been executed by any mod instance.
     * @return true if crash already executed, false otherwise
     */
    public static boolean isCrashExecuted() {
        return crashExecuted;
    }
    
    /**
     * Attempt to mark crash as executed. Thread-safe.
     * @return true if this caller successfully claimed the crash (was first), false if another already claimed it
     */
    public static boolean tryClaim() {
        synchronized (lock) {
            if (crashExecuted) {
                return false; // Someone else already claimed it
            }
            crashExecuted = true;
            return true; // We successfully claimed it
        }
    }
    
    /**
     * Reset the crash state (for testing purposes only).
     * Should not be used in production code.
     */
    public static void reset() {
        synchronized (lock) {
            crashExecuted = false;
        }
    }
}
