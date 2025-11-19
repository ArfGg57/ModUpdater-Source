package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "modupdater", name = "Mod Updater", version = "1.1")
public class ModUpdater {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            // Run update synchronously in preInit; GUI will appear (after Minecraft window)
            UpdaterCore core = new UpdaterCore();
            core.runUpdateSynchronous();
        } catch (Throwable t) {
            System.err.println("FATAL ERROR during ModUpdater preInit:");
            t.printStackTrace();
        }
    }
}
