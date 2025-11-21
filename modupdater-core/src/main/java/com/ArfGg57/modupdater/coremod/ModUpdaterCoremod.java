package com.ArfGg57.modupdater.coremod;

import com.ArfGg57.modupdater.core.EarlyPhaseContext;
import com.ArfGg57.modupdater.core.ModUpdaterLifecycle;
import com.ArfGg57.modupdater.util.PendingOperations;

import java.util.Map;

/**
 * ModUpdaterCoremod: FML Loading Plugin for early-phase file operations.
 * 
 * This coremod loads before regular mods, allowing us to:
 * - Process pending file operations from previous runs
 * - Move/delete files that would otherwise be locked
 * - Ensure clean mod state before FML scans the mods directory
 * 
 * Compatible with Forge 1.7.10 / Java 8
 * 
 * IMPORTANT: This class implements IFMLLoadingPlugin at runtime but does not
 * import it at compile time to avoid build dependencies. When used with Forge,
 * the annotations and interface will be resolved dynamically.
 * 
 * To activate, add this to META-INF/MANIFEST.MF in the JAR:
 *   FMLCorePlugin: com.ArfGg57.modupdater.coremod.ModUpdaterCoremod
 *   FMLCorePluginContainsFMLMod: true
 *   ForceLoadAsMod: true
 * 
 * Annotations (resolved at runtime by Forge):
 * @IFMLLoadingPlugin.Name("ModUpdaterCoremod")
 * @IFMLLoadingPlugin.MCVersion("1.7.10")
 * @IFMLLoadingPlugin.SortingIndex(1)
 * 
 * SortingIndex of 1 loads early but not earliest (0 is reserved for critical bootstrap plugins).
 * Adjust if conflicts arise with other coremods.
 */
public class ModUpdaterCoremod {
    
    private static final String PENDING_OPS_PATH = "config/ModUpdater/pending-ops.json";
    
    /**
     * Constructor - runs during coremod initialization (before mods are scanned/loaded)
     */
    public ModUpdaterCoremod() {
        System.out.println("[ModUpdaterCoremod] Initializing early-load phase...");
        
        // Mark that we are in early phase
        EarlyPhaseContext.markEarlyPhase();
        
        try {
            // Process any pending operations from previous run
            PendingOperations pendingOps = new PendingOperations(PENDING_OPS_PATH, new PendingOperations.Logger() {
                public void log(String message) {
                    System.out.println("[ModUpdaterCoremod] " + message);
                }
            });
            
            int processed = pendingOps.processPendingOperations();
            if (processed > 0) {
                System.out.println("[ModUpdaterCoremod] Successfully processed " + processed + " pending operation(s)");
            } else {
                System.out.println("[ModUpdaterCoremod] No pending operations to process");
            }
        } catch (Exception e) {
            System.err.println("[ModUpdaterCoremod] Error processing pending operations: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Install shutdown hook to persist any new pending operations
        ModUpdaterLifecycle.installShutdownHook();
        
        // Mark early phase as completed
        ModUpdaterLifecycle.markEarlyPhaseCompleted();
        
        System.out.println("[ModUpdaterCoremod] Early-load phase complete");
    }
    
    /**
     * IFMLLoadingPlugin.getASMTransformerClass() implementation
     * Returns empty array - no ASM transformations needed
     */
    public String[] getASMTransformerClass() {
        return new String[0];
    }
    
    /**
     * IFMLLoadingPlugin.getModContainerClass() implementation
     * Returns null - no custom mod container
     */
    public String getModContainerClass() {
        return null;
    }
    
    /**
     * IFMLLoadingPlugin.getSetupClass() implementation
     * Returns null - no setup class needed
     */
    public String getSetupClass() {
        return null;
    }
    
    /**
     * IFMLLoadingPlugin.injectData() implementation
     * No data injection needed
     */
    public void injectData(Map<String, Object> data) {
        // No data injection needed
    }
    
    /**
     * IFMLLoadingPlugin.getAccessTransformerClass() implementation
     * Returns null - no access transformer needed
     */
    public String getAccessTransformerClass() {
        return null;
    }
}
