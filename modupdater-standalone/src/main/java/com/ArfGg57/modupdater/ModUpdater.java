package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.core.ModUpdaterLifecycle;
import com.ArfGg57.modupdater.core.UpdaterCore;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "modupdater", name = "Mod Updater", version = "1.1")
public class ModUpdater {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            // Check if early phase already ran via coremod
            if (ModUpdaterLifecycle.wasEarlyPhaseCompleted()) {
                System.out.println("[ModUpdater] Early coremod phase already completed - skipping duplicate update run");
                System.out.println("[ModUpdater] Pending file operations were processed before mod loading");
                return;
            }
            
            // If no early phase, run update normally (backward compatibility for non-coremod mode)
            System.out.println("[ModUpdater] Early phase not detected - running update in preInit");
            UpdaterCore core = new UpdaterCore();
            core.runUpdateSynchronous();
        } catch (Throwable t) {
            System.err.println("FATAL ERROR during ModUpdater preInit:");
            t.printStackTrace();
        }
    }
}
