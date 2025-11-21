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
        
        // Run early cleanup to remove outdated mods BEFORE they get loaded
        try {
            // Import UpdaterCore via reflection to avoid compile-time dependency
            Class<?> updaterCoreClass = Class.forName("com.ArfGg57.modupdater.core.UpdaterCore");
            Class<?> loggerInterface = Class.forName("com.ArfGg57.modupdater.core.UpdaterCore$SimpleLogger");
            
            // Create logger implementation
            Object logger = java.lang.reflect.Proxy.newProxyInstance(
                ModUpdaterCoremod.class.getClassLoader(),
                new Class<?>[] { loggerInterface },
                new java.lang.reflect.InvocationHandler() {
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                        if (method.getName().equals("log") && args != null && args.length > 0) {
                            System.out.println("[ModUpdaterCoremod] " + args[0]);
                        }
                        return null;
                    }
                }
            );
            
            // Invoke runEarlyCleanup
            java.lang.reflect.Method runEarlyCleanup = updaterCoreClass.getMethod("runEarlyCleanup", loggerInterface);
            Boolean success = (Boolean) runEarlyCleanup.invoke(null, logger);
            
            if (success != null && success) {
                System.out.println("[ModUpdaterCoremod] Early cleanup completed successfully");
            } else {
                System.out.println("[ModUpdaterCoremod] Early cleanup completed with warnings");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[ModUpdaterCoremod] UpdaterCore not available, skipping early cleanup");
        } catch (Exception e) {
            System.err.println("[ModUpdaterCoremod] Error during early cleanup: " + e.getMessage());
            e.printStackTrace();
            // Don't fail the coremod initialization - continue with launch
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
