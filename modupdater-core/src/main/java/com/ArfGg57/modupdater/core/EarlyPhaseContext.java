package com.ArfGg57.modupdater.core;

/**
 * EarlyPhaseContext: Singleton to track whether we are executing in early coremod phase.
 * 
 * This context is set by the ModUpdaterCoremod when it initializes during FML coremod loading,
 * which happens before regular mods load and before Forge locks JAR files.
 * 
 * Other parts of the updater can query this to know if they should attempt immediate file
 * operations (early phase) or defer them (post-load phase).
 */
public class EarlyPhaseContext {
    
    private static EarlyPhaseContext instance;
    
    private final boolean isEarlyPhase;
    private final long startTime;
    
    private EarlyPhaseContext(boolean isEarlyPhase) {
        this.isEarlyPhase = isEarlyPhase;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Mark that we are in the early coremod phase.
     * This should be called by ModUpdaterCoremod constructor.
     */
    public static synchronized void markEarlyPhase() {
        if (instance == null) {
            instance = new EarlyPhaseContext(true);
        }
    }
    
    /**
     * Check if we are currently in the early coremod phase.
     * 
     * @return true if early phase was initialized, false otherwise
     */
    public static boolean isEarlyPhase() {
        return instance != null && instance.isEarlyPhase;
    }
    
    /**
     * Get the singleton instance.
     * 
     * @return the instance, or null if not initialized
     */
    public static EarlyPhaseContext getInstance() {
        return instance;
    }
    
    /**
     * Get the time when early phase was initialized.
     * 
     * @return timestamp in milliseconds
     */
    public long getStartTime() {
        return startTime;
    }
}
